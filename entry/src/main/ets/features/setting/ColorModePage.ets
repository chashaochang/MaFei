import { HMRouter } from '@hadss/hmrouter';
import { RouterConsts } from '../../common/RouterConsts';
import { ActionBar } from '../../component/ActionBar';
import { AppPreference } from '../../data/AppPreference';
import { AppUIState } from '../../entity/AppUIState';
import { CustomColorMode } from '../../entity/CustomColorMode';
import { ThemeStyle } from '../../entity/ThemeStyle';
import { AppStorageV2 } from '@kit.ArkUI';
import { common } from '@kit.AbilityKit';
import { AppThemeTokens } from '../../theme/AppThemeTokens';
import { setAutoColorMode, setDarkColorMode, setLightColorMode } from './ColorModeChangeFunctions';

@HMRouter({
  pageUrl: RouterConsts.ColorModePage
})
@ComponentV2
export struct ColorModePage {
  private context: common.UIAbilityContext = this.getUIContext().getHostContext() as common.UIAbilityContext;
  @Local selectedTheme: ThemeStyle = ThemeStyle.Feiniu
  @Local selectedColorMode: CustomColorMode = CustomColorMode.Auto
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
    try {
      AppPreference.getInstance().setThemeStyle(style)
      this.selectedTheme = style
      this.appUIState.themeStyle = style
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

  @Builder
  themePreview(style: ThemeStyle) {
    Column({ space: 5 }) {
      Row().height(8).width('100%')
        .backgroundColor($r('app.color.text_2'))
        .borderRadius(4)
      Row().height(8).width('82%')
        .backgroundColor($r('app.color.text_2'))
        .borderRadius(4)
      Row().height(8).width('58%')
        .backgroundColor($r('app.color.text_2'))
        .borderRadius(4)
    }
    .width(72)
    .height(54)
    .padding(8)
    .justifyContent(FlexAlign.Center)
    .backgroundColor($r('app.color.primary_light_hover'))
    .borderRadius(AppThemeTokens.groupRadius(style))
  }

  @Builder
  themeChoice(style: ThemeStyle, title: ResourceStr, description: ResourceStr) {
    Row({ space: 12 }) {
      this.themePreview(style)
      Column({ space: 4 }) {
        Text(title)
          .fontSize(16)
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.text_primary'))
        Text(description)
          .fontSize(12)
          .fontColor($r('app.color.text_1'))
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
    .backgroundColor($r('app.color.bg_1'))
    .borderRadius(AppThemeTokens.groupRadius(style))
    .borderWidth(this.selectedTheme === style ? 2 : 1)
    .borderColor(this.selectedTheme === style ? $r('app.color.color_main') : $r('app.color.border'))
    .onClick(() => {
      this.selectTheme(style)
    })
  }

  @Builder
  colorModeSelector() {
    Row({ space: 6 }) {
      ForEach(this.colorModes, (item: ColorModeItem) => {
        Button(item.title, { type: ButtonType.Normal })
          .fontSize(14)
          .fontWeight(FontWeight.Medium)
          .fontColor(this.selectedColorMode === item.mode ?
            $r('app.color.on_primary') : $r('app.color.text_1'))
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
    .width('100%')
    .padding(5)
    .borderRadius(16)
    .backgroundColor($r('app.color.component_bg'))
  }

  build() {
    Column() {
      ActionBar({
        title: $r('app.string.appearance_title')
      })
      Scroll() {
        Column({ space: 12 }) {
          Text($r('app.string.theme_style_title'))
            .fontSize(14)
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.text_1'))
            .width('100%')
            .margin({ left: 4 })
          this.themeChoice(
            ThemeStyle.Native,
            $r('app.string.theme_native_name'),
            $r('app.string.theme_native_desc')
          )
          this.themeChoice(
            ThemeStyle.Feiniu,
            $r('app.string.theme_feiniu_name'),
            $r('app.string.theme_feiniu_desc')
          )
          Text($r('app.string.color_mode_title'))
            .fontSize(14)
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.text_1'))
            .width('100%')
            .margin({ top: 10, left: 4 })
          this.colorModeSelector()
          Text($r('app.string.color_mode_auto_desc'))
            .fontSize(12)
            .fontColor($r('app.color.text_2'))
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
      .scrollBar(BarState.Off)
    }
    .width('100%')
    .height('100%')
    .backgroundColor($r('app.color.bg_main'))
    .expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.BOTTOM])
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
