package cn.mafei.cmp.common.glass

import kotlin.coroutines.cancellation.CancellationException

/**
 * UI/render-thread-confined circuit for one rendering lifetime, not a global
 * device blacklist. Only wrap effect creation/configuration here, NEVER content
 * composition or input handlers. Native crashes and fatal Errors propagate.
 */
internal class GlassEffectGuard(private val onFailure: () -> Unit = {}) {
    var failed: Boolean = false
        private set

    fun fail() {
        if (failed) return
        failed = true
        onFailure()
    }

    fun run(effect: () -> Unit): Boolean {
        if (failed) return false
        try {
            effect()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Do not retain exception text or retry an unsupported shader per frame.
            fail()
            return false
        }
        return true
    }
}
