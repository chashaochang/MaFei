import { AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver'
import { AppThemeTokens } from '../../../theme/AppThemeTokens'
import { ThemeStyle } from '../../../entity/ThemeStyle'
import { MineHistoryItem } from './MineHistoryPlanner'
import { MineHistoryPoster } from './MineHistoryPoster'
import { MineHistoryState } from './MineUIState'

@ComponentV2
export struct MineHistorySection {
  @Require @Param state: MineHistoryState
  @Require @Param items: MineHistoryItem[]
  @Require @Param currentBreakpoint: string
  @Require @Param nativeSurface: boolean
  @Require @Param themeStyle: ThemeStyle
  @Require @Param onItemClick: (item: MineHistoryItem) => void
  @Require @Param onRetry: () => void
  @Require @Param onMore: () => void

  private columnCount(): number {
    return this.currentBreakpoint.includes('s') || this.currentBreakpoint === 'md' ? 3 : 4
  }

  private visibleItems(): MineHistoryItem[] {
    return this.items.slice(0, this.columnCount() === 4 ? 8 : 6)
  }

  private skeletonItems(): number[] {
    return this.columnCount() === 4 ? [0, 1, 2, 3, 4, 5, 6, 7] : [0, 1, 2, 3, 4, 5]
  }

  private columnsTemplate(): string {
    return this.columnCount() === 4 ? '1fr 1fr 1fr 1fr' : '1fr 1fr 1fr'
  }

  private historyItemKey(item: MineHistoryItem): string {
    return `${item.id}|${item.latestSeasonNumber || 0}|${item.latestEpisodeNumber || 0}|` +
      `${item.seriesHasMultipleSeasons ? 1 : 0}`
  }

  private primaryTextColor(): ResourceColor {
    return this.nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  @Builder
  private loadingGrid(nativeSurface: boolean) {
    Grid() {
      ForEach(this.skeletonItems(), (_index: number) => {
        GridItem() {
          Column({ space: 8 }) {
            Column()
              .width('100%')
              .aspectRatio(2 / 3)
              .borderRadius(10)
              .backgroundColor(nativeSurface ?
                $r('sys.color.comp_background_tertiary') : $r('app.color.bg_2'))
            Column()
              .width('72%')
              .height(13)
              .borderRadius(6.5)
              .backgroundColor(nativeSurface ?
                $r('sys.color.comp_background_tertiary') : $r('app.color.bg_2'))
          }
          .width('100%')
          .alignItems(HorizontalAlign.Start)
        }
      })
    }
    .width('100%')
    .columnsTemplate(this.columnsTemplate())
    .columnsGap(10)
    .rowsGap(14)
    .scrollBar(BarState.Off)
    .opacity(0.7)
  }

  @Builder
  private historyGrid() {
    Grid() {
      ForEach(this.visibleItems(), (item: MineHistoryItem) => {
        GridItem() {
          MineHistoryPoster({
            item,
            nativeSurface: this.nativeSurface
          })
        }
        .onClick(() => {
          this.onItemClick(item)
        })
      }, (item: MineHistoryItem) => this.historyItemKey(item))
    }
    .width('100%')
    .columnsTemplate(this.columnsTemplate())
    .columnsGap(10)
    .rowsGap(14)
    .scrollBar(BarState.Off)
  }

  @Builder
  private errorState() {
    Row({ space: 8 }) {
      Text($r('app.string.mine_history_retry'))
        .fontSize(14)
        .fontColor(this.secondaryTextColor())
      SymbolGlyph($r('sys.symbol.arrow_clockwise'))
        .fontSize(18)
        .fontColor([this.secondaryTextColor()])
    }
    .height(64)
    .onClick(() => {
      this.onRetry()
    })
  }

  build() {
    if (this.state !== MineHistoryState.Empty) {
      Column({ space: 12 }) {
        Row() {
          Text($r('app.string.mine_recent_watching'))
            .fontSize(18)
            .fontWeight(FontWeight.Medium)
            .fontColor(this.primaryTextColor())
          Blank()
          if (this.state === MineHistoryState.Content && this.items.length > 0) {
            Button({ type: ButtonType.Normal }) {
              Row({ space: 2 }) {
                Text($r('app.string.mine_history_more'))
                  .fontSize(14)
                  .fontColor(this.secondaryTextColor())
                SymbolGlyph($r('sys.symbol.chevron_right'))
                  .fontSize(16)
                  .fontColor([this.secondaryTextColor()])
              }
              .alignItems(VerticalAlign.Center)
            }
              .height(36)
              .backgroundColor(Color.Transparent)
              .borderRadius(18)
              .onClick(() => this.onMore())
          }
        }
        .width('100%')
        .alignItems(VerticalAlign.Center)
        if (this.state === MineHistoryState.Loading) {
          this.loadingGrid(this.nativeSurface)
        } else if (this.state === MineHistoryState.Error) {
          this.errorState()
        } else {
          this.historyGrid()
        }
      }
      .width('100%')
      .padding(16)
      .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
      .backgroundColor(this.nativeSurface ?
        AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
    }
  }
}
