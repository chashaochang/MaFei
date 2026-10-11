import { image } from '@kit.ImageKit';

export type VideoBackdropSink = (handle: number, revision: number, width: number,
  height: number, alphaType: number, pixels: ArrayBuffer) => boolean;

/** A single in-flight, bounded live frame for Compose material sampling, never for video presentation. */
export class PlayerVideoBackdropCapture {
  private surfaceId: string = '';
  private handle: number = 0;
  private revision: number = 0;
  private enabled: boolean = false;
  private epoch: number = 0;
  private busy: boolean = false;
  private timer: number = -1;
  private readonly framePeriodMs: number = 1000 / 30;

  private readonly sink: VideoBackdropSink;
  constructor(sink: VideoBackdropSink) { this.sink = sink; }

  bind(surfaceId: string, handle: number, revision: number, enabled: boolean): void {
    const active = enabled && surfaceId.length > 0 && surfaceId !== '0' && handle > 0;
    if (this.surfaceId === surfaceId && this.handle === handle && this.revision === revision && this.enabled === active) return;
    this.surfaceId = surfaceId;
    this.handle = handle;
    this.revision = revision;
    this.enabled = active;
    this.epoch++;
    this.clearTimer();
    if (active && !this.busy) void this.capture();
  }

  stop(): void {
    this.enabled = false;
    this.epoch++;
    this.clearTimer();
  }

  private clearTimer(): void {
    if (this.timer !== -1) clearTimeout(this.timer);
    this.timer = -1;
  }

  private async capture(): Promise<void> {
    if (!this.enabled || this.busy) return;
    this.busy = true;
    const epoch = this.epoch;
    const surfaceId = this.surfaceId;
    const handle = this.handle;
    const revision = this.revision;
    const startedAt = Date.now();
    let frame: image.PixelMap | undefined = undefined;
    let retryDelay = 0;
    try {
      frame = await image.createPixelMapFromSurface(surfaceId);
      if (!this.enabled || epoch !== this.epoch) return;
      const original = await frame.getImageInfo();
      if (original.isHdr) await frame.toSdr();
      const scale = Math.min(1, 256 / Math.max(original.size.width, original.size.height));
      if (!(scale > 0)) return;
      if (scale < 1) await frame.scale(scale, scale);
      const info = await frame.getImageInfo();
      const width = info.size.width;
      const height = info.size.height;
      if (width < 1 || height < 1 || width > 256 || height > 256) return;
      const pixels = new ArrayBuffer(width * height * 4);
      const area: image.PositionArea = {
        pixels: pixels, offset: 0, stride: width * 4,
        region: { x: 0, y: 0, size: { width: width, height: height } }
      };
      // This API explicitly normalizes the result to BGRA_8888 (including HDR surfaces).
      await frame.readPixels(area);
      if (this.enabled && epoch === this.epoch) this.sink(handle, revision, width, height, info.alphaType, pixels);
    } catch (_) {
      // First-frame/surface capture may be temporarily unavailable. Back off;
      // a material failure must never become a playback error or a log storm.
      retryDelay = 700;
    } finally {
      if (frame) {
        try { await frame.release(); } catch (_) {}
      }
      this.busy = false;
      if (this.enabled) {
        // Budget from capture start, including readback and release. Slow capture
        // immediately schedules the next sample; the single-flight guard drops
        // missed opportunities rather than accumulating stale frames.
        const elapsed = Math.max(0, Date.now() - startedAt);
        const interval = retryDelay > 0 ? retryDelay : Math.max(0, this.framePeriodMs - elapsed);
        this.timer = setTimeout((): void => {
          this.timer = -1;
          void this.capture();
        }, epoch === this.epoch ? interval : 0);
      }
    }
  }
}
