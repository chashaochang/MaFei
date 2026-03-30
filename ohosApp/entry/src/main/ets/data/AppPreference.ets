import { common } from '@kit.AbilityKit';
import { preferences } from '@kit.ArkData';
import { CustomColorMode } from '../entity/CustomColorMode';
import { PlayerEngineType } from '../entity/PlayerEngineType';
import { Constants } from '../utils/Constants';

const PREFERENCES_NAME: string = 'JellyFin_Preference';

export class AppPreference {
  private preferences?: preferences.Preferences;
  private context = getContext(this) as common.UIAbilityContext;
  private static instance: AppPreference;

  private constructor() {
    this.initPreference(PREFERENCES_NAME);
  }

  public static getInstance(): AppPreference {
    if (!AppPreference.instance) {
      AppPreference.instance = new AppPreference();
    }
    return AppPreference.instance;
  }

  initPreference(storeName: string) {
    try {
      this.preferences = preferences.getPreferencesSync(this.context, { name: storeName })
    } catch (error) {

    }
  }

  setValue<T>(key: string, value: T) {
    if (this.preferences) {
      this.preferences.putSync(key, JSON.stringify(value))
      this.saveUserData();
    } else {
      this.initPreference(PREFERENCES_NAME)
      this.setValue<T>(key, value);
    }
  }

  getValue<T>(key: string): T | null {
    if (this.preferences) {
      let value: T | null = null;
      let res = this.preferences.getSync(key, '')
      if (res) {
        value = JSON.parse(res as string) as T;
      }
      return value;
    } else {
      this.initPreference(PREFERENCES_NAME)
      return this.getValue<T>(key);
    }
  }

  hasValue(key: string): boolean {
    if (this.preferences) {
      return this.preferences.hasSync(key);
    } else {
      this.initPreference(PREFERENCES_NAME)
      return this.hasValue(key);
    }
  }

  deleteValue(key: string) {
    if (this.preferences) {
      this.preferences.deleteSync(key)
      this.saveUserData();
    } else {
      this.initPreference(PREFERENCES_NAME)
      this.deleteValue(key);
    }
  }

  saveUserData() {
    try {
      this.preferences?.flushSync();
    } catch (error) {

    }
  }

  currentServerId?: number

  getCurrentServerId(): number | null {
    let id = this.getValue<number>(Constants.PREF_SERVER_ID)
    if (id !== undefined && id != null && id >= 0) {
      return id
    }
    return null
  }

  setCurrentServerId(id: number) {
    this.setValue<number>(Constants.PREF_SERVER_ID, id)
  }

  getCurrentUserId(): string {
    let id = this.getValue<string>(Constants.PREF_USER_ID)
    if (id && id.length > 0) {
      return id
    }
    return ''
  }

  setCurrentUserId(id: string) {
    this.setValue<string>(Constants.PREF_USER_ID, id)
  }

  getAccessToken(): string {
    return this.getValue<string>(Constants.PREF_ACCESS_TOKEN) || ''
  }

  setAccessToken(token: string) {
    this.setValue<string>(Constants.PREF_ACCESS_TOKEN, token)
  }

  getUserName(): string {
    return this.getValue<string>(Constants.PREF_USER_NAME) ?? ''
  }

  setUserName(userName: string) {
    this.setValue<string>(Constants.PREF_USER_NAME, userName)
  }

  setCustomColorMode(mode: CustomColorMode) {
    this.setValue<CustomColorMode>(Constants.PREF_COLOR_MODE, mode)
  }

  getCustomColorMode(): CustomColorMode {
    let mode = this.getValue<CustomColorMode>(Constants.PREF_COLOR_MODE)
    if (mode !== null) {
      return mode
    }
    return CustomColorMode.Auto
  }

  setPlayerEngineType(type: PlayerEngineType) {
    this.setValue<PlayerEngineType>(Constants.PREF_PLAYER_ENGINE, type)
  }

  getPlayerEngineType(): PlayerEngineType {
    let type = this.getValue<PlayerEngineType>(Constants.PREF_PLAYER_ENGINE)
    if (type !== null) {
      return type
    }
    return PlayerEngineType.MPV
  }

  setMpvUseHardwareDecode(enabled: boolean) {
    this.setValue<boolean>(Constants.PREF_MPV_HARDWARE_DECODE, enabled)
  }

  getMpvUseHardwareDecode(): boolean {
    let enabled = this.getValue<boolean>(Constants.PREF_MPV_HARDWARE_DECODE)
    if (enabled !== null) {
      return enabled
    }
    return false
  }

  setMpvCacheSizeMb(sizeMb: number) {
    this.setValue<number>(Constants.PREF_MPV_CACHE_SIZE_MB, sizeMb)
  }

  getMpvCacheSizeMb(): number {
    let sizeMb = this.getValue<number>(Constants.PREF_MPV_CACHE_SIZE_MB)
    if (sizeMb !== null && sizeMb > 0) {
      return sizeMb
    }
    return 1024
  }

  getSearchHistory(): string[] {
    let history = this.getValue<string[]>(Constants.PREF_SEARCH_HISTORY)
    if (history !== null) {
      return history
    }
    return []
  }

  setSearchHistory(history: string[]) {
    this.setValue<string[]>(Constants.PREF_SEARCH_HISTORY, history)
  }
}
