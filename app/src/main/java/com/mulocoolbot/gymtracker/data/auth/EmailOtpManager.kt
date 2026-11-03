package com.mulocoolbot.gymtracker.data.auth

import java.util.Locale

enum class OtpRequestResult {
    SENT,
    INVALID_EMAIL,
    COOLDOWN_ACTIVE
}

enum class OtpVerifyResult {
    SUCCESS,
    INVALID_FORMAT,
    INVALID_CODE,
    EXPIRED_CODE,
    NO_PENDING_OTP
}

class EmailOtpManager {

    var targetEmail: String = ""
        private set

    var code: String = ""
        private set

    var expiresAtMillis: Long = 0L
        private set

    var resendAvailableAtMillis: Long = 0L
        private set

    fun isValidEmail(email: String): Boolean {
        val trimmed = email.trim()
        return EMAIL_REGEX.matches(trimmed)
    }

    fun generateOtp(
        email: String,
        currentMillis: Long = System.currentTimeMillis(),
        forceResend: Boolean = false
    ): Pair<OtpRequestResult, String?> {
        val normalizedEmail = email.trim().lowercase(Locale.US)
        if (!isValidEmail(normalizedEmail)) {
            return OtpRequestResult.INVALID_EMAIL to null
        }

        if (!forceResend && targetEmail == normalizedEmail && currentMillis < resendAvailableAtMillis) {
            return OtpRequestResult.COOLDOWN_ACTIVE to null
        }

        val generatedCode = (100000..999999).random().toString()
        targetEmail = normalizedEmail
        code = generatedCode
        expiresAtMillis = currentMillis + OTP_EXPIRY_MILLIS
        resendAvailableAtMillis = currentMillis + RESEND_COOLDOWN_MILLIS
        return OtpRequestResult.SENT to generatedCode
    }

    fun verifyOtp(
        email: String,
        inputCode: String,
        currentMillis: Long = System.currentTimeMillis()
    ): OtpVerifyResult {
        val normalizedEmail = email.trim().lowercase(Locale.US)
        if (targetEmail.isEmpty() || code.isEmpty() || targetEmail != normalizedEmail) {
            return OtpVerifyResult.NO_PENDING_OTP
        }

        val trimmedCode = inputCode.trim()
        if (trimmedCode.length != 6 || !trimmedCode.all { it.isDigit() }) {
            return OtpVerifyResult.INVALID_FORMAT
        }

        if (currentMillis > expiresAtMillis) {
            return OtpVerifyResult.EXPIRED_CODE
        }

        if (trimmedCode != code) {
            return OtpVerifyResult.INVALID_CODE
        }

        clear()
        return OtpVerifyResult.SUCCESS
    }

    fun remainingCooldownSeconds(currentMillis: Long = System.currentTimeMillis()): Int {
        val remainingMillis = (resendAvailableAtMillis - currentMillis).coerceAtLeast(0L)
        return ((remainingMillis + 999L) / 1_000L).toInt()
    }

    fun clear() {
        targetEmail = ""
        code = ""
        expiresAtMillis = 0L
        resendAvailableAtMillis = 0L
    }

    companion object {
        const val OTP_EXPIRY_MILLIS = 300_000L
        const val RESEND_COOLDOWN_MILLIS = 60_000L
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}

