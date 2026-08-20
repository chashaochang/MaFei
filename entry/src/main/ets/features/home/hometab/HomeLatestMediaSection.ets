import { HMRouterMgr } from '@hadss/hmrouter'
import {
  HORIZONTAL_CARD_SPACE,
  VERTICAL_CARD_HEIGHT,
  VERTICAL_CARD_WIDTH
} from '../../../common/OneMoreConst'
import { RouterConsts } from '../../../common/RouterConsts'
import { VerticalVideoCard } from '../../../component/VerticalVideoCard'
import { toMediaRouteRefData } from '../../../media/navigation/MediaRouteRef'
import { MediaRefKind } from '../../../media/MediaRef'
import { HOME_RECENT_MEDIA_ID, VideoItem } from './HomeUIState'

const LATEST_CONTENT_GAP: number = 16
const LATEST_GRID_ROW_GAP: number = 20

@ComponentV2
export struct HomeLatestMediaSection {
  @Require @Param latestMap: Map<string, VideoItem[]>
  @Require @Param selectedMediaId: string
  @Require @Param nativeSurface: boolean
  @Require @Param currentBreakpoint: string
  @Require @Param loading: boolean
  @Require @Param loadingMore: boolean
  @Require @Param hasMore: boolean
  @Require @Param loadFailed: boolean
  @Require @Param onRetry: () => void
  @Param contentMinHeight: number = 0

  private sidePadding(): number {
    return HORIZONTAL_CARD_SPACE.getValue(this.currentBreakpoint) * 2
  }

  private selectedItems(): VideoItem[] {
    return this.latestMap.get(this.selectedMediaId) || []
  }

  private gridColumns(): string {
    if (this.currentBreakpoint.includes('s') || this.currentBreakpoint === 'md') {
      return '1fr 1fr 1fr'
    }
    if (this.currentBreakpoint === 'lg') {
      return '1fr 1fr 1fr 1fr'
    }
    return '1fr 1fr 1fr 1fr 1fr'
  }

  private openDetails(item: VideoItem): void {
    HMRouterMgr.to(RouterConsts.VideoDetailPage)
      .withParam({
        id: item.id,
        ref: item.mediaRef ? toMediaRouteRefData(item.mediaRef) : undefined,
        mediaName: item.mediaName,
        isTvShows: item.kind === MediaRefKind.Series || item.mediaRef?.kind === MediaRefKind.Series
      })
      .push()
  }

  @Builder
  private posterGrid() {
    Grid() {
      ForEach(this.selectedItems(), (item: VideoItem, index: number) => {
        GridItem() {
          Row() {
            VerticalVideoCard({
              image: item.image,
              imageRef: item.imageRef,
              name: item.name,
              subtitle: item.subtitle,
              index: index
            })
          }
          .width('100%')
          .justifyContent(FlexAlign.Center)
        }
        .onClick(() => {
          this.openDetails(item)
        })
      }, (item: VideoItem) => `${this.selectedMediaId}:${item.id}`)
    }
    .width('100%')
    .columnsTemplate(this.gridColumns())
    .columnsGap(HORIZONTAL_CARD_SPACE.getValue(this.currentBreakpoint))
    .rowsGap(LATEST_GRID_ROW_GAP)
    .margin({ top: LATEST_CONTENT_GAP })
    .scrollBar(BarState.Off)
  }

  @Builder
  private loadingGridState() {
    Grid() {
      ForEach([0, 1, 2, 3, 4, 5], () => {
        GridItem() {
          Column({ space: 8 }) {
            Column()
              .width(VERTICAL_CARD_WIDTH.getValue(this.currentBreakpoint))
              .height(VERTICAL_CARD_HEIGHT.getValue(this.currentBreakpoint))
              .borderRadius(10)
              .backgroundColor($r('sys.color.comp_background_tertiary'))
            Column()
              .width(VERTICAL_CARD_WIDTH.getValue(this.currentBreakpoint))
              .height(14)
              .borderRadius(7)
              .backgroundColor($r('sys.color.comp_background_tertiary'))
          }
        }
      })
    }
    .width('100%')
    .columnsTemplate(this.gridColumns())
    .columnsGap(HORIZONTAL_CARD_SPACE.getValue(this.currentBreakpoint))
    .rowsGap(LATEST_GRID_ROW_GAP)
    .margin({ top: LATEST_CONTENT_GAP })
    .scrollBar(BarState.Off)
    .opacity(0.7)
  }

  @Builder
  private emptyState() {
    Row() {
      Text(this.selectedMediaId === HOME_RECENT_MEDIA_ID ?
        '暂无最近添加内容' : '该媒体库暂无内容')
        .fontSize(14)
        .fontColor(this.nativeSurface ?
          $r('sys.color.font_secondary') : $r('app.color.text_1'))
    }
    .width('100%')
    .height(96)
    .alignItems(VerticalAlign.Center)
  }

  @Builder
  private retryState(message: string) {
    Column({ space: 12 }) {
      Text(message)
        .fontSize(14)
        .fontColor(this.nativeSurface ?
          $r('sys.color.font_secondary') : $r('app.color.text_1'))
      Button({ type: ButtonType.Capsule, stateEffect: true }) {
        Text('重试')
          .fontSize(14)
      }
      .height(36)
      .padding({ left: 18, right: 18 })
      .onClick(() => {
        this.onRetry()
      })
    }
    .width('100%')
    .height(120)
    .justifyContent(FlexAlign.Center)
  }

  @Builder
  private pagingState() {
    if (this.loadingMore) {
      Row() {
        LoadingProgress()
          .width(24)
          .height(24)
      }
      .width('100%')
      .height(64)
      .justifyContent(FlexAlign.Center)
    } else if (this.loadFailed) {
      this.retryState('加载更多失败')
    }
  }

  @Builder
  private feedContent() {
    Column() {
      if (this.loading) {
        this.loadingGridState()
      } else if (this.selectedItems().length > 0) {
        this.posterGrid()
        this.pagingState()
      } else if (this.loadFailed) {
        this.retryState('内容加载失败')
      } else {
        this.emptyState()
      }
    }
    .width('100%')
    .constraintSize({ minHeight: this.contentMinHeight })
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  private sectionContent() {
    Column() {
      this.feedContent()
    }
    .width('100%')
    .padding({ left: this.sidePadding(), right: this.sidePadding() })
    .alignItems(HorizontalAlign.Start)
  }

  build() {
    this.sectionContent()
  }
}
