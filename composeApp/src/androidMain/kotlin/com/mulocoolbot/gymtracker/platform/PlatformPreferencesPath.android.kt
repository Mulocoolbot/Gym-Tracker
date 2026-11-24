package com.mulocoolbot.gymtracker.platform

import java.io.File

actual fun getAuthPreferencesPath(): String {
    val context = AndroidPlatformContext.requireContext()
    val datastoreDir = File(context.filesDir, "datastore").apply { mkdirs() }
    return File(datastoreDir, "auth_session_prefs.preferences_pb").absolutePath
}
