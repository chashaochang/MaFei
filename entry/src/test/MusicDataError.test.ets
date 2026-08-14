import { describe, expect, it } from '@ohos/hypium'
import {
  MusicDataErrorCode,
  MusicDataErrorMapper
} from '../main/ets/features/music/MusicDataError'

class TestMusicErrorResponse {
  status: number = 0
}

class TestMusicError extends Error {
  isAxiosError?: boolean
  response?: TestMusicErrorResponse
}

function httpError(status: number): TestMusicError {
  const error = new TestMusicError('request failed')
  error.response = new TestMusicErrorResponse()
  error.response.status = status
  return error
}

export default function musicDataErrorTest() {
  describe('MusicDataErrorMapper', () => {
    it('classifiesHttpAndOfflineFailures', 0, () => {
      expect(MusicDataErrorMapper.from(httpError(401)).code)
        .assertEqual(MusicDataErrorCode.AuthenticationExpired)
      expect(MusicDataErrorMapper.from(httpError(403)).code)
        .assertEqual(MusicDataErrorCode.PermissionDenied)
      expect(MusicDataErrorMapper.from(httpError(404)).code)
        .assertEqual(MusicDataErrorCode.NotFound)
      const offline = new TestMusicError('offline')
      offline.isAxiosError = true
      expect(MusicDataErrorMapper.from(offline).code).assertEqual(MusicDataErrorCode.Network)
    })

    it('classifiesInvalidAndUnknownFailures', 0, () => {
      const invalid = new TestMusicError('invalid')
      invalid.name = 'RequiredError'
      expect(MusicDataErrorMapper.from(invalid).code).assertEqual(MusicDataErrorCode.InvalidRequest)
      expect(MusicDataErrorMapper.from(new TestMusicError('unknown')).code)
        .assertEqual(MusicDataErrorCode.Unknown)
    })
  })
}
