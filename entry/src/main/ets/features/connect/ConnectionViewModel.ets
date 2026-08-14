import { AxiosError, AxiosResponse } from "@ohos/axios";
import { AuthenticationResult } from "sdk/src/main/ets/generated-client/models";
import { getUserApi } from "sdk/src/main/ets/utils/api/user-api";
import { BaseViewModel } from "../../common/base/BaseViewModel";
import { ApiClientController } from "../../data/ApiClientController";
import { AppPreference } from "../../data/AppPreference";
import { ApiClient } from "../../network/ApiClient";
import connectionHelper from "../../setup/ConnectionHelper";
import { CheckUrlStateType, Error as CheckUrlError, Pending, Success } from "../../ui/state/CheckUrlState";
import { ConnectionUIState } from "./ConnectionUIState";
import { asset } from "@kit.AssetStoreKit";
import { stringToArray } from "../../common/CommonFunc";
import { util } from "@kit.ArkTS";
import { HMRouterMgr } from "@hadss/hmrouter";
import { RouterConsts } from "../../common/RouterConsts";
import ServerEntity from "../../data/ServerEntity";
import { DBManager } from "../../data/DBManager";

const CONNECTION_ERROR_TEXT: string = '无法建立连接。 请检查主机名和你的网络连接。'
const INVALID_CREDENTIALS_TEXT: string = '登录失败，用户名或密码错误'
const LOGIN_FAILED_TEXT: string = '登录失败'

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
    query.set(asset.Tag.RETURN_TYPE, asset.ReturnType.ALL);
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
    connectionHelper.checkServerUrl(this.ui.hostname).then(async (state) => {
      this.ui.checkUrlState = state
      if (state.type === CheckUrlStateType.Success) {
        let successData = state as Success
        let successServerAddr = successData.address
        await this.apiClientController.setupServer(successData.address, successData.name, successData.version,
          successData.serverId)
        this.ui.hostname = successServerAddr
        let serverEntity = this.apiClientController.loadSavedServer()
        if (serverEntity !== null) {
          this.appUIState.isConnecting = true
          this.appUIState.server = serverEntity
          this.loginByPwd(isFromAddAccount, serverEntity)
        }
      } else {
        this.ui.loading = false
        this.ui.externalError = true
        this.appUIState.isConnecting = false
      }
    }).catch(() => {
      this.ui.checkUrlState = new CheckUrlError(CONNECTION_ERROR_TEXT)
      this.ui.loading = false
      this.ui.externalError = true
      this.appUIState.isConnecting = false
    })
  }

  async loginByAccessToken(isFromAddAccount: boolean, serverEntity: ServerEntity, token: string): Promise<void> {
    let userId = AppPreference.getInstance().getCurrentUserId()
    let userEntity = DBManager.getInstance().getUserDao().getByUserId(serverEntity.id, userId)
    if (userEntity != null) {
      await this.apiClientController.setupUser(AppPreference.getInstance().getCurrentServerId()!, userEntity!.userId,
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
    }).then(async (result: AxiosResponse<AuthenticationResult>) => {
      if (result) {
        const preference = AppPreference.getInstance()
        if (this.ui.rememberPwd) {
          let assetMap: asset.AssetMap = new Map
          assetMap.set(asset.Tag.SECRET, stringToArray(this.ui.password))
          assetMap.set(asset.Tag.ALIAS, stringToArray(serverEntity.hostname + '_' + this.ui.username))
          assetMap.set(asset.Tag.ACCESSIBILITY, asset.Accessibility.DEVICE_UNLOCKED)
          try {
            asset.addSync(assetMap)
          } catch (_error) {
          }
        }
        await this.apiClientController.setupUser(preference.getCurrentServerId()!, result.data.User!.Id!,
          result.data.AccessToken!, this.ui.username)
        preference.setCurrentServerSystemId(result.data.ServerId || '')
        // A stored token marks the point at which all other login state has committed.
        preference.setAccessToken(result.data.AccessToken!)
        this.appUIState.isLogin = true
        if (isFromAddAccount) {
          HMRouterMgr.getCurrentPathStack()?.clear()
          HMRouterMgr.to(RouterConsts.IndexPage).withAnimator(false).replace()
        }
      } else {
        this.ui.errorText = LOGIN_FAILED_TEXT
      }
      this.ui.loading = false
      this.appUIState.isConnecting = false
    }).catch((error: Error) => {
      const axiosError = error as AxiosError<Object, Object>
      const status = axiosError.response?.status
      if (status === 400 || status === 401) {
        this.ui.errorText = INVALID_CREDENTIALS_TEXT
      } else if (axiosError.isAxiosError === true && !axiosError.response) {
        this.ui.errorText = CONNECTION_ERROR_TEXT
      } else {
        this.ui.errorText = LOGIN_FAILED_TEXT
      }
      this.ui.loading = false
      this.appUIState.isConnecting = false
    })
  }
}
