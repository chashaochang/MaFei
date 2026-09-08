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
    suspend fun load(connection: SavedConnection, binding: ProviderSessionBinding, image: MediaImage): ByteArray? {
        val url = HttpUrl.parseOrNull(image.url) ?: return null
        val base = validateSavedBaseUrl(connection.server.baseUrl)
        // Only an explicitly configured server route may receive this account's credentials.
        val route = connection.feiniuRoutes.firstOrNull { sameOrigin(validateSavedBaseUrl(it.baseUrl), url) }
        if (!sameOrigin(base, url) && route == null) return null
        val snapshot = binding.source.current()
        val token = snapshot.secrets[connection.tokenAlias] as? SecureSecret.AuthToken ?: return null
        val target = when (connection.server.provider) {
            ProviderKind.Jellyfin -> url.newBuilder().setQueryParameter("maxWidth", "480")
                .setQueryParameter("quality", "85").build()
            ProviderKind.Feiniu -> url
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
        val response = transport.execute(request.build())
        // An in-flight completion from an invalidated identity cannot populate a new screen.
        val latest = binding.source.current()
        if (latest.identityGeneration != snapshot.identityGeneration || latest.secrets[connection.tokenAlias] == null) {
            throw CancellationException("Artwork session changed")
        }
        return response.bodyBytes.takeIf {
            response.isSuccessful && response.contentType()?.substringBefore(';')?.startsWith("image/") == true && it.isNotEmpty()
        }
    }

    private fun sameOrigin(a: HttpUrl, b: HttpUrl) = a.scheme == b.scheme && a.host == b.host && a.port == b.port
}
