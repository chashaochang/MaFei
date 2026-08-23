import {
  FeiniuVideoAddressInput,
  FeiniuVideoAddressKind,
  FeiniuVideoAddressResolution,
  FeiniuVideoHostCandidate,
  FeiniuVideoHostResolution,
  FeiniuVideoRequestError,
  FeiniuVideoRequestFailureKind,
  MediaConnectionRoute
} from './FeiniuVideoApiModels'
import {
  FeiniuVideoAddressResolutionSource,
  FeiniuVideoHostResolutionSource
} from './FeiniuVideoResolutionSource'
import { FnConnectApi, FnConnectResolveResult } from '../upstream/common/FnConnectApi'
import { FnosHostResolver } from '../upstream/common/FnosHostResolver'
import { FeiniuVideoConnectionPreferenceStore } from './FeiniuVideoConnectionPreferenceStore'

export class FeiniuVideoUpstreamAddressResolver implements FeiniuVideoAddressResolutionSource {
  async resolve(rawValue: string): Promise<FeiniuVideoAddressResolution> {
    const originalAddress = rawValue.trim()
    if (originalAddress.length === 0) {
      throw new FeiniuVideoRequestError(
        FeiniuVideoRequestFailureKind.InvalidRequest,
        'FNID or server address is required'
      )
    }
    if (FnosHostResolver.isFnConnectLike(originalAddress)) {
      return this.resolveFnConnect(originalAddress)
    }
    const directBaseUrl = this.normalizeDirectBaseUrl(originalAddress)
    if (directBaseUrl.length === 0) {
      throw new FeiniuVideoRequestError(
        FeiniuVideoRequestFailureKind.InvalidRequest,
        'Server address is invalid'
      )
    }
    const input = new FeiniuVideoAddressInput(
      originalAddress,
      FeiniuVideoAddressKind.Direct,
      '',
      directBaseUrl
    )
    return new FeiniuVideoAddressResolution(input, [
      new FeiniuVideoHostCandidate(directBaseUrl, MediaConnectionRoute.Direct)
    ])
  }

  private normalizeDirectBaseUrl(address: string): string {
    const normalizedAddress = address.trim().replace(/\/+$/, '')
    const lowerAddress = normalizedAddress.toLowerCase()
    const hasExplicitScheme = lowerAddress.startsWith('http://') || lowerAddress.startsWith('https://') ||
      lowerAddress.startsWith('ws://') || lowerAddress.startsWith('wss://')
    if (!hasExplicitScheme) {
      return FnosHostResolver.normalizeHttpBaseUrl(normalizedAddress, false)
    }

    const secure = lowerAddress.startsWith('https://') || lowerAddress.startsWith('wss://')
    const scheme = secure ? 'https://' : 'http://'
    const withoutScheme = normalizedAddress.replace(/^(?:https?|wss?):\/\//i, '')
    const pathStart = withoutScheme.indexOf('/')
    const authority = pathStart >= 0 ? withoutScheme.substring(0, pathStart) : withoutScheme
    const path = pathStart >= 0 ? withoutScheme.substring(pathStart) : ''
    if (authority.length === 0) {
      return ''
    }
    if (this.hasExplicitPort(authority)) {
      return `${scheme}${authority}${path}`
    }
    return `${scheme}${authority}:${secure ? '443' : '80'}${path}`
  }

  private hasExplicitPort(authority: string): boolean {
    if (authority.startsWith('[')) {
      const bracketEnd = authority.indexOf(']')
      return bracketEnd >= 0 && authority.substring(bracketEnd + 1).startsWith(':')
    }
    const separator = authority.lastIndexOf(':')
    if (separator < 0) {
      return false
    }
    const port = authority.substring(separator + 1)
    return port.length > 0 && /^\d+$/.test(port)
  }

  private async resolveFnConnect(originalAddress: string): Promise<FeiniuVideoAddressResolution> {
    const resolved = await FnConnectApi.resolveFnTargets(
      originalAddress,
      FeiniuVideoConnectionPreferenceStore.routePreference(),
      true
    )
    const input = new FeiniuVideoAddressInput(
      originalAddress,
      FeiniuVideoAddressKind.FnId,
      resolved.fnId
    )
    const candidates: FeiniuVideoHostCandidate[] = []
    this.appendCandidate(candidates, resolved.preferredBaseUrl, this.route(resolved.preferredRoute))
    resolved.fallbackBaseUrls.forEach((baseUrl: string): void => {
      this.appendCandidate(candidates, baseUrl, this.inferRoute(baseUrl))
    })
    if (candidates.length === 0) {
      throw new FeiniuVideoRequestError(
        FeiniuVideoRequestFailureKind.Connection,
        'FNID did not resolve to a usable address'
      )
    }
    return new FeiniuVideoAddressResolution(input, candidates)
  }

  private appendCandidate(candidates: FeiniuVideoHostCandidate[], baseUrl: string,
    route: MediaConnectionRoute): void {
    const normalized = baseUrl.trim().replace(/\/+$/, '')
    if (normalized.length === 0 || candidates.find((item: FeiniuVideoHostCandidate): boolean =>
      item.baseUrl === normalized) !== undefined) {
      return
    }
    candidates.push(new FeiniuVideoHostCandidate(normalized, route))
  }

  private route(value: string): MediaConnectionRoute {
    if (value === 'lan') {
      return MediaConnectionRoute.Local
    }
    if (value === 'public') {
      return MediaConnectionRoute.Public
    }
    return MediaConnectionRoute.Relay
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
}

export class FeiniuVideoUpstreamHostResolver implements FeiniuVideoHostResolutionSource {
  async resolve(candidates: FeiniuVideoHostCandidate[]): Promise<FeiniuVideoHostResolution> {
    if (candidates.length === 0) {
      throw new FeiniuVideoRequestError(
        FeiniuVideoRequestFailureKind.InvalidRequest,
        'At least one Feiniu Video host candidate is required'
      )
    }
    return new FeiniuVideoHostResolution(candidates[0], candidates.slice(1))
  }
}
