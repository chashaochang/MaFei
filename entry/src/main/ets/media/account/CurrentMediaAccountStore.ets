import { common } from '@kit.AbilityKit'
import { preferences } from '@kit.ArkData'
import { MediaProviderKind } from '../MediaProviderKind'

const STORE_NAME: string = 'MediaProviderAccountPreference'
const CURRENT_ACCOUNT_KEY: string = 'current_media_provider_account'

export interface CurrentMediaAccount {
  readonly provider: MediaProviderKind
  readonly providerAccountId: string
}

interface StoredMediaAccount {
  provider: string
  providerAccountId: string
}

export interface CurrentMediaAccountBackend {
  read(): string
  write(value: string): void
  clear(): void
  flush(): void
}

class HarmonyCurrentMediaAccountBackend implements CurrentMediaAccountBackend {
  private preference: preferences.Preferences

  constructor(preference: preferences.Preferences) {
    this.preference = preference
  }

  read(): string {
    const value = this.preference.getSync(CURRENT_ACCOUNT_KEY, '')
    return typeof value === 'string' ? value : ''
  }

  write(value: string): void {
    this.preference.putSync(CURRENT_ACCOUNT_KEY, value)
  }

  clear(): void {
    this.preference.deleteSync(CURRENT_ACCOUNT_KEY)
  }

  flush(): void {
    this.preference.flushSync()
  }
}

function parseProvider(value: string): MediaProviderKind | undefined {
  if (value === MediaProviderKind.Jellyfin) {
    return MediaProviderKind.Jellyfin
  }
  if (value === MediaProviderKind.FeiniuVideo) {
    return MediaProviderKind.FeiniuVideo
  }
  return undefined
}

export class CurrentMediaAccountStore {
  private backend: CurrentMediaAccountBackend

  constructor(backend: CurrentMediaAccountBackend) {
    this.backend = backend
  }

  static create(context: common.Context): CurrentMediaAccountStore {
    const preference = preferences.getPreferencesSync(context, { name: STORE_NAME })
    return new CurrentMediaAccountStore(new HarmonyCurrentMediaAccountBackend(preference))
  }

  load(): CurrentMediaAccount | undefined {
    try {
      const raw = this.backend.read()
      if (raw.length === 0) {
        return undefined
      }
      const stored = JSON.parse(raw) as StoredMediaAccount
      const provider = parseProvider(stored.provider)
      const providerAccountId = typeof stored.providerAccountId === 'string' ?
        stored.providerAccountId.trim() : ''
      if (!provider || providerAccountId.length === 0) {
        return undefined
      }
      return {
        provider: provider,
        providerAccountId: providerAccountId
      }
    } catch (error) {
      return undefined
    }
  }

  save(account: CurrentMediaAccount): void {
    const providerAccountId = account.providerAccountId.trim()
    if (providerAccountId.length === 0) {
      throw new Error('providerAccountId must not be empty')
    }
    const stored: StoredMediaAccount = {
      provider: account.provider,
      providerAccountId: providerAccountId
    }
    this.persist(JSON.stringify(stored))
  }

  clear(): void {
    this.persist(undefined)
  }

  private persist(next?: string): void {
    let previous: string = ''
    try {
      previous = this.backend.read()
      if (next === undefined) {
        this.backend.clear()
      } else {
        this.backend.write(next)
      }
      this.backend.flush()
    } catch (error) {
      try {
        if (previous.length === 0) {
          this.backend.clear()
        } else {
          this.backend.write(previous)
        }
        this.backend.flush()
      } catch (rollbackError) {
      }
      throw new Error('Failed to persist current media account')
    }
  }
}
