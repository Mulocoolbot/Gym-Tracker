package com.mulocoolbot.gymtracker.platform

actual fun getAuthPreferencesPath(): String {
    return "${iosDocumentDirectory()}/auth_session_prefs.preferences_pb"
}
