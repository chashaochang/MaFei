import { MediaProviderKind } from '../../../media/MediaProviderKind'
import { MediaProviderSession } from '../../../media/MediaProviderSession'
import { MediaRef, MediaRefKind } from '../../../media/MediaRef'
import {
  createMediaUserState,
  MediaCatalogCounts,
  MediaDetail,
  MediaEpisode,
  MediaExternalLink,
  MediaFavoriteState,
  MediaImageKind,
  MediaImageRef,
  MediaLibrary,
  MediaLibraryKind,
  MediaPage,
  MediaPerson,
  MediaPersonCredit,
  MediaPersonKind,
  MediaRequestHeader,
  MediaSeason,
  MediaSummary,
  MediaUserState,
  MediaWatchedState
} from '../../../media/catalog/MediaCatalogModels'
import {
  FnMediaEpisodeItem,
  FnMediaItem,
  FnMediaItemDetailResp,
  FnMediaLibraryItem,
  FnMediaPersonItemListItem,
  FnMediaPersonListItem,
  FnMediaPersonRespData,
  FnMediaPlayItem,
  FnMediaSeasonItem
} from './FeiniuVideoDtos'

const FEINIU_SECONDS_TO_MILLISECONDS: number = 1000
const FEINIU_MINUTES_TO_MILLISECONDS: number = 60 * FEINIU_SECONDS_TO_MILLISECONDS

export class FeiniuVideoCatalogImageAccess {
  readonly baseUrl: string
  readonly token: string
  readonly cookie: string
  readonly relay: boolean

  constructor(baseUrl: string, token: string = '', cookie: string = '', relay: boolean = false) {
    this.baseUrl = baseUrl.trim()
    this.token = token.trim()
    this.cookie = cookie.trim()
    this.relay = relay
  }
}

export class FeiniuVideoCatalogMapper {
  private readonly session: MediaProviderSession
  private readonly access: FeiniuVideoCatalogImageAccess
  private readonly genreNames: Map<number, string>

  constructor(session: MediaProviderSession,
    access: FeiniuVideoCatalogImageAccess = new FeiniuVideoCatalogImageAccess(session.baseUrl),
    genreNames: Map<number, string> = new Map<number, string>()) {
    if (session.provider !== MediaProviderKind.FeiniuVideo) {
      throw new Error('Feiniu Video mapper requires a Feiniu Video session')
    }
    this.session = session
    this.access = access
    this.genreNames = new Map<number, string>(genreNames)
  }

  counts(data: Record<string, number>): MediaCatalogCounts {
    const movieCount = this.countValue(data, 'movie')
    const seriesCount = this.countValue(data, 'tv')
    const episodeCount = Math.max(
      this.countValue(data, 'episode'),
      this.countValue(data, 'episodes')
    )
    const explicitTotal = this.countValue(data, 'total')
    return {
      totalCount: explicitTotal > 0 ? explicitTotal : movieCount + seriesCount,
      movieCount: movieCount,
      seriesCount: seriesCount,
      episodeCount: episodeCount
    }
  }

  countValue(data: Record<string, number>, key: string): number {
    const normalized = key.trim()
    if (normalized.length === 0) {
      return 0
    }
    const direct = data[normalized] || 0
    if (direct > 0) {
      return Math.floor(direct)
    }
    const lower = normalized.toLowerCase()
    if (lower !== normalized) {
      const lowerValue = data[lower] || 0
      if (lowerValue > 0) {
        return Math.floor(lowerValue)
      }
    }
    const upper = normalized.toUpperCase()
    const upperValue = upper !== normalized ? data[upper] || 0 : 0
    return upperValue > 0 ? Math.floor(upperValue) : 0
  }

  isVisibleLibrary(item: FnMediaLibraryItem): boolean {
    return item.guid.trim().length > 0 &&
      (this.libraryKind(item.category) !== MediaLibraryKind.LiveTv || this.session.capabilities.liveTv)
  }

  library(item: FnMediaLibraryItem, counts?: Record<string, number>): MediaLibrary {
    const owner = this.ref(item.guid, MediaRefKind.Library)
    const posters = item.posters || []
    return {
      ref: owner,
      title: item.title || '',
      kind: this.libraryKind(item.category),
      itemCount: counts ? this.countValue(counts, item.guid) : 0,
      primaryImage: this.image(owner, posters.length > 0 ? posters[0] : '',
        MediaImageKind.Primary, 'library-primary-360', 360)
    }
  }

  isVisibleItem(item: FnMediaItem): boolean {
    return item.guid.trim().length > 0 &&
      (this.refKind(item.type) !== MediaRefKind.LiveChannel || this.session.capabilities.liveTv)
  }

  matchesKinds(item: FnMediaItem, kinds: MediaRefKind[]): boolean {
    if (kinds.length === 0) {
      return true
    }
    const kind = this.refKind(item.type)
    return kinds.indexOf(kind) >= 0
  }

  summary(item: FnMediaItem): MediaSummary {
    const owner = this.itemRef(item)
    return this.createSummary(item, owner, item.title || item.file_name || '', this.subtitle(item))
  }

  latest(item: FnMediaItem): MediaSummary {
    const kind = this.refKind(item.type)
    const seriesId = this.seriesId(item)
    if (kind === MediaRefKind.Episode && seriesId.length > 0) {
      const seriesRef = this.ref(seriesId, MediaRefKind.Series)
      return {
        ref: seriesRef,
        seriesRef: seriesRef,
        seasonRef: this.optionalRef(item.parent_guid, MediaRefKind.Season),
        title: item.ancestor_name || item.tv_title || item.parent_title || item.title || '',
        subtitle: this.episodeSubtitle(item),
        overview: item.overview || '',
        year: this.year(item),
        premiereDate: this.date(item),
        durationMs: this.durationMs(item.duration, item.runtime),
        communityRating: this.rating(item.vote_average),
        seriesTitle: item.ancestor_name || item.tv_title || item.parent_title || undefined,
        seasonNumber: this.positiveNumber(item.season_number),
        episodeNumber: this.positiveNumber(item.episode_number),
        childCount: this.childCount(item),
        genres: this.genres(item),
        tags: this.mediaTags(item),
        primaryImage: this.image(seriesRef, item.poster || '', MediaImageKind.Primary,
          'series-primary-640', 640),
        thumbnailImage: this.image(seriesRef, item.poster || '', MediaImageKind.Thumbnail,
          'series-thumbnail-640', 640),
        userState: this.userState(item)
      }
    }
    return this.summary(item)
  }

  resume(item: FnMediaPlayItem): MediaSummary {
    const owner = this.ref(item.guid, this.playItemKind(item))
    const seriesId = `${item.ancestor_guid || item.parent_guid || ''}`.trim()
    const seasonId = `${item.parent_guid || ''}`.trim()
    const seriesTitle = `${item.parent_title || item.tv_title || item.ancestor_name || ''}`.trim()
    return {
      ref: owner,
      seriesRef: seriesId.length > 0 ? this.ref(seriesId, MediaRefKind.Series) : undefined,
      seasonRef: seasonId.length > 0 && seasonId !== seriesId
        ? this.ref(seasonId, MediaRefKind.Season) : undefined,
      title: seriesTitle.length > 0 ? seriesTitle : (item.title || item.file_name || ''),
      subtitle: this.playSubtitle(item),
      overview: '',
      durationMs: this.durationMs(item.duration),
      seriesTitle: seriesTitle.length > 0 ? seriesTitle : undefined,
      seasonNumber: this.positiveNumber(item.season_number),
      episodeNumber: this.positiveNumber(item.episode_number),
      lastPlayedDate: this.playedDate(item.watched_ts, item.ts),
      genres: [],
      tags: [],
      primaryImage: this.image(owner, item.poster || '', MediaImageKind.Primary,
        'resume-primary-640', 640),
      thumbnailImage: this.image(owner, item.poster || '', MediaImageKind.Thumbnail,
        'resume-thumbnail-640', 640),
      userState: this.playUserState(item)
    }
  }

  detail(item: FnMediaItemDetailResp, people: FnMediaPersonListItem[] = [],
    seasons: MediaSeason[] = []): MediaDetail {
    const owner = this.itemRef(item)
    const externalLinks: MediaExternalLink[] = []
    const imdbId = `${item.imdb_id || ''}`.trim()
    if (imdbId.length > 0) {
      externalLinks.push({
        title: 'IMDb',
        url: `https://www.imdb.com/title/${encodeURIComponent(imdbId)}`
      })
    }
    const countries = item.production_countries || []
    return {
      ref: owner,
      seriesRef: this.optionalRef(this.seriesId(item), MediaRefKind.Series),
      seasonRef: this.refKind(item.type) === MediaRefKind.Episode
        ? this.optionalRef(item.parent_guid, MediaRefKind.Season) : undefined,
      title: item.title || item.file_name || '',
      originalTitle: item.original_title || item.title || '',
      tagline: '',
      overview: item.overview || '',
      seriesTitle: item.ancestor_name || item.tv_title || item.parent_title || undefined,
      seasonTitle: undefined,
      seasonNumber: this.positiveNumber(item.season_number),
      episodeNumber: this.positiveNumber(item.episode_number),
      productionYear: this.year(item),
      premiereDate: this.date(item),
      endDate: item.last_air_date || undefined,
      durationMs: this.durationMs(item.duration, item.runtime),
      officialRating: item.content_ratings || '',
      communityRating: this.rating(item.vote_average),
      genres: this.genres(item),
      tags: this.mediaTags(item).concat(countries),
      studios: [],
      personCredits: this.personCredits(people),
      seasons: seasons.slice(),
      mediaFiles: [],
      externalLinks: externalLinks,
      primaryImage: this.image(owner, item.poster || item.posters || '',
        MediaImageKind.Primary, 'detail-primary-720', 720),
      backdropImage: this.image(owner, item.backdrops || '',
        MediaImageKind.Backdrop, 'detail-backdrop-1280', 1280),
      logoImage: this.image(owner, item.logos || '',
        MediaImageKind.Logo, 'detail-logo-720', 720),
      userState: this.userState(item)
    }
  }

  season(item: FnMediaSeasonItem, seriesRef: MediaRef): MediaSeason {
    const owner = this.ref(item.guid, MediaRefKind.Season)
    return {
      ref: owner,
      seriesRef: seriesRef,
      title: item.title || this.seasonTitle(item.season_number),
      overview: item.overview || '',
      seasonNumber: this.positiveNumber(item.season_number),
      productionYear: this.year(item),
      communityRating: this.rating(item.vote_average),
      episodeCount: Math.max(0, item.local_number_of_episodes || item.number_of_episodes || 0),
      primaryImage: this.image(owner, item.poster || '', MediaImageKind.Primary,
        'season-primary-640', 640),
      backdropImage: this.image(owner, item.poster || '', MediaImageKind.Backdrop,
        'season-backdrop-1280', 1280),
      userState: this.userState(item)
    }
  }

  episode(item: FnMediaEpisodeItem, seriesRef: MediaRef, seasonRef?: MediaRef): MediaEpisode {
    const owner = this.ref(item.guid, MediaRefKind.Episode)
    return {
      ref: owner,
      seriesRef: seriesRef,
      seasonRef: seasonRef || this.optionalRef(item.parent_guid, MediaRefKind.Season),
      title: item.title || this.episodeTitle(item.episode_number),
      seriesTitle: item.ancestor_name || item.tv_title || item.parent_title || '',
      overview: item.overview || '',
      seasonNumber: this.positiveNumber(item.season_number),
      episodeNumber: this.positiveNumber(item.episode_number),
      premiereDate: this.date(item),
      durationMs: this.durationMs(item.duration, item.runtime),
      communityRating: this.rating(item.vote_average),
      tags: this.mediaTags(item),
      primaryImage: this.image(owner, item.poster || '', MediaImageKind.Primary,
        'episode-primary-640', 640),
      backdropImage: this.image(owner, item.poster || '', MediaImageKind.Backdrop,
        'episode-backdrop-1280', 1280),
      userState: this.userState(item)
    }
  }

  person(data: FnMediaPersonRespData): MediaPerson {
    const owner = this.ref(data.guid || '', MediaRefKind.Person)
    return {
      ref: owner,
      name: data.name || '',
      sortName: data.original_name || data.name || '',
      overview: data.biography || '',
      kind: MediaPersonKind.Other,
      role: '',
      primaryImage: this.image(owner, data.profile || '', MediaImageKind.Avatar,
        'person-avatar-360', 360),
      userState: createMediaUserState(
        (data.is_favorite || 0) > 0,
        false,
        0,
        0,
        this.session.capabilities.favorites,
        false
      )
    }
  }

  personWorkSummary(item: FnMediaPersonItemListItem): MediaSummary {
    const mapped: FnMediaItem = {
      guid: `${item.item_guid || item.guid || ''}`.trim(),
      title: item.title || '',
      type: item.type,
      poster: item.poster,
      vote_average: item.vote_average,
      watched: item.watched,
      is_favorite: item.is_favorite,
      media_stream: item.media_stream,
      release_date: item.release_date || item.first_air_date,
      first_air_date: item.first_air_date
    }
    return this.summary(mapped)
  }

  favoriteState(favorite: boolean): MediaFavoriteState {
    return {
      favorite: favorite,
      canUpdate: this.session.capabilities.favorites
    }
  }

  watchedState(watched: boolean): MediaWatchedState {
    return {
      watched: watched,
      canUpdate: this.session.capabilities.watched,
      playbackPositionMs: 0,
      playedPercentage: watched ? 100 : 0
    }
  }

  page<T>(items: T[], requestedStartIndex: number, limit: number, totalCount?: number,
    sourceItemCount: number = items.length): MediaPage<T> {
    const startIndex = Math.max(0, Math.floor(requestedStartIndex))
    const normalizedSourceItemCount = Math.max(0, sourceItemCount)
    const nextStartIndex = startIndex + normalizedSourceItemCount
    const normalizedTotalCount = totalCount === undefined
      ? nextStartIndex : Math.max(0, totalCount)
    const hasMore = normalizedSourceItemCount >= Math.max(1, limit) &&
      (totalCount === undefined || nextStartIndex < normalizedTotalCount)
    return {
      items: items,
      startIndex: startIndex,
      nextStartIndex: nextStartIndex,
      totalCount: normalizedTotalCount,
      hasMore: hasMore
    }
  }

  refKind(type?: string): MediaRefKind {
    const normalized = `${type || ''}`.trim().toLowerCase()
    if (normalized === 'movie') return MediaRefKind.Movie
    if (normalized === 'tv' || normalized === 'series') return MediaRefKind.Series
    if (normalized === 'season') return MediaRefKind.Season
    if (normalized === 'episode') return MediaRefKind.Episode
    if (normalized === 'directory' || normalized === 'folder') return MediaRefKind.Library
    if (normalized === 'video') return MediaRefKind.Video
    if (normalized === 'livechannel' || normalized === 'live') return MediaRefKind.LiveChannel
    return MediaRefKind.Unknown
  }

  playItemKind(item: FnMediaPlayItem): MediaRefKind {
    if ((item.episode_number || 0) > 0) {
      return MediaRefKind.Episode
    }
    return this.refKind(item.type)
  }

  private createSummary(item: FnMediaItem, owner: MediaRef, title: string, subtitle: string): MediaSummary {
    const detail = item as FnMediaItemDetailResp
    return {
      ref: owner,
      seriesRef: this.optionalRef(this.seriesId(item), MediaRefKind.Series),
      seasonRef: this.refKind(item.type) === MediaRefKind.Episode
        ? this.optionalRef(item.parent_guid, MediaRefKind.Season) : undefined,
      title: title,
      subtitle: subtitle,
      overview: item.overview || '',
      year: this.year(item),
      premiereDate: this.date(item),
      lastPlayedDate: this.playedDate(item.watched_ts),
      durationMs: this.durationMs(item.duration, item.runtime),
      communityRating: this.rating(item.vote_average),
      seriesTitle: item.ancestor_name || item.tv_title || item.parent_title || undefined,
      seasonNumber: this.positiveNumber(item.season_number),
      episodeNumber: this.positiveNumber(item.episode_number),
      childCount: this.childCount(item),
      genres: this.genres(item),
      tags: this.mediaTags(item),
      primaryImage: this.image(owner, item.poster || detail.posters || '', MediaImageKind.Primary,
        'item-primary-640', 640),
      thumbnailImage: this.image(owner, item.poster || '', MediaImageKind.Thumbnail,
        'item-thumbnail-640', 640),
      backdropImage: this.image(owner, detail.backdrops || '', MediaImageKind.Backdrop,
        'item-backdrop-1280', 1280),
      logoImage: this.image(owner, detail.logos || '', MediaImageKind.Logo,
        'item-logo-720', 720),
      userState: this.userState(item)
    }
  }

  private personCredits(people: FnMediaPersonListItem[]): MediaPersonCredit[] {
    const credits: MediaPersonCredit[] = []
    people.forEach((person: FnMediaPersonListItem): void => {
      const id = `${person.person_guid || person.trim_id || ''}`.trim()
      if (id.length === 0) {
        return
      }
      const owner = this.ref(id, MediaRefKind.Person)
      credits.push({
        personRef: owner,
        name: person.name || person.original_name || '',
        kind: this.personKind(person.job),
        role: person.role || '',
        primaryImage: this.image(owner, person.profile_path || '', MediaImageKind.Avatar,
          'person-credit-240', 240)
      })
    })
    return credits
  }

  private personKind(job?: string): MediaPersonKind {
    const normalized = `${job || ''}`.trim().toLowerCase()
    if (normalized === 'actor') return MediaPersonKind.Actor
    if (normalized === 'director') return MediaPersonKind.Director
    if (normalized === 'screenplay' || normalized === 'writer') return MediaPersonKind.Writer
    if (normalized === 'producer') return MediaPersonKind.Producer
    if (normalized === 'gueststar' || normalized === 'guest star') return MediaPersonKind.GuestStar
    return MediaPersonKind.Other
  }

  private itemRef(item: FnMediaItem): MediaRef {
    return this.ref(item.guid, this.refKind(item.type))
  }

  private ref(id: string, kind: MediaRefKind): MediaRef {
    return new MediaRef(this.session.provider, this.session.accountScope, id, kind)
  }

  private optionalRef(id: string | undefined, kind: MediaRefKind): MediaRef | undefined {
    const normalized = `${id || ''}`.trim()
    return normalized.length > 0 ? this.ref(normalized, kind) : undefined
  }

  private seriesId(item: FnMediaItem): string {
    const kind = this.refKind(item.type)
    if (kind === MediaRefKind.Series) {
      return item.guid.trim()
    }
    if (kind === MediaRefKind.Season) {
      return `${item.parent_guid || item.ancestor_guid || ''}`.trim()
    }
    if (kind === MediaRefKind.Episode) {
      return `${item.ancestor_guid || ''}`.trim()
    }
    return ''
  }

  private subtitle(item: FnMediaItem): string {
    if (this.refKind(item.type) === MediaRefKind.Episode) {
      return this.episodeSubtitle(item)
    }
    const year = this.year(item)
    return year === undefined ? '' : `${year}`
  }

  private playSubtitle(item: FnMediaPlayItem): string {
    const season = item.season_number || 0
    const episode = item.episode_number || 0
    if (season > 0 && episode > 0) {
      return `第 ${season} 季 · 第 ${episode} 集`
    }
    if (episode > 0) {
      return `第 ${episode} 集`
    }
    return item.type || ''
  }

  private episodeSubtitle(item: FnMediaItem): string {
    const season = item.season_number || 0
    const episode = item.episode_number || 0
    if (season > 0 && episode > 0) {
      return `S${season}:E${episode} - ${item.title || ''}`
    }
    if (episode > 0) {
      return `第 ${episode} 集 - ${item.title || ''}`
    }
    return item.title || ''
  }

  private seasonTitle(value?: number): string {
    return value && value > 0 ? `第 ${value} 季` : '剧季'
  }

  private episodeTitle(value?: number): string {
    return value && value > 0 ? `第 ${value} 集` : '剧集'
  }

  private childCount(item: FnMediaItem): number | undefined {
    const value = item.local_number_of_episodes || item.number_of_episodes ||
      item.local_number_of_seasons || item.number_of_seasons || 0
    return value > 0 ? value : undefined
  }

  private userState(item: FnMediaItem): MediaUserState {
    const durationSeconds = this.durationSeconds(item.duration, item.runtime)
    const positionSeconds = Math.max(0, item.ts || item.media_stream?.current_time || 0)
    let percentage = this.normalizePercentage(item.media_stream?.progress)
    if (percentage === 0 && positionSeconds > 0 && durationSeconds > 0) {
      percentage = this.normalizePercentage(positionSeconds * 100 / durationSeconds)
    }
    const watched = (item.watched || 0) > 0 ||
      ((item as FnMediaItemDetailResp).is_watched || 0) > 0
    if (percentage === 0 && watched) {
      percentage = 100
    }
    return createMediaUserState(
      (item.is_favorite || 0) > 0,
      watched,
      positionSeconds * FEINIU_SECONDS_TO_MILLISECONDS,
      percentage,
      this.session.capabilities.favorites,
      this.session.capabilities.watched
    )
  }

  private playUserState(item: FnMediaPlayItem): MediaUserState {
    const durationSeconds = Math.max(0, item.duration || 0)
    const positionSeconds = Math.max(0, item.ts || item.media_stream?.current_time || 0)
    let percentage = this.normalizePercentage(item.media_stream?.progress)
    if (percentage === 0 && positionSeconds > 0 && durationSeconds > 0) {
      percentage = this.normalizePercentage(positionSeconds * 100 / durationSeconds)
    }
    return createMediaUserState(
      (item.is_favorite || 0) > 0,
      (item.watched || 0) > 0,
      positionSeconds * FEINIU_SECONDS_TO_MILLISECONDS,
      percentage,
      this.session.capabilities.favorites,
      this.session.capabilities.watched
    )
  }

  private mediaTags(item: FnMediaItem): string[] {
    const tags: string[] = []
    const resolutions = item.media_stream?.resolutions || []
    resolutions.forEach((value: string): void => {
      const normalized = `${value || ''}`.trim().toUpperCase()
      if (normalized.length > 0 && tags.indexOf(normalized) < 0) {
        tags.push(normalized)
      }
    })
    const audioType = `${item.media_stream?.audio_type || ''}`.trim()
    if (audioType.length > 0 && tags.indexOf(audioType) < 0) {
      tags.push(audioType)
    }
    const colorRange = `${item.media_stream?.color_range_type || ''}`.trim()
    if (colorRange.length > 0 && tags.indexOf(colorRange) < 0) {
      tags.push(colorRange)
    }
    return tags
  }

  private genres(item: FnMediaItem): string[] {
    const result: string[] = []
    const ids = item.genres || []
    ids.forEach((id: number): void => {
      const name = `${this.genreNames.get(id) || ''}`.trim()
      if (name.length > 0 && result.indexOf(name) < 0) {
        result.push(name)
      }
    })
    return result
  }

  private libraryKind(category?: string): MediaLibraryKind {
    const normalized = `${category || ''}`.trim().toLowerCase()
    if (normalized === 'movie') return MediaLibraryKind.Movies
    if (normalized === 'tv') return MediaLibraryKind.Series
    if (normalized === 'live' || normalized === 'livetv' || normalized === 'iptv') {
      return MediaLibraryKind.LiveTv
    }
    return MediaLibraryKind.Mixed
  }

  private date(item: FnMediaItem): string | undefined {
    const value = `${item.release_date || item.air_date || item.first_air_date || item.last_air_date || ''}`.trim()
    return value.length > 0 ? value : undefined
  }

  private playedDate(watchedTimestamp?: number, fallbackTimestamp?: number): string | undefined {
    const seconds = watchedTimestamp && watchedTimestamp > 0 ? watchedTimestamp : (fallbackTimestamp || 0)
    if (seconds <= 0) {
      return undefined
    }
    return new Date(seconds * FEINIU_SECONDS_TO_MILLISECONDS).toISOString()
  }

  private year(item: FnMediaItem): number | undefined {
    const value = this.date(item)
    if (!value || value.length < 4) {
      return undefined
    }
    const parsed = Number(value.substring(0, 4))
    return Number.isFinite(parsed) && parsed > 0 ? Math.floor(parsed) : undefined
  }

  private rating(value?: string): number | undefined {
    const parsed = Number(`${value || ''}`.trim())
    return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
  }

  private durationSeconds(duration?: number, runtimeMinutes?: number): number {
    if (duration && duration > 0) {
      return Math.floor(duration)
    }
    return runtimeMinutes && runtimeMinutes > 0 ? Math.floor(runtimeMinutes * 60) : 0
  }

  private durationMs(duration?: number, runtimeMinutes?: number): number {
    if (duration && duration > 0) {
      return Math.floor(duration * FEINIU_SECONDS_TO_MILLISECONDS)
    }
    return runtimeMinutes && runtimeMinutes > 0
      ? Math.floor(runtimeMinutes * FEINIU_MINUTES_TO_MILLISECONDS) : 0
  }

  private positiveNumber(value?: number): number | undefined {
    return value && value > 0 ? Math.floor(value) : undefined
  }

  private normalizePercentage(value?: number): number {
    return Math.min(100, Math.max(0, value || 0))
  }

  private image(owner: MediaRef, path: string, kind: MediaImageKind,
    variant: string, width: number): MediaImageRef | undefined {
    const normalizedPath = path.trim()
    const baseUrl = this.normalizedBaseUrl(this.access.baseUrl)
    if (normalizedPath.length === 0 || baseUrl.length === 0) {
      return undefined
    }
    const imagePath = normalizedPath.startsWith('/') ? normalizedPath : `/${normalizedPath}`
    return new MediaImageRef({
      owner: owner,
      kind: kind,
      url: `${baseUrl}/v/api/v1/sys/img${imagePath}?w=${Math.max(1, Math.floor(width))}`,
      variant: variant,
      revision: normalizedPath,
      headers: this.imageHeaders()
    })
  }

  private imageHeaders(): MediaRequestHeader[] {
    const headers: MediaRequestHeader[] = []
    const token = this.access.token.trim()
    if (token.length > 0) {
      headers.push({ name: 'Authorization', value: token })
    }
    const cookie = this.imageCookie(token)
    if (cookie.length > 0) {
      headers.push({ name: 'Cookie', value: cookie })
    }
    return headers
  }

  private imageCookie(token: string): string {
    let cookie = this.access.cookie.trim()
    if (token.length > 0 && cookie.indexOf('Trim-MC-token=') < 0) {
      cookie = this.appendCookie(cookie, `Trim-MC-token=${token}`)
    }
    if (this.access.relay && cookie.indexOf('mode=relay') < 0) {
      cookie = this.appendCookie(cookie, 'mode=relay')
    }
    return cookie
  }

  private appendCookie(cookie: string, pair: string): string {
    return cookie.length > 0 ? `${cookie}; ${pair}` : pair
  }

  private normalizedBaseUrl(value: string): string {
    let normalized = value.trim()
    while (normalized.endsWith('/')) {
      normalized = normalized.substring(0, normalized.length - 1)
    }
    return normalized
  }

}
