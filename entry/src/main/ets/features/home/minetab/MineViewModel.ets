import { AxiosResponse } from "@ohos/axios";
import { ImageUrlsApi } from "sdk";
import { UserDto } from "sdk/src/main/ets/generated-client/models";
import { getUserApi } from "sdk/src/main/ets/utils/api/user-api";
import { BaseViewModel } from "../../../common/base/BaseViewModel";
import { ApiClient } from "../../../network/ApiClient";
import { MineUIState } from "./MineUIState";
import { bundleManager, common } from "@kit.AbilityKit";
import { ApiClientController } from "../../../data/ApiClientController";
import { AppPreference } from "../../../data/AppPreference";
import { DBManager } from "../../../data/DBManager";
import { MineHistoryPlanner } from "./MineHistoryPlanner";
import { MineHistoryLoadResult, MineHistoryRepository } from "./MineHistoryRepository";
import { MineHistoryState } from "./MineUIState";

export class MineViewModel extends BaseViewModel<MineUIState> {
  apiClientController: ApiClientController = new ApiClientController()
  private readonly historyRepository: MineHistoryRepository = new MineHistoryRepository()
  private readonly historyPlanner: MineHistoryPlanner = new MineHistoryPlanner()
  private historyRefreshing: boolean = false

  init() {
    this.ui.isAdministrator = false
    this.ui.userName = AppPreference.getInstance().getUserName()
    this.getServerInfo()
    this.getAppVersion()
    getUserApi(ApiClient.Instance()).getUserById({
      userId: ApiClient.Instance().userId
    }).then((res: AxiosResponse<UserDto>) => {
      if (res.data) {
        let user = res.data
        this.ui.userName = user.Name || ''
        let localUser = DBManager.getInstance().getUserDao().getByUserId(AppPreference.getInstance().getCurrentServerId()!,ApiClient.Instance().userId)
        if(user.Name !== localUser?.username){
          AppPreference.getInstance().setUserName(user.Name || '')
          this.apiClientController.updateUserName(ApiClient.Instance().userId, user.Name || '')
        }
        this.ui.userAvatar =
          ApiClient.Instance().basePath + new ImageUrlsApi().getUserImageUrl({
            Id: ApiClient.Instance().userId
          }, {
            tag: user.PrimaryImageTag,
            quality: 90
          })
        this.ui.isAdministrator = user.Policy?.IsAdministrator === true
        this.ui.userPolicy = this.ui.isAdministrator ? '管理员' : '普通用户'
      }
    }).catch(() => {
      this.ui.isAdministrator = false
    })
  }

  getServerInfo() {
    let serverId = AppPreference.getInstance().getCurrentServerId()
    if(serverId != null){
      let server = DBManager.getInstance().getServerDao().getServer(serverId)
      if (server) {
        this.ui.serverName = server.servername
        this.ui.serverVersion = server.version
      }
    }
  }

  private getAppVersion() {
    try {
      const context = this.ui.context.getHostContext() as common.UIAbilityContext;

      bundleManager.getBundleInfoForSelf(bundleManager.BundleFlag.GET_BUNDLE_INFO_WITH_APPLICATION)
        .then((bundleInfo) => {
          this.ui.versionName = `v${bundleInfo.versionName}`;
        })
        .catch(() => {
          console.error('Failed to get bundle info');
        });
    } catch (error) {
      console.error('Failed to get context:', error);
    }
  }

  async refreshHistory(): Promise<void> {
    if (this.historyRefreshing) {
      return
    }
    this.historyRefreshing = true
    this.ui.historyState = MineHistoryState.Loading
    try {
      const result: MineHistoryLoadResult = await this.historyRepository.load()
      const items = this.historyPlanner.merge(result.resume.items, result.completed.items, 8)
      this.ui.historyItems = items
      if (result.resume.failed && result.completed.failed) {
        this.ui.historyState = MineHistoryState.Error
      } else if (items.length === 0) {
        this.ui.historyState = MineHistoryState.Empty
      } else {
        this.ui.historyState = MineHistoryState.Content
      }
    } catch (error) {
      console.error(`[MineHistory] refresh failed: ${JSON.stringify(error)}`)
      this.ui.historyItems = []
      this.ui.historyState = MineHistoryState.Error
    } finally {
      this.historyRefreshing = false
    }
  }

  logout() {
    this.apiClientController.logout()
    this.appUIState.isLogin = false
  }
}
