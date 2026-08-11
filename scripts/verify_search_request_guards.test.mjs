import assert from 'node:assert/strict'
import test from 'node:test'
import {
  searchRequestGuardPaths,
  validateSearchRequestGuards,
  validateWorkspace
} from './verify_search_request_guards.mjs'

function validSources() {
  return new Map([
    [searchRequestGuardPaths.viewModel, `
      class SearchViewModel {
        private requestGeneration: number = 0

        search(value: string, saveHistory: boolean = false): void {
          const query = value.trim()
          const generation = ++this.requestGeneration
          if (query === '') {
            this.clearResults()
            this.ui.pageState = PageState.Content
            return
          }
          this.clearResults()
          this.ui.pageState = PageState.Loading
          getItemsApi(ApiClient.Instance()).getItems({ searchTerm: query })
            .then(() => {
              if (generation !== this.requestGeneration) { return }
              this.ui.pageState = PageState.Content
              if (saveHistory) {
                this.saveHistory(query)
              }
            })
            .catch(() => {
              if (generation !== this.requestGeneration) { return }
              this.ui.pageState = PageState.Error
            })
        }

        cancelPendingSearch(): void {
          this.requestGeneration += 1
        }

        retrySearch(): void {
          this.search(this.ui.searchText)
        }

        private clearResults(): void {
          this.dataSource.deleteAll()
          this.ui.totalCount = ''
          this.ui.errorMessage = ''
        }
      }
    `],
    [searchRequestGuardPaths.uiState, `
      class SearchUIState {
        @Trace searchText: string = ''
        @Trace errorMessage: string = ''
      }
    `],
    [searchRequestGuardPaths.page, `
      import { PageState } from "../../common/base/BaseUIState"
      import { FailedView } from "../../component/FailedView"

      struct SearchPage {
        aboutToDisappear(): void {
          this.vm.cancelPendingSearch()
        }

        private searchResultsContent() {
          if (this.ui.pageState === PageState.Loading) {
            LoadingProgress()
          } else if (this.ui.pageState === PageState.Error) {
            FailedView().onClick(() => {
              this.vm.retrySearch()
            })
          }
        }

        private searchBarContent() {
          Search()
            .onChange((value: string) => {
              this.vm.search(value)
            })
            .onSubmit((value: string) => {
              this.vm.search(value, true)
            })
        }

        private pageContent(showLegacyActionBar: boolean) {
          Column()
            .padding({
              left: AppThemeTokens.pageHorizontalPadding(
                this.vm.appUIState.themeStyle, this.vm.appUIState.isBigScreen),
              right: AppThemeTokens.pageHorizontalPadding(
                this.vm.appUIState.themeStyle, this.vm.appUIState.isBigScreen)
            })
        }
      }
    `]
  ])
}

test('accepts the complete latest-request contract', () => {
  assert.doesNotThrow(() => validateSearchRequestGuards(validSources()))
})

test('rejects a missing stale-response guard', () => {
  const sources = validSources()
  const viewModel = sources.get(searchRequestGuardPaths.viewModel)
  sources.set(searchRequestGuardPaths.viewModel,
    viewModel.replace('if (generation !== this.requestGeneration) { return }', ''))

  assert.throws(
    () => validateSearchRequestGuards(sources),
    /success and failure must reject stale generations/
  )
})

test('rejects an empty query that reaches the server', () => {
  const sources = validSources()
  const viewModel = sources.get(searchRequestGuardPaths.viewModel)
  sources.set(searchRequestGuardPaths.viewModel,
    viewModel.replace("if (query === '')", "if (query === 'never')"))

  assert.throws(
    () => validateSearchRequestGuards(sources),
    /empty query must clear and return before getItems/
  )
})

test('rejects page exit without request invalidation', () => {
  const sources = validSources()
  const page = sources.get(searchRequestGuardPaths.page)
  sources.set(searchRequestGuardPaths.page,
    page.replace('this.vm.cancelPendingSearch()', ''))

  assert.throws(
    () => validateSearchRequestGuards(sources),
    /page exit must invalidate pending search/
  )
})

test('rejects saving an unconfirmed query on page exit', () => {
  const sources = validSources()
  const page = sources.get(searchRequestGuardPaths.page)
  sources.set(searchRequestGuardPaths.page,
    page.replace('this.vm.cancelPendingSearch()', 'this.vm.saveHistory(this.ui.searchText)'))

  assert.throws(
    () => validateSearchRequestGuards(sources),
    /page exit must invalidate pending search|must not save an unconfirmed query/
  )
})

test('rejects removal of the Native page padding contract', () => {
  const sources = validSources()
  const page = sources.get(searchRequestGuardPaths.page)
  sources.set(searchRequestGuardPaths.page,
    page.replace('left: AppThemeTokens.pageHorizontalPadding(', 'left: ignoredPadding('))

  assert.throws(
    () => validateSearchRequestGuards(sources),
    /preserve Native left padding ownership/
  )
})

test('current workspace satisfies search request ownership', () => {
  assert.doesNotThrow(() => validateWorkspace())
})
