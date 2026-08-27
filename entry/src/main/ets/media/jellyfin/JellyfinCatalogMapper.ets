import { ImageUrlsApi } from 'sdk'
import {
  BaseItemDto,
  BaseItemKind,
  BaseItemPerson,
  CollectionType,
  ImageType,
  MediaSourceInfo,
  MediaStream,
  MediaStreamType,
  PersonKind,
  UserItemDataDto
} from 'sdk/src/main/ets/generated-client/models'
import { MediaProviderSession } from '../MediaProviderSession'
import { MediaRef, MediaRefKind } from '../MediaRef'
import {
  createMediaUserState,
  MediaDetail,
  MediaEpisode,
  MediaExternalLink,
  MediaFavoriteState,
  MediaFileInfo,
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
  MediaStreamInfo,
  MediaStreamKind,
  MediaSummary,
  MediaUserState,
  MediaWatchedState
} from '../catalog/MediaCatalogModels'

const JELLYFIN_TICKS_PER_MILLISECOND: number = 10000

export class JellyfinCatalogMapper {
  private readonly session: MediaProviderSession
  private readonly basePath: string
  private readonly authorizationHeader: string
  private readonly imageApi: ImageUrlsApi

  constructor(session: MediaProviderSession, basePath: string = session.baseUrl,
    authorizationHeader: string = '', imageApi: ImageUrlsApi = new ImageUrlsApi()) {
    this.session = session
    this.basePath = this.normalizeBasePath(basePath)
    this.authorizationHeader = authorizationHeader
    this.imageApi = imageApi
  }

  rehydrateImage(image: MediaImageRef): MediaImageRef {
    const requestPath = image.requestPath.trim()
    if (!image.belongsTo(this.session) || requestPath.length === 0) {
      return image
    }
    const headers: MediaRequestHeader[] = []
    if (this.authorizationHeader.length > 0) {
      headers.push({ name: 'Authorization', value: this.authorizationHeader })
    }
    return new MediaImageRef({
      owner: image.owner,
      kind: image.kind,
      url: this.absoluteUrl(requestPath),
      requestPath: requestPath,
      variant: 'rehydrated',
      cacheKeyOverride: image.cacheKey,
      headers: headers
    })
  }

  isVideoLibrary(item: BaseItemDto): boolean {
    return item.CollectionType === undefined || item.CollectionType === null ||
      item.CollectionType === CollectionType.Unknown ||
      item.CollectionType === CollectionType.Movies ||
      item.CollectionType === CollectionType.Tvshows ||
      item.CollectionType === CollectionType.Homevideos ||
      item.CollectionType === CollectionType.Folders
  }

  isVisibleLibrary(_item: BaseItemDto): boolean {
    return true
  }

  isLatestLibrary(item: BaseItemDto): boolean {
    return item.CollectionType !== CollectionType.Livetv && item.CollectionType !== CollectionType.Music
  }

  library(item: BaseItemDto, itemCount?: number): MediaLibrary {
    const owner = this.ref(item.Id || '', MediaRefKind.Library)
    return {
      ref: owner,
      title: item.Name || '',
      kind: this.libraryKind(item.CollectionType),
      itemCount: Math.max(0, itemCount ?? item.ChildCount ?? item.RecursiveItemCount ?? 0),
      primaryImage: this.image(owner, item.Id || '', ImageType.Primary,
        MediaImageKind.Primary, 'library-primary', item.ImageTags?.[ImageType.Primary])
    }
  }

  summary(item: BaseItemDto): MediaSummary {
    const owner = this.itemRef(item)
    return this.createSummary(item, owner, item.Name || '', this.defaultSubtitle(item))
  }

  recommendation(item: BaseItemDto): MediaSummary | undefined {
    if (!item.Id || item.Type !== BaseItemKind.Movie) {
      return undefined
    }
    const metadata: string[] = ['电影']
    if (item.ProductionYear) {
      metadata.push(item.ProductionYear.toString())
    }
    if (item.CommunityRating) {
      metadata.push(`${item.CommunityRating.toFixed(1)} 分`)
    }
    return this.createSummary(item, this.itemRef(item), item.Name || '', metadata.join(' · '))
  }

  resume(item: BaseItemDto): MediaSummary {
    let title = item.Name || ''
    let subtitle = item.Type || ''
    if (item.Type === BaseItemKind.Movie) {
      subtitle = '电影'
    } else if (item.Type === BaseItemKind.Episode) {
      title = item.SeriesName || item.Name || ''
      subtitle = `${item.SeasonName || ''} · ${item.Name || ''}`
    }
    return this.createSummary(item, this.itemRef(item), title, subtitle)
  }

  nextUp(item: BaseItemDto): MediaSummary {
    const title = item.SeriesName || item.Name || ''
    const subtitle = `S${item.ParentIndexNumber ?? 0}:E${item.IndexNumber ?? 0} - ${item.Name || ''}`
    return this.createSummary(item, this.itemRef(item), title, subtitle)
  }

  latest(item: BaseItemDto): MediaSummary {
    if (item.Type === BaseItemKind.Episode && (item.SeriesId || '').length > 0) {
      const seriesRef = this.ref(item.SeriesId || '', MediaRefKind.Series)
      const subtitle = `S${item.ParentIndexNumber ?? 0}:E${item.IndexNumber ?? 0} - ${item.Name || ''}`
      return {
        ref: seriesRef,
        seriesRef: seriesRef,
        seasonRef: this.optionalRef(item.SeasonId, MediaRefKind.Season),
        title: item.SeriesName || item.Name || '',
        subtitle: subtitle,
        overview: item.Overview || '',
        year: item.ProductionYear ?? undefined,
        premiereDate: item.PremiereDate || undefined,
        lastPlayedDate: item.UserData?.LastPlayedDate || undefined,
        durationMs: this.ticksToMilliseconds(item.RunTimeTicks),
        communityRating: item.CommunityRating ?? undefined,
        seriesTitle: item.SeriesName || undefined,
        seasonNumber: item.ParentIndexNumber ?? undefined,
        episodeNumber: item.IndexNumber ?? undefined,
        childCount: item.ChildCount ?? undefined,
        canDelete: item.CanDelete ?? undefined,
        path: item.Path || undefined,
        genres: (item.Genres || []).slice(),
        tags: this.resolutionTags(item.MediaStreams),
        primaryImage: this.seriesPrimaryImage(item, seriesRef),
        thumbnailImage: this.thumbnailImage(item, seriesRef),
        backdropImage: this.backdropImage(item, seriesRef),
        logoImage: this.logoImage(item, seriesRef),
        userState: this.userState(item.UserData)
      }
    }
    return this.summary(item)
  }

  detail(item: BaseItemDto, seasons: MediaSeason[] = []): MediaDetail {
    const owner = this.itemRef(item)
    return {
      ref: owner,
      seriesRef: this.optionalRef(item.SeriesId, MediaRefKind.Series),
      seasonRef: this.optionalRef(item.SeasonId, MediaRefKind.Season),
      title: item.Name || '',
      originalTitle: item.OriginalTitle || item.Name || '',
      tagline: item.Taglines?.[0] || '',
      overview: item.Overview || '',
      seriesTitle: item.SeriesName || undefined,
      seasonTitle: item.SeasonName || undefined,
      seasonNumber: item.ParentIndexNumber ?? undefined,
      episodeNumber: item.IndexNumber ?? undefined,
      productionYear: item.ProductionYear ?? undefined,
      premiereDate: item.PremiereDate || undefined,
      endDate: item.EndDate || undefined,
      durationMs: this.ticksToMilliseconds(item.RunTimeTicks),
      officialRating: item.OfficialRating || '',
      communityRating: item.CommunityRating ?? undefined,
      criticRating: item.CriticRating ?? undefined,
      canDelete: item.CanDelete ?? undefined,
      path: item.Path || undefined,
      genres: (item.Genres || []).slice(),
      tags: (item.Tags || []).slice(),
      studios: (item.Studios || []).map((studio): string => studio.Name || ''
        ).filter((name: string): boolean => name.length > 0),
      personCredits: this.personCredits(item.People),
      seasons: seasons.slice(),
      mediaFiles: this.mediaFiles(owner, item.MediaSources),
      externalLinks: (item.ExternalUrls || []).map((link): MediaExternalLink => {
        return {
          title: link.Name || '',
          url: link.Url || ''
        }
      }).filter((link: MediaExternalLink): boolean => link.url.length > 0),
      primaryImage: this.primaryImage(item, owner),
      backdropImage: this.backdropImage(item, owner),
      logoImage: this.logoImage(item, owner),
      userState: this.userState(item.UserData)
    }
  }

  season(item: BaseItemDto, seriesRef: MediaRef): MediaSeason {
    const owner = this.ref(item.Id || '', MediaRefKind.Season)
    return {
      ref: owner,
      seriesRef: seriesRef,
      title: item.Name || '',
      overview: item.Overview || '',
      seasonNumber: item.IndexNumber ?? undefined,
      productionYear: item.ProductionYear ?? undefined,
      communityRating: item.CommunityRating ?? undefined,
      episodeCount: Math.max(0, item.ChildCount ?? item.EpisodeCount ?? 0),
      tags: this.resolutionTags(item.MediaStreams),
      primaryImage: this.primaryImage(item, owner),
      backdropImage: this.backdropImage(item, owner),
      userState: this.userState(item.UserData)
    }
  }

  episode(item: BaseItemDto, seriesRef: MediaRef, seasonRef?: MediaRef): MediaEpisode {
    const owner = this.ref(item.Id || '', MediaRefKind.Episode)
    return {
      ref: owner,
      seriesRef: seriesRef,
      seasonRef: seasonRef || this.optionalRef(item.SeasonId, MediaRefKind.Season),
      title: item.Name || '',
      seriesTitle: item.SeriesName || '',
      overview: item.Overview || '',
      seasonNumber: item.ParentIndexNumber ?? undefined,
      episodeNumber: item.IndexNumber ?? undefined,
      premiereDate: item.PremiereDate || undefined,
      durationMs: this.ticksToMilliseconds(item.RunTimeTicks),
      communityRating: item.CommunityRating ?? undefined,
      tags: this.resolutionTags(item.MediaStreams),
      primaryImage: this.primaryImage(item, owner),
      backdropImage: this.backdropImage(item, owner),
      userState: this.userState(item.UserData)
    }
  }

  person(item: BaseItemDto): MediaPerson {
    const owner = this.ref(item.Id || '', MediaRefKind.Person)
    return {
      ref: owner,
      name: item.Name || '',
      sortName: item.SortName || item.Name || '',
      overview: item.Overview || '',
      kind: MediaPersonKind.Other,
      role: '',
      movieCount: item.MovieCount ?? undefined,
      episodeCount: item.EpisodeCount ?? undefined,
      seriesCount: item.SeriesCount ?? undefined,
      primaryImage: this.image(owner, item.Id || '', ImageType.Primary,
        MediaImageKind.Avatar, 'person-primary', item.ImageTags?.[ImageType.Primary]),
      userState: this.userState(item.UserData)
    }
  }

  favoriteState(data?: UserItemDataDto, fallback: boolean = false): MediaFavoriteState {
    return {
      favorite: data?.IsFavorite ?? fallback,
      canUpdate: this.session.capabilities.favorites
    }
  }

  watchedState(data?: UserItemDataDto, fallback: boolean = false): MediaWatchedState {
    return {
      watched: data?.Played ?? fallback,
      canUpdate: this.session.capabilities.watched,
      playbackPositionMs: this.ticksToMilliseconds(data?.PlaybackPositionTicks),
      playedPercentage: this.normalizePercentage(data?.PlayedPercentage)
    }
  }

  page<T>(items: T[], requestedStartIndex: number, limit: number, totalCount?: number,
    sourceItemCount: number = items.length, useTotalCount: boolean = true): MediaPage<T> {
    const startIndex = Math.max(0, requestedStartIndex)
    const normalizedSourceItemCount = Math.max(0, sourceItemCount)
    const nextStartIndex = startIndex + normalizedSourceItemCount
    const normalizedTotalCount = totalCount === undefined || totalCount === null ?
      nextStartIndex : Math.max(0, totalCount)
    const hasFullPage = normalizedSourceItemCount >= Math.max(1, limit)
    const hasMore = hasFullPage && (!useTotalCount || totalCount === undefined ||
      totalCount === null || nextStartIndex < normalizedTotalCount)
    return {
      items: items,
      startIndex: startIndex,
      nextStartIndex: nextStartIndex,
      totalCount: normalizedTotalCount,
      hasMore: hasMore
    }
  }

  private createSummary(item: BaseItemDto, owner: MediaRef, title: string, subtitle: string): MediaSummary {
    return {
      ref: owner,
      seriesRef: this.optionalRef(item.SeriesId, MediaRefKind.Series),
      seasonRef: this.optionalRef(item.SeasonId, MediaRefKind.Season),
      title: title,
      subtitle: subtitle,
      overview: item.Overview || '',
      year: item.ProductionYear ?? undefined,
      premiereDate: item.PremiereDate || undefined,
      lastPlayedDate: item.UserData?.LastPlayedDate || undefined,
      durationMs: this.ticksToMilliseconds(item.RunTimeTicks),
      communityRating: item.CommunityRating ?? undefined,
      seriesTitle: item.SeriesName || undefined,
      seasonNumber: item.ParentIndexNumber ?? undefined,
      episodeNumber: item.IndexNumber ?? undefined,
      childCount: item.ChildCount ?? undefined,
      canDelete: item.CanDelete ?? undefined,
      path: item.Path || undefined,
      genres: (item.Genres || []).slice(),
      tags: this.resolutionTags(item.MediaStreams),
      primaryImage: this.primaryImage(item, owner),
      thumbnailImage: this.thumbnailImage(item, owner),
      backdropImage: this.backdropImage(item, owner),
      logoImage: this.logoImage(item, owner),
      userState: this.userState(item.UserData)
    }
  }

  private itemRef(item: BaseItemDto): MediaRef {
    return this.ref(item.Id || '', this.refKind(item.Type))
  }

  private ref(id: string, kind: MediaRefKind): MediaRef {
    return new MediaRef(this.session.provider, this.session.accountScope, id, kind)
  }

  private optionalRef(id: string | null | undefined, kind: MediaRefKind): MediaRef | undefined {
    const normalized = (id || '').trim()
    return normalized.length > 0 ? this.ref(normalized, kind) : undefined
  }

  private refKind(kind?: BaseItemKind): MediaRefKind {
    if (kind === BaseItemKind.Movie) {
      return MediaRefKind.Movie
    }
    if (kind === BaseItemKind.Video) {
      return MediaRefKind.Video
    }
    if (kind === BaseItemKind.Series) {
      return MediaRefKind.Series
    }
    if (kind === BaseItemKind.Season) {
      return MediaRefKind.Season
    }
    if (kind === BaseItemKind.Episode) {
      return MediaRefKind.Episode
    }
    if (kind === BaseItemKind.Person) {
      return MediaRefKind.Person
    }
    if (kind === BaseItemKind.LiveTvChannel) {
      return MediaRefKind.LiveChannel
    }
    return MediaRefKind.Unknown
  }

  private libraryKind(kind?: CollectionType | null): MediaLibraryKind {
    if (kind === CollectionType.Movies) {
      return MediaLibraryKind.Movies
    }
    if (kind === CollectionType.Tvshows) {
      return MediaLibraryKind.Series
    }
    if (kind === CollectionType.Music) {
      return MediaLibraryKind.Music
    }
    if (kind === CollectionType.Musicvideos) {
      return MediaLibraryKind.MusicVideos
    }
    if (kind === CollectionType.Photos) {
      return MediaLibraryKind.Photos
    }
    if (kind === CollectionType.Livetv) {
      return MediaLibraryKind.LiveTv
    }
    if (kind === CollectionType.Trailers) {
      return MediaLibraryKind.Trailers
    }
    if (kind === CollectionType.Boxsets) {
      return MediaLibraryKind.BoxSets
    }
    if (kind === CollectionType.Books) {
      return MediaLibraryKind.Books
    }
    if (kind === CollectionType.Playlists) {
      return MediaLibraryKind.Playlists
    }
    if (kind === CollectionType.Homevideos || kind === CollectionType.Folders ||
      kind === CollectionType.Unknown || kind === undefined || kind === null) {
      return MediaLibraryKind.Mixed
    }
    return MediaLibraryKind.Other
  }

  private defaultSubtitle(item: BaseItemDto): string {
    if (item.Type === BaseItemKind.Episode) {
      return `S${item.ParentIndexNumber ?? 0}:E${item.IndexNumber ?? 0} - ${item.Name || ''}`
    }
    return item.ProductionYear ? item.ProductionYear.toString() : ''
  }

  private userState(data?: UserItemDataDto): MediaUserState {
    return createMediaUserState(
      data?.IsFavorite === true,
      data?.Played === true,
      this.ticksToMilliseconds(data?.PlaybackPositionTicks),
      this.normalizePercentage(data?.PlayedPercentage),
      this.session.capabilities.favorites,
      this.session.capabilities.watched
    )
  }

  private ticksToMilliseconds(value?: number | null): number {
    return value && value > 0 ? Math.floor(value / JELLYFIN_TICKS_PER_MILLISECOND) : 0
  }

  private normalizePercentage(value?: number | null): number {
    return Math.min(100, Math.max(0, value || 0))
  }

  private resolutionTags(streams?: MediaStream[] | null): string[] {
    const tags: string[] = []
    const sourceStreams = streams || []
    sourceStreams.forEach((stream: MediaStream): void => {
      const width = stream.Width || 0
      const height = stream.Height || 0
      let tag = ''
      if (width >= 3800 || height >= 2000) {
        tag = '4K'
      } else if (width >= 2500 || height >= 1400) {
        tag = '1440'
      } else if (width >= 1800 || height >= 1000) {
        tag = '1080'
      } else if (width >= 1200 || height >= 700) {
        tag = '720'
      } else if (width >= 700 || height >= 400) {
        tag = '480'
      }
      if (tag.length > 0 && !tags.includes(tag) && tags.length < 2) {
        tags.push(tag)
      }
    })
    return tags
  }

  private personCredits(people?: BaseItemPerson[] | null): MediaPersonCredit[] {
    const credits: MediaPersonCredit[] = []
    const sourcePeople = people || []
    sourcePeople.forEach((person: BaseItemPerson): void => {
      const id = (person.Id || '').trim()
      if (id.length === 0) {
        return
      }
      const personRef = this.ref(id, MediaRefKind.Person)
      credits.push({
        personRef: personRef,
        name: person.Name || '',
        kind: this.personKind(person.Type),
        role: person.Role || '',
        primaryImage: this.image(personRef, id, ImageType.Primary,
          MediaImageKind.Avatar, 'person-primary', person.PrimaryImageTag)
      })
    })
    return credits
  }

  private personKind(kind?: PersonKind): MediaPersonKind {
    if (kind === PersonKind.Actor) {
      return MediaPersonKind.Actor
    }
    if (kind === PersonKind.Director) {
      return MediaPersonKind.Director
    }
    if (kind === PersonKind.Writer) {
      return MediaPersonKind.Writer
    }
    if (kind === PersonKind.Producer) {
      return MediaPersonKind.Producer
    }
    if (kind === PersonKind.GuestStar) {
      return MediaPersonKind.GuestStar
    }
    return MediaPersonKind.Other
  }

  private mediaFiles(owner: MediaRef, sources?: MediaSourceInfo[] | null): MediaFileInfo[] {
    return (sources || []).map((source: MediaSourceInfo, index: number): MediaFileInfo => {
      return {
        ownerRef: owner,
        id: source.Id || `${owner.id}:${index}`,
        name: source.Name || '',
        path: source.Path || '',
        container: source.Container || '',
        protocol: source.Protocol || '',
        sizeBytes: Math.max(0, source.Size || 0),
        bitrate: source.Bitrate ?? undefined,
        durationMs: this.ticksToMilliseconds(source.RunTimeTicks),
        streams: (source.MediaStreams || []).map((stream: MediaStream): MediaStreamInfo => {
          return this.streamInfo(stream)
        })
      }
    })
  }

  private streamInfo(stream: MediaStream): MediaStreamInfo {
    return {
      index: stream.Index ?? -1,
      kind: this.streamKind(stream.Type),
      title: stream.Title || '',
      displayTitle: stream.DisplayTitle || '',
      codec: stream.Codec || '',
      profile: stream.Profile || undefined,
      level: stream.Level ?? undefined,
      language: stream.Language || '',
      bitrate: stream.BitRate ?? undefined,
      bitDepth: stream.BitDepth ?? undefined,
      refFrames: stream.RefFrames ?? undefined,
      sampleRate: stream.SampleRate ?? undefined,
      channels: stream.Channels ?? undefined,
      width: stream.Width ?? undefined,
      height: stream.Height ?? undefined,
      aspectRatio: stream.AspectRatio || undefined,
      frameRate: stream.AverageFrameRate ?? stream.RealFrameRate ?? undefined,
      interlaced: stream.IsInterlaced ?? undefined,
      videoRangeType: stream.VideoRangeType || undefined,
      colorPrimaries: stream.ColorPrimaries || undefined,
      colorSpace: stream.ColorSpace || undefined,
      colorTransfer: stream.ColorTransfer || undefined,
      pixelFormat: stream.PixelFormat || undefined,
      channelLayout: stream.ChannelLayout || undefined,
      external: stream.IsExternal === true,
      default: stream.IsDefault === true,
      forced: stream.IsForced === true
    }
  }

  private streamKind(kind?: MediaStreamType): MediaStreamKind {
    if (kind === MediaStreamType.Video) {
      return MediaStreamKind.Video
    }
    if (kind === MediaStreamType.Audio) {
      return MediaStreamKind.Audio
    }
    if (kind === MediaStreamType.Subtitle) {
      return MediaStreamKind.Subtitle
    }
    return MediaStreamKind.Other
  }

  private primaryImage(item: BaseItemDto, owner: MediaRef): MediaImageRef | undefined {
    if (item.Type === BaseItemKind.Episode && (item.SeriesId || '').length > 0) {
      return this.seriesPrimaryImage(item, owner)
    }
    return this.image(owner, item.Id || '', ImageType.Primary, MediaImageKind.Primary,
      'primary', item.ImageTags?.[ImageType.Primary])
  }

  private seriesPrimaryImage(item: BaseItemDto, owner: MediaRef): MediaImageRef | undefined {
    return this.image(owner, item.SeriesId || '', ImageType.Primary, MediaImageKind.Primary,
      'series-primary', item.SeriesPrimaryImageTag || undefined)
  }

  private thumbnailImage(item: BaseItemDto, owner: MediaRef): MediaImageRef | undefined {
    if ((item.ParentThumbItemId || '').length > 0) {
      return this.image(owner, item.ParentThumbItemId || '', ImageType.Thumb,
        MediaImageKind.Thumbnail, 'parent-thumb', item.ParentThumbImageTag || undefined)
    }
    if (item.ImageTags?.[ImageType.Thumb]) {
      return this.image(owner, item.Id || '', ImageType.Thumb, MediaImageKind.Thumbnail,
        'thumb', item.ImageTags[ImageType.Thumb])
    }
    return undefined
  }

  private backdropImage(item: BaseItemDto, owner: MediaRef): MediaImageRef | undefined {
    if (item.Type === BaseItemKind.Episode) {
      if ((item.ParentThumbItemId || '').length > 0) {
        return this.image(owner, item.ParentThumbItemId || '', ImageType.Thumb,
          MediaImageKind.Backdrop, 'parent-thumb-backdrop', item.ParentThumbImageTag || undefined)
      }
      if ((item.ParentBackdropItemId || '').length > 0) {
        return this.image(owner, item.ParentBackdropItemId || '', ImageType.Backdrop,
          MediaImageKind.Backdrop, 'parent-backdrop', item.ParentBackdropImageTags?.[0])
      }
      if ((item.SeriesId || '').length > 0) {
        return this.image(owner, item.SeriesId || '', ImageType.Primary,
          MediaImageKind.Backdrop, 'series-primary-backdrop', item.SeriesPrimaryImageTag || undefined)
      }
    }
    return this.image(owner, item.Id || '', ImageType.Backdrop, MediaImageKind.Backdrop,
      'backdrop', item.BackdropImageTags?.[0])
  }

  private logoImage(item: BaseItemDto, owner: MediaRef): MediaImageRef | undefined {
    const tag = item.ImageTags?.[ImageType.Logo]
    if (!tag) {
      return undefined
    }
    return this.image(owner, item.Id || '', ImageType.Logo, MediaImageKind.Logo, 'logo', tag)
  }

  private image(owner: MediaRef, imageItemId: string, sourceKind: ImageType,
    kind: MediaImageKind, variant: string, revision?: string | null): MediaImageRef | undefined {
    const id = imageItemId.trim()
    if (id.length === 0) {
      return undefined
    }
    const relativeUrl = this.imageApi.getItemImageUrlById(
      id, sourceKind, revision ? { tag: revision } : {})
    const headers: MediaRequestHeader[] = []
    if (this.authorizationHeader.length > 0) {
      headers.push({ name: 'Authorization', value: this.authorizationHeader })
    }
    return new MediaImageRef({
      owner: owner,
      kind: kind,
      url: this.absoluteUrl(relativeUrl),
      requestPath: relativeUrl,
      variant: variant,
      revision: revision || '',
      headers: headers
    })
  }

  private absoluteUrl(value: string): string {
    if (value.startsWith('http://') || value.startsWith('https://')) {
      return value
    }
    if (value.startsWith('/')) {
      return this.basePath + value
    }
    return `${this.basePath}/${value}`
  }

  private normalizeBasePath(value: string): string {
    let normalized = value.trim()
    while (normalized.endsWith('/')) {
      normalized = normalized.slice(0, -1)
    }
    return normalized
  }
}
