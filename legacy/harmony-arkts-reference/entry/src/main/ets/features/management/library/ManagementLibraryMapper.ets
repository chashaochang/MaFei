import {
  ImageOption,
  LibraryOptionInfoDto,
  VirtualFolderInfo
} from 'sdk/src/main/ets/generated-client/models'
import {
  ManagementLibraryAvailableOptions,
  ManagementLibraryDraft,
  ManagementLibraryItem,
  ManagementLibraryOptions,
  ManagementLibraryTypeOptions
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

  static defaultTypeOptions(available: ManagementLibraryAvailableOptions): ManagementLibraryTypeOptions[] {
    return (available.TypeOptions || []).map((source): ManagementLibraryTypeOptions => {
      const result: ManagementLibraryTypeOptions = {}
      result.Type = source.Type || ''
      result.MetadataFetchers = ManagementLibraryMapper.enabledProviderNames(source.MetadataFetchers)
      result.MetadataFetcherOrder = ManagementLibraryMapper.providerNames(source.MetadataFetchers)
      result.ImageFetchers = ManagementLibraryMapper.enabledProviderNames(source.ImageFetchers)
      result.ImageFetcherOrder = ManagementLibraryMapper.providerNames(source.ImageFetchers)
      result.SimilarItemProviders = ManagementLibraryMapper.enabledProviderNames(source.SimilarItemProviders)
      result.SimilarItemProviderOrder = ManagementLibraryMapper.providerNames(source.SimilarItemProviders)
      result.ImageOptions = ManagementLibraryMapper.cloneImageOptions(source.DefaultImageOptions || [])
      return result
    })
  }

  static providerNames(options: LibraryOptionInfoDto[] | undefined): string[] {
    return (options || []).map((item: LibraryOptionInfoDto) => item.Name || '')
      .filter((name: string) => name.length > 0)
  }

  static enabledProviderNames(options: LibraryOptionInfoDto[] | undefined): string[] {
    return (options || []).filter((item: LibraryOptionInfoDto) => item.DefaultEnabled === true)
      .map((item: LibraryOptionInfoDto) => item.Name || '')
      .filter((name: string) => name.length > 0)
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

  private static cloneImageOptions(options: ImageOption[]): ImageOption[] {
    return options.map((source: ImageOption): ImageOption => {
      return {
        Type: source.Type,
        Limit: source.Limit,
        MinWidth: source.MinWidth
      }
    })
  }
}
