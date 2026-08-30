import { AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver'
import { AppThemeTokens } from '../../../theme/AppThemeTokens'
import { ThemeStyle } from '../../../entity/ThemeStyle'
import { MineHistoryItem } from './MineHistoryPlanner'
import { MineHistoryPoster } from './MineHistoryPoster'
import { MineHistoryState } from './MineUIState'

@ComponentV2
struct MineHistoryLoadingGrid {
  @Require @Param items: number[]
  @Require @Param columnsTemplate: string
  @Require @Param nativeSurface: boolean

  build() {
    Grid() {
      ForEach(this.items, (index: number) => {
        GridItem() {
          Column({ space: 8 }) {
            Column()
              .width('100%')
              .aspectRatio(2 / 3)
              .borderRadius(10)
              .backgroundColor(this.nativeSurface ?
                $r('sys.color.comp_background_tertiary') : $r('app.color.bg_2'))
            Column()
              .width('72%')
              .height(13)
              .borderRadius(6.5)
              .backgroundColor(this.nativeSurface ?
                $r('sys.color.comp_background_tertiary') : $r('app.color.bg_2'))
          }
          .width('100%')
          .alignItems(HorizontalAlign.Start)
        }
      }, (index: number) => index.toString())
    }
    .width('100%')
    .columnsTemplate(this.columnsTemplate)
    .columnsGap(10)
    .rowsGap(14)
    .scrollBar(BarState.Off)
    .opacity(0.7)
  }
}

@ComponentV2
struct MineHistoryGrid {
  @Require @Param items: MineHistoryItem[]
  @Require @Param columnsTemplate: string
  @Require @Param nativeSurface: boolean
  @Event onItemPressed: (item: MineHistoryItem) => void = (_item: MineHistoryItem) => {}

  private itemKey(item: MineHistoryItem): string {
    return `${item.id}|${item.latestSeasonNumber || 0}|${item.latestEpisodeNumber || 0}|` +
      `${item.seriesHasMultipleSeasons ? 1 : 0}`
  }

  build() {
    Grid() {
      ForEach(this.items, (item: MineHistoryItem) => {
        GridItem() {
          MineHistoryPoster({
            item,
            nativeSurface: this.nativeSurface
          })
        }
        .onClick(() => this.onItemPressed(item))
      }, (item: MineHistoryItem) => this.itemKey(item))
    }
    .width('100%')
    .columnsTemplate(this.columnsTemplate)
    .columnsGap(10)
    .rowsGap(14)
    .scrollBar(BarState.Off)
  }
}

@ComponentV2
struct MineHistoryErrorState {
  @Require @Param secondaryTextColor: ResourceColor
  @Event onRetry: () => void = () => {}

  build() {
    Row({ space: 8 }) {
      Text($r('app.string.mine_history_retry'))
        .fontSize(14)
        .fontColor(this.secondaryTextColor)
      SymbolGlyph($r('sys.symbol.arrow_clockwise'))
        .fontSize(18)
        .fontColor([this.secondaryTextColor])
    }
    .height(64)
    .onClick(() => this.onRetry())
  }
}

@ComponentV2
export struct MineHistorySection {
  @Require @Param state: MineHistoryState
  @Require @Param items: MineHistoryItem[]
  @Require @Param currentBreakpoint: string
  @Require @Param nativeSurface: boolean
  @Require @Param themeStyle: ThemeStyle
  @Event onItemClick: (item: MineHistoryItem) => void = (_item: MineHistoryItem) => {}
  @Event onRetry: () => void = () => {}
  @Event onMore: () => void = () => {}

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

  private primaryTextColor(): ResourceColor {
    return this.nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1')
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
          MineHistoryLoadingGrid({
            items: this.skeletonItems(),
            columnsTemplate: this.columnsTemplate(),
            nativeSurface: this.nativeSurface
          })
        } else if (this.state === MineHistoryState.Error) {
          MineHistoryErrorState({
            secondaryTextColor: this.secondaryTextColor(),
            onRetry: () => this.onRetry()
          })
        } else {
          MineHistoryGrid({
            items: this.visibleItems(),
            columnsTemplate: this.columnsTemplate(),
            nativeSurface: this.nativeSurface,
            onItemPressed: (item: MineHistoryItem) => this.onItemClick(item)
          })
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
