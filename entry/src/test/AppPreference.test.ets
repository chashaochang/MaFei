import { describe, expect, it } from '@ohos/hypium'
import {
  AppPreference,
  AppPreferenceBackend,
  AppPreferenceBackendFactory
} from '../main/ets/data/AppPreference'
import { ThemeStyle } from '../main/ets/entity/ThemeStyle'
import { Constants } from '../main/ets/utils/Constants'

class FakePreferenceBackend implements AppPreferenceBackend {
  values: Map<string, string> = new Map()
  failNextWrite: boolean = false
  failNextFlush: boolean = false

  has(key: string): boolean {
    return this.values.has(key)
  }

  read(key: string): string {
    return this.values.get(key) ?? ''
  }

  write(key: string, value: string): void {
    if (this.failNextWrite) {
      this.failNextWrite = false
      throw new Error('write failed')
    }
    this.values.set(key, value)
  }

  remove(key: string): void {
    this.values.delete(key)
  }

  flush(): void {
    if (this.failNextFlush) {
      this.failNextFlush = false
      throw new Error('flush failed')
    }
  }
}

class FakePreferenceBackendFactory implements AppPreferenceBackendFactory {
  backend: FakePreferenceBackend = new FakePreferenceBackend()
  failOpen: boolean = false
  openCount: number = 0

  open(): AppPreferenceBackend {
    this.openCount += 1
    if (this.failOpen) {
      throw new Error('open failed')
    }
    return this.backend
  }
}

export default function appPreferenceTest() {
  describe('AppPreference', () => {
    it('usesInjectedFactoryWithoutResolvingProductionContext', 0, () => {
      const factory = new FakePreferenceBackendFactory()
      const preference = AppPreference.createForTest(factory)

      expect(factory.openCount).assertEqual(0)
      expect(preference.getThemeStyle()).assertEqual(ThemeStyle.Feiniu)
      expect(factory.openCount).assertEqual(1)
    })

    it('returnsThemeDefaultsAfterOneFailedOpenWithoutRecursion', 0, () => {
      const factory = new FakePreferenceBackendFactory()
      factory.failOpen = true
      const preference = AppPreference.createForTest(factory)

      expect(preference.getThemeStyle()).assertEqual(ThemeStyle.Feiniu)
      expect(factory.openCount).assertEqual(1)
    })

    it('returnsThemeDefaultsForMalformedJson', 0, () => {
      const factory = new FakePreferenceBackendFactory()
      factory.backend.values.set(Constants.PREF_THEME_STYLE, '{bad-json')
      const preference = AppPreference.createForTest(factory)

      expect(preference.getThemeStyle()).assertEqual(ThemeStyle.Feiniu)
    })

    it('restoresPreviousInMemoryValueWhenFlushFails', 0, () => {
      const factory = new FakePreferenceBackendFactory()
      factory.backend.values.set(
        Constants.PREF_THEME_STYLE,
        JSON.stringify(ThemeStyle.Feiniu)
      )
      factory.backend.failNextFlush = true
      const preference = AppPreference.createForTest(factory)

      expect(preference.trySetThemeStyle(ThemeStyle.Native)).assertFalse()
      expect(preference.getThemeStyle()).assertEqual(ThemeStyle.Feiniu)
    })

    it('throwsWhenGenericSetValueWriteFails', 0, () => {
      const factory = new FakePreferenceBackendFactory()
      factory.backend.failNextWrite = true
      const preference = AppPreference.createForTest(factory)

      expect(() => {
        preference.setValue<string>('generic-key', 'value')
      }).assertThrowError('Failed to persist preference value')
      expect(factory.backend.values.has('generic-key')).assertFalse()
    })

    it('throwsAndRestoresAccessTokenWhenFlushFails', 0, () => {
      const factory = new FakePreferenceBackendFactory()
      factory.backend.values.set(
        Constants.PREF_ACCESS_TOKEN,
        JSON.stringify('old-token')
      )
      factory.backend.failNextFlush = true
      const preference = AppPreference.createForTest(factory)

      expect(() => {
        preference.setAccessToken('new-token')
      }).assertThrowError('Failed to persist preference value')
      expect(preference.getAccessToken()).assertEqual('old-token')
    })
  })
}
