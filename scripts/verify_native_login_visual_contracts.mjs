import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const CONNECT_SCREEN = 'entry/src/main/ets/features/connect/ConnectScreen.ets'
const CONNECTING_SCREEN = 'entry/src/main/ets/features/connect/ConnectingScreen.ets'

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

function methodBlock(source, methodName) {
  const signature = new RegExp(
    '\\b(?:private\\s+)?' + methodName + '\\s*\\([^)]*\\)\\s*(?::\\s*[^\\{]+)?\\s*\\{'
  )
  const match = signature.exec(source)
  if (!match) {
    throw new Error('missing method: ' + methodName)
  }
  return bracedBlock(source, source.indexOf('{', match.index))
}

function structBlock(source, structName) {
  const signature = new RegExp('\\bstruct\\s+' + structName + '\\s*\\{')
  const match = signature.exec(source)
  if (!match) {
    throw new Error('missing struct: ' + structName)
  }
  return bracedBlock(source, source.indexOf('{', match.index))
}

export function defaultWorkspaceRoot() {
  return dirname(dirname(fileURLToPath(import.meta.url)))
}

export function validateNativeLoginVisualContracts(source) {
  const connectScreen = structBlock(source, 'ConnectScreen')
  const nativeForm = methodBlock(connectScreen, 'nativeConnectionForm')
  const build = methodBlock(connectScreen, 'build')
  const authField = structBlock(source, 'AuthTextField')
  const serverField = structBlock(source, 'ServerUrlField')
  const button = structBlock(source, 'StyledTextButton')

  if (!/AppThemeMaterialRole/.test(source)) {
    throw new Error('Native login must import the shared material roles')
  }
  if (/\.systemMaterial\s*\(|\.backgroundColor\s*\(/.test(nativeForm)) {
    throw new Error('Native login form container must stay transparent')
  }
  if (!/this\.nativeSurface\s*\?\s*52\s*:\s*56/.test(authField) ||
    !/this\.nativeSurface\s*\?\s*14\s*:\s*8/.test(authField) ||
    !/this\.nativeSurface\s*\?\s*52\s*:\s*56/.test(serverField) ||
    !/this\.nativeSurface\s*\?\s*14\s*:\s*8/.test(serverField)) {
    throw new Error('Native login fields must use 52vp height and 14vp radius')
  }
  if (!/AppThemeMaterialRole\.AdaptiveInteractive/.test(authField) ||
    !/AppThemeMaterialRole\.AdaptiveInteractive/.test(serverField)) {
    throw new Error('Native login fields must use adaptive interactive material')
  }
  if (!/@Prop\s+nativeSurface\s*:\s*boolean\s*=\s*false/.test(button) ||
    !/this\.nativeSurface\s*\?\s*24\s*:\s*8/.test(button) ||
    !/this\.nativeSurface\s*\?\s*Color\.Transparent\s*:\s*0x317aff/.test(button) ||
    !/AppThemeMaterialRole\.AdaptiveInteractive/.test(button)) {
    throw new Error('Native login action must be a material capsule while Feiniu keeps its blue button')
  }
  if (!/nativeSurface\s*:\s*nativeSurface/.test(connectScreen)) {
    throw new Error('login action must receive the active theme surface')
  }
  if (!/\$r\(\s*['"]app\.color\.native_canvas_background['"]\s*\)/.test(build)) {
    throw new Error('Native startup login must use the Native canvas background')
  }
  if (!/if\s*\(\s*this\.nativeSurface\s*\)\s*\{[\s\S]*?SymbolGlyph\s*\(\s*\$r\(\s*['"]sys\.symbol\.externaldrive['"]\s*\)/.test(serverField) ||
    !/else\s*\{[\s\S]*?Image\s*\(\s*\$r\(\s*['"]app\.media\.history['"]\s*\)/.test(serverField)) {
    throw new Error('Native server history must use a system Symbol while Feiniu keeps its bitmap')
  }
  if (!/this\.vm\.onLogin\s*\(\s*this\.isFromAddAccount\s*\)/.test(connectScreen)) {
    throw new Error('Native login refresh must preserve the login callback')
  }
  if (!/RouterConsts\.ServerDiscoveryPage/.test(connectScreen) ||
    !/RouterConsts\.AccountPage/.test(serverField)) {
    throw new Error('Native login refresh must preserve discovery and account routes')
  }
  if (!/rememberPwd/.test(connectScreen) || !/正在登录/.test(connectScreen)) {
    throw new Error('Native login refresh must preserve remember-password and loading states')
  }
  if (!/if\s*\(\s*this\.showRouteActionBar\s*\)/.test(build) ||
    !/Scroll\s*\(\s*\)/.test(build) || !/ScrollDirection\.Vertical/.test(build)) {
    throw new Error('Native login refresh must preserve route ownership and vertical scrolling')
  }
}

export function validateNativeConnectingVisualContracts(source) {
  const connectingScreen = structBlock(source, 'ConnectingScreen')
  const pageBackground = methodBlock(connectingScreen, 'pageBackground')
  const nativeContent = methodBlock(connectingScreen, 'nativeConnectingContent')
  const feiniuContent = methodBlock(connectingScreen, 'feiniuConnectingContent')
  const build = methodBlock(connectingScreen, 'build')

  if (!/this\.useNativeSurface\s*\(\s*\)\s*\?\s*\$r\(\s*['"]app\.color\.native_canvas_background['"]\s*\)/.test(pageBackground) ||
    !/AppThemeSurfaceResolver\.routeBackground/.test(pageBackground)) {
    throw new Error('connecting screen must use an opaque Native canvas and preserve Feiniu routing')
  }
  if (!/\.backgroundColor\s*\(\s*this\.pageBackground\s*\(\s*\)\s*\)/.test(build)) {
    throw new Error('connecting screen root must use its theme-aware page background')
  }
  if (!/SymbolGlyph\s*\(\s*\$r\(\s*['"]sys\.symbol\.externaldrive['"]\s*\)/.test(nativeContent) ||
    !/LoadingProgress\s*\(\s*\)/.test(nativeContent) ||
    !/AppThemeMaterialRole\.Floating/.test(nativeContent) ||
    !/AppThemeMaterialRole\.AdaptiveInteractive/.test(nativeContent)) {
    throw new Error('Native connecting content must use system status visuals and material actions')
  }
  if (/app\.media\.nas|Canvas\s*\(|LoadingDotsBlue/.test(nativeContent)) {
    throw new Error('Native connecting content must not reuse Feiniu artwork or Lottie')
  }
  if (!/this\.connectingContent\s*\(\s*\)/.test(feiniuContent) ||
    !/app\.media\.bg_zhezhao/.test(feiniuContent) ||
    !/app\.media\.nas/.test(connectingScreen) || !/LoadingDotsBlue/.test(connectingScreen)) {
    throw new Error('Feiniu connecting content must preserve its existing artwork and animation')
  }
}

export function validateWorkspace(root = defaultWorkspaceRoot()) {
  validateNativeLoginVisualContracts(readFileSync(resolve(root, CONNECT_SCREEN), 'utf8'))
  validateNativeConnectingVisualContracts(readFileSync(resolve(root, CONNECTING_SCREEN), 'utf8'))
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  validateWorkspace()
  console.log('Native login visual contracts verified')
}
