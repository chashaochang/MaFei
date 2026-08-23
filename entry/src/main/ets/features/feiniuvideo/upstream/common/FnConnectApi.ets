import { http } from "@kit.NetworkKit";
import CryptoJS from "@ohos/crypto-js";
import { hilog } from "@kit.PerformanceAnalysisKit";
import { buffer } from "@kit.ArkTS";
import { FnConnectPreferenceStore, FnConnectRoutePreference } from "./FnConnectPreferenceStore";

interface FnConnectPortInfo {
  httpsPort?: number;
  httpPort?: number;
}

interface FnConnectResolveData {
  ipv4?: string[];
  ipv6?: string[];
  publicIpv4?: string[];
  publicIpv6?: string[];
  fn?: string[];
  port?: FnConnectPortInfo;
}

interface FnConnectResolveEnvelope {
  code?: number;
  msg?: string;
  data?: FnConnectResolveData;
}

interface FnConnectResolveRequest {
  fnId: string;
}

export class FnConnectResolveResult {
  fnId: string = "";
  preferredBaseUrl: string = "";
  fallbackBaseUrls: string[] = [];
  usedLan: boolean = false;
  preferredRoute: string = "relay";
}

class FnConnectTargetCandidate {
  baseUrl: string = "";
  route: string = "relay";
}

class FnConnectProbeResult {
  baseUrl: string = "";
  route: string = "relay";
  reachable: boolean = false;
  elapsedMs: number = 0;
}

class FnConnectResolveCacheItem {
  result: FnConnectResolveResult = new FnConnectResolveResult();
  expiresAt: number = 0;
}

export class FnConnectApi {
  private static readonly API_URL: string = "https://5ddd.com/api/v1/fn/con";
  private static readonly AUTHX_PREFIX: string = "NDzZTVxnRKP8Z0jXg1VAMonaG8akvh";
  private static readonly AUTHX_API_KEY: string = "zIGtkc3dqZnJpd29qZXJqa2w7c";
  private static readonly RESOLVE_CACHE_MS: number = 30000;
  private static resolveCache: Map<string, FnConnectResolveCacheItem> = new Map<string, FnConnectResolveCacheItem>();
  private static pendingResolve: Map<string, Promise<FnConnectResolveResult>> = new Map<string, Promise<FnConnectResolveResult>>();

  static async resolveFnId(fnId: string): Promise<string> {
    const result = await FnConnectApi.resolveFnTargets(fnId);
    return result.preferredBaseUrl;
  }

  static async resolveFnTargets(fnId: string, routePreference?: string,
    p2pEnabled?: boolean): Promise<FnConnectResolveResult> {
    const normalizedFnId = FnConnectApi.extractFnId(fnId);
    if (normalizedFnId.length === 0) {
      throw new Error("FNID 不能为空");
    }
    const preference = FnConnectApi.normalizeRoutePreference(routePreference);
    const allowP2p = p2pEnabled === undefined ? FnConnectPreferenceStore.p2pEnabled() : p2pEnabled;
    const cacheKey = FnConnectApi.resolveCacheKey(normalizedFnId, preference, allowP2p);
    const cached = FnConnectApi.resolveCache.get(cacheKey);
    if (cached !== undefined && cached.expiresAt > Date.now()) {
      return FnConnectApi.cloneResolveResult(cached.result);
    }
    const pending = FnConnectApi.pendingResolve.get(cacheKey);
    if (pending !== undefined) {
      return FnConnectApi.cloneResolveResult(await pending);
    }
    const request = FnConnectApi.resolveFnTargetsRemote(normalizedFnId, preference, allowP2p);
    FnConnectApi.pendingResolve.set(cacheKey, request);
    try {
      const result = await request;
      FnConnectApi.saveResolveCache(cacheKey, result);
      return FnConnectApi.cloneResolveResult(result);
    } finally {
      FnConnectApi.pendingResolve.delete(cacheKey);
    }
  }

  static clearResolveCache(): void {
    FnConnectApi.resolveCache.clear();
    FnConnectApi.pendingResolve.clear();
  }

  private static async resolveFnTargetsRemote(normalizedFnId: string,
    preference: FnConnectRoutePreference, p2pEnabled: boolean): Promise<FnConnectResolveResult> {
    const payload: FnConnectResolveRequest = {
      fnId: normalizedFnId
    };
    const timestamp = `${Date.now()}`;
    const client = http.createHttp();
    try {
      const response = await client.request(FnConnectApi.API_URL, {
        method: http.RequestMethod.POST,
        expectDataType: http.HttpDataType.STRING,
        readTimeout: 15000,
        connectTimeout: 10000,
        header: {
          "Content-Type": "application/json",
          "Accept": "application/json",
          "fn-sign": FnConnectApi.fnSign(normalizedFnId, timestamp),
          "authx": FnConnectApi.authx("/api/v1/fn/con", payload)
        },
        extraData: JSON.stringify(payload)
      });
      const responseText = FnConnectApi.responseResultToText(response.result as string | Object | ArrayBuffer);
      let envelope: FnConnectResolveEnvelope;
      try {
        envelope = JSON.parse(responseText) as FnConnectResolveEnvelope;
      } catch (_) {
        throw new Error(`FNID 解析响应不是 JSON code=${response.responseCode} body=${FnConnectApi.truncate(responseText, 120)}`);
      }
      if (envelope.code !== 0 || !envelope.data) {
        throw new Error((envelope.msg || "").trim().length > 0 ? `${envelope.msg}` : "FNID 解析失败");
      }
      return await FnConnectApi.pickBestTargets(normalizedFnId, envelope.data, preference, p2pEnabled);
    } catch (error) {
      const message = (error as Error).message || "网络连接异常";
      hilog.warn(0x0000, "fnos-fnconnect", "resolve failed fnid=%{public}s err=%{public}s", normalizedFnId, message);
      const fallback = FnConnectApi.buildRelayFallbackTargets(normalizedFnId);
      if (fallback.preferredBaseUrl.length > 0) {
        return fallback;
      }
      throw new Error(`FNID 解析失败: ${message}`);
    } finally {
      client.destroy();
    }
  }

  private static saveResolveCache(cacheKey: string, result: FnConnectResolveResult): void {
    const cacheItem = new FnConnectResolveCacheItem();
    cacheItem.result = FnConnectApi.cloneResolveResult(result);
    cacheItem.expiresAt = Date.now() + FnConnectApi.RESOLVE_CACHE_MS;
    FnConnectApi.resolveCache.set(cacheKey, cacheItem);
  }

  private static cloneResolveResult(source: FnConnectResolveResult): FnConnectResolveResult {
    const result = new FnConnectResolveResult();
    result.fnId = source.fnId;
    result.preferredBaseUrl = source.preferredBaseUrl;
    result.fallbackBaseUrls = source.fallbackBaseUrls.slice();
    result.usedLan = source.usedLan;
    result.preferredRoute = source.preferredRoute;
    return result;
  }

  private static buildRelayFallbackTargets(fnId: string): FnConnectResolveResult {
    const result = new FnConnectResolveResult();
    result.fnId = fnId;
    result.usedLan = false;
    result.preferredRoute = "relay";
    result.preferredBaseUrl = `https://${fnId}.5ddd.com`;
    FnConnectApi.appendFallback(result.fallbackBaseUrls, `https://${fnId}.fnos.net`);
    return result;
  }

  private static async pickBestTargets(fnId: string, data: FnConnectResolveData,
    preference: FnConnectRoutePreference, p2pEnabled: boolean): Promise<FnConnectResolveResult> {
    const httpPort = data.port?.httpPort || 5666;
    const httpsPort = data.port?.httpsPort || 5667;
    const result = new FnConnectResolveResult();
    result.fnId = fnId;

    const candidates = FnConnectApi.applyP2pPreference(
      FnConnectApi.buildTargetCandidates(data, httpPort, httpsPort),
      p2pEnabled
    );
    const probes = await FnConnectApi.probeCandidates(candidates);
    const preferred = FnConnectApi.pickPreferredProbe(probes, candidates, preference);
    if (preferred.baseUrl.length > 0) {
      result.preferredBaseUrl = preferred.baseUrl;
      result.preferredRoute = preferred.route;
      result.usedLan = preferred.route === "lan";
      FnConnectApi.sortedFallbackProbes(probes).forEach((probe: FnConnectProbeResult) => {
        FnConnectApi.appendFallback(result.fallbackBaseUrls, probe.baseUrl);
      });
      FnConnectApi.sortedFallbackCandidates(candidates).forEach((candidate: FnConnectTargetCandidate) => {
        FnConnectApi.appendFallback(result.fallbackBaseUrls, candidate.baseUrl);
      });
      const preferredIndex = result.fallbackBaseUrls.indexOf(result.preferredBaseUrl);
      if (preferredIndex >= 0) {
        result.fallbackBaseUrls.splice(preferredIndex, 1);
      }
      hilog.info(0x0000, "fnos-fnconnect",
        "resolved fnid=%{public}s preferred=%{public}s route=%{public}s probes=%{public}s",
        fnId, result.preferredBaseUrl, result.preferredRoute, JSON.stringify(probes));
      return result;
    }

    throw new Error("FNID 没有解析到可用设备地址");
  }

  private static buildTargetCandidates(data: FnConnectResolveData, httpPort: number, httpsPort: number): FnConnectTargetCandidate[] {
    const candidates: FnConnectTargetCandidate[] = [];
    const lanIpv4List = data.ipv4 || [];
    lanIpv4List.forEach((lanIpv4: string) => {
      const normalized = lanIpv4.trim();
      FnConnectApi.appendCandidate(candidates, normalized.length > 0 ? `http://${normalized}:${httpPort}` : "", "lan");
    });
    const publicIpv4List = data.publicIpv4 || [];
    publicIpv4List.forEach((publicIpv4: string) => {
      const normalized = publicIpv4.trim();
      FnConnectApi.appendCandidate(candidates, normalized.length > 0 ? `https://${normalized}:${httpsPort}` : "", "public");
    });
    const publicIpv6List = data.publicIpv6 || [];
    publicIpv6List.forEach((publicIpv6: string) => {
      const normalized = publicIpv6.trim();
      FnConnectApi.appendCandidate(candidates, normalized.length > 0 ? `https://[${normalized}]:${httpsPort}` : "", "public");
    });
    FnConnectApi.appendCandidate(candidates, FnConnectApi.pickRelayBaseUrl(data, httpsPort), "relay");
    return candidates;
  }

  private static appendCandidate(candidates: FnConnectTargetCandidate[], baseUrl: string, route: string): void {
    const normalized = baseUrl.trim();
    if (normalized.length === 0) {
      return;
    }
    if (candidates.find((item: FnConnectTargetCandidate) => item.baseUrl === normalized) !== undefined) {
      return;
    }
    const candidate = new FnConnectTargetCandidate();
    candidate.baseUrl = normalized;
    candidate.route = route;
    candidates.push(candidate);
  }

  private static async probeCandidates(candidates: FnConnectTargetCandidate[]): Promise<FnConnectProbeResult[]> {
    const requests: Promise<FnConnectProbeResult>[] = [];
    candidates.forEach((candidate: FnConnectTargetCandidate) => {
      requests.push(FnConnectApi.probeCandidate(candidate));
    });
    return Promise.all(requests);
  }

  private static pickPreferredProbe(probes: FnConnectProbeResult[], candidates: FnConnectTargetCandidate[],
    preference: FnConnectRoutePreference): FnConnectProbeResult {
    const reachable = probes.filter((probe: FnConnectProbeResult) => probe.reachable);
    FnConnectApi.sortProbesByPreference(reachable, preference);
    if (reachable.length > 0) {
      return reachable[0];
    }
    const fallback = new FnConnectProbeResult();
    const first = FnConnectApi.firstCandidateByPreference(candidates, preference);
    if (first !== undefined) {
      fallback.baseUrl = first.baseUrl;
      fallback.route = first.route;
    }
    return fallback;
  }

  private static applyP2pPreference(candidates: FnConnectTargetCandidate[],
    p2pEnabled: boolean): FnConnectTargetCandidate[] {
    if (p2pEnabled) {
      return candidates;
    }
    const filtered = candidates.filter((item: FnConnectTargetCandidate) => item.route !== "public");
    return filtered.length > 0 ? filtered : candidates;
  }

  private static sortProbesByPreference(probes: FnConnectProbeResult[],
    preference: FnConnectRoutePreference): void {
    probes.sort((left: FnConnectProbeResult, right: FnConnectProbeResult) => {
      if (preference === FnConnectRoutePreference.SPEED) {
        return left.elapsedMs - right.elapsedMs;
      }
      const routeDiff = FnConnectApi.routePriority(left.route, preference) - FnConnectApi.routePriority(right.route, preference);
      if (routeDiff !== 0) {
        return routeDiff;
      }
      return left.elapsedMs - right.elapsedMs;
    });
  }

  private static firstCandidateByPreference(candidates: FnConnectTargetCandidate[],
    preference: FnConnectRoutePreference): FnConnectTargetCandidate | undefined {
    if (candidates.length === 0) {
      return undefined;
    }
    const sorted = candidates.slice();
    sorted.sort((left: FnConnectTargetCandidate, right: FnConnectTargetCandidate) => {
      return FnConnectApi.unreachableRoutePriority(left.route, preference) -
      FnConnectApi.unreachableRoutePriority(right.route, preference);
    });
    return sorted[0];
  }

  private static sortedFallbackProbes(probes: FnConnectProbeResult[]): FnConnectProbeResult[] {
    const sorted = probes.slice();
    sorted.sort((left: FnConnectProbeResult, right: FnConnectProbeResult) => {
      if (left.reachable !== right.reachable) {
        return left.reachable ? -1 : 1;
      }
      if (left.reachable) {
        if (left.elapsedMs !== right.elapsedMs) {
          return left.elapsedMs - right.elapsedMs;
        }
        return FnConnectApi.remoteRoutePriority(left.route) - FnConnectApi.remoteRoutePriority(right.route);
      }
      return FnConnectApi.remoteRoutePriority(left.route) - FnConnectApi.remoteRoutePriority(right.route);
    });
    return sorted;
  }

  private static sortedFallbackCandidates(candidates: FnConnectTargetCandidate[]): FnConnectTargetCandidate[] {
    const sorted = candidates.slice();
    sorted.sort((left: FnConnectTargetCandidate, right: FnConnectTargetCandidate) => {
      return FnConnectApi.remoteRoutePriority(left.route) - FnConnectApi.remoteRoutePriority(right.route);
    });
    return sorted;
  }

  private static routePriority(route: string, preference: FnConnectRoutePreference): number {
    if (preference === FnConnectRoutePreference.PUBLIC) {
      return route === "public" ? 0 : (route === "lan" ? 1 : 2);
    }
    if (preference === FnConnectRoutePreference.RELAY) {
      return route === "relay" ? 0 : (route === "public" ? 1 : 2);
    }
    return route === "lan" ? 0 : (route === "public" ? 1 : 2);
  }

  private static unreachableRoutePriority(route: string, preference: FnConnectRoutePreference): number {
    if (preference === FnConnectRoutePreference.PUBLIC) {
      return route === "public" ? 0 : (route === "relay" ? 1 : 2);
    }
    return FnConnectApi.remoteRoutePriority(route);
  }

  private static remoteRoutePriority(route: string): number {
    return route === "relay" ? 0 : (route === "public" ? 1 : 2);
  }

  private static normalizeRoutePreference(value?: string): FnConnectRoutePreference {
    if (value === FnConnectRoutePreference.PUBLIC) {
      return FnConnectRoutePreference.PUBLIC;
    }
    if (value === FnConnectRoutePreference.RELAY) {
      return FnConnectRoutePreference.RELAY;
    }
    if (value === FnConnectRoutePreference.SPEED) {
      return FnConnectRoutePreference.SPEED;
    }
    return FnConnectPreferenceStore.routePreference();
  }

  private static resolveCacheKey(fnId: string, preference: FnConnectRoutePreference,
    p2pEnabled: boolean): string {
    return `${fnId}|${preference}|${p2pEnabled ? 'p2p' : 'no-p2p'}`;
  }

  private static async probeCandidate(candidate: FnConnectTargetCandidate): Promise<FnConnectProbeResult> {
    const result = new FnConnectProbeResult();
    result.baseUrl = candidate.baseUrl;
    result.route = candidate.route;
    const start = Date.now();
    result.reachable = await FnConnectApi.canReachBaseUrl(candidate.baseUrl);
    result.elapsedMs = Date.now() - start;
    return result;
  }

  private static pickRelayBaseUrl(data: FnConnectResolveData, httpsPort: number): string {
    const relayHost = data.fn && data.fn.length > 0 ? data.fn[0].trim() : "";
    if (relayHost.length === 0) {
      return "";
    }
    if (relayHost.endsWith(":443")) {
      return `https://${relayHost.replace(/:443$/, "")}`;
    }
    return relayHost.indexOf(":") >= 0 ? `https://${relayHost}` : `https://${relayHost}:${httpsPort}`;
  }

  private static appendFallback(targets: string[], candidate: string): void {
    if (candidate.trim().length === 0) {
      return;
    }
    if (targets.indexOf(candidate) >= 0) {
      return;
    }
    targets.push(candidate);
  }

  private static responseResultToText(value: string | Object | ArrayBuffer): string {
    if (typeof value === "string") {
      return value;
    }
    if (value instanceof ArrayBuffer) {
      return buffer.from(value).toString("utf-8");
    }
    return JSON.stringify(value);
  }

  private static truncate(value: string, maxLength: number): string {
    const normalized = `${value || ""}`;
    if (normalized.length <= maxLength) {
      return normalized;
    }
    return `${normalized.substring(0, maxLength)}...`;
  }

  private static async canReachBaseUrl(baseUrl: string): Promise<boolean> {
    const client = http.createHttp();
    try {
      const response = await client.request(`${baseUrl}/license/v1/device/baseInfo`, {
        method: http.RequestMethod.GET,
        expectDataType: http.HttpDataType.STRING,
        readTimeout: 1800,
        connectTimeout: 1200,
        header: {
          "Accept": "application/json,text/plain,*/*"
        }
      });
      return response.responseCode >= 200 && response.responseCode < 500;
    } catch (_) {
      return false;
    } finally {
      client.destroy();
    }
  }

  private static extractFnId(rawValue: string): string {
    const trimmed = rawValue.trim()
      .replace(/^https?:\/\//, "")
      .replace(/^wss?:\/\//, "")
      .replace(/\/$/, "");
    if (trimmed.startsWith("5ddd.com/")) {
      return trimmed.substring("5ddd.com/".length).split("/")[0];
    }
    if (trimmed.endsWith(".fnos.net")) {
      return trimmed.substring(0, trimmed.indexOf(".fnos.net"));
    }
    return trimmed.split("/")[0];
  }

  private static fnSign(fnId: string, timestamp: string): string {
    return CryptoJS.SHA256(`trim_connect\`${fnId}\`${timestamp}\`anna`).toString(CryptoJS.enc.Hex);
  }

  private static authx(path: string, body: Object): string {
    const nonce = `${Math.floor(Math.random() * 900000) + 100000}`;
    const timestamp = `${Date.now()}`;
    const bodyText = JSON.stringify(body);
    const bodyMd5 = CryptoJS.MD5(bodyText).toString();
    const signText = [
      FnConnectApi.AUTHX_PREFIX,
      path,
      nonce,
      timestamp,
      bodyMd5,
      FnConnectApi.AUTHX_API_KEY
    ].join("_");
    const sign = CryptoJS.MD5(signText).toString();
    return `nonce=${nonce}&timestamp=${timestamp}&sign=${sign}`;
  }
}
