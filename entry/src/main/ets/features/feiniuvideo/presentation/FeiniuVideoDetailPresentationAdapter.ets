import { MediaFileInfo, MediaStreamInfo } from '../../../media/catalog/MediaCatalogModels'
import { MediaProviderKind } from '../../../media/MediaProviderKind'
import { BaseVideoDetailPresentationAdapter } from '../../../media/presentation/BaseVideoDetailPresentationAdapter'

export class FeiniuVideoDetailPresentationAdapter extends BaseVideoDetailPresentationAdapter {
  readonly provider: MediaProviderKind = MediaProviderKind.FeiniuVideo

  episodeLabel(seasonNumber?: number, episodeNumber?: number): string {
    if (seasonNumber !== undefined && episodeNumber !== undefined) {
      return `季 ${seasonNumber} 集 ${episodeNumber}`
    }
    if (episodeNumber !== undefined) return `集 ${episodeNumber}`
    if (seasonNumber !== undefined) return `季 ${seasonNumber}`
    return '播放'
  }

  protected compactSourceLabel(source: MediaFileInfo, video: MediaStreamInfo | undefined,
    audio: MediaStreamInfo | undefined, index: number): string {
    const parts: string[] = []
    const resolution = this.resolutionLabel(video)
    if (resolution.length > 0) parts.push(resolution)
    const range = this.dynamicRangeLabel(video)
    if (range.length > 0) parts.push(range)
    if (parts.length > 0) return parts.join('')
    const name = source.name.trim()
    return name.length > 0 ? name : `视频源 ${index + 1}`
  }

  protected sourceSummary(source: MediaFileInfo, video: MediaStreamInfo | undefined,
    audio: MediaStreamInfo | undefined): string {
    const parts = this.summaryParts(source, video, audio)
    return parts.length > 0 ? parts.join(' · ') : '播放版本'
  }

  protected fileSize(sizeBytes: number): string {
    if (sizeBytes <= 0) return ''
    const units: string[] = ['B', 'KB', 'MB', 'GB', 'TB']
    let value = sizeBytes
    let unitIndex = 0
    while (value >= 1024 && unitIndex < units.length - 1) {
      value /= 1024
      unitIndex += 1
    }
    return unitIndex === 0 ? `${Math.round(value)} ${units[unitIndex]}` :
      `${value.toFixed(2)} ${units[unitIndex]}`
  }

  protected formatDate(timestamp?: number): string {
    if (!timestamp || timestamp <= 0) return ''
    const date = new Date(timestamp)
    if (Number.isNaN(date.getTime())) return ''
    const year = date.getFullYear().toString()
    const month = (date.getMonth() + 1).toString().padStart(2, '0')
    const day = date.getDate().toString().padStart(2, '0')
    return `${year}-${month}-${day}`
  }

  protected videoStreamSummary(streams: MediaStreamInfo[]): string {
    const video = streams.length > 0 ? streams[0] : undefined
    if (!video) return ''
    const mainParts: string[] = []
    const resolution = this.resolutionLabel(video)
    if (resolution.length > 0) mainParts.push(resolution.toLowerCase())
    else if ((video.width || 0) > 0 && (video.height || 0) > 0) {
      mainParts.push(`${video.width}x${video.height}`)
    }
    const codec = video.codec.trim().toUpperCase()
    if (codec.length > 0) mainParts.push(codec)
    if ((video.bitrate || 0) > 0) mainParts.push(this.bitrateText(video.bitrate || 0))
    const bitDepth = (video.bitDepth || 0) > 0 ? `${video.bitDepth} bit` : ''
    if (bitDepth.length === 0) return mainParts.join(' ')
    const main = mainParts.join(' ')
    return main.length > 0 ? `${main} · ${bitDepth}` : bitDepth
  }

  protected audioStreamSummary(streams: MediaStreamInfo[]): string {
    const audio = streams.length > 0 ? streams[0] : undefined
    if (!audio) return ''
    const mainParts: string[] = []
    mainParts.push(this.audioLanguageText(audio))
    const codec = audio.codec.trim().toUpperCase()
    if (codec.length > 0) mainParts.push(codec)
    const sampleRate = (audio.sampleRate || 0) > 0 ? `${audio.sampleRate} Hz` : ''
    if (sampleRate.length === 0) return mainParts.join(' ')
    return `${mainParts.join(' ')} · ${sampleRate}`
  }

  private audioLanguageText(audio: MediaStreamInfo): string {
    const title = audio.title.trim()
    if (title.length > 0 && title.toLowerCase() !== 'und') return title
    const language = audio.language.trim()
    const normalized = language.toLowerCase()
    if (normalized === 'chi' || normalized === 'zho' || normalized === 'zh' || normalized === 'chs' ||
      normalized === 'cht') return '中文'
    if (normalized === 'jpn' || normalized === 'ja' || normalized === 'jp') return '日语'
    if (normalized === 'eng' || normalized === 'en') return '英语'
    if (normalized === 'kor' || normalized === 'ko') return '韩语'
    if (normalized.length === 0 || normalized === 'und' || normalized === 'unknown' || normalized === 'zxx' ||
      normalized === 'qaa-qtz' || normalized === 'zz-unknow' || language === '_no_display_') return '未知'
    return language
  }
}
