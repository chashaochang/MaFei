import {
  BaseItemKind,
  ItemSortBy,
  SortOrder
} from 'sdk/src/main/ets/generated-client/models'
import {
  PHOTO_UNKNOWN_DATE_KEY,
  PhotoAlbumItem,
  PhotoDateGroup,
  PhotoItem,
  PhotoLibrarySection
} from './PhotoModels'

export interface PhotoPageRequest {
  parentId: string
  section: PhotoLibrarySection
  startIndex: number
  limit: number
}

export class PhotoQueryPlanner {
  static normalize(request: PhotoPageRequest): PhotoPageRequest {
    const startIndex = Number.isFinite(request.startIndex) ? Math.floor(request.startIndex) : 0
    const limit = Number.isFinite(request.limit) ? Math.floor(request.limit) : 60
    return {
      parentId: request.parentId.trim(),
      section: PhotoQueryPlanner.section(request.section),
      startIndex: Math.max(0, startIndex),
      limit: Math.max(1, Math.min(100, limit))
    }
  }

  static includeItemTypes(section: PhotoLibrarySection): BaseItemKind[] {
    if (PhotoQueryPlanner.section(section) === PhotoLibrarySection.Albums) {
      return [BaseItemKind.PhotoAlbum, BaseItemKind.Folder]
    }
    return [BaseItemKind.Photo]
  }

  static recursive(section: PhotoLibrarySection): boolean {
    return PhotoQueryPlanner.section(section) === PhotoLibrarySection.Photos
  }

  static sortBy(section: PhotoLibrarySection): ItemSortBy[] {
    return PhotoQueryPlanner.section(section) === PhotoLibrarySection.Albums ?
      [ItemSortBy.SortName] : [ItemSortBy.PremiereDate, ItemSortBy.DateCreated, ItemSortBy.SortName]
  }

  static sortOrder(section: PhotoLibrarySection): SortOrder[] {
    return PhotoQueryPlanner.section(section) === PhotoLibrarySection.Albums ?
      [SortOrder.Ascending] : [SortOrder.Descending, SortOrder.Descending, SortOrder.Ascending]
  }

  static mergeUnique(current: PhotoItem[], incoming: PhotoItem[]): PhotoItem[] {
    const merged: PhotoItem[] = []
    const ids = new Set<string>()
    current.concat(incoming).forEach((item: PhotoItem): void => {
      if (item.id.length === 0 || ids.has(item.id)) {
        return
      }
      ids.add(item.id)
      merged.push(item)
    })
    return merged
  }

  static mergeAlbumsUnique(current: PhotoAlbumItem[], incoming: PhotoAlbumItem[]): PhotoAlbumItem[] {
    const merged: PhotoAlbumItem[] = []
    const ids = new Set<string>()
    current.concat(incoming).forEach((item: PhotoAlbumItem): void => {
      if (item.id.length === 0 || ids.has(item.id)) {
        return
      }
      ids.add(item.id)
      merged.push(item)
    })
    return merged
  }

  static groupByDate(items: PhotoItem[]): PhotoDateGroup[] {
    const groups: PhotoDateGroup[] = []
    items.forEach((item: PhotoItem): void => {
      const key = item.dateKey.length > 0 ? item.dateKey : PHOTO_UNKNOWN_DATE_KEY
      const index = groups.findIndex((group: PhotoDateGroup): boolean => group.key === key)
      if (index >= 0) {
        groups[index].items.push(item)
      } else {
        groups.push({
          key: key,
          items: [item]
        })
      }
    })
    groups.sort((left: PhotoDateGroup, right: PhotoDateGroup): number => {
      if (left.key === PHOTO_UNKNOWN_DATE_KEY) {
        return right.key === PHOTO_UNKNOWN_DATE_KEY ? 0 : 1
      }
      if (right.key === PHOTO_UNKNOWN_DATE_KEY) {
        return -1
      }
      return right.key.localeCompare(left.key)
    })
    return groups
  }

  private static section(section: PhotoLibrarySection): PhotoLibrarySection {
    if (section === PhotoLibrarySection.Photos || section === PhotoLibrarySection.Albums) {
      return section
    }
    throw new Error('Photo library section is invalid')
  }
}
