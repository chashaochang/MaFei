import { HMRouter } from '@hadss/hmrouter';
import { RouterConsts } from '../../common/RouterConsts';
import { ActionBar } from '../../component/ActionBar';
import { AppRouteDestination, AppRouteDestinationOptions } from '../../component/AppRouteDestination';
import { ThemeStylePreview } from '../../component/ThemeStylePreview';
import { AppPreference } from '../../data/AppPreference';
import { AppUIState } from '../../entity/AppUIState';
import { CustomColorMode } from '../../entity/CustomColorMode';
import { ThemeStyle } from '../../entity/ThemeStyle';
import { AppStorageV2 } from '@kit.ArkUI';
import { common } from '@kit.AbilityKit';
import { AppThemeTokens } from '../../theme/AppThemeTokens';
import { AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver';
import { HdsUiCapability } from '../../theme/HdsUiCapability';
import { nativeThemeCapability } from '../../theme/NativeThemeCapability';
import { themePromptService, ThemeWriteStatus } from '../../theme/ThemePromptService';
import { setAutoColorMode, setDarkColorMode, setLightColorMode } from './ColorModeChangeFunctions';
import { HdsNavDestination, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'

class ColorModeItem {
  mode: CustomColorMode
  title: ResourceStr

  constructor(mode: CustomColorMode, title: ResourceStr) {
    this.mode = mode
    this.title = title
  }
}

@ComponentV2
struct ColorModeThemeChoice {
  @Require @Param style: ThemeStyle
  @Require @Param title: ResourceStr
  @Require @Param description: ResourceStr
  @Require @Param selectedTheme: ThemeStyle
  @Require @Param pressedTheme: ThemeStyle | undefined
  @Require @Param nativeSurface: boolean
  @Event onSelect: (style: ThemeStyle) => void = () => {
  }
  @Event onPressedChange: (style: ThemeStyle | undefined) => void = () => {
  }

  private nativeBackground(): ResourceColor {
    if (this.pressedTheme === this.style) {
      return '#142173DF'
    }
    return this.selectedTheme === this.style ? '#1F2173DF' : Color.Transparent
  }

  build() {
    if (this.nativeSurface) {
      Row({ space: 12 }) {
        ThemeStylePreview({ style: this.style })
        Column({ space: 4 }) {
          Text(this.title)
            .fontSize(16)
            .fontWeight(FontWeight.Medium)
            .fontColor($r('sys.color.font_primary'))
          Text(this.description)
            .fontSize(12)
            .fontColor($r('sys.color.font_secondary'))
        }
        .layoutWeight(1)
        .alignItems(HorizontalAlign.Start)
        if (this.selectedTheme === this.style) {
          SymbolGlyph($r('sys.symbol.checkmark_circle_fill'))
            .fontSize(22)
            .fontColor([$r('app.color.color_main')])
        } else {
          Blank().width(22).height(22)
        }
      }
      .width('100%')
      .constraintSize({ minHeight: 86 })
      .padding(12)
      .backgroundColor(this.nativeBackground())
      .borderRadius(12)
      .onTouch((event: TouchEvent) => {
        if (event.type === TouchType.Down) {
          this.onPressedChange(this.style)
        } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
          this.onPressedChange(undefined)
        }
      })
      .onClick(() => this.onSelect(this.style))
    } else {
      Row({ space: 12 }) {
        ThemeStylePreview({ style: this.style })
        Column({ space: 4 }) {
          Text(this.title)
            .fontSize(16)
            .fontWeight(FontWeight.Medium)
            .fontColor(this.style === ThemeStyle.Native ?
              $r('sys.color.font_primary') : $r('app.color.text_primary'))
          Text(this.description)
            .fontSize(12)
            .fontColor(this.style === ThemeStyle.Native ?
              $r('sys.color.font_secondary') : $r('app.color.text_1'))
            .maxLines(2)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
        }
        .layoutWeight(1)
        .alignItems(HorizontalAlign.Start)
        Radio({ value: this.style, group: 'themeStyle' })
          .checked(this.selectedTheme === this.style)
          .onChange(() => this.onSelect(this.style))
      }
      .width('100%')
      .height(86)
      .padding(12)
      .backgroundColor(this.style === ThemeStyle.Native ?
        $r('sys.color.comp_background_secondary') : $r('app.color.bg_1'))
      .borderRadius(AppThemeTokens.groupRadius(this.style))
      .borderWidth(this.selectedTheme === this.style ? 2 : 1)
      .borderColor(this.selectedTheme === this.style ? $r('app.color.color_main') : $r('app.color.border'))
      .onClick(() => this.onSelect(this.style))
    }
  }
}

@ComponentV2
struct ColorModeSelector {
  @Require @Param items: ColorModeItem[]
  @Require @Param selectedMode: CustomColorMode
  @Require @Param nativeSurface: boolean
  @Event onSelect: (mode: CustomColorMode) => void = () => {
  }

  build() {
    Row({ space: 6 }) {
      ForEach(this.items, (item: ColorModeItem) => {
        Button(item.title, { type: ButtonType.Normal })
          .fontSize(14)
          .fontWeight(FontWeight.Medium)
          .fontColor(this.selectedMode === item.mode ?
            $r('app.color.on_primary') :
            (this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1')))
          .backgroundColor(this.selectedMode === item.mode ?
            $r('app.color.color_main') : Color.Transparent)
          .borderRadius(11)
          .height(44)
          .layoutWeight(1)
          .onClick(() => this.onSelect(item.mode))
      })
    }
    .width('100%')
    .padding(5)
    .borderRadius(16)
    .clip(this.nativeSurface)
    .backgroundColor(this.nativeSurface ?
      AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.component_bg'))
  }
}

@ComponentV2
struct ColorModePageSurface {
  @Require @Param showLegacyActionBar: boolean
  @Require @Param contentScroller: Scroller
  private context: common.UIAbilityContext = this.getUIContext().getHostContext() as common.UIAbilityContext
  @Local selectedTheme: ThemeStyle = ThemeStyle.Feiniu
  @Local selectedColorMode: CustomColorMode = CustomColorMode.Auto
  @Local pressedTheme: ThemeStyle | undefined = undefined
  private appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!
  private colorModes: ColorModeItem[] = [
    new ColorModeItem(CustomColorMode.Auto, $r('app.string.color_mode_auto')),
    new ColorModeItem(CustomColorMode.Light, $r('app.string.color_mode_light')),
    new ColorModeItem(CustomColorMode.Dark, $r('app.string.color_mode_dark'))
  ]

  aboutToAppear(): void {
    this.selectedTheme = this.appUIState.themeStyle
    this.selectedColorMode = this.appUIState.customColorMode
  }

  private selectTheme(style: ThemeStyle): void {
    if (style === ThemeStyle.Native && !HdsUiCapability.supportsNativeTheme()) {
      return
    }
    try {
      const result = themePromptService.saveTheme(
        style,
        this.appUIState.themeStyle,
        nativeThemeCapability.current()
      )
      if (result.status !== ThemeWriteStatus.Success) {
        throw new Error(result.status)
      }
      this.selectedTheme = result.effectiveTheme
      this.appUIState.themeStyle = result.effectiveTheme
    } catch (error) {
      this.getUIContext().getPromptAction().showToast({
        message: $r('app.string.theme_save_failed')
      })
    }
  }

  private selectColorMode(mode: CustomColorMode): void {
    if (mode === CustomColorMode.Auto) {
      setAutoColorMode(this.context)
    } else if (mode === CustomColorMode.Light) {
      setLightColorMode(this.context)
    } else {
      setDarkColorMode(this.context)
    }
    AppPreference.getInstance().setCustomColorMode(mode)
    this.selectedColorMode = mode
    this.appUIState.customColorMode = mode
  }

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle,
      this.appUIState.nativeThemeAvailable
    )
  }

  build() {
    Column() {
      if (this.showLegacyActionBar) {
        ActionBar({ title: $r('app.string.appearance_title') })
      }
      Scroll(this.contentScroller) {
        Column({ space: 12 }) {
          if (HdsUiCapability.supportsNativeTheme()) {
            Text($r('app.string.theme_style_title'))
              .fontSize(14)
              .fontWeight(FontWeight.Medium)
              .fontColor(AppThemeTokens.isNative(this.appUIState.themeStyle) ?
                $r('sys.color.font_secondary') : $r('app.color.text_1'))
              .width('100%')
              .margin({ left: 4 })
            Column({ space: this.useNativeSurface() ? 0 : 12 }) {
              ColorModeThemeChoice({
                style: ThemeStyle.Native,
                title: $r('app.string.theme_native_name'),
                description: $r('app.string.theme_native_desc'),
                selectedTheme: this.selectedTheme,
                pressedTheme: this.pressedTheme,
                nativeSurface: this.useNativeSurface(),
                onSelect: (style: ThemeStyle) => this.selectTheme(style),
                onPressedChange: (style: ThemeStyle | undefined) => {
                  this.pressedTheme = style
                }
              })
              if (this.useNativeSurface()) {
                Divider()
                  .color($r('sys.color.comp_divider'))
                  .margin({ left: 12, right: 12 })
              }
              ColorModeThemeChoice({
                style: ThemeStyle.Feiniu,
                title: $r('app.string.theme_feiniu_name'),
                description: $r('app.string.theme_feiniu_desc'),
                selectedTheme: this.selectedTheme,
                pressedTheme: this.pressedTheme,
                nativeSurface: this.useNativeSurface(),
                onSelect: (style: ThemeStyle) => this.selectTheme(style),
                onPressedChange: (style: ThemeStyle | undefined) => {
                  this.pressedTheme = style
                }
              })
            }
            .width('100%')
            .borderRadius(AppThemeTokens.groupRadius(this.appUIState.themeStyle))
            .clip(this.useNativeSurface())
            .backgroundColor(this.useNativeSurface() ?
              AppThemeSurfaceResolver.contentGroupBackground() : Color.Transparent)
          }
          Text($r('app.string.color_mode_title'))
            .fontSize(14)
            .fontWeight(FontWeight.Medium)
            .fontColor(AppThemeTokens.isNative(this.appUIState.themeStyle) ?
              $r('sys.color.font_secondary') : $r('app.color.text_1'))
            .width('100%')
            .margin({ top: 10, left: 4 })
          ColorModeSelector({
            items: this.colorModes,
            selectedMode: this.selectedColorMode,
            nativeSurface: this.useNativeSurface(),
            onSelect: (mode: CustomColorMode) => this.selectColorMode(mode)
          })
          Text($r('app.string.color_mode_auto_desc'))
            .fontSize(12)
            .fontColor(AppThemeTokens.isNative(this.appUIState.themeStyle) ?
              $r('sys.color.font_tertiary') : $r('app.color.text_2'))
            .width('100%')
            .margin({ left: 4 })
            .visibility(this.selectedColorMode === CustomColorMode.Auto ?
              Visibility.Visible : Visibility.Hidden)
        }
        .width('100%')
        .constraintSize({ maxWidth: 560 })
        .padding({ left: 16, right: 16, top: 12, bottom: 32 })
        .alignItems(HorizontalAlign.Center)
      }
      .width('100%')
      .layoutWeight(1)
      .align(Alignment.Top)
      .scrollBar(BarState.Off)
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.showLegacyActionBar ? $r('app.color.bg_main') : Color.Transparent)
    .expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.BOTTOM])
  }
}

@HMRouter({
  pageUrl: RouterConsts.ColorModePage,
  useNavDst: true
})
@ComponentV2
export struct ColorModePage {
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
  private readonly contentScroller: Scroller = new Scroller()

  private routeOptions(): AppRouteDestinationOptions {
    return {
        title: $r('app.string.appearance_title'),
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
              ColorModePageSurface({
                showLegacyActionBar: false,
                contentScroller: this.contentScroller
              })
            } else {
              ColorModePageSurface({
                showLegacyActionBar: true,
                contentScroller: this.contentScroller
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
        ColorModePageSurface({
          showLegacyActionBar: true,
          contentScroller: this.contentScroller
        })
      }
      .attributeModifier(this.routeDestination.legacyModifier(this.routeOptions()))
      .gestureModifier(this.routeDestination.gestureModifier)
      .width('100%')
      .height('100%')
    }
  }
}
