import { ThemeStyle } from '../entity/ThemeStyle';
import { ThemeStylePreview } from './ThemeStylePreview';

@ComponentV2
struct ThemeUpgradePreviewPanel {
  build() {
    Row({ space: 12 }) {
      Column({ space: 6 }) {
        ThemeStylePreview({ style: ThemeStyle.Native, compact: true })
        Text($r('app.string.theme_native_name'))
          .fontSize(11)
          .fontColor($r('sys.color.font_secondary'))
      }
      .alignItems(HorizontalAlign.Start)

      Column({ space: 6 }) {
        ThemeStylePreview({ style: ThemeStyle.Feiniu, compact: true })
        Text($r('app.string.theme_feiniu_name'))
          .fontSize(11)
          .fontColor($r('sys.color.font_secondary'))
      }
      .alignItems(HorizontalAlign.Start)
    }
    .width('100%')
    .height(150)
    .padding(16)
    .justifyContent(FlexAlign.Center)
    .borderRadius(20)
    .backgroundColor($r('sys.color.comp_background_secondary'))
  }
}

@ComponentV2
export struct ThemeUpgradeDialog {
  @Require @Param onSelectFeiniu: () => void
  @Require @Param onSelectNative: () => void
  @Require @Param onDismiss: () => void

  build() {
    Stack({ alignContent: Alignment.TopEnd }) {
      Column({ space: 14 }) {
        Text($r('app.string.theme_prompt_title'))
          .fontSize(20)
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.text_primary'))
          .width('100%')
          .padding({ right: 36 })
        Text($r('app.string.theme_prompt_content'))
          .fontSize(14)
          .fontColor($r('app.color.text_1'))
          .width('100%')
        ThemeUpgradePreviewPanel()
        Button($r('app.string.theme_prompt_native_action'), { type: ButtonType.Normal })
          .width('100%')
          .height(44)
          .fontSize(15)
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.on_primary'))
          .backgroundColor($r('app.color.color_main'))
          .borderRadius(14)
          .onClick(() => {
            this.onSelectNative()
          })
        Button($r('app.string.theme_prompt_feiniu_action'), { type: ButtonType.Normal })
          .width('100%')
          .height(44)
          .fontSize(15)
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.text_primary'))
          .backgroundColor($r('app.color.component_bg'))
          .borderRadius(14)
          .onClick(() => {
            this.onSelectFeiniu()
          })
      }
      .width('100%')
      .padding(20)
      .backgroundColor($r('sys.color.comp_background_primary'))
      .borderRadius(24)
      .alignItems(HorizontalAlign.Start)

      Button({ type: ButtonType.Circle }) {
        SymbolGlyph($r('sys.symbol.xmark'))
          .fontSize(16)
          .fontColor([$r('sys.color.icon_secondary')])
      }
      .width(36)
      .height(36)
      .margin({ top: 12, right: 12 })
      .backgroundColor($r('sys.color.comp_background_tertiary'))
      .onClick(() => {
        this.onDismiss()
      })
    }
    .width('calc(100% - 40vp)')
    .constraintSize({ maxWidth: 360 })
  }
}
