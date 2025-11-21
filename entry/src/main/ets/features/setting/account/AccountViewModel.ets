import { HMRouterMgr } from "@hadss/hmrouter";
import { getUserApi } from "sdk/src/main/ets/utils/api/user-api";
import { BaseViewModel } from "../../../common/base/BaseViewModel";
import { ApiClientController } from "../../../data/ApiClientController";
import { AppPreference } from "../../../data/AppPreference";
import { DBManager } from "../../../data/DBManager";
import ServerUser from "../../../data/ServerUser";
import { ApiClient } from "../../../network/ApiClient";
import connectionHelper from "../../../setup/ConnectionHelper";
import { CheckUrlStateType, Success } from "../../../ui/state/CheckUrlState";
import { AccountUIState, ItemGroup } from "./AccountUIState";
import { asset } from "@kit.AssetStoreKit";
import { stringToArray } from "../../../common/CommonFunc";
import { util } from "@kit.ArkTS";
import { AxiosResponse } from "@ohos/axios";
import { AuthenticationResult } from "sdk/src/main/ets/generated-client/models";
import { RouterConsts } from "../../../common/RouterConsts";
import { getSystemApi } from "sdk/src/main/ets/utils/api/system-api";
import ServerEntity from "../../../data/ServerEntity";

export class AccountViewModel extends BaseViewModel<AccountUIState> {
  apiClientController: ApiClientController = new ApiClientController()

  getAccountList() {
    let users = DBManager.getInstance().getUserDao().queryAllDataSync()
    let groupAccounts: ItemGroup[] = []
    //把users根据server分组
    let serverMap: Map<number, ServerUser[]> = new Map()
    for (const user of users) {
      let server = DBManager.getInstance().getServerDao().getServer(user.serverId)
      if (server) {
        let serverUser = new ServerUser(user, server)
        if (serverMap.has(user.serverId)) {
          serverMap.get(user.serverId)?.push(serverUser)
        } else {
          serverMap.set(user.serverId, [serverUser])
        }
        //如果是当前用户，放到组的最前面
        let currentUserId = AppPreference.getInstance().getCurrentUserId()
        if (user.userId === currentUserId) {
          let arr = serverMap.get(user.serverId)!
          arr.unshift(arr.pop()!)
        }
      }
    }
    //转换成ItemGroup数组
    serverMap.forEach((value, key) => {
      let server = DBManager.getInstance().getServerDao().getServer(key)
      if (server) {
        let itemGroup = new ItemGroup()
        itemGroup.title = server.servername
        itemGroup.items = value
        groupAccounts.push(itemGroup)
      }
    })
    //把当前用户所在的组放到最前面
    let currentServerId = AppPreference.getInstance().getCurrentServerId()
    let currentIndex = groupAccounts.findIndex(group => {
      let firstItem = group.items[0]
      return firstItem.server.id === currentServerId
    })
    if (currentIndex > 0) {
      let currentGroup = groupAccounts.splice(currentIndex, 1)[0]
      groupAccounts.unshift(currentGroup)
    }
    this.ui.groupAccountList = groupAccounts
  }

  // 切换账号逻辑
  changeAccount(serverUser: ServerUser) {
    new ApiClientController().logout()
    HMRouterMgr.to(RouterConsts.IndexPage).pop()
    this.appUIState.isLogin = false
    this.appUIState.isConnecting = true
    AppPreference.getInstance().setCurrentServerId(serverUser.server.id)
    AppPreference.getInstance().setUserName(serverUser.user.username)
    AppPreference.getInstance().setAccessToken(serverUser.user.accessToken || '')
    connectionHelper.checkServerUrl(serverUser.server.hostname).then((state) => {
      if (state.type === CheckUrlStateType.Success) {
        let successData = state as Success
        this.apiClientController.setupServer(successData.address, successData.name, successData.version)
        let serverEntity = this.apiClientController.loadSavedServer()
        if (serverEntity !== null) {
          AppStorage.setOrCreate('server', serverEntity)
          let userEntity = DBManager.getInstance().getUserDao().getByUserId(serverEntity.id, serverUser.user.userId)
          if (userEntity != null) {
            AppPreference.getInstance().setAccessToken(userEntity.accessToken!)
            this.apiClientController.setupUser(serverEntity!.id, userEntity.userId, userEntity.accessToken!,
              userEntity.username)
            //检查token是否有效
            getSystemApi(ApiClient.Instance()).getSystemInfo()
              .then(() => {
                HMRouterMgr.to(RouterConsts.IndexPage).withAnimator(false).replace()
                setTimeout(() => {
                  this.appUIState.isLogin = true
                  this.appUIState.isConnecting = false
                }, 1000)
              })
              .catch(() => {
                this.loginByPwd(serverUser, serverEntity!)
              })
          } else {
            this.loginByPwd(serverUser, serverEntity)
          }
        } else {
          HMRouterMgr.to(RouterConsts.IndexPage).withAnimator(false).replace()
          this.appUIState.isConnecting = false
        }
      } else {
        HMRouterMgr.to(RouterConsts.IndexPage).withAnimator(false).replace()
        this.appUIState.isConnecting = false
      }
    })
  }

  loginByPwd(serverUser: ServerUser, serverEntity: ServerEntity) {
    //从密码管理器获取密码
    let query: asset.AssetMap = new Map();
    query.set(asset.Tag.ALIAS, stringToArray(serverEntity?.hostname + '_' + serverUser.user.username));
    // 如果只需要返回关键资产的属性，可以将RETURN_TYPE设置为ATTRIBUTES。返回属性不需解密，查询时间较短。
    query.set(asset.Tag.RETURN_TYPE, asset.ReturnType.ALL); // 此处表示需要返回关键资产的所有信息，即属性+明文。返回明文需要解密，查询时间较长。
    let res: Array<asset.AssetMap> = asset.querySync(query);
    if (res.length > 0) {
      let re = res[0]
      let arr = re.get(asset.Tag.SECRET) as Uint8Array
      let decoder = util.TextDecoder.create('utf-8');
      let passwd = decoder.decodeToString(arr)
      getUserApi(ApiClient.Instance()).authenticateUserByName({
        authenticateUserByName: {
          Username: serverUser.user.username,
          Pw: passwd
        }
      }).then((result: AxiosResponse<AuthenticationResult>) => {
        AppPreference.getInstance().setAccessToken(result.data.AccessToken!)
        this.apiClientController.setupUser(serverEntity!.id, result.data.User!.Id!, result.data.AccessToken!,
          serverUser.user.username)
        HMRouterMgr.to(RouterConsts.IndexPage).withAnimator(false).replace()
        setTimeout(() => {
          this.appUIState.isLogin = true
          this.appUIState.isConnecting = false
        }, 1000)
      }).catch((e: Error) => {
        console.log(e.message);
        HMRouterMgr.to(RouterConsts.IndexPage).withAnimator(false).replace()
        this.appUIState.isConnecting = false
      })
    } else {
      HMRouterMgr.to(RouterConsts.IndexPage).withAnimator(false).replace()
      this.appUIState.isConnecting = false
    }
  }

  deleteAccount(serverUser: ServerUser) {
    let res = DBManager.getInstance().getUserDao().deleteDataSync({
      ID: serverUser.user.id
    })
    if (res >= 0) {
      this.getAccountList()
    }
  }
}