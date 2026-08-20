import { common } from '@kit.AbilityKit'
import { preferences } from '@kit.ArkData'

const STORE_NAME: string = 'MediaProviderAccountPreference'
const EXPLICIT_SELECTION_KEY: string = 'media_provider_explicit_selection'

export interface MediaProviderSelectionHistory {
  hasExplicitSelection(): boolean
  markExplicitSelection(): void
}

export class MediaProviderSelectionHistoryStore implements MediaProviderSelectionHistory {
  private readonly preference: preferences.Preferences

  private constructor(preference: preferences.Preferences) {
    this.preference = preference
  }

  static create(context: common.Context): MediaProviderSelectionHistoryStore {
    const preference = preferences.getPreferencesSync(context, { name: STORE_NAME })
    return new MediaProviderSelectionHistoryStore(preference)
  }

  hasExplicitSelection(): boolean {
    const value = this.preference.getSync(EXPLICIT_SELECTION_KEY, false)
    return typeof value === 'boolean' && value
  }

  markExplicitSelection(): void {
    this.preference.putSync(EXPLICIT_SELECTION_KEY, true)
    this.preference.flushSync()
  }
}
