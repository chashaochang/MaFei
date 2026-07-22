import { describe, expect, it } from '@ohos/hypium'
import { ThemeStyle } from '../main/ets/entity/ThemeStyle'
import {
  ThemePreferenceStore,
  ThemePromptService,
  ThemeWriteStatus,
  themePromptService
} from '../main/ets/theme/ThemePromptService'
import {
  NativeThemeCapabilityReason,
  NativeThemeCapabilityResult
} from '../main/ets/theme/NativeThemeCapability'

class FakeThemePreferenceStore implements ThemePreferenceStore {
  themeStyle: ThemeStyle = ThemeStyle.Feiniu
  promptVersion: string = ''
  themeWriteSucceeds: boolean = true
  markerWriteSucceeds: boolean = true
  themeWriteCount: number = 0
  markerWriteCount: number = 0
  themeReadCount: number = 0
  markerReadCount: number = 0

  getThemeStyle(): ThemeStyle {
    this.themeReadCount += 1
    return this.themeStyle
  }

  getThemePromptVersion(): string {
    this.markerReadCount += 1
    return this.promptVersion
  }

  trySetThemeStyle(style: ThemeStyle): boolean {
    this.themeWriteCount += 1
    if (this.themeWriteSucceeds) {
      this.themeStyle = style
    }
    return this.themeWriteSucceeds
  }

  trySetThemePromptVersion(version: string): boolean {
    this.markerWriteCount += 1
    if (this.markerWriteSucceeds) {
      this.promptVersion = version
    }
    return this.markerWriteSucceeds
  }
}

const availableCapability: NativeThemeCapabilityResult = {
  available: true,
  reason: NativeThemeCapabilityReason.Available,
  sdkApiVersion: 26
}

const unavailableCapability: NativeThemeCapabilityResult = {
  available: false,
  reason: NativeThemeCapabilityReason.UnsupportedApi,
  sdkApiVersion: 25
}

export default function themePromptServiceTest() {
  describe('ThemePromptService', () => {
    it('loadsModuleSingletonWithoutResolvingProductionPreference', 0, () => {
      expect(themePromptService !== undefined).assertTrue()
    })

    it('constructsWithInjectedStoreWithoutReadingIt', 0, () => {
      const store = new FakeThemePreferenceStore()

      new ThemePromptService(store)

      expect(store.themeReadCount).assertEqual(0)
      expect(store.markerReadCount).assertEqual(0)
    })

    it('keepsLowApiPreferenceUntouched', 0, () => {
      const store = new FakeThemePreferenceStore()
      store.themeStyle = ThemeStyle.Native
      store.promptVersion = 'old-marker'
      const state = new ThemePromptService(store).resolveLaunch(unavailableCapability)

      expect(state.effectiveTheme).assertEqual(ThemeStyle.Feiniu)
      expect(state.shouldShowPrompt).assertFalse()
      expect(store.themeWriteCount).assertEqual(0)
      expect(store.markerWriteCount).assertEqual(0)
    })

    it('keepsFeiniuUntilCurrentPromptIsCompleted', 0, () => {
      const store = new FakeThemePreferenceStore()
      store.themeStyle = ThemeStyle.Native
      const state = new ThemePromptService(store).resolveLaunch(availableCapability)

      expect(state.effectiveTheme).assertEqual(ThemeStyle.Feiniu)
      expect(state.shouldShowPrompt).assertTrue()
    })

    it('honorsPersistedThemeAfterCurrentMarker', 0, () => {
      const store = new FakeThemePreferenceStore()
      store.themeStyle = ThemeStyle.Native
      store.promptVersion = ThemePromptService.CURRENT_PROMPT_VERSION
      const state = new ThemePromptService(store).resolveLaunch(availableCapability)

      expect(state.effectiveTheme).assertEqual(ThemeStyle.Native)
      expect(state.shouldShowPrompt).assertFalse()
    })

    it('doesNotWriteMarkerWhenThemeWriteFails', 0, () => {
      const store = new FakeThemePreferenceStore()
      store.themeWriteSucceeds = false
      const result = new ThemePromptService(store)
        .completePrompt(ThemeStyle.Native, availableCapability)

      expect(result.status).assertEqual(ThemeWriteStatus.ThemeWriteFailed)
      expect(result.effectiveTheme).assertEqual(ThemeStyle.Feiniu)
      expect(store.markerWriteCount).assertEqual(0)
    })

    it('keepsEffectiveFeiniuWhenMarkerWriteFails', 0, () => {
      const store = new FakeThemePreferenceStore()
      store.markerWriteSucceeds = false
      const result = new ThemePromptService(store)
        .completePrompt(ThemeStyle.Native, availableCapability)

      expect(result.status).assertEqual(ThemeWriteStatus.PromptMarkerWriteFailed)
      expect(result.effectiveTheme).assertEqual(ThemeStyle.Feiniu)
      expect(store.themeStyle).assertEqual(ThemeStyle.Native)
      expect(store.promptVersion).assertEqual('')
    })

    it('switchesEffectiveThemeOnlyAfterBothWrites', 0, () => {
      const store = new FakeThemePreferenceStore()
      const result = new ThemePromptService(store)
        .completePrompt(ThemeStyle.Native, availableCapability)

      expect(result.status).assertEqual(ThemeWriteStatus.Success)
      expect(result.effectiveTheme).assertEqual(ThemeStyle.Native)
      expect(store.promptVersion).assertEqual(ThemePromptService.CURRENT_PROMPT_VERSION)
    })

    it('allowsSavingFeiniuWhenNativeIsUnavailable', 0, () => {
      const store = new FakeThemePreferenceStore()
      store.themeStyle = ThemeStyle.Native
      const result = new ThemePromptService(store).saveTheme(
        ThemeStyle.Feiniu,
        ThemeStyle.Feiniu,
        unavailableCapability
      )

      expect(result.status).assertEqual(ThemeWriteStatus.Success)
      expect(result.effectiveTheme).assertEqual(ThemeStyle.Feiniu)
      expect(store.themeStyle).assertEqual(ThemeStyle.Feiniu)
    })

    it('keepsCurrentNativeThemeWhenSwitchToFeiniuWriteFails', 0, () => {
      const store = new FakeThemePreferenceStore()
      store.themeStyle = ThemeStyle.Native
      store.themeWriteSucceeds = false
      const result = new ThemePromptService(store).saveTheme(
        ThemeStyle.Feiniu,
        ThemeStyle.Native,
        availableCapability
      )

      expect(result.status).assertEqual(ThemeWriteStatus.ThemeWriteFailed)
      expect(result.effectiveTheme).assertEqual(ThemeStyle.Native)
      expect(store.themeStyle).assertEqual(ThemeStyle.Native)
    })
  })
}
