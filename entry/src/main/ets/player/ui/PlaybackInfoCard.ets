import { PlaybackInfoEntry, PlaybackInfoSection, PlayerMenus } from './PlayerMenus'

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

      Column({ space: 10 }) {
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

        Scroll() {
          Column({ space: 12 }) {
            ForEach(this.menus.playbackInfoSections, (section: PlaybackInfoSection) => {
              Column({ space: 5 }) {
                Text(section.title)
                  .fontSize(11)
                  .fontWeight(FontWeight.Medium)
                  .fontColor('rgba(255,255,255,0.64)')
                  .lineHeight(16)

                Column({ space: 4 }) {
                  ForEach(section.entries, (item: PlaybackInfoEntry) => {
                    Row() {
                      Text(`${item.label}：`)
                        .width(82)
                        .fontSize(12)
                        .fontColor('rgba(255,255,255,0.68)')
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
                  }, (item: PlaybackInfoEntry) => `${section.title}:${item.label}:${item.value}`)
                }
                .width('100%')
              }
              .width('100%')
            }, (section: PlaybackInfoSection) => section.title)
          }
          .width('100%')
          .padding({ right: 2 })
        }
        .layoutWeight(1)
        .width('100%')
        .scrollBar(BarState.Off)
      }
      .width('72%')
      .height('76%')
      .constraintSize({ maxWidth: 360, minWidth: 250 })
      .margin({ left: 32, top: 16, bottom: 16 })
      .padding({
        top: 14,
        bottom: 14,
        left: 14,
        right: 12
      })
      .backgroundColor('#7A101010')
      .borderRadius(20)
      .border({ width: 1, color: 'rgba(255,255,255,0.10)' })
      .shadow({
        radius: 16,
        color: '#52000000',
        offsetX: 0,
        offsetY: 8
      })
      // 保留对下层播放器手势的隔离，同时让内部 Scroll 参与命中和滚动。
      .hitTestBehavior(HitTestMode.BLOCK_HIERARCHY)
    }
    .width('100%')
    .height('100%')
  }
}
