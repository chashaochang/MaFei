import { describe, expect, it } from '@ohos/hypium'
import {
  FeiniuVideoHostResolver,
  FeiniuVideoHttpHostProbe
} from '../main/ets/features/feiniuvideo/network/FeiniuVideoHostResolver'
import {
  FeiniuVideoHostCandidate,
  FeiniuVideoHostProbe,
  FeiniuVideoHostProbeResult,
  FeiniuVideoHostRequest,
  FeiniuVideoHostResolution,
  FeiniuVideoRequestError,
  FeiniuVideoRequestFailureKind,
  FeiniuVideoRoutePreference,
  MediaConnectionRoute
} from '../main/ets/features/feiniuvideo/network/FeiniuVideoApiModels'

class FakeHostProbe implements FeiniuVideoHostProbe {
  results: Map<string, FeiniuVideoHostProbeResult> = new Map<string, FeiniuVideoHostProbeResult>()
  calls: string[] = []

  async probe(baseUrl: string): Promise<FeiniuVideoHostProbeResult> {
    this.calls.push(baseUrl)
    return this.results.get(baseUrl) || new FeiniuVideoHostProbeResult(false)
  }
}

class ScriptedHostRequest implements FeiniuVideoHostRequest<string> {
  calls: string[] = []
  failureKinds: Map<string, FeiniuVideoRequestFailureKind> =
    new Map<string, FeiniuVideoRequestFailureKind>()
  failureMessages: Map<string, string> = new Map<string, string>()
  rawErrorBaseUrls: Set<string> = new Set<string>()

  async execute(candidate: FeiniuVideoHostCandidate): Promise<string> {
    this.calls.push(candidate.baseUrl)
    const failureKind = this.failureKinds.get(candidate.baseUrl)
    if (failureKind !== undefined) {
      throw new FeiniuVideoRequestError(
        failureKind,
        this.failureMessages.get(candidate.baseUrl) || 'scripted failure'
      )
    }
    if (this.rawErrorBaseUrls.has(candidate.baseUrl)) {
      throw new Error('unclassified')
    }
    return candidate.baseUrl
  }
}

function candidate(baseUrl: string, route: MediaConnectionRoute): FeiniuVideoHostCandidate {
  return new FeiniuVideoHostCandidate(baseUrl, route)
}

function hostResolution(): FeiniuVideoHostResolution {
  return new FeiniuVideoHostResolution(
    candidate('http://lan:5666', MediaConnectionRoute.Local),
    [
      candidate('https://public:5667', MediaConnectionRoute.Public),
      candidate('https://relay:5667', MediaConnectionRoute.Relay)
    ]
  )
}

async function requestError(resolver: FeiniuVideoHostResolver,
  request: ScriptedHostRequest): Promise<Error | undefined> {
  try {
    await resolver.executeWithFallback(hostResolution(), request)
  } catch (error) {
    return error as Error
  }
  return undefined
}

export default function feiniuVideoHostResolverTest() {
  describe('FeiniuVideoHostResolver', () => {
    it('prefersFastestReachableHostByDefaultAndKeepsFastFallbacksFirst', 0, async () => {
      const probe = new FakeHostProbe()
      probe.results.set('http://lan:5666', new FeiniuVideoHostProbeResult(true, 40))
      probe.results.set('https://public:5667', new FeiniuVideoHostProbeResult(true, 10))
      probe.results.set('https://relay:5667', new FeiniuVideoHostProbeResult(false, 5))
      const resolver = new FeiniuVideoHostResolver(probe)

      const result = await resolver.resolve([
        candidate('http://lan:5666', MediaConnectionRoute.Local),
        candidate('https://public:5667', MediaConnectionRoute.Public),
        candidate('https://relay:5667', MediaConnectionRoute.Relay)
      ])

      expect(result.preferredBaseUrl).assertEqual('https://public:5667')
      expect(result.usedLocal).assertFalse()
      expect(result.fallbackBaseUrls[0]).assertEqual('http://lan:5666')
      expect(result.fallbackBaseUrls[1]).assertEqual('https://relay:5667')
    })

    it('supportsPublicRelayAndSpeedPreferences', 0, async () => {
      const probe = new FakeHostProbe()
      probe.results.set('http://lan:5666', new FeiniuVideoHostProbeResult(true, 25))
      probe.results.set('https://public:5667', new FeiniuVideoHostProbeResult(true, 15))
      probe.results.set('https://relay:5667', new FeiniuVideoHostProbeResult(true, 5))
      const resolver = new FeiniuVideoHostResolver(probe)
      const candidates = [
        candidate('http://lan:5666', MediaConnectionRoute.Local),
        candidate('https://public:5667', MediaConnectionRoute.Public),
        candidate('https://relay:5667', MediaConnectionRoute.Relay)
      ]

      const publicResult = await resolver.resolve(candidates, FeiniuVideoRoutePreference.Public)
      const relayResult = await resolver.resolve(candidates, FeiniuVideoRoutePreference.Relay)
      const speedResult = await resolver.resolve(candidates, FeiniuVideoRoutePreference.Speed)

      expect(publicResult.preferredRoute).assertEqual(MediaConnectionRoute.Public)
      expect(relayResult.preferredRoute).assertEqual(MediaConnectionRoute.Relay)
      expect(speedResult.preferredBaseUrl).assertEqual('https://relay:5667')
    })

    it('usesRemoteFirstOrderWhenNoCandidateIsReachable', 0, async () => {
      const resolver = new FeiniuVideoHostResolver(new FakeHostProbe())
      const candidates = [
        candidate('http://lan:5666', MediaConnectionRoute.Local),
        candidate('https://public:5667', MediaConnectionRoute.Public),
        candidate('https://relay:5667', MediaConnectionRoute.Relay)
      ]

      const speedResult = await resolver.resolve(candidates, FeiniuVideoRoutePreference.Speed)
      const publicResult = await resolver.resolve(candidates, FeiniuVideoRoutePreference.Public)

      expect(speedResult.preferredRoute).assertEqual(MediaConnectionRoute.Relay)
      expect(speedResult.fallbackBaseUrls[0]).assertEqual('https://public:5667')
      expect(speedResult.fallbackBaseUrls[1]).assertEqual('http://lan:5666')
      expect(publicResult.preferredRoute).assertEqual(MediaConnectionRoute.Public)
      expect(publicResult.fallbackBaseUrls[0]).assertEqual('https://relay:5667')
      expect(publicResult.fallbackBaseUrls[1]).assertEqual('http://lan:5666')
    })

    it('usesTheReferenceProbeStatusBoundaryAndP2pFilter', 0, async () => {
      expect(FeiniuVideoHttpHostProbe.isReachableStatus(200)).assertTrue()
      expect(FeiniuVideoHttpHostProbe.isReachableStatus(499)).assertTrue()
      expect(FeiniuVideoHttpHostProbe.isReachableStatus(500)).assertFalse()

      const probe = new FakeHostProbe()
      probe.results.set('https://public:5667', new FeiniuVideoHostProbeResult(true, 1))
      probe.results.set('https://relay:5667', new FeiniuVideoHostProbeResult(true, 20))
      const resolver = new FeiniuVideoHostResolver(probe, FeiniuVideoRoutePreference.Speed, false)
      const result = await resolver.resolve([
        candidate('https://public:5667', MediaConnectionRoute.Public),
        candidate('https://relay:5667', MediaConnectionRoute.Relay)
      ])

      expect(result.preferredRoute).assertEqual(MediaConnectionRoute.Relay)
      expect(probe.calls.length).assertEqual(1)
    })

    it('fallsBackAfterExplicitConnectionFailure', 0, async () => {
      const resolver = new FeiniuVideoHostResolver(new FakeHostProbe())
      const request = new ScriptedHostRequest()
      request.failureKinds.set('http://lan:5666', FeiniuVideoRequestFailureKind.Connection)

      const result = await resolver.executeWithFallback(hostResolution(), request)

      expect(request.calls.length).assertEqual(2)
      expect(request.calls[1]).assertEqual('https://public:5667')
      expect(result.candidate.route).assertEqual(MediaConnectionRoute.Public)
    })

    it('doesNotFallbackForAuthenticationBusinessOrInvalidRequestErrors', 0, async () => {
      const resolver = new FeiniuVideoHostResolver(new FakeHostProbe())
      const failureKinds: FeiniuVideoRequestFailureKind[] = [
        FeiniuVideoRequestFailureKind.Authentication,
        FeiniuVideoRequestFailureKind.Business,
        FeiniuVideoRequestFailureKind.InvalidRequest
      ]

      for (let index = 0; index < failureKinds.length; index += 1) {
        const request = new ScriptedHostRequest()
        request.failureKinds.set('http://lan:5666', failureKinds[index])
        await requestError(resolver, request)
        expect(request.calls.length).assertEqual(1)
      }

    })

    it('fallsBackForInvalidResponseUnknownAndUnclassifiedErrors', 0, async () => {
      const resolver = new FeiniuVideoHostResolver(new FakeHostProbe())
      const failureKinds: FeiniuVideoRequestFailureKind[] = [
        FeiniuVideoRequestFailureKind.InvalidResponse,
        FeiniuVideoRequestFailureKind.Unknown
      ]

      for (let index = 0; index < failureKinds.length; index += 1) {
        const request = new ScriptedHostRequest()
        request.failureKinds.set('http://lan:5666', failureKinds[index])
        const result = await resolver.executeWithFallback(hostResolution(), request)
        expect(request.calls.length).assertEqual(2)
        expect(result.candidate.route).assertEqual(MediaConnectionRoute.Public)
      }

      const rawRequest = new ScriptedHostRequest()
      rawRequest.rawErrorBaseUrls.add('http://lan:5666')
      const rawResult = await resolver.executeWithFallback(hostResolution(), rawRequest)
      expect(rawRequest.calls.length).assertEqual(2)
      expect(rawResult.candidate.route).assertEqual(MediaConnectionRoute.Public)
    })

    it('triesEveryCandidateAndRethrowsTheLastConnectionFailure', 0, async () => {
      const resolver = new FeiniuVideoHostResolver(new FakeHostProbe())
      const request = new ScriptedHostRequest()
      const baseUrls = [
        'http://lan:5666',
        'https://public:5667',
        'https://relay:5667'
      ]
      baseUrls.forEach((baseUrl: string) => {
        request.failureKinds.set(baseUrl, FeiniuVideoRequestFailureKind.Connection)
      })
      request.failureMessages.set('https://relay:5667', 'relay connection failed')

      const error = await requestError(resolver, request)

      expect(request.calls.length).assertEqual(3)
      expect(request.calls[0]).assertEqual(baseUrls[0])
      expect(request.calls[1]).assertEqual(baseUrls[1])
      expect(request.calls[2]).assertEqual(baseUrls[2])
      expect(error?.message).assertEqual('relay connection failed')
    })

    it('marksOnlyRelayCandidatesForRelayCookieHandling', 0, () => {
      expect(candidate('https://relay:5667', MediaConnectionRoute.Relay).requiresRelayCookie).assertTrue()
      expect(candidate('https://public:5667', MediaConnectionRoute.Public).requiresRelayCookie).assertFalse()
      expect(candidate('http://lan:5666', MediaConnectionRoute.Local).requiresRelayCookie).assertFalse()
    })
  })
}
