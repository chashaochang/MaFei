import { relationalStore } from '@kit.ArkData'
import { MediaProviderAccount } from '../../../media/account/MediaProviderAccount'
import { MediaProviderKind } from '../../../media/MediaProviderKind'
import { MediaConnectionRoute } from '../../../media/MediaProviderSession'

export const FEINIU_VIDEO_ACCOUNT_TABLE_NAME: string = 'FeiniuVideoAccount'
export const FEINIU_VIDEO_ACCOUNT_COLUMN_PROVIDER_ACCOUNT_ID: string = 'provider_account_id'
export const FEINIU_VIDEO_ACCOUNT_COLUMN_USER_ID: string = 'user_id'
export const FEINIU_VIDEO_ACCOUNT_COLUMN_DISPLAY_NAME: string = 'display_name'
export const FEINIU_VIDEO_ACCOUNT_COLUMN_SERVER_NAME: string = 'server_name'
export const FEINIU_VIDEO_ACCOUNT_COLUMN_ORIGINAL_ADDRESS: string = 'original_address'
export const FEINIU_VIDEO_ACCOUNT_COLUMN_BASE_URL: string = 'base_url'
export const FEINIU_VIDEO_ACCOUNT_COLUMN_CONNECTION_ROUTE: string = 'connection_route'
export const FEINIU_VIDEO_ACCOUNT_COLUMN_FALLBACK_BASE_URLS: string = 'fallback_base_urls'
export const FEINIU_VIDEO_ACCOUNT_COLUMN_LAST_USED_TIMESTAMP: string = 'last_used_timestamp'

export const FEINIU_VIDEO_ACCOUNT_COLUMNS: string[] = [
  FEINIU_VIDEO_ACCOUNT_COLUMN_PROVIDER_ACCOUNT_ID,
  FEINIU_VIDEO_ACCOUNT_COLUMN_USER_ID,
  FEINIU_VIDEO_ACCOUNT_COLUMN_DISPLAY_NAME,
  FEINIU_VIDEO_ACCOUNT_COLUMN_SERVER_NAME,
  FEINIU_VIDEO_ACCOUNT_COLUMN_ORIGINAL_ADDRESS,
  FEINIU_VIDEO_ACCOUNT_COLUMN_BASE_URL,
  FEINIU_VIDEO_ACCOUNT_COLUMN_CONNECTION_ROUTE,
  FEINIU_VIDEO_ACCOUNT_COLUMN_FALLBACK_BASE_URLS,
  FEINIU_VIDEO_ACCOUNT_COLUMN_LAST_USED_TIMESTAMP
]

export interface FeiniuVideoAccountRecordOptions {
  readonly providerAccountId?: string
  readonly userId: string
  readonly displayName: string
  readonly serverName: string
  readonly originalAddress: string
  readonly baseUrl: string
  readonly connectionRoute?: string
  readonly fallbackBaseUrls?: string[]
  readonly lastUsedTimestamp?: number
}

function normalizedIdentityPart(value: string): string {
  return value.trim().replace(/\/+$/, '')
}

function scopedIdentityPart(value: string): string {
  return `${value.length}:${value}`
}

function decodeFallbackBaseUrls(raw: string): string[] {
  if (raw.trim().length === 0) {
    return []
  }
  try {
    const values = JSON.parse(raw) as string[]
    return Array.isArray(values) ? values.filter((value: string): boolean =>
      typeof value === 'string' && value.trim().length > 0) : []
  } catch (_error) {
    return []
  }
}

export function createFeiniuVideoProviderAccountId(originalAddress: string, userId: string): string {
  const normalizedAddress = normalizedIdentityPart(originalAddress)
  const normalizedUserId = userId.trim()
  if (normalizedAddress.length === 0 || normalizedUserId.length === 0) {
    throw new Error('Feiniu Video account identity must include an address and userId')
  }
  return `v1|${scopedIdentityPart(normalizedAddress)}|${scopedIdentityPart(normalizedUserId)}`
}

export class FeiniuVideoAccountRecord {
  readonly providerAccountId: string
  readonly userId: string
  readonly displayName: string
  readonly serverName: string
  readonly originalAddress: string
  readonly baseUrl: string
  readonly connectionRoute: string
  readonly fallbackBaseUrls: string[]
  readonly lastUsedTimestamp: number

  constructor(options: FeiniuVideoAccountRecordOptions) {
    this.userId = options.userId.trim()
    this.displayName = options.displayName.trim()
    this.serverName = options.serverName.trim()
    this.originalAddress = options.originalAddress.trim()
    this.baseUrl = options.baseUrl.trim().replace(/\/+$/, '')
    this.connectionRoute = options.connectionRoute?.trim() || MediaConnectionRoute.Direct
    this.fallbackBaseUrls = options.fallbackBaseUrls ? options.fallbackBaseUrls.slice() : []
    this.lastUsedTimestamp = Math.max(0, Math.floor(options.lastUsedTimestamp || 0))
    this.providerAccountId = options.providerAccountId?.trim() ||
      createFeiniuVideoProviderAccountId(this.originalAddress, this.userId)
    if (this.providerAccountId.length === 0 || this.userId.length === 0 ||
      this.originalAddress.length === 0 || this.baseUrl.length === 0) {
      throw new Error('Feiniu Video account metadata is incomplete')
    }
  }

  static getCreateTableSql(): string {
    return `CREATE TABLE IF NOT EXISTS ${FEINIU_VIDEO_ACCOUNT_TABLE_NAME} (` +
      `${FEINIU_VIDEO_ACCOUNT_COLUMN_PROVIDER_ACCOUNT_ID} TEXT PRIMARY KEY NOT NULL, ` +
      `${FEINIU_VIDEO_ACCOUNT_COLUMN_USER_ID} TEXT NOT NULL, ` +
      `${FEINIU_VIDEO_ACCOUNT_COLUMN_DISPLAY_NAME} TEXT NOT NULL DEFAULT '', ` +
      `${FEINIU_VIDEO_ACCOUNT_COLUMN_SERVER_NAME} TEXT NOT NULL DEFAULT '', ` +
      `${FEINIU_VIDEO_ACCOUNT_COLUMN_ORIGINAL_ADDRESS} TEXT NOT NULL, ` +
      `${FEINIU_VIDEO_ACCOUNT_COLUMN_BASE_URL} TEXT NOT NULL, ` +
      `${FEINIU_VIDEO_ACCOUNT_COLUMN_CONNECTION_ROUTE} TEXT NOT NULL DEFAULT 'direct', ` +
      `${FEINIU_VIDEO_ACCOUNT_COLUMN_FALLBACK_BASE_URLS} TEXT NOT NULL DEFAULT '[]', ` +
      `${FEINIU_VIDEO_ACCOUNT_COLUMN_LAST_USED_TIMESTAMP} INTEGER NOT NULL DEFAULT 0)`
  }

  toValuesBucket(): relationalStore.ValuesBucket {
    return {
      provider_account_id: this.providerAccountId,
      user_id: this.userId,
      display_name: this.displayName,
      server_name: this.serverName,
      original_address: this.originalAddress,
      base_url: this.baseUrl,
      connection_route: this.connectionRoute,
      fallback_base_urls: JSON.stringify(this.fallbackBaseUrls),
      last_used_timestamp: this.lastUsedTimestamp
    }
  }

  static fromResultSet(resultSet: relationalStore.ResultSet): FeiniuVideoAccountRecord {
    return new FeiniuVideoAccountRecord({
      providerAccountId: resultSet.getString(
        resultSet.getColumnIndex(FEINIU_VIDEO_ACCOUNT_COLUMN_PROVIDER_ACCOUNT_ID)),
      userId: resultSet.getString(resultSet.getColumnIndex(FEINIU_VIDEO_ACCOUNT_COLUMN_USER_ID)),
      displayName: resultSet.getString(resultSet.getColumnIndex(FEINIU_VIDEO_ACCOUNT_COLUMN_DISPLAY_NAME)),
      serverName: resultSet.getString(resultSet.getColumnIndex(FEINIU_VIDEO_ACCOUNT_COLUMN_SERVER_NAME)),
      originalAddress: resultSet.getString(
        resultSet.getColumnIndex(FEINIU_VIDEO_ACCOUNT_COLUMN_ORIGINAL_ADDRESS)),
      baseUrl: resultSet.getString(resultSet.getColumnIndex(FEINIU_VIDEO_ACCOUNT_COLUMN_BASE_URL)),
      connectionRoute: resultSet.getString(
        resultSet.getColumnIndex(FEINIU_VIDEO_ACCOUNT_COLUMN_CONNECTION_ROUTE)),
      fallbackBaseUrls: decodeFallbackBaseUrls(resultSet.getString(
        resultSet.getColumnIndex(FEINIU_VIDEO_ACCOUNT_COLUMN_FALLBACK_BASE_URLS))),
      lastUsedTimestamp: resultSet.getLong(
        resultSet.getColumnIndex(FEINIU_VIDEO_ACCOUNT_COLUMN_LAST_USED_TIMESTAMP))
    })
  }

  toProviderAccount(): MediaProviderAccount {
    return new MediaProviderAccount({
      provider: MediaProviderKind.FeiniuVideo,
      providerAccountId: this.providerAccountId,
      userId: this.userId,
      displayName: this.displayName,
      serverName: this.serverName,
      originalAddress: this.originalAddress,
      baseUrl: this.baseUrl,
      connectionRoute: this.connectionRoute,
      fallbackBaseUrls: this.fallbackBaseUrls,
      lastUsedTimestamp: this.lastUsedTimestamp
    })
  }
}
