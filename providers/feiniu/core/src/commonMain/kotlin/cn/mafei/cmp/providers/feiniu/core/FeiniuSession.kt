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
 * Immutable session snapshot: credentials plus the two counters that make
 * concurrent updates verifiable.
 *
 * - [identityGeneration] changes ONLY on identity switches (login install,
 *   restore install, account switch, logout/clear). Stale-response guards
 *   compare this value: a response that started under one identity must not
 *   write back after the identity moved on.
 * - [revision] changes on EVERY commit, including same-identity cookie
 *   merges, so concurrent same-account responses can merge without losing
 *   each other's cookies.
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
 * Holds the current credentials for one client (no globals).
 *
 * Commit rules (single-process concurrency contract, frozen by review):
 * - credentials + counters form one atomically swapped snapshot;
 * - conditional merges commit via compare-and-swap on the whole snapshot;
 *   when another response committed first, the merge re-bases on the new
 *   snapshot instead of dropping its cookies;
 * - identity installs/clears are guarded by the identity generation only —
 *   an ordinary cookie merge must never invalidate an in-flight login;
 * - responses whose identity moved on are dropped entirely (no write-back).
 */
interface FeiniuSessionState {
    val current: StateFlow<FeiniuCredentials>

    /** Every-commit counter; kept for compatibility with older guards. */
    fun generation(): Long

    /** Identity-switch counter; the stale-response guard. */
    fun identityGeneration(): Long

    fun snapshot(): FeiniuSessionSnapshot

    /** Unconditional identity install (login/restore success). */
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

    // G1: credentials + identity generation + revision live in ONE atomic
    // snapshot cell; every derive-and-commit happens inside the cell's
    // atomic swap, so concurrent installs never reuse a generation.
    private val cell = FeiniuSnapshotCell(FeiniuSessionSnapshot(initial.normalized(), 0L, 0L))
    private val flow = kotlinx.coroutines.flow.MutableStateFlow(initial.normalized())

    override val current: StateFlow<FeiniuCredentials> = flow

    private fun mirror(snapshot: FeiniuSessionSnapshot): FeiniuCredentials {
        flow.value = snapshot.credentials
        return snapshot.credentials
    }

    override fun generation(): Long = cell.read().revision

    override fun identityGeneration(): Long = cell.read().identityGeneration

    override fun snapshot(): FeiniuSessionSnapshot = cell.read()

    override fun set(credentials: FeiniuCredentials): FeiniuCredentials {
        // G1: derive the next generations INSIDE the atomic commit.
        while (true) {
            val observed = cell.read()
            val next = FeiniuSessionSnapshot(credentials.normalized(), observed.identityGeneration + 1, observed.revision + 1)
            if (cell.compareAndCommit(observed, next)) return mirror(next)
        }
    }

    override fun installIdentityIfUnchanged(
        expectedIdentity: Long,
        credentials: FeiniuCredentials,
    ): FeiniuCredentials? {
        val observed = cell.read()
        if (observed.identityGeneration != expectedIdentity) return null // identity stale (definitive)
        val next = FeiniuSessionSnapshot(credentials.normalized(), observed.identityGeneration + 1, observed.revision + 1)
        // Blocking compare-and-commit: contention never drops a
        // matching-identity install (G3); staleness is the only `null`.
        return if (cell.compareAndCommit(observed, next)) mirror(next) else null
    }

    override fun update(mutate: (FeiniuCredentials) -> FeiniuCredentials): FeiniuCredentials {
        while (true) {
            val observed = cell.read()
            val next = FeiniuSessionSnapshot(mutate(observed.credentials).normalized(), observed.identityGeneration, observed.revision + 1)
            if (cell.compareAndCommit(observed, next)) return mirror(next)
        }
    }

    override fun commitIfUnchanged(
        expected: FeiniuSessionSnapshot,
        mutate: (FeiniuCredentials) -> FeiniuCredentials,
    ): FeiniuCredentials? {
        val observed = cell.read()
        if (observed != expected) return null // stale (identity or revision moved) → caller re-bases
        val next = FeiniuSessionSnapshot(mutate(observed.credentials).normalized(), observed.identityGeneration, observed.revision + 1)
        return if (cell.compareAndCommit(observed, next)) mirror(next) else null
    }

    override fun updateIfUnchanged(
        expectedRevision: Long,
        mutate: (FeiniuCredentials) -> FeiniuCredentials,
    ): FeiniuCredentials? {
        val observed = cell.read()
        if (observed.revision != expectedRevision) return null
        // The mutation runs OUTSIDE the atomic commit (it may be slow or
        // blocking); the compare-and-commit re-validates the revision, so an
        // intervening logout/account switch drops this update (F1).
        val next = FeiniuSessionSnapshot(mutate(observed.credentials).normalized(), observed.identityGeneration, observed.revision + 1)
        return if (cell.compareAndCommit(observed, next)) mirror(next) else null
    }

    override fun clear(): FeiniuCredentials = set(FeiniuCredentials())

    override fun clearIfIdentityUnchanged(expectedIdentity: Long): FeiniuCredentials? {
        val observed = cell.read()
        if (observed.identityGeneration != expectedIdentity) return null // identity stale (definitive)
        val next = FeiniuSessionSnapshot(FeiniuCredentials(), observed.identityGeneration + 1, observed.revision + 1)
        return if (cell.compareAndCommit(observed, next)) mirror(next) else null
    }
}
