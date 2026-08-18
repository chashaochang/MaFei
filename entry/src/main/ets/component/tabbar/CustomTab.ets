import { UIConstants } from '../../common/UIConstants'

@ComponentV2
export struct CustomTab {
  @Require @Param selectIndex: number
  @Require @Param onItemClick: (index: number) => void
  list: TabItem[] = [
    new TabItem('首页', 0, $r('app.media.ic_home_sel'), $r('app.media.ic_home_unsel')),
    new TabItem('收藏', 2, $r('app.media.ic_favorite_sel'), $r('app.media.ic_favorite_unsel')),
    new TabItem('媒体库', 3, $r('app.media.ic_media_sel'), $r('app.media.ic_media_unsel')),
    new TabItem('我的', 4, $r('app.media.ic_mine_unsel'), $r('app.media.ic_mine_unsel')),
  ]

  private itemColor(item: TabItem): ResourceColor {
    return this.selectIndex === item.destinationIndex ?
      $r('app.color.text_primary') : $r('app.color.text_2')
  }

  build() {
    Row() {
      ForEach(this.list, (item: TabItem) => {
        Column({ space: 3 }) {
          Image(this.selectIndex === item.destinationIndex ? item.selectedImg : item.normalImg)
            .size({ width: 24, height: 24 })
            .fillColor(this.itemColor(item))

          Text(item.title)
            .fontSize(12)
            .maxLines(1)
            .width('100%')
            .textAlign(TextAlign.Center)
            .fontColor(this.itemColor(item))
        }
        .height('100%')
        .layoutWeight(1)
        .alignItems(HorizontalAlign.Center)
        .justifyContent(FlexAlign.Center)
        .onClick(() => {
          this.onItemClick(item.destinationIndex)
        })
      }, (item: TabItem) => item.destinationIndex.toString())
    }
    .width('100%')
    .height(UIConstants.BOTTOM_BAR_HEIGHT)
  }
}

class TabItem {
  title: string
  destinationIndex: number
  selectedImg: Resource
  normalImg: Resource

  constructor(title: string, destinationIndex: number, selectedImg: Resource, normalImg: Resource) {
    this.title = title
    this.destinationIndex = destinationIndex
    this.selectedImg = selectedImg
    this.normalImg = normalImg
  }
}
