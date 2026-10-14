package cn.mafei.cmp

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import cn.mafei.cmp.core.domain.ProviderKind

/** Browse and account-flow keys; navigation state and effects belong to their hosts. */
@Serializable
internal sealed interface BrowseRoute : NavKey

@Serializable
internal data object BrowseHomeRoute : BrowseRoute

@Serializable
internal data object BrowseChasingRoute : BrowseRoute

@Serializable
internal data object BrowseFavoriteRoute : BrowseRoute

@Serializable
internal data object BrowseLibraryIndexRoute : BrowseRoute

/** The home "最近添加 / 更多" feed. This is a list of the home feed, not the library index. */
@Serializable
internal data class BrowseLatestRoute(val collectionId: String? = null) : BrowseRoute

@Serializable
internal data object BrowseSearchRoute : BrowseRoute

@Serializable
internal data object BrowseMineRoute : BrowseRoute

@Serializable
internal data object BrowseMineHistoryRoute : BrowseRoute

@Serializable
internal data object BrowseAccountSwitchRoute : BrowseRoute

@Serializable
internal data object BrowseAddAccountRoute : BrowseRoute

@Serializable
internal data class BrowseConnectAccountRoute(
    val provider: ProviderKind,
    /** Re-authentication passes only non-secret account metadata in the route. */
    val presetBaseUrl: String? = null,
    val presetUsername: String? = null,
    val credentialKey: String? = null,
    val returnToBrowseOnBack: Boolean = false,
) : BrowseRoute

@Serializable
internal data object BrowseServerDiscoveryRoute : BrowseRoute

@Serializable
internal data object BrowseSettingsRoute : BrowseRoute

@Serializable
internal data class BrowseManagementRoute(val taskId: String? = null, val sessionId: String? = null) : BrowseRoute
internal data class BrowseManagementDevicesRoute(val deviceId: String? = null) : BrowseRoute

@Serializable
internal data object BrowseFeiniuConnectionPreferenceRoute : BrowseRoute

/** Reachable only once the appearance entry in `SettingsScreen` is re-enabled. */
@Serializable
internal data object BrowseAppearanceRoute : BrowseRoute

@Serializable
internal data object BrowseGlassStyleRoute : BrowseRoute

@Serializable
internal data object BrowsePlayerSettingsRoute : BrowseRoute

@Serializable
internal data object BrowseAddGroupRoute : BrowseRoute

@Serializable
internal data object BrowseAboutPrivacyRoute : BrowseRoute

@Serializable
internal data object BrowseUserAgreementRoute : BrowseRoute

@Serializable
internal data object BrowseWebITongRoute : BrowseRoute

@Serializable
internal data object BrowsePrivacyWebRoute : BrowseRoute

@Serializable
internal data class BrowseLibraryRoute(val collectionId: String) : BrowseRoute

@Serializable
internal data class BrowseMusicDetailRoute(
    val mediaId: String, val title: String, val artist: Boolean, val libraryId: String? = null,
    val origin: BrowseDetailOrigin = BrowseDetailOrigin.Home,
) : BrowseRoute

@Serializable
internal data class BrowsePhotoAlbumRoute(val albumId: String, val title: String, val libraryId: String?, val origin: BrowseDetailOrigin) : BrowseRoute

@Serializable
internal data class BrowsePhotoViewerRoute(val parentId: String?, val photoId: String, val libraryId: String?, val origin: BrowseDetailOrigin) : BrowseRoute

/**
 * The shell selection is part of the navigation route, not of the browser's
 * mutable collection state.  A home shelf can open a detail while the last
 * selected collection is still, for example, 短剧; using that mutable state
 * for the sidebar would then highlight the wrong library.
 */
@Serializable
internal enum class BrowseDetailOrigin {
    Home,
    Chasing,
    Favorite,
    Search,
    Library,
}

@Serializable
internal data class BrowseDetailRoute(
    val mediaId: String,
    /** Legacy VideoDetailPage route parameter used in the secondary metadata line. */
    val mediaName: String? = null,
    val origin: BrowseDetailOrigin = BrowseDetailOrigin.Home,
    val libraryId: String? = null,
) : BrowseRoute

@Serializable
internal data class BrowsePersonRoute(
    val personId: String,
    val personName: String,
    val origin: BrowseDetailOrigin = BrowseDetailOrigin.Home,
    val libraryId: String? = null,
) : BrowseRoute

@Serializable
internal data class BrowseSeasonRoute(
    val seriesId: String,
    val seasonId: String,
    /** Preserved through series -> season -> episode exactly like the ArkUI route chain. */
    val mediaName: String? = null,
    val origin: BrowseDetailOrigin = BrowseDetailOrigin.Home,
    val libraryId: String? = null,
) : BrowseRoute

@Serializable
internal data class BrowseEpisodeRoute(
    val seriesId: String,
    val seasonId: String,
    val episodeId: String,
    val mediaName: String? = null,
    val origin: BrowseDetailOrigin = BrowseDetailOrigin.Home,
    val libraryId: String? = null,
) : BrowseRoute
