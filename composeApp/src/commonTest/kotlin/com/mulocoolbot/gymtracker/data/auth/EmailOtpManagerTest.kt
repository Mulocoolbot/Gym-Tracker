package com.mulocoolbot.gymtracker.data.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmailOtpManagerTest {

    @Test
    fun invalidEmails_areRejected() {
        val otpManager = EmailOtpManager()
        listOf("", "plainaddress", "user@domain", "user@domain.c").forEach { invalid ->
            val (result, code) = otpManager.generateOtp(invalid, currentMillis = 1_000_000L)
            assertEquals(OtpRequestResult.INVALID_EMAIL, result)
            assertNull(code)
        }
    }

    @Test
    fun validEmail_generatesSixDigitsAndEnforcesCooldown() {
        val otpManager = EmailOtpManager()
        val (firstResult, firstCode) = otpManager.generateOtp("alex@gmail.com", currentMillis = 1_000_000L)
        assertEquals(OtpRequestResult.SENT, firstResult)
        assertNotNull(firstCode)
        assertEquals(6, firstCode.length)
        assertTrue(firstCode.all { it.isDigit() })

        val (cooldownResult, cooldownCode) = otpManager.generateOtp("alex@gmail.com", currentMillis = 1_059_999L)
        assertEquals(OtpRequestResult.COOLDOWN_ACTIVE, cooldownResult)
        assertNull(cooldownCode)

        val (afterCooldown, secondCode) = otpManager.generateOtp("alex@gmail.com", currentMillis = 1_060_000L)
        assertEquals(OtpRequestResult.SENT, afterCooldown)
        assertNotNull(secondCode)
    }

    @Test
    fun verifyOtp_checksFormatExpiryAndSingleUseConsumption() {
        val otpManager = EmailOtpManager()
        val (_, firstCode) = otpManager.generateOtp("alex@gmail.com", currentMillis = 1_000_000L)
        assertNotNull(firstCode)

        assertEquals(OtpVerifyResult.INVALID_FORMAT, otpManager.verifyOtp("alex@gmail.com", "12345", 1_010_000L))
        assertEquals(OtpVerifyResult.INVALID_FORMAT, otpManager.verifyOtp("alex@gmail.com", "12345A", 1_010_000L))

        val wrong = if (firstCode == "999999") "111111" else "999999"
        assertEquals(OtpVerifyResult.INVALID_CODE, otpManager.verifyOtp("alex@gmail.com", wrong, 1_010_000L))
        assertEquals(OtpVerifyResult.EXPIRED_CODE, otpManager.verifyOtp("alex@gmail.com", firstCode, 1_300_001L))

        val (_, secondCode) = otpManager.generateOtp("alex@gmail.com", currentMillis = 2_000_000L)
        assertNotNull(secondCode)
        assertEquals(OtpVerifyResult.SUCCESS, otpManager.verifyOtp("alex@gmail.com", secondCode, 2_300_000L))
        assertEquals(OtpVerifyResult.NO_PENDING_OTP, otpManager.verifyOtp("alex@gmail.com", secondCode, 2_300_000L))
    }
}
