import { MediaProviderKind } from '../MediaProviderKind'
import { MediaFileInfo } from '../catalog/MediaCatalogModels'

export type VideoTag = '1080' | '4K' | 'STEREO' | 'HDR' | 'SDR' | 'DTS' | 'DOLBY_VISION' | 'DOLBY_ATOMS'
  | 'DOLBY_SURROUND'

export interface MediaOptionViewData {
  readonly id: string
  readonly streamIndex: number
  readonly title: string
  readonly summary: string
}

export interface MediaInfoRowViewData {
  readonly title: string
  readonly value: string
  readonly dividerBefore: boolean
}

export interface MediaInfoCardViewData {
  readonly id: string
  readonly title: string
  readonly rows: MediaInfoRowViewData[]
}

export interface MediaInfoSectionViewData {
  readonly id: string
  readonly title: string
  readonly cards: MediaInfoCardViewData[]
}

export interface PlaybackSourceViewData {
  readonly id: string
  readonly compactLabel: string
  readonly title: string
  readonly summary: string
  readonly filePath: string
  readonly fileSize: string
  readonly fileCreatedDate: string
  readonly addedDate: string
  readonly tagKeys: VideoTag[]
  readonly audioOptions: MediaOptionViewData[]
  readonly subtitleOptions: MediaOptionViewData[]
  readonly defaultAudioOptionIndex: number
  readonly defaultSubtitleOptionIndex: number
  readonly videoSummary: string
  readonly audioSummary: string
  readonly subtitleSummary: string
  readonly infoSections: MediaInfoSectionViewData[]
}

export interface VideoDetailPresentationAdapter {
  readonly provider: MediaProviderKind

  present(sources: MediaFileInfo[]): PlaybackSourceViewData[]

  episodeLabel(seasonNumber?: number, episodeNumber?: number): string
}
