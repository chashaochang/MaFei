import {
  ImageInfo,
  ImageKnife,
  ImageKnifeComponent,
  ImageKnifeOption,
  ImageSourceFrom
} from '@ohos/imageknifepro'
import { MediaImageRef } from '../media/catalog/MediaCatalogModels'
import {
  createMediaImageNetworkFallbackOption,
  createMediaImageKnifeOption,
  mediaImageRequestFingerprint
} from '../media/catalog/MediaImageKnifeAdapter'

const MEDIA_IMAGE_MAX_RETRIES: number = 2
const MEDIA_IMAGE_RETRY_DELAY_MS: number = 300

@ComponentV2
export struct MediaNetworkImage {
  @Param imageRef?: MediaImageRef = undefined
  @Param fallbackSource: ResourceStr = ''
  @Param placeholder: Resource = $r('app.media.placeholder_video')
  @Param objectFit: ImageFit = ImageFit.Cover
  @Param placeholderObjectFit: ImageFit = ImageFit.CENTER
  @Param placeholderBackground: ResourceColor = Color.Transparent
  @Param radius: number = 0
  @Event onLoadSuccess: () => void = () => {}
  @Event onLoadFailed: () => void = () => {}
  @Local option: ImageKnifeOption = new ImageKnifeOption()
  private requestFingerprint: string = '__uninitialized__'
  private retryCount: number = 0
  private retryTimer: number = -1
  private active: boolean = false

  aboutToAppear(): void {
    this.active = true
    this.updateOption()
  }

  aboutToDisappear(): void {
    this.active = false
    this.clearRetryTimer()
  }

  @Monitor('imageRef')
  onImageRefChanged(): void {
    this.updateOption()
  }

  @Monitor('fallbackSource')
  onFallbackSourceChanged(): void {
    this.updateOption()
  }

  private hasSource(): boolean {
    const imageSource = this.imageRef?.url || ''
    if (imageSource.trim().length > 0) {
      return true
    }
    return typeof this.fallbackSource === 'string' ? this.fallbackSource.trim().length > 0 : true
  }

  private updateOption(): void {
    const fingerprint = mediaImageRequestFingerprint(this.imageRef, this.fallbackSource)
    if (fingerprint === this.requestFingerprint) {
      return
    }
    this.requestFingerprint = fingerprint
    this.retryCount = 0
    this.clearRetryTimer()
    this.option = this.createOption(fingerprint)
  }

  private createOption(requestFingerprint: string, networkFallbackAttempt: number = 0): ImageKnifeOption {
    const next = networkFallbackAttempt > 0 ? createMediaImageNetworkFallbackOption(
      this.imageRef, this.fallbackSource, requestFingerprint, networkFallbackAttempt, Date.now()) :
      createMediaImageKnifeOption(this.imageRef, this.fallbackSource)
    next.placeholderSrc = this.placeholder
    next.errorSrc = this.placeholder
    next.objectFit = this.objectFit
    next.placeholderObjectFit = this.placeholderObjectFit
    next.errorObjectFit = this.placeholderObjectFit
    next.httpOption = {
      connectTimeout: 10000,
      readTimeout: 15000
    }
    if (this.radius > 0) {
      next.border = {
        radius: this.radius
      }
    }
    next.onLoadListener = {
      onLoadSuccess: (_imageInfo: ImageInfo): void => {
        this.handleLoadSuccess(requestFingerprint)
      },
      onMemorySuccess: (_imageInfo: ImageInfo): void => {
        this.handleLoadSuccess(requestFingerprint)
      },
      onLoadFailed: (error: string, imageInfo: ImageInfo): void => {
        this.handleLoadFailure(error, imageInfo, requestFingerprint, next, networkFallbackAttempt > 0)
      },
      onLoadCancel: (reason: string, imageInfo: ImageInfo): void => {
        console.info(`[MediaNetworkImage] load cancelled reason=${reason} request=${imageInfo.requestId} ` +
          `${this.diagnosticIdentity()}`)
      }
    }
    return next
  }

  private handleLoadSuccess(requestFingerprint: string): void {
    if (requestFingerprint !== this.requestFingerprint) {
      return
    }
    this.retryCount = 0
    this.clearRetryTimer()
    this.onLoadSuccess()
  }

  private handleLoadFailure(error: string, imageInfo: ImageInfo, requestFingerprint: string,
    failedOption: ImageKnifeOption, networkFallback: boolean): void {
    if (requestFingerprint !== this.requestFingerprint) {
      return
    }
    console.warn(`[MediaNetworkImage] load failed error=${error} http=${imageInfo.httpCode || 0} ` +
      `source=${imageInfo.imageSourceFrom} networkFallback=${networkFallback ? 1 : 0} ` +
      `request=${imageInfo.requestId} retry=${this.retryCount} ${this.diagnosticIdentity()}`)
    if (!this.active || this.retryCount >= MEDIA_IMAGE_MAX_RETRIES || imageInfo.requestId.length === 0) {
      this.onLoadFailed()
      return
    }
    const cacheFailure = !networkFallback &&
      (imageInfo.imageSourceFrom === ImageSourceFrom.FROM_MEMORY_CACHE ||
        imageInfo.imageSourceFrom === ImageSourceFrom.FROM_FILE_CACHE || !imageInfo.httpCode)
    if (cacheFailure) {
      const imageKnife = ImageKnife.getInstance()
      imageKnife.removeMemoryCache(failedOption)
      imageKnife.removeFileCache(failedOption)
      console.info(`[MediaNetworkImage] evicted unreadable cache request=${imageInfo.requestId} ` +
        `${this.diagnosticIdentity()}`)
    }
    this.retryCount += 1
    const retryDelay = MEDIA_IMAGE_RETRY_DELAY_MS * this.retryCount
    this.clearRetryTimer()
    this.retryTimer = setTimeout((): void => {
      this.retryTimer = -1
      if (!this.active || requestFingerprint !== this.requestFingerprint) {
        return
      }
      if (cacheFailure) {
        console.info(`[MediaNetworkImage] network fallback attempt=${this.retryCount} ` +
          `${this.diagnosticIdentity()}`)
        this.option = this.createOption(requestFingerprint, this.retryCount)
        return
      }
      console.info(`[MediaNetworkImage] retry request=${imageInfo.requestId} attempt=${this.retryCount} ` +
        `${this.diagnosticIdentity()}`)
      ImageKnife.getInstance().reload(imageInfo.requestId)
    }, retryDelay)
  }

  private clearRetryTimer(): void {
    if (this.retryTimer >= 0) {
      clearTimeout(this.retryTimer)
      this.retryTimer = -1
    }
  }

  private diagnosticIdentity(): string {
    const image = this.imageRef
    if (!image) {
      return 'provider=fallback'
    }
    return `provider=${image.provider} ownerKind=${image.owner.kind} imageKind=${image.kind} ` +
      `ownerId=${image.owner.id} path=${image.requestPath}`
  }

  build() {
    Stack({ alignContent: Alignment.Center }) {
      if (this.hasSource()) {
        ImageKnifeComponent({
          imageKnifeOption: this.option
        })
          .width('100%')
          .height('100%')
      } else {
        Image(this.placeholder)
          .width('100%')
          .height('100%')
          .objectFit(this.placeholderObjectFit)
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.placeholderBackground)
    .borderRadius(this.radius)
    .clip(this.radius > 0)
  }
}
