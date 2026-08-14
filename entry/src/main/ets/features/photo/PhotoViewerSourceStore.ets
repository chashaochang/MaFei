import {
  PhotoExifInfo,
  PhotoItem
} from './PhotoModels'

export interface PhotoViewerSourceSnapshot {
  sourceKey: string
  scope: string
  title: string
  items: PhotoItem[]
}

export interface PhotoViewerSourceStorePort {
  save(scope: string, title: string, items: PhotoItem[]): string
  resolve(sourceKey: string, scope: string): PhotoViewerSourceSnapshot | undefined
  remove(sourceKey: string, scope?: string): boolean
  clearScope(scope: string): number
}

export class PhotoViewerSourceStore implements PhotoViewerSourceStorePort {
  private static shared?: PhotoViewerSourceStore
  private sources: Map<string, PhotoViewerSourceSnapshot> = new Map<string, PhotoViewerSourceSnapshot>()
  private sequence: number = 0

  static instance(): PhotoViewerSourceStore {
    if (!PhotoViewerSourceStore.shared) {
      PhotoViewerSourceStore.shared = new PhotoViewerSourceStore()
    }
    return PhotoViewerSourceStore.shared
  }

  save(scope: string, title: string, items: PhotoItem[]): string {
    const normalizedScope = scope.trim()
    const uniqueItems = this.uniqueItems(items)
    if (normalizedScope.length === 0 || uniqueItems.length === 0) {
      return ''
    }
    const sourceKey = this.nextSourceKey()
    this.sources.set(sourceKey, {
      sourceKey: sourceKey,
      scope: normalizedScope,
      title: title,
      items: uniqueItems
    })
    return sourceKey
  }

  resolve(sourceKey: string, scope: string): PhotoViewerSourceSnapshot | undefined {
    const normalizedKey = sourceKey.trim()
    const normalizedScope = scope.trim()
    if (normalizedKey.length === 0 || normalizedScope.length === 0) {
      return undefined
    }
    const source = this.sources.get(normalizedKey)
    if (!source || source.scope !== normalizedScope) {
      return undefined
    }
    return this.copySnapshot(source)
  }

  remove(sourceKey: string, scope?: string): boolean {
    const normalizedKey = sourceKey.trim()
    if (normalizedKey.length === 0) {
      return false
    }
    const source = this.sources.get(normalizedKey)
    if (!source) {
      return false
    }
    if (scope !== undefined && source.scope !== scope.trim()) {
      return false
    }
    return this.sources.delete(normalizedKey)
  }

  clearScope(scope: string): number {
    const normalizedScope = scope.trim()
    if (normalizedScope.length === 0) {
      return 0
    }
    const keys: string[] = []
    this.sources.forEach((source: PhotoViewerSourceSnapshot, key: string): void => {
      if (source.scope === normalizedScope) {
        keys.push(key)
      }
    })
    keys.forEach((key: string): void => {
      this.sources.delete(key)
    })
    return keys.length
  }

  private uniqueItems(items: PhotoItem[]): PhotoItem[] {
    const ids = new Set<string>()
    const unique: PhotoItem[] = []
    items.forEach((item: PhotoItem): void => {
      const id = item.id.trim()
      if (id.length === 0 || ids.has(id)) {
        return
      }
      ids.add(id)
      unique.push(this.copyItem(item, id))
    })
    return unique
  }

  private copySnapshot(source: PhotoViewerSourceSnapshot): PhotoViewerSourceSnapshot {
    return {
      sourceKey: source.sourceKey,
      scope: source.scope,
      title: source.title,
      items: source.items.map((item: PhotoItem): PhotoItem => this.copyItem(item))
    }
  }

  private copyItem(item: PhotoItem, id: string = item.id): PhotoItem {
    return {
      id: id,
      name: item.name,
      fileName: item.fileName,
      parentId: item.parentId,
      path: item.path,
      format: item.format,
      dateTaken: item.dateTaken,
      dateKey: item.dateKey,
      dateTimestamp: item.dateTimestamp,
      width: item.width,
      height: item.height,
      thumbnailUrl: item.thumbnailUrl,
      fullImageUrl: item.fullImageUrl,
      favorite: item.favorite,
      canDownload: item.canDownload,
      exif: this.copyExif(item.exif)
    }
  }

  private copyExif(exif: PhotoExifInfo): PhotoExifInfo {
    return {
      cameraMake: exif.cameraMake,
      cameraModel: exif.cameraModel,
      software: exif.software,
      exposureTime: exif.exposureTime,
      focalLength: exif.focalLength,
      orientation: exif.orientation,
      aperture: exif.aperture,
      shutterSpeed: exif.shutterSpeed,
      latitude: exif.latitude,
      longitude: exif.longitude,
      altitude: exif.altitude,
      isoSpeedRating: exif.isoSpeedRating
    }
  }

  private nextSourceKey(): string {
    this.sequence += 1
    return `photo-viewer-${Date.now().toString(36)}-${this.sequence.toString(36)}`
  }
}

export const photoViewerSourceStore: PhotoViewerSourceStore = PhotoViewerSourceStore.instance()
