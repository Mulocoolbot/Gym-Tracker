package com.mulocoolbot.gymtracker.platform

import kotlin.random.Random
import platform.Foundation.NSTemporaryDirectory

actual fun createTestPreferencesPath(name: String): String {
    val base = NSTemporaryDirectory().trimEnd('/')
    val suffix = Random.nextInt(100_000, 999_999)
    return "$base/${suffix}_$name"
}
