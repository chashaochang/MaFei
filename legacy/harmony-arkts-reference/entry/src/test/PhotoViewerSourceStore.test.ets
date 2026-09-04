import { describe, expect, it } from '@ohos/hypium'
import {
  PhotoItem,
  PhotoOrientation
} from '../main/ets/features/photo/PhotoModels'
import { PhotoViewerSourceStore } from '../main/ets/features/photo/PhotoViewerSourceStore'

function photo(id: string, name: string = 'Photo'): PhotoItem {
  return {
    id: id,
    name: name,
    fileName: 'photo.jpg',
    parentId: 'album-1',
    path: '/photos/photo.jpg',
    format: 'jpg',
    dateTaken: '2026-08-11T10:00:00Z',
    dateKey: '2026-08-11',
    dateTimestamp: 20260811,
    width: 4000,
    height: 3000,
    thumbnailUrl: 'https://photos/thumbnail',
    fullImageUrl: 'https://photos/full',
    favorite: false,
    canDownload: true,
    exif: {
      cameraMake: 'Huawei',
      cameraModel: 'Camera',
      software: 'MaFei',
      orientation: PhotoOrientation.Normal,
      isoSpeedRating: 100
    }
  }
}

export default function photoViewerSourceStoreTest() {
  describe('PhotoViewerSourceStore', () => {
    it('rejectsEmptyScopesAndSourcesWithoutValidItems', 0, () => {
      const store = new PhotoViewerSourceStore()

      expect(store.save('   ', 'Photos', [photo('photo-1')])).assertEqual('')
      expect(store.save('scope-a', 'Photos', [])).assertEqual('')
      expect(store.save('scope-a', 'Photos', [photo('   ')])).assertEqual('')
    })

    it('trimsIdsAndDeduplicatesTheSavedSource', 0, () => {
      const store = new PhotoViewerSourceStore()
      const sourceKey = store.save('  scope-a  ', 'Photos', [
        photo(' photo-1 ', 'First'),
        photo('photo-1', 'Duplicate'),
        photo(' photo-2 ', 'Second')
      ])
      const snapshot = store.resolve(sourceKey, 'scope-a')

      expect(sourceKey.length > 0).assertTrue()
      expect(snapshot !== undefined).assertTrue()
      expect(snapshot?.scope).assertEqual('scope-a')
      expect(snapshot?.items.length).assertEqual(2)
      expect(snapshot?.items[0].id).assertEqual('photo-1')
      expect(snapshot?.items[0].name).assertEqual('First')
      expect(snapshot?.items[1].id).assertEqual('photo-2')
    })

    it('isolatesSavedAndResolvedSnapshotsWithDeepCopies', 0, () => {
      const store = new PhotoViewerSourceStore()
      const input = photo('photo-1')
      const sourceKey = store.save('scope-a', 'Photos', [input])

      input.name = 'Changed input'
      input.exif.cameraMake = 'Changed input camera'
      const first = store.resolve(sourceKey, 'scope-a')
      expect(first?.items[0].name).assertEqual('Photo')
      expect(first?.items[0].exif.cameraMake).assertEqual('Huawei')

      if (first) {
        first.items[0].name = 'Changed result'
        first.items[0].exif.cameraMake = 'Changed result camera'
        first.items.push(photo('photo-2'))
      }
      const second = store.resolve(sourceKey, 'scope-a')
      expect(second?.items.length).assertEqual(1)
      expect(second?.items[0].name).assertEqual('Photo')
      expect(second?.items[0].exif.cameraMake).assertEqual('Huawei')
    })

    it('keepsResolveAndRemoveBoundToTheOwningScope', 0, () => {
      const store = new PhotoViewerSourceStore()
      const sourceKey = store.save('scope-a', 'Photos', [photo('photo-1')])

      expect(store.resolve(sourceKey, 'scope-b') === undefined).assertTrue()
      expect(store.remove(sourceKey, 'scope-b')).assertFalse()
      expect(store.resolve(sourceKey, 'scope-a') !== undefined).assertTrue()
      expect(store.remove(` ${sourceKey} `, ' scope-a ')).assertTrue()
      expect(store.resolve(sourceKey, 'scope-a') === undefined).assertTrue()
    })

    it('clearsOnlySourcesOwnedByTheRequestedScope', 0, () => {
      const store = new PhotoViewerSourceStore()
      const first = store.save('scope-a', 'First', [photo('photo-1')])
      const second = store.save('scope-a', 'Second', [photo('photo-2')])
      const other = store.save('scope-b', 'Other', [photo('photo-3')])

      expect(store.clearScope(' scope-a ')).assertEqual(2)
      expect(store.resolve(first, 'scope-a') === undefined).assertTrue()
      expect(store.resolve(second, 'scope-a') === undefined).assertTrue()
      expect(store.resolve(other, 'scope-b') !== undefined).assertTrue()
      expect(store.clearScope('   ')).assertEqual(0)
    })
  })
}
