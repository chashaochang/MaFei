import { AxiosResponse } from "@ohos/axios";
import { AuthenticationResult } from "sdk/src/main/ets/generated-client/models";
import { getUserApi } from "sdk/src/main/ets/utils/api/user-api";
import { BaseViewModel } from "../../common/base/BaseViewModel";
import { ApiClientController } from "../../data/ApiClientController";
import { AppPreference } from "../../data/AppPreference";
import { ApiClient } from "../../network/ApiClient";
import connectionHelper from "../../setup/ConnectionHelper";
import { CheckUrlStateType, Pending, Success } from "../../ui/state/CheckUrlState";
import { ConnectionUIState } from "./ConnectionUIState";
import { ServerSuggestion, Type } from "../serverdiscovery/ServerSuggestion";
import { asset } from "@kit.AssetStoreKit";
import { stringToArray } from "../../common/CommonFunc";
import { util } from "@kit.ArkTS";
import { HMRouterMgr } from "@hadss/hmrouter";
import { RouterConsts } from "../../common/RouterConsts";
import ServerEntity from "../../data/ServerEntity";
import { DBManager } from "../../data/DBManager";

export class ConnectionViewModel extends BaseViewModel<ConnectionUIState> {
  private apiClientController: ApiClientController = new ApiClientController()

  async init() {
    let server = this.apiClientController.loadSavedServer()
    if (server) {
      this.ui.hostname = server.hostname
    }
    let username = AppPreference.getInstance().getUserName()
    let query: asset.AssetMap = new Map();
    query.set(asset.Tag.ALIAS, stringToArray(server?.hostname + '_' + username));
    // 如果只需要返回关键资产的属性，可以将RETURN_TYPE设置为ATTRIBUTES。返回属性不需解密，查询时间较短。
    query.set(asset.Tag.RETURN_TYPE, asset.ReturnType.ALL); // 此处表示需要返回关键资产的所有信息，即属性+明文。返回明文需要解密，查询时间较长。
    let res: Array<asset.AssetMap> = asset.querySync(query);
    if (res.length > 0) {
      let re = res[0]
      let arr = re.get(asset.Tag.SECRET) as Uint8Array
      let decoder = util.TextDecoder.create('utf-8');
      let passwd = decoder.decodeToString(arr)
      this.ui.password = passwd
      this.ui.rememberPwd = username.length > 0 && passwd.length > 0
    }
    this.ui.username = username
  }

  onLogin(isFromAddAccount: boolean) {
    if (this.ui.username.trim().length === 0 || this.ui.password.length === 0) {
      this.ui.errorText = '请输入用户名和密码'
      return
    }
    this.ui.errorText = ''
    this.ui.loading = true
    this.ui.externalError = false
    this.ui.checkUrlState = new Pending()
    connectionHelper.checkServerUrl(this.ui.hostname).then((state) => {
      this.ui.checkUrlState = state
      if (state.type === CheckUrlStateType.Success) {
        let successData = state as Success
        let successServerAddr = successData.address
        this.apiClientController.setupServer(successData.address, successData.name, successData.version,
          successData.serverId)
        this.ui.hostname = successServerAddr
        let serverEntity = this.apiClientController.loadSavedServer()
        if (serverEntity !== null) {
          this.appUIState.isConnecting = true
          this.appUIState.server = serverEntity
          // AppStorage.setOrCreate('server', serverEntity)
          // let token = AppPreference.getInstance().getAccessToken()
          // if (token.length > 0) {
          //   this.loginByAccessToken(isFromAddAccount, serverEntity, token)
          // } else {
          this.loginByPwd(isFromAddAccount, serverEntity)
          // }
        }
      } else {
        this.ui.loading = false
        this.ui.externalError = true
        this.appUIState.isConnecting = false
      }
    })
  }

  loginByAccessToken(isFromAddAccount: boolean, serverEntity: ServerEntity, token: string) {
    let userId = AppPreference.getInstance().getCurrentUserId()
    let userEntity = DBManager.getInstance().getUserDao().getByUserId(serverEntity.id, userId)
    if (userEntity != null) {
      this.apiClientController.setupUser(AppPreference.getInstance().getCurrentServerId()!, userEntity!.userId,
        token, userEntity.username)
      this.appUIState.isLogin = true
      if (isFromAddAccount) {
        HMRouterMgr.getCurrentPathStack()?.clear()
        HMRouterMgr.to(RouterConsts.IndexPage).withAnimator(false).replace()
      }
      this.appUIState.isConnecting = false
    } else {
      this.loginByPwd(isFromAddAccount, serverEntity)
    }
  }

  loginByPwd(isFromAddAccount: boolean, serverEntity: ServerEntity) {
    getUserApi(ApiClient.Instance()).authenticateUserByName({
      authenticateUserByName: {
        Username: this.ui.username,
        Pw: this.ui.password
      }
    }).then((result: AxiosResponse<AuthenticationResult>) => {
      if (result) {
        this.ui.loading = false
        AppPreference.getInstance().setAccessToken(result.data.AccessToken!)
        AppPreference.getInstance().setCurrentServerSystemId(result.data.ServerId || '')
        if (this.ui.rememberPwd) {
          let assetMap: asset.AssetMap = new Map
          assetMap.set(asset.Tag.SECRET, stringToArray(this.ui.password))
          assetMap.set(asset.Tag.ALIAS, stringToArray(serverEntity!.hostname + '_' + this.ui.username))
          assetMap.set(asset.Tag.ACCESSIBILITY, asset.Accessibility.DEVICE_UNLOCKED)
          try {
            asset.addSync(assetMap)
          } catch (error) {
            //保存密码失败
            console.log("保存密码失败：" + (error as Error).message);
          }
        }
        this.apiClientController.setupUser(AppPreference.getInstance().getCurrentServerId()!, result.data.User!.Id!,
          result.data.AccessToken!,
          this.ui.username)
        this.appUIState.isLogin = true
        if (isFromAddAccount) {
          HMRouterMgr.getCurrentPathStack()?.clear()
          HMRouterMgr.to(RouterConsts.IndexPage).withAnimator(false).replace()
        }
        this.appUIState.isConnecting = false
      } else {
        this.ui.errorText = '登录失败'
        this.ui.loading = false
        this.appUIState.isConnecting = false
      }
    }).catch((e: Error) => {
      console.log(e.message);
      this.ui.errorText = '登录失败，用户名或密码错误'
      this.ui.loading = false
      this.appUIState.isConnecting = false
    })
  }
}
