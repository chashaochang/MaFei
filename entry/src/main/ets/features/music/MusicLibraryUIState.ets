import { BaseUIState } from '../../common/base/BaseUIState'
import {
  MusicAlbum,
  MusicArtist,
  MusicBrowseErrorCode,
  MusicLibrarySection,
  MusicTrack
} from './MusicModels'

export enum MusicContentState {
  Loading,
  Content,
  Empty,
  Error
}

@ObservedV2
export class MusicLibraryUIState extends BaseUIState {
  @Trace libraryId: string = ''
  @Trace libraryName: string = ''
  @Trace selectedSection: MusicLibrarySection = MusicLibrarySection.Albums

  @Trace albumsState: MusicContentState = MusicContentState.Loading
  @Trace albums: MusicAlbum[] = []
  @Trace albumsRefreshing: boolean = false
  @Trace albumsLoadingMore: boolean = false
  @Trace albumsLoadMoreFailed: boolean = false
  @Trace albumsHasMore: boolean = true
  @Trace albumsNextStartIndex: number = 0
  @Trace albumsErrorCode: MusicBrowseErrorCode = MusicBrowseErrorCode.None
  @Trace albumsScrollOffset: number = 0

  @Trace artistsState: MusicContentState = MusicContentState.Loading
  @Trace artists: MusicArtist[] = []
  @Trace artistsRefreshing: boolean = false
  @Trace artistsLoadingMore: boolean = false
  @Trace artistsLoadMoreFailed: boolean = false
  @Trace artistsHasMore: boolean = true
  @Trace artistsNextStartIndex: number = 0
  @Trace artistsErrorCode: MusicBrowseErrorCode = MusicBrowseErrorCode.None
  @Trace artistsScrollOffset: number = 0

  @Trace tracksState: MusicContentState = MusicContentState.Loading
  @Trace tracks: MusicTrack[] = []
  @Trace tracksRefreshing: boolean = false
  @Trace tracksLoadingMore: boolean = false
  @Trace tracksLoadMoreFailed: boolean = false
  @Trace tracksHasMore: boolean = true
  @Trace tracksNextStartIndex: number = 0
  @Trace tracksErrorCode: MusicBrowseErrorCode = MusicBrowseErrorCode.None
  @Trace tracksScrollOffset: number = 0
}
