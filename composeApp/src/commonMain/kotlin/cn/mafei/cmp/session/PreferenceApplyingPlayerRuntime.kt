package cn.mafei.cmp.session

import cn.mafei.cmp.player.api.PlayerPlaybackPreferences
import cn.mafei.cmp.player.api.PlayerRuntime
import cn.mafei.cmp.player.api.RuntimeCommand

/** Covers initial playback, next episode and re-negotiation without UI timing dependencies. */
internal class PreferenceApplyingPlayerRuntime(
    private val delegate: PlayerRuntime,
    private val preferences: () -> PlayerPlaybackPreferences,
) : PlayerRuntime by delegate {
    override fun execute(command: RuntimeCommand) {
        if (command is RuntimeCommand.Load && capabilities.playbackPreferences) {
            delegate.setPlaybackPreferences(preferences())
        }
        delegate.execute(command)
    }
}
