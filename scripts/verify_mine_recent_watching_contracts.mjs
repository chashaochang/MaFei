import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

export const posterPath = 'entry/src/main/ets/features/home/minetab/MineHistoryPoster.ets'
export const sectionPath = 'entry/src/main/ets/features/home/minetab/MineHistorySection.ets'
export const mineTabPath = 'entry/src/main/ets/features/home/minetab/MineTab.ets'
export const pagePath = 'entry/src/main/ets/features/home/minetab/MineHistoryPage.ets'
export const viewModelPath = 'entry/src/main/ets/features/home/minetab/MineHistoryPageViewModel.ets'
export const mineViewModelPath = 'entry/src/main/ets/features/home/minetab/MineViewModel.ets'
export const repositoryPath = 'entry/src/main/ets/features/home/minetab/MineHistoryRepository.ets'
export const routerPath = 'entry/src/main/ets/common/RouterConsts.ets'
export const resourcePaths = [
  'entry/src/main/resources/base/element/string.json',
  'entry/src/main/resources/zh_CN/element/string.json',
  'entry/src/main/resources/en_US/element/string.json'
]
export const colorPaths = [
  'entry/src/main/resources/base/element/color.json',
  'entry/src/main/resources/dark/element/color.json'
]

function source(sources, path) {
  const value = sources.get(path)
  if (value === undefined) {
    throw new Error(`missing source: ${path}`)
  }
  return value
}

function requireText(value, text, message) {
  if (!value.includes(text)) {
    throw new Error(message)
  }
}

export function validateMineRecentWatching(sources) {
  const poster = source(sources, posterPath)
  const section = source(sources, sectionPath)
  const mineTab = source(sources, mineTabPath)
  const page = source(sources, pagePath)
  const viewModel = source(sources, viewModelPath)
  const mineViewModel = source(sources, mineViewModelPath)
  const repository = source(sources, repositoryPath)
  const router = source(sources, routerPath)

  requireText(poster, 'export struct MineHistoryPoster', 'shared poster must be exported')
  requireText(poster, 'imageKnifeOption: {', 'poster image options must follow the current item')
  requireText(poster, "calc(100% - 16vp)", 'poster badge must reserve padding and edge spacing')
  requireText(poster, 'private titleText(): ResourceStr',
    'history title must support localized resources')
  ;[
    'mine_history_unnamed',
    'mine_history_episode_title_single',
    'mine_history_episode_title_season',
    'mine_history_updated_to_episode',
    'mine_history_updated_to_season_episode'
  ].forEach((key) => requireText(poster, key, `poster is missing localized resource: ${key}`))
  requireText(poster, "this.item.name, this.item.episodeNumber",
    'episode history title must pass the series name to the localized title')
  requireText(poster, 'private hasBadge(): boolean',
    'poster must decide badge visibility independently from ResourceStr')
  requireText(poster, 'if (this.hasBadge())',
    'poster must not inspect ResourceStr length')
  if (poster.includes('badgeText().length') || /return\s+`(?:第|更新至)/.test(poster)) {
    throw new Error('dynamic Mine history copy must remain resource-driven')
  }
  requireText(poster, '.fontSize(9)', 'episode update badge must fit narrow posters')
  requireText(poster, '.maxLines(2)', 'history title must have room for the series name')
  requireText(poster, "app.color.media_badge_background", 'poster badge must use a color resource')
  requireText(poster, "app.color.media_progress_track_on_image", 'poster progress track must use a color resource')
  requireText(poster, "app.color.bg_2", 'poster placeholder must preserve the Feiniu theme')
  if (poster.includes('@Local imageOption')) {
    throw new Error('poster image options must not freeze the first item image')
  }
  if (/struct\s+MineHistoryPoster\b/.test(section)) {
    throw new Error('Mine history section must use the shared poster')
  }
  requireText(section, '@Require @Param onMore: () => void', 'history section must expose More')
  requireText(section, 'private loadingGrid(nativeSurface: boolean)',
    'Mine history skeleton must resolve the active theme')
  requireText(section, '(item: MineHistoryItem) => this.historyItemKey(item)',
    'Mine overview must rebuild a poster after async badge enrichment')
  requireText(section, 'item.latestEpisodeNumber || 0',
    'Mine overview key must follow async episode badge data')
  requireText(mineTab, 'HMRouterMgr.to(RouterConsts.MineHistoryPage).push()',
    'Mine history More must open the full page')
  requireText(mineTab, 'playMediaRefs([item.mediaRef]',
    'Mine history playback must preserve the provider/account-scoped MediaRef')
  if (/\bplayItems\s*\(/.test(mineTab)) {
    throw new Error('Mine history must not use the legacy Jellyfin playback entry')
  }

  const administratorBlocks = mineTab.match(/if \(this\.ui\.isAdministrator\)/g) || []
  if (administratorBlocks.length !== 1) {
    throw new Error('Mine must contain exactly one administrator console block')
  }
  const consoleIndex = mineTab.indexOf('if (this.ui.isAdministrator)')
  const historyIndex = mineTab.indexOf('MineHistorySection({')
  if (consoleIndex < 0 || historyIndex < 0 || consoleIndex > historyIndex) {
    throw new Error('administrator console must appear before recent history')
  }

  requireText(router, "static readonly MineHistoryPage = 'MineHistoryPage'",
    'Mine history route constant is missing')
  requireText(page, 'scrollControllers: [this.contentScroller]',
    'full history page must bind a real scroll controller')
  requireText(page, '(item: MineHistoryItem) => this.historyItemKey(item)',
    'full history page must rebuild a poster after async badge enrichment')
  requireText(page, 'item.latestEpisodeNumber || 0',
    'full history key must follow async episode badge data')
  ;[
    '@HMRouter({',
    'pageUrl: RouterConsts.MineHistoryPage',
    'AppRouteDestination({',
    'MineHistoryPoster({',
    '.onReachEnd(() =>',
    'MineHistoryPageState.Loading',
    'MineHistoryPageState.Empty',
    'MineHistoryPageState.Error',
    'this.loadingGrid(nativeSurface)',
    'this.vm.retryLoadMore()'
  ].forEach((text) => requireText(page, text, `full history page is missing: ${text}`))

  requireText(viewModel, 'this.ui.loadMoreFailed = true',
    'full history page must keep a load-more retry state')
  requireText(viewModel, 'resumeStartIndex', 'Resume cursor is missing')
  requireText(viewModel, 'completedStartIndex', 'Completed cursor is missing')
  if (!/private\s+readonly\s+pageSize:\s*number\s*=\s*10\b/.test(viewModel)) {
    throw new Error('full history batches must stay bounded')
  }
  requireText(viewModel, 'batchCandidates', 'full history must enrich only the newly loaded batch')
  requireText(viewModel, 'this.enrichVisibleHistory(', 'full history enrichment must be non-blocking')
  requireText(viewModel, 'this.isCurrentRequest(', 'full history must reject stale session results')
  requireText(viewModel,
    'this.enrichVisibleHistory(visibleCandidates, result.session, contentGeneration)',
    'full history enrichment must follow the current content generation')
  const enrichmentStart = viewModel.indexOf('private async enrichVisibleHistory')
  const requestGuardStart = viewModel.indexOf('private isCurrentRequest', enrichmentStart)
  if (enrichmentStart < 0 || requestGuardStart < 0) {
    throw new Error('full history enrichment guard is missing')
  }
  const enrichmentSource = viewModel.slice(enrichmentStart, requestGuardStart)
  requireText(enrichmentSource, 'contentGeneration: number',
    'full history enrichment must capture the content generation')
  if (enrichmentSource.includes('requestEpoch') || enrichmentSource.includes('this.isCurrentRequest(')) {
    throw new Error('full history enrichment must not depend on the pagination request epoch')
  }
  requireText(enrichmentSource, 'this.isCurrentContent(contentGeneration, session.scope)',
    'full history enrichment must validate the current content generation')
  requireText(viewModel, 'this.shouldRestartForScopeChange()',
    'full history pagination must restart after an account change')
  requireText(mineViewModel, 'this.enrichVisibleHistory(', 'Mine overview enrichment must be non-blocking')
  requireText(mineViewModel, 'this.isCurrentHistoryRequest(', 'Mine overview must reject stale session results')
  requireText(repository, "name: isEpisode ? (item.SeriesName || item.Name || '')",
    'episode history must prefer the series name')

  ;[
    'rawCount',
    'startIndex + fetchResult.rawCount',
    'fetchResult.rawCount === limit',
    'MineHistoryRequestSession',
    'latestSeriesCacheTtlMs',
    'latestSeriesPending',
    'parentId: seriesId',
    'ItemSortBy.ParentIndexNumber',
    'ItemSortBy.IndexNumber',
    'timeout: this.latestSeriesTimeoutMs'
  ]
    .forEach((text) => requireText(repository, text, `series enrichment is missing: ${text}`))
  if (!/enrichmentConcurrency:\s*number\s*=\s*4\b/.test(repository)) {
    throw new Error('series enrichment is missing: bounded enrichmentConcurrency')
  }
  if (repository.includes('getTvShowsApi') || repository.includes('getEpisodes({')) {
    throw new Error('series enrichment must not download every episode')
  }
  if (repository.includes('await this.enrichSeriesPositions')) {
    throw new Error('base history loading must not wait for series enrichment')
  }
  if (/latestEpisodeNumber\s*:\s*item\.IndexNumber/.test(repository) ||
    /latestSeasonNumber\s*:\s*item\.ParentIndexNumber/.test(repository)) {
    throw new Error('latest episode must come from series query')
  }

  const requiredKeys = [
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
  ]
  resourcePaths.forEach((path) => {
    const parsed = JSON.parse(source(sources, path))
    const names = new Set(parsed.string.map((item) => item.name))
    requiredKeys.forEach((key) => {
      if (!names.has(key)) {
        throw new Error(`${path} is missing ${key}`)
      }
    })
  })

  colorPaths.forEach((path) => {
    const parsed = JSON.parse(source(sources, path))
    const names = new Set(parsed.color.map((item) => item.name))
    ;['media_badge_background', 'media_progress_track_on_image'].forEach((key) => {
      if (!names.has(key)) {
        throw new Error(`${path} is missing ${key}`)
      }
    })
  })
}

export function defaultWorkspaceRoot() {
  return resolve(dirname(fileURLToPath(import.meta.url)), '..')
}

export function validateWorkspace(root = defaultWorkspaceRoot()) {
  const paths = [posterPath, sectionPath, mineTabPath, pagePath, viewModelPath,
    mineViewModelPath, repositoryPath, routerPath, ...resourcePaths, ...colorPaths]
  const sources = new Map(paths.map((path) => [path, readFileSync(resolve(root, path), 'utf8')]))
  validateMineRecentWatching(sources)
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  validateWorkspace()
}
