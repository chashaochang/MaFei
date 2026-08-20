import { MediaProviderSession } from '../MediaProviderSession'
import { MediaRef, MediaRefKind } from '../MediaRef'
import {
  MediaCatalogCounts,
  MediaDetail,
  MediaEpisode,
  MediaFavoriteState,
  MediaLibrary,
  MediaPage,
  MediaPerson,
  MediaSeason,
  MediaSummary,
  MediaWatchedState
} from './MediaCatalogModels'

export enum MediaCatalogSortField {
  Default = 'default',
  Name = 'name',
  DateCreated = 'date_created',
  PremiereDate = 'premiere_date',
  DatePlayed = 'date_played',
  CommunityRating = 'community_rating',
  CriticRating = 'critic_rating',
  ProductionYear = 'production_year',
  PlayCount = 'play_count',
  Runtime = 'runtime',
  OfficialRating = 'official_rating',
  Random = 'random',
  Album = 'album',
  Artist = 'artist',
  AlbumArtist = 'album_artist',
  AiredEpisodeOrder = 'aired_episode_order',
  SeriesSortName = 'series_sort_name',
  Studio = 'studio',
  IsFavoriteOrLiked = 'is_favorite_or_liked',
  DateLastContentAdded = 'date_last_content_added',
  IsUnplayed = 'is_unplayed',
  IsPlayed = 'is_played'
}

export enum MediaCatalogSortDirection {
  Ascending = 'ascending',
  Descending = 'descending'
}

export interface MediaRecommendationsRequest {
  readonly categoryLimit: number
  readonly itemLimit: number
  readonly resultLimit: number
}

export interface MediaResumeRequest {
  readonly kinds: MediaRefKind[]
  readonly limit: number
}

export interface MediaNextUpRequest {
  readonly seriesRef?: MediaRef
  readonly limit: number
  readonly dateCutoff?: string
  readonly disableFirstEpisode: boolean
  readonly enableResumable: boolean
  readonly enableRewatching: boolean
}

export enum MediaEpisodeQueryProfile {
  Display = 'display',
  PlaybackQueue = 'playback_queue'
}

export interface MediaLatestRequest {
  readonly libraryRef?: MediaRef
  readonly kinds: MediaRefKind[]
  readonly startIndex: number
  readonly limit: number
  readonly recursive: boolean
}

export interface MediaListRequest {
  readonly libraryRef?: MediaRef
  readonly parentRef?: MediaRef
  readonly personRef?: MediaRef
  readonly kinds: MediaRefKind[]
  readonly startIndex: number
  readonly limit: number
  readonly sortFields: MediaCatalogSortField[]
  readonly sortDirections: MediaCatalogSortDirection[]
  readonly recursive: boolean
  readonly collapseBoxSetItems: boolean
  readonly favoriteOnly: boolean
  readonly watched?: boolean
}

export interface MediaSearchRequest {
  readonly query: string
  readonly libraryRef?: MediaRef
  readonly kinds: MediaRefKind[]
  readonly startIndex: number
  readonly limit: number
  readonly recursive: boolean
}

export interface MediaEpisodeRequest {
  readonly seriesRef: MediaRef
  readonly seasonRef?: MediaRef
  readonly startIndex: number
  readonly limit: number
  readonly profile?: MediaEpisodeQueryProfile
}

export interface MediaCatalogProvider {
  /** Immutable account snapshot used for every request from this provider instance. */
  readonly session: MediaProviderSession

  getCounts(): Promise<MediaCatalogCounts>

  getLibraries(): Promise<MediaLibrary[]>

  recommendations(request: MediaRecommendationsRequest): Promise<MediaSummary[]>

  resume(request: MediaResumeRequest): Promise<MediaPage<MediaSummary>>

  nextUp(request: MediaNextUpRequest): Promise<MediaPage<MediaSummary>>

  latest(request: MediaLatestRequest): Promise<MediaPage<MediaSummary>>

  getItems(request: MediaListRequest): Promise<MediaPage<MediaSummary>>

  search(request: MediaSearchRequest): Promise<MediaPage<MediaSummary>>

  getDetail(ref: MediaRef): Promise<MediaDetail>

  getSeasons(seriesRef: MediaRef): Promise<MediaSeason[]>

  getEpisodes(request: MediaEpisodeRequest): Promise<MediaPage<MediaEpisode>>

  getPerson(ref: MediaRef): Promise<MediaPerson>

  setFavorite(ref: MediaRef, favorite: boolean): Promise<MediaFavoriteState>

  setWatched(ref: MediaRef, watched: boolean): Promise<MediaWatchedState>

  /** Scan, delete, playback, search history, and chasing intentionally live outside this port. */
}
