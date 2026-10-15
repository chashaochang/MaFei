package cn.mafei.cmp.providers.jellyfin.discovery

import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.providers.jellyfin.core.JellyfinClient
import cn.mafei.cmp.providers.jellyfin.core.JellyfinUnsupportedServerVersionError
import cn.mafei.cmp.providers.jellyfin.core.JellyfinVersionCompatibility
import cn.mafei.cmp.providers.jellyfin.transport.JellyfinRequests
import cn.mafei.cmp.providers.jellyfin.wire.PublicSystemInfoDto

/** Stable discovery surface: server identification and version gating. */
interface JellyfinDiscoveryApi {

    /** Fetches the anonymous public server info from [baseUrl]. */
    suspend fun publicServerInfo(baseUrl: HttpUrl): PublicSystemInfoDto

    /**
     * [publicServerInfo] plus the version gate. Fails fast with
     * [JellyfinUnsupportedServerVersionError] (or a [cn.mafei.cmp.providers.jellyfin.core.JellyfinError]
     * subclass for transport failures) so the connect flow never proceeds
     * against an unreachable or incompatible server.
     */
    suspend fun verifiedServerInfo(
        baseUrl: HttpUrl,
        minimumVersion: String = JellyfinVersionCompatibility.MINIMUM_SUPPORTED,
    ): VerifiedJellyfinServer
}

data class VerifiedJellyfinServer(
    val baseUrl: HttpUrl,
    val info: PublicSystemInfoDto,
)

class DefaultJellyfinDiscoveryApi(
    private val client: JellyfinClient,
) : JellyfinDiscoveryApi {

    override suspend fun publicServerInfo(baseUrl: HttpUrl): PublicSystemInfoDto =
        client.executeDecoding(JellyfinRequests.publicSystemInfo(baseUrl), PublicSystemInfoDto.serializer())

    override suspend fun verifiedServerInfo(
        baseUrl: HttpUrl,
        minimumVersion: String,
    ): VerifiedJellyfinServer {
        val info = publicServerInfo(baseUrl)
        if (!JellyfinVersionCompatibility.isSupported(info.version, minimumVersion)) {
            throw JellyfinUnsupportedServerVersionError(actualVersion = info.version, minimumSupported = minimumVersion)
        }
        return VerifiedJellyfinServer(baseUrl = baseUrl, info = info)
    }
}
