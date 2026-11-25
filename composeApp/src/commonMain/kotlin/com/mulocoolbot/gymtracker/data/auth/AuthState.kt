package com.mulocoolbot.gymtracker.data.auth

data class AuthSession(
    val uid: String = "",
    val googleEmail: String? = null,
    val userEmail: String? = null,
    val isGoogleLinked: Boolean = false,
    val isEmailLinked: Boolean = false,
    val isOfflineMode: Boolean = false,
    val isProfileCompleted: Boolean = false,
    val registeredEmails: Set<String> = emptySet()
) {
    val isAuthenticated: Boolean
        get() = uid.isNotBlank() && (isGoogleLinked || isEmailLinked || isOfflineMode)
}

data class RestTimerPreferences(
    val warmupSeconds: Int = 60,
    val normalSeconds: Int = 120,
    val finalSeconds: Int = 180
)
