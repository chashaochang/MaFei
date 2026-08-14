import { BaseUIState } from '../../common/base/BaseUIState'
import {
  PhotoAlbumItem,
  PhotoBrowseErrorCode,
  PhotoDateGroup,
  PhotoItem,
  PhotoLibrarySection
} from './PhotoModels'

export enum PhotoContentState {
  Loading,
  Content,
  Empty,
  Error
}

@ObservedV2
export class PhotoLibraryUIState extends BaseUIState {
  @Trace libraryId: string = ''
  @Trace libraryName: string = ''
  @Trace selectedSection: PhotoLibrarySection = PhotoLibrarySection.Photos

  @Trace photosState: PhotoContentState = PhotoContentState.Loading
  @Trace photos: PhotoItem[] = []
  @Trace photoGroups: PhotoDateGroup[] = []
  @Trace photosRefreshing: boolean = false
  @Trace photosLoadingMore: boolean = false
  @Trace photosLoadMoreFailed: boolean = false
  @Trace photosHasMore: boolean = true
  @Trace photosNextIndex: number = 0
  @Trace photosErrorCode: PhotoBrowseErrorCode = PhotoBrowseErrorCode.None

  @Trace albumsState: PhotoContentState = PhotoContentState.Loading
  @Trace albums: PhotoAlbumItem[] = []
  @Trace albumsRefreshing: boolean = false
  @Trace albumsLoadingMore: boolean = false
  @Trace albumsLoadMoreFailed: boolean = false
  @Trace albumsHasMore: boolean = true
  @Trace albumsNextIndex: number = 0
  @Trace albumsErrorCode: PhotoBrowseErrorCode = PhotoBrowseErrorCode.None

  @Trace favoritePendingIds: string[] = []
}
