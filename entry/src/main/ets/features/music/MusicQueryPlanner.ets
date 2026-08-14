import {
  BaseItemKind,
  ItemSortBy,
  SortOrder
} from 'sdk/src/main/ets/generated-client/models'
import { MusicAlbum, MusicArtist, MusicLibrarySection, MusicTrack } from './MusicModels'

export interface MusicPageRequest {
  libraryId: string
  section: MusicLibrarySection
  startIndex: number
  limit: number
}

export class MusicQueryPlanner {
  static normalize(request: MusicPageRequest): MusicPageRequest {
    return {
      libraryId: request.libraryId.trim(),
      section: request.section,
      startIndex: Math.max(0, Math.floor(request.startIndex)),
      limit: Math.max(1, Math.min(100, Math.floor(request.limit)))
    }
  }

  static includeItemTypes(section: MusicLibrarySection): BaseItemKind[] {
    switch (section) {
      case MusicLibrarySection.Artists:
        return [BaseItemKind.MusicArtist]
      case MusicLibrarySection.Tracks:
        return [BaseItemKind.Audio]
      case MusicLibrarySection.Albums:
      default:
        return [BaseItemKind.MusicAlbum]
    }
  }

  static sortBy(section: MusicLibrarySection): ItemSortBy[] {
    if (section === MusicLibrarySection.Tracks) {
      return [ItemSortBy.AlbumArtist, ItemSortBy.Album, ItemSortBy.ParentIndexNumber, ItemSortBy.IndexNumber]
    }
    return [ItemSortBy.SortName]
  }

  static sortOrder(section: MusicLibrarySection): SortOrder[] {
    return MusicQueryPlanner.sortBy(section).map((_value: ItemSortBy): SortOrder => SortOrder.Ascending)
  }

  static mergeAlbumsUnique(current: MusicAlbum[], incoming: MusicAlbum[]): MusicAlbum[] {
    const merged: MusicAlbum[] = []
    const ids = new Set<string>()
    current.concat(incoming).forEach((item: MusicAlbum): void => {
      if (item.id.length === 0 || ids.has(item.id)) {
        return
      }
      ids.add(item.id)
      merged.push(item)
    })
    return merged
  }

  static mergeArtistsUnique(current: MusicArtist[], incoming: MusicArtist[]): MusicArtist[] {
    const merged: MusicArtist[] = []
    const ids = new Set<string>()
    current.concat(incoming).forEach((item: MusicArtist): void => {
      if (item.id.length === 0 || ids.has(item.id)) {
        return
      }
      ids.add(item.id)
      merged.push(item)
    })
    return merged
  }

  static mergeTracksUnique(current: MusicTrack[], incoming: MusicTrack[]): MusicTrack[] {
    const merged: MusicTrack[] = []
    const ids = new Set<string>()
    current.concat(incoming).forEach((item: MusicTrack): void => {
      if (item.id.length === 0 || ids.has(item.id)) {
        return
      }
      ids.add(item.id)
      merged.push(item)
    })
    return merged
  }
}
