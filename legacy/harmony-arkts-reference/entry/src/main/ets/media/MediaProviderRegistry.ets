import { MediaProviderSession } from './MediaProviderSession'

export interface MediaProviderScopedPlayback {
  stopForScope(accountScope: string): Promise<void>
  /**
   * True while this playback owner still holds a prepared media source.  The
   * scope must not replace its connection/session underneath that source.
   */
  hasActivePlaybackSession(): boolean
}

export interface MediaProviderScopePlaybackIdleListener {
  onScopePlaybackIdle(accountScope: string): void
}

export interface MediaProviderScopedRequest {
  cancelForScope(accountScope: string): Promise<void>
}

export interface MediaProviderScopedMemoryCache {
  clearScope(accountScope: string): void
}

export class MediaProviderRegistry {
  private static shared?: MediaProviderRegistry
  private activeSession?: MediaProviderSession
  private playbackByScope: Map<string, MediaProviderScopedPlayback[]> = new Map()
  private playbackIdleListenersByScope: Map<string,
    MediaProviderScopePlaybackIdleListener[]> = new Map()
  private requestByScope: Map<string, MediaProviderScopedRequest[]> = new Map()
  private memoryCacheByScope: Map<string, MediaProviderScopedMemoryCache[]> = new Map()

  static instance(): MediaProviderRegistry {
    if (!MediaProviderRegistry.shared) {
      MediaProviderRegistry.shared = new MediaProviderRegistry()
    }
    return MediaProviderRegistry.shared
  }

  getActiveSession(): MediaProviderSession | undefined {
    return this.activeSession
  }

  requiresScopeChange(next: MediaProviderSession): boolean {
    return !!this.activeSession && !this.activeSession.isSameAccount(next)
  }

  registerScopedPlayback(accountScope: string, playback: MediaProviderScopedPlayback): void {
    const items = this.playbackByScope.get(accountScope) || []
    if (items.indexOf(playback) === -1) {
      items.push(playback)
      this.playbackByScope.set(accountScope, items)
    }
  }

  unregisterScopedPlayback(accountScope: string, playback: MediaProviderScopedPlayback): void {
    const items = this.playbackByScope.get(accountScope)
    if (!items) {
      return
    }
    const index = items.indexOf(playback)
    if (index >= 0) {
      items.splice(index, 1)
    }
    if (items.length === 0) {
      this.playbackByScope.delete(accountScope)
    }
  }

  hasActivePlaybackSession(accountScope: string): boolean {
    const normalizedScope = accountScope.trim()
    if (normalizedScope.length === 0) {
      return false
    }
    const items = this.playbackByScope.get(normalizedScope) || []
    for (const playback of items) {
      if (playback.hasActivePlaybackSession()) {
        return true
      }
    }
    return false
  }

  addScopePlaybackIdleListener(accountScope: string,
    listener: MediaProviderScopePlaybackIdleListener): void {
    const normalizedScope = accountScope.trim()
    if (normalizedScope.length === 0) {
      return
    }
    const listeners = this.playbackIdleListenersByScope.get(normalizedScope) || []
    if (listeners.indexOf(listener) === -1) {
      listeners.push(listener)
      this.playbackIdleListenersByScope.set(normalizedScope, listeners)
    }
  }

  removeScopePlaybackIdleListener(accountScope: string,
    listener: MediaProviderScopePlaybackIdleListener): void {
    const normalizedScope = accountScope.trim()
    const listeners = this.playbackIdleListenersByScope.get(normalizedScope)
    if (!listeners) {
      return
    }
    const index = listeners.indexOf(listener)
    if (index >= 0) {
      listeners.splice(index, 1)
    }
    if (listeners.length === 0) {
      this.playbackIdleListenersByScope.delete(normalizedScope)
    }
  }

  notifyScopePlaybackIdle(accountScope: string): void {
    const normalizedScope = accountScope.trim()
    if (normalizedScope.length === 0 || this.hasActivePlaybackSession(normalizedScope)) {
      return
    }
    const listeners = this.playbackIdleListenersByScope.get(normalizedScope) || []
    for (const listener of listeners.slice()) {
      listener.onScopePlaybackIdle(normalizedScope)
    }
  }

  registerScopedRequest(accountScope: string, request: MediaProviderScopedRequest): void {
    const items = this.requestByScope.get(accountScope) || []
    if (items.indexOf(request) === -1) {
      items.push(request)
      this.requestByScope.set(accountScope, items)
    }
  }

  unregisterScopedRequest(accountScope: string, request: MediaProviderScopedRequest): void {
    const items = this.requestByScope.get(accountScope)
    if (!items) {
      return
    }
    const index = items.indexOf(request)
    if (index >= 0) {
      items.splice(index, 1)
    }
    if (items.length === 0) {
      this.requestByScope.delete(accountScope)
    }
  }

  registerScopedMemoryCache(accountScope: string, cache: MediaProviderScopedMemoryCache): void {
    const items = this.memoryCacheByScope.get(accountScope) || []
    if (items.indexOf(cache) === -1) {
      items.push(cache)
      this.memoryCacheByScope.set(accountScope, items)
    }
  }

  unregisterScopedMemoryCache(accountScope: string, cache: MediaProviderScopedMemoryCache): void {
    const items = this.memoryCacheByScope.get(accountScope)
    if (!items) {
      return
    }
    const index = items.indexOf(cache)
    if (index >= 0) {
      items.splice(index, 1)
    }
    if (items.length === 0) {
      this.memoryCacheByScope.delete(accountScope)
    }
  }

  async activateSession(next: MediaProviderSession): Promise<void> {
    if (this.requiresScopeChange(next)) {
      await this.releaseScope(this.activeSession!.accountScope)
    }
    this.activeSession = next
  }

  async clearActiveSession(): Promise<void> {
    const session = this.activeSession
    if (!session) {
      return
    }
    await this.releaseScope(session.accountScope)
    this.activeSession = undefined
  }

  private async releaseScope(accountScope: string): Promise<void> {
    let cleanupError: Error | undefined
    const playbackItems = this.playbackByScope.get(accountScope) || []
    for (const playback of playbackItems.slice()) {
      try {
        await playback.stopForScope(accountScope)
        this.unregisterScopedPlayback(accountScope, playback)
      } catch (error) {
        if (!cleanupError) {
          cleanupError = error as Error
        }
      }
    }
    const requestItems = this.requestByScope.get(accountScope) || []
    for (const request of requestItems.slice()) {
      try {
        await request.cancelForScope(accountScope)
        this.unregisterScopedRequest(accountScope, request)
      } catch (error) {
        if (!cleanupError) {
          cleanupError = error as Error
        }
      }
    }
    const cacheItems = this.memoryCacheByScope.get(accountScope) || []
    for (const cache of cacheItems.slice()) {
      try {
        cache.clearScope(accountScope)
        this.unregisterScopedMemoryCache(accountScope, cache)
      } catch (error) {
        if (!cleanupError) {
          cleanupError = error as Error
        }
      }
    }
    if (cleanupError) {
      throw cleanupError
    }
  }
}
