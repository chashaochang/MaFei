import { HMLifecycleState, HMRouter, HMRouterMgr } from '@hadss/hmrouter'
import { AppStorageV2 } from '@kit.ArkUI'
import { UIConstants } from '../../common/UIConstants'
import { RouterConsts } from '../../common/RouterConsts'
import { ActionBar } from '../../component/ActionBar'
import { AppRouteDestination } from '../../component/AppRouteDestination'
import { AppUIState } from '../../entity/AppUIState'
import { CustomColorMode } from '../../entity/CustomColorMode'
import { ThemeStyle } from '../../entity/ThemeStyle'
import { AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver'
import { AppThemeTokens } from '../../theme/AppThemeTokens'
import { MediaProviderKind } from '../../media/MediaProviderKind'
import { MediaProviderRegistry } from '../../media/MediaProviderRegistry'
import { FeiniuVideoConnectionPreferenceStore } from '../feiniuvideo/network/FeiniuVideoConnectionPreferenceStore'
import { FeiniuVideoRoutePreference } from '../feiniuvideo/network/FeiniuVideoApiModels'

@HMRouter({
  pageUrl: '/Setting',
  useNavDst: true
})
@ComponentV2
export struct SettingPage {
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

  private primaryTextColor(native: boolean): ResourceColor {
    return native ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(native: boolean): ResourceColor {
    return native ? $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  private nativeTopSpacer(): number {
    return Math.max(0, this.appUIState.safeTop) + UIConstants.ACTION_BAR_HEIGHT
  }

  @Builder
  private settingRow(title: ResourceStr, trailing: ResourceStr, native: boolean, onClick: () => void) {
    Row({ space: 12 }) {
      Text(title)
        .fontSize(native ? 16 : 14)
        .fontWeight(FontWeight.Medium)
        .fontColor(this.primaryTextColor(native))
        .layoutWeight(1)
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
      Text(trailing)
        .fontSize(native ? 14 : 13)
        .fontColor(this.secondaryTextColor(native))
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
      SymbolGlyph($r('sys.symbol.chevron_right'))
        .fontSize(native ? 18 : 14)
        .fontColor([native ? $r('sys.color.icon_tertiary') : $r('app.color.text_primary')])
    }
    .width('100%')
    .height(native ? 56 : 52)
    .padding({ left: 16, right: 16 })
    .onClick(onClick)
  }

  @Builder
  private settingGroup(title: ResourceStr, trailing: ResourceStr, native: boolean, onClick: () => void) {
    Column() {
      this.settingRow(title, trailing, native, onClick)
    }
    .width('100%')
    .borderRadius(AppThemeTokens.groupRadius(this.appUIState.themeStyle))
    .clip(true)
    .backgroundColor(native ? AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
  }

  @Builder
  private pageContent(showLegacyActionBar: boolean) {
    Column() {
      if (showLegacyActionBar) {
        ActionBar({ title: $r('app.string.settings_title') })
      }
      Scroll(this.contentScroller) {
        Column({ space: 12 }) {
          if (!showLegacyActionBar) {
            Blank().height(this.nativeTopSpacer())
          }
          this.settingGroup(
            $r('app.string.appearance_settings_title'),
            this.appearanceName,
            !showLegacyActionBar,
            () => HMRouterMgr.to(RouterConsts.ColorModePage).push()
          )
          if (this.showFeiniuConnectionPreference) {
            this.settingGroup(
              $r('app.string.feiniu_connection_strategy_title'),
              this.feiniuRoutePreferenceTitle(),
              !showLegacyActionBar,
              () => HMRouterMgr.to(RouterConsts.FeiniuConnectionPreferencePage).push()
            )
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
    .backgroundColor(showLegacyActionBar ? $r('app.color.start_window_background') : Color.Transparent)
    .expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.BOTTOM])
  }

  build() {
    AppRouteDestination({
      title: $r('app.string.settings_title'),
      contentExtendsUnderTitleBar: true,
      scrollControllers: [this.contentScroller],
      contentBuilder: () => {
        this.pageContent(false)
      },
      legacyContentBuilder: () => {
        this.pageContent(true)
      }
    })
  }
}
