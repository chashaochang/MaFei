package cn.mafei.cmp.feature.home

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.data.adapter_contract.AdapterCapabilities
import cn.mafei.cmp.data.repositories.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One account-scoped published read model. Controllers own request tickets and caches.
 * Shared media mutations update this model once so every visible surface stays consistent.
 * All controller calls and writes remain confined to the caller's UI dispatcher.
 */
internal class BrowserContext(
    val repository: MediaRepository,
    val session: MediaSession,
    val capabilities: AdapterCapabilities,
) {
    val mutableState = MutableStateFlow(MediaBrowserState(loading = true, homeLoading = true))
    val state: StateFlow<MediaBrowserState> = mutableState.asStateFlow()
    private val mutableAuthenticationFailure = MutableStateFlow<AuthFailureKind?>(null)
    val authenticationFailure: StateFlow<AuthFailureKind?> = mutableAuthenticationFailure.asStateFlow()

    fun browseError(error: Exception): String? {
        if (error is AuthenticationError) {
            mutableAuthenticationFailure.value = error.kind
            return null
        }
        return browseErrorMessage(error)
    }
}

/** Shared infrastructure only; no controller keeps a second copy of the published media. */
internal abstract class BrowserController(private val context: BrowserContext) {
    protected val repository get() = context.repository
    protected val session get() = context.session
    protected val capabilities get() = context.capabilities
    protected val mutableState get() = context.mutableState
    protected val state get() = context.state
    protected fun browseError(error: Exception): String? = context.browseError(error)
}
