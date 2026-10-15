package cn.mafei.cmp.common.ui

import cn.mafei.cmp.common.ui.generated.resources.Res
import cn.mafei.cmp.common.ui.generated.resources.add_group
import cn.mafei.cmp.common.ui.generated.resources.add
import cn.mafei.cmp.common.ui.generated.resources.app_icon
import cn.mafei.cmp.common.ui.generated.resources.arrow_right
import cn.mafei.cmp.common.ui.generated.resources.default_avatar
import cn.mafei.cmp.common.ui.generated.resources.empty
import cn.mafei.cmp.common.ui.generated.resources.empty_media
import cn.mafei.cmp.common.ui.generated.resources.empty_state_list
import cn.mafei.cmp.common.ui.generated.resources.failed
import cn.mafei.cmp.common.ui.generated.resources.feiniu_video_logo
import cn.mafei.cmp.common.ui.generated.resources.history
import cn.mafei.cmp.common.ui.generated.resources.ic_account_switch
import cn.mafei.cmp.common.ui.generated.resources.ic_avatar
import cn.mafei.cmp.common.ui.generated.resources.ic_audio
import cn.mafei.cmp.common.ui.generated.resources.ic_back
import cn.mafei.cmp.common.ui.generated.resources.ic_check
import cn.mafei.cmp.common.ui.generated.resources.ic_close
import cn.mafei.cmp.common.ui.generated.resources.ic_like
import cn.mafei.cmp.common.ui.generated.resources.ic_play_round
import cn.mafei.cmp.common.ui.generated.resources.ic_photo
import cn.mafei.cmp.common.ui.generated.resources.ic_pip
import cn.mafei.cmp.common.ui.generated.resources.ic_wireless_projection
import cn.mafei.cmp.common.ui.generated.resources.danmu_open
import cn.mafei.cmp.common.ui.generated.resources.danmu_close
import cn.mafei.cmp.common.ui.generated.resources.ic_setting
import cn.mafei.cmp.common.ui.generated.resources.ic_search
import cn.mafei.cmp.common.ui.generated.resources.ic_unlike
import cn.mafei.cmp.common.ui.generated.resources.ic_yinsi
import cn.mafei.cmp.common.ui.generated.resources.icon_del
import cn.mafei.cmp.common.ui.generated.resources.icon_replay
import cn.mafei.cmp.common.ui.generated.resources.jiangxu
import cn.mafei.cmp.common.ui.generated.resources.jellyfin_logo
import cn.mafei.cmp.common.ui.generated.resources.movie
import cn.mafei.cmp.common.ui.generated.resources.no_history
import cn.mafei.cmp.common.ui.generated.resources.pony_loading_frame_0
import cn.mafei.cmp.common.ui.generated.resources.pony_loading_frame_1
import cn.mafei.cmp.common.ui.generated.resources.pony_loading_frame_2
import cn.mafei.cmp.common.ui.generated.resources.pony_loading_frame_3
import cn.mafei.cmp.common.ui.generated.resources.placeholder_folder
import cn.mafei.cmp.common.ui.generated.resources.qrcode
import cn.mafei.cmp.common.ui.generated.resources.shengxu
import cn.mafei.cmp.common.ui.generated.resources.tv
import org.jetbrains.compose.resources.DrawableResource
import cn.mafei.cmp.common.ui.generated.resources.web

/**
 * Original HarmonyOS artwork exposed through a stable public API.
 *
 * Compose resource accessors are module-internal, so feature modules consume
 * these exact legacy assets through this object instead of redrawing icons.
 */
object MaFeiLegacyDrawables {
    val web: DrawableResource get() = Res.drawable.web
    val appIcon: DrawableResource get() = Res.drawable.app_icon
    val ponyLoadingFrames: List<DrawableResource>
        get() = listOf(
            Res.drawable.pony_loading_frame_0,
            Res.drawable.pony_loading_frame_1,
            Res.drawable.pony_loading_frame_2,
            Res.drawable.pony_loading_frame_3,
        )
    val back: DrawableResource get() = Res.drawable.ic_back
    val playRound: DrawableResource get() = Res.drawable.ic_play_round
    val favoriteSelected: DrawableResource get() = Res.drawable.ic_like
    val favoriteUnselected: DrawableResource get() = Res.drawable.ic_unlike
    val watched: DrawableResource get() = Res.drawable.ic_check
    val arrowRight: DrawableResource get() = Res.drawable.arrow_right
    val sortDescending: DrawableResource get() = Res.drawable.jiangxu
    val sortAscending: DrawableResource get() = Res.drawable.shengxu
    val libraryFolder: DrawableResource get() = Res.drawable.placeholder_folder
    val libraryMovie: DrawableResource get() = Res.drawable.movie
    val librarySeries: DrawableResource get() = Res.drawable.tv
    val libraryMusic: DrawableResource get() = Res.drawable.ic_audio
    val libraryPhoto: DrawableResource get() = Res.drawable.ic_photo
    val pictureInPicture: DrawableResource get() = Res.drawable.ic_pip
    /** Same HMSymbolVF U+F02EB glyph as legacy sys.symbol.wireless_projection, rasterized for CMP. */
    val wirelessProjection: DrawableResource get() = Res.drawable.ic_wireless_projection
    val danmakuOpen: DrawableResource get() = Res.drawable.danmu_open
    val danmakuClose: DrawableResource get() = Res.drawable.danmu_close
    // 注意：旧版还有一张 `bg_mine`（浅灰底 + 播放按钮水印）作「我的」页背景，但它属于**飞牛主题**：
    // 旧版判据是 `AppThemeTokens.showMineBackground(style) = (style === ThemeStyle.Feiniu)`，那个主题
    // 当前不做，所以不在这里暴露入口（资源文件仍留在 composeResources，将来做飞牛主题时再挂上）。
    val defaultAvatar: DrawableResource get() = Res.drawable.default_avatar
    val castAvatar: DrawableResource get() = Res.drawable.ic_avatar
    val accountSwitch: DrawableResource get() = Res.drawable.ic_account_switch
    val settings: DrawableResource get() = Res.drawable.ic_setting
    val privacy: DrawableResource get() = Res.drawable.ic_yinsi
    val addGroup: DrawableResource get() = Res.drawable.add_group
    val empty: DrawableResource get() = Res.drawable.empty
    val emptyMedia: DrawableResource get() = Res.drawable.empty_media
    val emptyList: DrawableResource get() = Res.drawable.empty_state_list
    val failed: DrawableResource get() = Res.drawable.failed
    val noHistory: DrawableResource get() = Res.drawable.no_history
    val groupQrCode: DrawableResource get() = Res.drawable.qrcode
    val jellyfinLogo: DrawableResource get() = Res.drawable.jellyfin_logo
    val feiniuLogo: DrawableResource get() = Res.drawable.feiniu_video_logo
    val delete: DrawableResource get() = Res.drawable.icon_del
    val add: DrawableResource get() = Res.drawable.add
    val close: DrawableResource get() = Res.drawable.ic_close
    val history: DrawableResource get() = Res.drawable.history
    val search: DrawableResource get() = Res.drawable.ic_search
    val replay: DrawableResource get() = Res.drawable.icon_replay
}
