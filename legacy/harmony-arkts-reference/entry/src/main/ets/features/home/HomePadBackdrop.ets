import { MediaNetworkImage } from '../../component/MediaNetworkImage'
import { VideoItem } from './hometab/HomeUIState'

const PAD_AMBIENT_BLUR_RADIUS: number = 128
const PAD_AMBIENT_SCALE: number = 1.18

@ComponentV2
export struct HomePadBackdrop {
  @Require @Param items: VideoItem[]
  @Param selectedIndex: number = 0
  @Param pagePositions: number[] = []
  @Param mainAxisLength: number = 0
  @Param fallbackPageWidth: number = 0
  @Param leadingOverflow: number = 0
  @Require @Param canvasColor: ResourceColor
  @Param darkMode: boolean = true

  private expandedWidth(): string {
    return this.leadingOverflow > 0 ? `calc(100% + ${this.leadingOverflow}vp)` : '100%'
  }

  private normalizedSelectedIndex(): number {
    if (this.items.length === 0) {
      return 0
    }
    return Math.max(0, Math.min(this.selectedIndex, this.items.length - 1))
  }

  private previousIndex(): number {
    if (this.items.length <= 1) {
      return 0
    }
    return (this.normalizedSelectedIndex() - 1 + this.items.length) % this.items.length
  }

  private nextIndex(): number {
    if (this.items.length <= 1) {
      return 0
    }
    return (this.normalizedSelectedIndex() + 1) % this.items.length
  }

  private pagePosition(index: number): number {
    const reportedPosition = this.pagePositions[index]
    if (Number.isFinite(reportedPosition)) {
      return reportedPosition
    }
    if (index === this.normalizedSelectedIndex()) {
      return 0
    }
    if (index === this.previousIndex()) {
      return -1
    }
    if (index === this.nextIndex()) {
      return 1
    }
    return 2
  }

  private shouldRenderPage(index: number): boolean {
    if (this.items.length <= 3) {
      return true
    }
    const position = this.pagePosition(index)
    return index === this.normalizedSelectedIndex() ||
      index === this.previousIndex() || index === this.nextIndex() || Math.abs(position) <= 1.2
  }

  private pageWidth(): number {
    if (this.mainAxisLength > 0) {
      return this.mainAxisLength
    }
    return Math.max(0, this.fallbackPageWidth)
  }

  private pageOffset(index: number): number {
    return this.pagePosition(index) * this.pageWidth()
  }

  build() {
    Stack() {
      Column()
        .width('100%')
        .height('100%')
        .backgroundColor(this.canvasColor)

      ForEach(this.items, (item: VideoItem, index: number) => {
        if (this.shouldRenderPage(index)) {
          Stack() {
            Stack() {
              MediaNetworkImage({
                imageRef: item.imageRef,
                fallbackSource: item.image
              })
                .width('100%')
                .height('100%')
            }
            .width('100%')
            .height('100%')
            .scale({ x: PAD_AMBIENT_SCALE, y: PAD_AMBIENT_SCALE })
            .blur(PAD_AMBIENT_BLUR_RADIUS)
            .brightness(this.darkMode ? 0.54 : 0.82)
          }
          .width('100%')
          .height('100%')
          .clip(true)
          .translate({ x: this.pageOffset(index) })
          .hitTestBehavior(HitTestMode.None)
        }
      }, (item: VideoItem) => `pad-ambient:${item.id}`)

      Stack()
        .width('100%')
        .height('100%')
        .linearGradient({
          angle: 180,
          colors: this.darkMode ? [
            ['rgba(0,0,0,0.05)', 0.0],
            ['rgba(0,0,0,0.16)', 0.48],
            ['rgba(0,0,0,0.42)', 1.0]
          ] : [
            ['rgba(255,255,255,0.02)', 0.0],
            ['rgba(255,255,255,0.14)', 0.48],
            ['rgba(255,255,255,0.46)', 1.0]
          ]
        })
        .hitTestBehavior(HitTestMode.None)
    }
    .width(this.expandedWidth())
    .height('100%')
    .translate({ x: -this.leadingOverflow })
    .clip(true)
    .hitTestBehavior(HitTestMode.None)
  }
}

@ComponentV2
export struct HomePadFeatheredHeroImage {
  @Require @Param item: VideoItem

  build() {
    Stack() {
      MediaNetworkImage({
        imageRef: this.item.imageRef,
        fallbackSource: this.item.image
      })
        .width('100%')
        .height('100%')

      Column()
        .width('100%')
        .height('100%')
        .linearGradient({
          angle: 180,
          colors: [
            ['rgba(255,255,255,1)', 0.0],
            ['rgba(255,255,255,1)', 0.64],
            ['rgba(255,255,255,0.84)', 0.73],
            ['rgba(255,255,255,0.5)', 0.82],
            ['rgba(255,255,255,0.16)', 0.91],
            ['rgba(255,255,255,0)', 1.0]
          ]
        })
        .blendMode(BlendMode.DST_IN)
        .hitTestBehavior(HitTestMode.None)
    }
    .width('100%')
    .height('100%')
    .blendMode(BlendMode.SRC_OVER, BlendApplyType.OFFSCREEN)
    .hitTestBehavior(HitTestMode.None)
  }
}

@ComponentV2
export struct HomePadHeroLayer {
  @Require @Param items: VideoItem[]
  @Param selectedIndex: number = 0
  @Param pagePositions: number[] = []
  @Param mainAxisLength: number = 0
  @Param fallbackPageWidth: number = 0
  @Require @Param heroHeight: number
  @Param verticalOffset: number = 0

  private normalizedSelectedIndex(): number {
    if (this.items.length === 0) {
      return 0
    }
    return Math.max(0, Math.min(this.selectedIndex, this.items.length - 1))
  }

  private previousIndex(): number {
    if (this.items.length <= 1) {
      return 0
    }
    return (this.normalizedSelectedIndex() - 1 + this.items.length) % this.items.length
  }

  private nextIndex(): number {
    if (this.items.length <= 1) {
      return 0
    }
    return (this.normalizedSelectedIndex() + 1) % this.items.length
  }

  private pagePosition(index: number): number {
    const reportedPosition = this.pagePositions[index]
    if (Number.isFinite(reportedPosition)) {
      return reportedPosition
    }
    if (index === this.normalizedSelectedIndex()) {
      return 0
    }
    if (index === this.previousIndex()) {
      return -1
    }
    if (index === this.nextIndex()) {
      return 1
    }
    return 2
  }

  private shouldRenderPage(index: number): boolean {
    if (this.items.length <= 3) {
      return true
    }
    const position = this.pagePosition(index)
    return index === this.normalizedSelectedIndex() || index === this.previousIndex() ||
      index === this.nextIndex() || Math.abs(position) <= 1.2
  }

  private pageWidth(): number {
    return this.mainAxisLength > 0 ? this.mainAxisLength : Math.max(0, this.fallbackPageWidth)
  }

  build() {
    Stack() {
      ForEach(this.items, (item: VideoItem, index: number) => {
        if (this.shouldRenderPage(index)) {
          HomePadFeatheredHeroImage({
            item: item
          })
            .width('100%')
            .height(this.heroHeight)
            .translate({ x: this.pagePosition(index) * this.pageWidth() })
        }
      }, (item: VideoItem) => `pad-hero-layer:${item.id}`)
    }
    .width('100%')
    .height(this.heroHeight)
    .translate({ y: -Math.max(0, this.verticalOffset) })
    .clip(true)
    .hitTestBehavior(HitTestMode.None)
  }
}
