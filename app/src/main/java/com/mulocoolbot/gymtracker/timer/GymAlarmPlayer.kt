package com.mulocoolbot.gymtracker.timer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class GymAlarmPlayer(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val vibrator: Vibrator? = resolveVibrator(appContext)

    private var mediaPlayer: MediaPlayer? = null
    private var toneGenerator: ToneGenerator? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isPlaying = false

    private val alarmAudioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun startAlarm() {
        if (isPlaying) return
        isPlaying = true
        requestAudioFocus()
        startSound()
        startVibration()
    }

    fun playAlarm() {
        startAlarm()
    }

    fun stopAlarm() {
        stopSound()
        stopVibration()
        abandonAudioFocus()
        isPlaying = false
    }

    private fun requestAudioFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(alarmAudioAttributes)
            .setOnAudioFocusChangeListener { }
            .build()
        audioFocusRequest = request
        audioManager.requestAudioFocus(request)
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let { request ->
            audioManager.abandonAudioFocusRequest(request)
        }
        audioFocusRequest = null
    }

    private fun startSound() {
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        if (alarmUri != null) {
            runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(alarmAudioAttributes)
                    setDataSource(appContext, alarmUri)
                    isLooping = true
                    prepare()
                    start()
                }.also { mediaPlayer = it }
            }.onFailure {
                startFallbackTone()
            }
        } else {
            startFallbackTone()
        }
    }

    private fun startFallbackTone() {
        runCatching {
            ToneGenerator(AudioManager.STREAM_ALARM, 100).apply {
                startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 2000)
            }.also { toneGenerator = it }
        }
    }

    private fun stopSound() {
        mediaPlayer?.let { player ->
            runCatching {
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            }
        }
        mediaPlayer = null

        toneGenerator?.let { generator ->
            runCatching {
                generator.stopTone()
                generator.release()
            }
        }
        toneGenerator = null
    }

    private fun startVibration() {
        val activeVibrator = vibrator ?: return
        if (!activeVibrator.hasVibrator()) return
        runCatching {
            val timings = longArrayOf(0L, 400L, 250L, 400L, 250L)
            val effect = VibrationEffect.createWaveform(timings, 0)
            activeVibrator.vibrate(effect)
        }
    }

    private fun stopVibration() {
        runCatching {
            vibrator?.cancel()
        }
    }

    private fun resolveVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}

