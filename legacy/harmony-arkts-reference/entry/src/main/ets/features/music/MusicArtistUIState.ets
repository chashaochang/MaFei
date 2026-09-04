import { BaseUIState } from '../../common/base/BaseUIState'
import { MusicAlbum, MusicArtist, MusicBrowseErrorCode, MusicTrack } from './MusicModels'
import { MusicDetailState } from './MusicAlbumUIState'

@ObservedV2
export class MusicArtistUIState extends BaseUIState {
  @Trace state: MusicDetailState = MusicDetailState.Loading
  @Trace artist: MusicArtist | null = null
  @Trace popularTracks: MusicTrack[] = []
  @Trace albums: MusicAlbum[] = []
  @Trace albumsHasMore: boolean = false
  @Trace albumsNextStartIndex: number = 0
  @Trace albumsLoadingMore: boolean = false
  @Trace albumsLoadMoreFailed: boolean = false
  @Trace albumsLoadMoreErrorCode: MusicBrowseErrorCode = MusicBrowseErrorCode.None
  @Trace errorCode: MusicBrowseErrorCode = MusicBrowseErrorCode.None
}
