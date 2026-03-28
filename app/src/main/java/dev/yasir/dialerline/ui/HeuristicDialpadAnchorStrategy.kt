package dev.yasir.dialerline.ui

import android.graphics.Rect
import android.view.View
import dev.yasir.dialerline.util.Logger
import dev.yasir.dialerline.util.readableText
import dev.yasir.dialerline.util.breadthFirst
import kotlin.math.abs
import kotlin.math.roundToInt

class HeuristicDialpadAnchorStrategy : DialpadAnchorStrategy {

    override fun isDialpadVisible(root: View): Boolean {
        val rootRect = Rect()
        if (!root.getGlobalVisibleRect(rootRect) || rootRect.isEmpty) {
            Logger.d("DialpadAnchor", "event=detect visible=false reason=root_not_visible")
            return false
        }

        val density = root.resources.displayMetrics.density
        val minimumKeySizePx = (MINIMUM_KEY_SIZE_DP * density).roundToInt()
        val rowMergeTolerancePx = (ROW_MERGE_TOLERANCE_DP * density).roundToInt()
        val keypadLowerBound = rootRect.top + (rootRect.height() * LOWER_SCREEN_FRACTION).roundToInt()

        val keyBounds = mutableListOf<KeyBounds>()
        val discoveredKeys = linkedSetOf<String>()

        root.breadthFirst()
            .filter { it.isShown && it.visibility == View.VISIBLE && it.isClickable && it.isEnabled && it.alpha >= MINIMUM_ALPHA }
            .forEach { view ->
                // Phone by Google obfuscates internal classes aggressively, so the UI injector stays
                // generic and uses visible dialpad-key signatures instead of class-name anchors.
                val normalizedKeys = view.readableText()
                    .orEmpty()
                    .filter { it.isDigit() || it == '*' || it == '#' }
                    .map { it.toString() }
                    .filter(VALID_KEYS::contains)
                if (normalizedKeys.isEmpty()) {
                    return@forEach
                }

                val viewRect = Rect()
                if (!view.getGlobalVisibleRect(viewRect) || viewRect.isEmpty) {
                    return@forEach
                }
                if (viewRect.width() < minimumKeySizePx || viewRect.height() < minimumKeySizePx) {
                    return@forEach
                }
                if (viewRect.centerY() < keypadLowerBound) {
                    return@forEach
                }

                normalizedKeys.forEach { key ->
                    discoveredKeys += key
                    keyBounds += KeyBounds(key = key, centerY = viewRect.centerY())
                }
            }

        val rowCount = keyBounds
            .map(KeyBounds::centerY)
            .sorted()
            .fold(mutableListOf<Int>()) { rows, centerY ->
                if (rows.none { abs(it - centerY) <= rowMergeTolerancePx }) {
                    rows += centerY
                }
                rows
            }
            .size

        val isDialpad = REQUIRED_CORE_KEYS.all(discoveredKeys::contains) &&
            SPECIAL_KEYS.any(discoveredKeys::contains) &&
            keyBounds.size >= MINIMUM_VISIBLE_KEYS &&
            rowCount >= MINIMUM_ROW_COUNT

        Logger.d(
            "DialpadAnchor",
            "event=detect visible=$isDialpad keys=${discoveredKeys.joinToString(separator = "")} visibleKeys=${keyBounds.size} rows=$rowCount",
        )
        return isDialpad
    }

    private data class KeyBounds(
        val key: String,
        val centerY: Int,
    )

    private companion object {
        val REQUIRED_CORE_KEYS = ('0'..'9').map { it.toString() }.toSet()
        val SPECIAL_KEYS = setOf("*", "#")
        val VALID_KEYS = REQUIRED_CORE_KEYS + SPECIAL_KEYS
        const val MINIMUM_KEY_SIZE_DP = 40
        const val ROW_MERGE_TOLERANCE_DP = 28
        const val MINIMUM_VISIBLE_KEYS = 10
        const val MINIMUM_ROW_COUNT = 4
        const val LOWER_SCREEN_FRACTION = 0.33f
        const val MINIMUM_ALPHA = 0.85f
    }
}
