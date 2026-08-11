import { BaseUIState } from "../../common/base/BaseUIState"

@ObservedV2
export class VideoListUIState extends BaseUIState {
  @Trace title: string = '视频列表'
  @Trace totalCount: string = '0'
  @Trace isDesc: boolean = true
  @Trace selectedSortIndex = 2
  @Trace isAdministrator: boolean = false
  @Trace selectionMode: boolean = false
  @Trace selectedIds: string[] = []
  @Trace deletingSelection: boolean = false
  @Trace deleteDialogVisible: boolean = false

  enterSelection(id: string): void {
    const normalized = id.trim()
    if (normalized.length === 0) {
      return
    }
    if (!this.selectedIds.includes(normalized)) {
      this.selectedIds = this.selectedIds.concat(normalized)
    }
    this.selectionMode = this.selectedIds.length > 0
  }

  toggleSelection(id: string): void {
    const normalized = id.trim()
    if (normalized.length === 0) {
      return
    }
    if (this.selectedIds.includes(normalized)) {
      this.selectedIds = this.selectedIds.filter((selectedId: string) => selectedId !== normalized)
    } else {
      this.selectedIds = this.selectedIds.concat(normalized)
    }
    this.selectionMode = this.selectedIds.length > 0
  }

  isSelected(id: string): boolean {
    return this.selectedIds.includes(id)
  }

  clearSelection(): void {
    this.selectionMode = false
    this.selectedIds = []
    this.deletingSelection = false
    this.deleteDialogVisible = false
  }
}
