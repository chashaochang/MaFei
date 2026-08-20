import { buffer } from '@kit.ArkTS'
import { http } from '@kit.NetworkKit'
import CryptoJS from '@ohos/crypto-js'
import {
  FeiniuVideoApiCredentials,
  FeiniuVideoApiRequest,
  FeiniuVideoAuthenticationError,
  FeiniuVideoConnectionError,
  FeiniuVideoDigestSource,
  FeiniuVideoHostCandidate,
  FeiniuVideoHostRequest,
  FeiniuVideoHostResolution,
  FeiniuVideoHttpHeaders,
  FeiniuVideoHttpMethod,
  FeiniuVideoHttpRequest,
  FeiniuVideoHttpResponse,
  FeiniuVideoHttpResponseHeaders,
  FeiniuVideoHttpTransport,
  FeiniuVideoQueryParameter,
  FeiniuVideoRequestError,
  FeiniuVideoRequestFailureKind,
  FeiniuVideoRuntimeSource,
  FnIdResolveEnvelope,
  FnIdResolveSource,
  FnMediaAuthReq,
  FnMediaAuthResp,
  FnMediaLoginReq,
  FnMediaLoginResp,
  FnMediaSysConfigResp,
  FnMediaUserInfoResp,
  MediaConnectionRoute
} from './FeiniuVideoApiModels'
import { FeiniuVideoHostResolver } from './FeiniuVideoHostResolver'
import { FnMediaApi as UpstreamFnMediaApi } from '../upstream/common/FnMediaApi'
import { FnMediaAuthStore as UpstreamFnMediaAuthStore } from '../upstream/common/FnMediaAuthStore'
import { SessionStore as UpstreamSessionStore } from '../upstream/common/SessionStore'

interface FeiniuVideoNativeRequestHeaders {
  'Content-Type': string
  'Accept': string
  'User-Agent'?: string
  'X-Trim-Client'?: string
  'X-Trim-Client-Version'?: string
  'Authx'?: string
  'Authorization'?: string
  'Cookie'?: string
  'fn-sign'?: string
}

interface FeiniuVideoNativeResponseHeaders {
  location?: string | string[]
  setCookie?: string | string[]
}

interface FeiniuVideoNativeHttpError {
  code?: number | string
  message?: string
}

interface FeiniuVideoResponseEnvelope {
  code?: number
  msg?: string
}

class FnConnectResolveRequest {
  fnId: string

  constructor(fnId: string) {
    this.fnId = fnId
  }
}

class FeiniuVideoEmptyPayload {
}

class FeiniuVideoCandidateResponse<T> {
  readonly value: T
  readonly candidate: FeiniuVideoHostCandidate
  readonly credentials: FeiniuVideoApiCredentials

  constructor(value: T, candidate: FeiniuVideoHostCandidate,
    credentials: FeiniuVideoApiCredentials) {
    this.value = value
    this.candidate = candidate
    this.credentials = credentials
  }
}

class FeiniuVideoRedirectRequestState {
  readonly url: string
  readonly method: FeiniuVideoHttpMethod
  readonly bodyText: string

  constructor(requestUrl: string, method: FeiniuVideoHttpMethod, bodyText: string) {
    this.url = requestUrl
    this.method = method
    this.bodyText = bodyText
  }
}

class FeiniuVideoParsedUrl {
  readonly protocol: string
  readonly hostname: string
  readonly port: string
  readonly origin: string
  readonly pathname: string
  readonly search: string
  readonly username: string
  readonly password: string
  readonly href: string

  constructor(protocol: string, hostname: string, port: string, pathname: string,
    search: string, username: string = '', password: string = '') {
    this.protocol = protocol
    this.hostname = hostname
    this.port = port
    this.pathname = pathname
    this.search = search
    this.username = username
    this.password = password
    const hostText = hostname.indexOf(':') >= 0 ? `[${hostname}]` : hostname
    const portText = port.length > 0 ? `:${port}` : ''
    this.origin = `${protocol}//${hostText}${portText}`
    const userInfo = username.length > 0 || password.length > 0
      ? `${username}${password.length > 0 ? `:${password}` : ''}@`
      : ''
    this.href = `${protocol}//${userInfo}${hostText}${portText}${pathname}${search}`
  }
}

class FeiniuVideoApiHostRequest<T> implements FeiniuVideoHostRequest<FeiniuVideoCandidateResponse<T>> {
  private readonly client: FeiniuVideoApiClient
  private readonly request: FeiniuVideoApiRequest
  private readonly credentials: FeiniuVideoApiCredentials

  constructor(client: FeiniuVideoApiClient, request: FeiniuVideoApiRequest,
    credentials: FeiniuVideoApiCredentials) {
    this.client = client
    this.request = request
    this.credentials = credentials
  }

  async execute(candidate: FeiniuVideoHostCandidate): Promise<FeiniuVideoCandidateResponse<T>> {
    return await this.client.executeCandidateRequest<T>(candidate, this.request, this.credentials)
  }
}

export class FeiniuVideoDefaultRuntimeSource implements FeiniuVideoRuntimeSource {
  now(): number {
    return Date.now()
  }

  random(): number {
    return Math.random()
  }
}

export class FeiniuVideoCryptoDigestSource implements FeiniuVideoDigestSource {
  async md5(value: string): Promise<string> {
    return CryptoJS.MD5(value).toString()
  }

  async sha256(value: string): Promise<string> {
    return CryptoJS.SHA256(value).toString(CryptoJS.enc.Hex)
  }
}

export class FeiniuVideoRedirectInterceptor implements http.HttpInterceptor {
  readonly interceptorType: http.InterceptorType = http.InterceptorType.REDIRECTION

  async interceptorHandle(_request: http.HttpRequestContext,
    _response: http.HttpResponse): Promise<http.ChainContinue> {
    return this.shouldContinue()
  }

  shouldContinue(): http.ChainContinue {
    return false
  }
}

export class FeiniuVideoNetworkHttpTransport implements FeiniuVideoHttpTransport {
  async request(request: FeiniuVideoHttpRequest): Promise<FeiniuVideoHttpResponse> {
    const client = http.createHttp()
    const options: http.HttpRequestOptions = {
      method: this.nativeMethod(request.method),
      expectDataType: http.HttpDataType.STRING,
      header: this.nativeHeaders(request.headers),
      readTimeout: request.readTimeoutMs,
      connectTimeout: request.connectTimeoutMs
    }
    if (request.bodyText.length > 0) {
      options.extraData = request.bodyText
    }
    try {
      let response: http.HttpResponse
      try {
        response = await client.request(request.url, options)
      } catch (error) {
        throw this.mapNativeError(error as Object)
      }
      let bodyText = ''
      if (typeof response.result === 'string') {
        bodyText = response.result
      } else if (response.result instanceof ArrayBuffer) {
        bodyText = buffer.from(response.result).toString('utf-8')
      } else {
        bodyText = JSON.stringify(response.result)
      }
      return new FeiniuVideoHttpResponse(
        response.responseCode,
        bodyText,
        this.normalizeResponseHeaders(response.header, response.cookies)
      )
    } finally {
      client.destroy()
    }
  }

  private nativeMethod(method: FeiniuVideoHttpMethod): http.RequestMethod {
    if (method === FeiniuVideoHttpMethod.Post) {
      return http.RequestMethod.POST
    }
    if (method === FeiniuVideoHttpMethod.Put) {
      return http.RequestMethod.PUT
    }
    if (method === FeiniuVideoHttpMethod.Delete) {
      return http.RequestMethod.DELETE
    }
    return http.RequestMethod.GET
  }

  private nativeHeaders(headers: FeiniuVideoHttpHeaders): FeiniuVideoNativeRequestHeaders {
    if (headers.fnSign.length > 0) {
      return {
        'Content-Type': headers.contentType,
        'Accept': headers.accept,
        'Authx': headers.authx,
        'fn-sign': headers.fnSign
      }
    }
    if (headers.authorization.length > 0 && headers.cookie.length > 0) {
      return {
        'Content-Type': headers.contentType,
        'Accept': headers.accept,
        'User-Agent': headers.userAgent,
        'X-Trim-Client': headers.trimClient,
        'X-Trim-Client-Version': headers.trimClientVersion,
        'Authx': headers.authx,
        'Authorization': headers.authorization,
        'Cookie': headers.cookie
      }
    }
    if (headers.authorization.length > 0) {
      return {
        'Content-Type': headers.contentType,
        'Accept': headers.accept,
        'User-Agent': headers.userAgent,
        'X-Trim-Client': headers.trimClient,
        'X-Trim-Client-Version': headers.trimClientVersion,
        'Authx': headers.authx,
        'Authorization': headers.authorization
      }
    }
    if (headers.cookie.length > 0) {
      return {
        'Content-Type': headers.contentType,
        'Accept': headers.accept,
        'User-Agent': headers.userAgent,
        'X-Trim-Client': headers.trimClient,
        'X-Trim-Client-Version': headers.trimClientVersion,
        'Authx': headers.authx,
        'Cookie': headers.cookie
      }
    }
    return {
      'Content-Type': headers.contentType,
      'Accept': headers.accept,
      'User-Agent': headers.userAgent,
      'X-Trim-Client': headers.trimClient,
      'X-Trim-Client-Version': headers.trimClientVersion,
      'Authx': headers.authx
    }
  }

  normalizeResponseHeaders(headers: Object,
    cookies: string): FeiniuVideoHttpResponseHeaders {
    return this.normalizeResponseHeadersJson(JSON.stringify(headers), cookies)
  }

  normalizeResponseHeadersJson(serializedHeaders: string,
    cookies: string): FeiniuVideoHttpResponseHeaders {
    const normalizedHeaders = this.normalizedNativeResponseHeaders(serializedHeaders)
    const location = this.firstHeaderValue(normalizedHeaders.location)
    const setCookieValues: string[] = []
    this.appendHeaderValues(setCookieValues, cookies)
    this.appendHeaderValues(setCookieValues, normalizedHeaders.setCookie)
    return new FeiniuVideoHttpResponseHeaders(location, setCookieValues)
  }

  mapNativeError(error: Object): FeiniuVideoRequestError {
    if (error instanceof FeiniuVideoRequestError) {
      return error
    }
    const nativeError = error as FeiniuVideoNativeHttpError
    const rawCode = nativeError.code
    const parsedCode = typeof rawCode === 'number' ? rawCode : Number(`${rawCode || ''}`)
    const code = Number.isFinite(parsedCode) ? parsedCode : 0
    const nativeMessage = `${nativeError.message || ''}`.trim()
    const message = nativeMessage.length > 0
      ? nativeMessage
      : (code > 0 ? `NetworkKit request failed (${code})` : 'NetworkKit request failed')
    if (this.isConnectionErrorCode(code)) {
      return new FeiniuVideoConnectionError(message)
    }
    if (code === 401 || code === 2300001 || code === 2300003) {
      return new FeiniuVideoRequestError(FeiniuVideoRequestFailureKind.InvalidRequest, message)
    }
    if (code === 2300008 || code === 2300016 || code === 2300018 ||
      code === 2300061 || code === 2300063) {
      return new FeiniuVideoRequestError(FeiniuVideoRequestFailureKind.InvalidResponse, message)
    }
    if (code === 2300094) {
      return new FeiniuVideoAuthenticationError(message, false)
    }
    return new FeiniuVideoRequestError(FeiniuVideoRequestFailureKind.Unknown, message)
  }

  private disableAutomaticRedirects(client: http.HttpRequest): void {
    try {
      const chain = new http.HttpInterceptorChain()
      const added = chain.addChain([new FeiniuVideoRedirectInterceptor()])
      const applied = added && chain.apply(client)
      if (!applied) {
        throw new FeiniuVideoRequestError(
          FeiniuVideoRequestFailureKind.Unknown,
          'Failed to attach the controlled redirect interceptor'
        )
      }
    } catch (error) {
      if (error instanceof FeiniuVideoRequestError) {
        throw error
      }
      const normalized = error as Error
      throw new FeiniuVideoRequestError(
        FeiniuVideoRequestFailureKind.Unknown,
        normalized.message || 'Failed to attach the controlled redirect interceptor'
      )
    }
  }

  private appendHeaderValues(target: string[], value?: string | string[]): void {
    if (value === undefined) {
      return
    }
    const values = Array.isArray(value) ? value : [value]
    values.forEach((item: string) => {
      const normalized = `${item || ''}`.trim()
      if (normalized.length > 0 && target.indexOf(normalized) < 0) {
        target.push(normalized)
      }
    })
  }

  private normalizedNativeResponseHeaders(serializedHeaders: string): FeiniuVideoNativeResponseHeaders {
    try {
      const normalizedKeys = serializedHeaders
        .replace(/"location"\s*:/gi, '"location":')
        .replace(/"set-cookie"\s*:/gi, '"setCookie":')
      return JSON.parse(normalizedKeys) as FeiniuVideoNativeResponseHeaders
    } catch (_) {
      return {}
    }
  }

  private isConnectionErrorCode(code: number): boolean {
    return code === 2300005
      || code === 2300006
      || code === 2300007
      || code === 2300028
      || code === 2300052
      || code === 2300055
      || code === 2300056
  }

  private firstHeaderValue(value?: string | string[]): string {
    if (value === undefined) {
      return ''
    }
    if (Array.isArray(value)) {
      return value.length > 0 ? `${value[0] || ''}`.trim() : ''
    }
    return value.trim()
  }

}

export class FeiniuVideoFnIdResolveSource implements FnIdResolveSource {
  private static readonly API_URL: string = 'https://5ddd.com/api/v1/fn/con'
  private static readonly API_PATH: string = '/api/v1/fn/con'
  private static readonly AUTHX_PREFIX: string = 'NDzZTVxnRKP8Z0jXg1VAMonaG8akvh'
  private static readonly AUTHX_API_KEY: string = 'zIGtkc3dqZnJpd29qZXJqa2w7c'
  private readonly transport: FeiniuVideoHttpTransport
  private readonly runtime: FeiniuVideoRuntimeSource
  private readonly digest: FeiniuVideoDigestSource

  constructor(transport: FeiniuVideoHttpTransport = new FeiniuVideoNetworkHttpTransport(),
    runtime: FeiniuVideoRuntimeSource = new FeiniuVideoDefaultRuntimeSource(),
    digest: FeiniuVideoDigestSource = new FeiniuVideoCryptoDigestSource()) {
    this.transport = transport
    this.runtime = runtime
    this.digest = digest
  }

  async resolve(fnId: string): Promise<FnIdResolveEnvelope> {
    const normalizedFnId = fnId.trim()
    if (normalizedFnId.length === 0) {
      throw new FeiniuVideoRequestError(FeiniuVideoRequestFailureKind.InvalidRequest, 'FNID is required')
    }
    const payload = new FnConnectResolveRequest(normalizedFnId)
    const bodyText = JSON.stringify(payload)
    const timestamp = `${this.runtime.now()}`
    const headers = new FeiniuVideoHttpHeaders()
    headers.contentType = 'application/json'
    headers.accept = 'application/json'
    headers.fnSign = await this.fnSign(normalizedFnId, timestamp)
    headers.authx = await this.authx(FeiniuVideoFnIdResolveSource.API_PATH, bodyText)
    const response = await this.transport.request(new FeiniuVideoHttpRequest(
      FeiniuVideoFnIdResolveSource.API_URL,
      FeiniuVideoHttpMethod.Post,
      headers,
      bodyText,
      15000,
      10000
    ))
    this.requireSuccessStatus(response.statusCode, false)
    let envelope: FnIdResolveEnvelope
    try {
      envelope = JSON.parse(response.bodyText) as FnIdResolveEnvelope
      if (typeof envelope.code !== 'number') {
        throw new Error('FNID response code is missing')
      }
    } catch (_) {
      throw new FeiniuVideoRequestError(
        FeiniuVideoRequestFailureKind.InvalidResponse,
        `FNID response is not valid JSON code=${response.statusCode} body=${this.truncate(response.bodyText, 120)}`
      )
    }
    return envelope
  }

  private async fnSign(fnId: string, timestamp: string): Promise<string> {
    return await this.digest.sha256(`trim_connect\`${fnId}\`${timestamp}\`anna`)
  }

  private async authx(path: string, bodyText: string): Promise<string> {
    const nonce = `${Math.floor(this.runtime.random() * 900000) + 100000}`
    const timestamp = `${this.runtime.now()}`
    const bodyMd5 = await this.digest.md5(bodyText)
    const signText = [
      FeiniuVideoFnIdResolveSource.AUTHX_PREFIX,
      path,
      nonce,
      timestamp,
      bodyMd5,
      FeiniuVideoFnIdResolveSource.AUTHX_API_KEY
    ].join('_')
    const sign = await this.digest.md5(signText)
    return `nonce=${nonce}&timestamp=${timestamp}&sign=${sign}`
  }

  private requireSuccessStatus(statusCode: number, authExpired: boolean): void {
    if (statusCode >= 200 && statusCode < 300) {
      return
    }
    if (statusCode === 401 || statusCode === 403) {
      throw new FeiniuVideoAuthenticationError(`FNID request rejected with HTTP ${statusCode}`, authExpired)
    }
    throw new FeiniuVideoRequestError(
      FeiniuVideoRequestFailureKind.InvalidResponse,
      `FNID request returned HTTP ${statusCode}`
    )
  }

  private truncate(value: string, maxLength: number): string {
    const normalized = `${value || ''}`
    return normalized.length <= maxLength ? normalized : `${normalized.substring(0, maxLength)}...`
  }
}

class FeiniuVideoApiConstants {
  static readonly API_KEY: string = 'NDzZTVxnRKP8Z0jXg1VAMonaG8akvh'
  static readonly API_SECRET: string = '16CCEB3D-AB42-077D-36A1-F355324E4237'
  static readonly USER_AGENT: string = '1.32.2 (com.trim.app; build:1322007; Android 36) okHttp/4.9.0'
  static readonly CLIENT: string = 'android-media'
  static readonly CLIENT_VERSION: string = '1322007'
  static readonly MAX_REDIRECTS: number = 4
}

export class FeiniuVideoApiClient {
  private resolution: FeiniuVideoHostResolution
  private credentials: FeiniuVideoApiCredentials
  private readonly transport: FeiniuVideoHttpTransport
  private readonly runtime: FeiniuVideoRuntimeSource
  private readonly digest: FeiniuVideoDigestSource
  private readonly hostResolver: FeiniuVideoHostResolver
  private readonly upstreamAuthEnabled: boolean
  private oauthAppId: string = ''
  private oauthBaseUrl: string = ''

  constructor(resolution: FeiniuVideoHostResolution,
    transport: FeiniuVideoHttpTransport = new FeiniuVideoNetworkHttpTransport(),
    credentials: FeiniuVideoApiCredentials = new FeiniuVideoApiCredentials(),
    runtime: FeiniuVideoRuntimeSource = new FeiniuVideoDefaultRuntimeSource(),
    digest: FeiniuVideoDigestSource = new FeiniuVideoCryptoDigestSource(),
    hostResolver: FeiniuVideoHostResolver = new FeiniuVideoHostResolver(),
    upstreamAuthEnabled: boolean = false) {
    this.resolution = resolution
    this.transport = transport
    this.runtime = runtime
    this.digest = digest
    this.hostResolver = hostResolver
    this.upstreamAuthEnabled = upstreamAuthEnabled
    this.credentials = this.normalizedCredentials(credentials)
  }

  static createWithUpstreamAuth(resolution: FeiniuVideoHostResolution,
    credentials: FeiniuVideoApiCredentials = new FeiniuVideoApiCredentials()): FeiniuVideoApiClient {
    return new FeiniuVideoApiClient(
      resolution,
      new FeiniuVideoNetworkHttpTransport(),
      credentials,
      new FeiniuVideoDefaultRuntimeSource(),
      new FeiniuVideoCryptoDigestSource(),
      new FeiniuVideoHostResolver(),
      true
    )
  }

  getActiveCandidate(): FeiniuVideoHostCandidate {
    return this.resolution.preferred
  }

  getResolution(): FeiniuVideoHostResolution {
    return this.resolution
  }

  setResolution(resolution: FeiniuVideoHostResolution): void {
    this.resolution = resolution
  }

  getCredentials(): FeiniuVideoApiCredentials {
    return new FeiniuVideoApiCredentials(this.credentials.token, this.credentials.cookie)
  }

  setCredentials(credentials: FeiniuVideoApiCredentials): void {
    this.credentials = this.normalizedCredentials(credentials)
  }

  getOauthAppId(): string {
    return this.oauthAppId
  }

  getOauthBaseUrl(): string {
    return this.oauthBaseUrl
  }

  async getSysConfig(): Promise<FnMediaSysConfigResp> {
    if (this.upstreamAuthEnabled) {
      const response = await UpstreamFnMediaApi.getSysConfig(this.resolution.preferredBaseUrl)
      const appId = response.data?.nas_oauth?.app_id || ''
      const oauthUrl = response.data?.nas_oauth?.url || ''
      if (appId.trim().length > 0) {
        this.oauthAppId = appId.trim()
        this.oauthBaseUrl = this.resolveOauthBaseUrl(oauthUrl)
      }
      return response as FnMediaSysConfigResp
    }
    const response = await this.request<FnMediaSysConfigResp>(new FeiniuVideoApiRequest(
      '/v/api/v1/sys/config',
      FeiniuVideoHttpMethod.Get
    ))
    const appId = response.data?.nas_oauth?.app_id || ''
    const oauthUrl = response.data?.nas_oauth?.url || ''
    if (appId.trim().length > 0) {
      this.oauthAppId = appId.trim()
      this.oauthBaseUrl = this.resolveOauthBaseUrl(oauthUrl)
    }
    return response
  }

  async auth(code: string): Promise<FnMediaAuthResp> {
    const payload: FnMediaAuthReq = {
      source: 'Trim-NAS',
      code: code
    }
    const execution = await this.executeRequest<FnMediaAuthResp>(new FeiniuVideoApiRequest(
      '/v/api/v1/auth',
      FeiniuVideoHttpMethod.Post,
      JSON.stringify(payload),
      [],
      false,
      15000,
      false,
      false
    ), new FeiniuVideoApiCredentials())
    const response = execution.value
    const token = response.token || response.data?.token || execution.credentials.token
    if (token.trim().length === 0) {
      throw new FeiniuVideoRequestError(
        FeiniuVideoRequestFailureKind.InvalidResponse,
        'Feiniu Video auth response did not contain a token'
      )
    }
    this.credentials = this.credentialsWithAuthToken(execution.credentials, token)
    this.promoteCandidate(execution.candidate)
    return response
  }

  async login(username: string, password: string): Promise<FnMediaLoginResp> {
    if (this.upstreamAuthEnabled) {
      const host = this.resolution.preferredBaseUrl
      UpstreamSessionStore.saveLoginForm(
        host,
        username,
        password,
        false,
        host.startsWith('https://'),
        host
      )
      const response = await UpstreamFnMediaApi.login(host, username, password)
      const snapshot = UpstreamFnMediaAuthStore.getSnapshot()
      const token = (response.token || response.data?.token || snapshot.token).trim()
      if (token.length === 0) {
        throw new FeiniuVideoRequestError(
          FeiniuVideoRequestFailureKind.InvalidResponse,
          'Feiniu Video login response did not contain a token'
        )
      }
      this.credentials = this.normalizedCredentials(
        new FeiniuVideoApiCredentials(token, snapshot.cookie)
      )
      return response as FnMediaLoginResp
    }
    const payload: FnMediaLoginReq = {
      username: username.trim(),
      password: password,
      app_name: 'trimemedia-web'
    }
    const execution = await this.executeRequest<FnMediaLoginResp>(new FeiniuVideoApiRequest(
      '/v/api/v1/login',
      FeiniuVideoHttpMethod.Post,
      JSON.stringify(payload),
      [],
      false,
      15000,
      false,
      false
    ), new FeiniuVideoApiCredentials())
    const response = execution.value
    const token = response.token || response.data?.token || execution.credentials.token
    if (token.trim().length === 0) {
      throw new FeiniuVideoRequestError(
        FeiniuVideoRequestFailureKind.InvalidResponse,
        'Feiniu Video login response did not contain a token'
      )
    }
    this.credentials = this.credentialsWithAuthToken(execution.credentials, token)
    this.promoteCandidate(execution.candidate)
    return response
  }

  async getUserInfo(): Promise<FnMediaUserInfoResp> {
    if (this.upstreamAuthEnabled) {
      return await UpstreamFnMediaApi.getUserInfo(
        this.resolution.preferredBaseUrl) as FnMediaUserInfoResp
    }
    return await this.request<FnMediaUserInfoResp>(new FeiniuVideoApiRequest(
      '/v/api/v1/user/info',
      FeiniuVideoHttpMethod.Get,
      '',
      [],
      true
    ))
  }

  async logout(): Promise<boolean> {
    if (this.upstreamAuthEnabled) {
      const loggedOut = await UpstreamFnMediaApi.logout(this.resolution.preferredBaseUrl)
      this.credentials = new FeiniuVideoApiCredentials()
      return loggedOut
    }
    await this.request<FeiniuVideoResponseEnvelope>(new FeiniuVideoApiRequest(
      '/v/api/v1/user/logout',
      FeiniuVideoHttpMethod.Post,
      '',
      [],
      true
    ))
    this.credentials = new FeiniuVideoApiCredentials()
    return true
  }

  async request<T>(request: FeiniuVideoApiRequest): Promise<T> {
    if (this.upstreamAuthEnabled) {
      const value = await UpstreamFnMediaApi.requestRaw<T>(
        this.resolution.preferredBaseUrl,
        request.path,
        this.upstreamRequestMethod(request.method),
        this.upstreamRequestBody(request),
        this.upstreamRequestParameters(request.parameters),
        request.requireMediaToken,
        request.readTimeoutMs,
        request.includeStoredMediaToken,
        request.includeStoredCookie
      )
      this.syncUpstreamSnapshot()
      return value
    }
    const credentialsSnapshot = this.credentials
    const result = await this.executeRequest<T>(request, credentialsSnapshot)
    if (this.credentials === credentialsSnapshot) {
      this.credentials = result.credentials
    }
    this.promoteCandidate(result.candidate)
    return result.value
  }

  private upstreamRequestMethod(method: FeiniuVideoHttpMethod): http.RequestMethod {
    if (method === FeiniuVideoHttpMethod.Post) {
      return http.RequestMethod.POST
    }
    if (method === FeiniuVideoHttpMethod.Put) {
      return http.RequestMethod.PUT
    }
    if (method === FeiniuVideoHttpMethod.Delete) {
      return http.RequestMethod.DELETE
    }
    return http.RequestMethod.GET
  }

  private upstreamRequestBody(request: FeiniuVideoApiRequest): Object | undefined {
    if (request.bodyText.trim().length === 0) {
      return undefined
    }
    try {
      return JSON.parse(request.bodyText) as Object
    } catch (_) {
      throw new FeiniuVideoRequestError(
        FeiniuVideoRequestFailureKind.InvalidRequest,
        'Feiniu Video request body must be valid JSON'
      )
    }
  }

  private upstreamRequestParameters(parameters: FeiniuVideoQueryParameter[]):
    Record<string, Object | string | number | boolean> | undefined {
    if (parameters.length === 0) {
      return undefined
    }
    const result: Record<string, Object | string | number | boolean> = {}
    parameters.forEach((parameter: FeiniuVideoQueryParameter): void => {
      result[parameter.name] = parameter.value
    })
    return result
  }

  private syncUpstreamSnapshot(): void {
    const snapshot = UpstreamFnMediaAuthStore.getSnapshot()
    this.credentials = new FeiniuVideoApiCredentials(snapshot.token, snapshot.cookie)
    const resolvedBaseUrl = snapshot.baseUrl.trim().replace(/\/+$/, '')
    if (resolvedBaseUrl.length === 0 || resolvedBaseUrl === this.resolution.preferredBaseUrl) {
      return
    }
    this.promoteCandidate(new FeiniuVideoHostCandidate(
      resolvedBaseUrl,
      this.routeForBaseUrl(resolvedBaseUrl, this.resolution.preferredRoute)
    ))
  }

  private async executeRequest<T>(request: FeiniuVideoApiRequest,
    credentials: FeiniuVideoApiCredentials): Promise<FeiniuVideoCandidateResponse<T>> {
    if (request.path.length === 0 || !request.path.startsWith('/')) {
      throw new FeiniuVideoRequestError(
        FeiniuVideoRequestFailureKind.InvalidRequest,
        'Feiniu Video request path must start with /'
      )
    }
    const execution = new FeiniuVideoApiHostRequest<T>(this, request, credentials)
    const result = await this.hostResolver.executeWithFallback<FeiniuVideoCandidateResponse<T>>(
      this.resolution,
      execution
    )
    return result.value
  }

  async executeCandidateRequest<T>(candidate: FeiniuVideoHostCandidate,
    request: FeiniuVideoApiRequest,
    credentials: FeiniuVideoApiCredentials = this.credentials): Promise<FeiniuVideoCandidateResponse<T>> {
    let activeCandidate = candidate
    let transientCookie = ''
    let requestState = this.initialRedirectState(candidate, request)
    for (let redirectCount = 0; redirectCount <= FeiniuVideoApiConstants.MAX_REDIRECTS; redirectCount += 1) {
      const response = await this.transport.request(
        await this.buildHttpRequest(activeCandidate, request, requestState, credentials, transientCookie)
      )
      transientCookie = this.mergeCookieHeader(
        transientCookie,
        this.normalizeSetCookieHeaders(response.headers.setCookieValues)
      )
      if (this.isRedirectCode(response.statusCode)) {
        const redirectUrl = this.resolveRedirectUrl(requestState.url, response.headers.location)
        this.requireTrustedRedirect(requestState.url, redirectUrl)
        if (!this.isSameOrigin(requestState.url, redirectUrl)) {
          transientCookie = ''
        }
        const redirectBaseUrl = this.baseUrlFromUrl(redirectUrl)
        if (redirectBaseUrl.length === 0) {
          throw new FeiniuVideoRequestError(
            FeiniuVideoRequestFailureKind.InvalidResponse,
            `Feiniu Video redirect ${response.statusCode} is missing a valid location`
          )
        }
        activeCandidate = new FeiniuVideoHostCandidate(
          redirectBaseUrl,
          this.routeForBaseUrl(redirectBaseUrl, activeCandidate.route)
        )
        requestState = this.redirectState(requestState, redirectUrl, response.statusCode)
        continue
      }
      this.requireSuccessStatus(response.statusCode, request.requireMediaToken)
      const value = this.parseResponse<T>(response.bodyText, request)
      const responseCredentials = this.credentialsFromResponse(transientCookie, request, credentials)
      return new FeiniuVideoCandidateResponse<T>(value, activeCandidate, responseCredentials)
    }
    throw new FeiniuVideoRequestError(
      FeiniuVideoRequestFailureKind.InvalidResponse,
      'Feiniu Video request exceeded the redirect limit'
    )
  }

  private async buildHttpRequest(candidate: FeiniuVideoHostCandidate,
    request: FeiniuVideoApiRequest, requestState: FeiniuVideoRedirectRequestState,
    credentials: FeiniuVideoApiCredentials, transientCookie: string): Promise<FeiniuVideoHttpRequest> {
    const parsedUrl = this.parseHttpUrl(requestState.url)
    if (parsedUrl === undefined) {
      throw new FeiniuVideoRequestError(
        FeiniuVideoRequestFailureKind.InvalidRequest,
        'Feiniu Video request URL is invalid'
      )
    }
    const headers = await this.buildHeaders(
      candidate,
      request,
      credentials,
      requestState.bodyText,
      transientCookie,
      parsedUrl.pathname || '/',
      this.queryParameters(parsedUrl)
    )
    return new FeiniuVideoHttpRequest(
      requestState.url,
      requestState.method,
      headers,
      requestState.bodyText,
      request.readTimeoutMs,
      10000
    )
  }

  private async buildHeaders(candidate: FeiniuVideoHostCandidate, request: FeiniuVideoApiRequest,
    credentials: FeiniuVideoApiCredentials, bodyText: string, transientCookie: string,
    authPath: string, authParameters: FeiniuVideoQueryParameter[]): Promise<FeiniuVideoHttpHeaders> {
    const headers = new FeiniuVideoHttpHeaders()
    headers.contentType = 'application/json; charset=utf-8'
    headers.accept = 'application/json'
    headers.userAgent = FeiniuVideoApiConstants.USER_AGENT
    headers.trimClient = FeiniuVideoApiConstants.CLIENT
    headers.trimClientVersion = FeiniuVideoApiConstants.CLIENT_VERSION
    headers.authx = await this.genAuthx(authPath, authParameters, bodyText)
    const token = credentials.token.trim()
    if (request.requireMediaToken && token.length === 0) {
      throw new FeiniuVideoAuthenticationError('Feiniu Video is not authorized')
    }
    if (request.includeStoredMediaToken && token.length > 0) {
      headers.authorization = token
      headers.cookie = this.mergeCookieHeader(credentials.cookie, `Trim-MC-token=${token}`)
    } else if (request.includeStoredCookie && credentials.cookie.trim().length > 0) {
      headers.cookie = credentials.cookie.trim()
    }
    if (transientCookie.trim().length > 0) {
      headers.cookie = this.mergeCookieHeader(headers.cookie, transientCookie)
    }
    if (candidate.requiresRelayCookie) {
      headers.cookie = this.mergeCookieHeader(headers.cookie, 'mode=relay')
    }
    return headers
  }

  private parseResponse<T>(bodyText: string, request: FeiniuVideoApiRequest): T {
    let envelope: FeiniuVideoResponseEnvelope
    try {
      envelope = JSON.parse(bodyText) as FeiniuVideoResponseEnvelope
      if (typeof envelope.code !== 'number') {
        throw new Error('Response code is missing')
      }
    } catch (_) {
      throw new FeiniuVideoRequestError(
        FeiniuVideoRequestFailureKind.InvalidResponse,
        'Feiniu Video returned an invalid JSON response'
      )
    }
    if (envelope.code !== 0) {
      const message = envelope.msg && envelope.msg.trim().length > 0
        ? envelope.msg.trim()
        : `Feiniu Video request failed (${envelope.code})`
      if (this.isAuthenticationResponse(envelope.code, envelope.msg || '') ||
        (request.path === '/v/api/v1/login' && this.isCredentialResponse(message))) {
        const expired = request.requireMediaToken
        throw new FeiniuVideoAuthenticationError(
          expired ? 'Feiniu Video authorization expired' : message,
          expired
        )
      }
      throw new FeiniuVideoRequestError(FeiniuVideoRequestFailureKind.Business, message)
    }
    return envelope as T
  }

  private requireSuccessStatus(statusCode: number, authExpired: boolean): void {
    if (statusCode >= 200 && statusCode < 300) {
      return
    }
    if (statusCode === 401 || statusCode === 403) {
      throw new FeiniuVideoAuthenticationError(
        `Feiniu Video request rejected with HTTP ${statusCode}`,
        authExpired
      )
    }
    throw new FeiniuVideoRequestError(
      FeiniuVideoRequestFailureKind.InvalidResponse,
      `Feiniu Video request returned HTTP ${statusCode}`
    )
  }

  private isAuthenticationResponse(code: number, message: string): boolean {
    if (code === 401 || code === 403 || code === 100401 || code === 100403) {
      return true
    }
    const normalized = message.trim().toLowerCase()
    if (normalized.length === 0) {
      return false
    }
    return normalized.indexOf('unauthorized') >= 0
      || normalized.indexOf('invalid token') >= 0
      || normalized.indexOf('token invalid') >= 0
      || normalized.indexOf('token expired') >= 0
      || normalized.indexOf('登录过期') >= 0
      || normalized.indexOf('token失效') >= 0
      || normalized.indexOf('授权已失效') >= 0
      || normalized.indexOf('鉴权失败') >= 0
  }

  private isCredentialResponse(message: string): boolean {
    const normalized = message.trim().toLowerCase()
    return normalized === '用户名或密码错误' ||
      normalized.indexOf('用户名或密码错误') >= 0 ||
      (normalized.indexOf('username') >= 0 && normalized.indexOf('password') >= 0)
  }

  private isRedirectCode(code: number): boolean {
    return code === 301 || code === 302 || code === 303 || code === 307 || code === 308
  }

  private initialRedirectState(candidate: FeiniuVideoHostCandidate,
    request: FeiniuVideoApiRequest): FeiniuVideoRedirectRequestState {
    const requestPath = this.appendQuery(request.path, request.parameters)
    const parsedUrl = this.parseHttpUrl(`${candidate.baseUrl}${requestPath}`)
    if (parsedUrl === undefined || this.hasUserInfo(parsedUrl)) {
      throw new FeiniuVideoRequestError(
        FeiniuVideoRequestFailureKind.InvalidRequest,
        'Feiniu Video request URL is invalid or contains user information'
      )
    }
    return new FeiniuVideoRedirectRequestState(
      parsedUrl.href,
      request.method,
      this.effectiveRequestBody(request)
    )
  }

  private redirectState(current: FeiniuVideoRedirectRequestState,
    redirectUrl: string, statusCode: number): FeiniuVideoRedirectRequestState {
    if (statusCode === 303) {
      return new FeiniuVideoRedirectRequestState(redirectUrl, FeiniuVideoHttpMethod.Get, '')
    }
    return new FeiniuVideoRedirectRequestState(redirectUrl, current.method, current.bodyText)
  }

  private queryParameters(parsedUrl: FeiniuVideoParsedUrl): FeiniuVideoQueryParameter[] {
    const parameters: FeiniuVideoQueryParameter[] = []
    const query = parsedUrl.search.startsWith('?') ? parsedUrl.search.substring(1) : parsedUrl.search
    query.split('&').forEach((item: string) => {
      if (item.length === 0) {
        return
      }
      const equalIndex = item.indexOf('=')
      const rawName = equalIndex >= 0 ? item.substring(0, equalIndex) : item
      const rawValue = equalIndex >= 0 ? item.substring(equalIndex + 1) : ''
      parameters.push(new FeiniuVideoQueryParameter(
        this.decodeQueryPart(rawName),
        this.decodeQueryPart(rawValue)
      ))
    })
    return parameters
  }

  private effectiveRequestBody(request: FeiniuVideoApiRequest): string {
    if (request.bodyText.length > 0) {
      return request.bodyText
    }
    if (request.method === FeiniuVideoHttpMethod.Post) {
      return JSON.stringify(new FeiniuVideoEmptyPayload())
    }
    return ''
  }

  private credentialsFromResponse(cookie: string, request: FeiniuVideoApiRequest,
    credentials: FeiniuVideoApiCredentials): FeiniuVideoApiCredentials {
    const normalizedCookie = cookie.trim()
    if (normalizedCookie.length === 0) {
      return credentials
    }
    const replaceExisting = !request.includeStoredMediaToken && !request.includeStoredCookie
    const mergedCookie = this.mergeCookieHeader(
      replaceExisting ? '' : credentials.cookie,
      normalizedCookie
    )
    const token = replaceExisting
      ? this.extractTokenFromCookie(mergedCookie)
      : (credentials.token.trim() || this.extractTokenFromCookie(mergedCookie))
    return new FeiniuVideoApiCredentials(token, mergedCookie)
  }

  private credentialsWithAuthToken(credentials: FeiniuVideoApiCredentials,
    token: string): FeiniuVideoApiCredentials {
    const normalizedToken = token.trim()
    if (normalizedToken.length === 0) {
      return credentials
    }
    return new FeiniuVideoApiCredentials(
      normalizedToken,
      this.mergeCookieHeader(credentials.cookie, `Trim-MC-token=${normalizedToken}`)
    )
  }

  private normalizedCredentials(credentials: FeiniuVideoApiCredentials): FeiniuVideoApiCredentials {
    const cookie = credentials.cookie.trim()
    const token = credentials.token.trim() || this.extractTokenFromCookie(cookie)
    return new FeiniuVideoApiCredentials(
      token,
      token.length > 0 ? this.mergeCookieHeader(cookie, `Trim-MC-token=${token}`) : cookie
    )
  }

  private normalizeSetCookieHeaders(cookieValues: string[]): string {
    const pairs: string[] = []
    cookieValues.forEach((cookieText: string) => {
      this.splitSetCookieHeader(cookieText).forEach((segment: string) => {
        const pair = this.cookiePairFromSetCookie(segment)
        if (pair.length > 0) {
          pairs.push(pair)
        }
      })
    })
    return pairs.join('; ')
  }

  private cookiePairFromSetCookie(segment: string): string {
    const trimmed = segment.trim()
    if (trimmed.length === 0) {
      return ''
    }
    const firstPart = trimmed.split(';')[0].trim()
    const equalIndex = firstPart.indexOf('=')
    return equalIndex > 0 ? firstPart : ''
  }

  private splitSetCookieHeader(cookieText: string): string[] {
    const normalized = cookieText.trim()
    if (normalized.length === 0) {
      return []
    }
    const segments: string[] = []
    let start = 0
    let index = 0
    while (index < normalized.length) {
      if (normalized[index] === ',' && this.isSetCookieBoundary(normalized, index + 1)) {
        segments.push(normalized.substring(start, index).trim())
        start = index + 1
      }
      index += 1
    }
    segments.push(normalized.substring(start).trim())
    return segments.filter((segment: string) => segment.length > 0)
  }

  private isSetCookieBoundary(text: string, startIndex: number): boolean {
    let index = startIndex
    while (index < text.length && text[index] === ' ') {
      index += 1
    }
    const nextSemicolon = text.indexOf(';', index)
    const nextComma = text.indexOf(',', index)
    let end = text.length
    if (nextSemicolon >= 0 && nextSemicolon < end) {
      end = nextSemicolon
    }
    if (nextComma >= 0 && nextComma < end) {
      end = nextComma
    }
    const candidate = text.substring(index, end)
    const equalIndex = candidate.indexOf('=')
    if (equalIndex <= 0) {
      return false
    }
    const cookieName = candidate.substring(0, equalIndex).trim()
    if (cookieName.length === 0) {
      return false
    }
    for (let charIndex = 0; charIndex < cookieName.length; charIndex += 1) {
      const code = cookieName.charCodeAt(charIndex)
      const valid = (code >= 48 && code <= 57)
        || (code >= 65 && code <= 90)
        || (code >= 97 && code <= 122)
        || cookieName[charIndex] === '-'
        || cookieName[charIndex] === '_'
      if (!valid) {
        return false
      }
    }
    return true
  }

  private mergeCookieHeader(existing: string, next: string): string {
    const cookieMap: Map<string, string> = new Map<string, string>()
    this.collectCookiePairs(cookieMap, existing)
    this.collectCookiePairs(cookieMap, next)
    const pairs: string[] = []
    cookieMap.forEach((value: string, key: string) => {
      pairs.push(`${key}=${value}`)
    })
    return pairs.join('; ')
  }

  private collectCookiePairs(cookieMap: Map<string, string>, cookieText: string): void {
    cookieText.split(';').forEach((segment: string) => {
      const trimmed = segment.trim()
      if (trimmed.length === 0) {
        return
      }
      const equalIndex = trimmed.indexOf('=')
      if (equalIndex <= 0) {
        return
      }
      const key = trimmed.substring(0, equalIndex).trim()
      const value = trimmed.substring(equalIndex + 1).trim()
      if (key.length > 0) {
        cookieMap.set(key, value)
      }
    })
  }

  private extractTokenFromCookie(cookie: string): string {
    const segments = cookie.split(';')
    for (let index = 0; index < segments.length; index += 1) {
      const segment = segments[index].trim()
      const equalIndex = segment.indexOf('=')
      if (equalIndex <= 0) {
        continue
      }
      if (segment.substring(0, equalIndex).trim() === 'Trim-MC-token') {
        return segment.substring(equalIndex + 1).trim()
      }
    }
    return ''
  }

  private resolveRedirectUrl(currentUrl: string, location: string): string {
    const trimmed = location.trim()
    if (trimmed.length === 0) {
      return ''
    }
    const parsedUrl = this.parseHttpUrl(trimmed, currentUrl)
    return parsedUrl === undefined ? '' : parsedUrl.href
  }

  private requireTrustedRedirect(currentUrl: string, redirectUrl: string): void {
    const current = this.parseHttpUrl(currentUrl)
    const target = this.parseHttpUrl(redirectUrl)
    if (current === undefined || target === undefined) {
      throw new FeiniuVideoRequestError(
        FeiniuVideoRequestFailureKind.InvalidResponse,
        'Feiniu Video redirect location is invalid'
      )
    }
    if (this.hasUserInfo(target)) {
      throw new FeiniuVideoRequestError(
        FeiniuVideoRequestFailureKind.InvalidResponse,
        'Feiniu Video redirect location must not contain user information'
      )
    }
    if (current.protocol.toLowerCase() === 'https:' && target.protocol.toLowerCase() === 'http:') {
      throw new FeiniuVideoRequestError(
        FeiniuVideoRequestFailureKind.InvalidResponse,
        'Feiniu Video refused an HTTPS downgrade redirect'
      )
    }
    if (current.origin.toLowerCase() === target.origin.toLowerCase()) {
      return
    }
    if (this.normalizedHostname(current) === this.normalizedHostname(target) &&
      current.protocol.toLowerCase() === 'http:' && target.protocol.toLowerCase() === 'https:') {
      return
    }
    const trusted = this.resolution.orderedCandidates.find((candidate: FeiniuVideoHostCandidate) => {
      const parsedCandidate = this.parseHttpUrl(candidate.baseUrl)
      return parsedCandidate !== undefined && !this.hasUserInfo(parsedCandidate) &&
        parsedCandidate.origin.toLowerCase() === target.origin.toLowerCase()
    })
    if (trusted === undefined) {
      throw new FeiniuVideoRequestError(
        FeiniuVideoRequestFailureKind.InvalidResponse,
        'Feiniu Video refused an untrusted cross-origin redirect'
      )
    }
  }

  private parseHttpUrl(value: string, base?: string): FeiniuVideoParsedUrl | undefined {
    const trimmed = value.trim()
    if (trimmed.length === 0 || this.containsUnsafeUrlCharacters(trimmed)) {
      return undefined
    }
    const withoutFragment = this.withoutFragment(trimmed)
    if (/^https?:\/\//i.test(withoutFragment)) {
      return this.parseAbsoluteHttpUrl(withoutFragment)
    }
    if (base === undefined) {
      return undefined
    }
    const parsedBase = this.parseAbsoluteHttpUrl(this.withoutFragment(base.trim()))
    if (parsedBase === undefined) {
      return undefined
    }
    if (withoutFragment.startsWith('//')) {
      return this.parseAbsoluteHttpUrl(`${parsedBase.protocol}${withoutFragment}`)
    }
    if (withoutFragment.length === 0) {
      return new FeiniuVideoParsedUrl(
        parsedBase.protocol,
        parsedBase.hostname,
        parsedBase.port,
        parsedBase.pathname,
        parsedBase.search,
        parsedBase.username,
        parsedBase.password
      )
    }
    if (withoutFragment.startsWith('?')) {
      return new FeiniuVideoParsedUrl(
        parsedBase.protocol,
        parsedBase.hostname,
        parsedBase.port,
        parsedBase.pathname,
        withoutFragment,
        parsedBase.username,
        parsedBase.password
      )
    }
    const queryIndex = withoutFragment.indexOf('?')
    const rawPath = queryIndex >= 0 ? withoutFragment.substring(0, queryIndex) : withoutFragment
    const search = queryIndex >= 0 ? withoutFragment.substring(queryIndex) : ''
    const combinedPath = rawPath.startsWith('/')
      ? rawPath
      : `${this.baseDirectory(parsedBase.pathname)}${rawPath}`
    const pathname = this.normalizePath(combinedPath)
    if (pathname.length === 0) {
      return undefined
    }
    return new FeiniuVideoParsedUrl(
      parsedBase.protocol,
      parsedBase.hostname,
      parsedBase.port,
      pathname,
      search,
      parsedBase.username,
      parsedBase.password
    )
  }

  private parseAbsoluteHttpUrl(value: string): FeiniuVideoParsedUrl | undefined {
    const match = value.match(/^(https?):\/\/([^/?#]*)([^?#]*)(\?[^#]*)?$/i)
    if (match === null || match.length < 4) {
      return undefined
    }
    const protocol = `${match[1] || ''}`.toLowerCase() === 'https' ? 'https:' : 'http:'
    const authority = `${match[2] || ''}`
    if (authority.length === 0 || this.containsUnsafeUrlCharacters(authority)) {
      return undefined
    }
    const userInfoIndex = authority.lastIndexOf('@')
    const rawUserInfo = userInfoIndex >= 0 ? authority.substring(0, userInfoIndex) : ''
    const rawHostPort = userInfoIndex >= 0 ? authority.substring(userInfoIndex + 1) : authority
    let username = ''
    let password = ''
    if (userInfoIndex >= 0) {
      const separator = rawUserInfo.indexOf(':')
      username = separator >= 0 ? rawUserInfo.substring(0, separator) : rawUserInfo
      password = separator >= 0 ? rawUserInfo.substring(separator + 1) : ''
    }
    let hostname = ''
    let port = ''
    if (rawHostPort.startsWith('[')) {
      const closingBracket = rawHostPort.indexOf(']')
      if (closingBracket <= 1) {
        return undefined
      }
      hostname = rawHostPort.substring(1, closingBracket)
      const suffix = rawHostPort.substring(closingBracket + 1)
      if (suffix.length > 0) {
        if (!suffix.startsWith(':')) {
          return undefined
        }
        port = suffix.substring(1)
      }
    } else {
      const colonIndex = rawHostPort.lastIndexOf(':')
      if (colonIndex >= 0 && rawHostPort.indexOf(':') === colonIndex) {
        hostname = rawHostPort.substring(0, colonIndex)
        port = rawHostPort.substring(colonIndex + 1)
      } else {
        hostname = rawHostPort
      }
    }
    hostname = hostname.toLowerCase().replace(/\.$/, '')
    if (hostname.length === 0 || this.containsUnsafeUrlCharacters(hostname) ||
      (port.length > 0 && !/^\d+$/.test(port))) {
      return undefined
    }
    const portNumber = port.length > 0 ? Number(port) : 0
    if (port.length > 0 && (!Number.isInteger(portNumber) || portNumber <= 0 || portNumber > 65535)) {
      return undefined
    }
    if ((protocol === 'http:' && port === '80') || (protocol === 'https:' && port === '443')) {
      port = ''
    }
    const pathname = this.normalizePath(`${match[3] || '/'}`)
    if (pathname.length === 0) {
      return undefined
    }
    return new FeiniuVideoParsedUrl(
      protocol,
      hostname,
      port,
      pathname,
      `${match[4] || ''}`,
      username,
      password
    )
  }

  private withoutFragment(value: string): string {
    const hashIndex = value.indexOf('#')
    return hashIndex >= 0 ? value.substring(0, hashIndex) : value
  }

  private baseDirectory(pathname: string): string {
    const lastSlash = pathname.lastIndexOf('/')
    return lastSlash >= 0 ? pathname.substring(0, lastSlash + 1) : '/'
  }

  private normalizePath(path: string): string {
    if (!path.startsWith('/') || path.indexOf('\\') >= 0) {
      return ''
    }
    const preserveTrailingSlash = path.length > 1 && path.endsWith('/')
    const output: string[] = []
    path.split('/').forEach((segment: string) => {
      if (segment.length === 0 || segment === '.') {
        return
      }
      if (segment === '..') {
        if (output.length > 0) {
          output.pop()
        }
        return
      }
      output.push(segment)
    })
    const normalized = `/${output.join('/')}`
    return preserveTrailingSlash && normalized !== '/' ? `${normalized}/` : normalized
  }

  private containsUnsafeUrlCharacters(value: string): boolean {
    for (let index = 0; index < value.length; index += 1) {
      const code = value.charCodeAt(index)
      if (code <= 32 || code === 127) {
        return true
      }
    }
    return false
  }

  private decodeQueryPart(value: string): string {
    const plusNormalized = value.replace(/\+/g, ' ')
    try {
      return decodeURIComponent(plusNormalized)
    } catch (_) {
      return plusNormalized
    }
  }

  private hasUserInfo(parsedUrl: FeiniuVideoParsedUrl): boolean {
    return parsedUrl.username.trim().length > 0 || parsedUrl.password.trim().length > 0
  }

  private isSameOrigin(leftUrl: string, rightUrl: string): boolean {
    const left = this.parseHttpUrl(leftUrl)
    const right = this.parseHttpUrl(rightUrl)
    return left !== undefined && right !== undefined &&
      left.origin.toLowerCase() === right.origin.toLowerCase()
  }

  private normalizedHostname(parsedUrl: FeiniuVideoParsedUrl): string {
    return parsedUrl.hostname.toLowerCase()
  }

  private baseUrlFromUrl(url: string): string {
    const parsedUrl = this.parseHttpUrl(url)
    return parsedUrl === undefined || this.hasUserInfo(parsedUrl) ? '' : parsedUrl.origin
  }

  private routeForBaseUrl(baseUrl: string, fallback: MediaConnectionRoute): MediaConnectionRoute {
    const parsedUrl = this.parseHttpUrl(baseUrl)
    if (parsedUrl === undefined) {
      return fallback
    }
    const knownCandidate = this.resolution.orderedCandidates.find((candidate: FeiniuVideoHostCandidate) => {
      const parsedCandidate = this.parseHttpUrl(candidate.baseUrl)
      return parsedCandidate !== undefined &&
        parsedCandidate.origin.toLowerCase() === parsedUrl.origin.toLowerCase()
    })
    if (knownCandidate !== undefined) {
      return knownCandidate.route
    }
    const hostname = this.normalizedHostname(parsedUrl)
    const relayHost = hostname === 'fnos.net' || hostname.endsWith('.fnos.net') ||
      hostname === '5ddd.com' || hostname.endsWith('.5ddd.com')
    return relayHost
      ? MediaConnectionRoute.Relay
      : fallback
  }

  private promoteCandidate(candidate: FeiniuVideoHostCandidate): void {
    const fallbacks: FeiniuVideoHostCandidate[] = []
    this.resolution.orderedCandidates.forEach((current: FeiniuVideoHostCandidate) => {
      if (current.baseUrl !== candidate.baseUrl &&
        fallbacks.find((item: FeiniuVideoHostCandidate) => item.baseUrl === current.baseUrl) === undefined) {
        fallbacks.push(current)
      }
    })
    this.resolution = new FeiniuVideoHostResolution(candidate, fallbacks)
  }

  private appendQuery(path: string, parameters: FeiniuVideoQueryParameter[]): string {
    if (parameters.length === 0) {
      return path
    }
    const sorted = parameters.slice()
    sorted.sort((left: FeiniuVideoQueryParameter, right: FeiniuVideoQueryParameter) =>
      left.name.localeCompare(right.name))
    const parts: string[] = []
    sorted.forEach((parameter: FeiniuVideoQueryParameter) => {
      if (parameter.name.length > 0) {
        parts.push(`${encodeURIComponent(parameter.name)}=${encodeURIComponent(parameter.value)}`)
      }
    })
    return parts.length > 0 ? `${path}${path.indexOf('?') >= 0 ? '&' : '?'}${parts.join('&')}` : path
  }

  private resolveOauthBaseUrl(oauthUrl: string): string {
    const trimmedOauth = oauthUrl.trim()
    if (trimmedOauth.length > 0 && trimmedOauth !== '://' &&
      (trimmedOauth.startsWith('http://') || trimmedOauth.startsWith('https://'))) {
      return trimmedOauth.replace(/\/$/, '')
    }
    return this.resolution.preferredBaseUrl
  }

  private async genAuthx(path: string, parameters: FeiniuVideoQueryParameter[], bodyText: string): Promise<string> {
    const nonce = `${Math.floor(this.runtime.random() * 900000) + 100000}`
    const timestamp = `${this.runtime.now()}`
    const dataMd5 = await this.dataMd5(parameters, bodyText)
    const signText = `${FeiniuVideoApiConstants.API_KEY}_${path}_${nonce}_${timestamp}_${dataMd5}_` +
      `${FeiniuVideoApiConstants.API_SECRET}`
    const sign = await this.digest.md5(signText)
    return `nonce=${nonce}&timestamp=${timestamp}&sign=${sign}`
  }

  private async dataMd5(parameters: FeiniuVideoQueryParameter[], bodyText: string): Promise<string> {
    if (bodyText.length > 0) {
      return await this.digest.md5(bodyText)
    }
    if (parameters.length > 0) {
      const sorted = parameters.slice()
      sorted.sort((left: FeiniuVideoQueryParameter, right: FeiniuVideoQueryParameter) =>
        left.name.localeCompare(right.name))
      const parts: string[] = []
      sorted.forEach((parameter: FeiniuVideoQueryParameter) => {
        if (parameter.name.length > 0) {
          parts.push(`${parameter.name}=${parameter.value}`)
        }
      })
      return await this.digest.md5(parts.join('&'))
    }
    return await this.digest.md5('')
  }
}
