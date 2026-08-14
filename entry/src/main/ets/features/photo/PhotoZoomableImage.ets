import { ImageKnifeComponent } from '@ohos/imageknifepro'
import { PhotoItem } from './PhotoModels'

const PHOTO_VIEWER_MIN_SCALE: number = 1
const PHOTO_VIEWER_DOUBLE_TAP_SCALE: number = 2.5
const PHOTO_VIEWER_MAX_SCALE: number = 5
const PHOTO_VIEWER_PAN_GESTURE_TAG: string = 'photo-viewer-image-pan'

@ComponentV2
export struct PhotoZoomableImage {
  @Require @Param photo: PhotoItem
  @Require @Param active: boolean
  @Require @Param resetRevision: number
  @Require @Param onSingleTap: () => void
  @Require @Param onInteraction: () => void
  @Require @Param onZoomChanged: (scale: number, offsetX: number, offsetY: number) => void

  @Local zoomScale: number = PHOTO_VIEWER_MIN_SCALE
  @Local offsetX: number = 0
  @Local offsetY: number = 0
  @Local loading: boolean = true
  @Local failed: boolean = false
  @Local loadRevision: number = 0
  @Local viewportWidth: number = 0
  @Local viewportHeight: number = 0

  private pinchStartScale: number = PHOTO_VIEWER_MIN_SCALE
  private panStartOffsetX: number = 0
  private panStartOffsetY: number = 0

  @Monitor('active')
  onActiveChanged(): void {
    if (!this.active) {
      this.resetTransform(false)
    }
  }

  @Monitor('resetRevision')
  onResetRevisionChanged(): void {
    this.resetTransform(this.active)
  }

  private clamp(value: number, min: number, max: number): number {
    return Math.min(max, Math.max(min, value))
  }

  private fittedImageSize(): SizeOptions {
    const sourceWidth = this.photo.width > 0 ? this.photo.width : this.viewportWidth
    const sourceHeight = this.photo.height > 0 ? this.photo.height : this.viewportHeight
    if (this.viewportWidth <= 0 || this.viewportHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
      return { width: this.viewportWidth, height: this.viewportHeight }
    }
    const fitScale = Math.min(this.viewportWidth / sourceWidth, this.viewportHeight / sourceHeight)
    return {
      width: sourceWidth * fitScale,
      height: sourceHeight * fitScale
    }
  }

  private clampedOffsetX(value: number, scale: number = this.zoomScale): number {
    const imageSize = this.fittedImageSize()
    const maxOffset = Math.max(0, (Number(imageSize.width) * scale - this.viewportWidth) / 2)
    return this.clamp(value, -maxOffset, maxOffset)
  }

  private clampedOffsetY(value: number, scale: number = this.zoomScale): number {
    const imageSize = this.fittedImageSize()
    const maxOffset = Math.max(0, (Number(imageSize.height) * scale - this.viewportHeight) / 2)
    return this.clamp(value, -maxOffset, maxOffset)
  }

  private publishZoom(): void {
    if (this.active) {
      this.onZoomChanged(this.zoomScale, this.offsetX, this.offsetY)
    }
  }

  private resetTransform(notify: boolean): void {
    this.zoomScale = PHOTO_VIEWER_MIN_SCALE
    this.offsetX = 0
    this.offsetY = 0
    if (notify) {
      this.publishZoom()
    }
  }

  private settleTransform(): void {
    if (this.zoomScale <= PHOTO_VIEWER_MIN_SCALE) {
      this.resetTransform(true)
      return
    }
    this.offsetX = this.clampedOffsetX(this.offsetX)
    this.offsetY = this.clampedOffsetY(this.offsetY)
    this.publishZoom()
  }

  private toggleDoubleTapZoom(): void {
    this.onInteraction()
    if (this.zoomScale > PHOTO_VIEWER_MIN_SCALE) {
      this.resetTransform(true)
      return
    }
    this.zoomScale = PHOTO_VIEWER_DOUBLE_TAP_SCALE
    this.offsetX = 0
    this.offsetY = 0
    this.publishZoom()
  }

  private retry(): void {
    this.loading = true
    this.failed = false
    this.loadRevision += 1
  }

  build() {
    Stack() {
      ImageKnifeComponent({
        imageKnifeOption: {
          loadSrc: this.photo.fullImageUrl,
          thumbnailSrc: this.photo.thumbnailUrl,
          objectFit: ImageFit.Contain,
          thumbnailObjectFit: ImageFit.Contain,
          placeholderSrc: $r('app.media.ic_photo'),
          placeholderObjectFit: ImageFit.CENTER,
          errorSrc: $r('app.media.ic_photo'),
          errorObjectFit: ImageFit.CENTER,
          forceWatch: this.loadRevision,
          onLoadListener: {
            onLoadStart: () => {
              this.loading = true
              this.failed = false
            },
            onLoadSuccess: () => {
              this.loading = false
              this.failed = false
            },
            onMemorySuccess: () => {
              this.loading = false
              this.failed = false
            },
            onLoadFailed: () => {
              this.loading = false
              this.failed = true
            }
          }
        }
      })
        .width('100%')
        .height('100%')
        .scale({ x: this.zoomScale, y: this.zoomScale })
        .translate({ x: this.offsetX, y: this.offsetY })

      if (this.loading && !this.failed) {
        LoadingProgress()
          .width(32)
          .height(32)
          .color(Color.White)
      }

      if (this.failed) {
        Column({ space: 12 }) {
          SymbolGlyph($r('sys.symbol.exclamationmark_circle'))
            .fontSize(40)
            .fontColor([Color.White])
          Text($r('app.string.photo_viewer_load_failed'))
            .fontSize(14)
            .fontColor(Color.White)
          Button($r('app.string.photo_retry'), { type: ButtonType.Normal })
            .height(40)
            .borderRadius(20)
            .onClick(() => this.retry())
        }
      }
    }
    .width('100%')
    .height('100%')
    .clip(true)
    .onAreaChange((_oldValue: Area, newValue: Area) => {
      this.viewportWidth = Number(newValue.width)
      this.viewportHeight = Number(newValue.height)
      this.settleTransform()
    })
    .gesture(GestureGroup(GestureMode.Parallel,
      GestureGroup(GestureMode.Exclusive,
        TapGesture({ count: 2 })
          .onAction(() => this.toggleDoubleTapZoom()),
        TapGesture({ count: 1 })
          .onAction(() => this.onSingleTap())
      ),
      PinchGesture({ fingers: 2 })
        .onActionStart(() => {
          this.onInteraction()
          this.pinchStartScale = this.zoomScale
        })
        .onActionUpdate((event: GestureEvent) => {
          const pinchEvent = event as PinchGestureEvent
          const nextScale = this.clamp(
            this.pinchStartScale * pinchEvent.scale,
            PHOTO_VIEWER_MIN_SCALE,
            PHOTO_VIEWER_MAX_SCALE
          )
          this.zoomScale = nextScale
          this.offsetX = this.clampedOffsetX(this.offsetX, nextScale)
          this.offsetY = this.clampedOffsetY(this.offsetY, nextScale)
          this.publishZoom()
        })
        .onActionEnd(() => this.settleTransform()),
      PanGesture({ fingers: 1 })
        .tag(PHOTO_VIEWER_PAN_GESTURE_TAG)
        .onActionStart(() => {
          if (this.zoomScale <= PHOTO_VIEWER_MIN_SCALE) {
            return
          }
          this.onInteraction()
          this.panStartOffsetX = this.offsetX
          this.panStartOffsetY = this.offsetY
        })
        .onActionUpdate((event: GestureEvent) => {
          if (this.zoomScale <= PHOTO_VIEWER_MIN_SCALE) {
            return
          }
          const panEvent = event as PanGestureEvent
          this.offsetX = this.clampedOffsetX(this.panStartOffsetX + panEvent.offsetX)
          this.offsetY = this.clampedOffsetY(this.panStartOffsetY + panEvent.offsetY)
          this.publishZoom()
        })
        .onActionEnd(() => this.settleTransform())
    ))
    .onGestureRecognizerJudgeBegin((_event: BaseGestureEvent, current: GestureRecognizer) => {
      if (current.getTag() === PHOTO_VIEWER_PAN_GESTURE_TAG &&
        this.zoomScale <= PHOTO_VIEWER_MIN_SCALE) {
        return GestureJudgeResult.REJECT
      }
      return GestureJudgeResult.CONTINUE
    })
  }
}
