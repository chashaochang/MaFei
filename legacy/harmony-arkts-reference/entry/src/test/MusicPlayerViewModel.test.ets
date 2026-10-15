import { describe, expect, it } from '@ohos/hypium'
import {
  MusicPlaybackCandidateStatus,
  MusicPlaybackErrorCode,
  MusicPlaybackSnapshot,
  MusicPlaybackStatus,
  MusicQueueItem,
  MusicRepeatMode
} from '../main/ets/features/music/MusicModels'
import {
  MusicFavoriteRepositoryPort,
  MusicPlayerControllerPort,
  MusicPlayerViewModel
} from '../main/ets/features/music/MusicPlayerViewModel'

function queueItem(): MusicQueueItem {
  return {
    id: 'track-1',
    title: 'Track',
    artist: 'Artist',
    album: 'Album',
    albumId: 'album-1',
    imageUrl: '',
    durationMs: 120000,
    favorite: false,
    playbackPositionMs: 0,
    serverId: 'server|user'
  }
}

function snapshot(): MusicPlaybackSnapshot {
  return {
    queue: [queueItem()],
    currentIndex: 0,
    status: MusicPlaybackStatus.Paused,
    positionMs: 0,
    durationMs: 120000,
    errorCode: MusicPlaybackErrorCode.None,
    errorMessage: '',
    candidateItemId: '',
    candidateStatus: MusicPlaybackCandidateStatus.None,
    candidateErrorCode: MusicPlaybackErrorCode.None,
    candidateErrorMessage: '',
    activeScope: 'server|user',
    repeatMode: MusicRepeatMode.Off
  }
}

class DeferredFavoriteRepository implements MusicFavoriteRepositoryPort {
  scope: string = 'server|user'
  private resolveRequest?: (favorite: boolean) => void

  currentScope(): string {
    return this.scope
  }

  setFavorite(_itemId: string, _favorite: boolean): Promise<boolean> {
    return new Promise<boolean>((resolve: (favorite: boolean) => void) => {
      this.resolveRequest = resolve
    })
  }

  resolve(favorite: boolean): void {
    this.resolveRequest?.(favorite)
  }
}

class RecordingPlayerController implements MusicPlayerControllerPort {
  current: MusicPlaybackSnapshot = snapshot()
  favoriteUpdates: boolean[] = []

  currentSnapshot(): MusicPlaybackSnapshot {
    return this.current
  }

  activeScope(): string {
    return this.current.activeScope
  }

  updateCurrentFavorite(favorite: boolean): void {
    this.favoriteUpdates.push(favorite)
  }
}

export default function musicPlayerViewModelTest() {
  describe('MusicPlayerViewModel', () => {
    it('updatesTheQueueFavoriteOnlyAfterServerConfirmation', 0, async () => {
      const repository = new DeferredFavoriteRepository()
      const controller = new RecordingPlayerController()
      const vm = new MusicPlayerViewModel(repository, controller)

      const request = vm.setCurrentFavorite(true)
      expect(controller.favoriteUpdates.length).assertEqual(0)
      repository.resolve(true)
      await request

      expect(controller.favoriteUpdates.length).assertEqual(1)
      expect(controller.favoriteUpdates[0]).assertTrue()
    })

    it('ignoresFavoriteResultsFromAChangedScope', 0, async () => {
      const repository = new DeferredFavoriteRepository()
      const controller = new RecordingPlayerController()
      const vm = new MusicPlayerViewModel(repository, controller)

      const request = vm.setCurrentFavorite(true)
      repository.scope = 'server-b|user-b'
      repository.resolve(true)
      await request

      expect(controller.favoriteUpdates.length).assertEqual(0)
    })
  })
}
