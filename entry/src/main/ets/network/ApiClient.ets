import axios, { AxiosError, AxiosInstance, AxiosResponse } from "@ohos/axios";
import { Api } from "sdk/src/main/ets/api";
import jellyfin from "sdk/src/main/ets/jellyfin";
import emitter from "@ohos.events.emitter";
import { TokenExpiredEvent } from "../events/Events";

export class ApiClient extends Api {
  private static api?: Api
  private static responseInterceptorId: number = -1

  static isInitialized(): boolean {
    return ApiClient.api !== undefined
  }

  static Instance(): Api {
    if (!ApiClient.api) {
      throw Error('ApiClient未初始化')
    }
    return ApiClient.api!
  }

  static update(host: string): Api {
    if (!ApiClient.api) {
      ApiClient.api = jellyfin.createApi(host, undefined, axios.create())
    } else {
      ApiClient.api.basePath = host.endsWith('/') ? host.slice(0, -1) : host;
    }
    const axiosInstance = ApiClient.api.axiosInstance as AxiosInstance
    if (ApiClient.responseInterceptorId < 0) {
      ApiClient.responseInterceptorId = axiosInstance.interceptors.response.use((res: AxiosResponse) => {
        return res
      }, (error: AxiosError<Object, Object>) => {
        const requestUrl: string = error.config?.url || ''
        const isPasswordLogin: boolean = requestUrl.includes('/Users/AuthenticateByName')
        const authorizationHeader: string = String(error.config?.headers.getAuthorization() || '')
        const hasRequestAccessToken: boolean = /(?:^|,\s*)Token="[^"]+"/.test(authorizationHeader)
        if (error.response?.status === 401 && !isPasswordLogin && hasRequestAccessToken) {
          emitter.emit(TokenExpiredEvent)
        }
        console.error(`HTTP request failed: ${error.message}`)
        return Promise.reject(error)
      })
    }
    return ApiClient.api!
  }
}
