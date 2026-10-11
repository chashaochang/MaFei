import { common } from '@kit.AbilityKit';
import { PiPWindow, window } from '@kit.ArkUI';
import { deviceInfo } from '@kit.BasicServicesKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { promptAction } from '@kit.ArkUI';

export interface PlayerPiPSnapshot {
  widthPx: number;
  heightPx: number;
  playing: boolean;
}

/** Only manages the native window. Playback controls belong to the CMP controller. */
export class PlayerPictureInPicture {
  private epoch: number = 0;
  private controller: PiPWindow.PiPController | undefined = undefined;
  private operations: Promise<void> = Promise.resolve();
  private snapshot: PlayerPiPSnapshot = { widthPx: 0, heightPx: 0, playing: false };
  private appliedWidth: number = 0;
  private appliedHeight: number = 0;
  private appliedPlaying: boolean | undefined = undefined;
  private restoring: boolean = false;
  active: boolean = false;

  start(context: common.UIAbilityContext, surface: XComponentController,
    onState: (active: boolean) => void, onAction: (action: string) => void,
    snapshot: PlayerPiPSnapshot): void {
    this.update(snapshot);
    const epoch = this.epoch;
    this.operations = this.operations.then(async (): Promise<void> => {
      if (epoch !== this.epoch) return;
      try {
        if (!canIUse('SystemCapability.Window.SessionManager') || !PiPWindow.isPiPEnabled()) {
          onState(false);
          promptAction.showToast({ message: '暂时无法进入画中画' });
          return;
        }
        if (!this.controller) {
          if (this.snapshot.widthPx <= 0 || this.snapshot.heightPx <= 0) {
            onState(false);
            promptAction.showToast({ message: '暂时无法进入画中画' });
            return;
          }
          const configuration: PiPWindow.PiPConfiguration = { context: context, componentController: surface,
            templateType: PiPWindow.PiPTemplateType.VIDEO_PLAY,
            contentWidth: this.snapshot.widthPx, contentHeight: this.snapshot.heightPx };
          const controller = await PiPWindow.create(configuration);
          if (epoch !== this.epoch) {
            try { await controller.stopPiP(); } catch (_error) {}
            return;
          }
          this.controller = controller;
          this.appliedWidth = 0;
          this.appliedHeight = 0;
          this.appliedPlaying = undefined;
          controller.on('stateChange', (state: PiPWindow.PiPState, _reason: string): void => {
            if (epoch !== this.epoch || controller !== this.controller) return;
            hilog.info(0x4D46, 'MaFeiPlayer', 'picture-in-picture state=%{public}d', state);
            if (state === PiPWindow.PiPState.STARTED && !this.active) {
              this.active = true; this.restoring = false; this.sync(); onState(true);
              // startPiP transfers the XComponent, not the main window. Keep
              // the CMP route mounted, but leave it in the background.
              void this.backgroundMainWindow(context, controller, epoch);
            }
            if (state === PiPWindow.PiPState.ABOUT_TO_RESTORE) this.restoring = true;
            if (state === PiPWindow.PiPState.STOPPED || state === PiPWindow.PiPState.ERROR) {
              if (state === PiPWindow.PiPState.STOPPED && this.active && !this.restoring) onAction('pause');
              this.active = false; onState(false);
              this.restoring = false;
            }
          });
          controller.on('controlPanelActionEvent',
            (action: PiPWindow.PiPActionEventType, status?: number): void => {
              if (epoch === this.epoch && controller === this.controller && action === 'playbackStateChanged' &&
                (status === PiPWindow.PiPControlStatus.PAUSE || status === PiPWindow.PiPControlStatus.PLAY)) {
                onAction(status === 0 ? 'pause' : 'play');
              }
            });
        }
        this.sync();
        if (!this.active) await this.controller.startPiP();
      } catch (_error) {
        if (epoch !== this.epoch) return;
        this.active = false; onState(false);
        hilog.warn(0x4D46, 'MaFeiPlayer', 'picture-in-picture unavailable');
        promptAction.showToast({ message: '暂时无法进入画中画' });
      }
    });
  }

  private async backgroundMainWindow(context: common.UIAbilityContext,
    controller: PiPWindow.PiPController, epoch: number): Promise<void> {
    try {
      if (deviceInfo.deviceType === '2in1') {
        // moveAbilityToBackground is not supported on PC/2-in-1.
        const mainWindow = await window.getLastWindow(context);
        if (epoch !== this.epoch || controller !== this.controller || !this.active || this.restoring) return;
        await mainWindow.minimize();
      } else {
        await context.moveAbilityToBackground();
      }
      hilog.info(0x4D46, 'MaFeiPlayer', 'picture-in-picture source window backgrounded');
    } catch (_error) {
      if (epoch !== this.epoch || controller !== this.controller || !this.active || this.restoring) return;
      hilog.warn(0x4D46, 'MaFeiPlayer', 'Unable to background the picture-in-picture source window');
      // A failed handoff must return the video surface instead of leaving a
      // black player behind the small window. This is a restore, not a close.
      this.restoreToPlayer();
      promptAction.showToast({ message: '暂时无法进入画中画' });
    }
  }

  /** Also used when the user opens the app directly rather than the PiP restore button. */
  restoreToPlayer(): void {
    const controller = this.controller;
    if (!controller || !this.active || this.restoring) return;
    this.restoring = true;
    hilog.info(0x4D46, 'MaFeiPlayer', 'picture-in-picture returning to source window');
    const epoch = this.epoch;
    this.operations = this.operations.then(async (): Promise<void> => {
      if (epoch !== this.epoch || controller !== this.controller || !this.active) return;
      try {
        await controller.stopPiP();
      } catch (_error) {
        if (epoch !== this.epoch || controller !== this.controller || !this.active) return;
        this.restoring = false;
        hilog.warn(0x4D46, 'MaFeiPlayer', 'Unable to restore the picture-in-picture surface');
        promptAction.showToast({ message: '恢复播放窗口失败，请重试' });
      }
    });
  }

  update(snapshot: PlayerPiPSnapshot): void {
    this.snapshot = { widthPx: Number.isFinite(snapshot.widthPx) ? Math.max(0, Math.round(snapshot.widthPx)) : 0,
      heightPx: Number.isFinite(snapshot.heightPx) ? Math.max(0, Math.round(snapshot.heightPx)) : 0,
      playing: snapshot.playing };
    this.sync();
  }

  private sync(): void {
    const controller = this.controller;
    if (!controller) return;
    const snapshot = this.snapshot;
    if (snapshot.widthPx > 0 && snapshot.heightPx > 0 &&
      (snapshot.widthPx !== this.appliedWidth || snapshot.heightPx !== this.appliedHeight)) {
      try {
        controller.updateContentSize(snapshot.widthPx, snapshot.heightPx);
        this.appliedWidth = snapshot.widthPx;
        this.appliedHeight = snapshot.heightPx;
      } catch (_error) { hilog.warn(0x4D46, 'MaFeiPlayer', 'Unable to update picture-in-picture dimensions'); }
    }
    if (snapshot.playing !== this.appliedPlaying) {
      try {
        controller.updatePiPControlStatus(PiPWindow.PiPControlType.VIDEO_PLAY_PAUSE,
          snapshot.playing ? PiPWindow.PiPControlStatus.PLAY : PiPWindow.PiPControlStatus.PAUSE);
        this.appliedPlaying = snapshot.playing;
      } catch (_error) { hilog.warn(0x4D46, 'MaFeiPlayer', 'Unable to update picture-in-picture playback status'); }
    }
  }

  close(): void {
    this.epoch += 1;
    const controller = this.controller;
    this.controller = undefined;
    this.active = false;
    this.restoring = false;
    this.appliedWidth = 0;
    this.appliedHeight = 0;
    this.appliedPlaying = undefined;
    if (!controller) return;
    controller.off('stateChange');
    controller.off('controlPanelActionEvent');
    this.operations = this.operations.then(async (): Promise<void> => {
      try { await controller.stopPiP(); } catch (_error) {}
    });
  }
}
