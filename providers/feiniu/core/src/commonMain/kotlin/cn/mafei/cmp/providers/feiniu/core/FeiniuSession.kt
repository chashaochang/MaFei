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

/**
 * Holds the current credentials for one client (no globals).
 *
 * [generation] powers stale-response protection: a request captures the
 * generation when it starts; a response that arrives after logout/account
 * switch (generation moved on) must NOT write its credentials back.
 */
interface FeiniuSessionState {
    val current: StateFlow<FeiniuCredentials>

    /** Monotonic counter bumped by every mutation. */
    fun generation(): Long

    fun set(credentials: FeiniuCredentials): FeiniuCredentials

    fun update(mutate: (FeiniuCredentials) -> FeiniuCredentials): FeiniuCredentials

    /**
     * Applies [mutate] only when the generation still equals [expected];
     * returns the new credentials, or `null` when the state moved on and the
     * (possibly stale) mutation was dropped.
     */
    fun updateIfUnchanged(
        expected: Long,
        mutate: (FeiniuCredentials) -> FeiniuCredentials,
    ): FeiniuCredentials?

    fun clear(): FeiniuCredentials
}

class InMemoryFeiniuSessionState(initial: FeiniuCredentials = FeiniuCredentials()) : FeiniuSessionState {
    private val flow = kotlinx.coroutines.flow.MutableStateFlow(initial.normalized())
    private var generationCounter = 0L

    override val current: StateFlow<FeiniuCredentials> = flow

    override fun generation(): Long = generationCounter

    override fun set(credentials: FeiniuCredentials): FeiniuCredentials =
        credentials.normalized().also {
            flow.value = it
            generationCounter += 1
        }

    override fun update(mutate: (FeiniuCredentials) -> FeiniuCredentials): FeiniuCredentials =
        set(mutate(flow.value))

    override fun updateIfUnchanged(
        expected: Long,
        mutate: (FeiniuCredentials) -> FeiniuCredentials,
    ): FeiniuCredentials? {
        if (generationCounter != expected) return null
        return update(mutate)
    }

    override fun clear(): FeiniuCredentials = flow.value.also {
        flow.value = FeiniuCredentials()
        generationCounter += 1
    }
}
