package cn.mafei.cmp.core.serialization

/**
 * Normalizes a UUID-shaped string to the canonical lowercase
 * `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx` form, tolerating braces and the
 * `urn:uuid:` prefix. Returns `null` when the input is not a valid UUID.
 */
fun normalizeUuidOrNull(raw: String?): String? {
    if (raw == null) return null
    var text = raw.trim().lowercase()
    if (text.startsWith("{") && text.endsWith("}") && text.length >= 2) {
        text = text.substring(1, text.length - 1)
    }
    if (text.startsWith("urn:uuid:")) {
        text = text.substring("urn:uuid:".length)
    }
    if (text.length != 36) return null
    for (index in 0 until 36) {
        val c = text[index]
        when (index) {
            8, 13, 18, 23 -> if (c != '-') return null
            else -> if (!c.isHexDigit()) return null
        }
    }
    return text
}

private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
