package dev.yasir.dialerline.hook

import android.app.Activity
import android.app.Instrumentation
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.TextView
import dev.yasir.dialerline.state.SimSelectionController
import dev.yasir.dialerline.ui.DialpadAnchorStrategy
import dev.yasir.dialerline.util.Logger
import dev.yasir.dialerline.util.breadthFirst
import dev.yasir.dialerline.util.readableText
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

class DialpadUiInjector(
    private val module: XposedModule,
    private val simSelectionController: SimSelectionController,
    private val dialpadAnchorStrategy: DialpadAnchorStrategy,
) {
    private val sessions = WeakHashMap<Activity, OverlaySession>()

    fun register() {
        hookActivityResume()
        hookActivityDestroy()
    }

    private fun hookActivityResume() {
        val method = Instrumentation::class.java.getDeclaredMethod(
            "callActivityOnResume",
            Activity::class.java,
        )
        module
            .hook(method)
            .setPriority(XposedInterface.PRIORITY_LOWEST)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    val activity = chain.getArg(0) as? Activity ?: return result
                    if (activity.packageName != TARGET_PACKAGE) {
                        return result
                    }
                    ensureSession(activity)?.scheduleRefresh()
                    return result
                }
            })
        Logger.i("DialpadUi", "event=hook_registered method=Instrumentation.callActivityOnResume")
    }

    private fun hookActivityDestroy() {
        val method = Instrumentation::class.java.getDeclaredMethod(
            "callActivityOnDestroy",
            Activity::class.java,
        )
        module
            .hook(method)
            .setPriority(XposedInterface.PRIORITY_LOWEST)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val activity = chain.getArg(0) as? Activity
                    activity?.let(::disposeSession)
                    return chain.proceed()
                }
            })
        Logger.i("DialpadUi", "event=hook_registered method=Instrumentation.callActivityOnDestroy")
    }

    private fun ensureSession(activity: Activity): OverlaySession? {
        sessions[activity]?.let { return it }

        val decorRoot = activity.window?.decorView as? ViewGroup
            ?: run {
                Logger.w("DialpadUi", "event=session_skipped reason=decor_root_missing activity=${activity::class.java.name}")
                return null
            }

        val pillView = createPillView(activity)
        val session = OverlaySession(
            activity = activity,
            root = decorRoot,
            pillView = pillView,
        )

        decorRoot.addView(
            pillView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            },
        )
        decorRoot.viewTreeObserver.addOnGlobalLayoutListener(session.layoutListener)
        sessions[activity] = session
        Logger.d("DialpadUi", "event=session_created activity=${activity::class.java.name}")
        return session
    }

    private fun disposeSession(activity: Activity) {
        val session = sessions.remove(activity) ?: return
        val observer = session.root.viewTreeObserver
        if (observer.isAlive) {
            observer.removeOnGlobalLayoutListener(session.layoutListener)
        }
        session.root.removeCallbacks(session.refreshRunnable)
        session.root.removeView(session.pillView)
        Logger.d("DialpadUi", "event=session_disposed activity=${activity::class.java.name}")
    }

    private fun refresh(session: OverlaySession) {
        val state = simSelectionController.getState(session.activity)
        val shouldShow = state.canToggle && dialpadAnchorStrategy.isDialpadVisible(session.root)
        if (!shouldShow) {
            session.pillView.visibility = View.GONE
            return
        }

        val selected = state.selected ?: run {
            session.pillView.visibility = View.GONE
            return
        }

        session.pillView.text = selected.displayLabel
        session.pillView.contentDescription = "Outgoing line ${selected.displayLabel}"
        stylePill(session.activity, session.pillView)
        if (!positionPill(session.activity, session.root, session.pillView)) {
            session.pillView.visibility = View.GONE
            return
        }
        session.pillView.visibility = View.VISIBLE
    }

    private fun createPillView(activity: Activity): TextView {
        return TextView(activity).apply {
            tag = OVERLAY_TAG
            gravity = Gravity.CENTER
            text = DEFAULT_LABEL
            textSize = 12f
            typeface = Typeface.create(typeface, 600, false)
            includeFontPadding = false
            setPadding(dp(activity, 16), dp(activity, 9), dp(activity, 16), dp(activity, 9))
            minWidth = dp(activity, 88)
            minHeight = dp(activity, 34)
            elevation = dp(activity, 1).toFloat()
            visibility = View.GONE
            isClickable = true
            isFocusable = false
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                val selected = simSelectionController.toggle(activity)
                text = selected?.displayLabel ?: DEFAULT_LABEL
                Logger.i("DialpadUi", "event=selection_toggled label=${selected?.displayLabel}")
            }
        }
    }

    private fun stylePill(activity: Activity, pillView: TextView) {
        val surfaceColor = resolveThemeColor(activity, android.R.attr.colorBackgroundFloating, 0xFF16181D.toInt())
        val textColor = resolveThemeColor(activity, android.R.attr.textColorPrimary, Color.WHITE)
        val isLightSurface = luminance(surfaceColor) >= 0.5f
        val backgroundColor = if (isLightSurface) {
            blendColors(surfaceColor, Color.BLACK, 0.05f)
        } else {
            blendColors(surfaceColor, Color.BLACK, 0.22f)
        }
        val strokeColor = blendColors(textColor, backgroundColor, if (isLightSurface) 0.86f else 0.82f)

        val backgroundDrawable = GradientDrawable().apply {
            setShape(GradientDrawable.RECTANGLE)
            cornerRadius = dp(activity, 10).toFloat()
            setColor(backgroundColor)
            setStroke(dp(activity, 1), strokeColor.withAlpha(if (isLightSurface) 0.12f else 0.14f))
        }
        val mask = GradientDrawable().apply {
            setShape(GradientDrawable.RECTANGLE)
            cornerRadius = dp(activity, 10).toFloat()
            setColor(Color.WHITE)
        }
        pillView.background = RippleDrawable(
            ColorStateList.valueOf(textColor.withAlpha(0.08f)),
            backgroundDrawable,
            mask,
        )
        pillView.setTextColor(textColor)
        pillView.alpha = 0.98f
    }

    private fun positionPill(activity: Activity, root: ViewGroup, pillView: TextView): Boolean {
        val layoutParams = createResolvedLayoutParams(activity, root, pillView) ?: return false
        pillView.layoutParams = layoutParams
        return true
    }

    private fun createResolvedLayoutParams(
        activity: Activity,
        root: ViewGroup,
        pillView: TextView,
    ): ViewGroup.LayoutParams? {
        val rootRect = Rect()
        val callAnchor = if (root.getGlobalVisibleRect(rootRect)) {
            findCallAnchorRect(activity, root, rootRect)
        } else {
            null
        }

        if (root !is FrameLayout || callAnchor == null || rootRect.isEmpty) {
            Logger.d("DialpadUi", "event=anchor_missing activity=${activity::class.java.name}")
            return null
        }

        val pillWidth = measuredWidthFor(pillView, activity, ESTIMATED_PILL_WIDTH_DP)
        val pillHeight = measuredHeightFor(pillView, activity, ESTIMATED_PILL_HEIGHT_DP)
        val horizontalGap = dp(activity, CALL_SIDE_GAP_DP)
        val minSideMargin = dp(activity, MIN_SIDE_MARGIN_DP)
        val preferredLeft = (callAnchor.left - rootRect.left) - pillWidth - horizontalGap
        val fallbackLeft = (callAnchor.right - rootRect.left) + horizontalGap
        val leftMargin = when {
            preferredLeft >= minSideMargin -> preferredLeft
            fallbackLeft + pillWidth <= rootRect.width() - minSideMargin -> fallbackLeft
            else -> minSideMargin
        }
        val centeredTop = (callAnchor.centerY() - rootRect.top) - (pillHeight / 2)
        val maxTop = rootRect.height() - pillHeight - dp(activity, MIN_BOTTOM_CLEARANCE_DP)
        val topMargin = centeredTop.coerceIn(dp(activity, MIN_TOP_MARGIN_DP), maxTop.coerceAtLeast(dp(activity, MIN_TOP_MARGIN_DP)))

        return FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.topMargin = topMargin
            marginStart = leftMargin
        }
    }

    private fun findCallAnchorRect(activity: Activity, root: ViewGroup, rootRect: Rect): Rect? {
        val density = activity.resources.displayMetrics.density
        val isLandscape = rootRect.width() > rootRect.height()
        val minimumSize = (MIN_CALL_TARGET_DP * density).roundToInt()
        val centerTolerance = (CENTER_TOLERANCE_DP * density).roundToInt()
        val lowerBoundFraction = if (rootRect.width() > rootRect.height()) {
            LANDSCAPE_CALL_SEARCH_LOWER_FRACTION
        } else {
            PORTRAIT_CALL_SEARCH_LOWER_FRACTION
        }
        val lowerBound = rootRect.top + (rootRect.height() * lowerBoundFraction).roundToInt()
        val dialpadBounds = findDialpadBounds(root, rootRect)

        var bestScore = Int.MIN_VALUE
        var bestRect: Rect? = null

        root.breadthFirst().forEach { view ->
            if (view === root || view.tag == OVERLAY_TAG) {
                return@forEach
            }
            if (!view.isShown || view.visibility != View.VISIBLE || !view.isClickable || !view.isEnabled || view.alpha < 0.85f) {
                return@forEach
            }

            val rect = Rect()
            if (!view.getGlobalVisibleRect(rect) || rect.isEmpty) {
                return@forEach
            }
            if (rect.width() < minimumSize || rect.height() < minimumSize || rect.centerY() < lowerBound) {
                return@forEach
            }
            if (isLandscape && dialpadBounds != null && rect.centerX() < dialpadBounds.left) {
                return@forEach
            }

            val label = view.readableText().orEmpty().lowercase()
            val idName = resolveViewIdName(view).lowercase()
            val centerDistance = abs(rect.centerX() - rootRect.centerX())

            var score = 0
            if (label.contains("call") || label.contains("dial")) {
                score += 12
            }
            if (idName.contains("call") || idName.contains("dial") || idName.contains("fab")) {
                score += 10
            }
            if (centerDistance <= centerTolerance) {
                score += 6
            }
            if (abs(rect.width() - rect.height()) <= (18 * density).roundToInt()) {
                score += 2
            }
            if (rect.width() <= (112 * density).roundToInt() && rect.height() <= (112 * density).roundToInt()) {
                score += 1
            }

            if (score > bestScore) {
                bestScore = score
                bestRect = Rect(rect)
            }
        }

        return bestRect?.takeIf { bestScore >= MIN_CALL_ANCHOR_SCORE }
    }

    private fun findDialpadBounds(root: ViewGroup, rootRect: Rect): Rect? {
        val density = root.resources.displayMetrics.density
        val minimumKeySize = (MIN_DIALPAD_KEY_DP * density).roundToInt()
        val keypadBounds = Rect()
        var hasBounds = false

        root.breadthFirst().forEach { view ->
            if (!view.isShown || view.visibility != View.VISIBLE || !view.isClickable || !view.isEnabled || view.alpha < 0.85f) {
                return@forEach
            }

            val label = view.readableText()
                .orEmpty()
                .filter { it.isDigit() || it == '*' || it == '#' }
            if (label.isEmpty()) {
                return@forEach
            }

            val rect = Rect()
            if (!view.getGlobalVisibleRect(rect) || rect.isEmpty) {
                return@forEach
            }
            if (rect.width() < minimumKeySize || rect.height() < minimumKeySize) {
                return@forEach
            }
            if (rect.centerY() < rootRect.top + (rootRect.height() * DIALPAD_BOUNDS_LOWER_FRACTION).roundToInt()) {
                return@forEach
            }

            if (!hasBounds) {
                keypadBounds.set(rect)
                hasBounds = true
            } else {
                keypadBounds.union(rect)
            }
        }

        return keypadBounds.takeIf { hasBounds && !it.isEmpty }
    }

    private fun resolveThemeColor(activity: Activity, attr: Int, fallback: Int): Int {
        val typedArray = activity.obtainStyledAttributes(intArrayOf(attr))
        return try {
            typedArray.getColor(0, fallback)
        } finally {
            typedArray.recycle()
        }
    }

    private fun dp(activity: Activity, value: Int): Int =
        (value * activity.resources.displayMetrics.density).roundToInt()

    private fun measuredWidthFor(view: TextView, activity: Activity, fallbackDp: Int): Int {
        if (view.measuredWidth > 0) {
            return view.measuredWidth
        }
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(unspecified, unspecified)
        return view.measuredWidth.takeIf { it > 0 } ?: dp(activity, fallbackDp)
    }

    private fun measuredHeightFor(view: TextView, activity: Activity, fallbackDp: Int): Int {
        if (view.measuredHeight > 0) {
            return view.measuredHeight
        }
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(unspecified, unspecified)
        return view.measuredHeight.takeIf { it > 0 } ?: dp(activity, fallbackDp)
    }

    private fun Int.withAlpha(alphaFraction: Float): Int {
        val alpha = (Color.alpha(this) * alphaFraction).roundToInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(this), Color.green(this), Color.blue(this))
    }

    private fun blendColors(baseColor: Int, overlayColor: Int, overlayWeight: Float): Int {
        val weight = overlayWeight.coerceIn(0f, 1f)
        val baseWeight = 1f - weight
        return Color.argb(
            (Color.alpha(baseColor) * baseWeight + Color.alpha(overlayColor) * weight).roundToInt(),
            (Color.red(baseColor) * baseWeight + Color.red(overlayColor) * weight).roundToInt(),
            (Color.green(baseColor) * baseWeight + Color.green(overlayColor) * weight).roundToInt(),
            (Color.blue(baseColor) * baseWeight + Color.blue(overlayColor) * weight).roundToInt(),
        )
    }

    private fun luminance(color: Int): Float {
        fun channel(value: Int): Float {
            val normalized = value / 255f
            return if (normalized <= 0.03928f) {
                normalized / 12.92f
            } else {
                (((normalized + 0.055f) / 1.055f).toDouble().pow(2.4)).toFloat()
            }
        }

        val red = channel(Color.red(color))
        val green = channel(Color.green(color))
        val blue = channel(Color.blue(color))
        return 0.2126f * red + 0.7152f * green + 0.0722f * blue
    }

    private fun resolveViewIdName(view: View): String {
        val viewId = view.id
        if (viewId == View.NO_ID) {
            return ""
        }
        return runCatching { view.resources.getResourceEntryName(viewId) }.getOrDefault("")
    }

    private inner class OverlaySession(
        val activity: Activity,
        val root: ViewGroup,
        val pillView: TextView,
    ) {
        val refreshRunnable = Runnable {
            runCatching { refresh(this) }
                .onFailure { throwable ->
                    Logger.w("DialpadUi", "event=refresh_failed activity=${activity::class.java.name}", throwable)
                    pillView.visibility = View.GONE
                }
        }
        val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            scheduleRefresh()
        }

        fun scheduleRefresh() {
            root.removeCallbacks(refreshRunnable)
            root.post(refreshRunnable)
            root.postDelayed(refreshRunnable, 180L)
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "com.google.android.dialer"
        const val OVERLAY_TAG = "DialerLineOverlay"
        const val DEFAULT_LABEL = "SIM"
        const val MIN_DIALPAD_KEY_DP = 40
        const val DIALPAD_BOUNDS_LOWER_FRACTION = 0.20f
        const val MIN_CALL_TARGET_DP = 44
        const val CENTER_TOLERANCE_DP = 84
        const val PORTRAIT_CALL_SEARCH_LOWER_FRACTION = 0.45f
        const val LANDSCAPE_CALL_SEARCH_LOWER_FRACTION = 0.22f
        const val MIN_CALL_ANCHOR_SCORE = 12
        const val CALL_SIDE_GAP_DP = 12
        const val MIN_SIDE_MARGIN_DP = 12
        const val MIN_TOP_MARGIN_DP = 12
        const val MIN_BOTTOM_CLEARANCE_DP = 12
        const val ESTIMATED_PILL_WIDTH_DP = 108
        const val ESTIMATED_PILL_HEIGHT_DP = 36
    }
}
