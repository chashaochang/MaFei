import { HMRouter } from '@hadss/hmrouter'
import { RouterConsts } from '../../common/RouterConsts'
import { ActionBar } from '../../component/ActionBar'
import { AppRouteDestination } from '../../component/AppRouteDestination'
import { AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver'
import { MediaProviderComposition } from './account/MediaProviderComposition'
import { FeiniuVideoConnectionPreferenceStore } from '../feiniuvideo/network/FeiniuVideoConnectionPreferenceStore'
import { FeiniuVideoRoutePreference } from '../feiniuvideo/network/FeiniuVideoApiModels'

class FeiniuConnectionPreferenceOption {
  readonly value: FeiniuVideoRoutePreference
  readonly title: ResourceStr

  constructor(value: FeiniuVideoRoutePreference, title: ResourceStr) {
    this.value = value
    this.title = title
  }
}

@HMRouter({
  pageUrl: RouterConsts.FeiniuConnectionPreferencePage,
  useNavDst: true
})
@ComponentV2
export struct FeiniuConnectionPreferencePage {
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

  private primaryTextColor(native: boolean): ResourceColor {
    return native ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private isNativePage(showLegacyActionBar: boolean): boolean {
    return !showLegacyActionBar
  }

  @Builder
  private pageContent(showLegacyActionBar: boolean): void {
    Column() {
      if (showLegacyActionBar) {
        ActionBar({ title: $r('app.string.feiniu_connection_strategy_title') })
      }
      Scroll(this.contentScroller) {
        Column({ space: 12 }) {
          Column() {
            ForEach(this.options, (option: FeiniuConnectionPreferenceOption, index: number) => {
              Row({ space: 12 }) {
                Text(option.title)
                  .fontSize(this.isNativePage(showLegacyActionBar) ? 16 : 14)
                  .fontWeight(FontWeight.Medium)
                  .fontColor(this.primaryTextColor(this.isNativePage(showLegacyActionBar)))
                  .layoutWeight(1)
                  .maxLines(1)
                  .textOverflow({ overflow: TextOverflow.Ellipsis })
                if (this.selectedPreference === option.value) {
                  SymbolGlyph($r('sys.symbol.checkmark'))
                    .fontSize(this.isNativePage(showLegacyActionBar) ? 20 : 18)
                    .fontColor([$r('app.color.color_main')])
                } else {
                  Blank()
                    .width(this.isNativePage(showLegacyActionBar) ? 20 : 18)
                    .height(this.isNativePage(showLegacyActionBar) ? 20 : 18)
                }
              }
              .width('100%')
              .height(this.isNativePage(showLegacyActionBar) ? 56 : 52)
              .padding({ left: 16, right: 16 })
              .onClick(() => {
                this.selectPreference(option.value)
              })
              if (index < this.options.length - 1) {
                Divider()
                  .color(this.isNativePage(showLegacyActionBar) ?
                    $r('sys.color.comp_divider') : $r('app.color.border_input'))
                  .margin({ left: 16, right: 16 })
              }
            })
          }
          .width('100%')
          .borderRadius(this.isNativePage(showLegacyActionBar) ? 16 : 12)
          .clip(true)
          .backgroundColor(this.isNativePage(showLegacyActionBar) ?
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
    .backgroundColor(showLegacyActionBar ? $r('app.color.start_window_background') : Color.Transparent)
    .expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.BOTTOM])
  }

  build() {
    AppRouteDestination({
      title: $r('app.string.feiniu_connection_strategy_title'),
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
