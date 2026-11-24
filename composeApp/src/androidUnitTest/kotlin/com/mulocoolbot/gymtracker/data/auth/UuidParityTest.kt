package com.mulocoolbot.gymtracker.data.auth

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class UuidParityTest {

    @Test
    fun computeUidForEmail_matchesJavaUuidNameUuidFromBytesExactly() {
        val samples = listOf(
            "alex@example.com",
            "  Alex@Example.COM ",
            "athlete.mfit+01@gmail.com",
            "a@b.co"
        )
        samples.forEach { email ->
            val expected = UUID.nameUUIDFromBytes(email.trim().lowercase().toByteArray()).toString()
            val actual = SessionPreferences.computeUidForEmail(email)
            assertEquals(expected, actual)
        }
    }
}
