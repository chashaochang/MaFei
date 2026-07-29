import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const SURFACE_RESOLVER = 'entry/src/main/ets/theme/AppThemeSurfaceResolver.ets'
const HOME_TAB = 'entry/src/main/ets/features/home/hometab/HomeTab.ets'

function requiredSource(sources, path) {
  const source = sources.get(path)
  if (source === undefined) {
    throw new Error('missing source: ' + path)
  }
  return source
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

function methodBlock(source, methodName) {
  const signature = new RegExp(
    '\\b(?:private\\s+|static\\s+|private\\s+static\\s+)?' + methodName +
      '\\s*\\([^)]*\\)\\s*(?::\\s*[^\\{]+)?\\s*\\{'
  )
  const match = signature.exec(source)
  if (!match) {
    throw new Error('missing method: ' + methodName)
  }
  return bracedBlock(source, source.indexOf('{', match.index))
}

export function defaultWorkspaceRoot() {
  return dirname(dirname(fileURLToPath(import.meta.url)))
}

export function validateNativeHomeVisualContracts(sources) {
  const resolver = requiredSource(sources, SURFACE_RESOLVER)
  const homeTab = requiredSource(sources, HOME_TAB)
  const canvas = methodBlock(resolver, 'appCanvasBackground')
  const material = methodBlock(resolver, 'material')

  if (!/effectiveTheme\s*===\s*ThemeStyle\.Native[\s\S]*comp_background_gray/.test(canvas)) {
    throw new Error('Native app canvas must use the system gray background')
  }
  if (!/app\.color\.bg_main/.test(canvas)) {
    throw new Error('Feiniu app canvas must keep bg_main')
  }
  if (!/AppThemeMaterialRole\.ContentGroup[\s\S]*materialColor\s*:\s*\$r\('sys\.color\.comp_background_primary'\)/
    .test(material)) {
    throw new Error('Native ContentGroup must use the system primary card color')
  }

  if (!/nativeMediaNavigationItem\s*\(\s*type\s*:\s*BaseItemKind\s*\)/.test(homeTab)) {
    throw new Error('Native media navigation Builder must take one stable discriminator')
  }
  if (/nativeMediaNavigationItem\s*\([^)]*\bcount\s*:/.test(homeTab) ||
    /nativeMediaNavigationItem\s*\([^)]*this\.ui\.(?:totalCount|movieCount|tvCount)/.test(homeTab)) {
    throw new Error('Reactive media counts must not be passed into a parameterized Builder')
  }

  const countResolver = methodBlock(homeTab, 'nativeMediaCount')
  for (const state of ['totalCount', 'movieCount', 'tvCount']) {
    if (!new RegExp('this\\.ui\\.' + state + '\\b').test(countResolver)) {
      throw new Error('Native media count resolver must read ' + state)
    }
  }

  const item = methodBlock(homeTab, 'nativeMediaNavigationItem')
  if (!/Text\s*\(\s*this\.nativeMediaCount\s*\(\s*type\s*\)\s*\)/.test(item) ||
    !/\.width\s*\(\s*90\s*\)[\s\S]*\.height\s*\(\s*60\s*\)/.test(item)) {
    throw new Error('Native media item must render live count state in a stable 90x60 box')
  }

  const group = methodBlock(homeTab, 'nativeAllMediaCount')
  for (const type of ['Video', 'Movie', 'TvProgram']) {
    if (!new RegExp('nativeMediaNavigationItem\\s*\\(\\s*BaseItemKind\\.' + type + '\\s*\\)').test(group)) {
      throw new Error('Native media group is missing ' + type)
    }
  }
  if (!/\.width\s*\(\s*286\s*\)[\s\S]*\.height\s*\(\s*60\s*\)/.test(group)) {
    throw new Error('Native media group must reserve a stable 286x60 layout')
  }
}

export function validateWorkspace(root = defaultWorkspaceRoot()) {
  const sources = new Map([
    [SURFACE_RESOLVER, readFileSync(resolve(root, SURFACE_RESOLVER), 'utf8')],
    [HOME_TAB, readFileSync(resolve(root, HOME_TAB), 'utf8')]
  ])
  validateNativeHomeVisualContracts(sources)
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  validateWorkspace()
  console.log('Native home visual contracts verified')
}
