import { AppUIState } from "../../entity/AppUIState";
import { HomeTab } from "./hometab/HomeTab";
import { ChasingTab } from "./chasing/ChasingTab";
import { MediaTab } from "./mediatab/MediaTab";
import { MineTab } from "./minetab/MineTab";
import { AppStorageV2 } from "@kit.ArkUI";
import { BreakpointTypeEnum } from "../../utils/BreakpointPolicy";
import { HomeScreenUIState, PadMenuItem, PadMenuType } from "./HomeScreenUIState";
import { HomeScreenViewModel } from "./HomeScreenViewModel";
import { getUIAbilityContext } from "../../common/CommonFunc";
import { RouterConsts } from "../../common/RouterConsts";
import { HomeBackPressedEvent } from "../../events/Events";
import { HMRouterMgr } from "@hadss/hmrouter";
import { process } from "@kit.ArkTS";
import { FavoriteListPage } from "../favorite/FavoriteListPage";
import { VideoListPage } from "../videolist/VideoListPage";
import { MusicLibraryPage } from "../music/MusicLibraryPage";
import { PhotoLibraryPage } from "../photo/PhotoLibraryPage";
import { LoadingView } from "../../ui/components/LoadingView";
import { MediaItem } from "./hometab/HomeUIState";
import { MediaLibraryKind } from "../../media/catalog/MediaCatalogModels";
import { HORIZONTAL_CARD_SPACE } from "../../common/OneMoreConst";
import { OneMoreUtil } from "../../utils/OneMoreUtil";
import { PadTopTab, TabItem } from "../../component/tabbar/PadTopTab";
import { ThemeUpgradeDialog } from "../../component/ThemeUpgradeDialog";
import { ThemeStyle } from "../../entity/ThemeStyle";
import { themePromptService, ThemeWriteStatus } from "../../theme/ThemePromptService";
import { nativeThemeCapability } from "../../theme/NativeThemeCapability";
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from "../../theme/AppThemeSurfaceResolver";
import { FeiniuPointLightModifier } from "../../theme/FeiniuPointLightModifier";
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
  private lastTime: number = 0;
  private themePromptChecked: boolean = false
  private themePromptCompleted: boolean = false
  private themePromptTimer: number = -1
  private themeDialogId: number = -1

  @Monitor('appUIState.currentBreakpoint')
  onBreakpointChange() {
    this.updateFeiniuPointLightEnabled()
    this.appUIState.isBigScreen = !this.appUIState.currentBreakpoint.includes('s')
    if (this.appUIState.currentBreakpoint.includes('l')) {
      this.ui.isMenuModalVisible = false
      this.ui.isLeftSidebarVisible = true
    }
    this.contentTabsController.changeIndex(this.contentSelectedIndex())
    this.updateRootNavigationTitleBar()
  }

  @Monitor('appUIState.themeStyle')
  onThemeStyleChange() {
    this.updateFeiniuPointLightEnabled()
    this.updateRootNavigationTitleBar()
  }

  @Monitor('appUIState.nativeThemeAvailable')
  onNativeThemeAvailabilityChange() {
    this.updateRootNavigationTitleBar()
  }

  private resolveShell(): HomeShellKind {
    return HomeShellPolicy.resolve(
      this.appUIState.currentBreakpoint as BreakpointTypeEnum,
      this.appUIState.themeStyle,
      this.appUIState.nativeThemeAvailable
    )
  }

  private get shell(): HomeShellKind {
    return this.resolveShell()
  }

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable)
  }

  private padActionBackground(): ResourceColor {
    return this.useNativeSurface() ? Color.Transparent : $r('app.color.bg_main')
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

  private photoCapabilityEnabled(): boolean {
    return MediaProviderRegistry.instance().getActiveSession()?.capabilities.photos !== false
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

  private padSelectedIndex(): number {
    return HomeDestinationMapper.padMenuIndex(
      this.ui.selectedDestination,
      this.ui.lastSelectedMediaIndex
    )
  }

  private contentSelectedIndex(): number {
    return HomeDestinationMapper.contentIndex(
      this.ui.selectedDestination,
      this.usesPadMediaContent(),
      this.ui.lastSelectedMediaIndex
    )
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

  private selectPadMenu(index: number, item: PadMenuItem): void {
    if (item.extra) {
      this.ui.lastSelectedMediaIndex = index
    }
    this.ui.selectedDestination = HomeDestinationMapper.fromPadMenuIndex(
      index,
      item.extra !== undefined
    )
  }

  @Monitor('ui.selectedDestination')
  onDestinationChange(): void {
    this.contentTabsController.changeIndex(this.contentSelectedIndex())
    this.updateRootNavigationTitleBar()
  }

  @Monitor('ui.lastSelectedMediaIndex')
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

  @Builder
  themePromptDialogBuilder() {
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

  private getPadTopTabItems(): TabItem[] {
    void this.ui.padMenuDataVersion
    const ds = this.vm.datasource
    if (ds.totalCount() < 3) {
      return [
        new TabItem('首页', $r('app.media.ic_home_sel'), $r('app.media.ic_home_unsel')),
        new TabItem('追剧', $r('app.media.history'), $r('app.media.history')),
        new TabItem('收藏', $r('app.media.ic_favorite_sel'), $r('app.media.ic_favorite_unsel')),
        new TabItem('全部', $r('app.media.all'), $r('app.media.all'))
      ]
    }
    const thirdIndex = this.getThirdItemDatasourceIndex()
    const thirdItem = ds.getData(thirdIndex)
    return [
      new TabItem('首页', $r('app.media.ic_home_sel'), $r('app.media.ic_home_unsel')),
      new TabItem('追剧', $r('app.media.history'), $r('app.media.history')),
      new TabItem('收藏', $r('app.media.ic_favorite_sel'), $r('app.media.ic_favorite_unsel')),
      new TabItem(thirdItem.title, thirdItem.selectedIcon as Resource, thirdItem.unselectedIcon as Resource)
    ]
  }

  private getPadTopTabSelectIndex(): number {
    const selectedIndex = this.padSelectedIndex()
    if (selectedIndex >= 0 && selectedIndex <= 2) {
      return selectedIndex
    }
    return this.ui.selectedDestination === HomeDestination.Media ? 3 : -1
  }

  private getThirdItemDatasourceIndex(): number {
    const ds = this.vm.datasource
    if (this.ui.selectedDestination !== HomeDestination.Media) {
      const lastMedia = this.ui.lastSelectedMediaIndex
      if (lastMedia >= 0 && lastMedia < ds.totalCount() && ds.getData(lastMedia).type === PadMenuType.ITEM &&
        ds.getData(lastMedia).extra) {
        return lastMedia
      }
      return ds.getFirstMediaItemIndex()
    }
    return this.padSelectedIndex()
  }

  private currentPadTitle(): string {
    const selectedIndex = this.padSelectedIndex()
    if (selectedIndex >= 0 && selectedIndex < this.vm.datasource.totalCount()) {
      return this.vm.datasource.getData(selectedIndex).title
    }
    if (this.ui.selectedDestination === HomeDestination.Mine) {
      return '我的'
    }
    return '首页'
  }

  @Builder
  private leftMenuContent(onItemSelect: (index: number) => void, nativeSurface: boolean) {
    Row() {
      Row() {
        Image($r('app.media.app_icon')).height(48)
        Text($r('app.string.app_name'))
          .fontColor($r('app.color.text_primary'))
          .fontSize(20)
          .fontWeight(FontWeight.Medium)
          .fontStyle(FontStyle.Italic)
      }
      .layoutWeight(1)
      .justifyContent(FlexAlign.Start)

      if (this.appUIState.currentBreakpoint.includes('l') && this.ui.isLeftSidebarVisible && !OneMoreUtil.isPc()) {
        Button() {
          SymbolGlyph($r('sys.symbol.close_sidebar'))
            .fontSize(20)
            .fontColor([$r('app.color.text_primary')])
        }
        .width(40)
        .height(40)
        .borderRadius(20)
        .align(Alignment.Center)
        .backgroundColor(nativeSurface ? Color.Transparent : $r('app.color.bg_main'))
        .onClick(() => {
          this.ui.context.animateTo({ duration: 300, curve: Curve.FastOutSlowIn }, () => {
            this.ui.isLeftSidebarVisible = false
          })
        })
      } else {
        Blank().width(40).height(40)
      }
    }
    .width('100%')
    .padding({
      left: 12,
      right: 12,
      bottom: 18
    })

    List() {
      LazyForEach(this.vm.datasource, (item: PadMenuItem, index: number) => {
        if (item.type == PadMenuType.TITLE) {
          ListItem() {
            Text(item.title).fontColor($r('app.color.text_1')).fontSize(12)
              .height(20)
              .margin({
                top: 20,
                left: 10
              })
          }
        } else {
          ListItem() {
            Row({ space: 8 }) {
              Image(this.padSelectedIndex() == index ? item.selectedIcon : item.unselectedIcon)
                .fillColor(this.padSelectedIndex() == index ? $r('app.color.color_main') :
                  $r('app.color.text_primary'))
                .width(20)
                .height(20)
              Text(item.title)
                .fontColor(this.padSelectedIndex() == index ? $r('app.color.color_main') :
                  $r('app.color.text_primary'))
                .fontSize(14)
                .maxLines(1)
                .textOverflow({ overflow: TextOverflow.Ellipsis })
                .layoutWeight(1)
              Text(item.badge > 0 ? item.badge > 99 ? '99+' : item.badge.toString() : '')
                .fontColor(this.padSelectedIndex() == index ? $r('app.color.color_main') :
                  $r('app.color.text_primary'))
                .fontSize(14)
            }
            .backgroundBlurStyle(nativeSurface ? BlurStyle.NONE :
              (this.padSelectedIndex() == index ? BlurStyle.Thin : BlurStyle.NONE))
            .backgroundColor(nativeSurface ?
              (this.padSelectedIndex() == index ?
                AppThemeSurfaceResolver.interactionOverlay(this.appUIState.themeStyle) : Color.Transparent) :
              ((this.padSelectedIndex() == index && this.vm.appUIState.currentBreakpoint.includes('l')) ?
                $r('app.color.menu_item_bg') : Color.Transparent))
            .width('100%')
            .padding(10)
            .borderRadius(8)
            .onClick(() => {
              if (this.padSelectedIndex() === index) {
                return
              }
              this.selectPadMenu(index, item)
              onItemSelect(index)
            })
          }
        }
      })
    }.padding({
      left: 20,
      right: 20,
    })
    .width('100%')
    .listDirection(Axis.Vertical)
    .layoutWeight(1)
  }

  @Builder
  private nativeLeftMenuBuilder(onItemSelect: (index: number) => void) {
    Column() {
      this.leftMenuContent(onItemSelect, true)
    }
    .width(240)
    .height('100%')
    .padding({
      top: 20,
      bottom: 32
    })
    .borderRadius(20)
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.Floating,
      true,
      this.appUIState.systemMaterialAvailable
    ))
    .borderColor(this.appUIState.systemMaterialAvailable ?
      Color.Transparent : $r('sys.color.comp_divider'))
    .borderWidth(this.appUIState.systemMaterialAvailable ? 0 : 1)
  }

  @Builder
  private feiniuLeftMenuBuilder(onItemSelect: (index: number) => void) {
    Column() {
      this.leftMenuContent(onItemSelect, false)
    }
    .width(240)
    .height('100%')
    .padding({
      top: 20,
      bottom: 32
    })
    .backgroundBlurStyle(BlurStyle.Thin)
    .borderColor($r('app.color.border'))
    .borderWidth(1)
    .borderRadius(20)
    .visualEffect(FeiniuPointLightModifier.build({
      enabled: this.appUIState.feiniuPointLightEnabled
    }))
  }

  @Builder
  leftMenuBuilder(onItemSelect: (index: number) => void) {
    if (this.useNativeSurface()) {
      this.nativeLeftMenuBuilder(onItemSelect)
    } else {
      this.feiniuLeftMenuBuilder(onItemSelect)
    }
  }

  @Builder
  menuModalBuilder() {
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
          this.leftMenuBuilder(() => {
            this.ui.context.animateTo({ duration: 300, curve: Curve.FastOutSlowIn }, () => {
              this.ui.isMenuModalVisible = false
            })
          })
        }
        .width(240)
        .height('100%')
        .margin({ left: 12 })
        .padding({
          top: this.appUIState.safeTop + 12,
          bottom: this.appUIState.safeBottom + 12,
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

  @Builder
  private homeTabContent() {
    HomeTab({
      active: this.ui.selectedDestination === HomeDestination.Home,
      contentBottomInset: this.contentBottomInset,
      contentScroller: this.homeScroller
    })
  }

  @Builder
  private chasingTabContent() {
    ChasingTab({
      fromHome: true,
      contentTopInset: this.nativeFromHomeTopInset,
      contentBottomInset: this.contentBottomInset,
      contentScroller: this.chasingScroller
    })
  }

  @Builder
  private favoriteTabContent() {
    FavoriteListPage({
      fromHome: true,
      contentTopInset: this.nativeFromHomeTopInset,
      contentBottomInset: this.contentBottomInset,
      contentScroller: this.favoriteScroller
    })
  }

  @Builder
  private mediaTabContent() {
    MediaTab({
      contentBottomInset: this.contentBottomInset,
      rootTitleBarOwned: this.shell === HomeShellKind.PhoneNativeHds,
      contentScroller: this.mediaScroller,
      onAdministratorStateChange: (isAdministrator: boolean) => {
        this.mediaAdministrator = isAdministrator
        this.updateRootNavigationTitleBar()
      }
    })
  }

  @Builder
  private mineTabContent() {
    MineTab({
      contentBottomInset: this.contentBottomInset,
      compactTopInset: this.shell === HomeShellKind.PhoneNativeHds,
      contentScroller: this.mineScroller
    })
  }

  @Builder
  private homeContentOwner() {
    Tabs({
      index: this.contentSelectedIndex(),
      controller: this.contentTabsController
    }) {
      TabContent() {
        this.homeTabContent()
      }
      TabContent() {
        this.chasingTabContent()
      }
      TabContent() {
        this.favoriteTabContent()
      }
      TabContent() {
        this.mediaTabContent()
      }
      TabContent() {
        this.mineTabContent()
      }

      LazyForEach(this.vm.datasource, (item: PadMenuItem) => {
        TabContent() {
          if (item.extra) {
            if ((item.extra as MediaItem).libraryKind === MediaLibraryKind.Music) {
              if (this.musicCapabilityEnabled()) {
                MusicLibraryPage({
                  fromHome: true,
                  mediaItem: item.extra as MediaItem
                })
              } else {
                Stack()
              }
            } else if ((item.extra as MediaItem).libraryKind === MediaLibraryKind.Photos) {
              if (this.photoCapabilityEnabled()) {
                PhotoLibraryPage({
                  fromHome: true,
                  mediaItem: item.extra as MediaItem,
                  contentBottomInset: this.contentBottomInset
                })
              } else {
                Stack()
              }
            } else {
              VideoListPage({
                fromHome: true,
                mediaItem: item.extra as MediaItem
              })
            }
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

  @Builder
  private padChromeBuilder(shell: HomeShellKind) {
    Stack() {
      Row() {
        Column() {
          this.leftMenuBuilder(() => {
          })
        }
        .width(240)
        .height('100%')
        .padding({
          top: this.appUIState.safeTop + 12,
          bottom: this.appUIState.safeBottom + 12
        })
        .visibility((shell === HomeShellKind.LargeSidebar && this.ui.isLeftSidebarVisible) ?
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
          Column() {
            Row({ space: 16 }) {
              Text(this.currentPadTitle())
                .fontSize(24)
                .fontWeight(FontWeight.Bold)
                .margin({
                  left: HORIZONTAL_CARD_SPACE.getValue(this.vm.appUIState.currentBreakpoint) * 2
                })
                .hitTestBehavior(HitTestMode.None)
              Blank()
                .layoutWeight(1)
                .hitTestBehavior(HitTestMode.None)
              Button() {
                SymbolGlyph($r('sys.symbol.magnifyingglass'))
                  .fontSize(20)
                  .fontColor([$r('app.color.text_primary')])
              }
              .width(40)
              .height(40)
              .borderRadius(20)
              .align(Alignment.Center)
              .backgroundColor(this.padActionBackground())
              .onClick(() => {
                HMRouterMgr.to(RouterConsts.SearchPage).push()
              })

              Button() {
                SymbolGlyph($r('sys.symbol.person'))
                  .fontSize(20)
                  .fontColor([$r('app.color.text_primary')])
              }
              .width(40)
              .height(40)
              .borderRadius(20)
              .align(Alignment.Center)
              .backgroundColor(this.padActionBackground())
              .onClick(() => {
                HMRouterMgr.to(RouterConsts.MinePage).push()
              })
            }
            .width('100%')
            .alignItems(VerticalAlign.Center)
            .height(88)
            .padding({
              top: this.vm.appUIState.safeTop,
              right: 24
            })
            .hitTestBehavior(HitTestMode.Transparent)

            Blank()
              .layoutWeight(1)
              .hitTestBehavior(HitTestMode.None)
          }
          .hitTestBehavior(HitTestMode.Transparent)

          PadTopTab({
            list: this.getPadTopTabItems(),
            selectIndex: this.getPadTopTabSelectIndex(),
            onItemClick: (index: number) => {
              const targetIndex = index === 0 ? 0 : index === 1 ? 1 : index === 2 ? 2 :
                this.getThirdItemDatasourceIndex()
              if (targetIndex < 0 || targetIndex >= this.vm.datasource.totalCount()) {
                return
              }
              this.selectPadMenu(targetIndex, this.vm.datasource.getData(targetIndex))
            },
            onMenuButtonClick: () => {
              if (shell === HomeShellKind.LargeSidebar) {
                this.ui.context.animateTo({ duration: 300, curve: Curve.FastOutSlowIn }, () => {
                  this.ui.isLeftSidebarVisible = true
                })
              } else {
                this.ui.context.animateTo({ duration: 300, curve: Curve.FastOutSlowIn }, () => {
                  this.ui.isMenuModalVisible = true
                })
              }
            }
          })
            .margin({
              top: this.appUIState.safeTop
            })
            .visibility((shell === HomeShellKind.LargeSidebar && this.ui.isLeftSidebarVisible) ?
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

      if (this.ui.isMenuModalVisible && shell === HomeShellKind.MediumDrawer) {
        this.menuModalBuilder()
      }
    }
    .width('100%')
    .height('100%')
    .alignContent(Alignment.TopStart)
    .padding({
      left: shell === HomeShellKind.LargeSidebar ? 12 : 0
    })
    .hitTestBehavior(HitTestMode.Transparent)
  }

  @Builder
  private largeSidebarBuilder() {
    this.padChromeBuilder(HomeShellKind.LargeSidebar)
  }

  @Builder
  private mediumDrawerBuilder() {
    this.padChromeBuilder(HomeShellKind.MediumDrawer)
  }

  private runtimeShellProbeLabel(shell: HomeShellKind): string {
    return NativeThemeConstructionProbe.label(
      this.appUIState.deviceWidth,
      this.appUIState.currentBreakpoint,
      shell
    )
  }

  @Builder
  private runtimeShellProbe(shell: HomeShellKind) {
    Text(this.runtimeShellProbeLabel(shell))
      .fontSize(1)
      .width(1)
      .height(1)
      .opacity(0.01)
      .accessibilityText(this.runtimeShellProbeLabel(shell))
      .hitTestBehavior(HitTestMode.None)
  }

  @Builder
  private legacyPadContent(shell: HomeShellKind) {
    Row() {
      if (shell === HomeShellKind.LargeSidebar) {
        // Keep the content in the same column as the legacy large-screen
        // chrome: 12vp outer gutter plus the visible 240vp sidebar.
        Blank().width(this.ui.isLeftSidebarVisible ? 252 : 12)
      }

      Column() {
        // The legacy pad title row owns this 88vp strip.  Without reserving
        // it, the routed tab content starts behind the title row and status
        // area on the API 24–25 blur fallback.
        Blank().height(88)
        Stack() {
          this.homeContentOwner()
        }
          .layoutWeight(1)
          .width('100%')
      }
      .layoutWeight(1)
      .height('100%')
    }
    .width('100%')
    .height('100%')
  }

  @Builder
  private nonNativeHomeShell(shell: HomeShellKind) {
    Stack({ alignContent: Alignment.Bottom }) {
      if (shell === HomeShellKind.MediumDrawer || shell === HomeShellKind.LargeSidebar) {
        this.legacyPadContent(shell)
      } else {
        this.homeContentOwner()
      }

      if (shell === HomeShellKind.LargeSidebar) {
        this.largeSidebarBuilder()
      } else if (shell === HomeShellKind.MediumDrawer) {
        this.mediumDrawerBuilder()
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

  build() {
    Stack() {
      if (this.shell === HomeShellKind.PhoneNativeHds) {
        HomePhoneNativeTabs({
          selectedIndex: this.phoneSelectedIndex(),
          safeBottom: this.appUIState.safeBottom,
          compactPlayerChrome: this.compactPlayerChrome(),
          homeContentScroller: this.homeScroller,
          onSelectedIndexChange: (index: number) => {
            this.selectPhoneIndex(index)
          },
          homeContentBuilder: () => {
            this.homeTabContent()
          },
          favoriteContentBuilder: () => {
            this.favoriteTabContent()
          },
          mediaContentBuilder: () => {
            this.mediaTabContent()
          },
          mineContentBuilder: () => {
            this.mineTabContent()
          }
        })
      } else {
        this.nonNativeHomeShell(this.shell)
      }

      this.runtimeShellProbe(this.shell)
    }
    .width('100%')
    .height('100%')
  }
}
