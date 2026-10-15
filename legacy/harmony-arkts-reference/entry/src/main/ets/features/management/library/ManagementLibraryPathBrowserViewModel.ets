import { HMRouterMgr } from '@hadss/hmrouter'
import {
  FileSystemEntryInfo,
  FileSystemEntryType
} from 'sdk/src/main/ets/generated-client/models'
import { PageState } from '../../../common/base/BaseUIState'
import { BaseViewModel } from '../../../common/base/BaseViewModel'
import {
  ManagementLibraryApiService,
  ManagementLibraryService
} from './ManagementLibraryApiService'
import { ManagementLibraryPathEntry } from './ManagementLibraryModels'
import { ManagementLibraryPathBrowserUIState } from './ManagementLibraryPathBrowserUIState'

export class ManagementLibraryPathBrowserPolicy {
  static directories(entries: FileSystemEntryInfo[]): ManagementLibraryPathEntry[] {
    const directories = entries.filter((entry: FileSystemEntryInfo) =>
      ManagementLibraryPathBrowserPolicy.isDirectory(entry) && (entry.Path || '').trim().length > 0)
    directories.sort((left: FileSystemEntryInfo, right: FileSystemEntryInfo): number => {
      const leftName = (left.Name || left.Path || '').toLocaleLowerCase()
      const rightName = (right.Name || right.Path || '').toLocaleLowerCase()
      if (leftName < rightName) return -1
      if (leftName > rightName) return 1
      return (left.Path || '').localeCompare(right.Path || '')
    })
    return directories.map((entry: FileSystemEntryInfo): ManagementLibraryPathEntry => {
      const result = new ManagementLibraryPathEntry()
      result.name = (entry.Name || entry.Path || '').trim()
      result.path = (entry.Path || '').trim()
      return result
    })
  }

  static isManualPathVerified(manualPath: string, validatedPath: string): boolean {
    const normalized = manualPath.trim()
    return normalized.length > 0 && normalized === validatedPath
  }

  private static isDirectory(entry: FileSystemEntryInfo): boolean {
    return entry.Type === FileSystemEntryType.Directory ||
      entry.Type === FileSystemEntryType.NetworkComputer ||
      entry.Type === FileSystemEntryType.NetworkShare
  }
}

export class ManagementLibraryPathBrowserViewModel extends BaseViewModel<ManagementLibraryPathBrowserUIState> {
  private service: ManagementLibraryService
  private requestToken: number = 0
  private visible: boolean = false

  constructor(ui: ManagementLibraryPathBrowserUIState,
    service: ManagementLibraryService = new ManagementLibraryApiService()) {
    super(ui)
    this.service = service
  }

  async init(): Promise<void> {
    if (this.visible) return
    this.visible = true
    await this.loadRoot()
  }

  stop(): void {
    this.visible = false
    this.requestToken += 1
    this.ui.isLoading = false
    this.ui.isValidating = false
  }

  async loadRoot(): Promise<void> {
    const token = ++this.requestToken
    this.beginLoad()
    try {
      const entries = await this.service.getDrives()
      if (!this.isActive(token)) return
      this.ui.currentPath = ''
      this.applyEntries(entries)
    } catch (error) {
      this.applyLoadError(token, error as Error)
    }
  }

  async open(entry: ManagementLibraryPathEntry): Promise<void> {
    const path = entry.path.trim()
    if (path.length === 0) return
    await this.loadDirectory(path)
  }

  async goParent(): Promise<void> {
    if (this.ui.currentPath.length === 0) {
      await this.loadRoot()
      return
    }
    const token = ++this.requestToken
    this.beginLoad()
    try {
      const parentPath = (await this.service.getParentPath(this.ui.currentPath)).trim()
      if (!this.isActive(token)) return
      if (parentPath.length === 0) {
        const entries = await this.service.getDrives()
        if (!this.isActive(token)) return
        this.ui.currentPath = ''
        this.applyEntries(entries)
      } else {
        const entries = await this.service.getDirectoryContents(parentPath)
        if (!this.isActive(token)) return
        this.ui.currentPath = parentPath
        this.applyEntries(entries)
      }
    } catch (error) {
      this.applyLoadError(token, error as Error)
    }
  }

  setManualPath(value: string): void {
    this.ui.manualPath = value
    if (value.trim() !== this.ui.validatedManualPath) {
      this.ui.validatedManualPath = ''
      this.ui.validationMessage = ''
    }
  }

  async validateManualPath(): Promise<boolean> {
    const path = this.ui.manualPath.trim()
    this.ui.validatedManualPath = ''
    this.ui.validationMessage = ''
    if (path.length === 0 || this.ui.isValidating) return false
    this.ui.isValidating = true
    try {
      await this.service.validatePath(path, true)
      this.ui.validatedManualPath = path
      this.ui.validationMessage = 'management_library_path_validation_success'
      return true
    } catch (error) {
      this.ui.validationMessage = this.errorText(error as Error,
        'management_library_path_validation_failed')
      return false
    } finally {
      this.ui.isValidating = false
    }
  }

  canReturnManualPath(): boolean {
    return ManagementLibraryPathBrowserPolicy.isManualPathVerified(
      this.ui.manualPath, this.ui.validatedManualPath)
  }

  returnManualPath(): boolean {
    if (!this.canReturnManualPath()) return false
    this.complete(this.ui.validatedManualPath)
    return true
  }

  async validateAndReturn(path: string): Promise<boolean> {
    const normalized = path.trim()
    if (normalized.length === 0 || this.ui.isValidating) return false
    this.ui.isValidating = true
    this.ui.validationMessage = ''
    try {
      await this.service.validatePath(normalized, true)
      this.complete(normalized)
      return true
    } catch (error) {
      this.ui.validationMessage = this.errorText(error as Error,
        'management_library_path_validation_failed')
      return false
    } finally {
      this.ui.isValidating = false
    }
  }

  private async loadDirectory(path: string): Promise<void> {
    const token = ++this.requestToken
    this.beginLoad()
    try {
      const entries = await this.service.getDirectoryContents(path)
      if (!this.isActive(token)) return
      this.ui.currentPath = path
      this.applyEntries(entries)
    } catch (error) {
      this.applyLoadError(token, error as Error)
    }
  }

  private beginLoad(): void {
    this.ui.isLoading = true
    this.ui.errorMessage = ''
    if (this.ui.entries.length === 0) {
      this.ui.pageState = PageState.Loading
    }
  }

  private applyEntries(entries: FileSystemEntryInfo[]): void {
    this.ui.entries = ManagementLibraryPathBrowserPolicy.directories(entries)
    this.ui.isLoading = false
    this.ui.errorMessage = ''
    this.ui.pageState = PageState.Content
  }

  private applyLoadError(token: number, error: Error): void {
    if (!this.isActive(token)) return
    this.ui.isLoading = false
    this.ui.errorMessage = this.errorText(error, 'management_library_path_load_failed')
    if (this.ui.entries.length === 0) {
      this.ui.pageState = PageState.Error
    }
  }

  private errorText(error: Error, fallback: string): string {
    return error.message && error.message.trim().length > 0 ? error.message.trim() : fallback
  }

  private complete(path: string): void {
    HMRouterMgr.pop({ param: { path: path } })
  }

  private isActive(token: number): boolean {
    return this.visible && token === this.requestToken
  }
}
