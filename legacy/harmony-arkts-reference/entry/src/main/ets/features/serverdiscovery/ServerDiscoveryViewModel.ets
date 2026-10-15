import { BaseViewModel } from "../../common/base/BaseViewModel";
import { ApiClientController } from "../../data/ApiClientController";
import connectionHelper from "../../setup/ConnectionHelper";
import { ServerDiscoveryUIState } from "./ServerDiscoveryUIState";
import { ServerSuggestion, Type } from "./ServerSuggestion";

export class ServerDiscoveryViewModel extends BaseViewModel<ServerDiscoveryUIState>{
  private apiClientController: ApiClientController = new ApiClientController()

  async init(){
    this.ui.loading = true
    this.ui.errorText = ''
    try {
      let previousServers = this.apiClientController.loadPreviouslyUsedServers()
      this.ui.serverSuggestions = previousServers.map(server => {
        return {
          type: Type.SAVED,
          name: server.hostname,
          address: server.hostname,
          timestamp: server.lastUsedTimestamp
        } as ServerSuggestion
      })
      let servers = await connectionHelper.discoverServers()
      servers.forEach(serverInfo => {
        this.ui.serverSuggestions = [
          {
            type: Type.DISCOVERED,
            name: serverInfo.Name ?? "",
            address: serverInfo.Address ?? '',
            timestamp: new Date().getTime()
          },
          ...this.ui.serverSuggestions.filter(existing => existing.address !== serverInfo.Address)
        ];
      })
    } catch (error) {
      console.error(`[ServerDiscovery] init failed: ${JSON.stringify(error)}`)
      this.ui.errorText = '局域网扫描失败，请检查网络权限和当前网络环境'
    } finally {
      this.ui.loading = false
    }
  }
}
