import assert from 'node:assert/strict'
import test from 'node:test'
import {
  loadManagementSessionSources,
  managementSessionPaths,
  validateManagementSessions
} from './verify_management_sessions_contracts.mjs'

function validSources() {
  return loadManagementSessionSources()
}

test('accepts the current management session implementation', () => {
  assert.doesNotThrow(() => validateManagementSessions(validSources()))
})

test('rejects a sessions request without the Web activity window', () => {
  const sources = validSources()
  sources.set(managementSessionPaths.service,
    sources.get(managementSessionPaths.service)
      .replace('activeWithinSeconds: 960', 'activeWithinSeconds: 60'))
  assert.throws(() => validateManagementSessions(sources), /960 second/)
})

test('rejects snapshot routing', () => {
  const sources = validSources()
  sources.set(managementSessionPaths.dashboardPage,
    sources.get(managementSessionPaths.dashboardPage)
      .replace('withParam({ sessionId: session.id })', 'withParam({ session: session })'))
  assert.throws(() => validateManagementSessions(sources), /sessionId only/)
})

test('rejects unsupported remote commands', () => {
  const sources = validSources()
  sources.set(managementSessionPaths.repository,
    sources.get(managementSessionPaths.repository) + '\nSeek NextTrack PreviousTrack')
  assert.throws(() => validateManagementSessions(sources), /command whitelist/)
})

test('rejects a dashboard session row without mapped artwork', () => {
  const sources = validSources()
  sources.set(managementSessionPaths.dashboardSection,
    sources.get(managementSessionPaths.dashboardSection)
      .replace('loadSrc: session.imageUrl', "loadSrc: ''"))
  assert.throws(() => validateManagementSessions(sources), /media artwork/)
})

test('rejects idle sessions without last activity', () => {
  const sources = validSources()
  sources.set(managementSessionPaths.dashboardSection,
    sources.get(managementSessionPaths.dashboardSection)
      .replace('if (!session.hasMedia) {', 'if (session.hasMedia) {'))
  assert.throws(() => validateManagementSessions(sources), /last activity/)
})

test('rejects a detail page without stale state feedback', () => {
  const sources = validSources()
  sources.set(managementSessionPaths.detailPage,
    sources.get(managementSessionPaths.detailPage)
      .replace('if (this.ui.stale) {', 'if (false) {'))
  assert.throws(() => validateManagementSessions(sources), /stale data/)
})

test('rejects an ended session without a dashboard return action', () => {
  const sources = validSources()
  sources.set(managementSessionPaths.detailPage,
    sources.get(managementSessionPaths.detailPage)
      .replace('management_session_return_dashboard', 'management_session_ended'))
  assert.throws(() => validateManagementSessions(sources), /return-to-dashboard/)
})

test('rejects raw transcode reason enums', () => {
  const sources = validSources()
  sources.set(managementSessionPaths.detailPage,
    sources.get(managementSessionPaths.detailPage) +
      '\nitem.transcodeReasons.join(", ")')
  assert.throws(() => validateManagementSessions(sources), /raw server transcode enums/)
})
