import { describe, expect, it } from '@ohos/hypium'
import {
  PhotoAlbumItem,
  PhotoAlbumKind,
  PhotoBrowseErrorCode,
  PhotoItem,
  PhotoLibrarySection,
  PhotoOrientation,
  PhotoPageResult
} from '../main/ets/features/photo/PhotoModels'
import { PhotoLibraryUIState } from '../main/ets/features/photo/PhotoLibraryUIState'
import { PhotoLibraryViewModel } from '../main/ets/features/photo/PhotoLibraryViewModel'
import { PhotoRepository } from '../main/ets/features/photo/PhotoRepository'

function photo(id: string, favorite: boolean = false): PhotoItem {
  return {
    id: id,
    name: id,
    fileName: id + '.jpg',
    parentId: 'library-1',
    path: '',
    format: 'jpg',
    dateTaken: '2026-08-11',
    dateKey: '2026-08-11',
    dateTimestamp: 20260811,
    width: 1,
    height: 1,
    thumbnailUrl: '',
    fullImageUrl: '',
    favorite: favorite,
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
    itemCount: 1
  }
}

function page<T>(items: T[], startIndex: number, nextStartIndex: number,
  hasMore: boolean): PhotoPageResult<T> {
  return {
    items: items,
    startIndex: startIndex,
    nextStartIndex: nextStartIndex,
    totalCount: hasMore ? nextStartIndex + 10 : nextStartIndex,
    hasMore: hasMore
  }
}

class FakePhotoRepository extends PhotoRepository {
  scope: string = 'server-a|user-a'
  photoPages: PhotoPageResult<PhotoItem>[] = []
  albumPages: PhotoPageResult<PhotoAlbumItem>[] = []
  photoStarts: number[] = []
  albumStarts: number[] = []
  failNextPhotos: boolean = false
  failNextAlbums: boolean = false
  private photoPageIndex: number = 0
  private albumPageIndex: number = 0
  private favoriteResolver?: (favorite: boolean) => void

  currentScope(): string {
    return this.scope
  }

  classifyError(_error: Object): PhotoBrowseErrorCode {
    return PhotoBrowseErrorCode.RequestFailed
  }

  async getPhotos(_parentId: string, startIndex: number = 0,
    _limit: number = 60): Promise<PhotoPageResult<PhotoItem>> {
    this.photoStarts.push(startIndex)
    if (this.failNextPhotos) {
      this.failNextPhotos = false
      throw new Error('photo load failed')
    }
    const result = this.photoPages[this.photoPageIndex]
    this.photoPageIndex++
    return result
  }

  async getAlbums(_parentId: string, startIndex: number = 0,
    _limit: number = 60): Promise<PhotoPageResult<PhotoAlbumItem>> {
    this.albumStarts.push(startIndex)
    if (this.failNextAlbums) {
      this.failNextAlbums = false
      throw new Error('album load failed')
    }
    const result = this.albumPages[this.albumPageIndex]
    this.albumPageIndex++
    return result
  }

  async getPhoto(photoId: string): Promise<PhotoItem> {
    return photo(photoId)
  }

  async getAlbum(albumId: string): Promise<PhotoAlbumItem> {
    return album(albumId)
  }

  setFavorite(_photoId: string, _favorite: boolean): Promise<boolean> {
    return new Promise<boolean>((resolve: (favorite: boolean) => void) => {
      this.favoriteResolver = resolve
    })
  }

  resolveFavorite(favorite: boolean): void {
    this.favoriteResolver?.(favorite)
  }
}

export default function photoLibraryViewModelTest() {
  describe('PhotoLibraryViewModel', () => {
    it('deduplicatesInitialAndAppendedPagesWhileUsingServerCursors', 0, async () => {
      const repository = new FakePhotoRepository()
      repository.photoPages = [
        page([photo('photo-1'), photo('photo-1')], 0, 4, true),
        page([photo('photo-1'), photo('photo-2')], 4, 7, false)
      ]
      const ui = new PhotoLibraryUIState({} as UIContext)
      const vm = new PhotoLibraryViewModel(ui, repository)

      await vm.init(' library-1 ', 'Photos')
      await vm.loadMoreCurrent()

      expect(repository.photoStarts.join(',')).assertEqual('0,4')
      expect(ui.photos.length).assertEqual(2)
      expect(ui.photos[1].id).assertEqual('photo-2')
      expect(ui.photosNextIndex).assertEqual(7)
      expect(ui.photosHasMore).assertFalse()
    })

    it('keepsExistingContentWhenLoadMoreFails', 0, async () => {
      const repository = new FakePhotoRepository()
      repository.photoPages = [page([photo('photo-1')], 0, 3, true)]
      const ui = new PhotoLibraryUIState({} as UIContext)
      const vm = new PhotoLibraryViewModel(ui, repository)

      await vm.init('library-1', 'Photos')
      repository.failNextPhotos = true
      await vm.loadMoreCurrent()

      expect(ui.photos.length).assertEqual(1)
      expect(ui.photos[0].id).assertEqual('photo-1')
      expect(ui.photosLoadMoreFailed).assertTrue()
    })

    it('retriesPhotoLoadMoreFromTheFailedCursor', 0, async () => {
      const repository = new FakePhotoRepository()
      repository.photoPages = [
        page([photo('photo-1')], 0, 3, true),
        page([photo('photo-2')], 3, 5, false)
      ]
      const ui = new PhotoLibraryUIState({} as UIContext)
      const vm = new PhotoLibraryViewModel(ui, repository)

      await vm.init('library-1', 'Photos')
      repository.failNextPhotos = true
      await vm.loadMoreCurrent()
      await vm.retryLoadMoreCurrent()

      expect(repository.photoStarts.join(',')).assertEqual('0,3,3')
      expect(ui.photosLoadMoreFailed).assertFalse()
      expect(ui.photos.length).assertEqual(2)
      expect(ui.photos[1].id).assertEqual('photo-2')
      expect(ui.photosHasMore).assertFalse()
    })

    it('retriesAlbumLoadMoreFromTheFailedCursor', 0, async () => {
      const repository = new FakePhotoRepository()
      repository.photoPages = [page([], 0, 0, false)]
      repository.albumPages = [
        page([album('album-1')], 0, 1, true),
        page([album('album-2')], 1, 2, false)
      ]
      const ui = new PhotoLibraryUIState({} as UIContext)
      const vm = new PhotoLibraryViewModel(ui, repository)

      await vm.init('library-1', 'Photos')
      await vm.selectSection(1)
      repository.failNextAlbums = true
      await vm.loadMoreCurrent()
      await vm.retryLoadMoreCurrent()

      expect(repository.albumStarts.join(',')).assertEqual('0,1,1')
      expect(ui.albumsLoadMoreFailed).assertFalse()
      expect(ui.albums.length).assertEqual(2)
      expect(ui.albums[1].id).assertEqual('album-2')
      expect(ui.albumsHasMore).assertFalse()
    })

    it('preservesTheSelectedSectionAcrossRefresh', 0, async () => {
      const repository = new FakePhotoRepository()
      repository.photoPages = [page([], 0, 0, false)]
      repository.albumPages = [
        page([album('album-1')], 0, 1, false),
        page([album('album-2')], 0, 1, false)
      ]
      const ui = new PhotoLibraryUIState({} as UIContext)
      const vm = new PhotoLibraryViewModel(ui, repository)

      await vm.init('library-1', 'Photos')
      await vm.selectSection(1)
      await vm.refreshCurrent()

      expect(ui.selectedSection).assertEqual(PhotoLibrarySection.Albums)
      expect(ui.albums[0].id).assertEqual('album-2')
    })

    it('updatesFavoritesOnlyAfterCurrentScopeServerConfirmation', 0, async () => {
      const repository = new FakePhotoRepository()
      repository.photoPages = [page([photo('photo-1')], 0, 1, false)]
      const ui = new PhotoLibraryUIState({} as UIContext)
      const vm = new PhotoLibraryViewModel(ui, repository)

      await vm.init('library-1', 'Photos')
      const request = vm.setPhotoFavorite('photo-1', true)
      expect(ui.photos[0].favorite).assertFalse()
      repository.resolveFavorite(true)
      expect(await request).assertTrue()
      expect(ui.photos[0].favorite).assertTrue()

      const staleRequest = vm.setPhotoFavorite('photo-1', false)
      repository.scope = 'server-b|user-b'
      repository.resolveFavorite(false)
      expect(await staleRequest).assertFalse()
      expect(ui.photos[0].favorite).assertTrue()
    })
  })
}
