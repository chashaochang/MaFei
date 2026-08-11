import assert from 'node:assert/strict'
import test from 'node:test'
import {
  loadBatchDeleteSources,
  loadManagementLibraryServiceText,
  runManagementLibraryValidation,
  verifyBatchDeleteText,
  verifyMediaEntryScopeText,
  verifyServiceOwnershipText,
  verifyServiceText,
  verifyUiText
} from './verify_management_library_contracts.mjs'

const endpointFixture = `
  /Library/VirtualFolders
  /Library/VirtualFolders/LibraryOptions
  /Library/VirtualFolders/Name
  /Library/VirtualFolders/Paths
  /Library/VirtualFolders/Paths/Update
  /Libraries/AvailableOptions
  /Environment/Drives
  /Environment/DirectoryContents
  /Environment/ParentPath
  /Environment/ValidatePath
  /Localization/Countries
  /Localization/Cultures
  /Items/{itemId}/Refresh
  /ScheduledTasks
  /ScheduledTasks/Running/{taskId}
`

test('requires complete Jellyfin library administrator endpoints', () => {
  assert.doesNotThrow(() => verifyServiceText(endpointFixture))
})

test('rejects a missing administrator endpoint', () => {
  assert.throws(() => verifyServiceText(
    endpointFixture.replace('/Environment/ValidatePath', '/Environment/MissingPath')
  ), /missing endpoint/)
})

test('rejects page-owned axios requests', () => {
  assert.throws(() => verifyUiText(
    'await ApiClient.Instance().axiosInstance.get("/Library/VirtualFolders")'
  ), /direct HTTP/)
})

test('rejects all direct HTTP ownership markers in UI', () => {
  for (const marker of ['axiosInstance', 'ApiClient.Instance()', '.get<void>(', '.post<void>(', '.delete<void>(']) {
    assert.throws(() => verifyUiText(marker), /direct HTTP/)
  }
})

test('requires generated deleteItems ownership', () => {
  const source = loadManagementLibraryServiceText()
    .replace('.deleteItems({ ids: ids })', '.missingDeleteItems({ ids: ids })')
  assert.throws(() => verifyServiceOwnershipText(source), /generated deleteItems/)
})

test('rejects query-string concatenation in the API service', () => {
  const source = loadManagementLibraryServiceText() +
    '\nconst badUrl = "/Library/VirtualFolders?name=Movies"'
  assert.throws(() => verifyServiceOwnershipText(source), /Axios params/)
})

test('accepts the current media library API ownership', () => {
  assert.doesNotThrow(() => runManagementLibraryValidation())
})

test('rejects Media root actions without administrator and destination scoping', () => {
  assert.throws(() => verifyMediaEntryScopeText(`
    rootNavigationMediaAdminActionsVisible
    selectedDestination === HomeDestination.Media
    publishRootNavigationChrome(false, '首页', false, false)
  `), /administrator scoped/)
})

test('requires CanDelete and Path in every video list query', () => {
  const sources = loadBatchDeleteSources()
  sources.listViewModel = sources.listViewModel.replace("'CanDelete', 'Path'", "'Path'")
  assert.throws(() => verifyBatchDeleteText(sources), /every video list query/)
})

test('rejects direct batch-delete submission from VideoListPage', () => {
  const sources = loadBatchDeleteSources()
  sources.listPage += '\ngetLibraryApi().deleteItems({ ids: [] })'
  assert.throws(() => verifyBatchDeleteText(sources), /must not submit deleteItems directly/)
})

test('requires stable long-press selection overlay contracts', () => {
  const sources = loadBatchDeleteSources()
  sources.listPage = sources.listPage.replace('.backgroundColor(0x38007DFF)', '')
  assert.throws(() => verifyBatchDeleteText(sources), /selection UI is incomplete/)
})

test('requires long-press selection to outrank grid and image gestures', () => {
  const sources = loadBatchDeleteSources()
  sources.listPage = sources.listPage.replace('.priorityGesture(', '.gesture(')
  assert.throws(() => verifyBatchDeleteText(sources), /selection UI is incomplete/)
})

test('requires server capability recheck before delete confirmation', () => {
  const sources = loadBatchDeleteSources()
  sources.listPage = sources.listPage.replace('await this.vm.prepareDeleteSelection()', 'this.vm.deleteSummary()')
  assert.throws(() => verifyBatchDeleteText(sources), /selection UI is incomplete/)
})

test('requires successful deletion to publish shared media refresh', () => {
  const sources = loadBatchDeleteSources()
  sources.listViewModel = sources.listViewModel.replace('eventHub.emit(MediaLibraryRefreshEvent)', 'eventHub.emit()')
  assert.throws(() => verifyBatchDeleteText(sources), /batch-delete ownership is incomplete/)
})

test('requires batch confirmation to expose eligible names and count', () => {
  const sources = loadBatchDeleteSources()
  sources.deleteDialog = sources.deleteDialog.replaceAll('allowedIds.length', 'blockedNames.length')
  assert.throws(() => verifyBatchDeleteText(sources), /confirmation scope is incomplete/)
})
