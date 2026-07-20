import { UIConstants } from '../../common/UIConstants'
import { AppStorageV2 } from '@kit.ArkUI'
import { AppUIState } from '../../entity/AppUIState'
import { AppThemeTokens } from '../../theme/AppThemeTokens'

@ComponentV2
export struct CustomTab {
  @Require @Param selectIndex: number
  @Require @Param onItemClick: (index: number) => void
  @Param chasingBadgeCount: number = 0
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;
  list: TabItem[] = [
    new TabItem('首页', $r('app.media.ic_home_sel'), $r('app.media.ic_home_unsel')),
    new TabItem('追剧', $r('app.media.history'), $r('app.media.history')),
    new TabItem('收藏', $r('app.media.ic_favorite_sel'), $r('app.media.ic_favorite_unsel')),
    new TabItem('媒体库', $r('app.media.ic_media_sel'), $r('app.media.ic_media_unsel')),
    new TabItem('我的', $r('app.media.ic_mine_sel'), $r('app.media.ic_mine_unsel')),
  ]

  build() {
    Row() {
      ForEach(this.list, (item: TabItem, index: number) => {
        Column({ space: 4 }) {
          Stack({ alignContent: Alignment.TopEnd }) {
            Image(this.selectIndex === index ? item.selectedImg : item.normalImg)
              .size({ width: 20, height: 20 })
              .fillColor(this.selectIndex === index ? $r('app.color.color_main') : $r('app.color.text_primary'))
            if (index === 1 && this.chasingBadgeCount > 0) {
              Text(this.chasingBadgeCount > 99 ? '99+' : this.chasingBadgeCount.toString())
                .fontSize(9)
                .fontWeight(FontWeight.Bold)
                .fontColor(Color.White)
                .height(14)
                .width(this.chasingBadgeCount > 9 ? 22 : 14)
                .padding({ left: 4, right: 4 })
                .backgroundColor('#ff4d4f')
                .borderRadius(7)
                .translate({ x: 8, y: -5 })
            }
          }
          .width(32)
          .height(22)
          .alignContent(Alignment.Center)
          Text(item.title)
            .fontSize(13)
            .maxLines(1)
            .width('100%')
            .textAlign(TextAlign.Center)
            .fontColor(this.selectIndex === index ? $r('app.color.color_main') : $r('app.color.text_primary'))
        }
        .height('100%')
        .layoutWeight(1)
        .alignItems(HorizontalAlign.Center)
        .justifyContent(FlexAlign.Center)
        .backgroundColor(this.selectIndex === index ?
          (AppThemeTokens.isNative(this.appUIState.themeStyle) ?
            $r('app.color.primary_light_hover') : $r('app.color.bg_tab')) : Color.Transparent)
        .borderRadius(AppThemeTokens.navigationItemRadius(this.appUIState.themeStyle))
        .onClick(() => {
          this.onItemClick(index)
        })
      })
    }
    .width('calc(100% - 40vp)')
    .padding(3)
    .height(UIConstants.BOTTOM_BAR_HEIGHT)
    .backgroundEffect({
      radius: 12,
      color: $r('app.color.bg_tab_item')
    })
    .borderRadius(AppThemeTokens.navigationRadius(this.appUIState.themeStyle))
    .borderWidth(AppThemeTokens.isNative(this.appUIState.themeStyle) ? 1 : 1.5)
    .borderColor($r('app.color.tab'))
    .shadow({
      radius: AppThemeTokens.isNative(this.appUIState.themeStyle) ? 24 : 50,
      color: $r('app.color.border'),
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
