import { ImageKnifeComponent } from '@ohos/imageknifepro'
import { BreakpointTypeEnum } from '../../utils/BreakpointSystem'
import { musicBrowseErrorText } from './MusicErrorText'
import { MusicArtist, MusicBrowseErrorCode } from './MusicModels'

@ComponentV2
export struct MusicArtistGrid {
  @Require @Param artists: MusicArtist[]
  @Require @Param currentBreakpoint: string
  @Require @Param nativeSurface: boolean
  @Require @Param scroller: Scroller
  @Require @Param loadingMore: boolean
  @Require @Param loadMoreFailed: boolean
  @Param loadMoreErrorCode: MusicBrowseErrorCode = MusicBrowseErrorCode.RequestFailed
  @Require @Param onArtistClick: (artist: MusicArtist) => void
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

  private textColor(secondary: boolean = false): ResourceColor {
    if (this.nativeSurface) {
      return secondary ? $r('sys.color.font_secondary') : $r('sys.color.font_primary')
    }
    return secondary ? $r('app.color.text_1') : $r('app.color.text_primary')
  }

  @Builder
  private footer() {
    if (this.loadingMore) {
      LoadingProgress().width(22).height(22)
    } else if (this.loadMoreFailed) {
      Column({ space: 6 }) {
        Text(musicBrowseErrorText(this.loadMoreErrorCode))
          .fontSize(12)
          .fontColor(this.textColor(true))
        Button($r('app.string.music_retry'), { type: ButtonType.Normal })
          .height(40)
          .borderRadius(20)
          .backgroundColor(Color.Transparent)
          .fontColor(this.textColor(true))
          .onClick(() => this.onRetryLoadMore())
      }
      .padding({ top: 8, bottom: 8 })
    }
  }

  build() {
    Grid(this.scroller) {
      ForEach(this.artists, (artist: MusicArtist) => {
        GridItem() {
          Column({ space: 8 }) {
            ImageKnifeComponent({
              imageKnifeOption: {
                loadSrc: artist.imageUrl,
                objectFit: ImageFit.Cover,
                border: { radius: 8 },
                placeholderSrc: $r('app.media.default_avatar'),
                placeholderObjectFit: ImageFit.Cover
              }
            })
              .width('100%')
              .aspectRatio(1)
              .borderRadius(8)
              .backgroundColor(this.nativeSurface ?
                $r('sys.color.comp_background_tertiary') : $r('app.color.bg_2'))
            Text(artist.name)
              .width('100%')
              .fontSize(13)
              .fontWeight(FontWeight.Medium)
              .fontColor(this.textColor())
              .maxLines(1)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
          }
          .width('100%')
          .alignItems(HorizontalAlign.Start)
        }
        .onClick(() => this.onArtistClick(artist))
      }, (artist: MusicArtist) => artist.id)
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
    .scrollBar(BarState.Off)
    .onReachEnd(() => this.onReachEnd())
    .onScrollStop(() => this.onContentScrollStop())
  }
}
