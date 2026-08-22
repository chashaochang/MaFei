import { describe, expect, it } from '@ohos/hypium'
import { HOME_RECENT_MEDIA_ID, MediaItem, VideoItem } from '../main/ets/features/home/hometab/HomeUIState'
import {
  HomeSnapshotBackend,
  HomeSnapshotContent,
  HomeSnapshotStore
} from '../main/ets/features/home/hometab/HomeSnapshotStore'
import { MediaProviderKind } from '../main/ets/media/MediaProviderKind'
import { MediaRef, MediaRefKind } from '../main/ets/media/MediaRef'
import { MediaImageKind, MediaImageRef, MediaLibraryKind } from '../main/ets/media/catalog/MediaCatalogModels'

class FakeHomeSnapshotBackend implements HomeSnapshotBackend {
  value: string = ''
  flushCount: number = 0

  read(): string {
    return this.value
  }

  write(value: string): void {
    this.value = value
  }

  flush(): void {
    this.flushCount += 1
  }
}

function homeSnapshotSource(accountScope: string): HomeSnapshotContent {
  const movieRef = new MediaRef(MediaProviderKind.FeiniuVideo, accountScope, 'movie-1', MediaRefKind.Movie)
  const libraryRef = new MediaRef(
    MediaProviderKind.FeiniuVideo, accountScope, 'library-1', MediaRefKind.Library)
  const imageRef = new MediaImageRef({
    owner: movieRef,
    kind: MediaImageKind.Backdrop,
    url: 'https://media.example/movie-1/backdrop',
    variant: 'backdrop-1280',
    revision: 'backdrop-1',
    headers: [{ name: 'Authorization', value: 'secret-token' }]
  })
  const mediaList: MediaItem[] = [{
    name: '电影',
    image: '',
    mediaRef: libraryRef,
    id: libraryRef.id,
    childCount: 10,
    kind: MediaRefKind.Movie,
    libraryKind: MediaLibraryKind.Movies
  }]
  const video: VideoItem = {
    id: movieRef.id,
    name: 'Movie One',
    image: imageRef.url,
    mediaRef: movieRef,
    imageRef: imageRef,
    kind: MediaRefKind.Movie,
    score: 8.8,
    tags: ['4K']
  }
  const latestMap = new Map<string, VideoItem[]>()
  latestMap.set(HOME_RECENT_MEDIA_ID, [video])
  return new HomeSnapshotContent(
    mediaList,
    [video],
    [video],
    [],
    mediaList,
    latestMap,
    HOME_RECENT_MEDIA_ID,
    false
  )
}

export default function homeSnapshotStoreTest() {
  describe('HomeSnapshotStore', () => {
    it('restoresScopedHomeContentWithoutPersistingCredentials', 0, () => {
      const backend = new FakeHomeSnapshotBackend()
      const store = new HomeSnapshotStore(backend)
      const accountScope = 'feiniu_video:account-1'
      store.save(accountScope, homeSnapshotSource(accountScope))

      const snapshot = store.load(accountScope)

      expect(backend.flushCount).assertEqual(1)
      expect(backend.value.indexOf('secret-token') >= 0).assertFalse()
      expect(snapshot?.recommendationList.length).assertEqual(1)
      expect(snapshot?.recommendationList[0].mediaRef?.accountScope).assertEqual(accountScope)
      expect(snapshot?.recommendationList[0].imageRef?.headers.length).assertEqual(0)
      expect(snapshot?.latestMap.get(HOME_RECENT_MEDIA_ID)?.length).assertEqual(1)
    })

    it('rejectsAnotherAccountAndExpiredSnapshots', 0, () => {
      const backend = new FakeHomeSnapshotBackend()
      const store = new HomeSnapshotStore(backend)
      const accountScope = 'jellyfin:account-1'
      store.save(accountScope, homeSnapshotSource(accountScope))

      expect(store.load('jellyfin:account-2') === undefined).assertTrue()
      expect(store.load(accountScope, Date.now() + 8 * 24 * 60 * 60 * 1000) === undefined).assertTrue()
    })
  })
}
