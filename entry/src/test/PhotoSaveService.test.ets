import { describe, expect, it } from '@ohos/hypium'
import {
  PhotoItem,
  PhotoOrientation,
  PhotoSaveState
} from '../main/ets/features/photo/PhotoModels'
import {
  PhotoDownloadPort,
  PhotoImportPort,
  PhotoSaveErrorCode,
  PhotoSaveService,
  PhotoTempFilePort
} from '../main/ets/features/photo/PhotoSaveService'

class FakePhotoImportError extends Error {
  readonly code: number

  constructor(code: number) {
    super('import failed')
    this.code = code
  }
}

class FakePhotoDownloadPort implements PhotoDownloadPort {
  calls: number = 0
  itemId: string = ''
  filePath: string = ''
  throws: boolean = false

  async download(itemId: string, filePath: string): Promise<void> {
    this.calls += 1
    this.itemId = itemId
    this.filePath = filePath
    if (this.throws) {
      throw new Error('download failed')
    }
  }
}

class FakePhotoImportPort implements PhotoImportPort {
  calls: number = 0
  filePath: string = ''
  throws: boolean = false
  errorCode: number = 0

  async importPhoto(filePath: string): Promise<void> {
    this.calls += 1
    this.filePath = filePath
    if (this.errorCode !== 0) {
      throw new FakePhotoImportError(this.errorCode)
    }
    if (this.throws) {
      throw new Error('import failed')
    }
  }
}

class FakePhotoTempFilePort implements PhotoTempFilePort {
  path: string = '/cache/photo.jpg'
  size: number = 128
  createCalls: number = 0
  sizeCalls: number = 0
  removeCalls: number = 0
  removedPath: string = ''
  sizeThrows: boolean = false
  removeThrows: boolean = false

  createPath(_photo: PhotoItem): string {
    this.createCalls += 1
    return this.path
  }

  fileSize(_filePath: string): number {
    this.sizeCalls += 1
    if (this.sizeThrows) {
      throw new Error('stat failed')
    }
    return this.size
  }

  remove(filePath: string): void {
    this.removeCalls += 1
    this.removedPath = filePath
    if (this.removeThrows) {
      throw new Error('cleanup failed')
    }
  }
}

function photo(id: string = ' photo-1 ', canDownload: boolean = true): PhotoItem {
  return {
    id: id,
    name: 'Photo',
    fileName: 'photo.heic',
    parentId: 'album-1',
    path: '/photos/photo.heic',
    format: 'heic',
    dateTaken: '2026-08-11T10:00:00Z',
    dateKey: '2026-08-11',
    dateTimestamp: 20260811,
    width: 4000,
    height: 3000,
    thumbnailUrl: 'https://photos/thumbnail',
    fullImageUrl: 'https://photos/full',
    favorite: false,
    canDownload: canDownload,
    exif: {
      cameraMake: '',
      cameraModel: '',
      software: '',
      orientation: PhotoOrientation.Normal
    }
  }
}

function service(download: FakePhotoDownloadPort, importer: FakePhotoImportPort,
  files: FakePhotoTempFilePort): PhotoSaveService {
  return new PhotoSaveService(download, importer, files)
}

export default function photoSaveServiceTest() {
  describe('PhotoSaveService', () => {
    it('downloadsImportsAndCleansUpTheOriginalPhoto', 0, async () => {
      const download = new FakePhotoDownloadPort()
      const importer = new FakePhotoImportPort()
      const files = new FakePhotoTempFilePort()

      const result = await service(download, importer, files).save(photo())

      expect(result.state).assertEqual(PhotoSaveState.Saved)
      expect(result.errorCode).assertEqual(PhotoSaveErrorCode.None)
      expect(download.calls).assertEqual(1)
      expect(download.itemId).assertEqual('photo-1')
      expect(download.filePath).assertEqual(files.path)
      expect(importer.calls).assertEqual(1)
      expect(importer.filePath).assertEqual(files.path)
      expect(files.removeCalls).assertEqual(1)
      expect(files.removedPath).assertEqual(files.path)
    })

    it('rejectsAnEmptyPhotoIdWithoutCallingPorts', 0, async () => {
      const download = new FakePhotoDownloadPort()
      const importer = new FakePhotoImportPort()
      const files = new FakePhotoTempFilePort()

      const result = await service(download, importer, files).save(photo('   '))

      expect(result.state).assertEqual(PhotoSaveState.Failed)
      expect(result.errorCode).assertEqual(PhotoSaveErrorCode.InvalidPhotoId)
      expect(download.calls).assertEqual(0)
      expect(importer.calls).assertEqual(0)
      expect(files.createCalls).assertEqual(0)
      expect(files.removeCalls).assertEqual(0)
    })

    it('rejectsDownloadsDisallowedByTheServerWithoutCallingPorts', 0, async () => {
      const download = new FakePhotoDownloadPort()
      const importer = new FakePhotoImportPort()
      const files = new FakePhotoTempFilePort()

      const result = await service(download, importer, files).save(photo('photo-1', false))

      expect(result.state).assertEqual(PhotoSaveState.Failed)
      expect(result.errorCode).assertEqual(PhotoSaveErrorCode.DownloadNotAllowed)
      expect(download.calls).assertEqual(0)
      expect(importer.calls).assertEqual(0)
      expect(files.createCalls).assertEqual(0)
      expect(files.removeCalls).assertEqual(0)
    })

    it('cleansUpWithoutImportingWhenDownloadFails', 0, async () => {
      const download = new FakePhotoDownloadPort()
      const importer = new FakePhotoImportPort()
      const files = new FakePhotoTempFilePort()
      download.throws = true

      const result = await service(download, importer, files).save(photo())

      expect(result.state).assertEqual(PhotoSaveState.Failed)
      expect(result.errorCode).assertEqual(PhotoSaveErrorCode.DownloadFailed)
      expect(importer.calls).assertEqual(0)
      expect(files.removeCalls).assertEqual(1)
    })

    it('rejectsAndCleansUpAnEmptyDownloadWithoutImporting', 0, async () => {
      const download = new FakePhotoDownloadPort()
      const importer = new FakePhotoImportPort()
      const files = new FakePhotoTempFilePort()
      files.size = 0

      const result = await service(download, importer, files).save(photo())

      expect(result.state).assertEqual(PhotoSaveState.Failed)
      expect(result.errorCode).assertEqual(PhotoSaveErrorCode.EmptyDownload)
      expect(importer.calls).assertEqual(0)
      expect(files.removeCalls).assertEqual(1)
    })

    it('cleansUpWhenGalleryImportFails', 0, async () => {
      const download = new FakePhotoDownloadPort()
      const importer = new FakePhotoImportPort()
      const files = new FakePhotoTempFilePort()
      importer.throws = true
      files.removeThrows = true

      const result = await service(download, importer, files).save(photo())

      expect(result.state).assertEqual(PhotoSaveState.Failed)
      expect(result.errorCode).assertEqual(PhotoSaveErrorCode.ImportFailed)
      expect(importer.calls).assertEqual(1)
      expect(files.removeCalls).assertEqual(1)
    })

    it('classifiesUnavailableSaveAuthorizationAndCleansUp', 0, async () => {
      const download = new FakePhotoDownloadPort()
      const importer = new FakePhotoImportPort()
      const files = new FakePhotoTempFilePort()
      importer.errorCode = 201

      const result = await service(download, importer, files).save(photo())

      expect(result.state).assertEqual(PhotoSaveState.Failed)
      expect(result.errorCode).assertEqual(PhotoSaveErrorCode.AuthorizationUnavailable)
      expect(importer.calls).assertEqual(1)
      expect(files.removeCalls).assertEqual(1)
    })

    it('classifiesAMissingDownloadedFileAndStillAttemptsCleanup', 0, async () => {
      const download = new FakePhotoDownloadPort()
      const importer = new FakePhotoImportPort()
      const files = new FakePhotoTempFilePort()
      files.sizeThrows = true

      const result = await service(download, importer, files).save(photo())

      expect(result.state).assertEqual(PhotoSaveState.Failed)
      expect(result.errorCode).assertEqual(PhotoSaveErrorCode.DownloadFailed)
      expect(importer.calls).assertEqual(0)
      expect(files.removeCalls).assertEqual(1)
    })

    it('doesNotReplaceSuccessWhenCleanupFails', 0, async () => {
      const download = new FakePhotoDownloadPort()
      const importer = new FakePhotoImportPort()
      const files = new FakePhotoTempFilePort()
      files.removeThrows = true

      const result = await service(download, importer, files).save(photo())

      expect(result.state).assertEqual(PhotoSaveState.Saved)
      expect(result.errorCode).assertEqual(PhotoSaveErrorCode.None)
      expect(files.removeCalls).assertEqual(1)
    })
  })
}
