import { UserDto } from 'sdk/src/main/ets/generated-client/models'
import {
  ManagementLibraryApiService,
  ManagementLibraryService
} from './ManagementLibraryApiService'
import {
  ManagementLibraryOperationCode,
  ManagementLibraryOperationResult,
  ManagementMediaDeleteTarget
} from './ManagementLibraryModels'
import { ManagementMediaDeletePolicy } from './ManagementMediaDeletePolicy'

interface ManagementMediaDeleteHttpResponse {
  status: number
  data?: string | Record<string, string>
}

interface ManagementMediaDeleteHttpError {
  response?: ManagementMediaDeleteHttpResponse
  message?: string
  isAxiosError?: boolean
}

export class ManagementMediaDeleteRepository {
  private service: ManagementLibraryService

  constructor(service: ManagementLibraryService = new ManagementLibraryApiService()) {
    this.service = service
  }

  async requireAdministrator(): Promise<ManagementLibraryOperationResult> {
    try {
      const user: UserDto = await this.service.getCurrentUser()
      return user.Policy?.IsAdministrator === true ?
        ManagementLibraryOperationResult.success() :
        ManagementLibraryOperationResult.failure(
          ManagementLibraryOperationCode.PermissionDenied, 'management_permission_denied')
    } catch (error) {
      return this.failureFor(error as Object)
    }
  }

  async deleteTargets(targets: ManagementMediaDeleteTarget[]):
    Promise<ManagementLibraryOperationResult> {
    const access = await this.requireAdministrator()
    if (!access.ok) {
      return access
    }

    const summary = ManagementMediaDeletePolicy.summarize(targets, true)
    if (summary.allowedIds.length === 0) {
      return ManagementLibraryOperationResult.failure(
        ManagementLibraryOperationCode.PermissionDenied, 'management_media_delete_no_eligible')
    }

    try {
      await this.service.deleteItems(summary.allowedIds)
      return ManagementLibraryOperationResult.success()
    } catch (error) {
      return this.failureFor(error as Object)
    }
  }

  private failureFor(error: Object): ManagementLibraryOperationResult {
    const status = this.getHttpStatus(error)
    const code = this.codeForStatus(status)
    const message = this.isOffline(error) ? 'management_access_offline' :
      this.getServerErrorMessage(error) || this.defaultMessage(code)
    return ManagementLibraryOperationResult.failure(code, message)
  }

  private codeForStatus(status: number | undefined): ManagementLibraryOperationCode {
    if (status === 401) return ManagementLibraryOperationCode.AuthenticationExpired
    if (status === 403) return ManagementLibraryOperationCode.PermissionDenied
    if (status === 404) return ManagementLibraryOperationCode.NotFound
    if (status === 409) return ManagementLibraryOperationCode.Conflict
    return ManagementLibraryOperationCode.Failed
  }

  private defaultMessage(code: ManagementLibraryOperationCode): string {
    if (code === ManagementLibraryOperationCode.AuthenticationExpired) {
      return 'management_library_authentication_expired'
    }
    if (code === ManagementLibraryOperationCode.PermissionDenied) {
      return 'management_permission_denied'
    }
    if (code === ManagementLibraryOperationCode.NotFound) {
      return 'management_library_not_found'
    }
    if (code === ManagementLibraryOperationCode.Conflict) {
      return 'management_media_delete_conflict'
    }
    return 'management_media_delete_failed'
  }

  private getHttpStatus(error: Object): number | undefined {
    return (error as ManagementMediaDeleteHttpError).response?.status
  }

  private isOffline(error: Object): boolean {
    const httpError = error as ManagementMediaDeleteHttpError
    return httpError.isAxiosError === true && httpError.response === undefined
  }

  private getServerErrorMessage(error: Object): string {
    const httpError = error as ManagementMediaDeleteHttpError
    const data = httpError.response?.data
    if (typeof data === 'string') {
      return this.normalizeMessage(data)
    }
    if (data) {
      return this.normalizeMessage(data.Message || data.message || data.Error || data.error ||
        data.Detail || data.detail || data.Title || data.title)
    }
    return ''
  }

  private normalizeMessage(message: string | undefined): string {
    if (!message) return ''
    const normalized = message.replace(/\s+/g, ' ').trim()
    return normalized.length > 500 ? `${normalized.slice(0, 497)}...` : normalized
  }
}
