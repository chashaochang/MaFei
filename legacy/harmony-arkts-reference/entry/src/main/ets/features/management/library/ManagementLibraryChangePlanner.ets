import { ManagementLibraryChangeSet, ManagementLibraryDraft } from './ManagementLibraryModels'
import { ManagementLibraryMapper } from './ManagementLibraryMapper'
import { ManagementLibraryPolicy } from './ManagementLibraryPolicy'

export class ManagementLibraryChangePlanner {
  static plan(original: ManagementLibraryDraft, edited: ManagementLibraryDraft): ManagementLibraryChangeSet {
    const result = new ManagementLibraryChangeSet()
    const originalPaths = ManagementLibraryPolicy.normalizePaths(original.paths)
    const editedPaths = ManagementLibraryPolicy.normalizePaths(edited.paths)
    const editedName = edited.name.trim()
    result.newName = editedName !== original.originalName ? editedName : ''
    result.addedPaths = editedPaths.filter((path: string) => !originalPaths.includes(path))
    result.removedPaths = originalPaths.filter((path: string) => !editedPaths.includes(path))
    result.optionsChanged = ManagementLibraryMapper.serializeOptions(edited.options) !==
      original.originalOptionsJson
    return result
  }
}
