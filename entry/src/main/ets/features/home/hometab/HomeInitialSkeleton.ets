import { AnimateType, SkeletonType, UISkeleton } from '@hw-agconnect/ui-skeleton'
import {
  HORIZONTAL_CARD_HEIGHT,
  HORIZONTAL_CARD_SPACE,
  HORIZONTAL_CARD_WIDTH,
  VERTICAL_CARD_HEIGHT
} from '../../../common/OneMoreConst'

const HORIZONTAL_PLACEHOLDERS: number[] = [0, 1, 2]
const GRID_PLACEHOLDERS: number[] = [0, 1, 2, 3, 4, 5]
const TABLET_HERO_PLACEHOLDERS: number[] = [0, 1]
const TABLET_HERO_VERTICAL_INSET: number = 12
const TABLET_HERO_TITLE_HEIGHT: number = 28
const TABLET_HERO_TITLE_GAP: number = 12

@ComponentV2
export struct HomeInitialSkeleton {
  @Require @Param showHero: boolean
  @Require @Param currentBreakpoint: string
  @Require @Param heroHeight: number
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

  private tabletHeroPlaceholderCount(): number {
    return this.currentBreakpoint === 'md' ? 1 : 2
  }

  @Builder
  private heroSkeleton() {
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
      Row() {
        Blank().width(this.sidePadding())

        Column({ space: TABLET_HERO_TITLE_GAP }) {
          UISkeleton({
            animate: this.animate,
            options: [{
              type: SkeletonType.RECT,
              width: '100%',
              height: '100%',
              radius: 7
            }]
          })
            .width(72)
            .height(TABLET_HERO_TITLE_HEIGHT)

          Row({ space: HORIZONTAL_CARD_SPACE.getValue(this.currentBreakpoint) }) {
            ForEach(TABLET_HERO_PLACEHOLDERS.slice(0, this.tabletHeroPlaceholderCount()), () => {
              UISkeleton({
                animate: this.animate,
                options: [{
                  type: SkeletonType.RECT,
                  width: '100%',
                  height: '100%',
                  radius: 8
                }]
              })
                .layoutWeight(1)
                .height('100%')
            }, (item: number) => `tablet-hero-skeleton:${item}`)
          }
          .width('100%')
          .layoutWeight(1)
        }
        .layoutWeight(1)
        .height('100%')
        .padding({
          top: TABLET_HERO_VERTICAL_INSET,
          bottom: TABLET_HERO_VERTICAL_INSET
        })
        .alignItems(HorizontalAlign.Start)

        Blank().width(this.sidePadding())
      }
      .width('100%')
      .height(this.heroHeight)
    }
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
