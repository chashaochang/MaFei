import { ImageKnifeComponent } from '@ohos/imageknifepro'
import { BreakpointTypeEnum } from '../../utils/BreakpointSystem'
import { PhotoAlbumItem } from './PhotoModels'

@ComponentV2
export struct PhotoAlbumGrid {
  @Require @Param albums: PhotoAlbumItem[]
  @Require @Param currentBreakpoint: string
  @Require @Param nativeSurface: boolean
  @Require @Param scroller: Scroller
  @Require @Param loadingMore: boolean
  @Require @Param loadMoreFailed: boolean
  @Require @Param onAlbumClick: (album: PhotoAlbumItem) => void
  @Require @Param onReachEnd: () => void
  @Require @Param onRetryLoadMore: () => void

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

  private textColor(secondary: boolean = false): ResourceColor {
    if (this.nativeSurface) {
      return secondary ? $r('sys.color.font_secondary') : $r('sys.color.font_primary')
    }
    return secondary ? $r('app.color.text_1') : $r('app.color.text_primary')
  }

  private placeholderColor(): ResourceColor {
    return this.nativeSurface ? $r('sys.color.comp_background_tertiary') : $r('app.color.bg_2')
  }

  @Builder
  private footer() {
    if (this.loadingMore) {
      LoadingProgress().width(22).height(22)
    } else if (this.loadMoreFailed) {
      Button($r('app.string.photo_load_more_failed'), { type: ButtonType.Normal })
        .height(44)
        .borderRadius(22)
        .backgroundColor(Color.Transparent)
        .fontColor(this.textColor(true))
        .onClick(() => this.onRetryLoadMore())
    }
  }

  build() {
    Grid(this.scroller) {
      ForEach(this.albums, (album: PhotoAlbumItem) => {
        GridItem() {
          Column({ space: 8 }) {
            ImageKnifeComponent({
              imageKnifeOption: {
                loadSrc: album.thumbnailUrl,
                objectFit: ImageFit.Cover,
                border: { radius: 8 },
                placeholderSrc: $r('app.media.placeholder_folder'),
                placeholderObjectFit: ImageFit.CENTER
              }
            })
              .width('100%')
              .aspectRatio(1)
              .borderRadius(8)
              .backgroundColor(this.placeholderColor())
            Text(album.name)
              .width('100%')
              .fontSize(14)
              .fontWeight(FontWeight.Medium)
              .fontColor(this.textColor())
              .maxLines(1)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
          }
          .width('100%')
          .alignItems(HorizontalAlign.Start)
        }
        .onClick(() => this.onAlbumClick(album))
      }, (album: PhotoAlbumItem) => album.id)

      if (this.loadingMore || this.loadMoreFailed) {
        GridItem() {
          this.footer()
        }
        .columnStart(0)
        .columnEnd(this.columnCount() - 1)
      }

      GridItem() {
        Row().height(20)
      }
      .columnStart(0)
      .columnEnd(this.columnCount() - 1)
    }
    .width('100%')
    .height('100%')
    .columnsTemplate('1fr '.repeat(this.columnCount()))
    .columnsGap(12)
    .rowsGap(18)
    .cachedCount(this.columnCount() * 3)
    .scrollBar(BarState.Off)
    .onReachEnd(() => this.onReachEnd())
  }
}
