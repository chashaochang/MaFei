@ComponentV2
export struct PhotoGridFooter {
  @Require @Param loadingMore: boolean
  @Require @Param loadMoreFailed: boolean
  @Require @Param textColor: ResourceColor
  @Param progressSize: number = 22
  @Param showLoadingText: boolean = false
  @Event onRetry: () => void = () => {}

  build() {
    if (this.loadingMore) {
      if (this.showLoadingText) {
        Row({ space: 8 }) {
          LoadingProgress()
            .width(this.progressSize)
            .height(this.progressSize)
          Text($r('app.string.photo_loading_more'))
            .fontSize(13)
            .fontColor(this.textColor)
        }
        .height(52)
        .justifyContent(FlexAlign.Center)
      } else {
        LoadingProgress()
          .width(this.progressSize)
          .height(this.progressSize)
      }
    } else if (this.loadMoreFailed) {
      Button($r('app.string.photo_load_more_failed'), { type: ButtonType.Normal })
        .height(44)
        .borderRadius(22)
        .backgroundColor(Color.Transparent)
        .fontColor(this.textColor)
        .onClick(() => this.onRetry())
    }
  }
}
