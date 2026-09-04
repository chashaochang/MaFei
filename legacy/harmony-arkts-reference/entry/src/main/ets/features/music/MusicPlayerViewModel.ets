import { MusicPlaybackSnapshot } from './MusicModels'
import { MusicPlaybackController } from './MusicPlaybackController'
import { MusicRepository } from './MusicRepository'

export interface MusicFavoriteRepositoryPort {
  currentScope(): string
  setFavorite(itemId: string, favorite: boolean): Promise<boolean>
}

export interface MusicPlayerControllerPort {
  currentSnapshot(): MusicPlaybackSnapshot
  activeScope(): string
  updateCurrentFavorite(favorite: boolean): void
}

@ObservedV2
export class MusicPlayerViewModel {
  @Trace favoritePending: boolean = false
  @Trace favoriteFailed: boolean = false
  private repository: MusicFavoriteRepositoryPort
  private controller: MusicPlayerControllerPort

  constructor(repository?: MusicFavoriteRepositoryPort, controller?: MusicPlayerControllerPort) {
    this.repository = repository || new MusicRepository()
    this.controller = controller || MusicPlaybackController.instance()
  }

  async setCurrentFavorite(favorite: boolean): Promise<boolean> {
    if (this.favoritePending) {
      return false
    }
    const snapshot = this.controller.currentSnapshot()
    if (snapshot.currentIndex < 0 || snapshot.currentIndex >= snapshot.queue.length) {
      return false
    }
    const itemId = snapshot.queue[snapshot.currentIndex].id
    const requestScope = this.controller.activeScope()
    if (requestScope.length === 0 || requestScope !== this.repository.currentScope()) {
      return false
    }
    this.favoritePending = true
    this.favoriteFailed = false
    try {
      const confirmedFavorite = await this.repository.setFavorite(itemId, favorite)
      const current = this.controller.currentSnapshot()
      const currentItemId = current.currentIndex >= 0 && current.currentIndex < current.queue.length ?
        current.queue[current.currentIndex].id : ''
      if (requestScope !== this.controller.activeScope() ||
        requestScope !== this.repository.currentScope() || currentItemId !== itemId) {
        return false
      }
      this.controller.updateCurrentFavorite(confirmedFavorite)
      return true
    } catch (_error) {
      this.favoriteFailed = true
      return false
    } finally {
      this.favoritePending = false
    }
  }
}
