import { BaseViewModel } from '../../../common/base/BaseViewModel';
import {
  ManagementErrorCode,
  ManagementPermissionState,
  ManagementSectionStatus
} from '../ManagementModels';
import {
  MANAGEMENT_SESSION_POLL_INTERVAL_MS,
  ManagementSessionAction,
  ManagementSessionActionResult,
  ManagementSessionItem
} from './ManagementSessionModels';
import { ManagementSessionRepository } from './ManagementSessionRepository';
import { ManagementSessionsUIState } from './ManagementSessionsUIState';

export class ManagementSessionsViewModel extends BaseViewModel<ManagementSessionsUIState> {
  private repository: ManagementSessionRepository = new ManagementSessionRepository()
  private generation: number = 0
  private timer: number = -1
  private loading: boolean = false
  private visible: boolean = false
  private detailSessionId: string = ''

  async initList(): Promise<void> {
    await this.start('')
  }

  async initDetail(sessionId: string): Promise<void> {
    await this.start(sessionId)
  }

  stop(): void {
    this.visible = false
    this.generation += 1
    if (this.timer >= 0) {
      clearInterval(this.timer)
    }
    this.timer = -1
    this.loading = false
  }

  async refresh(): Promise<void> {
    await this.refreshForGeneration(this.generation)
  }

  async playPause(): Promise<ManagementSessionActionResult> {
    return this.runAction(ManagementSessionAction.PlayPause,
      () => this.repository.playPause(this.detailSessionId))
  }

  async stopPlayback(): Promise<ManagementSessionActionResult> {
    return this.runAction(ManagementSessionAction.Stop,
      () => this.repository.stop(this.detailSessionId))
  }

  async sendMessage(): Promise<ManagementSessionActionResult> {
    const result = await this.runAction(ManagementSessionAction.Message,
      () => this.repository.message(this.detailSessionId, this.ui.messageDraft))
    if (result.ok) {
      this.ui.messageDraft = ''
    }
    return result
  }

  private async start(sessionId: string): Promise<void> {
    this.stop()
    this.visible = true
    this.detailSessionId = sessionId
    this.ui.permissionState = ManagementPermissionState.Checking
    this.ui.ended = false
    const generation = this.generation
    if (!this.repository.isApiClientInitialized()) {
      this.deny(ManagementErrorCode.RequestFailed, 'management_access_no_server')
      return
    }
    const accessCode = await this.repository.checkCurrentUserAccess()
    if (!this.isActive(generation)) {
      return
    }
    if (accessCode !== ManagementErrorCode.None) {
      this.deny(accessCode, accessCode === ManagementErrorCode.PermissionDenied ?
        'management_permission_denied' : 'management_permission_check_failed')
      return
    }
    this.ui.permissionState = ManagementPermissionState.Allowed
    await this.refreshForGeneration(generation)
    if (this.isActive(generation)) {
      this.timer = setInterval(() => this.refreshForGeneration(generation),
        MANAGEMENT_SESSION_POLL_INTERVAL_MS)
    }
  }

  private async refreshForGeneration(generation: number): Promise<void> {
    if (!this.isActive(generation) || this.loading) {
      return
    }
    this.loading = true
    this.ui.refreshing = true
    if (this.ui.sessions.length === 0 && !this.ui.detail) {
      this.ui.status = ManagementSectionStatus.Loading
    }
    try {
      if (this.detailSessionId) {
        const session = await this.repository.loadSession(this.detailSessionId)
        if (!this.isActive(generation)) {
          return
        }
        if (!session) {
          this.ui.ended = true
          this.ui.detail = null
          this.ui.status = ManagementSectionStatus.Empty
        } else {
          this.ui.detail = session
          this.ui.ended = false
          this.ui.status = ManagementSectionStatus.Content
        }
      } else {
        const sessions = await this.repository.loadRecentSessions()
        if (!this.isActive(generation)) {
          return
        }
        this.ui.sessions = sessions
        this.ui.status = sessions.length > 0 ? ManagementSectionStatus.Content :
          ManagementSectionStatus.Empty
      }
      this.ui.stale = false
      this.ui.errorCode = ManagementErrorCode.None
      this.ui.message = ''
    } catch (error) {
      if (!this.isActive(generation)) {
        return
      }
      const code = this.repository.classifyError(error as Object)
      if (code === ManagementErrorCode.PermissionDenied) {
        this.deny(code, 'management_permission_denied')
        return
      }
      this.ui.errorCode = code
      this.ui.message = code === ManagementErrorCode.Unsupported ?
        'management_section_unsupported' : 'management_sessions_failed'
      if (this.ui.sessions.length > 0 || this.ui.detail) {
        this.ui.stale = true
        this.ui.status = ManagementSectionStatus.Error
      } else {
        this.ui.status = code === ManagementErrorCode.Unsupported ?
          ManagementSectionStatus.Unsupported : ManagementSectionStatus.Error
      }
    } finally {
      if (generation === this.generation) {
        this.loading = false
        this.ui.refreshing = false
      }
    }
  }

  private async runAction(action: ManagementSessionAction,
    block: () => Promise<ManagementSessionActionResult>): Promise<ManagementSessionActionResult> {
    if (this.ui.pendingAction !== ManagementSessionAction.None || this.ui.ended) {
      const locked = new ManagementSessionActionResult()
      locked.errorCode = ManagementErrorCode.RequestFailed
      locked.message = 'management_session_action_in_progress'
      return locked
    }
    this.ui.pendingAction = action
    try {
      const result = await block()
      if (result.ok) {
        await this.refresh()
      }
      return result
    } finally {
      this.ui.pendingAction = ManagementSessionAction.None
    }
  }

  private deny(code: ManagementErrorCode, message: string): void {
    this.ui.permissionState = code === ManagementErrorCode.PermissionDenied ?
      ManagementPermissionState.Denied : ManagementPermissionState.Error
    this.ui.errorCode = code
    this.ui.message = message
    this.ui.status = ManagementSectionStatus.Error
    if (this.timer >= 0) {
      clearInterval(this.timer)
      this.timer = -1
    }
  }

  private isActive(generation: number): boolean {
    return this.visible && generation === this.generation
  }
}
