import { describe, expect, it } from '@ohos/hypium'
import {
  FeiniuVideoAccountBackend,
  FeiniuVideoAccountStore
} from '../main/ets/features/feiniuvideo/account/FeiniuVideoAccountStore'
import {
  createFeiniuVideoProviderAccountId,
  FeiniuVideoAccountRecord
} from '../main/ets/features/feiniuvideo/account/FeiniuVideoAccountModels'
import {
  createFeiniuVideoCredentialAlias,
  FeiniuVideoCredentialBackend,
  FeiniuVideoCredentialStore
} from '../main/ets/features/feiniuvideo/account/FeiniuVideoCredentialStore'
import {
  FeiniuVideoApiClientFactory,
  FeiniuVideoSessionClock,
  FeiniuVideoSessionService
} from '../main/ets/features/feiniuvideo/account/FeiniuVideoSessionService'
import { FeiniuVideoApiClient } from '../main/ets/features/feiniuvideo/network/FeiniuVideoApiClient'
import {
  FeiniuVideoApiCredentials,
  FeiniuVideoApiRequest,
  FeiniuVideoConnectionError,
  FeiniuVideoDigestSource,
  FeiniuVideoHostProbe,
  FeiniuVideoHostProbeResult,
  FeiniuVideoHostResolution,
  FeiniuVideoHttpMethod,
  FeiniuVideoHttpRequest,
  FeiniuVideoHttpResponse,
  FeiniuVideoHttpResponseHeaders,
  FeiniuVideoHttpTransport,
  FeiniuVideoRequestError,
  FeiniuVideoRequestFailureKind,
  FeiniuVideoRuntimeSource,
  MediaConnectionRoute
} from '../main/ets/features/feiniuvideo/network/FeiniuVideoApiModels'
import { FeiniuVideoHostResolver } from '../main/ets/features/feiniuvideo/network/FeiniuVideoHostResolver'
import { FnIdResolver } from '../main/ets/features/feiniuvideo/network/FnIdResolver'

class FakeSessionAccountBackend implements FeiniuVideoAccountBackend {
  records: Map<string, FeiniuVideoAccountRecord> = new Map()
  upsertCount: number = 0

  listRecords(): FeiniuVideoAccountRecord[] {
    return Array.from(this.records.values())
  }

  findRecord(providerAccountId: string): FeiniuVideoAccountRecord | undefined {
    return this.records.get(providerAccountId)
  }

  upsertRecord(record: FeiniuVideoAccountRecord): void {
    this.upsertCount += 1
    this.records.set(record.providerAccountId, record)
  }

  deleteRecord(providerAccountId: string): void {
    this.records.delete(providerAccountId)
  }
}

class FakeSessionCredentialBackend implements FeiniuVideoCredentialBackend {
  values: Map<string, string> = new Map()
  failWrite: boolean = false

  read(alias: string): string {
    return this.values.get(alias) || ''
  }

  write(alias: string, value: string): void {
    if (this.failWrite) {
      throw new Error('asset write failed')
    }
    this.values.set(alias, value)
  }

  remove(alias: string): void {
    this.values.delete(alias)
  }
}

class AlwaysReachableProbe implements FeiniuVideoHostProbe {
  async probe(_baseUrl: string): Promise<FeiniuVideoHostProbeResult> {
    return new FeiniuVideoHostProbeResult(true, 1)
  }
}

class FixedSessionRuntime implements FeiniuVideoRuntimeSource {
  now(): number {
    return 1700000000000
  }

  random(): number {
    return 0.5
  }
}

class FixedSessionDigest implements FeiniuVideoDigestSource {
  async md5(value: string): Promise<string> {
    return `md5(${value})`
  }

  async sha256(value: string): Promise<string> {
    return `sha256(${value})`
  }
}

class FixedSessionClock implements FeiniuVideoSessionClock {
  private readonly timestamp: number

  constructor(timestamp: number) {
    this.timestamp = timestamp
  }

  now(): number {
    return this.timestamp
  }
}

class SessionTransportStep {
  readonly response?: FeiniuVideoHttpResponse
  readonly error?: FeiniuVideoRequestError

  constructor(response?: FeiniuVideoHttpResponse, error?: FeiniuVideoRequestError) {
    this.response = response
    this.error = error
  }
}

class ScriptedSessionTransport implements FeiniuVideoHttpTransport {
  readonly steps: SessionTransportStep[]
  readonly requests: FeiniuVideoHttpRequest[] = []
  private index: number = 0

  constructor(steps: SessionTransportStep[]) {
    this.steps = steps.slice()
  }

  async request(request: FeiniuVideoHttpRequest): Promise<FeiniuVideoHttpResponse> {
    this.requests.push(request)
    const step = this.steps[this.index]
    this.index += 1
    if (step === undefined) {
      throw new Error('No session transport step remains')
    }
    if (step.error !== undefined) {
      throw step.error
    }
    if (step.response === undefined) {
      throw new Error('Session transport step has no response')
    }
    return step.response
  }
}

class QueueSessionApiClientFactory implements FeiniuVideoApiClientFactory {
  readonly transports: ScriptedSessionTransport[]
  readonly clients: FeiniuVideoApiClient[] = []
  createCount: number = 0

  constructor(transports: ScriptedSessionTransport[]) {
    this.transports = transports.slice()
  }

  create(resolution: FeiniuVideoHostResolution,
    credentials: FeiniuVideoApiCredentials): FeiniuVideoApiClient {
    const transport = this.transports[this.createCount]
    this.createCount += 1
    if (transport === undefined) {
      throw new Error('No API client transport remains')
    }
    const client = new FeiniuVideoApiClient(
      resolution,
      transport,
      credentials,
      new FixedSessionRuntime(),
      new FixedSessionDigest()
    )
    this.clients.push(client)
    return client
  }
}

function response(statusCode: number, bodyText: string,
  setCookieValues: string[] = []): FeiniuVideoHttpResponse {
  return new FeiniuVideoHttpResponse(
    statusCode,
    bodyText,
    new FeiniuVideoHttpResponseHeaders('', setCookieValues)
  )
}

function loginTransport(): ScriptedSessionTransport {
  return new ScriptedSessionTransport([
    new SessionTransportStep(response(200,
      '{"code":0,"msg":"ok","data":{"server_name":"Living Room NAS"}}')),
    new SessionTransportStep(response(200,
      '{"code":0,"msg":"ok","token":"new-token"}',
      ['route=lan; Path=/'])),
    new SessionTransportStep(response(200,
      '{"code":0,"msg":"ok","data":{"user_id":"u1","nickname":"Media User","username":"admin"}}'))
  ])
}

function accountRecord(address: string, userId: string, baseUrl: string,
  route: MediaConnectionRoute, fallbacks: string[] = []): FeiniuVideoAccountRecord {
  return new FeiniuVideoAccountRecord({
    userId: userId,
    displayName: `User ${userId}`,
    serverName: 'FNOS',
    originalAddress: address,
    baseUrl: baseUrl,
    connectionRoute: route,
    fallbackBaseUrls: fallbacks,
    lastUsedTimestamp: 10
  })
}

function service(accountBackend: FakeSessionAccountBackend,
  credentialBackend: FakeSessionCredentialBackend,
  factory: QueueSessionApiClientFactory,
  timestamp: number = 100): FeiniuVideoSessionService {
  return new FeiniuVideoSessionService(
    new FeiniuVideoAccountStore(accountBackend),
    new FeiniuVideoCredentialStore(credentialBackend),
    new FnIdResolver(),
    new FeiniuVideoHostResolver(new AlwaysReachableProbe()),
    factory,
    new FixedSessionClock(timestamp)
  )
}

async function loginError(sessionService: FeiniuVideoSessionService): Promise<Error | undefined> {
  try {
    await sessionService.login('https://nas.example:5667', 'admin', 'secret-password')
  } catch (error) {
    return error as Error
  }
  return undefined
}

async function logoutError(sessionService: FeiniuVideoSessionService,
  providerAccountId: string): Promise<Error | undefined> {
  try {
    await sessionService.logout(providerAccountId)
  } catch (error) {
    return error as Error
  }
  return undefined
}

export default function feiniuVideoSessionServiceTest() {
  describe('FeiniuVideoSessionService', () => {
    it('logsInValidatesTheUserAndPersistsOnlyScopedMetadataAndCredentials', 0, async () => {
      const accountBackend = new FakeSessionAccountBackend()
      const credentialBackend = new FakeSessionCredentialBackend()
      const transport = loginTransport()
      const factory = new QueueSessionApiClientFactory([transport])
      const sessionService = service(accountBackend, credentialBackend, factory, 123)

      const context = await sessionService.login(
        ' https://nas.example:5667/ ',
        ' admin ',
        'secret-password'
      )

      const providerAccountId = createFeiniuVideoProviderAccountId(
        'https://nas.example:5667/', 'u1')
      const stored = accountBackend.records.get(providerAccountId) as FeiniuVideoAccountRecord
      const rawCredentials = credentialBackend.values.get(
        createFeiniuVideoCredentialAlias(providerAccountId)) || ''
      expect(stored.userId).assertEqual('u1')
      expect(stored.displayName).assertEqual('Media User')
      expect(stored.serverName).assertEqual('Living Room NAS')
      expect(stored.baseUrl).assertEqual('https://nas.example:5667')
      expect(stored.lastUsedTimestamp).assertEqual(123)
      expect(rawCredentials.includes('new-token')).assertTrue()
      expect(rawCredentials.includes('route=lan')).assertTrue()
      expect(rawCredentials.includes('secret-password')).assertFalse()
      expect(JSON.stringify(stored.toValuesBucket()).includes('secret-password')).assertFalse()
      expect(JSON.stringify(context.session).includes('new-token')).assertFalse()
      expect(JSON.stringify(context.session).includes('cookie')).assertFalse()
      expect(context.apiClient.getCredentials().token).assertEqual('new-token')
      expect(transport.requests.length).assertEqual(3)
      expect(transport.requests[0].url.endsWith('/v/api/v1/sys/config')).assertTrue()
      expect(transport.requests[1].url.endsWith('/v/api/v1/login')).assertTrue()
      expect(transport.requests[2].url.endsWith('/v/api/v1/user/info')).assertTrue()
    })

    it('keepsLoginSuccessfulWhenOptionalUserInfoIsUnavailable', 0, async () => {
      const accountBackend = new FakeSessionAccountBackend()
      const credentialBackend = new FakeSessionCredentialBackend()
      const transport = new ScriptedSessionTransport([
        new SessionTransportStep(response(200,
          '{"code":0,"msg":"ok","data":{"server_name":"Living Room NAS"}}')),
        new SessionTransportStep(response(200,
          '{"code":0,"msg":"ok","token":"new-token"}',
          ['route=lan; Path=/'])),
        new SessionTransportStep(undefined, new FeiniuVideoRequestError(
          FeiniuVideoRequestFailureKind.InvalidResponse,
          'user info is not available on this server'
        ))
      ])
      const sessionService = service(
        accountBackend,
        credentialBackend,
        new QueueSessionApiClientFactory([transport]),
        124
      )

      const context = await sessionService.login(
        'https://nas.example:5667',
        'admin',
        'secret-password'
      )

      const providerAccountId = createFeiniuVideoProviderAccountId(
        'https://nas.example:5667', 'admin')
      const stored = accountBackend.records.get(providerAccountId) as FeiniuVideoAccountRecord
      expect(stored.userId).assertEqual('admin')
      expect(stored.displayName).assertEqual('admin')
      expect(stored.serverName).assertEqual('Living Room NAS')
      expect(context.apiClient.getCredentials().token).assertEqual('new-token')
      expect(transport.requests.length).assertEqual(3)
    })

    it('loadsAColdSessionAndPromotesAWorkingFallbackAfterValidation', 0, async () => {
      const accountBackend = new FakeSessionAccountBackend()
      const credentialBackend = new FakeSessionCredentialBackend()
      const record = accountRecord(
        'fn-one',
        'u1',
        'http://192.168.1.8:5666',
        MediaConnectionRoute.Local,
        ['https://public.example:5667']
      )
      accountBackend.upsertRecord(record)
      const credentialStore = new FeiniuVideoCredentialStore(credentialBackend)
      credentialStore.save(record.providerAccountId, {
        token: 'stored-token',
        cookie: 'route=relay'
      })

      const loadFactory = new QueueSessionApiClientFactory([
        new ScriptedSessionTransport([])
      ])
      const loaded = service(accountBackend, credentialBackend, loadFactory).load(record.providerAccountId)
      expect(loaded?.session.baseUrl).assertEqual('http://192.168.1.8:5666')
      expect(loaded?.apiClient.getCredentials().token).assertEqual('stored-token')
      expect(JSON.stringify(loaded?.session).includes('stored-token')).assertFalse()

      const restoreTransport = new ScriptedSessionTransport([
        new SessionTransportStep(undefined, new FeiniuVideoConnectionError('LAN offline')),
        new SessionTransportStep(response(200,
          '{"code":0,"msg":"ok","data":{"user_id":"u1","nickname":"Restored User"}}',
          ['route=public; Path=/', 'refresh=rotated; Path=/']))
      ])
      const restoreFactory = new QueueSessionApiClientFactory([restoreTransport])
      const restored = await service(
        accountBackend,
        credentialBackend,
        restoreFactory,
        456
      ).restore(record.providerAccountId)

      const refreshed = accountBackend.records.get(record.providerAccountId) as FeiniuVideoAccountRecord
      expect(restored?.session.baseUrl).assertEqual('https://public.example:5667')
      expect(refreshed.baseUrl).assertEqual('https://public.example:5667')
      expect(refreshed.connectionRoute).assertEqual(MediaConnectionRoute.Public)
      expect(refreshed.fallbackBaseUrls[0]).assertEqual('http://192.168.1.8:5666')
      expect(refreshed.displayName).assertEqual('Restored User')
      expect(refreshed.lastUsedTimestamp).assertEqual(456)
      expect(credentialStore.load(record.providerAccountId)?.cookie.includes('route=public')).assertTrue()
      expect(credentialStore.load(record.providerAccountId)?.cookie.includes('refresh=rotated')).assertTrue()
      expect(restoreTransport.requests.length).assertEqual(2)
    })

    it('writesBackPromotedRoutesAndRotatedCredentialsOnlyOncePerSnapshot', 0, async () => {
      const accountBackend = new FakeSessionAccountBackend()
      const credentialBackend = new FakeSessionCredentialBackend()
      const record = accountRecord(
        'fn-one',
        'u1',
        'http://192.168.1.8:5666',
        MediaConnectionRoute.Local,
        ['https://public.example:5667']
      )
      accountBackend.upsertRecord(record)
      const credentialStore = new FeiniuVideoCredentialStore(credentialBackend)
      credentialStore.save(record.providerAccountId, {
        token: 'stored-token',
        cookie: 'route=lan'
      })
      const transport = new ScriptedSessionTransport([
        new SessionTransportStep(undefined, new FeiniuVideoConnectionError('LAN offline')),
        new SessionTransportStep(response(200,
          '{"code":0,"msg":"ok","data":{"total":0,"items":[]}}',
          ['route=public; Path=/', 'refresh=rotated; Path=/']))
      ])
      const sessionService = service(
        accountBackend,
        credentialBackend,
        new QueueSessionApiClientFactory([transport]),
        789
      )
      const context = sessionService.load(record.providerAccountId)
      expect(context !== undefined).assertTrue()
      accountBackend.upsertCount = 0

      await context?.apiClient.request<Object>(new FeiniuVideoApiRequest(
        '/v/api/v1/mediadb/list',
        FeiniuVideoHttpMethod.Get,
        '',
        [],
        true
      ))
      context?.syncSnapshot()

      const refreshed = accountBackend.records.get(record.providerAccountId) as FeiniuVideoAccountRecord
      const refreshedCredentials = credentialStore.load(record.providerAccountId)
      expect(refreshed.baseUrl).assertEqual('https://public.example:5667')
      expect(refreshed.connectionRoute).assertEqual(MediaConnectionRoute.Public)
      expect(refreshed.fallbackBaseUrls[0]).assertEqual('http://192.168.1.8:5666')
      expect(refreshed.lastUsedTimestamp).assertEqual(789)
      expect(refreshedCredentials?.token).assertEqual('stored-token')
      expect(refreshedCredentials?.cookie.includes('route=public')).assertTrue()
      expect(refreshedCredentials?.cookie.includes('refresh=rotated')).assertTrue()
      expect(accountBackend.upsertCount).assertEqual(1)

      context?.syncSnapshot()
      expect(accountBackend.upsertCount).assertEqual(1)
      expect(transport.requests.length).assertEqual(2)
    })

    it('returnsEmptyWhenEitherAccountMetadataOrCredentialsAreMissing', 0, async () => {
      const accountBackend = new FakeSessionAccountBackend()
      const credentialBackend = new FakeSessionCredentialBackend()
      const record = accountRecord(
        'fn-one', 'u1', 'https://relay.fnos.net', MediaConnectionRoute.Relay)
      accountBackend.upsertRecord(record)
      const factory = new QueueSessionApiClientFactory([])
      const sessionService = service(accountBackend, credentialBackend, factory)

      expect(sessionService.load('missing') === undefined).assertTrue()
      expect(sessionService.load(record.providerAccountId) === undefined).assertTrue()
      expect(await sessionService.restore(record.providerAccountId) === undefined).assertTrue()
      expect(factory.createCount).assertEqual(0)
    })

    it('removesOnlyTheLoggedOutAccountAfterRemoteLogoutSucceeds', 0, async () => {
      const accountBackend = new FakeSessionAccountBackend()
      const credentialBackend = new FakeSessionCredentialBackend()
      const first = accountRecord('fn-one', 'u1', 'https://one.fnos.net', MediaConnectionRoute.Relay)
      const second = accountRecord('fn-two', 'u2', 'https://two.fnos.net', MediaConnectionRoute.Relay)
      accountBackend.upsertRecord(first)
      accountBackend.upsertRecord(second)
      const credentialStore = new FeiniuVideoCredentialStore(credentialBackend)
      credentialStore.save(first.providerAccountId, { token: 'token-one', cookie: '' })
      credentialStore.save(second.providerAccountId, { token: 'token-two', cookie: '' })
      const logoutTransport = new ScriptedSessionTransport([
        new SessionTransportStep(response(200, '{"code":0,"msg":"ok"}'))
      ])
      const sessionService = service(
        accountBackend,
        credentialBackend,
        new QueueSessionApiClientFactory([logoutTransport])
      )

      expect(await sessionService.logout(first.providerAccountId)).assertTrue()
      expect(accountBackend.records.has(first.providerAccountId)).assertFalse()
      expect(accountBackend.records.has(second.providerAccountId)).assertTrue()
      expect(credentialStore.load(first.providerAccountId) === undefined).assertTrue()
      expect(credentialStore.load(second.providerAccountId)?.token).assertEqual('token-two')
    })

    it('retainsLocalAccountAndCredentialsWhenRemoteLogoutFails', 0, async () => {
      const accountBackend = new FakeSessionAccountBackend()
      const credentialBackend = new FakeSessionCredentialBackend()
      const record = accountRecord('fn-one', 'u1', 'https://one.fnos.net', MediaConnectionRoute.Relay)
      accountBackend.upsertRecord(record)
      const credentialStore = new FeiniuVideoCredentialStore(credentialBackend)
      credentialStore.save(record.providerAccountId, { token: 'token-one', cookie: '' })
      const logoutTransport = new ScriptedSessionTransport([
        new SessionTransportStep(undefined, new FeiniuVideoConnectionError('offline'))
      ])
      const sessionService = service(
        accountBackend,
        credentialBackend,
        new QueueSessionApiClientFactory([logoutTransport])
      )

      const error = await logoutError(sessionService, record.providerAccountId)

      expect(error?.message).assertEqual('offline')
      expect(accountBackend.records.has(record.providerAccountId)).assertTrue()
      expect(credentialStore.load(record.providerAccountId)?.token).assertEqual('token-one')
    })

    it('deletesOnlyTheSelectedLocalAccountWithoutRequiringRemoteLogout', 0, () => {
      const accountBackend = new FakeSessionAccountBackend()
      const credentialBackend = new FakeSessionCredentialBackend()
      const first = accountRecord('fn-one', 'u1', 'https://one.fnos.net', MediaConnectionRoute.Relay)
      const second = accountRecord('fn-two', 'u2', 'https://two.fnos.net', MediaConnectionRoute.Relay)
      accountBackend.upsertRecord(first)
      accountBackend.upsertRecord(second)
      const credentialStore = new FeiniuVideoCredentialStore(credentialBackend)
      credentialStore.save(first.providerAccountId, { token: 'token-one', cookie: '' })
      credentialStore.save(second.providerAccountId, { token: 'token-two', cookie: '' })
      const factory = new QueueSessionApiClientFactory([])
      const sessionService = service(accountBackend, credentialBackend, factory)

      sessionService.delete(first.providerAccountId)

      expect(accountBackend.records.has(first.providerAccountId)).assertFalse()
      expect(accountBackend.records.has(second.providerAccountId)).assertTrue()
      expect(credentialStore.load(first.providerAccountId) === undefined).assertTrue()
      expect(credentialStore.load(second.providerAccountId)?.token).assertEqual('token-two')
      expect(factory.createCount).assertEqual(0)
    })

    it('rollsBackAccountMetadataWhenCredentialPersistenceFails', 0, async () => {
      const accountBackend = new FakeSessionAccountBackend()
      const credentialBackend = new FakeSessionCredentialBackend()
      credentialBackend.failWrite = true
      const sessionService = service(
        accountBackend,
        credentialBackend,
        new QueueSessionApiClientFactory([loginTransport()])
      )

      const error = await loginError(sessionService)

      expect(error?.message).assertEqual('asset write failed')
      expect(accountBackend.records.size).assertEqual(0)
      expect(credentialBackend.values.size).assertEqual(0)
    })
  })
}
