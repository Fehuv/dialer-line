package dev.yasir.dialerline.hook

import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.telecom.TelecomManager
import dev.yasir.dialerline.guard.EmergencyAndMmiGuard
import dev.yasir.dialerline.state.SimSelectionController
import dev.yasir.dialerline.util.Logger
import dev.yasir.dialerline.util.ProcessAppContext
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

class IntentCallHookStrategy(
    private val module: XposedModule,
    private val simSelectionController: SimSelectionController,
    private val emergencyAndMmiGuard: EmergencyAndMmiGuard,
) : CallPlacementHookStrategy {

    override fun register() {
        hookStartActivity(
            ContextWrapper::class.java.getDeclaredMethod("startActivity", Intent::class.java),
            bundleIndex = null,
        )
        hookStartActivity(
            ContextWrapper::class.java.getDeclaredMethod("startActivity", Intent::class.java, Bundle::class.java),
            bundleIndex = 1,
        )
    }

    private fun hookStartActivity(method: java.lang.reflect.Method, bundleIndex: Int?) {
        module
            .hook(method)
            .setPriority(XposedInterface.PRIORITY_HIGHEST)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val context = ProcessAppContext.get() ?: return chain.proceed()
                    val originalIntent = chain.getArg(0) as? Intent ?: return chain.proceed()
                    if (!shouldHandleIntent(originalIntent)) {
                        return chain.proceed()
                    }

                    val uri = originalIntent.data
                    if (!emergencyAndMmiGuard.shouldOverride(context, uri)) {
                        return chain.proceed()
                    }

                    val selected = simSelectionController.getSelectedOrFallback(context)
                    if (selected == null) {
                        return chain.proceed()
                    }

                    // Public framework fallback for call flows that build an ACTION_CALL intent
                    // instead of calling TelecomManager.placeCall directly.
                    val updatedIntent = Intent(originalIntent).apply {
                        putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, selected.phoneAccountHandle)
                    }

                    Logger.i(
                        "IntentHook",
                        "event=account_applied strategy=startActivity action=${originalIntent.action} label=${selected.displayLabel}",
                    )

                    val newArgs = if (bundleIndex == null) {
                        arrayOf(updatedIntent)
                    } else {
                        arrayOf(updatedIntent, chain.getArg(bundleIndex) as? Bundle)
                    }
                    return chain.proceed(newArgs)
                }
            })

        Logger.i("IntentHook", "event=hook_registered method=${method.name} paramCount=${method.parameterCount}")
    }

    private fun shouldHandleIntent(intent: Intent): Boolean {
        val action = intent.action ?: return false
        if (action != Intent.ACTION_CALL && action != ACTION_CALL_PRIVILEGED) {
            return false
        }
        return intent.data?.scheme == "tel"
    }

    private companion object {
        const val ACTION_CALL_PRIVILEGED = "android.intent.action.CALL_PRIVILEGED"
    }
}
