import { BreakpointTypeEnum } from '../../utils/BreakpointSystem'
import { MusicLibrarySection } from './MusicModels'

const MUSIC_SKELETON_ROWS: number[] = [0, 1, 2, 3, 4, 5, 6, 7]

@ComponentV2
export struct MusicLibrarySkeleton {
  @Require @Param section: MusicLibrarySection
  @Require @Param currentBreakpoint: string
  @Require @Param nativeSurface: boolean

  private columnCount(): number {
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
      case BreakpointTypeEnum.XS:
      default:
        return 2
    }
  }

  private blockColor(): ResourceColor {
    return this.nativeSurface ? $r('sys.color.comp_background_tertiary') : $r('app.color.bg_2')
  }

  @Builder
  private gridSkeleton(artistMode: boolean) {
    Grid() {
      ForEach(Array.from({ length: this.columnCount() * 4 }, (_value: Object, index: number) => index),
        (index: number) => {
          GridItem() {
            Column({ space: artistMode ? 8 : 7 }) {
              Row()
                .width('100%')
                .aspectRatio(1)
                .borderRadius(8)
                .backgroundColor(this.blockColor())
                .opacity(0.72)
              Row()
                .width(index % 3 === 0 ? '76%' : '60%')
                .height(13)
                .borderRadius(7)
                .backgroundColor(this.blockColor())
                .opacity(0.72)
              if (!artistMode) {
                Row()
                  .width(index % 2 === 0 ? '54%' : '42%')
                  .height(11)
                  .borderRadius(6)
                  .backgroundColor(this.blockColor())
                  .opacity(0.58)
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
    .columnsGap(12)
    .rowsGap(18)
    .scrollBar(BarState.Off)
    .enabled(false)
  }

  @Builder
  private trackSkeleton() {
    Column() {
      ForEach(MUSIC_SKELETON_ROWS, (index: number) => {
        Row({ space: 12 }) {
          Row()
            .width(26)
            .height(12)
            .borderRadius(6)
            .backgroundColor(this.blockColor())
            .opacity(0.58)
          Column({ space: 6 }) {
            Row()
              .width(index % 3 === 0 ? '78%' : '62%')
              .height(14)
              .borderRadius(7)
              .backgroundColor(this.blockColor())
              .opacity(0.72)
            Row()
              .width(index % 2 === 0 ? '56%' : '44%')
              .height(11)
              .borderRadius(6)
              .backgroundColor(this.blockColor())
              .opacity(0.58)
          }
          .layoutWeight(1)
          .alignItems(HorizontalAlign.Start)
          Row()
            .width(34)
            .height(11)
            .borderRadius(6)
            .backgroundColor(this.blockColor())
            .opacity(0.58)
        }
        .width('100%')
        .height(64)
        .padding({ left: 2, right: 2 })
        .alignItems(VerticalAlign.Center)
        if (index < MUSIC_SKELETON_ROWS.length - 1) {
          Divider()
            .strokeWidth(0.5)
            .color(this.nativeSurface ? $r('sys.color.comp_divider') : $r('app.color.border'))
            .margin({ left: 38 })
        }
      }, (index: number) => index.toString())
    }
    .width('100%')
    .height('100%')
    .enabled(false)
  }

  build() {
    if (this.section === MusicLibrarySection.Tracks) {
      this.trackSkeleton()
    } else {
      this.gridSkeleton(this.section === MusicLibrarySection.Artists)
    }
  }
}
