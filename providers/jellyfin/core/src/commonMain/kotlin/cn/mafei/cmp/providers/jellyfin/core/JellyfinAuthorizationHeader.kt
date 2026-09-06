package cn.mafei.cmp.providers.jellyfin.core

/**
 * Formats the Jellyfin `Authorization: MediaBrowser ...` header.
 *
 * Format (frozen protocol shape):
 * `MediaBrowser Client="c", Device="d", DeviceId="id", Version="v"` and,
 * once authenticated, `, Token="t"`.
 */
data object JellyfinAuthorizationHeader {

    fun format(
        clientName: String,
        deviceName: String,
        deviceId: String,
        appVersion: String,
        token: String? = null,
    ): String = buildString {
        append("MediaBrowser ")
        append("Client=").append(escape(clientName)).append(", ")
        append("Device=").append(escape(deviceName)).append(", ")
        append("DeviceId=").append(escape(deviceId)).append(", ")
        append("Version=").append(escape(appVersion))
        if (!token.isNullOrBlank()) {
            append(", Token=").append(escape(token))
        }
    }

    /** Jellyfin's own SDK escapes backslashes first, then quotes. */
    private fun escape(value: String): String {
        val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
        return "\"$escaped\""
    }
}
