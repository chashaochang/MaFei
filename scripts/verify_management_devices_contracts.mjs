import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

export const managementDevicePaths = Object.freeze({
  models: 'entry/src/main/ets/features/management/devices/ManagementDeviceModels.ets',
  protection: 'entry/src/main/ets/features/management/devices/ManagementDeviceProtection.ets',
  query: 'entry/src/main/ets/features/management/devices/ManagementDeviceQuery.ets',
  service: 'entry/src/main/ets/features/management/devices/ManagementDeviceApiService.ets',
  repository: 'entry/src/main/ets/features/management/devices/ManagementDeviceRepository.ets',
  state: 'entry/src/main/ets/features/management/devices/ManagementDevicesUIState.ets',
  viewModel: 'entry/src/main/ets/features/management/devices/ManagementDevicesViewModel.ets',
  listPage: 'entry/src/main/ets/features/management/devices/ManagementDevicesPage.ets',
  detailPage: 'entry/src/main/ets/features/management/devices/ManagementDeviceDetailPage.ets',
  detailPanel: 'entry/src/main/ets/features/management/devices/ManagementDeviceDetailPanel.ets'
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

export function validateManagementDevices(sources) {
  for (const path of Object.values(managementDevicePaths)) {
    requiredSource(sources, path)
  }
  for (const path of Object.values(managementDevicePaths)) {
    rejectPattern(sources, path, /\bAccessToken\b/,
      'stable device management must not expose AccessToken')
  }

  requirePattern(sources, managementDevicePaths.models,
    /MANAGEMENT_DEVICE_PAGE_SIZE\s*:\s*number\s*=\s*25/,
    'device paging must use 25 items')
  requirePattern(sources, managementDevicePaths.repository,
    /ApiClient\.Instance\(\)\.deviceInfo\.id/,
    'current device must use the exact ApiClient device id')
  for (const path of [
    managementDevicePaths.viewModel,
    managementDevicePaths.repository,
    managementDevicePaths.service
  ]) {
    requirePattern(sources, path, /assertNotCurrentDevice/,
      'current device rejection is missing from a required layer')
  }
  requirePattern(sources, managementDevicePaths.repository,
    /revocableIds\s*\(\s*devices\s*,\s*currentDeviceId\s*\)/,
    'batch revoke must exclude the exact current device id')
  requirePattern(sources, managementDevicePaths.protection,
    /item\.id\s*!==\s*currentDeviceId/,
    'batch protection must compare the exact current device id')
  requirePattern(sources, managementDevicePaths.protection,
    /if\s*\(\s*!currentDeviceId\s*\)\s*return\s*\[\s*\]/,
    'unknown current device id must make batch revoke fail closed')
  requirePattern(sources, managementDevicePaths.protection,
    /!currentDeviceId\s*\|\|\s*deviceId\s*===\s*currentDeviceId/,
    'unknown current device id must make single revoke fail closed')
  requirePattern(sources, managementDevicePaths.repository,
    /if\s*\(\s*!currentDeviceId\s*\)[\s\S]*failedIds\.push\s*\([\s\S]*return\s+result/,
    'batch revoke must report failures without issuing writes when current device id is missing')
  requirePattern(sources, managementDevicePaths.detailPage,
    /@Monitor\s*\(\s*['"]ui\.permissionState['"]\s*\)[\s\S]*ManagementPermissionState\.Denied[\s\S]*HMRouterMgr\.pop/,
    'device detail must exit after administrator access is denied')
  requirePattern(sources, managementDevicePaths.detailPage,
    /management_device_login_will_expire[\s\S]*showDialog/,
    'device revoke must require an invalidation confirmation')
  for (const path of [managementDevicePaths.listPage, managementDevicePaths.detailPage]) {
    requirePattern(sources, path,
      /AppThemeOverlayPolicy\.resolve\s*\([\s\S]*OverlaySurfaceRole\.Dangerous[\s\S]*this\.vm\.appUIState\.systemMaterialAvailable[\s\S]*this\.vm\.appUIState\.nativeThemeAvailable/,
      'device confirmation must use the runtime dangerous-overlay policy')
    requirePattern(sources, path,
      /OverlayMaterialDecision\.DisableSystemMaterial[\s\S]*systemMaterial:\s*AppThemeSurfaceResolver\.disabledSystemMaterial\s*\(\s*\)/,
      'device confirmation must keep the disabled-material branch')
    rejectPattern(sources, path, /deviceInfo\.sdkApiVersion\s*>=\s*26/,
      'device confirmation must not hard-code an API 26 SDK guard')
  }
  requirePattern(sources, managementDevicePaths.detailPage,
    /ManagementSectionStatus\.Loading/,
    'device detail must expose a loading state')
  requirePattern(sources, managementDevicePaths.detailPage,
    /ManagementSectionStatus\.Unsupported/,
    'device detail must expose an unsupported state')
  requirePattern(sources, managementDevicePaths.detailPage,
    /management_retry/,
    'device detail must expose empty/error retry')
  requirePattern(sources, managementDevicePaths.service,
    /post<void>[\s\S]*\{\s*CustomName\s*:\s*customName\s*\}[\s\S]*params\s*:\s*\{\s*id\s*:\s*deviceId\s*\}/,
    'device rename must use a CustomName-only body and structured id params')
  requirePattern(sources, managementDevicePaths.service,
    /delete<void>[\s\S]*params\s*:\s*\{\s*id\s*:\s*deviceId\s*\}/,
    'device revoke must use structured id params')
  rejectPattern(sources, managementDevicePaths.service, /\/Devices\?[^'"`\s]*/,
    'device requests must not concatenate query strings')
  requirePattern(sources, managementDevicePaths.repository,
    /checkCurrentUserAccess[\s\S]*updateName|checkCurrentUserAccess[\s\S]*service\.updateName/,
    'device rename must recheck administrator access')
  requirePattern(sources, managementDevicePaths.repository,
    /checkCurrentUserAccess[\s\S]*service\.revoke/,
    'device revoke must recheck administrator access')
  requirePattern(sources, managementDevicePaths.viewModel,
    /pendingDeviceId[\s\S]*batchPending/,
    'device writes must keep row and batch action locks')
  requirePattern(sources, managementDevicePaths.query,
    /pageCount[\s\S]*pageIndex[\s\S]*slice/,
    'device query must clamp and slice local pages')
  for (const path of [managementDevicePaths.listPage, managementDevicePaths.detailPage]) {
    rejectPattern(sources, path, /axiosInstance|\/Devices\b/,
      'device pages must not perform direct HTTP')
  }
}

export function defaultWorkspaceRoot() {
  return dirname(dirname(fileURLToPath(import.meta.url)))
}

export function loadManagementDeviceSources(workspaceRoot = defaultWorkspaceRoot()) {
  const sources = new Map()
  for (const path of Object.values(managementDevicePaths)) {
    sources.set(path, readFileSync(resolve(workspaceRoot, path), 'utf8'))
  }
  return sources
}

export function runManagementDeviceValidation(workspaceRoot = defaultWorkspaceRoot()) {
  validateManagementDevices(loadManagementDeviceSources(workspaceRoot))
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  runManagementDeviceValidation()
}
