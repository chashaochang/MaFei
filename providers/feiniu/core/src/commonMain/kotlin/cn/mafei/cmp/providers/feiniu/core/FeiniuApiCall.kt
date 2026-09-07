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
