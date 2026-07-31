import { ManagementErrorCode } from '../ManagementModels';
import { ManagementRepository } from '../ManagementRepository';
import { ManagementSessionApiService } from './ManagementSessionApiService';
import { ManagementSessionMapper } from './ManagementSessionMapper';
import { ManagementSessionActionResult, ManagementSessionItem } from './ManagementSessionModels';

interface SessionHttpResponse {
  status: number
  data?: Object | string
}

interface SessionHttpError {
  isAxiosError?: boolean
  response?: SessionHttpResponse
}

export class ManagementSessionRepository {
  private service: ManagementSessionApiService = new ManagementSessionApiService()
  private management: ManagementRepository = new ManagementRepository()

  isApiClientInitialized(): boolean {
    return this.management.isApiClientInitialized()
  }

  async checkCurrentUserAccess(): Promise<ManagementErrorCode> {
    return this.management.checkCurrentUserAccess()
  }

  async loadRecentSessions(nowMs: number = Date.now()): Promise<ManagementSessionItem[]> {
    return ManagementSessionMapper.mapMany(await this.service.getSessions(), nowMs)
  }

  async loadSession(sessionId: string, nowMs: number = Date.now()): Promise<ManagementSessionItem | null> {
    const sessions = await this.loadRecentSessions(nowMs)
    return sessions.find((item: ManagementSessionItem) => item.id === sessionId) || null
  }

  async playPause(sessionId: string): Promise<ManagementSessionActionResult> {
    return this.runControl(sessionId, async (): Promise<void> => {
      await this.service.sendPlayPause(sessionId)
    }, true)
  }

  async stop(sessionId: string): Promise<ManagementSessionActionResult> {
    return this.runControl(sessionId, async (): Promise<void> => {
      await this.service.sendStop(sessionId)
    }, true)
  }

  async message(sessionId: string, text: string): Promise<ManagementSessionActionResult> {
    const normalized = text.trim()
    if (!normalized) {
      return this.failure(ManagementErrorCode.RequestFailed, 'management_session_message_empty')
    }
    return this.runControl(sessionId, async (): Promise<void> => {
      await this.service.sendMessage(sessionId, normalized)
    }, false)
  }

  classifyError(error: Object): ManagementErrorCode {
    const httpError = error as SessionHttpError
    const status = httpError.response?.status
    if (status === 401) {
      return ManagementErrorCode.AuthenticationExpired
    }
    if (status === 403) {
      return ManagementErrorCode.PermissionDenied
    }
    if (status === 404) {
      return ManagementErrorCode.Unsupported
    }
    return httpError.isAxiosError === true && httpError.response === undefined ?
      ManagementErrorCode.Offline : ManagementErrorCode.RequestFailed
  }

  private async runControl(sessionId: string, command: () => Promise<void>, requireMedia: boolean):
    Promise<ManagementSessionActionResult> {
    const accessCode = await this.checkCurrentUserAccess()
    if (accessCode !== ManagementErrorCode.None) {
      return this.failure(accessCode, 'management_permission_check_failed')
    }
    try {
      const session = await this.loadSession(sessionId)
      if (!session) {
        return this.failure(ManagementErrorCode.RequestFailed, 'management_session_ended')
      }
      if (!session.controllable || (requireMedia && !session.hasMedia)) {
        return this.failure(ManagementErrorCode.PermissionDenied, 'management_session_control_unavailable')
      }
      await command()
      const result = new ManagementSessionActionResult()
      result.ok = true
      return result
    } catch (error) {
      return this.failure(this.classifyError(error as Object), 'management_session_action_failed')
    }
  }

  private failure(code: ManagementErrorCode, message: string): ManagementSessionActionResult {
    const result = new ManagementSessionActionResult()
    result.errorCode = code
    result.message = message
    return result
  }
}
