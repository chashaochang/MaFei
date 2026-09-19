import { common } from '@kit.AbilityKit';
import { preferences, relationalStore } from '@kit.ArkData';
import { asset } from '@kit.AssetStoreKit';
import { util } from '@kit.ArkTS';
import { hilog } from '@kit.PerformanceAnalysisKit';

const DOMAIN = 0x4D46;
const CAPTURED = 'captured';
const READ = 'Read';
const ABSENT = 'ConfirmedAbsent';

interface LegacySourcePayload {
  JellyfinDatabase: string;
  JellyfinPreferences: string;
  FeiniuAccounts: string;
  FeiniuCredentials: string;
  FeiniuLegacySession: string;
  AccountSelection: string;
  PlaybackPreferences: string;
  ChasingSeries: string;
  HomeSnapshot: string;
}

interface LegacyTokenPayload {
  scheme: string;
  value: string;
}

interface LegacyServerPayload {
  key: string;
  provider: string;
  name: string;
  baseUrl: string;
  version?: string;
  originalAddress?: string;
  connectionRoute?: string;
  fallbackBaseUrls?: string[];
}

interface LegacyAccountPayload {
  key: string;
  serverKey: string;
  remoteUserId: string;
  userName: string;
  token?: LegacyTokenPayload;
  cookie?: string;
  password?: string;
  lastActiveEpochMs?: number;
}

interface LegacySnapshotPayload {
  status: string;
  version: number;
  installationId: string;
  sources: LegacySourcePayload;
  servers: LegacyServerPayload[];
  accounts: LegacyAccountPayload[];
  selectedAccountKey?: string;
}

interface CurrentAccountPayload {
  provider?: string;
  providerAccountId?: string;
}

interface StoredFeiniuCredentials {
  version?: number;
  token?: string;
  cookie?: string;
}

interface CapturedFeiniuCredentials {
  token: string;
  cookie: string;
}

interface AccountCapture {
  servers: LegacyServerPayload[];
  accounts: LegacyAccountPayload[];
  selectionKeys: Map<string, string>;
  sourceState: string;
}

interface LegacyCmpSettingsEntry {
  key: string;
  value: string;
}

interface LegacyCmpSettingsPayload {
  status: string;
  version: number;
  entries: LegacyCmpSettingsEntry[];
}

/**
 * Reads the first CMP prototype's ordinary Preferences store. Only string
 * values are exported: session indexes and connection metadata are strings,
 * while credentials remain in AssetStore and are migrated by Kotlin.
 */
export function captureLegacyCmpSettings(context: common.Context): string {
  try {
    // The first CMP build opened this store with the module/ability context.
    // Reuse the same context here so ArkData resolves the existing on-disk
    // Preferences database instead of an empty application-context store.
    const store = preferences.getPreferencesSync(context, {
      name: 'mafei_cmp_settings_v1'
    });
    const values = store.getAllSync() as Record<string, preferences.ValueType>;
    const entries: LegacyCmpSettingsEntry[] = [];
    Object.keys(values).sort().forEach((key: string): void => {
      const value = values[key];
      if (typeof value === 'string' && key.length > 0 && key.length <= 512 &&
        value.length <= 1048576 && entries.length < 4096) {
        entries.push({ key: key, value: value });
      }
    });
    const payload: LegacyCmpSettingsPayload = {
      status: CAPTURED,
      version: 1,
      entries: entries
    };
    hilog.info(DOMAIN, 'MaFeiMigration',
      'Legacy CMP settings captured keys=%{public}d strings=%{public}d',
      Object.keys(values).length, entries.length);
    return JSON.stringify(payload);
  } catch (error) {
    const failure = error as Error;
    hilog.error(DOMAIN, 'MaFeiMigration',
      'Legacy CMP settings unavailable type=%{public}s', failure.name || 'Error');
    return JSON.stringify({ status: 'unavailable', version: 1, entries: [] });
  }
}

/**
 * Thin HarmonyOS data bridge. It only reads the old ArkUI stores and produces
 * a normalized in-memory snapshot. Validation, idempotency and all writes are
 * owned by the Kotlin migration module.
 */
export async function captureLegacyAccountSnapshot(context: common.Context,
  bundleName: string): Promise<string> {
  try {
    const jellyfin = await captureJellyfin(context);
    const feiniuPreferences = preferences.getPreferencesSync(context, {
      name: 'mafei_feiniu_video_preferences'
    });
    const feiniu = await captureFeiniu(context, feiniuPreferences);
    const accountPreference = preferences.getPreferencesSync(context, {
      name: 'MediaProviderAccountPreference'
    });
    const selected = selectedAccountKey(accountPreference, jellyfin.selectionKeys,
      feiniu.selectionKeys);

    // These stores are deliberately inspected even when the account-only
    // compatibility snapshot has no corresponding optional payload.
    const jellyfinPreference = preferences.getPreferencesSync(context, { name: 'JellyFin_Preference' });
    jellyfinPreference.getSync('pref_server_id', '');
    jellyfinPreference.getSync('pref_user_id', '');
    jellyfinPreference.getSync('pref_player_engine', '');
    jellyfinPreference.getSync('pref_mpv_hardware_decode', '');
    jellyfinPreference.getSync('pref_mpv_cache_size_mb', '');
    jellyfinPreference.getSync('pref_media_source_selections', '');
    const homePreference = preferences.getPreferencesSync(context, { name: 'HomeContentSnapshot' });
    homePreference.getSync('home_content_snapshot', '');

    const payload: LegacySnapshotPayload = {
      status: CAPTURED,
      version: 1,
      installationId: `ohos-${bundleName}`,
      sources: {
        JellyfinDatabase: jellyfin.sourceState,
        JellyfinPreferences: READ,
        FeiniuAccounts: feiniu.sourceState,
        FeiniuCredentials: READ,
        FeiniuLegacySession: READ,
        AccountSelection: READ,
        PlaybackPreferences: READ,
        ChasingSeries: READ,
        HomeSnapshot: READ
      },
      servers: jellyfin.servers.concat(feiniu.servers),
      accounts: jellyfin.accounts.concat(feiniu.accounts),
      selectedAccountKey: selected
    };
    const rememberedPasswordCount = payload.accounts.filter(
      (account: LegacyAccountPayload): boolean => (account.password || '').length > 0
    ).length;
    hilog.info(DOMAIN, 'MaFeiMigration',
      'Legacy account snapshot captured servers=%{public}d accounts=%{public}d passwords=%{public}d selected=%{public}s',
      payload.servers.length, payload.accounts.length, rememberedPasswordCount, selected ? 'yes' : 'no');
    return JSON.stringify(payload);
  } catch (error) {
    const failure = error as Error;
    hilog.error(DOMAIN, 'MaFeiMigration',
      'Legacy account capture failed type=%{public}s', failure.name || 'Error');
    return JSON.stringify({
      status: 'unavailable',
      reason: 'Corrupted'
    });
  }
}

async function captureJellyfin(context: common.Context): Promise<AccountCapture> {
  const store = await relationalStore.getRdbStore(context, {
    name: 'jellyFin.db',
    securityLevel: relationalStore.SecurityLevel.S1
  });
  if (!tableExists(store, 'Server') || !tableExists(store, 'User')) {
    return emptyCapture();
  }

  const servers: LegacyServerPayload[] = [];
  const accounts: LegacyAccountPayload[] = [];
  const selectionKeys: Map<string, string> = new Map();
  const serverKeys: Map<number, string> = new Map();
  const serverAddresses: Map<number, string> = new Map();
  const serverResult = store.querySqlSync(
    'SELECT id, hostname, server_name, version FROM Server ORDER BY id ASC'
  );
  try {
    while (serverResult.goToNextRow()) {
      const id = serverResult.getLong(serverResult.getColumnIndex('id'));
      const baseUrl = serverResult.getString(serverResult.getColumnIndex('hostname')).trim();
      if (id <= 0 || !isHttpUrl(baseUrl)) {
        continue;
      }
      const key = `jellyfin-server-${id}`;
      serverKeys.set(id, key);
      serverAddresses.set(id, baseUrl);
      servers.push({
        key: key,
        provider: 'jellyfin',
        name: serverResult.getString(serverResult.getColumnIndex('server_name')).trim(),
        baseUrl: trimTrailingSlash(baseUrl),
        version: serverResult.getString(serverResult.getColumnIndex('version')).trim(),
        originalAddress: baseUrl,
        connectionRoute: 'direct'
      });
    }
  } finally {
    serverResult.close();
  }

  const userResult = store.querySqlSync(
    'SELECT id, server_id, user_id, access_token, user_name, last_login_timestamp FROM `User` ORDER BY id ASC'
  );
  try {
    while (userResult.goToNextRow()) {
      const rowId = userResult.getLong(userResult.getColumnIndex('id'));
      const serverId = userResult.getLong(userResult.getColumnIndex('server_id'));
      const serverKey = serverKeys.get(serverId);
      const remoteUserId = userResult.getString(userResult.getColumnIndex('user_id')).trim();
      if (!serverKey || rowId <= 0 || remoteUserId.length === 0) {
        continue;
      }
      const accountKey = `jellyfin-account-${rowId}`;
      const accessToken = userResult.getString(userResult.getColumnIndex('access_token')).trim();
      const userName = userResult.getString(userResult.getColumnIndex('user_name')).trim();
      const account: LegacyAccountPayload = {
        key: accountKey,
        serverKey: serverKey,
        remoteUserId: remoteUserId,
        userName: userName,
        lastActiveEpochMs: userResult.getLong(userResult.getColumnIndex('last_login_timestamp'))
      };
      if (accessToken.length > 0) {
        account.token = { scheme: 'MediaBrowser', value: accessToken };
      }
      const rememberedPassword = readJellyfinPassword(serverAddresses.get(serverId) || '', userName);
      if (rememberedPassword.length > 0) {
        account.password = rememberedPassword;
      }
      accounts.push(account);
      selectionKeys.set(selectionIdentity('jellyfin', jellyfinProviderAccountId(serverId, remoteUserId)),
        accountKey);
    }
  } finally {
    userResult.close();
  }
  return { servers: servers, accounts: accounts, selectionKeys: selectionKeys, sourceState: READ };
}

function readJellyfinPassword(baseUrl: string, username: string): string {
  if (baseUrl.length === 0 || username.length === 0) {
    return '';
  }
  const candidates: string[] = [baseUrl, trimTrailingSlash(baseUrl)];
  const visited: Set<string> = new Set();
  for (let index = 0; index < candidates.length; index += 1) {
    const address = candidates[index].trim();
    if (address.length === 0 || visited.has(address)) {
      continue;
    }
    visited.add(address);
    try {
      const query: asset.AssetMap = new Map();
      query.set(asset.Tag.ALIAS, new util.TextEncoder().encodeInto(`${address}_${username}`));
      query.set(asset.Tag.RETURN_TYPE, asset.ReturnType.ALL);
      const stored: Array<asset.AssetMap> = asset.querySync(query);
      if (stored.length > 0) {
        const secret = stored[0].get(asset.Tag.SECRET) as Uint8Array;
        return util.TextDecoder.create('utf-8').decodeToString(secret);
      }
    } catch (_error) {
      // Missing, locked or obsolete legacy assets do not invalidate RDB account history.
    }
  }
  return '';
}

async function captureFeiniu(context: common.Context,
  credentialPreference: preferences.Preferences): Promise<AccountCapture> {
  const store = await relationalStore.getRdbStore(context, {
    name: 'mafei_feiniu_accounts.db',
    securityLevel: relationalStore.SecurityLevel.S1
  });
  if (!tableExists(store, 'FeiniuVideoAccount')) {
    return emptyCapture();
  }

  const servers: LegacyServerPayload[] = [];
  const accounts: LegacyAccountPayload[] = [];
  const selectionKeys: Map<string, string> = new Map();
  const result = store.querySqlSync(
    'SELECT provider_account_id, user_id, display_name, server_name, original_address, base_url, ' +
    'connection_route, fallback_base_urls, last_used_timestamp FROM FeiniuVideoAccount ' +
    'ORDER BY last_used_timestamp DESC'
  );
  try {
    while (result.goToNextRow()) {
      const providerAccountId = result.getString(result.getColumnIndex('provider_account_id')).trim();
      const remoteUserId = result.getString(result.getColumnIndex('user_id')).trim();
      const baseUrl = result.getString(result.getColumnIndex('base_url')).trim();
      const originalAddress = result.getString(result.getColumnIndex('original_address')).trim();
      if (providerAccountId.length === 0 || remoteUserId.length === 0 || !isHttpUrl(baseUrl)) {
        continue;
      }
      const serverKey = `feiniu-server-${providerAccountId}`;
      const accountKey = `feiniu-account-${providerAccountId}`;
      servers.push({
        key: serverKey,
        provider: 'feiniu',
        name: result.getString(result.getColumnIndex('server_name')).trim(),
        baseUrl: trimTrailingSlash(baseUrl),
        originalAddress: originalAddress.length > 0 ? originalAddress : baseUrl,
        connectionRoute: normalizedRoute(result.getString(result.getColumnIndex('connection_route'))),
        fallbackBaseUrls: parseStringArray(
          result.getString(result.getColumnIndex('fallback_base_urls'))
        ).filter((value: string): boolean => isHttpUrl(value))
      });
      const credentials = readFeiniuCredentials(credentialPreference, providerAccountId);
      const account: LegacyAccountPayload = {
        key: accountKey,
        serverKey: serverKey,
        remoteUserId: remoteUserId,
        userName: result.getString(result.getColumnIndex('display_name')).trim(),
        lastActiveEpochMs: result.getLong(result.getColumnIndex('last_used_timestamp'))
      };
      if (credentials.token.length > 0) {
        account.token = { scheme: 'Bearer', value: credentials.token };
      }
      if (credentials.cookie.length > 0) {
        account.cookie = credentials.cookie;
      }
      accounts.push(account);
      selectionKeys.set(selectionIdentity('feiniu_video', providerAccountId), accountKey);
    }
  } finally {
    result.close();
  }
  return { servers: servers, accounts: accounts, selectionKeys: selectionKeys, sourceState: READ };
}

function tableExists(store: relationalStore.RdbStore, tableName: string): boolean {
  const result = store.querySqlSync(
    `SELECT name FROM sqlite_master WHERE type='table' AND name='${tableName}'`
  );
  try {
    return result.goToNextRow();
  } finally {
    result.close();
  }
}

function emptyCapture(): AccountCapture {
  return {
    servers: [],
    accounts: [],
    selectionKeys: new Map(),
    sourceState: ABSENT
  };
}

function selectedAccountKey(store: preferences.Preferences,
  jellyfinKeys: Map<string, string>, feiniuKeys: Map<string, string>): string | undefined {
  const raw = store.getSync('current_media_provider_account', '');
  if (typeof raw !== 'string' || raw.length === 0) {
    return undefined;
  }
  try {
    const selected = JSON.parse(raw) as CurrentAccountPayload;
    const provider = `${selected.provider || ''}`.trim();
    const providerAccountId = `${selected.providerAccountId || ''}`.trim();
    if (provider.length === 0 || providerAccountId.length === 0) {
      return undefined;
    }
    const identity = selectionIdentity(provider, providerAccountId);
    return provider === 'feiniu_video' ? feiniuKeys.get(identity) : jellyfinKeys.get(identity);
  } catch (_error) {
    return undefined;
  }
}

function readFeiniuCredentials(store: preferences.Preferences,
  providerAccountId: string): CapturedFeiniuCredentials {
  const alias = `mafei.feiniu.video.credentials.v1:${providerAccountId}`;
  const raw = store.getSync(alias, '');
  if (typeof raw !== 'string' || raw.length === 0) {
    return { token: '', cookie: '' };
  }
  try {
    const stored = JSON.parse(raw) as StoredFeiniuCredentials;
    if (stored.version !== 1) {
      return { token: '', cookie: '' };
    }
    const cookie = `${stored.cookie || ''}`.trim();
    const token = `${stored.token || ''}`.trim() || tokenFromCookie(cookie);
    return { token: token, cookie: cookie };
  } catch (_error) {
    return { token: '', cookie: '' };
  }
}

function tokenFromCookie(cookie: string): string {
  const segments = cookie.split(';');
  for (let index = 0; index < segments.length; index += 1) {
    const segment = segments[index].trim();
    const separator = segment.indexOf('=');
    if (separator > 0 && segment.substring(0, separator).trim() === 'Trim-MC-token') {
      return segment.substring(separator + 1).trim();
    }
  }
  return '';
}

function parseStringArray(raw: string): string[] {
  try {
    const values = JSON.parse(raw) as string[];
    return Array.isArray(values) ? values.filter((value: string): boolean =>
      typeof value === 'string' && value.trim().length > 0).map((value: string): string =>
      trimTrailingSlash(value.trim())) : [];
  } catch (_error) {
    return [];
  }
}

function normalizedRoute(value: string): string {
  const normalized = value.trim();
  return normalized.length > 0 ? normalized : 'direct';
}

function jellyfinProviderAccountId(serverId: number, userId: string): string {
  return `v1|${serverId}|${userId.length}:${userId}`;
}

function selectionIdentity(provider: string, providerAccountId: string): string {
  return `${provider}\n${providerAccountId}`;
}

function trimTrailingSlash(value: string): string {
  return value.replace(/\/+$/, '');
}

function isHttpUrl(value: string): boolean {
  const normalized = value.trim();
  return (normalized.startsWith('http://') || normalized.startsWith('https://')) &&
    normalized.indexOf('?') < 0 && normalized.indexOf('#') < 0 && normalized.indexOf('@') < 0;
}
