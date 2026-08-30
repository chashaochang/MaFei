@ComponentV2
export struct ManagementDetailRow {
  @Require @Param label: ResourceStr
  @Require @Param value: ResourceStr
  @Param showDivider: boolean = true
  @Param labelMinWidth: number = 92
  @Param valueMaxLines: number = 2
  @Param emptyValue: ResourceStr = ''
  @Param dividerLeftMargin: number = 0
  @Require @Param primaryTextColor: ResourceColor
  @Require @Param secondaryTextColor: ResourceColor
  @Require @Param dividerColor: ResourceColor

  build() {
    Column() {
      Row({ space: 16 }) {
        Text(this.label)
          .fontSize(13)
          .fontColor(this.secondaryTextColor)
          .constraintSize({ minWidth: this.labelMinWidth })
        Text(this.value || this.emptyValue)
          .fontSize(13)
          .fontColor(this.primaryTextColor)
          .layoutWeight(1)
          .textAlign(TextAlign.End)
          .maxLines(this.valueMaxLines)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
      }
      .width('100%')
      .constraintSize({ minHeight: 48 })
      .alignItems(VerticalAlign.Center)
      if (this.showDivider) {
        Divider()
          .height('0.5px')
          .margin({ left: this.dividerLeftMargin })
          .backgroundColor(this.dividerColor)
      }
    }
    .width('100%')
  }
}
