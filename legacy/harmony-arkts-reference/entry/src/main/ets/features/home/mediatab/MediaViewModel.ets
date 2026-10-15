import { PageState } from "../../../common/base/BaseUIState"
import { currentMediaCatalogProvider } from "../../../media/MediaCatalogAccess"
import {
  MediaLibrary,
  MediaLibraryKind
} from "../../../media/catalog/MediaCatalogModels"
import { MediaCatalogProvider } from "../../../media/catalog/MediaCatalogProvider"
import { MediaRefKind } from "../../../media/MediaRef"
import { MediaProviderRegistry } from "../../../media/MediaProviderRegistry"
import { MediaItem } from "../hometab/HomeUIState"
import {
  ManagementLibraryOperationCode,
  ManagementLibraryOperationResult
} from "../../management/library/ManagementLibraryModels"
import { ManagementLibraryRepository } from "../../management/library/ManagementLibraryRepository"
import { MediaUIState } from "./MediaUIState"

export class MediaViewModel {
  ui: MediaUIState
  private libraryRepository: ManagementLibraryRepository = new ManagementLibraryRepository()
  private catalog?: MediaCatalogProvider
  private requestGeneration: number = 0

  constructor(ui: MediaUIState) {
    this.ui = ui
  }

  async init(): Promise<void> {
    const generation = ++this.requestGeneration
    this.ui.pageState = PageState.Loading
    let catalog: MediaCatalogProvider
    try {
      catalog = currentMediaCatalogProvider()
      this.catalog = catalog
      const mediaList = await this.getMediaList(catalog)
      if (!this.isCurrent(catalog, generation)) {
        return
      }
      this.ui.mediaList = mediaList
      if (catalog.session.capabilities.management) {
        const administratorResult = await this.libraryRepository.requireAdministrator()
        if (!this.isCurrent(catalog, generation)) {
          return
        }
        this.ui.isAdministrator = administratorResult.ok
      } else {
        this.ui.isAdministrator = false
      }
      this.ui.pageState = PageState.Content
    } catch (_error) {
      if (generation !== this.requestGeneration) {
        return
      }
      console.error('[MediaVM] init failed')
      this.catalog = undefined
      this.ui.mediaList = []
      this.ui.isAdministrator = false
      this.ui.pageState = PageState.Error
    } finally {
      if (generation === this.requestGeneration) {
        this.ui.isRefreshing = false
      }
    }
  }

  private isCurrent(catalog: MediaCatalogProvider, generation: number): boolean {
    const activeSession = MediaProviderRegistry.instance().getActiveSession()
    return this.catalog === catalog && generation === this.requestGeneration &&
      (!activeSession || activeSession.accountScope === catalog.session.accountScope)
  }

  async scanAll(): Promise<ManagementLibraryOperationResult> {
    if (!this.catalog?.session.capabilities.management) {
      return ManagementLibraryOperationResult.failure(
        ManagementLibraryOperationCode.Unsupported, 'management_library_unsupported')
    }
    if (this.ui.isScanBusy) {
      return ManagementLibraryOperationResult.failure(
        ManagementLibraryOperationCode.Conflict, 'management_library_operation_in_progress')
    }
    this.ui.isScanBusy = true
    try {
      return await this.libraryRepository.startAllScan()
    } finally {
      this.ui.isScanBusy = false
    }
  }

  private async getMediaList(catalog: MediaCatalogProvider): Promise<MediaItem[]> {
    const libraries = (await catalog.getLibraries()).filter((library: MediaLibrary): boolean => {
      if (library.kind === MediaLibraryKind.LiveTv) {
        return false
      }
      if (library.kind === MediaLibraryKind.Music) {
        return catalog.session.capabilities.music
      }
      if (library.kind === MediaLibraryKind.Photos) {
        return catalog.session.capabilities.photos
      }
      return true
    })
    return libraries.map((library: MediaLibrary): MediaItem => {
      return {
        name: library.title,
        image: library.primaryImage?.url || '',
        mediaRef: library.ref,
        imageRef: library.primaryImage,
        id: library.ref.id,
        childCount: library.itemCount,
        kind: libraryContentKind(library.kind),
        libraryKind: library.kind
      }
    })
  }
}

function libraryContentKind(kind: MediaLibraryKind): MediaRefKind | undefined {
  if (kind === MediaLibraryKind.Movies) {
    return MediaRefKind.Movie
  }
  if (kind === MediaLibraryKind.Series) {
    return MediaRefKind.Series
  }
  return undefined
}
