import { describe, expect, it } from '@ohos/hypium'
import { FeiniuVideoUpstreamAddressResolver } from
  '../main/ets/features/feiniuvideo/network/FeiniuVideoUpstreamHostResolution'

export default function feiniuVideoUpstreamHostResolutionTest() {
  describe('FeiniuVideoUpstreamAddressResolver', () => {
    it('usesStandardPortsForExplicitSchemesWithoutPorts', 0, async () => {
      const resolver = new FeiniuVideoUpstreamAddressResolver()

      const http = await resolver.resolve('http://media.example.com')
      const https = await resolver.resolve('https://media.example.com/library')
      const ws = await resolver.resolve('ws://192.168.1.8')
      const wss = await resolver.resolve('wss://media.example.com')

      expect(http.preferredBaseUrl).assertEqual('http://media.example.com:80')
      expect(https.preferredBaseUrl).assertEqual('https://media.example.com:443/library')
      expect(ws.preferredBaseUrl).assertEqual('http://192.168.1.8:80')
      expect(wss.preferredBaseUrl).assertEqual('https://media.example.com:443')
    })

    it('preservesExplicitPortsAndSupportsBareServerAddresses', 0, async () => {
      const resolver = new FeiniuVideoUpstreamAddressResolver()

      const explicit = await resolver.resolve('https://media.example.com:7443/')
      const bareHost = await resolver.resolve('media.example.com')
      const barePort = await resolver.resolve('192.168.1.8:6000')

      expect(explicit.preferredBaseUrl).assertEqual('https://media.example.com:7443')
      expect(bareHost.preferredBaseUrl).assertEqual('http://media.example.com:5666')
      expect(barePort.preferredBaseUrl).assertEqual('http://192.168.1.8:6000')
    })
  })
}
