import { PlaybackInfoEntry, PlayerMenus } from "./PlayerMenus"

@ComponentV2
export struct PlaybackInfoCard {
  @Param @Require menus: PlayerMenus
  @Param @Require onDismiss: () => void

  build() {
    Stack({ alignContent: Alignment.Start }) {
      Stack()
        .width('100%')
        .height('100%')
        .onClick(() => {
          this.onDismiss()
        })

      Column({ space: 8 }) {
        Row({ space: 8 }) {
          Image($r('app.media.ic_info'))
            .width(16)
            .height(16)
            .objectFit(ImageFit.Contain)
            .fillColor(Color.White)
          Text('视频信息')
            .fontSize(14)
            .fontWeight(FontWeight.Medium)
            .fontColor(Color.White)
        }

        Column({ space: 4 }) {
          ForEach(this.menus.playBackInfoEntries, (item: PlaybackInfoEntry) => {
            Row() {
              Text(`${item.label}：`)
                .width(60)
                .fontSize(12)
                .fontColor('rgba(255,255,255,0.72)')
                .lineHeight(18)
              Text(item.value)
                .layoutWeight(1)
                .fontSize(12)
                .fontColor(Color.White)
                .lineHeight(18)
                .maxLines(item.label === '标题' ? 3 : 2)
                .textOverflow({ overflow: TextOverflow.Ellipsis })
            }
            .width('100%')
            .alignItems(VerticalAlign.Top)
          }, (item: PlaybackInfoEntry) => `${item.label}:${item.value}`)
        }
        .width('100%')
      }
      .width('50%')
      .constraintSize({ maxWidth: 280, minWidth: 210 })
      .margin({ left: 40 })
      .padding({
        top: 14,
        bottom: 14,
        left: 14,
        right: 14
      })
      .backgroundColor('#70101010')
      .borderRadius(18)
      .border({ width: 1, color: 'rgba(255,255,255,0.10)' })
      .shadow({
        radius: 16,
        color: '#52000000',
        offsetX: 0,
        offsetY: 8
      })
      .hitTestBehavior(HitTestMode.Block)
    }
    .width('100%')
    .height('100%')
  }
}
