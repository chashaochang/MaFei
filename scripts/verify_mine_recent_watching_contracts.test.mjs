import assert from 'node:assert/strict'
import test from 'node:test'
import {
  colorPaths,
  mineViewModelPath,
  mineTabPath,
  pagePath,
  posterPath,
  repositoryPath,
  resourcePaths,
  routerPath,
  sectionPath,
  validateMineRecentWatching,
  viewModelPath
} from './verify_mine_recent_watching_contracts.mjs'

function resourceSource() {
  return JSON.stringify({
    string: [
      'mine_history_title',
      'mine_history_more',
      'mine_history_empty',
      'mine_history_loading_more',
      'mine_history_load_more_failed',
      'mine_history_unnamed',
      'mine_history_episode_title_single',
      'mine_history_episode_title_season',
      'mine_history_updated_to_episode',
      'mine_history_updated_to_season_episode'
    ].map((name) => ({ name, value: name }))
  })
}

function colorSource() {
  return JSON.stringify({
    color: [
      { name: 'media_badge_background', value: '#99000000' },
      { name: 'media_progress_track_on_image', value: '#55FFFFFF' }
    ]
  })
}

function validSources() {
  const sources = new Map([
    [posterPath, [
      'export struct MineHistoryPoster {}',
      'imageKnifeOption: { loadSrc: this.item.image }',
      "constraintSize({ maxWidth: 'calc(100% - 16vp)' })",
      'private titleText(): ResourceStr',
      'mine_history_unnamed',
      'mine_history_episode_title_single',
      'mine_history_episode_title_season',
      'mine_history_updated_to_episode',
      'mine_history_updated_to_season_episode',
      'this.item.name, this.item.episodeNumber',
      'private hasBadge(): boolean',
      'if (this.hasBadge())',
      '.fontSize(9)',
      '.maxLines(2)',
      "app.color.media_badge_background",
      "app.color.media_progress_track_on_image",
      "app.color.bg_2"
    ].join('\n')],
    [sectionPath, [
      '@Require @Param onMore: () => void',
      'private loadingGrid(nativeSurface: boolean)',
      'item.latestEpisodeNumber || 0',
      '(item: MineHistoryItem) => this.historyItemKey(item)',
      'MineHistoryPoster({ item })'
    ].join('\n')],
    [mineTabPath, [
      'if (this.ui.isAdministrator) { ManagementDashboardPage }',
      'MineHistorySection({',
      'HMRouterMgr.to(RouterConsts.MineHistoryPage).push()'
    ].join('\n')],
    [pagePath, [
      '@HMRouter({ pageUrl: RouterConsts.MineHistoryPage })',
      'AppRouteDestination({',
      'scrollControllers: [this.contentScroller]',
      'item.latestEpisodeNumber || 0',
      '(item: MineHistoryItem) => this.historyItemKey(item)',
      'MineHistoryPoster({',
      '.onReachEnd(() => this.vm.loadMore())',
      'MineHistoryPageState.Loading',
      'MineHistoryPageState.Empty',
      'MineHistoryPageState.Error',
      'this.loadingGrid(nativeSurface)',
      'this.vm.retryLoadMore()'
    ].join('\n')],
    [viewModelPath, [
      'resumeStartIndex',
      'completedStartIndex',
      'this.ui.loadMoreFailed = true',
      'private readonly pageSize: number = 10',
      'batchCandidates',
      'this.enrichVisibleHistory(',
      'this.isCurrentRequest(',
      'this.enrichVisibleHistory(visibleCandidates, result.session, contentGeneration)',
      'private async enrichVisibleHistory(',
      'contentGeneration: number',
      'this.isCurrentContent(contentGeneration, session.scope)',
      'private isCurrentRequest(',
      'this.shouldRestartForScopeChange()'
    ].join('\n')],
    [mineViewModelPath, [
      'this.enrichVisibleHistory(',
      'this.isCurrentHistoryRequest('
    ].join('\n')],
    [repositoryPath, [
      'rawCount',
      'startIndex + fetchResult.rawCount',
      'fetchResult.rawCount === limit',
      'MineHistoryRequestSession',
      'latestSeriesCacheTtlMs',
      'enrichmentConcurrency: number = 4',
      'latestSeriesPending',
      'parentId: seriesId',
      'ItemSortBy.ParentIndexNumber',
      'ItemSortBy.IndexNumber',
      'timeout: this.latestSeriesTimeoutMs',
      "name: isEpisode ? (item.SeriesName || item.Name || '')"
    ].join('\n')],
    [routerPath, "static readonly MineHistoryPage = 'MineHistoryPage'"]
  ])
  resourcePaths.forEach((path) => sources.set(path, resourceSource()))
  colorPaths.forEach((path) => sources.set(path, colorSource()))
  return sources
}

test('accepts the complete Mine recent-watching contract', () => {
  assert.doesNotThrow(() => validateMineRecentWatching(validSources()))
})

test('rejects a duplicate poster implementation', () => {
  const sources = validSources()
  sources.set(sectionPath, `${sources.get(sectionPath) ?? ''}\n@ComponentV2\nstruct MineHistoryPoster {}`)
  assert.throws(() => validateMineRecentWatching(sources), /shared poster/)
})

test('rejects the administrator console below recent watching', () => {
  const sources = validSources()
  sources.set(mineTabPath, (sources.get(mineTabPath) ?? '')
    .replace('if (this.ui.isAdministrator)', 'MineHistorySection({})\nif (this.ui.isAdministrator)'))
  assert.throws(() => validateMineRecentWatching(sources), /console.*before.*history/)
})

test('rejects update text derived from the current episode', () => {
  const sources = validSources()
  sources.set(repositoryPath, `${sources.get(repositoryPath) ?? ''}\n` +
    'latestEpisodeNumber: item.IndexNumber')
  assert.throws(() => validateMineRecentWatching(sources), /latest episode must come from series query/)
})

test('requires a system destination and real scroll controller', () => {
  const sources = validSources()
  sources.set(pagePath, (sources.get(pagePath) ?? '')
    .replace('scrollControllers: [this.contentScroller]', ''))
  assert.throws(() => validateMineRecentWatching(sources), /scroll controller/)
})

test('requires async episode badges to invalidate poster keys', () => {
  const sources = validSources()
  sources.set(sectionPath, (sources.get(sectionPath) ?? '')
    .replace('(item: MineHistoryItem) => this.historyItemKey(item)', '(item: MineHistoryItem) => item.id'))
  assert.throws(() => validateMineRecentWatching(sources), /rebuild a poster/)
})

test('requires load-more failure to preserve content', () => {
  const sources = validSources()
  sources.set(viewModelPath, (sources.get(viewModelPath) ?? '')
    .replace('this.ui.loadMoreFailed = true', ''))
  assert.throws(() => validateMineRecentWatching(sources), /load-more retry/)
})

test('rejects pagination based on filtered item count', () => {
  const sources = validSources()
  sources.set(repositoryPath, (sources.get(repositoryPath) ?? '')
    .replace('startIndex + fetchResult.rawCount', 'startIndex + fetchResult.items.length'))
  assert.throws(() => validateMineRecentWatching(sources), /rawCount/)
})

test('rejects downloading every episode for series badges', () => {
  const sources = validSources()
  sources.set(repositoryPath, `${sources.get(repositoryPath) ?? ''}\ngetTvShowsApi().getEpisodes({})`)
  assert.throws(() => validateMineRecentWatching(sources), /download every episode/)
})

test('rejects blocking base history on series enrichment', () => {
  const sources = validSources()
  sources.set(repositoryPath, `${sources.get(repositoryPath) ?? ''}\nawait this.enrichSeriesPositions(items)`)
  assert.throws(() => validateMineRecentWatching(sources), /must not wait/)
})

test('requires bounded series requests and stale-session guards', () => {
  const sources = validSources()
  sources.set(repositoryPath, (sources.get(repositoryPath) ?? '')
    .replace('enrichmentConcurrency: number = 4', 'enrichmentConcurrency: number = 40'))
  assert.throws(() => validateMineRecentWatching(sources), /enrichmentConcurrency/)
})

test('requires pagination to restart after an account change', () => {
  const sources = validSources()
  sources.set(viewModelPath, (sources.get(viewModelPath) ?? '')
    .replace('this.shouldRestartForScopeChange()', ''))
  assert.throws(() => validateMineRecentWatching(sources), /restart after an account change/)
})

test('rejects enrichment coupled to the pagination request epoch', () => {
  const sources = validSources()
  sources.set(viewModelPath, (sources.get(viewModelPath) ?? '')
    .replace('this.isCurrentContent(contentGeneration, session.scope)',
      'this.isCurrentRequest(requestEpoch, contentGeneration, session.scope)'))
  assert.throws(() => validateMineRecentWatching(sources), /pagination request epoch/)
})

test('rejects poster image state frozen to the first item', () => {
  const sources = validSources()
  sources.set(posterPath, `${sources.get(posterPath) ?? ''}\n@Local imageOption = {}`)
  assert.throws(() => validateMineRecentWatching(sources), /freeze the first item/)
})

test('requires episode history to use the series name', () => {
  const sources = validSources()
  sources.set(repositoryPath, (sources.get(repositoryPath) ?? '')
    .replace("name: isEpisode ? (item.SeriesName || item.Name || '')", "name: item.Name || ''"))
  assert.throws(() => validateMineRecentWatching(sources), /prefer the series name/)
})

test('rejects hardcoded dynamic Mine history copy', () => {
  const sources = validSources()
  sources.set(posterPath, `${sources.get(posterPath) ?? ''}\nreturn ` + '`更新至${episode}集`')
  assert.throws(() => validateMineRecentWatching(sources), /resource-driven/)
})

test('requires ResourceStr-independent badge visibility', () => {
  const sources = validSources()
  sources.set(posterPath, (sources.get(posterPath) ?? '')
    .replace('if (this.hasBadge())', 'if (this.badgeText().length > 0)'))
  assert.throws(() => validateMineRecentWatching(sources), /ResourceStr|resource-driven/)
})

test('requires all localized dynamic Mine history resources', () => {
  const sources = validSources()
  sources.set(resourcePaths[2], JSON.stringify({ string: [] }))
  assert.throws(() => validateMineRecentWatching(sources), /mine_history_title/)
})

test('requires theme-owned media overlay colors', () => {
  const sources = validSources()
  sources.set(colorPaths[0], JSON.stringify({ color: [] }))
  assert.throws(() => validateMineRecentWatching(sources), /media_badge_background/)
})
