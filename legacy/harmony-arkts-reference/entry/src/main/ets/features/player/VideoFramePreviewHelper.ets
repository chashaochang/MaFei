import { image } from '@kit.ImageKit'
import { media } from '@kit.MediaKit'
import { BusinessError } from '@kit.BasicServicesKit'
import { MediaPlaybackRequestHeader } from '../../media/playback/MediaPlaybackModels'

const PREVIEW_FRAME_MAX_EDGE: number = 256
const DEFAULT_PREVIEW_FRAME_WIDTH: number = 256
const DEFAULT_PREVIEW_FRAME_HEIGHT: number = 144

interface PreviewFrameSize {
  width: number
  height: number
}

export class VideoFramePreviewHelper {
  private extractor?: media.AVMetadataExtractor
  private currentSourceUrl: string = ''
  private currentSourceHeaders: MediaPlaybackRequestHeader[] = []
  private currentPreviewFrameSize?: PreviewFrameSize
  private previewFrameSizeTask?: Promise<void>

  async prepare(videoUrl: string, headers: MediaPlaybackRequestHeader[] = []): Promise<void> {
    if (!this.supportsPreview(videoUrl) || !canIUse('SystemCapability.Multimedia.Media.AVMetadataExtractor')) {
      return
    }
    try {
      const extractor = await this.ensureExtractor(videoUrl, headers)
      await this.ensurePreviewFrameSizeReady(extractor)
    } catch (err) {
      console.error(`[VideoFramePreview] prepare failed: ${JSON.stringify(err as BusinessError)}`)
    }
  }

  async fetchFrameByTime(videoUrl: string, timeMs: number,
    headers: MediaPlaybackRequestHeader[] = []): Promise<image.PixelMap | undefined> {
    if (!this.supportsPreview(videoUrl) || !canIUse('SystemCapability.Multimedia.Media.AVMetadataExtractor')) {
      return undefined
    }
    try {
      const extractor = await this.ensureExtractor(videoUrl, headers)
      this.ensurePreviewFrameSizeReady(extractor).catch(() => {
      })
      const previewFrameSize = this.currentPreviewFrameSize ?? {
        width: DEFAULT_PREVIEW_FRAME_WIDTH,
        height: DEFAULT_PREVIEW_FRAME_HEIGHT
      }
      return await extractor.fetchFrameByTime(
        Math.max(0, Math.floor(timeMs * 1000)),
        media.AVImageQueryOptions.AV_IMAGE_QUERY_CLOSEST_SYNC,
        previewFrameSize
      )
    } catch (err) {
      console.error(`[VideoFramePreview] fetch frame failed: ${JSON.stringify(err as BusinessError)}`)
      return undefined
    }
  }

  async release(): Promise<void> {
    const extractor = this.extractor
    this.extractor = undefined
    this.currentSourceUrl = ''
    this.currentSourceHeaders = []
    this.currentPreviewFrameSize = undefined
    this.previewFrameSizeTask = undefined
    if (!extractor) {
      return
    }
    try {
      await extractor.release()
    } catch (err) {
      console.error(`[VideoFramePreview] release extractor failed: ${JSON.stringify(err as BusinessError)}`)
    }
  }

  private async ensureExtractor(videoUrl: string,
    headers: MediaPlaybackRequestHeader[]): Promise<media.AVMetadataExtractor> {
    if (this.extractor && this.currentSourceUrl === videoUrl && this.headersEqual(headers, this.currentSourceHeaders)) {
      return this.extractor
    }
    await this.release()
    const extractor = await media.createAVMetadataExtractor()
    const headerRecord = this.toHeaderRecord(headers)
    extractor.setUrlSource(videoUrl, headerRecord)
    this.extractor = extractor
    this.currentSourceUrl = videoUrl
    this.currentSourceHeaders = headers.slice()
    return extractor
  }

  private toHeaderRecord(headers: MediaPlaybackRequestHeader[]): Record<string, string> | undefined {
    const result: Record<string, string> = {}
    let count = 0
    for (const header of headers) {
      const name = header.name.trim()
      if (name.length === 0 || header.value.length === 0) {
        continue
      }
      result[name] = header.value
      count++
    }
    return count > 0 ? result : undefined
  }

  private headersEqual(left: MediaPlaybackRequestHeader[], right: MediaPlaybackRequestHeader[]): boolean {
    if (left.length !== right.length) {
      return false
    }
    for (let index = 0; index < left.length; index++) {
      if (left[index].name !== right[index].name || left[index].value !== right[index].value) {
        return false
      }
    }
    return true
  }

  private async ensurePreviewFrameSizeReady(extractor: media.AVMetadataExtractor): Promise<void> {
    if (this.currentPreviewFrameSize) {
      return
    }
    if (this.previewFrameSizeTask) {
      await this.previewFrameSizeTask
      return
    }
    this.previewFrameSizeTask = (async () => {
      let width: number = DEFAULT_PREVIEW_FRAME_WIDTH
      let height: number = DEFAULT_PREVIEW_FRAME_HEIGHT
      try {
        const metadata = await extractor.fetchMetadata()
        let videoWidth = this.parseMetadataNumber(metadata.videoWidth)
        let videoHeight = this.parseMetadataNumber(metadata.videoHeight)
        const videoOrientation = this.parseMetadataNumber(metadata.videoOrientation)
        if ((videoOrientation === 90 || videoOrientation === 270) && videoWidth > 0 && videoHeight > 0) {
          const originalWidth = videoWidth
          videoWidth = videoHeight
          videoHeight = originalWidth
        }
        if (videoWidth > 0 && videoHeight > 0) {
          const aspectRatio = videoWidth / videoHeight
          if (aspectRatio >= 1) {
            width = PREVIEW_FRAME_MAX_EDGE
            height = Math.max(1, Math.round(PREVIEW_FRAME_MAX_EDGE / aspectRatio))
          } else {
            height = PREVIEW_FRAME_MAX_EDGE
            width = Math.max(1, Math.round(PREVIEW_FRAME_MAX_EDGE * aspectRatio))
          }
        }
      } catch (err) {
        console.error(`[VideoFramePreview] fetch metadata failed: ${JSON.stringify(err as BusinessError)}`)
      }
      this.currentPreviewFrameSize = { width, height }
    })()
    try {
      await this.previewFrameSizeTask
    } finally {
      this.previewFrameSizeTask = undefined
    }
  }

  private parseMetadataNumber(value?: string | number | null): number {
    if (typeof value === 'number') {
      return Number.isFinite(value) ? value : 0
    }
    if (typeof value === 'string') {
      const parsed = Number(value)
      return Number.isFinite(parsed) ? parsed : 0
    }
    return 0
  }

  private supportsPreview(videoUrl: string): boolean {
    const normalizedUrl = videoUrl.toLowerCase()
    if (!(normalizedUrl.startsWith('http://') || normalizedUrl.startsWith('https://'))) {
      return false
    }
    return !normalizedUrl.includes('.m3u8') && !normalizedUrl.includes('.mpd')
  }
}
