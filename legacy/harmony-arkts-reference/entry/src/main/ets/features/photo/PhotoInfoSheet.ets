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
          if (this.photo.dateTaken.length > 0) {
            PhotoInfoRow({
              label: $r('app.string.photo_info_date'),
              value: this.formatDate(this.photo.dateTaken),
              nativeSurface: this.nativeSurface
            })
          }
          if (this.photo.width > 0 && this.photo.height > 0) {
            PhotoInfoRow({
              label: $r('app.string.photo_info_dimensions'),
              value: `${this.photo.width} × ${this.photo.height}`,
              nativeSurface: this.nativeSurface
            })
          }
          if (this.photo.fileName.length > 0) {
            PhotoInfoRow({
              label: $r('app.string.photo_info_file_name'),
              value: this.photo.fileName,
              multiLine: true,
              nativeSurface: this.nativeSurface
            })
          }
          if (this.photo.format.length > 0) {
            PhotoInfoRow({
              label: $r('app.string.photo_info_format'),
              value: this.photo.format.toUpperCase(),
              nativeSurface: this.nativeSurface
            })
          }
          if (this.photo.path.length > 0) {
            PhotoInfoRow({
              label: $r('app.string.photo_info_path'),
              value: this.photo.path,
              multiLine: true,
              nativeSurface: this.nativeSurface
            })
          }
          if (this.camera(this.photo).length > 0) {
            PhotoInfoRow({
              label: $r('app.string.photo_info_camera'),
              value: this.camera(this.photo),
              nativeSurface: this.nativeSurface
            })
          }
          if (this.photo.exif.software.length > 0) {
            PhotoInfoRow({
              label: $r('app.string.photo_info_software'),
              value: this.photo.exif.software,
              nativeSurface: this.nativeSurface
            })
          }
          if (this.exposure(this.photo).length > 0) {
            PhotoInfoRow({
              label: $r('app.string.photo_info_exposure'),
              value: this.exposure(this.photo),
              nativeSurface: this.nativeSurface
            })
          }
          if (this.focalLength(this.photo).length > 0) {
            PhotoInfoRow({
              label: $r('app.string.photo_info_focal_length'),
              value: this.focalLength(this.photo),
              nativeSurface: this.nativeSurface
            })
          }
          if (this.aperture(this.photo).length > 0) {
            PhotoInfoRow({
              label: $r('app.string.photo_info_aperture'),
              value: this.aperture(this.photo),
              nativeSurface: this.nativeSurface
            })
          }
          if (this.shutterSpeed(this.photo).length > 0) {
            PhotoInfoRow({
              label: $r('app.string.photo_info_shutter_speed'),
              value: this.shutterSpeed(this.photo),
              nativeSurface: this.nativeSurface
            })
          }
          if (this.iso(this.photo).length > 0) {
            PhotoInfoRow({
              label: $r('app.string.photo_info_iso'),
              value: this.iso(this.photo),
              nativeSurface: this.nativeSurface
            })
          }
          if (this.location(this.photo).length > 0) {
            PhotoInfoRow({
              label: $r('app.string.photo_info_location'),
              value: this.location(this.photo),
              multiLine: true,
              nativeSurface: this.nativeSurface
            })
          }
          if (this.hasOrientation(this.photo)) {
            PhotoInfoRow({
              label: $r('app.string.photo_info_orientation'),
              value: this.orientation(this.photo),
              nativeSurface: this.nativeSurface
            })
          }
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

@ComponentV2
struct PhotoInfoRow {
  @Require @Param label: ResourceStr
  @Require @Param value: ResourceStr
  @Param multiLine: boolean = false
  @Param nativeSurface: boolean = false

  build() {
    Row({ space: 16 }) {
      Text(this.label)
        .width(88)
        .fontSize(13)
        .fontColor(this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1'))
      Text(this.value)
        .layoutWeight(1)
        .fontSize(14)
        .fontColor(this.nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary'))
        .maxLines(this.multiLine ? 3 : 1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .textAlign(TextAlign.End)
    }
    .width('100%')
    .constraintSize({ minHeight: 48 })
    .padding({ top: 10, bottom: 10 })
    .border({
      width: { bottom: 0.5 },
      color: this.nativeSurface ? $r('sys.color.comp_divider') : $r('app.color.border')
    })
  }
}
