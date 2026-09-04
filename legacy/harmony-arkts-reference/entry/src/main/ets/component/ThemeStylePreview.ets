import { ThemeStyle } from '../entity/ThemeStyle';
import { AppThemeTokens } from '../theme/AppThemeTokens';

@ComponentV2
export struct ThemeStylePreview {
  @Require @Param style: ThemeStyle
  @Param compact: boolean = false

  private isNative(): boolean {
    return this.style === ThemeStyle.Native
  }

  private previewWidth(): number {
    return this.compact ? 64 : 72
  }

  private previewHeight(): number {
    return this.compact ? 46 : 54
  }

  private buttonWidth(): number {
    return this.compact ? 42 : 48
  }

  private buttonHeight(): number {
    return this.compact ? 20 : 22
  }

  build() {
    Stack({ alignContent: Alignment.Center }) {
      Row()
        .width('100%')
        .height('100%')
        .backgroundColor(this.isNative() ?
          $r('sys.color.comp_background_tertiary') : $r('app.color.primary_light_hover'))

      Row() {
        Text($r('app.string.playback_settings_title'))
          .fontSize(this.compact ? 9 : 10)
          .fontWeight(FontWeight.Medium)
          .fontColor(this.isNative() ? $r('app.color.on_primary') : $r('app.color.text_primary'))
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
      }
        .width(this.buttonWidth())
        .height(this.buttonHeight())
        .justifyContent(FlexAlign.Center)
        .alignItems(VerticalAlign.Center)
        .backgroundColor(this.isNative() ? $r('app.color.color_main') : $r('app.color.component_bg'))
        .borderRadius(this.isNative() ? this.buttonHeight() / 2 : 6)
        .borderWidth(this.isNative() ? 0 : 1)
        .borderColor($r('app.color.border'))
    }
    .width(this.previewWidth())
    .height(this.previewHeight())
    .borderRadius(AppThemeTokens.groupRadius(this.style))
    .borderWidth(this.isNative() ? 0 : 1)
    .borderColor($r('app.color.border'))
    .clip(true)
    .hitTestBehavior(HitTestMode.None)
  }
}
