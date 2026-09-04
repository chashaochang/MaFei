import { describe, expect, it } from '@ohos/hypium'
import {
  CurrentMediaAccountBackend,
  CurrentMediaAccountStore
} from '../main/ets/media/account/CurrentMediaAccountStore'
import {
  MediaProviderSelectionHistory
} from '../main/ets/media/account/MediaProviderSelectionHistoryStore'
import { MediaProviderSessionSource } from
  '../main/ets/media/catalog/MediaCatalogProviderResolver'
import { MediaProviderKind } from '../main/ets/media/MediaProviderKind'
import {
  MediaProviderRegistry,
  MediaProviderScopedRequest
} from '../main/ets/media/MediaProviderRegistry'
import {
  MediaConnectionRoute,
  MediaProviderSession
} from '../main/ets/media/MediaProviderSession'
import {
  FeiniuVideoSessionContextPort,
  FeiniuVideoSessionPort,
  MediaAccountSessionCoordinator
} from '../main/ets/features/setting/account/MediaAccountSessionCoordinator'
import {
  classifyFeiniuRestoreStatus,
  MediaProviderRestoreStatus
} from '../main/ets/features/setting/account/MediaProviderComposition'
import {
  FeiniuVideoSessionContext
} from '../main/ets/features/feiniuvideo/account/FeiniuVideoSessionService'
import {
  FeiniuVideoSessionContextRegistry
} from '../main/ets/features/feiniuvideo/account/FeiniuVideoSessionContextRegistry'
import { FeiniuVideoApiClient } from
  '../main/ets/features/feiniuvideo/network/FeiniuVideoApiClient'
import {
  FeiniuVideoHostCandidate,
  FeiniuVideoHostResolution,
  FeiniuVideoRequestError,
  FeiniuVideoRequestFailureKind
} from '../main/ets/features/feiniuvideo/network/FeiniuVideoApiModels'

class FakeCurrentMediaAccountBackend implements CurrentMediaAccountBackend {
  value: string = ''
  failNextFlush: boolean = false

  read(): string {
    return this.value
  }

  write(value: string): void {
    this.value = value
  }

  clear(): void {
    this.value = ''
  }

  flush(): void {
    if (this.failNextFlush) {
      this.failNextFlush = false
      throw new Error('pointer flush failed')
    }
  }
}

class FakeSelectionHistory implements MediaProviderSelectionHistory {
  explicitSelection: boolean = false

  hasExplicitSelection(): boolean {
    return this.explicitSelection
  }

  markExplicitSelection(): void {
    this.explicitSelection = true
  }
}

class FakeJellyfinSessionSource implements MediaProviderSessionSource {
  session?: MediaProviderSession

  currentSession(): MediaProviderSession | undefined {
    return this.session
  }
}

class FakeFeiniuVideoSessionPort implements FeiniuVideoSessionPort {
  contexts: Map<string, FeiniuVideoSessionContext> = new Map()
  deletedIds: string[] = []
  logoutResult: boolean = true

  restore(providerAccountId: string): Promise<FeiniuVideoSessionContext | undefined> {
    return Promise.resolve(this.contexts.get(providerAccountId))
  }

  logout(providerAccountId: string): Promise<boolean> {
    if (this.logoutResult) {
      this.contexts.delete(providerAccountId)
    }
    return Promise.resolve(this.logoutResult)
  }

  delete(providerAccountId: string): void {
    this.deletedIds.push(providerAccountId)
    this.contexts.delete(providerAccountId)
  }
}

class FeiniuVideoSessionContextPortAdapter implements FeiniuVideoSessionContextPort {
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

class RecordingScopedRequest implements MediaProviderScopedRequest {
  private readonly registry: MediaProviderRegistry
  readonly cancelledScopes: string[] = []
  activeScopeAtCancellation: string = ''
  failNextCancellation: boolean = false

  constructor(registry: MediaProviderRegistry) {
    this.registry = registry
  }

  async cancelForScope(accountScope: string): Promise<void> {
    this.cancelledScopes.push(accountScope)
    this.activeScopeAtCancellation = this.registry.getActiveSession()?.accountScope || ''
    if (this.failNextCancellation) {
      this.failNextCancellation = false
      throw new Error('scope cleanup failed')
    }
  }
}

class CoordinatorFixture {
  readonly backend: FakeCurrentMediaAccountBackend
  readonly pointerStore: CurrentMediaAccountStore
  readonly registry: MediaProviderRegistry
  readonly service: FakeFeiniuVideoSessionPort
  readonly contexts: FeiniuVideoSessionContextRegistry
  readonly contextPort: FeiniuVideoSessionContextPortAdapter
  readonly jellyfinSource: FakeJellyfinSessionSource
  readonly history: FakeSelectionHistory
  readonly coordinator: MediaAccountSessionCoordinator

  constructor() {
    this.backend = new FakeCurrentMediaAccountBackend()
    this.pointerStore = new CurrentMediaAccountStore(this.backend)
    this.registry = new MediaProviderRegistry()
    this.service = new FakeFeiniuVideoSessionPort()
    this.contexts = new FeiniuVideoSessionContextRegistry(this.registry)
    this.contextPort = new FeiniuVideoSessionContextPortAdapter(this.contexts)
    this.jellyfinSource = new FakeJellyfinSessionSource()
    this.history = new FakeSelectionHistory()
    this.coordinator = new MediaAccountSessionCoordinator(
      this.pointerStore,
      this.registry,
      this.service,
      this.contextPort,
      this.jellyfinSource,
      this.history
    )
  }
}

function session(provider: MediaProviderKind, providerAccountId: string,
  baseUrl: string): MediaProviderSession {
  return new MediaProviderSession({
    provider: provider,
    providerAccountId: providerAccountId,
    userId: `${providerAccountId}-user`,
    displayName: `${providerAccountId} User`,
    serverName: `${providerAccountId} Server`,
    originalAddress: baseUrl,
    baseUrl: baseUrl,
    connectionRoute: MediaConnectionRoute.Direct
  })
}

function feiniuContext(owner: MediaProviderSession): FeiniuVideoSessionContext {
  const resolution = new FeiniuVideoHostResolution(
    new FeiniuVideoHostCandidate(owner.baseUrl, MediaConnectionRoute.Direct),
    []
  )
  return new FeiniuVideoSessionContext(owner, new FeiniuVideoApiClient(resolution))
}

async function caughtError(operation: () => Promise<void>): Promise<Error | undefined> {
  try {
    await operation()
  } catch (error) {
    return error as Error
  }
  return undefined
}

export default function mediaAccountSessionCoordinatorTest() {
  describe('MediaAccountSessionCoordinator', () => {
    it('switchesFeiniuToJellyfinAfterCancellingThePreviousScope', 0, async () => {
      const fixture = new CoordinatorFixture()
      const feiniu = session(MediaProviderKind.FeiniuVideo, 'feiniu-1', 'http://feiniu.local')
      const jellyfin = session(MediaProviderKind.Jellyfin, 'jellyfin-1', 'https://jellyfin.local')
      const context = feiniuContext(feiniu)
      const request = new RecordingScopedRequest(fixture.registry)

      await fixture.coordinator.activateFeiniuContext(context)
      fixture.registry.registerScopedRequest(feiniu.accountScope, request)
      await fixture.coordinator.activateJellyfinSession(jellyfin)

      expect(request.cancelledScopes.length).assertEqual(1)
      expect(request.cancelledScopes[0]).assertEqual(feiniu.accountScope)
      expect(request.activeScopeAtCancellation).assertEqual(feiniu.accountScope)
      expect(fixture.registry.getActiveSession()?.accountScope).assertEqual(jellyfin.accountScope)
      expect(fixture.contexts.get(feiniu) === undefined).assertTrue()
      expect(fixture.pointerStore.load()?.provider).assertEqual(MediaProviderKind.Jellyfin)
    })

    it('switchesJellyfinToFeiniuAndRegistersTheOwningContext', 0, async () => {
      const fixture = new CoordinatorFixture()
      const jellyfin = session(MediaProviderKind.Jellyfin, 'jellyfin-1', 'https://jellyfin.local')
      const feiniu = session(MediaProviderKind.FeiniuVideo, 'feiniu-1', 'http://feiniu.local')
      const context = feiniuContext(feiniu)

      await fixture.coordinator.activateJellyfinSession(jellyfin)
      await fixture.coordinator.activateFeiniuContext(context)

      expect(fixture.registry.getActiveSession()?.accountScope).assertEqual(feiniu.accountScope)
      expect(fixture.contexts.get(feiniu) === context).assertTrue()
      expect(fixture.pointerStore.load()?.provider).assertEqual(MediaProviderKind.FeiniuVideo)
      expect(fixture.pointerStore.load()?.providerAccountId).assertEqual('feiniu-1')
    })

    it('restoresThePreviousSessionAndContextWhenPointerPersistenceFails', 0, async () => {
      const fixture = new CoordinatorFixture()
      const previous = session(MediaProviderKind.FeiniuVideo, 'feiniu-1', 'http://first.local')
      const next = session(MediaProviderKind.FeiniuVideo, 'feiniu-2', 'http://second.local')
      const previousContext = feiniuContext(previous)

      await fixture.coordinator.activateFeiniuContext(previousContext)
      fixture.backend.failNextFlush = true
      const error = await caughtError(async (): Promise<void> => {
        await fixture.coordinator.activateFeiniuContext(feiniuContext(next))
      })

      expect(error?.message).assertEqual('Failed to persist current media account')
      expect(fixture.registry.getActiveSession()?.accountScope).assertEqual(previous.accountScope)
      expect(fixture.contexts.get(previous) === previousContext).assertTrue()
      expect(fixture.contexts.get(next) === undefined).assertTrue()
      expect(fixture.pointerStore.load()?.providerAccountId).assertEqual('feiniu-1')
    })

    it('restoresThePreviousSessionAndContextWhenScopeCleanupFails', 0, async () => {
      const fixture = new CoordinatorFixture()
      const previous = session(MediaProviderKind.FeiniuVideo, 'feiniu-1', 'http://first.local')
      const next = session(MediaProviderKind.FeiniuVideo, 'feiniu-2', 'http://second.local')
      const previousContext = feiniuContext(previous)
      const request = new RecordingScopedRequest(fixture.registry)
      request.failNextCancellation = true

      await fixture.coordinator.activateFeiniuContext(previousContext)
      fixture.registry.registerScopedRequest(previous.accountScope, request)
      const error = await caughtError(async (): Promise<void> => {
        await fixture.coordinator.activateFeiniuContext(feiniuContext(next))
      })

      expect(error?.message).assertEqual('scope cleanup failed')
      expect(fixture.registry.getActiveSession()?.accountScope).assertEqual(previous.accountScope)
      expect(fixture.contexts.get(previous) === previousContext).assertTrue()
      expect(fixture.contexts.get(next) === undefined).assertTrue()
      expect(fixture.pointerStore.load()?.providerAccountId).assertEqual('feiniu-1')
    })

    it('deletesOnlyTheCurrentFeiniuAccountAndClearsItsActiveState', 0, async () => {
      const fixture = new CoordinatorFixture()
      const current = session(MediaProviderKind.FeiniuVideo, 'feiniu-1', 'http://first.local')
      const other = session(MediaProviderKind.FeiniuVideo, 'feiniu-2', 'http://second.local')
      const currentContext = feiniuContext(current)
      const otherContext = feiniuContext(other)
      fixture.service.contexts.set(current.providerAccountId, currentContext)
      fixture.service.contexts.set(other.providerAccountId, otherContext)
      fixture.contexts.register(otherContext)

      await fixture.coordinator.activateFeiniuContext(currentContext)
      await fixture.coordinator.deleteFeiniu(current.providerAccountId)

      expect(fixture.service.deletedIds.length).assertEqual(1)
      expect(fixture.service.deletedIds[0]).assertEqual(current.providerAccountId)
      expect(fixture.service.contexts.has(other.providerAccountId)).assertTrue()
      expect(fixture.contexts.get(other) === otherContext).assertTrue()
      expect(fixture.registry.getActiveSession() === undefined).assertTrue()
      expect(fixture.pointerStore.load() === undefined).assertTrue()
    })

    it('restoresLegacyJellyfinOnlyBeforeAnExplicitProviderSelection', 0, async () => {
      const fixture = new CoordinatorFixture()
      const jellyfin = session(MediaProviderKind.Jellyfin, 'jellyfin-1', 'https://jellyfin.local')
      fixture.jellyfinSource.session = jellyfin

      expect(fixture.coordinator.shouldRestoreLegacyJellyfin()).assertTrue()
      const restored = await fixture.coordinator.mirrorLegacyJellyfinSession()

      expect(restored?.accountScope).assertEqual(jellyfin.accountScope)
      expect(fixture.registry.getActiveSession()?.accountScope).assertEqual(jellyfin.accountScope)
      expect(fixture.pointerStore.load()?.provider).assertEqual(MediaProviderKind.Jellyfin)
      expect(fixture.history.hasExplicitSelection()).assertTrue()
    })

    it('doesNotFallBackToLegacyJellyfinAfterFeiniuSelectionIsCleared', 0, async () => {
      const fixture = new CoordinatorFixture()
      const feiniu = session(MediaProviderKind.FeiniuVideo, 'feiniu-1', 'http://feiniu.local')

      await fixture.coordinator.activateFeiniuContext(feiniuContext(feiniu))
      await fixture.coordinator.clearInvalidFeiniuSelection(feiniu.providerAccountId)

      expect(fixture.pointerStore.load() === undefined).assertTrue()
      expect(fixture.history.hasExplicitSelection()).assertTrue()
      expect(fixture.coordinator.shouldRestoreLegacyJellyfin()).assertFalse()
    })

    it('classifiesMissingAuthenticationAndConnectionRestoreOutcomes', 0, () => {
      const authentication = new FeiniuVideoRequestError(
        FeiniuVideoRequestFailureKind.Authentication,
        'expired'
      )
      const connection = new FeiniuVideoRequestError(
        FeiniuVideoRequestFailureKind.Connection,
        'offline'
      )

      expect(classifyFeiniuRestoreStatus(false)).assertEqual(
        MediaProviderRestoreStatus.MissingLocalSession)
      expect(classifyFeiniuRestoreStatus(false, authentication)).assertEqual(
        MediaProviderRestoreStatus.AuthenticationExpired)
      expect(classifyFeiniuRestoreStatus(false, connection)).assertEqual(
        MediaProviderRestoreStatus.RetryableFailure)
    })

    it('returnsNoSessionWhenTheSelectedFeiniuRecordOrCredentialsAreMissing', 0, async () => {
      const fixture = new CoordinatorFixture()

      const restored = await fixture.coordinator.restoreFeiniu('missing-account')

      expect(restored === undefined).assertTrue()
      expect(fixture.registry.getActiveSession() === undefined).assertTrue()
      expect(fixture.pointerStore.load() === undefined).assertTrue()
    })
  })
}
