package cn.mafei.cmp.core.http_contract

/**
 * Minimal percent-encoding used by [HttpUrl]. Pure Kotlin so it behaves
 * identically on every target.
 */
internal object PercentCodec {

    private val HEX = "0123456789ABCDEF".encodeToByteArray()

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
                    out.append(HEX[b ushr 4].toInt().toChar())
                    out.append(HEX[b and 0xF].toInt().toChar())
                }
            }
        }
        return out.toString()
    }

    /** Decodes percent escapes; when [plusAsSpace] is set `+` becomes a space (form/query convention). */
    fun decode(encoded: String, plusAsSpace: Boolean = false): String? {
        if (!encoded.contains('%') && !(plusAsSpace && encoded.contains('+'))) return encoded
        val bytes = ByteArray(encoded.length)
        var count = 0
        var i = 0
        while (i < encoded.length) {
            val c = encoded[i]
            when {
                c == '%' -> {
                    val hi = hexValue(encoded.getOrNull(i + 1)) ?: return null
                    val lo = hexValue(encoded.getOrNull(i + 2)) ?: return null
                    bytes[count++] = ((hi shl 4) or lo).toByte()
                    i += 3
                }
                c == '+' && plusAsSpace -> {
                    bytes[count++] = ' '.code.toByte()
                    i += 1
                }
                else -> {
                    bytes[count++] = c.code.toByte()
                    i += 1
                }
            }
        }
        return bytes.decodeToString(0, count)
    }

    private fun hexValue(c: Char?): Int? {
        if (c == null) return null
        return when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> null
        }
    }
}
