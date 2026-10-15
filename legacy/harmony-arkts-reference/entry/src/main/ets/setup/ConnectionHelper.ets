import jellyfin, { PublicSystemInfo, RecommendedServerInfo } from "sdk"
import { ServerDiscoveryInfo } from "sdk/src/main/ets/generated-client/models"
import { RecommendedServerInfoScore } from "sdk/src/main/ets/models/recommended-server-info"
import { CheckUrlState, Error, Success } from "../ui/state/CheckUrlState"
import { connection } from "@kit.NetworkKit"
import { url } from "@kit.ArkTS"

interface NetworkResolverError {
  code?: number
  message?: string
}

class ConnectionHelper {
  async checkServerUrl(enteredUrl: string): Promise<CheckUrlState> {
    const candidates = jellyfin.discovery.getAddressCandidates(enteredUrl)
    console.info(`[JellyfinConnection] server probe start candidates=${candidates.join(',')}`)
    const badServers: RecommendedServerInfo[] = []
    const goodServers: RecommendedServerInfo[] = []
    let servers: RecommendedServerInfo[] = []
    try {
      servers = await jellyfin.discovery.getRecommendedServers(candidates)
      this.logProbeResults('hostname', servers)
    } catch (error) {
      console.error(`[JellyfinConnection] hostname probe threw error=${this.errorSummary(error)}`)
    }
    const hasUsableServer = servers.some((server: RecommendedServerInfo): boolean =>
      server.score === RecommendedServerInfoScore.GREAT ||
      server.score === RecommendedServerInfoScore.GOOD)
    if (!hasUsableServer) {
      // Axios can fail to resolve a hostname on some networks even though the
      // system resolver returns a usable address. For HTTP endpoints, retry by
      // connecting directly to every system-resolved IPv4/IPv6 address.
      const resolvedCandidates = await this.getHttpResolvedCandidates(candidates)
      if (resolvedCandidates.length > 0) {
        console.info(`[JellyfinConnection] direct-address fallback candidates=${resolvedCandidates.join(',')}`)
        try {
          const resolvedServers = await jellyfin.discovery.getRecommendedServers(resolvedCandidates)
          this.logProbeResults('direct-address', resolvedServers)
          servers = servers.concat(resolvedServers)
        } catch (error) {
          console.error(`[JellyfinConnection] direct-address probe threw error=${this.errorSummary(error)}`)
        }
      }
    }
    let greatServer = servers.find((e: RecommendedServerInfo) => {
      switch (e.score) {
        case RecommendedServerInfoScore.GREAT:
          return true
        case RecommendedServerInfoScore.GOOD:
          goodServers.push(e)
          return false
        case RecommendedServerInfoScore.OK:
        case RecommendedServerInfoScore.BAD:
          badServers.push(e)
          return false
      }
    })
    const server = greatServer ?? goodServers[0]
    if (server) {
      const systemInfo: PublicSystemInfo | undefined = server.systemInfo
      let suc = new Success(server.address, systemInfo?.ServerName || '', systemInfo?.Version || '',
        systemInfo?.Id || '')
      return suc
    }
    let error: string | null = null

    if (badServers.length > 0) {
      const count = badServers.length
      const unreachableServers = badServers.filter(result => result.systemInfo == null)
      const incompatibleServers = badServers.filter(result => result.systemInfo != null)

      let errorMessage = `${count}条连接错误`

      if (unreachableServers.length > 0) {
        errorMessage += '\n\n无法连接到服务器:\n'
        errorMessage += unreachableServers.map(result => `· ${result.address}`).join('\n')
      }

      if (incompatibleServers.length > 0) {
        errorMessage += '\n\n不支持的版本:\n'
        errorMessage += incompatibleServers.map(result => `· ${result.address}`).join('\n')
      }

      error = errorMessage
    }
    let err = new Error(error ?? '')
    return err
  }

  async discoverServers(): Promise<ServerDiscoveryInfo[]> {
    return await jellyfin.discovery.discoverLocalServers()
  }

  private logProbeResults(stage: string, servers: RecommendedServerInfo[]): void {
    const summary = servers.map((server: RecommendedServerInfo): string =>
      `${server.address}[score=${server.score},systemInfo=${server.systemInfo !== undefined}]`).join('; ')
    console.info(`[JellyfinConnection] ${stage} probe results=${summary || 'none'}`)
  }

  private errorSummary(error: Object): string {
    const networkError = error as NetworkResolverError
    return `code=${networkError.code ?? 'unknown'} message=${networkError.message ?? String(error)}`
  }

  private async resolveAddresses(host: string): Promise<connection.NetAddress[]> {
    const addresses: connection.NetAddress[] = []
    try {
      // Axios fails to resolve AAAA-only names on some HarmonyOS releases.
      // Ask NetworkKit for an IPv6 record explicitly before falling back to a
      // generic query, which preserves normal IPv4 server behavior.
      const ipv6Addresses = await connection.getAddressesByNameWithOptions(host, {
        family: connection.FamilyType.FAMILY_TYPE_IPV6
      })
      ipv6Addresses.forEach((address: connection.NetAddress): void => {
        if (!addresses.some((item: connection.NetAddress): boolean =>
          item.address === address.address && item.family === address.family)) {
          addresses.push(address)
        }
      })
      console.info(`[JellyfinConnection] IPv6 resolver host=${host} count=${ipv6Addresses.length}`)
    } catch (error) {
      console.error(`[JellyfinConnection] IPv6 resolver failed host=${host} error=${this.errorSummary(error)}`)
    }
    if (addresses.length > 0) {
      return addresses
    }
    try {
      const genericAddresses = await connection.getAddressesByName(host)
      genericAddresses.forEach((address: connection.NetAddress): void => {
        if (!addresses.some((item: connection.NetAddress): boolean =>
          item.address === address.address && item.family === address.family)) {
          addresses.push(address)
        }
      })
    } catch (error) {
      console.error(`[JellyfinConnection] generic resolver failed host=${host} error=${this.errorSummary(error)}`)
    }
    return addresses
  }

  private async getHttpResolvedCandidates(candidates: string[]): Promise<string[]> {
    const resolvedCandidates: string[] = []
    for (const candidate of candidates) {
      let parsed: url.URL
      try {
        parsed = url.URL.parseURL(candidate)
      } catch (_) {
        continue
      }
      if (parsed.protocol !== 'http:' || parsed.hostname.length === 0 ||
        parsed.hostname.includes(':')) {
        continue
      }
      try {
        const addresses = await this.resolveAddresses(parsed.hostname)
        const resolvedAddresses = addresses.map((address: connection.NetAddress): string =>
          `${address.address}/${address.family ?? 1}`).join(',')
        console.info(`[JellyfinConnection] resolver host=${parsed.hostname} addresses=${resolvedAddresses || 'none'}`)
        addresses.forEach((address: connection.NetAddress): void => {
          const normalizedAddress = address.address.trim()
          if (normalizedAddress.length === 0) {
            return
          }
          const host = address.family === 2 ? `[${normalizedAddress}]` : normalizedAddress
          const port = parsed.port.length > 0 ? `:${parsed.port}` : ''
          const path = parsed.pathname || ''
          const resolvedUrl = `http://${host}${port}${path}`
          if (!resolvedCandidates.includes(resolvedUrl)) {
            resolvedCandidates.push(resolvedUrl)
          }
        })
      } catch (error) {
        console.error(`[JellyfinConnection] resolver failed host=${parsed.hostname} error=${this.errorSummary(error)}`)
      }
    }
    return resolvedCandidates
  }
}

const connectionHelper = new ConnectionHelper()

export default connectionHelper
