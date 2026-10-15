import { HMRouterMgr } from "@hadss/hmrouter"
import { ImageKnifeComponent } from "@ohos/imageknifepro"
import { PageState } from "../../../common/base/BaseUIState"
import { CustomRefreshComponent } from "../../../common/CustomRefreshComponent"
import { HORIZONTAL_CARD_LIST_HEIGHT, HORIZONTAL_CARD_SPACE } from "../../../common/OneMoreConst"
import { playMediaRefs } from "../../../common/CommonFunc"
import { ActionBar } from "../../../component/ActionBar"
import { EmptyView } from "../../../component/EmptyView"
import { FailedView } from "../../../component/FailedView"
import { HorizontalVideoCard } from "../../../component/HorizontalVideoCard"
import { RouterConsts } from "../../../common/RouterConsts"
import { VideoItem } from "../hometab/HomeUIState"
import { ChaseSection, ChasingUIState } from "./ChasingUIState"
import { ChasingViewModel } from "./ChasingViewModel"
import { HomeInsetsPolicy } from "../HomeInsetsPolicy"
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from "../../../theme/AppThemeSurfaceResolver"
import { MediaProviderRegistry } from "../../../media/MediaProviderRegistry"

@ComponentV2
struct ChasingFilterChip {
  @Require @Param label: string
  @Require @Param index: number
  @Require @Param selectedIndex: number
  @Require @Param nativeSurface: boolean
  @Require @Param systemMaterialAvailable: boolean
  @Event onSelect: (index: number) => void = () => {
  }

  build() {
    if (this.nativeSurface && this.selectedIndex !== this.index) {
      Text(this.label)
        .fontSize(13)
        .fontWeight(FontWeight.Medium)
        .fontColor($r('sys.color.font_secondary'))
        .textAlign(TextAlign.Center)
        .layoutWeight(1)
        .height(36)
        .padding({ left: 8, right: 8 })
        .borderRadius(18)
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.InteractiveFloating,
          true,
          this.systemMaterialAvailable
        ))
        .onClick(() => this.onSelect(this.index))
    } else {
      Text(this.label)
        .fontSize(13)
        .fontWeight(FontWeight.Medium)
        .fontColor(this.selectedIndex === this.index ? Color.White : $r('app.color.text_1'))
        .textAlign(TextAlign.Center)
        .layoutWeight(1)
        .height(36)
        .padding({ left: 8, right: 8 })
        .backgroundColor(this.selectedIndex === this.index ?
          $r('app.color.color_main') : $r('app.color.bg_1'))
        .borderColor(this.selectedIndex === this.index ?
          $r('app.color.color_main') : $r('app.color.border'))
        .borderWidth(this.nativeSurface ? 0 : 0.5)
        .borderRadius(18)
        .onClick(() => this.onSelect(this.index))
    }
  }
}

@ComponentV2
struct ChasingFeaturedUpdate {
  @Require @Param item: VideoItem
  @Require @Param horizontalSpacing: number
  @Event onPressed: (item: VideoItem) => void = () => {
  }

  build() {
    Stack({ alignContent: Alignment.BottomStart }) {
      ImageKnifeComponent({
        imageKnifeOption: {
          loadSrc: this.item.image,
          objectFit: ImageFit.Cover,
          placeholderSrc: $r('app.media.placeholder_video'),
          placeholderObjectFit: ImageFit.CENTER
        }
      })
        .width('100%')
        .height('100%')
      Column()
        .width('100%')
        .height('100%')
        .linearGradient({
          angle: 180,
          colors: [[Color.Transparent, 0.0], ['rgba(0,0,0,0.70)', 1.0]]
        })
      Column({ space: 8 }) {
        Text('下一集待看')
          .fontSize(12)
          .fontWeight(FontWeight.Bold)
          .fontColor(Color.White)
          .padding({ left: 10, right: 10, top: 4, bottom: 4 })
          .backgroundColor($r('app.color.color_main'))
          .borderRadius(99)
        Text(this.item.name)
          .fontSize(21)
          .fontWeight(FontWeight.Bold)
          .fontColor(Color.White)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        Text(this.item.subtitle)
          .fontSize(13)
          .fontColor('rgba(255,255,255,0.82)')
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
      }
      .padding(16)
      .width('100%')
    }
    .width('calc(100% - 32vp)')
    .height(148)
    .borderRadius(22)
    .clip(true)
    .margin({ left: this.horizontalSpacing * 2, right: this.horizontalSpacing * 2, top: 14 })
    .onClick(() => this.onPressed(this.item))
  }
}

@ComponentV2
struct ChasingSectionList {
  @Require @Param title: string
  @Require @Param subtitle: string
  @Require @Param items: VideoItem[]
  @Require @Param kind: string
  @Require @Param horizontalSpacing: number
  @Require @Param nativeSurface: boolean
  @Require @Param primaryColor: ResourceColor
  @Require @Param secondaryColor: ResourceColor
  @Event onItemPressed: (item: VideoItem, kind: string) => void = () => {
  }

  build() {
    Column() {
      Row() {
        Column({ space: 3 }) {
          Text(this.title)
            .fontSize(15)
            .fontWeight(FontWeight.Bold)
            .fontColor(this.primaryColor)
          Text(this.subtitle)
            .fontSize(12)
            .fontColor(this.secondaryColor)
        }
        .alignItems(HorizontalAlign.Start)
        Blank().layoutWeight(1)
        Text(this.items.length.toString())
          .fontSize(12)
          .fontColor(this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_2'))
          .padding({ left: 8, right: 8, top: 3, bottom: 3 })
          .backgroundColor(this.nativeSurface ? Color.Transparent : $r('app.color.bg_1'))
          .borderRadius(99)
      }
      .width('100%')
      .padding({ left: this.horizontalSpacing * 2, right: this.horizontalSpacing * 2 })

      List({ space: this.horizontalSpacing }) {
        ListItem().width(this.horizontalSpacing)
        ForEach(this.items, (item: VideoItem, index: number) => {
          ListItem() {
            HorizontalVideoCard({
              image: item.image,
              percent: item.percent,
              name: item.name,
              subtitle: item.subtitle,
              index
            })
          }
          .onClick(() => this.onItemPressed(item, this.kind))
        })
        ListItem().width(this.horizontalSpacing)
      }
      .width('100%')
      .height(HORIZONTAL_CARD_LIST_HEIGHT)
      .margin({ top: 12 })
      .listDirection(Axis.Horizontal)
      .scrollBar(BarState.Off)
    }
    .width('100%')
    .margin({ top: 22 })
    .alignItems(HorizontalAlign.Start)
  }
}

@ComponentV2
struct ChasingRefreshIndicator {
  @Require @Param refreshOffset: number
  @Require @Param refreshStatus: RefreshStatus

  build() {
    CustomRefreshComponent({ refreshOffset: this.refreshOffset, refreshStatus: this.refreshStatus })
  }
}

@ComponentV2
export struct ChasingTab {
  @Param fromHome: boolean = false
  @Param contentTopInset: number = 0
  @Param contentBottomInset?: number = undefined
  @Param contentScroller: Scroller = new Scroller()
  @Local ui: ChasingUIState = new ChasingUIState(this.getUIContext())
  @Local vm: ChasingViewModel = new ChasingViewModel(this.ui)
  @Local selectedFilterIndex: number = 0

  private scrollTail(): number {
    return HomeInsetsPolicy.scrollTail(this.contentBottomInset, this.vm.appUIState.safeBottom)
  }

  private ownedContentTopInset(): number {
    return this.fromHome ? Math.max(0, this.contentTopInset) : 0
  }

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable)
  }

  private pageBackground(): ResourceColor {
    if (this.vm.appUIState.isBigScreen) {
      return Color.Transparent
    }
    return AppThemeSurfaceResolver.routeBackground(this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable)
  }

  private primaryTextColor(): ResourceColor {
    return this.useNativeSurface() ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return this.useNativeSurface() ? $r('sys.color.font_secondary') : $r('app.color.text_2')
  }

  aboutToAppear(): void {
    this.vm.refresh()
  }

  private getSectionCount(kind: string): number {
    const section = this.ui.sections.find((item: ChaseSection) => item.kind === kind)
    return section ? section.items.length : 0
  }

  private getSectionItems(kind: string): VideoItem[] {
    const section = this.ui.sections.find((item: ChaseSection) => item.kind === kind)
    return section ? section.items : []
  }

  private getAllItems(): VideoItem[] {
    const merged: VideoItem[] = []
    this.appendUniqueItems(merged, this.getSectionItems('nextUp'))
    this.appendUniqueItems(merged, this.getSectionItems('continue'))
    this.appendUniqueItems(merged, this.getSectionItems('completed'))
    return merged
  }

  private getRemainingNextUpItems(): VideoItem[] {
    const items = this.getSectionItems('nextUp')
    return items.length > 1 ? items.slice(1) : []
  }

  private getFilteredItems(): VideoItem[] {
    if (this.selectedFilterIndex === 0) {
      return this.getSectionItems('nextUp')
    }
    if (this.selectedFilterIndex === 1) {
      return this.getSectionItems('continue')
    }
    if (this.selectedFilterIndex === 2) {
      return this.getSectionItems('completed')
    }
    return this.getAllItems()
  }

  private appendUniqueItems(target: VideoItem[], incoming: VideoItem[]): void {
    incoming.forEach((item: VideoItem) => {
      const key = item.seriesId || item.id
      if (target.findIndex((current: VideoItem) => (current.seriesId || current.id) === key) < 0) {
        target.push(item)
      }
    })
  }

  private openItem(item: VideoItem): void {
    if (item.type === 'Episode') {
      this.playItem(item)
      return
    }
    this.openSeriesDetail(item)
  }

  private playItem(item: VideoItem): void {
    const activeSession = MediaProviderRegistry.instance().getActiveSession()
    if (!activeSession || !item.mediaRef || !item.mediaRef.belongsTo(activeSession)) {
      return
    }
    playMediaRefs([item.mediaRef], this.getUIContext().getHostContext()!)
  }

  private openSeriesDetail(item: VideoItem): void {
    HMRouterMgr.to(RouterConsts.VideoDetailPage)
      .withParam({
        id: item.seriesId || item.id,
        isTvShows: true
      })
      .push()
  }

  private handleSectionItem(item: VideoItem, kind: string): void {
    if (kind === 'completed') {
      this.openSeriesDetail(item)
      return
    }
    if (kind === 'continue') {
      this.playItem(item)
      return
    }
    this.openItem(item)
  }

  private horizontalSpacing(): number {
    return HORIZONTAL_CARD_SPACE.getValue(this.vm.appUIState.currentBreakpoint)
  }

  // CustomBuilderAdapter: chasing-refresh-indicator
  @Builder
  private refreshIndicatorBuilder() {
    ChasingRefreshIndicator({
      refreshOffset: this.ui.refreshOffset,
      refreshStatus: this.ui.refreshStatus
    })
  }

  build() {
    Stack() {
      Column() {
        if (this.useNativeSurface()) {
          ActionBar({ title: '追剧', showBackButton: false })
            .visibility(this.fromHome ? Visibility.None : Visibility.Visible)
        } else {
          ActionBar({ title: '追剧', showBackButton: false })
            .backgroundBlurStyle(BlurStyle.COMPONENT_THICK)
            .visibility(this.fromHome ? Visibility.None : Visibility.Visible)
        }

        Refresh({
          refreshing: $$this.ui.isRefreshing,
          builder: () => {
            this.refreshIndicatorBuilder()
          }
        }) {
          List({ scroller: this.contentScroller }) {
            if (this.ownedContentTopInset() > 0) {
              ListItem().height(this.ownedContentTopInset())
            }
            if (this.ui.followedCount > 0 || this.ui.totalCount > 0) {
              ListItem() {
                Row({ space: 8 }) {
                  ForEach(['待看', '继续看', '已看完', '全部'], (label: string, index: number) => {
                    ChasingFilterChip({
                      label,
                      index,
                      selectedIndex: this.selectedFilterIndex,
                      nativeSurface: this.useNativeSurface(),
                      systemMaterialAvailable: this.vm.appUIState.systemMaterialAvailable,
                      onSelect: (selectedIndex: number) => {
                        this.selectedFilterIndex = selectedIndex
                      }
                    })
                  })
                }
                .width('calc(100% - 32vp)')
                .margin({
                  left: this.horizontalSpacing() * 2,
                  right: this.horizontalSpacing() * 2,
                  top: 12
                })
              }
            }
            if (this.ui.followedCount > 0 && this.getFilteredItems().length === 0) {
              ListItem() {
                Column({ space: 8 }) {
                  Text(this.getFilterEmptyTitle())
                    .fontSize(16)
                    .fontWeight(FontWeight.Medium)
                    .fontColor(this.primaryTextColor())
                    .textAlign(TextAlign.Center)
                  Text(this.getFilterSubtitle())
                    .fontSize(13)
                    .fontColor(this.secondaryTextColor())
                    .textAlign(TextAlign.Center)
                }
                .width('100%')
                .height(180)
                .padding({ left: 32, right: 32 })
                .justifyContent(FlexAlign.Center)
              }
            } else if (this.selectedFilterIndex === 0 && this.getSectionItems('nextUp').length > 0) {
              ListItem() {
                ChasingFeaturedUpdate({
                  item: this.getSectionItems('nextUp')[0],
                  horizontalSpacing: this.horizontalSpacing(),
                  onPressed: (item: VideoItem) => this.openItem(item)
                })
              }
              if (this.getRemainingNextUpItems().length > 0) {
                ListItem() {
                  ChasingSectionList({
                    title: '接下来',
                    subtitle: `${this.getRemainingNextUpItems().length} 集等待播放`,
                    items: this.getRemainingNextUpItems(),
                    kind: 'nextUp',
                    horizontalSpacing: this.horizontalSpacing(),
                    nativeSurface: this.useNativeSurface(),
                    primaryColor: this.primaryTextColor(),
                    secondaryColor: this.secondaryTextColor(),
                    onItemPressed: (item: VideoItem, kind: string) => this.handleSectionItem(item, kind)
                  })
                }
              }
            } else if (this.selectedFilterIndex === 1) {
              ListItem() {
                ChasingSectionList({
                  title: '继续观看',
                  subtitle: `${this.getSectionItems('continue').length} 个内容可继续播放`,
                  items: this.getSectionItems('continue'),
                  kind: 'continue',
                  horizontalSpacing: this.horizontalSpacing(),
                  nativeSurface: this.useNativeSurface(),
                  primaryColor: this.primaryTextColor(),
                  secondaryColor: this.secondaryTextColor(),
                  onItemPressed: (item: VideoItem, kind: string) => this.handleSectionItem(item, kind)
                })
              }
            } else if (this.selectedFilterIndex === 2) {
              ListItem() {
                ChasingSectionList({
                  title: '已看完',
                  subtitle: `${this.getSectionItems('completed').length} 部剧集已看完`,
                  items: this.getSectionItems('completed'),
                  kind: 'completed',
                  horizontalSpacing: this.horizontalSpacing(),
                  nativeSurface: this.useNativeSurface(),
                  primaryColor: this.primaryTextColor(),
                  secondaryColor: this.secondaryTextColor(),
                  onItemPressed: (item: VideoItem, kind: string) => this.handleSectionItem(item, kind)
                })
              }
            } else if (this.selectedFilterIndex === 3) {
              ListItem() {
                ChasingSectionList({
                  title: '全部追剧',
                  subtitle: this.getFilterSubtitle(),
                  items: this.getFilteredItems(),
                  kind: 'all',
                  horizontalSpacing: this.horizontalSpacing(),
                  nativeSurface: this.useNativeSurface(),
                  primaryColor: this.primaryTextColor(),
                  secondaryColor: this.secondaryTextColor(),
                  onItemPressed: (item: VideoItem, kind: string) => this.handleSectionItem(item, kind)
                })
              }
            }
            ListItem().height(this.scrollTail())
          }
          .width('100%')
          .height('100%')
          .listDirection(Axis.Vertical)
          .scrollBar(BarState.Off)
        }
        .pullDownRatio(0.3)
        .refreshOffset(64)
        .maxPullDownDistance(64 + this.vm.appUIState.safeTop)
        .layoutWeight(1)
        .onStateChange((status) => {
          this.ui.refreshStatus = status
        })
        .onOffsetChange((offset) => {
          this.ui.refreshOffset = offset
        })
        .onRefreshing(() => {
          this.vm.refresh()
        })
      }
      .width('100%')
      .height('100%')
      .backgroundColor(this.pageBackground())

      if (this.ui.pageState === PageState.Loading) {
        LoadingProgress()
          .width(42)
          .height(42)
          .align(Alignment.Center)
      }
      if (this.ui.pageState === PageState.Error) {
        FailedView()
          .onClick(() => {
            this.vm.refresh()
          })
      }
      if (this.ui.pageState === PageState.Content && this.ui.followedCount === 0) {
        EmptyView({ title: '暂无追剧，去剧集详情页添加' })
      }
    }
    .width('100%')
    .height('100%')
    .alignContent(Alignment.Top)
  }

  private getFilterSubtitle(): string {
    if (this.selectedFilterIndex === 0) {
      return '按下一集优先展示'
    }
    if (this.selectedFilterIndex === 1) {
      return '只看已有播放进度'
    }
    if (this.selectedFilterIndex === 2) {
      return '只看已完整看完的剧集'
    }
    return '全部已关注剧集'
  }

  private getFilterEmptyTitle(): string {
    if (this.selectedFilterIndex === 0) {
      return '暂无待看的下一集'
    }
    if (this.selectedFilterIndex === 1) {
      return '暂无可继续观看的内容'
    }
    if (this.selectedFilterIndex === 2) {
      return '暂无已看完的剧集'
    }
    return '暂无可展示的追剧内容'
  }

}
