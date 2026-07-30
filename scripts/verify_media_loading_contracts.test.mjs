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
      '    this.ui.pageState = PageState.Loading',
      '    try {',
      '      this.ui.mediaList = await this.getMediaList()',
      '      this.ui.pageState = PageState.Content',
      '    } catch (error) {',
      '      this.ui.mediaList = []',
      '      this.ui.pageState = PageState.Error',
      '    } finally {',
      '      this.ui.isRefreshing = false',
      '    }',
      '  }',
      '  private async getMediaList(): Promise<MediaItem[]> {',
      '    const response = await api.getUserViews()',
      '    const views: BaseItemDto[] = (response.data?.Items || [])',
      '    return Promise.all(views.map(async (item: BaseItemDto): Promise<MediaItem> => item))',
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

test('rejects a Promise that resolves before child requests settle', () => {
  const sources = validSources()
  sources.set(mediaLoadingPaths.viewModel,
    sources.get(mediaLoadingPaths.viewModel).replace(
      'return Promise.all(views.map(async (item: BaseItemDto): Promise<MediaItem> => item))',
      'return new Promise((resolve) => { Promise.all(views.map(loadItem)); resolve() })'))
  assert.throws(
    () => validateMediaLoadingContracts(sources),
    /must not wrap SDK calls in a manual Promise/
  )
})

test('rejects an empty response path without a normalized return', () => {
  const sources = validSources()
  sources.set(mediaLoadingPaths.viewModel,
    sources.get(mediaLoadingPaths.viewModel).replace(
      'const views: BaseItemDto[] = (response.data?.Items || [])',
      'if (response.data?.Items) { return Promise.all(response.data.Items.map(loadItem)) }'))
  assert.throws(
    () => validateMediaLoadingContracts(sources),
    /normalize an empty response/
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
