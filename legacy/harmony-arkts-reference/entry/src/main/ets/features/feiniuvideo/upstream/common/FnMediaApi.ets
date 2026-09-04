/*
 * Portions adapted from FNOSP/FlyNarwhal and modified for HarmonyOS.
 * FlyNarwhal is licensed under AGPL-3.0-only. See THIRD_PARTY_NOTICES.md.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

import { http } from "@kit.NetworkKit";
import { common } from "@kit.AbilityKit";
import { BusinessError, request } from "@kit.BasicServicesKit";
import CryptoJS from "@ohos/crypto-js";
import { hilog } from "@kit.PerformanceAnalysisKit";
import { FnMediaAuthSnapshot, FnMediaAuthStore } from "./FnMediaAuthStore";
import { FnosHostResolver } from "./FnosHostResolver";
import { FnosRequestHostResolver } from "./FnosRequestHostResolver";
import { SessionStore } from "./SessionStore";
import {
  FnMediaAuthReq,
  FnMediaAuthResp,
  FnMediaAuthDirResp,
  FnMediaEpisodeItem,
  FnMediaGuidReq,
  FnMediaGenreItem,
  FnMediaItem,
  FnMediaItemDetailResp,
  FnMediaItemFile,
  FnMediaItemListReq,
  FnMediaItemListResp,
  FnMediaItemListTags,
  FnMediaLibraryItem,
  FnMediaListResp,
  FnMediaLoginReq,
  FnMediaLoginResp,
  FnMediaOauthAuthorizeReq,
  FnMediaOauthAuthorizeResp,
  FnMediaPlayRecordReq,
  FnMediaPlayInfoReq,
  FnMediaPlayInfoResp,
  FnMediaPlayPlayReq,
  FnMediaPlayPlayResp,
  FnMediaPlayItem,
  FnMediaRunningTask,
  FnMediaPersonItemListReq,
  FnMediaPersonItemListResp,
  FnMediaPersonListResp,
  FnMediaPersonResp,
  FnMediaPReq,
  FnMediaResetQualityResp,
  FnMediaScrapReq,
  FnMediaScrapRescrapReq,
  FnMediaScrapSearchItem,
  FnMediaScrapSearchReq,
  FnMediaSeasonItem,
  FnMediaServerPathItem,
  FnMediaSetConfigByItemReq,
  FnMediaStreamData,
  FnMediaStreamResp,
  FnMediaStreamReq,
  FnMediaStreamListData,
  FnMediaStreamListResp,
  FnMediaSubtitleDownloadReq,
  FnMediaSubtitleItem,
  FnMediaSubtitleItemResp,
  FnMediaSubtitleMarkReq,
  FnMediaSubtitleSearchReq,
  FnMediaSubtitleSearchResp,
  FnMediaSysConfigResp,
  FnMediaTagItem,
  FnMediaTagListResp,
  FnMediaTranscodeResp,
  FnMediaUserInfoResp
} from "../entity/FnMediaResp";

class FnMediaApiConstants {
  static readonly API_KEY: string = "NDzZTVxnRKP8Z0jXg1VAMonaG8akvh";
  static readonly API_SECRET: string = "16CCEB3D-AB42-077D-36A1-F355324E4237";
  static readonly USER_AGENT: string = "1.32.2 (com.trim.app; build:1322007; Android 36) okHttp/4.9.0";
  static readonly CLIENT: string = "android-media";
  static readonly CLIENT_VERSION: string = "1322007";
}

interface FnMediaEnvelope<T> {
  code?: number;
  msg?: string;
  data?: T;
}

class FnMediaEmptyPayload {
}

export class FnMediaItemListFilter {
  genres: number = 0;
  resolution: string = "";
  colorRange: string = "";
  audioType: string = "";
  decade: string = "";
  locate: string = "";
  recognitionStatus: string = "";
  watched: string = "";
  type: string = "";
}

interface FnMediaServerPathReq {
  path: string;
}

export class FnMediaApi {
  static async getSysConfig(host: string): Promise<FnMediaSysConfigResp> {
    const response = await FnMediaApi.request<FnMediaSysConfigResp>(
      host,
      "/v/api/v1/sys/config",
      http.RequestMethod.GET
    );
    const appId = response.data?.nas_oauth?.app_id || "";
    const oauthUrl = response.data?.nas_oauth?.url || "";
    if (appId.length > 0) {
      FnMediaAuthStore.saveOauthConfig(appId, FnMediaApi.resolveOauthBaseUrl(host, oauthUrl));
    }
    return response;
  }

  static async auth(host: string, code: string): Promise<FnMediaAuthResp> {
    const payload: FnMediaAuthReq = {
      source: "Trim-NAS",
      code: code
    };
    const response = await FnMediaApi.request<FnMediaAuthResp>(
      host,
      "/v/api/v1/auth",
      http.RequestMethod.POST,
      payload,
      undefined,
      false,
      15000,
      false,
      false
    );
    FnMediaApi.persistAuthTokenFromResponse(response.token || response.data?.token || "");
    return response;
  }

  static async authorizeWithNasToken(host: string, nasToken: string, appId: string, baseUrl: string,
    state: string = ""): Promise<string> {
    const normalizedToken = nasToken.trim();
    const normalizedAppId = appId.trim();
    const normalizedBaseUrl = baseUrl.trim();
    if (normalizedToken.length === 0) {
      throw new Error("NAS 登录 token 为空");
    }
    if (normalizedAppId.length === 0 || normalizedBaseUrl.length === 0) {
      throw new Error("飞牛影视授权配置不完整");
    }
    const path = "/oauthapi/authorize";
    const payload: FnMediaOauthAuthorizeReq = {
      token: normalizedToken,
      client_id: normalizedAppId,
      redirect_uri: `${normalizedBaseUrl}/v/oauth/result`,
      state: state,
      response_type: "code"
    };
    const baseUrls = await FnMediaApi.resolveRequestBaseUrls(host, path);
    FnMediaApi.appendBaseUrl(baseUrls, normalizedBaseUrl);
    let lastError: Error = new Error("NAS 账号授权飞牛影视失败");
    for (let index = 0; index < baseUrls.length; index += 1) {
      const httpRequest = http.createHttp();
      try {
        const requestBaseUrl = baseUrls[index];
        const headers: Record<string, string> = {
          "Content-Type": "application/json",
          "Accept": "application/json",
          "User-Agent": FnMediaApiConstants.USER_AGENT,
          "X-Trim-Client": FnMediaApiConstants.CLIENT,
          "X-Trim-Client-Version": FnMediaApiConstants.CLIENT_VERSION
        };
        FnosHostResolver.applyRelayCookie(headers, host);
        FnosHostResolver.applyRelayCookie(headers, requestBaseUrl);
        const requestUrl = `${requestBaseUrl}${path}`;
        const data = await httpRequest.request(requestUrl, {
          method: http.RequestMethod.POST,
          expectDataType: http.HttpDataType.STRING,
          header: headers,
          extraData: JSON.stringify(payload),
          readTimeout: 15000,
          connectTimeout: 10000
        });
        FnMediaApi.saveResponseCookies(data.header);
        const redirectUrl = FnMediaApi.resolveRedirectUrl(requestBaseUrl, FnMediaApi.headerValue(data.header, "location"));
        const redirectCode = FnMediaApi.oauthCodeFromText(redirectUrl);
        if (redirectCode.length > 0) {
          FnMediaApi.updateResolvedMediaBaseUrl(FnMediaApi.baseUrlFromUrl(redirectUrl) || normalizedBaseUrl);
          return redirectCode;
        }
        const bodyText = `${data.result || ""}`.trim();
        let response: FnMediaOauthAuthorizeResp;
        try {
          response = JSON.parse(bodyText) as FnMediaOauthAuthorizeResp;
        } catch (error) {
          const bodyCode = FnMediaApi.oauthCodeFromText(bodyText);
          if (bodyCode.length > 0) {
            FnMediaApi.updateResolvedMediaBaseUrl(FnMediaApi.baseUrlFromUrl(redirectUrl) || requestBaseUrl);
            return bodyCode;
          }
          hilog.error(0x0000, "fnos-media-api",
            "nas oauth authorize parse failed status=%{public}d url=%{public}s bodyPrefix=%{public}s err=%{public}s",
            data.responseCode, requestUrl, bodyText.substring(0, 100), (error as Error).message || "unknown");
          throw error as Error;
        }
        if (response.code !== 0) {
          const message = response.msg && response.msg.trim().length > 0
            ? response.msg.trim()
            : `NAS 账号授权飞牛影视失败(${response.code})`;
          throw new Error(message);
        }
        const code = response.data?.code || "";
        if (code.trim().length === 0) {
          throw new Error("NAS 账号授权未返回 code");
        }
        FnMediaApi.updateResolvedMediaBaseUrl(requestBaseUrl);
        return code.trim();
      } catch (error) {
        lastError = error as Error;
        hilog.warn(0x0000, "fnos-media-api",
          "nas oauth authorize failed index=%{public}d total=%{public}d err=%{public}s",
          index + 1, baseUrls.length, lastError.message || "unknown");
      } finally {
        httpRequest.destroy();
      }
    }
    throw lastError;
  }

  static async login(host: string, username: string, password: string): Promise<FnMediaLoginResp> {
    const payload: FnMediaLoginReq = {
      username: username.trim(),
      password: password,
      app_name: "trimemedia-web"
    };
    const response = await FnMediaApi.request<FnMediaLoginResp>(
      host,
      "/v/api/v1/login",
      http.RequestMethod.POST,
      payload,
      undefined,
      false,
      15000,
      false,
      false
    );
    FnMediaApi.persistAuthTokenFromResponse(response.token || response.data?.token || "");
    return response;
  }

  static async oauthResult(host: string, code: string, state?: string): Promise<boolean> {
    const normalizedCode = code.trim();
    if (normalizedCode.length === 0) {
      throw new Error("影视授权缺少 code");
    }
    const normalizedState = (state || "").trim();
    const queryState = normalizedState.length > 0 ? normalizedState : "undefined";
    const path = `/v/oauth/result?code=${encodeURIComponent(normalizedCode)}&state=${encodeURIComponent(queryState)}`;
    const baseUrls = await FnMediaApi.resolveRequestBaseUrls(host, path);
    let lastError: Error = new Error("请求失败");
    for (let index = 0; index < baseUrls.length; index += 1) {
      const httpRequest = http.createHttp();
      try {
        const baseUrl = baseUrls[index];
        const payload = new FnMediaEmptyPayload();
        const data = await httpRequest.request(`${baseUrl}${path}`, {
          method: http.RequestMethod.POST,
          expectDataType: http.HttpDataType.STRING,
          header: FnMediaApi.buildHeaders(baseUrl, path, payload),
          extraData: JSON.stringify(payload),
          readTimeout: 15000,
          connectTimeout: 10000
        });
        FnMediaApi.saveResponseCookies(data.header);
        const bodyText = `${data.result || ""}`.trim();
        if (bodyText.length > 0) {
          try {
            const parsedEnvelope = JSON.parse(bodyText) as FnMediaEnvelope<Object>;
            if (typeof parsedEnvelope.code === "number" && parsedEnvelope.code !== 0) {
              throw new Error(
                parsedEnvelope.msg && parsedEnvelope.msg.trim().length > 0
                  ? parsedEnvelope.msg.trim()
                  : `请求失败(${parsedEnvelope.code})`
              );
            }
          } catch (error) {
            const message = (error as Error).message || "";
            if (message.indexOf("请求失败(") >= 0) {
              throw error as Error;
            }
          }
        }
        FnMediaApi.updateResolvedMediaBaseUrl(baseUrl);
        return true;
      } catch (error) {
        lastError = error as Error;
      } finally {
        httpRequest.destroy();
      }
    }
    throw lastError;
  }

  static async getMediaDbList(host: string): Promise<FnMediaLibraryItem[]> {
    const response = await FnMediaApi.request<FnMediaListResp<FnMediaLibraryItem[]>>(
      host,
      "/v/api/v1/mediadb/list",
      http.RequestMethod.GET,
      undefined,
      undefined,
      true
    );
    return response.data || [];
  }

  static async getMediaDbSum(host: string): Promise<Record<string, number>> {
    const response = await FnMediaApi.request<FnMediaListResp<Record<string, number>>>(
      host,
      "/v/api/v1/mediadb/sum",
      http.RequestMethod.GET,
      undefined,
      undefined,
      true
    );
    return response.data || {};
  }

  static async getPlayList(host: string): Promise<FnMediaPlayItem[]> {
    const response = await FnMediaApi.request<FnMediaListResp<FnMediaPlayItem[]>>(
      host,
      "/v/api/v1/play/list",
      http.RequestMethod.GET,
      undefined,
      undefined,
      true
    );
    return response.data || [];
  }

  static async getRunningTasks(host: string): Promise<FnMediaRunningTask[]> {
    const response = await FnMediaApi.request<FnMediaListResp<FnMediaRunningTask[]>>(
      host,
      "/v/api/v1/task/running",
      http.RequestMethod.GET,
      undefined,
      undefined,
      true
    );
    return response.data || [];
  }

  static async getUserInfo(host: string): Promise<FnMediaUserInfoResp> {
    return await FnMediaApi.request<FnMediaUserInfoResp>(
      host,
      "/v/api/v1/user/info",
      http.RequestMethod.GET,
      undefined,
      undefined,
      true
    );
  }

  static async getPlayInfo(host: string, itemGuid: string, mediaGuid?: string): Promise<FnMediaPlayInfoResp> {
    const payload: FnMediaPlayInfoReq = {
      item_guid: itemGuid
    };
    if (mediaGuid && mediaGuid.trim().length > 0) {
      payload.media_guid = mediaGuid.trim();
    }
    return await FnMediaApi.request<FnMediaPlayInfoResp>(
      host,
      "/v/api/v1/play/info",
      http.RequestMethod.POST,
      payload,
      undefined,
      true
    );
  }

  static async getItemList(host: string, ancestorGuid: string, category?: string,
    pageSize: number = 36, page: number = 1, sortColumn: string = "create_time",
    sortType: string = "DESC", filter?: FnMediaItemListFilter): Promise<FnMediaItem[]> {
    const response = await FnMediaApi.getItemListPage(host, ancestorGuid, category, pageSize, page, sortColumn, sortType, filter);
    return response.data && response.data.list ? response.data.list : [];
  }

  static async getItemListPage(host: string, ancestorGuid: string, category?: string,
    pageSize: number = 36, page: number = 1, sortColumn: string = "create_time",
    sortType: string = "DESC", filter?: FnMediaItemListFilter): Promise<FnMediaItemListResp> {
    const tags = FnMediaApi.itemListTags(category, filter);
    const payload: FnMediaItemListReq = {
      ancestor_guid: ancestorGuid.trim().length > 0 ? ancestorGuid.trim() : undefined,
      exclude_grouped_video: 1,
      sort_type: sortType.trim().length > 0 ? sortType.trim().toUpperCase() : "DESC",
      sort_column: sortColumn.trim().length > 0 ? sortColumn.trim() : "create_time",
      page_size: pageSize,
      page: page,
      tags: tags
    };
    return await FnMediaApi.request<FnMediaItemListResp>(
      host,
      "/v/api/v1/item/list",
      http.RequestMethod.POST,
      payload,
      undefined,
      true
    );
  }

  static async getFavoriteList(host: string, ancestorGuid: string = "", category?: string,
    pageSize: number = 36, page: number = 1, sortColumn: string = "create_time",
    sortType: string = "DESC", filter?: FnMediaItemListFilter): Promise<FnMediaItemListResp> {
    const tags = FnMediaApi.itemListTags(category, filter);
    const payload: FnMediaItemListReq = {
      ancestor_guid: ancestorGuid.trim().length > 0 ? ancestorGuid.trim() : undefined,
      exclude_grouped_video: 1,
      sort_type: sortType.trim().length > 0 ? sortType.trim().toUpperCase() : "DESC",
      sort_column: sortColumn.trim().length > 0 ? sortColumn.trim() : "create_time",
      page_size: pageSize,
      page: page,
      tags: tags
    };
    return await FnMediaApi.request<FnMediaItemListResp>(
      host,
      "/v/api/v1/favorite/list",
      http.RequestMethod.POST,
      payload,
      undefined,
      true
    );
  }

  static async favorite(host: string, guid: string): Promise<boolean> {
    const payload: FnMediaGuidReq = {
      item_guid: guid.trim()
    };
    await FnMediaApi.request<FnMediaEnvelope<Object>>(
      host,
      "/v/api/v1/item/favorite",
      http.RequestMethod.PUT,
      payload,
      undefined,
      true
    );
    return true;
  }

  static async cancelFavorite(host: string, guid: string): Promise<boolean> {
    const payload: FnMediaGuidReq = {
      item_guid: guid.trim()
    };
    await FnMediaApi.request<FnMediaEnvelope<Object>>(
      host,
      "/v/api/v1/item/favorite",
      http.RequestMethod.DELETE,
      payload,
      undefined,
      true
    );
    return true;
  }

  static async watched(host: string, guid: string): Promise<boolean> {
    const payload: FnMediaGuidReq = {
      item_guid: guid.trim()
    };
    await FnMediaApi.request<FnMediaEnvelope<Object>>(
      host,
      "/v/api/v1/item/watched",
      http.RequestMethod.POST,
      payload,
      undefined,
      true
    );
    return true;
  }

  static async cancelWatched(host: string, guid: string): Promise<boolean> {
    const payload: FnMediaGuidReq = {
      item_guid: guid.trim()
    };
    await FnMediaApi.request<FnMediaEnvelope<Object>>(
      host,
      "/v/api/v1/item/watched",
      http.RequestMethod.DELETE,
      payload,
      undefined,
      true
    );
    return true;
  }

  static async getGenres(host: string, lan: string = "zh"): Promise<FnMediaGenreItem[]> {
    const query: Record<string, Object | string | number | boolean> = {
      "lan": lan
    };
    const response = await FnMediaApi.request<FnMediaListResp<FnMediaGenreItem[]>>(
      host,
      "/v/api/v1/tag/genres",
      http.RequestMethod.GET,
      undefined,
      query,
      true
    );
    return response.data || [];
  }

  static async getTag(host: string, tag: string, lan: string = "zh"): Promise<FnMediaTagItem[]> {
    const query: Record<string, Object | string | number | boolean> = {
      "lan": lan
    };
    const response = await FnMediaApi.request<FnMediaListResp<FnMediaTagItem[]>>(
      host,
      `/v/api/v1/tag/${encodeURIComponent(tag.trim())}`,
      http.RequestMethod.GET,
      undefined,
      query,
      true
    );
    return response.data || [];
  }

  static async getTagList(host: string, ancestorGuid: string = "", isFavorite: number = 0,
    type: string = ""): Promise<FnMediaTagListResp> {
    const query: Record<string, Object | string | number | boolean> = {
      "is_favorite": isFavorite
    };
    if (ancestorGuid.trim().length > 0) {
      query["ancestor_guid"] = ancestorGuid.trim();
    }
    if (type.trim().length > 0) {
      query["type"] = type.trim();
    }
    return await FnMediaApi.request<FnMediaTagListResp>(
      host,
      "/v/api/v1/tag/list",
      http.RequestMethod.GET,
      undefined,
      query,
      true
    );
  }

  static async search(host: string, keyword: string): Promise<FnMediaItem[]> {
    const query: Record<string, Object | string | number | boolean> = {
      "q": keyword.trim()
    };
    const response = await FnMediaApi.request<FnMediaListResp<FnMediaItem[]>>(
      host,
      "/v/api/v1/search/list",
      http.RequestMethod.GET,
      undefined,
      query,
      true
    );
    return response.data || [];
  }

  static async getItem(host: string, guid: string): Promise<FnMediaItemDetailResp> {
    const response = await FnMediaApi.request<FnMediaListResp<FnMediaItemDetailResp>>(
      host,
      `/v/api/v1/item/${guid.trim()}`,
      http.RequestMethod.GET,
      undefined,
      undefined,
      true
    );
    if (response.data) {
      return response.data;
    }
    throw new Error("影视详情为空");
  }

  static async getSeasonList(host: string, guid: string): Promise<FnMediaSeasonItem[]> {
    const response = await FnMediaApi.request<FnMediaListResp<FnMediaSeasonItem[]>>(
      host,
      `/v/api/v1/season/list/${guid.trim()}`,
      http.RequestMethod.GET,
      undefined,
      undefined,
      true
    );
    return response.data || [];
  }

  static async getEpisodeList(host: string, guid: string): Promise<FnMediaEpisodeItem[]> {
    const response = await FnMediaApi.request<FnMediaListResp<FnMediaEpisodeItem[]>>(
      host,
      `/v/api/v1/episode/list/${guid.trim()}`,
      http.RequestMethod.GET,
      undefined,
      undefined,
      true
    );
    return response.data || [];
  }

  static async getStreamList(host: string, guid: string, beforePlay?: number): Promise<FnMediaStreamListData> {
    let query: Record<string, Object | string | number | boolean> | undefined = undefined;
    if (beforePlay !== undefined) {
      query = {
        "before_play": beforePlay
      };
    }
    const response = await FnMediaApi.request<FnMediaStreamListResp>(
      host,
      `/v/api/v1/stream/list/${guid.trim()}`,
      http.RequestMethod.GET,
      undefined,
      query,
      true
    );
    if (response.data) {
      return response.data;
    }
    const emptyData: FnMediaStreamListData = {};
    return emptyData;
  }

  static async playPlay(host: string, request: FnMediaPlayPlayReq): Promise<FnMediaPlayPlayResp> {
    return await FnMediaApi.request<FnMediaPlayPlayResp>(
      host,
      "/v/api/v1/play/play",
      http.RequestMethod.POST,
      request,
      undefined,
      true,
      60000
    );
  }

  static async playRecord(host: string, request: FnMediaPlayRecordReq): Promise<boolean> {
    await FnMediaApi.request<FnMediaEnvelope<Object>>(
      host,
      "/v/api/v1/play/record",
      http.RequestMethod.POST,
      request,
      undefined,
      true
    );
    return true;
  }

  static async setConfigByItem(host: string, request: FnMediaSetConfigByItemReq): Promise<boolean> {
    await FnMediaApi.request<FnMediaEnvelope<Object>>(
      host,
      "/v/api/v1/play/setConfigByItem",
      http.RequestMethod.POST,
      request,
      undefined,
      true
    );
    return true;
  }

  static async stream(host: string, request: FnMediaStreamReq): Promise<FnMediaStreamData> {
    const response = await FnMediaApi.request<FnMediaStreamResp>(
      host,
      "/v/api/v1/stream",
      http.RequestMethod.POST,
      request,
      undefined,
      true
    );
    return response.data || new FnMediaEmptyPayload();
  }

  static async getOfficialStreamInfo(host: string, mediaGuid: string): Promise<FnMediaStreamData> {
    const normalizedGuid = mediaGuid.trim();
    if (normalizedGuid.length === 0) {
      throw new Error("影视媒体 GUID 为空");
    }
    const ip = await FnMediaApi.officialStreamIp(host);
    return await FnMediaApi.stream(host, {
      media_guid: normalizedGuid,
      ip: ip,
      level: 1,
      header: {
        "User-Agent": [FnMediaApi.mediaUserAgent()]
      }
    });
  }

  private static async officialStreamIp(host: string): Promise<string> {
    const userInfo = await FnMediaApi.getUserInfo(host);
    const sources = userInfo.data?.sources || [];
    const sourceName = sources.length > 0 ? `${sources[0].source_name || ""}`.trim() : "";
    return CryptoJS.MD5(sourceName).toString();
  }

  static async getAppAuthorizedDir(host: string, withoutCache: number = 0): Promise<FnMediaAuthDirResp> {
    const query: Record<string, Object | string | number | boolean> = {
      "without_cache": withoutCache
    };
    return await FnMediaApi.request<FnMediaAuthDirResp>(
      host,
      "/v/api/v1/server/getAppAuthorizedDir",
      http.RequestMethod.GET,
      undefined,
      query,
      true
    );
  }

  static async getFilesByServerPath(host: string, path: string): Promise<FnMediaServerPathItem[]> {
    const payload: FnMediaServerPathReq = {
      path: path
    };
    const response = await FnMediaApi.request<FnMediaListResp<FnMediaServerPathItem[]>>(
      host,
      "/v/api/v1/server/path",
      http.RequestMethod.POST,
      payload,
      undefined,
      true
    );
    return response.data || [];
  }

  static async logout(host: string): Promise<boolean> {
    await FnMediaApi.request<FnMediaEnvelope<Object>>(
      host,
      "/v/api/v1/user/logout",
      http.RequestMethod.POST,
      undefined,
      undefined,
      true
    );
    FnMediaAuthStore.clearSession();
    return true;
  }

  static async personList(host: string, guid: string): Promise<FnMediaPersonListResp> {
    return await FnMediaApi.request<FnMediaPersonListResp>(
      host,
      `/v/api/v1/person/list/${guid.trim()}`,
      http.RequestMethod.POST,
      undefined,
      undefined,
      true
    );
  }

  static async person(host: string, guid: string): Promise<FnMediaPersonResp> {
    return await FnMediaApi.request<FnMediaPersonResp>(
      host,
      `/v/api/v1/person/${guid.trim()}`,
      http.RequestMethod.GET,
      undefined,
      undefined,
      true
    );
  }

  static async personItemList(host: string, request: FnMediaPersonItemListReq): Promise<FnMediaPersonItemListResp> {
    return await FnMediaApi.request<FnMediaPersonItemListResp>(
      host,
      "/v/api/v1/person/item/list",
      http.RequestMethod.POST,
      request,
      undefined,
      true
    );
  }

  static async subtitleSearch(host: string, request: FnMediaSubtitleSearchReq): Promise<FnMediaSubtitleSearchResp> {
    return await FnMediaApi.request<FnMediaSubtitleSearchResp>(
      host,
      "/v/api/v1/subtitle/search",
      http.RequestMethod.POST,
      request,
      undefined,
      true
    );
  }

  static async subtitleDownload(host: string, request: FnMediaSubtitleDownloadReq): Promise<FnMediaSubtitleItemResp> {
    return await FnMediaApi.request<FnMediaSubtitleItemResp>(
      host,
      "/v/api/v1/subtitle/download",
      http.RequestMethod.POST,
      request,
      undefined,
      true
    );
  }

  static async subtitleMark(host: string, request: FnMediaSubtitleMarkReq): Promise<FnMediaSubtitleItem[]> {
    const response = await FnMediaApi.request<FnMediaListResp<FnMediaSubtitleItem[]>>(
      host,
      "/v/api/v1/subtitle/mark",
      http.RequestMethod.PUT,
      request,
      undefined,
      true
    );
    return response.data || [];
  }

  static async uploadSubtitle(context: common.UIAbilityContext, host: string, mediaGuid: string,
    filePath: string, fileName: string): Promise<boolean> {
    const normalizedMediaGuid = mediaGuid.trim();
    const normalizedFilePath = filePath.trim();
    const normalizedFileName = fileName.trim().length > 0 ? fileName.trim() : "subtitle.srt";
    if (normalizedMediaGuid.length === 0) {
      throw new Error("当前影视条目缺少媒体标识");
    }
    if (normalizedFilePath.length === 0) {
      throw new Error("请选择要上传的字幕文件");
    }
    const path = "/v/api/v1/subtitle/upload";
    const baseUrls = await FnMediaApi.resolveRequestBaseUrls(host, path);
    let lastError: Error = new Error("字幕上传失败");
    for (let index = 0; index < baseUrls.length; index += 1) {
      const baseUrl = baseUrls[index];
      try {
        await FnMediaApi.uploadSubtitleToBaseUrl(context, baseUrl, path, normalizedMediaGuid, normalizedFilePath, normalizedFileName);
        FnMediaApi.updateResolvedMediaBaseUrl(baseUrl);
        return true;
      } catch (error) {
        lastError = error as Error;
        hilog.warn(0x0000, "fnos-media-api",
          "subtitle upload failed index=%{public}d total=%{public}d err=%{public}s",
          index + 1, baseUrls.length, lastError.message || "unknown");
      }
    }
    throw lastError;
  }

  static async deleteSubtitle(host: string, subtitleGuid: string): Promise<boolean> {
    const payload: Record<string, Object | string | number | boolean> = {
      "subtitle_guid": subtitleGuid.trim()
    };
    await FnMediaApi.request<FnMediaEnvelope<Object>>(
      host,
      "/v/api/v1/subtitle/del",
      http.RequestMethod.DELETE,
      payload,
      undefined,
      true
    );
    return true;
  }

  static async mediaItemFile(host: string, guid: string): Promise<FnMediaItemFile[]> {
    const response = await FnMediaApi.request<FnMediaListResp<FnMediaItemFile[]>>(
      host,
      `/v/api/v1/media/itemfile/${guid.trim()}`,
      http.RequestMethod.GET,
      undefined,
      undefined,
      true
    );
    return response.data || [];
  }

  static async scrap(host: string, guid: string, mediaGuids: string[]): Promise<boolean> {
    const payload: FnMediaScrapReq = {
      media_guids: mediaGuids
    };
    await FnMediaApi.request<FnMediaEnvelope<Object>>(
      host,
      `/v/api/v1/scrap/${guid.trim()}`,
      http.RequestMethod.DELETE,
      payload,
      undefined,
      true
    );
    return true;
  }

  static async scrapSearch(host: string, request: FnMediaScrapSearchReq): Promise<FnMediaScrapSearchItem[]> {
    const response = await FnMediaApi.request<FnMediaListResp<FnMediaScrapSearchItem[]>>(
      host,
      "/v/api/v1/scrap/search",
      http.RequestMethod.POST,
      request,
      undefined,
      true
    );
    return response.data || [];
  }

  static async scrapRescrap(host: string, request: FnMediaScrapRescrapReq): Promise<boolean> {
    await FnMediaApi.request<FnMediaEnvelope<Object>>(
      host,
      "/v/api/v1/scrap/rescrap",
      http.RequestMethod.POST,
      request,
      undefined,
      true
    );
    return true;
  }

  static async mediaTranscodeStatus(host: string, request: FnMediaPReq): Promise<FnMediaTranscodeResp> {
    return await FnMediaApi.request<FnMediaTranscodeResp>(
      host,
      "/v/api/v1/media/p",
      http.RequestMethod.POST,
      request,
      undefined,
      true,
      60000
    );
  }

  static async mediaResetQuality(host: string, request: FnMediaPReq): Promise<FnMediaResetQualityResp> {
    return await FnMediaApi.mediaP(host, request);
  }

  static async mediaP(host: string, request: FnMediaPReq): Promise<FnMediaResetQualityResp> {
    return await FnMediaApi.request<FnMediaResetQualityResp>(
      host,
      "/v/api/v1/media/p",
      http.RequestMethod.POST,
      request,
      undefined,
      true,
      60000
    );
  }

  static async mediaQuit(host: string, playLink: string): Promise<FnMediaResetQualityResp> {
    return await FnMediaApi.mediaP(host, {
      req: "media.quit",
      reqid: "1234567890ABCDEF",
      playLink: playLink
    });
  }

  static mediaDirectPlayUrl(host: string, mediaGuid: string, byteOffset: number = -1): string {
    const baseUrl = `${FnMediaApi.resolveBaseUrl(host)}/v/api/v1/media/range/${mediaGuid.trim()}`;
    if (byteOffset > 0) {
      return `${baseUrl}?range=bytes=${Math.floor(byteOffset)}-`;
    }
    return baseUrl;
  }

  static mediaBaseUrl(host: string): string {
    return FnMediaApi.resolveBaseUrl(host);
  }

  static mediaImageUrl(host: string, imagePath: string, width: number = 400): string {
    const trimmed = imagePath.trim();
    if (trimmed.length === 0) {
      return "";
    }
    return `${FnMediaApi.resolveBaseUrl(host)}/v/api/v1/sys/img${trimmed}?w=${width}`;
  }

  static subtitleDownloadUrl(host: string, subtitleGuid: string): string {
    const normalizedGuid = subtitleGuid.trim();
    if (normalizedGuid.length === 0) {
      return "";
    }
    return `${FnMediaApi.resolveBaseUrl(host)}/v/api/v1/subtitle/dl/${encodeURIComponent(normalizedGuid)}`;
  }

  static mediaPlaybackHeadersText(hostOrUrl?: string): string {
    const headers = FnMediaApi.mediaRequestHeaders(hostOrUrl);
    const cookie = headers["Cookie"] || "";
    if (cookie.trim().length > 0) {
      // FlyNarwhal 播放链路传给播放器的是小写 cookie。这里同时保留两种 key，
      // 避免 HarmonyOS 底层播放器对 header 大小写处理不一致导致授权 cookie 丢失。
      headers["cookie"] = cookie;
    }
    return JSON.stringify(headers);
  }

  static mediaUserAgent(): string {
    return FnMediaApiConstants.USER_AGENT;
  }

  static mediaRequestHeaders(hostOrUrl?: string): Record<string, string> {
    const mediaSnapshot: FnMediaAuthSnapshot = FnMediaAuthStore.getSnapshot();
    const mediaToken = mediaSnapshot.token.trim();
    if (mediaToken.length === 0) {
      return {};
    }
    const headers: Record<string, string> = {
      "Authorization": mediaToken
    };
    const cookie = FnMediaApi.mergeCookieHeader(mediaSnapshot.cookie, mediaToken).trim();
    if (cookie.length > 0) {
      headers["Cookie"] = cookie;
    }
    if (hostOrUrl && hostOrUrl.trim().length > 0) {
      FnosHostResolver.applyRelayCookie(headers, hostOrUrl);
    }
    return headers;
  }

  private static itemTypesByCategory(category?: string): string[] {
    const normalized = `${category || ""}`.trim().toLowerCase();
    if (normalized === "movie") {
      return ["Movie"];
    }
    if (normalized === "tv") {
      return ["TV"];
    }
    if (normalized === "directory") {
      return ["Directory"];
    }
    if (normalized === "video") {
      return ["Video"];
    }
    if (normalized === "live" || normalized === "livetv" || normalized === "iptv" || normalized === "livechannel") {
      return ["LiveChannel"];
    }
    if (normalized === "others") {
      return ["Directory", "Video"];
    }
    return ["Movie", "TV", "Directory", "Video", "LiveChannel"];
  }

  private static itemListTags(category?: string, filter?: FnMediaItemListFilter): FnMediaItemListTags {
    const tags: FnMediaItemListTags = {
      type: FnMediaApi.itemTypesByCategory(filter?.type && filter.type.trim().length > 0 ? filter.type : category)
    };
    if (!filter) {
      return tags;
    }
    if (filter.genres > 0) {
      tags.genres = filter.genres;
    }
    if (filter.resolution.trim().length > 0) {
      tags.resolution = filter.resolution.trim();
    }
    if (filter.colorRange.trim().length > 0) {
      tags.color_range = filter.colorRange.trim();
    }
    if (filter.audioType.trim().length > 0) {
      tags.audio_type = filter.audioType.trim();
    }
    if (filter.decade.trim().length > 0) {
      tags.decade = filter.decade.trim();
    }
    if (filter.locate.trim().length > 0) {
      tags.locate = filter.locate.trim();
    }
    if (filter.recognitionStatus.trim().length > 0) {
      tags.recognition_status = filter.recognitionStatus.trim();
    }
    if (filter.watched.trim().length > 0) {
      tags.watched = filter.watched.trim();
    }
    return tags;
  }

  static async requestRaw<T>(
    host: string,
    path: string,
    method: http.RequestMethod,
    body?: Object,
    parameters?: Record<string, Object | string | number | boolean>,
    requireMediaToken: boolean = false,
    readTimeoutMs: number = 15000,
    includeStoredMediaToken: boolean = true,
    includeStoredCookie: boolean = true
  ): Promise<T> {
    return await FnMediaApi.request<T>(
      host,
      path,
      method,
      body,
      parameters,
      requireMediaToken,
      readTimeoutMs,
      includeStoredMediaToken,
      includeStoredCookie
    );
  }

  private static async request<T>(
    host: string,
    path: string,
    method: http.RequestMethod,
    body?: Object,
    parameters?: Record<string, Object | string | number | boolean>,
    requireMediaToken: boolean = false,
    readTimeoutMs: number = 15000,
    includeStoredMediaToken: boolean = true,
    includeStoredCookie: boolean = true
  ): Promise<T> {
    const baseUrls = await FnMediaApi.resolveRequestBaseUrls(host, path);
    const requestPath = FnMediaApi.appendQuery(path, parameters);
    let lastError: Error = new Error("请求失败");
    for (let index = 0; index < baseUrls.length; index += 1) {
      const httpRequest = http.createHttp();
      try {
        const baseUrl = baseUrls[index];
        const requestUrl = `${baseUrl}${requestPath}`;
        const effectiveBody = FnMediaApi.effectiveRequestBody(method, body);
        const options: http.HttpRequestOptions = {
          method: method,
          expectDataType: http.HttpDataType.STRING,
          header: FnMediaApi.buildHeaders(baseUrl, path, effectiveBody, parameters, requireMediaToken, includeStoredMediaToken,
            includeStoredCookie),
          readTimeout: readTimeoutMs,
          connectTimeout: 10000
        };
        if (effectiveBody !== undefined) {
          options.extraData = JSON.stringify(effectiveBody);
        }
        const data = await httpRequest.request(requestUrl, options);
        FnMediaApi.saveResponseCookies(data.header);
        const responseCode = data.responseCode;
        if (FnMediaApi.isRedirectCode(responseCode)) {
          const redirectUrl = FnMediaApi.resolveRedirectUrl(baseUrl, FnMediaApi.headerValue(data.header, "location"));
          hilog.warn(0x0000, "fnos-media-api",
            "media request redirect code=%{public}d from=%{public}s to=%{public}s",
            responseCode, requestUrl, redirectUrl);
          if (redirectUrl.length > 0) {
            const redirectBaseUrl = FnMediaApi.baseUrlFromUrl(redirectUrl);
            if (redirectBaseUrl.length > 0) {
              FnMediaApi.updateResolvedMediaBaseUrl(redirectBaseUrl);
              FnMediaApi.appendBaseUrl(baseUrls, redirectBaseUrl);
              lastError = new Error("影视接口发生跳转，已切换地址重试");
              continue;
            }
          }
        }
        const bodyText = `${data.result || ""}`;
        if (FnMediaApi.shouldLogRawMediaPath(path)) {
          hilog.info(0x0000, "fnos-media-api",
            "media raw path=%{public}s code=%{public}d url=%{public}s len=%{public}d hasIPTV=%{public}s hasMobile=%{public}s hasLive=%{public}s",
            path,
            responseCode,
            requestUrl,
            bodyText.length,
            bodyText.indexOf("IPTV") >= 0 ? "yes" : "no",
            bodyText.indexOf("移动") >= 0 ? "yes" : "no",
            bodyText.indexOf("LiveChannel") >= 0 || bodyText.indexOf("\"live\"") >= 0 ? "yes" : "no");
        }
        let parsedEnvelope: FnMediaEnvelope<Object>;
        try {
          parsedEnvelope = JSON.parse(bodyText) as FnMediaEnvelope<Object>;
        } catch (error) {
          hilog.error(0x0000, "fnos-media-api",
            "media response parse failed code=%{public}d url=%{public}s bodyPrefix=%{public}s err=%{public}s",
            responseCode, requestUrl, bodyText.substring(0, 80), (error as Error).message || "unknown");
          throw error as Error;
        }
        if (!parsedEnvelope || typeof parsedEnvelope !== "object") {
          throw new Error("影视接口返回空响应");
        }
        if (typeof parsedEnvelope.code === "number" && parsedEnvelope.code !== 0) {
          const authExpired = requireMediaToken && FnMediaApi.isAuthExpiredResponse(parsedEnvelope.code, parsedEnvelope.msg || "");
          let errorMessage = `请求失败(${parsedEnvelope.code})`;
          if (authExpired) {
            errorMessage = "影视授权已失效，请重新授权";
          } else if (parsedEnvelope.msg && parsedEnvelope.msg.trim().length > 0) {
            errorMessage = parsedEnvelope.msg.trim();
          }
          hilog.warn(0x0000, "fnos-media-api",
            "media request rejected code=%{public}d path=%{public}s url=%{public}s token=%{public}s msg=%{public}s",
            parsedEnvelope.code, path, requestUrl, requireMediaToken ? "required" : "optional",
            parsedEnvelope.msg || "");
          throw new Error(errorMessage);
        }
        FnMediaApi.updateResolvedMediaBaseUrl(baseUrl);
        return parsedEnvelope as T;
      } catch (error) {
        lastError = error as Error;
        hilog.warn(0x0000, "fnos-media-api",
          "media request failed path=%{public}s index=%{public}d total=%{public}d err=%{public}s",
          path, index + 1, baseUrls.length, lastError.message || "unknown");
      } finally {
        httpRequest.destroy();
      }
    }
    throw lastError;
  }

  private static isAuthExpiredResponse(code: number, message: string): boolean {
    if (code === 401 || code === 403 || code === 100401 || code === 100403) {
      return true;
    }
    const normalized = message.trim().toLowerCase();
    if (normalized.length === 0) {
      return false;
    }
    return normalized.indexOf("unauthorized") >= 0
      || normalized.indexOf("invalid token") >= 0
      || normalized.indexOf("token invalid") >= 0
      || normalized.indexOf("token expired") >= 0
      || normalized.indexOf("登录过期") >= 0
      || normalized.indexOf("token失效") >= 0
      || normalized.indexOf("授权已失效") >= 0
      || normalized.indexOf("鉴权失败") >= 0;
  }

  private static isRedirectCode(code: number): boolean {
    return code === 301 || code === 302 || code === 303 || code === 307 || code === 308;
  }

  private static effectiveRequestBody(method: http.RequestMethod, body?: Object): Object | undefined {
    if (body !== undefined) {
      return body;
    }
    if (method === http.RequestMethod.POST) {
      return new FnMediaEmptyPayload();
    }
    return undefined;
  }

  private static headerValue(headers: Object, key: string): string {
    const record = headers as Record<string, Object | string | string[]>;
    const targetKey = key.toLowerCase();
    const keys = Object.keys(record);
    for (let index = 0; index < keys.length; index += 1) {
      const currentKey = keys[index];
      if (currentKey.toLowerCase() !== targetKey) {
        continue;
      }
      const value = record[currentKey];
      if (Array.isArray(value)) {
        return value.length > 0 ? `${value[0]}` : "";
      }
      return `${value || ""}`;
    }
    return "";
  }

  private static saveResponseCookies(headers: Object): void {
    const cookieValues = FnMediaApi.headerValues(headers, "set-cookie");
    if (cookieValues.length === 0) {
      return;
    }
    const normalizedCookie = FnMediaApi.normalizeSetCookieHeaders(cookieValues);
    if (normalizedCookie.length > 0) {
      FnMediaAuthStore.saveCookie(normalizedCookie);
    }
  }

  private static persistAuthTokenFromResponse(token: string): string {
    const normalizedToken = token.trim();
    if (normalizedToken.length > 0) {
      FnMediaAuthStore.saveToken(normalizedToken);
      return normalizedToken;
    }
    return FnMediaAuthStore.getSnapshot().token.trim();
  }

  private static headerValues(headers: Object, key: string): string[] {
    const record = headers as Record<string, Object | string | string[]>;
    const targetKey = key.toLowerCase();
    const keys = Object.keys(record);
    for (let index = 0; index < keys.length; index += 1) {
      const currentKey = keys[index];
      if (currentKey.toLowerCase() !== targetKey) {
        continue;
      }
      const value = record[currentKey];
      if (Array.isArray(value)) {
        return value.map((item: string) => `${item || ""}`).filter((item: string) => item.trim().length > 0);
      }
      const text = `${value || ""}`.trim();
      return text.length > 0 ? [text] : [];
    }
    return [];
  }

  private static normalizeSetCookieHeaders(cookieValues: string[]): string {
    const pairs: string[] = [];
    cookieValues.forEach((cookieText: string) => {
      FnMediaApi.splitSetCookieHeader(cookieText).forEach((segment: string) => {
        const pair = FnMediaApi.cookiePairFromSetCookie(segment);
        if (pair.length > 0) {
          pairs.push(pair);
        }
      });
    });
    return pairs.join("; ");
  }

  private static cookiePairFromSetCookie(segment: string): string {
    const trimmed = segment.trim();
    if (trimmed.length === 0) {
      return "";
    }
    const firstPart = trimmed.split(";")[0].trim();
    const equalIndex = firstPart.indexOf("=");
    return equalIndex > 0 ? firstPart : "";
  }

  private static splitSetCookieHeader(cookieText: string): string[] {
    const normalized = cookieText.trim();
    if (normalized.length === 0) {
      return [];
    }
    const segments: string[] = [];
    let start = 0;
    let index = 0;
    while (index < normalized.length) {
      if (normalized[index] === "," && FnMediaApi.isSetCookieBoundary(normalized, index + 1)) {
        segments.push(normalized.substring(start, index).trim());
        start = index + 1;
      }
      index += 1;
    }
    segments.push(normalized.substring(start).trim());
    return segments.filter((segment: string) => segment.length > 0);
  }

  private static isSetCookieBoundary(text: string, startIndex: number): boolean {
    let index = startIndex;
    while (index < text.length && text[index] === " ") {
      index += 1;
    }
    const nextSemicolon = text.indexOf(";", index);
    const nextComma = text.indexOf(",", index);
    let end = text.length;
    if (nextSemicolon >= 0 && nextSemicolon < end) {
      end = nextSemicolon;
    }
    if (nextComma >= 0 && nextComma < end) {
      end = nextComma;
    }
    const candidate = text.substring(index, end);
    const equalIndex = candidate.indexOf("=");
    if (equalIndex <= 0) {
      return false;
    }
    const cookieName = candidate.substring(0, equalIndex).trim();
    if (cookieName.length === 0) {
      return false;
    }
    for (let charIndex = 0; charIndex < cookieName.length; charIndex += 1) {
      const code = cookieName.charCodeAt(charIndex);
      const valid = (code >= 48 && code <= 57)
        || (code >= 65 && code <= 90)
        || (code >= 97 && code <= 122)
        || cookieName[charIndex] === "-"
        || cookieName[charIndex] === "_";
      if (!valid) {
        return false;
      }
    }
    return true;
  }

  private static resolveRedirectUrl(baseUrl: string, location: string): string {
    const trimmed = location.trim();
    if (trimmed.length === 0) {
      return "";
    }
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
      return trimmed;
    }
    if (trimmed.startsWith("/")) {
      return `${baseUrl}${trimmed}`;
    }
    return `${baseUrl}/${trimmed}`;
  }

  private static oauthCodeFromText(text: string): string {
    const normalized = text.trim();
    if (normalized.length === 0) {
      return "";
    }
    const markerIndex = normalized.indexOf("code=");
    if (markerIndex < 0) {
      return "";
    }
    let start = markerIndex + "code=".length;
    while (start < normalized.length && (normalized[start] === "\"" || normalized[start] === "'")) {
      start += 1;
    }
    let end = start;
    while (end < normalized.length) {
      const char = normalized[end];
      if (char === "&" || char === "#" || char === "\"" || char === "'" || char === "<" || char === ">"
        || char === " " || char === "\n" || char === "\r" || char === "\t") {
        break;
      }
      end += 1;
    }
    if (end <= start) {
      return "";
    }
    const rawCode = normalized.substring(start, end).trim();
    if (rawCode.length === 0) {
      return "";
    }
    try {
      return decodeURIComponent(rawCode).trim();
    } catch (_) {
      return rawCode;
    }
  }

  private static baseUrlFromUrl(url: string): string {
    const normalized = url.trim();
    const match = normalized.match(/^(https?:\/\/[^/]+)/);
    if (!match || match.length < 2) {
      return "";
    }
    return match[1];
  }

  private static buildHeaders(
    host: string,
    path: string,
    body?: Object,
    parameters?: Record<string, Object | string | number | boolean>,
    requireMediaToken: boolean = false,
    includeStoredMediaToken: boolean = true,
    includeStoredCookie: boolean = true
  ): Object {
    const headers: Record<string, string> = {
      "Content-Type": "application/json; charset=utf-8",
      "Accept": "application/json",
      "User-Agent": FnMediaApiConstants.USER_AGENT,
      "X-Trim-Client": FnMediaApiConstants.CLIENT,
      "X-Trim-Client-Version": FnMediaApiConstants.CLIENT_VERSION,
      "Authx": FnMediaApi.genAuthx(path, parameters, body)
    };
    const mediaSnapshot: FnMediaAuthSnapshot = FnMediaAuthStore.getSnapshot();
    const mediaToken = mediaSnapshot.token.trim();
    if (requireMediaToken && mediaToken.length === 0) {
      throw new Error("飞牛影视尚未完成授权");
    }
    if (includeStoredMediaToken && mediaToken.length > 0) {
      headers["Authorization"] = mediaToken;
      headers["Cookie"] = FnMediaApi.mergeCookieHeader(mediaSnapshot.cookie, mediaToken);
      FnosHostResolver.applyRelayCookie(headers, host);
      return headers;
    }
    if (includeStoredCookie && mediaSnapshot.cookie.trim().length > 0) {
      headers["Cookie"] = mediaSnapshot.cookie.trim();
    }
    FnosHostResolver.applyRelayCookie(headers, host);
    return headers;
  }

  private static buildUploadHeaders(host: string, path: string): Object {
    const headers: Record<string, string> = {
      "Accept": "application/json",
      "User-Agent": FnMediaApiConstants.USER_AGENT,
      "X-Trim-Client": FnMediaApiConstants.CLIENT,
      "X-Trim-Client-Version": FnMediaApiConstants.CLIENT_VERSION,
      "Authx": FnMediaApi.genAuthx(path)
    };
    const mediaSnapshot: FnMediaAuthSnapshot = FnMediaAuthStore.getSnapshot();
    const mediaToken = mediaSnapshot.token.trim();
    if (mediaToken.length === 0) {
      throw new Error("飞牛影视尚未完成授权");
    }
    headers["Authorization"] = mediaToken;
    headers["Cookie"] = FnMediaApi.mergeCookieHeader(mediaSnapshot.cookie, mediaToken);
    FnosHostResolver.applyRelayCookie(headers, host);
    return headers;
  }

  private static uploadSubtitleToBaseUrl(context: common.UIAbilityContext, baseUrl: string, path: string,
    mediaGuid: string, filePath: string, fileName: string): Promise<void> {
    const attachments: Array<request.agent.FormItem> = [{
      name: "file",
      value: {
        filename: fileName,
        path: filePath
      }
    }, {
      name: "guid",
      value: mediaGuid
    }];
    const config: request.agent.Config = {
      action: request.agent.Action.UPLOAD,
      url: `${baseUrl}${path}`,
      title: `上传字幕 ${fileName}`,
      description: "飞牛影视字幕上传",
      mode: request.agent.Mode.FOREGROUND,
      overwrite: true,
      method: "POST",
      headers: FnMediaApi.buildUploadHeaders(baseUrl, path),
      data: attachments,
      network: request.agent.Network.ANY,
      gauge: true
    };
    return new Promise<void>((resolve, reject) => {
      request.agent.create(context, config).then((task: request.agent.Task) => {
        let settled = false;
        task.on('completed', (_: request.agent.Progress) => {
          if (settled) {
            return;
          }
          settled = true;
          request.agent.remove(task.tid);
          resolve();
        });
        task.on('failed', (progress: request.agent.Progress) => {
          if (settled) {
            return;
          }
          settled = true;
          request.agent.remove(task.tid);
          reject(new Error(`字幕上传失败，状态 ${progress.state}`));
        });
        task.start((err: BusinessError) => {
          if (err) {
            if (!settled) {
              settled = true;
              request.agent.remove(task.tid);
              reject(new Error(err.message || "字幕上传启动失败"));
            }
          }
        });
      }).catch((err: BusinessError) => {
        reject(new Error(err.message || "创建字幕上传任务失败"));
      });
    });
  }

  private static mergeCookieHeader(cookieText: string, mediaToken: string): string {
    const cookieMap: Map<string, string> = new Map<string, string>();
    cookieText.split(";").forEach((segment: string) => {
      const trimmed = segment.trim();
      if (trimmed.length === 0) {
        return;
      }
      const equalIndex = trimmed.indexOf("=");
      if (equalIndex <= 0) {
        return;
      }
      const key = trimmed.substring(0, equalIndex).trim();
      const value = trimmed.substring(equalIndex + 1).trim();
      if (key.length > 0) {
        cookieMap.set(key, value);
      }
    });
    cookieMap.set("Trim-MC-token", mediaToken);
    const pairs: string[] = [];
    cookieMap.forEach((value: string, key: string) => {
      pairs.push(`${key}=${value}`);
    });
    return pairs.join("; ");
  }

  private static resolveBaseUrl(host: string): string {
    const currentHost = SessionStore.currentHost().trim();
    if (currentHost.length > 0) {
      return FnosHostResolver.normalizeHttpBaseUrl(currentHost, SessionStore.getSnapshot().httpsAccess);
    }
    const snapshot = FnMediaAuthStore.getSnapshot();
    if (snapshot.baseUrl.trim().length > 0) {
      return snapshot.baseUrl.trim();
    }
    return FnosHostResolver.normalizeHttpBaseUrl(host, SessionStore.getSnapshot().httpsAccess);
  }

  private static async resolveRequestBaseUrls(host: string, path: string): Promise<string[]> {
    const candidates: string[] = [];
    const fallbackBaseUrls = await FnosRequestHostResolver.resolveHttpBaseUrls(host);
    fallbackBaseUrls.forEach((candidate: string) => {
      FnMediaApi.appendBaseUrl(candidates, candidate);
    });
    if (path !== "/v/api/v1/sys/config") {
      const mediaBaseUrl = FnMediaAuthStore.getSnapshot().baseUrl.trim();
      if (mediaBaseUrl.length > 0) {
        FnMediaApi.appendBaseUrl(candidates, mediaBaseUrl);
      }
    }
    return candidates;
  }

  private static shouldLogRawMediaPath(path: string): boolean {
    return path === "/v/api/v1/mediadb/list"
      || path === "/v/api/v1/mediadb/sum"
      || path === "/v/api/v1/play/list"
      || path === "/v/api/v1/play/info"
      || path === "/v/api/v1/play/play"
      || path.startsWith("/v/api/v1/stream/list/");
  }

  private static appendBaseUrl(targets: string[], candidate: string): void {
    const normalized = FnosHostResolver.normalizeHttpBaseUrl(candidate, SessionStore.getSnapshot().httpsAccess).trim();
    if (normalized.length === 0 || targets.indexOf(normalized) >= 0) {
      return;
    }
    targets.push(normalized);
  }

  private static appendQuery(path: string, parameters?: Record<string, Object | string | number | boolean>): string {
    if (parameters === undefined) {
      return path;
    }
    const parts: string[] = [];
    Object.keys(parameters).sort().forEach((key: string) => {
      const value = parameters[key];
      if (value !== undefined && value !== null) {
        parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(`${value}`)}`);
      }
    });
    if (parts.length === 0) {
      return path;
    }
    return `${path}${path.indexOf("?") >= 0 ? "&" : "?"}${parts.join("&")}`;
  }

  private static resolveOauthBaseUrl(host: string, oauthUrl: string): string {
    const trimmedOauth = oauthUrl.trim();
    if (trimmedOauth.length > 0 && trimmedOauth !== "://"
      && (trimmedOauth.startsWith("http://") || trimmedOauth.startsWith("https://"))) {
      return trimmedOauth.replace(/\/$/, "");
    }
    return FnMediaApi.resolveBaseUrl(host);
  }

  private static updateResolvedMediaBaseUrl(baseUrl: string): void {
    const normalized = baseUrl.trim();
    if (normalized.length === 0) {
      return;
    }
    FnMediaAuthStore.saveBaseUrl(normalized);
  }

  private static genAuthx(
    path: string,
    parameters?: Record<string, Object | string | number | boolean>,
    body?: Object
  ): string {
    const nonce = `${Math.floor(Math.random() * 900000) + 100000}`;
    const timestamp = `${Date.now()}`;
    const dataMd5 = FnMediaApi.dataMd5(parameters, body);
    const signStr = `${FnMediaApiConstants.API_KEY}_${path}_${nonce}_${timestamp}_${dataMd5}_${FnMediaApiConstants.API_SECRET}`;
    const sign = CryptoJS.MD5(signStr).toString();
    return `nonce=${nonce}&timestamp=${timestamp}&sign=${sign}`;
  }

  private static dataMd5(
    parameters?: Record<string, Object | string | number | boolean>,
    body?: Object
  ): string {
    if (body !== undefined) {
      return CryptoJS.MD5(JSON.stringify(body)).toString();
    }
    if (parameters !== undefined) {
      const parts: string[] = [];
      Object.keys(parameters).sort().forEach((key: string) => {
        const value = parameters[key];
        if (value !== undefined && value !== null) {
          parts.push(`${key}=${value}`);
        }
      });
      return CryptoJS.MD5(parts.join("&")).toString();
    }
    return CryptoJS.MD5("").toString();
  }
}
