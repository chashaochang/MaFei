import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const SURFACE_RESOLVER = 'entry/src/main/ets/theme/AppThemeSurfaceResolver.ets'
const VIDEO_DETAIL = 'entry/src/main/ets/features/videodetail/VideoDetailPage.ets'

const OUTLINE_TARGETS = [
  {
    path: 'entry/src/main/ets/component/BaseItemCard.ets',
    widths: [0.5],
    whiteFrames: 1
  },
  {
    path: 'entry/src/main/ets/component/HorizontalVideoCard.ets',
    widths: [0.5],
    whiteFrames: 0
  },
  {
    path: 'entry/src/main/ets/component/VerticalVideoCard.ets',
    widths: [0.5],
    whiteFrames: 0
  },
  {
    path: 'entry/src/main/ets/features/home/hometab/HomeTab.ets',
    widths: [0.5],
    whiteFrames: 0
  },
  {
    path: 'entry/src/main/ets/features/videolist/VideoListPage.ets',
    widths: [0.5],
    whiteFrames: 1
  },
  {
    path: 'entry/src/main/ets/features/livetv/LiveTvChannelListPage.ets',
    widths: [0.5],
    whiteFrames: 1
  },
  {
    path: 'entry/src/main/ets/features/seasondetail/SeasonDetailPage.ets',
    widths: [0.5, 0.5, 1],
    whiteFrames: 1
  },
  {
    path: 'entry/src/main/ets/features/cast/CastDetailPage.ets',
    widths: [0.5, 1],
    whiteFrames: 0
  },
  {
    path: VIDEO_DETAIL,
    widths: [1, 1, 1, 1, 1, 1],
    whiteFrames: 0
  }
]

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

function matchCount(source, pattern) {
  return [...source.matchAll(pattern)].length
}

function outlineWidths(source) {
  const call = /AppThemeSurfaceResolver\.outlineWidth\(\s*[\w.]+\s*,\s*[\w.]+\s*,\s*(0\.5|1)\s*\)/g
  return [...source.matchAll(call)].map((match) => Number(match[1])).sort((left, right) => left - right)
}

function assertResolverContract(resolver) {
  const widthResolver = methodBlock(resolver, 'outlineWidth')
  if (!/return\s+AppThemeSurfaceResolver\.useNativeSurface\(\s*style\s*,\s*available\s*\)\s*\?\s*0\s*:\s*feiniuWidth/
    .test(widthResolver)) {
    throw new Error('outlineWidth must return zero only for an available Native surface')
  }
}

function assertSourceContract(source, target) {
  const widths = outlineWidths(source)
  const expected = [...target.widths].sort((left, right) => left - right)
  if (widths.join(',') !== expected.join(',')) {
    throw new Error(target.path + ' must preserve Feiniu outline widths through outlineWidth')
  }

  const outlineColors = matchCount(
    source,
    /AppThemeSurfaceResolver\.(?:contentOutline|mediaOutline)\s*\(/g
  )
  if (outlineColors !== widths.length) {
    throw new Error(target.path + ' has a decorative outline color without a resolved width')
  }

  if (/\.borderWidth\(\s*0\.5\s*\)|\bwidth\s*:\s*0\.5\b/.test(source)) {
    throw new Error(target.path + ' keeps a hard-coded media outline width')
  }

  const whiteFrames = matchCount(
    source,
    /\.borderWidth\(\s*1\s*\)[\s\S]{0,120}?\.borderColor\(\s*Color\.White\s*\)/g
  )
  if (whiteFrames !== target.whiteFrames) {
    throw new Error(target.path + ' must preserve semantic white label frames')
  }
}

function assertSelectedStateContracts(videoDetail) {
  if (/\.borderWidth\(\s*this\.ui\.isChasing\s*\?/.test(videoDetail) ||
    /\.borderWidth\(\s*index\s*===\s*this\.ui\.selectedMediaSourceIndex\s*\?/.test(videoDetail)) {
    throw new Error('selected Native states must not restore decorative outlines')
  }
  if (!/\.borderColor\(\s*this\.ui\.isChasing\s*\?\s*\$r\('app\.color\.color_main'\)\s*:/.test(videoDetail)) {
    throw new Error('selected chasing action must keep its semantic color')
  }
  if (!/\.borderColor\(\s*index\s*===\s*this\.ui\.selectedMediaSourceIndex\s*\?\s*\$r\('app\.color\.color_main'\)\s*:/.test(videoDetail)) {
    throw new Error('selected media source must keep its semantic color')
  }
}

export function defaultWorkspaceRoot() {
  return dirname(dirname(fileURLToPath(import.meta.url)))
}

export function validateNativeOutlineContracts(sources) {
  assertResolverContract(requiredSource(sources, SURFACE_RESOLVER))
  for (const target of OUTLINE_TARGETS) {
    assertSourceContract(requiredSource(sources, target.path), target)
  }
  assertSelectedStateContracts(requiredSource(sources, VIDEO_DETAIL))
}

export function validateWorkspace(root = defaultWorkspaceRoot()) {
  const paths = [SURFACE_RESOLVER, ...OUTLINE_TARGETS.map((target) => target.path)]
  const sources = new Map(paths.map((path) => [path, readFileSync(resolve(root, path), 'utf8')]))
  validateNativeOutlineContracts(sources)
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  validateWorkspace()
  console.log('Native outline contracts verified')
}
