import { LanguageUtil } from '../../utils/LanguageUtil'
import { MediaProviderKind } from '../MediaProviderKind'
import { MediaFileInfo, MediaStreamInfo, MediaStreamKind } from '../catalog/MediaCatalogModels'
import {
  MediaInfoCardViewData,
  MediaInfoRowViewData,
  MediaInfoSectionViewData,
  MediaOptionViewData,
  PlaybackSourceViewData,
  VideoDetailPresentationAdapter,
  VideoTag
} from './VideoDetailPresentationModels'

export abstract class BaseVideoDetailPresentationAdapter implements VideoDetailPresentationAdapter {
  abstract readonly provider: MediaProviderKind

  abstract episodeLabel(seasonNumber?: number, episodeNumber?: number): string

  present(sources: MediaFileInfo[]): PlaybackSourceViewData[] {
    return sources.map((source: MediaFileInfo, index: number): PlaybackSourceViewData => {
      const videos = this.streamsOfKind(source, MediaStreamKind.Video)
      const audios = this.streamsOfKind(source, MediaStreamKind.Audio)
      const subtitles = this.streamsOfKind(source, MediaStreamKind.Subtitle)
      const video = videos.length > 0 ? videos[0] : undefined
      const audio = audios.length > 0 ? audios[0] : undefined
      const audioOptions = audios.map((stream: MediaStreamInfo, optionIndex: number): MediaOptionViewData => {
        return this.audioOption(stream, optionIndex)
      })
      const subtitleOptions = subtitles.map((stream: MediaStreamInfo, optionIndex: number): MediaOptionViewData => {
        return this.subtitleOption(stream, optionIndex)
      })
      return {
        id: source.id,
        compactLabel: this.compactSourceLabel(source, video, audio, index),
        title: this.sourceTitle(source, index),
        summary: this.sourceSummary(source, video, audio),
        filePath: source.path.trim(),
        fileSize: this.fileSize(source.sizeBytes),
        fileCreatedDate: this.formatDate(source.fileCreatedAtMs),
        addedDate: this.formatDate(source.addedAtMs),
        tagKeys: this.videoTags(source.streams),
        audioOptions: audioOptions,
        subtitleOptions: subtitleOptions,
        defaultAudioOptionIndex: this.defaultOptionIndex(audios),
        defaultSubtitleOptionIndex: this.defaultOptionIndex(subtitles),
        videoSummary: this.videoStreamSummary(videos),
        audioSummary: this.audioStreamSummary(audios),
        subtitleSummary: this.subtitleStreamSummary(subtitles),
        infoSections: this.infoSections(videos, audios, subtitles)
      }
    })
  }

  protected abstract compactSourceLabel(source: MediaFileInfo, video: MediaStreamInfo | undefined,
    audio: MediaStreamInfo | undefined, index: number): string

  protected abstract sourceSummary(source: MediaFileInfo, video: MediaStreamInfo | undefined,
    audio: MediaStreamInfo | undefined): string

  protected sourceTitle(source: MediaFileInfo, index: number): string {
    const name = source.name.trim()
    return name.length > 0 ? name : `版本 ${index + 1}`
  }

  protected resolutionLabel(video?: MediaStreamInfo): string {
    if (!video) return ''
    const explicit = `${video.resolutionType || ''}`.trim().toUpperCase()
    if (explicit.length > 0) {
      if (explicit.includes('2160') || explicit.includes('UHD')) return '4K'
      return explicit
    }
    const width = video.width || 0
    const height = video.height || 0
    if (width >= 3800 || height >= 2000) return '4K'
    if (width >= 2500 || height >= 1400) return '1440P'
    if (width >= 1900 || height >= 1000) return '1080P'
    if (width >= 1200 || height >= 700) return '720P'
    const display = video.displayTitle.trim().toUpperCase()
    if (display.includes('4K') || display.includes('2160') || display.includes('UHD')) return '4K'
    if (display.includes('1440')) return '1440P'
    if (display.includes('1080') || display.includes('FHD')) return '1080P'
    if (display.includes('720')) return '720P'
    return height > 0 ? `${height}P` : ''
  }

  protected detailedResolution(video?: MediaStreamInfo): string {
    if (!video) return ''
    if ((video.width || 0) > 0 && (video.height || 0) > 0) {
      return `${video.width} * ${video.height}`
    }
    return this.displayOrDash(video.resolutionType || video.displayTitle)
  }

  protected dynamicRangeLabel(video?: MediaStreamInfo): string {
    if (!video) return ''
    if ((video.dvProfile || 0) > 0) return '杜比'
    const source = `${video.videoRangeType || ''} ${video.displayTitle || ''} ${video.colorTransfer || ''}`
    const normalized = source.trim().toUpperCase()
    if (normalized.includes('DOVI') || normalized.includes('DOLBY VISION') || normalized.includes('杜比')) {
      return '杜比'
    }
    if (normalized.includes('HDR') || normalized.includes('HLG') || normalized.includes('VIVID') ||
      normalized.includes('PQ') || normalized.includes('2084')) {
      return 'HDR'
    }
    if (normalized.includes('SDR')) return 'SDR'
    return ''
  }

  protected detailedDynamicRange(video: MediaStreamInfo): string {
    if ((video.dvProfile || 0) > 0) return `Dolby Vision P${video.dvProfile}`
    return this.displayOrDash(video.videoRangeType)
  }

  protected fileSize(sizeBytes: number): string {
    const bytes = Math.max(0, sizeBytes)
    if (bytes >= 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024 / 1024).toFixed(1)} GB`
    if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
    return bytes > 0 ? `${Math.floor(bytes / 1024)} KB` : ''
  }

  protected summaryParts(source: MediaFileInfo, video?: MediaStreamInfo, audio?: MediaStreamInfo): string[] {
    const parts: string[] = []
    this.appendUnique(parts, this.resolutionLabel(video))
    this.appendUnique(parts, `${video?.codec || ''}`.trim().toUpperCase())
    this.appendUnique(parts, this.dynamicRangeLabel(video))
    this.appendUnique(parts, source.container.trim().toUpperCase())
    this.appendUnique(parts, this.fileSize(source.sizeBytes))
    return parts
  }

  private streamsOfKind(source: MediaFileInfo, kind: MediaStreamKind): MediaStreamInfo[] {
    return source.streams.filter((stream: MediaStreamInfo): boolean => stream.kind === kind)
  }

  private defaultOptionIndex(streams: MediaStreamInfo[]): number {
    const index = streams.findIndex((stream: MediaStreamInfo): boolean => stream.default)
    return index >= 0 ? index : 0
  }

  private audioOption(stream: MediaStreamInfo, index: number): MediaOptionViewData {
    const language = LanguageUtil.getLanguageDisplayNameBy6392T(stream.language || '')
    const title = language.length > 0 ? `${language}音频` : `音轨 ${index + 1}`
    const summaryParts: string[] = []
    this.appendUnique(summaryParts, stream.codec.trim().toUpperCase())
    this.appendUnique(summaryParts, this.audioChannelText(stream))
    if ((stream.sampleRate || 0) > 0) this.appendUnique(summaryParts, `${stream.sampleRate} Hz`)
    if (stream.default) this.appendUnique(summaryParts, '默认')
    return {
      id: `audio:${stream.index}:${index}`,
      streamIndex: stream.index,
      title: title,
      summary: summaryParts.join(' · ')
    }
  }

  private subtitleOption(stream: MediaStreamInfo, index: number): MediaOptionViewData {
    const language = LanguageUtil.getLanguageDisplayNameBy6392T(stream.language || '')
    const title = stream.title.trim().length > 0 ? stream.title.trim() :
      (language.length > 0 ? `${language}字幕` : `字幕 ${index + 1}`)
    const summaryParts: string[] = []
    this.appendUnique(summaryParts, stream.codec.trim().toUpperCase())
    if (stream.external) this.appendUnique(summaryParts, '外挂')
    if (stream.forced) this.appendUnique(summaryParts, '强制')
    if (stream.default) this.appendUnique(summaryParts, '默认')
    return {
      id: `subtitle:${stream.index}:${index}`,
      streamIndex: stream.index,
      title: title,
      summary: summaryParts.join(' · ')
    }
  }

  protected videoStreamSummary(streams: MediaStreamInfo[]): string {
    return this.streamSummary(streams)
  }

  protected audioStreamSummary(streams: MediaStreamInfo[]): string {
    return this.streamSummary(streams)
  }

  private streamSummary(streams: MediaStreamInfo[]): string {
    const stream = streams.length > 0 ? streams[0] : undefined
    if (!stream) return ''
    const parts: string[] = []
    this.appendUnique(parts, stream.displayTitle)
    if ((stream.bitrate || 0) > 0) this.appendUnique(parts, this.bitrateText(stream.bitrate || 0))
    if ((stream.bitDepth || 0) > 0) this.appendUnique(parts, `${stream.bitDepth} bit`)
    return parts.join(' · ')
  }

  private subtitleStreamSummary(streams: MediaStreamInfo[]): string {
    const stream = streams.length > 0 ? streams[0] : undefined
    if (!stream) return ''
    const language = LanguageUtil.getLanguageDisplayNameBy6392T(stream.language || '')
    return [language, stream.codec.trim().toUpperCase()]
      .filter((value: string): boolean => value.length > 0).join(' · ')
  }

  protected formatDate(timestamp?: number): string {
    if (!timestamp || timestamp <= 0) return ''
    const date = new Date(timestamp)
    if (Number.isNaN(date.getTime())) return ''
    const year = date.getFullYear().toString()
    const month = (date.getMonth() + 1).toString().padStart(2, '0')
    const day = date.getDate().toString().padStart(2, '0')
    const hour = date.getHours().toString().padStart(2, '0')
    const minute = date.getMinutes().toString().padStart(2, '0')
    return `${year}-${month}-${day} ${hour}:${minute}`
  }

  private infoSections(videos: MediaStreamInfo[], audios: MediaStreamInfo[],
    subtitles: MediaStreamInfo[]): MediaInfoSectionViewData[] {
    const sections: MediaInfoSectionViewData[] = []
    const videoCards = videos.filter((video: MediaStreamInfo): boolean => this.hasVideoDetails(video))
      .map((video: MediaStreamInfo, index: number): MediaInfoCardViewData => this.videoInfoCard(video, index))
    const audioCards = audios.filter((audio: MediaStreamInfo): boolean => this.hasAudioDetails(audio))
      .map((audio: MediaStreamInfo, index: number): MediaInfoCardViewData => this.audioInfoCard(audio, index))
    const subtitleCards = subtitles.filter((subtitle: MediaStreamInfo): boolean => this.hasSubtitleDetails(subtitle))
      .map((subtitle: MediaStreamInfo, index: number): MediaInfoCardViewData =>
        this.subtitleInfoCard(subtitle, index))
    if (videoCards.length > 0) {
      sections.push({
        id: 'video',
        title: '视频',
        cards: videoCards
      })
    }
    if (audioCards.length > 0) {
      sections.push({
        id: 'audio',
        title: '音频',
        cards: audioCards
      })
    }
    if (subtitleCards.length > 0) {
      sections.push({
        id: 'subtitle',
        title: '字幕',
        cards: subtitleCards
      })
    }
    return sections
  }

  private hasVideoDetails(video: MediaStreamInfo): boolean {
    return this.hasText(video.title) || this.hasText(video.displayTitle) || this.hasText(video.codec) ||
      this.hasText(video.profile) || video.level !== undefined || (video.width || 0) > 0 ||
      (video.height || 0) > 0 || this.hasText(video.resolutionType) || this.hasText(video.aspectRatio) ||
      video.frameRate !== undefined || video.bitrate !== undefined || this.hasText(video.videoRangeType) ||
      (video.dvProfile || 0) > 0 || this.hasText(video.colorPrimaries) || this.hasText(video.colorSpace) ||
      this.hasText(video.colorTransfer) || video.bitDepth !== undefined || this.hasText(video.pixelFormat) ||
      video.refFrames !== undefined
  }

  private hasAudioDetails(audio: MediaStreamInfo): boolean {
    return this.hasText(audio.title) || this.hasText(audio.displayTitle) || this.hasText(audio.language) ||
      this.hasText(audio.codec) || this.hasText(audio.profile) || audio.channels !== undefined ||
      audio.sampleRate !== undefined || audio.bitrate !== undefined || this.hasText(audio.audioType) ||
      this.hasText(audio.channelLayout)
  }

  private hasSubtitleDetails(subtitle: MediaStreamInfo): boolean {
    return this.hasText(subtitle.title) || this.hasText(subtitle.displayTitle) ||
      this.hasText(subtitle.language) || this.hasText(subtitle.codec) || subtitle.external ||
      subtitle.default || subtitle.forced
  }

  private hasText(value?: string): boolean {
    return `${value || ''}`.trim().length > 0
  }

  private videoInfoCard(video: MediaStreamInfo, index: number): MediaInfoCardViewData {
    const titleParts: string[] = []
    this.appendUnique(titleParts, this.resolutionLabel(video))
    this.appendUnique(titleParts, video.codec.trim().toUpperCase())
    this.appendUnique(titleParts, this.dynamicRangeLabel(video))
    return {
      id: `video:${video.index}:${index}`,
      title: titleParts.length > 0 ? titleParts.join(' ') : `视频 ${index + 1}`,
      rows: [
        this.infoRow('编码器', video.codec.trim().toLowerCase()),
        this.infoRow('配置', video.profile),
        this.infoRow('等级', video.level !== undefined ? video.level.toString() : ''),
        this.infoRow('分辨率', this.detailedResolution(video)),
        this.infoRow('宽高比', video.aspectRatio),
        this.infoRow('隔行扫描', video.interlaced === undefined ? '--' : (video.interlaced ? '是' : '否'), true),
        this.infoRow('帧率', video.frameRate !== undefined ? `${video.frameRate} fps` : ''),
        this.infoRow('码率', video.bitrate !== undefined ? this.bitrateText(video.bitrate) : ''),
        this.infoRow('视频动态范围', this.detailedDynamicRange(video)),
        this.infoRow('色彩原色', video.colorPrimaries),
        this.infoRow('色彩空间', video.colorSpace, true),
        this.infoRow('色彩转换', video.colorTransfer),
        this.infoRow('位深度', video.bitDepth !== undefined ? `${video.bitDepth} bit` : ''),
        this.infoRow('像素格式', video.pixelFormat),
        this.infoRow('参考帧', video.refFrames !== undefined ? video.refFrames.toString() : '')
      ]
    }
  }

  private audioInfoCard(audio: MediaStreamInfo, index: number): MediaInfoCardViewData {
    const language = LanguageUtil.getLanguageDisplayNameBy6392T(audio.language || '')
    const titleParts: string[] = []
    this.appendUnique(titleParts, language)
    this.appendUnique(titleParts, audio.codec.trim().toUpperCase())
    this.appendUnique(titleParts, this.audioChannelText(audio))
    return {
      id: `audio:${audio.index}:${index}`,
      title: titleParts.length > 0 ? titleParts.join(' ') : `音频 ${index + 1}`,
      rows: [
        this.infoRow('语言', language),
        this.infoRow('编码器', audio.codec.trim().toLowerCase()),
        this.infoRow('配置', audio.profile),
        this.infoRow('声道', audio.channels !== undefined ? `${audio.channels} ch` : '', true),
        this.infoRow('采样率', audio.sampleRate !== undefined ? `${audio.sampleRate} Hz` : ''),
        this.infoRow('码率', audio.bitrate !== undefined ? this.bitrateText(audio.bitrate) : ''),
        this.infoRow('布局', audio.channelLayout, true),
        this.infoRow('默认', audio.default ? '是' : '否')
      ]
    }
  }

  private subtitleInfoCard(subtitle: MediaStreamInfo, index: number): MediaInfoCardViewData {
    const language = LanguageUtil.getLanguageDisplayNameBy6392T(subtitle.language || '')
    const title = subtitle.title.trim().length > 0 ? subtitle.title.trim() :
      (language.length > 0 ? language : `字幕 ${index + 1}`)
    return {
      id: `subtitle:${subtitle.index}:${index}`,
      title: title,
      rows: [
        this.infoRow('语言', language),
        this.infoRow('编码器', subtitle.codec.trim().toLowerCase()),
        this.infoRow('外置字幕', subtitle.external ? '是' : '否'),
        this.infoRow('默认', subtitle.default ? '是' : '否'),
        this.infoRow('强制', subtitle.forced ? '是' : '否')
      ]
    }
  }

  private infoRow(title: string, value?: string, dividerBefore: boolean = false): MediaInfoRowViewData {
    return {
      title: title,
      value: this.displayOrDash(value),
      dividerBefore: dividerBefore
    }
  }

  private displayOrDash(value?: string): string {
    const normalized = `${value || ''}`.trim()
    return normalized.length > 0 ? normalized : '--'
  }

  protected bitrateText(value: number): string {
    if (value >= 1000000) return `${(value / 1000000).toFixed(2)} mbps`
    if (value >= 1000) return `${(value / 1000).toFixed(0)} kbps`
    return value > 0 ? `${value} bps` : ''
  }

  private audioChannelText(audio: MediaStreamInfo): string {
    const layout = `${audio.channelLayout || ''}`.trim().toLowerCase()
    if (layout.length > 0) {
      if (layout.includes('stereo')) return 'stereo'
      if (layout.includes('mono')) return 'mono'
      return layout
    }
    const channels = audio.channels || 0
    if (channels === 1) return 'mono'
    if (channels === 2) return 'stereo'
    if (channels === 6) return '5.1'
    if (channels === 8) return '7.1'
    return channels > 0 ? `${channels} 声道` : ''
  }

  private videoTags(streams: MediaStreamInfo[]): VideoTag[] {
    const tags: VideoTag[] = []
    streams.forEach((stream: MediaStreamInfo): void => {
      if (stream.kind === MediaStreamKind.Video) {
        const resolution = this.resolutionLabel(stream)
        if (resolution === '4K') this.appendTag(tags, '4K')
        else if (resolution === '1080P') this.appendTag(tags, '1080')
        const range = this.dynamicRangeLabel(stream)
        if (range === '杜比') this.appendTag(tags, 'DOLBY_VISION')
        else if (range === 'HDR') this.appendTag(tags, 'HDR')
        else if (range === 'SDR') this.appendTag(tags, 'SDR')
      } else if (stream.kind === MediaStreamKind.Audio) {
        const layout = `${stream.channelLayout || ''}`.toLowerCase()
        const codec = stream.codec.toLowerCase()
        if (layout.includes('stereo')) this.appendTag(tags, 'STEREO')
        if (codec.includes('dts')) {
          this.appendTag(tags, 'DTS')
        } else if (codec.includes('dolby') || codec.includes('ac3') || codec.includes('eac3')) {
          if (layout.includes('7.1')) this.appendTag(tags, 'DOLBY_SURROUND')
          else if (layout.includes('5.1')) this.appendTag(tags, 'DOLBY_ATOMS')
        }
      }
    })
    return tags
  }

  private appendTag(tags: VideoTag[], value: VideoTag): void {
    if (!tags.includes(value)) tags.push(value)
  }

  protected appendUnique(parts: string[], value?: string): void {
    const normalized = `${value || ''}`.trim()
    if (normalized.length === 0) return
    const key = normalized.toLowerCase()
    if (!parts.some((item: string): boolean => item.toLowerCase() === key)) parts.push(normalized)
  }
}
