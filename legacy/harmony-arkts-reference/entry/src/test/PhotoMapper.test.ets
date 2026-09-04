import { describe, expect, it } from '@ohos/hypium'
import {
  BaseItemDto,
  BaseItemKind
} from 'sdk/src/main/ets/generated-client/models'
import { PhotoMapper } from '../main/ets/features/photo/PhotoMapper'

function rejects(action: () => void): boolean {
  try {
    action()
  } catch (_error) {
    return true
  }
  return false
}

export default function photoMapperTest() {
  describe('PhotoMapper', () => {
    const mapper = new PhotoMapper('https://photos.example/')

    it('fallsBackFromMalformedPremiereDateAndReadsWindowsExtensions', 0, () => {
      const photo = mapper.photo({
        Id: 'photo-1',
        Type: BaseItemKind.Photo,
        Name: '',
        Path: 'C:\\Pictures\\Trip.Final.JPEG',
        PremiereDate: '2026-02-30T10:00:00Z',
        DateCreated: '2025-12-31T08:00:00Z'
      })

      expect(photo.fileName).assertEqual('Trip.Final.JPEG')
      expect(photo.format).assertEqual('jpeg')
      expect(photo.dateTaken).assertEqual('2025-12-31T08:00:00Z')
      expect(photo.dateKey).assertEqual('2025-12-31')
    })

    it('keepsExifNumbersAbsentAndCanDownloadFailClosed', 0, () => {
      const missing = mapper.photo({ Id: 'photo-1', Type: BaseItemKind.Photo })
      const denied = mapper.photo({ Id: 'photo-2', Type: BaseItemKind.Photo, CanDownload: false })
      const allowed = mapper.photo({ Id: 'photo-3', Type: BaseItemKind.Photo, CanDownload: true })

      expect(missing.exif.exposureTime === undefined).assertTrue()
      expect(missing.exif.focalLength === undefined).assertTrue()
      expect(missing.exif.orientation === undefined).assertTrue()
      expect(missing.canDownload).assertFalse()
      expect(denied.canDownload).assertFalse()
      expect(allowed.canDownload).assertTrue()
    })

    it('usesRecursiveAlbumCountWithoutDiscardingZero', 0, () => {
      const recursiveWins = mapper.album({
        Id: 'album-1',
        Type: BaseItemKind.PhotoAlbum,
        RecursiveItemCount: 0,
        ChildCount: 9
      })
      const childFallback = mapper.album({
        Id: 'folder-1',
        Type: BaseItemKind.Folder,
        ChildCount: 4
      })

      expect(recursiveWins.itemCount).assertEqual(0)
      expect(childFallback.itemCount).assertEqual(4)
    })

    it('boundsPrimaryThumbnailAndViewerImages', 0, () => {
      const photo = mapper.photo({
        Id: 'photo-image',
        Type: BaseItemKind.Photo,
        ImageTags: { Primary: 'tag-1' }
      })

      expect(photo.thumbnailUrl.includes('/Images/Primary')).assertTrue()
      expect(photo.thumbnailUrl.includes('maxWidth=720')).assertTrue()
      expect(photo.thumbnailUrl.includes('maxHeight=720')).assertTrue()
      expect(photo.fullImageUrl.includes('/Images/Primary')).assertTrue()
      expect(photo.fullImageUrl.includes('maxWidth=4096')).assertTrue()
      expect(photo.fullImageUrl.includes('maxHeight=4096')).assertTrue()
    })

    it('normalizesNonFiniteServerPageValues', 0, () => {
      const page = mapper.page<string>([], Number.NaN, Number.NaN, Number.POSITIVE_INFINITY)

      expect(page.startIndex).assertEqual(0)
      expect(page.nextStartIndex).assertEqual(0)
      expect(page.totalCount).assertEqual(0)
      expect(page.hasMore).assertFalse()
    })

    it('rejectsEmptyIdsAndMismatchedDtoKinds', 0, () => {
      const emptyId: BaseItemDto = { Id: ' ', Type: BaseItemKind.Photo }
      const movie: BaseItemDto = { Id: 'movie-1', Type: BaseItemKind.Movie }
      const photo: BaseItemDto = { Id: 'photo-1', Type: BaseItemKind.Photo }

      expect(rejects(() => mapper.photo(emptyId))).assertTrue()
      expect(rejects(() => mapper.photo(movie))).assertTrue()
      expect(rejects(() => mapper.album(photo))).assertTrue()
    })
  })
}
