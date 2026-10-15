package cn.mafei.cmp.providers.feiniu.assets

import cn.mafei.cmp.providers.feiniu.core.FeiniuCredentials

/**
 * Feiniu asset URL surface: posters/images, subtitle downloads and the
 * playback headers a player must attach (Authorization + Cookie, lower-case
 * cookie key included for player compatibility, plus `mode=relay` on relay
 * routes).
 */
class FeiniuAssetsApi {

    /** Absolute image URL (`/v/api/v1/sys/img{path}?w={width}`). */
    fun imageUrl(baseUrl: String, imagePath: String, width: Int = 400): String {
        val trimmed = imagePath.trim()
        if (trimmed.isEmpty()) return ""
        val base = baseUrl.trimEnd('/')
        return "$base/v/api/v1/sys/img$trimmed?w=$width"
    }

    /** Absolute subtitle download URL. */
    fun subtitleDownloadUrl(baseUrl: String, subtitleGuid: String): String {
        val guid = subtitleGuid.trim()
        if (guid.isEmpty()) return ""
        return "${baseUrl.trimEnd('/')}/v/api/v1/subtitle/dl/$guid"
    }

    /**
     * Headers the player must send when fetching stream/asset URLs.
     * Tokens travel via Authorization and the mirrored Trim-MC-token cookie.
     */
    fun playbackHeaders(credentials: FeiniuCredentials, relayRoute: Boolean): Map<String, String> {
        if (!credentials.hasToken) return emptyMap()
        val headers = linkedMapOf(
            "Authorization" to credentials.token,
            "Cookie" to FeiniuCookieJar_mergeToken(credentials.cookie, credentials.token),
        )
        if (relayRoute) {
            headers["Cookie"] = FeiniuCookieJar_merge(headers["Cookie"].orEmpty(), "mode=relay")
        }
        // legacy players read the lowercase key
        headers["cookie"] = headers["Cookie"]!!
        return headers
    }
}

private fun FeiniuCookieJar_mergeToken(cookie: String, token: String): String =
    cn.mafei.cmp.providers.feiniu.core.FeiniuCookieJar.mergeToken(cookie, token)

private fun FeiniuCookieJar_merge(cookie: String, next: String): String =
    cn.mafei.cmp.providers.feiniu.core.FeiniuCookieJar.merge(cookie, next)
