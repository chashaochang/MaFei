import assert from 'node:assert/strict'
import { dirname, resolve } from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'
import {
  defaultWorkspaceRoot,
  validateMediaProviderStartupFailOpen,
  validateWorkspace
} from './verify_media_provider_startup_fail_open.mjs'

const entryPath = 'entry/src/main/ets/entryability/EntryAbility.ets'
const compositionPath = 'entry/src/main/ets/features/setting/account/MediaProviderComposition.ets'
const coordinatorPath = 'entry/src/main/ets/features/setting/account/MediaAccountSessionCoordinator.ets'
const accountViewModelPath = 'entry/src/main/ets/features/setting/account/AccountViewModel.ets'

function validSources() {
  return new Map([
    [entryPath, `
      class EntryAbility {
        private async initializeApplication(): Promise<void> {
          await DBManager.getInstance().init(this.context)
          const providerComposition = await MediaProviderComposition.initialize(this.context)
          if (!providerComposition.isFeiniuAvailable()) {
            log('continuing with Jellyfin')
          }
          await ImageKnife.getInstance().initFileCache()
        }
      }
    `],
    [compositionPath, `
      class MediaProviderComposition {
        static async initialize(context: Context): Promise<MediaProviderComposition> {
          const composition = await MediaProviderComposition.create(context)
            .catch((error: Error): MediaProviderComposition => {
              return MediaProviderComposition.createUnavailable(context, error)
            })
          if (composition.isFeiniuAvailable()) {
            MediaProviderComposition.shared = composition
          }
          return composition
        }
        private static createUnavailable(context: Context,
          error: Error): MediaProviderComposition {
          const coordinator = MediaAccountSessionCoordinator.createWithPorts(
            context, new UnavailableFeiniuVideoSessionPort(error), registry)
          return new MediaProviderComposition(undefined, coordinator, error)
        }
        async loginFeiniu(): Promise<void> {
          const sessionService = this.sessionService
          if (!sessionService) {
            if (this.initializationFailure) { throw this.initializationFailure }
            throw new Error('unavailable')
          }
        }
      }
    `],
    [coordinatorPath, `
      class MediaAccountSessionCoordinator {
        static createWithPorts(context: Context): MediaAccountSessionCoordinator {
          return new MediaAccountSessionCoordinator(
            CurrentMediaAccountStore.create(context), registry, port, contexts,
            new JellyfinActiveSessionSource(),
            MediaProviderSelectionHistoryStore.create(context))
        }
      }
    `],
    [accountViewModelPath, `
      class AccountViewModel {
        async getAccountList(): Promise<void> {
          const jellyfinAccounts = this.jellyfinRepository.listAccounts()
          if (composition.isFeiniuAvailable()) {
            await FeiniuVideoAccountStore.create(hostContext)
          }
        }
      }
    `]
  ])
}

test('accepts a Jellyfin-capable fallback after Feiniu factory failure', () => {
  assert.doesNotThrow(() => validateMediaProviderStartupFailOpen(validSources()))
})

test('derives the workspace root from the verifier location', () => {
  const scriptsDirectory = dirname(fileURLToPath(import.meta.url))
  assert.equal(defaultWorkspaceRoot(), resolve(scriptsDirectory, '..'))
})

test('validates the current workspace startup boundary', () => {
  assert.doesNotThrow(() => validateWorkspace())
})

test('rejects a throwing composition without a fallback factory', () => {
  const sources = validSources()
  sources.set(compositionPath, sources.get(compositionPath)
    .replace('.catch((error: Error): MediaProviderComposition => {', '.then((error: Error) => {'))
  assert.throws(
    () => validateMediaProviderStartupFailOpen(sources),
    /fall back/
  )
})

test('rejects a fallback that cannot mirror Jellyfin', () => {
  const sources = validSources()
  sources.set(coordinatorPath, sources.get(coordinatorPath)
    .replace('new JellyfinActiveSessionSource()', 'undefined'))
  assert.throws(
    () => validateMediaProviderStartupFailOpen(sources),
    /Jellyfin mirroring/
  )
})

test('rejects optional Feiniu storage opened before Jellyfin accounts', () => {
  const sources = validSources()
  sources.set(accountViewModelPath, sources.get(accountViewModelPath)
    .replace(
      'const jellyfinAccounts = this.jellyfinRepository.listAccounts()\n          if (composition.isFeiniuAvailable()) {',
      'if (composition.isFeiniuAvailable()) {\n            await FeiniuVideoAccountStore.create(hostContext)\n          }\n          const jellyfinAccounts = this.jellyfinRepository.listAccounts()\n          if (false) {'
    ))
  assert.throws(
    () => validateMediaProviderStartupFailOpen(sources),
    /Jellyfin accounts must remain available/
  )
})

test('rejects any unguarded second Feiniu store initialization', () => {
  const sources = validSources()
  sources.set(accountViewModelPath, sources.get(accountViewModelPath)
    .replace(
      'const jellyfinAccounts = this.jellyfinRepository.listAccounts()',
      'await FeiniuVideoAccountStore.create(hostContext)\n' +
      '          const jellyfinAccounts = this.jellyfinRepository.listAccounts()'
    ))
  assert.throws(
    () => validateMediaProviderStartupFailOpen(sources),
    /outside the availability guard/
  )
})
