import jellyfin from "sdk/src/main/ets/jellyfin";
import { ApiClient } from "../network/ApiClient";
import { ObjectUtil } from "../utils/ObjectUtil";
import { AppPreference } from "./AppPreference";
import { DBManager } from "./DBManager";
import { ServerDao } from "./ServerDao";
import ServerEntity from "./ServerEntity";
import { UserDao } from "./UserDao";

export class ApiClientController {
  appPreference: AppPreference
  serverDao: ServerDao
  userDao: UserDao
  baseDeviceInfo = jellyfin.deviceInfo

  constructor() {
    this.appPreference = AppPreference.getInstance()
    this.serverDao = DBManager.getInstance().getServerDao()
    this.userDao = DBManager.getInstance().getUserDao()
  }

  setupUser(serverId: number, userId: string, accessToken: string) {
    this.userDao.upsert(serverId,userId,accessToken)
    this.appPreference.setCurrentUserId(userId)
    this.configureApiClientUser(userId, accessToken)
  }

  setupServer(hostname: string) {
    let server = this.serverDao.getServerByHostname(hostname)
    let id: number
    if (!server) {
      id = this.serverDao.insert(hostname)
    } else {
      id = server.id
    }
    this.appPreference.setCurrentServerId(id)
    ApiClient.update(hostname)
  }

  loadSavedServer(): ServerEntity | null {
    let server: ServerEntity | null = null
    let serverId = this.appPreference.getCurrentServerId()
    if (serverId) {
      server = this.serverDao.getServer(serverId)
    }
    this.configureApiClientServer(server)
    return server
  }

  loadPreviouslyUsedServers(): ServerEntity[] {
    return this.serverDao.getAllServers().filter(server => {
      server.id === this.appPreference.getCurrentServerId()
    })
  }

  configureApiClientServer(server: ServerEntity | null) {
    if (server) {
      ApiClient.update(server?.hostname)
    }
  }

  configureApiClientUser(userId: string, accessToken: string) {
    ApiClient.Instance().userId = userId
    ApiClient.Instance().accessToken = accessToken
    // Append user id to device id to ensure uniqueness across sessions
    let info = ObjectUtil.Assign({},this.baseDeviceInfo)
    info.id = this.baseDeviceInfo.id + userId
    ApiClient.Instance().deviceInfo = info
  }

  logout(){
    this.userDao.deleteUser()
    AppPreference.getInstance().setAccessToken('')
    AppPreference.getInstance().setCurrentUserId('')
    ApiClient.Instance().logout()
  }
}