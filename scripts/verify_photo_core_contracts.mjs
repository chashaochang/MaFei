import { readFileSync, readdirSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const ROUTER_CONSTS = 'entry/src/main/ets/common/RouterConsts.ets'
const MEDIA_TAB = 'entry/src/main/ets/features/home/mediatab/MediaTab.ets'
const HOME_SCREEN = 'entry/src/main/ets/features/home/HomeScreen.ets'
const MODULE_PROFILE = 'entry/src/main/module.json5'
const PHOTO_MODELS = 'entry/src/main/ets/features/photo/PhotoModels.ets'
const PHOTO_MAPPER = 'entry/src/main/ets/features/photo/PhotoMapper.ets'
const PHOTO_QUERY_PLANNER = 'entry/src/main/ets/features/photo/PhotoQueryPlanner.ets'
const PHOTO_REPOSITORY = 'entry/src/main/ets/features/photo/PhotoRepository.ets'
const PHOTO_LIBRARY_PAGE = 'entry/src/main/ets/features/photo/PhotoLibraryPage.ets'
const PHOTO_ALBUM_PAGE = 'entry/src/main/ets/features/photo/PhotoAlbumPage.ets'
const PHOTO_VIEWER_PAGE = 'entry/src/main/ets/features/photo/PhotoViewerPage.ets'
const PHOTO_ZOOMABLE_IMAGE = 'entry/src/main/ets/features/photo/PhotoZoomableImage.ets'
const PHOTO_INFO_SHEET = 'entry/src/main/ets/features/photo/PhotoInfoSheet.ets'
const PHOTO_SAVE_SERVICE = 'entry/src/main/ets/features/photo/PhotoSaveService.ets'
const PHOTO_SOURCE_STORE = 'entry/src/main/ets/features/photo/PhotoViewerSourceStore.ets'

const RESOURCE_PATHS = [
  'entry/src/main/resources/base/element/string.json',
  'entry/src/main/resources/zh_CN/element/string.json',
  'entry/src/main/resources/en_US/element/string.json'
]

const ROUTES = [
  ['PhotoLibraryPage', PHOTO_LIBRARY_PAGE],
  ['PhotoAlbumPage', PHOTO_ALBUM_PAGE],
  ['PhotoViewerPage', PHOTO_VIEWER_PAGE]
]

export const REQUIRED_PHOTO_STRING_KEYS = [
  'photo_back',
  'photo_empty_albums',
  'photo_empty_photos',
  'photo_error_authentication_expired',
  'photo_error_not_found',
  'photo_error_offline',
  'photo_error_permission_denied',
  'photo_favorite',
  'photo_favorite_failed',
  'photo_info',
  'photo_info_aperture',
  'photo_info_camera',
  'photo_info_date',
  'photo_info_dimensions',
  'photo_info_exposure',
  'photo_info_file_name',
  'photo_info_focal_length',
  'photo_info_format',
  'photo_info_iso',
  'photo_info_load_failed',
  'photo_info_location',
  'photo_info_orientation',
  'photo_info_path',
  'photo_info_shutter_speed',
  'photo_info_software',
  'photo_library_title',
  'photo_load_failed',
  'photo_load_more_failed',
  'photo_loading_more',
  'photo_loading_info',
  'photo_orientation_mirrored',
  'photo_orientation_normal',
  'photo_refresh',
  'photo_retry',
  'photo_save_authorization_unavailable',
  'photo_save_authorization_failed',
  'photo_save_failed',
  'photo_save_success',
  'photo_save_unavailable',
  'photo_slideshow_start',
  'photo_slideshow_stop',
  'photo_tab_albums',
  'photo_tab_photos',
  'photo_unfavorite',
  'photo_unknown_date',
  'photo_viewer_load_failed'
]

const REQUIRED_SOURCE_PATHS = [
  ROUTER_CONSTS,
  MEDIA_TAB,
  HOME_SCREEN,
  MODULE_PROFILE,
  PHOTO_MODELS,
  PHOTO_MAPPER,
  PHOTO_QUERY_PLANNER,
  PHOTO_REPOSITORY,
  PHOTO_LIBRARY_PAGE,
  PHOTO_ALBUM_PAGE,
  PHOTO_VIEWER_PAGE,
  PHOTO_ZOOMABLE_IMAGE,
  PHOTO_INFO_SHEET,
  PHOTO_SAVE_SERVICE,
  PHOTO_SOURCE_STORE
]

function requiredSource(sources, path) {
  const source = sources.get(path)
  if (source === undefined) {
    throw new Error('missing source: ' + path)
  }
  return source
}

function countMatches(source, pattern) {
  return Array.from(source.matchAll(pattern)).length
}

function bracedBlock(source, openingBrace) {
  let depth = 0
  for (let index = openingBrace; index < source.length; index += 1) {
    if (source[index] === '{') {
      depth += 1
    } else if (source[index] === '}') {
      depth -= 1
      if (depth === 0) {
        return source.slice(openingBrace + 1, index)
      }
    }
  }
  throw new Error('unterminated block')
}

function namedBlock(source, kind, name) {
  const signature = new RegExp('\\b' + kind + '\\s+' + name + '\\b[^\\{]*\\{')
  const match = signature.exec(source)
  if (!match) {
    throw new Error('missing ' + kind + ': ' + name)
  }
  return bracedBlock(source, source.indexOf('{', match.index))
}

function methodBlock(source, methodName) {
  const signature = new RegExp(
    '\\b(?:private\\s+|public\\s+|protected\\s+)?(?:static\\s+)?(?:async\\s+)?' +
      methodName + '\\s*\\('
  )
  const match = signature.exec(source)
  if (!match) {
    throw new Error('missing method: ' + methodName)
  }
  const openingParenthesis = source.indexOf('(', match.index)
  let parenthesisDepth = 0
  let closingParenthesis = -1
  for (let index = openingParenthesis; index < source.length; index += 1) {
    if (source[index] === '(') {
      parenthesisDepth += 1
    } else if (source[index] === ')') {
      parenthesisDepth -= 1
      if (parenthesisDepth === 0) {
        closingParenthesis = index
        break
      }
    }
  }
  if (closingParenthesis < 0) {
    throw new Error('unterminated method signature: ' + methodName)
  }
  const openingBrace = source.indexOf('{', closingParenthesis)
  if (openingBrace < 0) {
    throw new Error('missing method body: ' + methodName)
  }
  return bracedBlock(source, openingBrace)
}

function conditionForRoute(source, route) {
  const routeIndex = source.indexOf(route)
  if (routeIndex < 0) {
    throw new Error('missing route: ' + route)
  }
  const ifIndex = source.lastIndexOf('if (', routeIndex)
  if (ifIndex < 0) {
    throw new Error('missing route condition: ' + route)
  }
  const openingParenthesis = source.indexOf('(', ifIndex)
  let depth = 0
  for (let index = openingParenthesis; index < routeIndex; index += 1) {
    if (source[index] === '(') {
      depth += 1
    } else if (source[index] === ')') {
      depth -= 1
      if (depth === 0) {
        return source.slice(openingParenthesis + 1, index)
      }
    }
  }
  throw new Error('unterminated route condition: ' + route)
}

function sorted(values) {
  return Array.from(values).sort()
}

function assertEqualSets(left, right, message) {
  if (JSON.stringify(sorted(left)) !== JSON.stringify(sorted(right))) {
    throw new Error(message)
  }
}

function parseResourceKeys(source, path) {
  let parsed
  try {
    parsed = JSON.parse(source)
  } catch (error) {
    throw new Error('invalid resource json: ' + path + ': ' + error.message)
  }
  if (!Array.isArray(parsed.string)) {
    throw new Error('resource file has no string array: ' + path)
  }
  const keys = parsed.string.map((entry) => entry.name)
  if (new Set(keys).size !== keys.length) {
    throw new Error('duplicate resource key: ' + path)
  }
  return new Set(keys)
}

function photoSources(sources) {
  return Array.from(sources.entries()).filter(([path]) =>
    path.startsWith('entry/src/main/ets/features/photo/') && path.endsWith('.ets'))
}

function validateRoutes(sources) {
  const routerConsts = requiredSource(sources, ROUTER_CONSTS)
  for (const [route, path] of ROUTES) {
    if (countMatches(routerConsts, new RegExp('static\\s+readonly\\s+' + route + '\\s*=', 'g')) !== 1) {
      throw new Error('missing or duplicate photo route constant: ' + route)
    }
    const page = requiredSource(sources, path)
    const destination = new RegExp(
      '@HMRouter\\s*\\(\\s*\\{[\\s\\S]*?pageUrl\\s*:\\s*RouterConsts\\.' + route
    )
    if (!destination.test(page)) {
      throw new Error('missing photo route destination: ' + route)
    }
  }
}

function validateMediaRouting(sources) {
  const openLibrary = methodBlock(requiredSource(sources, MEDIA_TAB), 'openLibrary')
  const photoCondition = conditionForRoute(openLibrary, 'RouterConsts.PhotoLibraryPage')
  if (!/MediaLibraryKind\.Photos/.test(photoCondition)) {
    throw new Error('photo libraries must route to PhotoLibraryPage')
  }
  const videoCondition = conditionForRoute(openLibrary, 'RouterConsts.VideoListPage')
  if (/MediaLibraryKind\.Photos/.test(videoCondition)) {
    throw new Error('photo libraries must not reuse video routing')
  }
  if (!/media_library_type_unsupported/.test(openLibrary)) {
    throw new Error('unsupported media libraries must retain the typed prompt')
  }
}

function validateEmbeddedHome(sources) {
  const home = requiredSource(sources, HOME_SCREEN)
  if (!/MediaLibraryKind\.Photos[\s\S]{0,320}PhotoLibraryPage\s*\(\s*\{[\s\S]{0,220}fromHome\s*:\s*true/.test(home)) {
    throw new Error('large-screen home must embed PhotoLibraryPage for photo destinations')
  }
}

function validateViewerRoutePayload(sources) {
  const routeParam = namedBlock(requiredSource(sources, PHOTO_MODELS), 'interface', 'PhotoViewerRouteParam')
  const fields = Array.from(routeParam.matchAll(/^\s*([A-Za-z][A-Za-z0-9_]*)\??\s*:/gm))
    .map((match) => match[1])
  assertEqualSets(fields, ['sourceKey', 'initialPhotoId'],
    'viewer route payload must contain only sourceKey and initialPhotoId')

  for (const path of [PHOTO_LIBRARY_PAGE, PHOTO_ALBUM_PAGE]) {
    const openPhoto = methodBlock(requiredSource(sources, path), 'openPhoto')
    if (!/photoViewerSourceStore\.save\s*\(/.test(openPhoto) ||
      !/sourceKey\s*:\s*sourceKey/.test(openPhoto) ||
      !/initialPhotoId\s*:\s*photo\.id/.test(openPhoto) ||
      !/RouterConsts\.PhotoViewerPage/.test(openPhoto)) {
      throw new Error('photo pages must open the viewer through PhotoViewerSourceStore: ' + path)
    }
    if (/\b(?:photos|items)\s*:/.test(openPhoto)) {
      throw new Error('photo viewer route must not serialize the photo collection: ' + path)
    }
  }

  const viewer = requiredSource(sources, PHOTO_VIEWER_PAGE)
  if (!/HMRouterMgr\.getCurrentParam\s*\(\s*\)\s+as\s+PhotoViewerRouteParam/.test(viewer)) {
    throw new Error('viewer must consume the small PhotoViewerRouteParam contract')
  }
  const store = requiredSource(sources, PHOTO_SOURCE_STORE)
  if (!/resolve\s*\(\s*sourceKey\s*:\s*string\s*,\s*scope\s*:\s*string/.test(store) ||
    !/source\.scope\s*!==\s*normalizedScope/.test(store)) {
    throw new Error('viewer sources must resolve within the active account scope')
  }
}

function validateMappingAndPagination(sources) {
  const mapper = requiredSource(sources, PHOTO_MAPPER)
  if (!/PHOTO_THUMBNAIL_SIZE\s*:\s*number\s*=\s*720\b/.test(mapper) ||
    !/PHOTO_FULL_IMAGE_SIZE\s*:\s*number\s*=\s*4096\b/.test(mapper)) {
    throw new Error('photo image URLs must retain 720px thumbnails and 4096px full images')
  }
  if (countMatches(mapper, /ImageType\.Primary/g) < 1) {
    throw new Error('photo images must use the Jellyfin Primary image type')
  }
  if (!/RecursiveItemCount\s*\?\?\s*item\.ChildCount/.test(mapper)) {
    throw new Error('album counts must prefer RecursiveItemCount before ChildCount')
  }

  const planner = requiredSource(sources, PHOTO_QUERY_PLANNER)
  if (!/\[\s*ItemSortBy\.PremiereDate\s*,\s*ItemSortBy\.DateCreated\s*,\s*ItemSortBy\.SortName\s*\]/.test(planner) ||
    !/\[\s*SortOrder\.Descending\s*,\s*SortOrder\.Descending\s*,\s*SortOrder\.Ascending\s*\]/.test(planner)) {
    throw new Error('photo sorting must remain stable across PremiereDate, DateCreated, and SortName')
  }

  const repository = requiredSource(sources, PHOTO_REPOSITORY)
  if (countMatches(repository,
    /mapper\.page\s*\(\s*items\s*,\s*resultStartIndex\s*,\s*sourceItems\.length\s*,\s*totalCount\s*\)/g) < 2) {
    throw new Error('photo pagination must advance by the raw server item count')
  }
}

function validateViewerStructure(sources) {
  const viewer = requiredSource(sources, PHOTO_VIEWER_PAGE)
  if (!/\.backgroundColor\s*\(\s*(?:Color\.Black|['"]#(?:ff)?000000['"])\s*\)/i.test(viewer)) {
    throw new Error('photo viewer must own an opaque black media surface')
  }
  if (countMatches(viewer, /\bSwiper\s*\(/g) !== 1) {
    throw new Error('photo viewer must contain exactly one Swiper')
  }
  if (countMatches(viewer, /\bPhotoZoomableImage\s*\(/g) !== 1) {
    throw new Error('photo viewer must contain exactly one zoomable image owner')
  }
  if (countMatches(viewer, /\bPhotoInfoSheet\s*\(/g) !== 1 ||
    countMatches(viewer, /\.bindSheet\s*\(/g) !== 1) {
    throw new Error('photo viewer must contain exactly one information Sheet')
  }
  if (countMatches(viewer, /\bSaveButton\s*\(/g) !== 1) {
    throw new Error('photo viewer must expose exactly one system SaveButton')
  }
}

function validateViewerInteractionSafety(sources) {
  const viewer = requiredSource(sources, PHOTO_VIEWER_PAGE)
  const controls = methodBlock(viewer, 'controls')
  if (!/\.hitTestBehavior\s*\(\s*HitTestMode\.None\s*\)/.test(controls)) {
    throw new Error('viewer control overlay must let its empty area pass through')
  }

  const floatingButton = methodBlock(viewer, 'floatingButton')
  if (countMatches(floatingButton,
    /\.hitTestBehavior\s*\(\s*HitTestMode\.BLOCK_HIERARCHY\s*\)/g) !== 2) {
    throw new Error('native and legacy floating controls must both block lower hit-test targets')
  }

  const saveButton = methodBlock(viewer, 'saveButton')
  if (!/\bRow\s*\(\s*\)\s*\{[\s\S]*?\bSaveButton\s*\(/.test(saveButton) ||
    !/\.hitTestBehavior\s*\(\s*HitTestMode\.BLOCK_HIERARCHY\s*\)/.test(saveButton)) {
    throw new Error('system SaveButton must be wrapped by a blocking hit-test owner')
  }

  const toggleFavorite = methodBlock(viewer, 'toggleFavorite')
  const disappear = methodBlock(viewer, 'aboutToDisappear')
  if (!/requestEpoch\s*=\s*\+\+this\.favoriteEpoch/.test(toggleFavorite) ||
    !/!this\.active\s*\|\|\s*requestEpoch\s*!==\s*this\.favoriteEpoch/.test(toggleFavorite) ||
    !/this\.favoriteEpoch\s*\+=\s*1/.test(disappear)) {
    throw new Error('favorite callbacks must be invalidated when the viewer leaves')
  }

  const zoomableImage = requiredSource(sources, PHOTO_ZOOMABLE_IMAGE)
  if (!/PanGesture\s*\(\s*\{\s*fingers\s*:\s*1\s*\}\s*\)\s*\n\s*\.tag\s*\(\s*PHOTO_VIEWER_PAN_GESTURE_TAG\s*\)/.test(zoomableImage) ||
    !/\.onGestureRecognizerJudgeBegin\s*\([\s\S]*?current\.getTag\s*\(\s*\)\s*===\s*PHOTO_VIEWER_PAN_GESTURE_TAG[\s\S]*?zoomScale\s*<=\s*PHOTO_VIEWER_MIN_SCALE[\s\S]*?GestureJudgeResult\.REJECT/.test(zoomableImage)) {
    throw new Error('unzoomed image Pan must be rejected before it competes with the viewer Swiper')
  }
}

function validateSaveFlow(sources) {
  const saveService = requiredSource(sources, PHOTO_SAVE_SERVICE)
  if (!/MediaAssetChangeRequest\.createImageAssetRequest\s*\(/.test(saveService) ||
    !/helper\.applyChanges\s*\(\s*request\s*\)/.test(saveService)) {
    throw new Error('photo save must import through a PhotoAccessHelper change request')
  }
  const save = methodBlock(saveService, 'save')
  if (!/finally\s*\{[\s\S]*?tempFilePort\.remove\s*\(\s*filePath\s*\)/.test(save)) {
    throw new Error('photo save must always clean up its temporary file')
  }
  const errorCodeBlock = namedBlock(saveService, 'enum', 'PhotoSaveErrorCode')
  const declaredErrorCodes = new Set(Array.from(
    errorCodeBlock.matchAll(/^\s*([A-Za-z][A-Za-z0-9_]*)\s*(?:=|,|$)/gm)
  ).map((match) => match[1]))
  const viewer = requiredSource(sources, PHOTO_VIEWER_PAGE)
  for (const match of viewer.matchAll(/PhotoSaveErrorCode\.([A-Za-z][A-Za-z0-9_]*)/g)) {
    if (!declaredErrorCodes.has(match[1])) {
      throw new Error('viewer references an undeclared photo save error code: ' + match[1])
    }
  }
  const profile = requiredSource(sources, MODULE_PROFILE)
  if (/ohos\.permission\.(?:WRITE_IMAGEVIDEO|READ_IMAGEVIDEO)/.test(profile)) {
    throw new Error('photo save must not request long-term image library permissions')
  }
}

function validateMaterialGates(sources) {
  for (const [path, source] of photoSources(sources)) {
    if (/disabledSystemMaterial\s*\(/.test(source)) {
      throw new Error('API 24-25 photo paths must not construct disabled system material: ' + path)
    }
    if (/systemMaterial\s*:\s*[^\n]+\?/.test(source)) {
      throw new Error('photo material must use separate native and legacy branches: ' + path)
    }
  }

  const viewer = requiredSource(sources, PHOTO_VIEWER_PAGE)
  if (!/AppThemeMaterialRole\.InteractiveFloating/.test(viewer)) {
    throw new Error('viewer controls must use the InteractiveFloating material role')
  }
  if (!/legacyContentBuilder\s*:\s*\(\)\s*=>\s*\{[\s\S]{0,220}?viewerSurface\s*\(\s*false\s*\)/.test(viewer)) {
    throw new Error('viewer legacy destination must render the non-material surface')
  }

  const methodNames = Array.from(viewer.matchAll(/\bprivate\s+([A-Za-z][A-Za-z0-9_]*)\s*\(/g))
    .map((match) => match[1])
  let coveredMaterialUses = 0
  for (const name of new Set(methodNames)) {
    const block = methodBlock(viewer, name)
    const directMaterialUses = countMatches(block, /(?:\.systemMaterial\s*\(|\bsystemMaterial\s*:)/g)
    const modifierUses = countMatches(block, /AppThemeSurfaceResolver\.modifier\s*\(/g)
    const materialUses = directMaterialUses + modifierUses
    if (materialUses === 0) {
      continue
    }
    coveredMaterialUses += materialUses
    if (name.startsWith('legacy')) {
      throw new Error('legacy viewer branch must not construct API 26 material: ' + name)
    }
    const directMaterialGuarded = directMaterialUses === 0 || name.startsWith('native') ||
      /if\s*\(\s*this\.useNativeSurface\s*\(\s*\)\s*\)/.test(block) ||
      /systemMaterialAvailable\s*\?\s*\{[\s\S]*?systemMaterial\s*:/.test(block) ||
      /nativeSurface\s*\?\s*\{[\s\S]*?systemMaterial\s*:/.test(block)
    const safeModifierUses = countMatches(block,
      /AppThemeSurfaceResolver\.modifier\s*\([\s\S]{0,180}?(?:systemMaterialAvailable|,\s*false\s*)\s*\)/g)
    if (!directMaterialGuarded || safeModifierUses !== modifierUses) {
      throw new Error('viewer material construction must stay behind a native capability branch: ' + name)
    }
  }
  const totalMaterialUses = countMatches(viewer,
    /(?:\.systemMaterial\s*\(|\bsystemMaterial\s*:|AppThemeSurfaceResolver\.modifier\s*\()/g)
  if (coveredMaterialUses !== totalMaterialUses) {
    throw new Error('viewer material construction must be owned by an explicit guarded method')
  }
}

function validateResources(sources) {
  const allKeys = RESOURCE_PATHS.map((path) => parseResourceKeys(requiredSource(sources, path), path))
  const photoKeys = allKeys.map((keys) => new Set(Array.from(keys).filter((key) => key.startsWith('photo_'))))
  for (let index = 1; index < photoKeys.length; index += 1) {
    assertEqualSets(photoKeys[0], photoKeys[index], 'photo resource locales must have equal key sets')
  }
  for (const key of REQUIRED_PHOTO_STRING_KEYS) {
    if (!photoKeys[0].has(key)) {
      throw new Error('missing photo resource key: ' + key)
    }
  }

  const referenced = new Set()
  for (const [, source] of photoSources(sources)) {
    for (const match of source.matchAll(/app\.string\.(photo_[A-Za-z0-9_]+)/g)) {
      referenced.add(match[1])
    }
  }
  for (const key of referenced) {
    if (!photoKeys[0].has(key)) {
      throw new Error('photo source references missing resource key: ' + key)
    }
  }
}

export function validatePhotoCoreContracts(fixture) {
  const sources = fixture.sources
  validateRoutes(sources)
  validateMediaRouting(sources)
  validateEmbeddedHome(sources)
  validateViewerRoutePayload(sources)
  validateMappingAndPagination(sources)
  validateViewerStructure(sources)
  validateViewerInteractionSafety(sources)
  validateSaveFlow(sources)
  validateMaterialGates(sources)
  validateResources(sources)
}

export function defaultWorkspaceRoot() {
  return dirname(dirname(fileURLToPath(import.meta.url)))
}

function readPhotoSources(root, sources) {
  const directory = resolve(root, 'entry/src/main/ets/features/photo')
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const absolute = join(directory, entry.name)
    if (entry.isFile() && entry.name.endsWith('.ets')) {
      sources.set(relative(root, absolute), readFileSync(absolute, 'utf8'))
    }
  }
}

export function workspacePhotoCoreFixture(root = defaultWorkspaceRoot()) {
  const sources = new Map()
  for (const path of REQUIRED_SOURCE_PATHS.concat(RESOURCE_PATHS)) {
    sources.set(path, readFileSync(resolve(root, path), 'utf8'))
  }
  readPhotoSources(root, sources)
  return { sources }
}

export function validateWorkspacePhotoCoreContracts(root = defaultWorkspaceRoot()) {
  validatePhotoCoreContracts(workspacePhotoCoreFixture(root))
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  validateWorkspacePhotoCoreContracts()
  console.log('Photo core contracts verified')
}
