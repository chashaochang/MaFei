import { MediaFileInfo, MediaStreamInfo } from '../catalog/MediaCatalogModels'
import { MediaProviderKind } from '../MediaProviderKind'
import { BaseVideoDetailPresentationAdapter } from '../presentation/BaseVideoDetailPresentationAdapter'
import { MediaOptionViewData } from '../presentation/VideoDetailPresentationModels'
import { LanguageUtil } from '../../utils/LanguageUtil'

export class JellyfinVideoDetailPresentationAdapter extends BaseVideoDetailPresentationAdapter {
  readonly provider: MediaProviderKind = MediaProviderKind.Jellyfin

  episodeLabel(seasonNumber?: number, episodeNumber?: number): string {
    const parts: string[] = []
    if (seasonNumber !== undefined) parts.push(`第 ${seasonNumber} 季`)
    if (episodeNumber !== undefined) parts.push(`第 ${episodeNumber} 集`)
    return parts.length > 0 ? parts.join('  ') : '播放'
  }

  protected compactSourceLabel(source: MediaFileInfo, video: MediaStreamInfo | undefined,
    audio: MediaStreamInfo | undefined, index: number): string {
    const resolution = this.resolutionLabel(video)
    const range = this.dynamicRangeLabel(video)
    const label = `${resolution}${range}`.trim()
    if (label.length > 0) return label
    const name = source.name.trim()
    return name.length > 0 ? name : `视频源 ${index + 1}`
  }

  protected sourceSummary(source: MediaFileInfo, video: MediaStreamInfo | undefined,
    audio: MediaStreamInfo | undefined): string {
    const parts = this.summaryParts(source, video, audio)
    return parts.length > 0 ? parts.join(' · ') : '播放版本'
  }

  protected audioOption(stream: MediaStreamInfo, index: number): MediaOptionViewData {
    const language = LanguageUtil.getLanguageDisplayNameBy6392T(stream.language || '')
    const title = language.length > 0 ? `${language}音频` : `音轨 ${index + 1}`
    const codec = stream.codec.trim().toUpperCase()
    const channels = this.audioChannelText(stream)
    const summaryParts: string[] = []
    this.appendUnique(summaryParts, stream.displayTitle)
    this.appendUnique(summaryParts, codec)
    this.appendUnique(summaryParts, channels)
    if ((stream.sampleRate || 0) > 0) this.appendUnique(summaryParts, `${stream.sampleRate} Hz`)
    if (stream.default) this.appendUnique(summaryParts, '默认')
    return {
      id: `audio:${stream.index}:${index}`,
      streamIndex: stream.index,
      compactLabel: this.compactOptionLabel(title, [codec, channels]),
      title: title,
      summary: summaryParts.join(' · ')
    }
  }

  protected subtitleOption(stream: MediaStreamInfo, index: number): MediaOptionViewData {
    const language = LanguageUtil.getLanguageDisplayNameBy6392T(stream.language || '')
    const title = language.length > 0 ? `${language}字幕` : `字幕 ${index + 1}`
    const codec = stream.codec.trim().toUpperCase()
    const summaryParts: string[] = []
    this.appendUnique(summaryParts, stream.displayTitle)
    this.appendUnique(summaryParts, codec)
    if (stream.external) this.appendUnique(summaryParts, '外挂')
    if (stream.forced) this.appendUnique(summaryParts, '强制')
    if (stream.default) this.appendUnique(summaryParts, '默认')
    return {
      id: `subtitle:${stream.index}:${index}`,
      streamIndex: stream.index,
      compactLabel: this.compactOptionLabel(title, [codec]),
      title: title,
      summary: summaryParts.join(' · ')
    }
  }
}
