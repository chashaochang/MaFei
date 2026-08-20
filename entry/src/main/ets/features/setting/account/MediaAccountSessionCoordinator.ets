import { common } from '@kit.AbilityKit'
import {
  FeiniuVideoSessionContext,
  FeiniuVideoSessionService
} from '../../feiniuvideo/account/FeiniuVideoSessionService'
import { FeiniuVideoSessionContextRegistry } from
  '../../feiniuvideo/account/FeiniuVideoSessionContextRegistry'
import { JellyfinActiveSessionSource } from '../../../media/jellyfin/JellyfinActiveSessionSource'
import { MediaProviderSessionSource } from '../../../media/catalog/MediaCatalogProviderResolver'
import { MediaProviderKind } from '../../../media/MediaProviderKind'
import { MediaProviderRegistry } from '../../../media/MediaProviderRegistry'
import { MediaProviderSession } from '../../../media/MediaProviderSession'
import {
  CurrentMediaAccount,
  CurrentMediaAccountStore
} from '../../../media/account/CurrentMediaAccountStore'
import {
  MediaProviderSelectionHistory,
  MediaProviderSelectionHistoryStore
} from '../../../media/account/MediaProviderSelectionHistoryStore'

export interface FeiniuVideoSessionPort {
  restore(providerAccountId: string): Promise<FeiniuVideoSessionContext | undefined>
  logout(providerAccountId: string): Promise<boolean>
  delete(providerAccountId: string): void
}

export interface FeiniuVideoSessionContextPort {
  register(context: FeiniuVideoSessionContext): void
  get(session: MediaProviderSession): FeiniuVideoSessionContext | undefined
  remove(session: MediaProviderSession): void
  removeByProviderAccountId(providerAccountId: string): void
}

class FeiniuVideoSessionServiceAdapter implements FeiniuVideoSessionPort {
  private readonly service: FeiniuVideoSessionService

  constructor(service: FeiniuVideoSessionService) {
    this.service = service
  }

  restore(providerAccountId: string): Promise<FeiniuVideoSessionContext | undefined> {
    return this.service.restore(providerAccountId)
  }

  logout(providerAccountId: string): Promise<boolean> {
    return this.service.logout(providerAccountId)
  }

  delete(providerAccountId: string): void {
    this.service.delete(providerAccountId)
  }
}

class FeiniuVideoSessionContextRegistryAdapter implements FeiniuVideoSessionContextPort {
  private readonly registry: FeiniuVideoSessionContextRegistry

  constructor(registry: FeiniuVideoSessionContextRegistry) {
    this.registry = registry
  }

  register(context: FeiniuVideoSessionContext): void {
    this.registry.register(context)
  }

  get(session: MediaProviderSession): FeiniuVideoSessionContext | undefined {
    return this.registry.get(session)
  }

  remove(session: MediaProviderSession): void {
    this.registry.remove(session)
  }

  removeByProviderAccountId(providerAccountId: string): void {
    this.registry.removeByProviderAccountId(providerAccountId)
  }
}

class VolatileMediaProviderSelectionHistory implements MediaProviderSelectionHistory {
  private explicitSelection: boolean = false

  hasExplicitSelection(): boolean {
    return this.explicitSelection
  }

  markExplicitSelection(): void {
    this.explicitSelection = true
  }
}

export class MediaAccountSessionCoordinator {
  private readonly currentAccountStore: CurrentMediaAccountStore
  private readonly providerRegistry: MediaProviderRegistry
  private readonly feiniuSessionService: FeiniuVideoSessionPort
  private readonly feiniuContextRegistry: FeiniuVideoSessionContextPort
  private readonly jellyfinSessionSource: MediaProviderSessionSource
  private readonly selectionHistory: MediaProviderSelectionHistory

  constructor(currentAccountStore: CurrentMediaAccountStore,
    providerRegistry: MediaProviderRegistry,
    feiniuSessionService: FeiniuVideoSessionPort,
    feiniuContextRegistry: FeiniuVideoSessionContextPort,
    jellyfinSessionSource: MediaProviderSessionSource = new JellyfinActiveSessionSource(),
    selectionHistory: MediaProviderSelectionHistory =
      new VolatileMediaProviderSelectionHistory()) {
    this.currentAccountStore = currentAccountStore
    this.providerRegistry = providerRegistry
    this.feiniuSessionService = feiniuSessionService
    this.feiniuContextRegistry = feiniuContextRegistry
    this.jellyfinSessionSource = jellyfinSessionSource
    this.selectionHistory = selectionHistory
  }

  static async create(context: common.Context,
    sessionService?: FeiniuVideoSessionService,
    contextRegistry: FeiniuVideoSessionContextRegistry =
      FeiniuVideoSessionContextRegistry.instance()): Promise<MediaAccountSessionCoordinator> {
    const owningSessionService = sessionService || await FeiniuVideoSessionService.create(context)
    return MediaAccountSessionCoordinator.createWithPorts(
      context,
      new FeiniuVideoSessionServiceAdapter(owningSessionService),
      contextRegistry
    )
  }

  static createWithPorts(context: common.Context,
    feiniuSessionService: FeiniuVideoSessionPort,
    contextRegistry: FeiniuVideoSessionContextRegistry): MediaAccountSessionCoordinator {
    const coordinator = new MediaAccountSessionCoordinator(
      CurrentMediaAccountStore.create(context),
      MediaProviderRegistry.instance(),
      feiniuSessionService,
      new FeiniuVideoSessionContextRegistryAdapter(contextRegistry),
      new JellyfinActiveSessionSource(),
      MediaProviderSelectionHistoryStore.create(context)
    )
    if (coordinator.currentSelection()) {
      coordinator.selectionHistory.markExplicitSelection()
    }
    return coordinator
  }

  currentSelection(): CurrentMediaAccount | undefined {
    return this.currentAccountStore.load()
  }

  shouldRestoreLegacyJellyfin(): boolean {
    return this.currentSelection() === undefined &&
      !this.selectionHistory.hasExplicitSelection()
  }

  async restoreSelectedFeiniu(): Promise<FeiniuVideoSessionContext | undefined> {
    const selected = this.currentSelection()
    if (!selected || selected.provider !== MediaProviderKind.FeiniuVideo) {
      return undefined
    }
    return this.restoreFeiniu(selected.providerAccountId)
  }

  async restoreFeiniu(providerAccountId: string): Promise<FeiniuVideoSessionContext | undefined> {
    const restored = await this.feiniuSessionService.restore(providerAccountId)
    if (!restored) {
      return undefined
    }
    await this.activateFeiniuContext(restored)
    return restored
  }

  async clearInvalidFeiniuSelection(providerAccountId: string): Promise<void> {
    const normalizedId = providerAccountId.trim()
    if (normalizedId.length === 0) {
      return
    }
    this.feiniuContextRegistry.removeByProviderAccountId(normalizedId)
    const active = this.providerRegistry.getActiveSession()
    if (active?.provider === MediaProviderKind.FeiniuVideo &&
      active.providerAccountId === normalizedId) {
      await this.providerRegistry.clearActiveSession()
    }
    const selected = this.currentSelection()
    if (selected?.provider === MediaProviderKind.FeiniuVideo &&
      selected.providerAccountId === normalizedId) {
      this.currentAccountStore.clear()
    }
  }

  async activateFeiniuContext(context: FeiniuVideoSessionContext): Promise<void> {
    if (context.session.provider !== MediaProviderKind.FeiniuVideo) {
      throw new Error('Expected a Feiniu Video session')
    }
    const previousSession = this.providerRegistry.getActiveSession()
    const previousContext = previousSession ? this.feiniuContextRegistry.get(previousSession) : undefined
    this.feiniuContextRegistry.register(context)
    try {
      await this.providerRegistry.activateSession(context.session)
      this.savePointer(context.session)
    } catch (error) {
      this.feiniuContextRegistry.remove(context.session)
      await this.rollbackSession(previousSession)
      if (previousContext) {
        this.feiniuContextRegistry.register(previousContext)
      }
      throw error as Error
    }
  }

  async activateJellyfinSession(session: MediaProviderSession): Promise<void> {
    if (session.provider !== MediaProviderKind.Jellyfin) {
      throw new Error('Expected a Jellyfin session')
    }
    const previousSession = this.providerRegistry.getActiveSession()
    const previousContext = previousSession ? this.feiniuContextRegistry.get(previousSession) : undefined
    try {
      await this.providerRegistry.activateSession(session)
      this.savePointer(session)
    } catch (error) {
      await this.rollbackSession(previousSession)
      if (previousContext) {
        this.feiniuContextRegistry.register(previousContext)
      }
      throw error as Error
    }
  }

  async mirrorLegacyJellyfinSession(): Promise<MediaProviderSession | undefined> {
    const session = this.jellyfinSessionSource.currentSession()
    if (!session) {
      return undefined
    }
    await this.activateJellyfinSession(session)
    return session
  }

  async logoutFeiniu(providerAccountId: string): Promise<boolean> {
    const loggedOut = await this.feiniuSessionService.logout(providerAccountId)
    if (!loggedOut) {
      return false
    }
    await this.releaseDeletedFeiniu(providerAccountId)
    return true
  }

  async deleteFeiniu(providerAccountId: string): Promise<void> {
    this.feiniuSessionService.delete(providerAccountId)
    await this.releaseDeletedFeiniu(providerAccountId)
  }

  private savePointer(session: MediaProviderSession): void {
    const previous = this.currentSelection()
    this.currentAccountStore.save({
      provider: session.provider,
      providerAccountId: session.providerAccountId
    })
    try {
      this.selectionHistory.markExplicitSelection()
    } catch (error) {
      try {
        if (previous) {
          this.currentAccountStore.save(previous)
        } else {
          this.currentAccountStore.clear()
        }
      } catch (_rollbackError) {
      }
      throw error as Error
    }
  }

  private async releaseDeletedFeiniu(providerAccountId: string): Promise<void> {
    const normalizedId = providerAccountId.trim()
    this.feiniuContextRegistry.removeByProviderAccountId(normalizedId)
    const active = this.providerRegistry.getActiveSession()
    if (active?.provider === MediaProviderKind.FeiniuVideo &&
      active.providerAccountId === normalizedId) {
      await this.providerRegistry.clearActiveSession()
    }
    const selected = this.currentSelection()
    if (selected?.provider === MediaProviderKind.FeiniuVideo &&
      selected.providerAccountId === normalizedId) {
      this.currentAccountStore.clear()
    }
  }

  private async rollbackSession(previousSession?: MediaProviderSession): Promise<void> {
    if (previousSession) {
      await this.providerRegistry.activateSession(previousSession)
      return
    }
    await this.providerRegistry.clearActiveSession()
  }
}
