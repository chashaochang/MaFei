import { common, wantAgent } from '@kit.AbilityKit';
import { backgroundTaskManager } from '@kit.BackgroundTasksKit';
import { hilog } from '@kit.PerformanceAnalysisKit';

/** The same audioPlayback continuous task as the legacy music controller. */
export class PlayerBackgroundAudio {
  private context: common.UIAbilityContext | undefined = undefined;
  private generation: number = 0;
  private wanted: boolean = false;
  private running: boolean = false;
  private granted: boolean = false;
  private operations: Promise<void> = Promise.resolve();
  private onGrant: ((active: boolean) => void) | undefined = undefined;

  get active(): boolean { return this.granted; }

  bind(context: common.UIAbilityContext, onGrant: (active: boolean) => void): void {
    this.close();
    this.context = context;
    this.onGrant = onGrant;
  }

  update(audioOnly: boolean, phase: string): void {
    const wanted = audioOnly && (phase === 'playing' || phase === 'buffering');
    if (wanted === this.wanted) return;
    this.wanted = wanted;
    this.reconcile();
  }

  close(): void {
    ++this.generation;
    this.wanted = false;
    this.publish(false);
    this.onGrant = undefined;
    this.reconcile();
  }

  private publish(active: boolean): void {
    if (this.granted === active) return;
    this.granted = active;
    this.onGrant?.(active);
  }

  private reconcile(): void {
    const generation = this.generation;
    const context = this.context;
    this.operations = this.operations.then(async (): Promise<void> => {
      // Retiring a source must stop its task before a new source can own it.
      if (this.running && (!this.wanted || generation !== this.generation)) {
        if (context) await backgroundTaskManager.stopBackgroundRunning(context);
        this.running = false;
        this.publish(false);
      }
      if (!context || generation !== this.generation || !this.wanted || this.running) return;
      const info: wantAgent.WantAgentInfo = {
        wants: [{ bundleName: context.abilityInfo.bundleName, abilityName: context.abilityInfo.name }],
        operationType: wantAgent.OperationType.START_ABILITY,
        requestCode: 0,
        wantAgentFlags: [wantAgent.WantAgentFlags.UPDATE_PRESENT_FLAG]
      };
      const agent = await wantAgent.getWantAgent(info);
      if (generation !== this.generation || !this.wanted) return;
      await backgroundTaskManager.startBackgroundRunning(context,
        backgroundTaskManager.BackgroundMode.AUDIO_PLAYBACK, agent);
      this.running = true;
      if (generation !== this.generation || !this.wanted) {
        await backgroundTaskManager.stopBackgroundRunning(context);
        this.running = false;
      } else this.publish(true);
    }).catch((): void => {
      this.publish(false);
      hilog.warn(0x4D46, 'MaFeiPlayer', 'audio background task operation failed');
    });
  }
}
