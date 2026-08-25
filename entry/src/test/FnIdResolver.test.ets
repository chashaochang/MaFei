import { describe, expect, it } from '@ohos/hypium'
import { FnIdResolver } from '../main/ets/features/feiniuvideo/network/FnIdResolver'
import {
  FeiniuVideoAddressKind,
  FeiniuVideoRequestError,
  FeiniuVideoRequestFailureKind,
  FnIdResolveEnvelope,
  FnIdResolveSource,
  MediaConnectionRoute
} from '../main/ets/features/feiniuvideo/network/FeiniuVideoApiModels'

class FakeFnIdResolveSource implements FnIdResolveSource {
  calls: string[] = []
  response: FnIdResolveEnvelope = { code: 0, data: {} }
  failureKind?: FeiniuVideoRequestFailureKind
  rawError: boolean = false

  async resolve(fnId: string): Promise<FnIdResolveEnvelope> {
    this.calls.push(fnId)
    if (this.failureKind !== undefined) {
      throw new FeiniuVideoRequestError(this.failureKind, 'scripted resolve failure')
    }
    if (this.rawError) {
      throw new Error('raw resolve failure')
    }
    return this.response
  }
}

export default function fnIdResolverTest() {
  describe('FnIdResolver', () => {
    it('acceptsPlainFnIdAndKnownFnIdUrls', 0, () => {
      const resolver = new FnIdResolver()

      expect(resolver.parseInput('  Abc_123-xy  ').fnId).assertEqual('Abc_123-xy')
      expect(resolver.parseInput('https://5ddd.com/abc123/').fnId).assertEqual('abc123')
      expect(resolver.parseInput('https://abc123.fnos.net/').fnId).assertEqual('abc123')
    })

    it('keepsExplicitHttpAndHttpsAddressesAsDirectHosts', 0, () => {
      const resolver = new FnIdResolver()
      const httpInput = resolver.parseInput('http://192.168.1.8:5666/')
      const httpsInput = resolver.parseInput('https://nas.example:5667/base/')

      expect(httpInput.kind).assertEqual(FeiniuVideoAddressKind.Direct)
      expect(httpInput.directBaseUrl).assertEqual('http://192.168.1.8:5666')
      expect(httpsInput.kind).assertEqual(FeiniuVideoAddressKind.Direct)
      expect(httpsInput.directBaseUrl).assertEqual('https://nas.example:5667/base')
    })

    it('normalizesBareServerAddressesLikeTheReferenceResolver', 0, () => {
      const resolver = new FnIdResolver()
      const bareIp = resolver.parseInput('192.168.1.8')
      const explicitPort = resolver.parseInput('nas.example:6000')
      const httpsHost = resolver.parseInput('https://nas.example')

      expect(bareIp.kind).assertEqual(FeiniuVideoAddressKind.Direct)
      expect(bareIp.directBaseUrl).assertEqual('http://192.168.1.8:5666')
      expect(explicitPort.kind).assertEqual(FeiniuVideoAddressKind.Direct)
      expect(explicitPort.directBaseUrl).assertEqual('http://nas.example:6000')
      expect(httpsHost.directBaseUrl).assertEqual('https://nas.example:5667')
    })

    it('rejectsCredentialsEmbeddedInDirectAddresses', 0, () => {
      const resolver = new FnIdResolver()

      expect(() => resolver.parseInput('https://admin:secret@nas.example:5667'))
        .assertThrowError('Server address host is invalid')
    })

    it('buildsLanPublicAndRelayCandidatesInContractOrder', 0, async () => {
      const source = new FakeFnIdResolveSource()
      source.response = {
        code: 0,
        data: {
          ipv4: [' 192.168.1.9 ', '192.168.1.9'],
          ipv6: ['fe80::10'],
          publicIpv4: ['203.0.113.9'],
          publicIpv6: ['2001:db8::9'],
          fn: ['relay.example:443'],
          port: { httpPort: 6000, httpsPort: 7000 }
        }
      }

      const result = await new FnIdResolver(source).resolve('fn-1')

      expect(result.candidates.length).assertEqual(6)
      expect(result.candidates[0].baseUrl).assertEqual('http://192.168.1.9:6000')
      expect(result.candidates[0].route).assertEqual(MediaConnectionRoute.Local)
      expect(result.candidates[1].baseUrl).assertEqual('http://203.0.113.9:6000')
      expect(result.candidates[2].baseUrl).assertEqual('https://203.0.113.9:7000')
      expect(result.candidates[3].baseUrl).assertEqual('http://[2001:db8::9]:6000')
      expect(result.candidates[4].baseUrl).assertEqual('https://[2001:db8::9]:7000')
      expect(result.candidates[5].baseUrl).assertEqual('https://relay.example')
      expect(result.candidates[5].requiresRelayCookie).assertTrue()
    })

    it('usesDefaultPortsWhenResolvePortsAreMissingOrZero', 0, () => {
      const resolver = new FnIdResolver()
      const candidates = resolver.buildCandidates({
        ipv4: ['10.0.0.2'],
        publicIpv4: ['198.51.100.2'],
        fn: ['relay.example'],
        port: { httpPort: 0, httpsPort: 0 }
      })

      expect(candidates[0].baseUrl).assertEqual('http://10.0.0.2:5666')
      expect(candidates[1].baseUrl).assertEqual('http://198.51.100.2:5666')
      expect(candidates[2].baseUrl).assertEqual('https://198.51.100.2:5667')
      expect(candidates[3].baseUrl).assertEqual('https://relay.example:5667')
    })

    it('bypassesFnIdLookupForDirectAddresses', 0, async () => {
      const source = new FakeFnIdResolveSource()
      const result = await new FnIdResolver(source).resolve('https://nas.example:5667')

      expect(source.calls.length).assertEqual(0)
      expect(result.candidates.length).assertEqual(1)
      expect(result.candidates[0].route).assertEqual(MediaConnectionRoute.Direct)
    })

    it('usesBuiltInRelayCandidatesWhenFnIdLookupFails', 0, async () => {
      const source = new FakeFnIdResolveSource()
      source.failureKind = FeiniuVideoRequestFailureKind.Connection

      const result = await new FnIdResolver(source).resolve('fn123')

      expect(result.candidates.length).assertEqual(2)
      expect(result.candidates[0].baseUrl).assertEqual('https://fn123.5ddd.com')
      expect(result.candidates[1].baseUrl).assertEqual('https://fn123.fnos.net')
    })

    it('matchesTheReferenceResolverFallbackForAllFnIdSourceFailures', 0, async () => {
      const failureKinds: FeiniuVideoRequestFailureKind[] = [
        FeiniuVideoRequestFailureKind.Authentication,
        FeiniuVideoRequestFailureKind.Business,
        FeiniuVideoRequestFailureKind.InvalidResponse,
        FeiniuVideoRequestFailureKind.InvalidRequest,
        FeiniuVideoRequestFailureKind.Unknown
      ]

      for (let index = 0; index < failureKinds.length; index += 1) {
        const source = new FakeFnIdResolveSource()
        source.failureKind = failureKinds[index]
        const result = await new FnIdResolver(source).resolve('fn123')
        expect(result.candidates[0].baseUrl).assertEqual('https://fn123.5ddd.com')
        expect(result.candidates[1].baseUrl).assertEqual('https://fn123.fnos.net')
        expect(source.calls.length).assertEqual(1)
      }

      const rawSource = new FakeFnIdResolveSource()
      rawSource.rawError = true
      const rawResult = await new FnIdResolver(rawSource).resolve('fn123')
      expect(rawResult.candidates[0].baseUrl).assertEqual('https://fn123.5ddd.com')
      expect(rawSource.calls.length).assertEqual(1)
    })

    it('usesRelayFallbackForRejectedOrInvalidFnIdResponses', 0, async () => {
      const businessSource = new FakeFnIdResolveSource()
      businessSource.response = { code: 1001, msg: 'FNID does not exist' }
      const responseSource = new FakeFnIdResolveSource()
      responseSource.response = { code: 0 }

      const businessResult = await new FnIdResolver(businessSource).resolve('fn123')
      const responseResult = await new FnIdResolver(responseSource).resolve('fn123')

      expect(businessResult.candidates[0].baseUrl).assertEqual('https://fn123.5ddd.com')
      expect(responseResult.candidates[0].baseUrl).assertEqual('https://fn123.5ddd.com')
    })
  })
}
