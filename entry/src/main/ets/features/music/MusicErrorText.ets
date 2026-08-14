import { MusicBrowseErrorCode, MusicPlaybackErrorCode } from './MusicModels'

export function musicBrowseErrorText(code: MusicBrowseErrorCode): ResourceStr {
  switch (code) {
    case MusicBrowseErrorCode.AuthenticationExpired:
      return $r('app.string.music_error_authentication_expired')
    case MusicBrowseErrorCode.PermissionDenied:
      return $r('app.string.music_browse_error_permission_denied')
    case MusicBrowseErrorCode.NotFound:
      return $r('app.string.music_error_not_found')
    case MusicBrowseErrorCode.Offline:
      return $r('app.string.music_error_offline')
    case MusicBrowseErrorCode.None:
    case MusicBrowseErrorCode.RequestFailed:
    default:
      return $r('app.string.music_load_failed')
  }
}

export function musicPlaybackErrorText(code: MusicPlaybackErrorCode): ResourceStr {
  switch (code) {
    case MusicPlaybackErrorCode.Authentication:
      return $r('app.string.music_error_authentication_expired')
    case MusicPlaybackErrorCode.PermissionDenied:
      return $r('app.string.music_error_permission_denied')
    case MusicPlaybackErrorCode.NotFound:
      return $r('app.string.music_error_not_found')
    case MusicPlaybackErrorCode.Network:
      return $r('app.string.music_error_offline')
    case MusicPlaybackErrorCode.NoPlayableSource:
      return $r('app.string.music_error_no_playable_source')
    case MusicPlaybackErrorCode.TranscodingFailed:
      return $r('app.string.music_error_transcoding_failed')
    case MusicPlaybackErrorCode.SystemPlayer:
      return $r('app.string.music_error_system_player')
    case MusicPlaybackErrorCode.None:
    default:
      return $r('app.string.music_playback_failed')
  }
}
