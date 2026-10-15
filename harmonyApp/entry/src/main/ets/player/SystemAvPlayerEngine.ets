import { media } from '@kit.MediaKit';
import { BusinessError } from '@kit.BasicServicesKit';

export interface SystemEngineSource {
  handle: number;
  url: string;
  headers: string[];
  subtitleUrl: string;
  positionMs: number;
  speed: number;
}

export interface SystemEngineTrack {
  id: number;
  kind: string;
  label: string;
  language?: string;
  selected: boolean;
}

export interface SystemEngineSnapshot {
  handle: number;
  event: string;
  positionMs: number;
  durationMs: number;
  bufferedMs: number;
  tracks: SystemEngineTrack[];
}

/** Native engine only. Queue, provider reporting and visible chrome remain in CMP. */
export class SystemAvPlayerEngine {
  private player: media.AVPlayer | undefined = undefined;
  private epoch: number = 0;
  private source: SystemEngineSource | undefined = undefined;
  private surfaceId: string = '';
  private preparing: boolean = false;
  private prepared: boolean = false;
  private lastProgressAt: number = 0;
  private bufferedMs: number = 0;
  private positionMs: number = 0;
  private speedValue: number = 1;
  private playing: boolean = false;
  private fills: boolean = false;
  private tracks: SystemEngineTrack[] = [];
  private readonly emit: (snapshot: SystemEngineSnapshot) => void;
  private readonly fallback: (positionMs: number, speed: number, playing: boolean) => void;

  constructor(emit: (snapshot: SystemEngineSnapshot) => void,
    fallback: (positionMs: number, speed: number, playing: boolean) => void) {
    this.emit = emit;
    this.fallback = fallback;
  }

  load(source: SystemEngineSource): void {
    this.release();
    const epoch = this.epoch;
    this.source = source;
    this.positionMs = Math.max(0, source.positionMs);
    this.speedValue = source.speed > 0 ? source.speed : 1;
    media.createAVPlayer().then((player: media.AVPlayer): void => {
      if (epoch !== this.epoch) { void player.release().catch((): void => {}); return; }
      this.player = player;
      player.on('stateChange', (state: media.AVPlayerState): void => {
        if (!this.owned(player, epoch)) return;
        switch (state) {
          case 'initialized': this.prepareIfReady(player, epoch); break;
          case 'prepared':
            this.prepared = true;
            this.fit(this.fills);
            this.speed(this.speedValue);
            if (source.subtitleUrl.length > 0) {
              void player.addSubtitleFromUrl(source.subtitleUrl).catch((): void => {});
            }
            this.publish('ready');
            void this.readTracks(player, epoch).catch((): void => {});
            break;
          case 'playing': this.playing = true; this.publish('playing'); break;
          case 'paused': this.playing = false; this.publish('paused'); break;
          case 'completed': this.playing = false; this.publish('ended'); break;
          case 'error': this.fail(player, epoch); break;
        }
      });
      player.on('error', (_error: BusinessError): void => this.fail(player, epoch));
      player.on('timeUpdate', (position: number): void => {
        if (!this.owned(player, epoch)) return;
        this.positionMs = Math.max(0, position);
        if (Date.now() - this.lastProgressAt >= 250) { this.lastProgressAt = Date.now(); this.publish('progress'); }
      });
      player.on('durationUpdate', (_duration: number): void => { if (this.owned(player, epoch)) this.publish('progress'); });
      player.on('bufferingUpdate', (type: media.BufferingInfoType, value: number): void => {
        if (!this.owned(player, epoch)) return;
        if (type === media.BufferingInfoType.BUFFERING_START) this.publish('buffering');
        else if (type === media.BufferingInfoType.BUFFERING_END) this.publish(this.playing ? 'playing' : 'paused');
        else if (type === media.BufferingInfoType.CACHED_DURATION) this.bufferedMs = this.positionMs + Math.max(0, value);
      });
      const headers: Record<string, string> = {};
      source.headers.forEach((header: string): void => {
        const separator = header.indexOf(':');
        if (separator > 0) headers[header.substring(0, separator).trim()] = header.substring(separator + 1).trim();
      });
      void player.setMediaSource(media.createMediaSourceWithUrl(source.url, headers))
        .catch((): void => this.fail(player, epoch));
    }).catch((): void => {
      if (epoch === this.epoch) this.fail(undefined, epoch);
    });
  }

  surface(id: string): void {
    this.surfaceId = id;
    if (this.player) this.prepareIfReady(this.player, this.epoch);
  }

  get videoWidthPx(): number { return this.prepared ? Math.max(0, this.player?.width ?? 0) : 0; }
  get videoHeightPx(): number { return this.prepared ? Math.max(0, this.player?.height ?? 0) : 0; }

  play(positionMs: number): void {
    const player = this.player;
    if (!player || !this.prepared) return;
    this.playing = true;
    if (positionMs > 0) this.seek(positionMs);
    const epoch = this.epoch;
    void player.play().catch((): void => this.fail(player, epoch));
  }

  pause(): void {
    const player = this.player;
    if (!player || !this.prepared) return;
    this.playing = false;
    const epoch = this.epoch;
    void player.pause().catch((): void => this.fail(player, epoch));
  }

  seek(position: number): void {
    const player = this.player;
    if (!player || !this.prepared) return;
    this.positionMs = Math.max(0, position);
    try { player.seek(this.positionMs, media.SeekMode.SEEK_CLOSEST); } catch (_) { this.fail(player, this.epoch); }
  }

  speed(value: number): void {
    if (value <= 0 || !Number.isFinite(value)) return;
    const previous = this.speedValue;
    this.speedValue = value;
    if (this.player && this.prepared && (value !== 1 || previous !== 1)) {
      try { this.player.setPlaybackRate(value); } catch (_) { this.fail(this.player, this.epoch); }
    }
  }

  fit(fill: boolean): void {
    this.fills = fill;
    if (this.player && this.prepared) {
      try {
        this.player.videoScaleType = fill ? media.VideoScaleType.VIDEO_SCALE_TYPE_FIT_CROP : media.VideoScaleType.VIDEO_SCALE_TYPE_SCALED_ASPECT;
      } catch (_) { this.fail(this.player, this.epoch); }
    }
  }

  select(kind: string, index: number): void {
    const player = this.player;
    if (!player || !this.prepared) return;
    const epoch = this.epoch;
    const operation = index >= 0 ? player.selectTrack(index) :
      this.tracks.filter((track: SystemEngineTrack): boolean => track.kind === kind && track.selected)
        .reduce((chain: Promise<void>, track: SystemEngineTrack): Promise<void> =>
          chain.then((): Promise<void> => player.deselectTrack(track.id)), Promise.resolve());
    void operation.then((): Promise<void> => this.readTracks(player, epoch)).catch((): void => this.fail(player, epoch));
  }

  release(): void {
    this.epoch++;
    const player = this.player;
    this.player = undefined; this.source = undefined;
    this.prepared = false; this.preparing = false; this.playing = false;
    this.bufferedMs = 0; this.tracks = [];
    if (player) void player.release().catch((): void => {});
  }

  private owned(player: media.AVPlayer, epoch: number): boolean {
    return epoch === this.epoch && player === this.player;
  }

  private prepareIfReady(player: media.AVPlayer, epoch: number): void {
    if (!this.owned(player, epoch) || player.state !== 'initialized' || this.surfaceId.length === 0 || this.preparing) return;
    this.preparing = true;
    player.surfaceId = this.surfaceId;
    void player.prepare().catch((): void => this.fail(player, epoch));
  }

  private fail(player: media.AVPlayer | undefined, epoch: number): void {
    if (epoch !== this.epoch || (player && player !== this.player) || !this.source) return;
    const position = this.positionMs, speed = this.speedValue, playing = this.playing;
    this.release();
    this.fallback(position, speed, playing);
  }

  private publish(event: string): void {
    if (!this.source) return;
    const snapshot: SystemEngineSnapshot = { handle: this.source.handle, event: event,
      positionMs: this.positionMs, durationMs: Math.max(0, this.player?.duration ?? 0),
      bufferedMs: this.bufferedMs, tracks: event === 'tracks' ? this.tracks.slice() : [] };
    this.emit(snapshot);
  }

  private async readTracks(player: media.AVPlayer, epoch: number): Promise<void> {
    const descriptions = await player.getTrackDescription();
    if (!this.owned(player, epoch)) return;
    const selected = await player.getSelectedTracks();
    if (!this.owned(player, epoch)) return;
    this.tracks = descriptions.filter((value: media.MediaDescription): boolean =>
      Number(value['track_type']) === 0 || Number(value['track_type']) === 2).map((value: media.MediaDescription): SystemEngineTrack => {
      const id = Number(value['track_index']);
      const kind = Number(value['track_type']) === 0 ? 'audio' : 'subtitle';
      const language = value['language'] ? String(value['language']) : '';
      const codec = value['codec_mime'] ? String(value['codec_mime']) : '';
      const track: SystemEngineTrack = { id: id, kind: kind, label: [language, codec].filter((part: string): boolean => part.length > 0).join(' · ') ||
        `${kind === 'audio' ? '音轨' : '字幕'} ${id + 1}`, language: language, selected: selected.includes(id) };
      return track;
    });
    this.publish('tracks');
  }
}
