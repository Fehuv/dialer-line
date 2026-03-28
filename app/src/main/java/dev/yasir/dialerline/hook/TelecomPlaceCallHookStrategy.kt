package dev.yasir.dialerline.hook

import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import dev.yasir.dialerline.guard.EmergencyAndMmiGuard
import dev.yasir.dialerline.state.SimSelectionController
import dev.yasir.dialerline.util.Logger
import dev.yasir.dialerline.util.ProcessAppContext
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

class TelecomPlaceCallHookStrategy(
    private val module: XposedModule,
    private val simSelectionController: SimSelectionController,
    private val emergencyAndMmiGuard: EmergencyAndMmiGuard,
) : CallPlacementHookStrategy {

    override fun register() {
        val placeCall = TelecomManager::class.java.getDeclaredMethod(
            "placeCall",
            Uri::class.java,
            Bundle::class.java,
        )

        module
            .hook(placeCall)
            .setPriority(XposedInterface.PRIORITY_HIGHEST)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val context = ProcessAppContext.get() ?: return chain.proceed()
                    val uri = chain.getArg(0) as? Uri
                    if (!emergencyAndMmiGuard.shouldOverride(context, uri)) {
                        return chain.proceed()
                    }

                    val selected = simSelectionController.getSelectedOrFallback(context)
                    if (selected == null) {
                        Logger.d("TelecomHook", "event=skip reason=no_selected_account")
                        return chain.proceed()
                    }

                    // Clone the extras bundle so the dialer's original bundle object is not mutated
                    // in-place if later code keeps a reference to it.
                    val originalExtras = chain.getArg(1) as? Bundle
                    val updatedExtras = Bundle(originalExtras ?: Bundle()).apply {
                        putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, selected.phoneAccountHandle)
                    }

                    Logger.i(
                        "TelecomHook",
                        "event=account_applied strategy=placeCall label=${selected.displayLabel} key=${selected.stableKey}",
                    )
                    return chain.proceed(arrayOf(uri, updatedExtras))
                }
            })

        Logger.i("TelecomHook", "event=hook_registered method=TelecomManager.placeCall")
    }
}
