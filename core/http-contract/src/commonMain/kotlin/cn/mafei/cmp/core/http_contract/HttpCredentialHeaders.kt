package cn.mafei.cmp.core.http_contract

/**
 * Canonical set of headers that carry credential material across the
 * Jellyfin and FNOS (Feiniu) protocols.
 *
 * Single source of truth for two consumers:
 * - [HttpRedirects] strips every header in this set when a redirect crosses
 *   an origin — credentials never follow to another scheme/host/port;
 * - [DefaultHttpLogRedactor] masks every header in this set before logging.
 *
 * GLM-03 may extend the set for provider-specific credentials; extending it
 * automatically tightens redirect stripping and log masking everywhere.
 */
data object HttpCredentialHeaders {

    /** Lower-case credential header names. Treat as immutable. */
    val ALL: Set<String> = setOf(
        HttpHeaders.AUTHORIZATION.lowercase(),
        "proxy-authorization",
        HttpHeaders.COOKIE.lowercase(),
        "x-emby-authorization", // Jellyfin client/device auth
        "x-emby-token", // Jellyfin access token
        "x-auth-token",
        "x-access-token",
        "trim-mc-token", // FNOS media token
    )

    fun isCredentialHeader(name: String): Boolean = name.lowercase() in ALL
}
