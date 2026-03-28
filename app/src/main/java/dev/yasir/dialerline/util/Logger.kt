package dev.yasir.dialerline.util

import android.util.Log
import dev.yasir.dialerline.BuildConfig
import io.github.libxposed.api.XposedModule

object Logger {
    private const val PREFIX = "DialerLine"

    @Volatile
    private var module: XposedModule? = null

    fun attach(module: XposedModule) {
        this.module = module
    }

    fun d(component: String, message: String) {
        if (BuildConfig.DIAGNOSTICS_ENABLED) {
            log(Log.DEBUG, component, message, null)
        }
    }

    fun i(component: String, message: String) {
        log(Log.INFO, component, message, null)
    }

    fun w(component: String, message: String, throwable: Throwable? = null) {
        log(Log.WARN, component, message, throwable)
    }

    fun e(component: String, message: String, throwable: Throwable? = null) {
        log(Log.ERROR, component, message, throwable)
    }

    private fun log(priority: Int, component: String, message: String, throwable: Throwable?) {
        val tag = "$PREFIX/$component"
        if (throwable == null) {
            Log.println(priority, tag, message)
            module?.log(priority, tag, message)
        } else {
            Log.println(priority, tag, "$message\n${Log.getStackTraceString(throwable)}")
            module?.log(priority, tag, message, throwable)
        }
    }
}
