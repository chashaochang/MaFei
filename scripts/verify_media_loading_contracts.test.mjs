import assert from 'node:assert/strict'
import test from 'node:test'
import {
  loadMediaLoadingSources,
  mediaLoadingPaths,
  validateMediaLoadingContracts
} from './verify_media_loading_contracts.mjs'

function validSources() {
  return new Map([
    [mediaLoadingPaths.viewModel, [
      'class MediaViewModel {',
      '  async init(): Promise<void> {',
      '    const generation = ++this.requestGeneration',
      '    this.ui.pageState = PageState.Loading',
      '    try {',
      '      const catalog = currentMediaCatalogProvider()',
      '      const mediaList = await this.getMediaList(catalog)',
      '      if (!this.isCurrent(catalog, generation)) {',
      '        return',
      '      }',
      '      this.ui.mediaList = mediaList',
      '      if (catalog.session.capabilities.management) {',
      '        await this.libraryRepository.requireAdministrator()',
      '      }',
      '      this.ui.pageState = PageState.Content',
      '    } catch (error) {',
      '      this.ui.mediaList = []',
      '      this.ui.pageState = PageState.Error',
      '    } finally {',
      '      this.ui.isRefreshing = false',
      '    }',
      '  }',
      '  private async getMediaList(catalog: MediaCatalogProvider): Promise<MediaItem[]> {',
      '    const libraries = await catalog.getLibraries()',
      '    return libraries.map((item: MediaLibrary): MediaItem => item)',
      '  }',
      '}'
    ].join('\n')],
    [mediaLoadingPaths.uiState, [
      'import { BaseUIState } from "../../../common/base/BaseUIState"',
      'export class MediaUIState extends BaseUIState {}'
    ].join('\n')],
    [mediaLoadingPaths.tab, [
      'class MediaTab {',
      '  ui = new MediaUIState(this.getUIContext())',
      '  build() {',
      '    if (this.ui.pageState === PageState.Content) {',
      '      Refresh() {}',
      '    }',
      '    if (this.ui.pageState === PageState.Loading) {',
      '      LoadingProgress()',
      '    }',
      '    if (this.ui.pageState === PageState.Error) {',
      '      FailedView()',
      '        .onClick(() => {',
      '          this.vm.init()',
      '        })',
      '    }',
      '  }',
      '}'
    ].join('\n')]
  ])
}

test('accepts the complete media loading contract', () => {
  assert.doesNotThrow(() => validateMediaLoadingContracts(validSources()))
})

test('accepts the current workspace implementation', () => {
  assert.doesNotThrow(() =>
    validateMediaLoadingContracts(loadMediaLoadingSources()))
})

test('rejects wrapping Catalog loading in an early-settling Promise', () => {
  const sources = validSources()
  sources.set(mediaLoadingPaths.viewModel,
    sources.get(mediaLoadingPaths.viewModel).replace(
      'return libraries.map((item: MediaLibrary): MediaItem => item)',
      'return new Promise((resolve) => { catalog.getLibraries(); resolve([]) })'))
  assert.throws(
    () => validateMediaLoadingContracts(sources),
    /must not wrap Catalog calls in a manual Promise/
  )
})

test('rejects bypassing the neutral Catalog library contract', () => {
  const sources = validSources()
  sources.set(mediaLoadingPaths.viewModel,
    sources.get(mediaLoadingPaths.viewModel).replace(
      'const libraries = await catalog.getLibraries()',
      'const libraries = await legacyApi.getUserViews()'))
  assert.throws(
    () => validateMediaLoadingContracts(sources),
    /neutral Catalog/
  )
})

test('rejects publishing a media list before the active request guard', () => {
  const sources = validSources()
  sources.set(mediaLoadingPaths.viewModel,
    sources.get(mediaLoadingPaths.viewModel).replace(
      '      if (!this.isCurrent(catalog, generation)) {\n        return\n      }\n' +
        '      this.ui.mediaList = mediaList',
      '      this.ui.mediaList = mediaList'))
  assert.throws(
    () => validateMediaLoadingContracts(sources),
    /await the complete media list/
  )
})

test('rejects init without finally cleanup', () => {
  const sources = validSources()
  sources.set(mediaLoadingPaths.viewModel,
    sources.get(mediaLoadingPaths.viewModel).replace(
      '    } finally {\n      this.ui.isRefreshing = false\n    }',
      '    }'))
  assert.throws(
    () => validateMediaLoadingContracts(sources),
    /clear refresh state in finally/
  )
})

test('rejects an error view without retry', () => {
  const sources = validSources()
  sources.set(mediaLoadingPaths.tab,
    sources.get(mediaLoadingPaths.tab).replace(
      '          this.vm.init()',
      '          this.dismissError()'))
  assert.throws(
    () => validateMediaLoadingContracts(sources),
    /FailedView with retry/
  )
})
