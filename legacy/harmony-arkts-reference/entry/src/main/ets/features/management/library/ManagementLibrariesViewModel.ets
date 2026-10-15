import { HMRouterMgr } from '@hadss/hmrouter'
import { PageState } from '../../../common/base/BaseUIState'
import { BaseViewModel } from '../../../common/base/BaseViewModel'
import { RouterConsts } from '../../../common/RouterConsts'
import {
  ManagementLibraryItem,
  ManagementLibraryOperationCode,
  ManagementLibraryOperationResult
} from './ManagementLibraryModels'
import {
  ManagementLibraryLoadResult,
  ManagementLibraryLoadStatus,
  ManagementLibraryRepository
} from './ManagementLibraryRepository'
import { ManagementLibrariesUIState } from './ManagementLibrariesUIState'

export class ManagementLibrariesViewModel extends BaseViewModel<ManagementLibrariesUIState> {
  private repository: ManagementLibraryRepository = new ManagementLibraryRepository()
  private loadToken: number = 0
  private visible: boolean = false

  init(): void {
    if (this.visible) {
      return
    }
    this.visible = true
    this.load(false)
  }

  stop(): void {
    this.visible = false
    this.loadToken += 1
    this.ui.isRefreshing = false
    this.ui.scanningAll = false
    this.ui.busyLibraryId = ''
    this.ui.removeDialogVisible = false
    this.ui.removeCandidate = undefined
  }

  async load(preserve: boolean = true): Promise<void> {
    if (!this.visible) {
      return
    }
    const token = ++this.loadToken
    const retainContent = preserve && this.ui.items.length > 0
    this.ui.isRefreshing = retainContent
    if (!retainContent) {
      this.ui.pageState = PageState.Loading
    }
    const result = await this.repository.loadLibraries()
    if (!this.visible || token !== this.loadToken) {
      return
    }
    this.applyLoadResult(result, retainContent)
    this.ui.isRefreshing = false
  }

  selectLibrary(item: ManagementLibraryItem): void {
    this.ui.selectedLibraryId = item.id
  }

  selectedLibrary(): ManagementLibraryItem | undefined {
    return this.ui.items.find((item: ManagementLibraryItem) => item.id === this.ui.selectedLibraryId)
  }

  async scanAll(): Promise<ManagementLibraryOperationResult> {
    if (this.ui.scanningAll || this.ui.busyLibraryId.length > 0) {
      return ManagementLibraryOperationResult.failure(
        ManagementLibraryOperationCode.Conflict, 'management_library_operation_in_progress')
    }
    this.ui.scanningAll = true
    try {
      return await this.repository.startAllScan()
    } finally {
      this.ui.scanningAll = false
    }
  }

  async scanOne(item: ManagementLibraryItem): Promise<ManagementLibraryOperationResult> {
    if (this.ui.scanningAll || this.ui.busyLibraryId.length > 0) {
      return ManagementLibraryOperationResult.failure(
        ManagementLibraryOperationCode.Conflict, 'management_library_operation_in_progress')
    }
    this.ui.busyLibraryId = item.id
    try {
      return await this.repository.startLibraryScan(item.id)
    } finally {
      this.ui.busyLibraryId = ''
    }
  }

  confirmRemove(item: ManagementLibraryItem): void {
    if (this.ui.busyLibraryId.length > 0 || this.ui.scanningAll) {
      return
    }
    this.ui.removeCandidate = item
    this.ui.removeDialogVisible = true
  }

  cancelRemove(): void {
    this.ui.removeDialogVisible = false
    this.ui.removeCandidate = undefined
  }

  async removeConfirmed(): Promise<ManagementLibraryOperationResult> {
    const candidate = this.ui.removeCandidate
    if (!candidate || this.ui.busyLibraryId.length > 0 || this.ui.scanningAll) {
      return ManagementLibraryOperationResult.failure(
        ManagementLibraryOperationCode.Conflict, 'management_library_operation_in_progress')
    }
    this.ui.busyLibraryId = candidate.id
    try {
      const result = await this.repository.removeLibrary(candidate.name)
      if (result.ok) {
        this.ui.removeDialogVisible = false
        this.ui.removeCandidate = undefined
        await this.load(true)
      }
      return result
    } finally {
      this.ui.busyLibraryId = ''
    }
  }

  openCreate(): void {
    HMRouterMgr.to(RouterConsts.ManagementLibraryEditorPage)
      .withParam({ libraryId: '' })
      .push()
  }

  openEdit(item: ManagementLibraryItem): void {
    HMRouterMgr.to(RouterConsts.ManagementLibraryEditorPage)
      .withParam({ libraryId: item.id })
      .push()
  }

  private applyLoadResult(result: ManagementLibraryLoadResult, retainContent: boolean): void {
    this.ui.errorCode = result.errorCode
    this.ui.errorMessage = result.message
    if (result.status === ManagementLibraryLoadStatus.Content ||
      result.status === ManagementLibraryLoadStatus.Empty) {
      this.ui.items = result.items
      this.ui.stale = false
      this.ui.errorCode = ManagementLibraryOperationCode.None
      this.ui.errorMessage = ''
      this.ui.pageState = PageState.Content
      this.preserveSelection()
      return
    }
    if (retainContent) {
      this.ui.stale = true
      this.ui.pageState = PageState.Content
      return
    }
    this.ui.items = []
    this.ui.stale = false
    this.ui.pageState = PageState.Error
    if (this.ui.errorMessage.length === 0) {
      this.ui.errorMessage = result.status === ManagementLibraryLoadStatus.Unsupported ?
        'management_library_unsupported' : 'management_library_load_failed'
    }
  }

  private preserveSelection(): void {
    if (this.ui.items.length === 0) {
      this.ui.selectedLibraryId = ''
      return
    }
    const exists = this.ui.items.some((item: ManagementLibraryItem) =>
      item.id === this.ui.selectedLibraryId)
    if (!exists) {
      this.ui.selectedLibraryId = this.ui.items[0].id
    }
  }
}
