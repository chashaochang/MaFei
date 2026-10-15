package cn.mafei.cmp.providers.jellyfin.core

/**
 * Formats the Jellyfin `Authorization: MediaBrowser ...` header.
 *
 * Official frozen order: `Client`, `Version`, `DeviceId`, `Device`, and —
 * once authenticated — `Token`:
 * `MediaBrowser Client="c", Version="v", DeviceId="id", Device="d", Token="t"`.
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
        append("Version=").append(escape(appVersion)).append(", ")
        append("DeviceId=").append(escape(deviceId)).append(", ")
        append("Device=").append(escape(deviceName))
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
