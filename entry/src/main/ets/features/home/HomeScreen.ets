import { AppUIState } from "../../entity/AppUIState";
import { HomeTab } from "./hometab/HomeTab";
import { ChasingTab } from "./chasing/ChasingTab";
import { MediaTab } from "./mediatab/MediaTab";
import { MineTab } from "./minetab/MineTab";
import { AppStorageV2 } from "@kit.ArkUI";
import { ConfigurationConstant } from '@kit.AbilityKit'
import { BreakpointTypeEnum } from "../../utils/BreakpointPolicy";
import { HomeScreenUIState, PadMenuItem, PadMenuType } from "./HomeScreenUIState";
import { HomeScreenViewModel } from "./HomeScreenViewModel";
import { getUIAbilityContext } from "../../common/CommonFunc";
import { HomeBackPressedEvent } from "../../events/Events";
import { process } from "@kit.ArkTS";
import { FavoriteListPage } from "../favorite/FavoriteListPage";
import { LoadingView } from "../../ui/components/LoadingView";
import { MediaItem, VideoItem } from "./hometab/HomeUIState";
import { OneMoreUtil } from "../../utils/OneMoreUtil";
import { PadTopTab, TabItem } from "../../component/tabbar/PadTopTab";
import { ThemeUpgradeDialog } from "../../component/ThemeUpgradeDialog";
import { ThemeStyle } from "../../entity/ThemeStyle";
import { themePromptService, ThemeWriteStatus } from "../../theme/ThemePromptService";
import { nativeThemeCapability } from "../../theme/NativeThemeCapability";
import { AppThemeSurfaceResolver } from "../../theme/AppThemeSurfaceResolver";
import { HdsUiCapability } from "../../theme/HdsUiCapability";
import { NativeThemeConstructionProbe } from "../../theme/NativeThemeConstructionProbe";
import { HomeShellKind, HomeShellPolicy } from "./HomeShellPolicy";
import { HomeInsetsPolicy } from "./HomeInsetsPolicy";
import { HomeDestination, HomeDestinationMapper } from "./HomeDestination";
import { HomePhoneNativeTabs } from "./phone/HomePhoneNativeTabs";
import { HomePhoneStandardTabs } from "./phone/HomePhoneStandardTabs";
import { UIConstants } from "../../common/UIConstants";
import { connectMusicHostUIState, MusicHostUIState } from "../music/MusicHostUIState";
import { MusicHostVisibilityPolicy } from "../music/MusicHostVisibilityPolicy";
import { connectMusicPlaybackState, MusicPlaybackState } from "../music/MusicPlaybackState";
import { MediaProviderRegistry } from "../../media/MediaProviderRegistry";
import { BreakpointPolicy } from '../../utils/BreakpointPolicy'
import { HomeContentLayoutPolicy } from './HomeContentLayoutPolicy'
import { HomeSidebarMenu } from './HomeSidebarMenu'
import {
  HomeMinePane,
  HomeSidebarItem,
  HomeSidebarItemType
} from './HomePadNavigation'
import { HomeMineWorkspace } from './HomeMineWorkspace'
import { HomeMediaLibraryContent } from './HomeMediaLibraryContent'
import { SearchPageContent } from '../search/SearchPage'
import { HomePadBackdrop, HomePadHeroLayer } from './HomePadBackdrop'

@ComponentV2
export struct HomeScreen {
  @Param homeScroller: Scroller = new Scroller()
  @Param chasingScroller: Scroller = new Scroller()
  @Param favoriteScroller: Scroller = new Scroller()
  @Param mediaScroller: Scroller = new Scroller()
  @Param mineScroller: Scroller = new Scroller()
  @Local appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;
  private contentTabsController: TabsController = new TabsController()
  @Local ui: HomeScreenUIState = new HomeScreenUIState(this.getUIContext())
  @Local vm: HomeScreenViewModel = new HomeScreenViewModel(this.ui)
  private musicHostUIState: MusicHostUIState = connectMusicHostUIState()
  private musicPlaybackState: MusicPlaybackState = connectMusicPlaybackState()
  @Local mediaAdministrator: boolean = false
  @Local mineManagementAvailable: boolean = false
  @Local tabletHeroItems: VideoItem[] = []
  @Local tabletHeroSelectedIndex: number = 0
  @Local tabletHeroPagePositions: number[] = []
  @Local tabletHeroMainAxisLength: number = 0
  @Local tabletHeroVerticalOffset: number = 0
  private lastTime: number = 0;
  private themePromptChecked: boolean = false
  private themePromptCompleted: boolean = false
  private themePromptTimer: number = -1
  private themeDialogId: number = -1
  private lastSynchronizedShell?: HomeShellKind

  @Monitor('appUIState.currentBreakpoint')
  onBreakpointChange() {
    this.updateFeiniuPointLightEnabled()
    this.appUIState.isBigScreen = !this.appUIState.currentBreakpoint.includes('s')
    this.syncPadShellVisibility()
    this.contentTabsController.changeIndex(this.contentSelectedIndex())
    this.updateRootNavigationTitleBar()
  }

  @Monitor('appUIState.orientation')
  onOrientationChange(): void {
    this.updateFeiniuPointLightEnabled()
    this.syncPadShellVisibility()
    this.contentTabsController.changeIndex(this.contentSelectedIndex())
    this.updateRootNavigationTitleBar()
  }

  @Monitor('appUIState.themeStyle')
  onThemeStyleChange() {
    this.syncPadShellVisibility()
    this.updateFeiniuPointLightEnabled()
    this.updateRootNavigationTitleBar()
  }

  @Monitor('appUIState.nativeThemeAvailable')
  onNativeThemeAvailabilityChange() {
    this.syncPadShellVisibility()
    this.updateRootNavigationTitleBar()
  }

  private resolveShell(): HomeShellKind {
    return HomeShellPolicy.resolve(
      this.appUIState.currentBreakpoint as BreakpointTypeEnum,
      this.appUIState.themeStyle,
      this.appUIState.nativeThemeAvailable,
      this.isPortraitViewport()
    )
  }

  private isPortraitViewport(): boolean {
    return this.appUIState.deviceWidth > 0 &&
      this.appUIState.deviceHeight >= this.appUIState.deviceWidth
  }

  private syncPadShellVisibility(): void {
    const shell = this.resolveShell()
    if ((HomeShellPolicy.usesOverlayDrawer(shell) || HomeShellPolicy.usesEmbeddedSidebar(shell)) &&
      this.ui.selectedDestination === HomeDestination.Chasing) {
      this.ui.selectedDestination = HomeDestination.Home
    }
    if (!HomeShellPolicy.usesOverlayDrawer(shell) && !HomeShellPolicy.usesEmbeddedSidebar(shell) &&
      this.ui.selectedDestination === HomeDestination.Search) {
      this.ui.selectedDestination = HomeDestination.Home
    }
    this.ui.isMenuModalVisible = false
    if (HomeShellPolicy.usesOverlayDrawer(shell)) {
      this.ui.isLeftSidebarVisible = false
    } else if (HomeShellPolicy.usesEmbeddedSidebar(shell) &&
      this.lastSynchronizedShell !== HomeShellKind.LargeSidebar) {
      this.ui.isLeftSidebarVisible = true
    } else if (!HomeShellPolicy.usesEmbeddedSidebar(shell)) {
      this.ui.isLeftSidebarVisible = false
    }
    this.lastSynchronizedShell = shell
  }

  private openPadMenu(): void {
    const shell = this.resolveShell()
    if (HomeShellPolicy.usesEmbeddedSidebar(shell)) {
      this.ui.isLeftSidebarVisible = true
    } else if (HomeShellPolicy.usesOverlayDrawer(shell)) {
      this.ui.isMenuModalVisible = true
    }
  }

  private get shell(): HomeShellKind {
    return this.resolveShell()
  }

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable)
  }

  private get navigationChromeInset(): number {
    return HomeInsetsPolicy.navigationChromeInset(
      this.shell,
      this.appUIState.safeBottom
    )
  }

  private get contentBottomInset(): number {
    return HomeInsetsPolicy.contentBottomInset(
      this.shell,
      this.appUIState.safeBottom,
      this.musicMiniPlayerVisible(),
      this.useNativeSurface()
    )
  }

  private musicMiniPlayerVisible(): boolean {
    if (!this.musicCapabilityEnabled()) {
      return false
    }
    return MusicHostVisibilityPolicy.shouldShow({
      queueSize: this.musicPlaybackState.queue.length,
      isLogin: this.appUIState.isLogin,
      isConnecting: this.appUIState.isConnecting,
      loginVisible: this.musicHostUIState.loginVisible,
      videoPlayerVisible: this.musicHostUIState.videoPlayerVisible,
      livePlayerVisible: this.musicHostUIState.livePlayerVisible,
      musicPlayerVisible: this.musicHostUIState.musicPlayerVisible,
      photoViewerVisible: this.musicHostUIState.photoViewerVisible
    })
  }

  private musicCapabilityEnabled(): boolean {
    return MediaProviderRegistry.instance().getActiveSession()?.capabilities.music !== false
  }

  private compactPlayerChrome(): boolean {
    return MusicHostVisibilityPolicy.compactHomeChrome(
      this.musicHostUIState.homeVisible,
      this.shell,
      this.ui.selectedDestination === HomeDestination.Home,
      this.appUIState.rootNavigationHomeLibraryPinned,
      this.musicMiniPlayerVisible()
    )
  }

  private get nativeFromHomeTopInset(): number {
    if (this.shell !== HomeShellKind.PhoneNativeHds) {
      return 0
    }
    return Math.max(0, this.appUIState.safeTop) + UIConstants.ACTION_BAR_HEIGHT
  }

  private usesPadMediaContent(): boolean {
    const shell = this.resolveShell()
    return shell === HomeShellKind.MediumDrawer || shell === HomeShellKind.LargeSidebar
  }

  private phoneSelectedIndex(): number {
    return HomeDestinationMapper.phoneIndex(this.ui.selectedDestination)
  }

  private contentSelectedIndex(): number {
    if (this.usesPadMediaContent()) {
      if (this.ui.selectedDestination === HomeDestination.Search) {
        return 5
      }
      if (this.ui.selectedDestination === HomeDestination.Favorite) {
        return 2
      }
      if (this.ui.selectedDestination === HomeDestination.Media) {
        return 3
      }
      if (this.ui.selectedDestination === HomeDestination.Mine) {
        return 4
      }
      return 0
    }
    return HomeDestinationMapper.contentIndex(
      this.ui.selectedDestination,
      false,
      this.ui.lastSelectedMediaIndex
    )
  }

  private selectedPadMediaItem(): MediaItem | undefined {
    if (!this.usesPadMediaContent()) {
      return undefined
    }
    const selectedIndex = this.effectiveMediaDataSourceIndex()
    if (selectedIndex < 0) {
      return undefined
    }
    return this.vm.datasource.getData(selectedIndex).extra
  }

  private isMediaDataSourceIndex(index: number): boolean {
    return index >= 0 && index < this.vm.datasource.totalCount() &&
      this.vm.datasource.getData(index).type === PadMenuType.ITEM &&
      this.vm.datasource.getData(index).extra !== undefined
  }

  private effectiveMediaDataSourceIndex(): number {
    if (this.isMediaDataSourceIndex(this.ui.selectedMediaDataSourceIndex)) {
      return this.ui.selectedMediaDataSourceIndex
    }
    if (this.isMediaDataSourceIndex(this.ui.lastSelectedMediaIndex)) {
      return this.ui.lastSelectedMediaIndex
    }
    const firstMediaIndex = this.vm.datasource.getFirstMediaItemIndex()
    return this.isMediaDataSourceIndex(firstMediaIndex) ? firstMediaIndex : -1
  }

  private selectDefaultMediaItem(): void {
    const selectedIndex = this.effectiveMediaDataSourceIndex()
    this.ui.selectedMediaDataSourceIndex = selectedIndex
    if (selectedIndex >= 0) {
      this.ui.lastSelectedMediaIndex = selectedIndex
    }
  }

  private homeContentKey(item: PadMenuItem, index: number): string {
    const mediaId = item.extra?.id
    if (mediaId !== undefined && mediaId.length > 0) {
      return `media:${item.type}:${mediaId}`
    }
    return `menu:${item.type}:${index}`
  }

  private selectPhoneIndex(index: number): void {
    this.ui.selectedDestination = HomeDestinationMapper.fromPhoneIndex(index)
  }

  @Monitor('ui.selectedDestination')
  onDestinationChange(): void {
    this.contentTabsController.changeIndex(this.contentSelectedIndex())
    this.updateRootNavigationTitleBar()
  }

  @Monitor('ui.selectedMediaDataSourceIndex')
  onSelectedMediaChange(): void {
    this.contentTabsController.changeIndex(this.contentSelectedIndex())
  }

  private updateFeiniuPointLightEnabled(): void {
    const shell = this.resolveShell()
    const supportsPointLight = HdsUiCapability.supportsSystemMaterial()
    const isWideShell = shell === HomeShellKind.MediumDrawer ||
      shell === HomeShellKind.LargeSidebar
    this.appUIState.feiniuPointLightEnabled =
      this.appUIState.themeStyle === ThemeStyle.Feiniu &&
      supportsPointLight &&
      isWideShell
  }

  private rootNavigationTitle(): string {
    if (this.ui.selectedDestination === HomeDestination.Search) {
      return '搜索'
    }
    if (this.ui.selectedDestination === HomeDestination.Chasing) {
      return '追剧'
    }
    if (this.ui.selectedDestination === HomeDestination.Favorite) {
      return '收藏'
    }
    if (this.ui.selectedDestination === HomeDestination.Media) {
      return '媒体库'
    }
    if (this.ui.selectedDestination === HomeDestination.Mine) {
      return '我的'
    }
    return '首页'
  }

  private publishRootNavigationChrome(visible: boolean, title: string, homeActionsVisible: boolean,
    mediaAdminActionsVisible: boolean): void {
    this.appUIState.rootNavigationTitleBarVisible = visible
    this.appUIState.rootNavigationTitle = title
    this.appUIState.rootNavigationHomeActionsVisible = homeActionsVisible
    this.appUIState.rootNavigationMediaAdminActionsVisible = mediaAdminActionsVisible
  }

  private updateRootNavigationTitleBar(): void {
    const visible = this.resolveShell() === HomeShellKind.PhoneNativeHds
    this.publishRootNavigationChrome(
      visible,
      this.rootNavigationTitle(),
      visible && this.ui.selectedDestination === HomeDestination.Home,
      visible && this.ui.selectedDestination === HomeDestination.Media && this.mediaAdministrator
    )
  }

  aboutToAppear() {
    this.appUIState.isBigScreen = !this.appUIState.currentBreakpoint.includes('s')
    this.syncPadShellVisibility()
    this.updateFeiniuPointLightEnabled()
    this.updateRootNavigationTitleBar()
    this.vm.init()
    getUIAbilityContext().eventHub.on(HomeBackPressedEvent, () => {
      //点两次退出
      let time = new Date().getTime();
      if (time - this.lastTime > 1000) {
        this.lastTime = time;
        this.getUIContext().getPromptAction().showToast({
          message: '再次返回退出应用',
          duration: 1000
        });
      } else {
        new process.ProcessManager().exit(0)
      }
    })
    this.themePromptTimer = setTimeout(() => {
      this.showThemePromptIfNeeded()
    }, 350)
  }

  aboutToDisappear() {
    this.publishRootNavigationChrome(false, '首页', false, false)
    if (this.themePromptTimer >= 0) {
      clearTimeout(this.themePromptTimer)
      this.themePromptTimer = -1
    }
    getUIAbilityContext().eventHub.off(HomeBackPressedEvent)
  }

  // CustomBuilderAdapter: home-theme-upgrade-dialog
  @Builder
  private themePromptDialogBuilder() {
    ThemeUpgradeDialog({
      onSelectFeiniu: () => {
        this.saveThemePrompt(ThemeStyle.Feiniu)
      },
      onSelectNative: () => {
        this.saveThemePrompt(ThemeStyle.Native)
      },
      onDismiss: () => {
        this.saveThemePrompt(ThemeStyle.Feiniu)
      }
    })
  }

  private showThemePromptIfNeeded(): void {
    const capability = nativeThemeCapability.current()
    if (this.themePromptChecked || !capability.available || !themePromptService.shouldShow()) {
      return
    }
    this.themePromptChecked = true
    this.themePromptCompleted = false
    const promptAction = this.getUIContext().getPromptAction()
    const onWillDismiss = (action: DismissDialogAction): void => {
      if (!this.themePromptCompleted && !this.saveThemePrompt(ThemeStyle.Feiniu, false)) {
        return
      }
      this.themeDialogId = -1
      action.dismiss()
    }
    let openPromise: Promise<number>
    if (this.appUIState.systemMaterialAvailable) {
      openPromise = promptAction.openCustomDialog({
        builder: () => {
          this.themePromptDialogBuilder()
        },
        alignment: DialogAlignment.Center,
        autoCancel: true,
        backgroundColor: Color.Transparent,
        backgroundBlurStyle: BlurStyle.NONE,
        systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial(),
        onWillDismiss
      })
    } else {
      openPromise = promptAction.openCustomDialog({
        builder: () => {
          this.themePromptDialogBuilder()
        },
        alignment: DialogAlignment.Center,
        autoCancel: true,
        backgroundColor: Color.Transparent,
        backgroundBlurStyle: BlurStyle.NONE,
        onWillDismiss
      })
    }
    openPromise.then((dialogId: number) => {
      this.themeDialogId = dialogId
      if (this.themePromptCompleted) {
        try {
          this.getUIContext().getPromptAction().closeCustomDialog(dialogId)
          this.themeDialogId = -1
        } catch (error) {
        }
      }
    }).catch(() => {
      this.themePromptChecked = false
      this.themePromptCompleted = false
    })
  }

  private saveThemePrompt(style: ThemeStyle, closeDialog: boolean = true): boolean {
    try {
      const result = themePromptService.completePrompt(style, nativeThemeCapability.current())
      if (result.status !== ThemeWriteStatus.Success) {
        throw new Error(result.status)
      }
      this.appUIState.themeStyle = result.effectiveTheme
      this.appUIState.themePromptPending = false
      this.themePromptCompleted = true
      if (closeDialog && this.themeDialogId >= 0) {
        this.getUIContext().getPromptAction().closeCustomDialog(this.themeDialogId)
        this.themeDialogId = -1
      }
      return true
    } catch (error) {
      this.getUIContext().getPromptAction().showToast({
        message: $r('app.string.theme_save_failed')
      })
      return false
    }
  }

  private getPadTopTabItems(): TabItem[] {
    return [
      new TabItem('首页', $r('app.media.ic_home_sel'), $r('app.media.ic_home_unsel')),
      new TabItem('收藏', $r('app.media.ic_favorite_sel'), $r('app.media.ic_favorite_unsel')),
      new TabItem('媒体库', $r('app.media.ic_media_sel'), $r('app.media.ic_media_unsel')),
      new TabItem('我的', $r('app.media.ic_mine_sel'), $r('app.media.ic_mine_unsel'))
    ]
  }

  private getPadTopTabSelectIndex(): number {
    if (this.ui.selectedDestination === HomeDestination.Search) {
      return -1
    }
    if (this.ui.selectedDestination === HomeDestination.Favorite) {
      return 1
    }
    if (this.ui.selectedDestination === HomeDestination.Media) {
      return 2
    }
    if (this.ui.selectedDestination === HomeDestination.Mine) {
      return 3
    }
    return 0
  }

  private selectPadTopTab(index: number): void {
    if (index === 1) {
      this.ui.selectedDestination = HomeDestination.Favorite
    } else if (index === 2) {
      this.selectDefaultMediaItem()
      this.ui.selectedDestination = HomeDestination.Media
    } else if (index === 3) {
      this.ui.selectedDestination = HomeDestination.Mine
    } else {
      this.ui.selectedDestination = HomeDestination.Home
    }
  }

  private padChromeHeight(): number {
    const topTabsVisible = HomeShellPolicy.usesOverlayDrawer(this.shell) ||
      (HomeShellPolicy.usesEmbeddedSidebar(this.shell) && !this.ui.isLeftSidebarVisible)
    return topTabsVisible ? 88 : 0
  }

  private tabletHeroLeadingOverflow(): number {
    if (!HomeShellPolicy.usesEmbeddedSidebar(this.shell)) {
      return 0
    }
    return this.ui.isLeftSidebarVisible ? 252 : 12
  }

  private tabletHomeGridColumnCount(): number {
    if (HomeShellPolicy.usesOverlayDrawer(this.shell)) {
      return 4
    }
    if (HomeShellPolicy.usesEmbeddedSidebar(this.shell)) {
      return this.ui.isLeftSidebarVisible ? 5 : 6
    }
    return 0
  }

  private updateTabletHeroState(items: VideoItem[], selectedIndex: number,
    pagePositions: number[], mainAxisLength: number, verticalOffset: number): void {
    this.tabletHeroItems = items
    this.tabletHeroSelectedIndex = selectedIndex
    this.tabletHeroPagePositions = pagePositions
    this.tabletHeroMainAxisLength = mainAxisLength
    this.tabletHeroVerticalOffset = verticalOffset
  }

  private showTabletHomeBackdrop(): boolean {
    return (HomeShellPolicy.usesOverlayDrawer(this.shell) ||
      HomeShellPolicy.usesEmbeddedSidebar(this.shell)) &&
      this.ui.selectedDestination === HomeDestination.Home && this.tabletHeroItems.length > 0
  }

  private rootSidebarItem(id: string, title: string, icon: ResourceStr,
    destination: HomeDestination, badge: number = 0): HomeSidebarItem {
    const item = new HomeSidebarItem(id, HomeSidebarItemType.Item, title, icon, badge)
    item.destination = destination
    return item
  }

  private searchSidebarItem(): HomeSidebarItem {
    const item = this.rootSidebarItem('root:search', '搜索', '', HomeDestination.Search)
    item.symbolIcon = $r('sys.symbol.magnifyingglass')
    return item
  }

  private titleSidebarItem(id: string, title: string): HomeSidebarItem {
    return new HomeSidebarItem(id, HomeSidebarItemType.Title, title)
  }

  private sidebarItems(): HomeSidebarItem[] {
    void this.ui.padMenuDataVersion
    const items: HomeSidebarItem[] = [
      this.searchSidebarItem(),
      this.rootSidebarItem('root:home', '首页', $r('app.media.ic_home_sel'), HomeDestination.Home),
      this.rootSidebarItem('root:favorite', '收藏', $r('app.media.ic_favorite_sel'),
        HomeDestination.Favorite, this.ui.favoriteCount)
    ]
    for (let index = 0; index < this.vm.datasource.totalCount(); index++) {
      const sourceItem = this.vm.datasource.getData(index)
      if (sourceItem.type === PadMenuType.TITLE) {
        items.push(this.titleSidebarItem(`media-title:${index}`, sourceItem.title))
      } else if (sourceItem.extra) {
        const mediaItem = new HomeSidebarItem(
          `media:${sourceItem.extra.id}`,
          HomeSidebarItemType.Item,
          sourceItem.title,
          sourceItem.selectedIcon,
          sourceItem.badge
        )
        mediaItem.mediaDataSourceIndex = index
        items.push(mediaItem)
      }
    }
    return items
  }

  private sidebarBottomItems(): HomeSidebarItem[] {
    return [
      this.rootSidebarItem('root:mine', '我的', $r('app.media.ic_mine_sel'), HomeDestination.Mine)
    ]
  }

  private selectedSidebarId(): string {
    if (this.ui.selectedDestination === HomeDestination.Media) {
      const selectedIndex = this.effectiveMediaDataSourceIndex()
      if (selectedIndex < 0) {
        return ''
      }
      const mediaItem = this.vm.datasource.getData(selectedIndex)
      return mediaItem.extra ? `media:${mediaItem.extra.id}` : 'root:media'
    }
    return `root:${this.ui.selectedDestination}`
  }

  private selectSidebarItem(item: HomeSidebarItem): void {
    if (item.mediaDataSourceIndex >= 0) {
      this.ui.selectedMediaDataSourceIndex = item.mediaDataSourceIndex
      this.ui.lastSelectedMediaIndex = item.mediaDataSourceIndex
      this.ui.selectedDestination = HomeDestination.Media
      return
    }
    if (item.minePane !== undefined) {
      this.ui.selectedMinePane = item.minePane
      this.ui.selectedDestination = HomeDestination.Mine
      return
    }
    if (item.destination === HomeDestination.Media) {
      this.ui.selectedMediaDataSourceIndex = -1
    } else if (item.destination === HomeDestination.Mine) {
      this.ui.selectedMinePane = HomeMinePane.Overview
    }
    if (item.destination !== undefined) {
      this.ui.selectedDestination = item.destination
    }
  }

  private updateHomeContentMetrics(area: Area): void {
    const width = Number(area.width)
    if (!Number.isFinite(width) || width <= 0) {
      return
    }
    if (Math.abs(this.appUIState.homeContentWidth - width) < 0.5) {
      return
    }
    this.appUIState.homeContentWidth = width
    this.appUIState.homeContentBreakpoint = BreakpointPolicy.fromWidth(width)
  }










  private runtimeShellProbeLabel(): string {
    return NativeThemeConstructionProbe.label(
      this.appUIState.deviceWidth,
      this.appUIState.currentBreakpoint,
      this.shell
    )
  }



  build() {
    Stack({ alignContent: Alignment.TopStart }) {
      if (this.showTabletHomeBackdrop()) {
        HomePadBackdrop({
          items: this.tabletHeroItems,
          selectedIndex: this.tabletHeroSelectedIndex,
          pagePositions: this.tabletHeroPagePositions,
          mainAxisLength: this.tabletHeroMainAxisLength,
          fallbackPageWidth: this.appUIState.deviceWidth,
          canvasColor: this.useNativeSurface() ?
            $r('app.color.native_canvas_background') : $r('app.color.bg_main'),
          darkMode: this.appUIState.colorMode === ConfigurationConstant.ColorMode.COLOR_MODE_DARK
        })
        HomePadHeroLayer({
          items: this.tabletHeroItems,
          selectedIndex: this.tabletHeroSelectedIndex,
          pagePositions: this.tabletHeroPagePositions,
          mainAxisLength: this.tabletHeroMainAxisLength,
          fallbackPageWidth: this.appUIState.deviceWidth,
          heroHeight: HomeContentLayoutPolicy.tabletHeroHeight(this.appUIState, true),
          verticalOffset: this.tabletHeroVerticalOffset
        })
      }
      if (this.shell === HomeShellKind.PhoneNativeHds) {
        HomePhoneNativeTabs({
          selectedIndex: this.phoneSelectedIndex(),
          safeBottom: this.appUIState.safeBottom,
          compactPlayerChrome: this.compactPlayerChrome(),
          homeContentScroller: this.homeScroller,
          favoriteContentScroller: this.favoriteScroller,
          mediaContentScroller: this.mediaScroller,
          mineContentScroller: this.mineScroller,
          contentTopInset: this.nativeFromHomeTopInset,
          contentBottomInset: this.contentBottomInset,
          onSelectedIndexChange: (index: number) => {
            this.selectPhoneIndex(index)
          },
          onMediaAdministratorStateChange: (isAdministrator: boolean) => {
            this.mediaAdministrator = isAdministrator
            this.updateRootNavigationTitleBar()
          }
        })
      } else {
        Stack({ alignContent: Alignment.Bottom }) {
          Row() {
            if (HomeShellPolicy.usesEmbeddedSidebar(this.shell)) {
              // Keep the content in the same column as the legacy large-screen
              // chrome: 12vp outer gutter plus the visible 240vp sidebar.
              Blank().width(this.ui.isLeftSidebarVisible ? 252 : 12)
            }
            Column() {
              Stack() {
                Tabs({
                  index: this.contentSelectedIndex(),
                  controller: this.contentTabsController
                }) {
                  TabContent() {
                    HomeTab({
                      active: this.ui.selectedDestination === HomeDestination.Home,
                      useHomeContentWidth: this.usesPadMediaContent(),
                      tabletHeroLeadingOverflow: this.tabletHeroLeadingOverflow(),
                      latestGridColumnCount: this.tabletHomeGridColumnCount(),
                      stickyHeaderAnchorY: this.padChromeHeight(),
                      stickyHeaderPinnedTopInset: this.padChromeHeight() > 0 ?
                        this.padChromeHeight() : Math.max(0, this.appUIState.safeTop),
                      contentBottomInset: this.contentBottomInset,
                      contentScroller: this.homeScroller,
                      onTabletHeroStateChange: (items: VideoItem[], selectedIndex: number,
                        pagePositions: number[], mainAxisLength: number, verticalOffset: number): void => {
                        this.updateTabletHeroState(items, selectedIndex, pagePositions, mainAxisLength, verticalOffset)
                      }
                    })
                  }
                  TabContent() {
                    ChasingTab({
                      fromHome: true,
                      contentTopInset: this.nativeFromHomeTopInset,
                      contentBottomInset: this.contentBottomInset,
                      contentScroller: this.chasingScroller
                    })
                  }
                  TabContent() {
                    FavoriteListPage({
                      fromHome: true,
                      useHomeContentWidth: this.usesPadMediaContent(),
                      contentTopInset: this.nativeFromHomeTopInset,
                      contentBottomInset: this.contentBottomInset,
                      contentScroller: this.favoriteScroller
                    })
                  }
                  TabContent() {
                    if (this.selectedPadMediaItem() !== undefined) {
                      HomeMediaLibraryContent({
                        mediaItem: this.selectedPadMediaItem() as MediaItem,
                        useHomeContentWidth: true,
                        contentBottomInset: this.contentBottomInset
                      })
                    } else {
                      MediaTab({
                        contentBottomInset: this.contentBottomInset,
                        rootTitleBarOwned: false,
                        contentScroller: this.mediaScroller,
                        onAdministratorStateChange: (isAdministrator: boolean) => {
                          this.mediaAdministrator = isAdministrator
                          this.updateRootNavigationTitleBar()
                        }
                      })
                    }
                  }
                  TabContent() {
                    if (this.usesPadMediaContent()) {
                      HomeMineWorkspace({
                        selectedPane: this.ui.selectedMinePane,
                        contentWidth: this.appUIState.homeContentWidth,
                        onAdministratorStateChange: (available: boolean) => {
                          this.mineManagementAvailable = available
                        },
                        onPaneFallback: (pane: HomeMinePane) => {
                          this.ui.selectedMinePane = pane
                        }
                      })
                    } else {
                      MineTab({
                        contentBottomInset: this.contentBottomInset,
                        compactTopInset: false,
                        contentScroller: this.mineScroller
                      })
                    }
                  }
                  TabContent() {
                    SearchPageContent({
                      showLegacyActionBar: !this.useNativeSurface(),
                      embedded: true
                    })
                  }

                  LazyForEach(this.vm.datasource, (item: PadMenuItem) => {
                    TabContent() {
                      if (item.extra) {
                        HomeMediaLibraryContent({
                          mediaItem: item.extra as MediaItem,
                          useHomeContentWidth: this.usesPadMediaContent(),
                          contentBottomInset: this.contentBottomInset
                        })
                      } else {
                        Stack()
                      }
                    }
                  }, (item: PadMenuItem, index: number) => this.homeContentKey(item, index))
                }
                .barHeight(0)
                .barWidth(0)
                .vertical(false)
                .scrollable(false)
                .animationDuration(0)
                .width('100%')
                .height('100%')
              }
              .layoutWeight(1)
              .width('100%')
            }
            .layoutWeight(1)
            .height('100%')
            .onAreaChange((_oldValue: Area, newValue: Area): void => {
              if (this.usesPadMediaContent()) {
                this.updateHomeContentMetrics(newValue)
              }
            })
          }
          .width('100%')
          .height('100%')

          if (HomeShellPolicy.usesEmbeddedSidebar(this.shell) || HomeShellPolicy.usesOverlayDrawer(this.shell)) {
            Stack() {
              Row() {
                Column() {
                  HomeSidebarMenu({
                    items: this.sidebarItems(),
                    bottomItems: this.sidebarBottomItems(),
                    appUIState: this.appUIState,
                    selectedId: this.selectedSidebarId(),
                    shell: (this.shell),
                    showCloseButton: HomeShellPolicy.usesEmbeddedSidebar(this.resolveShell()) &&
                      this.ui.isLeftSidebarVisible && !OneMoreUtil.isPc(),
                    nativeSurface: this.useNativeSurface(),
                    onClose: () => {
                      this.ui.context.animateTo({ duration: 300, curve: Curve.FastOutSlowIn }, () => {
                        this.ui.isLeftSidebarVisible = false
                      })
                    },
                    onItemSelect: (item: HomeSidebarItem) => {
                      this.selectSidebarItem(item)
                    }
                  })
                }
                .width(240)
                .height('100%')
                .padding({
                  top: this.appUIState.safeTop + 12,
                  bottom: this.appUIState.safeBottom
                })
                .visibility((HomeShellPolicy.usesEmbeddedSidebar((this.shell)) && this.ui.isLeftSidebarVisible) ?
                  Visibility.Visible : Visibility.None)
                .transition(TransitionEffect.asymmetric(
                  TransitionEffect.scale({ x: 0, y: 0 })
                    .combine(TransitionEffect.translate({ x: 120, y: -300 }))
                    .animation({ duration: 300, curve: Curve.FastOutSlowIn }),
                  TransitionEffect.scale({ x: 0, y: 0 })
                    .combine(TransitionEffect.translate({ x: 120, y: -300 }))
                    .animation({ duration: 300, curve: Curve.FastOutSlowIn })
                ))

                Stack() {
                  PadTopTab({
                    list: this.getPadTopTabItems(),
                    selectIndex: this.getPadTopTabSelectIndex(),
                    onItemClick: (index: number) => {
                      this.selectPadTopTab(index)
                    },
                    onMenuButtonClick: () => {
                      this.ui.context.animateTo({ duration: 300, curve: Curve.FastOutSlowIn }, () => {
                        this.openPadMenu()
                      })
                    }
                  })
                    .margin({
                      top: this.appUIState.safeTop
                    })
                    .visibility((HomeShellPolicy.usesEmbeddedSidebar((this.shell)) && this.ui.isLeftSidebarVisible) ?
                      Visibility.None : Visibility.Visible)
                }
                .layoutWeight(1)
                .height('100%')
                .alignContent(Alignment.Top)
                .hitTestBehavior(HitTestMode.Transparent)
              }
              .width('100%')
              .height('100%')
              .hitTestBehavior(HitTestMode.Transparent)

              if (this.ui.isLoading) {
                LoadingView()
              }

              if (this.ui.isMenuModalVisible && HomeShellPolicy.usesOverlayDrawer((this.shell))) {
                Stack() {
                  Column()
                    .width('100%')
                    .height('100%')
                    .backgroundColor('rgba(0,0,0,0.5)')
                    .onClick(() => {
                      this.ui.context.animateTo({ duration: 300, curve: Curve.FastOutSlowIn }, () => {
                        this.ui.isMenuModalVisible = false
                      })
                    })
                  Row() {
                    Column() {
                      HomeSidebarMenu({
                        items: this.sidebarItems(),
                        bottomItems: this.sidebarBottomItems(),
                        appUIState: this.appUIState,
                        selectedId: this.selectedSidebarId(),
                        shell: (this.shell),
                        showCloseButton: false,
                        nativeSurface: this.useNativeSurface(),
                        onItemSelect: (item: HomeSidebarItem) => {
                          this.selectSidebarItem(item)
                          this.ui.context.animateTo({ duration: 300, curve: Curve.FastOutSlowIn }, () => {
                            this.ui.isMenuModalVisible = false
                          })
                        }
                      })
                    }
                    .width(240)
                    .height('100%')
                    .margin({ left: 12 })
                    .padding({
                      top: this.appUIState.safeTop + 12,
                      bottom: this.appUIState.safeBottom,
                    })
                    .transition(TransitionEffect.translate({ x: -240 }).animation({ duration: 300, curve: Curve.FastOutSlowIn }))

                    Blank()
                      .layoutWeight(1)
                      .onClick(() => {
                        this.ui.context.animateTo({ duration: 300, curve: Curve.FastOutSlowIn }, () => {
                          this.ui.isMenuModalVisible = false
                        })
                      })
                  }
                  .width('100%')
                  .height('100%')
                  .alignItems(VerticalAlign.Top)
                  .justifyContent(FlexAlign.Start)
                }
                .width('100%')
                .height('100%')
                .alignContent(Alignment.Start)
              }
            }
            .width('100%')
            .height('100%')
            .alignContent(Alignment.TopStart)
            .padding({
              left: HomeShellPolicy.usesEmbeddedSidebar((this.shell)) ? 12 : 0
            })
            .hitTestBehavior(HitTestMode.Transparent)
          } else {
            HomePhoneStandardTabs({
              selectedIndex: this.phoneSelectedIndex(),
              navigationChromeInset: this.navigationChromeInset,
              onSelectedIndexChange: (index: number) => {
                this.selectPhoneIndex(index)
              }
            })
          }
        }
        .width('100%')
        .height('100%')
      }

      Text(this.runtimeShellProbeLabel())
        .fontSize(1)
        .width(1)
        .height(1)
        .opacity(0.01)
        .accessibilityText(this.runtimeShellProbeLabel())
        .hitTestBehavior(HitTestMode.None)
    }
    .width('100%')
    .height('100%')
  }
}
