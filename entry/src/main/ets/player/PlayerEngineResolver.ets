import { PlayerEngineType } from '../entity/PlayerEngineType'
import { JellyfinMediaSource } from './source/JellyfinMediaSource'

export class PlayerEngineDecision {
  engineType: PlayerEngineType
  reason: string
  forceMpvHardwareDecode: boolean
  forceMpvSoftwareDecode: boolean

  constructor(
    engineType: PlayerEngineType,
    reason: string,
    forceMpvHardwareDecode: boolean = false,
    forceMpvSoftwareDecode: boolean = false
  ) {
    this.engineType = engineType
    this.reason = reason
    this.forceMpvHardwareDecode = forceMpvHardwareDecode
    this.forceMpvSoftwareDecode = forceMpvSoftwareDecode
  }
}

export class PlayerEngineResolver {
  static getRequestProfileEngineType(engineType: PlayerEngineType): PlayerEngineType {
    if (engineType === PlayerEngineType.AUTO) {
      return PlayerEngineType.MPV
    }
    return engineType
  }

  static resolvePlaybackEngine(engineType: PlayerEngineType, source?: JellyfinMediaSource): PlayerEngineDecision {
    if (engineType === PlayerEngineType.AV) {
      return new PlayerEngineDecision(PlayerEngineType.AV, '已手动指定使用系统播放器。')
    }
    if (engineType === PlayerEngineType.MPV) {
      return new PlayerEngineDecision(PlayerEngineType.MPV, '已手动指定使用 MPV。')
    }
    if (!source) {
      return new PlayerEngineDecision(PlayerEngineType.AV, '自动模式等待片源信息，先按系统播放器准备。')
    }
    if (source.shouldPreferMpvSoftwareDecode()) {
      return new PlayerEngineDecision(
        PlayerEngineType.MPV,
        `检测到 ${source.selectedVideoStream?.PixelFormat ?? 'unknown'} 像素格式，这类 4:4:4 / 4:2:2 片源更容易在硬解链路黑屏，自动切到 MPV 软解。`,
        false,
        true
      )
    }
    const avUnsupportedReasons = source.getAvPlayerUnsupportedReasons()
    if (avUnsupportedReasons.length > 0) {
      return new PlayerEngineDecision(
        PlayerEngineType.MPV,
        `系统播放器不支持当前片源：${avUnsupportedReasons.join('；')}，自动切到 MPV 硬解。`,
        true
      )
    }
    if (source.isHdrPreferredForAvPlayer()) {
      return new PlayerEngineDecision(
        PlayerEngineType.AV,
        '当前是 HDR 片源，系统播放器可以直接播放，自动优先使用系统播放器。'
      )
    }
    return new PlayerEngineDecision(
      PlayerEngineType.AV,
      '当前片源格式、音轨和字幕都在系统播放器支持范围内，自动优先使用系统播放器。'
    )
  }
}
