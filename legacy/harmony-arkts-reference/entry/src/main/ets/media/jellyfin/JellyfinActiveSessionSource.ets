import { AppPreference } from '../../data/AppPreference'
import { ApiClient } from '../../network/ApiClient'
import { MediaProviderSessionSource } from '../catalog/MediaCatalogProviderResolver'
import { MediaProviderKind } from '../MediaProviderKind'
import { MediaConnectionRoute, MediaProviderSession } from '../MediaProviderSession'
import { createJellyfinProviderAccountId } from './JellyfinAccountRepository'

export interface JellyfinActiveIdentity {
  readonly serverId: number
  readonly userId: string
  readonly baseUrl: string
}

export interface JellyfinActiveIdentitySource {
  currentIdentity(): JellyfinActiveIdentity | undefined
}

export class JellyfinApiClientIdentitySource implements JellyfinActiveIdentitySource {
  currentIdentity(): JellyfinActiveIdentity | undefined {
    try {
      if (!ApiClient.isInitialized()) {
        return undefined
      }
      const serverId = AppPreference.getInstance().getCurrentServerId()
      const api = ApiClient.Instance()
      if (serverId === null) {
        return undefined
      }
      return {
        serverId: serverId,
        userId: String(api.userId || ''),
        baseUrl: String(api.basePath || '')
      }
    } catch (error) {
      return undefined
    }
  }
}

export class JellyfinActiveSessionSource implements MediaProviderSessionSource {
  private identitySource: JellyfinActiveIdentitySource

  constructor(identitySource: JellyfinActiveIdentitySource = new JellyfinApiClientIdentitySource()) {
    this.identitySource = identitySource
  }

  currentSession(): MediaProviderSession | undefined {
    const identity = this.identitySource.currentIdentity()
    if (!identity) {
      return undefined
    }
    const userId = identity.userId.trim()
    const baseUrl = this.normalizeBaseUrl(identity.baseUrl)
    if (identity.serverId <= 0 || userId.length === 0 || baseUrl.length === 0) {
      return undefined
    }
    return new MediaProviderSession({
      provider: MediaProviderKind.Jellyfin,
      providerAccountId: createJellyfinProviderAccountId(identity.serverId, userId),
      userId: userId,
      originalAddress: baseUrl,
      baseUrl: baseUrl,
      connectionRoute: MediaConnectionRoute.Direct
    })
  }

  private normalizeBaseUrl(value: string): string {
    let normalized = value.trim()
    while (normalized.endsWith('/')) {
      normalized = normalized.slice(0, -1)
    }
    return normalized
  }
}
