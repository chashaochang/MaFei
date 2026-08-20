import { HMRouterMgr, HMRouterPathInfo } from "@hadss/hmrouter";
import { PreferencesUtil } from "./PreferencesUtil";
import { hilog } from "@kit.PerformanceAnalysisKit";
import { SessionStore } from "./SessionStore";

export class FnMediaAuthPreferenceKeys {
  static readonly baseUrl: string = "fnmedia-base-url";
  static readonly oauthAppId: string = "fnmedia-oauth-app-id";
  static readonly token: string = "fnmedia-token";
  static readonly cookie: string = "fnmedia-cookie";
  static readonly authHost: string = "fnmedia-auth-host";
  static readonly scopedSeparator: string = "::";
}

export class FnMediaAuthSnapshot {
  baseUrl: string = "";
  oauthAppId: string = "";
  token: string = "";
  cookie: string = "";
}

export class FnMediaAuthEntryOptions {
  onResult?: () => void;
}

export class FnMediaAuthStore {
  private static readonly VIDEO_ENTRY_MAX_AGE_MS: number = 30 * 1000;

  static getSnapshot(): FnMediaAuthSnapshot {
    const snapshot = new FnMediaAuthSnapshot();
    snapshot.baseUrl = FnMediaAuthStore.getConfigScopedString(FnMediaAuthPreferenceKeys.baseUrl);
    snapshot.oauthAppId = FnMediaAuthStore.getConfigScopedString(FnMediaAuthPreferenceKeys.oauthAppId);
    snapshot.cookie = FnMediaAuthStore.getSessionScopedString(FnMediaAuthPreferenceKeys.cookie);
    const storedToken = FnMediaAuthStore.getSessionScopedString(FnMediaAuthPreferenceKeys.token).trim();
    if (storedToken.length > 0) {
      snapshot.token = storedToken;
      return snapshot;
    }
    const cookieToken = FnMediaAuthStore.extractTokenFromCookie(snapshot.cookie);
    snapshot.token = cookieToken;
    if (cookieToken.length > 0) {
      FnMediaAuthStore.putSessionScopedString(FnMediaAuthPreferenceKeys.token, cookieToken);
    }
    return snapshot;
  }

  static saveOauthConfig(appId: string, baseUrl: string): void {
    FnMediaAuthStore.putConfigScopedString(FnMediaAuthPreferenceKeys.oauthAppId, appId.trim());
    FnMediaAuthStore.putConfigScopedString(FnMediaAuthPreferenceKeys.baseUrl, baseUrl.trim());
  }

  static saveBaseUrl(baseUrl: string): void {
    FnMediaAuthStore.putConfigScopedString(FnMediaAuthPreferenceKeys.baseUrl, baseUrl.trim());
  }

  static saveToken(token: string): void {
    const normalizedToken = token.trim();
    FnMediaAuthStore.putSessionScopedString(FnMediaAuthPreferenceKeys.token, normalizedToken);
    if (normalizedToken.length > 0) {
      const mergedCookie = FnMediaAuthStore.mergeCookieToken(
        FnMediaAuthStore.getSessionScopedString(FnMediaAuthPreferenceKeys.cookie),
        normalizedToken
      );
      FnMediaAuthStore.putSessionScopedString(FnMediaAuthPreferenceKeys.cookie, mergedCookie);
    }
    FnMediaAuthStore.touchRevision();
  }

  static saveCookie(cookie: string): void {
    const normalized = cookie.trim();
    const merged = FnMediaAuthStore.mergeCookieText(
      FnMediaAuthStore.getSessionScopedString(FnMediaAuthPreferenceKeys.cookie),
      normalized
    );
    FnMediaAuthStore.putSessionScopedString(FnMediaAuthPreferenceKeys.cookie, merged);
    FnMediaAuthStore.touchRevision();
    const token = FnMediaAuthStore.extractTokenFromCookie(merged);
    if (token.length > 0) {
      FnMediaAuthStore.saveToken(token);
    }
  }

  static saveWebCookie(cookie: string): void {
    const merged = FnMediaAuthStore.mergeCookieText(
      FnMediaAuthStore.getSessionScopedString(FnMediaAuthPreferenceKeys.cookie),
      cookie.trim()
    );
    FnMediaAuthStore.putSessionScopedString(FnMediaAuthPreferenceKeys.cookie, merged);
    FnMediaAuthStore.touchRevision();
    const token = FnMediaAuthStore.extractTokenFromCookie(merged);
    if (token.length > 0) {
      FnMediaAuthStore.saveToken(token);
    }
  }

  static hasToken(): boolean {
    return FnMediaAuthStore.getSnapshot().token.trim().length > 0;
  }

  static hasAuthorizedSession(): boolean {
    const snapshot = FnMediaAuthStore.getSnapshot();
    return snapshot.token.trim().length > 0
      && snapshot.baseUrl.trim().length > 0
      && snapshot.oauthAppId.trim().length > 0;
  }

  static grantVideoEntry(): void {
    AppStorage.setOrCreate('fnMediaVideoEntryScope', FnMediaAuthStore.videoEntryScopeKey());
    AppStorage.setOrCreate('fnMediaVideoEntryAt', Date.now());
  }

  static revokeVideoEntry(): void {
    AppStorage.setOrCreate('fnMediaVideoEntryScope', "");
    AppStorage.setOrCreate('fnMediaVideoEntryAt', 0);
  }

  static canEnterVideo(): boolean {
    if (!FnMediaAuthStore.hasAuthorizedSession()) {
      return false;
    }
    const entryScope = AppStorage.get<string>('fnMediaVideoEntryScope') || "";
    if (!FnMediaAuthStore.isCurrentSessionScope(entryScope)) {
      return false;
    }
    const entryAt = AppStorage.get<number>('fnMediaVideoEntryAt') || 0;
    if (entryAt <= 0) {
      return false;
    }
    return Date.now() - entryAt <= FnMediaAuthStore.VIDEO_ENTRY_MAX_AGE_MS;
  }

  static consumeVideoEntry(): void {
    FnMediaAuthStore.revokeVideoEntry();
  }

  static entryPageUrl(): string {
    return "/VideoAuth";
  }

  static videoPageUrl(): string {
    return "/Video";
  }

  static openVideoEntry(options?: FnMediaAuthEntryOptions): void {
    if (!FnMediaAuthStore.hasAuthorizedSession()) {
      FnMediaAuthStore.openAuthEntry(options);
      return;
    }
    const pathInfo = FnMediaAuthStore.createVideoEntryPathInfo();
    if (options !== undefined) {
      HMRouterMgr.push(pathInfo, options);
      return;
    }
    HMRouterMgr.push(pathInfo);
  }

  static replaceVideoEntry(options?: FnMediaAuthEntryOptions): void {
    if (!FnMediaAuthStore.hasAuthorizedSession()) {
      FnMediaAuthStore.replaceAuthEntry(options);
      return;
    }
    const pathInfo = FnMediaAuthStore.createVideoEntryPathInfo();
    if (options !== undefined) {
      HMRouterMgr.replace(pathInfo, options);
      return;
    }
    HMRouterMgr.replace(pathInfo);
  }

  static openAuthEntry(options?: FnMediaAuthEntryOptions): void {
    const pathInfo = FnMediaAuthStore.createAuthEntryPathInfo();
    if (options !== undefined) {
      HMRouterMgr.push(pathInfo, options);
      return;
    }
    HMRouterMgr.push(pathInfo);
  }

  static replaceAuthEntry(options?: FnMediaAuthEntryOptions): void {
    const pathInfo = FnMediaAuthStore.createAuthEntryPathInfo();
    if (options !== undefined) {
      HMRouterMgr.replace(pathInfo, options);
      return;
    }
    HMRouterMgr.replace(pathInfo);
  }

  private static createAuthEntryPathInfo(): HMRouterPathInfo {
    FnMediaAuthStore.revokeVideoEntry();
    const entryNonce = Date.now();
    AppStorage.setOrCreate<number>('fnMediaAuthEntryNonce', entryNonce);
    const routeUrl = FnMediaAuthStore.entryPageUrl();
    hilog.info(0x0000, "fnos-video-route",
      "open media auth entry page=%{public}s nonce=%{public}d host=%{public}s auth=%{public}s",
      routeUrl,
      entryNonce,
      SessionStore.currentHost(),
      FnMediaAuthStore.hasAuthorizedSession() ? "yes" : "no");
    return {
      pageUrl: routeUrl,
      param: { entryNonce: entryNonce }
    };
  }

  private static createVideoEntryPathInfo(): HMRouterPathInfo {
    FnMediaAuthStore.grantVideoEntry();
    const entryNonce = Date.now();
    AppStorage.setOrCreate<number>('fnMediaVideoEntryNonce', entryNonce);
    const routeUrl = FnMediaAuthStore.videoPageUrl();
    hilog.info(0x0000, "fnos-video-route",
      "open media video entry page=%{public}s nonce=%{public}d host=%{public}s auth=%{public}s",
      routeUrl,
      entryNonce,
      SessionStore.currentHost(),
      FnMediaAuthStore.hasAuthorizedSession() ? "yes" : "no");
    return {
      pageUrl: routeUrl,
      param: { entryNonce: entryNonce }
    };
  }

  static clearSession(): void {
    FnMediaAuthStore.putSessionScopedString(FnMediaAuthPreferenceKeys.token, "");
    FnMediaAuthStore.putSessionScopedString(FnMediaAuthPreferenceKeys.cookie, "");
    FnMediaAuthStore.revokeVideoEntry();
    FnMediaAuthStore.touchRevision();
  }

  static clear(): void {
    FnMediaAuthStore.putConfigScopedString(FnMediaAuthPreferenceKeys.baseUrl, "");
    FnMediaAuthStore.putConfigScopedString(FnMediaAuthPreferenceKeys.oauthAppId, "");
    FnMediaAuthStore.clearSession();
  }

  private static touchRevision(): void {
    AppStorage.setOrCreate('fnMediaAuthRevision', `${Date.now()}`);
  }

  static extractTokenFromCookie(cookie: string): string {
    const normalized = cookie.trim();
    if (normalized.length === 0) {
      return "";
    }
    const parts = normalized.split(";");
    for (let index = 0; index < parts.length; index += 1) {
      const segment = parts[index].trim();
      if (segment.length === 0) {
        continue;
      }
      const equalIndex = segment.indexOf("=");
      if (equalIndex <= 0) {
        continue;
      }
      const key = segment.substring(0, equalIndex).trim();
      if (key !== "Trim-MC-token") {
        continue;
      }
      return segment.substring(equalIndex + 1).trim();
    }
    return "";
  }

  private static mergeCookieToken(cookieText: string, token: string): string {
    return FnMediaAuthStore.mergeCookieText(cookieText, `Trim-MC-token=${token.trim()}`);
  }

  private static mergeCookieText(baseCookieText: string, nextCookieText: string): string {
    const cookieMap: Map<string, string> = new Map<string, string>();
    FnMediaAuthStore.collectCookiePairs(cookieMap, baseCookieText);
    FnMediaAuthStore.collectCookiePairs(cookieMap, nextCookieText);
    const pairs: string[] = [];
    cookieMap.forEach((value: string, key: string) => {
      pairs.push(`${key}=${value}`);
    });
    return pairs.join("; ");
  }

  private static collectCookiePairs(cookieMap: Map<string, string>, cookieText: string): void {
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
  }

  private static getConfigScopedString(key: string): string {
    const scopes = FnMediaAuthStore.candidateConfigScopeKeys();
    for (let index = 0; index < scopes.length; index += 1) {
      const scopedValue = PreferencesUtil.getStringSync(FnMediaAuthStore.scopedKeyFor(key, scopes[index]));
      if (scopedValue.trim().length > 0) {
        return scopedValue;
      }
    }
    return "";
  }

  private static getSessionScopedString(key: string): string {
    const scopes = FnMediaAuthStore.candidateSessionScopeKeys();
    for (let index = 0; index < scopes.length; index += 1) {
      const scopedValue = PreferencesUtil.getStringSync(FnMediaAuthStore.scopedKeyFor(key, scopes[index]));
      if (scopedValue.trim().length > 0) {
        return scopedValue;
      }
    }
    if (scopes.length > 0) {
      return "";
    }
    return PreferencesUtil.getStringSync(key);
  }

  private static putConfigScopedString(key: string, value: string): void {
    const normalized = value.trim();
    const scopeKey = FnMediaAuthStore.currentConfigScopeKey();
    const scopes = FnMediaAuthStore.candidateConfigScopeKeys();
    for (let index = 0; index < scopes.length; index += 1) {
      PreferencesUtil.putSync(FnMediaAuthStore.scopedKeyFor(key, scopes[index]), normalized);
    }
    if (scopeKey.length > 0 && normalized.length > 0) {
      PreferencesUtil.putSync(FnMediaAuthPreferenceKeys.authHost, scopeKey);
      return;
    }
    if (scopes.length === 0 || normalized.length === 0) {
      PreferencesUtil.putSync(key, normalized);
    }
  }

  private static putSessionScopedString(key: string, value: string): void {
    const normalized = value.trim();
    const scopes = FnMediaAuthStore.candidateSessionScopeKeys();
    for (let index = 0; index < scopes.length; index += 1) {
      PreferencesUtil.putSync(FnMediaAuthStore.scopedKeyFor(key, scopes[index]), normalized);
    }
    if (scopes.length > 0) {
      PreferencesUtil.putSync(FnMediaAuthPreferenceKeys.authHost, scopes[0]);
      PreferencesUtil.putSync(key, "");
      return;
    }
    PreferencesUtil.putSync(key, normalized);
  }

  private static currentConfigScopeKey(): string {
    return SessionStore.currentHostKey().trim();
  }

  private static currentSessionScopeKey(): string {
    const hostKey = SessionStore.currentHostKey().trim();
    const username = SessionStore.currentUsername().trim();
    const machineId = SessionStore.currentMachineId().trim();
    const backId = SessionStore.currentBackId().trim();
    if (hostKey.length === 0 || username.length === 0) {
      return "";
    }
    if (machineId.length > 0 && backId.length > 0) {
      return `${hostKey}::${username}::${machineId}::${backId}`;
    }
    if (machineId.length > 0) {
      return `${hostKey}::${username}::${machineId}`;
    }
    return `${hostKey}::${username}`;
  }

  private static stableAccountScopeKey(): string {
    const hostKey = SessionStore.currentHostKey().trim();
    const username = SessionStore.currentUsername().trim();
    if (hostKey.length === 0 || username.length === 0) {
      return "";
    }
    return `${hostKey}::${username}`;
  }

  private static candidateSessionScopeKeys(): string[] {
    const scopes: string[] = [];
    FnMediaAuthStore.pushScope(scopes, FnMediaAuthStore.currentSessionScopeKey());
    FnMediaAuthStore.pushScope(scopes, FnMediaAuthStore.stableAccountScopeKey());
    return scopes;
  }

  private static videoEntryScopeKey(): string {
    const stableScope = FnMediaAuthStore.stableAccountScopeKey();
    if (stableScope.length > 0) {
      return stableScope;
    }
    return FnMediaAuthStore.currentSessionScopeKey();
  }

  private static isCurrentSessionScope(scope: string): boolean {
    const normalized = scope.trim();
    if (normalized.length === 0) {
      return false;
    }
    const scopes = FnMediaAuthStore.candidateSessionScopeKeys();
    for (let index = 0; index < scopes.length; index += 1) {
      if (scopes[index] === normalized) {
        return true;
      }
    }
    return false;
  }

  private static candidateConfigScopeKeys(): string[] {
    const scopes: string[] = [];
    FnMediaAuthStore.pushScope(scopes, FnMediaAuthStore.currentSessionScopeKey());
    FnMediaAuthStore.pushScope(scopes, FnMediaAuthStore.currentConfigScopeKey());
    FnMediaAuthStore.pushScope(scopes, SessionStore.currentHostKey().trim());
    return scopes;
  }

  private static pushScope(scopes: string[], scope: string): void {
    const normalized = scope.trim();
    if (normalized.length === 0 || scopes.indexOf(normalized) >= 0) {
      return;
    }
    scopes.push(normalized);
  }

  private static scopedKeyFor(key: string, scopeKey: string): string {
    return `${key}${FnMediaAuthPreferenceKeys.scopedSeparator}${scopeKey}`;
  }
}
