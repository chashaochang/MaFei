package cn.mafei.cmp.providers.feiniu.core

/**
 * One Feiniu media API call description. Built by the capability modules,
 * executed by [FeiniuClient].
 */
data class FeiniuApiCall(
    val path: String,
    val method: FeiniuMethod,
    val bodyText: String = "",
    val queryParameters: List<Pair<String, String>> = emptyList(),
    val authRequirement: AuthRequirement = AuthRequirement.Cookie,
    val readTimeoutMs: Long = 15_000,
    /**
     * H2/J1: full operation context for auth operations (logout/restore) —
     * pins the EXACT snapshot (credentials AND identity generation) the
     * operation started with. When set, the client builds request headers
     * from, commits responses against, and reports this snapshot's identity,
     * so a mid-flight account switch can neither be logged out by nor
     * polluted with the old operation's response.
     */
    val pinnedOperation: FeiniuSessionSnapshot? = null,
) {
    init {
        require(path.startsWith("/")) { "Feiniu request path must start with / but was '$path'" }
    }

    enum class FeiniuMethod { Get, Post, Put, Delete }

    /**
     * [None] — no stored credentials attached (login/logout-family).
     * [Token] — Authorization header + `Trim-MC-token` cookie required.
     * [Cookie] — credentials attached when present, request stays anonymous-capable.
     */
    enum class AuthRequirement { None, Token, Cookie }
}

/** Result of one executed call together with the candidate that served it. */
class FeiniuExecuted<T>(
    val value: T,
    val candidate: FeiniuHostCandidate,
)
