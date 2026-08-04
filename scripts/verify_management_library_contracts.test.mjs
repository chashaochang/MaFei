import assert from 'node:assert/strict'
import test from 'node:test'
import {
  loadManagementLibraryServiceText,
  runManagementLibraryValidation,
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
