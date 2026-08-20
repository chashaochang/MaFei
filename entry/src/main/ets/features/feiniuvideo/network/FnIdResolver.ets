import {
  FeiniuVideoAddressInput,
  FeiniuVideoAddressKind,
  FeiniuVideoAddressResolution,
  FeiniuVideoHostCandidate,
  FeiniuVideoRequestError,
  FeiniuVideoRequestFailureKind,
  FnIdResolveData,
  FnIdResolveEnvelope,
  FnIdResolveSource,
  MediaConnectionRoute
} from './FeiniuVideoApiModels'
import { FeiniuVideoAddressResolutionSource } from './FeiniuVideoResolutionSource'

const DEFAULT_HTTP_PORT: number = 5666
const DEFAULT_HTTPS_PORT: number = 5667

export class FnIdResolver implements FeiniuVideoAddressResolutionSource {
  private readonly source?: FnIdResolveSource

  constructor(source?: FnIdResolveSource) {
    this.source = source
  }

  parseInput(rawValue: string): FeiniuVideoAddressInput {
    const originalAddress = rawValue.trim()
    if (originalAddress.length === 0) {
      throw this.invalidRequest('FNID or server address is required')
    }

    if (this.isFnConnectLike(originalAddress)) {
      const fnId = this.extractFnId(originalAddress)
      if (fnId.length === 0) {
        throw this.invalidRequest('FNID is required')
      }
      return new FeiniuVideoAddressInput(originalAddress, FeiniuVideoAddressKind.FnId, fnId)
    }
    return new FeiniuVideoAddressInput(
      originalAddress,
      FeiniuVideoAddressKind.Direct,
      '',
      this.normalizeDirectBaseUrl(originalAddress)
    )
  }

  async resolve(rawValue: string): Promise<FeiniuVideoAddressResolution> {
    const input = this.parseInput(rawValue)
    if (input.kind === FeiniuVideoAddressKind.Direct) {
      return new FeiniuVideoAddressResolution(input, [
        new FeiniuVideoHostCandidate(input.directBaseUrl, MediaConnectionRoute.Direct)
      ])
    }
    if (this.source === undefined) {
      throw this.invalidRequest('FNID resolve source is not configured')
    }

    try {
      const envelope: FnIdResolveEnvelope = await this.source.resolve(input.fnId)
      const data = this.requireResolveData(envelope)
      const candidates = this.buildCandidates(data)
      if (candidates.length === 0) {
        throw new FeiniuVideoRequestError(
          FeiniuVideoRequestFailureKind.InvalidResponse,
          'FNID response contains no usable host candidate'
        )
      }
      return new FeiniuVideoAddressResolution(input, candidates)
    } catch (_error) {
      return new FeiniuVideoAddressResolution(input, this.buildRelayFallbackCandidates(input.fnId))
    }
  }

  buildCandidates(data: FnIdResolveData): FeiniuVideoHostCandidate[] {
    const candidates: FeiniuVideoHostCandidate[] = []
    const httpPort = data.port?.httpPort || DEFAULT_HTTP_PORT
    const httpsPort = data.port?.httpsPort || DEFAULT_HTTPS_PORT
    const lanIpv4List = data.ipv4 || []
    lanIpv4List.forEach((lanIpv4: string) => {
      const normalized = lanIpv4.trim()
      this.appendCandidate(candidates, normalized.length > 0 ? `http://${normalized}:${httpPort}` : '',
        MediaConnectionRoute.Local)
    })
    const publicIpv4List = data.publicIpv4 || []
    publicIpv4List.forEach((publicIpv4: string) => {
      const normalized = publicIpv4.trim()
      this.appendCandidate(candidates, normalized.length > 0 ? `https://${normalized}:${httpsPort}` : '',
        MediaConnectionRoute.Public)
    })
    const publicIpv6List = data.publicIpv6 || []
    publicIpv6List.forEach((publicIpv6: string) => {
      const normalized = publicIpv6.trim()
      this.appendCandidate(candidates, normalized.length > 0 ? `https://[${normalized}]:${httpsPort}` : '',
        MediaConnectionRoute.Public)
    })
    this.appendCandidate(candidates, this.pickRelayBaseUrl(data, httpsPort), MediaConnectionRoute.Relay)
    return candidates
  }

  buildRelayFallbackCandidates(fnId: string): FeiniuVideoHostCandidate[] {
    const normalizedFnId = this.extractFnId(fnId)
    if (normalizedFnId.length === 0) {
      throw this.invalidRequest('FNID is required')
    }
    return [
      new FeiniuVideoHostCandidate(`https://${normalizedFnId}.5ddd.com`, MediaConnectionRoute.Relay),
      new FeiniuVideoHostCandidate(`https://${normalizedFnId}.fnos.net`, MediaConnectionRoute.Relay)
    ]
  }

  extractFnId(rawValue: string): string {
    const trimmed = rawValue.trim()
      .replace(/^https?:\/\//, '')
      .replace(/^wss?:\/\//, '')
      .replace(/\/$/, '')
    if (trimmed.startsWith('5ddd.com/')) {
      return trimmed.substring('5ddd.com/'.length).split('/')[0]
    }
    if (trimmed.endsWith('.fnos.net')) {
      return trimmed.substring(0, trimmed.indexOf('.fnos.net'))
    }
    return trimmed.split('/')[0]
  }

  private requireResolveData(envelope: FnIdResolveEnvelope): FnIdResolveData {
    if (envelope.code !== 0) {
      const message = envelope.msg?.trim() || 'FNID resolve request was rejected'
      throw new FeiniuVideoRequestError(FeiniuVideoRequestFailureKind.Business, message)
    }
    if (envelope.data === undefined) {
      throw new FeiniuVideoRequestError(
        FeiniuVideoRequestFailureKind.InvalidResponse,
        'FNID resolve response is missing data'
      )
    }
    return envelope.data
  }

  private normalizeDirectBaseUrl(value: string): string {
    if (value.indexOf('?') >= 0 || value.indexOf('#') >= 0) {
      throw this.invalidRequest('Server address must be an HTTP or HTTPS base URL')
    }
    const normalized = this.normalizeHttpBaseUrl(value)
    const schemeEnd = normalized.indexOf('://') + 3
    const authorityAndPath = normalized.substring(schemeEnd)
    const authority = this.authorityOf(authorityAndPath)
    if (authority.length === 0 || authority.indexOf('@') >= 0 || this.containsWhitespace(authority)) {
      throw this.invalidRequest('Server address host is invalid')
    }
    return normalized
  }

  // Copied from fnOS FnosHostResolver and narrowed to the video login input.
  private normalizeHttpBaseUrl(input: string, preferHttps: boolean = false): string {
    const trimmed = this.trimTrailingSlashes(input.trim())
    if (trimmed.length === 0) {
      return ''
    }
    if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) {
      return this.normalizePrefixedHttpBaseUrl(trimmed, preferHttps)
    }
    if (trimmed.startsWith('ws://') || trimmed.startsWith('wss://')) {
      const httpScheme = trimmed.startsWith('wss://') ? 'https://' : 'http://'
      return this.normalizePrefixedHttpBaseUrl(
        `${httpScheme}${trimmed.replace(/^wss?:\/\//, '')}`,
        preferHttps
      )
    }
    const scheme = preferHttps ? 'https://' : 'http://'
    if (this.hasExplicitPort(trimmed)) {
      return `${scheme}${trimmed}`
    }
    return `${scheme}${trimmed}:${preferHttps ? '5667' : '5666'}`
  }

  private normalizePrefixedHttpBaseUrl(input: string, preferHttps: boolean): string {
    const scheme = input.startsWith('https://') || (preferHttps && !input.startsWith('http://'))
      ? 'https://'
      : 'http://'
    const withoutScheme = this.trimTrailingSlashes(input.replace(/^https?:\/\//, ''))
    if (this.isRelayPath(withoutScheme) || this.isRelayDomain(withoutScheme)) {
      return `${scheme === 'http://' ? 'https://' : scheme}${withoutScheme}`
    }
    if (this.hasExplicitPort(withoutScheme)) {
      return `${scheme}${withoutScheme}`
    }
    return `${scheme}${withoutScheme}:${scheme === 'https://' ? '5667' : '5666'}`
  }

  private pickRelayBaseUrl(data: FnIdResolveData, httpsPort: number): string {
    const relayHost = data.fn && data.fn.length > 0 ? data.fn[0].trim() : ''
    if (relayHost.length === 0) {
      return ''
    }
    if (relayHost.endsWith(':443')) {
      return `https://${relayHost.replace(/:443$/, '')}`
    }
    return relayHost.indexOf(':') >= 0 ? `https://${relayHost}` : `https://${relayHost}:${httpsPort}`
  }

  private appendCandidate(targets: FeiniuVideoHostCandidate[], baseUrl: string,
    route: MediaConnectionRoute): void {
    const normalized = this.trimTrailingSlashes(baseUrl.trim())
    if (normalized.length === 0) {
      return
    }
    const duplicate = targets.find((candidate: FeiniuVideoHostCandidate) => candidate.baseUrl === normalized)
    if (duplicate === undefined) {
      targets.push(new FeiniuVideoHostCandidate(normalized, route))
    }
  }

  private isFnConnectLike(input: string): boolean {
    const trimmed = this.trimTrailingSlashes(input.trim())
    if (trimmed.length === 0) {
      return false
    }
    if (this.isFnId(trimmed)) {
      return true
    }
    if (trimmed.startsWith('http://') || trimmed.startsWith('https://') ||
      trimmed.startsWith('ws://') || trimmed.startsWith('wss://')) {
      const withoutScheme = trimmed.replace(/^[a-z]+:\/\//i, '')
      return this.isRelayPath(withoutScheme) || this.isRelayDomain(withoutScheme)
    }
    return this.isRelayPath(trimmed) || this.isRelayDomain(trimmed)
  }

  private isFnId(input: string): boolean {
    return /^[A-Za-z0-9_-]+$/.test(input)
  }

  private isRelayDomain(input: string): boolean {
    return /(^|\/)[^/]*\.fnos\.net(?::\d+)?$/i.test(input) ||
      /\.fnos\.net(?::\d+)?(\/|$)/i.test(input) ||
      /(^|\/)[^/]*\.5ddd\.com(?::\d+)?$/i.test(input) ||
      /\.5ddd\.com(?::\d+)?(\/|$)/i.test(input)
  }

  private isRelayPath(input: string): boolean {
    return /^5ddd\.com\/[^/]+$/i.test(input)
  }

  private hasExplicitPort(input: string): boolean {
    const authority = input.split('/')[0]
    const colonIndex = authority.lastIndexOf(':')
    if (colonIndex <= 0) {
      return false
    }
    return /^\d+$/.test(authority.substring(colonIndex + 1))
  }

  private authorityOf(value: string): string {
    const slashIndex = value.indexOf('/')
    return slashIndex >= 0 ? value.substring(0, slashIndex) : value
  }

  private trimTrailingSlashes(value: string): string {
    let normalized = value
    while (normalized.endsWith('/')) {
      normalized = normalized.substring(0, normalized.length - 1)
    }
    return normalized
  }

  private containsWhitespace(value: string): boolean {
    for (let index = 0; index < value.length; index += 1) {
      const code = value.charCodeAt(index)
      if (code === 9 || code === 10 || code === 13 || code === 32) {
        return true
      }
    }
    return false
  }

  private invalidRequest(message: string): FeiniuVideoRequestError {
    return new FeiniuVideoRequestError(FeiniuVideoRequestFailureKind.InvalidRequest, message)
  }
}
