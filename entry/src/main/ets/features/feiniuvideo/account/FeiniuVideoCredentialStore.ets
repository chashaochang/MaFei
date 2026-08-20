import { PreferencesUtil } from '../upstream/common/PreferencesUtil'

const CREDENTIAL_ALIAS_PREFIX: string = 'mafei.feiniu.video.credentials.v1:'
const CREDENTIAL_FORMAT_VERSION: number = 1
const MEDIA_TOKEN_COOKIE: string = 'Trim-MC-token'

export interface FeiniuVideoCredentials {
  readonly token: string
  readonly cookie: string
}

interface StoredFeiniuVideoCredentials {
  version: number
  token: string
  cookie: string
}

export interface FeiniuVideoCredentialBackend {
  read(alias: string): string
  write(alias: string, value: string): void
  remove(alias: string): void
}

class PreferencesFeiniuVideoCredentialBackend implements FeiniuVideoCredentialBackend {
  read(alias: string): string {
    return PreferencesUtil.getStringSync(alias)
  }

  write(alias: string, value: string): void {
    PreferencesUtil.putSync(alias, value)
  }

  remove(alias: string): void {
    PreferencesUtil.putSync(alias, '')
  }
}

function normalizedProviderAccountId(providerAccountId: string): string {
  const normalized = providerAccountId.trim()
  if (normalized.length === 0) {
    throw new Error('providerAccountId must not be empty')
  }
  return normalized
}

export function createFeiniuVideoCredentialAlias(providerAccountId: string): string {
  return `${CREDENTIAL_ALIAS_PREFIX}${normalizedProviderAccountId(providerAccountId)}`
}

export function extractFeiniuVideoTokenFromCookie(cookie: string): string {
  const segments = cookie.split(';')
  for (let index = 0; index < segments.length; index += 1) {
    const segment = segments[index].trim()
    const separator = segment.indexOf('=')
    if (separator <= 0) {
      continue
    }
    const name = segment.substring(0, separator).trim()
    if (name === MEDIA_TOKEN_COOKIE) {
      return segment.substring(separator + 1).trim()
    }
  }
  return ''
}

export function mergeFeiniuVideoCookie(cookie: string, token: string): string {
  const pairs: Map<string, string> = new Map()
  const segments = cookie.split(';')
  for (let index = 0; index < segments.length; index += 1) {
    const segment = segments[index].trim()
    const separator = segment.indexOf('=')
    if (separator <= 0) {
      continue
    }
    const name = segment.substring(0, separator).trim()
    const value = segment.substring(separator + 1).trim()
    if (name.length > 0) {
      pairs.set(name, value)
    }
  }
  const normalizedToken = token.trim()
  if (normalizedToken.length > 0) {
    pairs.set(MEDIA_TOKEN_COOKIE, normalizedToken)
  }
  const normalizedPairs: string[] = []
  pairs.forEach((value: string, name: string) => {
    normalizedPairs.push(`${name}=${value}`)
  })
  return normalizedPairs.join('; ')
}

export class FeiniuVideoCredentialStore {
  private backend: FeiniuVideoCredentialBackend

  constructor(backend: FeiniuVideoCredentialBackend) {
    this.backend = backend
  }

  static create(): FeiniuVideoCredentialStore {
    return new FeiniuVideoCredentialStore(new PreferencesFeiniuVideoCredentialBackend())
  }

  load(providerAccountId: string): FeiniuVideoCredentials | undefined {
    const raw = this.backend.read(createFeiniuVideoCredentialAlias(providerAccountId))
    if (raw.length === 0) {
      return undefined
    }
    try {
      const stored = JSON.parse(raw) as StoredFeiniuVideoCredentials
      if (stored.version !== CREDENTIAL_FORMAT_VERSION ||
        typeof stored.token !== 'string' || typeof stored.cookie !== 'string') {
        return undefined
      }
      const cookie = stored.cookie.trim()
      const token = stored.token.trim() || extractFeiniuVideoTokenFromCookie(cookie)
      if (token.length === 0 && cookie.length === 0) {
        return undefined
      }
      return {
        token: token,
        cookie: mergeFeiniuVideoCookie(cookie, token)
      }
    } catch (_error) {
      return undefined
    }
  }

  save(providerAccountId: string, credentials: FeiniuVideoCredentials): void {
    const alias = createFeiniuVideoCredentialAlias(providerAccountId)
    const sourceCookie = credentials.cookie.trim()
    const token = credentials.token.trim() || extractFeiniuVideoTokenFromCookie(sourceCookie)
    const cookie = mergeFeiniuVideoCookie(sourceCookie, token)
    if (token.length === 0 && cookie.length === 0) {
      this.backend.remove(alias)
      return
    }
    const stored: StoredFeiniuVideoCredentials = {
      version: CREDENTIAL_FORMAT_VERSION,
      token: token,
      cookie: cookie
    }
    this.backend.write(alias, JSON.stringify(stored))
  }

  clear(providerAccountId: string): void {
    this.backend.remove(createFeiniuVideoCredentialAlias(providerAccountId))
  }
}
