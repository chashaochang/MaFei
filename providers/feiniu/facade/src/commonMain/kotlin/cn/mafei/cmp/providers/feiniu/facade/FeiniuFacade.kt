package cn.mafei.cmp.providers.feiniu.facade

import cn.mafei.cmp.core.http_contract.HttpTransport
import cn.mafei.cmp.providers.feiniu.assets.FeiniuAssetsApi
import cn.mafei.cmp.providers.feiniu.auth.FeiniuAuthApi
import cn.mafei.cmp.providers.feiniu.playback.FeiniuPlaybackApi
import cn.mafei.cmp.providers.feiniu.catalog.FeiniuCatalogApi
import cn.mafei.cmp.providers.feiniu.connection.FeiniuConnectionApi
import cn.mafei.cmp.providers.feiniu.core.FeiniuClient
import cn.mafei.cmp.providers.feiniu.core.FeiniuCredentials
import cn.mafei.cmp.providers.feiniu.core.InMemoryFeiniuSessionState
import cn.mafei.cmp.providers.feiniu.core.FeiniuSessionState
import kotlinx.coroutines.flow.StateFlow

/**
 * Feiniu facade: one constructor-injected entry point aggregating
 * connection, auth, catalog, playback and assets. Account/management
 * capability packs will extend it later without touching these types.
 */
interface FeiniuFacade {
    val client: FeiniuClient

    /** Current media credentials (token + cookie); never log the token. */
    val credentials: StateFlow<FeiniuCredentials>

    val connection: FeiniuConnectionApi
    val auth: FeiniuAuthApi
    val catalog: FeiniuCatalogApi
    val playback: FeiniuPlaybackApi
    val assets: FeiniuAssetsApi
}

class DefaultFeiniuFacade(
    transport: HttpTransport,
    sessionState: FeiniuSessionState = InMemoryFeiniuSessionState(),
) : FeiniuFacade {

    override val client: FeiniuClient = FeiniuClient(transport = transport, sessionState = sessionState)

    override val credentials: StateFlow<FeiniuCredentials> = client.sessionState.current

    override val connection: FeiniuConnectionApi by lazy { FeiniuConnectionApi(transport) }
    override val auth: FeiniuAuthApi by lazy { FeiniuAuthApi(client) }
    override val catalog: FeiniuCatalogApi by lazy { FeiniuCatalogApi(client) }
    override val playback: FeiniuPlaybackApi by lazy { FeiniuPlaybackApi(client) }
    override val assets: FeiniuAssetsApi by lazy { FeiniuAssetsApi() }
}
