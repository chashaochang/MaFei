package cn.mafei.cmp.providers.feiniu.core

/**
 * Pure-Kotlin MD5 (RFC 1321) and SHA-256 (FIPS 180-4) for the Feiniu
 * signing schemes. Verified against the standard test vectors in commonTest —
 * the wire formulas are frozen, so a wrong digest silently breaks auth.
 */
data object FeiniuDigest {

    fun md5(value: String): String = md5Bytes(value.encodeToByteArray()).toHexLittleEndianWords()

    fun sha256(value: String): String = sha256Bytes(value.encodeToByteArray()).toHexBigEndianWords()

    // ------------------------------------------------------------------ MD5

    private val MD5_S = intArrayOf(
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
    )

    private val MD5_K = IntArray(64) { index ->
        ((1L shl 32) * kotlin.math.abs(kotlin.math.sin((index + 1).toDouble()))).toLong().toInt()
    }

    fun md5Bytes(message: ByteArray): IntArray {
        var a0 = 0x67452301
        var b0 = 0xefcdab89.toInt()
        var c0 = 0x98badcfe.toInt()
        var d0 = 0x10325476

        val bitLength = message.size.toLong() * 8
        val paddedLength = ((message.size + 8) / 64 + 1) * 64
        val padded = ByteArray(paddedLength)
        message.copyInto(padded)
        padded[message.size] = 0x80.toByte()
        for (i in 0 until 8) {
            padded[paddedLength - 8 + i] = (bitLength ushr (8 * i)).toByte()
        }

        val m = IntArray(16)
        var chunk = 0
        while (chunk < paddedLength) {
            for (i in 0 until 16) {
                val o = chunk + i * 4
                m[i] = (padded[o].toInt() and 0xFF) or
                    ((padded[o + 1].toInt() and 0xFF) shl 8) or
                    ((padded[o + 2].toInt() and 0xFF) shl 16) or
                    ((padded[o + 3].toInt() and 0xFF) shl 24)
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
                        f = (b xor c) xor d
                        g = (3 * i + 5) % 16
                    }
                    else -> {
                        f = c xor (b or d.inv())
                        g = (7 * i) % 16
                    }
                }
                val temp = a + f + MD5_K[i] + m[g]  // Int arithmetic
                val newB = b + temp.rotateLeft(MD5_S[i])
                a = d
                d = c
                c = b
                b = newB
            }
            a0 += a
            b0 += b
            c0 += c
            d0 += d
            chunk += 64
        }
        return intArrayOf(a0, b0, c0, d0)
    }

    // --------------------------------------------------------------- SHA-256

    private val SHA256_K = intArrayOf(
        0x428a2f98, 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
        0x3956c25b, 0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
        0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3,
        0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
        0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc,
        0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(),
        0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
        0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(),
        0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(),
        0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
        0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(),
        0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt(),
    )

    fun sha256Bytes(message: ByteArray): IntArray {
        var h0 = 0x6a09e667
        var h1 = 0xbb67ae85.toInt()
        var h2 = 0x3c6ef372
        var h3 = 0xa54ff53a.toInt()
        var h4 = 0x510e527f
        var h5 = 0x9b05688c.toInt()
        var h6 = 0x1f83d9ab
        var h7 = 0x5be0cd19

        val bitLength = message.size.toLong() * 8
        val paddedLength = ((message.size + 8) / 64 + 1) * 64
        val padded = ByteArray(paddedLength)
        message.copyInto(padded)
        padded[message.size] = 0x80.toByte()
        for (i in 0 until 8) {
            padded[paddedLength - 8 + i] = (bitLength ushr (8 * (7 - i))).toByte()
        }

        val w = IntArray(64)
        var chunk = 0
        while (chunk < paddedLength) {
            for (i in 0 until 16) {
                val o = chunk + i * 4
                w[i] = (padded[o].toInt() and 0xFF) shl 24 or
                    ((padded[o + 1].toInt() and 0xFF) shl 16) or
                    ((padded[o + 2].toInt() and 0xFF) shl 8) or
                    (padded[o + 3].toInt() and 0xFF)
            }
            for (i in 16 until 64) {
                val s0 = w[i - 15].rotateRight(7) xor w[i - 15].rotateRight(18) xor (w[i - 15] ushr 3)
                val s1 = w[i - 2].rotateRight(17) xor w[i - 2].rotateRight(19) xor (w[i - 2] ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }
            var a = h0
            var b = h1
            var c = h2
            var d = h3
            var e = h4
            var f = h5
            var g = h6
            var h = h7
            for (i in 0 until 64) {
                val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val ch = (e and f) xor (e.inv() and g)
                val temp1 = h + s1 + ch + SHA256_K[i] + w[i]
                val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val temp2 = s0 + maj
                h = g
                g = f
                f = e
                e = d + temp1
                d = c
                c = b
                b = a
                a = temp1 + temp2
            }
            h0 += a
            h1 += b
            h2 += c
            h3 += d
            h4 += e
            h5 += f
            h6 += g
            h7 += h
            chunk += 64
        }
        return intArrayOf(h0, h1, h2, h3, h4, h5, h6, h7)
    }

    /** MD5 prints each 32-bit word least-significant byte first (RFC 1321 output convention). */
    private fun IntArray.toHexLittleEndianWords(): String = buildString(size * 8) {
        for (word in this@toHexLittleEndianWords) {
            for (byteIndex in 0 until 4) {
                val byte = (word ushr (8 * byteIndex)) and 0xFF
                append("0123456789abcdef"[(byte ushr 4) and 0xF])
                append("0123456789abcdef"[byte and 0xF])
            }
        }
    }

    /** SHA-256 prints words most-significant byte first (FIPS 180-4 output convention). */
    private fun IntArray.toHexBigEndianWords(): String = buildString(size * 8) {
        for (word in this@toHexBigEndianWords) {
            for (shift in 28 downTo 0 step 4) {
                append("0123456789abcdef"[(word ushr shift) and 0xF])
            }
        }
    }
}
