import { PreferencesUtil } from "./PreferencesUtil";

export enum FnConnectRoutePreference {
  SPEED = "speed",
  PUBLIC = "public",
  RELAY = "relay"
}

class FnConnectPreferenceKeys {
  static readonly routePreference: string = "fn-connect-route-preference";
  static readonly p2pEnabled: string = "fn-connect-p2p-enabled";
}

export class FnConnectPreferenceOption {
  value: FnConnectRoutePreference = FnConnectRoutePreference.SPEED;
  title: string = "";
}

export class FnConnectPreferenceStore {
  static routePreference(): FnConnectRoutePreference {
    const value = PreferencesUtil.getStringSync(FnConnectPreferenceKeys.routePreference).trim();
    if (value === FnConnectRoutePreference.PUBLIC || value === FnConnectRoutePreference.RELAY ||
      value === FnConnectRoutePreference.SPEED) {
      return value as FnConnectRoutePreference;
    }
    return FnConnectRoutePreference.SPEED;
  }

  static saveRoutePreference(value: FnConnectRoutePreference): void {
    PreferencesUtil.putSync(FnConnectPreferenceKeys.routePreference, value);
  }

  static p2pEnabled(): boolean {
    return PreferencesUtil.getBooleanSync(FnConnectPreferenceKeys.p2pEnabled, true);
  }

  static saveP2pEnabled(value: boolean): void {
    PreferencesUtil.putSync(FnConnectPreferenceKeys.p2pEnabled, value);
  }

  static options(): FnConnectPreferenceOption[] {
    return [
      FnConnectPreferenceStore.option(FnConnectRoutePreference.SPEED, "速度优先"),
      FnConnectPreferenceStore.option(FnConnectRoutePreference.PUBLIC, "公网连接优先"),
      FnConnectPreferenceStore.option(FnConnectRoutePreference.RELAY, "中继转发优先")
    ];
  }

  static routeTitle(value: FnConnectRoutePreference = FnConnectPreferenceStore.routePreference()): string {
    if (value === FnConnectRoutePreference.PUBLIC) {
      return "公网连接优先";
    }
    if (value === FnConnectRoutePreference.RELAY) {
      return "中继转发优先";
    }
    return "速度优先";
  }

  private static option(value: FnConnectRoutePreference, title: string): FnConnectPreferenceOption {
    const item = new FnConnectPreferenceOption();
    item.value = value;
    item.title = title;
    return item;
  }
}
