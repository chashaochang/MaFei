import { AppPreference } from '../../../data/AppPreference'
import { Constants } from '../../../utils/Constants'
import { FeiniuVideoRoutePreference } from './FeiniuVideoApiModels'

/**
 * MaFei-owned connection preference for Feiniu Video.
 *
 * This is deliberately separate from the copied upstream preference store so
 * changing it never mutates any upstream/global Feiniu application state.
 */
export class FeiniuVideoConnectionPreferenceStore {
  static routePreference(): FeiniuVideoRoutePreference {
    try {
      const saved = AppPreference.getInstance().getValue<string>(
        Constants.PREF_FEINIU_CONNECTION_PREFERENCE
      )
      if (saved === FeiniuVideoRoutePreference.Public) {
        return FeiniuVideoRoutePreference.Public
      }
      if (saved === FeiniuVideoRoutePreference.Relay) {
        return FeiniuVideoRoutePreference.Relay
      }
    } catch (_) {
      // AppPreference is unavailable only before application bootstrap.
    }
    return FeiniuVideoRoutePreference.Speed
  }

  static saveRoutePreference(value: FeiniuVideoRoutePreference): boolean {
    const normalized = FeiniuVideoConnectionPreferenceStore.normalize(value)
    try {
      AppPreference.getInstance().setValue<string>(
        Constants.PREF_FEINIU_CONNECTION_PREFERENCE,
        normalized
      )
      return true
    } catch (_) {
      return false
    }
  }

  private static normalize(value: FeiniuVideoRoutePreference): FeiniuVideoRoutePreference {
    if (value === FeiniuVideoRoutePreference.Public) {
      return FeiniuVideoRoutePreference.Public
    }
    if (value === FeiniuVideoRoutePreference.Relay) {
      return FeiniuVideoRoutePreference.Relay
    }
    return FeiniuVideoRoutePreference.Speed
  }
}
