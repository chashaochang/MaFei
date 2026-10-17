package cn.mafei.cmp

import platform.UIKit.UIApplication

/** UIApplication's idle timer is process-wide, so one scene must not release another's request. */
internal object IosVideoScreenAwake {
    private val owners = mutableSetOf<Any>()
    private var foreground = true

    fun setRequested(owner: Any, requested: Boolean) {
        if (requested) owners.add(owner) else owners.remove(owner)
        apply()
    }

    fun setForeground(value: Boolean) {
        foreground = value
        apply()
    }

    private fun apply() {
        UIApplication.sharedApplication.idleTimerDisabled = foreground && owners.isNotEmpty()
    }
}
