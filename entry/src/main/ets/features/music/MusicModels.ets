export const MUSIC_TICKS_PER_MILLISECOND: number = 10000

export function musicTicksToMilliseconds(ticks?: number | null): number {
  if (ticks === undefined || ticks === null || !Number.isFinite(ticks) || ticks <= 0) {
    return 0
  }
  return Math.floor(ticks / MUSIC_TICKS_PER_MILLISECOND)
}

export function millisecondsToMusicTicks(milliseconds: number): number {
  if (!Number.isFinite(milliseconds) || milliseconds <= 0) {
    return 0
  }
  return Math.floor(milliseconds) * MUSIC_TICKS_PER_MILLISECOND
}

export enum MusicLibrarySection {
  Albums,
  Artists,
  Tracks
}

export interface MusicLibraryRef {
  id: string
  name: string
  itemCount: number
}

export interface MusicAlbum {
  id: string
  title: string
  artist: string
  artistId: string
  year: number
  imageUrl: string
  trackCount: number
  durationMs: number
  favorite: boolean
  overview: string
}

export interface MusicArtist {
  id: string
  name: string
  imageUrl: string
  albumCount: number
  trackCount: number
  overview: string
}

export interface MusicTrack {
  id: string
  title: string
  artist: string
  artistId: string
  album: string
  albumId: string
  discNumber: number
  trackNumber: number
  durationMs: number
  imageUrl: string
  favorite: boolean
  playbackPositionMs: number
}

export interface MusicQueueItem {
  id: string
  title: string
  artist: string
  album: string
  albumId: string
  imageUrl: string
  durationMs: number
  favorite: boolean
  playbackPositionMs: number
  serverId: string
}

export interface MusicPageResult<T> {
  items: T[]
  startIndex: number
  totalCount: number
  hasMore: boolean
}

export enum MusicBrowseErrorCode {
  None,
  AuthenticationExpired,
  PermissionDenied,
  NotFound,
  Offline,
  RequestFailed
}

export enum MusicPlaybackStatus {
  Idle,
  Preparing,
  Playing,
  Paused,
  Buffering,
  Completed,
  Error
}

export enum MusicPlaybackCandidateStatus {
  None,
  Preparing,
  Error
}

export enum MusicPlaybackErrorCode {
  None,
  Network,
  Authentication,
  PermissionDenied,
  NotFound,
  NoPlayableSource,
  TranscodingFailed,
  SystemPlayer
}

export enum MusicPlayMethod {
  DirectPlay,
  DirectStream,
  Transcode
}

export enum MusicRepeatMode {
  Off,
  All,
  One
}

export interface MusicResolvedSource {
  itemId: string
  mediaSourceId: string
  playSessionId: string
  liveStreamId: string
  url: string
  playMethod: MusicPlayMethod
  durationMs: number
  startPositionMs: number
  scope: string
}

export interface MusicScopedResult<T> {
  scope: string
  value: T
}

export interface MusicPlaybackSnapshot {
  queue: MusicQueueItem[]
  currentIndex: number
  status: MusicPlaybackStatus
  positionMs: number
  durationMs: number
  errorCode: MusicPlaybackErrorCode
  errorMessage: string
  candidateItemId?: string
  candidateStatus?: MusicPlaybackCandidateStatus
  candidateErrorCode?: MusicPlaybackErrorCode
  candidateErrorMessage?: string
  activeScope: string
  source?: MusicResolvedSource
  repeatMode: MusicRepeatMode
}
