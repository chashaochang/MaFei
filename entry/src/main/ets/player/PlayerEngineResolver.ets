import { PlayerEngineType } from '../entity/PlayerEngineType'
import { MediaPlaybackSource } from '../media/playback/MediaPlaybackModels'

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
  static getRequestProfileEngineType(_engineType: PlayerEngineType): PlayerEngineType {
    return PlayerEngineType.MPV
  }

  static resolvePlaybackEngine(_engineType: PlayerEngineType, source?: MediaPlaybackSource): PlayerEngineDecision {
    if (!source) {
      return new PlayerEngineDecision(PlayerEngineType.MPV, 'MFPlayer 正在等待片源信息。')
    }
    if (source.shouldPreferMpvSoftwareDecode()) {
      return new PlayerEngineDecision(
        PlayerEngineType.MPV,
        `检测到 ${source.selectedVideoStream?.PixelFormat ?? 'unknown'} 像素格式，MFPlayer 将优先使用兼容解码路径。`,
        false,
        true
      )
    }
    return new PlayerEngineDecision(
      PlayerEngineType.MPV,
      '当前片源由 MFPlayer 播放。',
      true
    )
  }
}
