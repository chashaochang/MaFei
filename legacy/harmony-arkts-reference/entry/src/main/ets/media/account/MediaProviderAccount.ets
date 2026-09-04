import {
  copyMediaProviderCapabilities,
  MediaProviderCapabilities,
  mediaProviderCapabilities
} from '../MediaProviderCapabilities'
import { MediaProviderKind } from '../MediaProviderKind'
import {
  createMediaAccountScope,
  MediaConnectionRoute,
  MediaProviderSession
} from '../MediaProviderSession'

export interface MediaProviderAccountOptions {
  readonly provider: MediaProviderKind
  readonly providerAccountId: string
  readonly userId: string
  readonly displayName: string
  readonly serverName: string
  readonly originalAddress: string
  readonly baseUrl: string
  readonly connectionRoute?: string
  readonly fallbackBaseUrls?: string[]
  readonly lastUsedTimestamp?: number
  readonly capabilities?: MediaProviderCapabilities
}

export class MediaProviderAccount {
  readonly provider: MediaProviderKind
  readonly providerAccountId: string
  readonly userId: string
  readonly displayName: string
  readonly serverName: string
  readonly originalAddress: string
  readonly baseUrl: string
  readonly connectionRoute: string
  readonly fallbackBaseUrls: string[]
  readonly lastUsedTimestamp: number
  readonly capabilities: MediaProviderCapabilities
  readonly accountScope: string

  constructor(options: MediaProviderAccountOptions) {
    this.provider = options.provider
    this.providerAccountId = options.providerAccountId.trim()
    this.userId = options.userId.trim()
    this.displayName = options.displayName.trim()
    this.serverName = options.serverName.trim()
    this.originalAddress = options.originalAddress.trim()
    this.baseUrl = options.baseUrl.trim()
    this.connectionRoute = options.connectionRoute?.trim() || MediaConnectionRoute.Direct
    this.fallbackBaseUrls = options.fallbackBaseUrls ? options.fallbackBaseUrls.slice() : []
    this.lastUsedTimestamp = options.lastUsedTimestamp || 0
    this.capabilities = copyMediaProviderCapabilities(
      options.capabilities || mediaProviderCapabilities(options.provider)
    )
    this.accountScope = createMediaAccountScope(this.provider, this.providerAccountId)
  }

  toSession(): MediaProviderSession {
    return new MediaProviderSession({
      provider: this.provider,
      providerAccountId: this.providerAccountId,
      userId: this.userId,
      originalAddress: this.originalAddress,
      baseUrl: this.baseUrl,
      connectionRoute: this.connectionRoute,
      fallbackBaseUrls: this.fallbackBaseUrls,
      displayName: this.displayName,
      serverName: this.serverName,
      capabilities: this.capabilities
    })
  }
}
