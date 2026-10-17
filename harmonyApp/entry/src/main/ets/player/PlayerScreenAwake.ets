import { window } from '@kit.ArkUI';
import { hilog } from '@kit.PerformanceAnalysisKit';

/** Window-wide requests outlive asynchronous writes, but never their video surface owners. */
export class PlayerScreenAwake {
  private readonly owners: Map<Object, () => Promise<window.Window | undefined>> = new Map();
  private appliedWindow: window.Window | undefined = undefined;
  private pending: boolean = false;

  update(owner: Object, enabled: boolean, resolveWindow: () => Promise<window.Window | undefined>): void {
    if (enabled) this.owners.set(owner, resolveWindow);
    else this.owners.delete(owner);
    if (this.pending) return;
    this.pending = true;
    void this.reconcile();
  }

  private async reconcile(): Promise<void> {
    try {
      while ((this.owners.size > 0) !== (this.appliedWindow !== undefined)) {
        if (this.owners.size > 0) {
          // All player surfaces belong to the same application window. Resolve
          // it only when acquiring; releasing must target that exact window.
          const owner = Array.from(this.owners.keys())[0];
          const resolveWindow = this.owners.get(owner);
          if (!resolveWindow) continue;
          const target = await resolveWindow();
          if (!this.owners.has(owner)) continue;
          if (!target) return;
          await target.setWindowKeepScreenOn(true);
          this.appliedWindow = target;
        } else {
          const target = this.appliedWindow;
          if (target) await target.setWindowKeepScreenOn(false);
          this.appliedWindow = undefined;
        }
        // A pause, background or close during an awaited write is reconciled
        // before this worker finishes, never left as a stale enabled flag.
      }
    } catch (_error) {
      hilog.warn(0x4D46, 'MaFeiPlayer', 'setWindowKeepScreenOn rejected');
    } finally {
      this.pending = false;
    }
  }
}

export const playerScreenAwake: PlayerScreenAwake = new PlayerScreenAwake();
