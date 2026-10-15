import { ImageUrlsApi } from 'sdk'
import { BaseItemDto, ImageType } from 'sdk/src/main/ets/generated-client/models'
import {
  MusicAlbum,
  MusicArtist,
  MusicPageResult,
  MusicTrack,
  musicTicksToMilliseconds
} from './MusicModels'

export class MusicMapper {
  private basePath: string
  private imageApi: ImageUrlsApi

  constructor(basePath: string, imageApi: ImageUrlsApi = new ImageUrlsApi()) {
    this.basePath = basePath
    this.imageApi = imageApi
  }

  album(item: BaseItemDto): MusicAlbum {
    const albumArtist = item.AlbumArtist || item.AlbumArtists?.[0]?.Name || item.Artists?.[0] || ''
    const albumArtistId = item.AlbumArtists?.[0]?.Id || item.ArtistItems?.[0]?.Id || ''
    return {
      id: item.Id || '',
      title: item.Name || '',
      artist: albumArtist,
      artistId: albumArtistId,
      year: item.ProductionYear || 0,
      imageUrl: this.primaryImage(item.Id || '', item.ImageTags?.Primary),
      trackCount: item.ChildCount || item.RecursiveItemCount || 0,
      durationMs: musicTicksToMilliseconds(item.CumulativeRunTimeTicks || item.RunTimeTicks),
      favorite: item.UserData?.IsFavorite === true,
      overview: item.Overview || ''
    }
  }

  artist(item: BaseItemDto): MusicArtist {
    return {
      id: item.Id || '',
      name: item.Name || '',
      imageUrl: this.primaryImage(item.Id || '', item.ImageTags?.Primary),
      albumCount: item.ChildCount || 0,
      trackCount: item.RecursiveItemCount || 0,
      overview: item.Overview || ''
    }
  }

  track(item: BaseItemDto): MusicTrack {
    const artists = item.Artists || []
    const artistName = artists.join(' / ') || item.AlbumArtist || ''
    const artistId = item.ArtistItems?.[0]?.Id || item.AlbumArtists?.[0]?.Id || ''
    const imageItemId = item.AlbumId || item.Id || ''
    const imageTag = item.AlbumPrimaryImageTag || item.ImageTags?.Primary
    return {
      id: item.Id || '',
      title: item.Name || '',
      artist: artistName,
      artistId: artistId,
      album: item.Album || '',
      albumId: item.AlbumId || '',
      discNumber: item.ParentIndexNumber || 0,
      trackNumber: item.IndexNumber || 0,
      durationMs: musicTicksToMilliseconds(item.RunTimeTicks),
      imageUrl: this.primaryImage(imageItemId, imageTag),
      favorite: item.UserData?.IsFavorite === true,
      playbackPositionMs: musicTicksToMilliseconds(item.UserData?.PlaybackPositionTicks)
    }
  }

  page<T>(items: T[], startIndex: number, totalCount: number): MusicPageResult<T> {
    return {
      items: items,
      startIndex: startIndex,
      totalCount: totalCount,
      hasMore: startIndex + items.length < totalCount
    }
  }

  private primaryImage(itemId: string, tag?: string | null): string {
    if (itemId.length === 0) {
      return ''
    }
    const relativeUrl = tag ? this.imageApi.getItemImageUrlById(itemId, ImageType.Primary, { tag: tag }) :
      this.imageApi.getItemImageUrlById(itemId, ImageType.Primary)
    return this.basePath + relativeUrl
  }
}
