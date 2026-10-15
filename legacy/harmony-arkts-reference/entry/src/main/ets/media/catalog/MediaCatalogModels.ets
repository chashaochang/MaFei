import { MediaProviderKind } from '../MediaProviderKind'
import { MediaProviderSession } from '../MediaProviderSession'
import { MediaRef } from '../MediaRef'

export enum MediaLibraryKind {
  Movies = 'movies',
  Series = 'series',
  Music = 'music',
  MusicVideos = 'music_videos',
  Photos = 'photos',
  LiveTv = 'live_tv',
  Trailers = 'trailers',
  BoxSets = 'box_sets',
  Books = 'books',
  Playlists = 'playlists',
  Mixed = 'mixed',
  Other = 'other'
}

export enum MediaImageKind {
  Primary = 'primary',
  Backdrop = 'backdrop',
  Logo = 'logo',
  Thumbnail = 'thumbnail',
  Avatar = 'avatar'
}

export enum MediaHomeSectionKind {
  Featured = 'featured',
  ContinueWatching = 'continue_watching',
  NextUp = 'next_up',
  Latest = 'latest',
  Library = 'library'
}

export enum MediaPersonKind {
  Actor = 'actor',
  Director = 'director',
  Writer = 'writer',
  Producer = 'producer',
  GuestStar = 'guest_star',
  Other = 'other'
}

export enum MediaStreamKind {
  Video = 'video',
  Audio = 'audio',
  Subtitle = 'subtitle',
  Other = 'other'
}

export interface MediaRequestHeader {
  readonly name: string
  readonly value: string
}

export interface MediaImageRefOptions {
  readonly owner: MediaRef
  readonly kind: MediaImageKind
  readonly url: string
  /** Credential-free provider request path used to rebuild the URL for the active route. */
  readonly requestPath?: string
  /** Stable variant such as original, poster-600, or backdrop-1280. */
  readonly variant: string
  /** Provider image revision or tag. It must not contain credentials. */
  readonly revision?: string
  readonly headers?: MediaRequestHeader[]
  /** Reuses a previously generated cache key without persisting request credentials. */
  readonly cacheKeyOverride?: string
}

function cacheKeyPart(value: string): string {
  return `${value.length}:${value}`
}

export function createMediaImageCacheKey(
  owner: MediaRef,
  kind: MediaImageKind,
  variant: string,
  revision: string = ''
): string {
  const normalizedVariant = variant.trim()
  if (normalizedVariant.length === 0) {
    throw new Error('Media image cache variant is required')
  }
  return `v1|${cacheKeyPart(owner.identity)}|${cacheKeyPart(kind)}|` +
    `${cacheKeyPart(normalizedVariant)}|${cacheKeyPart(revision.trim())}`
}

export class MediaImageRef {
  readonly owner: MediaRef
  readonly provider: MediaProviderKind
  readonly accountScope: string
  readonly kind: MediaImageKind
  readonly url: string
  readonly requestPath: string
  readonly headers: MediaRequestHeader[]
  readonly cacheKey: string

  constructor(options: MediaImageRefOptions) {
    this.owner = options.owner
    this.provider = options.owner.provider
    this.accountScope = options.owner.accountScope
    this.kind = options.kind
    this.url = options.url.trim()
    this.requestPath = `${options.requestPath || ''}`.trim()
    this.headers = (options.headers || []).map((header: MediaRequestHeader): MediaRequestHeader => {
      return {
        name: header.name.trim(),
        value: header.value
      }
    })
    const cacheKeyOverride = `${options.cacheKeyOverride || ''}`.trim()
    this.cacheKey = cacheKeyOverride.length > 0 ? cacheKeyOverride : createMediaImageCacheKey(
      options.owner,
      options.kind,
      options.variant,
      options.revision || ''
    )
  }

  belongsTo(session: MediaProviderSession): boolean {
    return this.provider === session.provider && this.accountScope === session.accountScope
  }
}

export interface MediaFavoriteState {
  readonly favorite: boolean
  readonly canUpdate: boolean
}

export interface MediaWatchedState {
  readonly watched: boolean
  readonly canUpdate: boolean
  readonly playbackPositionMs: number
  readonly playedPercentage: number
}

export interface MediaUserState {
  readonly favorite: MediaFavoriteState
  readonly watched: MediaWatchedState
}

export function createMediaUserState(
  favorite: boolean = false,
  watched: boolean = false,
  playbackPositionMs: number = 0,
  playedPercentage: number = 0,
  canUpdateFavorite: boolean = true,
  canUpdateWatched: boolean = true
): MediaUserState {
  return {
    favorite: {
      favorite: favorite,
      canUpdate: canUpdateFavorite
    },
    watched: {
      watched: watched,
      canUpdate: canUpdateWatched,
      playbackPositionMs: playbackPositionMs,
      playedPercentage: playedPercentage
    }
  }
}

export interface MediaLibrary {
  readonly ref: MediaRef
  readonly title: string
  readonly kind: MediaLibraryKind
  readonly itemCount: number
  readonly primaryImage?: MediaImageRef
}

export interface MediaSummary {
  readonly ref: MediaRef
  readonly seriesRef?: MediaRef
  readonly seasonRef?: MediaRef
  readonly title: string
  readonly subtitle: string
  readonly overview: string
  readonly year?: number
  readonly premiereDate?: string
  readonly lastPlayedDate?: string
  readonly durationMs: number
  readonly communityRating?: number
  readonly seriesTitle?: string
  readonly seasonNumber?: number
  readonly episodeNumber?: number
  readonly childCount?: number
  readonly canDelete?: boolean
  readonly path?: string
  readonly genres: string[]
  readonly tags: string[]
  readonly primaryImage?: MediaImageRef
  readonly thumbnailImage?: MediaImageRef
  readonly backdropImage?: MediaImageRef
  readonly logoImage?: MediaImageRef
  readonly userState: MediaUserState
}

export interface MediaHomeSection {
  readonly id: string
  readonly title: string
  readonly kind: MediaHomeSectionKind
  readonly libraryRef?: MediaRef
  readonly items: MediaSummary[]
}

export interface MediaPage<T> {
  readonly items: T[]
  readonly startIndex: number
  readonly nextStartIndex: number
  readonly totalCount: number
  readonly hasMore: boolean
}

export interface MediaExternalLink {
  readonly title: string
  readonly url: string
}

export interface MediaPersonCredit {
  readonly personRef: MediaRef
  readonly name: string
  readonly kind: MediaPersonKind
  readonly role: string
  readonly primaryImage?: MediaImageRef
}

export interface MediaPlaybackTarget {
  readonly ref?: MediaRef
  readonly seasonNumber?: number
  readonly episodeNumber?: number
  readonly playbackPositionMs?: number
  readonly playedPercentage?: number
  readonly durationMs?: number
}

export interface MediaStreamInfo {
  readonly index: number
  readonly kind: MediaStreamKind
  readonly title: string
  readonly displayTitle: string
  readonly codec: string
  readonly profile?: string
  readonly level?: number
  readonly language: string
  readonly bitrate?: number
  readonly bitDepth?: number
  readonly refFrames?: number
  readonly sampleRate?: number
  readonly channels?: number
  readonly width?: number
  readonly height?: number
  readonly codedWidth?: number
  readonly codedHeight?: number
  readonly aspectRatio?: string
  readonly frameRate?: number
  readonly rotation?: number
  readonly interlaced?: boolean
  readonly resolutionType?: string
  readonly videoRangeType?: string
  readonly dvProfile?: number
  readonly colorPrimaries?: string
  readonly colorSpace?: string
  readonly colorTransfer?: string
  readonly pixelFormat?: string
  readonly audioType?: string
  readonly channelLayout?: string
  readonly bluray?: boolean
  readonly fake?: boolean
  readonly source?: string
  readonly fileSizeBytes?: number
  readonly external: boolean
  readonly default: boolean
  readonly forced: boolean
}

export interface MediaDirectLinkInfo {
  readonly url: string
  readonly resolution: string
  readonly bitrate: number
  readonly progressive: boolean
  readonly hls: boolean
}

export interface MediaFileInfo {
  readonly ownerRef: MediaRef
  readonly id: string
  readonly name: string
  readonly path: string
  readonly container: string
  readonly protocol: string
  readonly sizeBytes: number
  readonly bitrate?: number
  readonly durationMs: number
  readonly fileCreatedAtMs?: number
  readonly addedAtMs?: number
  readonly sortOrder?: number
  readonly playable?: boolean
  readonly playError?: string
  readonly defaultVideoStreamIndex?: number
  readonly defaultAudioStreamIndex?: number
  readonly defaultSubtitleStreamIndex?: number
  readonly directLinks?: MediaDirectLinkInfo[]
  readonly streams: MediaStreamInfo[]
}

export interface MediaPerson {
  readonly ref: MediaRef
  readonly name: string
  readonly sortName: string
  readonly overview: string
  readonly kind: MediaPersonKind
  readonly role: string
  readonly movieCount?: number
  readonly episodeCount?: number
  readonly seriesCount?: number
  readonly primaryImage?: MediaImageRef
  readonly userState: MediaUserState
}

export interface MediaSeason {
  readonly ref: MediaRef
  readonly seriesRef: MediaRef
  readonly title: string
  readonly overview: string
  readonly seasonNumber?: number
  readonly productionYear?: number
  readonly communityRating?: number
  readonly episodeCount: number
  readonly tags: string[]
  readonly primaryImage?: MediaImageRef
  readonly backdropImage?: MediaImageRef
  readonly userState: MediaUserState
}

export interface MediaEpisode {
  readonly ref: MediaRef
  readonly seriesRef: MediaRef
  readonly seasonRef?: MediaRef
  readonly title: string
  readonly seriesTitle: string
  readonly overview: string
  readonly seasonNumber?: number
  readonly episodeNumber?: number
  readonly premiereDate?: string
  readonly durationMs: number
  readonly communityRating?: number
  readonly tags: string[]
  readonly primaryImage?: MediaImageRef
  readonly backdropImage?: MediaImageRef
  readonly userState: MediaUserState
}

export interface MediaDetail {
  readonly ref: MediaRef
  readonly seriesRef?: MediaRef
  readonly seasonRef?: MediaRef
  readonly title: string
  readonly originalTitle: string
  readonly tagline: string
  readonly overview: string
  readonly seriesTitle?: string
  readonly seasonTitle?: string
  readonly seasonNumber?: number
  readonly episodeNumber?: number
  readonly productionYear?: number
  readonly premiereDate?: string
  readonly endDate?: string
  readonly durationMs: number
  readonly officialRating: string
  readonly communityRating?: number
  readonly criticRating?: number
  readonly canDelete?: boolean
  readonly path?: string
  readonly genres: string[]
  readonly tags: string[]
  readonly studios: string[]
  readonly personCredits: MediaPersonCredit[]
  readonly seasons: MediaSeason[]
  readonly playbackTarget?: MediaPlaybackTarget
  readonly mediaFiles: MediaFileInfo[]
  readonly externalLinks: MediaExternalLink[]
  readonly primaryImage?: MediaImageRef
  readonly backdropImage?: MediaImageRef
  readonly logoImage?: MediaImageRef
  readonly userState: MediaUserState
}

export interface MediaCatalogCounts {
  readonly totalCount: number
  readonly movieCount: number
  readonly seriesCount: number
  readonly episodeCount: number
}
