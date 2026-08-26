/**
 * Feiniu Video catalog-only DTO subset.
 *
 * Copy-first sources:
 * - fnOS/entry/src/main/ets/entity/FnMediaResp.ets:84-221
 * - fnOS/entry/src/main/ets/entity/FnMediaResp.ets:312-521
 * - fnOS/entry/src/main/ets/entity/FnMediaResp.ets:668-751
 *
 * The provider-specific wire names stay unchanged so fixtures match the server
 * contract. These DTOs must not be imported by shared pages or UI state.
 */

export interface FnMediaListResp<T> {
  code: number
  msg: string
  data: T
}

export interface FnMediaGuidReq {
  item_guid: string
}

export interface FnMediaItemListTags {
  genres?: number
  resolution?: string
  color_range?: string
  locate?: string
  decade?: string
  recognition_status?: string
  watched?: string
  audio_type?: string
  type: string[]
}

export interface FnMediaItemListReq {
  ancestor_guid?: string
  exclude_grouped_video?: number
  sort_type?: string
  sort_column?: string
  page_size?: number
  page?: number
  tags: FnMediaItemListTags
}

export interface FnMediaLibraryItem {
  guid: string
  title: string
  posters?: string[]
  category?: string
  view_type?: number
}

export interface FnMediaPlayStreamInfo {
  progress?: number
  current_time?: number
}

export interface FnMediaPlayItem {
  guid: string
  title: string
  type: string
  poster?: string
  media_guid?: string
  video_guid?: string
  audio_guid?: string
  subtitle_guid?: string
  parent_guid?: string
  parent_title?: string
  ancestor_guid?: string
  ancestor_name?: string
  ancestor_category?: string
  tv_title?: string
  season_number?: number
  episode_number?: number
  status?: string
  is_favorite?: number
  watched?: number
  watched_ts?: number
  ts?: number
  duration?: number
  file_name?: string
  media_stream?: FnMediaPlayStreamInfo
}

export interface FnMediaItemStream {
  resolutions?: string[]
  audio_type?: string
  color_range_type?: string
  progress?: number
  current_time?: number
}

export interface FnMediaItem {
  guid: string
  title: string
  type?: string
  poster?: string
  can_play?: number
  parent_guid?: string
  parent_title?: string
  ancestor_guid?: string
  ancestor_name?: string
  ancestor_category?: string
  tv_title?: string
  watched?: number
  watched_ts?: number
  is_favorite?: number
  overview?: string
  status?: string
  vote_average?: string
  ts?: number
  duration?: number
  runtime?: number
  season_number?: number
  episode_number?: number
  number_of_seasons?: number
  number_of_episodes?: number
  local_number_of_seasons?: number
  local_number_of_episodes?: number
  single_child_guid?: string
  play_item_guid?: string
  media_guid?: string
  video_guid?: string
  audio_guid?: string
  subtitle_guid?: string
  file_name?: string
  release_date?: string
  air_date?: string
  first_air_date?: string
  last_air_date?: string
  genres?: number[]
  media_stream?: FnMediaItemStream
}

export interface FnMediaItemDetailResp extends FnMediaItem {
  imdb_id?: string
  trim_id?: string
  original_title?: string
  backdrops?: string
  posters?: string
  poster_width?: number
  poster_height?: number
  content_ratings?: string
  production_countries?: string[]
  is_watched?: number
  play_error?: string
  logic_type?: number
  logos?: string
}

export interface FnMediaItemFile {
  media_guid?: string
  item_guid?: string
  filename?: string
  file_path?: string
  size?: number
  file_size?: number
  duration?: number
  create_time?: number
  update_time?: number
  file_birth_time?: number
  media_stream?: FnMediaItemStream
}

export interface FnMediaGenreItem {
  id?: number
  value?: string
}

export interface FnMediaSeasonItem extends FnMediaItem {
  lan?: string
}

export interface FnMediaEpisodeItem extends FnMediaItem {
  lan?: string
  runtime?: number
}

export interface FnMediaItemListData {
  dir?: string
  jump_list?: string[]
  list?: FnMediaItem[]
  mdb_category?: string
  mdb_name?: string
  top_dir?: string
  total?: number
}

export interface FnMediaItemListResp {
  code: number
  msg: string
  data: FnMediaItemListData
}

export interface FnMediaCountResp {
  code: number
  msg: string
  /** Flat server object keyed by aggregate names and media-library GUIDs. */
  data: Record<string, number>
}

export interface FnMediaPersonListItem {
  item_guid?: string
  person_guid?: string
  role?: string
  job?: string
  order?: number
  department?: string
  trim_id?: string
  imdb_id?: string
  tmdb_id?: number
  lan?: string
  name?: string
  original_name?: string
  also_know_as?: string
  biography?: string
  known_for_department?: string
  profile_path?: string
  gender?: number
}

export interface FnMediaPersonListData {
  list?: FnMediaPersonListItem[]
  total?: number
}

export interface FnMediaPersonListResp {
  code: number
  msg: string
  data: FnMediaPersonListData
}

export interface FnMediaPersonRespData {
  guid?: string
  name?: string
  imdb_id?: string
  original_name?: string
  profile?: string
  biography?: string
  is_favorite?: number
}

export interface FnMediaPersonResp {
  code: number
  msg: string
  data: FnMediaPersonRespData
}

export interface FnMediaPersonItemListReq {
  person_guid: string
  page: number
  page_size: number
  job: string
  sort_column: string
  sort_type: string
}

export interface FnMediaPersonItemListItem {
  item_guid?: string
  person_guid?: string
  guid?: string
  title?: string
  type?: string
  poster?: string
  vote_average?: string
  watched?: number
  is_favorite?: number
  media_stream?: FnMediaItemStream
  job?: string
  role?: string
  release_date?: string
  first_air_date?: string
}

export interface FnMediaPersonItemListData {
  total?: number
  list?: FnMediaPersonItemListItem[]
}

export interface FnMediaPersonItemListResp {
  code: number
  msg: string
  data: FnMediaPersonItemListData
}
