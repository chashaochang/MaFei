import { MediaProviderKind } from './MediaProviderKind'

export interface MediaProviderCapabilities {
  /** Provides video libraries, lists, and details. */
  readonly videoCatalog: boolean
  /** Provides server-side media search. */
  readonly search: boolean
  /** Supports favorite reads and mutations. */
  readonly favorites: boolean
  /** Supports watched state reads and mutations. */
  readonly watched: boolean
  /** Supports MaFei's legacy local chasing workflow. */
  readonly chasing: boolean
  /** Resolves sources for the shared MaFei player. */
  readonly playback: boolean
  /** Provides Live TV content. */
  readonly liveTv: boolean
  /** Provides the MaFei music experience. */
  readonly music: boolean
  /** Provides the MaFei photo experience. */
  readonly photos: boolean
  /** Provides server administration. */
  readonly management: boolean
}

export function mediaProviderCapabilities(provider: MediaProviderKind): MediaProviderCapabilities {
  if (provider === MediaProviderKind.FeiniuVideo) {
    return {
      videoCatalog: true,
      search: true,
      favorites: true,
      watched: true,
      chasing: false,
      playback: true,
      liveTv: false,
      music: false,
      photos: false,
      management: false
    }
  }
  return {
    videoCatalog: true,
    search: true,
    favorites: true,
    watched: true,
    chasing: true,
    playback: true,
    liveTv: true,
    music: true,
    photos: true,
    management: true
  }
}

export function copyMediaProviderCapabilities(
  capabilities: MediaProviderCapabilities
): MediaProviderCapabilities {
  return {
    videoCatalog: capabilities.videoCatalog,
    search: capabilities.search,
    favorites: capabilities.favorites,
    watched: capabilities.watched,
    chasing: capabilities.chasing,
    playback: capabilities.playback,
    liveTv: capabilities.liveTv,
    music: capabilities.music,
    photos: capabilities.photos,
    management: capabilities.management
  }
}
