@Preview
@ComponentV2
export struct FailedView {
  build() {
    Stack() {
      Image($r('app.media.failed'))
        .width(240)
        .height(240)
      Text('加载失败,点击重试')
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