package dev.yasir.dialerline.util

import android.app.Application
import android.content.Context

object ProcessAppContext {
    @Volatile
    private var application: Application? = null

    fun install(application: Application) {
        this.application = application
        Logger.d("AppContext", "event=installed package=${application.packageName}")
    }

    fun get(): Context? = application?.applicationContext
}
