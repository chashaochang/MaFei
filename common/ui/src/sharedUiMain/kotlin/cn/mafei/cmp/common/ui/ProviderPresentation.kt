package cn.mafei.cmp.common.ui

import cn.mafei.cmp.common.ui.generated.resources.Res
import cn.mafei.cmp.common.ui.generated.resources.provider_emby
import cn.mafei.cmp.common.ui.generated.resources.provider_plex
import cn.mafei.cmp.core.domain.ProviderKind
import org.jetbrains.compose.resources.DrawableResource

fun providerName(provider: ProviderKind): String = when (provider) {
    ProviderKind.Jellyfin -> "Jellyfin"
    ProviderKind.Feiniu -> "飞牛影视"
    ProviderKind.Emby -> "Emby"
    ProviderKind.Plex -> "Plex"
}

fun providerLogo(provider: ProviderKind): DrawableResource = when (provider) {
    ProviderKind.Jellyfin -> MaFeiLegacyDrawables.jellyfinLogo
    ProviderKind.Feiniu -> MaFeiLegacyDrawables.feiniuLogo
    ProviderKind.Emby -> Res.drawable.provider_emby
    ProviderKind.Plex -> Res.drawable.provider_plex
}
