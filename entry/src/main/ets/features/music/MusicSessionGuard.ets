import { MusicPlaybackController } from './MusicPlaybackController'

export class MusicSessionGuard {
  private static pending?: Promise<void>

  static stopAndClear(): Promise<void> {
    const pending = MusicSessionGuard.pending
    if (pending) {
      return pending
    }
    const cleanup = MusicPlaybackController.instance()
      .stopAndClear()
      .finally((): void => {
        MusicSessionGuard.pending = undefined
      })
    MusicSessionGuard.pending = cleanup
    return cleanup
  }
}
