import { describe, expect, it } from '@ohos/hypium'
import {
  FeiniuVideoApiClient,
  FeiniuVideoCryptoDigestSource,
  FeiniuVideoFnIdResolveSource,
  FeiniuVideoNetworkHttpTransport,
  FeiniuVideoRedirectInterceptor
} from '../main/ets/features/feiniuvideo/network/FeiniuVideoApiClient'
import {
  FeiniuVideoApiCredentials,
  FeiniuVideoApiRequest,
  FeiniuVideoAuthenticationError,
  FeiniuVideoConnectionError,
  FeiniuVideoDigestSource,
  FeiniuVideoHostCandidate,
  FeiniuVideoHostResolution,
  FeiniuVideoHttpMethod,
  FeiniuVideoHttpRequest,
  FeiniuVideoHttpResponse,
  FeiniuVideoHttpResponseHeaders,
  FeiniuVideoHttpTransport,
  FeiniuVideoQueryParameter,
  FeiniuVideoRequestError,
  FeiniuVideoRequestFailureKind,
  FeiniuVideoRuntimeSource,
  MediaConnectionRoute
} from '../main/ets/features/feiniuvideo/network/FeiniuVideoApiModels'

class ScriptedRuntimeSource implements FeiniuVideoRuntimeSource {
  times: number[]
  randomValue: number
  private timeIndex: number = 0

  constructor(times: number[], randomValue: number = 0) {
    this.times = times.slice()
    this.randomValue = randomValue
  }

  now(): number {
    if (this.times.length === 0) {
      return 0
    }
    const index = Math.min(this.timeIndex, this.times.length - 1)
    this.timeIndex += 1
    return this.times[index]
  }

  random(): number {
    return this.randomValue
  }
}

class PrefixDigestSource implements FeiniuVideoDigestSource {
  md5Calls: string[] = []
  sha256Calls: string[] = []

  async md5(value: string): Promise<string> {
    this.md5Calls.push(value)
    return `md5(${value})`
  }

  async sha256(value: string): Promise<string> {
    this.sha256Calls.push(value)
    return `sha256(${value})`
  }
}

class FakeTransportStep {
  readonly response?: FeiniuVideoHttpResponse
  readonly error?: FeiniuVideoRequestError

  constructor(response?: FeiniuVideoHttpResponse, error?: FeiniuVideoRequestError) {
    this.response = response
    this.error = error
  }
}

class FakeHttpTransport implements FeiniuVideoHttpTransport {
  readonly steps: FakeTransportStep[]
  readonly requests: FeiniuVideoHttpRequest[] = []
  private index: number = 0

  constructor(steps: FakeTransportStep[]) {
    this.steps = steps.slice()
  }

  async request(request: FeiniuVideoHttpRequest): Promise<FeiniuVideoHttpResponse> {
    this.requests.push(request)
    const step = this.steps[this.index]
    this.index += 1
    if (step === undefined) {
      throw new Error('No fake transport step remains')
    }
    if (step.error !== undefined) {
      throw step.error
    }
    if (step.response === undefined) {
      throw new Error('Fake transport step has no response')
    }
    return step.response
  }
}

class NativeHttpError extends Error {
  readonly code: number

  constructor(code: number, message: string) {
    super(message)
    this.code = code
  }
}

class ConcurrentLoginTransport implements FeiniuVideoHttpTransport {
  readonly requests: FeiniuVideoHttpRequest[] = []
  private rejectFirst?: (error: FeiniuVideoRequestError) => void

  async request(request: FeiniuVideoHttpRequest): Promise<FeiniuVideoHttpResponse> {
    this.requests.push(request)
    if (this.requests.length === 1) {
      return await new Promise<FeiniuVideoHttpResponse>((_resolve, reject) => {
        this.rejectFirst = (error: FeiniuVideoRequestError): void => reject(error)
      })
    }
    return response(200, '{"code":0,"msg":"ok","token":"new-token"}', '', [
      'session=new-session; Path=/'
    ])
  }

  failFirst(): void {
    if (this.rejectFirst !== undefined) {
      this.rejectFirst(new FeiniuVideoRequestError(FeiniuVideoRequestFailureKind.Unknown, 'first login failed'))
    }
  }
}

function response(statusCode: number, bodyText: string, location: string = '',
  setCookieValues: string[] = []): FeiniuVideoHttpResponse {
  return new FeiniuVideoHttpResponse(
    statusCode,
    bodyText,
    new FeiniuVideoHttpResponseHeaders(location, setCookieValues)
  )
}

function candidate(baseUrl: string, route: MediaConnectionRoute): FeiniuVideoHostCandidate {
  return new FeiniuVideoHostCandidate(baseUrl, route)
}

function resolution(preferred: FeiniuVideoHostCandidate,
  fallbacks: FeiniuVideoHostCandidate[] = []): FeiniuVideoHostResolution {
  return new FeiniuVideoHostResolution(preferred, fallbacks)
}

function client(transport: FeiniuVideoHttpTransport,
  credentials: FeiniuVideoApiCredentials = new FeiniuVideoApiCredentials(),
  preferred: FeiniuVideoHostCandidate = candidate('http://lan:5666', MediaConnectionRoute.Local),
  fallbacks: FeiniuVideoHostCandidate[] = [],
  runtime: FeiniuVideoRuntimeSource = new ScriptedRuntimeSource([1000]),
  digest: FeiniuVideoDigestSource = new PrefixDigestSource()): FeiniuVideoApiClient {
  return new FeiniuVideoApiClient(
    resolution(preferred, fallbacks),
    transport,
    credentials,
    runtime,
    digest
  )
}

async function fnIdError(source: FeiniuVideoFnIdResolveSource): Promise<FeiniuVideoRequestError | undefined> {
  try {
    await source.resolve('fn123')
  } catch (error) {
    return error as FeiniuVideoRequestError
  }
  return undefined
}

async function loginError(api: FeiniuVideoApiClient): Promise<FeiniuVideoRequestError | undefined> {
  try {
    await api.login('user', 'password')
  } catch (error) {
    return error as FeiniuVideoRequestError
  }
  return undefined
}

async function userInfoError(api: FeiniuVideoApiClient): Promise<FeiniuVideoRequestError | undefined> {
  try {
    await api.getUserInfo()
  } catch (error) {
    return error as FeiniuVideoRequestError
  }
  return undefined
}

async function logoutError(api: FeiniuVideoApiClient): Promise<FeiniuVideoRequestError | undefined> {
  try {
    await api.logout()
  } catch (error) {
    return error as FeiniuVideoRequestError
  }
  return undefined
}

export default function feiniuVideoApiClientTest() {
  describe('FeiniuVideoApiClient', () => {
    it('computesTheReferenceMd5AndSha256Digests', 0, async () => {
      const digest = new FeiniuVideoCryptoDigestSource()

      expect(await digest.md5('abc')).assertEqual('900150983cd24fb0d6963f7d28e17f72')
      expect(await digest.sha256('abc')).assertEqual(
        'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad')
    })

    it('usesTheReferenceFnIdPostAndSigningMaterial', 0, async () => {
      const transport = new FakeHttpTransport([
        new FakeTransportStep(response(200,
          '{"code":0,"msg":"ok","data":{"ipv4":["192.168.1.8"]}}'))
      ])
      const runtime = new ScriptedRuntimeSource([1700000000000, 1700000000001], 0.5)
      const digest = new PrefixDigestSource()
      const source = new FeiniuVideoFnIdResolveSource(transport, runtime, digest)

      const result = await source.resolve(' fn123 ')

      expect(result.code).assertEqual(0)
      expect(transport.requests.length).assertEqual(1)
      const request = transport.requests[0]
      expect(request.url).assertEqual('https://5ddd.com/api/v1/fn/con')
      expect(request.method).assertEqual(FeiniuVideoHttpMethod.Post)
      expect(request.bodyText).assertEqual('{"fnId":"fn123"}')
      expect(request.headers.fnSign).assertEqual(
        'sha256(trim_connect`fn123`1700000000000`anna)')
      const bodyMd5 = 'md5({"fnId":"fn123"})'
      const signText = 'NDzZTVxnRKP8Z0jXg1VAMonaG8akvh_/api/v1/fn/con_550000_1700000000001_' +
        `${bodyMd5}_zIGtkc3dqZnJpd29qZXJqa2w7c`
      expect(request.headers.authx).assertEqual(
        `nonce=550000&timestamp=1700000000001&sign=md5(${signText})`)
    })

    it('classifiesFnIdConnectionAuthenticationAndInvalidResponses', 0, async () => {
      const connectionSource = new FeiniuVideoFnIdResolveSource(new FakeHttpTransport([
        new FakeTransportStep(undefined, new FeiniuVideoConnectionError('offline'))
      ]), new ScriptedRuntimeSource([1, 2]), new PrefixDigestSource())
      const authSource = new FeiniuVideoFnIdResolveSource(new FakeHttpTransport([
        new FakeTransportStep(response(403, '{}'))
      ]), new ScriptedRuntimeSource([1, 2]), new PrefixDigestSource())
      const invalidSource = new FeiniuVideoFnIdResolveSource(new FakeHttpTransport([
        new FakeTransportStep(response(200, '<html>bad</html>'))
      ]), new ScriptedRuntimeSource([1, 2]), new PrefixDigestSource())

      const connectionError = await fnIdError(connectionSource) as FeiniuVideoRequestError
      const authError = await fnIdError(authSource) as FeiniuVideoRequestError
      const invalidError = await fnIdError(invalidSource) as FeiniuVideoRequestError

      expect(connectionError.kind).assertEqual(FeiniuVideoRequestFailureKind.Connection)
      expect(authError.kind).assertEqual(FeiniuVideoRequestFailureKind.Authentication)
      expect(invalidError.kind).assertEqual(FeiniuVideoRequestFailureKind.InvalidResponse)
    })

    it('logsInWithoutSendingOrRetainingOldCredentials', 0, async () => {
      const transport = new FakeHttpTransport([
        new FakeTransportStep(response(200, '{"code":0,"msg":"ok","token":"new-token"}', '', [
          'session=new-session; Path=/, theme=dark; Expires=Wed, 21 Oct 2026 07:28:00 GMT; Path=/'
        ]))
      ])
      const api = client(
        transport,
        new FeiniuVideoApiCredentials('old-token', 'old-cookie=1; Trim-MC-token=old-token')
      )

      await api.login(' user ', 'password')

      expect(transport.requests[0].headers.authorization).assertEqual('')
      expect(transport.requests[0].headers.cookie).assertEqual('')
      expect(transport.requests[0].bodyText).assertEqual(
        '{"username":"user","password":"password","app_name":"trimemedia-web"}')
      const credentials = api.getCredentials()
      expect(credentials.token).assertEqual('new-token')
      expect(credentials.cookie.indexOf('session=new-session') >= 0).assertTrue()
      expect(credentials.cookie.indexOf('theme=dark') >= 0).assertTrue()
      expect(credentials.cookie.indexOf('Trim-MC-token=new-token') >= 0).assertTrue()
      expect(credentials.cookie.indexOf('old-cookie') < 0).assertTrue()
    })

    it('rejectsTokenlessLoginAndRestoresThePreviousCredentials', 0, async () => {
      const transport = new FakeHttpTransport([
        new FakeTransportStep(response(200, '{"code":0,"msg":"ok","data":{}}'))
      ])
      const api = client(
        transport,
        new FeiniuVideoApiCredentials('old-token', 'session=old; Trim-MC-token=old-token')
      )

      const error = await loginError(api) as FeiniuVideoRequestError

      expect(error.kind).assertEqual(FeiniuVideoRequestFailureKind.InvalidResponse)
      expect(api.getCredentials().token).assertEqual('old-token')
      expect(api.getCredentials().cookie.indexOf('session=old') >= 0).assertTrue()
    })

    it('sendsTokenCookieAndRelayModeForAuthenticatedRequests', 0, async () => {
      const transport = new FakeHttpTransport([
        new FakeTransportStep(response(200,
          '{"code":0,"msg":"ok","data":{"user_id":"u1","nickname":"User"}}',
          '', ['rotated=1; Path=/']))
      ])
      const api = client(
        transport,
        new FeiniuVideoApiCredentials('media-token', 'session=abc'),
        candidate('https://fn123.fnos.net', MediaConnectionRoute.Relay)
      )

      const result = await api.getUserInfo()

      expect(result.data?.user_id).assertEqual('u1')
      expect(transport.requests[0].headers.authorization).assertEqual('media-token')
      const requestCookie = transport.requests[0].headers.cookie
      expect(requestCookie.indexOf('session=abc') >= 0).assertTrue()
      expect(requestCookie.indexOf('Trim-MC-token=media-token') >= 0).assertTrue()
      expect(requestCookie.indexOf('mode=relay') >= 0).assertTrue()
      expect(api.getCredentials().cookie.indexOf('rotated=1') >= 0).assertTrue()
    })

    it('fallsBackAndPromotesOnlyAfterAnExplicitConnectionFailure', 0, async () => {
      const transport = new FakeHttpTransport([
        new FakeTransportStep(undefined, new FeiniuVideoConnectionError('lan offline')),
        new FakeTransportStep(response(200,
          '{"code":0,"msg":"ok","data":{"user_id":"u1"}}'))
      ])
      const api = client(
        transport,
        new FeiniuVideoApiCredentials('token', ''),
        candidate('http://lan:5666', MediaConnectionRoute.Local),
        [candidate('https://public:5667', MediaConnectionRoute.Public)]
      )

      await api.getUserInfo()

      expect(transport.requests.length).assertEqual(2)
      expect(transport.requests[0].url).assertEqual('http://lan:5666/v/api/v1/user/info')
      expect(transport.requests[1].url).assertEqual('https://public:5667/v/api/v1/user/info')
      expect(api.getActiveCandidate().route).assertEqual(MediaConnectionRoute.Public)
    })

    it('doesNotFallbackForBusinessOrExpiredAuthenticationAndRetriesInvalidResponses', 0, async () => {
      const businessTransport = new FakeHttpTransport([
        new FakeTransportStep(response(200, '{"code":900,"msg":"rejected"}'))
      ])
      const invalidTransport = new FakeHttpTransport([
        new FakeTransportStep(response(200, '<html>bad</html>')),
        new FakeTransportStep(response(200, '<html>bad</html>'))
      ])
      const expiredTransport = new FakeHttpTransport([
        new FakeTransportStep(response(200, '{"code":100401,"msg":"token expired"}'))
      ])
      const fallback = [candidate('https://public:5667', MediaConnectionRoute.Public)]
      const credentials = new FeiniuVideoApiCredentials('token', '')
      const businessApi = client(businessTransport, credentials,
        candidate('http://lan:5666', MediaConnectionRoute.Local), fallback)
      const invalidApi = client(invalidTransport, credentials,
        candidate('http://lan:5666', MediaConnectionRoute.Local), fallback)
      const expiredApi = client(expiredTransport, credentials,
        candidate('http://lan:5666', MediaConnectionRoute.Local), fallback)

      const businessError = await userInfoError(businessApi) as FeiniuVideoRequestError
      const invalidError = await userInfoError(invalidApi) as FeiniuVideoRequestError
      const expiredError = await userInfoError(expiredApi) as FeiniuVideoRequestError

      expect(businessError.kind).assertEqual(FeiniuVideoRequestFailureKind.Business)
      expect(invalidError.kind).assertEqual(FeiniuVideoRequestFailureKind.InvalidResponse)
      expect(expiredError.kind).assertEqual(FeiniuVideoRequestFailureKind.Authentication)
      expect(expiredError instanceof FeiniuVideoAuthenticationError).assertTrue()
      expect((expiredError as FeiniuVideoAuthenticationError).expired).assertTrue()
      expect(businessTransport.requests.length).assertEqual(1)
      expect(invalidTransport.requests.length).assertEqual(2)
      expect(expiredTransport.requests.length).assertEqual(1)
    })

    it('normalizesBothNetworkKitCookieSourcesAndStopsAutomaticRedirects', 0, async () => {
      const networkTransport = new FeiniuVideoNetworkHttpTransport()
      const normalized = networkTransport.normalizeResponseHeadersJson(
        '{"Location":"/next","set-cookie":["header-cookie=2; Path=/","shared=header; Path=/"]}',
        'response-cookie=1; Path=/, shared=response; Path=/'
      )

      expect(normalized.location).assertEqual('/next')
      expect(normalized.setCookieValues.length).assertEqual(3)
      const transport = new FakeHttpTransport([
        new FakeTransportStep(new FeiniuVideoHttpResponse(
          200,
          '{"code":0,"msg":"ok","token":"new-token"}',
          normalized
        ))
      ])
      const api = client(transport)
      await api.login('user', 'password')

      const cookie = api.getCredentials().cookie
      expect(cookie.indexOf('response-cookie=1') >= 0).assertTrue()
      expect(cookie.indexOf('header-cookie=2') >= 0).assertTrue()
      expect(cookie.indexOf('shared=header') >= 0).assertTrue()
      const interceptor = new FeiniuVideoRedirectInterceptor()
      expect(`${interceptor.interceptorType}`).assertEqual('REDIRECTION')
      expect(interceptor.shouldContinue()).assertFalse()
    })

    it('allowsHostFallbackOnlyForKnownNetworkKitConnectionErrors', 0, () => {
      const transport = new FeiniuVideoNetworkHttpTransport()
      const connectionError = transport.mapNativeError(
        new NativeHttpError(2300007, 'connect failed')
      )
      const certificateError = transport.mapNativeError(
        new NativeHttpError(2300060, 'certificate rejected')
      )
      const genericError = transport.mapNativeError(new Error('unexpected failure'))

      expect(connectionError.kind).assertEqual(FeiniuVideoRequestFailureKind.Connection)
      expect(connectionError.allowsHostFallback()).assertTrue()
      expect(certificateError.kind).assertEqual(FeiniuVideoRequestFailureKind.Unknown)
      expect(certificateError.allowsHostFallback()).assertFalse()
      expect(genericError.kind).assertEqual(FeiniuVideoRequestFailureKind.Unknown)
      expect(genericError.allowsHostFallback()).assertFalse()
    })

    it('classifiesLoginEnvelopeAuthenticationWithoutClaimingTokenExpiry', 0, async () => {
      const codeTransport = new FakeHttpTransport([
        new FakeTransportStep(response(200, '{"code":401,"msg":"用户名或密码错误"}'))
      ])
      const messageTransport = new FakeHttpTransport([
        new FakeTransportStep(response(200, '{"code":900,"msg":"unauthorized"}'))
      ])

      const codeError = await loginError(client(codeTransport)) as FeiniuVideoRequestError
      const messageError = await loginError(client(messageTransport)) as FeiniuVideoRequestError

      expect(codeError instanceof FeiniuVideoAuthenticationError).assertTrue()
      expect((codeError as FeiniuVideoAuthenticationError).expired).assertFalse()
      expect(messageError instanceof FeiniuVideoAuthenticationError).assertTrue()
      expect((messageError as FeiniuVideoAuthenticationError).expired).assertFalse()
    })

    it('usesLocationPathAndQueryAndConvertsOnly303ToGet', 0, async () => {
      const transport = new FakeHttpTransport([
        new FakeTransportStep(response(
          303,
          '',
          '/v/api/v1/landing?z=2&a=one',
          ['same-origin=1; Path=/']
        )),
        new FakeTransportStep(response(200, '{"code":0,"msg":"ok"}'))
      ])
      const api = client(transport)

      await api.request<Object>(new FeiniuVideoApiRequest(
        '/v/api/v1/start',
        FeiniuVideoHttpMethod.Post,
        '{"secret":"value"}'
      ))

      expect(transport.requests.length).assertEqual(2)
      expect(transport.requests[1].url).assertEqual(
        'http://lan:5666/v/api/v1/landing?z=2&a=one')
      expect(transport.requests[1].method).assertEqual(FeiniuVideoHttpMethod.Get)
      expect(transport.requests[1].bodyText).assertEqual('')
      expect(transport.requests[1].headers.cookie.indexOf('same-origin=1') >= 0).assertTrue()
    })

    it('rejectsUnsafeRedirectTargetsBeforeForwardingLoginSecrets', 0, async () => {
      const userInfoTransport = new FakeHttpTransport([
        new FakeTransportStep(response(302, '', 'https://user:pass@server:5667/steal'))
      ])
      const lookalikeTransport = new FakeHttpTransport([
        new FakeTransportStep(response(302, '', 'https://server.fnos.net.evil/steal'))
      ])
      const downgradeTransport = new FakeHttpTransport([
        new FakeTransportStep(response(302, '', 'http://server:5667/steal'))
      ])
      const preferred = candidate('https://server:5667', MediaConnectionRoute.Direct)

      const userInfoError = await loginError(client(userInfoTransport,
        new FeiniuVideoApiCredentials(), preferred)) as FeiniuVideoRequestError
      const lookalikeError = await loginError(client(lookalikeTransport,
        new FeiniuVideoApiCredentials(), preferred)) as FeiniuVideoRequestError
      const downgradeError = await loginError(client(downgradeTransport,
        new FeiniuVideoApiCredentials(), preferred)) as FeiniuVideoRequestError

      expect(userInfoError.kind).assertEqual(FeiniuVideoRequestFailureKind.InvalidResponse)
      expect(lookalikeError.kind).assertEqual(FeiniuVideoRequestFailureKind.InvalidResponse)
      expect(downgradeError.kind).assertEqual(FeiniuVideoRequestFailureKind.InvalidResponse)
      expect(userInfoTransport.requests.length).assertEqual(1)
      expect(lookalikeTransport.requests.length).assertEqual(1)
      expect(downgradeTransport.requests.length).assertEqual(1)
    })

    it('allowsSameHostHttpToHttpsUpgradeWithoutASecondConfiguredOrigin', 0, async () => {
      const transport = new FakeHttpTransport([
        new FakeTransportStep(response(301, '', 'https://server:5667/v/api/v1/sys/config')),
        new FakeTransportStep(response(200,
          '{"code":0,"msg":"ok","data":{"nas_oauth":{"app_id":"app-1","url":"://"}}}'))
      ])
      const api = client(
        transport,
        new FeiniuVideoApiCredentials(),
        candidate('http://server:5666', MediaConnectionRoute.Local)
      )

      await api.getSysConfig()

      expect(transport.requests.length).assertEqual(2)
      expect(transport.requests[1].url).assertEqual(
        'https://server:5667/v/api/v1/sys/config')
      expect(api.getActiveCandidate().baseUrl).assertEqual('https://server:5667')
      expect(api.getActiveCandidate().route).assertEqual(MediaConnectionRoute.Local)
    })

    it('doesNotLetAnOlderFailedLoginRestoreCredentialsOverANewerSuccess', 0, async () => {
      const transport = new ConcurrentLoginTransport()
      const api = client(
        transport,
        new FeiniuVideoApiCredentials('old-token', 'session=old')
      )

      const olderLogin = api.login('older', 'password-1')
      await api.login('newer', 'password-2')
      transport.failFirst()
      try {
        await olderLogin
      } catch (_) {
      }

      expect(api.getCredentials().token).assertEqual('new-token')
      expect(api.getCredentials().cookie.indexOf('session=new-session') >= 0).assertTrue()
      expect(api.getCredentials().cookie.indexOf('session=old') < 0).assertTrue()
    })

    it('followsRedirectsWithTemporaryCookiesAndPromotesTheRedirectBaseUrl', 0, async () => {
      const transport = new FakeHttpTransport([
        new FakeTransportStep(response(
        302,
        '',
        'https://redirect.example:5667/oauth/result',
        ['redirect-session=abc; Path=/']
      )),
      new FakeTransportStep(response(200,
          '{"code":0,"msg":"ok","data":{"nas_oauth":{"app_id":"app-1","url":"://"}}}',
        '', ['target-session=xyz; Path=/']))
      ])
      const api = client(
        transport,
        new FeiniuVideoApiCredentials(),
        candidate('http://lan:5666', MediaConnectionRoute.Local),
        [candidate('https://redirect.example:5667', MediaConnectionRoute.Public)]
      )

      await api.getSysConfig()

      expect(transport.requests.length).assertEqual(2)
      expect(transport.requests[1].url).assertEqual(
        'https://redirect.example:5667/oauth/result')
      expect(transport.requests[1].headers.cookie.indexOf('redirect-session=abc') < 0).assertTrue()
      expect(api.getActiveCandidate().baseUrl).assertEqual('https://redirect.example:5667')
      expect(api.getActiveCandidate().route).assertEqual(MediaConnectionRoute.Public)
      expect(api.getOauthAppId()).assertEqual('app-1')
      expect(api.getOauthBaseUrl()).assertEqual('https://redirect.example:5667')
      expect(api.getCredentials().cookie.indexOf('redirect-session=abc') < 0).assertTrue()
      expect(api.getCredentials().cookie.indexOf('target-session=xyz') >= 0).assertTrue()
    })

    it('sortsQueryParametersForTheUrlAndAuthxDigest', 0, async () => {
      const transport = new FakeHttpTransport([
        new FakeTransportStep(response(200, '{"code":0,"msg":"ok"}'))
      ])
      const digest = new PrefixDigestSource()
      const api = client(
        transport,
        new FeiniuVideoApiCredentials(),
        candidate('https://server:5667', MediaConnectionRoute.Direct),
        [],
        new ScriptedRuntimeSource([7000], 0),
        digest
      )

      await api.request<Object>(new FeiniuVideoApiRequest(
        '/v/api/v1/query',
        FeiniuVideoHttpMethod.Get,
        '',
        [new FeiniuVideoQueryParameter('z', 2), new FeiniuVideoQueryParameter('a', 'one')]
      ))

      expect(transport.requests[0].url).assertEqual('https://server:5667/v/api/v1/query?a=one&z=2')
      expect(digest.md5Calls[0]).assertEqual('a=one&z=2')
      const signText = 'NDzZTVxnRKP8Z0jXg1VAMonaG8akvh_/v/api/v1/query_100000_7000_' +
        'md5(a=one&z=2)_16CCEB3D-AB42-077D-36A1-F355324E4237'
      expect(transport.requests[0].headers.authx).assertEqual(
        `nonce=100000&timestamp=7000&sign=md5(${signText})`)
    })

    it('clearsCredentialsOnlyAfterSuccessfulLogout', 0, async () => {
      const successTransport = new FakeHttpTransport([
        new FakeTransportStep(response(200, '{"code":0,"msg":"ok"}'))
      ])
      const failedTransport = new FakeHttpTransport([
        new FakeTransportStep(undefined, new FeiniuVideoConnectionError('offline'))
      ])
      const credentials = new FeiniuVideoApiCredentials('token', 'session=abc')
      const successApi = client(successTransport, credentials)
      const failedApi = client(failedTransport, credentials)

      await successApi.logout()
      await logoutError(failedApi)

      expect(successApi.getCredentials().token).assertEqual('')
      expect(successApi.getCredentials().cookie).assertEqual('')
      expect(failedApi.getCredentials().token).assertEqual('token')
      expect(failedApi.getCredentials().cookie.indexOf('session=abc') >= 0).assertTrue()
    })
  })
}
