package dev.yasir.dialerline.hook

import dev.yasir.dialerline.account.PhoneAccountResolver
import dev.yasir.dialerline.guard.EmergencyAndMmiGuard
import dev.yasir.dialerline.state.SelectedAccountStore
import dev.yasir.dialerline.state.SimSelectionController
import dev.yasir.dialerline.ui.HeuristicDialpadAnchorStrategy
import dev.yasir.dialerline.util.Logger
import io.github.libxposed.api.XposedModule
import java.util.concurrent.atomic.AtomicBoolean

class DialerProcessEntrypoint private constructor(
    private val module: XposedModule,
) {
    private val phoneAccountResolver = PhoneAccountResolver()
    private val selectedAccountStore = SelectedAccountStore()
    private val simSelectionController = SimSelectionController(phoneAccountResolver, selectedAccountStore)
    private val emergencyAndMmiGuard = EmergencyAndMmiGuard()

    fun installHooks() {
        ProcessContextHook(module).register()
        OutgoingCallInterceptor(
            strategies = listOf(
                TelecomPlaceCallHookStrategy(module, simSelectionController, emergencyAndMmiGuard),
                IntentCallHookStrategy(module, simSelectionController, emergencyAndMmiGuard),
            ),
        ).register()
        DialpadUiInjector(
            module = module,
            simSelectionController = simSelectionController,
            dialpadAnchorStrategy = HeuristicDialpadAnchorStrategy(),
        ).register()
    }

    companion object {
        private val installed = AtomicBoolean(false)

        fun installOnce(module: XposedModule, classLoader: ClassLoader) {
            if (!installed.compareAndSet(false, true)) {
                Logger.d("Entrypoint", "event=install_skipped reason=already_installed classLoader=$classLoader")
                return
            }

            Logger.i("Entrypoint", "event=install_started classLoader=$classLoader")
            DialerProcessEntrypoint(module).installHooks()
        }
    }
}
