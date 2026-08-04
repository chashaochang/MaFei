import { describe, expect, it } from '@ohos/hypium'
import {
  CollectionTypeOptions,
  CountryInfo,
  CultureDto,
  EmbeddedSubtitleOptions,
  FileSystemEntryInfo,
  FileSystemEntryType,
  ImageType,
  LibraryOptionInfoDto,
  MediaPathInfo,
  TaskInfo,
  TaskState,
  UserDto,
  VirtualFolderInfo
} from 'sdk/src/main/ets/generated-client/models'
import {
  ManagementLibraryApiService
} from '../main/ets/features/management/library/ManagementLibraryApiService'
import {
  ManagementLibraryAvailableOptions,
  ManagementLibraryDraft,
  ManagementLibraryOperationCode,
  ManagementLibraryOptions,
  ManagementLibraryValidationCode,
  ManagementMediaDeleteTarget
} from '../main/ets/features/management/library/ManagementLibraryModels'
import { ManagementLibraryChangePlanner } from
  '../main/ets/features/management/library/ManagementLibraryChangePlanner'
import { ManagementLibraryMapper } from
  '../main/ets/features/management/library/ManagementLibraryMapper'
import { ManagementLibraryPolicy } from
  '../main/ets/features/management/library/ManagementLibraryPolicy'
import { ManagementMediaDeletePolicy } from
  '../main/ets/features/management/library/ManagementMediaDeletePolicy'
import {
  ManagementLibraryLoadStatus,
  ManagementLibraryRepository
} from '../main/ets/features/management/library/ManagementLibraryRepository'
import { ManagementLibraryPathBrowserUIState } from
  '../main/ets/features/management/library/ManagementLibraryPathBrowserUIState'
import {
  ManagementLibraryPathBrowserPolicy,
  ManagementLibraryPathBrowserViewModel
} from '../main/ets/features/management/library/ManagementLibraryPathBrowserViewModel'
import { ManagementLibraryEditorUIState } from
  '../main/ets/features/management/library/ManagementLibraryEditorUIState'
import { ManagementLibraryEditorViewModel } from
  '../main/ets/features/management/library/ManagementLibraryEditorViewModel'

class FakeLibraryHttpResponse {
  status: number = 0
  data: string = ''
}

class FakeLibraryHttpError extends Error {
  response?: FakeLibraryHttpResponse

  constructor() {
    super('fake library HTTP error')
  }
}

class FakeManagementLibraryService extends ManagementLibraryApiService {
  calls: string[] = []
  virtualFolders: VirtualFolderInfo[] = []
  tasks: TaskInfo[] = []
  administrator: boolean = true
  failOn: string = ''
  failStatus: number = 0
  drives: FileSystemEntryInfo[] = []
  directoryEntries: FileSystemEntryInfo[] = []
  parentPath: string = ''
  validatedPath: string = ''
  validatedWritable: boolean = false
  availableOptions: ManagementLibraryAvailableOptions = {}
  countries: CountryInfo[] = []
  cultures: CultureDto[] = []
  requestedType: string = ''
  requestedIsNew: boolean = false

  private record(name: string): void {
    this.calls.push(name)
    if (this.failOn !== name) return
    if (this.failStatus > 0) {
      const response = new FakeLibraryHttpResponse()
      response.status = this.failStatus
      response.data = 'server failure ' + this.failStatus
      const error = new FakeLibraryHttpError()
      error.response = response
      throw error
    }
    throw Error(`forced ${name}`)
  }

  async getCurrentUser(): Promise<UserDto> {
    this.record('getCurrentUser')
    return {
      Policy: {
        IsAdministrator: this.administrator,
        AuthenticationProviderId: '',
        PasswordResetProviderId: ''
      }
    }
  }

  async getVirtualFolders(): Promise<VirtualFolderInfo[]> {
    this.record('getVirtualFolders')
    return this.virtualFolders
  }

  async getAvailableOptions(type: string, isNew: boolean): Promise<ManagementLibraryAvailableOptions> {
    this.record('getAvailableOptions')
    this.requestedType = type
    this.requestedIsNew = isNew
    return this.availableOptions
  }

  async getCountries(): Promise<CountryInfo[]> {
    this.record('getCountries')
    return this.countries
  }

  async getCultures(): Promise<CultureDto[]> {
    this.record('getCultures')
    return this.cultures
  }

  async renameVirtualFolder(_name: string, _newName: string): Promise<void> {
    this.record('renameVirtualFolder')
  }

  async removeMediaPath(_name: string, _path: string): Promise<void> {
    this.record('removeMediaPath')
  }

  async addMediaPath(_name: string, _path: string): Promise<void> {
    this.record('addMediaPath')
  }

  async updateMediaPath(_name: string, _pathInfo: MediaPathInfo): Promise<void> {
    this.record('updateMediaPath')
  }

  async updateLibraryOptions(_id: string, _options: ManagementLibraryOptions): Promise<void> {
    this.record('updateLibraryOptions')
  }

  async addVirtualFolder(_name: string, _type: string | undefined, _paths: string[],
    _options: ManagementLibraryOptions): Promise<void> {
    this.record('addVirtualFolder')
  }

  async removeVirtualFolder(name: string): Promise<void> {
    this.record('removeVirtualFolder')
    this.virtualFolders = this.virtualFolders.filter((item: VirtualFolderInfo) => item.Name !== name)
  }

  async validatePath(path: string, writable: boolean): Promise<void> {
    this.record('validatePath')
    this.validatedPath = path
    this.validatedWritable = writable
  }

  async getDrives(): Promise<FileSystemEntryInfo[]> {
    this.record('getDrives')
    return this.drives
  }

  async getDirectoryContents(_path: string): Promise<FileSystemEntryInfo[]> {
    this.record('getDirectoryContents')
    return this.directoryEntries
  }

  async getParentPath(_path: string): Promise<string> {
    this.record('getParentPath')
    return this.parentPath
  }

  async refreshItem(_itemId: string): Promise<void> {
    this.record('refreshItem')
  }

  async getScheduledTasks(): Promise<TaskInfo[]> {
    this.record('getScheduledTasks')
    return this.tasks
  }

  async startScheduledTask(_taskId: string): Promise<void> {
    this.record('startScheduledTask')
  }
}

function libraryDraft(name: string, paths: string[]): ManagementLibraryDraft {
  const draft = new ManagementLibraryDraft()
  draft.name = name
  draft.originalName = name
  draft.paths = paths
  draft.options = { Enabled: true }
  draft.originalOptionsJson = ManagementLibraryMapper.serializeOptions(draft.options)
  return draft
}

function fullLibraryOptionsFixture(): ManagementLibraryOptions {
  return {
    Enabled: true,
    EnableArchiveMediaFiles: false,
    EnablePhotos: true,
    EnableRealtimeMonitor: true,
    EnableLUFSScan: true,
    EnableChapterImageExtraction: true,
    ExtractChapterImagesDuringLibraryScan: false,
    EnableTrickplayImageExtraction: true,
    ExtractTrickplayImagesDuringLibraryScan: false,
    PathInfos: [{ Path: '/media' }],
    SaveLocalMetadata: true,
    EnableInternetProviders: true,
    EnableAutomaticSeriesGrouping: true,
    EnableEmbeddedTitles: true,
    EnableEmbeddedExtrasTitles: true,
    EnableEmbeddedEpisodeInfos: true,
    AutomaticRefreshIntervalDays: 30,
    PreferredMetadataLanguage: 'zh',
    MetadataCountryCode: 'CN',
    SeasonZeroDisplayName: 'Specials',
    MetadataSavers: ['Nfo'],
    DisabledLocalMetadataReaders: ['DisabledReader'],
    LocalMetadataReaderOrder: ['Nfo'],
    DisabledSubtitleFetchers: ['DisabledSub'],
    SubtitleFetcherOrder: ['SubA'],
    DisabledMediaSegmentProviders: ['DisabledSegment'],
    MediaSegmentProvideOrder: ['LegacySegmentProvider'],
    MediaSegmentProviderOrder: ['SegmentProvider'],
    SkipSubtitlesIfEmbeddedSubtitlesPresent: true,
    SkipSubtitlesIfAudioTrackMatches: true,
    SubtitleDownloadLanguages: ['zho'],
    RequirePerfectSubtitleMatch: true,
    SaveSubtitlesWithMedia: true,
    SaveLyricsWithMedia: true,
    SaveTrickplayWithMedia: true,
    DisabledLyricFetchers: ['DisabledLyric'],
    LyricFetcherOrder: ['LyricProvider'],
    PreferNonstandardArtistsTag: true,
    UseCustomTagDelimiters: true,
    CustomTagDelimiters: ['/', '|'],
    DelimiterWhitelist: ['AC/DC'],
    AutomaticallyAddToCollection: true,
    AllowEmbeddedSubtitles: EmbeddedSubtitleOptions.AllowText,
    TypeOptions: [{
      Type: 'Movie',
      MetadataFetchers: ['ProviderA'],
      MetadataFetcherOrder: ['ProviderB', 'ProviderA'],
      ImageFetchers: ['ImageA'],
      ImageFetcherOrder: ['ImageA'],
      SimilarItemProviders: ['SimilarProvider'],
      SimilarItemProviderOrder: ['SimilarProvider'],
      ImageOptions: [{ Type: ImageType.Primary, Limit: 1, MinWidth: 600 }]
    }]
  }
}

function providerOption(name: string, enabled: boolean): LibraryOptionInfoDto {
  return { Name: name, DefaultEnabled: enabled }
}

function deleteTarget(id: string, canDelete: boolean, path: string): ManagementMediaDeleteTarget {
  const target = new ManagementMediaDeleteTarget()
  target.id = id
  target.name = id
  target.canDelete = canDelete
  target.path = path
  return target
}

function folder(id: string, name: string, paths: string[]): VirtualFolderInfo {
  return { ItemId: id, Name: name, Locations: paths, LibraryOptions: { Enabled: true } }
}

function originalDraft(): ManagementLibraryDraft {
  const draft = libraryDraft('Movies', ['/old', '/keep'])
  draft.id = 'library-id'
  draft.originalName = 'Movies'
  return draft
}

function editedDraft(): ManagementLibraryDraft {
  const draft = libraryDraft('Films', ['/keep', '/new'])
  draft.id = 'library-id'
  draft.originalName = 'Movies'
  draft.options.Enabled = false
  return draft
}

function refreshTask(state: TaskState): TaskInfo {
  return { Id: 'refresh-task', Key: 'RefreshLibrary', State: state }
}

export default function managementLibrariesTest() {
  describe('ManagementLibraries', () => {
    it('rejectsBlankNameAndMissingPaths', 0, () => {
      const draft = libraryDraft('   ', [])
      expect(ManagementLibraryPolicy.validateDraft(draft))
        .assertEqual(ManagementLibraryValidationCode.NameRequired)
      draft.name = 'Movies'
      expect(ManagementLibraryPolicy.validateDraft(draft))
        .assertEqual(ManagementLibraryValidationCode.PathRequired)
    })

    it('deduplicatesTrimmedPathsWithoutChangingCase', 0, () => {
      const paths = ManagementLibraryPolicy.normalizePaths([
        ' /media/Movies ', '/media/Movies', '/MEDIA/Movies'
      ])
      expect(paths.length).assertEqual(2)
      expect(paths[0]).assertEqual('/media/Movies')
      expect(paths[1]).assertEqual('/MEDIA/Movies')
    })

    it('rejectsExactDuplicatePathsAfterTrimming', 0, () => {
      const draft = libraryDraft('Movies', ['/media/Movies', ' /media/Movies '])
      expect(ManagementLibraryPolicy.validateDraft(draft))
        .assertEqual(ManagementLibraryValidationCode.DuplicatePath)
    })

    it('omitsMixedTypeOnlyWhenCreatingLibrary', 0, () => {
      expect(ManagementLibraryPolicy.collectionTypeForAvailableOptions(CollectionTypeOptions.Mixed))
        .assertEqual('mixed')
      expect(ManagementLibraryPolicy.collectionTypeForCreate(CollectionTypeOptions.Mixed) === undefined)
        .assertTrue()
      expect(ManagementLibraryPolicy.collectionTypeForCreate(CollectionTypeOptions.Movies))
        .assertEqual('movies')
    })

    it('plansRenamePathsAndOptionsDeterministically', 0, () => {
      const original = libraryDraft('Movies', ['/old', '/keep'])
      original.id = 'library-id'
      original.originalName = 'Movies'
      original.originalOptionsJson = '{"Enabled":true}'
      const edited = libraryDraft('Films', ['/keep', '/new'])
      edited.id = 'library-id'
      edited.originalName = 'Movies'
      edited.originalOptionsJson = original.originalOptionsJson
      edited.options.Enabled = false
      const plan = ManagementLibraryChangePlanner.plan(original, edited)
      expect(plan.newName).assertEqual('Films')
      expect(plan.removedPaths[0]).assertEqual('/old')
      expect(plan.addedPaths[0]).assertEqual('/new')
      expect(plan.optionsChanged).assertTrue()
    })

    it('requiresAdministratorItemCapabilityAndIdForDelete', 0, () => {
      expect(ManagementMediaDeletePolicy.canDelete(true, true, 'item')).assertTrue()
      expect(ManagementMediaDeletePolicy.canDelete(false, true, 'item')).assertFalse()
      expect(ManagementMediaDeletePolicy.canDelete(true, false, 'item')).assertFalse()
      expect(ManagementMediaDeletePolicy.canDelete(true, true, '')).assertFalse()
    })

    it('separatesDeletableAndBlockedBatchItems', 0, () => {
      const summary = ManagementMediaDeletePolicy.summarize([
        deleteTarget('allowed', true, '/media/a.mkv'),
        deleteTarget('blocked', false, '/media/b.mkv')
      ], true)
      expect(summary.allowedIds.length).assertEqual(1)
      expect(summary.allowedIds[0]).assertEqual('allowed')
      expect(summary.allowedPaths[0]).assertEqual('/media/a.mkv')
      expect(summary.blockedNames.length).assertEqual(1)
      expect(summary.blockedNames[0]).assertEqual('blocked')
    })

    it('preservesCompatibilityFieldsWhenCloningOptions', 0, () => {
      const draft = libraryDraft('Movies', ['/media'])
      draft.options.EnableArchiveMediaFiles = true
      draft.options.MediaSegmentProviderOrder = ['Chapter Segments']
      draft.options.TypeOptions = [{
        Type: 'Movie',
        SimilarItemProviders: ['MovieDb'],
        SimilarItemProviderOrder: ['MovieDb']
      }]

      const clone = ManagementLibraryMapper.cloneDraft(draft)
      expect(clone.options.EnableArchiveMediaFiles).assertTrue()
      expect(clone.options.MediaSegmentProviderOrder?.[0]).assertEqual('Chapter Segments')
      expect(clone.options.TypeOptions?.[0].SimilarItemProviders?.[0]).assertEqual('MovieDb')
      expect(clone.options.TypeOptions?.[0].SimilarItemProviderOrder?.[0]).assertEqual('MovieDb')
      clone.options.MediaSegmentProviderOrder?.push('Other')
      expect(draft.options.MediaSegmentProviderOrder?.length).assertEqual(1)
    })

    it('clonesAllLibraryOptionFamiliesWithoutDroppingCompatibilityFields', 0, () => {
      const source = fullLibraryOptionsFixture()
      const result = ManagementLibraryMapper.cloneOptions(source)
      expect(ManagementLibraryMapper.serializeOptions(result))
        .assertEqual(ManagementLibraryMapper.serializeOptions(source))
    })

    it('preservesServerProviderOrderAndDisabledEntries', 0, () => {
      const options = ManagementLibraryMapper.cloneOptions(fullLibraryOptionsFixture())
      expect(options.TypeOptions?.[0].MetadataFetcherOrder?.join(','))
        .assertEqual('ProviderB,ProviderA')
      expect(options.DisabledSubtitleFetchers?.[0]).assertEqual('DisabledSub')
      expect(options.MediaSegmentProviderOrder?.[0]).assertEqual('SegmentProvider')
      expect(options.TypeOptions?.[0].SimilarItemProviderOrder?.[0]).assertEqual('SimilarProvider')
    })

    it('mapsDynamicProviderDefaultsWithoutDroppingDisabledOrderEntries', 0, () => {
      const available: ManagementLibraryAvailableOptions = {
        TypeOptions: [{
          Type: 'Movie',
          MetadataFetchers: [providerOption('Disabled', false), providerOption('Enabled', true)],
          ImageFetchers: [providerOption('ImageB', false), providerOption('ImageA', true)],
          SimilarItemProviders: [providerOption('SimilarB', false), providerOption('SimilarA', true)],
          DefaultImageOptions: [{ Type: ImageType.Primary, Limit: 1, MinWidth: 600 }]
        }]
      }
      const options = ManagementLibraryMapper.defaultTypeOptions(available)
      expect(options[0].MetadataFetchers?.join(',')).assertEqual('Enabled')
      expect(options[0].MetadataFetcherOrder?.join(',')).assertEqual('Disabled,Enabled')
      expect(options[0].ImageFetcherOrder?.join(',')).assertEqual('ImageB,ImageA')
      expect(options[0].SimilarItemProviderOrder?.join(',')).assertEqual('SimilarB,SimilarA')
      expect(options[0].ImageOptions?.[0].MinWidth).assertEqual(600)
    })

    it('createsAfterSequentialValidationAndReload', 0, async () => {
      const fake = new FakeManagementLibraryService()
      fake.virtualFolders = [folder('new-id', 'Movies', ['/one', '/two'])]
      const result = await new ManagementLibraryRepository(fake)
        .createLibrary(libraryDraft('Movies', ['/one', '/two']))
      expect(result.ok).assertTrue()
      expect(fake.calls.join(','))
        .assertEqual('getCurrentUser,validatePath,validatePath,addVirtualFolder,getVirtualFolders')
    })

    it('editsInRenamePathOptionReloadOrder', 0, async () => {
      const fake = new FakeManagementLibraryService()
      fake.virtualFolders = [folder('library-id', 'Films', ['/keep', '/new'])]
      const result = await new ManagementLibraryRepository(fake).saveEdit(originalDraft(), editedDraft())
      expect(result.ok).assertTrue()
      expect(fake.calls.join(','))
        .assertEqual('getCurrentUser,getVirtualFolders,renameVirtualFolder,removeMediaPath,validatePath,' +
          'addMediaPath,updateLibraryOptions,getVirtualFolders')
    })

    it('reloadsFinalStateAfterPartialWriteFailure', 0, async () => {
      const fake = new FakeManagementLibraryService()
      fake.virtualFolders = [folder('library-id', 'Movies', ['/old', '/keep'])]
      fake.failOn = 'addMediaPath'
      const result = await new ManagementLibraryRepository(fake).saveEdit(originalDraft(), editedDraft())
      expect(result.code).assertEqual(ManagementLibraryOperationCode.PartialSuccess)
      expect(fake.calls.join(','))
        .assertEqual('getCurrentUser,getVirtualFolders,renameVirtualFolder,removeMediaPath,validatePath,' +
          'addMediaPath,getVirtualFolders')
    })

    it('rejectsAdministratorWritesBeforeMutation', 0, async () => {
      const fake = new FakeManagementLibraryService()
      fake.administrator = false
      const result = await new ManagementLibraryRepository(fake)
        .createLibrary(libraryDraft('Movies', ['/media']))
      expect(result.code).assertEqual(ManagementLibraryOperationCode.PermissionDenied)
      expect(fake.calls.join(',')).assertEqual('getCurrentUser')
    })

    it('rejectsGlobalScanUnlessRefreshTaskIsIdle', 0, async () => {
      const fake = new FakeManagementLibraryService()
      fake.tasks = [refreshTask(TaskState.Running)]
      const result = await new ManagementLibraryRepository(fake).startAllScan()
      expect(result.ok).assertFalse()
      expect(fake.calls.includes('startScheduledTask')).assertFalse()
    })

    it('startsGlobalAndSingleScansAfterFreshAdministratorChecks', 0, async () => {
      const globalFake = new FakeManagementLibraryService()
      globalFake.tasks = [refreshTask(TaskState.Idle)]
      expect((await new ManagementLibraryRepository(globalFake).startAllScan()).ok).assertTrue()
      expect(globalFake.calls.join(','))
        .assertEqual('getCurrentUser,getScheduledTasks,startScheduledTask')

      const itemFake = new FakeManagementLibraryService()
      expect((await new ManagementLibraryRepository(itemFake).startLibraryScan('library-id')).ok)
        .assertTrue()
      expect(itemFake.calls.join(',')).assertEqual('getCurrentUser,refreshItem')
    })

    it('removesOnlyVirtualLibraryConfigurationAndReloads', 0, async () => {
      const fake = new FakeManagementLibraryService()
      fake.virtualFolders = [folder('library-id', 'Movies', ['/media'])]
      const result = await new ManagementLibraryRepository(fake).removeLibrary('Movies')
      expect(result.ok).assertTrue()
      expect(fake.calls.join(','))
        .assertEqual('getCurrentUser,removeVirtualFolder,getVirtualFolders')
    })

    it('mapsLoadPermissionErrorsAndPreservesServerMessage', 0, async () => {
      const fake = new FakeManagementLibraryService()
      fake.failOn = 'getVirtualFolders'
      fake.failStatus = 403
      const result = await new ManagementLibraryRepository(fake).loadLibraries()
      expect(result.status).assertEqual(ManagementLibraryLoadStatus.Error)
      expect(result.errorCode).assertEqual(ManagementLibraryOperationCode.PermissionDenied)
      expect(result.message).assertEqual('server failure 403')
    })

    it('filtersFilesAndSortsBrowsableDirectories', 0, () => {
      const entries = ManagementLibraryPathBrowserPolicy.directories([
        { Name: 'zeta', Path: '/zeta', Type: FileSystemEntryType.Directory },
        { Name: 'movie.mkv', Path: '/movie.mkv', Type: FileSystemEntryType.File },
        { Name: 'Alpha', Path: '/alpha', Type: FileSystemEntryType.Directory },
        { Name: 'share', Path: '/share', Type: FileSystemEntryType.NetworkShare }
      ])
      expect(entries.length).assertEqual(3)
      expect(entries[0].path).assertEqual('/alpha')
      expect(entries[1].path).assertEqual('/share')
      expect(entries[2].path).assertEqual('/zeta')
    })

    it('doesNotReturnAnUnverifiedManualPath', 0, () => {
      expect(ManagementLibraryPathBrowserPolicy.isManualPathVerified('/media', '')).assertFalse()
      expect(ManagementLibraryPathBrowserPolicy.isManualPathVerified('/media', '/other')).assertFalse()
      expect(ManagementLibraryPathBrowserPolicy.isManualPathVerified(' /media ', '/media')).assertTrue()
    })

    it('usesServerParentPathAndWritableValidation', 0, async () => {
      const fake = new FakeManagementLibraryService()
      fake.parentPath = '/media'
      fake.directoryEntries = [
        { Name: 'Movies', Path: '/media/Movies', Type: FileSystemEntryType.Directory }
      ]
      const ui = new ManagementLibraryPathBrowserUIState({} as UIContext)
      const vm = new ManagementLibraryPathBrowserViewModel(ui, fake)
      await vm.init()
      fake.calls = []
      ui.currentPath = '/media/Movies'
      await vm.goParent()
      expect(fake.calls.join(',')).assertEqual('getParentPath,getDirectoryContents')
      expect(ui.currentPath).assertEqual('/media')
      vm.setManualPath('/media/Movies')
      expect(await vm.validateManualPath()).assertTrue()
      expect(fake.validatedPath).assertEqual('/media/Movies')
      expect(fake.validatedWritable).assertTrue()
      expect(vm.canReturnManualPath()).assertTrue()
      vm.stop()
    })

    it('loadsMixedEditorBootstrapWithoutOverwritingCustomName', 0, async () => {
      const fake = new FakeManagementLibraryService()
      fake.availableOptions = {
        MetadataSavers: [providerOption('Nfo', true), providerOption('DisabledSaver', false)],
        MetadataReaders: [providerOption('ReaderA', true), providerOption('ReaderB', false)],
        SubtitleFetchers: [providerOption('SubA', true), providerOption('SubB', false)],
        LyricFetchers: [providerOption('LyricsA', true), providerOption('LyricsB', false)],
        MediaSegmentProviders: [providerOption('SegmentA', true), providerOption('SegmentB', false)]
      }
      const ui = new ManagementLibraryEditorUIState({} as UIContext)
      const vm = new ManagementLibraryEditorViewModel(ui, new ManagementLibraryRepository(fake))
      await vm.loadNew(CollectionTypeOptions.Mixed)
      expect(fake.requestedType).assertEqual('mixed')
      expect(fake.requestedIsNew).assertTrue()
      expect(ui.draft.name).assertEqual('')
      expect(ui.draft.options.MetadataSavers?.join(',')).assertEqual('Nfo')
      expect(ui.draft.options.DisabledSubtitleFetchers?.join(',')).assertEqual('SubB')
      vm.setName('Custom Library')
      await vm.setCollectionType(CollectionTypeOptions.Movies)
      expect(ui.draft.name).assertEqual('Custom Library')
      expect(ui.dirty).assertTrue()
      vm.stop()
    })

    it('validatesEveryPathThroughRepositoryAndKeepsDirtyOnFailure', 0, async () => {
      const fake = new FakeManagementLibraryService()
      const ui = new ManagementLibraryEditorUIState({} as UIContext)
      const vm = new ManagementLibraryEditorViewModel(ui, new ManagementLibraryRepository(fake))
      await vm.loadNew(CollectionTypeOptions.Mixed)
      vm.setName('Movies')
      vm.setPaths(['/one', '/two'])
      fake.failOn = 'validatePath'
      const result = await vm.save()
      expect(result.ok).assertFalse()
      expect(ui.pathErrors.has('/one')).assertTrue()
      expect(ui.dirty).assertTrue()
      expect(ui.saving).assertFalse()
      expect(vm.beforeBack()).assertTrue()
      expect(ui.discardConfirmVisible).assertTrue()
      expect(vm.beforeBack()).assertTrue()
      vm.stop()
    })
  })
}
