package com.mulocoolbot.gymtracker.platform

import android.content.Context
import kotlin.concurrent.Volatile

object AndroidPlatformContext {
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    fun requireContext(): Context {
        return appContext ?: error("AndroidPlatformContext.init(context) must be called before accessing platform storage.")
    }
}
