import { AxiosResponse } from "@ohos/axios";
import { AuthenticationResult } from "sdk/src/main/ets/generated-client/models";
import { getUserApi } from "sdk/src/main/ets/utils/api/user-api";
import { BaseViewModel } from "../../common/base/BaseViewModel";
import { ApiClientController } from "../../data/ApiClientController";
import { AppPreference } from "../../data/AppPreference";
import { AppUIState } from "../../entity/AppUIState";
import { ApiClient } from "../../network/ApiClient";
import connectionHelper from "../../setup/ConnectionHelper";
import { CheckUrlStateType, Pending, Success } from "../../ui/state/CheckUrlState";
import { ConnectionUIState } from "./ConnectionUIState";
import { ServerSuggestion, Type } from "./ServerSuggestion";
import { AppStorageV2 } from "@kit.ArkUI";

export class ConnectionViewModel extends BaseViewModel<ConnectionUIState> {

  private apiClientController: ApiClientController = new ApiClientController()
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;

  async init() {
    let username = AppPreference.getInstance().getUserName()
    let passwd = AppPreference.getInstance().getPasswd()
    this.ui.username = username
    this.ui.password = passwd
    this.ui.rememberPwd = username.length > 0 && passwd.length > 0
    let server = this.apiClientController.loadSavedServer()
    if (server) {
      this.ui.hostname = server.hostname
    }
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

  onLogin() {
    if (this.ui.username.trim().length === 0 || this.ui.password.length === 0) {
      this.ui.errorText = '请输入用户名和密码'
      return
    }
    this.ui.errorText = ''
    this.ui.loading = true
    this.ui.externalError = false
    this.ui.checkUrlState = new Pending()
    connectionHelper.checkServerUrl(this.ui.hostname).then((state)=>{
      this.ui.checkUrlState = state
      if (state.type === CheckUrlStateType.Success) {
        let successServerAddr = (state as Success).address
        this.apiClientController.setupServer(successServerAddr)
        this.ui.hostname = successServerAddr
        let serverEntity = this.apiClientController.loadSavedServer()
        if (serverEntity !== null) {
          AppStorage.setOrCreate('server', serverEntity)
          getUserApi(ApiClient.Instance()).authenticateUserByName({
            authenticateUserByName: {
              Username: this.ui.username,
              Pw: this.ui.password
            }
          }).then((result: AxiosResponse<AuthenticationResult>) => {
            this.ui.loading = false
            AppPreference.getInstance().setAccessToken(result.data.AccessToken!)
            if(this.ui.rememberPwd){
              AppPreference.getInstance().setUserName(this.ui.username)
              AppPreference.getInstance().setPasswd(this.ui.password)
            }
            this.apiClientController.setupUser(serverEntity!.id, result.data.User!.Id!, result.data.AccessToken!)
            this.appUIState.isLogin = true
          }).catch((e: Error) => {
            console.log(e.message);
            this.ui.errorText = '登录失败，用户名或密码错误'
            this.ui.loading = false
          })
        }
      } else {
        this.ui.loading = false
        this.ui.externalError = true
      }
    })
  }
}