package cn.mafei.cmp.session

import cn.mafei.cmp.core.http_contract.HttpUrl
import kotlinx.serialization.json.*

/** Transient route-owned browser input. Credentials/scripts never become navigation arguments or settings. */
class WebPageRequest(
    val url: String,
    val title: String,
    val bootstrapScript: String = "",
    val deviceInformation: String = "{}",
) {
    val origin: String = HttpUrl.parse(url).let { base ->
        "${base.scheme}://${if (base.host.contains(':')) "[${base.host}]" else base.host}" +
            if (base.port == if (base.scheme == "https") 443 else 80) "" else ":${base.port}"
    }
    fun accepts(candidate: String): Boolean {
        val target = HttpUrl.parseOrNull(candidate) ?: return false
        val base = HttpUrl.parse(url)
        return base.scheme == target.scheme && base.host == target.host && base.port == target.port
    }
    override fun toString() = "WebPageRequest(title=$title, content=<redacted>)"
    fun atUrl(next: String): WebPageRequest {
        require(accepts(next))
        return WebPageRequest(next, title, bootstrapScript, deviceInformation)
    }

    /** Android's origin-filtered message channel supplies the same old NativeShell surface. */
    fun messageBridgeScript(channel: String): String {
        require(channel.matches(Regex("[A-Za-z][A-Za-z0-9_]*")))
        return """
            (() => {
              if (window.top !== window || location.origin !== ${JsonPrimitive(origin)}) return;
              const send = (action, value = '') => window.$channel.postMessage(JSON.stringify({action, value: String(value)}));
              window.NativeInterface = {
                getDeviceInformation: () => JSON.stringify(${deviceInformation}),
                openUrl: value => send('openUrl', value), openClientSettings: () => send('openClientSettings'),
                openServerSelection: () => send('openServerSelection'), exitApp: () => send('exitApp'),
                enableFullscreen: () => send('enableFullscreen'), disableFullscreen: () => send('disableFullscreen')
              };
              window.NativePlayer = {
                isEnabled: () => true, loadPlayer: value => send('loadPlayer', value),
                pausePlayer: () => send('pausePlayer'), resumePlayer: () => send('resumePlayer'),
                stopPlayer: () => send('stopPlayer'), destroyPlayer: () => send('destroyPlayer'),
                seek: value => send('seek', value), seekMs: value => send('seekMs', value),
                setVolume: value => send('setVolume', value)
              };
            })();
        """.trimIndent()
    }

    companion object {
        fun jellyfin(baseUrl: String, serverId: String, serverName: String, userId: String, token: String,
            user: JsonObject, deviceId: String, nowMs: Long): WebPageRequest {
            val webServerAddress = baseUrl.trimEnd('/')
            val credentials = buildJsonObject {
                put("Servers", buildJsonArray { add(buildJsonObject {
                    put("AccessToken", token); put("DateLastAccessed", nowMs); put("ExchangeToken", JsonNull)
                    put("Id", serverId); put("LastConnectionMode", 2); put("LocalAddress", webServerAddress)
                    put("ManualAddress", webServerAddress); put("Name", serverName); put("UserId", userId); put("manualAddressOnly", true)
                }) })
            }
            val key = "user-$userId-$serverId"
            val origin = WebPageRequest(baseUrl, "WebITong").origin
            val script = """
                (() => {
                  if (window.top !== window || location.origin !== ${JsonPrimitive(origin)}) return;
                  try {
                    const previous = JSON.parse(localStorage.getItem('jellyfin_credentials') || '{"Servers":[]}');
                    for (const server of (previous.Servers || [])) {
                      if (server.UserId && server.Id) localStorage.removeItem('user-' + server.UserId + '-' + server.Id);
                    }
                    localStorage.setItem('jellyfin_credentials', JSON.stringify($credentials));
                    localStorage.setItem(${JsonPrimitive(key)}, JSON.stringify($user));
                  } catch (_) {}
                })();
            """.trimIndent()
            val device = buildJsonObject { put("deviceId", deviceId); put("deviceName", "MaFei")
                put("appName", "MaFei"); put("appVersion", "2.0.0") }
            return WebPageRequest(webServerAddress, "WebITong", script, device.toString())
        }
    }
}
