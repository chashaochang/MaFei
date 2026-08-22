import { MediaConnectionRoute } from '../../../media/MediaProviderSession'

export { MediaConnectionRoute }

export enum FeiniuVideoAddressKind {
  FnId = 'fnid',
  Direct = 'direct'
}

export enum FeiniuVideoRoutePreference {
  Public = 'public',
  Relay = 'relay',
  Speed = 'speed'
}

export enum FeiniuVideoRequestFailureKind {
  Connection = 'connection',
  Authentication = 'authentication',
  Business = 'business',
  InvalidResponse = 'invalid_response',
  InvalidRequest = 'invalid_request',
  Unknown = 'unknown'
}

export interface FnIdResolvePortInfo {
  readonly httpPort?: number
  readonly httpsPort?: number
}

export interface FnIdResolveData {
  readonly ipv4?: string[]
  readonly ipv6?: string[]
  readonly publicIpv4?: string[]
  readonly publicIpv6?: string[]
  readonly fn?: string[]
  readonly port?: FnIdResolvePortInfo
}

export interface FnIdResolveEnvelope {
  readonly code?: number
  readonly msg?: string
  readonly data?: FnIdResolveData
}

export interface FnIdResolveSource {
  resolve(fnId: string): Promise<FnIdResolveEnvelope>
}

export class FeiniuVideoRequestError extends Error {
  readonly kind: FeiniuVideoRequestFailureKind

  constructor(kind: FeiniuVideoRequestFailureKind, message: string) {
    super(message)
    this.kind = kind
  }

  allowsHostFallback(): boolean {
    return this.kind === FeiniuVideoRequestFailureKind.Connection
  }
}

export class FeiniuVideoAuthenticationError extends FeiniuVideoRequestError {
  readonly expired: boolean

  constructor(message: string, expired: boolean = false) {
    super(FeiniuVideoRequestFailureKind.Authentication, message)
    this.expired = expired
  }
}

export function isFeiniuVideoAuthExpired(error: Error): boolean {
  return error instanceof FeiniuVideoAuthenticationError && error.expired
}

export class FeiniuVideoConnectionError extends FeiniuVideoRequestError {
  constructor(message: string) {
    super(FeiniuVideoRequestFailureKind.Connection, message)
  }
}

export function isFeiniuVideoConnectionFailure(error: Error): boolean {
  return error instanceof FeiniuVideoRequestError && error.allowsHostFallback()
}

export class FeiniuVideoAddressInput {
  readonly originalAddress: string
  readonly kind: FeiniuVideoAddressKind
  readonly fnId: string
  readonly directBaseUrl: string

  constructor(originalAddress: string, kind: FeiniuVideoAddressKind,
    fnId: string = '', directBaseUrl: string = '') {
    this.originalAddress = originalAddress.trim()
    this.kind = kind
    this.fnId = fnId.trim()
    this.directBaseUrl = directBaseUrl.trim()
  }
}

export class FeiniuVideoHostCandidate {
  readonly baseUrl: string
  readonly route: MediaConnectionRoute

  constructor(baseUrl: string, route: MediaConnectionRoute) {
    this.baseUrl = baseUrl.trim()
    this.route = route
  }

  get requiresRelayCookie(): boolean {
    return this.route === MediaConnectionRoute.Relay
  }
}

export class FeiniuVideoAddressResolution {
  readonly input: FeiniuVideoAddressInput
  readonly candidates: FeiniuVideoHostCandidate[]

  constructor(input: FeiniuVideoAddressInput, candidates: FeiniuVideoHostCandidate[]) {
    this.input = input
    this.candidates = candidates.slice()
  }
}

export class FeiniuVideoHostProbeResult {
  readonly reachable: boolean
  readonly elapsedMs: number

  constructor(reachable: boolean, elapsedMs: number = 0) {
    this.reachable = reachable
    this.elapsedMs = Number.isFinite(elapsedMs) && elapsedMs >= 0 ? Math.floor(elapsedMs) : 0
  }
}

export interface FeiniuVideoHostProbe {
  probe(baseUrl: string): Promise<FeiniuVideoHostProbeResult>
}

export class FeiniuVideoHostResolution {
  readonly preferred: FeiniuVideoHostCandidate
  readonly fallbacks: FeiniuVideoHostCandidate[]

  constructor(preferred: FeiniuVideoHostCandidate, fallbacks: FeiniuVideoHostCandidate[]) {
    this.preferred = preferred
    this.fallbacks = fallbacks.slice()
  }

  get preferredBaseUrl(): string {
    return this.preferred.baseUrl
  }

  get preferredRoute(): MediaConnectionRoute {
    return this.preferred.route
  }

  get usedLocal(): boolean {
    return this.preferred.route === MediaConnectionRoute.Local
  }

  get fallbackBaseUrls(): string[] {
    return this.fallbacks.map((candidate: FeiniuVideoHostCandidate) => candidate.baseUrl)
  }

  get orderedCandidates(): FeiniuVideoHostCandidate[] {
    return [this.preferred].concat(this.fallbacks)
  }
}

export interface FeiniuVideoHostRequest<T> {
  execute(candidate: FeiniuVideoHostCandidate): Promise<T>
}

export class FeiniuVideoHostRequestResult<T> {
  readonly value: T
  readonly candidate: FeiniuVideoHostCandidate

  constructor(value: T, candidate: FeiniuVideoHostCandidate) {
    this.value = value
    this.candidate = candidate
  }
}

export enum FeiniuVideoHttpMethod {
  Get = 'GET',
  Post = 'POST',
  Put = 'PUT',
  Delete = 'DELETE'
}

export class FeiniuVideoHttpHeaders {
  contentType: string = ''
  accept: string = ''
  userAgent: string = ''
  trimClient: string = ''
  trimClientVersion: string = ''
  authx: string = ''
  authorization: string = ''
  cookie: string = ''
  fnSign: string = ''
}

export class FeiniuVideoHttpRequest {
  readonly url: string
  readonly method: FeiniuVideoHttpMethod
  readonly headers: FeiniuVideoHttpHeaders
  readonly bodyText: string
  readonly readTimeoutMs: number
  readonly connectTimeoutMs: number

  constructor(url: string, method: FeiniuVideoHttpMethod, headers: FeiniuVideoHttpHeaders,
    bodyText: string = '', readTimeoutMs: number = 15000, connectTimeoutMs: number = 10000) {
    this.url = url
    this.method = method
    this.headers = headers
    this.bodyText = bodyText
    this.readTimeoutMs = readTimeoutMs
    this.connectTimeoutMs = connectTimeoutMs
  }
}

export class FeiniuVideoHttpResponseHeaders {
  readonly location: string
  readonly setCookieValues: string[]

  constructor(location: string = '', setCookieValues: string[] = []) {
    this.location = location.trim()
    this.setCookieValues = setCookieValues.slice()
  }
}

export class FeiniuVideoHttpResponse {
  readonly statusCode: number
  readonly bodyText: string
  readonly headers: FeiniuVideoHttpResponseHeaders

  constructor(statusCode: number, bodyText: string,
    headers: FeiniuVideoHttpResponseHeaders = new FeiniuVideoHttpResponseHeaders()) {
    this.statusCode = statusCode
    this.bodyText = bodyText
    this.headers = headers
  }
}

export interface FeiniuVideoHttpTransport {
  request(request: FeiniuVideoHttpRequest): Promise<FeiniuVideoHttpResponse>
}

export interface FeiniuVideoRuntimeSource {
  now(): number
  random(): number
}

export interface FeiniuVideoDigestSource {
  md5(value: string): Promise<string>
  sha256(value: string): Promise<string>
}

export class FeiniuVideoApiCredentials {
  readonly token: string
  readonly cookie: string

  constructor(token: string = '', cookie: string = '') {
    this.token = token.trim()
    this.cookie = cookie.trim()
  }
}

export class FeiniuVideoQueryParameter {
  readonly name: string
  readonly value: string

  constructor(name: string, value: string | number | boolean) {
    this.name = name.trim()
    this.value = `${value}`
  }
}

export class FeiniuVideoApiRequest {
  readonly path: string
  readonly method: FeiniuVideoHttpMethod
  readonly bodyText: string
  readonly parameters: FeiniuVideoQueryParameter[]
  readonly requireMediaToken: boolean
  readonly readTimeoutMs: number
  readonly includeStoredMediaToken: boolean
  readonly includeStoredCookie: boolean

  constructor(path: string, method: FeiniuVideoHttpMethod, bodyText: string = '',
    parameters: FeiniuVideoQueryParameter[] = [], requireMediaToken: boolean = false,
    readTimeoutMs: number = 15000, includeStoredMediaToken: boolean = true,
    includeStoredCookie: boolean = true) {
    this.path = path.trim()
    this.method = method
    this.bodyText = bodyText
    this.parameters = parameters.slice()
    this.requireMediaToken = requireMediaToken
    this.readTimeoutMs = readTimeoutMs
    this.includeStoredMediaToken = includeStoredMediaToken
    this.includeStoredCookie = includeStoredCookie
  }
}

// Video-login subset copied from fnOS/entity/FnMediaResp.ets.
export interface FnMediaOauthConfig {
  app_id?: string
  url?: string
}

export interface FnMediaSysConfigData {
  nas_oauth?: FnMediaOauthConfig
  server_name?: string
}

export interface FnMediaSysConfigResp {
  code: number
  msg: string
  data: FnMediaSysConfigData
}

export interface FnMediaAuthReq {
  source: string
  code: string
}

export interface FnMediaAuthTokenData {
  token?: string
}

export interface FnMediaAuthResp {
  code: number
  msg: string
  data?: FnMediaAuthTokenData
  token?: string
}

export interface FnMediaLoginReq {
  username: string
  password: string
  app_name: string
}

export interface FnMediaLoginResp {
  code: number
  msg: string
  data?: FnMediaAuthTokenData
  token?: string
}

export interface FnMediaUserInfoData {
  avatar?: string
  nickname?: string
  username?: string
  user_id?: string
  is_admin?: number
  sources?: FnMediaUserSource[]
}

export interface FnMediaUserSource {
  source?: string
  source_id?: string
  source_name?: string
}

export interface FnMediaUserInfoResp {
  code: number
  msg: string
  data?: FnMediaUserInfoData
}
