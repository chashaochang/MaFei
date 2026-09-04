import { HMRouterMgr } from '@hadss/hmrouter'
import { HORIZONTAL_CARD_SPACE } from '../../../common/OneMoreConst'
import { RouterConsts } from '../../../common/RouterConsts'
import { VerticalVideoCard } from '../../../component/VerticalVideoCard'
import { toMediaRouteRefData } from '../../../media/navigation/MediaRouteRef'
import { MediaRefKind } from '../../../media/MediaRef'
import { HOME_RECENT_MEDIA_ID, VideoItem } from './HomeUIState'

const LATEST_CONTENT_GAP: number = 16
const LATEST_GRID_ROW_GAP: number = 20
const LATEST_PHONE_COLUMN_GAP: number = 8
const LATEST_WIDE_COLUMN_GAP: number = 12

@ComponentV2
struct HomeLatestPosterGrid {
  @Require @Param items: VideoItem[]
  @Require @Param selectedMediaId: string
  @Require @Param gridColumns: string
  @Require @Param columnGap: number
  @Event onOpenDetails: (item: VideoItem) => void = (_item: VideoItem) => {}

  build() {
    Grid() {
      ForEach(this.items, (item: VideoItem, index: number) => {
        GridItem() {
          Row() {
            VerticalVideoCard({
              image: item.image,
              imageRef: item.imageRef,
              name: item.name,
              subtitle: item.subtitle,
              score: item.score,
              tags: item.tags || [],
              index
            })
          }
          .width('100%')
          .justifyContent(FlexAlign.Center)
        }
        .onClick(() => this.onOpenDetails(item))
      }, (item: VideoItem) => `${this.selectedMediaId}:${item.id}`)
    }
    .width('100%')
    .columnsTemplate(this.gridColumns)
    .columnsGap(this.columnGap)
    .rowsGap(LATEST_GRID_ROW_GAP)
    .margin({ top: LATEST_CONTENT_GAP })
    .scrollBar(BarState.Off)
  }
}

@ComponentV2
struct HomeLatestLoadingGrid {
  @Require @Param gridColumns: string
  @Require @Param columnGap: number

  build() {
    Grid() {
      ForEach([0, 1, 2, 3, 4, 5], () => {
        GridItem() {
          Row() {
            Column({ space: 8 }) {
              Column()
                .width('100%')
                .aspectRatio(2 / 3)
                .borderRadius(10)
                .backgroundColor($r('sys.color.comp_background_tertiary'))
              Column()
                .width('100%')
                .height(14)
                .borderRadius(7)
                .backgroundColor($r('sys.color.comp_background_tertiary'))
            }
            .width('100%')
          }
          .width('100%')
          .justifyContent(FlexAlign.Center)
        }
      })
    }
    .width('100%')
    .columnsTemplate(this.gridColumns)
    .columnsGap(this.columnGap)
    .rowsGap(LATEST_GRID_ROW_GAP)
    .margin({ top: LATEST_CONTENT_GAP })
    .scrollBar(BarState.Off)
    .opacity(0.7)
  }
}

@ComponentV2
struct HomeLatestEmptyState {
  @Require @Param recentMediaSelected: boolean
  @Require @Param nativeSurface: boolean

  build() {
    Row() {
      Text(this.recentMediaSelected ? '暂无最近添加内容' : '该媒体库暂无内容')
        .fontSize(14)
        .fontColor(this.nativeSurface ?
          $r('sys.color.font_secondary') : $r('app.color.text_1'))
    }
    .width('100%')
    .height(96)
    .alignItems(VerticalAlign.Center)
  }
}

@ComponentV2
struct HomeLatestRetryState {
  @Require @Param message: string
  @Require @Param nativeSurface: boolean
  @Event onRetry: () => void = () => {}

  build() {
    Column({ space: 12 }) {
      Text(this.message)
        .fontSize(14)
        .fontColor(this.nativeSurface ?
          $r('sys.color.font_secondary') : $r('app.color.text_1'))
      Button({ type: ButtonType.Capsule, stateEffect: true }) {
        Text('重试')
          .fontSize(14)
      }
      .height(36)
      .padding({ left: 18, right: 18 })
      .onClick(() => this.onRetry())
    }
    .width('100%')
    .height(120)
    .justifyContent(FlexAlign.Center)
  }
}

@ComponentV2
struct HomeLatestPagingState {
  @Require @Param loadingMore: boolean
  @Require @Param loadFailed: boolean
  @Require @Param nativeSurface: boolean
  @Event onRetry: () => void = () => {}

  build() {
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
      HomeLatestRetryState({
        message: '加载更多失败',
        nativeSurface: this.nativeSurface,
        onRetry: () => this.onRetry()
      })
    }
  }
}

@ComponentV2
struct HomeLatestFeedContent {
  @Require @Param items: VideoItem[]
  @Require @Param selectedMediaId: string
  @Require @Param gridColumns: string
  @Require @Param columnGap: number
  @Require @Param nativeSurface: boolean
  @Require @Param loading: boolean
  @Require @Param loadingMore: boolean
  @Require @Param loadFailed: boolean
  @Param contentMinHeight: number = 0
  @Event onRetry: () => void = () => {}
  @Event onOpenDetails: (item: VideoItem) => void = (_item: VideoItem) => {}

  build() {
    Column() {
      if (this.loading && this.items.length === 0) {
        HomeLatestLoadingGrid({
          gridColumns: this.gridColumns,
          columnGap: this.columnGap
        })
      } else if (this.items.length > 0) {
        HomeLatestPosterGrid({
          items: this.items,
          selectedMediaId: this.selectedMediaId,
          gridColumns: this.gridColumns,
          columnGap: this.columnGap,
          onOpenDetails: (item: VideoItem) => this.onOpenDetails(item)
        })
        HomeLatestPagingState({
          loadingMore: this.loadingMore,
          loadFailed: this.loadFailed,
          nativeSurface: this.nativeSurface,
          onRetry: () => this.onRetry()
        })
      } else if (this.loadFailed) {
        HomeLatestRetryState({
          message: '内容加载失败',
          nativeSurface: this.nativeSurface,
          onRetry: () => this.onRetry()
        })
      } else {
        HomeLatestEmptyState({
          recentMediaSelected: this.selectedMediaId === HOME_RECENT_MEDIA_ID,
          nativeSurface: this.nativeSurface
        })
      }
    }
    .width('100%')
    .constraintSize({ minHeight: this.contentMinHeight })
    .alignItems(HorizontalAlign.Start)
  }
}

@ComponentV2
export struct HomeLatestMediaSection {
  @Require @Param latestMap: Map<string, VideoItem[]>
  @Require @Param selectedMediaId: string
  @Require @Param nativeSurface: boolean
  @Require @Param currentBreakpoint: string
  @Param gridColumnCount: number = 0
  @Require @Param loading: boolean
  @Require @Param loadingMore: boolean
  @Require @Param hasMore: boolean
  @Require @Param loadFailed: boolean
  @Event onRetry: () => void = () => {}
  @Param contentMinHeight: number = 0

  private sidePadding(): number {
    return HORIZONTAL_CARD_SPACE.getValue(this.currentBreakpoint) * 2
  }

  private selectedItems(): VideoItem[] {
    return this.latestMap.get(this.selectedMediaId) || []
  }

  private columnGap(): number {
    return this.currentBreakpoint.includes('s') ? LATEST_PHONE_COLUMN_GAP : LATEST_WIDE_COLUMN_GAP
  }

  private gridColumns(): string {
    if (this.gridColumnCount > 0) {
      return `repeat(${this.gridColumnCount}, 1fr)`
    }
    if (this.currentBreakpoint.includes('s') || this.currentBreakpoint === 'md') {
      return 'repeat(3, 1fr)'
    }
    if (this.currentBreakpoint === 'lg') {
      return 'repeat(4, 1fr)'
    }
    return 'repeat(5, 1fr)'
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

  build() {
    Column() {
      HomeLatestFeedContent({
        items: this.selectedItems(),
        selectedMediaId: this.selectedMediaId,
        gridColumns: this.gridColumns(),
        columnGap: this.columnGap(),
        nativeSurface: this.nativeSurface,
        loading: this.loading,
        loadingMore: this.loadingMore,
        loadFailed: this.loadFailed,
        contentMinHeight: this.contentMinHeight,
        onRetry: () => this.onRetry(),
        onOpenDetails: (item: VideoItem) => this.openDetails(item)
      })
    }
    .width('100%')
    .padding({ left: this.sidePadding(), right: this.sidePadding() })
    .alignItems(HorizontalAlign.Start)
  }
}
