import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

export const managementSessionPaths = Object.freeze({
  service: 'entry/src/main/ets/features/management/sessions/ManagementSessionApiService.ets',
  models: 'entry/src/main/ets/features/management/sessions/ManagementSessionModels.ets',
  mapper: 'entry/src/main/ets/features/management/sessions/ManagementSessionMapper.ets',
  repository: 'entry/src/main/ets/features/management/sessions/ManagementSessionRepository.ets',
  state: 'entry/src/main/ets/features/management/sessions/ManagementSessionsUIState.ets',
  viewModel: 'entry/src/main/ets/features/management/sessions/ManagementSessionsViewModel.ets',
  dashboardSection: 'entry/src/main/ets/features/management/sessions/ManagementSessionsDashboardSection.ets',
  detailPage: 'entry/src/main/ets/features/management/sessions/ManagementSessionDetailPage.ets',
  dashboardPage: 'entry/src/main/ets/features/management/ManagementDashboardPage.ets'
})

function requiredSource(sources, path) {
  const source = sources.get(path)
  if (source === undefined) {
    throw new Error('missing source: ' + path)
  }
  return source
}

function requirePattern(sources, path, pattern, message) {
  if (!pattern.test(requiredSource(sources, path))) {
    throw new Error(message + ': ' + path)
  }
}

function rejectPattern(sources, path, pattern, message) {
  if (pattern.test(requiredSource(sources, path))) {
    throw new Error(message + ': ' + path)
  }
}

export function validateManagementSessions(sources) {
  for (const path of Object.values(managementSessionPaths)) {
    requiredSource(sources, path)
  }

  requirePattern(sources, managementSessionPaths.service, /activeWithinSeconds\s*:\s*960/,
    'session query must keep the Jellyfin Web 960 second activity window')
  requirePattern(sources, managementSessionPaths.models,
    /MANAGEMENT_SESSION_RECENT_SECONDS\s*:\s*number\s*=\s*95/,
    'recent session filtering must keep the 95 second cutoff')
  requirePattern(sources, managementSessionPaths.models,
    /MANAGEMENT_SESSION_POLL_INTERVAL_MS\s*:\s*number\s*=\s*2000/,
    'visible session polling must keep the two second interval')
  requirePattern(sources, managementSessionPaths.mapper,
    /LastActivityDate[\s\S]*NowPlayingItem[\s\S]*UserId/,
    'session mapper must keep recent playing and idle user sessions')
  requirePattern(sources, managementSessionPaths.mapper,
    /imageItemId[\s\S]*imageUrl[\s\S]*getItemImageUrlById/,
    'mapped session artwork must resolve through the shared Jellyfin image URL API')
  requirePattern(sources, managementSessionPaths.dashboardSection,
    /ImageKnifeComponent[\s\S]*loadSrc\s*:\s*session\.imageUrl/,
    'playing sessions must render mapped media artwork')
  requirePattern(sources, managementSessionPaths.dashboardSection,
    /deviceLine[\s\S]*management_detail_device[\s\S]*session\.deviceName/,
    'dashboard session rows must always render the device name')
  requirePattern(sources, managementSessionPaths.dashboardSection,
    /if\s*\(\s*!session\.hasMedia\s*\)\s*\{[\s\S]{0,240}Text\s*\(\s*this\.idleActivityLine\s*\(\s*session\s*\)\s*\)/,
    'idle dashboard sessions must render their last activity time')
  requirePattern(sources, managementSessionPaths.detailPage,
    /if\s*\(\s*this\.ui\.stale\s*\)[\s\S]*management_stale_result/,
    'session detail must visibly mark retained stale data')
  requirePattern(sources, managementSessionPaths.viewModel,
    /ui\.ended\s*=\s*true[\s\S]{0,160}ui\.detail\s*=\s*null/,
    'an ended session must release its stale detail snapshot')
  requirePattern(sources, managementSessionPaths.detailPage,
    /management_session_return_dashboard[\s\S]*HMRouterMgr\.pop/,
    'an ended session must provide a clear return-to-dashboard action')
  requirePattern(sources, managementSessionPaths.models,
    /transcodeReasonResourceName[\s\S]*management_session_transcode_reason_other/,
    'transcode reasons must map to localized resource names with an unknown fallback')
  rejectPattern(sources, managementSessionPaths.detailPage,
    /item\.transcodeReasons\.join/,
    'session detail must not expose raw server transcode enums')
  requirePattern(sources, managementSessionPaths.service, /PlaystateCommand\.PlayPause/,
    'session command whitelist must include PlayPause')
  requirePattern(sources, managementSessionPaths.service, /PlaystateCommand\.Stop/,
    'session command whitelist must include Stop')
  requirePattern(sources, managementSessionPaths.service, /TimeoutMs\s*:\s*5000/,
    'session messages must use TimeoutMs=5000')
  requirePattern(sources, managementSessionPaths.dashboardPage,
    /withParam\s*\(\s*\{\s*sessionId\s*:\s*session\.id\s*\}\s*\)/,
    'session detail routing must pass sessionId only')
  rejectPattern(sources, managementSessionPaths.dashboardPage,
    /withParam\s*\(\s*\{\s*session\s*:/,
    'session snapshots must not be routed')
  requirePattern(sources, managementSessionPaths.detailPage,
    /getCurrentParam\s*\(\s*\)[\s\S]*\['sessionId'\]/,
    'session detail must reload from the sessionId route parameter')
  requirePattern(sources, managementSessionPaths.viewModel,
    /generation[\s\S]*isActive\s*\(\s*generation\s*\)[\s\S]*setInterval/,
    'session state must use generation ownership before visible polling')
  requirePattern(sources, managementSessionPaths.viewModel, /clearInterval/,
    'session polling must stop when the page is hidden')
  requirePattern(sources, managementSessionPaths.repository, /checkCurrentUserAccess/,
    'session writes must recheck administrator access')

  const commandSource = requiredSource(sources, managementSessionPaths.service) + '\n' +
    requiredSource(sources, managementSessionPaths.repository)
  if (/\b(?:Seek|NextTrack|PreviousTrack|PlayNext|PlayPrevious)\b/.test(commandSource)) {
    throw new Error('session command whitelist contains an unsupported command')
  }
  for (const path of [
    managementSessionPaths.models,
    managementSessionPaths.state,
    managementSessionPaths.dashboardSection,
    managementSessionPaths.detailPage
  ]) {
    rejectPattern(sources, path, /\bAccessToken\b/, 'session stable state must not expose AccessToken')
  }
  for (const path of [
    managementSessionPaths.dashboardSection,
    managementSessionPaths.detailPage
  ]) {
    rejectPattern(sources, path, /axiosInstance|getSessionApi|\/Sessions\b/,
      'session pages must not perform direct HTTP')
  }
}

export function defaultWorkspaceRoot() {
  return dirname(dirname(fileURLToPath(import.meta.url)))
}

export function loadManagementSessionSources(workspaceRoot = defaultWorkspaceRoot()) {
  const sources = new Map()
  for (const path of Object.values(managementSessionPaths)) {
    sources.set(path, readFileSync(resolve(workspaceRoot, path), 'utf8'))
  }
  return sources
}

export function runManagementSessionValidation(workspaceRoot = defaultWorkspaceRoot()) {
  validateManagementSessions(loadManagementSessionSources(workspaceRoot))
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  runManagementSessionValidation()
}
