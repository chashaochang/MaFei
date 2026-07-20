@ComponentV2
export struct ThemeUpgradeDialog {
  @Require @Param onSelectFeiniu: () => void
  @Require @Param onSelectNative: () => void
  @Require @Param onDismiss: () => void

  @Builder
  previewCard(highlight: boolean) {
    Column({ space: 5 }) {
      Row()
        .width('100%')
        .aspectRatio(2 / 3)
        .backgroundColor(highlight ? $r('app.color.primary_light_hover') :
          $r('sys.color.comp_background_tertiary'))
        .borderRadius(10)
      Row()
        .height(5)
        .width('78%')
        .backgroundColor($r('sys.color.font_secondary'))
        .borderRadius(3)
    }
    .layoutWeight(1)
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  nativeThemePreview() {
    Column() {
      Row() {
        Column({ space: 5 }) {
          Row().height(8).width(86).borderRadius(4).backgroundColor($r('sys.color.font_primary'))
          Row().height(6).width(132).borderRadius(3).backgroundColor($r('sys.color.font_secondary'))
        }
        .alignItems(HorizontalAlign.Start)
        Blank()
        SymbolGlyph($r('sys.symbol.magnifyingglass'))
          .fontSize(20)
          .fontColor([$r('sys.color.icon_primary')])
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)
      Row({ space: 8 }) {
        this.previewCard(true)
        this.previewCard(false)
        this.previewCard(false)
      }
      .width('100%')
      .margin({ top: 14 })
    }
    .width('100%')
    .height(150)
    .padding(16)
    .borderRadius(20)
    .backgroundColor($r('sys.color.comp_background_secondary'))
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
        this.nativeThemePreview()
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
