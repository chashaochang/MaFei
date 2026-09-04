package cn.mafei.cmp.core.http_contract

/**
 * Minimal percent-encoding used by [HttpUrl]. Pure Kotlin so it behaves
 * identically on every target.
 */
internal object PercentCodec {

    private const val HEX = "0123456789ABCDEF"

    fun encode(raw: String, keepPlus: Boolean = false): String {
        val bytes = raw.encodeToByteArray()
        val out = StringBuilder(bytes.size + 8)
        for (byte in bytes) {
            val b = byte.toInt() and 0xFF
            val c = b.toChar()
            val unreserved = (c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9') ||
                c == '-' || c == '.' || c == '_' || c == '~'
            when {
                unreserved -> out.append(c)
                c == '+' && keepPlus -> out.append(c)
                else -> {
                    out.append('%')
                    out.append(HEX[b ushr 4])
                    out.append(HEX[b and 0xF])
                }
            }
        }
        return out.toString()
    }

    /**
     * Decodes percent escapes over the UTF-8 byte representation of the input,
     * so mixed input like `"%E4%B8%AD%20文"` round-trips instead of being
     * mangled. When [plusAsSpace] is set, `+` becomes a space (query form
     * convention). Returns `null` for truncated or non-hex `%` escapes.
     */
    fun decode(encoded: String, plusAsSpace: Boolean = false): String? {
        val hasPercent = encoded.indexOf('%') != -1
        val hasPlus = plusAsSpace && encoded.indexOf('+') != -1
        if (!hasPercent && !hasPlus) return encoded

        val bytes = encoded.encodeToByteArray()
        val out = ByteArray(bytes.size)
        var count = 0
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b == '%'.code -> {
                    if (i + 2 > bytes.size - 1) return null
                    val hi = hexValue(bytes[i + 1]) ?: return null
                    val lo = hexValue(bytes[i + 2]) ?: return null
                    out[count++] = ((hi shl 4) or lo).toByte()
                    i += 3
                }
                b == '+'.code && plusAsSpace -> {
                    out[count++] = ' '.code.toByte()
                    i += 1
                }
                else -> {
                    out[count++] = bytes[i]
                    i += 1
                }
            }
        }
        return out.decodeToString(0, count)
    }

    /** `true` when every `%` in [encoded] introduces a valid two-hex-digit escape. */
    fun hasValidPercentEscapes(encoded: String): Boolean {
        var index = encoded.indexOf('%')
        if (index == -1) return true
        while (index != -1) {
            if (index + 2 > encoded.length - 1) return false
            if (hexValue(encoded[index + 1]) == null || hexValue(encoded[index + 2]) == null) return false
            index = encoded.indexOf('%', index + 1)
        }
        return true
    }

    private fun hexValue(b: Byte): Int? = hexValue((b.toInt() and 0xFF).toChar())

    private fun hexValue(c: Char): Int? = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> null
    }
}
