import assert from 'node:assert/strict'
import test from 'node:test'
import {
  readWorkspacePlayerApiCompatSources,
  validatePlayerApiCompatContracts,
  validateWorkspacePlayerApiCompatContracts
} from './verify_player_api_compat_contracts.mjs'

function workspaceFixture() {
  return readWorkspacePlayerApiCompatSources()
}

function requireMutation(original, mutated) {
  assert.notEqual(mutated, original, 'mutation must change the source fixture')
  return mutated
}

test('workspace player API compatibility contracts pass', () => {
  assert.doesNotThrow(() => validateWorkspacePlayerApiCompatContracts())
})

test('rejects a PlayerPage build path that conditionally bypasses AppRouteDestination', () => {
  const fixture = workspaceFixture()
  fixture.playerPageSource = requireMutation(
    fixture.playerPageSource,
    fixture.playerPageSource.replace(
      '  build() {\n    AppRouteDestination({',
      '  build() {\n    if (this.usesNativeTopNavigation()) {\n      AppRouteDestination({'
    )
  )
  assert.throws(() => validatePlayerApiCompatContracts(fixture), /must not bypass the destination conditionally/)
})

test('rejects removal of the legacy route content builder', () => {
  const fixture = workspaceFixture()
  fixture.playerPageSource = requireMutation(
    fixture.playerPageSource,
    fixture.playerPageSource.replace('legacyContentBuilder:', 'legacyFallbackBuilder:')
  )
  assert.throws(() => validatePlayerApiCompatContracts(fixture), /legacy route content/)
})

test('rejects a system material call added back to an AV legacy builder', () => {
  const fixture = workspaceFixture()
  fixture.avPlayerSource = requireMutation(
    fixture.avPlayerSource,
    fixture.avPlayerSource.replace(
      '.backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)',
      '.backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)\n' +
        '    .systemMaterial(AppThemeSurfaceResolver.material(AppThemeMaterialRole.InteractiveFloating))'
    )
  )
  assert.throws(() => validatePlayerApiCompatContracts(fixture), /legacyAudioMenu must not call systemMaterial/)
})

test('rejects a system material call moved outside an MPV native builder', () => {
  const fixture = workspaceFixture()
  fixture.mpvPlayerSource = requireMutation(
    fixture.mpvPlayerSource,
    fixture.mpvPlayerSource.replace('private nativeAudioMenu()', 'private materialAudioMenu()')
  )
  assert.throws(() => validatePlayerApiCompatContracts(fixture), /material modifiers must exist only in native builders/)
})

test('rejects a native material modifier without the runtime capability gate', () => {
  const fixture = workspaceFixture()
  fixture.avPlayerSource = requireMutation(
    fixture.avPlayerSource,
    fixture.avPlayerSource.replace(
      'AppThemeMaterialRole.InteractiveFloating, true,\n      this.vm.appUIState.systemMaterialAvailable))',
      'AppThemeMaterialRole.InteractiveFloating, true))'
    )
  )
  assert.throws(() => validatePlayerApiCompatContracts(fixture), /must pass systemMaterialAvailable/)
})

test('rejects removal of the AV native portrait report action', () => {
  const fixture = workspaceFixture()
  fixture.avPlayerSource = requireMutation(
    fixture.avPlayerSource,
    fixture.avPlayerSource.replace('        this.reportControl()\n        this.castControl(0)', '        this.castControl(0)')
  )
  assert.throws(() => validatePlayerApiCompatContracts(fixture), /retain the report action/)
})

test('rejects removal of the MPV native portrait cast action', () => {
  const fixture = workspaceFixture()
  fixture.mpvPlayerSource = requireMutation(
    fixture.mpvPlayerSource,
    fixture.mpvPlayerSource.replace(
      '        this.reportActionButton()\n        this.castActionButton()',
      '        this.reportActionButton()'
    )
  )
  assert.throws(() => validatePlayerApiCompatContracts(fixture), /retain the cast action/)
})

test('rejects a PlayerPage initialization that does not await the first request', () => {
  const fixture = workspaceFixture()
  fixture.playerPageSource = requireMutation(
    fixture.playerPageSource,
    fixture.playerPageSource.replace(
      'const result = await this.vm.queueManager.initializePlaybackQueue(this.options)',
      'const result = this.vm.queueManager.initializePlaybackQueue(this.options)'
    )
  )
  assert.throws(() => validatePlayerApiCompatContracts(fixture), /must await the first playback request/)
})

test('rejects PlayerPage callbacks that bypass ViewModel failure handling', () => {
  const fixture = workspaceFixture()
  fixture.playerPageSource = requireMutation(
    fixture.playerPageSource,
    fixture.playerPageSource.replace('this.vm.playNext()', 'this.vm.queueManager.next()')
  )
  assert.throws(() => validatePlayerApiCompatContracts(fixture), /must use ViewModel failure handling/)
})

test('rejects a stale playback request reported as success', () => {
  const fixture = workspaceFixture()
  fixture.queueManagerSource = requireMutation(
    fixture.queueManagerSource,
    fixture.queueManagerSource.replace(
      'return PlaybackRequestResult.Cancelled',
      'return PlaybackRequestResult.Success'
    )
  )
  assert.throws(() => validatePlayerApiCompatContracts(fixture), /must report cancellation/)
})

test('rejects first-load playback that is not awaited', () => {
  const fixture = workspaceFixture()
  fixture.queueManagerSource = requireMutation(
    fixture.queueManagerSource,
    fixture.queueManagerSource.replace(
      'const result = await this.startPlayback(\n      itemRef,',
      'const result = this.startPlayback(\n      itemRef,'
    )
  )
  assert.throws(() => validatePlayerApiCompatContracts(fixture), /must await startPlayback/)
})

test('rejects an item request failure that cannot reach the first-load Toast', () => {
  const fixture = workspaceFixture()
  fixture.mediaSourceResolverSource = requireMutation(
    fixture.mediaSourceResolverSource,
    fixture.mediaSourceResolverSource.replace(
      'return this.provider.resolveSource({',
      'void this.provider.resolveSource({'
    )
  )
  assert.throws(() => validatePlayerApiCompatContracts(fixture), /must propagate provider item and playback-info request failures/)
})

test('rejects a neutral playback entry that forces unspecified resume to zero', () => {
  const fixture = workspaceFixture()
  fixture.commonFuncSource = requireMutation(
    fixture.commonFuncSource,
    fixture.commonFuncSource.replace(
      'options.startPositionTicks = startPositionTicks ?? null',
      'options.startPositionTicks = startPositionTicks ?? 0'
    )
  )
  assert.throws(() => validatePlayerApiCompatContracts(fixture), /must preserve an unspecified resume position/)
})

test('rejects a Jellyfin provider that ignores the server resume point', () => {
  const fixture = workspaceFixture()
  fixture.jellyfinPlaybackProviderSource = requireMutation(
    fixture.jellyfinPlaybackProviderSource,
    fixture.jellyfinPlaybackProviderSource.replace(
      'request.startTimeTicks ?? item.UserData?.PlaybackPositionTicks ?? 0',
      'request.startTimeTicks ?? 0'
    )
  )
  assert.throws(() => validatePlayerApiCompatContracts(fixture), /must restore the server resume point/)
})

test('rejects playback position zero as invalid', () => {
  const fixture = workspaceFixture()
  fixture.queueManagerSource = requireMutation(
    fixture.queueManagerSource,
    fixture.queueManagerSource.replace('position >= 0', 'position > 0')
  )
  assert.throws(() => validatePlayerApiCompatContracts(fixture), /position zero must remain valid/)
})

test('rejects a restart path that does not restore playback after failure', () => {
  const fixture = workspaceFixture()
  fixture.queueManagerSource = requireMutation(
    fixture.queueManagerSource,
    fixture.queueManagerSource.replace(
      '    this.restorePlaybackAfterFailure(result)\n    return result',
      '    return result'
    )
  )
  assert.throws(() => validatePlayerApiCompatContracts(fixture), /failed restarts must restore current playback/)
})

test('rejects removal of the playback failure Toast', () => {
  const fixture = workspaceFixture()
  fixture.playerViewModelSource = requireMutation(
    fixture.playerViewModelSource,
    fixture.playerViewModelSource.replace(
      '      ToastUtil.showToast(this.ui.context, failureMessage)',
      '      console.error(failureMessage)'
    )
  )
  assert.throws(() => validatePlayerApiCompatContracts(fixture), /must show their failure Toast/)
})

test('rejects advancing the next-item index before playback succeeds', () => {
  const fixture = workspaceFixture()
  fixture.queueManagerSource = requireMutation(
    fixture.queueManagerSource,
    fixture.queueManagerSource.replace(
      '    const targetQueueIndex = this.currentQueueIndex + 1',
      '    this.currentQueueIndex++\n    const targetQueueIndex = this.currentQueueIndex'
    )
  )
  assert.throws(() => validatePlayerApiCompatContracts(fixture), /must not mutate the queue index/)
})

test('rejects removal of queue-index rollback on load failure', () => {
  const fixture = workspaceFixture()
  fixture.queueManagerSource = requireMutation(
    fixture.queueManagerSource,
    fixture.queueManagerSource.replace(
      '        this.currentQueueIndex = previousQueueIndex',
      '        this.currentQueueIndex = targetQueueIndex ?? previousQueueIndex'
    )
  )
  assert.throws(() => validatePlayerApiCompatContracts(fixture), /must restore the queue index/)
})
