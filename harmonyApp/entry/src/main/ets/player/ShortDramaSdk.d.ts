interface DramaSnapshot {
  phase: string;
  firstFrame: boolean;
  positionMs: number;
  durationMs: number;
}

interface DramaSurfaceSize {
  id: string;
  width: number;
  height: number;
}

interface DramaLoadOptions {
  startPositionMs: number;
  playWhenReady: boolean;
  httpHeaders: string[];
  allowInsecureTls: boolean;
}

export interface DramaPlayer {
  load(source: string, surface: DramaSurfaceSize, options: DramaLoadOptions): boolean;
  resize(width: number, height: number): boolean;
  play(): boolean;
  pause(): boolean;
  seekTo(positionMs: number): boolean;
  setPlaybackSpeed?(speed: number): boolean;
  stop(): void;
  release(): void;
  snapshot(): DramaSnapshot | undefined;
}

export function hasShortDramaSdk(): boolean;
export function hasShortDramaAdvancedSource(): boolean;
export function createShortDramaPlayer(): DramaPlayer;
