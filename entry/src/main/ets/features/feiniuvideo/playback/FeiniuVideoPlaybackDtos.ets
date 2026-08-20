/**
 * Feiniu Video playback-only wire DTOs.
 *
 * Copied as contiguous contracts from:
 * - fnOS/entry/src/main/ets/entity/FnMediaResp.ets:90-156
 * - fnOS/entry/src/main/ets/entity/FnMediaResp.ets:239-310
 * - fnOS/entry/src/main/ets/entity/FnMediaResp.ets:397-503
 * - fnOS/entry/src/main/ets/entity/FnMediaResp.ets:747-814
 *
 * Server field names deliberately remain unchanged. Shared player code must
 * consume only MediaPlaybackSource and never import these DTOs.
 */

export interface FnMediaPlayInfoReq {
  item_guid: string
  media_guid?: string
}

export interface FnMediaGuidReq {
  item_guid: string
}

export interface FnMediaPlayRecordReq {
  item_guid: string
  media_guid: string
  video_guid: string
  audio_guid: string
  subtitle_guid?: string
  resolution: string
  bitrate: number
  ts: number
  duration: number
  play_link?: string
}

export interface FnMediaPlayInfoItemCore {
  title?: string
  type?: string
  parent_title?: string
  tv_title?: string
  episode_number?: number
  season_number?: number
  duration?: number
}

export interface FnMediaPlayConfig {
  guid?: string
  skip_opening?: number
  skip_ending?: number
}

export interface FnMediaPlayInfoData {
  guid?: string
  media_guid?: string
  audio_guid?: string
  subtitle_guid?: string
  video_guid?: string
  direct_link_audio_index?: number
  ts?: number
  type?: string
  item?: FnMediaPlayInfoItemCore
  play_config?: FnMediaPlayConfig
}

export interface FnMediaPlayInfoResp {
  code: number
  msg: string
  data: FnMediaPlayInfoData
}

export interface FnMediaPlayPlayReq {
  media_guid: string
  video_guid: string
  video_encoder: string
  resolution: string
  bitrate: number
  startTimestamp: number
  audio_encoder: string
  audio_guid: string
  subtitle_guid: string
  channels: number
  forced_sdr: number
}

export interface FnMediaPlayPlayData {
  audio_guid?: string
  audio_index?: number
  hls_time?: number
  is_subtitle_external?: number
  media_guid?: string
  play_link?: string
  subtitle_guid?: string
  subtitle_index?: number
  subtitle_link?: string
  video_guid?: string
  video_index?: number
  non_fatal_errno?: number
  video_encoder?: string
  not_supported_resolution?: string
  supported_highest_resolution?: string
}

export interface FnMediaPlayPlayResp {
  code: number
  msg: string
  data: FnMediaPlayPlayData
}

export interface FnMediaStreamFileInfo {
  guid?: string
  path?: string
  file_name?: string
  size?: number
  timestamp?: number
  type?: number
  sort_num?: number
  can_play?: number
  play_error?: string
  create_time?: number
  update_time?: number
  file_birth_time?: number
  progress_thumb_hash_dir?: string
}

export interface FnMediaStreamVideoInfo {
  media_guid?: string
  guid?: string
  title?: string
  index?: number
  resolution_type?: string
  color_range_type?: string
  color_range?: string
  codec_name?: string
  codec_type?: string
  profile?: string
  width?: number
  height?: number
  coded_width?: number
  coded_height?: number
  display_aspect_ratio?: string
  pix_fmt?: string
  level?: string
  color_space?: string
  color_transfer?: string
  color_primaries?: string
  dv_profile?: number
  bits_per_raw_sample?: string
  wrapper?: string
  duration?: number
  bps?: number
  fps?: number | string
  frame_rate?: number | string
  avg_frame_rate?: string
  r_frame_rate?: string
  progressive?: number
  bit_depth?: number
  refs?: number
  reference_frames?: number
  rotation?: number
  is_bluray?: boolean
}

export interface FnMediaStreamAudioInfo {
  media_guid?: string
  guid?: string
  index?: number
  title?: string
  audio_type?: string
  codec_name?: string
  codec_type?: string
  language?: string
  channels?: number
  profile?: string
  sample_rate?: string
  is_default?: number
  channel_layout?: string
  duration?: number
  bps?: number
  is_fake?: boolean
}

export interface FnMediaStreamSubtitleInfo {
  media_guid?: string
  guid?: string
  index?: number
  title?: string
  codec_name?: string
  codec_type?: string
  format?: string
  language?: string
  forced?: number
  is_default?: number
  is_external?: number
  trim_id?: string
  source_id?: string
  Source?: string
  create_time?: number
  update_time?: number
  extra_file?: number
  is_bitmap?: number
  file_size?: number
}

export interface FnMediaStreamListResp {
  code: number
  msg: string
  data: FnMediaStreamListData
}

export interface FnMediaStreamListData {
  files?: FnMediaStreamFileInfo[]
  video_streams?: FnMediaStreamVideoInfo[]
  audio_streams?: FnMediaStreamAudioInfo[]
  subtitle_streams?: FnMediaStreamSubtitleInfo[]
}

export interface FnMediaQualityReq {
  resolution: string
  bitrate: number
}

export interface FnMediaPReq {
  req?: string
  reqid?: string
  playLink: string
  quality?: FnMediaQualityReq
  startTimestamp?: number
  clearCache?: boolean
  audioEncoder?: string
  channels?: number
  audioIndex?: number
  subtitleIndex?: number
}

export interface FnMediaResetQualityData {
  hlsTime?: number
  reqid?: string
  result?: string
  updateM3u8?: boolean
}

export interface FnMediaResetQualityResp {
  code: number
  msg: string
  data: FnMediaResetQualityData
}

export interface FnMediaMutationResp {
  code: number
  msg: string
}
