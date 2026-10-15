export enum MediaCatalogErrorCode {
  AuthenticationExpired = 'authentication_expired',
  PermissionDenied = 'permission_denied',
  NotFound = 'not_found',
  Offline = 'offline',
  ServiceUnavailable = 'service_unavailable',
  IncompatibleService = 'incompatible_service',
  InvalidRequest = 'invalid_request',
  Unsupported = 'unsupported',
  RequestFailed = 'request_failed'
}

export interface MediaCatalogErrorOptions {
  readonly code: MediaCatalogErrorCode
  readonly message: string
  readonly accountScope: string
  readonly operation: string
  readonly retryable?: boolean
}

export function defaultMediaCatalogErrorRetryable(code: MediaCatalogErrorCode): boolean {
  return code === MediaCatalogErrorCode.Offline ||
    code === MediaCatalogErrorCode.ServiceUnavailable ||
    code === MediaCatalogErrorCode.RequestFailed
}

export class MediaCatalogError extends Error {
  readonly code: MediaCatalogErrorCode
  readonly accountScope: string
  readonly operation: string
  readonly retryable: boolean

  constructor(options: MediaCatalogErrorOptions) {
    super(options.message)
    this.code = options.code
    this.accountScope = options.accountScope.trim()
    this.operation = options.operation.trim()
    this.retryable = options.retryable === undefined
      ? defaultMediaCatalogErrorRetryable(options.code)
      : options.retryable
  }

  isAuthenticationExpired(): boolean {
    return this.code === MediaCatalogErrorCode.AuthenticationExpired
  }
}
