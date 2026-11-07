package com.mulocoolbot.gymtracker.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.auth.FirebaseAuth
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "auth_session_prefs"
)

enum class AuthEntryMode {
    SIGN_IN,
    LOG_IN
}

class SessionPreferences(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.authDataStore)

    val authSessionFlow: Flow<AuthSession> = dataStore.data.map { prefs ->
        AuthSession(
            uid = prefs[KEY_UID].orEmpty(),
            googleEmail = prefs[KEY_GOOGLE_EMAIL],
            userEmail = prefs[KEY_USER_EMAIL],
            isGoogleLinked = prefs[KEY_IS_GOOGLE_LINKED] ?: false,
            isEmailLinked = prefs[KEY_IS_EMAIL_LINKED] ?: false,
            isProfileCompleted = prefs[KEY_IS_PROFILE_COMPLETED] ?: false,
            registeredEmails = extractRegisteredEmails(prefs)
        )
    }

    val registeredEmailsFlow: Flow<Set<String>> = dataStore.data.map { prefs ->
        extractRegisteredEmails(prefs)
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

        syncFirebaseCreateUser(normalizedEmail, password)

        dataStore.edit { prefs ->
            val newUid = generateUid(normalizedEmail)
            val previousUid = prefs[KEY_UID].orEmpty()
            val isSameUser = previousUid == newUid && prefs[KEY_IS_PROFILE_COMPLETED] == true
            val updatedEmails = extractRegisteredEmails(prefs) + lowerEmail

            prefs[KEY_UID] = newUid
            prefs[KEY_REGISTERED_UID] = newUid
            prefs[KEY_USER_EMAIL] = normalizedEmail
            prefs[KEY_REGISTERED_EMAIL] = lowerEmail
            prefs[KEY_REGISTERED_EMAILS] = updatedEmails
            prefs[KEY_REGISTERED_PASSWORD] = password
            prefs[KEY_IS_EMAIL_LINKED] = true
            prefs[KEY_IS_PROFILE_COMPLETED] = isSameUser
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
        }

        if (authenticated) {
            syncFirebaseSignIn(normalizedEmail, password)
        }
        return authenticated
    }

    suspend fun signInWithGoogle(
        email: String,
        entryMode: AuthEntryMode = AuthEntryMode.SIGN_IN
    ) {
        val normalizedEmail = email.trim()
        val lowerEmail = normalizeEmail(normalizedEmail)
        if (normalizedEmail.isEmpty()) return

        dataStore.edit { prefs ->
            val currentUid = prefs[KEY_UID].orEmpty()
            val isCurrentlyAuthenticated = (prefs[KEY_IS_GOOGLE_LINKED] ?: false) ||
                (prefs[KEY_IS_EMAIL_LINKED] ?: false)
            val existingEmails = extractRegisteredEmails(prefs)
            val wasRegistered = lowerEmail in existingEmails
            val registeredGoogle = prefs[KEY_REGISTERED_GOOGLE_EMAIL]
            val registeredUid = prefs[KEY_REGISTERED_UID].orEmpty()

            val resolvedUid = when {
                isCurrentlyAuthenticated && currentUid.isNotBlank() -> currentUid
                registeredGoogle?.equals(normalizedEmail, ignoreCase = true) == true &&
                    registeredUid.isNotBlank() -> registeredUid
                else -> generateUid(normalizedEmail)
            }

            prefs[KEY_UID] = resolvedUid
            prefs[KEY_REGISTERED_UID] = resolvedUid
            prefs[KEY_GOOGLE_EMAIL] = normalizedEmail
            prefs[KEY_REGISTERED_GOOGLE_EMAIL] = lowerEmail
            prefs[KEY_REGISTERED_EMAILS] = existingEmails + lowerEmail
            prefs[KEY_IS_GOOGLE_LINKED] = true

            if (entryMode == AuthEntryMode.LOG_IN && wasRegistered) {
                prefs[KEY_IS_PROFILE_COMPLETED] = true
            } else if (!isCurrentlyAuthenticated && !wasRegistered) {
                prefs[KEY_IS_PROFILE_COMPLETED] = false
            }
        }
    }

    suspend fun signInWithEmail(
        email: String,
        entryMode: AuthEntryMode = AuthEntryMode.SIGN_IN
    ) {
        val normalizedEmail = email.trim()
        val lowerEmail = normalizeEmail(normalizedEmail)
        if (normalizedEmail.isEmpty()) return

        dataStore.edit { prefs ->
            val currentUid = prefs[KEY_UID].orEmpty()
            val isCurrentlyAuthenticated = (prefs[KEY_IS_GOOGLE_LINKED] ?: false) ||
                (prefs[KEY_IS_EMAIL_LINKED] ?: false)
            val existingEmails = extractRegisteredEmails(prefs)
            val wasRegistered = lowerEmail in existingEmails
            val registeredEmail = prefs[KEY_REGISTERED_EMAIL]
            val registeredUid = prefs[KEY_REGISTERED_UID].orEmpty()

            val resolvedUid = when {
                isCurrentlyAuthenticated && currentUid.isNotBlank() -> currentUid
                registeredEmail?.equals(normalizedEmail, ignoreCase = true) == true &&
                    registeredUid.isNotBlank() -> registeredUid
                else -> generateUid(normalizedEmail)
            }

            prefs[KEY_UID] = resolvedUid
            prefs[KEY_REGISTERED_UID] = resolvedUid
            prefs[KEY_USER_EMAIL] = normalizedEmail
            prefs[KEY_REGISTERED_EMAIL] = lowerEmail
            prefs[KEY_REGISTERED_EMAILS] = existingEmails + lowerEmail
            prefs[KEY_IS_EMAIL_LINKED] = true

            if (entryMode == AuthEntryMode.LOG_IN && wasRegistered) {
                prefs[KEY_IS_PROFILE_COMPLETED] = true
            } else if (!isCurrentlyAuthenticated && !wasRegistered) {
                prefs[KEY_IS_PROFILE_COMPLETED] = false
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

    suspend fun markProfileCompleted(completed: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_IS_PROFILE_COMPLETED] = completed
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

    private fun normalizeEmail(email: String): String {
        return email.trim().lowercase(Locale.US)
    }

    private fun syncFirebaseCreateUser(email: String, password: String) {
        runCatching {
            FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
        }
    }

    private fun syncFirebaseSignIn(email: String, password: String) {
        runCatching {
            FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
        }
    }

    private fun generateUid(seedEmail: String): String {
        return computeUidForEmail(seedEmail)
    }

    companion object {
        fun computeUidForEmail(seedEmail: String): String {
            if (seedEmail.isBlank()) return UUID.randomUUID().toString()
            return UUID.nameUUIDFromBytes(seedEmail.trim().lowercase(Locale.US).toByteArray()).toString()
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
        private val KEY_IS_PROFILE_COMPLETED = booleanPreferencesKey("is_profile_completed")
    }
}
