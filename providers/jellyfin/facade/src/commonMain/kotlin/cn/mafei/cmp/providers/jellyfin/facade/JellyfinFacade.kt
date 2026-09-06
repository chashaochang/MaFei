package cn.mafei.cmp.providers.jellyfin.facade

import cn.mafei.cmp.core.http_contract.HttpTransport
import cn.mafei.cmp.providers.jellyfin.auth.JellyfinAuthApi
import cn.mafei.cmp.providers.jellyfin.catalog.JellyfinCatalogApi
import cn.mafei.cmp.providers.jellyfin.core.InMemoryJellyfinSessionState
import cn.mafei.cmp.providers.jellyfin.core.JellyfinClient
import cn.mafei.cmp.providers.jellyfin.core.JellyfinServerConfig
import cn.mafei.cmp.providers.jellyfin.core.JellyfinSessionState
import cn.mafei.cmp.providers.jellyfin.discovery.JellyfinDiscoveryApi
import cn.mafei.cmp.providers.jellyfin.images.JellyfinImagesApi
import cn.mafei.cmp.providers.jellyfin.playback.JellyfinPlaybackApi
import cn.mafei.cmp.providers.jellyfin.playstate.JellyfinPlayStateApi
import kotlinx.coroutines.flow.StateFlow

/**
 * The one stable entry point the data layer (adapter-jellyfin) consumes:
 * every P0 Jellyfin capability behind a single constructor-injected facade.
 * P0 capability packs that ship later (search/music/photos/livetv/management)
 * will extend this facade in their own modules without touching these types.
 */
interface JellyfinFacade {
    val client: JellyfinClient

    /** Current session; null before login and after logout. Never log its token. */
    val session: StateFlow<cn.mafei.cmp.providers.jellyfin.core.JellyfinSession?>

    val discovery: JellyfinDiscoveryApi
    val auth: JellyfinAuthApi
    val catalog: JellyfinCatalogApi
    val playback: JellyfinPlaybackApi
    val playstate: JellyfinPlayStateApi
    val images: JellyfinImagesApi
}

class DefaultJellyfinFacade(
    config: JellyfinServerConfig,
    transport: HttpTransport,
    sessionState: JellyfinSessionState = InMemoryJellyfinSessionState(),
) : JellyfinFacade {

    override val client: JellyfinClient = JellyfinClient(config, transport, sessionState)

    override val session: StateFlow<cn.mafei.cmp.providers.jellyfin.core.JellyfinSession?> =
        client.sessionState.current

    override val discovery: JellyfinDiscoveryApi by lazy { cn.mafei.cmp.providers.jellyfin.discovery.DefaultJellyfinDiscoveryApi(client) }
    override val auth: JellyfinAuthApi by lazy { cn.mafei.cmp.providers.jellyfin.auth.DefaultJellyfinAuthApi(client) }
    override val catalog: JellyfinCatalogApi by lazy { cn.mafei.cmp.providers.jellyfin.catalog.DefaultJellyfinCatalogApi(client) }
    override val playback: JellyfinPlaybackApi by lazy { cn.mafei.cmp.providers.jellyfin.playback.DefaultJellyfinPlaybackApi(client) }
    override val playstate: JellyfinPlayStateApi by lazy { cn.mafei.cmp.providers.jellyfin.playstate.DefaultJellyfinPlayStateApi(client) }
    override val images: JellyfinImagesApi by lazy { cn.mafei.cmp.providers.jellyfin.images.DefaultJellyfinImagesApi(client) }
}
