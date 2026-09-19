package cn.mafei.cmp.session

import cn.mafei.cmp.core.domain.ClientConfigError
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.http_contract.HttpRequest
import cn.mafei.cmp.core.http_contract.HttpResponse
import cn.mafei.cmp.core.http_contract.HttpTransport
import cn.mafei.cmp.core.secure_credential_api.CredentialAlias
import cn.mafei.cmp.core.secure_credential_api.SecureSecret
import cn.mafei.cmp.data.adapter_contract.MediaProviderAdapter
import cn.mafei.cmp.data.adapter_feiniu.FeiniuMediaProviderAdapter
import cn.mafei.cmp.data.adapter_jellyfin.JellyfinMediaProviderAdapter
import cn.mafei.cmp.data.repositories.session.AuthoritativeCredentialSource
import cn.mafei.cmp.data.repositories.session.SessionCredentialSnapshot
import cn.mafei.cmp.data.repositories.session.StateFlowCredentialSource
import cn.mafei.cmp.providers.feiniu.core.FeiniuCredentials
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostCandidate
import cn.mafei.cmp.providers.feiniu.core.FeiniuHostResolution
import cn.mafei.cmp.providers.feiniu.core.FeiniuRoute
import cn.mafei.cmp.providers.feiniu.core.InMemoryFeiniuSessionState
import cn.mafei.cmp.providers.feiniu.facade.DefaultFeiniuFacade
import cn.mafei.cmp.providers.jellyfin.core.JellyfinServerConfig
import cn.mafei.cmp.providers.jellyfin.core.JellyfinSession
import cn.mafei.cmp.providers.jellyfin.core.JellyfinSessionState
import cn.mafei.cmp.providers.jellyfin.facade.DefaultJellyfinFacade
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.cancellation.CancellationException

interface ProviderSessionBinding {
    val adapter: MediaProviderAdapter
    val source: AuthoritativeCredentialSource
    val aliases: Set<CredentialAlias>
    /** Invalidates this SDK in memory only. Persistence and root transport ownership stay outside. */
    fun clear()
}

fun interface ProviderSessionFactory {
    fun restore(connection: SavedConnection, secrets: Map<CredentialAlias, SecureSecret>): ProviderSessionBinding
}

class DefaultProviderSessionFactory(private val transport: HttpTransport) : ProviderSessionFactory {
    override fun restore(connection: SavedConnection, secrets: Map<CredentialAlias, SecureSecret>): ProviderSessionBinding {
        // Revalidate and freeze caller-owned lists before constructing an SDK.
        val saved = connection.copy(feiniuRoutes = connection.feiniuRoutes.map { it.copy() })
        if (!saved.aliases.containsAll(secrets.keys)) throw ClientConfigError("Unexpected saved credential alias")
        val token = secrets[saved.tokenAlias] as? SecureSecret.AuthToken
            ?: throw ClientConfigError("Saved access token is missing or has an unsupported type")
        val scheme = if (saved.server.provider == ProviderKind.Jellyfin) "MediaBrowser" else "Bearer"
        if (token.scheme != scheme || token.token.isBlank() || token.token != token.token.trim() || token.token.any { it.code < 32 || it.code == 127 }) {
            throw ClientConfigError("Saved access token type or scheme is unsupported")
        }
        val cookie = secrets[saved.cookieAlias]
        if (cookie != null && cookie !is SecureSecret.CookieText) throw ClientConfigError("Saved cookie has an unsupported type")
        if (cookie is SecureSecret.CookieText && cookie.value.any { it.code < 32 || it.code == 127 }) {
            throw ClientConfigError("Saved cookie contains invalid control characters")
        }
        return when (saved.server.provider) {
            ProviderKind.Jellyfin -> jellyfin(saved, token)
            ProviderKind.Feiniu -> feiniu(saved, token, cookie as? SecureSecret.CookieText)
        }
    }

    private fun jellyfin(connection: SavedConnection, token: SecureSecret.AuthToken): ProviderSessionBinding {
        val installed = JellyfinSession(userId = connection.remoteUserId, accessToken = token.token)
        val state = SingleIdentityJellyfinState(installed)
        val facade = DefaultJellyfinFacade(
            JellyfinServerConfig(validateSavedBaseUrl(connection.server.baseUrl), "MaFei", "MaFei CMP", connection.deviceId, "2.0.0"),
            BorrowedSessionTransport(transport) { state.current.value != null },
            state,
        )
        val source = StateFlowCredentialSource(facade.session) { session ->
            // These are binding-local validity markers, NOT invented SDK generations.
            // Jellyfin has no versioned identity support: a replacement invalidates
            // the whole binding, which must be rebuilt by the application root.
            if (session != null) SessionCredentialSnapshot(0, 0,
                mapOf(connection.tokenAlias to SecureSecret.AuthToken("MediaBrowser", session.accessToken)))
            else SessionCredentialSnapshot(1, 1, emptyMap())
        }
        val adapter = JellyfinMediaProviderAdapter(facade, connection.server.id, connection.account.id, connection.remoteUserId)
        return binding(adapter, source, connection.aliases) { state.clear() }
    }

    private fun feiniu(connection: SavedConnection, token: SecureSecret.AuthToken, cookie: SecureSecret.CookieText?): ProviderSessionBinding {
        val state = InMemoryFeiniuSessionState()
        val identity = state.install(FeiniuCredentials(token.token, cookie?.value.orEmpty())).identityGeneration
        val facade = DefaultFeiniuFacade(
            BorrowedSessionTransport(transport) { state.snapshots.value.identityGeneration == identity }, state,
        )
        val candidates = connection.feiniuRoutes.map { saved ->
            FeiniuHostCandidate(saved.baseUrl, when (saved.route) {
                "Local" -> FeiniuRoute.Local
                "Public" -> FeiniuRoute.Public
                "Relay" -> FeiniuRoute.Relay
                "Direct" -> FeiniuRoute.Direct
                else -> throw ClientConfigError("Unsupported saved Feiniu route")
            })
        }
        val source = StateFlowCredentialSource(facade.credentials) { snapshot ->
            val projected = buildMap<CredentialAlias, SecureSecret> {
                if (snapshot.credentials.token.isNotEmpty()) put(connection.tokenAlias, SecureSecret.AuthToken("Bearer", snapshot.credentials.token))
                if (snapshot.credentials.cookie.isNotEmpty()) put(connection.cookieAlias, SecureSecret.CookieText(snapshot.credentials.cookie))
            }
            SessionCredentialSnapshot(snapshot.identityGeneration, snapshot.revision, projected)
        }
        // Construct only AFTER install: the adapter pins the authoritative generation.
        val adapter = FeiniuMediaProviderAdapter(facade, connection.server.id, connection.account.id,
            FeiniuHostResolution(candidates.first(), candidates.drop(1)))
        return binding(adapter, source, connection.aliases) { state.clear() }
    }

    private fun binding(adapter: MediaProviderAdapter, source: AuthoritativeCredentialSource, aliases: Set<CredentialAlias>, onClear: () -> Unit) =
        object : ProviderSessionBinding {
            override val adapter = adapter
            override val source = source
            override val aliases = aliases.toSet()
            override fun clear() { onClear() }
        }
}

/** Only one preinstalled identity is supported. SDK set() cannot quietly replace its account. */
internal class SingleIdentityJellyfinState(initial: JellyfinSession) : JellyfinSessionState {
    private val flow = MutableStateFlow<JellyfinSession?>(initial)
    override val current: StateFlow<JellyfinSession?> = flow.asStateFlow()
    override fun set(session: JellyfinSession) { flow.value = null }
    override fun clear(): JellyfinSession? = flow.value.also { flow.value = null }
}

private class BorrowedSessionTransport(private val root: HttpTransport, private val valid: () -> Boolean) : HttpTransport {
    override suspend fun execute(request: HttpRequest): HttpResponse {
        if (!valid()) throw CancellationException("Provider binding was invalidated")
        return root.execute(request).also {
            if (!valid()) throw CancellationException("Provider binding was invalidated")
        }
    }
    override fun close() = Unit // This binding does not own the application transport.
}
