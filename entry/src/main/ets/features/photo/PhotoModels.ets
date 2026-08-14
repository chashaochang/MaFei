export const PHOTO_UNKNOWN_DATE_KEY: string = 'unknown'

export enum PhotoLibrarySection {
  Photos,
  Albums
}

export enum PhotoAlbumKind {
  PhotoAlbum,
  Folder
}

export enum PhotoBrowseErrorCode {
  None,
  AuthenticationExpired,
  PermissionDenied,
  NotFound,
  Offline,
  RequestFailed
}

export enum PhotoOrientation {
  Normal,
  MirrorHorizontal,
  Rotate180,
  MirrorVertical,
  Transpose,
  Rotate90,
  Transverse,
  Rotate270
}

export function photoOrientationRotation(orientation: PhotoOrientation): number {
  switch (orientation) {
    case PhotoOrientation.Rotate90:
    case PhotoOrientation.Transpose:
      return 90
    case PhotoOrientation.Rotate180:
      return 180
    case PhotoOrientation.Rotate270:
    case PhotoOrientation.Transverse:
      return 270
    default:
      return 0
  }
}

export function photoOrientationMirrored(orientation: PhotoOrientation): boolean {
  return orientation === PhotoOrientation.MirrorHorizontal ||
    orientation === PhotoOrientation.MirrorVertical ||
    orientation === PhotoOrientation.Transpose ||
    orientation === PhotoOrientation.Transverse
}

export interface PhotoExifInfo {
  cameraMake: string
  cameraModel: string
  software: string
  exposureTime?: number
  focalLength?: number
  orientation?: PhotoOrientation
  aperture?: number
  shutterSpeed?: number
  latitude?: number
  longitude?: number
  altitude?: number
  isoSpeedRating?: number
}

export interface PhotoItem {
  id: string
  name: string
  fileName: string
  parentId: string
  path: string
  format: string
  dateTaken: string
  dateKey: string
  dateTimestamp: number
  width: number
  height: number
  thumbnailUrl: string
  fullImageUrl: string
  favorite: boolean
  canDownload: boolean
  exif: PhotoExifInfo
}

export interface PhotoAlbumItem {
  id: string
  name: string
  parentId: string
  kind: PhotoAlbumKind
  thumbnailUrl: string
  itemCount: number
}

export interface PhotoDateGroup {
  key: string
  items: PhotoItem[]
}

export interface PhotoPageResult<T> {
  items: T[]
  startIndex: number
  nextStartIndex: number
  totalCount: number
  hasMore: boolean
}

export interface PhotoViewerRouteParam {
  sourceKey: string
  initialPhotoId: string
}

export enum PhotoSaveState {
  Idle,
  Downloading,
  Saved,
  AuthorizationCancelled,
  Failed
}

export interface PhotoRequestSession {
  scope: string
  basePath: string
  userId: string
}
