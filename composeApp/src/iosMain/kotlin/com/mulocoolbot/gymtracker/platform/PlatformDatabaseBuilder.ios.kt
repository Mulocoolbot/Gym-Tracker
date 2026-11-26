package com.mulocoolbot.gymtracker.platform

import androidx.room.Room
import androidx.room.RoomDatabase
import com.mulocoolbot.gymtracker.data.local.GymDatabase

actual fun createGymDatabaseBuilder(): RoomDatabase.Builder<GymDatabase> {
    return Room.databaseBuilder<GymDatabase>(
        name = "${iosDocumentDirectory()}/gym_tracker.db"
    )
}
