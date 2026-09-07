package cn.mafei.cmp.providers.feiniu.core

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex

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

    private class Snapshot(
        val credentials: FeiniuCredentials,
        val identityGeneration: Long,
        val revision: Long,
    )

    // tryLock-based critical sections keep check+commit atomic without
    // suspending callers (the review's latch regression drives this from a
    // plain thread). Contended committers re-base instead of blocking.
    private val mutex = Mutex()
    private var credentials = initial.normalized()
    private var identityGenerationCounter = 0L
    private var revisionCounter = 0L
    private val flow = kotlinx.coroutines.flow.MutableStateFlow(credentials)

    override val current: StateFlow<FeiniuCredentials> = flow

    override fun generation(): Long = readSnapshot().revision

    override fun identityGeneration(): Long = readSnapshot().identityGeneration

    override fun snapshot(): FeiniuSessionSnapshot = readSnapshot().toPublicSnapshot()

    override fun set(credentials: FeiniuCredentials): FeiniuCredentials = writeAndMirror(
        Snapshot(credentials.normalized(), readSnapshot().identityGeneration + 1, readSnapshot().revision + 1),
    )

    override fun installIdentityIfUnchanged(
        expectedIdentity: Long,
        credentials: FeiniuCredentials,
    ): FeiniuCredentials? {
        if (!mutex.tryLock()) return null
        try {
            if (identityGenerationCounter != expectedIdentity) return null
            val normalized = credentials.normalized()
            identityGenerationCounter += 1
            revisionCounter += 1
            this.credentials = normalized
            flow.value = normalized
            return normalized
        } finally {
            mutex.unlock()
        }
    }

    override fun update(mutate: (FeiniuCredentials) -> FeiniuCredentials): FeiniuCredentials {
        while (true) {
            val committed = commitIfUnchanged(readSnapshot().toPublicSnapshot(), mutate)
            if (committed != null) return committed
        }
    }

    override fun commitIfUnchanged(
        expected: FeiniuSessionSnapshot,
        mutate: (FeiniuCredentials) -> FeiniuCredentials,
    ): FeiniuCredentials? {
        if (!mutex.tryLock()) return null
        try {
            if (credentials != expected.credentials ||
                identityGenerationCounter != expected.identityGeneration ||
                revisionCounter != expected.revision
            ) {
                return null
            }
            val normalized = mutate(credentials).normalized()
            revisionCounter += 1
            this.credentials = normalized
            flow.value = normalized
            return normalized
        } finally {
            mutex.unlock()
        }
    }

    override fun updateIfUnchanged(
        expectedRevision: Long,
        mutate: (FeiniuCredentials) -> FeiniuCredentials,
    ): FeiniuCredentials? {
        // The mutation runs OUTSIDE the lock (it may be slow or blocking);
        // the commit re-validates the revision under the lock, so any
        // intervening logout/account switch drops this update (F1).
        val observed = readSnapshot()
        if (observed.revision != expectedRevision) return null
        val normalized = mutate(observed.credentials).normalized()
        if (!mutex.tryLock()) return null
        try {
            if (revisionCounter != expectedRevision) return null
            this.credentials = normalized
            revisionCounter += 1
            flow.value = normalized
            return normalized
        } finally {
            mutex.unlock()
        }
    }

    override fun clear(): FeiniuCredentials = set(FeiniuCredentials())

    override fun clearIfIdentityUnchanged(expectedIdentity: Long): FeiniuCredentials? {
        if (!mutex.tryLock()) return null
        try {
            if (identityGenerationCounter != expectedIdentity) return null
            identityGenerationCounter += 1
            revisionCounter += 1
            this.credentials = FeiniuCredentials()
            flow.value = this.credentials
            return this.credentials
        } finally {
            mutex.unlock()
        }
    }

    private fun writeAndMirror(next: Snapshot): FeiniuCredentials {
        while (!mutex.tryLock()) { /* brief spin: critical sections are tiny */ }
        try {
            credentials = next.credentials
            identityGenerationCounter = next.identityGeneration
            revisionCounter = next.revision
            flow.value = next.credentials
            return next.credentials
        } finally {
            mutex.unlock()
        }
    }

    private fun readSnapshot(): Snapshot {
        while (!mutex.tryLock()) { /* spin */ }
        try {
            return Snapshot(credentials, identityGenerationCounter, revisionCounter)
        } finally {
            mutex.unlock()
        }
    }

    private fun Snapshot.toPublicSnapshot(): FeiniuSessionSnapshot =
        FeiniuSessionSnapshot(credentials, identityGeneration, revision)
}
