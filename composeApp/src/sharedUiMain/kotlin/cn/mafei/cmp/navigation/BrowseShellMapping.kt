package cn.mafei.cmp

import cn.mafei.cmp.common.ui.MaFeiGlyph
import cn.mafei.cmp.common.ui.MaFeiNavItem

internal const val HomeDestination = "home"
internal const val ChasingDestination = "chasing"
internal const val FavoriteDestination = "favorite"
internal const val LibraryDestination = "library"
internal const val MineDestination = "mine"

/** The active selection comes from the route, never the browser's last loaded collection. */
internal data class BrowseShellContext(
    val destination: String,
    val libraryId: String? = null,
    val origin: BrowseDetailOrigin? = null,
) {
    val topTabDestination: String
        get() = libraryId ?: destination.takeUnless { it == LibraryDestination }.orEmpty()
}

private fun BrowseDetailOrigin.shellContext(libraryId: String?): BrowseShellContext = when (this) {
    BrowseDetailOrigin.Home -> BrowseShellContext(HomeDestination, origin = this)
    BrowseDetailOrigin.Chasing -> BrowseShellContext(ChasingDestination, origin = this)
    BrowseDetailOrigin.Favorite -> BrowseShellContext(FavoriteDestination, origin = this)
    // Search has its own icon selection; sharing the Home host does not select Home.
    BrowseDetailOrigin.Search -> BrowseShellContext("", origin = this)
    BrowseDetailOrigin.Library -> BrowseShellContext(LibraryDestination, libraryId, this)
}

internal fun BrowseRoute.shellContext(): BrowseShellContext = when (this) {
    BrowseHomeRoute -> BrowseDetailOrigin.Home.shellContext(null)
    BrowseChasingRoute -> BrowseDetailOrigin.Chasing.shellContext(null)
    BrowseFavoriteRoute -> BrowseDetailOrigin.Favorite.shellContext(null)
    is BrowseLatestRoute -> BrowseDetailOrigin.Home.shellContext(null)
    BrowseSearchRoute -> BrowseDetailOrigin.Search.shellContext(null)
    BrowseLibraryIndexRoute -> BrowseDetailOrigin.Library.shellContext(null)
    is BrowseLibraryRoute -> BrowseDetailOrigin.Library.shellContext(collectionId)
    is BrowseMusicDetailRoute -> origin.shellContext(libraryId)
    is BrowsePhotoAlbumRoute -> origin.shellContext(libraryId)
    is BrowsePhotoViewerRoute -> origin.shellContext(libraryId)
    is BrowseDetailRoute -> origin.shellContext(libraryId)
    is BrowsePersonRoute -> origin.shellContext(libraryId)
    is BrowseSeasonRoute -> origin.shellContext(libraryId)
    is BrowseEpisodeRoute -> origin.shellContext(libraryId)
    BrowseMineRoute,
    BrowseMineHistoryRoute,
    BrowseAccountSwitchRoute,
    BrowseAddAccountRoute,
    is BrowseConnectAccountRoute,
    BrowseServerDiscoveryRoute,
    BrowseSettingsRoute,
    is BrowseManagementRoute,
    is BrowseManagementDevicesRoute,
    BrowseFeiniuConnectionPreferenceRoute,
    BrowseAppearanceRoute,
    BrowseGlassStyleRoute,
    BrowsePlayerSettingsRoute,
    BrowseAddGroupRoute,
    BrowseAboutPrivacyRoute,
    BrowseUserAgreementRoute,
    BrowseWebITongRoute,
    BrowsePrivacyWebRoute -> BrowseShellContext(MineDestination)
}

/** Route-derived inputs to the geometry-only shell. No stack, account or window state is owned here. */
internal data class BrowseShellPresentation(
    val context: BrowseShellContext,
    val mediaDetail: Boolean,
    val personDetail: Boolean,
    val detail: Boolean,
    val fullPage: Boolean,
    val secondaryContent: Boolean,
    val search: Boolean,
) {
    // Full-page content hides the phone dock, while wide-screen navigation stays available.
    fun showBottomNavigation(
        isPhoneNavigation: Boolean,
        accountPresented: Boolean,
        accountStackDepth: Int,
    ): Boolean = !isPhoneNavigation ||
        (!fullPage && !(accountPresented && accountStackDepth > 1))
}

internal fun BrowseRoute.shellPresentation(): BrowseShellPresentation {
    val mediaDetail = this is BrowseDetailRoute || this is BrowseSeasonRoute || this is BrowseEpisodeRoute
    val personDetail = this is BrowsePersonRoute
    val detail = mediaDetail || personDetail || this is BrowseMusicDetailRoute ||
        this is BrowsePhotoAlbumRoute || this is BrowsePhotoViewerRoute
    val secondaryContent = detail || this is BrowseLatestRoute
    return BrowseShellPresentation(
        context = shellContext(),
        mediaDetail = mediaDetail,
        personDetail = personDetail,
        detail = detail,
        fullPage = secondaryContent || this == BrowseSearchRoute ||
            this == BrowseFavoriteRoute || this is BrowseLibraryRoute,
        secondaryContent = secondaryContent,
        search = this == BrowseSearchRoute,
    )
}

internal data class BrowseNavigationItems(
    val bottom: List<MaFeiNavItem>,
    val sidebar: List<MaFeiNavItem>,
    val topTabs: List<MaFeiNavItem>,
)

/** Retained library shortcuts and the currently selected route are deliberately independent. */
internal fun browseNavigationItems(
    chasingEnabled: Boolean,
    libraries: List<MaFeiNavItem>,
    libraryTabId: String?,
): BrowseNavigationItems {
    val bottom = buildList {
        add(MaFeiNavItem(HomeDestination, "首页", MaFeiGlyph.Home))
        if (chasingEnabled) add(MaFeiNavItem(ChasingDestination, "追剧", MaFeiGlyph.History))
        add(MaFeiNavItem(LibraryDestination, "媒体库", MaFeiGlyph.Library))
        add(MaFeiNavItem(MineDestination, "我的", MaFeiGlyph.Account))
    }
    // Favorite belongs to the library entry on phones and has its own entry on wide screens.
    val sidebar = bottom.filterNot { it.key == ChasingDestination }.toMutableList().apply {
        add(1, MaFeiNavItem(FavoriteDestination, "收藏", MaFeiGlyph.Favorite))
    }
    val topTabs = buildList {
        add(bottom.first { it.key == HomeDestination })
        add(sidebar.first { it.key == FavoriteDestination })
        if (libraryTabId != null) libraries.firstOrNull { it.key == libraryTabId }?.let(::add)
    }
    // The shell renders the trailing Search action and account area separately.
    return BrowseNavigationItems(bottom, sidebar, topTabs)
}
