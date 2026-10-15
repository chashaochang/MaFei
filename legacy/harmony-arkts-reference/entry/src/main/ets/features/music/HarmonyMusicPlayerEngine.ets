import { audio } from '@kit.AudioKit'
import { BusinessError } from '@kit.BasicServicesKit'
import { media } from '@kit.MediaKit'
import { MusicPlayerEngine, MusicPlayerEngineListener } from './MusicPlayerEngine'

class MusicPlayerSlot {
  player: media.AVPlayer
  generation: number
  requestId: number
  lifecycle: number
  listener: MusicPlayerEngineListener
  prepareStarted: boolean = false
  prepared: boolean = false
  settled: boolean = false
  released: boolean = false
  failed: boolean = false
  errorNotified: boolean = false
  resolvePreparation?: () => void
  rejectPreparation?: (reason?: Error) => void

  constructor(player: media.AVPlayer, generation: number, requestId: number, lifecycle: number,
    listener: MusicPlayerEngineListener) {
    this.player = player
    this.generation = generation
    this.requestId = requestId
    this.lifecycle = lifecycle
    this.listener = listener
  }
}

class MusicPlayerPromotion {
  id: number
  lifecycle: number
  candidate: MusicPlayerSlot
  previous?: MusicPlayerSlot

  constructor(id: number, lifecycle: number, candidate: MusicPlayerSlot,
    previous?: MusicPlayerSlot) {
    this.id = id
    this.lifecycle = lifecycle
    this.candidate = candidate
    this.previous = previous
  }
}

export class HarmonyMusicPlayerEngine implements MusicPlayerEngine {
  private active?: MusicPlayerSlot
  private candidates: Map<number, MusicPlayerSlot> = new Map<number, MusicPlayerSlot>()
  private promotions: Map<number, MusicPlayerPromotion> = new Map<number, MusicPlayerPromotion>()
  private generationRequests: Map<number, number> = new Map<number, number>()
  private requestSequence: number = 0
  private latestRequestId: number = 0
  private promotionSequence: number = 0
  private lifecycle: number = 0

  async prepare(url: string, generation: number, listener: MusicPlayerEngineListener): Promise<void> {
    const lifecycle = this.lifecycle
    const requestId = ++this.requestSequence
    this.latestRequestId = requestId
    this.generationRequests.set(generation, requestId)
    this.detachSupersededCandidates(requestId)
    if (!this.isRequestCurrent(generation, requestId, lifecycle)) {
      this.clearRequest(generation, requestId)
      throw new Error('Music player preparation was superseded')
    }

    let player: media.AVPlayer
    try {
      player = await media.createAVPlayer()
    } catch (error) {
      this.clearRequest(generation, requestId)
      throw new Error((error as Error).message || 'The system music player could not be created')
    }
    if (!this.isRequestCurrent(generation, requestId, lifecycle)) {
      this.clearRequest(generation, requestId)
      await this.releasePlayerQuietly(player)
      throw new Error('Music player preparation was superseded')
    }

    const slot = new MusicPlayerSlot(player, generation, requestId, lifecycle, listener)
    this.candidates.set(generation, slot)
    try {
      player.audioRendererInfo = {
        usage: audio.StreamUsage.STREAM_USAGE_MUSIC,
        rendererFlags: 0
      }
      this.bindEvents(slot)
      await new Promise<void>((resolve: () => void, reject: (reason?: Error) => void) => {
        slot.resolvePreparation = resolve
        slot.rejectPreparation = reject
        try {
          player.url = url
        } catch (error) {
          this.handleError(slot, (error as Error).message)
        }
      })
    } catch (error) {
      await this.removeCandidate(slot)
      throw new Error((error as Error).message || 'The system music player could not prepare this track')
    }

    if (!this.isCurrentCandidate(slot)) {
      this.detachCandidate(slot)
      await this.releaseSlot(slot)
      throw new Error('Music player preparation was superseded')
    }
  }

  stagePromotion(generation: number): number {
    const candidate = this.candidates.get(generation)
    if (!candidate) {
      throw new Error('No prepared music player candidate is available')
    }
    if (candidate.requestId !== this.latestRequestId || !this.isCurrentCandidate(candidate)) {
      const staleCandidate = candidate
      this.detachCandidate(staleCandidate)
      this.releaseSlotQuietly(staleCandidate)
      throw new Error('The music player candidate was superseded')
    }
    if (!candidate.prepared || candidate.failed) {
      if (candidate.failed) {
        const failedCandidate = candidate
        this.detachCandidate(failedCandidate)
        this.releaseSlotQuietly(failedCandidate)
      }
      throw new Error('The music player candidate is not prepared')
    }

    this.detachCandidate(candidate)
    const previous = this.active
    this.active = candidate
    const promotionId = ++this.promotionSequence
    this.promotions.set(promotionId,
      new MusicPlayerPromotion(promotionId, this.lifecycle, candidate, previous))
    return promotionId
  }

  confirmPromotion(promotionId: number): void {
    const promotion = this.promotions.get(promotionId)
    if (!promotion) {
      return
    }
    this.promotions.delete(promotionId)
    if (promotion.previous && promotion.previous !== promotion.candidate) {
      this.releaseSlotQuietly(promotion.previous)
    }
  }

  rollbackPromotion(promotionId: number): void {
    const promotion = this.promotions.get(promotionId)
    if (!promotion) {
      return
    }
    this.promotions.delete(promotionId)
    if (promotion.lifecycle === this.lifecycle && this.active === promotion.candidate) {
      this.active = promotion.previous
    }
    this.releaseSlotQuietly(promotion.candidate)
  }

  async discard(generation: number): Promise<void> {
    const requestId = this.generationRequests.get(generation)
    if (requestId !== undefined) {
      this.generationRequests.delete(generation)
    }
    const candidate = this.candidates.get(generation)
    if (!candidate || requestId === undefined || candidate.requestId !== requestId) {
      return
    }
    await this.removeCandidate(candidate)
  }

  async play(): Promise<void> {
    const player = this.active?.player
    if (player && (player.state === 'prepared' || player.state === 'paused' || player.state === 'completed')) {
      await player.play()
    }
  }

  async pause(): Promise<void> {
    const player = this.active?.player
    if (player && (player.state === 'playing' || player.state === 'prepared')) {
      await player.pause()
    }
  }

  async stop(): Promise<void> {
    const player = this.active?.player
    if (player && (player.state === 'prepared' || player.state === 'playing' ||
      player.state === 'paused' || player.state === 'completed')) {
      await player.stop()
    }
  }

  async seek(positionMs: number): Promise<void> {
    const player = this.active?.player
    if (player && (player.state === 'prepared' || player.state === 'playing' ||
      player.state === 'paused' || player.state === 'completed')) {
      player.seek(Math.max(0, positionMs))
    }
  }

  async release(): Promise<void> {
    this.lifecycle += 1
    this.latestRequestId = ++this.requestSequence
    this.generationRequests.clear()
    const active = this.active
    this.active = undefined
    const candidates: MusicPlayerSlot[] = []
    this.candidates.forEach((candidate: MusicPlayerSlot) => {
      candidates.push(candidate)
    })
    this.candidates.clear()
    const promotions: MusicPlayerPromotion[] = []
    this.promotions.forEach((promotion: MusicPlayerPromotion) => {
      promotions.push(promotion)
    })
    this.promotions.clear()
    if (active) {
      await this.releaseSlot(active)
    }
    for (let index = 0; index < candidates.length; index += 1) {
      await this.releaseSlot(candidates[index])
    }
    for (let index = 0; index < promotions.length; index += 1) {
      const promotion = promotions[index]
      await this.releaseSlot(promotion.candidate)
      const previous = promotion.previous
      if (previous) {
        await this.releaseSlot(previous)
      }
    }
  }

  private bindEvents(slot: MusicPlayerSlot): void {
    const player = slot.player
    player.on('stateChange', (state: media.AVPlayerState) => {
      if (slot.released) {
        return
      }
      if (state === 'initialized') {
        if (!this.isCurrentCandidate(slot) || slot.prepareStarted) {
          return
        }
        slot.prepareStarted = true
        player.prepare().catch((error: BusinessError) => this.handleError(slot, error.message))
      } else if (state === 'prepared') {
        if (!this.isCurrentCandidate(slot) || slot.prepared || slot.failed) {
          return
        }
        slot.prepared = true
        this.resolvePreparation(slot)
        slot.listener.onPrepared(slot.generation, Math.max(0, player.duration))
      } else if (this.isActiveSlot(slot)) {
        if (state === 'playing') {
          slot.listener.onPlaying(slot.generation)
        } else if (state === 'paused') {
          slot.listener.onPaused(slot.generation)
        } else if (state === 'completed') {
          slot.listener.onCompleted(slot.generation)
        } else if (state === 'stopped') {
          slot.listener.onStopped(slot.generation)
        }
      }
    })
    player.on('timeUpdate', (positionMs: number) => {
      if (this.isActiveSlot(slot)) {
        slot.listener.onTimeUpdate(slot.generation, positionMs)
      }
    })
    player.on('seekDone', (positionMs: number) => {
      if (this.isActiveSlot(slot)) {
        slot.listener.onSeekDone(slot.generation, positionMs)
      }
    })
    player.on('bufferingUpdate', (type: media.BufferingInfoType) => {
      if (!this.isActiveSlot(slot)) {
        return
      }
      if (type === media.BufferingInfoType.BUFFERING_START) {
        slot.listener.onBuffering(slot.generation)
      } else if (type === media.BufferingInfoType.BUFFERING_END && player.state === 'playing') {
        slot.listener.onPlaying(slot.generation)
      }
    })
    player.on('error', (error: BusinessError) => this.handleError(slot, error.message))
  }

  private handleError(slot: MusicPlayerSlot, message: string): void {
    if (slot.released || (!this.isCurrentCandidate(slot) && !this.isActiveSlot(slot))) {
      return
    }
    slot.failed = true
    if (!slot.prepared) {
      this.rejectPreparation(slot, new Error(message))
    }
    if (!slot.errorNotified) {
      slot.errorNotified = true
      slot.listener.onError(slot.generation, message)
    }
  }

  private resolvePreparation(slot: MusicPlayerSlot): void {
    if (slot.settled) {
      return
    }
    slot.settled = true
    const resolve = slot.resolvePreparation
    slot.resolvePreparation = undefined
    slot.rejectPreparation = undefined
    if (resolve) {
      resolve()
    }
  }

  private rejectPreparation(slot: MusicPlayerSlot, error: Error): void {
    if (slot.settled) {
      return
    }
    slot.settled = true
    const reject = slot.rejectPreparation
    slot.resolvePreparation = undefined
    slot.rejectPreparation = undefined
    if (reject) {
      reject(error)
    }
  }

  private isRequestCurrent(generation: number, requestId: number, lifecycle: number): boolean {
    return lifecycle === this.lifecycle && requestId === this.latestRequestId &&
      this.generationRequests.get(generation) === requestId
  }

  private clearRequest(generation: number, requestId: number): void {
    if (this.generationRequests.get(generation) === requestId) {
      this.generationRequests.delete(generation)
    }
  }

  private isCurrentCandidate(slot: MusicPlayerSlot): boolean {
    return !slot.released && slot.lifecycle === this.lifecycle &&
      this.candidates.get(slot.generation) === slot &&
      this.generationRequests.get(slot.generation) === slot.requestId
  }

  private isActiveSlot(slot: MusicPlayerSlot): boolean {
    return !slot.released && slot.lifecycle === this.lifecycle && this.active === slot
  }

  private detachSupersededCandidates(requestId: number): void {
    this.candidates.forEach((candidate: MusicPlayerSlot, generation: number) => {
      if (candidate.requestId < requestId) {
        this.candidates.delete(generation)
        if (this.generationRequests.get(generation) === candidate.requestId) {
          this.generationRequests.delete(generation)
        }
        this.rejectPreparation(candidate, new Error('Music player preparation was superseded'))
        this.releaseSlotQuietly(candidate)
      }
    })
  }

  private async removeCandidate(slot: MusicPlayerSlot): Promise<void> {
    this.detachCandidate(slot)
    await this.releaseSlot(slot)
  }

  private detachCandidate(slot: MusicPlayerSlot): void {
    if (this.candidates.get(slot.generation) === slot) {
      this.candidates.delete(slot.generation)
    }
    if (this.generationRequests.get(slot.generation) === slot.requestId) {
      this.generationRequests.delete(slot.generation)
    }
    this.rejectPreparation(slot, new Error('Music player preparation was discarded'))
  }

  private async releaseSlot(slot: MusicPlayerSlot): Promise<void> {
    if (slot.released) {
      return
    }
    slot.released = true
    this.rejectPreparation(slot, new Error('Music player preparation was released'))
    await this.releasePlayer(slot.player)
  }

  private async releaseSlotQuietly(slot: MusicPlayerSlot): Promise<void> {
    try {
      await this.releaseSlot(slot)
    } catch (_error) {
    }
  }

  private async releasePlayerQuietly(player: media.AVPlayer): Promise<void> {
    try {
      await this.releasePlayer(player)
    } catch (_error) {
    }
  }

  private async releasePlayer(player: media.AVPlayer): Promise<void> {
    if (player.state === 'released') {
      return
    }
    player.off('stateChange')
    player.off('timeUpdate')
    player.off('seekDone')
    player.off('bufferingUpdate')
    player.off('error')
    await player.release()
  }
}
