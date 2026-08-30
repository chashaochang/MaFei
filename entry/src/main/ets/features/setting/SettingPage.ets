import { HMLifecycleState, HMRouter, HMRouterMgr } from '@hadss/hmrouter'
import { AppStorageV2 } from '@kit.ArkUI'
import { UIConstants } from '../../common/UIConstants'
import { RouterConsts } from '../../common/RouterConsts'
import { ActionBar } from '../../component/ActionBar'
import { AppRouteDestination, AppRouteDestinationOptions } from '../../component/AppRouteDestination'
import { AppUIState } from '../../entity/AppUIState'
import { CustomColorMode } from '../../entity/CustomColorMode'
import { ThemeStyle } from '../../entity/ThemeStyle'
import { AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver'
import { AppThemeTokens } from '../../theme/AppThemeTokens'
import { MediaProviderKind } from '../../media/MediaProviderKind'
import { MediaProviderRegistry } from '../../media/MediaProviderRegistry'
import { FeiniuVideoConnectionPreferenceStore } from '../feiniuvideo/network/FeiniuVideoConnectionPreferenceStore'
import { FeiniuVideoRoutePreference } from '../feiniuvideo/network/FeiniuVideoApiModels'
import { HdsNavDestination, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'

@HMRouter({
  pageUrl: '/Setting',
  useNavDst: true
})
@ComponentV2
export struct SettingPage {
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;
  private readonly contentScroller: Scroller = new Scroller()
  @Local appearanceName: string = ''
  @Local showFeiniuConnectionPreference: boolean = false
  @Local feiniuRoutePreference: FeiniuVideoRoutePreference = FeiniuVideoRoutePreference.Speed

  aboutToAppear(): void {
    this.refreshSummaries()
    HMRouterMgr.getCurrentLifecycleOwner()?.addObserver(HMLifecycleState.onShown, () => {
      this.refreshSummaries()
    })
  }

  private refreshSummaries(): void {
    const activeSession = MediaProviderRegistry.instance().getActiveSession()
    this.showFeiniuConnectionPreference = activeSession?.provider === MediaProviderKind.FeiniuVideo
    this.feiniuRoutePreference = FeiniuVideoConnectionPreferenceStore.routePreference()
    const resourceManager = this.getUIContext().getHostContext()?.resourceManager
    if (resourceManager) {
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

  private routeOptions(): AppRouteDestinationOptions {
    return {
        title: $r('app.string.settings_title'),
        contentExtendsUnderTitleBar: true,
        scrollControllers: [this.contentScroller]
      }
  }

  build() {
    if (this.routeDestination.usesHdsDestination()) {
      HdsNavDestination() {
        Column() {
          Blank().height(this.routeDestination.nativeContentTopInset(this.routeOptions()))
          Stack() {
            if (this.routeDestination.usesNativeChrome()) {
              SettingPageContent({
                appUIState: this.appUIState,
                contentScroller: this.contentScroller,
                appearanceName: this.appearanceName,
                showFeiniuConnectionPreference: this.showFeiniuConnectionPreference,
                feiniuRoutePreferenceTitle: this.feiniuRoutePreferenceTitle(),
                onOpenAppearance: (): void => {
                  HMRouterMgr.to(RouterConsts.ColorModePage).push()
                },
                onOpenFeiniuPreference: (): void => {
                  HMRouterMgr.to(RouterConsts.FeiniuConnectionPreferencePage).push()
                }
              })
            } else {
              SettingPageContent({
                showLegacyActionBar: true,
                appUIState: this.appUIState,
                contentScroller: this.contentScroller,
                appearanceName: this.appearanceName,
                showFeiniuConnectionPreference: this.showFeiniuConnectionPreference,
                feiniuRoutePreferenceTitle: this.feiniuRoutePreferenceTitle(),
                onOpenAppearance: (): void => {
                  HMRouterMgr.to(RouterConsts.ColorModePage).push()
                },
                onOpenFeiniuPreference: (): void => {
                  HMRouterMgr.to(RouterConsts.FeiniuConnectionPreferencePage).push()
                }
              })
            }
          }
          .width('100%')
          .layoutWeight(1)
        }
        .width('100%')
        .height('100%')
      }
      .backgroundColor(this.routeDestination.hdsBackgroundColor())
      .titleMode(HdsNavDestinationTitleMode.MINI)
      .titleBar(this.routeDestination.nativeTitleBarOptions(this.routeOptions()))
      .hideTitleBar(this.routeDestination.hdsTitleBarHidden(this.routeOptions()))
      .hideBackButton(!this.routeDestination.backButtonVisible(this.routeOptions()))
      .hideToolBar(true)
      .bindToScrollable(this.routeDestination.hdsBoundScrollers(this.routeOptions()))
      .onWillAppear(() => this.routeDestination.helper.onWillAppear())
      .onAppear(() => this.routeDestination.helper.onAppear())
      .onWillShow(() => this.routeDestination.helper.onWillShow())
      .onShown(() => this.routeDestination.helper.onShown())
      .onWillHide(() => this.routeDestination.helper.onWillHide())
      .onHidden(() => this.routeDestination.helper.onHidden())
      .onWillDisappear(() => this.routeDestination.helper.onWillDisappear())
      .onDisAppear(() => this.routeDestination.helper.onDisAppear())
      .onReady((ctx: NavDestinationContext) => this.routeDestination.helper.onReady(ctx))
      .onBackPressed(() => this.routeDestination.handleBackPressed(this.routeOptions()))
      .onResult((result: ESObject) => this.routeDestination.helper.onResult(result))
      .onActive((reason: NavDestinationActiveReason) => this.routeDestination.helper.onActive(reason))
      .onInactive((reason: NavDestinationActiveReason) => this.routeDestination.helper.onInactive(reason))
      .ignoreLayoutSafeArea(
        this.routeDestination.nativeSafeAreaTypes(),
        this.routeDestination.nativeSafeAreaEdges()
      )
      .width('100%')
      .height('100%')
    } else {
      NavDestination() {
        SettingPageContent({
          showLegacyActionBar: true,
          appUIState: this.appUIState,
          contentScroller: this.contentScroller,
          appearanceName: this.appearanceName,
          showFeiniuConnectionPreference: this.showFeiniuConnectionPreference,
          feiniuRoutePreferenceTitle: this.feiniuRoutePreferenceTitle(),
          onOpenAppearance: (): void => {
            HMRouterMgr.to(RouterConsts.ColorModePage).push()
          },
          onOpenFeiniuPreference: (): void => {
            HMRouterMgr.to(RouterConsts.FeiniuConnectionPreferencePage).push()
          }
        })
      }
      .attributeModifier(this.routeDestination.legacyModifier(this.routeOptions()))
      .gestureModifier(this.routeDestination.gestureModifier)
      .width('100%')
      .height('100%')
    }
  }
}

@ComponentV2
struct SettingRow {
  @Require @Param title: ResourceStr
  @Require @Param trailing: ResourceStr
  @Param nativeSurface: boolean = false
  @Event onPressed: () => void = () => {}

  build() {
    Row({ space: 12 }) {
      Text(this.title)
        .fontSize(this.nativeSurface ? 16 : 14)
        .fontWeight(FontWeight.Medium)
        .fontColor(this.nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary'))
        .layoutWeight(1)
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
      Text(this.trailing)
        .fontSize(this.nativeSurface ? 14 : 13)
        .fontColor(this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1'))
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
      SymbolGlyph($r('sys.symbol.chevron_right'))
        .fontSize(this.nativeSurface ? 18 : 14)
        .fontColor([this.nativeSurface ? $r('sys.color.icon_tertiary') : $r('app.color.text_primary')])
    }
    .width('100%')
    .height(this.nativeSurface ? 56 : 52)
    .padding({ left: 16, right: 16 })
    .onClick((): void => {
      this.onPressed()
    })
  }
}

@ComponentV2
struct SettingGroup {
  @Require @Param title: ResourceStr
  @Require @Param trailing: ResourceStr
  @Require @Param themeStyle: ThemeStyle
  @Param nativeSurface: boolean = false
  @Event onPressed: () => void = () => {}

  build() {
    Column() {
      SettingRow({
        title: this.title,
        trailing: this.trailing,
        nativeSurface: this.nativeSurface,
        onPressed: this.onPressed
      })
    }
    .width('100%')
    .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
    .clip(true)
    .backgroundColor(this.nativeSurface ? AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
  }
}

@ComponentV2
export struct SettingPageContent {
  @Require @Param appUIState: AppUIState
  @Require @Param contentScroller: Scroller
  @Param showLegacyActionBar: boolean = false
  @Param embeddedInHome: boolean = false
  @Param appearanceName: string = ''
  @Param showFeiniuConnectionPreference: boolean = false
  @Require @Param feiniuRoutePreferenceTitle: ResourceStr
  @Event onOpenAppearance: () => void = () => {}
  @Event onOpenFeiniuPreference: () => void = () => {}

  private nativeTopSpacer(): number {
    if (this.embeddedInHome) {
      return 0
    }
    return Math.max(0, this.appUIState.safeTop) + UIConstants.ACTION_BAR_HEIGHT
  }

  build() {
    Column() {
      if (this.showLegacyActionBar) {
        ActionBar({ title: $r('app.string.settings_title') })
      }
      Scroll(this.contentScroller) {
        Column({ space: 12 }) {
          if (!this.showLegacyActionBar) {
            Blank().height(this.nativeTopSpacer())
          }
          SettingGroup({
            title: $r('app.string.appearance_settings_title'),
            trailing: this.appearanceName,
            themeStyle: this.appUIState.themeStyle,
            nativeSurface: !this.showLegacyActionBar,
            onPressed: this.onOpenAppearance
          })
          if (this.showFeiniuConnectionPreference) {
            SettingGroup({
              title: $r('app.string.feiniu_connection_strategy_title'),
              trailing: this.feiniuRoutePreferenceTitle,
              themeStyle: this.appUIState.themeStyle,
              nativeSurface: !this.showLegacyActionBar,
              onPressed: this.onOpenFeiniuPreference
            })
          }
          Blank().height(Math.max(24, this.appUIState.safeBottom))
        }
        .width('100%')
        .constraintSize({ maxWidth: 680 })
        .padding({ left: 16, right: 16 })
      }
      .width('100%')
      .layoutWeight(1)
      .scrollBar(BarState.Off)
      .align(Alignment.Top)
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Start)
    .backgroundColor(this.showLegacyActionBar ? $r('app.color.start_window_background') : Color.Transparent)
    .expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.BOTTOM])
  }
}
