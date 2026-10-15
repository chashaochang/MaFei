@ComponentV2
export struct MusicGridFooter {
  @Require @Param loadingMore: boolean
  @Require @Param loadMoreFailed: boolean
  @Require @Param errorText: ResourceStr
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
          Text($r('app.string.music_loading_more'))
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
      Column({ space: 6 }) {
        Text(this.errorText)
          .fontSize(12)
          .fontColor(this.textColor)
        Button($r('app.string.music_retry'), { type: ButtonType.Normal })
          .height(40)
          .borderRadius(20)
          .backgroundColor(Color.Transparent)
          .fontColor(this.textColor)
          .onClick(() => this.onRetry())
      }
      .padding({ top: 8, bottom: 8 })
    }
  }
}
