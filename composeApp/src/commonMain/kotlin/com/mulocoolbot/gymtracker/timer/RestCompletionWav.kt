package com.mulocoolbot.gymtracker.timer

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

internal fun buildRestCompletionWavBytes(): ByteArray {
    val sampleRate = 22_050
    val segments = listOf(
        880.0 to 180,
        0.0 to 80,
        880.0 to 180,
        0.0 to 80,
        1174.66 to 340,
        0.0 to 120
    )
    val totalSamples = segments.sumOf { (_, ms) -> (sampleRate * ms) / 1000 }
    val dataSize = totalSamples * 2
    val bytes = ByteArray(44 + dataSize)

    writeAscii(bytes, 0, "RIFF")
    writeIntLE(bytes, 4, 36 + dataSize)
    writeAscii(bytes, 8, "WAVE")
    writeAscii(bytes, 12, "fmt ")
    writeIntLE(bytes, 16, 16)
    writeShortLE(bytes, 20, 1)
    writeShortLE(bytes, 22, 1)
    writeIntLE(bytes, 24, sampleRate)
    writeIntLE(bytes, 28, sampleRate * 2)
    writeShortLE(bytes, 32, 2)
    writeShortLE(bytes, 34, 16)
    writeAscii(bytes, 36, "data")
    writeIntLE(bytes, 40, dataSize)

    var sampleOffset = 0
    val fadeSamples = (sampleRate * 12) / 1000
    for ((freqHz, durationMs) in segments) {
        val count = (sampleRate * durationMs) / 1000
        for (i in 0 until count) {
            val sampleValue = if (freqHz <= 0.0) {
                0
            } else {
                val attack = if (i < fadeSamples) i.toDouble() / fadeSamples else 1.0
                val release = if (i > count - fadeSamples) (count - i).toDouble() / fadeSamples else 1.0
                val envelope = minOf(attack, release).coerceIn(0.0, 1.0)
                val wave = sin(2.0 * PI * freqHz * i.toDouble() / sampleRate.toDouble())
                (wave * envelope * 0.45 * 32767.0).roundToInt().coerceIn(-32767, 32767)
            }
            val byteIndex = 44 + (sampleOffset + i) * 2
            bytes[byteIndex] = (sampleValue and 0xFF).toByte()
            bytes[byteIndex + 1] = ((sampleValue shr 8) and 0xFF).toByte()
        }
        sampleOffset += count
    }
    return bytes
}

private fun writeAscii(dest: ByteArray, offset: Int, text: String) {
    for (i in text.indices) {
        dest[offset + i] = text[i].code.toByte()
    }
}

private fun writeShortLE(dest: ByteArray, offset: Int, value: Int) {
    dest[offset] = (value and 0xFF).toByte()
    dest[offset + 1] = ((value shr 8) and 0xFF).toByte()
}

private fun writeIntLE(dest: ByteArray, offset: Int, value: Int) {
    dest[offset] = (value and 0xFF).toByte()
    dest[offset + 1] = ((value shr 8) and 0xFF).toByte()
    dest[offset + 2] = ((value shr 16) and 0xFF).toByte()
    dest[offset + 3] = ((value shr 24) and 0xFF).toByte()
}
