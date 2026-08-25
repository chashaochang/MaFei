import { common } from '@kit.AbilityKit'
import { hilog } from '@kit.PerformanceAnalysisKit'
import { MediaConnectionRoute, MediaProviderSession } from '../../../media/MediaProviderSession'
import { FeiniuVideoAccountRecord } from './FeiniuVideoAccountModels'
import { FeiniuVideoAccountStore } from './FeiniuVideoAccountStore'
import {
  FeiniuVideoCredentials,
  FeiniuVideoCredentialStore
} from './FeiniuVideoCredentialStore'
import {
  FeiniuVideoApiClient,
  FeiniuVideoFnIdResolveSource
} from '../network/FeiniuVideoApiClient'
import {
  FeiniuVideoApiCredentials,
  FeiniuVideoHostCandidate,
  FeiniuVideoHostResolution,
  FeiniuVideoRequestError,
  FeiniuVideoRequestFailureKind,
  FnMediaSysConfigResp,
  FnMediaUserInfoData,
  FnMediaUserInfoResp,
  FnMediaUserSource,
  isFeiniuVideoConnectionFailure
} from '../network/FeiniuVideoApiModels'
import {
  FeiniuVideoAddressResolutionSource,
  FeiniuVideoHostResolutionSource
} from '../network/FeiniuVideoResolutionSource'
import { FeiniuVideoHostResolver } from '../network/FeiniuVideoHostResolver'
import { FnIdResolver } from '../network/FnIdResolver'
import {
  FeiniuVideoUpstreamAddressResolver,
  FeiniuVideoUpstreamHostResolver
} from '../network/FeiniuVideoUpstreamHostResolution'
import { FnMediaAuthStore as UpstreamFnMediaAuthStore } from '../upstream/common/FnMediaAuthStore'
import { PreferencesUtil as UpstreamPreferencesUtil } from '../upstream/common/PreferencesUtil'
import { SessionStore as UpstreamSessionStore } from '../upstream/common/SessionStore'

export interface FeiniuVideoApiClientFactory {
  create(resolution: FeiniuVideoHostResolution,
    credentials: FeiniuVideoApiCredentials): FeiniuVideoApiClient
}

export interface FeiniuVideoSessionClock {
  now(): number
}

export interface FeiniuVideoSessionSnapshotWriter {
  write(session: MediaProviderSession, apiClient: FeiniuVideoApiClient): void
}

export interface FeiniuVideoSessionSnapshotSync {
  syncSnapshot(): void
}

class DefaultFeiniuVideoApiClientFactory implements FeiniuVideoApiClientFactory {
  create(resolution: FeiniuVideoHostResolution,
    credentials: FeiniuVideoApiCredentials): FeiniuVideoApiClient {
    return FeiniuVideoApiClient.createWithUpstreamAuth(resolution, credentials)
  }
}

class SystemFeiniuVideoSessionClock implements FeiniuVideoSessionClock {
  now(): number {
    return Date.now()
  }
}

/**
 * Keeps the neutral session free of secrets while giving provider adapters an
 * API client whose token/cookie snapshot has already been restored.
 */
export class FeiniuVideoSessionContext implements FeiniuVideoSessionSnapshotSync {
  readonly session: MediaProviderSession
  readonly apiClient: FeiniuVideoApiClient
  private readonly snapshotWriter?: FeiniuVideoSessionSnapshotWriter
  private lastSnapshotKey: string
  private syncingSnapshot: boolean = false
  private snapshotSyncPending: boolean = false

  constructor(session: MediaProviderSession, apiClient: FeiniuVideoApiClient,
    snapshotWriter?: FeiniuVideoSessionSnapshotWriter) {
    this.session = session
    this.apiClient = apiClient
    this.snapshotWriter = snapshotWriter
    this.lastSnapshotKey = this.snapshotKey()
  }

  syncSnapshot(): void {
    if (!this.snapshotWriter) {
      return
    }
    if (this.syncingSnapshot) {
      this.snapshotSyncPending = true
      return
    }
    do {
      this.snapshotSyncPending = false
      const nextKey = this.snapshotKey()
      if (nextKey === this.lastSnapshotKey) {
        continue
      }
      this.syncingSnapshot = true
      try {
        this.snapshotWriter.write(this.session, this.apiClient)
        this.lastSnapshotKey = this.snapshotKey()
      } finally {
        this.syncingSnapshot = false
      }
    } while (this.snapshotSyncPending)
  }

  private snapshotKey(): string {
    const resolution = this.apiClient.getResolution()
    const credentials = this.apiClient.getCredentials()
    const candidates = resolution.orderedCandidates.map((candidate: FeiniuVideoHostCandidate): string => {
      return `${candidate.route}:${candidate.baseUrl}`
    }).join('|')
    return `${candidates}\n${credentials.token}\n${credentials.cookie}`
  }
}

/**
 * Account-scoped replacement for the copied FnMediaAuthStore. Token/cookie
 * snapshot semantics remain in FeiniuVideoCredentialStore and ApiClient, while
 * route/UI/global Jellyfin state is deliberately excluded.
 */
export class FeiniuVideoSessionService implements FeiniuVideoSessionSnapshotWriter {
  private readonly accountStore: FeiniuVideoAccountStore
  private readonly credentialStore: FeiniuVideoCredentialStore
  private readonly fnIdResolver: FeiniuVideoAddressResolutionSource
  private readonly hostResolver: FeiniuVideoHostResolutionSource
  private readonly apiClientFactory: FeiniuVideoApiClientFactory
  private readonly clock: FeiniuVideoSessionClock

  constructor(accountStore: FeiniuVideoAccountStore,
    credentialStore: FeiniuVideoCredentialStore,
    fnIdResolver: FeiniuVideoAddressResolutionSource = new FnIdResolver(new FeiniuVideoFnIdResolveSource()),
    hostResolver: FeiniuVideoHostResolutionSource = new FeiniuVideoHostResolver(),
    apiClientFactory: FeiniuVideoApiClientFactory = new DefaultFeiniuVideoApiClientFactory(),
    clock: FeiniuVideoSessionClock = new SystemFeiniuVideoSessionClock()) {
    this.accountStore = accountStore
    this.credentialStore = credentialStore
    this.fnIdResolver = fnIdResolver
    this.hostResolver = hostResolver
    this.apiClientFactory = apiClientFactory
    this.clock = clock
  }

  static async create(context: common.Context): Promise<FeiniuVideoSessionService> {
    let stage = 'initialize_upstream_store'
    try {
      UpstreamPreferencesUtil.init(
        context as common.UIAbilityContext,
        'mafei_feiniu_video_preferences'
      )
      stage = 'create_account_store'
      const accountStore = await FeiniuVideoAccountStore.create(context)
      stage = 'create_credential_store'
      const credentialStore = FeiniuVideoCredentialStore.create()
      return new FeiniuVideoSessionService(
        accountStore,
        credentialStore,
        new FeiniuVideoUpstreamAddressResolver(),
        new FeiniuVideoUpstreamHostResolver()
      )
    } catch (error) {
      const normalizedError = error as Error
      hilog.error(0x0000, 'mafei-feiniu-login',
        'session initialization failed stage=%{public}s type=%{public}s',
        stage, normalizedError.name || 'Error')
      throw normalizedError
    }
  }

  async login(address: string, username: string, password: string): Promise<FeiniuVideoSessionContext> {
    let stage = 'validate_input'
    try {
      const normalizedUsername = username.trim()
      if (normalizedUsername.length === 0) {
        throw new FeiniuVideoRequestError(
          FeiniuVideoRequestFailureKind.InvalidRequest,
          'Feiniu Video username is required'
        )
      }

      stage = 'resolve_address'
      const addressResolution = await this.fnIdResolver.resolve(address)
      stage = 'resolve_host'
      const hostResolution = await this.hostResolver.resolve(addressResolution.candidates)
      this.prepareUpstreamSession(
        hostResolution,
        addressResolution.input.originalAddress,
        normalizedUsername,
        new FeiniuVideoApiCredentials()
      )
      const apiClient = this.apiClientFactory.create(hostResolution, new FeiniuVideoApiCredentials())
      stage = 'load_config'
      const config = await apiClient.getSysConfig()
      stage = 'authenticate'
      await apiClient.login(normalizedUsername, password)
      stage = 'load_user'
      const userData = await this.loadOptionalUserData(apiClient)
      const credentials = apiClient.getCredentials()
      if (credentials.token.length === 0 && credentials.cookie.length === 0) {
        throw new FeiniuVideoRequestError(
          FeiniuVideoRequestFailureKind.InvalidResponse,
          'Feiniu Video login did not produce a credential snapshot'
        )
      }

      stage = 'build_account'
      const userId = userData ? this.loginUserId(userData, normalizedUsername) : normalizedUsername
      const finalResolution = apiClient.getResolution()
      const record = new FeiniuVideoAccountRecord({
        userId: userId,
        displayName: userData ? this.firstNonEmpty([
          userData.nickname || '',
          userData.username || '',
          normalizedUsername
        ]) : normalizedUsername,
        serverName: this.serverName(config, finalResolution.preferredBaseUrl),
        originalAddress: addressResolution.input.originalAddress,
        baseUrl: finalResolution.preferredBaseUrl,
        connectionRoute: finalResolution.preferredRoute,
        fallbackBaseUrls: finalResolution.fallbackBaseUrls,
        lastUsedTimestamp: this.clock.now()
      })
      stage = 'persist_account'
      this.persistSnapshot(record, credentials)
      return this.createContext(record, apiClient)
    } catch (error) {
      const normalizedError = error as Error
      const kind = normalizedError instanceof FeiniuVideoRequestError
        ? normalizedError.kind
        : FeiniuVideoRequestFailureKind.Unknown
      hilog.error(0x0000, 'mafei-feiniu-login',
        'login failed stage=%{public}s kind=%{public}s type=%{public}s',
        stage, kind, normalizedError.name || 'Error')
      throw normalizedError
    }
  }

  load(providerAccountId: string): FeiniuVideoSessionContext | undefined {
    const normalizedId = providerAccountId.trim()
    if (normalizedId.length === 0) {
      return undefined
    }
    const record = this.accountStore.findRecord(normalizedId)
    if (record === undefined) {
      return undefined
    }
    const credentials = this.credentialStore.load(normalizedId)
    if (credentials === undefined) {
      return undefined
    }
    const resolution = this.resolutionFromRecord(record)
    const apiCredentials = new FeiniuVideoApiCredentials(credentials.token, credentials.cookie)
    this.prepareUpstreamSession(resolution, record.originalAddress, record.displayName, apiCredentials)
    const apiClient = this.apiClientFactory.create(resolution, apiCredentials)
    return this.createContext(record, apiClient)
  }

  async restore(providerAccountId: string): Promise<FeiniuVideoSessionContext | undefined> {
    const loaded = this.load(providerAccountId)
    if (loaded === undefined) {
      return undefined
    }
    const record = this.accountStore.findRecord(loaded.session.providerAccountId)
    if (record === undefined) {
      return undefined
    }

    const latestResolution = await this.resolveLatestRoute(record)
    if (latestResolution !== undefined) {
      this.applyResolvedRoute(loaded, record, latestResolution)
    }

    let userInfo: FnMediaUserInfoResp
    try {
      userInfo = await loaded.apiClient.getUserInfo()
    } catch (error) {
      const normalizedError = error as Error
      if (!isFeiniuVideoConnectionFailure(normalizedError)) {
        throw normalizedError
      }
      const refreshedResolution = await this.resolveLatestRoute(record)
      if (refreshedResolution === undefined) {
        throw normalizedError
      }
      this.applyResolvedRoute(loaded, record, refreshedResolution)
      userInfo = await loaded.apiClient.getUserInfo()
    }

    const userData = this.requireUserData(userInfo)
    const resolution = loaded.apiClient.getResolution()
    const refreshedRecord = new FeiniuVideoAccountRecord({
      providerAccountId: record.providerAccountId,
      userId: record.userId,
      displayName: this.firstNonEmpty([
        userData.nickname || '',
        userData.username || '',
        record.displayName
      ]),
      serverName: record.serverName,
      originalAddress: record.originalAddress,
      baseUrl: resolution.preferredBaseUrl,
      connectionRoute: resolution.preferredRoute,
      fallbackBaseUrls: resolution.fallbackBaseUrls,
      lastUsedTimestamp: this.clock.now()
    })
    this.persistSnapshot(refreshedRecord, loaded.apiClient.getCredentials())
    return this.createContext(refreshedRecord, loaded.apiClient)
  }

  async logout(providerAccountId: string): Promise<boolean> {
    const loaded = this.load(providerAccountId)
    if (loaded === undefined) {
      return false
    }
    const loggedOut = await loaded.apiClient.logout()
    if (!loggedOut) {
      return false
    }
    this.delete(providerAccountId)
    return true
  }

  delete(providerAccountId: string): void {
    const normalizedId = providerAccountId.trim()
    if (normalizedId.length === 0) {
      return
    }
    let failure: Error | undefined
    try {
      this.credentialStore.clear(normalizedId)
    } catch (error) {
      failure = error as Error
    }
    try {
      this.accountStore.delete(normalizedId)
    } catch (error) {
      if (failure === undefined) {
        failure = error as Error
      }
    }
    if (failure !== undefined) {
      throw failure
    }
  }

  write(session: MediaProviderSession, apiClient: FeiniuVideoApiClient): void {
    const record = this.accountStore.findRecord(session.providerAccountId)
    if (record === undefined || !record.toProviderAccount().toSession().isSameAccount(session)) {
      return
    }
    const resolution = apiClient.getResolution()
    const refreshedRecord = new FeiniuVideoAccountRecord({
      providerAccountId: record.providerAccountId,
      userId: record.userId,
      displayName: record.displayName,
      serverName: record.serverName,
      originalAddress: record.originalAddress,
      baseUrl: resolution.preferredBaseUrl,
      connectionRoute: resolution.preferredRoute,
      fallbackBaseUrls: resolution.fallbackBaseUrls,
      lastUsedTimestamp: this.clock.now()
    })
    this.persistSnapshot(refreshedRecord, apiClient.getCredentials())
  }

  private persistSnapshot(record: FeiniuVideoAccountRecord,
    credentials: FeiniuVideoApiCredentials): void {
    const previousRecord = this.accountStore.findRecord(record.providerAccountId)
    const previousCredentials = this.credentialStore.load(record.providerAccountId)
    this.accountStore.save(record)
    try {
      this.credentialStore.save(record.providerAccountId, {
        token: credentials.token,
        cookie: credentials.cookie
      })
    } catch (error) {
      this.rollbackAccount(record.providerAccountId, previousRecord)
      this.rollbackCredentials(record.providerAccountId, previousCredentials)
      throw error as Error
    }
  }

  private rollbackAccount(providerAccountId: string,
    previousRecord?: FeiniuVideoAccountRecord): void {
    try {
      if (previousRecord !== undefined) {
        this.accountStore.save(previousRecord)
      } else {
        this.accountStore.delete(providerAccountId)
      }
    } catch (_error) {
    }
  }

  private rollbackCredentials(providerAccountId: string,
    previousCredentials?: FeiniuVideoCredentials): void {
    try {
      if (previousCredentials !== undefined) {
        this.credentialStore.save(providerAccountId, previousCredentials)
      } else {
        this.credentialStore.clear(providerAccountId)
      }
    } catch (_error) {
    }
  }

  private createContext(record: FeiniuVideoAccountRecord,
    apiClient: FeiniuVideoApiClient): FeiniuVideoSessionContext {
    return new FeiniuVideoSessionContext(record.toProviderAccount().toSession(), apiClient, this)
  }

  /**
   * Re-resolve a FNID whenever a stored session is restored.  A saved route is
   * only a last-known-good fallback: connection preference and reachability may
   * have changed while the application was not running.
   */
  private async resolveLatestRoute(record: FeiniuVideoAccountRecord):
    Promise<FeiniuVideoHostResolution | undefined> {
    try {
      const refreshedAddress = await this.fnIdResolver.resolve(record.originalAddress)
      return await this.hostResolver.resolve(refreshedAddress.candidates)
    } catch (error) {
      const normalizedError = error as Error
      hilog.warn(0x0000, 'mafei-feiniu-login',
        'route refresh failed; keeping last known route. type=%{public}s',
        normalizedError.name || 'Error')
      return undefined
    }
  }

  private applyResolvedRoute(context: FeiniuVideoSessionContext,
    record: FeiniuVideoAccountRecord, resolution: FeiniuVideoHostResolution): void {
    context.apiClient.setResolution(resolution)
    this.prepareUpstreamSession(
      resolution,
      record.originalAddress,
      record.displayName,
      context.apiClient.getCredentials()
    )
  }

  private prepareUpstreamSession(resolution: FeiniuVideoHostResolution, originalAddress: string,
    username: string, credentials: FeiniuVideoApiCredentials): void {
    const baseUrl = resolution.preferredBaseUrl
    UpstreamSessionStore.saveLoginForm(
      baseUrl,
      username,
      '',
      false,
      baseUrl.startsWith('https://'),
      originalAddress
    )
    UpstreamSessionStore.saveConnectionRoute(this.upstreamRoute(resolution.preferredRoute))
    UpstreamFnMediaAuthStore.clearSession()
    if (credentials.token.length > 0) {
      UpstreamFnMediaAuthStore.saveToken(credentials.token)
    }
    if (credentials.cookie.length > 0) {
      UpstreamFnMediaAuthStore.saveCookie(credentials.cookie)
    }
  }

  private upstreamRoute(route: MediaConnectionRoute): string {
    if (route === MediaConnectionRoute.Local || route === MediaConnectionRoute.Direct) {
      return 'lan'
    }
    if (route === MediaConnectionRoute.Public) {
      return 'public'
    }
    return 'relay'
  }

  private resolutionFromRecord(record: FeiniuVideoAccountRecord): FeiniuVideoHostResolution {
    const preferred = new FeiniuVideoHostCandidate(
      record.baseUrl,
      this.routeFromStoredValue(record.connectionRoute, record.baseUrl)
    )
    const fallbacks: FeiniuVideoHostCandidate[] = []
    record.fallbackBaseUrls.forEach((baseUrl: string) => {
      const normalized = baseUrl.trim().replace(/\/+$/, '')
      if (normalized.length > 0 && normalized !== preferred.baseUrl &&
        fallbacks.find((candidate: FeiniuVideoHostCandidate): boolean =>
          candidate.baseUrl === normalized) === undefined) {
        fallbacks.push(new FeiniuVideoHostCandidate(normalized, this.inferRoute(normalized)))
      }
    })
    return new FeiniuVideoHostResolution(preferred, fallbacks)
  }

  private routeFromStoredValue(value: string, baseUrl: string): MediaConnectionRoute {
    if (value === MediaConnectionRoute.Direct) {
      return MediaConnectionRoute.Direct
    }
    if (value === MediaConnectionRoute.Local) {
      return MediaConnectionRoute.Local
    }
    if (value === MediaConnectionRoute.Public) {
      return MediaConnectionRoute.Public
    }
    if (value === MediaConnectionRoute.Relay) {
      return MediaConnectionRoute.Relay
    }
    return this.inferRoute(baseUrl)
  }

  private inferRoute(baseUrl: string): MediaConnectionRoute {
    const normalized = baseUrl.trim().toLowerCase()
    if (normalized.indexOf('.fnos.net') >= 0 || normalized.indexOf('.5ddd.com') >= 0) {
      return MediaConnectionRoute.Relay
    }
    const authority = normalized.replace(/^https?:\/\//, '').split('/')[0]
    const host = authority.startsWith('[') ? authority : authority.split(':')[0]
    if (host === 'localhost' || host.startsWith('127.') || host.startsWith('10.') ||
      host.startsWith('192.168.') || this.isPrivate172Address(host)) {
      return MediaConnectionRoute.Local
    }
    return MediaConnectionRoute.Public
  }

  private isPrivate172Address(host: string): boolean {
    if (!host.startsWith('172.')) {
      return false
    }
    const parts = host.split('.')
    if (parts.length < 2) {
      return false
    }
    const secondPart = parseInt(parts[1], 10)
    return secondPart >= 16 && secondPart <= 31
  }

  private async loadOptionalUserData(apiClient: FeiniuVideoApiClient): Promise<FnMediaUserInfoData | undefined> {
    try {
      const response: FnMediaUserInfoResp = await apiClient.getUserInfo()
      return response.data
    } catch (error) {
      const normalizedError = error as Error
      if (normalizedError instanceof FeiniuVideoRequestError &&
        (normalizedError.kind === FeiniuVideoRequestFailureKind.Business ||
          normalizedError.kind === FeiniuVideoRequestFailureKind.InvalidResponse)) {
        return undefined
      }
      throw normalizedError
    }
  }

  private requireUserData(response: FnMediaUserInfoResp): FnMediaUserInfoData {
    if (response.data === undefined) {
      throw new FeiniuVideoRequestError(
        FeiniuVideoRequestFailureKind.InvalidResponse,
        'Feiniu Video user validation returned no user data'
      )
    }
    return response.data
  }

  private loginUserId(data: FnMediaUserInfoData, fallbackUsername: string): string {
    return this.firstNonEmpty([
      data.user_id || '',
      this.firstSourceId(data.sources),
      data.username || '',
      fallbackUsername
    ])
  }

  private firstSourceId(sources?: FnMediaUserSource[]): string {
    if (sources === undefined) {
      return ''
    }
    for (let index = 0; index < sources.length; index += 1) {
      const sourceId = sources[index].source_id?.trim() || ''
      if (sourceId.length > 0) {
        return sourceId
      }
    }
    return ''
  }

  private serverName(config: FnMediaSysConfigResp, fallbackBaseUrl: string): string {
    return this.firstNonEmpty([
      config.data?.server_name || '',
      fallbackBaseUrl
    ])
  }

  private firstNonEmpty(values: string[]): string {
    for (let index = 0; index < values.length; index += 1) {
      const normalized = values[index].trim()
      if (normalized.length > 0) {
        return normalized
      }
    }
    return ''
  }
}
