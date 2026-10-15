import { describe, expect, it } from '@ohos/hypium'
import {
  BaseItemKind,
  ItemSortBy,
  SortOrder
} from 'sdk/src/main/ets/generated-client/models'
import {
  PHOTO_UNKNOWN_DATE_KEY,
  PhotoAlbumItem,
  PhotoAlbumKind,
  PhotoItem,
  PhotoLibrarySection,
  PhotoOrientation
} from '../main/ets/features/photo/PhotoModels'
import { PhotoQueryPlanner } from '../main/ets/features/photo/PhotoQueryPlanner'

function photo(id: string, dateKey: string): PhotoItem {
  return {
    id: id,
    name: id,
    fileName: id + '.jpg',
    parentId: 'library-1',
    path: '',
    format: 'jpg',
    dateTaken: dateKey === PHOTO_UNKNOWN_DATE_KEY ? '' : dateKey,
    dateKey: dateKey,
    dateTimestamp: 0,
    width: 1,
    height: 1,
    thumbnailUrl: '',
    fullImageUrl: '',
    favorite: false,
    canDownload: false,
    exif: {
      cameraMake: '',
      cameraModel: '',
      software: '',
      orientation: PhotoOrientation.Normal
    }
  }
}

function album(id: string): PhotoAlbumItem {
  return {
    id: id,
    name: id,
    parentId: 'library-1',
    kind: PhotoAlbumKind.PhotoAlbum,
    thumbnailUrl: '',
    itemCount: 0
  }
}

export default function photoQueryPlannerTest() {
  describe('PhotoQueryPlanner', () => {
    it('normalizesFinitePagingAndRejectsUnknownSections', 0, () => {
      const normalized = PhotoQueryPlanner.normalize({
        parentId: ' library-1 ',
        section: PhotoLibrarySection.Photos,
        startIndex: Number.NaN,
        limit: Number.POSITIVE_INFINITY
      })
      const bounded = PhotoQueryPlanner.normalize({
        parentId: 'library-1',
        section: PhotoLibrarySection.Albums,
        startIndex: -3,
        limit: 500
      })
      let rejected = false
      try {
        PhotoQueryPlanner.normalize({
          parentId: 'library-1',
          section: 99 as PhotoLibrarySection,
          startIndex: 0,
          limit: 60
        })
      } catch (_error) {
        rejected = true
      }

      expect(normalized.parentId).assertEqual('library-1')
      expect(normalized.startIndex).assertEqual(0)
      expect(normalized.limit).assertEqual(60)
      expect(bounded.startIndex).assertEqual(0)
      expect(bounded.limit).assertEqual(100)
      expect(rejected).assertTrue()
    })

    it('usesStablePhotoAndAlbumQueries', 0, () => {
      const photoSort = PhotoQueryPlanner.sortBy(PhotoLibrarySection.Photos)
      const photoOrder = PhotoQueryPlanner.sortOrder(PhotoLibrarySection.Photos)
      const albumTypes = PhotoQueryPlanner.includeItemTypes(PhotoLibrarySection.Albums)

      expect(PhotoQueryPlanner.includeItemTypes(PhotoLibrarySection.Photos)[0]).assertEqual(BaseItemKind.Photo)
      expect(PhotoQueryPlanner.recursive(PhotoLibrarySection.Photos)).assertTrue()
      expect(PhotoQueryPlanner.recursive(PhotoLibrarySection.Albums)).assertFalse()
      expect(albumTypes[0]).assertEqual(BaseItemKind.PhotoAlbum)
      expect(albumTypes[1]).assertEqual(BaseItemKind.Folder)
      expect(photoSort.length).assertEqual(3)
      expect(photoSort[0]).assertEqual(ItemSortBy.PremiereDate)
      expect(photoSort[1]).assertEqual(ItemSortBy.DateCreated)
      expect(photoSort[2]).assertEqual(ItemSortBy.SortName)
      expect(photoOrder.length).assertEqual(photoSort.length)
      expect(photoOrder[0]).assertEqual(SortOrder.Descending)
      expect(photoOrder[1]).assertEqual(SortOrder.Descending)
      expect(photoOrder[2]).assertEqual(SortOrder.Ascending)
    })

    it('deduplicatesInitialAndAppendedPagesByNonEmptyId', 0, () => {
      const initial = PhotoQueryPlanner.mergeUnique([], [
        photo('photo-1', '2026-08-11'),
        photo('photo-1', '2026-08-11'),
        photo('', '2026-08-11')
      ])
      const appended = PhotoQueryPlanner.mergeUnique(initial, [
        photo('photo-1', '2026-08-11'),
        photo('photo-2', '2026-08-10')
      ])
      const albums = PhotoQueryPlanner.mergeAlbumsUnique([], [album('album-1'), album('album-1'), album('')])

      expect(initial.length).assertEqual(1)
      expect(appended.length).assertEqual(2)
      expect(appended[1].id).assertEqual('photo-2')
      expect(albums.length).assertEqual(1)
    })

    it('groupsNewestDatesFirstAndUnknownDatesLast', 0, () => {
      const groups = PhotoQueryPlanner.groupByDate([
        photo('unknown', PHOTO_UNKNOWN_DATE_KEY),
        photo('older', '2026-07-01'),
        photo('newer', '2026-08-11'),
        photo('newer-2', '2026-08-11')
      ])

      expect(groups.length).assertEqual(3)
      expect(groups[0].key).assertEqual('2026-08-11')
      expect(groups[0].items.length).assertEqual(2)
      expect(groups[2].key).assertEqual(PHOTO_UNKNOWN_DATE_KEY)
    })
  })
}
