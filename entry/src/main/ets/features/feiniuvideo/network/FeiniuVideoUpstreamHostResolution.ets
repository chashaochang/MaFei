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
    const preferHttps = originalAddress.startsWith('https://') || originalAddress.startsWith('wss://')
    const directBaseUrl = FnosHostResolver.normalizeHttpBaseUrl(originalAddress, preferHttps)
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

  private async resolveFnConnect(originalAddress: string): Promise<FeiniuVideoAddressResolution> {
    const resolved = await FnConnectApi.resolveFnTargets(originalAddress)
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
