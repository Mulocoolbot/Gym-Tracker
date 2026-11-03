package com.mulocoolbot.gymtracker.data.auth

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.mulocoolbot.gymtracker.BuildConfig
import com.mulocoolbot.gymtracker.R
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object EmailOtpSender {

    private const val CHANNEL_ID_OTP = "mfit_otp_channel"
    private const val NOTIFICATION_ID_OTP = 1002

    suspend fun sendOtpEmail(
        context: Context,
        recipientEmail: String,
        otpCode: String
    ) = withContext(Dispatchers.IO) {
        val smtpEmail = BuildConfig.SMTP_EMAIL.trim()
        val smtpPassword = BuildConfig.SMTP_PASSWORD.trim()

        if (smtpEmail.isNotEmpty() && smtpPassword.isNotEmpty()) {
            runCatching {
                dispatchSmtpMessage(
                    senderEmail = smtpEmail,
                    senderPassword = smtpPassword,
                    recipientEmail = recipientEmail.trim(),
                    otpCode = otpCode
                )
            }
        }

        runCatching {
            postOtpNotification(context.applicationContext, recipientEmail.trim(), otpCode)
        }
    }

    private fun dispatchSmtpMessage(
        senderEmail: String,
        senderPassword: String,
        recipientEmail: String,
        otpCode: String
    ) {
        val props = Properties().apply {
            put("mail.smtp.host", "smtp.gmail.com")
            put("mail.smtp.port", "587")
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.connectiontimeout", "8000")
            put("mail.smtp.timeout", "8000")
        }

        val session = Session.getInstance(
            props,
            object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(senderEmail, senderPassword)
                }
            }
        )

        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(senderEmail))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail))
            subject = "MFit Verification Code"
            setText("Your MFit verification code is: $otpCode\nThis code expires in 5 minutes.")
        }

        Transport.send(message)
    }

    private fun postOtpNotification(
        context: Context,
        recipientEmail: String,
        otpCode: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val channel = NotificationChannel(
            CHANNEL_ID_OTP,
            "MFit Verification Codes",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_OTP)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setContentTitle("MFit Verification Code")
            .setContentText("Your verification code for $recipientEmail is $otpCode")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Your verification code for $recipientEmail is $otpCode")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_OTP, notification)
    }
}

