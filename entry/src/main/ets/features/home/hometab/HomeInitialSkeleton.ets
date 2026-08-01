import { AnimateType, SkeletonType, UISkeleton } from '@hw-agconnect/ui-skeleton'
import {
  HORIZONTAL_CARD_HEIGHT,
  HORIZONTAL_CARD_SPACE,
  HORIZONTAL_CARD_WIDTH,
  VERTICAL_CARD_HEIGHT
} from '../../../common/OneMoreConst'

const HORIZONTAL_PLACEHOLDERS: number[] = [0, 1, 2]
const GRID_PLACEHOLDERS: number[] = [0, 1, 2, 3, 4, 5]

@ComponentV2
export struct HomeInitialSkeleton {
  @Require @Param showHero: boolean
  @Require @Param currentBreakpoint: string
  @Local animate: AnimateType = AnimateType.GRADIENT

  private sidePadding(): number {
    return HORIZONTAL_CARD_SPACE.getValue(this.currentBreakpoint) * 2
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

  @Builder
  private heroSkeleton() {
    UISkeleton({
      animate: this.animate,
      options: [{
        type: SkeletonType.RECT,
        width: '100%',
        height: '100%'
      }]
    })
      .width('100%')
      .height(430)
  }

  @Builder
  private horizontalSectionSkeleton() {
    Column({ space: 12 }) {
      UISkeleton({
        animate: this.animate,
        options: [{
          type: SkeletonType.RECT,
          width: '100%',
          height: '100%',
          radius: 10
        }]
      })
        .width(108)
        .height(24)

      Row({ space: HORIZONTAL_CARD_SPACE.getValue(this.currentBreakpoint) }) {
        ForEach(HORIZONTAL_PLACEHOLDERS, () => {
          Column({ space: 8 }) {
            UISkeleton({
              animate: this.animate,
              options: [{
                type: SkeletonType.RECT,
                width: '100%',
                height: '100%',
                radius: 10
              }]
            })
              .width(HORIZONTAL_CARD_WIDTH.getValue(this.currentBreakpoint))
              .height(HORIZONTAL_CARD_HEIGHT.getValue(this.currentBreakpoint))

            UISkeleton({
              animate: this.animate,
              options: [{
                type: SkeletonType.RECT,
                width: '100%',
                height: '100%',
                radius: 7
              }]
            })
              .width(HORIZONTAL_CARD_WIDTH.getValue(this.currentBreakpoint))
              .height(14)

            UISkeleton({
              animate: this.animate,
              options: [{
                type: SkeletonType.RECT,
                width: '70%',
                height: '100%',
                radius: 6
              }]
            })
              .width(HORIZONTAL_CARD_WIDTH.getValue(this.currentBreakpoint))
              .height(12)
          }
          .width(HORIZONTAL_CARD_WIDTH.getValue(this.currentBreakpoint))
          .alignItems(HorizontalAlign.Start)
        })
      }
      .width('100%')
      .clip(true)
    }
    .width('100%')
    .padding({ left: this.sidePadding(), right: this.sidePadding() })
    .margin({ top: 20 })
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  private libraryHeaderSkeleton() {
    Row({ space: 8 }) {
      ForEach([84, 72, 96], (width: number) => {
        UISkeleton({
          animate: this.animate,
          options: [{
            type: SkeletonType.RECT,
            width: '100%',
            height: '100%',
            radius: 18
          }]
        })
          .width(width)
          .height(36)
      })
    }
    .width('100%')
    .height(56)
    .padding({ left: this.sidePadding(), right: this.sidePadding() })
    .alignItems(VerticalAlign.Center)
    .clip(true)
  }

  @Builder
  private latestGridSkeleton() {
    Grid() {
      ForEach(GRID_PLACEHOLDERS, () => {
        GridItem() {
          Column({ space: 8 }) {
            UISkeleton({
              animate: this.animate,
              options: [{
                type: SkeletonType.RECT,
                width: '100%',
                height: '100%',
                radius: 10
              }]
            })
              .width('100%')
              .height(VERTICAL_CARD_HEIGHT.getValue(this.currentBreakpoint))

            UISkeleton({
              animate: this.animate,
              options: [{
                type: SkeletonType.RECT,
                width: '100%',
                height: '100%',
                radius: 7
              }]
            })
              .width('100%')
              .height(14)

            UISkeleton({
              animate: this.animate,
              options: [{
                type: SkeletonType.RECT,
                width: '70%',
                height: '100%',
                radius: 6
              }]
            })
              .width('100%')
              .height(12)
          }
          .width('100%')
          .alignItems(HorizontalAlign.Start)
        }
      })
    }
    .width('100%')
    .columnsTemplate(this.gridColumns())
    .columnsGap(HORIZONTAL_CARD_SPACE.getValue(this.currentBreakpoint))
    .rowsGap(20)
    .padding({ left: this.sidePadding(), right: this.sidePadding(), top: 16 })
    .scrollBar(BarState.Off)
  }

  build() {
    Column() {
      if (this.showHero) {
        this.heroSkeleton()
      }
      this.horizontalSectionSkeleton()
      this.horizontalSectionSkeleton()
      this.libraryHeaderSkeleton()
      this.latestGridSkeleton()
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }
}
