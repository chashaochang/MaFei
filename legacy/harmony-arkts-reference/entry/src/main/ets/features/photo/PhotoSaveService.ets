import { common } from '@kit.AbilityKit'
import { BusinessError } from '@kit.BasicServicesKit'
import { fileIo, fileUri } from '@kit.CoreFileKit'
import { photoAccessHelper } from '@kit.MediaLibraryKit'
import { getLibraryApi } from 'sdk'
import { ApiClient } from '../../network/ApiClient'
import { PhotoItem, PhotoSaveState } from './PhotoModels'

const PHOTO_TEMP_FILE_PREFIX: string = 'mafei-photo-'
const PHOTO_DEFAULT_EXTENSION: string = 'jpg'
const PHOTO_MAX_EXTENSION_LENGTH: number = 16
const PHOTO_PERMISSION_DENIED_ERROR_CODE: number = 201

let photoTempFileSequence: number = 0

export enum PhotoSaveErrorCode {
  None,
  InvalidPhotoId,
  DownloadNotAllowed,
  DownloadFailed,
  EmptyDownload,
  AuthorizationUnavailable,
  ImportFailed
}

export interface PhotoSaveResult {
  state: PhotoSaveState
  errorCode: PhotoSaveErrorCode
}

export interface PhotoDownloadPort {
  download(itemId: string, filePath: string): Promise<void>
}

export interface PhotoImportPort {
  importPhoto(filePath: string): Promise<void>
}

export interface PhotoTempFilePort {
  createPath(photo: PhotoItem): string
  fileSize(filePath: string): number
  remove(filePath: string): void
}

class SystemPhotoDownloadPort implements PhotoDownloadPort {
  private readonly context: common.UIAbilityContext

  constructor(context: common.UIAbilityContext) {
    this.context = context
  }

  async download(itemId: string, filePath: string): Promise<void> {
    await getLibraryApi(ApiClient.Instance()).getDownload({ itemId: itemId }, {
      context: this.context,
      filePath: filePath
    })
  }
}

class SystemPhotoImportPort implements PhotoImportPort {
  private readonly context: common.UIAbilityContext

  constructor(context: common.UIAbilityContext) {
    this.context = context
  }

  async importPhoto(filePath: string): Promise<void> {
    const helper = photoAccessHelper.getPhotoAccessHelper(this.context)
    try {
      const uri = fileUri.getUriFromPath(filePath)
      const request = photoAccessHelper.MediaAssetChangeRequest.createImageAssetRequest(this.context, uri)
      await helper.applyChanges(request)
    } finally {
      try {
        await helper.release()
      } catch (_error) {
        // Releasing the helper must not replace the save result.
      }
    }
  }
}

class SystemPhotoTempFilePort implements PhotoTempFilePort {
  private readonly context: common.UIAbilityContext

  constructor(context: common.UIAbilityContext) {
    this.context = context
  }

  createPath(photo: PhotoItem): string {
    photoTempFileSequence += 1
    const separator = this.context.cacheDir.endsWith('/') ? '' : '/'
    const extension = photoExtension(photo)
    return `${this.context.cacheDir}${separator}${PHOTO_TEMP_FILE_PREFIX}${Date.now()}-${photoTempFileSequence}.${extension}`
  }

  fileSize(filePath: string): number {
    return fileIo.statSync(filePath).size
  }

  remove(filePath: string): void {
    fileIo.unlinkSync(filePath)
  }
}

function photoExtension(photo: PhotoItem): string {
  const fileName = photo.fileName.trim()
  const dotIndex = fileName.lastIndexOf('.')
  const fileNameExtension = dotIndex >= 0 && dotIndex + 1 < fileName.length ?
    fileName.substring(dotIndex + 1) : ''
  const candidate = fileNameExtension.length > 0 ? fileNameExtension : photo.format
  const slashIndex = candidate.lastIndexOf('/')
  const withoutMimePrefix = slashIndex >= 0 && slashIndex + 1 < candidate.length ?
    candidate.substring(slashIndex + 1) : candidate
  const sanitized = withoutMimePrefix.trim().toLowerCase().replace(/[^a-z0-9]/g, '')
  return sanitized.length > 0 && sanitized.length <= PHOTO_MAX_EXTENSION_LENGTH ?
    sanitized : PHOTO_DEFAULT_EXTENSION
}

export class PhotoSaveService {
  private readonly downloadPort: PhotoDownloadPort
  private readonly importPort: PhotoImportPort
  private readonly tempFilePort: PhotoTempFilePort

  constructor(downloadPort: PhotoDownloadPort, importPort: PhotoImportPort, tempFilePort: PhotoTempFilePort) {
    this.downloadPort = downloadPort
    this.importPort = importPort
    this.tempFilePort = tempFilePort
  }

  static create(context: common.UIAbilityContext): PhotoSaveService {
    return new PhotoSaveService(
      new SystemPhotoDownloadPort(context),
      new SystemPhotoImportPort(context),
      new SystemPhotoTempFilePort(context)
    )
  }

  async save(photo: PhotoItem): Promise<PhotoSaveResult> {
    const itemId = photo.id.trim()
    if (itemId.length === 0) {
      return PhotoSaveService.failed(PhotoSaveErrorCode.InvalidPhotoId)
    }
    if (!photo.canDownload) {
      return PhotoSaveService.failed(PhotoSaveErrorCode.DownloadNotAllowed)
    }

    let filePath: string
    try {
      filePath = this.tempFilePort.createPath(photo)
    } catch (_error) {
      return PhotoSaveService.failed(PhotoSaveErrorCode.DownloadFailed)
    }

    try {
      try {
        await this.downloadPort.download(itemId, filePath)
      } catch (_error) {
        return PhotoSaveService.failed(PhotoSaveErrorCode.DownloadFailed)
      }

      let fileSize: number
      try {
        fileSize = this.tempFilePort.fileSize(filePath)
      } catch (_error) {
        return PhotoSaveService.failed(PhotoSaveErrorCode.DownloadFailed)
      }
      if (fileSize <= 0) {
        return PhotoSaveService.failed(PhotoSaveErrorCode.EmptyDownload)
      }

      try {
        await this.importPort.importPhoto(filePath)
      } catch (error) {
        if (PhotoSaveService.isAuthorizationUnavailable(error as BusinessError)) {
          return PhotoSaveService.failed(PhotoSaveErrorCode.AuthorizationUnavailable)
        }
        return PhotoSaveService.failed(PhotoSaveErrorCode.ImportFailed)
      }
      return {
        state: PhotoSaveState.Saved,
        errorCode: PhotoSaveErrorCode.None
      }
    } finally {
      try {
        this.tempFilePort.remove(filePath)
      } catch (_error) {
        // Cache cleanup is best effort and must not replace the primary result.
      }
    }
  }

  private static failed(errorCode: PhotoSaveErrorCode): PhotoSaveResult {
    return {
      state: PhotoSaveState.Failed,
      errorCode: errorCode
    }
  }

  private static isAuthorizationUnavailable(error: BusinessError): boolean {
    return error !== undefined && error !== null && error.code === PHOTO_PERMISSION_DENIED_ERROR_CODE
  }
}
