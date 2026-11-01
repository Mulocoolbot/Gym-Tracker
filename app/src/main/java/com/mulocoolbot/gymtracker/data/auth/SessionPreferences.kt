package com.mulocoolbot.gymtracker.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "auth_session_prefs"
)

class SessionPreferences(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.authDataStore)

    val authSessionFlow: Flow<AuthSession> = dataStore.data.map { prefs ->
        AuthSession(
            uid = prefs[KEY_UID].orEmpty(),
            googleEmail = prefs[KEY_GOOGLE_EMAIL],
            userEmail = prefs[KEY_USER_EMAIL],
            isGoogleLinked = prefs[KEY_IS_GOOGLE_LINKED] ?: false,
            isEmailLinked = prefs[KEY_IS_EMAIL_LINKED] ?: false,
            isProfileCompleted = prefs[KEY_IS_PROFILE_COMPLETED] ?: false
        )
    }

    suspend fun signInWithGoogle(email: String) {
        val normalizedEmail = email.trim()
        dataStore.edit { prefs ->
            val currentUid = prefs[KEY_UID].orEmpty()
            if (currentUid.isBlank()) {
                prefs[KEY_UID] = generateUid(normalizedEmail)
            }
            prefs[KEY_GOOGLE_EMAIL] = normalizedEmail
            prefs[KEY_IS_GOOGLE_LINKED] = true
        }
    }

    suspend fun signInWithEmail(email: String) {
        val normalizedEmail = email.trim()
        dataStore.edit { prefs ->
            val currentUid = prefs[KEY_UID].orEmpty()
            if (currentUid.isBlank()) {
                prefs[KEY_UID] = generateUid(normalizedEmail)
            }
            prefs[KEY_USER_EMAIL] = normalizedEmail
            prefs[KEY_IS_EMAIL_LINKED] = true
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

    private fun generateUid(seedEmail: String): String {
        if (seedEmail.isBlank()) return UUID.randomUUID().toString()
        return UUID.nameUUIDFromBytes(seedEmail.lowercase().toByteArray()).toString()
    }

    companion object {
        private val KEY_UID = stringPreferencesKey("uid")
        private val KEY_GOOGLE_EMAIL = stringPreferencesKey("google_email")
        private val KEY_USER_EMAIL = stringPreferencesKey("user_email")
        private val KEY_IS_GOOGLE_LINKED = booleanPreferencesKey("is_google_linked")
        private val KEY_IS_EMAIL_LINKED = booleanPreferencesKey("is_email_linked")
        private val KEY_IS_PROFILE_COMPLETED = booleanPreferencesKey("is_profile_completed")
    }
}

