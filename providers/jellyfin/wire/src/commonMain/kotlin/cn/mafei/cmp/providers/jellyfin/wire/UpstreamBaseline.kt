package cn.mafei.cmp.providers.jellyfin.wire

/**
 * Protocol baseline this wire layer is modeled against (mainline-pinned):
 *
 * - upstream: official `jellyfin/jellyfin-sdk-kotlin`
 * - version: v1.6.8
 * - commit: a2f3489642de5a54686627a128cbd8d1a229085b
 * - bundled OpenAPI: Jellyfin 10.10.7
 *
 * Field names in `@SerialName` follow the OpenAPI schema exactly (Jellyfin
 * emits PascalCase JSON, except `ProblemDetails` which stays lowercase).
 * Widening any tolerated decode behavior still requires a real-server
 * fixture per the frozen GLM-01 rules.
 */
data object JellyfinUpstreamBaseline {
    const val SDK_REPO: String = "jellyfin/jellyfin-sdk-kotlin"
    const val SDK_VERSION: String = "v1.6.8"
    const val SDK_COMMIT: String = "a2f3489642de5a54686627a128cbd8d1a229085b"
    const val OPENAPI_JELLYFIN_VERSION: String = "10.10.7"
}
