@ComponentV2
export struct ThemeUpgradeDialog {
  @Require @Param onSelectFeiniu: () => void
  @Require @Param onSelectNative: () => void
  @Require @Param onDismiss: () => void

  @Builder
  themePreview(title: ResourceStr, nativeStyle: boolean) {
    Column({ space: 8 }) {
      Text(title)
        .fontSize(14)
        .fontWeight(FontWeight.Medium)
        .fontColor($r('app.color.text_primary'))
        .width('100%')
      Column({ space: 5 }) {
        Row().height(7).width('100%').borderRadius(4).backgroundColor($r('app.color.text_2'))
        Row().height(7).width('68%').borderRadius(4).backgroundColor($r('app.color.text_2'))
      }
      .width('100%')
      .padding(8)
      .borderRadius(nativeStyle ? 14 : 7)
      .backgroundColor($r('app.color.component_bg'))
    }
    .layoutWeight(1)
    .padding(10)
    .borderRadius(nativeStyle ? 18 : 8)
    .borderWidth(1)
    .borderColor($r('app.color.border'))
    .backgroundColor($r('app.color.bg_1'))
    .alignItems(HorizontalAlign.Start)
  }

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
        Row({ space: 10 }) {
          this.themePreview($r('app.string.theme_feiniu_name'), false)
          this.themePreview($r('app.string.theme_native_name'), true)
        }
        .width('100%')
        Button($r('app.string.theme_prompt_feiniu_action'), { type: ButtonType.Normal })
          .width('100%')
          .height(44)
          .fontSize(15)
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.on_primary'))
          .backgroundColor($r('app.color.color_main'))
          .borderRadius(14)
          .onClick(() => {
            this.onSelectFeiniu()
          })
        Button($r('app.string.theme_prompt_native_action'), { type: ButtonType.Normal })
          .width('100%')
          .height(44)
          .fontSize(15)
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.text_primary'))
          .backgroundColor($r('app.color.component_bg'))
          .borderRadius(14)
          .onClick(() => {
            this.onSelectNative()
          })
      }
      .width('100%')
      .padding(20)
      .backgroundColor($r('app.color.bg_1'))
      .borderRadius(24)
      .alignItems(HorizontalAlign.Start)

      Button({ type: ButtonType.Circle }) {
        SymbolGlyph($r('sys.symbol.xmark'))
          .fontSize(16)
          .fontColor([$r('app.color.text_1')])
      }
      .width(36)
      .height(36)
      .margin({ top: 12, right: 12 })
      .backgroundColor($r('app.color.component_bg'))
      .onClick(() => {
        this.onDismiss()
      })
    }
    .width('calc(100% - 40vp)')
    .constraintSize({ maxWidth: 360 })
  }
}
