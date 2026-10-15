package cn.mafei.cmp.providers.feiniu.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Feiniu media credentials: the bearer token plus its mirrored cookie set. */
data class FeiniuCredentials(
    val token: String = "",
    val cookie: String = "",
) {
    override fun toString(): String = "FeiniuCredentials(token=██, cookie=██)"

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
 * Immutable, versioned session snapshot — the SINGLE authoritative session
 * state. `MutableStateFlow.compareAndSet` both validates and PUBLISHES in
 * one atomic step, so the observable value can never lag or regress behind
 * the committed state (H3): there is no secondary mirror.
 *
 * - [identityGeneration] changes ONLY on identity switches (login/restore
 *   install, account switch, logout/clear) and guards stale responses.
 * - [revision] changes on every commit, including same-identity cookie
 *   merges, so concurrent same-account responses re-base instead of
 *   dropping each other's cookies.
 */
class FeiniuSessionSnapshot(
    val credentials: FeiniuCredentials,
    val identityGeneration: Long,
    val revision: Long,
) {
    override fun equals(other: Any?): Boolean =
        other is FeiniuSessionSnapshot &&
            other.credentials == credentials &&
            other.identityGeneration == identityGeneration &&
            other.revision == revision

    override fun hashCode(): Int {
        var result = credentials.hashCode()
        result = 31 * result + identityGeneration.hashCode()
        result = 31 * result + revision.hashCode()
        return result
    }

    override fun toString(): String =
        "FeiniuSessionSnapshot(credentials=$credentials, identityGeneration=$identityGeneration, revision=$revision)"
}

/**
 * Holds the current credentials for one client (no globals). The published
 * [snapshots] flow is the authoritative state: every commit is an atomic
 * compare-and-set ON the flow itself.
 */
interface FeiniuSessionState {
    val snapshots: StateFlow<FeiniuSessionSnapshot>

    fun snapshot(): FeiniuSessionSnapshot

    /** Every-commit counter; kept for compatibility with older guards. */
    fun generation(): Long

    /** Identity-switch counter; the stale-response guard. */
    fun identityGeneration(): Long

    /**
     * Installs [credentials] as an identity switch and returns the exact
     * post-install snapshot receipt (G3/H2: the caller binds follow-up
     * validation and cleanup to THIS identity, never a re-read).
     */
    fun install(credentials: FeiniuCredentials): FeiniuSessionSnapshot

    /** Compatibility view of [install]. */
    fun set(credentials: FeiniuCredentials): FeiniuCredentials

    /** Installs [credentials] only if the identity is still [expectedIdentity]. */
    fun installIdentityIfUnchanged(
        expectedIdentity: Long,
        credentials: FeiniuCredentials,
    ): FeiniuCredentials?

    fun update(mutate: (FeiniuCredentials) -> FeiniuCredentials): FeiniuCredentials

    /** CAS merge on the full snapshot observed by the caller. */
    fun commitIfUnchanged(
        expected: FeiniuSessionSnapshot,
        mutate: (FeiniuCredentials) -> FeiniuCredentials,
    ): FeiniuCredentials?

    /** Legacy revision-guarded merge; commits only while the revision holds. */
    fun updateIfUnchanged(
        expectedRevision: Long,
        mutate: (FeiniuCredentials) -> FeiniuCredentials,
    ): FeiniuCredentials?

    /** Unconditional identity switch (logout/account switch). */
    fun clear(): FeiniuCredentials

    /** Clears only if the identity is still [expectedIdentity]. */
    fun clearIfIdentityUnchanged(expectedIdentity: Long): FeiniuCredentials?
}

class InMemoryFeiniuSessionState(initial: FeiniuCredentials = FeiniuCredentials()) : FeiniuSessionState {

    // The flow IS the authoritative state: compareAndSet validates AND
    // publishes in one atomic step (H3 — no secondary mirror to diverge).
    private val state = MutableStateFlow(FeiniuSessionSnapshot(initial.normalized(), 0L, 0L))

    override val snapshots: StateFlow<FeiniuSessionSnapshot> = state

    override fun snapshot(): FeiniuSessionSnapshot = state.value

    override fun generation(): Long = state.value.revision

    override fun identityGeneration(): Long = state.value.identityGeneration

    override fun install(credentials: FeiniuCredentials): FeiniuSessionSnapshot {
        while (true) {
            val observed = state.value
            val next = FeiniuSessionSnapshot(credentials.normalized(), observed.identityGeneration + 1, observed.revision + 1)
            if (state.compareAndSet(observed, next)) return next
        }
    }

    override fun set(credentials: FeiniuCredentials): FeiniuCredentials = install(credentials).credentials

    override fun installIdentityIfUnchanged(
        expectedIdentity: Long,
        credentials: FeiniuCredentials,
    ): FeiniuCredentials? {
        val observed = state.value
        if (observed.identityGeneration != expectedIdentity) return null // identity stale (definitive)
        val next = FeiniuSessionSnapshot(credentials.normalized(), observed.identityGeneration + 1, observed.revision + 1)
        // compareAndSet validates AND publishes atomically (H3)
        return if (state.compareAndSet(observed, next)) next.credentials else null
    }

    override fun update(mutate: (FeiniuCredentials) -> FeiniuCredentials): FeiniuCredentials {
        while (true) {
            val observed = state.value
            val next = FeiniuSessionSnapshot(mutate(observed.credentials).normalized(), observed.identityGeneration, observed.revision + 1)
            if (state.compareAndSet(observed, next)) return next.credentials
        }
    }

    override fun commitIfUnchanged(
        expected: FeiniuSessionSnapshot,
        mutate: (FeiniuCredentials) -> FeiniuCredentials,
    ): FeiniuCredentials? {
        val observed = state.value
        if (observed != expected) return null // stale (identity or revision moved) → caller re-bases
        val next = FeiniuSessionSnapshot(mutate(observed.credentials).normalized(), observed.identityGeneration, observed.revision + 1)
        return if (state.compareAndSet(observed, next)) next.credentials else null
    }

    override fun updateIfUnchanged(
        expectedRevision: Long,
        mutate: (FeiniuCredentials) -> FeiniuCredentials,
    ): FeiniuCredentials? {
        val observed = state.value
        if (observed.revision != expectedRevision) return null
        // The mutation runs outside the atomic commit (it may be slow or
        // blocking); the compare-and-commit re-validates the revision, so an
        // intervening logout/account switch drops this update (F1).
        val next = FeiniuSessionSnapshot(mutate(observed.credentials).normalized(), observed.identityGeneration, observed.revision + 1)
        return if (state.compareAndSet(observed, next)) next.credentials else null
    }

    override fun clear(): FeiniuCredentials = install(FeiniuCredentials()).credentials

    override fun clearIfIdentityUnchanged(expectedIdentity: Long): FeiniuCredentials? {
        val observed = state.value
        if (observed.identityGeneration != expectedIdentity) return null // identity stale (definitive)
        val next = FeiniuSessionSnapshot(FeiniuCredentials(), observed.identityGeneration + 1, observed.revision + 1)
        return if (state.compareAndSet(observed, next)) next.credentials else null
    }
}
