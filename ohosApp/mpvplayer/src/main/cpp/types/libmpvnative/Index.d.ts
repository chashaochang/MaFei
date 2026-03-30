declare module 'libmpvnative.so' {
  export interface MpvTrackInfo {
    id: number;
    type?: string;
    title?: string;
    lang?: string;
    codec?: string;
    external?: boolean;
    selected?: boolean;
  }

  export interface MpvSubtitleStyle {
    fontSize?: number;
    color?: string;
    position?: string;
    borderWidth?: number;
    alpha?: number;
  }

  export interface MpvNetworkSpeedInfo {
    speed?: number;
    unit?: string;
  }

  export const setHwdecMode: (mode: number) => void;
  export const getHwdecMode: () => number;
  export const setDecodeType: (type: number) => void;
  export const getDecodeType: () => number;
  export const setLogLevel: (level: string) => void;
  export const setAutoBufferFallback: (enabled: boolean) => void;
  export const getAutoBufferFallback: () => boolean;
  export const setCacheSize: (sizeMb: number) => void;
  export const getCacheSize: () => number;
  export const create: () => number | null;
  export const destroy: (mpvHandle: number) => void;
  export const reset: (mpvHandle: number) => void;
  export const command: (mpvHandle: number, args: Array<string>) => void;
  export const isInitialized: () => boolean;
  export const loadVideo: (mpvHandle: number, url: string, startPosition?: number) => void;
  export const seek: (mpvHandle: number, seconds: number, exact: boolean) => void;
  export const pause: (playerId: number) => boolean;
  export const play: (playerId: number) => boolean;
  export const setSpeed: (mpvHandle: number, speed: number) => void;
  export const getCurrentPosition: (mpvHandle: number) => number;
  export const getDuration: (mpvHandle: number) => number;
  export const getCacheDuration: (mpvHandle: number) => number;
  export const getSubtitleTracks: (mpvHandle: number) => Array<MpvTrackInfo>;
  export const selectSubtitle: (mpvHandle: number, trackId: number) => void;
  export const setSubtitleStyle: (mpvHandle: number, style: MpvSubtitleStyle) => void;
  export const getCurrentSubtitleTrack: (mpvHandle: number) => number;
  export const getAudioTracks: (mpvHandle: number) => Array<MpvTrackInfo>;
  export const selectAudio: (mpvHandle: number, trackId: number) => void;
  export const getCurrentAudioTrack: (mpvHandle: number) => number;
  export const getHardwareDecoder: (mpvHandle: number) => string;
  export const getVideoWidth: (mpvHandle: number) => number;
  export const getVideoHeight: (mpvHandle: number) => number;
  export const setKeepAspect: (mpvHandle: number, keepAspect: boolean) => void;
  export const getNetworkSpeed: (mpvHandle: number) => MpvNetworkSpeedInfo;
  export const setOnInitializedCallback: (callback: () => void) => void;
  export const setOnEndFileCallback: (callback: (reason: number, error: string) => void) => void;
  export const setOnDurationChangedCallback: (callback: (durationMs: number) => void) => void;
  export const setOnTrackListChangedCallback: (callback: () => void) => void;
  export const setOnHwdecChangedCallback: (callback: (decoder: string) => void) => void;
  export const setOnPausedForCacheCallback: (callback: (paused: boolean) => void) => void;
  export const setOnLoadingCallback: (callback: (loading: boolean) => void) => void;
  export const setOnStartFileCallback: (callback: () => void) => void;
  export const setOnFileLoadedCallback: (callback: () => void) => void;
  export const setOnLogMessageCallback: (callback: (level: string, prefix: string, text: string) => void) => void;
}
