export interface FnMediaOauthConfig {
  app_id?: string;
  url?: string;
}

export interface FnMediaSysConfigData {
  nas_oauth?: FnMediaOauthConfig;
  server_name?: string;
}

export interface FnMediaSysConfigResp {
  code: number;
  msg: string;
  data: FnMediaSysConfigData;
}

export interface FnMediaAuthReq {
  source: string;
  code: string;
}

export interface FnMediaAuthTokenData {
  token?: string;
}

export interface FnMediaAuthResp {
  code: number;
  msg: string;
  data?: FnMediaAuthTokenData;
  token?: string;
}

export interface FnMediaOauthAuthorizeReq {
  token: string;
  client_id: string;
  redirect_uri: string;
  state: string;
  response_type: string;
}

export interface FnMediaOauthAuthorizeData {
  code?: string;
}

export interface FnMediaOauthAuthorizeResp {
  code: number;
  msg: string;
  data?: FnMediaOauthAuthorizeData;
}

export interface FnMediaLoginReq {
  username: string;
  password: string;
  app_name: string;
}

export interface FnMediaLoginResp {
  code: number;
  msg: string;
  data?: FnMediaAuthTokenData;
  token?: string;
}

export interface FnMediaUserInfoData {
  avatar?: string;
  nickname?: string;
  username?: string;
  user_id?: string;
  sources?: FnMediaUserSource[];
}

export interface FnMediaUserSource {
  source?: string;
  source_id?: string;
  source_name?: string;
}

export interface FnMediaUserInfoResp {
  code: number;
  msg: string;
  data?: FnMediaUserInfoData;
}

export interface FnMediaListResp<T> {
  code: number;
  msg: string;
  data: T;
}

export interface FnMediaPlayInfoReq {
  item_guid: string;
  media_guid?: string;
}

export interface FnMediaGuidReq {
  item_guid: string;
}

export interface FnMediaPlayRecordReq {
  item_guid: string;
  media_guid: string;
  video_guid: string;
  audio_guid: string;
  subtitle_guid?: string;
  resolution: string;
  bitrate: number;
  ts: number;
  duration: number;
  play_link?: string;
}

export interface FnMediaStreamReqHeader {
  "User-Agent": string[];
}

export interface FnMediaStreamReq {
  media_guid: string;
  ip: string;
  header: FnMediaStreamReqHeader;
  level: number;
}

export interface FnMediaStreamQualityInfo {
  bitrate?: number;
  resolution?: string;
  progressive?: boolean;
  is_m3u8?: boolean;
}

export interface FnMediaStreamResp {
  code: number;
  msg: string;
  data?: FnMediaStreamData;
}

export interface FnMediaStreamData {
  file_stream?: FnMediaStreamFileInfo;
  video_stream?: FnMediaStreamVideoInfo;
  audio_streams?: FnMediaStreamAudioInfo[];
  qualities?: FnMediaStreamQualityInfo[];
  subtitle_streams?: FnMediaStreamSubtitleInfo[];
}

export interface FnMediaSubtitleSearchReq {
  lan: string;
  media_guid: string;
}

export interface FnMediaSubtitleDownloadReq {
  media_guid: string;
  trim_id: string;
  sync_download: number;
}

export interface FnMediaSubtitleMarkReq {
  media_guid: string;
  filepaths: string[];
}

export interface FnMediaItemListTags {
  genres?: number;
  resolution?: string;
  color_range?: string;
  locate?: string;
  decade?: string;
  recognition_status?: string;
  watched?: string;
  audio_type?: string;
  type: string[];
}

export interface FnMediaItemListReq {
  ancestor_guid?: string;
  exclude_grouped_video?: number;
  sort_type?: string;
  sort_column?: string;
  page_size?: number;
  page?: number;
  tags: FnMediaItemListTags;
}

export interface FnMediaLibraryItem {
  guid: string;
  title: string;
  posters?: string[];
  category?: string;
  view_type?: number;
}

export interface FnMediaPlayStreamInfo {
  progress?: number;
  current_time?: number;
}

export interface FnMediaPlayItem {
  guid: string;
  title: string;
  type: string;
  poster?: string;
  media_guid?: string;
  video_guid?: string;
  audio_guid?: string;
  subtitle_guid?: string;
  parent_guid?: string;
  parent_title?: string;
  ancestor_guid?: string;
  ancestor_name?: string;
  ancestor_category?: string;
  tv_title?: string;
  season_number?: number;
  episode_number?: number;
  status?: string;
  is_favorite?: number;
  watched?: number;
  watched_ts?: number;
  ts?: number;
  duration?: number;
  file_name?: string;
  media_stream?: FnMediaPlayStreamInfo;
}

export interface FnMediaRunningTask {
  guid?: string;
  type?: string;
  ancestor?: string;
  ancestor_name?: string;
  ancestor_category?: string;
  start_time?: number;
  end_time?: number;
  status?: number;
  total_count?: number;
  success_count?: number;
  failed_count?: number;
  finished_count?: number;
  need_user_check?: boolean;
  cancellable?: boolean;
}

export interface FnMediaPlayInfoItemCore {
  title?: string;
  type?: string;
  parent_title?: string;
  tv_title?: string;
  episode_number?: number;
  season_number?: number;
  duration?: number;
}

export interface FnMediaPlayConfig {
  guid?: string;
  skip_opening?: number;
  skip_ending?: number;
}

export interface FnMediaPlayInfoData {
  guid?: string;
  media_guid?: string;
  audio_guid?: string;
  subtitle_guid?: string;
  video_guid?: string;
  direct_link_audio_index?: number;
  ts?: number;
  type?: string;
  item?: FnMediaPlayInfoItemCore;
  play_config?: FnMediaPlayConfig;
}

export interface FnMediaPlayInfoResp {
  code: number;
  msg: string;
  data: FnMediaPlayInfoData;
}

export interface FnMediaPlayPlayReq {
  media_guid: string;
  video_guid: string;
  video_encoder: string;
  resolution: string;
  bitrate: number;
  startTimestamp: number;
  audio_encoder: string;
  audio_guid: string;
  subtitle_guid: string;
  channels: number;
  forced_sdr: number;
}

export interface FnMediaPlayPlayData {
  audio_guid?: string;
  audio_index?: number;
  hls_time?: number;
  is_subtitle_external?: number;
  media_guid?: string;
  play_link?: string;
  subtitle_guid?: string;
  subtitle_index?: number;
  subtitle_link?: string;
  video_guid?: string;
  video_index?: number;
  non_fatal_errno?: number;
  video_encoder?: string;
  not_supported_resolution?: string;
  supported_highest_resolution?: string;
}

export interface FnMediaPlayPlayResp {
  code: number;
  msg: string;
  data: FnMediaPlayPlayData;
}

export interface FnMediaItemStream {
  resolutions?: string[];
  audio_type?: string;
  color_range_type?: string;
  progress?: number;
  current_time?: number;
}

export interface FnMediaItem {
  guid: string;
  title: string;
  type?: string;
  poster?: string;
  can_play?: number;
  parent_guid?: string;
  parent_title?: string;
  ancestor_guid?: string;
  ancestor_name?: string;
  ancestor_category?: string;
  tv_title?: string;
  watched?: number;
  watched_ts?: number;
  is_favorite?: number;
  overview?: string;
  status?: string;
  vote_average?: string;
  ts?: number;
  duration?: number;
  runtime?: number;
  season_number?: number;
  episode_number?: number;
  number_of_seasons?: number;
  number_of_episodes?: number;
  local_number_of_seasons?: number;
  local_number_of_episodes?: number;
  single_child_guid?: string;
  play_item_guid?: string;
  media_guid?: string;
  video_guid?: string;
  audio_guid?: string;
  subtitle_guid?: string;
  file_name?: string;
  release_date?: string;
  air_date?: string;
  first_air_date?: string;
  last_air_date?: string;
  genres?: number[];
  media_stream?: FnMediaItemStream;
}

export interface FnMediaItemDetailResp extends FnMediaItem {
  imdb_id?: string;
  trim_id?: string;
  original_title?: string;
  backdrops?: string;
  posters?: string;
  poster_width?: number;
  poster_height?: number;
  content_ratings?: string;
  production_countries?: string[];
  is_watched?: number;
  play_error?: string;
  logic_type?: number;
  logos?: string;
}

export interface FnMediaGenreItem {
  id?: number;
  value?: string;
}

export interface FnMediaTagItem {
  key?: string;
  value?: string;
}

export interface FnMediaSeasonItem extends FnMediaItem {
  lan?: string;
}

export interface FnMediaEpisodeItem extends FnMediaItem {
  lan?: string;
  runtime?: number;
}

export interface FnMediaStreamFileInfo {
  guid?: string;
  path?: string;
  file_name?: string;
  size?: number;
  timestamp?: number;
  type?: number;
  sort_num?: number;
  can_play?: number;
  play_error?: string;
  create_time?: number;
  update_time?: number;
  file_birth_time?: number;
  progress_thumb_hash_dir?: string;
}

export interface FnMediaStreamVideoInfo {
  media_guid?: string;
  guid?: string;
  title?: string;
  index?: number;
  resolution_type?: string;
  color_range_type?: string;
  color_range?: string;
  codec_name?: string;
  codec_type?: string;
  profile?: string;
  width?: number;
  height?: number;
  coded_width?: number;
  coded_height?: number;
  display_aspect_ratio?: string;
  pix_fmt?: string;
  level?: string;
  color_space?: string;
  color_transfer?: string;
  color_primaries?: string;
  dv_profile?: number;
  bits_per_raw_sample?: string;
  wrapper?: string;
  duration?: number;
  bps?: number;
  fps?: number | string;
  frame_rate?: number | string;
  avg_frame_rate?: string;
  r_frame_rate?: string;
  progressive?: number;
  bit_depth?: number;
  refs?: number;
  reference_frames?: number;
  rotation?: number;
  is_bluray?: boolean;
}

export interface FnMediaStreamAudioInfo {
  media_guid?: string;
  guid?: string;
  index?: number;
  title?: string;
  audio_type?: string;
  codec_name?: string;
  codec_type?: string;
  language?: string;
  channels?: number;
  profile?: string;
  sample_rate?: string;
  is_default?: number;
  channel_layout?: string;
  duration?: number;
  bps?: number;
  is_fake?: boolean;
}

export interface FnMediaStreamSubtitleInfo {
  media_guid?: string;
  guid?: string;
  index?: number;
  title?: string;
  codec_name?: string;
  codec_type?: string;
  format?: string;
  language?: string;
  forced?: number;
  is_default?: number;
  is_external?: number;
  trim_id?: string;
  source_id?: string;
  Source?: string;
  create_time?: number;
  update_time?: number;
  extra_file?: number;
  is_bitmap?: number;
  file_size?: number;
}

export interface FnMediaStreamListResp {
  code: number;
  msg: string;
  data: FnMediaStreamListData;
}

export interface FnMediaStreamListData {
  files?: FnMediaStreamFileInfo[];
  video_streams?: FnMediaStreamVideoInfo[];
  audio_streams?: FnMediaStreamAudioInfo[];
  subtitle_streams?: FnMediaStreamSubtitleInfo[];
}

export interface FnMediaItemListData {
  dir?: string;
  jump_list?: string[];
  list?: FnMediaItem[];
  mdb_category?: string;
  mdb_name?: string;
  top_dir?: string;
  total?: number;
}

export interface FnMediaItemListResp {
  code: number;
  msg: string;
  data: FnMediaItemListData;
}

export interface FnMediaSearchSubtitleItem {
  filename?: string;
  download?: number;
  source_id?: string;
  source?: string;
  trim_id?: string;
  format?: string;
}

export interface FnMediaSubtitleSearchData {
  lan?: string;
  subtitles?: FnMediaSearchSubtitleItem[];
}

export interface FnMediaSubtitleSearchResp {
  code: number;
  msg: string;
  data: FnMediaSubtitleSearchData;
}

export interface FnMediaSubtitleItem {
  media_guid?: string;
  title?: string;
  guid?: string;
  codec_name?: string;
  codec_type?: string;
  language?: string;
  forced?: number;
  index?: number;
  is_default?: number;
  is_external?: number;
  format?: string;
  trim_id?: string;
  source_id?: string;
  Source?: string;
  create_time?: number;
  update_time?: number;
  extra_file?: number;
  is_bitmap?: number;
  file_size?: number;
}

export interface FnMediaSubtitleItemResp {
  code: number;
  msg: string;
  data: FnMediaSubtitleItem;
}

export interface FnMediaServerPathItem {
  filename?: string;
  is_dir?: boolean;
  trim_name?: string;
  trim_id?: string;
}

export interface FnMediaAuthDirItem {
  path?: string;
  storageType?: number;
  uname?: string;
  address?: string;
  comment?: string;
  type?: number;
  proto?: string;
  username?: string;
  port?: number;
  cloudStorageType?: number;
}

export interface FnMediaAuthDirData {
  isFnOS?: boolean;
  authDirList?: FnMediaAuthDirItem[];
}

export interface FnMediaAuthDirResp {
  code: number;
  msg: string;
  data: FnMediaAuthDirData;
}

export interface FnMediaItemFile {
  media_guid?: string;
  item_guid?: string;
  filename?: string;
  file_path?: string;
  size?: number;
  file_size?: number;
  duration?: number;
  create_time?: number;
  update_time?: number;
  file_birth_time?: number;
  media_stream?: FnMediaItemStream;
}

export interface FnMediaTagListData {
  genres?: number[];
  resolutions?: string[];
  color_range?: string[];
  audio_type?: string[];
  locate?: string[];
  decades?: string[];
  recognition_status?: number[];
}

export interface FnMediaTagListResp {
  code: number;
  msg: string;
  data: FnMediaTagListData;
}

export interface FnMediaSetConfigByItemReq {
  guid: string;
  skip_opening: number;
  skip_ending: number;
}

export interface FnMediaScrapReq {
  media_guids: string[];
}

export interface FnMediaScrapSearchReq {
  source: string;
  source_keyword: string;
  lan: string;
  item_guid: string;
}

export interface FnMediaScrapSearchItem {
  source?: string;
  source_id?: string;
  title?: string;
  type?: string;
  poster_link?: string;
  first_air_date?: string;
  last_air_date?: string;
  number_of_seasons?: number;
  genres?: number[];
  production_countries?: string[];
}

export interface FnMediaScrapRescrapReq {
  item_guid: string;
  source_id: string;
  source: string;
  type: string;
  media_guids: string[];
}

export interface FnMediaPersonListItem {
  item_guid?: string;
  person_guid?: string;
  role?: string;
  job?: string;
  order?: number;
  department?: string;
  trim_id?: string;
  imdb_id?: string;
  tmdb_id?: number;
  lan?: string;
  name?: string;
  original_name?: string;
  also_know_as?: string;
  biography?: string;
  known_for_department?: string;
  profile_path?: string;
  gender?: number;
}

export interface FnMediaPersonListData {
  list?: FnMediaPersonListItem[];
  total?: number;
}

export interface FnMediaPersonListResp {
  code: number;
  msg: string;
  data: FnMediaPersonListData;
}

export interface FnMediaPersonRespData {
  guid?: string;
  name?: string;
  imdb_id?: string;
  original_name?: string;
  profile?: string;
  biography?: string;
  is_favorite?: number;
}

export interface FnMediaPersonResp {
  code: number;
  msg: string;
  data: FnMediaPersonRespData;
}

export interface FnMediaPersonItemListReq {
  person_guid: string;
  page: number;
  page_size: number;
  job: string;
  sort_column: string;
  sort_type: string;
}

export interface FnMediaPersonItemListItem {
  item_guid?: string;
  person_guid?: string;
  guid?: string;
  title?: string;
  type?: string;
  poster?: string;
  vote_average?: string;
  watched?: number;
  is_favorite?: number;
  media_stream?: FnMediaItemStream;
  job?: string;
  role?: string;
  release_date?: string;
  first_air_date?: string;
}

export interface FnMediaPersonItemListData {
  total?: number;
  list?: FnMediaPersonItemListItem[];
}

export interface FnMediaPersonItemListResp {
  code: number;
  msg: string;
  data: FnMediaPersonItemListData;
}

export interface FnMediaQualityReq {
  resolution: string;
  bitrate: number;
}

export interface FnMediaPReq {
  req?: string;
  reqid?: string;
  playLink: string;
  quality?: FnMediaQualityReq;
  startTimestamp?: number;
  clearCache?: boolean;
  audioEncoder?: string;
  channels?: number;
  audioIndex?: number;
  subtitleIndex?: number;
}

export interface FnMediaTranscodeAudioData {
  channels?: number;
  encoder?: string;
}

export interface FnMediaTranscodeVideoData {
  corruptedFrames?: number;
  decodeMethod?: number;
  droppedFrames?: number;
  dynamicRange?: string;
  encodeMethod?: number;
  encoder?: string;
  selectedGpu?: string;
  transcodingRate?: string;
}

export interface FnMediaTranscodeData {
  audio?: FnMediaTranscodeAudioData;
  bitrate?: number;
  reqid?: string;
  resolution?: string;
  result?: string;
  transcoded?: boolean;
  transcodingReason?: number[];
  video?: FnMediaTranscodeVideoData;
}

export interface FnMediaTranscodeResp {
  code: number;
  msg: string;
  data: FnMediaTranscodeData;
}

export interface FnMediaResetQualityData {
  hlsTime?: number;
  reqid?: string;
  result?: string;
  updateM3u8?: boolean;
}

export interface FnMediaResetQualityResp {
  code: number;
  msg: string;
  data: FnMediaResetQualityData;
}
