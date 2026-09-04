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
struct HomeHeroSkeleton {
  @Require @Param currentBreakpoint: string
  @Require @Param heroHeight: number
  @Require @Param sidePadding: number
  @Require @Param animate: AnimateType

  build() {
    Column() {
      if (this.currentBreakpoint.includes('s')) {
        UISkeleton({
          animate: this.animate,
          options: [{
            type: SkeletonType.RECT,
            width: '100%',
            height: '100%'
          }]
        })
          .width('100%')
          .height(this.heroHeight)
      } else {
        UISkeleton({
          animate: this.animate,
          options: [{
            type: SkeletonType.RECT,
            width: '100%',
            height: '100%'
          }]
        })
        .width('100%')
        .height(this.heroHeight)
      }
    }
    .width('100%')
  }
}

@ComponentV2
struct HomeHorizontalSectionSkeleton {
  @Require @Param currentBreakpoint: string
  @Require @Param sidePadding: number
  @Require @Param animate: AnimateType

  build() {
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
        ForEach(HORIZONTAL_PLACEHOLDERS, (item: number) => {
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
        }, (item: number) => `horizontal-skeleton:${item}`)
      }
      .width('100%')
      .clip(true)
    }
    .width('100%')
    .padding({ left: this.sidePadding, right: this.sidePadding })
    .margin({ top: 20 })
    .alignItems(HorizontalAlign.Start)
  }
}

@ComponentV2
struct HomeLibraryHeaderSkeleton {
  @Require @Param sidePadding: number
  @Require @Param animate: AnimateType

  build() {
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
      }, (width: number) => `library-header-skeleton:${width}`)
    }
    .width('100%')
    .height(56)
    .padding({ left: this.sidePadding, right: this.sidePadding })
    .alignItems(VerticalAlign.Center)
    .clip(true)
  }
}

@ComponentV2
struct HomeLatestGridSkeleton {
  @Require @Param currentBreakpoint: string
  @Require @Param columnsTemplate: string
  @Require @Param sidePadding: number
  @Require @Param animate: AnimateType

  build() {
    Grid() {
      ForEach(GRID_PLACEHOLDERS, (item: number) => {
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
      }, (item: number) => `latest-grid-skeleton:${item}`)
    }
    .width('100%')
    .columnsTemplate(this.columnsTemplate)
    .columnsGap(HORIZONTAL_CARD_SPACE.getValue(this.currentBreakpoint))
    .rowsGap(20)
    .padding({ left: this.sidePadding, right: this.sidePadding, top: 16 })
    .scrollBar(BarState.Off)
  }
}

@ComponentV2
export struct HomeInitialSkeleton {
  @Require @Param showHero: boolean
  @Require @Param currentBreakpoint: string
  @Require @Param heroHeight: number
  @Param gridColumnCount: number = 0
  @Local animate: AnimateType = AnimateType.GRADIENT

  private sidePadding(): number {
    return HORIZONTAL_CARD_SPACE.getValue(this.currentBreakpoint) * 2
  }

  private gridColumns(): string {
    if (this.gridColumnCount > 0) {
      return `repeat(${this.gridColumnCount}, 1fr)`
    }
    if (this.currentBreakpoint.includes('s') || this.currentBreakpoint === 'md') {
      return '1fr 1fr 1fr'
    }
    if (this.currentBreakpoint === 'lg') {
      return '1fr 1fr 1fr 1fr'
    }
    return '1fr 1fr 1fr 1fr 1fr'
  }

  build() {
    Column() {
      if (this.showHero) {
        HomeHeroSkeleton({
          currentBreakpoint: this.currentBreakpoint,
          heroHeight: this.heroHeight,
          sidePadding: this.sidePadding(),
          animate: this.animate
        })
      }
      HomeHorizontalSectionSkeleton({
        currentBreakpoint: this.currentBreakpoint,
        sidePadding: this.sidePadding(),
        animate: this.animate
      })
      HomeHorizontalSectionSkeleton({
        currentBreakpoint: this.currentBreakpoint,
        sidePadding: this.sidePadding(),
        animate: this.animate
      })
      HomeLibraryHeaderSkeleton({
        sidePadding: this.sidePadding(),
        animate: this.animate
      })
      HomeLatestGridSkeleton({
        currentBreakpoint: this.currentBreakpoint,
        columnsTemplate: this.gridColumns(),
        sidePadding: this.sidePadding(),
        animate: this.animate
      })
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }
}
