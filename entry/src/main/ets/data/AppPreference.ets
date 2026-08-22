import { common } from '@kit.AbilityKit';
import { preferences } from '@kit.ArkData';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { CustomColorMode } from '../entity/CustomColorMode';
import { PlayerEngineType } from '../entity/PlayerEngineType';
import { Constants } from '../utils/Constants';
import { ThemeStyle } from '../entity/ThemeStyle';

const PREFERENCES_NAME: string = 'JellyFin_Preference';

export interface AppPreferenceBackend {
  has(key: string): boolean
  read(key: string): string
  write(key: string, value: string): void
  remove(key: string): void
  flush(): void
}

export interface AppPreferenceBackendFactory {
  open(): AppPreferenceBackend
}

class HarmonyPreferenceBackend implements AppPreferenceBackend {
  private preference: preferences.Preferences

  constructor(preference: preferences.Preferences) {
    this.preference = preference
  }

  has(key: string): boolean {
    return this.preference.hasSync(key)
  }

  read(key: string): string {
    const value = this.preference.getSync(key, '')
    return typeof value === 'string' ? value : ''
  }

  write(key: string, value: string): void {
    this.preference.putSync(key, value)
  }

  remove(key: string): void {
    this.preference.deleteSync(key)
  }

  flush(): void {
    this.preference.flushSync()
  }
}

class HarmonyPreferenceBackendFactory implements AppPreferenceBackendFactory {
  private context: common.Context
  private storeName: string

  constructor(context: common.Context, storeName: string) {
    this.context = context
    this.storeName = storeName
  }

  open(): AppPreferenceBackend {
    return new HarmonyPreferenceBackend(preferences.getPreferencesSync(
      this.context,
      { name: this.storeName }
    ))
  }
}

export class AppPreference {
  private backend?: AppPreferenceBackend
  private backendFactory: AppPreferenceBackendFactory
  private static instance?: AppPreference

  private constructor(factory: AppPreferenceBackendFactory) {
    this.backendFactory = factory
  }

  public static initialize(context: common.Context): void {
    if (!AppPreference.instance) {
      AppPreference.instance = new AppPreference(
        new HarmonyPreferenceBackendFactory(context, PREFERENCES_NAME)
      )
    }
  }

  public static getInstance(): AppPreference {
    if (!AppPreference.instance) {
      throw new Error('AppPreference has not been initialized')
    }
    return AppPreference.instance
  }

  static createForTest(factory: AppPreferenceBackendFactory): AppPreference {
    return new AppPreference(factory)
  }

  private getBackend(): AppPreferenceBackend | undefined {
    if (this.backend) {
      return this.backend
    }
    try {
      this.backend = this.backendFactory.open()
      return this.backend
    } catch (error) {
      return undefined
    }
  }

  getValue<T>(key: string): T | null {
    const backend = this.getBackend()
    if (!backend) {
      return null
    }
    try {
      const raw = backend.read(key)
      return raw.length > 0 ? JSON.parse(raw) as T : null
    } catch (error) {
      return null
    }
  }

  setValue<T>(key: string, value: T): void {
    if (!this.trySetValue<T>(key, value)) {
      throw new Error('Failed to persist preference value')
    }
  }

  hasValue(key: string): boolean {
    const backend = this.getBackend()
    if (!backend) {
      return false
    }
    try {
      return backend.has(key)
    } catch (error) {
      return false
    }
  }

  deleteValue(key: string): void {
    const backend = this.getBackend()
    if (!backend) {
      return
    }
    try {
      backend.remove(key)
      backend.flush()
    } catch (error) {
      // Preserve the existing best-effort delete contract without recursion.
    }
  }

  private trySetValue<T>(key: string, value: T): boolean {
    const backend = this.getBackend()
    if (!backend) {
      return false
    }
    let writeApplied = false
    let hadPrevious = false
    let previous = ''
    try {
      hadPrevious = backend.has(key)
      previous = hadPrevious ? backend.read(key) : ''
      backend.write(key, JSON.stringify(value))
      writeApplied = true
      backend.flush()
      return true
    } catch (error) {
      if (writeApplied) {
        try {
          if (hadPrevious) {
            backend.write(key, previous)
          } else {
            backend.remove(key)
          }
        } catch (rollbackError) {
          hilog.error(0x0000, 'Mafei',
            'Failed to roll back preference key %{public}s: %{public}s',
            key, String(rollbackError))
        }
      }
      return false
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

  getCurrentServerSystemId(): string {
    return this.getValue<string>(Constants.PREF_SERVER_SYSTEM_ID) || ''
  }

  setCurrentServerSystemId(id: string) {
    this.setValue<string>(Constants.PREF_SERVER_SYSTEM_ID, id)
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

  trySetThemeStyle(style: ThemeStyle): boolean {
    return this.trySetValue<ThemeStyle>(Constants.PREF_THEME_STYLE, style)
  }

  trySetThemePromptVersion(version: string): boolean {
    return this.trySetValue<string>(Constants.PREF_THEME_PROMPT_VERSION, version)
  }

  setThemeStyle(style: ThemeStyle): void {
    if (!this.trySetThemeStyle(style)) {
      throw new Error('Failed to persist theme style')
    }
  }

  getThemeStyle(): ThemeStyle {
    const style = this.getValue<ThemeStyle>(Constants.PREF_THEME_STYLE)
    if (style === ThemeStyle.Native || style === ThemeStyle.Feiniu) {
      return style
    }
    return ThemeStyle.Feiniu
  }

  setThemePromptVersion(version: string): void {
    if (!this.trySetThemePromptVersion(version)) {
      throw new Error('Failed to persist theme prompt version')
    }
  }

  getThemePromptVersion(): string {
    return this.getValue<string>(Constants.PREF_THEME_PROMPT_VERSION) || ''
  }

  setPlayerEngineType(type: PlayerEngineType) {
    this.setValue<PlayerEngineType>(Constants.PREF_PLAYER_ENGINE, type)
  }

  getPlayerEngineType(): PlayerEngineType {
    let type = this.getValue<PlayerEngineType>(Constants.PREF_PLAYER_ENGINE)
    if (type !== null) {
      return type
    }
    return PlayerEngineType.AUTO
  }

  setMpvUseHardwareDecode(enabled: boolean) {
    this.setValue<boolean>(Constants.PREF_MPV_HARDWARE_DECODE, enabled)
  }

  getMpvUseHardwareDecode(): boolean {
    let enabled = this.getValue<boolean>(Constants.PREF_MPV_HARDWARE_DECODE)
    if (enabled !== null) {
      return enabled
    }
    return true
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

  private getChasingSeriesIdsKey(): string {
    const serverSystemId = this.getCurrentServerSystemId()
    const serverId = this.getCurrentServerId()
    const userId = this.getCurrentUserId()
    const serverKey = serverSystemId.trim().length > 0 ? serverSystemId :
      (serverId !== null ? serverId.toString() : 'default')
    const userKey = userId.trim().length > 0 ? userId : 'default'
    return `${Constants.PREF_CHASING_SERIES_IDS}_${serverKey}_${userKey}`
  }

  private normalizeChasingSeriesIds(ids: string[]): string[] {
    const result: string[] = []
    ids.forEach((item: string) => {
      const id = item.trim()
      if (id.length > 0 && result.indexOf(id) < 0) {
        result.push(id)
      }
    })
    return result
  }

  getChasingSeriesIds(): string[] {
    const ids = this.getValue<string[]>(this.getChasingSeriesIdsKey())
    if (ids === null) {
      return []
    }
    return this.normalizeChasingSeriesIds(ids)
  }

  setChasingSeriesIds(ids: string[]) {
    this.setValue<string[]>(this.getChasingSeriesIdsKey(), this.normalizeChasingSeriesIds(ids))
  }

  isChasingSeries(seriesId: string): boolean {
    if (seriesId.length === 0) {
      return false
    }
    return this.getChasingSeriesIds().indexOf(seriesId) >= 0
  }

  setChasingSeries(seriesId: string, chasing: boolean): boolean {
    const normalizedId = seriesId.trim()
    if (normalizedId.length === 0) {
      return false
    }
    const currentIds = this.getChasingSeriesIds()
    const nextIds = chasing ? currentIds.concat([normalizedId]) :
      currentIds.filter((id: string): boolean => id !== normalizedId)
    this.setChasingSeriesIds(nextIds)
    return chasing
  }

  toggleChasingSeries(seriesId: string): boolean {
    const normalizedId = seriesId.trim()
    if (normalizedId.length === 0) {
      return false
    }
    return this.setChasingSeries(normalizedId, !this.isChasingSeries(normalizedId))
  }
}
