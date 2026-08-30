import { ImageKnifeComponent } from '@ohos/imageknifepro'
import { BreakpointTypeEnum } from '../../utils/BreakpointSystem'
import { musicBrowseErrorText } from './MusicErrorText'
import { MusicAlbum, MusicBrowseErrorCode } from './MusicModels'
import { MusicGridFooter } from './MusicGridFooter'

@ComponentV2
export struct MusicAlbumGrid {
  @Require @Param albums: MusicAlbum[]
  @Require @Param currentBreakpoint: string
  @Require @Param nativeSurface: boolean
  @Require @Param scroller: Scroller
  @Require @Param loadingMore: boolean
  @Require @Param loadMoreFailed: boolean
  @Param loadMoreErrorCode: MusicBrowseErrorCode = MusicBrowseErrorCode.RequestFailed
  @Require @Param onAlbumClick: (album: MusicAlbum) => void
  @Require @Param onReachEnd: () => void
  @Require @Param onRetryLoadMore: () => void
  @Event onContentScrollStop: () => void = () => {}

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

  private columnsTemplate(): string {
    return '1fr '.repeat(this.columnCount())
  }

  private primaryTextColor(): ResourceColor {
    return this.nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  build() {
    Grid(this.scroller) {
      ForEach(this.albums, (album: MusicAlbum) => {
        GridItem() {
          Column({ space: 7 }) {
            ImageKnifeComponent({
              imageKnifeOption: {
                loadSrc: album.imageUrl,
                objectFit: ImageFit.Cover,
                border: { radius: 8 },
                placeholderSrc: $r('app.media.placeholder_folder'),
                placeholderObjectFit: ImageFit.CENTER
              }
            })
              .width('100%')
              .aspectRatio(1)
              .borderRadius(8)
              .backgroundColor(this.nativeSurface ?
                $r('sys.color.comp_background_tertiary') : $r('app.color.bg_2'))
            Text(album.title)
              .width('100%')
              .fontSize(13)
              .lineHeight(17)
              .fontWeight(FontWeight.Medium)
              .fontColor(this.primaryTextColor())
              .maxLines(1)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
            if (album.artist.length > 0) {
              Text(album.artist)
                .width('100%')
                .fontSize(11)
                .lineHeight(15)
                .fontColor(this.secondaryTextColor())
                .maxLines(1)
                .textOverflow({ overflow: TextOverflow.Ellipsis })
            }
          }
          .width('100%')
          .alignItems(HorizontalAlign.Start)
        }
        .onClick(() => this.onAlbumClick(album))
      }, (album: MusicAlbum) => album.id)
      if (this.loadingMore || this.loadMoreFailed) {
        GridItem() {
          MusicGridFooter({
            loadingMore: this.loadingMore,
            loadMoreFailed: this.loadMoreFailed,
            errorText: musicBrowseErrorText(this.loadMoreErrorCode),
            textColor: this.secondaryTextColor(),
            progressSize: 20,
            showLoadingText: true,
            onRetry: () => this.onRetryLoadMore()
          })
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
    .columnsTemplate(this.columnsTemplate())
    .columnsGap(12)
    .rowsGap(18)
    .scrollBar(BarState.Off)
    .onReachEnd(() => this.onReachEnd())
    .onScrollStop(() => this.onContentScrollStop())
  }
}
