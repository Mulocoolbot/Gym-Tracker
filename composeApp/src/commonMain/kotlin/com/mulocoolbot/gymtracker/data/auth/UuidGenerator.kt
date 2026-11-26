package com.mulocoolbot.gymtracker.data.auth

import kotlin.random.Random

internal object UuidGenerator {

    private val md5Shifts = intArrayOf(
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21
    )

    private val md5K = intArrayOf(
        0xd76aa478.toInt(), 0xe8c7b756.toInt(), 0x242070db, 0xc1bdceee.toInt(),
        0xf57c0faf.toInt(), 0x4787c62a, 0xa8304613.toInt(), 0xfd469501.toInt(),
        0x698098d8, 0x8b44f7af.toInt(), 0xffff5bb1.toInt(), 0x895cd7be.toInt(),
        0x6b901122, 0xfd987193.toInt(), 0xa679438e.toInt(), 0x49b40821,
        0xf61e2562.toInt(), 0xc040b340.toInt(), 0x265e5a51, 0xe9b6c7aa.toInt(),
        0xd62f105d.toInt(), 0x02441453, 0xd8a1e681.toInt(), 0xe7d3fbc8.toInt(),
        0x21e1cde6, 0xc33707d6.toInt(), 0xf4d50d87.toInt(), 0x455a14ed,
        0xa9e3e905.toInt(), 0xfcefa3f8.toInt(), 0x676f02d9, 0x8d2a4c8a.toInt(),
        0xfffa3942.toInt(), 0x8771f681.toInt(), 0x6d9d6122, 0xfde5380c.toInt(),
        0xa4beea44.toInt(), 0x4bdecfa9, 0xf6bb4b60.toInt(), 0xbebfbc70.toInt(),
        0x289b7ec6, 0xeaa127fa.toInt(), 0xd4ef3085.toInt(), 0x04881d05,
        0xd9d4d039.toInt(), 0xe6db99e5.toInt(), 0x1fa27cf8, 0xc4ac5665.toInt(),
        0xf4292244.toInt(), 0x432aff97, 0xab9423a7.toInt(), 0xfc93a039.toInt(),
        0x655b59c3, 0x8f0ccc92.toInt(), 0xffeff47d.toInt(), 0x85845dd1.toInt(),
        0x6fa87e4f, 0xfe2ce6e0.toInt(), 0xa3014314.toInt(), 0x4e0811a1,
        0xf7537e82.toInt(), 0xbd3af235.toInt(), 0x2ad7d2bb, 0xeb86d391.toInt()
    )

    fun randomUuidV4(): String {
        val bytes = Random.nextBytes(16)
        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
        return formatUuid(bytes)
    }

    fun nameUuidV3FromBytes(name: ByteArray): String {
        val digest = md5(name)
        digest[6] = ((digest[6].toInt() and 0x0F) or 0x30).toByte()
        digest[8] = ((digest[8].toInt() and 0x3F) or 0x80).toByte()
        return formatUuid(digest)
    }

    private fun formatUuid(bytes: ByteArray): String {
        val hex = CharArray(36)
        var out = 0
        for (i in 0 until 16) {
            if (i == 4 || i == 6 || i == 8 || i == 10) {
                hex[out++] = '-'
            }
            val v = bytes[i].toInt() and 0xFF
            hex[out++] = HEX_DIGITS[v ushr 4]
            hex[out++] = HEX_DIGITS[v and 0x0F]
        }
        return hex.concatToString()
    }

    private fun md5(input: ByteArray): ByteArray {
        val bitLen = input.size.toLong() * 8L
        val paddedLen = (((input.size + 8) / 64) + 1) * 64
        val padded = ByteArray(paddedLen)
        input.copyInto(padded)
        padded[input.size] = 0x80.toByte()
        for (i in 0 until 8) {
            padded[paddedLen - 8 + i] = ((bitLen ushr (8 * i)) and 0xFFL).toByte()
        }

        var a0 = 0x67452301
        var b0 = 0xefcdab89.toInt()
        var c0 = 0x98badcfe.toInt()
        var d0 = 0x10325476

        val m = IntArray(16)
        var offset = 0
        while (offset < paddedLen) {
            for (j in 0 until 16) {
                val idx = offset + j * 4
                m[j] = (padded[idx].toInt() and 0xFF) or
                    ((padded[idx + 1].toInt() and 0xFF) shl 8) or
                    ((padded[idx + 2].toInt() and 0xFF) shl 16) or
                    ((padded[idx + 3].toInt() and 0xFF) shl 24)
            }

            var a = a0
            var b = b0
            var c = c0
            var d = d0

            for (i in 0 until 64) {
                val f: Int
                val g: Int
                when {
                    i < 16 -> {
                        f = (b and c) or (b.inv() and d)
                        g = i
                    }
                    i < 32 -> {
                        f = (d and b) or (d.inv() and c)
                        g = (5 * i + 1) % 16
                    }
                    i < 48 -> {
                        f = b xor c xor d
                        g = (3 * i + 5) % 16
                    }
                    else -> {
                        f = c xor (b or d.inv())
                        g = (7 * i) % 16
                    }
                }
                val temp = d
                d = c
                c = b
                b += (a + f + md5K[i] + m[g]).rotateLeft(md5Shifts[i])
                a = temp
            }

            a0 += a
            b0 += b
            c0 += c
            d0 += d
            offset += 64
        }

        val out = ByteArray(16)
        intToLeBytes(a0, out, 0)
        intToLeBytes(b0, out, 4)
        intToLeBytes(c0, out, 8)
        intToLeBytes(d0, out, 12)
        return out
    }

    private fun intToLeBytes(value: Int, dest: ByteArray, offset: Int) {
        dest[offset] = (value and 0xFF).toByte()
        dest[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        dest[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        dest[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    private val HEX_DIGITS = "0123456789abcdef".toCharArray()
}
