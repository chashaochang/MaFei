package cn.mafei.cmp.providers.jellyfin.core

import kotlinx.coroutines.flow.StateFlow

/** An authenticated Jellyfin session. The token is bearer material — never log it. */
data class JellyfinSession(
    val userId: String,
    val accessToken: String,
    val serverId: String? = null,
) {
    override fun toString(): String =
        "JellyfinSession(userId=$userId, serverId=$serverId, accessToken=██)"
}

/**
 * Holds the current session for one client instance. Constructed per client
 * (no global singletons); the data layer persists/restores its content via
 * the secure credential store.
 */
interface JellyfinSessionState {
    val current: StateFlow<JellyfinSession?>
    fun set(session: JellyfinSession)
    fun clear(): JellyfinSession?
}

class InMemoryJellyfinSessionState(initial: JellyfinSession? = null) : JellyfinSessionState {
    private val flow = kotlinx.coroutines.flow.MutableStateFlow(initial)
    override val current: StateFlow<JellyfinSession?> = flow

    override fun set(session: JellyfinSession) {
        flow.value = session
    }

    override fun clear(): JellyfinSession? = flow.value.also { flow.value = null }
}
