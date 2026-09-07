package cn.mafei.cmp.providers.feiniu.core

/**
 * Frozen Feiniu (fnOS / Trim media) protocol identity extracted from the
 * legacy reference implementation. These constants are part of the wire
 * contract — servers validate them; changing any value breaks logins.
 */
data object FeiniuIdentity {
    /** Shared `Authx`/`fn-sign` API key (also the authx sign prefix). */
    const val AUTHX_API_KEY: String = "zIGtkc3dqZnJpd29qZXJqa2w7c"

    /** Authx sign prefix for the media API. */
    const val AUTHX_PREFIX: String = "NDzZTVxnRKP8Z0jXg1VAMonaG8akvh"

    /** Media API authx secret suffix. */
    const val MEDIA_API_SECRET: String = "16CCEB3D-AB42-077D-36A1-F355324E4237"

    const val USER_AGENT: String = "1.32.2 (com.trim.app; build:1322007; Android 36) okHttp/4.9.0"
    const val CLIENT: String = "android-media"
    const val CLIENT_VERSION: String = "1322007"

    /** Connect (FNID resolution) endpoint. */
    const val FN_CONNECT_URL: String = "https://5ddd.com/api/v1/fn/con"
    const val FN_CONNECT_PATH: String = "/api/v1/fn/con"

    const val DEFAULT_HTTP_PORT: Int = 5666
    const val DEFAULT_HTTPS_PORT: Int = 5667

    /** Reachability probe path; 2xx-4xx counts as reachable. */
    const val PROBE_PATH: String = "/license/v1/device/baseInfo"
    const val PROBE_CONNECT_TIMEOUT_MS: Long = 1200
    const val PROBE_READ_TIMEOUT_MS: Long = 1800

    const val MAX_REDIRECTS: Int = 4

    /** Session cookie carrying the media access token. */
    const val TOKEN_COOKIE: String = "Trim-MC-token"

    /** Cookie marking a relay-routed session. */
    const val RELAY_COOKIE: String = "mode=relay"

    const val LOGIN_APP_NAME: String = "trimemedia-web"
}
