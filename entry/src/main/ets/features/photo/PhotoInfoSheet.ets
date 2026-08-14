import {
  photoOrientationMirrored,
  photoOrientationRotation,
  PhotoItem
} from './PhotoModels'

@ComponentV2
export struct PhotoInfoSheet {
  @Require @Param photo: PhotoItem | null
  @Require @Param loading: boolean
  @Require @Param loadFailed: boolean
  @Require @Param nativeSurface: boolean
  @Require @Param onRetry: () => void

  private primaryTextColor(): ResourceColor {
    return this.nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  private dividerColor(): ResourceColor {
    return this.nativeSurface ? $r('sys.color.comp_divider') : $r('app.color.border')
  }

  private formatDate(value: string): string {
    if (value.length === 0) {
      return ''
    }
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) {
      return value
    }
    const year = date.getFullYear().toString()
    const month = (date.getMonth() + 1).toString().padStart(2, '0')
    const day = date.getDate().toString().padStart(2, '0')
    const hour = date.getHours().toString().padStart(2, '0')
    const minute = date.getMinutes().toString().padStart(2, '0')
    return `${year}-${month}-${day} ${hour}:${minute}`
  }

  private camera(photo: PhotoItem): string {
    const parts: string[] = []
    if (photo.exif.cameraMake.length > 0) {
      parts.push(photo.exif.cameraMake)
    }
    if (photo.exif.cameraModel.length > 0) {
      parts.push(photo.exif.cameraModel)
    }
    return parts.join(' ')
  }

  private exposure(photo: PhotoItem): string {
    const value = photo.exif.exposureTime
    if (value === undefined || value <= 0) {
      return ''
    }
    if (value < 1) {
      return `1/${Math.max(1, Math.round(1 / value))} s`
    }
    return `${value.toFixed(value < 10 ? 2 : 0)} s`
  }

  private focalLength(photo: PhotoItem): string {
    const value = photo.exif.focalLength
    return value !== undefined && value > 0 ? `${value.toFixed(1)} mm` : ''
  }

  private aperture(photo: PhotoItem): string {
    const value = photo.exif.aperture
    return value !== undefined && value > 0 ? `f/${value.toFixed(1)}` : ''
  }

  private shutterSpeed(photo: PhotoItem): string {
    const value = photo.exif.shutterSpeed
    return value !== undefined && Number.isFinite(value) ? value.toFixed(2) : ''
  }

  private iso(photo: PhotoItem): string {
    const value = photo.exif.isoSpeedRating
    return value !== undefined && value > 0 ? Math.round(value).toString() : ''
  }

  private location(photo: PhotoItem): string {
    const latitude = photo.exif.latitude
    const longitude = photo.exif.longitude
    if (latitude === undefined || longitude === undefined) {
      return ''
    }
    const altitude = photo.exif.altitude
    return altitude === undefined ?
      `${latitude.toFixed(5)}, ${longitude.toFixed(5)}` :
      `${latitude.toFixed(5)}, ${longitude.toFixed(5)} · ${altitude.toFixed(1)} m`
  }

  private hasOrientation(photo: PhotoItem): boolean {
    return photo.exif.orientation !== undefined
  }

  private orientation(photo: PhotoItem): ResourceStr {
    const orientation = photo.exif.orientation
    if (orientation === undefined) {
      return ''
    }
    const rotation = photoOrientationRotation(orientation)
    if (photoOrientationMirrored(orientation)) {
      return rotation === 0 ? $r('app.string.photo_orientation_mirrored') :
        `${rotation}° · ${this.getString('photo_orientation_mirrored')}`
    }
    return rotation === 0 ? $r('app.string.photo_orientation_normal') : `${rotation}°`
  }

  private getString(name: string): string {
    return this.getUIContext().getHostContext()?.resourceManager.getStringByNameSync(name) || name
  }

  @Builder
  private row(label: ResourceStr, value: ResourceStr, multiLine: boolean = false) {
    Row({ space: 16 }) {
      Text(label)
        .width(88)
        .fontSize(13)
        .fontColor(this.secondaryTextColor())
      Text(value)
        .layoutWeight(1)
        .fontSize(14)
        .fontColor(this.primaryTextColor())
        .maxLines(multiLine ? 3 : 1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .textAlign(TextAlign.End)
    }
    .width('100%')
    .constraintSize({ minHeight: 48 })
    .padding({ top: 10, bottom: 10 })
    .border({ width: { bottom: 0.5 }, color: this.dividerColor() })
  }

  @Builder
  private details(photo: PhotoItem) {
    if (photo.dateTaken.length > 0) {
      this.row($r('app.string.photo_info_date'), this.formatDate(photo.dateTaken))
    }
    if (photo.width > 0 && photo.height > 0) {
      this.row($r('app.string.photo_info_dimensions'), `${photo.width} × ${photo.height}`)
    }
    if (photo.fileName.length > 0) {
      this.row($r('app.string.photo_info_file_name'), photo.fileName, true)
    }
    if (photo.format.length > 0) {
      this.row($r('app.string.photo_info_format'), photo.format.toUpperCase())
    }
    if (photo.path.length > 0) {
      this.row($r('app.string.photo_info_path'), photo.path, true)
    }
    if (this.camera(photo).length > 0) {
      this.row($r('app.string.photo_info_camera'), this.camera(photo))
    }
    if (photo.exif.software.length > 0) {
      this.row($r('app.string.photo_info_software'), photo.exif.software)
    }
    if (this.exposure(photo).length > 0) {
      this.row($r('app.string.photo_info_exposure'), this.exposure(photo))
    }
    if (this.focalLength(photo).length > 0) {
      this.row($r('app.string.photo_info_focal_length'), this.focalLength(photo))
    }
    if (this.aperture(photo).length > 0) {
      this.row($r('app.string.photo_info_aperture'), this.aperture(photo))
    }
    if (this.shutterSpeed(photo).length > 0) {
      this.row($r('app.string.photo_info_shutter_speed'), this.shutterSpeed(photo))
    }
    if (this.iso(photo).length > 0) {
      this.row($r('app.string.photo_info_iso'), this.iso(photo))
    }
    if (this.location(photo).length > 0) {
      this.row($r('app.string.photo_info_location'), this.location(photo), true)
    }
    if (this.hasOrientation(photo)) {
      this.row($r('app.string.photo_info_orientation'), this.orientation(photo))
    }
  }

  build() {
    Scroll() {
      Column() {
        Text($r('app.string.photo_info'))
          .width('100%')
          .fontSize(20)
          .fontWeight(FontWeight.Medium)
          .fontColor(this.primaryTextColor())
          .padding({ top: 4, bottom: 12 })

        if (this.photo) {
          this.details(this.photo)
        }

        if (this.loading) {
          Row({ space: 8 }) {
            LoadingProgress().width(20).height(20)
            Text($r('app.string.photo_loading_info'))
              .fontSize(13)
              .fontColor(this.secondaryTextColor())
          }
          .height(52)
          .justifyContent(FlexAlign.Center)
        } else if (this.loadFailed) {
          Column({ space: 10 }) {
            Text($r('app.string.photo_info_load_failed'))
              .fontSize(13)
              .fontColor(this.secondaryTextColor())
            Button($r('app.string.photo_retry'), { type: ButtonType.Normal })
              .height(40)
              .borderRadius(20)
              .onClick(() => this.onRetry())
          }
          .width('100%')
          .padding({ top: 16, bottom: 16 })
        }
      }
      .width('100%')
      .padding({ left: 20, right: 20, bottom: 24 })
    }
    .width('100%')
    .height('100%')
    .scrollBar(BarState.Off)
    .edgeEffect(EdgeEffect.Spring)
  }
}
