import { common } from '@kit.AbilityKit'
import { hilog } from '@kit.PerformanceAnalysisKit'
import { FeiniuVideoSessionContextRegistry } from
  '../../feiniuvideo/account/FeiniuVideoSessionContextRegistry'
import {
  FeiniuVideoSessionContext,
  FeiniuVideoSessionService
} from '../../feiniuvideo/account/FeiniuVideoSessionService'
import { registerFeiniuVideoCatalogProvider } from
  '../../feiniuvideo/catalog/FeiniuVideoCatalogRegistration'
import { registerFeiniuVideoPlaybackProvider } from
  '../../feiniuvideo/playback/FeiniuVideoPlaybackRegistration'
import {
  FeiniuVideoRequestError,
  FeiniuVideoRequestFailureKind
} from '../../feiniuvideo/network/FeiniuVideoApiModels'
import { MediaProviderKind } from '../../../media/MediaProviderKind'
import { MediaProviderRegistry } from '../../../media/MediaProviderRegistry'
import {
  FeiniuVideoSessionPort,
  MediaAccountSessionCoordinator
} from './MediaAccountSessionCoordinator'

class UnavailableFeiniuVideoSessionPort implements FeiniuVideoSessionPort {
  private readonly failure: Error

  constructor(failure: Error) {
    this.failure = failure
  }

  restore(_providerAccountId: string): Promise<FeiniuVideoSessionContext | undefined> {
    return Promise.reject(this.failure)
  }

  logout(_providerAccountId: string): Promise<boolean> {
    return Promise.reject(this.failure)
  }

  delete(_providerAccountId: string): void {
    throw this.failure
  }
}

export enum MediaProviderRestoreStatus {
  NotSelected = 'not_selected',
  Restored = 'restored',
  MissingLocalSession = 'missing_local_session',
  AuthenticationExpired = 'authentication_expired',
  RetryableFailure = 'retryable_failure'
}

export function classifyFeiniuRestoreStatus(restored: boolean,
  error?: Object): MediaProviderRestoreStatus {
  if (error) {
    if (error instanceof FeiniuVideoRequestError &&
      error.kind === FeiniuVideoRequestFailureKind.Authentication) {
      return MediaProviderRestoreStatus.AuthenticationExpired
    }
    return MediaProviderRestoreStatus.RetryableFailure
  }
  return restored ? MediaProviderRestoreStatus.Restored :
    MediaProviderRestoreStatus.MissingLocalSession
}

/**
 * Application integration boundary for provider-owned factories and services.
 * Neutral media modules stay unaware of concrete Feiniu implementations.
 */
export class MediaProviderComposition {
  private static shared?: MediaProviderComposition
  private static initialization?: Promise<MediaProviderComposition>
  private readonly sessionService?: FeiniuVideoSessionService
  private readonly sessionCoordinator: MediaAccountSessionCoordinator
  private readonly initializationFailure?: Error

  private constructor(sessionService: FeiniuVideoSessionService | undefined,
    sessionCoordinator: MediaAccountSessionCoordinator,
    initializationFailure?: Error) {
    this.sessionService = sessionService
    this.sessionCoordinator = sessionCoordinator
    this.initializationFailure = initializationFailure
  }

  static async initialize(context: common.Context): Promise<MediaProviderComposition> {
    if (MediaProviderComposition.shared?.isFeiniuAvailable()) {
      return MediaProviderComposition.shared
    }
    if (!MediaProviderComposition.initialization) {
      MediaProviderComposition.initialization = MediaProviderComposition.create(context)
        .catch((error: Error): MediaProviderComposition => {
          return MediaProviderComposition.createUnavailable(context, error)
        })
    }
    try {
      const composition = await MediaProviderComposition.initialization
      if (composition.isFeiniuAvailable()) {
        MediaProviderComposition.shared = composition
      }
      return composition
    } finally {
      MediaProviderComposition.initialization = undefined
    }
  }

  coordinator(): MediaAccountSessionCoordinator {
    return this.sessionCoordinator
  }

  isFeiniuAvailable(): boolean {
    return this.sessionService !== undefined
  }

  feiniuInitializationFailureType(): string {
    return this.initializationFailure?.name || 'Error'
  }

  selectedProvider(): MediaProviderKind | undefined {
    return this.sessionCoordinator.currentSelection()?.provider
  }

  shouldRestoreLegacyJellyfin(): boolean {
    return this.sessionCoordinator.shouldRestoreLegacyJellyfin()
  }

  hasActiveSelectedSession(): boolean {
    const selected = this.sessionCoordinator.currentSelection()
    const active = MediaProviderRegistry.instance().getActiveSession()
    return selected !== undefined && active !== undefined &&
      selected.provider === active.provider &&
      selected.providerAccountId === active.providerAccountId
  }

  async restoreSelectedFeiniu(): Promise<MediaProviderRestoreStatus> {
    const selected = this.sessionCoordinator.currentSelection()
    if (!selected || selected.provider !== MediaProviderKind.FeiniuVideo) {
      return MediaProviderRestoreStatus.NotSelected
    }
    try {
      const restored = await this.sessionCoordinator.restoreSelectedFeiniu()
      const status = classifyFeiniuRestoreStatus(restored !== undefined)
      if (status === MediaProviderRestoreStatus.MissingLocalSession) {
        await this.sessionCoordinator.clearInvalidFeiniuSelection(selected.providerAccountId)
      }
      return status
    } catch (error) {
      const status = classifyFeiniuRestoreStatus(false, error)
      if (status === MediaProviderRestoreStatus.AuthenticationExpired) {
        await this.sessionCoordinator.clearInvalidFeiniuSelection(selected.providerAccountId)
      }
      return status
    }
  }

  async clearSelectedFeiniu(): Promise<void> {
    const selected = this.sessionCoordinator.currentSelection()
    if (selected?.provider === MediaProviderKind.FeiniuVideo) {
      await this.sessionCoordinator.clearInvalidFeiniuSelection(selected.providerAccountId)
    }
  }

  async mirrorLegacyJellyfinSession(): Promise<boolean> {
    return await this.sessionCoordinator.mirrorLegacyJellyfinSession() !== undefined
  }

  async loginFeiniu(address: string, username: string, password: string): Promise<void> {
    const sessionService = this.sessionService
    if (!sessionService) {
      if (this.initializationFailure) {
        throw this.initializationFailure
      }
      throw new Error('Feiniu Video provider is unavailable')
    }
    const context: FeiniuVideoSessionContext =
      await sessionService.login(address, username, password)
    await this.sessionCoordinator.activateFeiniuContext(context)
  }

  private static async create(context: common.Context): Promise<MediaProviderComposition> {
    let stage = 'register_providers'
    try {
      const contextRegistry = FeiniuVideoSessionContextRegistry.instance()
      registerFeiniuVideoCatalogProvider(contextRegistry)
      registerFeiniuVideoPlaybackProvider(contextRegistry)
      stage = 'create_session_service'
      const sessionService = await FeiniuVideoSessionService.create(context)
      stage = 'create_coordinator'
      const coordinator = await MediaAccountSessionCoordinator.create(
        context,
        sessionService,
        contextRegistry
      )
      return new MediaProviderComposition(sessionService, coordinator)
    } catch (error) {
      const normalizedError = error as Error
      hilog.error(0x0000, 'mafei-feiniu-login',
        'provider initialization failed stage=%{public}s type=%{public}s',
        stage, normalizedError.name || 'Error')
      throw normalizedError
    }
  }

  private static createUnavailable(context: common.Context,
    error: Error): MediaProviderComposition {
    const contextRegistry = FeiniuVideoSessionContextRegistry.instance()
    const coordinator = MediaAccountSessionCoordinator.createWithPorts(
      context,
      new UnavailableFeiniuVideoSessionPort(error),
      contextRegistry
    )
    return new MediaProviderComposition(undefined, coordinator, error)
  }
}
