import { ApiClientController } from '../data/ApiClientController'
import { AppUIState } from '../entity/AppUIState';
import { AppStorageV2 } from '@kit.ArkUI';

export enum ServerState {
  Pending = 0,
  Unset = 1,
  Available = 2
}

@ObservedV2
export class MainViewModel {
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;
  apiClientController: ApiClientController
  @Trace serverState: ServerState = ServerState.Unset
  @Monitor('serverState') onStateChanged(monitor: IMonitor){
    monitor.dirty.forEach((path: string) => {
      console.log(`${path} changed from ${monitor.value(path)?.before} to ${monitor.value(path)?.now}`)
    })
  }

  constructor() {
    this.apiClientController = new ApiClientController()
  }

  refreshServer() {
    let serverEntity = this.apiClientController.loadSavedServer()
    if (serverEntity) {
      this.appUIState.server = serverEntity
    }
    this.serverState = serverEntity ? ServerState.Available : ServerState.Unset
  }

  // async switchServer(hostname: string) {
  //   this.apiClientController.setupServer(hostname)
  //   this.refreshServer()
  // }

  resetServer() {
    this.serverState = ServerState.Unset
  }

}

const mainViewModel = new MainViewModel()

export default mainViewModel

