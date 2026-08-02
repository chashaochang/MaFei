import { ImageKnifeComponent, ImageKnifeOption } from '@ohos/imageknifepro'
import { AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver'
import { AppThemeTokens } from '../../../theme/AppThemeTokens'
import { ThemeStyle } from '../../../entity/ThemeStyle'
import { MineHistoryItem } from './MineHistoryPlanner'
import { MineHistoryState } from './MineUIState'

@ComponentV2
struct MineHistoryPoster {
  @Require @Param image: string
  @Require @Param name: string
  @Require @Param percent: number
  @Require @Param played: boolean
  @Require @Param nativeSurface: boolean
  @Local imageOption: ImageKnifeOption = {
    loadSrc: this.image,
    objectFit: ImageFit.Cover,
    border: { radius: 10 },
    placeholderSrc: $r('app.media.placeholder_video'),
    placeholderObjectFit: ImageFit.CENTER
  }

  private primaryTextColor(): ResourceColor {
    return this.nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  build() {
    Column({ space: 8 }) {
      Stack({ alignContent: Alignment.Bottom }) {
        ImageKnifeComponent({ imageKnifeOption: this.imageOption })
          .width('100%')
          .aspectRatio(2 / 3)
          .borderRadius(10)
          .backgroundColor($r('sys.color.comp_background_tertiary'))
        if (!this.played && this.percent > 0) {
          Progress({ value: this.percent, total: 100, type: ProgressType.Linear })
            .width('100%')
            .height(3)
            .color(this.nativeSurface ? $r('sys.color.icon_primary') : $r('app.color.color_main'))
            .backgroundColor($r('sys.color.comp_background_tertiary'))
            .style({
              strokeWidth: 3,
              strokeRadius: 1.5,
              enableSmoothEffect: false,
              enableScanEffect: false
            })
        }
      }
      .width('100%')
      .aspectRatio(2 / 3)
      .borderRadius(10)
      .clip(true)
      Text(this.name)
        .fontSize(13)
        .fontColor(this.primaryTextColor())
        .width('100%')
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }
}

@ComponentV2
export struct MineHistorySection {
  @Require @Param state: MineHistoryState
  @Require @Param items: MineHistoryItem[]
  @Require @Param currentBreakpoint: string
  @Require @Param nativeSurface: boolean
  @Require @Param themeStyle: ThemeStyle
  @Require @Param onItemClick: (item: MineHistoryItem) => void
  @Require @Param onRetry: () => void

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

  @Builder
  private loadingGrid() {
    Grid() {
      ForEach(this.skeletonItems(), (_index: number) => {
        GridItem() {
          Column({ space: 8 }) {
            Column()
              .width('100%')
              .aspectRatio(2 / 3)
              .borderRadius(10)
              .backgroundColor($r('sys.color.comp_background_tertiary'))
            Column()
              .width('72%')
              .height(13)
              .borderRadius(6.5)
              .backgroundColor($r('sys.color.comp_background_tertiary'))
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
            image: item.image,
            name: item.name,
            percent: item.percent,
            played: item.played,
            nativeSurface: this.nativeSurface
          })
        }
        .onClick(() => {
          this.onItemClick(item)
        })
      }, (item: MineHistoryItem) => item.id)
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
        Text($r('app.string.mine_recent_watching'))
          .fontSize(20)
          .fontWeight(FontWeight.Medium)
          .fontColor(this.primaryTextColor())
          .width('100%')
        if (this.state === MineHistoryState.Loading) {
          this.loadingGrid()
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
