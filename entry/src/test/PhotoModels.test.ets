import { describe, expect, it } from '@ohos/hypium'
import {
  PhotoExifInfo,
  PhotoOrientation,
  PhotoPageResult,
  PhotoViewerRouteParam,
  photoOrientationMirrored,
  photoOrientationRotation
} from '../main/ets/features/photo/PhotoModels'

export default function photoModelsTest() {
  describe('PhotoModels', () => {
    it('mapsExifOrientationToRotationAndMirroring', 0, () => {
      expect(photoOrientationRotation(PhotoOrientation.Normal)).assertEqual(0)
      expect(photoOrientationRotation(PhotoOrientation.Transpose)).assertEqual(90)
      expect(photoOrientationRotation(PhotoOrientation.Rotate180)).assertEqual(180)
      expect(photoOrientationRotation(PhotoOrientation.Transverse)).assertEqual(270)
      expect(photoOrientationMirrored(PhotoOrientation.Rotate90)).assertFalse()
      expect(photoOrientationMirrored(PhotoOrientation.MirrorHorizontal)).assertTrue()
      expect(photoOrientationMirrored(PhotoOrientation.MirrorVertical)).assertTrue()
    })

    it('keepsMissingExifNumbersOptional', 0, () => {
      const exif: PhotoExifInfo = {
        cameraMake: '',
        cameraModel: '',
        software: ''
      }

      expect(exif.exposureTime === undefined).assertTrue()
      expect(exif.isoSpeedRating === undefined).assertTrue()
      expect(exif.orientation === undefined).assertTrue()
    })

    it('keepsViewerRoutesSmallAndPageCursorsExplicit', 0, () => {
      const route: PhotoViewerRouteParam = {
        sourceKey: 'server|user|library',
        initialPhotoId: 'photo-1'
      }
      const page: PhotoPageResult<string> = {
        items: ['photo-1'],
        startIndex: 0,
        nextStartIndex: 4,
        totalCount: 10,
        hasMore: true
      }

      expect(route.sourceKey).assertEqual('server|user|library')
      expect(route.initialPhotoId).assertEqual('photo-1')
      expect(page.nextStartIndex).assertEqual(4)
    })
  })
}
