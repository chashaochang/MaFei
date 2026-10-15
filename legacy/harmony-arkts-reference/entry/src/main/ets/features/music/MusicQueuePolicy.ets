import { MusicQueueItem, MusicRepeatMode, MusicTrack } from './MusicModels'

export interface MusicQueueSnapshot {
  items: MusicQueueItem[]
  currentIndex: number
  scope: string
}

export interface MusicQueueMutation {
  queue: MusicQueueSnapshot
  changed: boolean
}

export class MusicQueuePolicy {
  static create(tracks: MusicTrack[], startIndex: number, scope: string): MusicQueueSnapshot {
    if (tracks.length === 0 || startIndex < 0 || startIndex >= tracks.length || scope.length === 0) {
      return MusicQueuePolicy.clear(scope)
    }
    return {
      items: tracks.map((track: MusicTrack): MusicQueueItem => ({
        id: track.id,
        title: track.title,
        artist: track.artist,
        album: track.album,
        albumId: track.albumId,
        imageUrl: track.imageUrl,
        durationMs: track.durationMs,
        favorite: track.favorite,
        playbackPositionMs: track.playbackPositionMs,
        serverId: scope
      })),
      currentIndex: startIndex,
      scope: scope
    }
  }

  static nextIndex(queue: MusicQueueSnapshot): number {
    const next = queue.currentIndex + 1
    return next >= 0 && next < queue.items.length ? next : -1
  }

  static previousIndex(queue: MusicQueueSnapshot): number {
    const previous = queue.currentIndex - 1
    return previous >= 0 && previous < queue.items.length ? previous : -1
  }

  static jumpIndex(queue: MusicQueueSnapshot, index: number): number {
    return index >= 0 && index < queue.items.length ? index : -1
  }

  static completionIndex(queue: MusicQueueSnapshot, repeatMode: MusicRepeatMode): number {
    if (queue.items.length === 0 || queue.currentIndex < 0) {
      return -1
    }
    if (repeatMode === MusicRepeatMode.One) {
      return queue.currentIndex
    }
    if (repeatMode === MusicRepeatMode.All) {
      return 0
    }
    return -1
  }

  static removeAt(queue: MusicQueueSnapshot, index: number): MusicQueueMutation {
    if (index < 0 || index >= queue.items.length || index === queue.currentIndex) {
      return { queue: MusicQueuePolicy.copy(queue), changed: false }
    }
    const items = queue.items.slice()
    items.splice(index, 1)
    return {
      queue: {
        items: items,
        currentIndex: index < queue.currentIndex ? queue.currentIndex - 1 : queue.currentIndex,
        scope: queue.scope
      },
      changed: true
    }
  }

  static clear(scope: string = ''): MusicQueueSnapshot {
    return { items: [], currentIndex: -1, scope: scope }
  }

  static matchesScope(queue: MusicQueueSnapshot, scope: string): boolean {
    return queue.scope.length > 0 && queue.scope === scope
  }

  private static copy(queue: MusicQueueSnapshot): MusicQueueSnapshot {
    return {
      items: queue.items.slice(),
      currentIndex: queue.currentIndex,
      scope: queue.scope
    }
  }
}
