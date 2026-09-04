import { http } from '@kit.NetworkKit'
import {
  FeiniuVideoHostCandidate,
  FeiniuVideoHostProbe,
  FeiniuVideoHostProbeResult,
  FeiniuVideoHostRequest,
  FeiniuVideoHostRequestResult,
  FeiniuVideoHostResolution,
  FeiniuVideoRequestError,
  FeiniuVideoRequestFailureKind,
  FeiniuVideoRoutePreference,
  MediaConnectionRoute
} from './FeiniuVideoApiModels'
import { FeiniuVideoHostResolutionSource } from './FeiniuVideoResolutionSource'
import { FeiniuVideoConnectionPreferenceStore } from './FeiniuVideoConnectionPreferenceStore'

class FnConnectProbeResult {
  baseUrl: string = ''
  route: MediaConnectionRoute = MediaConnectionRoute.Relay
  reachable: boolean = false
  elapsedMs: number = 0
  originalIndex: number = 0
}

export class FeiniuVideoHttpHostProbe implements FeiniuVideoHostProbe {
  static readonly PROBE_PATH: string = '/license/v1/device/baseInfo'
  static readonly CONNECT_TIMEOUT_MS: number = 1200
  static readonly READ_TIMEOUT_MS: number = 1800

  static isReachableStatus(statusCode: number): boolean {
    return statusCode >= 200 && statusCode < 500
  }

  async probe(baseUrl: string): Promise<FeiniuVideoHostProbeResult> {
    const client = http.createHttp()
    const start = Date.now()
    try {
      const response = await client.request(`${baseUrl}${FeiniuVideoHttpHostProbe.PROBE_PATH}`, {
        method: http.RequestMethod.GET,
        expectDataType: http.HttpDataType.STRING,
        readTimeout: FeiniuVideoHttpHostProbe.READ_TIMEOUT_MS,
        connectTimeout: FeiniuVideoHttpHostProbe.CONNECT_TIMEOUT_MS,
        header: {
          'Accept': 'application/json,text/plain,*/*'
        }
      })
      return new FeiniuVideoHostProbeResult(
        FeiniuVideoHttpHostProbe.isReachableStatus(response.responseCode),
        Date.now() - start
      )
    } catch (_) {
      return new FeiniuVideoHostProbeResult(false, Date.now() - start)
    } finally {
      client.destroy()
    }
  }
}

export class FeiniuVideoHostResolver implements FeiniuVideoHostResolutionSource {
  private readonly probe: FeiniuVideoHostProbe
  private readonly routePreference?: FeiniuVideoRoutePreference
  private readonly p2pEnabled: boolean

  constructor(probe: FeiniuVideoHostProbe = new FeiniuVideoHttpHostProbe(),
    routePreference?: FeiniuVideoRoutePreference,
    p2pEnabled: boolean = true) {
    this.probe = probe
    this.routePreference = routePreference
    this.p2pEnabled = p2pEnabled
  }

  async resolve(candidates: FeiniuVideoHostCandidate[],
    preference?: FeiniuVideoRoutePreference): Promise<FeiniuVideoHostResolution> {
    const effectivePreference = preference || this.routePreference ||
      FeiniuVideoConnectionPreferenceStore.routePreference()
    const uniqueCandidates = this.uniqueCandidates(candidates)
    if (uniqueCandidates.length === 0) {
      throw new FeiniuVideoRequestError(
        FeiniuVideoRequestFailureKind.InvalidRequest,
        'At least one Feiniu Video host candidate is required'
      )
    }

    const preferredCandidates = this.applyP2pPreference(uniqueCandidates)
    const probes = await this.probeCandidates(preferredCandidates)
    const preferred = this.pickPreferredProbe(probes, preferredCandidates, effectivePreference)
    if (preferred.baseUrl.length === 0) {
      throw new FeiniuVideoRequestError(
        FeiniuVideoRequestFailureKind.Connection,
        'Feiniu Video has no host candidate to probe'
      )
    }

    const preferredCandidate = new FeiniuVideoHostCandidate(preferred.baseUrl, preferred.route)
    const fallbacks: FeiniuVideoHostCandidate[] = []
    this.sortedFallbackProbes(probes).forEach((probe: FnConnectProbeResult) => {
      this.appendFallback(fallbacks, preferredCandidate,
        new FeiniuVideoHostCandidate(probe.baseUrl, probe.route))
    })
    this.sortedFallbackCandidates(preferredCandidates).forEach((candidate: FeiniuVideoHostCandidate) => {
      this.appendFallback(fallbacks, preferredCandidate, candidate)
    })
    return new FeiniuVideoHostResolution(preferredCandidate, fallbacks)
  }

  async executeWithFallback<T>(resolution: FeiniuVideoHostResolution,
    request: FeiniuVideoHostRequest<T>): Promise<FeiniuVideoHostRequestResult<T>> {
    const candidates = resolution.orderedCandidates
    let lastRetryableError: Error | undefined
    for (let index = 0; index < candidates.length; index += 1) {
      const candidate = candidates[index]
      try {
        const value = await request.execute(candidate)
        return new FeiniuVideoHostRequestResult<T>(value, candidate)
      } catch (error) {
        const normalizedError = error as Error
        if (normalizedError instanceof FeiniuVideoRequestError &&
          (normalizedError.kind === FeiniuVideoRequestFailureKind.Authentication ||
            normalizedError.kind === FeiniuVideoRequestFailureKind.Business ||
            normalizedError.kind === FeiniuVideoRequestFailureKind.InvalidRequest)) {
          throw normalizedError
        }
        lastRetryableError = normalizedError
      }
    }
    throw lastRetryableError || new FeiniuVideoRequestError(
      FeiniuVideoRequestFailureKind.Connection,
      'All Feiniu Video host candidates failed to connect'
    )
  }

  private async probeCandidates(candidates: FeiniuVideoHostCandidate[]): Promise<FnConnectProbeResult[]> {
    const requests: Promise<FnConnectProbeResult>[] = []
    candidates.forEach((candidate: FeiniuVideoHostCandidate, index: number) => {
      requests.push(this.probeCandidate(candidate, index))
    })
    return Promise.all(requests)
  }

  private pickPreferredProbe(probes: FnConnectProbeResult[], candidates: FeiniuVideoHostCandidate[],
    preference: FeiniuVideoRoutePreference): FnConnectProbeResult {
    const reachable = probes.filter((probe: FnConnectProbeResult) => probe.reachable)
    this.sortProbesByPreference(reachable, preference)
    if (reachable.length > 0) {
      return reachable[0]
    }
    const fallback = new FnConnectProbeResult()
    const first = this.firstCandidateByPreference(candidates, preference)
    if (first !== undefined) {
      fallback.baseUrl = first.baseUrl
      fallback.route = first.route
    }
    return fallback
  }

  private applyP2pPreference(candidates: FeiniuVideoHostCandidate[]): FeiniuVideoHostCandidate[] {
    if (this.p2pEnabled) {
      return candidates
    }
    const filtered = candidates.filter((item: FeiniuVideoHostCandidate) =>
      item.route !== MediaConnectionRoute.Public)
    return filtered.length > 0 ? filtered : candidates
  }

  private sortProbesByPreference(probes: FnConnectProbeResult[], preference: FeiniuVideoRoutePreference): void {
    probes.sort((left: FnConnectProbeResult, right: FnConnectProbeResult) => {
      if (preference === FeiniuVideoRoutePreference.Speed) {
        const elapsedDifference = left.elapsedMs - right.elapsedMs
        return elapsedDifference !== 0 ? elapsedDifference : left.originalIndex - right.originalIndex
      }
      const routeDifference = this.routePriority(left.route, preference) -
        this.routePriority(right.route, preference)
      if (routeDifference !== 0) {
        return routeDifference
      }
      const elapsedDifference = left.elapsedMs - right.elapsedMs
      return elapsedDifference !== 0 ? elapsedDifference : left.originalIndex - right.originalIndex
    })
  }

  private firstCandidateByPreference(candidates: FeiniuVideoHostCandidate[],
    preference: FeiniuVideoRoutePreference): FeiniuVideoHostCandidate | undefined {
    if (candidates.length === 0) {
      return undefined
    }
    const sorted = candidates.slice()
    sorted.sort((left: FeiniuVideoHostCandidate, right: FeiniuVideoHostCandidate) => {
      return this.unreachableRoutePriority(left.route, preference) -
        this.unreachableRoutePriority(right.route, preference)
    })
    return sorted[0]
  }

  private sortedFallbackProbes(probes: FnConnectProbeResult[]): FnConnectProbeResult[] {
    const sorted = probes.slice()
    sorted.sort((left: FnConnectProbeResult, right: FnConnectProbeResult) => {
      if (left.reachable !== right.reachable) {
        return left.reachable ? -1 : 1
      }
      if (left.reachable) {
        if (left.elapsedMs !== right.elapsedMs) {
          return left.elapsedMs - right.elapsedMs
        }
        return this.remoteRoutePriority(left.route) - this.remoteRoutePriority(right.route)
      }
      return this.remoteRoutePriority(left.route) - this.remoteRoutePriority(right.route)
    })
    return sorted
  }

  private sortedFallbackCandidates(candidates: FeiniuVideoHostCandidate[]): FeiniuVideoHostCandidate[] {
    const sorted = candidates.slice()
    sorted.sort((left: FeiniuVideoHostCandidate, right: FeiniuVideoHostCandidate) => {
      return this.remoteRoutePriority(left.route) - this.remoteRoutePriority(right.route)
    })
    return sorted
  }

  private routePriority(route: MediaConnectionRoute, preference: FeiniuVideoRoutePreference): number {
    if (preference === FeiniuVideoRoutePreference.Public) {
      return route === MediaConnectionRoute.Public ? 0 :
        (route === MediaConnectionRoute.Relay ? 2 : 1)
    }
    if (preference === FeiniuVideoRoutePreference.Relay) {
      return route === MediaConnectionRoute.Relay ? 0 :
        (route === MediaConnectionRoute.Public ? 1 : 2)
    }
    return route === MediaConnectionRoute.Relay ? 2 :
      (route === MediaConnectionRoute.Public ? 1 : 0)
  }

  private unreachableRoutePriority(route: MediaConnectionRoute, preference: FeiniuVideoRoutePreference): number {
    if (preference === FeiniuVideoRoutePreference.Public) {
      return route === MediaConnectionRoute.Public ? 0 :
        (route === MediaConnectionRoute.Relay ? 1 : 2)
    }
    return this.remoteRoutePriority(route)
  }

  private remoteRoutePriority(route: MediaConnectionRoute): number {
    if (route === MediaConnectionRoute.Relay) {
      return 0
    }
    if (route === MediaConnectionRoute.Public) {
      return 1
    }
    if (route === MediaConnectionRoute.Direct) {
      return 2
    }
    return 3
  }

  private async probeCandidate(candidate: FeiniuVideoHostCandidate, index: number): Promise<FnConnectProbeResult> {
    const result = new FnConnectProbeResult()
    result.baseUrl = candidate.baseUrl
    result.route = candidate.route
    result.originalIndex = index
    try {
      const probeResult = await this.probe.probe(candidate.baseUrl)
      result.reachable = probeResult.reachable
      result.elapsedMs = probeResult.elapsedMs
    } catch (_) {
      result.reachable = false
    }
    return result
  }

  private uniqueCandidates(candidates: FeiniuVideoHostCandidate[]): FeiniuVideoHostCandidate[] {
    const result: FeiniuVideoHostCandidate[] = []
    candidates.forEach((candidate: FeiniuVideoHostCandidate) => {
      if (candidate.baseUrl.trim().length === 0) {
        return
      }
      if (result.find((item: FeiniuVideoHostCandidate) => item.baseUrl === candidate.baseUrl) === undefined) {
        result.push(candidate)
      }
    })
    return result
  }

  private appendFallback(targets: FeiniuVideoHostCandidate[], preferred: FeiniuVideoHostCandidate,
    candidate: FeiniuVideoHostCandidate): void {
    if (candidate.baseUrl === preferred.baseUrl) {
      return
    }
    if (targets.find((item: FeiniuVideoHostCandidate) => item.baseUrl === candidate.baseUrl) === undefined) {
      targets.push(candidate)
    }
  }
}
