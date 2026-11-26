package com.mulocoolbot.gymtracker.platform

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath

expect fun getAuthPreferencesPath(): String

fun createAuthPreferencesDataStore(
    producePath: () -> String = ::getAuthPreferencesPath
): DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(
    produceFile = { producePath().toPath() }
)

object AuthPreferencesProvider {
    val instance: DataStore<Preferences> by lazy { createAuthPreferencesDataStore() }
}
