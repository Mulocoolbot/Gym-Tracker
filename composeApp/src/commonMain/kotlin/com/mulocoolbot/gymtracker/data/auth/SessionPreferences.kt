package com.mulocoolbot.gymtracker.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class AuthEntryMode {
    SIGN_IN,
    LOG_IN
}

class SessionPreferences(
    private val dataStore: DataStore<Preferences>,
    private val onRemoteAuthSync: ((email: String, password: String, isRegister: Boolean) -> Unit)? = null
) {

    val authSessionFlow: Flow<AuthSession> = dataStore.data.map { prefs ->
        AuthSession(
            uid = prefs[KEY_UID].orEmpty(),
            googleEmail = prefs[KEY_GOOGLE_EMAIL],
            userEmail = prefs[KEY_USER_EMAIL],
            isGoogleLinked = prefs[KEY_IS_GOOGLE_LINKED] ?: false,
            isEmailLinked = prefs[KEY_IS_EMAIL_LINKED] ?: false,
            isOfflineMode = prefs[KEY_IS_OFFLINE_MODE] ?: false,
            isProfileCompleted = prefs[KEY_IS_PROFILE_COMPLETED] ?: false,
            registeredEmails = extractRegisteredEmails(prefs),
            shouldShowTutorial = prefs[KEY_SHOULD_SHOW_TUTORIAL] ?: false,
            hasCompletedTutorial = prefs[KEY_HAS_COMPLETED_TUTORIAL] ?: false
        )
    }

    val registeredEmailsFlow: Flow<Set<String>> = dataStore.data.map { prefs ->
        extractRegisteredEmails(prefs)
    }

    val restTimerPreferencesFlow: Flow<RestTimerPreferences> = dataStore.data.map { prefs ->
        RestTimerPreferences(
            warmupSeconds = prefs[KEY_REST_WARMUP_SECONDS] ?: 60,
            normalSeconds = prefs[KEY_REST_NORMAL_SECONDS] ?: 120,
            finalSeconds = prefs[KEY_REST_FINAL_SECONDS] ?: 180
        )
    }

    suspend fun updateRestTimerDurations(
        warmupSeconds: Int,
        normalSeconds: Int,
        finalSeconds: Int
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_REST_WARMUP_SECONDS] = warmupSeconds.coerceAtLeast(1)
            prefs[KEY_REST_NORMAL_SECONDS] = normalSeconds.coerceAtLeast(1)
            prefs[KEY_REST_FINAL_SECONDS] = finalSeconds.coerceAtLeast(1)
        }
    }

    suspend fun isEmailRegistered(email: String): Boolean {
        val normalized = normalizeEmail(email)
        if (normalized.isEmpty()) return false
        val prefs = dataStore.data.first()
        return normalized in extractRegisteredEmails(prefs)
    }

    suspend fun registerWithEmailPassword(email: String, password: String): Boolean {
        val normalizedEmail = email.trim()
        val lowerEmail = normalizeEmail(normalizedEmail)
        if (normalizedEmail.isEmpty() || password.length < 6) return false

        onRemoteAuthSync?.invoke(normalizedEmail, password, true)

        dataStore.edit { prefs ->
            val newUid = generateUid(normalizedEmail)
            val previousUid = prefs[KEY_UID].orEmpty()
            val isSameUser = previousUid == newUid && prefs[KEY_IS_PROFILE_COMPLETED] == true
            val existingEmails = extractRegisteredEmails(prefs)
            val wasRegistered = lowerEmail in existingEmails
            val hasCompleted = prefs[KEY_HAS_COMPLETED_TUTORIAL] ?: false
            val updatedEmails = existingEmails + lowerEmail

            prefs[KEY_UID] = newUid
            prefs[KEY_REGISTERED_UID] = newUid
            prefs[KEY_USER_EMAIL] = normalizedEmail
            prefs[KEY_REGISTERED_EMAIL] = lowerEmail
            prefs[KEY_REGISTERED_EMAILS] = updatedEmails
            prefs[KEY_REGISTERED_PASSWORD] = password
            prefs[KEY_IS_EMAIL_LINKED] = true
            prefs[KEY_IS_PROFILE_COMPLETED] = isSameUser
            prefs[KEY_SHOULD_SHOW_TUTORIAL] = !wasRegistered && !isSameUser && !hasCompleted
        }
        return true
    }

    suspend fun loginWithEmailPassword(email: String, password: String): Boolean {
        val normalizedEmail = email.trim()
        val lowerEmail = normalizeEmail(normalizedEmail)
        if (normalizedEmail.isEmpty() || password.length < 6) return false

        var authenticated = false
        dataStore.edit { prefs ->
            val registeredEmail = prefs[KEY_REGISTERED_EMAIL]
            val registeredPassword = prefs[KEY_REGISTERED_PASSWORD]
            val updatedEmails = extractRegisteredEmails(prefs) + lowerEmail

            if (registeredEmail != null && registeredPassword != null) {
                if (registeredEmail.equals(normalizedEmail, ignoreCase = true) &&
                    registeredPassword == password
                ) {
                    val restoredUid = prefs[KEY_REGISTERED_UID]
                        ?.takeIf { it.isNotBlank() }
                        ?: prefs[KEY_UID].orEmpty().ifBlank { generateUid(normalizedEmail) }
                    prefs[KEY_UID] = restoredUid
                    prefs[KEY_USER_EMAIL] = normalizedEmail
                    prefs[KEY_REGISTERED_EMAILS] = updatedEmails
                    prefs[KEY_IS_EMAIL_LINKED] = true
                    prefs[KEY_IS_PROFILE_COMPLETED] = true
                    authenticated = true
                }
            } else {
                val restoredUid = prefs[KEY_UID].orEmpty().ifBlank { generateUid(normalizedEmail) }
                prefs[KEY_UID] = restoredUid
                prefs[KEY_REGISTERED_UID] = restoredUid
                prefs[KEY_USER_EMAIL] = normalizedEmail
                prefs[KEY_REGISTERED_EMAIL] = lowerEmail
                prefs[KEY_REGISTERED_EMAILS] = updatedEmails
                prefs[KEY_REGISTERED_PASSWORD] = password
                prefs[KEY_IS_EMAIL_LINKED] = true
                authenticated = true
            }
            if (authenticated) {
                prefs[KEY_SHOULD_SHOW_TUTORIAL] = false
            }
        }

        if (authenticated) {
            onRemoteAuthSync?.invoke(normalizedEmail, password, false)
        }
        return authenticated
    }

    suspend fun signInWithGoogle(
        email: String,
        entryMode: AuthEntryMode = AuthEntryMode.SIGN_IN
    ) {
        signInWithProvider(email = email, entryMode = entryMode, isGoogle = true)
    }

    suspend fun signInWithEmail(
        email: String,
        entryMode: AuthEntryMode = AuthEntryMode.SIGN_IN
    ) {
        signInWithProvider(email = email, entryMode = entryMode, isGoogle = false)
    }

    private suspend fun signInWithProvider(
        email: String,
        entryMode: AuthEntryMode,
        isGoogle: Boolean
    ) {
        val normalizedEmail = email.trim()
        val lowerEmail = normalizeEmail(normalizedEmail)
        if (normalizedEmail.isEmpty()) return

        val activeKey = if (isGoogle) KEY_GOOGLE_EMAIL else KEY_USER_EMAIL
        val registeredKey = if (isGoogle) KEY_REGISTERED_GOOGLE_EMAIL else KEY_REGISTERED_EMAIL
        val linkedKey = if (isGoogle) KEY_IS_GOOGLE_LINKED else KEY_IS_EMAIL_LINKED

        dataStore.edit { prefs ->
            val currentUid = prefs[KEY_UID].orEmpty()
            val isCurrentlyAuthenticated = (prefs[KEY_IS_GOOGLE_LINKED] ?: false) ||
                (prefs[KEY_IS_EMAIL_LINKED] ?: false) ||
                (prefs[KEY_IS_OFFLINE_MODE] ?: false)
            val existingEmails = extractRegisteredEmails(prefs)
            val wasRegistered = lowerEmail in existingEmails
            val registeredProviderEmail = prefs[registeredKey]
            val registeredUid = prefs[KEY_REGISTERED_UID].orEmpty()

            val resolvedUid = when {
                isCurrentlyAuthenticated && currentUid.isNotBlank() -> currentUid
                registeredProviderEmail?.equals(normalizedEmail, ignoreCase = true) == true &&
                    registeredUid.isNotBlank() -> registeredUid
                else -> generateUid(normalizedEmail)
            }

            prefs[KEY_UID] = resolvedUid
            prefs[KEY_REGISTERED_UID] = resolvedUid
            prefs[activeKey] = normalizedEmail
            prefs[registeredKey] = lowerEmail
            prefs[KEY_REGISTERED_EMAILS] = existingEmails + lowerEmail
            prefs[linkedKey] = true

            if (entryMode == AuthEntryMode.LOG_IN && wasRegistered) {
                prefs[KEY_IS_PROFILE_COMPLETED] = true
            } else if (!isCurrentlyAuthenticated && !wasRegistered) {
                prefs[KEY_IS_PROFILE_COMPLETED] = false
            }

            if (!isCurrentlyAuthenticated) {
                val hasCompleted = prefs[KEY_HAS_COMPLETED_TUTORIAL] ?: false
                prefs[KEY_SHOULD_SHOW_TUTORIAL] =
                    entryMode == AuthEntryMode.SIGN_IN && !wasRegistered && !hasCompleted
            }
        }
    }

    suspend fun unlinkGoogle() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_GOOGLE_EMAIL)
            prefs[KEY_IS_GOOGLE_LINKED] = false
        }
    }

    suspend fun unlinkEmail() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_USER_EMAIL)
            prefs[KEY_IS_EMAIL_LINKED] = false
        }
    }

    suspend fun continueOffline(preferredUid: String? = null) {
        dataStore.edit { prefs ->
            val resolvedUid = prefs[KEY_UID].orEmpty()
                .ifBlank { prefs[KEY_REGISTERED_UID].orEmpty() }
                .ifBlank { preferredUid.orEmpty().trim() }
                .ifBlank { computeUidForEmail("offline-local@mfit.local") }
            prefs[KEY_UID] = resolvedUid
            prefs[KEY_REGISTERED_UID] = resolvedUid
            prefs[KEY_IS_OFFLINE_MODE] = true
            prefs[KEY_SHOULD_SHOW_TUTORIAL] = false
        }
    }

    suspend fun markProfileCompleted(completed: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_IS_PROFILE_COMPLETED] = completed
        }
    }

    suspend fun setShouldShowTutorial(shouldShow: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SHOULD_SHOW_TUTORIAL] = shouldShow
        }
    }

    suspend fun markTutorialCompleted() {
        dataStore.edit { prefs ->
            prefs[KEY_SHOULD_SHOW_TUTORIAL] = false
            prefs[KEY_HAS_COMPLETED_TUTORIAL] = true
        }
    }

    private fun extractRegisteredEmails(prefs: Preferences): Set<String> {
        val storedSet = prefs[KEY_REGISTERED_EMAILS].orEmpty()
            .map { normalizeEmail(it) }
            .filter { it.isNotEmpty() }
            .toSet()
        val legacyEmail = prefs[KEY_REGISTERED_EMAIL]?.let { normalizeEmail(it) }.orEmpty()
        val legacyGoogle = prefs[KEY_REGISTERED_GOOGLE_EMAIL]?.let { normalizeEmail(it) }.orEmpty()
        return buildSet {
            addAll(storedSet)
            if (legacyEmail.isNotEmpty()) add(legacyEmail)
            if (legacyGoogle.isNotEmpty()) add(legacyGoogle)
        }
    }

    private fun normalizeEmail(email: String): String = email.trim().lowercase()

    private fun generateUid(seedEmail: String): String = computeUidForEmail(seedEmail)

    companion object {
        fun computeUidForEmail(seedEmail: String): String {
            if (seedEmail.isBlank()) return UuidGenerator.randomUuidV4()
            return UuidGenerator.nameUuidV3FromBytes(seedEmail.trim().lowercase().encodeToByteArray())
        }

        private val KEY_UID = stringPreferencesKey("uid")
        private val KEY_REGISTERED_UID = stringPreferencesKey("registered_uid")
        private val KEY_GOOGLE_EMAIL = stringPreferencesKey("google_email")
        private val KEY_REGISTERED_GOOGLE_EMAIL = stringPreferencesKey("registered_google_email")
        private val KEY_USER_EMAIL = stringPreferencesKey("user_email")
        private val KEY_REGISTERED_EMAIL = stringPreferencesKey("registered_email")
        private val KEY_REGISTERED_EMAILS = stringSetPreferencesKey("registered_emails")
        private val KEY_REGISTERED_PASSWORD = stringPreferencesKey("registered_password")
        private val KEY_IS_GOOGLE_LINKED = booleanPreferencesKey("is_google_linked")
        private val KEY_IS_EMAIL_LINKED = booleanPreferencesKey("is_email_linked")
        private val KEY_IS_OFFLINE_MODE = booleanPreferencesKey("is_offline_mode")
        private val KEY_IS_PROFILE_COMPLETED = booleanPreferencesKey("is_profile_completed")
        private val KEY_SHOULD_SHOW_TUTORIAL = booleanPreferencesKey("should_show_tutorial")
        private val KEY_HAS_COMPLETED_TUTORIAL = booleanPreferencesKey("has_completed_tutorial")
        private val KEY_REST_WARMUP_SECONDS = intPreferencesKey("rest_warmup_seconds")
        private val KEY_REST_NORMAL_SECONDS = intPreferencesKey("rest_normal_seconds")
        private val KEY_REST_FINAL_SECONDS = intPreferencesKey("rest_final_seconds")
    }
}
