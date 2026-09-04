import {
  copyMediaProviderCapabilities,
  MediaProviderCapabilities,
  mediaProviderCapabilities
} from './MediaProviderCapabilities'
import { MediaProviderKind } from './MediaProviderKind'

export enum MediaConnectionRoute {
  Direct = 'direct',
  Local = 'lan',
  Public = 'public',
  Relay = 'relay'
}

export interface MediaProviderSessionOptions {
  /** Provider that owns this account. */
  readonly provider: MediaProviderKind
  /** Provider-owned account identifier, independent from the legacy Jellyfin database. */
  readonly providerAccountId: string
  /** Provider-side user identifier. */
  readonly userId: string
  /** User-entered FNID or direct address. */
  readonly originalAddress: string
  /** Currently resolved request base URL. */
  readonly baseUrl: string
  /** Route currently selected by the connection resolver. */
  readonly connectionRoute?: string
  /** Remaining request base URLs in fallback order. */
  readonly fallbackBaseUrls?: string[]
  /** User-facing account name. */
  readonly displayName?: string
  /** User-facing server name captured by the owning account repository. */
  readonly serverName?: string
  /** Features exposed by this provider. */
  readonly capabilities?: MediaProviderCapabilities
}

function scopedPart(value: string): string {
  return `${value.length}:${value}`
}

export function createMediaAccountScope(
  provider: MediaProviderKind,
  providerAccountId: string
): string {
  const normalizedAccountId = providerAccountId.trim()
  return `v1|${scopedPart(provider)}|${scopedPart(normalizedAccountId)}`
}

export class MediaProviderSession {
  readonly provider: MediaProviderKind
  readonly providerAccountId: string
  readonly userId: string
  readonly originalAddress: string
  readonly baseUrl: string
  readonly connectionRoute: string
  readonly fallbackBaseUrls: string[]
  readonly displayName: string
  readonly serverName: string
  readonly capabilities: MediaProviderCapabilities
  readonly accountScope: string

  constructor(options: MediaProviderSessionOptions) {
    this.provider = options.provider
    this.providerAccountId = options.providerAccountId.trim()
    this.userId = options.userId.trim()
    this.originalAddress = options.originalAddress.trim()
    this.baseUrl = options.baseUrl.trim()
    this.connectionRoute = options.connectionRoute?.trim() || MediaConnectionRoute.Direct
    this.fallbackBaseUrls = options.fallbackBaseUrls ? options.fallbackBaseUrls.slice() : []
    this.displayName = options.displayName?.trim() || ''
    this.serverName = options.serverName?.trim() || ''
    this.capabilities = copyMediaProviderCapabilities(
      options.capabilities || mediaProviderCapabilities(options.provider)
    )
    this.accountScope = createMediaAccountScope(this.provider, this.providerAccountId)
  }

  isSameAccount(other?: MediaProviderSession): boolean {
    return !!other && this.accountScope === other.accountScope
  }
}
