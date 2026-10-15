import { BreakpointTypeEnum } from '../../utils/BreakpointSystem'

@ComponentV2
export struct PhotoSkeletonGrid {
  @Require @Param currentBreakpoint: string
  @Require @Param nativeSurface: boolean
  @Param albumMode: boolean = false

  private columnCount(): number {
    if (this.albumMode) {
      switch (this.currentBreakpoint) {
        case BreakpointTypeEnum.SM:
          return 3
        case BreakpointTypeEnum.MD:
          return 4
        case BreakpointTypeEnum.LG:
          return 5
        case BreakpointTypeEnum.XL:
          return 6
        case BreakpointTypeEnum.XXL:
          return 8
        default:
          return 2
      }
    }
    switch (this.currentBreakpoint) {
      case BreakpointTypeEnum.SM:
        return 4
      case BreakpointTypeEnum.MD:
        return 5
      case BreakpointTypeEnum.LG:
        return 6
      case BreakpointTypeEnum.XL:
        return 8
      case BreakpointTypeEnum.XXL:
        return 10
      default:
        return 3
    }
  }

  private blockColor(): ResourceColor {
    return this.nativeSurface ? $r('sys.color.comp_background_tertiary') : $r('app.color.bg_2')
  }

  build() {
    Grid() {
      ForEach(Array.from({ length: this.columnCount() * 5 }, (_value: Object, index: number) => index),
        (index: number) => {
          GridItem() {
            Column({ space: 8 }) {
              Row()
                .width('100%')
                .aspectRatio(1)
                .borderRadius(this.albumMode ? 8 : 4)
                .backgroundColor(this.blockColor())
                .opacity(0.72)
              if (this.albumMode) {
                Row()
                  .width(index % 3 === 0 ? '76%' : '58%')
                  .height(12)
                  .borderRadius(6)
                  .backgroundColor(this.blockColor())
                  .opacity(0.72)
              }
            }
            .width('100%')
            .alignItems(HorizontalAlign.Start)
          }
        }, (index: number) => index.toString())
    }
    .width('100%')
    .height('100%')
    .columnsTemplate('1fr '.repeat(this.columnCount()))
    .columnsGap(this.albumMode ? 12 : 4)
    .rowsGap(this.albumMode ? 18 : 4)
    .scrollBar(BarState.Off)
    .enabled(false)
  }
}
