package dev.yasir.dialerline.hook

import android.app.Application
import android.app.Instrumentation
import dev.yasir.dialerline.util.Logger
import dev.yasir.dialerline.util.ProcessAppContext
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

class ProcessContextHook(
    private val module: XposedModule,
) {
    fun register() {
        val method = Instrumentation::class.java.getDeclaredMethod(
            "callApplicationOnCreate",
            Application::class.java,
        )
        module
            .hook(method)
            .setPriority(XposedInterface.PRIORITY_HIGHEST)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    val application = chain.getArg(0) as? Application
                    if (application != null) {
                        ProcessAppContext.install(application)
                    }
                    Logger.d("ProcessContext", "event=application_created package=${application?.packageName}")
                    return result
                }
            })
        Logger.i("ProcessContext", "event=hook_registered method=Instrumentation.callApplicationOnCreate")
    }
}
