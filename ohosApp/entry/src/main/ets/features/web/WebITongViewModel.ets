import { BaseViewModel } from "../../common/base/BaseViewModel";
import { WebITongUIState } from "./WebITongUIState";
import { webview } from "@kit.ArkWeb";
import { NativeInterface } from "../../bridge/NativeInterface";
import { NativePlayer } from "../../bridge/NativePlayer";
import { ExternalPlayer } from "../../bridge/ExternalPlayer";
import { ActivityEventHandler } from "../../events/ActivityEventHandler";
import { AppPreference } from "../../data/AppPreference";
import { DBManager } from "../../data/DBManager";
import { ApiClient } from "../../network/ApiClient";
import { getUserApi } from "sdk/src/main/ets/utils/api/user-api";
import { AxiosResponse } from "@ohos/axios";
import { UserDto } from "sdk/src/main/ets/generated-client/models";
import { Credentials } from "../../entity/Credentials";
import { Constants } from "../../utils/Constants";
import { util } from "@kit.ArkTS";

export class WebITongViewModel extends BaseViewModel<WebITongUIState> {
  controller: webview.WebviewController = new webview.WebviewController();
  responseWeb: WebResourceResponse = new WebResourceResponse()
  isFirst: boolean = true

  //注册桥接接口
  registerJavaScriptProxy(activityEventHandler: ActivityEventHandler) {
    this.controller.registerJavaScriptProxy(new NativeInterface(activityEventHandler), 'NativeInterface',
      ['getDeviceInformation', 'enableFullscreen', 'disableFullscreen', 'openUrl', 'updateMediaSession',
        'hideMediaSession', 'updateVolumeLevel', 'downloadFiles', 'openClientSettings', 'openServerSelection',
        'exitApp'])
    this.controller.registerJavaScriptProxy(new NativePlayer(), 'NativePlayer',
      ['isEnabled', 'loadPlayer', 'pausePlayer', 'resumePlayer', 'stopPlayer', 'destroyPlayer', 'seek', 'seekMs',
        'setVolume'])
    this.controller.registerJavaScriptProxy(new ExternalPlayer(), 'ExternalPlayer', ['isEnabled'])
  }

  //注入登录凭证
  injectCredentials() {
    if (!this.isFirst) {
      return
    }
    this.controller.runJavaScript("JSON.parse(window.localStorage.getItem('jellyfin_credentials'))")
      .then(result => {
        let serverId = AppPreference.getInstance().getCurrentServerId()
        let serverEntity = DBManager.getInstance().getServerDao().getServer(serverId!)
        let userId = ApiClient.Instance().userId
        let token: string = ApiClient.Instance().accessToken
        getUserApi(ApiClient.Instance()).getCurrentUser()
          .then((res: AxiosResponse<UserDto>) => {
            let user = res.data
            let credentials: Credentials | null = JSON.parse(result)
            if (credentials == null || credentials == undefined) {
              credentials = {
                Servers: [{
                  AccessToken: token,
                  DateLastAccessed: Date.now(),
                  ExchangeToken: null,
                  Id: serverId + '',
                  LastConnectionMode: 2, //0/1/2 代表自动发现、本地直连、手动地址
                  LocalAddress: "",
                  ManualAddress: ApiClient.Instance().basePath,
                  Name: serverEntity?.servername || '',
                  UserId: userId,
                  manualAddressOnly: true
                }]
              }
            }
            let server = credentials.Servers[0]
            server.UserId = userId
            server.AccessToken = token
            this.controller.runJavaScript('window.localStorage.setItem("jellyfin_credentials", JSON.stringify(' +
            JSON.stringify(credentials) + '))')
              .then(() => {
                this.controller.runJavaScript('window.localStorage.setItem("user-' + userId +
                  '-' + serverId + '", JSON.stringify(' +
                JSON.stringify(user) + '))')
                  .then(() => {
                    this.controller.refresh()
                    this.isFirst = false
                  }).catch()
              }).catch()
          })

      })
      .catch(() => {
        throw Error('Failed to extract credentials')
      })
  }

  //拦截请求，添加认证头
  interceptRequest(path: string): WebResourceResponse | null {
    //console.log(path);
    if (path.match(Constants.MAIN_BUNDLE_PATH_REGEX) && !path?.includes('deferred')) {
      const promise: Promise<String> = new Promise((resolve: Function, _reject: Function) => {
        //this.onConnectedToWebapp()
        let buffer = getContext().resourceManager.getRawFileContentSync('native/injectionScript.js')
        let decoder = util.TextDecoder.create('utf-8');
        let str = decoder.decodeToString(new Uint8Array(buffer));
        this.responseWeb.setResponseData(str)
        this.responseWeb.setResponseMimeType('application/javascript')
        this.responseWeb.setResponseEncoding('utf-8');
        this.responseWeb.setResponseCode(200);
        this.responseWeb.setReasonMessage('OK');
        console.log('注入native/injectionScript.js');
        resolve("success");
      })
      promise.then(() => {
        console.log("prepare response ready");
        this.responseWeb.setResponseIsReady(true);
      })
      this.responseWeb.setResponseIsReady(false);
      return this.responseWeb
    }
    if (path.includes('/native/')) {
      const promise: Promise<String> = new Promise((resolve: Function, _reject: Function) => {
        let buffer = getContext().resourceManager.getRawFileContentSync(`native/${getLastPathSegment(path)}`)
        let decoder = util.TextDecoder.create('utf-8');
        let str = decoder.decodeToString(new Uint8Array(buffer));
        this.responseWeb.setResponseData(str)
        this.responseWeb.setResponseMimeType('application/javascript')
        this.responseWeb.setResponseEncoding('utf-8');
        this.responseWeb.setResponseCode(200);
        this.responseWeb.setReasonMessage('OK');
        console.log(`注入native/${getLastPathSegment(path)}`);
        resolve("success");
      })
      promise.then(() => {
        console.log("prepare response ready");
        this.responseWeb.setResponseIsReady(true);
      })
      this.responseWeb.setResponseIsReady(false);
      return this.responseWeb
    }
    return null
  }
}

function getLastPathSegment(path: string): string {
  // 使用 split 方法
  const segments = path.split('/')

  // 返回最后一个非空片段
  return segments[segments.length - 1]
}