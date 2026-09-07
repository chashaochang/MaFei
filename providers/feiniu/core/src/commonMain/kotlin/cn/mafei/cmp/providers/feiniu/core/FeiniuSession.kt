package cn.mafei.cmp.providers.feiniu.core

import kotlinx.coroutines.flow.StateFlow

/** Feiniu media credentials: the bearer token plus its mirrored cookie set. */
data class FeiniuCredentials(
    val token: String = "",
    val cookie: String = "",
) {
    /** Normalizes: trims, derives the token from `Trim-MC-token`, re-merges. */
    fun normalized(): FeiniuCredentials {
        val trimmedCookie = cookie.trim()
        val resolvedToken = token.trim().ifEmpty { FeiniuCookieJar.extractToken(trimmedCookie).orEmpty() }
        return FeiniuCredentials(
            token = resolvedToken,
            cookie = FeiniuCookieJar.mergeToken(trimmedCookie, resolvedToken),
        )
    }

    val hasToken: Boolean get() = token.isNotEmpty()
}

/** Holds the current credentials for one client (no globals). */
interface FeiniuSessionState {
    val current: StateFlow<FeiniuCredentials>
    fun set(credentials: FeiniuCredentials): FeiniuCredentials
    fun update(mutate: (FeiniuCredentials) -> FeiniuCredentials): FeiniuCredentials
    fun clear(): FeiniuCredentials
}

class InMemoryFeiniuSessionState(initial: FeiniuCredentials = FeiniuCredentials()) : FeiniuSessionState {
    private val flow = kotlinx.coroutines.flow.MutableStateFlow(initial.normalized())
    override val current: StateFlow<FeiniuCredentials> = flow

    override fun set(credentials: FeiniuCredentials): FeiniuCredentials =
        credentials.normalized().also { flow.value = it }

    override fun update(mutate: (FeiniuCredentials) -> FeiniuCredentials): FeiniuCredentials =
        set(mutate(flow.value))

    override fun clear(): FeiniuCredentials = flow.value.also { flow.value = FeiniuCredentials() }
}
