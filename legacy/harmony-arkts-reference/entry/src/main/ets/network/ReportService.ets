import http from '@ohos.net.http';
import { AppPreference } from '../data/AppPreference';
import { Constants } from '../utils/Constants';

export interface ReportPayload {
  title: string;
  itemId: string;
  serverUrl?: string;
  source?: string;
  description?: string;
  playSessionId?: string;
  client?: string;
  reporterId?: string;
  reporterName?: string;
}

interface ReportResponse {
  ok?: boolean;
  id?: string;
  message?: string;
  code?: string;
  retryAfterSeconds?: number;
}

interface ReportRequestHeaders {
  'Content-Type': string;
  'x-report-token'?: string;
}

export interface ReportSubmitResult {
  ok: boolean;
  message: string;
  alreadyReported?: boolean;
  rateLimited?: boolean;
  retryAfterSeconds?: number;
}

export class ReportService {
  private static readonly SUBMIT_URL: string = 'https://mafei.hmos.site/api/report';
  private static readonly REPORT_TOKEN: string = 'hsfjkdfhjkdhfjksdhfkhsdjfk';
  private static readonly REPORTED_IDS_KEY: string = Constants.PREF_REPORTED_CONTENT_IDS;

  static isEnabled(): boolean {
    return true;
  }

  private static buildReportedItemKey(itemId: string, serverUrl?: string): string {
    const preference = AppPreference.getInstance();
    const serverId = preference.getCurrentServerSystemId() || serverUrl || 'unknown-server';
    const userId = preference.getCurrentUserId() || 'unknown-user';
    return `${serverId}::${userId}::${itemId}`;
  }

  private static getReportedItemKeys(): string[] {
    return AppPreference.getInstance().getValue<string[]>(ReportService.REPORTED_IDS_KEY) || [];
  }

  private static saveReportedItemKeys(keys: string[]): void {
    AppPreference.getInstance().setValue<string[]>(ReportService.REPORTED_IDS_KEY, keys);
  }

  static hasReported(itemId: string, serverUrl?: string): boolean {
    const normalizedItemId = itemId.trim();
    if (normalizedItemId.length === 0) {
      return false;
    }
    const key = ReportService.buildReportedItemKey(normalizedItemId, serverUrl);
    return ReportService.getReportedItemKeys().includes(key);
  }

  static markReported(itemId: string, serverUrl?: string): void {
    const normalizedItemId = itemId.trim();
    if (normalizedItemId.length === 0) {
      return;
    }
    const key = ReportService.buildReportedItemKey(normalizedItemId, serverUrl);
    const keys = ReportService.getReportedItemKeys();
    if (!keys.includes(key)) {
      keys.push(key);
      ReportService.saveReportedItemKeys(keys);
    }
  }

  private static buildHeaders(token: string): ReportRequestHeaders {
    if (token.length > 0) {
      return {
        'Content-Type': 'application/json',
        'x-report-token': token
      };
    }
    return {
      'Content-Type': 'application/json'
    };
  }

  private static parseResponseBody(result: string | Object | ArrayBuffer): ReportResponse {
    if (typeof result === 'string') {
      if (result.length === 0) {
        return {};
      }
      try {
        return JSON.parse(result) as ReportResponse;
      } catch {
        return {};
      }
    }
    if (result instanceof ArrayBuffer) {
      return {};
    }
    return result as ReportResponse;
  }

  private static buildSuccessResult(response: http.HttpResponse): ReportSubmitResult {
    const responseBody = ReportService.parseResponseBody(response.result);
    if (response.responseCode === http.ResponseCode.OK || response.responseCode === http.ResponseCode.CREATED) {
      if (responseBody.ok === false) {
        return {
          ok: false,
          message: responseBody.message || '提交失败'
        };
      }
      return {
        ok: true,
        message: responseBody.message || '举报已提交'
      };
    }
    if (response.responseCode === 409 || responseBody.code === 'DUPLICATE_REPORT') {
      return {
        ok: false,
        alreadyReported: true,
        message: responseBody.message || '该视频已举报过，请勿重复举报'
      };
    }
    if (response.responseCode === 429 || responseBody.code === 'RATE_LIMITED') {
      return {
        ok: false,
        rateLimited: true,
        retryAfterSeconds: responseBody.retryAfterSeconds,
        message: responseBody.message || '提交过于频繁，请稍后再试'
      };
    }
    return {
      ok: false,
      message: responseBody.message || `提交失败(${response.responseCode})`
    };
  }

  private static buildErrorResult(error: Error): ReportSubmitResult {
    return {
      ok: false,
      message: error.message || '网络异常'
    };
  }

  static async submitReport(payload: ReportPayload): Promise<ReportSubmitResult> {
    const requestBody: ReportPayload = {
      title: payload.title,
      itemId: payload.itemId,
      serverUrl: payload.serverUrl,
      source: payload.source,
      description: payload.description,
      playSessionId: payload.playSessionId,
      client: payload.client || 'mafei-ohos',
      reporterId: payload.reporterId,
      reporterName: payload.reporterName,
    };
    const httpRequest = http.createHttp();
    const options: http.HttpRequestOptions = {
      method: http.RequestMethod.POST,
      header: ReportService.buildHeaders(ReportService.REPORT_TOKEN),
      extraData: JSON.stringify(requestBody),
      expectDataType: http.HttpDataType.STRING,
      readTimeout: 8000,
      connectTimeout: 8000,
      usingCache: false
    };

    try {
      const response = await httpRequest.request(ReportService.SUBMIT_URL, options);
      const result = ReportService.buildSuccessResult(response);
      if (result.ok || result.alreadyReported) {
        ReportService.markReported(payload.itemId, payload.serverUrl);
      }
      return result;
    } catch (error) {
      return ReportService.buildErrorResult(error as Error);
    } finally {
      httpRequest.destroy();
    }
  }
}
