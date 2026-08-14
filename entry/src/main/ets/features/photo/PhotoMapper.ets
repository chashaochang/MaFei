import { ImageUrlsApi } from 'sdk'
import {
  BaseItemDto,
  BaseItemKind,
  ImageOrientation,
  ImageType
} from 'sdk/src/main/ets/generated-client/models'
import {
  PHOTO_UNKNOWN_DATE_KEY,
  PhotoAlbumItem,
  PhotoAlbumKind,
  PhotoExifInfo,
  PhotoItem,
  PhotoOrientation,
  PhotoPageResult
} from './PhotoModels'

const PHOTO_THUMBNAIL_SIZE: number = 720
const PHOTO_THUMBNAIL_QUALITY: number = 82
const PHOTO_FULL_IMAGE_SIZE: number = 4096
const PHOTO_FULL_IMAGE_QUALITY: number = 92

export class PhotoMapper {
  private basePath: string
  private imageApi: ImageUrlsApi

  constructor(basePath: string, imageApi: ImageUrlsApi = new ImageUrlsApi()) {
    this.basePath = basePath.endsWith('/') ? basePath.substring(0, basePath.length - 1) : basePath
    this.imageApi = imageApi
  }

  photo(item: BaseItemDto): PhotoItem {
    const id = PhotoMapper.requiredId(item.Id, 'Photo')
    if (item.Type !== BaseItemKind.Photo) {
      throw new Error('Photo item type is invalid')
    }
    const path = item.Path || ''
    const fileName = PhotoMapper.fileName(path, item.Name || '')
    const preferredDate = PhotoMapper.preferredDate(item.PremiereDate, item.DateCreated)
    return {
      id: id,
      name: item.Name || fileName,
      fileName: fileName,
      parentId: item.ParentId || '',
      path: path,
      format: PhotoMapper.format(item.Container, fileName),
      dateTaken: preferredDate,
      dateKey: PhotoMapper.dateKey(preferredDate),
      dateTimestamp: PhotoMapper.dateTimestamp(preferredDate),
      width: PhotoMapper.nonNegativeNumber(item.Width),
      height: PhotoMapper.nonNegativeNumber(item.Height),
      thumbnailUrl: this.imageUrl(
        id, item.ImageTags?.Primary, PHOTO_THUMBNAIL_SIZE, PHOTO_THUMBNAIL_QUALITY),
      fullImageUrl: this.imageUrl(
        id, item.ImageTags?.Primary, PHOTO_FULL_IMAGE_SIZE, PHOTO_FULL_IMAGE_QUALITY),
      favorite: item.UserData?.IsFavorite === true,
      canDownload: item.CanDownload === true,
      exif: this.exif(item)
    }
  }

  album(item: BaseItemDto): PhotoAlbumItem {
    const id = PhotoMapper.requiredId(item.Id, 'Photo album')
    if (item.Type !== BaseItemKind.PhotoAlbum && item.Type !== BaseItemKind.Folder) {
      throw new Error('Photo album item type is invalid')
    }
    return {
      id: id,
      name: item.Name || '',
      parentId: item.ParentId || '',
      kind: item.Type === BaseItemKind.PhotoAlbum ? PhotoAlbumKind.PhotoAlbum : PhotoAlbumKind.Folder,
      thumbnailUrl: this.imageUrl(
        id, item.ImageTags?.Primary, PHOTO_THUMBNAIL_SIZE, PHOTO_THUMBNAIL_QUALITY),
      itemCount: PhotoMapper.nonNegativeNumber(item.RecursiveItemCount ?? item.ChildCount)
    }
  }

  page<T>(items: T[], startIndex: number, returnedCount: number, totalCount: number): PhotoPageResult<T> {
    const normalizedStart = PhotoMapper.nonNegativeInteger(startIndex)
    const normalizedReturned = PhotoMapper.nonNegativeInteger(returnedCount)
    const nextIndex = normalizedStart + normalizedReturned
    const normalizedTotal = Math.max(nextIndex, PhotoMapper.nonNegativeInteger(totalCount))
    return {
      items: items,
      startIndex: normalizedStart,
      nextStartIndex: nextIndex,
      totalCount: normalizedTotal,
      hasMore: nextIndex < normalizedTotal
    }
  }

  static preferredDate(premiereDate?: string | null, dateCreated?: string | null): string {
    if (PhotoMapper.isValidDate(premiereDate)) {
      return premiereDate?.trim() || ''
    }
    if (PhotoMapper.isValidDate(dateCreated)) {
      return dateCreated?.trim() || ''
    }
    return ''
  }

  static dateKey(value?: string | null): string {
    if (!PhotoMapper.isValidDate(value)) {
      return PHOTO_UNKNOWN_DATE_KEY
    }
    return value?.trim().substring(0, 10) || PHOTO_UNKNOWN_DATE_KEY
  }

  static dateTimestamp(value?: string | null): number {
    const key = PhotoMapper.dateKey(value)
    if (key === PHOTO_UNKNOWN_DATE_KEY) {
      return 0
    }
    return Number(key.substring(0, 4)) * 10000 + Number(key.substring(5, 7)) * 100 +
      Number(key.substring(8, 10))
  }

  private exif(item: BaseItemDto): PhotoExifInfo {
    return {
      cameraMake: item.CameraMake || '',
      cameraModel: item.CameraModel || '',
      software: item.Software || '',
      exposureTime: PhotoMapper.optionalNumber(item.ExposureTime),
      focalLength: PhotoMapper.optionalNumber(item.FocalLength),
      orientation: PhotoMapper.orientation(item.ImageOrientation),
      aperture: PhotoMapper.optionalNumber(item.Aperture),
      shutterSpeed: PhotoMapper.optionalNumber(item.ShutterSpeed),
      latitude: PhotoMapper.optionalNumber(item.Latitude),
      longitude: PhotoMapper.optionalNumber(item.Longitude),
      altitude: PhotoMapper.optionalNumber(item.Altitude),
      isoSpeedRating: PhotoMapper.optionalNumber(item.IsoSpeedRating)
    }
  }

  private imageUrl(itemId: string, tag: string | null | undefined, size: number, quality: number): string {
    if (itemId.length === 0) {
      return ''
    }
    const relativeUrl = tag ? this.imageApi.getItemImageUrlById(itemId, ImageType.Primary, {
      tag: tag,
      maxWidth: size,
      maxHeight: size,
      quality: quality
    }) : this.imageApi.getItemImageUrlById(itemId, ImageType.Primary, {
      maxWidth: size,
      maxHeight: size,
      quality: quality
    })
    if (relativeUrl.startsWith('http://') || relativeUrl.startsWith('https://')) {
      return relativeUrl
    }
    return this.basePath + (relativeUrl.startsWith('/') ? relativeUrl : `/${relativeUrl}`)
  }

  private static orientation(value?: ImageOrientation): PhotoOrientation | undefined {
    switch (value) {
      case ImageOrientation.TopRight:
        return PhotoOrientation.MirrorHorizontal
      case ImageOrientation.BottomRight:
        return PhotoOrientation.Rotate180
      case ImageOrientation.BottomLeft:
        return PhotoOrientation.MirrorVertical
      case ImageOrientation.LeftTop:
        return PhotoOrientation.Transpose
      case ImageOrientation.RightTop:
        return PhotoOrientation.Rotate90
      case ImageOrientation.RightBottom:
        return PhotoOrientation.Transverse
      case ImageOrientation.LeftBottom:
        return PhotoOrientation.Rotate270
      case ImageOrientation.TopLeft:
        return PhotoOrientation.Normal
      default:
        return undefined
    }
  }

  private static fileName(path: string, fallback: string): string {
    const slashIndex = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'))
    if (slashIndex >= 0 && slashIndex + 1 < path.length) {
      return path.substring(slashIndex + 1)
    }
    return path.length > 0 ? path : fallback
  }

  private static format(container?: string | null, fileName: string = ''): string {
    if (container && container.trim().length > 0) {
      return container.trim().toLowerCase()
    }
    const dotIndex = fileName.lastIndexOf('.')
    return dotIndex >= 0 && dotIndex + 1 < fileName.length ?
      fileName.substring(dotIndex + 1).toLowerCase() : ''
  }

  private static isValidDate(value?: string | null): boolean {
    if (!value) {
      return false
    }
    const normalized = value.trim()
    if (normalized.length < 10 || normalized.charAt(4) !== '-' || normalized.charAt(7) !== '-') {
      return false
    }
    const year = Number(normalized.substring(0, 4))
    const month = Number(normalized.substring(5, 7))
    const day = Number(normalized.substring(8, 10))
    if (!Number.isInteger(year) || !Number.isInteger(month) || !Number.isInteger(day) ||
      year <= 1 || month < 1 || month > 12 || day < 1) {
      return false
    }
    return day <= PhotoMapper.daysInMonth(year, month)
  }

  private static requiredId(value: string | null | undefined, label: string): string {
    const normalized = value?.trim() || ''
    if (normalized.length === 0) {
      throw new Error(`${label} id is required`)
    }
    return normalized
  }

  private static optionalNumber(value: number | null | undefined): number | undefined {
    return value !== undefined && value !== null && Number.isFinite(value) ? value : undefined
  }

  private static nonNegativeNumber(value: number | null | undefined): number {
    return value !== undefined && value !== null && Number.isFinite(value) && value > 0 ? value : 0
  }

  private static nonNegativeInteger(value: number): number {
    return Number.isFinite(value) ? Math.max(0, Math.floor(value)) : 0
  }

  private static daysInMonth(year: number, month: number): number {
    if (month === 2) {
      const leap = year % 400 === 0 || (year % 4 === 0 && year % 100 !== 0)
      return leap ? 29 : 28
    }
    return month === 4 || month === 6 || month === 9 || month === 11 ? 30 : 31
  }
}
