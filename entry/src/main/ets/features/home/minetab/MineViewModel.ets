import { AxiosResponse } from "@ohos/axios";
import { ImageUrlsApi } from "sdk";
import { UserDto } from "sdk/src/main/ets/generated-client/models";
import { getUserApi } from "sdk/src/main/ets/utils/api/user-api";
import { BaseViewModel } from "../../../common/base/BaseViewModel";
import { ApiClient } from "../../../network/ApiClient";
import { MineUIState } from "./MineUIState";
import { bundleManager } from "@kit.AbilityKit";
import { ApiClientController } from "../../../data/ApiClientController";
import { AppPreference } from "../../../data/AppPreference";
import { DBManager } from "../../../data/DBManager";
import { MineHistoryCandidate, MineHistoryPlanner } from "./MineHistoryPlanner";
import {
  MineHistoryLoadResult,
  MineHistoryRepository,
  MineHistoryRequestSession
} from "./MineHistoryRepository";
import { MineHistoryState } from "./MineUIState";
import { MediaProviderKind } from "../../../media/MediaProviderKind";
import { MediaProviderRegistry } from "../../../media/MediaProviderRegistry";
import { MediaProviderSession } from "../../../media/MediaProviderSession";
import { MediaProviderComposition } from "../../setting/account/MediaProviderComposition";

export class MineViewModel extends BaseViewModel<MineUIState> {
  apiClientController: ApiClientController = new ApiClientController()
  private readonly historyRepository: MineHistoryRepository = new MineHistoryRepository()
  private readonly historyPlanner: MineHistoryPlanner = new MineHistoryPlanner()
  private historyRefreshing: boolean = false
  private historyRequestEpoch: number = 0
  private historyActiveScope: string = ''

  init(): void {
    const activeSession = MediaProviderRegistry.instance().getActiveSession()
    if (activeSession?.provider === MediaProviderKind.FeiniuVideo) {
      this.initFeiniuProfile(activeSession)
      this.getAppVersion()
      return
    }
    this.initJellyfinProfile(activeSession)
    this.getAppVersion()
  }

  private initFeiniuProfile(session: MediaProviderSession): void {
    const address = session.originalAddress || session.baseUrl
    this.ui.userName = session.displayName || session.userId
    this.ui.userAvatar = ''
    this.ui.serverName = session.serverName || address
    this.ui.serverVersion = address
    this.ui.isAdministrator = false
    this.ui.managementAvailable = session.capabilities.management
    this.ui.webITongAvailable = false
    this.ui.userPolicy = '普通用户'
  }

  private initJellyfinProfile(session?: MediaProviderSession): void {
    this.ui.userAvatar = ''
    this.ui.serverName = ''
    this.ui.serverVersion = ''
    this.ui.userName = AppPreference.getInstance().getUserName()
    this.ui.managementAvailable = session?.capabilities.management !== false
    this.ui.webITongAvailable = true
    this.ui.userPolicy = '普通用户'
    this.getServerInfo()
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
      if (!this.ui.context.getHostContext()) {
        return
      }

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
    const requestScope = this.historyRepository.currentScope()
    if (this.historyRefreshing && this.historyActiveScope === requestScope) {
      return
    }
    const requestEpoch = ++this.historyRequestEpoch
    this.historyRefreshing = true
    this.historyActiveScope = requestScope
    this.ui.historyState = MineHistoryState.Loading
    try {
      const result: MineHistoryLoadResult = await this.historyRepository.load({
        resumeStartIndex: 0,
        completedStartIndex: 0,
        limit: 24,
        loadResume: true,
        loadCompleted: true
      })
      if (!this.isCurrentHistoryRequest(requestEpoch, result.scope)) {
        return
      }
      const items = this.historyPlanner.merge(result.resume.items, result.completed.items, 8)
      const anyFailed = result.resume.failed || result.completed.failed
      this.ui.historyItems = items
      if ((result.resume.failed && result.completed.failed) || (items.length === 0 && anyFailed)) {
        this.ui.historyState = MineHistoryState.Error
      } else if (items.length === 0) {
        this.ui.historyState = MineHistoryState.Empty
      } else {
        this.ui.historyState = MineHistoryState.Content
      }
      if (items.length > 0 && result.session) {
        this.enrichVisibleHistory(items, result.session, requestEpoch)
      }
    } catch (error) {
      if (!this.isCurrentHistoryRequest(requestEpoch, requestScope)) {
        return
      }
      console.error(`[MineHistory] refresh failed: ${JSON.stringify(error)}`)
      this.ui.historyItems = []
      this.ui.historyState = MineHistoryState.Error
    } finally {
      if (requestEpoch === this.historyRequestEpoch) {
        this.historyRefreshing = false
      }
    }
  }

  private async enrichVisibleHistory(
    items: MineHistoryCandidate[],
    session: MineHistoryRequestSession,
    requestEpoch: number
  ): Promise<void> {
    try {
      await this.historyRepository.enrichSeriesPositions(items, session)
      if (!this.isCurrentHistoryRequest(requestEpoch, session.scope)) {
        return
      }
      this.ui.historyItems = this.historyPlanner.merge(items, [], 8)
    } catch (error) {
      console.warn(`[MineHistory] series enrichment failed: ${JSON.stringify(error)}`)
    }
  }

  private isCurrentHistoryRequest(requestEpoch: number, scope: string): boolean {
    return requestEpoch === this.historyRequestEpoch && scope === this.historyActiveScope &&
      scope === this.historyRepository.currentScope()
  }

  async logout(): Promise<void> {
    const activeSession = MediaProviderRegistry.instance().getActiveSession()
    if (activeSession?.provider === MediaProviderKind.FeiniuVideo) {
      const hostContext = this.ui.context.getHostContext()
      if (!hostContext) {
        return
      }
      const composition = await MediaProviderComposition.initialize(hostContext)
      const loggedOut = await composition.coordinator().logoutFeiniu(activeSession.providerAccountId)
      if (loggedOut) {
        this.appUIState.isLogin = false
      }
      return
    }
    await this.apiClientController.logout()
    this.appUIState.isLogin = false
  }
}
