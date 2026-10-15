import {
  CollectionTypeOptions,
  EmbeddedSubtitleOptions,
  LibraryOptionInfoDto
} from 'sdk/src/main/ets/generated-client/models'
import { PageState } from '../../../common/base/BaseUIState'
import { BaseViewModel } from '../../../common/base/BaseViewModel'
import { ManagementLibraryEditorUIState } from './ManagementLibraryEditorUIState'
import { ManagementLibraryMapper } from './ManagementLibraryMapper'
import {
  ManagementLibraryAvailableOptions,
  ManagementLibraryDraft,
  ManagementLibraryItem,
  ManagementLibraryOperationCode,
  ManagementLibraryOperationResult,
  ManagementLibraryOptions,
  ManagementLibraryValidationCode
} from './ManagementLibraryModels'
import { ManagementLibraryPolicy } from './ManagementLibraryPolicy'
import { ManagementLibraryRepository } from './ManagementLibraryRepository'

export class ManagementLibraryEditorViewModel extends BaseViewModel<ManagementLibraryEditorUIState> {
  private repository: ManagementLibraryRepository
  private generation: number = 0
  private typeLoadToken: number = 0
  private visible: boolean = false
  private baseline: string = ''
  private autoName: string = ''

  constructor(ui: ManagementLibraryEditorUIState,
    repository: ManagementLibraryRepository = new ManagementLibraryRepository()) {
    super(ui)
    this.repository = repository
    this.ui.createTypes = [
      CollectionTypeOptions.Movies,
      CollectionTypeOptions.Tvshows,
      CollectionTypeOptions.Music,
      CollectionTypeOptions.Musicvideos,
      CollectionTypeOptions.Homevideos,
      CollectionTypeOptions.Boxsets,
      CollectionTypeOptions.Books,
      CollectionTypeOptions.Mixed
    ]
  }

  async loadNew(type: CollectionTypeOptions = CollectionTypeOptions.Mixed): Promise<void> {
    this.visible = true
    const generation = ++this.generation
    this.typeLoadToken += 1
    this.ui.pageState = PageState.Loading
    this.ui.saveMessage = ''
    try {
      const available = await this.repository.loadAvailableOptions(
        ManagementLibraryPolicy.collectionTypeForAvailableOptions(type), true)
      const countries = await this.repository.loadCountries()
      const cultures = await this.repository.loadCultures()
      if (!this.isActive(generation)) return
      const draft = new ManagementLibraryDraft()
      draft.collectionType = type
      draft.name = type === CollectionTypeOptions.Mixed ? '' : this.typeDisplayName(type)
      draft.originalName = draft.name
      draft.options = this.defaultOptions(available)
      draft.originalOptionsJson = ManagementLibraryMapper.serializeOptions(draft.options)
      this.autoName = draft.name
      this.ui.isNew = true
      this.ui.original = undefined
      this.ui.draft = draft
      this.ui.availableOptions = available
      this.ui.countries = countries.slice()
      this.ui.cultures = cultures.slice()
      this.resetLoadedState()
    } catch (error) {
      if (this.isActive(generation)) {
        this.failLoad(error as Error)
      }
    }
  }

  async loadEdit(itemId: string): Promise<void> {
    this.visible = true
    const generation = ++this.generation
    this.typeLoadToken += 1
    this.ui.pageState = PageState.Loading
    this.ui.saveMessage = ''
    try {
      const item = await this.repository.loadLibraryById(itemId)
      if (!item) {
        throw Error('management_library_not_found')
      }
      const draft = ManagementLibraryMapper.toDraft(item)
      const available = await this.repository.loadAvailableOptions(
        ManagementLibraryPolicy.collectionTypeForAvailableOptions(draft.collectionType), false)
      const countries = await this.repository.loadCountries()
      const cultures = await this.repository.loadCultures()
      if (!this.isActive(generation)) return
      this.autoName = ''
      this.ui.isNew = false
      this.ui.original = ManagementLibraryMapper.cloneDraft(draft)
      this.ui.draft = ManagementLibraryMapper.cloneDraft(draft)
      this.ui.availableOptions = available
      this.ui.countries = countries.slice()
      this.ui.cultures = cultures.slice()
      this.resetLoadedState()
    } catch (error) {
      if (this.isActive(generation)) {
        this.failLoad(error as Error)
      }
    }
  }

  stop(): void {
    this.visible = false
    this.generation += 1
    this.typeLoadToken += 1
    this.ui.saving = false
  }

  resume(): void {
    this.visible = true
  }

  setName(value: string): void {
    const draft = ManagementLibraryMapper.cloneDraft(this.ui.draft)
    draft.name = value
    this.ui.draft = draft
    this.recomputeDirty()
  }

  async setCollectionType(type: CollectionTypeOptions): Promise<void> {
    if (!this.ui.isNew || type === this.ui.draft.collectionType) return
    const token = ++this.typeLoadToken
    const replaceAutoName = this.ui.draft.name.trim().length === 0 || this.ui.draft.name === this.autoName
    const previousDraft = ManagementLibraryMapper.cloneDraft(this.ui.draft)
    try {
      const available = await this.repository.loadAvailableOptions(
        ManagementLibraryPolicy.collectionTypeForAvailableOptions(type), true)
      if (!this.visible || token !== this.typeLoadToken) return
      const draft = ManagementLibraryMapper.cloneDraft(previousDraft)
      draft.collectionType = type
      draft.options = this.defaultOptions(available)
      if (replaceAutoName) {
        draft.name = type === CollectionTypeOptions.Mixed ? '' : this.typeDisplayName(type)
      }
      this.autoName = type === CollectionTypeOptions.Mixed ? '' : this.typeDisplayName(type)
      this.ui.availableOptions = available
      this.ui.draft = draft
      this.recomputeDirty()
    } catch (error) {
      if (this.visible && token === this.typeLoadToken) {
        this.ui.saveMessage = this.errorMessage(error as Error, 'management_library_options_load_failed')
      }
    }
  }

  setPaths(paths: string[]): void {
    const draft = ManagementLibraryMapper.cloneDraft(this.ui.draft)
    draft.paths = ManagementLibraryPolicy.normalizePaths(paths)
    this.ui.draft = draft
    this.ui.pathErrors = new Map<string, string>()
    this.recomputeDirty()
  }

  setOptions(options: ManagementLibraryOptions): void {
    const draft = ManagementLibraryMapper.cloneDraft(this.ui.draft)
    draft.options = ManagementLibraryMapper.cloneOptions(options)
    this.ui.draft = draft
    this.recomputeDirty()
  }

  moveProvider(order: string[], from: number, to: number): string[] {
    if (from < 0 || to < 0 || from >= order.length || to >= order.length || from === to) {
      return order.slice()
    }
    const next = order.slice()
    const removed = next.splice(from, 1)
    if (removed.length > 0) {
      next.splice(to, 0, removed[0])
    }
    return next
  }

  setAdvancedExpanded(expanded: boolean): void {
    this.ui.advancedExpanded = expanded
  }

  async save(): Promise<ManagementLibraryOperationResult> {
    if (this.ui.saving) {
      return ManagementLibraryOperationResult.failure(
        ManagementLibraryOperationCode.Conflict, 'management_library_operation_in_progress')
    }
    const draft = ManagementLibraryMapper.cloneDraft(this.ui.draft)
    const validation = ManagementLibraryPolicy.validateDraft(draft)
    this.ui.validationCode = validation
    this.ui.pathErrors = new Map<string, string>()
    if (validation !== ManagementLibraryValidationCode.None) {
      this.ui.saveCode = ManagementLibraryOperationCode.Failed
      this.ui.saveMessage = validation
      return ManagementLibraryOperationResult.failure(ManagementLibraryOperationCode.Failed, validation)
    }
    this.ui.saving = true
    this.ui.saveCode = ManagementLibraryOperationCode.None
    this.ui.saveMessage = ''
    try {
      for (const path of draft.paths) {
        const pathResult = await this.repository.validateWritablePath(path)
        if (!pathResult.ok) {
          const errors = new Map<string, string>(this.ui.pathErrors)
          errors.set(path, pathResult.message || pathResult.code)
          this.ui.pathErrors = errors
          this.ui.saveCode = pathResult.code
          this.ui.saveMessage = pathResult.message || pathResult.code
          return pathResult
        }
      }
      const result = this.ui.isNew ? await this.repository.createLibrary(draft) :
        await this.repository.saveEdit(this.ui.original!, draft)
      this.ui.saveCode = result.code
      this.ui.saveMessage = result.message
      if (result.ok) {
        this.applySavedResult(result, draft)
      }
      return result
    } finally {
      this.ui.saving = false
    }
  }

  beforeBack(): boolean {
    if (!this.ui.dirty) return false
    if (!this.ui.discardConfirmVisible) {
      this.ui.discardConfirmVisible = true
    }
    return true
  }

  cancelDiscard(): void {
    this.ui.discardConfirmVisible = false
  }

  confirmDiscard(): void {
    this.ui.discardConfirmVisible = false
    this.ui.dirty = false
  }

  private resetLoadedState(): void {
    this.ui.validationCode = ManagementLibraryValidationCode.None
    this.ui.pathErrors = new Map<string, string>()
    this.ui.dirty = false
    this.ui.saving = false
    this.ui.saveCode = ManagementLibraryOperationCode.None
    this.ui.saveMessage = ''
    this.ui.discardConfirmVisible = false
    this.baseline = this.snapshot(this.ui.draft)
    this.ui.pageState = PageState.Content
  }

  private defaultOptions(available: ManagementLibraryAvailableOptions): ManagementLibraryOptions {
    const metadataSavers = ManagementLibraryMapper.enabledProviderNames(available.MetadataSavers)
    const metadataReaders = ManagementLibraryMapper.providerNames(available.MetadataReaders)
    const subtitleFetchers = ManagementLibraryMapper.providerNames(available.SubtitleFetchers)
    const lyricFetchers = ManagementLibraryMapper.providerNames(available.LyricFetchers)
    const segmentProviders = ManagementLibraryMapper.providerNames(available.MediaSegmentProviders)
    return {
      Enabled: true,
      EnableArchiveMediaFiles: false,
      EnablePhotos: true,
      EnableRealtimeMonitor: true,
      EnableLUFSScan: true,
      EnableChapterImageExtraction: false,
      ExtractChapterImagesDuringLibraryScan: false,
      EnableTrickplayImageExtraction: false,
      ExtractTrickplayImagesDuringLibraryScan: false,
      PathInfos: [],
      SaveLocalMetadata: false,
      EnableInternetProviders: true,
      EnableAutomaticSeriesGrouping: false,
      EnableEmbeddedTitles: false,
      EnableEmbeddedExtrasTitles: false,
      EnableEmbeddedEpisodeInfos: false,
      AutomaticRefreshIntervalDays: 0,
      PreferredMetadataLanguage: null,
      MetadataCountryCode: null,
      SeasonZeroDisplayName: 'Specials',
      MetadataSavers: metadataSavers,
      DisabledLocalMetadataReaders: this.disabledProviderNames(available.MetadataReaders),
      LocalMetadataReaderOrder: metadataReaders,
      DisabledSubtitleFetchers: this.disabledProviderNames(available.SubtitleFetchers),
      SubtitleFetcherOrder: subtitleFetchers,
      DisabledMediaSegmentProviders: this.disabledProviderNames(available.MediaSegmentProviders),
      MediaSegmentProvideOrder: segmentProviders,
      MediaSegmentProviderOrder: segmentProviders.slice(),
      SkipSubtitlesIfEmbeddedSubtitlesPresent: false,
      SkipSubtitlesIfAudioTrackMatches: false,
      SubtitleDownloadLanguages: [],
      RequirePerfectSubtitleMatch: true,
      SaveSubtitlesWithMedia: true,
      SaveLyricsWithMedia: false,
      SaveTrickplayWithMedia: false,
      DisabledLyricFetchers: this.disabledProviderNames(available.LyricFetchers),
      LyricFetcherOrder: lyricFetchers,
      PreferNonstandardArtistsTag: false,
      UseCustomTagDelimiters: false,
      CustomTagDelimiters: ['/', '|', ';', '\\'],
      DelimiterWhitelist: [],
      AutomaticallyAddToCollection: false,
      AllowEmbeddedSubtitles: EmbeddedSubtitleOptions.AllowAll,
      TypeOptions: ManagementLibraryMapper.defaultTypeOptions(available)
    }
  }

  private disabledProviderNames(options: LibraryOptionInfoDto[] | undefined): string[] {
    return (options || []).filter((item: LibraryOptionInfoDto) => item.DefaultEnabled !== true)
      .map((item: LibraryOptionInfoDto) => item.Name || '')
      .filter((name: string) => name.length > 0)
  }

  private applySavedResult(result: ManagementLibraryOperationResult, submitted: ManagementLibraryDraft): void {
    const saved = result.library ? ManagementLibraryMapper.toDraft(result.library) :
      ManagementLibraryMapper.cloneDraft(submitted)
    saved.originalName = saved.name
    saved.originalOptionsJson = ManagementLibraryMapper.serializeOptions(saved.options)
    this.ui.isNew = false
    this.ui.original = ManagementLibraryMapper.cloneDraft(saved)
    this.ui.draft = ManagementLibraryMapper.cloneDraft(saved)
    this.ui.validationCode = ManagementLibraryValidationCode.None
    this.ui.pathErrors = new Map<string, string>()
    this.ui.dirty = false
    this.baseline = this.snapshot(saved)
  }

  private recomputeDirty(): void {
    this.ui.dirty = this.snapshot(this.ui.draft) !== this.baseline
  }

  private snapshot(draft: ManagementLibraryDraft): string {
    return `${draft.id}\n${draft.originalName}\n${draft.name}\n${draft.collectionType}\n${
      JSON.stringify(draft.paths)}\n${ManagementLibraryMapper.serializeOptions(draft.options)}`
  }

  private typeDisplayName(type: CollectionTypeOptions): string {
    const resourceName = type === CollectionTypeOptions.Movies ? 'management_movies' :
      (type === CollectionTypeOptions.Tvshows ? 'management_series' :
        (type === CollectionTypeOptions.Music ? 'management_library_type_music' :
          (type === CollectionTypeOptions.Musicvideos ? 'management_library_type_musicvideos' :
            (type === CollectionTypeOptions.Homevideos ? 'management_library_type_homevideos' :
              (type === CollectionTypeOptions.Boxsets ? 'management_library_type_boxsets' :
                (type === CollectionTypeOptions.Books ? 'management_library_type_books' :
                  'management_library_type_mixed'))))))
    if (resourceName.length > 0) {
      try {
        return this.ui.context.getHostContext()?.resourceManager.getStringByNameSync(resourceName) || type
      } catch (error) {
      }
    }
    return type.length > 0 ? `${type.slice(0, 1).toLocaleUpperCase()}${type.slice(1)}` : type
  }

  private failLoad(error: Error): void {
    this.ui.pageState = PageState.Error
    this.ui.saveCode = ManagementLibraryOperationCode.Failed
    this.ui.saveMessage = this.errorMessage(error, 'management_library_load_failed')
  }

  private errorMessage(error: Error, fallback: string): string {
    return error.message && error.message.trim().length > 0 ? error.message.trim() : fallback
  }

  private isActive(generation: number): boolean {
    return this.visible && generation === this.generation
  }
}
