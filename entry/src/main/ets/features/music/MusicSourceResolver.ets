import {
  millisecondsToMusicTicks,
  MusicPlaybackErrorCode,
  MusicPlayMethod,
  MusicResolvedSource
} from './MusicModels'
import {
  MusicPlaybackApiService,
  MusicPlaybackInfoResult,
  MusicPlaybackServiceError,
  MusicPlaybackSourceService,
  redactMusicPlaybackUrl
} from './MusicPlaybackApiService'

interface MusicHttpResponse {
  status: number
}

interface MusicHttpError {
  isAxiosError?: boolean
  response?: MusicHttpResponse
}

export class MusicSourceResolver {
  private api: MusicPlaybackSourceService

  constructor(api?: MusicPlaybackSourceService) {
    this.api = api || new MusicPlaybackApiService()
  }

  async resolve(itemId: string, startPositionMs: number = 0): Promise<MusicResolvedSource> {
    try {
      const playbackInfo = await this.api.getPlaybackInfo(
        itemId,
        millisecondsToMusicTicks(this.normalizeMilliseconds(startPositionMs))
      )
      this.assertPlayableResponse(playbackInfo)
      const source = this.resolveSource(playbackInfo, startPositionMs)
      console.info(
        `[MusicSource] itemId=${source.itemId}, mediaSourceId=${source.mediaSourceId}, ` +
        `playMethod=${source.playMethod}, url=${redactMusicPlaybackUrl(source.url)}`
      )
      return source
    } catch (error) {
      if (error instanceof MusicPlaybackServiceError) {
        throw error
      }
      const normalizedError = error as Error
      throw new MusicPlaybackServiceError(
        this.classifyError(normalizedError),
        normalizedError.message || 'Unable to resolve music playback source'
      )
    }
  }

  classifyError(error: Error): MusicPlaybackErrorCode {
    if (error instanceof MusicPlaybackServiceError) {
      return error.code
    }
    const httpError = error as MusicHttpError
    const status = httpError.response?.status
    if (status === 401) {
      return MusicPlaybackErrorCode.Authentication
    }
    if (status === 403) {
      return MusicPlaybackErrorCode.PermissionDenied
    }
    if (status === 404) {
      return MusicPlaybackErrorCode.NotFound
    }
    return MusicPlaybackErrorCode.Network
  }

  private assertPlayableResponse(source: MusicPlaybackInfoResult): void {
    if (source.serverPlaybackError === 'NotAllowed') {
      throw new MusicPlaybackServiceError(
        MusicPlaybackErrorCode.PermissionDenied,
        'The server denied music playback'
      )
    }
    if (source.playSessionId.length === 0) {
      throw new MusicPlaybackServiceError(
        MusicPlaybackErrorCode.NoPlayableSource,
        'The server did not create a music play session'
      )
    }
    if (!source.sourceFound) {
      throw new MusicPlaybackServiceError(
        MusicPlaybackErrorCode.NoPlayableSource,
        'The server returned no music media source'
      )
    }
  }

  private resolveSource(info: MusicPlaybackInfoResult, requestedStartMs: number): MusicResolvedSource {
    let url = ''
    let playMethod = MusicPlayMethod.DirectPlay
    if (info.supportsDirectPlay && info.protocol === 'File') {
      url = this.api.buildStaticStreamUrl(info)
    } else if (info.supportsDirectPlay && info.protocol === 'Http' && info.path.length > 0) {
      url = this.api.absoluteAuthenticatedUrl(info.path, info.playSessionId)
    } else if (info.supportsDirectStream) {
      url = this.api.buildDirectStreamUrl(info)
      playMethod = MusicPlayMethod.DirectStream
    } else if (info.supportsTranscoding) {
      if (info.transcodingUrl.length === 0) {
        throw new MusicPlaybackServiceError(
          MusicPlaybackErrorCode.TranscodingFailed,
          'The server returned no music transcoding URL'
        )
      }
      url = this.api.absoluteAuthenticatedUrl(info.transcodingUrl, info.playSessionId)
      playMethod = MusicPlayMethod.Transcode
    } else {
      throw new MusicPlaybackServiceError(
        MusicPlaybackErrorCode.NoPlayableSource,
        'The server returned no compatible music source'
      )
    }
    if (url.length === 0) {
      throw new MusicPlaybackServiceError(
        playMethod === MusicPlayMethod.Transcode ?
          MusicPlaybackErrorCode.TranscodingFailed : MusicPlaybackErrorCode.NoPlayableSource,
        'The resolved music URL is empty'
      )
    }
    const source: MusicResolvedSource = {
      itemId: info.itemId,
      mediaSourceId: info.mediaSourceId,
      playSessionId: info.playSessionId,
      liveStreamId: info.liveStreamId,
      url: url,
      playMethod: playMethod,
      durationMs: info.durationMs,
      startPositionMs: this.validStartPosition(requestedStartMs, info.durationMs),
      scope: info.scope
    }
    return source
  }

  private validStartPosition(positionMs: number, durationMs: number): number {
    const position = this.normalizeMilliseconds(positionMs)
    return position > 0 && durationMs > position ? position : 0
  }

  private normalizeMilliseconds(milliseconds: number): number {
    return Number.isFinite(milliseconds) && milliseconds > 0 ? Math.floor(milliseconds) : 0
  }
}
