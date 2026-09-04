import { MediaProviderSession } from './MediaProviderSession'
import { MediaProviderKind } from './MediaProviderKind'

export enum MediaRefKind {
  Unknown = 'unknown',
  Library = 'library',
  Movie = 'movie',
  Video = 'video',
  Series = 'series',
  Season = 'season',
  Episode = 'episode',
  Person = 'person',
  LiveChannel = 'live_channel'
}

function identityPart(value: string): string {
  return `${value.length}:${value}`
}

export class MediaRef {
  readonly provider: MediaProviderKind
  readonly accountScope: string
  readonly id: string
  readonly kind: MediaRefKind
  readonly identity: string

  constructor(provider: MediaProviderKind, accountScope: string, id: string,
    kind: MediaRefKind = MediaRefKind.Unknown) {
    this.provider = provider
    this.accountScope = accountScope.trim()
    this.id = id.trim()
    this.kind = kind
    this.identity = `v1|${identityPart(provider)}|${identityPart(this.accountScope)}|` +
      identityPart(this.id)
  }

  belongsTo(session: MediaProviderSession): boolean {
    return this.provider === session.provider && this.accountScope === session.accountScope
  }

  equals(other?: MediaRef): boolean {
    return !!other && this.identity === other.identity
  }
}
