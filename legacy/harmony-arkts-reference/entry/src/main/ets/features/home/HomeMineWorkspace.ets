import {
  HMLifecycleContext,
  HMLifecycleState,
  HMRouterMgr,
  IHMLifecycleOwner
} from '@hadss/hmrouter'
import { AppStorageV2 } from '@kit.ArkUI'
import { AppUIState } from '../../entity/AppUIState'
import { CustomColorMode } from '../../entity/CustomColorMode'
import { ThemeStyle } from '../../entity/ThemeStyle'
import { MediaProviderKind } from '../../media/MediaProviderKind'
import { MediaProviderRegistry } from '../../media/MediaProviderRegistry'
import { RouterConsts } from '../../common/RouterConsts'
import { playMediaRefs } from '../../common/CommonFunc'
import { AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver'
import { BreakpointPolicy, BreakpointTypeEnum } from '../../utils/BreakpointPolicy'
import { AccountContent } from '../setting/account/AccountPage'
import { SettingPageContent } from '../setting/SettingPage'
import { AboutPageSurface } from '../setting/AboutPage'
import { ManagementDashboardContent } from '../management/ManagementDashboardPage'
import { FeiniuVideoConnectionPreferenceStore } from '../feiniuvideo/network/FeiniuVideoConnectionPreferenceStore'
import { FeiniuVideoRoutePreference } from '../feiniuvideo/network/FeiniuVideoApiModels'
import { MineTab } from './minetab/MineTab'
import { MineHistoryPageContent } from './minetab/MineHistoryPage'
import { MineHistoryPageUIState } from './minetab/MineHistoryPageUIState'
import { MineHistoryPageViewModel } from './minetab/MineHistoryPageViewModel'
import { MineHistoryItem } from './minetab/MineHistoryPlanner'
import { HomeMinePane } from './HomePadNavigation'

@ComponentV2
struct HomeAboutPrivacyPane {
  @Require @Param appUIState: AppUIState
  @Require @Param contentScroller: Scroller

  private nativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable)
  }

  private actionBackground(): ResourceColor {
    return this.nativeSurface() ? AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1')
  }

  build() {
    Column({ space: 12 }) {
      Row({ space: 12 }) {
        Row({ space: 8 }) {
          Image($r('app.media.ic_yinsi'))
            .width(20)
            .height(20)
          Text('隐私协议')
            .fontSize(14)
            .fontColor($r('app.color.text_primary'))
        }
        .layoutWeight(1)
        .height(48)
        .padding({ left: 16, right: 16 })
        .borderRadius(12)
        .backgroundColor(this.actionBackground())
        .onClick(() => HMRouterMgr.to(RouterConsts.PrivacyPage).push())

        Row({ space: 8 }) {
          Image($r('app.media.ic_setting'))
            .width(20)
            .height(20)
          Text('用户协议')
            .fontSize(14)
            .fontColor($r('app.color.text_primary'))
        }
        .layoutWeight(1)
        .height(48)
        .padding({ left: 16, right: 16 })
        .borderRadius(12)
        .backgroundColor(this.actionBackground())
        .onClick(() => HMRouterMgr.to(RouterConsts.UserAgreementPage).push())
      }
      .width('100%')
      .constraintSize({ maxWidth: 560 })
      .padding({ left: 16, right: 16, top: 12 })

      AboutPageSurface({
        showLegacyActionBar: false,
        contentScroller: this.contentScroller
      })
        .layoutWeight(1)
    }
    .width('100%')
    .height('100%')
    .alignItems(HorizontalAlign.Center)
  }
}

@ComponentV2
export struct HomeMineWorkspace {
  @Require @Param selectedPane: HomeMinePane
  @Param contentWidth: number = 0
  @Event onAdministratorStateChange: (available: boolean) => void = (_available: boolean) => {}
  @Event onPaneFallback: (pane: HomeMinePane) => void = (_pane: HomeMinePane) => {}
  @Local appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!
  @Local historyUI: MineHistoryPageUIState = new MineHistoryPageUIState(this.getUIContext())
  @Local appearanceName: string = ''
  @Local showFeiniuConnectionPreference: boolean = false
  @Local feiniuRoutePreference: FeiniuVideoRoutePreference = FeiniuVideoRoutePreference.Speed
  @Local historyLoaded: boolean = false
  @Local accountsLoaded: boolean = false
  @Local managementLoaded: boolean = false
  @Local settingsLoaded: boolean = false
  @Local aboutLoaded: boolean = false
  private historyVM: MineHistoryPageViewModel = new MineHistoryPageViewModel(this.historyUI)
  private tabsController: TabsController = new TabsController()
  private historyScroller: Scroller = new Scroller()
  private settingsScroller: Scroller = new Scroller()
  private aboutScroller: Scroller = new Scroller()
  private managementAvailable: boolean = false
  private lifecycleOwner: IHMLifecycleOwner | null = null
  private pageShownCallback = (_context: HMLifecycleContext): void => {
    if (this.selectedPane === HomeMinePane.Settings) {
      this.refreshSettingsSummaries()
    }
  }

  @Monitor('selectedPane')
  onSelectedPaneChange(): void {
    if (this.selectedPane === HomeMinePane.Management && !this.managementAvailable) {
      this.onPaneFallback(HomeMinePane.Overview)
      return
    }
    this.ensurePaneLoaded(this.selectedPane)
    this.tabsController.changeIndex(this.paneIndex(this.selectedPane))
  }

  aboutToAppear(): void {
    this.ensurePaneLoaded(this.selectedPane)
    this.refreshSettingsSummaries()
    this.tabsController.changeIndex(this.paneIndex(this.selectedPane))
    this.lifecycleOwner = HMRouterMgr.getCurrentLifecycleOwner()
    this.lifecycleOwner?.addObserver(HMLifecycleState.onShown, this.pageShownCallback)
  }

  aboutToDisappear(): void {
    this.lifecycleOwner?.removeObserver(HMLifecycleState.onShown, this.pageShownCallback)
    this.lifecycleOwner = null
  }

  private ensurePaneLoaded(pane: HomeMinePane): void {
    if (pane === HomeMinePane.History && !this.historyLoaded) {
      this.historyLoaded = true
      this.historyVM.loadInitial()
    } else if (pane === HomeMinePane.Accounts) {
      this.accountsLoaded = true
    } else if (pane === HomeMinePane.Management) {
      this.managementLoaded = true
    } else if (pane === HomeMinePane.Settings) {
      this.settingsLoaded = true
      this.refreshSettingsSummaries()
    } else if (pane === HomeMinePane.AboutPrivacy) {
      this.aboutLoaded = true
    }
  }

  private paneIndex(pane: HomeMinePane): number {
    if (pane === HomeMinePane.History) {
      return 1
    }
    if (pane === HomeMinePane.Accounts) {
      return 2
    }
    if (pane === HomeMinePane.Management) {
      return 3
    }
    if (pane === HomeMinePane.Settings) {
      return 4
    }
    if (pane === HomeMinePane.AboutPrivacy) {
      return 5
    }
    return 0
  }

  private historyBreakpoint(): BreakpointTypeEnum {
    const width = this.contentWidth > 0 ? this.contentWidth : this.appUIState.deviceWidth
    return BreakpointPolicy.fromWidth(width)
  }

  private historyColumnCount(): number {
    switch (this.historyBreakpoint()) {
      case BreakpointTypeEnum.XS:
      case BreakpointTypeEnum.SM:
        return 3
      case BreakpointTypeEnum.MD:
        return 4
      case BreakpointTypeEnum.LG:
        return 5
      case BreakpointTypeEnum.XL:
        return 7
      default:
        return 8
    }
  }

  private historyHorizontalPadding(): number {
    return this.historyBreakpoint() === BreakpointTypeEnum.XS ||
      this.historyBreakpoint() === BreakpointTypeEnum.SM ? 16 : 24
  }

  private refreshSettingsSummaries(): void {
    const activeSession = MediaProviderRegistry.instance().getActiveSession()
    this.showFeiniuConnectionPreference = activeSession?.provider === MediaProviderKind.FeiniuVideo
    this.feiniuRoutePreference = FeiniuVideoConnectionPreferenceStore.routePreference()
    const resourceManager = this.getUIContext().getHostContext()?.resourceManager
    if (!resourceManager) {
      this.appearanceName = ''
      return
    }
    try {
      const themeName = this.appUIState.themeStyle === ThemeStyle.Native ?
        resourceManager.getStringByNameSync('theme_native_name') :
        resourceManager.getStringByNameSync('theme_feiniu_name')
      let colorModeName = resourceManager.getStringByNameSync('color_mode_auto')
      if (this.appUIState.customColorMode === CustomColorMode.Light) {
        colorModeName = resourceManager.getStringByNameSync('color_mode_light')
      } else if (this.appUIState.customColorMode === CustomColorMode.Dark) {
        colorModeName = resourceManager.getStringByNameSync('color_mode_dark')
      }
      this.appearanceName = `${themeName} · ${colorModeName}`
    } catch (_) {
      this.appearanceName = ''
    }
  }

  private feiniuRoutePreferenceTitle(): ResourceStr {
    if (this.feiniuRoutePreference === FeiniuVideoRoutePreference.Public) {
      return $r('app.string.feiniu_connection_strategy_public')
    }
    if (this.feiniuRoutePreference === FeiniuVideoRoutePreference.Relay) {
      return $r('app.string.feiniu_connection_strategy_relay')
    }
    return $r('app.string.feiniu_connection_strategy_speed')
  }

  private updateManagementAvailability(available: boolean): void {
    this.managementAvailable = available
    this.onAdministratorStateChange(available)
    if (!available && this.selectedPane === HomeMinePane.Management) {
      this.onPaneFallback(HomeMinePane.Overview)
    }
  }

  build() {
    Tabs({ index: this.paneIndex(this.selectedPane), controller: this.tabsController }) {
      TabContent() {
        MineTab({
          fromHome: true,
          destinationOwnsTitleBar: true,
          onAdministratorStateChange: (available: boolean) => {
            this.updateManagementAvailability(available)
          }
        })
      }
      TabContent() {
        if (this.historyLoaded) {
          MineHistoryPageContent({
            historyState: this.historyUI.historyState,
            items: this.historyUI.items,
            contentScroller: this.historyScroller,
            gridTemplate: '1fr '.repeat(this.historyColumnCount()),
            columnCount: this.historyColumnCount(),
            horizontalPadding: this.historyHorizontalPadding(),
            safeBottom: this.appUIState.safeBottom,
            nativeSurface: AppThemeSurfaceResolver.useNativeSurface(
              this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable),
            loadingMore: this.historyUI.loadingMore,
            loadMoreFailed: this.historyUI.loadMoreFailed,
            onRetry: () => this.historyVM.loadInitial(),
            onItemPressed: (item: MineHistoryItem) =>
              playMediaRefs([item.mediaRef], this.getUIContext().getHostContext()!),
            onLoadMore: () => this.historyVM.loadMore(),
            onRetryLoadMore: () => this.historyVM.retryLoadMore()
          })
        } else {
          Stack()
        }
      }
      TabContent() {
        if (this.accountsLoaded) {
          AccountContent({ showLegacyActionBar: false })
        } else {
          Stack()
        }
      }
      TabContent() {
        if (this.managementLoaded) {
          ManagementDashboardContent({
            showLegacyActionBar: false,
            embeddedInHome: true
          })
        } else {
          Stack()
        }
      }
      TabContent() {
        if (this.settingsLoaded) {
          SettingPageContent({
            appUIState: this.appUIState,
            contentScroller: this.settingsScroller,
            showLegacyActionBar: false,
            embeddedInHome: true,
            appearanceName: this.appearanceName,
            showFeiniuConnectionPreference: this.showFeiniuConnectionPreference,
            feiniuRoutePreferenceTitle: this.feiniuRoutePreferenceTitle(),
            onOpenAppearance: () => HMRouterMgr.to(RouterConsts.ColorModePage).push(),
            onOpenFeiniuPreference: () => HMRouterMgr.to(RouterConsts.FeiniuConnectionPreferencePage).push()
          })
        } else {
          Stack()
        }
      }
      TabContent() {
        if (this.aboutLoaded) {
          HomeAboutPrivacyPane({
            appUIState: this.appUIState,
            contentScroller: this.aboutScroller
          })
        } else {
          Stack()
        }
      }
    }
    .barHeight(0)
    .barWidth(0)
    .vertical(false)
    .scrollable(false)
    .animationDuration(0)
    .width('100%')
    .height('100%')
  }
}
