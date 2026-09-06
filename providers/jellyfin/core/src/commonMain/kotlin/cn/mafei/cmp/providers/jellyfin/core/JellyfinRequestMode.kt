package cn.mafei.cmp.providers.jellyfin.core

/**
 * Request authentication mode, tagged by the transport request builders and
 * enforced by the client pipeline:
 *
 * - [ANONYMOUS] — `System/Info/Public`, `Users/Public`,
 *   `Users/AuthenticateByName`. These requests must NEVER carry a session
 *   token, even when the client holds a stale session.
 * - [AUTHENTICATED] — everything else; the token is attached only when a
 *   session exists AND the request targets the same origin as the configured
 *   base URL. Tokens never leave the server's origin.
 */
data object JellyfinRequestMode {
    const val ATTRIBUTE: String = "jellyfin.request-mode"
    const val ANONYMOUS: String = "anonymous"
    const val AUTHENTICATED: String = "authenticated"
}
