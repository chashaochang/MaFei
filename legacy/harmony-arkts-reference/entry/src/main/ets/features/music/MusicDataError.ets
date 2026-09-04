export enum MusicDataErrorCode {
  AuthenticationExpired,
  PermissionDenied,
  NotFound,
  Network,
  InvalidRequest,
  Unknown
}

export class MusicDataError extends Error {
  readonly code: MusicDataErrorCode

  constructor(code: MusicDataErrorCode, message: string) {
    super(message)
    this.code = code
  }
}

export class MusicDataErrorMapper {
  static from(error: Object): MusicDataError {
    if (error instanceof MusicDataError) {
      return error
    }
    const requestError = error as MusicRequestError
    const status = requestError.response?.status
    if (status === 401) {
      return new MusicDataError(MusicDataErrorCode.AuthenticationExpired,
        MusicDataErrorMapper.message(error, 'Music authentication expired'))
    }
    if (status === 403) {
      return new MusicDataError(MusicDataErrorCode.PermissionDenied,
        MusicDataErrorMapper.message(error, 'Music access is not allowed'))
    }
    if (status === 404) {
      return new MusicDataError(MusicDataErrorCode.NotFound,
        MusicDataErrorMapper.message(error, 'Music content was not found'))
    }
    if (requestError.isAxiosError === true && requestError.response === undefined) {
      return new MusicDataError(MusicDataErrorCode.Network,
        MusicDataErrorMapper.message(error, 'Music network request failed'))
    }
    if (requestError.name === 'RequiredError' || requestError.name === 'TypeError') {
      return new MusicDataError(MusicDataErrorCode.InvalidRequest,
        MusicDataErrorMapper.message(error, 'Music request is invalid'))
    }
    return new MusicDataError(MusicDataErrorCode.Unknown,
      MusicDataErrorMapper.message(error, 'Music request failed'))
  }

  private static message(error: Object, fallback: string): string {
    const value = error as Error
    return value.message && value.message.length > 0 ? value.message : fallback
  }
}

interface MusicHttpResponse {
  status: number
}

interface MusicRequestError extends Error {
  isAxiosError?: boolean
  response?: MusicHttpResponse
}
