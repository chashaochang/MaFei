import { HMRouter } from '@hadss/hmrouter';
import { RouterConsts } from '../../common/RouterConsts';
import { ActionBar } from '../../component/ActionBar';
import { AppRouteDestination } from '../../component/AppRouteDestination';
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

@HMRouter({
  pageUrl: RouterConsts.ColorModePage,
  useNavDst: true
})
@ComponentV2
export struct ColorModePage {
  private context: common.UIAbilityContext = this.getUIContext().getHostContext() as common.UIAbilityContext;
  private readonly contentScroller: Scroller = new Scroller()
  @Local selectedTheme: ThemeStyle = ThemeStyle.Feiniu
  @Local selectedColorMode: CustomColorMode = CustomColorMode.Auto
  @Local pressedTheme: ThemeStyle | undefined = undefined
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;
  colorModes: Array<ColorModeItem> = [
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

  private useNativeMaterial(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle,
      this.appUIState.nativeThemeAvailable
    )
  }

  private contentTopPadding(showLegacyActionBar: boolean): number {
    return 12
  }

  private nativeThemeChoiceBackground(style: ThemeStyle): ResourceColor {
    if (this.pressedTheme === style) {
      return '#142173DF'
    }
    return this.selectedTheme === style ? '#1F2173DF' : Color.Transparent
  }

  @Builder
  themePreview(style: ThemeStyle) {
    Column({ space: 5 }) {
      Row().height(8).width('100%')
        .backgroundColor(style === ThemeStyle.Native ?
          $r('sys.color.font_secondary') : $r('app.color.text_2'))
        .borderRadius(4)
      Row().height(8).width('82%')
        .backgroundColor(style === ThemeStyle.Native ?
          $r('sys.color.font_secondary') : $r('app.color.text_2'))
        .borderRadius(4)
      Row().height(8).width('58%')
        .backgroundColor(style === ThemeStyle.Native ?
          $r('sys.color.font_secondary') : $r('app.color.text_2'))
        .borderRadius(4)
    }
    .width(72)
    .height(54)
    .padding(8)
    .justifyContent(FlexAlign.Center)
    .backgroundColor(style === ThemeStyle.Native ?
      $r('sys.color.comp_background_tertiary') : $r('app.color.primary_light_hover'))
    .borderRadius(AppThemeTokens.groupRadius(style))
  }

  @Builder
  private feiniuThemeChoice(style: ThemeStyle, title: ResourceStr, description: ResourceStr) {
    Row({ space: 12 }) {
      this.themePreview(style)
      Column({ space: 4 }) {
        Text(title)
          .fontSize(16)
          .fontWeight(FontWeight.Medium)
          .fontColor(style === ThemeStyle.Native ?
            $r('sys.color.font_primary') : $r('app.color.text_primary'))
        Text(description)
          .fontSize(12)
          .fontColor(style === ThemeStyle.Native ?
            $r('sys.color.font_secondary') : $r('app.color.text_1'))
          .maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
      Radio({ value: style, group: 'themeStyle' })
        .checked(this.selectedTheme === style)
        .onChange(() => {
          this.selectTheme(style)
        })
    }
    .width('100%')
    .height(86)
    .padding(12)
    .backgroundColor(style === ThemeStyle.Native ?
      $r('sys.color.comp_background_secondary') : $r('app.color.bg_1'))
    .borderRadius(AppThemeTokens.groupRadius(style))
    .borderWidth(this.selectedTheme === style ? 2 : 1)
    .borderColor(this.selectedTheme === style ? $r('app.color.color_main') : $r('app.color.border'))
    .onClick(() => {
      this.selectTheme(style)
    })
  }

  @Builder
  private nativeThemeChoice(style: ThemeStyle, title: ResourceStr, description: ResourceStr) {
    Row({ space: 12 }) {
      this.themePreview(style)
      Column({ space: 4 }) {
        Text(title)
          .fontSize(16)
          .fontWeight(FontWeight.Medium)
          .fontColor($r('sys.color.font_primary'))
        Text(description)
          .fontSize(12)
          .fontColor($r('sys.color.font_secondary'))
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
      if (this.selectedTheme === style) {
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
    .backgroundColor(this.nativeThemeChoiceBackground(style))
    .borderRadius(12)
    .onTouch((event: TouchEvent) => {
      if (event.type === TouchType.Down) {
        this.pressedTheme = style
      } else if (event.type === TouchType.Up || event.type === TouchType.Cancel) {
        this.pressedTheme = undefined
      }
    })
    .onClick(() => {
      this.selectTheme(style)
    })
  }

  @Builder
  private themeChoicesGroup() {
    Column() {
      this.nativeThemeChoice(
        ThemeStyle.Native,
        $r('app.string.theme_native_name'),
        $r('app.string.theme_native_desc')
      )
      Divider()
        .color($r('sys.color.comp_divider'))
        .margin({ left: 12, right: 12 })
      this.nativeThemeChoice(
        ThemeStyle.Feiniu,
        $r('app.string.theme_feiniu_name'),
        $r('app.string.theme_feiniu_desc')
      )
    }
    .width('100%')
    .borderRadius(AppThemeTokens.groupRadius(this.appUIState.themeStyle))
    .clip(true)
    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  private feiniuThemeChoicesGroup() {
    Column({ space: 12 }) {
      this.feiniuThemeChoice(
        ThemeStyle.Native,
        $r('app.string.theme_native_name'),
        $r('app.string.theme_native_desc')
      )
      this.feiniuThemeChoice(
        ThemeStyle.Feiniu,
        $r('app.string.theme_feiniu_name'),
        $r('app.string.theme_feiniu_desc')
      )
    }
    .width('100%')
  }

  @Builder
  private themeChoicesSurface() {
    if (this.useNativeMaterial()) {
      this.themeChoicesGroup()
    } else {
      this.feiniuThemeChoicesGroup()
    }
  }

  @Builder
  private colorModeButtons(native: boolean) {
    ForEach(this.colorModes, (item: ColorModeItem) => {
      Button(item.title, { type: ButtonType.Normal })
        .fontSize(14)
        .fontWeight(FontWeight.Medium)
        .fontColor(this.selectedColorMode === item.mode ?
          $r('app.color.on_primary') :
          (native ? $r('sys.color.font_secondary') : $r('app.color.text_1')))
        .backgroundColor(this.selectedColorMode === item.mode ?
          $r('app.color.color_main') : Color.Transparent)
        .borderRadius(11)
        .height(44)
        .layoutWeight(1)
        .onClick(() => {
          this.selectColorMode(item.mode)
        })
    })
  }

  @Builder
  private colorModeSelector() {
    Row({ space: 6 }) {
      this.colorModeButtons(true)
    }
    .width('100%')
    .padding(5)
    .borderRadius(16)
    .clip(true)
    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  private feiniuColorModeSelector() {
    Row({ space: 6 }) {
      this.colorModeButtons(false)
    }
    .width('100%')
    .padding(5)
    .borderRadius(16)
    .backgroundColor($r('app.color.component_bg'))
  }

  @Builder
  private colorModeSelectorSurface() {
    if (this.useNativeMaterial()) {
      this.colorModeSelector()
    } else {
      this.feiniuColorModeSelector()
    }
  }

  @Builder
  private pageContent(showLegacyActionBar: boolean) {
    Column() {
      if (showLegacyActionBar) {
        ActionBar({
          title: $r('app.string.appearance_title')
        })
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
            this.themeChoicesSurface()
          }
          Text($r('app.string.color_mode_title'))
            .fontSize(14)
            .fontWeight(FontWeight.Medium)
            .fontColor(AppThemeTokens.isNative(this.appUIState.themeStyle) ?
              $r('sys.color.font_secondary') : $r('app.color.text_1'))
            .width('100%')
            .margin({ top: 10, left: 4 })
          this.colorModeSelectorSurface()
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
        .padding({
          left: 16,
          right: 16,
          top: this.contentTopPadding(showLegacyActionBar),
          bottom: 32
        })
        .alignItems(HorizontalAlign.Center)
      }
      .width('100%')
      .layoutWeight(1)
      .align(Alignment.Top)
      .scrollBar(BarState.Off)
    }
    .width('100%')
    .height('100%')
    .backgroundColor(showLegacyActionBar ? $r('app.color.bg_main') : Color.Transparent)
    .expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.BOTTOM])
  }

  build() {
    AppRouteDestination({
      title: $r('app.string.appearance_title'),
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

class ColorModeItem {
  mode: CustomColorMode
  title: ResourceStr

  constructor(mode: CustomColorMode, title: ResourceStr) {
    this.mode = mode
    this.title = title
  }
}
