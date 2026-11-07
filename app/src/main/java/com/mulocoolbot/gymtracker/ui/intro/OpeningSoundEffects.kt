package com.mulocoolbot.gymtracker.ui.intro

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object OpeningSoundEffects {

    private const val SAMPLE_RATE_HZ = 22_050
    private const val BOOM_DURATION_MS = 320
    private const val METALLIC_DURATION_MS = 260

    private val soundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun playHeavyBoom(context: Context, intensity: Float = 1.0f) {
        val clampedIntensity = intensity.coerceIn(0.1f, 1.5f)
        triggerHapticOneShot(
            context = context,
            durationMillis = 45L,
            amplitude = 220
        )
        soundScope.launch {
            runCatching {
                val pcmBuffer = synthesizeHeavyBoomPcm(clampedIntensity)
                playStaticPcmBuffer(pcmBuffer, BOOM_DURATION_MS.toLong())
            }
        }
    }

    fun playMetallicLock(context: Context) {
        triggerHapticOneShot(
            context = context,
            durationMillis = 25L,
            amplitude = 180
        )
        soundScope.launch {
            runCatching {
                val pcmBuffer = synthesizeMetallicLockPcm()
                playStaticPcmBuffer(pcmBuffer, METALLIC_DURATION_MS.toLong())
            }
        }
    }

    fun synthesizeHeavyBoomPcm(intensity: Float = 1.0f): ShortArray {
        val clampedIntensity = intensity.coerceIn(0.1f, 1.5f)
        val totalSamples = (SAMPLE_RATE_HZ * BOOM_DURATION_MS) / 1000
        val buffer = ShortArray(totalSamples)
        val startFreq = 115.0
        val endFreq = 28.0
        val freqRatio = endFreq / startFreq
        val random = Random(42)

        var phase = 0.0
        var filteredNoise = 0.0

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE_HZ.toDouble()
            val normalizedTime = i.toDouble() / totalSamples.toDouble()

            val instantFreq = startFreq * freqRatio.pow(normalizedTime)
            phase += (2.0 * PI * instantFreq) / SAMPLE_RATE_HZ.toDouble()

            val subEnvelope = exp(-5.8 * normalizedTime)
            val subWave = sin(phase) * subEnvelope

            val rawNoise = random.nextDouble(-1.0, 1.0)
            filteredNoise = filteredNoise * 0.72 + rawNoise * 0.28
            val crunchEnvelope = exp(-36.0 * t)
            val crunchWave = filteredNoise * crunchEnvelope * 0.55

            val combined = ((subWave * 0.85 + crunchWave) * clampedIntensity).coerceIn(-1.0, 1.0)
            buffer[i] = (combined * Short.MAX_VALUE).toInt().toShort()
        }

        return buffer
    }

    fun synthesizeMetallicLockPcm(): ShortArray {
        val totalSamples = (SAMPLE_RATE_HZ * METALLIC_DURATION_MS) / 1000
        val buffer = ShortArray(totalSamples)
        val frequencies = doubleArrayOf(520.0, 1040.0, 1560.0, 2240.0)
        val weights = doubleArrayOf(0.42, 0.28, 0.18, 0.12)
        val decayRates = doubleArrayOf(11.0, 15.0, 20.0, 26.0)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE_HZ.toDouble()
            val attack = (i.toDouble() / 48.0).coerceAtMost(1.0)
            var sampleValue = 0.0

            for (h in frequencies.indices) {
                val harmonicWave = sin(2.0 * PI * frequencies[h] * t)
                val harmonicDecay = exp(-decayRates[h] * t)
                sampleValue += harmonicWave * weights[h] * harmonicDecay
            }

            val clamped = (sampleValue * attack).coerceIn(-1.0, 1.0)
            buffer[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
        }

        return buffer
    }

    private suspend fun playStaticPcmBuffer(
        pcmBuffer: ShortArray,
        durationMillis: Long
    ) {
        val byteSize = pcmBuffer.size * 2
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE_HZ)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        val track = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(byteSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        try {
            val written = track.write(pcmBuffer, 0, pcmBuffer.size)
            if (written > 0) {
                track.play()
                delay(durationMillis + 40L)
            }
        } finally {
            runCatching {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track.release()
            }
        }
    }

    private fun triggerHapticOneShot(
        context: Context,
        durationMillis: Long,
        amplitude: Int
    ) {
        runCatching {
            val appContext = context.applicationContext ?: context
            val vibrator = resolveVibrator(appContext) ?: return
            if (!vibrator.hasVibrator()) return
            val effect = VibrationEffect.createOneShot(
                durationMillis,
                amplitude.coerceIn(1, 255)
            )
            vibrator.vibrate(effect)
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

