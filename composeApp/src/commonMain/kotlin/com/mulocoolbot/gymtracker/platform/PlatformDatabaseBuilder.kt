package com.mulocoolbot.gymtracker.platform

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.mulocoolbot.gymtracker.data.local.GymDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

expect fun createGymDatabaseBuilder(): RoomDatabase.Builder<GymDatabase>

fun buildGymDatabase(): GymDatabase = createGymDatabaseBuilder()
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .fallbackToDestructiveMigration(dropAllTables = true)
    .build()

object GymDatabaseProvider {
    val instance: GymDatabase by lazy { buildGymDatabase() }
}
