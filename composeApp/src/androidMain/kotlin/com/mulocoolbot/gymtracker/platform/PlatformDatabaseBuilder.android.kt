package com.mulocoolbot.gymtracker.platform

import androidx.room.Room
import androidx.room.RoomDatabase
import com.mulocoolbot.gymtracker.data.local.GymDatabase

actual fun createGymDatabaseBuilder(): RoomDatabase.Builder<GymDatabase> {
    val context = AndroidPlatformContext.requireContext()
    return Room.databaseBuilder<GymDatabase>(
        context = context,
        name = context.getDatabasePath("gym_tracker.db").absolutePath
    )
}
