import { AppPreference } from '../data/AppPreference'
import { ThemeStyle } from '../entity/ThemeStyle'
import { NativeThemeCapabilityResult, nativeThemeCapability } from './NativeThemeCapability'

export interface ThemePreferenceStore {
  getThemeStyle(): ThemeStyle
  getThemePromptVersion(): string
  trySetThemeStyle(style: ThemeStyle): boolean
  trySetThemePromptVersion(version: string): boolean
}

export interface ThemeLaunchState {
  persistedTheme: ThemeStyle
  effectiveTheme: ThemeStyle
  nativeThemeAvailable: boolean
  shouldShowPrompt: boolean
}

export enum ThemeWriteStatus {
  Success = 'success',
  NativeUnavailable = 'native_unavailable',
  ThemeWriteFailed = 'theme_write_failed',
  PromptMarkerWriteFailed = 'prompt_marker_write_failed'
}

export interface ThemeWriteResult {
  status: ThemeWriteStatus
  effectiveTheme: ThemeStyle
}

class AppPreferenceThemeStore implements ThemePreferenceStore {
  private preference: AppPreference

  constructor(preference: AppPreference) {
    this.preference = preference
  }

  getThemeStyle(): ThemeStyle {
    return this.preference.getThemeStyle()
  }

  getThemePromptVersion(): string {
    return this.preference.getThemePromptVersion()
  }

  trySetThemeStyle(style: ThemeStyle): boolean {
    return this.preference.trySetThemeStyle(style)
  }

  trySetThemePromptVersion(version: string): boolean {
    return this.preference.trySetThemePromptVersion(version)
  }
}

export class ThemePromptService {
  static readonly CURRENT_PROMPT_VERSION: string = 'api26-native-v1'
  private preference?: ThemePreferenceStore

  constructor(preference?: ThemePreferenceStore) {
    this.preference = preference
  }

  private getPreference(): ThemePreferenceStore {
    if (!this.preference) {
      this.preference = new AppPreferenceThemeStore(AppPreference.getInstance())
    }
    return this.preference
  }

  resolveLaunch(capability: NativeThemeCapabilityResult): ThemeLaunchState {
    const preference = this.getPreference()
    const persistedTheme = preference.getThemeStyle()
    if (!capability.available) {
      return {
        persistedTheme,
        effectiveTheme: ThemeStyle.Feiniu,
        nativeThemeAvailable: false,
        shouldShowPrompt: false
      }
    }
    const shouldShowPrompt = preference.getThemePromptVersion() !==
      ThemePromptService.CURRENT_PROMPT_VERSION
    return {
      persistedTheme,
      effectiveTheme: shouldShowPrompt ? ThemeStyle.Feiniu : persistedTheme,
      nativeThemeAvailable: true,
      shouldShowPrompt
    }
  }

  completePrompt(style: ThemeStyle,
    capability: NativeThemeCapabilityResult): ThemeWriteResult {
    if (!capability.available) {
      return {
        status: ThemeWriteStatus.NativeUnavailable,
        effectiveTheme: ThemeStyle.Feiniu
      }
    }
    const preference = this.getPreference()
    if (!preference.trySetThemeStyle(style)) {
      return {
        status: ThemeWriteStatus.ThemeWriteFailed,
        effectiveTheme: ThemeStyle.Feiniu
      }
    }
    if (!preference.trySetThemePromptVersion(ThemePromptService.CURRENT_PROMPT_VERSION)) {
      return {
        status: ThemeWriteStatus.PromptMarkerWriteFailed,
        effectiveTheme: ThemeStyle.Feiniu
      }
    }
    return { status: ThemeWriteStatus.Success, effectiveTheme: style }
  }

  saveTheme(style: ThemeStyle, currentEffectiveTheme: ThemeStyle,
    capability: NativeThemeCapabilityResult): ThemeWriteResult {
    if (style === ThemeStyle.Native && !capability.available) {
      return {
        status: ThemeWriteStatus.NativeUnavailable,
        effectiveTheme: currentEffectiveTheme
      }
    }
    const preference = this.getPreference()
    if (!preference.trySetThemeStyle(style)) {
      return {
        status: ThemeWriteStatus.ThemeWriteFailed,
        effectiveTheme: currentEffectiveTheme
      }
    }
    return { status: ThemeWriteStatus.Success, effectiveTheme: style }
  }

  shouldShow(): boolean {
    return this.resolveLaunch(nativeThemeCapability.current()).shouldShowPrompt
  }

  static resolveLaunchTheme(): ThemeStyle {
    return themePromptService.resolveLaunch(nativeThemeCapability.current()).effectiveTheme
  }
}

export const themePromptService: ThemePromptService = new ThemePromptService()
