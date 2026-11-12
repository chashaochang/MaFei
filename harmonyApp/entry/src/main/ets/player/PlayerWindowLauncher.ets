import { common, StartOptions } from '@kit.AbilityKit';
import { window } from '@kit.ArkUI';
import { deviceInfo } from '@kit.BasicServicesKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import nativeApi from 'libentry.so';

let activeLauncher: PlayerWindowLauncher | undefined;

export function playerWindowDestroyed(): void {
  activeLauncher?.retryAfterWindowDestroyed();
}

/** Starts one singleton UIAbility; subsequent media selections bring that window forward. */
export class PlayerWindowLauncher {
  private context: common.UIAbilityContext;
  private mainWindow: window.Window;
  private timer: number | undefined;
  private lastRevision: number = -1;
  private opening: boolean = false;
  private windowOpen: boolean = false;
  private stopped: boolean = false;

  constructor(context: common.UIAbilityContext, mainWindow: window.Window) {
    this.context = context;
    this.mainWindow = mainWindow;
  }

  start(): void {
    if (deviceInfo.deviceType !== '2in1') return;
    activeLauncher = this;
    nativeApi.SetOhosPlayerWindowEnabled(true);
    this.timer = setInterval(() => this.sync(), 200);
  }

  private sync(): void {
    if (this.stopped || this.opening) return;
    const id = nativeApi.GetOhosPlayerWindowRequestId();
    const revision = nativeApi.GetOhosPlayerWindowRevision();
    if (id === 0 || revision === this.lastRevision) return;
    this.lastRevision = revision;
    this.opening = true;
    this.open(id).catch((error: Object) => {
      nativeApi.FailOhosPlayerWindow(id);
      hilog.warn(0x4D46, 'MaFeiPlayerWindow', 'Unable to open player window: %{public}s',
        JSON.stringify(error));
    }).finally(() => { this.opening = false; });
  }

  private async open(id: number): Promise<void> {
    const options: StartOptions = { withAnimation: false };
    if (!this.windowOpen) {
      // Both windowRect and StartOptions use physical pixels. Read the main
      // window at launch time, including any resize since the app started.
      const rect = this.mainWindow.getWindowProperties().windowRect;
      options.windowWidth = rect.width;
      options.windowHeight = rect.height;
    }
    await this.context.startAbility({
      bundleName: this.context.applicationInfo.name,
      abilityName: 'PlayerAbility',
      parameters: { playerWindowRequestId: id }
    }, options);
    this.windowOpen = true;
  }

  retryAfterWindowDestroyed(): void {
    this.lastRevision = -1;
    this.windowOpen = false;
  }

  stop(): void {
    this.stopped = true;
    if (this.timer !== undefined) clearInterval(this.timer);
    this.timer = undefined;
    if (activeLauncher === this) activeLauncher = undefined;
    if (deviceInfo.deviceType === '2in1') nativeApi.SetOhosPlayerWindowEnabled(false);
  }
}
