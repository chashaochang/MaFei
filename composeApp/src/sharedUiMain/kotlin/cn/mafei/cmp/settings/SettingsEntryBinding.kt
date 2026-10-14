package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.feature.settings.SettingsScreen
import cn.mafei.cmp.player.api.PlayerRenderMode
import cn.mafei.cmp.session.*

@Composable
internal fun SettingsEntryBinding(
    account: AccountFlowState,
    feiniu: FeiniuPreferenceState,
    session: ApplicationSession,
    playback: SessionPlaybackRuntime?,
    popMineRoute: () -> Unit,
    appearanceStore: AppearanceStore,
    glassStyleStore: GlassStyleStore,
    homeCarouselVisible: State<Boolean>,
    onHomeCarouselVisibleChanged: (Boolean) -> Unit,
    currentPlayerRenderMode: State<PlayerRenderMode>,
    currentPlayerRenderModeChanged: State<(PlayerRenderMode) -> Unit>,
    playerRenderModeSupported: Boolean,
) {
    SettingsScreen(
        onBack = popMineRoute,
        onOpenAppearance = {
            if (account.mineBackStack.lastOrNull() != BrowseAppearanceRoute) {
                account.mineBackStack += BrowseAppearanceRoute
            }
        },
        onOpenGlassStyleSettings = {
            if (account.mineBackStack.lastOrNull() != BrowseGlassStyleRoute) {
                account.mineBackStack += BrowseGlassStyleRoute
            }
        },
        appearanceTitle = appearanceStore.state.collectAsState().value.title,
        glassStyleTitle = glassStyleStore.state.collectAsState().value.title,
        feiniuRoutePreferenceTitle = if (
            session.selection.provider == ProviderKind.Feiniu
        ) {
            feiniu.feiniuConnectionPreference.title
        } else {
            null
        },
        onOpenFeiniuPreference = if (
            session.selection.provider == ProviderKind.Feiniu
        ) {
            {
                feiniu.feiniuConnectionPreferenceError = null
                if (account.mineBackStack.lastOrNull() != BrowseFeiniuConnectionPreferenceRoute) {
                    account.mineBackStack += BrowseFeiniuConnectionPreferenceRoute
                }
            }
            } else {
            null
        },
        homeCarouselVisible = { homeCarouselVisible.value },
        onHomeCarouselVisibleChanged = onHomeCarouselVisibleChanged,
        showPlayerRenderMode = playerRenderModeSupported,
        onOpenPlayerSettings = if (playback?.controller?.capabilities?.playbackPreferences == true) {
            { account.mineBackStack += BrowsePlayerSettingsRoute }
        } else null,
        playerRenderMode = { currentPlayerRenderMode.value.storageValue },
        onPlayerRenderModeChanged = { value ->
            currentPlayerRenderModeChanged.value(value.toPlayerRenderMode())
        },
        modifier = Modifier.fillMaxSize(),
    )
}
