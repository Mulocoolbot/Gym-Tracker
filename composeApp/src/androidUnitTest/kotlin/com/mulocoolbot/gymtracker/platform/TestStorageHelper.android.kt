package com.mulocoolbot.gymtracker.platform

import java.nio.file.Files

actual fun createTestPreferencesPath(name: String): String {
    val dir = Files.createTempDirectory("gymtracker_test_prefs").toFile()
    return dir.resolve(name).absolutePath
}
