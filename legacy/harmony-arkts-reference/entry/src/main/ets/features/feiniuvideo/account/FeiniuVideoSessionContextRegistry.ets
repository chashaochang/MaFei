import {
  MediaProviderRegistry,
  MediaProviderScopedMemoryCache
} from '../../../media/MediaProviderRegistry'
import { MediaProviderKind } from '../../../media/MediaProviderKind'
import { MediaProviderSession } from '../../../media/MediaProviderSession'
import { FeiniuVideoSessionContext } from './FeiniuVideoSessionService'

/**
 * Keeps authenticated Feiniu clients account-scoped and reusable by catalog,
 * playback, and account flows without copying credentials into neutral models.
 */
export class FeiniuVideoSessionContextRegistry implements MediaProviderScopedMemoryCache {
  private static shared?: FeiniuVideoSessionContextRegistry
  private readonly providerRegistry: MediaProviderRegistry
  private readonly contexts: Map<string, FeiniuVideoSessionContext> = new Map()

  constructor(providerRegistry: MediaProviderRegistry = MediaProviderRegistry.instance()) {
    this.providerRegistry = providerRegistry
  }

  static instance(): FeiniuVideoSessionContextRegistry {
    if (!FeiniuVideoSessionContextRegistry.shared) {
      FeiniuVideoSessionContextRegistry.shared = new FeiniuVideoSessionContextRegistry()
    }
    return FeiniuVideoSessionContextRegistry.shared
  }

  register(context: FeiniuVideoSessionContext): void {
    this.requireFeiniuSession(context.session)
    const accountScope = context.session.accountScope
    this.contexts.set(accountScope, context)
    this.providerRegistry.registerScopedMemoryCache(accountScope, this)
  }

  get(session: MediaProviderSession): FeiniuVideoSessionContext | undefined {
    if (session.provider !== MediaProviderKind.FeiniuVideo) {
      return undefined
    }
    const context = this.contexts.get(session.accountScope)
    if (!context || !context.session.isSameAccount(session)) {
      return undefined
    }
    return context
  }

  remove(session: MediaProviderSession): void {
    if (session.provider !== MediaProviderKind.FeiniuVideo) {
      return
    }
    this.clearScope(session.accountScope)
  }

  removeByProviderAccountId(providerAccountId: string): void {
    const normalizedId = providerAccountId.trim()
    if (normalizedId.length === 0) {
      return
    }
    const scopes: string[] = []
    this.contexts.forEach((context: FeiniuVideoSessionContext, accountScope: string): void => {
      if (context.session.providerAccountId === normalizedId) {
        scopes.push(accountScope)
      }
    })
    scopes.forEach((accountScope: string): void => {
      this.clearScope(accountScope)
    })
  }

  clear(): void {
    const scopes: string[] = []
    this.contexts.forEach((_context: FeiniuVideoSessionContext, accountScope: string): void => {
      scopes.push(accountScope)
    })
    scopes.forEach((accountScope: string): void => {
      this.clearScope(accountScope)
    })
  }

  clearScope(accountScope: string): void {
    this.contexts.delete(accountScope)
    this.providerRegistry.unregisterScopedMemoryCache(accountScope, this)
  }

  private requireFeiniuSession(session: MediaProviderSession): void {
    if (session.provider !== MediaProviderKind.FeiniuVideo) {
      throw new Error('Feiniu session context registry only accepts Feiniu Video sessions')
    }
    if (session.providerAccountId.length === 0 || session.accountScope.length === 0) {
      throw new Error('Feiniu session context requires an account scope')
    }
  }
}
