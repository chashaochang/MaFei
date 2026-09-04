import { MediaProviderKind } from '../MediaProviderKind'
import { MediaRef, MediaRefKind } from '../MediaRef'

export interface MediaRouteRefData {
  readonly provider: string
  readonly accountScope: string
  readonly id: string
  readonly kind: string
}

function routeProvider(value: string): MediaProviderKind | undefined {
  if (value === MediaProviderKind.Jellyfin) {
    return MediaProviderKind.Jellyfin
  }
  if (value === MediaProviderKind.FeiniuVideo) {
    return MediaProviderKind.FeiniuVideo
  }
  return undefined
}

function routeKind(value: string): MediaRefKind | undefined {
  if (value === MediaRefKind.Unknown) {
    return MediaRefKind.Unknown
  }
  if (value === MediaRefKind.Library) {
    return MediaRefKind.Library
  }
  if (value === MediaRefKind.Movie) {
    return MediaRefKind.Movie
  }
  if (value === MediaRefKind.Video) {
    return MediaRefKind.Video
  }
  if (value === MediaRefKind.Series) {
    return MediaRefKind.Series
  }
  if (value === MediaRefKind.Season) {
    return MediaRefKind.Season
  }
  if (value === MediaRefKind.Episode) {
    return MediaRefKind.Episode
  }
  if (value === MediaRefKind.Person) {
    return MediaRefKind.Person
  }
  if (value === MediaRefKind.LiveChannel) {
    return MediaRefKind.LiveChannel
  }
  return undefined
}

export function toMediaRouteRefData(ref: MediaRef): MediaRouteRefData {
  return {
    provider: ref.provider,
    accountScope: ref.accountScope,
    id: ref.id,
    kind: ref.kind
  }
}

export function parseMediaRouteRefData(data?: MediaRouteRefData): MediaRef | undefined {
  if (!data || typeof data.provider !== 'string' || typeof data.accountScope !== 'string' ||
    typeof data.id !== 'string' || typeof data.kind !== 'string') {
    return undefined
  }
  const provider = routeProvider(data.provider)
  const kind = routeKind(data.kind)
  const accountScope = data.accountScope.trim()
  const id = data.id.trim()
  if (!provider || !kind || accountScope.length === 0 || id.length === 0) {
    return undefined
  }
  return new MediaRef(provider, accountScope, id, kind)
}
