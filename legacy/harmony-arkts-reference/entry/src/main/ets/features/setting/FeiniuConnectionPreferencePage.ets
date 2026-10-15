import { HMRouter } from '@hadss/hmrouter'
import { RouterConsts } from '../../common/RouterConsts'
import { ActionBar } from '../../component/ActionBar'
import { AppRouteDestination, AppRouteDestinationOptions } from '../../component/AppRouteDestination'
import { AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver'
import { MediaProviderComposition } from './account/MediaProviderComposition'
import { FeiniuVideoConnectionPreferenceStore } from '../feiniuvideo/network/FeiniuVideoConnectionPreferenceStore'
import { FeiniuVideoRoutePreference } from '../feiniuvideo/network/FeiniuVideoApiModels'
import { HdsNavDestination, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'

class FeiniuConnectionPreferenceOption {
  readonly value: FeiniuVideoRoutePreference
  readonly title: ResourceStr

  constructor(value: FeiniuVideoRoutePreference, title: ResourceStr) {
    this.value = value
    this.title = title
  }
}

@ComponentV2
struct FeiniuConnectionPreferenceContent {
  @Require @Param showLegacyActionBar: boolean
  @Require @Param selectedPreference: FeiniuVideoRoutePreference
  @Require @Param options: FeiniuConnectionPreferenceOption[]
  @Require @Param contentScroller: Scroller
  @Event onSelect: (value: FeiniuVideoRoutePreference) => void = () => {}

  private isNativePage(): boolean {
    return !this.showLegacyActionBar
  }

  private primaryTextColor(): ResourceColor {
    return this.isNativePage() ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  build() {
    Column() {
      if (this.showLegacyActionBar) {
        ActionBar({ title: $r('app.string.feiniu_connection_strategy_title') })
      }
      Scroll(this.contentScroller) {
        Column({ space: 12 }) {
          Column() {
            ForEach(this.options, (option: FeiniuConnectionPreferenceOption, index: number) => {
              Row({ space: 12 }) {
                Text(option.title)
                  .fontSize(this.isNativePage() ? 16 : 14)
                  .fontWeight(FontWeight.Medium)
                  .fontColor(this.primaryTextColor())
                  .layoutWeight(1)
                  .maxLines(1)
                  .textOverflow({ overflow: TextOverflow.Ellipsis })
                if (this.selectedPreference === option.value) {
                  SymbolGlyph($r('sys.symbol.checkmark'))
                    .fontSize(this.isNativePage() ? 20 : 18)
                    .fontColor([$r('app.color.color_main')])
                } else {
                  Blank()
                    .width(this.isNativePage() ? 20 : 18)
                    .height(this.isNativePage() ? 20 : 18)
                }
              }
              .width('100%')
              .height(this.isNativePage() ? 56 : 52)
              .padding({ left: 16, right: 16 })
              .onClick(() => this.onSelect(option.value))
              if (index < this.options.length - 1) {
                Divider()
                  .color(this.isNativePage() ?
                    $r('sys.color.comp_divider') : $r('app.color.border_input'))
                  .margin({ left: 16, right: 16 })
              }
            })
          }
          .width('100%')
          .borderRadius(this.isNativePage() ? 16 : 12)
          .clip(true)
          .backgroundColor(this.isNativePage() ?
            AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
          Blank().height(32)
        }
        .width('100%')
        .constraintSize({ maxWidth: 680 })
        .padding({ left: 16, right: 16, top: 12 })
      }
      .width('100%')
      .layoutWeight(1)
      .align(Alignment.Top)
      .scrollBar(BarState.Off)
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Start)
    .backgroundColor(this.showLegacyActionBar ?
      $r('app.color.start_window_background') : Color.Transparent)
    .expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.BOTTOM])
  }
}

@HMRouter({
  pageUrl: RouterConsts.FeiniuConnectionPreferencePage,
  useNavDst: true
})
@ComponentV2
export struct FeiniuConnectionPreferencePage {
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
  private readonly contentScroller: Scroller = new Scroller()
  @Local selectedPreference: FeiniuVideoRoutePreference = FeiniuVideoRoutePreference.Speed
  private readonly options: FeiniuConnectionPreferenceOption[] = [
    new FeiniuConnectionPreferenceOption(
      FeiniuVideoRoutePreference.Speed,
      $r('app.string.feiniu_connection_strategy_speed')
    ),
    new FeiniuConnectionPreferenceOption(
      FeiniuVideoRoutePreference.Public,
      $r('app.string.feiniu_connection_strategy_public')
    ),
    new FeiniuConnectionPreferenceOption(
      FeiniuVideoRoutePreference.Relay,
      $r('app.string.feiniu_connection_strategy_relay')
    )
  ]

  aboutToAppear(): void {
    this.selectedPreference = FeiniuVideoConnectionPreferenceStore.routePreference()
  }

  private async selectPreference(value: FeiniuVideoRoutePreference): Promise<void> {
    if (value === this.selectedPreference) {
      return
    }
    if (!FeiniuVideoConnectionPreferenceStore.saveRoutePreference(value)) {
      this.getUIContext().getPromptAction().showToast({
        message: $r('app.string.feiniu_connection_strategy_save_failed')
      })
      return
    }
    this.selectedPreference = value
    const hostContext = this.getUIContext().getHostContext()
    if (!hostContext) {
      return
    }
    try {
      const composition = await MediaProviderComposition.initialize(hostContext)
      await composition.refreshSelectedFeiniuConnection()
    } catch (error) {
      console.warn(`[FeiniuConnectionPreference] route refresh failed: ${String(error)}`)
    }
  }

  private routeOptions(): AppRouteDestinationOptions {
    return {
        title: $r('app.string.feiniu_connection_strategy_title'),
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
              FeiniuConnectionPreferenceContent({
                showLegacyActionBar: false,
                selectedPreference: this.selectedPreference,
                options: this.options,
                contentScroller: this.contentScroller,
                onSelect: (value: FeiniuVideoRoutePreference) => this.selectPreference(value)
              })
            } else {
              FeiniuConnectionPreferenceContent({
                showLegacyActionBar: true,
                selectedPreference: this.selectedPreference,
                options: this.options,
                contentScroller: this.contentScroller,
                onSelect: (value: FeiniuVideoRoutePreference) => this.selectPreference(value)
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
        FeiniuConnectionPreferenceContent({
          showLegacyActionBar: true,
          selectedPreference: this.selectedPreference,
          options: this.options,
          contentScroller: this.contentScroller,
          onSelect: (value: FeiniuVideoRoutePreference) => this.selectPreference(value)
        })
      }
      .attributeModifier(this.routeDestination.legacyModifier(this.routeOptions()))
      .gestureModifier(this.routeDestination.gestureModifier)
      .width('100%')
      .height('100%')
    }
  }
}
