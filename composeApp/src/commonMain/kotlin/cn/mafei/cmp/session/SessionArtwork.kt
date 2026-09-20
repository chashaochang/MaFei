package cn.mafei.cmp.session

import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.http_contract.*
import cn.mafei.cmp.core.secure_credential_api.SecureSecret
import cn.mafei.cmp.providers.feiniu.assets.FeiniuAssetsApi
import cn.mafei.cmp.providers.feiniu.core.FeiniuCredentials
import kotlinx.coroutines.CancellationException

/** Runtime-only artwork requests. Credentials never enter image URLs or a disk cache. */
internal class SessionArtwork(private val transport: HttpTransport) {
    suspend fun load(
        connection: SavedConnection,
        binding: ProviderSessionBinding,
        image: MediaImage,
        targetWidthPx: Int = 480,
    ): ByteArray? {
        val url = HttpUrl.parseOrNull(image.url) ?: return null
        val base = validateSavedBaseUrl(connection.server.baseUrl)
        // Only an explicitly configured server route may receive this account's credentials.
        val route = connection.feiniuRoutes.firstOrNull { sameOrigin(validateSavedBaseUrl(it.baseUrl), url) }
        if (!sameOrigin(base, url) && route == null) {
            return null
        }
        val snapshot = binding.source.current()
        val token = snapshot.secrets[connection.tokenAlias] as? SecureSecret.AuthToken
        if (token == null) {
            return null
        }
        val target = when (connection.server.provider) {
            ProviderKind.Jellyfin -> url.newBuilder()
                .setQueryParameter("maxWidth", targetWidthPx.coerceIn(96, 960).toString())
                .setQueryParameter("quality", "85").build()
            ProviderKind.Feiniu -> url.newBuilder()
                .setQueryParameter("w", targetWidthPx.coerceIn(96, 960).toString())
                .build()
        }
        val request = HttpRequest.builder(HttpMethod.Get, target)
            .redirectPolicy(RedirectPolicy.FollowSameOrigin)
            .readPolicy(BodyReadPolicy.BufferedAtMost(4L * 1024 * 1024))
            .timeoutMillis(12_000)
        when (connection.server.provider) {
            ProviderKind.Jellyfin -> request.header("X-Emby-Token", token.token)
            ProviderKind.Feiniu -> {
                val cookie = snapshot.secrets[connection.cookieAlias] as? SecureSecret.CookieText
                FeiniuAssetsApi().playbackHeaders(FeiniuCredentials(token.token, cookie?.value.orEmpty()), route?.route == "Relay")
                    .filterKeys { it != "cookie" }.forEach { (name, value) -> request.header(name, value) }
            }
        }
        val response = try {
            transport.execute(request.build())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            throw failure
        }
        // An in-flight completion from an invalidated identity cannot populate a new screen.
        val latest = binding.source.current()
        if (latest.identityGeneration != snapshot.identityGeneration || latest.secrets[connection.tokenAlias] == null) {
            throw CancellationException("Artwork session changed")
        }
        val mediaType = response.contentType()?.substringBefore(';')?.trim()?.lowercase()
        if (!response.isSuccessful || response.bodyBytes.isEmpty() ||
            (mediaType != null && !mediaType.startsWith("image/") && mediaType != "application/octet-stream")
        ) {
            return null
        }
        return response.bodyBytes.takeIf {
            response.isSuccessful && it.isNotEmpty() &&
                (mediaType == null || mediaType.startsWith("image/") || mediaType == "application/octet-stream")
        }
    }

    private fun sameOrigin(a: HttpUrl, b: HttpUrl) = a.scheme == b.scheme && a.host == b.host && a.port == b.port
}
