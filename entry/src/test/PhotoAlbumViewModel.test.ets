import { describe, expect, it } from '@ohos/hypium'
import {
  PhotoAlbumItem,
  PhotoAlbumKind,
  PhotoBrowseErrorCode,
  PhotoItem,
  PhotoOrientation,
  PhotoPageResult
} from '../main/ets/features/photo/PhotoModels'
import {
  PhotoAlbumContentState,
  PhotoAlbumUIState
} from '../main/ets/features/photo/PhotoAlbumUIState'
import { PhotoAlbumViewModel } from '../main/ets/features/photo/PhotoAlbumViewModel'
import { PhotoRepository } from '../main/ets/features/photo/PhotoRepository'

function photo(id: string): PhotoItem {
  return {
    id: id,
    name: id,
    fileName: id + '.jpg',
    parentId: 'album-1',
    path: '',
    format: 'jpg',
    dateTaken: '2026-08-11',
    dateKey: '2026-08-11',
    dateTimestamp: 20260811,
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

function page(items: PhotoItem[], startIndex: number, nextStartIndex: number,
  hasMore: boolean): PhotoPageResult<PhotoItem> {
  return {
    items: items,
    startIndex: startIndex,
    nextStartIndex: nextStartIndex,
    totalCount: hasMore ? nextStartIndex + 10 : nextStartIndex,
    hasMore: hasMore
  }
}

async function settle(): Promise<void> {
  await Promise.resolve()
  await Promise.resolve()
  await Promise.resolve()
}

class FakeAlbumPhotoRepository extends PhotoRepository {
  scope: string = 'server-a|user-a'
  pages: PhotoPageResult<PhotoItem>[] = []
  starts: number[] = []
  private pageIndex: number = 0

  currentScope(): string {
    return this.scope
  }

  classifyError(_error: Object): PhotoBrowseErrorCode {
    return PhotoBrowseErrorCode.RequestFailed
  }

  async getPhotos(_parentId: string, startIndex: number = 0,
    _limit: number = 60): Promise<PhotoPageResult<PhotoItem>> {
    this.starts.push(startIndex)
    const result = this.pages[this.pageIndex]
    this.pageIndex++
    return result
  }

  async getAlbums(_parentId: string, _startIndex: number = 0,
    _limit: number = 60): Promise<PhotoPageResult<PhotoAlbumItem>> {
    return {
      items: [],
      startIndex: 0,
      nextStartIndex: 0,
      totalCount: 0,
      hasMore: false
    }
  }

  async getPhoto(photoId: string): Promise<PhotoItem> {
    return photo(photoId)
  }

  async getAlbum(albumId: string): Promise<PhotoAlbumItem> {
    return {
      id: albumId,
      name: albumId,
      parentId: '',
      kind: PhotoAlbumKind.PhotoAlbum,
      thumbnailUrl: '',
      itemCount: 0
    }
  }

  async setFavorite(_photoId: string, favorite: boolean): Promise<boolean> {
    return favorite
  }
}

export default function photoAlbumViewModelTest() {
  describe('PhotoAlbumViewModel', () => {
    it('deduplicatesPagesAndLoadsMoreFromTheServerCursor', 0, async () => {
      const repository = new FakeAlbumPhotoRepository()
      repository.pages = [
        page([photo('photo-1'), photo('photo-1')], 0, 5, true),
        page([photo('photo-1'), photo('photo-2')], 5, 8, false)
      ]
      const ui = new PhotoAlbumUIState({} as UIContext)
      const vm = new PhotoAlbumViewModel(ui, repository)

      vm.init(' album-1 ', 'Album')
      await settle()
      vm.loadMore()
      await settle()

      expect(repository.starts.join(',')).assertEqual('0,5')
      expect(ui.photos.length).assertEqual(2)
      expect(ui.photos[1].id).assertEqual('photo-2')
      expect(ui.nextIndex).assertEqual(8)
      expect(ui.hasMore).assertFalse()
      expect(ui.state).assertEqual(PhotoAlbumContentState.Content)
    })

    it('rebuildsDateGroupsAfterFavoriteChanges', 0, async () => {
      const repository = new FakeAlbumPhotoRepository()
      repository.pages = [page([photo('photo-1')], 0, 1, false)]
      const ui = new PhotoAlbumUIState({} as UIContext)
      const vm = new PhotoAlbumViewModel(ui, repository)

      vm.init('album-1', 'Album')
      await settle()
      vm.applyFavorite('photo-1', true)

      expect(ui.photos[0].favorite).assertTrue()
      expect(ui.photoGroups.length).assertEqual(1)
      expect(ui.photoGroups[0].items[0].favorite).assertTrue()
    })

    it('rejectsAnEmptyAlbumIdWithoutIssuingARequest', 0, async () => {
      const repository = new FakeAlbumPhotoRepository()
      const ui = new PhotoAlbumUIState({} as UIContext)
      const vm = new PhotoAlbumViewModel(ui, repository)

      vm.init(' ', 'Album')
      await settle()

      expect(repository.starts.length).assertEqual(0)
      expect(ui.state).assertEqual(PhotoAlbumContentState.Error)
      expect(ui.errorCode).assertEqual(PhotoBrowseErrorCode.NotFound)
    })
  })
}
