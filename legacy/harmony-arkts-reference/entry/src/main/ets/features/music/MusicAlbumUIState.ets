import { BaseUIState } from '../../common/base/BaseUIState'
import { MusicAlbum, MusicBrowseErrorCode, MusicTrack } from './MusicModels'

export enum MusicDetailState {
  Loading,
  Content,
  Error
}

@ObservedV2
export class MusicAlbumUIState extends BaseUIState {
  @Trace state: MusicDetailState = MusicDetailState.Loading
  @Trace album: MusicAlbum | null = null
  @Trace tracks: MusicTrack[] = []
  @Trace tracksHasMore: boolean = false
  @Trace tracksNextStartIndex: number = 0
  @Trace tracksLoadingMore: boolean = false
  @Trace tracksLoadMoreFailed: boolean = false
  @Trace tracksLoadMoreErrorCode: MusicBrowseErrorCode = MusicBrowseErrorCode.None
  @Trace favoritePending: boolean = false
  @Trace errorCode: MusicBrowseErrorCode = MusicBrowseErrorCode.None
}
