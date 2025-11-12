import { Api } from "sdk/src/main/ets/api";
import jellyfin from "sdk/src/main/ets/jellyfin";

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
    } else {
      ApiClient.api.basePath = host.endsWith('/') ? host.slice(0, -1) : host;
    }
    return ApiClient.api!
  }
}