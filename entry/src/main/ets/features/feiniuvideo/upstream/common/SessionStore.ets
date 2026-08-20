import { PreferencesUtil } from "./PreferencesUtil";
import { FnosHostResolver } from "./FnosHostResolver";

export class PreferenceKeys {
  static readonly host: string = "fnos-host";
  static readonly hostInput: string = "fnos-host-input";
  static readonly username: string = "fnos-username";
  static readonly uid: string = "fnos-uid";
  static readonly password: string = "fnos-password";
  static readonly savePassword: string = "fnos-save-password";
  static readonly httpsAccess: string = "fnos-https-access";
  static readonly token: string = "token";
  static readonly longToken: string = "longToken";
  static readonly entryToken: string = "entry-token";
  static readonly secret: string = "fnos-Secret";
  static readonly machineId: string = "fnos-machine-id";
  static readonly backId: string = "fnos-back-id";
  static readonly clientDeviceId: string = "fnos-client-device-id";
  static readonly deviceName: string = "fnos-device-name";
  static readonly deviceNamePrefix: string = "fnos-device-name::";
  static readonly deviceNameSession: string = "fnos-device-name-session";
  static readonly connectionRoute: string = "fnos-connection-route";
}

export interface SessionSnapshot {
  host: string;
  hostInput: string;
  username: string;
  uid: number;
  password: string;
  savePassword: boolean;
  httpsAccess: boolean;
  token: string;
  longToken: string;
  entryToken: string;
  secret: string;
  machineId: string;
  backId: string;
  deviceName: string;
  connectionRoute: string;
}

export class SessionStore {
  private static runtimePassword: string = "";

  static getSnapshot(): SessionSnapshot {
    const persistedPassword = PreferencesUtil.getStringSync(PreferenceKeys.password);
    const savePassword = PreferencesUtil.getBooleanSync(PreferenceKeys.savePassword, true);
    return {
      host: PreferencesUtil.getStringSync(PreferenceKeys.host),
      hostInput: PreferencesUtil.getStringSync(PreferenceKeys.hostInput),
      username: PreferencesUtil.getStringSync(PreferenceKeys.username),
      uid: SessionStore.currentUid(),
      password: savePassword ? (persistedPassword.length > 0 ? persistedPassword : SessionStore.runtimePassword) : "",
      savePassword: savePassword,
      httpsAccess: PreferencesUtil.getBooleanSync(PreferenceKeys.httpsAccess, false),
      token: PreferencesUtil.getStringSync(PreferenceKeys.token),
      longToken: PreferencesUtil.getStringSync(PreferenceKeys.longToken),
      entryToken: PreferencesUtil.getStringSync(PreferenceKeys.entryToken),
      secret: PreferencesUtil.getStringSync(PreferenceKeys.secret),
      machineId: PreferencesUtil.getStringSync(PreferenceKeys.machineId),
      backId: PreferencesUtil.getStringSync(PreferenceKeys.backId),
      deviceName: SessionStore.currentDeviceName(),
      connectionRoute: SessionStore.currentConnectionRoute()
    };
  }

  static saveLoginForm(host: string, username: string, password: string, savePassword: boolean,
    httpsAccess: boolean = false, hostInput?: string): void {
    SessionStore.runtimePassword = savePassword ? password : "";
    const previousHostKey = SessionStore.currentDeviceNameHostKey().trim();
    const normalizedHost = host.trim();
    const normalizedInput = (hostInput !== undefined ? hostInput : host).trim();
    PreferencesUtil.putSync(PreferenceKeys.host, normalizedHost);
    PreferencesUtil.putSync(PreferenceKeys.hostInput, normalizedInput.length > 0 ? normalizedInput : normalizedHost);
    PreferencesUtil.putSync(PreferenceKeys.username, username.trim());
    PreferencesUtil.putSync(PreferenceKeys.savePassword, savePassword);
    PreferencesUtil.putSync(PreferenceKeys.httpsAccess, httpsAccess);
    PreferencesUtil.putSync(PreferenceKeys.password, savePassword ? password : "");
    SessionStore.saveConnectionRoute(SessionStore.inferConnectionRoute(normalizedHost, normalizedInput));
    const nextHostKey = SessionStore.currentDeviceNameHostKey().trim();
    if (nextHostKey.length > 0 && nextHostKey !== previousHostKey) {
      AppStorage.setOrCreate('fnHomeDeviceNameHost', SessionStore.deviceNameCacheKeyFor(nextHostKey, "", ""));
      AppStorage.setOrCreate('fnHomeDeviceNameSession', "");
      AppStorage.setOrCreate('fnHomeDeviceName', "");
    }
  }

  static updateResolvedHost(host: string, httpsAccess?: boolean, connectionRoute?: string): void {
    const normalizedHost = host.trim();
    if (normalizedHost.length === 0) {
      return;
    }
    PreferencesUtil.putSync(PreferenceKeys.host, normalizedHost);
    if (httpsAccess !== undefined) {
      PreferencesUtil.putSync(PreferenceKeys.httpsAccess, httpsAccess);
    }
    SessionStore.saveConnectionRoute(connectionRoute !== undefined ? connectionRoute :
      SessionStore.inferConnectionRoute(normalizedHost, SessionStore.currentHostInput()));
  }

  static saveConnectionRoute(route: string): void {
    const normalized = SessionStore.normalizeConnectionRoute(route);
    PreferencesUtil.putSync(PreferenceKeys.connectionRoute, normalized);
    AppStorage.setOrCreate("fnConnectionRoute", normalized);
  }

  static currentConnectionRoute(): string {
    const saved = PreferencesUtil.getStringSync(PreferenceKeys.connectionRoute).trim();
    if (saved.length > 0) {
      return SessionStore.normalizeConnectionRoute(saved);
    }
    return SessionStore.inferConnectionRoute(SessionStore.currentHost(), SessionStore.currentHostInput());
  }

  static inferConnectionRoute(host: string, hostInput: string): string {
    const normalizedHost = host.trim();
    const normalizedInput = hostInput.trim();
    if (!FnosHostResolver.isFnConnectLike(normalizedInput)) {
      return "lan";
    }
    const authority = normalizedHost.replace(/^[a-z]+:\/\//i, "").split("/")[0].toLowerCase();
    if (authority.indexOf(".5ddd.com") >= 0 || authority.indexOf(".fnos.net") >= 0) {
      return "relay";
    }
    if (authority.startsWith("192.168.") || authority.startsWith("10.") || /^172\.(1[6-9]|2\d|3[0-1])\./.test(authority)) {
      return "lan";
    }
    return "public";
  }

  private static normalizeConnectionRoute(route: string): string {
    const normalized = `${route || ""}`.trim().toLowerCase();
    if (normalized === "lan" || normalized === "public" || normalized === "relay") {
      return normalized;
    }
    return "lan";
  }

  static saveSession(token: string = "", longToken: string = "", machineId: string = "", backId: string = "", secret: string = ""): void {
    SessionStore.saveSessionInternal(token, longToken, machineId, backId, secret, true);
  }

  static saveFreshLoginSession(token: string = "", longToken: string = "", machineId: string = "", backId: string = "", secret: string = ""): void {
    SessionStore.saveSessionInternal(token, longToken, machineId, backId, secret, false);
  }

  static saveEntryToken(entryToken: string): void {
    PreferencesUtil.putSync(PreferenceKeys.entryToken, entryToken.trim());
  }

  private static saveSessionInternal(token: string = "", longToken: string = "", machineId: string = "", backId: string = "", secret: string = "",
    preserveMissing: boolean = true): void {
    const nextToken = SessionStore.pickSessionValue(token, preserveMissing ? PreferencesUtil.getStringSync(PreferenceKeys.token) : "");
    const nextLongToken = SessionStore.pickSessionValue(longToken, preserveMissing ? PreferencesUtil.getStringSync(PreferenceKeys.longToken) : "");
    const nextMachineId = SessionStore.pickSessionValue(machineId, preserveMissing ? PreferencesUtil.getStringSync(PreferenceKeys.machineId) : "");
    const nextBackId = SessionStore.pickSessionValue(backId, preserveMissing ? PreferencesUtil.getStringSync(PreferenceKeys.backId) : "");
    const nextSecret = SessionStore.pickSessionValue(secret, preserveMissing ? PreferencesUtil.getStringSync(PreferenceKeys.secret) : "");

    PreferencesUtil.putSync(PreferenceKeys.token, nextToken);
    PreferencesUtil.putSync(PreferenceKeys.longToken, nextLongToken);
    PreferencesUtil.putSync(PreferenceKeys.secret, nextSecret);
    PreferencesUtil.putSync(PreferenceKeys.machineId, nextMachineId);
    PreferencesUtil.putSync(PreferenceKeys.backId, nextBackId);
    const hostKey = SessionStore.currentDeviceNameHostKey().trim();
    if (hostKey.length > 0) {
      const nextDeviceNameCacheKey = SessionStore.deviceNameCacheKeyFor(hostKey, nextMachineId, nextBackId);
      const cachedHost = AppStorage.get<string>('fnHomeDeviceNameHost') || "";
      if (cachedHost.trim() !== nextDeviceNameCacheKey) {
        AppStorage.setOrCreate('fnHomeDeviceName', "");
        AppStorage.setOrCreate('fnHomeDeviceNameSession', "");
      }
      AppStorage.setOrCreate('fnHomeDeviceNameHost', nextDeviceNameCacheKey);
    }
    AppStorage.setOrCreate("isLogin", true);
  }

  static clearSession(clearPassword: boolean = false): void {
    PreferencesUtil.putSync(PreferenceKeys.token, "");
    PreferencesUtil.putSync(PreferenceKeys.longToken, "");
    PreferencesUtil.putSync(PreferenceKeys.entryToken, "");
    PreferencesUtil.putSync(PreferenceKeys.secret, "");
    PreferencesUtil.putSync(PreferenceKeys.machineId, "");
    PreferencesUtil.putSync(PreferenceKeys.backId, "");
    PreferencesUtil.putSync(PreferenceKeys.uid, "");
    SessionStore.clearRuntimeDeviceNameDisplay();
    if (clearPassword) {
      PreferencesUtil.putSync(PreferenceKeys.password, "");
      SessionStore.runtimePassword = "";
    }
    AppStorage.setOrCreate("isLogin", false);
  }

  static hasSession(): boolean {
    const snapshot = SessionStore.getSnapshot();
    return snapshot.host.trim().length > 0
      && (snapshot.token.trim().length > 0
        || (snapshot.longToken.trim().length > 0 && snapshot.secret.trim().length > 0));
  }

  static currentHost(): string {
    return PreferencesUtil.getStringSync(PreferenceKeys.host);
  }

  static currentHostInput(): string {
    const input = PreferencesUtil.getStringSync(PreferenceKeys.hostInput).trim();
    if (input.length > 0) {
      return input;
    }
    return PreferencesUtil.getStringSync(PreferenceKeys.host);
  }

  static currentHostKey(): string {
    const hostInput = SessionStore.currentHostInput().trim();
    if (FnosHostResolver.isFnConnectLike(hostInput)) {
      return hostInput;
    }
    const host = SessionStore.currentHost().trim();
    return host.length > 0 ? host : hostInput;
  }

  static currentUsername(): string {
    return PreferencesUtil.getStringSync(PreferenceKeys.username);
  }

  static currentUid(): number {
    const uidText = PreferencesUtil.getStringSync(PreferenceKeys.uid).trim();
    if (uidText.length === 0) {
      return 0;
    }
    const uid = Number.parseInt(uidText, 10);
    return Number.isNaN(uid) || uid <= 0 ? 0 : uid;
  }

  static saveUid(uid?: number): void {
    if (uid === undefined || uid <= 0) {
      return;
    }
    PreferencesUtil.putSync(PreferenceKeys.uid, `${uid}`);
  }

  static currentMachineId(): string {
    return PreferencesUtil.getStringSync(PreferenceKeys.machineId);
  }

  static saveDeviceName(deviceName: string, machineId: string = ""): void {
    const normalized = deviceName.trim();
    if (normalized.length === 0 || normalized === "飞牛设备") {
      return;
    }
    const hostKey = SessionStore.currentDeviceNameHostKey().trim();
    const normalizedMachineId = machineId.trim().length > 0 ? machineId.trim() : SessionStore.currentMachineId().trim();
    const normalizedBackId = SessionStore.currentBackId().trim();
    if (hostKey.length > 0) {
      if (normalizedMachineId.length === 0) {
        AppStorage.setOrCreate('fnHomeDeviceNameHost', SessionStore.deviceNameCacheKeyFor(hostKey, "", ""));
        AppStorage.setOrCreate('fnHomeDeviceNamePendingKey', "");
        AppStorage.setOrCreate('fnHomeDeviceNameSession', "");
        AppStorage.setOrCreate('fnHomeDeviceName', "");
        return;
      }
      PreferencesUtil.putSync(SessionStore.deviceNamePreferenceKeyFor(hostKey, normalizedMachineId, normalizedBackId), normalized);
      PreferencesUtil.putSync(PreferenceKeys.deviceNameSession, SessionStore.currentDeviceNameSessionSignature());
      AppStorage.setOrCreate('fnHomeDeviceNameHost', SessionStore.deviceNameCacheKeyFor(hostKey, normalizedMachineId, normalizedBackId));
      AppStorage.setOrCreate('fnHomeDeviceNamePendingKey', "");
      AppStorage.setOrCreate('fnHomeDeviceNameSession', SessionStore.currentDeviceNameSessionSignature());
      AppStorage.setOrCreate('fnHomeDeviceName', normalized);
      return;
    }
    PreferencesUtil.putSync(PreferenceKeys.deviceName, normalized);
    PreferencesUtil.putSync(PreferenceKeys.deviceNameSession, SessionStore.currentDeviceNameSessionSignature());
    AppStorage.setOrCreate('fnHomeDeviceNamePendingKey', "");
    AppStorage.setOrCreate('fnHomeDeviceNameSession', SessionStore.currentDeviceNameSessionSignature());
    AppStorage.setOrCreate('fnHomeDeviceName', normalized);
  }

  static clearCurrentDeviceNameCache(): void {
    const hostKey = SessionStore.currentDeviceNameHostKey().trim();
    const machineId = SessionStore.currentMachineId().trim();
    const backId = SessionStore.currentBackId().trim();
    AppStorage.setOrCreate('fnHomeDeviceNamePendingKey', "");
    AppStorage.setOrCreate('fnHomeDeviceNameSession', "");
    if (hostKey.length > 0) {
      PreferencesUtil.putSync(SessionStore.deviceNamePreferenceKeyFor(hostKey, machineId, backId), "");
      PreferencesUtil.putSync(PreferenceKeys.deviceNameSession, "");
      PreferencesUtil.putSync(SessionStore.legacyDeviceNamePreferenceKey(hostKey), "");
      const cachedHost = AppStorage.get<string>('fnHomeDeviceNameHost') || "";
      if (cachedHost.trim() === SessionStore.deviceNameCacheKeyFor(hostKey, machineId, backId)
        || cachedHost.trim() === SessionStore.deviceNameCacheKeyFor(hostKey, "", "")) {
        AppStorage.setOrCreate('fnHomeDeviceName', "");
      }
      return;
    }
    AppStorage.setOrCreate('fnHomeDeviceName', "");
  }

  static clearRuntimeDeviceNameDisplay(): void {
    const hostKey = SessionStore.currentDeviceNameHostKey().trim();
    AppStorage.setOrCreate('fnHomeDeviceNameHost', hostKey.length > 0 ? SessionStore.deviceNameCacheKeyFor(hostKey, "", "") : "");
    AppStorage.setOrCreate('fnHomeDeviceNameSession', "");
    AppStorage.setOrCreate('fnHomeDeviceName', "");
  }

  static beginDeviceNameResolution(): void {
    const cacheKey = SessionStore.currentDeviceNameCacheKey();
    AppStorage.setOrCreate('fnHomeDeviceNamePendingKey', cacheKey);
    AppStorage.setOrCreate('fnHomeDeviceNameHost', cacheKey);
    AppStorage.setOrCreate('fnHomeDeviceNameSession', "");
    AppStorage.setOrCreate('fnHomeDeviceName', "");
  }

  static currentDeviceName(): string {
    const hostKey = SessionStore.currentDeviceNameHostKey().trim();
    const machineId = SessionStore.currentMachineId().trim();
    const backId = SessionStore.currentBackId().trim();
    if (hostKey.length > 0 && machineId.length === 0) {
      return "";
    }
    const cacheKey = SessionStore.deviceNameCacheKeyFor(hostKey, machineId, backId);
    const pendingKey = AppStorage.get<string>('fnHomeDeviceNamePendingKey') || "";
    if (hostKey.length > 0 && pendingKey.trim() === cacheKey) {
      return "";
    }
    const sessionSignature = SessionStore.currentDeviceNameSessionSignature();
    const cached = AppStorage.get<string>('fnHomeDeviceName') || "";
    const cachedHost = AppStorage.get<string>('fnHomeDeviceNameHost') || "";
    const cachedSession = AppStorage.get<string>('fnHomeDeviceNameSession') || "";
    if (hostKey.length > 0
      && cachedHost.trim() === cacheKey
      && cachedSession.trim() === sessionSignature
      && cached.trim().length > 0
      && cached.trim() !== "飞牛设备") {
      return cached.trim();
    }
    if (hostKey.length > 0) {
      const persistedForHost = PreferencesUtil.getStringSync(SessionStore.deviceNamePreferenceKeyFor(hostKey, machineId, backId)).trim();
      const persistedSession = PreferencesUtil.getStringSync(PreferenceKeys.deviceNameSession).trim();
      if (persistedForHost.length > 0 && persistedForHost !== "飞牛设备" && persistedSession === sessionSignature) {
        AppStorage.setOrCreate('fnHomeDeviceNameHost', cacheKey);
        AppStorage.setOrCreate('fnHomeDeviceNameSession', sessionSignature);
        AppStorage.setOrCreate('fnHomeDeviceName', persistedForHost);
        return persistedForHost;
      }
      return "";
    }
    const persisted = PreferencesUtil.getStringSync(PreferenceKeys.deviceName).trim();
    const persistedSession = PreferencesUtil.getStringSync(PreferenceKeys.deviceNameSession).trim();
    if (persisted.length > 0 && persisted !== "飞牛设备" && persistedSession === sessionSignature) {
      AppStorage.setOrCreate('fnHomeDeviceNameSession', sessionSignature);
      AppStorage.setOrCreate('fnHomeDeviceName', persisted);
      return persisted;
    }
    return "";
  }

  static currentSecret(): string {
    return PreferencesUtil.getStringSync(PreferenceKeys.secret);
  }

  static currentBackId(): string {
    return PreferencesUtil.getStringSync(PreferenceKeys.backId);
  }

  static currentClientDeviceId(): string {
    const cached = PreferencesUtil.getStringSync(PreferenceKeys.clientDeviceId).trim();
    if (cached.length > 0) {
      return cached;
    }
    const next = `${Date.now().toString(36)}-${SessionStore.randomString(12)}-${SessionStore.randomString(12)}`;
    PreferencesUtil.putSync(PreferenceKeys.clientDeviceId, next);
    return next;
  }

  private static pickSessionValue(nextValue: string, fallback: string): string {
    const normalized = typeof nextValue === "string" ? nextValue.trim() : "";
    return normalized.length > 0 ? normalized : fallback;
  }

  static currentDeviceNameCacheKey(): string {
    const hostKey = SessionStore.currentDeviceNameHostKey().trim();
    if (hostKey.length === 0) {
      return "";
    }
    return SessionStore.deviceNameCacheKeyFor(hostKey, SessionStore.currentMachineId().trim(), SessionStore.currentBackId().trim());
  }

  static currentDeviceNameSessionSignature(): string {
    const hostKey = SessionStore.currentDeviceNameHostKey().trim();
    const username = SessionStore.currentUsername().trim();
    const machineId = SessionStore.currentMachineId().trim();
    const backId = SessionStore.currentBackId().trim();
    const authKey = SessionStore.currentDeviceNameAuthKey();
    if (hostKey.length === 0 || username.length === 0 || machineId.length === 0 || authKey === "no-auth") {
      return "";
    }
    if (backId.length === 0) {
      return `${hostKey}::${username}::${machineId}::${authKey}`;
    }
    return `${hostKey}::${username}::${machineId}::${backId}::${authKey}`;
  }

  private static currentDeviceNameHostKey(): string {
    const hostInput = SessionStore.currentHostInput().trim();
    const host = SessionStore.currentHost().trim();
    if (hostInput.length > 0 && host.length > 0 && hostInput !== host) {
      return `${hostInput}=>${host}`;
    }
    if (host.length > 0) {
      return host;
    }
    return hostInput;
  }

  private static deviceNameCacheKeyFor(hostKey: string, machineId: string, backId: string): string {
    const normalizedMachineId = machineId.trim();
    const normalizedBackId = backId.trim();
    const normalizedUsername = SessionStore.currentUsername().trim();
    const authKey = SessionStore.currentDeviceNameAuthKey();
    if (normalizedUsername.length > 0 && normalizedMachineId.length > 0 && normalizedBackId.length > 0) {
      return `${hostKey.trim()}::${normalizedUsername}::${normalizedMachineId}::${normalizedBackId}::${authKey}`;
    }
    if (normalizedUsername.length > 0 && normalizedMachineId.length > 0) {
      return `${hostKey.trim()}::${normalizedUsername}::${normalizedMachineId}::${authKey}`;
    }
    if (normalizedMachineId.length > 0) {
      return `${hostKey.trim()}::${normalizedMachineId}::${authKey}`;
    }
    return hostKey.trim();
  }

  private static deviceNamePreferenceKeyFor(hostKey: string, machineId: string, backId: string): string {
    const normalizedMachineId = machineId.trim();
    const normalizedBackId = backId.trim();
    const normalizedUsername = SessionStore.currentUsername().trim();
    const authKey = SessionStore.currentDeviceNameAuthKey();
    if (normalizedUsername.length > 0 && normalizedMachineId.length > 0 && normalizedBackId.length > 0) {
      return `${PreferenceKeys.deviceNamePrefix}${hostKey.trim()}::${normalizedUsername}::${normalizedMachineId}::${normalizedBackId}::${authKey}`;
    }
    if (normalizedUsername.length > 0 && normalizedMachineId.length > 0) {
      return `${PreferenceKeys.deviceNamePrefix}${hostKey.trim()}::${normalizedUsername}::${normalizedMachineId}::${authKey}`;
    }
    if (normalizedMachineId.length > 0) {
      return `${PreferenceKeys.deviceNamePrefix}${hostKey.trim()}::${normalizedMachineId}::${authKey}`;
    }
    return SessionStore.legacyDeviceNamePreferenceKey(hostKey);
  }

  private static currentDeviceNameAuthKey(): string {
    const longToken = PreferencesUtil.getStringSync(PreferenceKeys.longToken).trim();
    if (longToken.length > 0) {
      return SessionStore.compactSessionValue(longToken);
    }
    const token = PreferencesUtil.getStringSync(PreferenceKeys.token).trim();
    if (token.length > 0) {
      return SessionStore.compactSessionValue(token);
    }
    const secret = PreferencesUtil.getStringSync(PreferenceKeys.secret).trim();
    if (secret.length > 0) {
      return SessionStore.compactSessionValue(secret);
    }
    return "no-auth";
  }

  private static compactSessionValue(value: string): string {
    const normalized = value.trim();
    if (normalized.length <= 24) {
      return `${normalized.length}:${normalized}`;
    }
    return `${normalized.length}:${normalized.substring(normalized.length - 24)}`;
  }

  private static legacyDeviceNamePreferenceKey(hostKey: string): string {
    return `${PreferenceKeys.deviceNamePrefix}${hostKey.trim()}`;
  }

  private static randomString(length: number): string {
    const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    let result = "";
    for (let index = 0; index < length; index += 1) {
      result += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    return result;
  }
}
