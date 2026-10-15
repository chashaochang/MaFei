import { ImageKnifeComponent } from '@ohos/imageknifepro'
import { BreakpointTypeEnum } from '../../utils/BreakpointSystem'
import {
  PhotoDateGroup,
  PhotoItem
} from './PhotoModels'
import { PhotoGridFooter } from './PhotoGridFooter'

@ComponentV2
export struct PhotoGrid {
  @Require @Param groups: PhotoDateGroup[]
  @Require @Param currentBreakpoint: string
  @Require @Param nativeSurface: boolean
  @Require @Param scroller: Scroller
  @Require @Param loadingMore: boolean
  @Require @Param loadMoreFailed: boolean
  @Require @Param onPhotoClick: (photo: PhotoItem, index: number) => void
  @Require @Param onReachEnd: () => void
  @Require @Param onRetryLoadMore: () => void

  private columnCount(): number {
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
      case BreakpointTypeEnum.XS:
      default:
        return 3
    }
  }

  private secondaryTextColor(): ResourceColor {
    return this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  private placeholderColor(): ResourceColor {
    return this.nativeSurface ? $r('sys.color.comp_background_tertiary') : $r('app.color.bg_2')
  }

  private dateLabel(key: string): string {
    if (key.length !== 10) {
      return ''
    }
    return `${key.slice(0, 4)} / ${key.slice(5, 7)} / ${key.slice(8, 10)}`
  }

  private photoIndex(photoId: string): number {
    let index = 0
    for (const group of this.groups) {
      for (const photo of group.items) {
        if (photo.id === photoId) {
          return index
        }
        index++
      }
    }
    return 0
  }

  build() {
    Grid(this.scroller) {
      ForEach(this.groups, (group: PhotoDateGroup) => {
        GridItem() {
          Text(group.key.length === 10 ? this.dateLabel(group.key) : $r('app.string.photo_unknown_date'))
            .width('100%')
            .fontSize(15)
            .fontWeight(FontWeight.Medium)
            .fontColor(this.secondaryTextColor())
            .padding({ top: 8, bottom: 6 })
        }
        .columnStart(0)
        .columnEnd(this.columnCount() - 1)

        ForEach(group.items, (photo: PhotoItem) => {
          GridItem() {
            ImageKnifeComponent({
              imageKnifeOption: {
                loadSrc: photo.thumbnailUrl,
                objectFit: ImageFit.Cover,
                border: { radius: 4 },
                placeholderSrc: $r('app.media.placeholder_folder'),
                placeholderObjectFit: ImageFit.CENTER
              }
            })
              .width('100%')
              .aspectRatio(1)
              .borderRadius(4)
              .backgroundColor(this.placeholderColor())
          }
          .onClick(() => this.onPhotoClick(photo, this.photoIndex(photo.id)))
        }, (photo: PhotoItem) => photo.id)
      }, (group: PhotoDateGroup) => group.key)

      if (this.loadingMore || this.loadMoreFailed) {
        GridItem() {
          PhotoGridFooter({
            loadingMore: this.loadingMore,
            loadMoreFailed: this.loadMoreFailed,
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
    .columnsTemplate('1fr '.repeat(this.columnCount()))
    .columnsGap(4)
    .rowsGap(4)
    .cachedCount(this.columnCount() * 4)
    .scrollBar(BarState.Off)
    .onReachEnd(() => this.onReachEnd())
  }
}
