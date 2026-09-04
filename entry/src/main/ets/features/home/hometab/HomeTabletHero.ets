import { VideoItem } from './HomeUIState'

@ComponentV2
export struct HomeTabletHero {
  @Require @Param items: VideoItem[]
  @Require @Param heroHeight: number
  @Param active: boolean = true
  @Param leadingOverflow: number = 0
  @Param autoplayInterval: number = 6500
  @Param transitionDuration: number = 450
  @Local activeIndex: number = 0
  @Event onOpenDetails: (item: VideoItem) => void = (_item: VideoItem): void => {}
  @Event onIndexChange: (index: number) => void = (_index: number): void => {}
  @Event onContentScroll: (selectedIndex: number, index: number, position: number,
    mainAxisLength: number) => void = (_selectedIndex: number, _index: number, _position: number,
      _mainAxisLength: number): void => {}

  aboutToAppear(): void {
    this.publishActiveIndex()
  }

  @Monitor('items')
  onItemsChanged(): void {
    this.activeIndex = this.items.length > 0 ? Math.min(this.activeIndex, this.items.length - 1) : 0
    this.publishActiveIndex()
  }

  private publishActiveIndex(): void {
    if (this.items.length > 0) {
      this.onIndexChange(this.activeIndex)
    }
  }

  private expandedWidth(): string {
    return this.leadingOverflow > 0 ? `calc(100% + ${this.leadingOverflow}vp)` : '100%'
  }

  private indicatorIndexes(): number[] {
    const visibleCount = Math.min(7, this.items.length)
    const maxStart = Math.max(0, this.items.length - visibleCount)
    const start = Math.max(0, Math.min(this.activeIndex - Math.floor(visibleCount / 2), maxStart))
    const indexes: number[] = []
    for (let offset = 0; offset < visibleCount; offset++) {
      indexes.push(start + offset)
    }
    return indexes
  }

  private contentAreaWidth(): string {
    return this.leadingOverflow > 0 ? `calc(100% - ${this.leadingOverflow}vp)` : '100%'
  }

  private textScrimHeight(): number {
    return Math.min(this.heroHeight, 240)
  }

  build() {
    Stack({ alignContent: Alignment.Bottom }) {
      Swiper() {
        ForEach(this.items, (item: VideoItem, index: number) => {
          Stack({ alignContent: Alignment.BottomStart }) {
            Stack()
              .width('72%')
              .height(this.textScrimHeight())
              .linearGradient({
                angle: 180,
                colors: [
                  ['rgba(0,0,0,0)', 0.0],
                  ['rgba(0,0,0,0.04)', 0.46],
                  ['rgba(0,0,0,0.24)', 0.82],
                  ['rgba(0,0,0,0)', 1.0]
                ]
              })
              .position({ x: 0, y: Math.max(0, this.heroHeight - this.textScrimHeight()) })
              .hitTestBehavior(HitTestMode.None)

            Column({ space: 8 }) {
              Text(item.name)
                .fontSize(30)
                .fontWeight(FontWeight.Bold)
                .fontColor(Color.White)
                .maxLines(2)
                .textOverflow({ overflow: TextOverflow.Ellipsis })

              if (item.subtitle && item.subtitle.length > 0) {
                Text(item.subtitle)
                  .fontSize(14)
                  .fontColor('rgba(255,255,255,0.84)')
                  .maxLines(1)
                  .textOverflow({ overflow: TextOverflow.Ellipsis })
              }

              if (item.description && item.description.length > 0) {
                Text(item.description)
                  .fontSize(14)
                  .fontColor('rgba(255,255,255,0.76)')
                  .lineHeight(21)
                  .maxLines(2)
                  .textOverflow({ overflow: TextOverflow.Ellipsis })
              }
            }
            .width('68%')
            .padding({ left: this.leadingOverflow + 28, right: 24, bottom: 48 })
            .alignItems(HorizontalAlign.Start)
          }
          .width('100%')
          .height('100%')
          .backgroundColor(Color.Transparent)
          .onClick((): void => {
            this.onOpenDetails(item)
          })
        }, (item: VideoItem) => `tablet-hero:${item.id}`)
      }
      .width('100%')
      .height('100%')
      .autoPlay(this.active && this.items.length > 1)
      .interval(this.autoplayInterval)
      .duration(this.transitionDuration)
      .loop(this.items.length > 1)
      .indicator(false)
      .onChange((index: number): void => {
        this.activeIndex = index
        this.onIndexChange(index)
      })
      .onAnimationEnd((index: number): void => {
        this.activeIndex = index
        this.onIndexChange(index)
      })
      .onContentDidScroll((selectedIndex: number, index: number, position: number,
        mainAxisLength: number): void => {
        this.onContentScroll(selectedIndex, index, position, mainAxisLength)
      })

      if (this.items.length > 1) {
        Row({ space: 8 }) {
          ForEach(this.indicatorIndexes(), (index: number) => {
            Circle()
              .width(index === this.activeIndex ? 7 : 6)
              .height(index === this.activeIndex ? 7 : 6)
              .fill(index === this.activeIndex ? Color.White : 'rgba(255,255,255,0.42)')
          }, (index: number) => `tablet-hero-indicator:${index}`)
        }
        .width(this.contentAreaWidth())
        .height(24)
        .justifyContent(FlexAlign.Center)
        .position({ x: this.leadingOverflow, y: Math.max(0, this.heroHeight - 28) })
        .hitTestBehavior(HitTestMode.None)
      }
    }
    .width(this.expandedWidth())
    .height(this.heroHeight)
    .translate({ x: -this.leadingOverflow })
    .clip(true)
  }
}
