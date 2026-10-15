import { BaseUIState } from '../../common/base/BaseUIState'
import {
  PhotoBrowseErrorCode,
  PhotoItem
} from './PhotoModels'

@ObservedV2
export class PhotoViewerUIState extends BaseUIState {
  @Trace sourceKey: string = ''
  @Trace sourceScope: string = ''
  @Trace sourceTitle: string = ''
  @Trace photos: PhotoItem[] = []
  @Trace currentIndex: number = -1

  @Trace controlsVisible: boolean = true
  @Trace zoomScale: number = 1
  @Trace zoomOffsetX: number = 0
  @Trace zoomOffsetY: number = 0
  @Trace zoomRevision: number = 0

  @Trace slideshowActive: boolean = false

  @Trace infoVisible: boolean = false
  @Trace infoLoading: boolean = false
  @Trace infoPhoto: PhotoItem | null = null
  @Trace infoErrorCode: PhotoBrowseErrorCode = PhotoBrowseErrorCode.None

  @Trace favoritePendingPhotoId: string = ''
  @Trace favoriteFailedPhotoId: string = ''
  @Trace favoriteErrorCode: PhotoBrowseErrorCode = PhotoBrowseErrorCode.None

  @Trace loadErrorCode: PhotoBrowseErrorCode = PhotoBrowseErrorCode.None
}
