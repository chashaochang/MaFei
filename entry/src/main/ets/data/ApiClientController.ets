import jellyfin from "sdk/src/main/ets/jellyfin";
import { ApiClient } from "../network/ApiClient";
import { ObjectUtil } from "../utils/ObjectUtil";
import { AppPreference } from "./AppPreference";
import { DBManager } from "./DBManager";
import { ServerDao } from "./ServerDao";
import ServerEntity from "./ServerEntity";
import { UserDao } from "./UserDao";
import { ValuesBucket } from "@kit.ArkData";
import { webview } from "@kit.ArkWeb";

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

  setupUser(serverId: number, userId: string, accessToken: string, username?: string) {
    this.userDao.upsert(serverId, userId, accessToken, username)
    this.appPreference.setCurrentUserId(userId)
    if (!!username) {
      this.appPreference.setUserName(username)
    }
    this.configureApiClientUser(userId, accessToken)
  }

  updateUserName(userId: string, username: string) {
    let valueBucket: ValuesBucket = {
      USER_ID: userId,
      USER_NAME: username,
      LAST_LOGIN_TIMESTAMP: Date.now()
    }
    this.userDao.updateDataSync(valueBucket)
  }

  setupServer(hostname: string, serverName?: string, version?: string) {
    let server = this.serverDao.getServerByHostname(hostname)
    let id: number
    if (!server && serverName !== undefined && version !== undefined) {
      id = this.serverDao.insert(hostname, serverName, version)
    } else if (!server) {
      id = this.serverDao.insert(hostname, '', '')
    } else {
      id = server.id
    }
    this.appPreference.setCurrentServerId(id)
    ApiClient.update(hostname)
  }

  updateServerName(server: ServerEntity) {
    let valueBucket: ValuesBucket = {
      id: server.id,
      server_name: server.servername,
      version: server.version,
      last_used_timestamp: Date.now()
    }
    this.serverDao.updateDataSync(valueBucket)
  }

  loadSavedServer(): ServerEntity | null {
    let server: ServerEntity | null = null
    let serverId = this.appPreference.getCurrentServerId()
    if (serverId !== null) {
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
    let info = ObjectUtil.Assign({}, this.baseDeviceInfo)
    info.id = this.baseDeviceInfo.id + userId
    ApiClient.Instance().deviceInfo = info
  }

  logout() {
    //this.userDao.deleteUser()
    AppPreference.getInstance().setAccessToken('')
    AppPreference.getInstance().setCurrentUserId('')
    AppPreference.getInstance().setUserName('')
    webview.WebviewController.removeAllCache(true)
    // ApiClient.Instance().logout()
  }
}