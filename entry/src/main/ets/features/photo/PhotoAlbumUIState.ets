import { BaseUIState } from '../../common/base/BaseUIState'
import {
  PhotoAlbumItem,
  PhotoBrowseErrorCode,
  PhotoDateGroup,
  PhotoItem
} from './PhotoModels'

export enum PhotoAlbumContentState {
  Loading,
  Content,
  Empty,
  Error
}

@ObservedV2
export class PhotoAlbumUIState extends BaseUIState {
  @Trace state: PhotoAlbumContentState = PhotoAlbumContentState.Loading
  @Trace albumId: string = ''
  @Trace albumName: string = ''
  @Trace album: PhotoAlbumItem | null = null
  @Trace photos: PhotoItem[] = []
  @Trace photoGroups: PhotoDateGroup[] = []
  @Trace refreshing: boolean = false
  @Trace loadingMore: boolean = false
  @Trace loadMoreFailed: boolean = false
  @Trace hasMore: boolean = true
  @Trace nextIndex: number = 0
  @Trace errorCode: PhotoBrowseErrorCode = PhotoBrowseErrorCode.None
  @Trace favoritePendingIds: string[] = []
}
