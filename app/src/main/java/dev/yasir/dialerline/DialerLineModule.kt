package dev.yasir.dialerline

import dev.yasir.dialerline.hook.DialerProcessEntrypoint
import dev.yasir.dialerline.util.Logger
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class DialerLineModule : XposedModule() {

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        Logger.attach(this)
        Logger.i(
            component = "Module",
            message = "event=module_loaded process=${param.processName} api=${getApiVersion()} framework=${getFrameworkName()} version=${getFrameworkVersion()}",
        )
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (param.packageName != TARGET_PACKAGE || !param.isFirstPackage) {
            return
        }

        DialerProcessEntrypoint.installOnce(module = this, classLoader = param.classLoader)
    }

    private companion object {
        const val TARGET_PACKAGE = "com.google.android.dialer"
    }
}
