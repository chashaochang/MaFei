@ComponentV2
export struct ListGroup {
  @Param items: Array<BaseGroupItem> = []
  @Param onItemClick: (index: number) => void = (_index: number) => {
  }

  @Styles
  pressedStyles(): void {
    .backgroundColor($r('app.color.bg_hover'))
  }

  @Styles
  normalStyles(): void {
    .backgroundColor(Color.Transparent)
  }

  build() {
    Column({ space: 8 }) {
      Column() {
        ForEach(this.items, (item: BaseGroupItem, index) => {
          Row({ space: 10 }) {
            Image(item.icon)
              .width(18)
              .height(18)
              .fillColor($r('app.color.text_primary'))
              .visibility(item.icon != '' ? Visibility.Visible : Visibility.None)
            Text(item.name)
              .fontColor($r('app.color.text_primary'))
              .fontSize(13)
              .fontWeight(FontWeight.Medium)
              .layoutWeight(1)
            Text(item.rightText)
              .fontColor($r('app.color.text_1'))
              .fontSize(13)
              .fontWeight(FontWeight.Medium)
            SymbolGlyph($r('sys.symbol.chevron_right'))
              .fontSize(13)
              .fontColor([$r('app.color.text_primary')])
              .fontWeight(FontWeight.Bold)
              .visibility(item.showArrow ? Visibility.Visible : Visibility.None)
          }
          .width('100%')
          .height(52)
          .padding({
            top: 12,
            bottom: 12,
            left: 16,
            right: 16
          })
          .stateStyles({ pressed: this.pressedStyles, normal: this.normalStyles })
          .onClick(() => {
            this.onItemClick(index)
          })

          if (index < this.items.length - 1) {
            Divider()
              .height('0.5px')
              .backgroundColor('rgba(255,255,255,0.1)')
              .margin({
                left: 16,
                right: 16
              })
          }
        })
      }.backgroundColor($r('app.color.bg_1'))
      .borderRadius(8)
    }
  }
}

export class BaseGroupItem {
  icon: ResourceStr = ''
  name: string = ''
  rightText: ResourceStr = ''
  showArrow: boolean = true

  constructor(name: string, icon: ResourceStr = '', rightText: ResourceStr = '', showArrow: boolean = true) {
    this.name = name
    this.icon = icon
    this.rightText = rightText
    this.showArrow = showArrow
  }
}