import { VirtualFolderInfo } from 'sdk/src/main/ets/generated-client/models'
import {
  ManagementLibraryDraft,
  ManagementLibraryItem,
  ManagementLibraryOptions
} from './ManagementLibraryModels'
import { ManagementLibraryPolicy } from './ManagementLibraryPolicy'

export class ManagementLibraryMapper {
  static cloneOptions(options: ManagementLibraryOptions): ManagementLibraryOptions {
    const json = ManagementLibraryMapper.serializeOptions(options)
    const result: ManagementLibraryOptions = JSON.parse(json)
    return result
  }

  static serializeOptions(options: ManagementLibraryOptions): string {
    return JSON.stringify(options)
  }

  static toItem(folder: VirtualFolderInfo): ManagementLibraryItem {
    const item = new ManagementLibraryItem()
    item.id = folder.ItemId ?? ''
    item.name = folder.Name ?? ''
    item.collectionType = folder.CollectionType ?? ''
    item.paths = ManagementLibraryPolicy.normalizePaths(folder.Locations ?? [])
    if (folder.LibraryOptions !== undefined) {
      const options: ManagementLibraryOptions = JSON.parse(JSON.stringify(folder.LibraryOptions))
      item.options = options
    }
    item.refreshProgress = folder.RefreshProgress ?? undefined
    item.refreshStatus = folder.RefreshStatus ?? ''
    return item
  }

  static toDraft(item: ManagementLibraryItem): ManagementLibraryDraft {
    const draft = new ManagementLibraryDraft()
    draft.id = item.id
    draft.originalName = item.name
    draft.name = item.name
    draft.collectionType = ManagementLibraryPolicy.collectionTypeFromValue(item.collectionType)
    draft.paths = ManagementLibraryPolicy.normalizePaths(item.paths)
    draft.options = ManagementLibraryMapper.cloneOptions(item.options)
    draft.originalOptionsJson = ManagementLibraryMapper.serializeOptions(draft.options)
    return draft
  }

  static cloneDraft(source: ManagementLibraryDraft): ManagementLibraryDraft {
    const draft = new ManagementLibraryDraft()
    draft.id = source.id
    draft.originalName = source.originalName
    draft.name = source.name
    draft.collectionType = source.collectionType
    draft.paths = source.paths.slice()
    draft.options = ManagementLibraryMapper.cloneOptions(source.options)
    draft.originalOptionsJson = source.originalOptionsJson
    return draft
  }
}
