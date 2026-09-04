import { common } from '@kit.AbilityKit';
import { preferences } from '@kit.ArkData';

const DEFAULT_PREFERENCE_NAME: string = 'SP_HARMONY_UTILS_PREFERENCES';

export class PreferencesUtil {
  private static context?: common.UIAbilityContext;
  private static defaultPreferenceName: string = DEFAULT_PREFERENCE_NAME;
  private static preference?: preferences.Preferences;

  static init(context: common.UIAbilityContext, preferenceName: string = DEFAULT_PREFERENCE_NAME): void {
    PreferencesUtil.context = context;
    PreferencesUtil.defaultPreferenceName = preferenceName;
    PreferencesUtil.preference = preferences.getPreferencesSync(context, { name: preferenceName });
  }

  static putSync(key: string, value: preferences.ValueType, preferenceName: string = PreferencesUtil.defaultPreferenceName): void {
    const store = PreferencesUtil.getPreferencesSync(preferenceName);
    store.putSync(key, value);
    store.flushSync();
  }

  static async put(key: string, value: preferences.ValueType,
    preferenceName: string = PreferencesUtil.defaultPreferenceName): Promise<void> {
    const store = PreferencesUtil.getPreferencesSync(preferenceName);
    await store.put(key, value);
    await store.flush();
  }

  static getStringSync(key: string, defValue: string = '', preferenceName: string = PreferencesUtil.defaultPreferenceName): string {
    return PreferencesUtil.getPreferencesSync(preferenceName).getSync(key, defValue) as string;
  }

  static getBooleanSync(key: string, defValue: boolean = false,
    preferenceName: string = PreferencesUtil.defaultPreferenceName): boolean {
    return PreferencesUtil.getPreferencesSync(preferenceName).getSync(key, defValue) as boolean;
  }

  static getNumberSync(key: string, defValue: number = 0,
    preferenceName: string = PreferencesUtil.defaultPreferenceName): number {
    return PreferencesUtil.getPreferencesSync(preferenceName).getSync(key, defValue) as number;
  }

  private static getPreferencesSync(preferenceName: string = PreferencesUtil.defaultPreferenceName): preferences.Preferences {
    const context = PreferencesUtil.context ?? getContext() as common.UIAbilityContext;
    if (preferenceName !== PreferencesUtil.defaultPreferenceName) {
      return preferences.getPreferencesSync(context, { name: preferenceName });
    }
    if (!PreferencesUtil.preference) {
      PreferencesUtil.preference = preferences.getPreferencesSync(context, { name: preferenceName });
    }
    return PreferencesUtil.preference;
  }
}
