import { ConfigurationConstant, UIAbility, Want } from '@kit.AbilityKit';
import { KeyboardAvoidMode, window } from '@kit.ArkUI';
import { deviceInfo } from '@kit.BasicServicesKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import nativeApi from 'libentry.so';
import { playerWindowDestroyed } from '../player/PlayerWindowLauncher';
import { setApplicationWindowForeground } from './ApplicationVisibility';

/** Window/lifecycle host only. The existing session-owned Compose player supplies all UI. */
export default class PlayerAbility extends UIAbility {
  private playerWindow: window.Window | undefined;
  private requestId: number = 0;
  private closingId: number = 0;
  private timer: number | undefined;
  private lastFullscreenRevision: number = -1;
  private transitionInFlight: boolean = false;
  private restoreStatus: window.WindowStatusType | undefined;
  private readonly metricsChanged = (): void => this.publishMetrics();
  private readonly windowWillClose = async (): Promise<boolean> => {
    this.markClosing();
    return false;
  };

  onCreate(want: Want): void {
    const id = want.parameters?.playerWindowRequestId;
    this.requestId = typeof id === 'number' ? id : 0;
    if (deviceInfo.deviceType !== '2in1' || this.requestId === 0 ||
        this.requestId !== nativeApi.GetOhosPlayerWindowRequestId()) {
      this.finish();
    }
  }

  onNewWant(want: Want): void {
    if (this.closingId !== 0) return;
    const id = want.parameters?.playerWindowRequestId;
    if (typeof id === 'number' && id === nativeApi.GetOhosPlayerWindowRequestId()) {
      this.requestId = id;
      this.updateTitle();
    }
  }

  onWindowStageCreate(stage: window.WindowStage): void {
    if (this.closingId !== 0 || this.requestId === 0 ||
        nativeApi.GetOhosPlayerWindowRequestId() === 0) {
      this.finish();
      return;
    }
    try {
      const win = stage.getMainWindowSync();
      this.playerWindow = win;
      win.on('avoidAreaChange', this.metricsChanged);
      win.on('windowSizeChange', this.metricsChanged);
      win.on('windowStatusDidChange', this.metricsChanged);
      win.on('windowTitleButtonRectChange', this.metricsChanged);
      win.on('windowWillClose', this.windowWillClose);
      win.setWindowLimits({ minWidth: 640, minHeight: 360 }).catch(() => {});
      win.setWindowLayoutFullScreen(true).then(() => this.loadContent(stage, win)).catch(() => {
        this.loadContent(stage, win);
      });
    } catch (error) {
      this.fail(error);
    }
  }

  private loadContent(stage: window.WindowStage, win: window.Window): void {
    if (this.closingId !== 0 || this.playerWindow !== win) return;
    stage.loadContent('pages/Player', (error) => {
      if (error.code) {
        this.fail(error);
        return;
      }
      if (this.playerWindow !== win || this.closingId !== 0) return;
      try {
        win.setWindowDecorHeight(52);
        win.setWindowDecorVisible(false);
        win.setWindowTitleButtonVisible(true, true, true);
        win.setWindowTitleMoveEnabled(true);
        win.setDecorButtonStyle({
          colorMode: ConfigurationConstant.ColorMode.COLOR_MODE_DARK,
          buttonBackgroundSize: 28,
          spacingBetweenButtons: 12,
          closeButtonRightMargin: 20,
          buttonIconSize: 20,
          buttonBackgroundCornerRadius: 6
        });
        win.getUIContext().setKeyboardAvoidMode(KeyboardAvoidMode.NONE);
      } catch (chromeError) {
        hilog.warn(0x4D46, 'MaFeiPlayerWindow', 'Unable to configure player chrome: %{public}s',
          JSON.stringify(chromeError));
      }
      this.updateTitle();
      this.publishMetrics();
      this.timer = setInterval(() => this.sync(), 200);
      this.sync();
    });
  }

  private updateTitle(): void {
    try {
      this.playerWindow?.setWindowTitle(nativeApi.GetOhosPlayerWindowTitle());
    } catch (_) { /* The next media selection retries after window creation. */ }
  }

  private sync(): void {
    if (this.closingId !== 0) return;
    const currentId = nativeApi.GetOhosPlayerWindowRequestId();
    if (currentId === 0) {
      this.finish();
      return;
    }
    if (currentId !== this.requestId) {
      this.requestId = currentId;
      this.updateTitle();
    }
    const win = this.playerWindow;
    if (!win || this.transitionInFlight) return;
    const revision = nativeApi.GetOhosPlayerWindowFullscreenRevision();
    if (revision === this.lastFullscreenRevision) return;
    this.lastFullscreenRevision = revision;
    try {
      const status = win.getWindowStatus();
      const enter = nativeApi.IsOhosPlayerWindowFullscreenRequested();
      if (enter === (status === window.WindowStatusType.FULL_SCREEN)) return;
      if (!enter && status !== window.WindowStatusType.FULL_SCREEN) return;
      if (enter) this.restoreStatus = status;
      this.transitionInFlight = true;
      const transition = enter ? win.maximize(window.MaximizePresentation.ENTER_IMMERSIVE) :
        this.restoreStatus === window.WindowStatusType.MAXIMIZE ?
          win.maximize(window.MaximizePresentation.EXIT_IMMERSIVE) : win.recover();
      transition.then(() => {
        if (!enter) this.restoreStatus = undefined;
      }).catch((error: Object) => {
        hilog.warn(0x4D46, 'MaFeiPlayerWindow', 'Unable to change player fullscreen: %{public}s',
          JSON.stringify(error));
      }).finally(() => {
        this.transitionInFlight = false;
        this.publishMetrics();
      });
    } catch (error) {
      hilog.warn(0x4D46, 'MaFeiPlayerWindow', 'Unable to query player fullscreen: %{public}s',
        JSON.stringify(error));
    }
  }

  private publishMetrics(): void {
    const win = this.playerWindow;
    if (!win) return;
    try {
      const context = win.getUIContext();
      const rect = win.getWindowProperties().windowRect;
      const system = win.getWindowAvoidArea(window.AvoidAreaType.TYPE_SYSTEM);
      const navigation = win.getWindowAvoidArea(window.AvoidAreaType.TYPE_NAVIGATION_INDICATOR);
      const cutout = win.getWindowAvoidArea(window.AvoidAreaType.TYPE_CUTOUT);
      const buttons = win.getTitleButtonRect();
      const fullscreen = win.getWindowStatus() === window.WindowStatusType.FULL_SCREEN;
      nativeApi.UpdateOhosPlayerWindowMetrics(
        context.px2vp(Math.max(0, system.leftRect.left + system.leftRect.width,
          cutout.leftRect.left + cutout.leftRect.width)),
        context.px2vp(Math.max(0, system.topRect.height, cutout.topRect.height)),
        context.px2vp(Math.max(0, system.rightRect.width > 0 ? rect.width - system.rightRect.left : 0,
          cutout.rightRect.width > 0 ? rect.width - cutout.rightRect.left : 0)),
        context.px2vp(Math.max(0, system.bottomRect.height, navigation.bottomRect.height, cutout.bottomRect.height)),
        fullscreen ? 0 : Math.max(0, context.px2vp(rect.width) - (buttons.right - buttons.width)),
        fullscreen ? 0 : Math.max(0, buttons.top + buttons.height),
        fullscreen
      );
    } catch (_) { /* Resizing can temporarily make the window metrics unavailable. */ }
  }

  private markClosing(): void {
    if (this.closingId !== 0) return;
    this.closingId = this.requestId;
    if (this.closingId > 0) nativeApi.CloseOhosPlayerWindow(this.closingId);
  }

  private finish(): void {
    this.markClosing();
    this.context.terminateSelf().catch((error: Object) => {
      hilog.warn(0x4D46, 'MaFeiPlayerWindow', 'Unable to close player window: %{public}s',
        JSON.stringify(error));
    });
  }

  private fail(error: Object): void {
    if (this.requestId > 0) nativeApi.FailOhosPlayerWindow(this.requestId);
    hilog.warn(0x4D46, 'MaFeiPlayerWindow', 'Player window initialization failed: %{public}s',
      JSON.stringify(error));
    this.finish();
  }

  onForeground(): void {
    setApplicationWindowForeground('player', true);
    this.context.eventHub.emit('onForeground');
  }

  onBackground(): void {
    setApplicationWindowForeground('player', false);
    this.context.eventHub.emit('onBackground');
  }

  onWindowStageWillDestroy(): void {
    this.markClosing();
  }

  onWindowStageDestroy(): void {
    if (this.timer !== undefined) clearInterval(this.timer);
    this.timer = undefined;
    const win = this.playerWindow;
    this.playerWindow = undefined;
    if (win) {
      try {
        win.off('avoidAreaChange', this.metricsChanged);
        win.off('windowSizeChange', this.metricsChanged);
        win.off('windowStatusDidChange', this.metricsChanged);
        win.off('windowTitleButtonRectChange', this.metricsChanged);
        win.off('windowWillClose', this.windowWillClose);
      } catch (_) { /* The native window may already be destroyed. */ }
    }
  }

  onDestroy(): void {
    this.markClosing();
    setApplicationWindowForeground('player', false);
    playerWindowDestroyed();
  }
}
