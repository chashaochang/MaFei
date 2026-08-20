import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const PLAYER_PAGE = 'entry/src/main/ets/features/player/PlayerPage.ets'
const AV_PLAYER_VIEW = 'entry/src/main/ets/features/player/AVPlayerView.ets'
const MPV_PLAYER_VIEW = 'entry/src/main/ets/features/player/MPVPlayerView.ets'
const PLAYER_VIEW_MODEL = 'entry/src/main/ets/features/player/PlayerPageViewModel.ets'
const QUEUE_MANAGER = 'entry/src/main/ets/player/queue/QueueManager.ets'
const TRACK_SELECTION_HELPER = 'entry/src/main/ets/player/TrackSelectionHelper.ets'
const PLAYBACK_REQUEST_RESULT = 'entry/src/main/ets/player/queue/PlaybackRequestResult.ets'
const MEDIA_SOURCE_RESOLVER = 'entry/src/main/ets/player/source/MediaSourceResolver.ets'
const COMMON_FUNC = 'entry/src/main/ets/common/CommonFunc.ets'
const JELLYFIN_PLAYBACK_PROVIDER = 'entry/src/main/ets/media/jellyfin/JellyfinPlaybackProvider.ets'

function requireValue(condition, message) {
  if (!condition) {
    throw new Error(message)
  }
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function countOccurrences(source, value) {
  return source.split(value).length - 1
}

function findBlockEnd(source, openingBraceIndex) {
  let depth = 0
  let quote = null
  let escaped = false
  let lineComment = false
  let blockComment = false

  for (let index = openingBraceIndex; index < source.length; index++) {
    const char = source[index]
    const next = source[index + 1]

    if (lineComment) {
      if (char === '\n') {
        lineComment = false
      }
      continue
    }
    if (blockComment) {
      if (char === '*' && next === '/') {
        blockComment = false
        index++
      }
      continue
    }
    if (quote) {
      if (escaped) {
        escaped = false
      } else if (char === '\\') {
        escaped = true
      } else if (char === quote) {
        quote = null
      }
      continue
    }
    if (char === '/' && next === '/') {
      lineComment = true
      index++
      continue
    }
    if (char === '/' && next === '*') {
      blockComment = true
      index++
      continue
    }
    if (char === '\'' || char === '"' || char === '`') {
      quote = char
      continue
    }
    if (char === '{') {
      depth++
    } else if (char === '}') {
      depth--
      if (depth === 0) {
        return index + 1
      }
    }
  }
  return -1
}

export function extractMethod(source, methodName) {
  const methodPattern = new RegExp(
    `(?:@Builder\\s*)?(?:private\\s+)?(?:async\\s+)?${escapeRegExp(methodName)}\\s*\\([^)]*\\)\\s*(?::\\s*[^\\{]+)?\\{`,
    'm'
  )
  const match = methodPattern.exec(source)
  requireValue(match, `missing method ${methodName}`)
  const openingBraceIndex = source.indexOf('{', match.index + match[0].lastIndexOf('{'))
  const endIndex = findBlockEnd(source, openingBraceIndex)
  requireValue(endIndex > openingBraceIndex, `unterminated method ${methodName}`)
  return source.slice(match.index, endIndex)
}

function builderNames(source) {
  return Array.from(
    source.matchAll(/@Builder\s+(?:private\s+)?([A-Za-z_$][\w$]*)\s*\(/g),
    (match) => match[1]
  )
}

function validatePlayerDestination(playerPageSource) {
  requireValue(
    /@HMRouter\(\{[\s\S]*?useNavDst\s*:\s*true[\s\S]*?\}\)/.test(playerPageSource),
    'PlayerPage must remain a NavDestination route'
  )
  const buildSource = extractMethod(playerPageSource, 'build')
  requireValue(
    countOccurrences(buildSource, 'AppRouteDestination({') === 1,
    'PlayerPage.build must create exactly one AppRouteDestination'
  )
  requireValue(!/\bif\s*\(/.test(buildSource), 'PlayerPage.build must not bypass the destination conditionally')
  requireValue(/contentBuilder\s*:/.test(buildSource), 'PlayerPage must provide native route content')
  requireValue(/legacyContentBuilder\s*:/.test(buildSource), 'PlayerPage must provide legacy route content')
  requireValue(
    countOccurrences(buildSource, 'this.playerContent()') === 2,
    'both PlayerPage route builders must render the same player content'
  )
  requireValue(
    /title\s*:\s*this\.usesNativeTopNavigation\(\)\s*\?\s*''\s*:\s*this\.ui\.title/.test(buildSource),
    'native player title must be owned by the player overlay to avoid duplicate titles'
  )
}

function validateMaterialBuilders(source, label) {
  requireValue(!source.includes('disabledSystemMaterial'), `${label} must not construct disabled system material`)
  requireValue(!source.includes('Material.empty'), `${label} must not reference Material.empty`)

  const builders = builderNames(source).map((name) => ({
    name,
    source: extractMethod(source, name)
  }))
  const legacyBuilders = builders.filter((builder) => builder.name.startsWith('legacy'))
  requireValue(legacyBuilders.length >= 5, `${label} must keep explicit legacy menu and control builders`)
  for (const builder of legacyBuilders) {
    requireValue(
      !builder.source.includes('.systemMaterial('),
      `${label}.${builder.name} must not call systemMaterial`
    )
    requireValue(
      !builder.source.includes('AppThemeSurfaceResolver.modifier('),
      `${label}.${builder.name} must not apply a native material modifier`
    )
  }

  const nativeMaterialCount = builders
    .filter((builder) => builder.name.startsWith('native'))
    .reduce((count, builder) => {
      const modifierCount = countOccurrences(builder.source, 'AppThemeSurfaceResolver.modifier(')
      if (modifierCount > 0) {
        requireValue(
          countOccurrences(builder.source, 'this.vm.appUIState.systemMaterialAvailable') >= modifierCount,
          `${label}.${builder.name} must pass systemMaterialAvailable to every native material modifier`
        )
      }
      return count + modifierCount
    }, 0)
  const totalMaterialCount = countOccurrences(source, 'AppThemeSurfaceResolver.modifier(')
  requireValue(totalMaterialCount > 0, `${label} must retain native material modifiers`)
  requireValue(
    nativeMaterialCount === totalMaterialCount,
    `${label} material modifiers must exist only in native builders`
  )
  requireValue(
    countOccurrences(source, '.systemMaterial(') === 0,
    `${label} must route native material through the capability-aware modifier`
  )

  const names = new Set(builders.map((builder) => builder.name))
  requireValue(
    Array.from(names).some((name) => name.startsWith('native') && name.includes('AudioMenu')) &&
      Array.from(names).some((name) => name.startsWith('legacy') && name.includes('AudioMenu')),
    `${label} must split the audio menu into native and legacy builders`
  )
  requireValue(
    Array.from(names).some((name) => name.startsWith('native') && name.includes('SubtitleMenu')) &&
      Array.from(names).some((name) => name.startsWith('legacy') && name.includes('SubtitleMenu')),
    `${label} must split the subtitle menu into native and legacy builders`
  )
  requireValue(
    Array.from(names).some((name) => name.startsWith('native') && name.includes('QualityMenu')) &&
      Array.from(names).some((name) => name.startsWith('legacy') && name.includes('QualityMenu')),
    `${label} must split the quality menu into native and legacy builders`
  )
  requireValue(
    Array.from(names).some((name) => name.startsWith('native') && name.includes('TopControl')) &&
      Array.from(names).some((name) => name.startsWith('legacy') && name.includes('TopControl')),
    `${label} must split the top controls into native and legacy builders`
  )
  requireValue(
    Array.from(names).some((name) => name.startsWith('native') && name.includes('BottomControl')) &&
      Array.from(names).some((name) => name.startsWith('legacy') && name.includes('BottomControl')),
    `${label} must split the bottom controls into native and legacy builders`
  )
}

function validateNativePortraitActions(source, label) {
  const portraitBuilderName = builderNames(source).find((name) => name.startsWith('nativePortrait'))
  requireValue(portraitBuilderName, `${label} must provide a native portrait top action builder`)
  const portraitSource = extractMethod(source, portraitBuilderName)
  requireValue(/Text\(this\.ui\.title\)/.test(portraitSource), `${label} native portrait layer must show the title`)
  requireValue(portraitSource.includes('TextOverflow.MARQUEE'), `${label} native portrait title must marquee`)
  requireValue(
    portraitSource.includes("calc(100% - 88vp)"),
    `${label} native portrait layer must reserve back and action widths without overflowing`
  )
  requireValue(
    portraitSource.includes('AppThemeSurfaceResolver.modifier(') &&
      portraitSource.includes('this.vm.appUIState.systemMaterialAvailable'),
    `${label} native portrait action group must use native material`
  )

  const reportCall = /this\.(report[A-Za-z_$][\w$]*)\s*\(/.exec(portraitSource)
  const castCall = /this\.(cast[A-Za-z_$][\w$]*)\s*\(/.exec(portraitSource)
  requireValue(reportCall, `${label} native portrait layer must retain the report action`)
  requireValue(castCall, `${label} native portrait layer must retain the cast action`)
  requireValue(
    extractMethod(source, reportCall[1]).includes('showReportComposer'),
    `${label} report action must open the report composer`
  )
  requireValue(
    extractMethod(source, castCall[1]).includes('AVCastPicker'),
    `${label} cast action must retain AVCastPicker`
  )
  requireValue(
    countOccurrences(source, `this.${portraitBuilderName}()`) >= 1,
    `${label} must render the native portrait action layer`
  )
}

function validateAsyncPlayerEntry(playerPageSource) {
  const initializeSource = extractMethod(playerPageSource, 'initializePlayback')
  requireValue(
    /async\s+initializePlayback\s*\([^)]*\)\s*:\s*Promise<void>/.test(initializeSource),
    'PlayerPage initialization must be asynchronous'
  )
  requireValue(
    /await\s+this\.vm\.queueManager\.initializePlaybackQueue\s*\(/.test(initializeSource),
    'PlayerPage must await the first playback request'
  )
  requireValue(
    initializeSource.includes('case PlayerException.NetworkFailure:') &&
      initializeSource.includes("ToastUtil.showToast(this.getUIContext(), '无法从服务器加载媒体信息')"),
    'PlayerPage must surface first-load network failures'
  )

  const aboutToAppearSource = extractMethod(playerPageSource, 'aboutToAppear')
  const menuIndex = aboutToAppearSource.indexOf('this.vm.ui.playerMenus = new PlayerMenus()')
  const initializeIndex = aboutToAppearSource.indexOf('this.initializePlayback()')
  requireValue(menuIndex >= 0, 'PlayerPage must create playback menus')
  requireValue(initializeIndex > menuIndex, 'PlayerPage must create menus before asynchronous playback initialization')
  requireValue(
    !aboutToAppearSource.includes('initializePlaybackQueue('),
    'PlayerPage.aboutToAppear must not treat queue initialization as a synchronous result'
  )

  const playerContentSource = extractMethod(playerPageSource, 'playerContent')
  requireValue(
    !playerContentSource.includes('this.vm.queueManager.') &&
      !playerContentSource.includes('this.vm.trackSelectionHelper.'),
    'Player callbacks must use ViewModel failure handling'
  )
  requireValue(countOccurrences(playerContentSource, 'this.vm.playNext()') === 2,
    'both player engines must route next actions through PlayerPageViewModel')
  requireValue(countOccurrences(playerContentSource, 'this.vm.selectAudioTrack(stream)') === 2,
    'both player engines must route audio changes through PlayerPageViewModel')
  requireValue(countOccurrences(playerContentSource, 'this.vm.selectSubtitleTrack(stream)') === 2,
    'both player engines must route subtitle changes through PlayerPageViewModel')
}

function validatePlaybackResultContract(queueManagerSource, playbackRequestResultSource, mediaSourceResolverSource) {
  for (const member of ['Success', 'Failure', 'Cancelled']) {
    requireValue(
      new RegExp(`\\b${member}\\b`).test(playbackRequestResultSource),
      `PlaybackRequestResult must define ${member}`
    )
  }

  const startPlaybackSource = extractMethod(queueManagerSource, 'startPlayback')
  requireValue(
    /async\s+startPlayback\s*\([^)]*\)\s*:\s*Promise<PlaybackRequestResult>/.test(startPlaybackSource),
    'QueueManager.startPlayback must expose the three-state result'
  )
  requireValue(
    countOccurrences(startPlaybackSource, 'return PlaybackRequestResult.Cancelled') === 3,
    'stale playback requests must report cancellation at every request-age check'
  )
  requireValue(startPlaybackSource.includes('return PlaybackRequestResult.Success'),
    'successful playback requests must report success')
  requireValue(startPlaybackSource.includes('return PlaybackRequestResult.Failure'),
    'failed playback requests must report failure')

  const initializeSource = extractMethod(queueManagerSource, 'initializePlaybackQueue')
  requireValue(
    /async\s+initializePlaybackQueue\s*\([^)]*\)\s*:\s*Promise<PlayerException\s*\|\s*null>/.test(initializeSource),
    'QueueManager.initializePlaybackQueue must wait for the first request'
  )
  requireValue(/const\s+result\s*=\s*await\s+this\.startPlayback\s*\(/.test(initializeSource),
    'QueueManager.initializePlaybackQueue must await startPlayback')
  requireValue(
    /result\s*===\s*PlaybackRequestResult\.Failure/.test(initializeSource) &&
      initializeSource.includes('return PlayerException.NetworkFailure'),
    'first-load failures must propagate as NetworkFailure'
  )

  const resolverSource = extractMethod(mediaSourceResolverSource, 'resolveMediaSource')
  requireValue(
    resolverSource.includes('return this.provider.resolveSource({') &&
      !resolverSource.includes('.catch('),
    'MediaSourceResolver must propagate provider item and playback-info request failures'
  )
}

function validateProviderResumeContract(commonFuncSource, mediaSourceResolverSource,
  jellyfinPlaybackProviderSource) {
  const entrySource = extractMethod(commonFuncSource, 'playMediaRefs')
  requireValue(
    entrySource.includes('options.startPositionTicks = startPositionTicks ?? null'),
    'neutral playback entry must preserve an unspecified resume position'
  )

  const resolverSource = extractMethod(mediaSourceResolverSource, 'resolveMediaSource')
  requireValue(
    resolverSource.includes('startTimeTicks: startTimeTicks ?? undefined'),
    'MediaSourceResolver must distinguish unspecified resume from explicit zero'
  )

  const providerSource = extractMethod(jellyfinPlaybackProviderSource, 'resolveSource')
  requireValue(
    /const\s+effectiveStartTicks\s*=\s*request\.startTimeTicks\s*\?\?\s*item\.UserData\?\.PlaybackPositionTicks\s*\?\?\s*0/.test(providerSource),
    'Jellyfin playback must restore the server resume point when no position is specified'
  )
  requireValue(
    countOccurrences(providerSource, 'effectiveStartTicks') >= 3,
    'Jellyfin playback must use the effective resume point for negotiation and the resolved source'
  )
}

function validatePlaybackPositionAndRecovery(queueManagerSource, playerViewModelSource, trackSelectionSource) {
  const positionValidatorSource = extractMethod(queueManagerSource, 'isValidPlaybackPosition')
  requireValue(positionValidatorSource.includes('Number.isFinite(position)'),
    'playback positions must reject non-finite values')
  requireValue(/position\s*>=\s*0/.test(positionValidatorSource),
    'playback position zero must remain valid')

  const pauseSource = extractMethod(playerViewModelSource, 'getPositionAndPause')
  requireValue(/position\s*<\s*0/.test(pauseSource) && !/position\s*<=\s*0/.test(pauseSource),
    'PlayerPageViewModel must pause at position zero')
  requireValue(pauseSource.includes("'eventName': 'Pause'"),
    'valid restart requests must pause current playback')

  const restartSource = extractMethod(queueManagerSource, 'restartCurrentPlayback')
  requireValue(/const\s+result\s*=\s*await\s+this\.startPlayback\s*\(/.test(restartSource),
    'restart requests must await startPlayback')
  requireValue(restartSource.includes('this.restorePlaybackAfterFailure(result)'),
    'failed restarts must restore current playback')

  const restoreSource = extractMethod(queueManagerSource, 'restorePlaybackAfterFailure')
  requireValue(
    /result\s*===\s*PlaybackRequestResult\.Failure/.test(restoreSource) &&
      restoreSource.includes('this.viewModel.resumePlaybackAfterFailedRequest()'),
    'only failed restart requests must resume the previous playback'
  )
  const resumeSource = extractMethod(playerViewModelSource, 'resumePlaybackAfterFailedRequest')
  requireValue(resumeSource.includes("'eventName': 'Resume'"),
    'failed restarts must emit Resume')

  const resultHandlerSource = extractMethod(playerViewModelSource, 'handlePlaybackRequestResult')
  requireValue(
    /result\s*===\s*PlaybackRequestResult\.Failure/.test(resultHandlerSource) &&
      resultHandlerSource.includes('ToastUtil.showToast(this.ui.context, failureMessage)'),
    'failed playback requests must show their failure Toast'
  )
  requireValue(
    !/result\s*===\s*PlaybackRequestResult\.Cancelled[\s\S]*ToastUtil\.showToast/.test(resultHandlerSource),
    'cancelled playback requests must not be reported as failures'
  )

  for (const methodName of ['changeBitrate', 'selectAudioStreamAndRestartPlayback',
    'selectSubtitleStreamAndRestartPlayback']) {
    const methodSource = extractMethod(queueManagerSource, methodName)
    requireValue(/Promise<PlaybackRequestResult>/.test(methodSource),
      `QueueManager.${methodName} must preserve the three-state result`)
    requireValue(methodSource.includes('restartCurrentPlayback('),
      `QueueManager.${methodName} must use the recoverable restart path`)
  }

  for (const methodName of ['selectAudioTrack', 'selectSubtitleTrack']) {
    const methodSource = extractMethod(trackSelectionSource, methodName)
    requireValue(/Promise<PlaybackRequestResult>/.test(methodSource),
      `TrackSelectionHelper.${methodName} must preserve the three-state result`)
  }
}

function validateQueueIndexTransaction(queueManagerSource) {
  const startPlaybackSource = extractMethod(queueManagerSource, 'startPlayback')
  requireValue(startPlaybackSource.includes('const previousQueueIndex = this.currentQueueIndex'),
    'startPlayback must snapshot the previous queue index')
  requireValue(startPlaybackSource.includes('this.currentQueueIndex = previousQueueIndex'),
    'startPlayback must restore the queue index when loading fails')
  const prepareIndex = startPlaybackSource.indexOf('await this.prepareStreams(')
  const commitIndex = startPlaybackSource.indexOf('this.currentQueueIndex = targetQueueIndex')
  requireValue(prepareIndex >= 0 && commitIndex > prepareIndex,
    'startPlayback must commit the target queue index only after stream preparation succeeds')

  const methodContracts = [
    ['previous', 'this.currentQueueIndex - 1'],
    ['next', 'this.currentQueueIndex + 1'],
    ['changeEpisode', 'index']
  ]
  for (const [methodName, targetExpression] of methodContracts) {
    const methodSource = extractMethod(queueManagerSource, methodName)
    requireValue(!/this\.currentQueueIndex\s*(?:\+\+|--|[+\-]?=)/.test(methodSource),
      `QueueManager.${methodName} must not mutate the queue index before playback succeeds`)
    requireValue(methodSource.includes(`const targetQueueIndex = ${targetExpression}`) ||
      (methodName === 'changeEpisode' && /this\.startPlayback\([\s\S]*?\bindex\s*\)/.test(methodSource)),
    `QueueManager.${methodName} must pass a target index into startPlayback`)
    requireValue(methodSource.includes('this.restorePlaybackAfterFailure(result)'),
      `QueueManager.${methodName} must recover the active item after a failed request`)
  }
}

function validateViewModelPlaybackActions(playerViewModelSource) {
  for (const methodName of ['changeBitrate', 'selectAudioTrack', 'selectSubtitleTrack',
    'playNext', 'playPrevious', 'changeEpisode']) {
    const methodSource = extractMethod(playerViewModelSource, methodName)
    requireValue(/Promise<boolean>/.test(methodSource),
      `PlayerPageViewModel.${methodName} must await and report the playback result`)
    requireValue(methodSource.includes('this.handlePlaybackRequestResult(result,'),
      `PlayerPageViewModel.${methodName} must use unified failure handling`)
  }
}

function validatePlayerStateSynchronization(playerPageSource, playerViewModelSource) {
  const invalidMonitors = [
    "@Monitor('vm.queueManager.currentMediaSourceOrNull')",
    "@Monitor('mediaSourceOrNull')",
    "@Monitor('mediaSourceOrNull._selectedAudioStream')",
    "@Monitor('mediaSourceOrNull._selectedSubtitleStream')",
    "@Monitor('uiState.CONTROL_PlayStatus')"
  ]
  for (const monitor of invalidMonitors) {
    requireValue(
      !playerPageSource.includes(monitor) && !playerViewModelSource.includes(monitor),
      `player state synchronization must not use invalid nested monitor ${monitor}`
    )
  }

  requireValue(playerViewModelSource.includes('this.syncMediaSourceState(source)'),
    'PlayerPageViewModel.load must synchronize the resolved media source explicitly')
  requireValue(playerViewModelSource.includes('this.mediaSourceAppliedCallback?.(source)'),
    'PlayerPageViewModel.load must notify the page after applying the media source')

  const aboutToAppearSource = extractMethod(playerPageSource, 'aboutToAppear')
  const callbackIndex = aboutToAppearSource.indexOf('this.vm.setMediaSourceAppliedCallback(')
  const initializeIndex = aboutToAppearSource.indexOf('this.initializePlayback()')
  requireValue(callbackIndex >= 0 && initializeIndex > callbackIndex,
    'PlayerPage must register media-source callbacks before playback initialization')
}

export function validatePlayerApiCompatContracts(sources) {
  validatePlayerDestination(sources.playerPageSource)
  validateMaterialBuilders(sources.avPlayerSource, 'AVPlayerView')
  validateMaterialBuilders(sources.mpvPlayerSource, 'MPVPlayerView')
  validateNativePortraitActions(sources.avPlayerSource, 'AVPlayerView')
  validateNativePortraitActions(sources.mpvPlayerSource, 'MPVPlayerView')
  validateAsyncPlayerEntry(sources.playerPageSource)
  validatePlaybackResultContract(
    sources.queueManagerSource,
    sources.playbackRequestResultSource,
    sources.mediaSourceResolverSource
  )
  validateProviderResumeContract(
    sources.commonFuncSource,
    sources.mediaSourceResolverSource,
    sources.jellyfinPlaybackProviderSource
  )
  validatePlaybackPositionAndRecovery(
    sources.queueManagerSource,
    sources.playerViewModelSource,
    sources.trackSelectionSource
  )
  validateQueueIndexTransaction(sources.queueManagerSource)
  validateViewModelPlaybackActions(sources.playerViewModelSource)
  validatePlayerStateSynchronization(sources.playerPageSource, sources.playerViewModelSource)
}

export function defaultWorkspaceRoot() {
  return resolve(dirname(fileURLToPath(import.meta.url)), '..')
}

export function readWorkspacePlayerApiCompatSources(workspaceRoot = defaultWorkspaceRoot()) {
  return {
    playerPageSource: readFileSync(resolve(workspaceRoot, PLAYER_PAGE), 'utf8'),
    avPlayerSource: readFileSync(resolve(workspaceRoot, AV_PLAYER_VIEW), 'utf8'),
    mpvPlayerSource: readFileSync(resolve(workspaceRoot, MPV_PLAYER_VIEW), 'utf8'),
    playerViewModelSource: readFileSync(resolve(workspaceRoot, PLAYER_VIEW_MODEL), 'utf8'),
    queueManagerSource: readFileSync(resolve(workspaceRoot, QUEUE_MANAGER), 'utf8'),
    trackSelectionSource: readFileSync(resolve(workspaceRoot, TRACK_SELECTION_HELPER), 'utf8'),
    playbackRequestResultSource: readFileSync(resolve(workspaceRoot, PLAYBACK_REQUEST_RESULT), 'utf8'),
    mediaSourceResolverSource: readFileSync(resolve(workspaceRoot, MEDIA_SOURCE_RESOLVER), 'utf8'),
    commonFuncSource: readFileSync(resolve(workspaceRoot, COMMON_FUNC), 'utf8'),
    jellyfinPlaybackProviderSource: readFileSync(resolve(workspaceRoot, JELLYFIN_PLAYBACK_PROVIDER), 'utf8')
  }
}

export function validateWorkspacePlayerApiCompatContracts(workspaceRoot = defaultWorkspaceRoot()) {
  validatePlayerApiCompatContracts(readWorkspacePlayerApiCompatSources(workspaceRoot))
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  validateWorkspacePlayerApiCompatContracts()
  console.log('Player API compatibility contracts passed.')
}
