@Component
export struct EmptyView {
  @Prop title: string = '暂无数据'

  build() {
    Stack() {
      Image($r('app.media.empty'))
        .width(240)
        .height(240)
      Text(this.title)
        .fontSize(14)
        .fontColor($r('app.color.text_2'))
        .margin({
          top: 180
        })
    }
    .width('100%')
    .height('100%')
    .padding({
      bottom: 100
    })
  }
}