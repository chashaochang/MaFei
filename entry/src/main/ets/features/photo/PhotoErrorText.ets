import { PhotoBrowseErrorCode } from './PhotoModels'

export function photoBrowseErrorText(code: PhotoBrowseErrorCode): ResourceStr {
  switch (code) {
    case PhotoBrowseErrorCode.AuthenticationExpired:
      return $r('app.string.photo_error_authentication_expired')
    case PhotoBrowseErrorCode.PermissionDenied:
      return $r('app.string.photo_error_permission_denied')
    case PhotoBrowseErrorCode.NotFound:
      return $r('app.string.photo_error_not_found')
    case PhotoBrowseErrorCode.Offline:
      return $r('app.string.photo_error_offline')
    case PhotoBrowseErrorCode.None:
    case PhotoBrowseErrorCode.RequestFailed:
    default:
      return $r('app.string.photo_load_failed')
  }
}
