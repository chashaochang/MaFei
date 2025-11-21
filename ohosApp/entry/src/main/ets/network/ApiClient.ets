import { AxiosInstance, AxiosResponse } from "@ohos/axios";
import { Api } from "sdk/src/main/ets/api";
import jellyfin from "sdk/src/main/ets/jellyfin";
import emitter from "@ohos.events.emitter";
import { TokenExpiredEvent } from "../events/Events";

export class ApiClient extends Api {
  private static api?: Api

  static Instance(): Api {
    if (!ApiClient.api) {
      throw Error('ApiClient未初始化')
    }
    return ApiClient.api!
  }

  static update(host: string): Api {
    if (!ApiClient.api) {
      ApiClient.api = jellyfin.createApi(host)
      let axiosInstance = ApiClient.api.axiosInstance as AxiosInstance
      axiosInstance.interceptors.response.use((res: AxiosResponse) => {
        if (res.status === 401) {
          //处理未授权情况，比如token过期
          emitter.emit(TokenExpiredEvent)
        }
        return res;
      })
    } else {
      ApiClient.api.basePath = host.endsWith('/') ? host.slice(0, -1) : host;
    }
    return ApiClient.api!
  }
}