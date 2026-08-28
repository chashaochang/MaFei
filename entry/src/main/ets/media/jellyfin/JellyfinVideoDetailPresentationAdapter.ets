import { MediaFileInfo, MediaStreamInfo } from '../catalog/MediaCatalogModels'
import { MediaProviderKind } from '../MediaProviderKind'
import { BaseVideoDetailPresentationAdapter } from '../presentation/BaseVideoDetailPresentationAdapter'

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
}
