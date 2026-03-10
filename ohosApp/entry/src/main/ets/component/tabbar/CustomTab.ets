import { UIConstants } from '../../common/UIConstants'

@ComponentV2
export struct CustomTab {
  @Require @Param selectIndex: number
  @Require @Param onItemClick: (index: number) => void
  list: TabItem[] = [
    new TabItem('首页', $r('app.media.ic_home_sel'), $r('app.media.ic_home_unsel')),
    new TabItem('媒体库', $r('app.media.ic_media_sel'), $r('app.media.ic_media_store')),
    new TabItem('我的', $r('app.media.ic_mine_sel'), $r('app.media.ic_mine')),
  ]

  build() {
    Row() {
      ForEach(this.list, (item: TabItem, index: number) => {
        Column({ space: 4 }) {
          Image(this.selectIndex === index ? item.selectedImg : item.normalImg)
            .size({ width: 20, height: 20 })
          Text(item.title)
            .fontSize(14)
            .fontColor(this.selectIndex === index ? $r('app.color.color_main') : $r('app.color.text_primary'))
        }
        .height('100%')
        .layoutWeight(1)
        .justifyContent(FlexAlign.Center)
        .backgroundColor(this.selectIndex === index ? $r('app.color.bg_tab') : Color.Transparent)
        .borderRadius(UIConstants.BOTTOM_BAR_HEIGHT / 2)
        .constraintSize({
          minWidth: 80
        })
        .onClick(() => {
          this.onItemClick(index)
        })
      })
    }
    .padding(3)
    .constraintSize({
      minWidth: 300
    })
    .height(UIConstants.BOTTOM_BAR_HEIGHT)
    .backgroundEffect({
      radius: 12,
      color: $r('app.color.bg_tab_item')
    })
    .borderRadius(UIConstants.BOTTOM_BAR_HEIGHT / 2)
    .borderWidth(1.5)
    .borderColor($r('app.color.tab'))
    .shadow({
      radius: 50,
      color: 'rgba(0,0,0,0.1)',
    })
  }
}

class TabItem {
  title: string
  selectedImg: Resource
  normalImg: Resource

  constructor(title: string, selectedImg: Resource, normalImg: Resource) {
    this.title = title
    this.selectedImg = selectedImg
    this.normalImg = normalImg
  }
}