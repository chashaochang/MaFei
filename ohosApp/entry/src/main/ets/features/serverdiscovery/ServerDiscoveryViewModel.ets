import { BaseViewModel } from "../../common/base/BaseViewModel";
import { ApiClientController } from "../../data/ApiClientController";
import connectionHelper from "../../setup/ConnectionHelper";
import { ServerDiscoveryUIState } from "./ServerDiscoveryUIState";
import { ServerSuggestion, Type } from "./ServerSuggestion";

export class ServerDiscoveryViewModel extends BaseViewModel<ServerDiscoveryUIState>{
  private apiClientController: ApiClientController = new ApiClientController()

  async init(){
    let previousServers = this.apiClientController.loadPreviouslyUsedServers()
    // Suggest saved servers
    this.ui.serverSuggestions = previousServers.map(server => {
      return {
        type: Type.SAVED,
        name: server.hostname,
        address: server.hostname,
        timestamp: server.lastUsedTimestamp
      } as ServerSuggestion
    })
    // Prepend discovered servers to suggestions
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
  }
}