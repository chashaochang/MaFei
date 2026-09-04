import { common } from '@kit.AbilityKit'
import { asset } from '@kit.AssetStoreKit'
import { util } from '@kit.ArkTS'
import { HMRouterMgr } from '@hadss/hmrouter'
import axios, { AxiosResponse } from '@ohos/axios'
import jellyfin from 'sdk/src/main/ets/jellyfin'
import { Api } from 'sdk/src/main/ets/api'
import { AuthenticationResult } from 'sdk/src/main/ets/generated-client/models'
import { getSystemApi } from 'sdk/src/main/ets/utils/api/system-api'
import { getUserApi } from 'sdk/src/main/ets/utils/api/user-api'
import { BaseViewModel } from '../../../common/base/BaseViewModel'
import { stringToArray } from '../../../common/CommonFunc'
import { RouterConsts } from '../../../common/RouterConsts'
import { ApiClientController } from '../../../data/ApiClientController'
import { AppPreference } from '../../../data/AppPreference'
import { DBManager } from '../../../data/DBManager'
import ServerEntity from '../../../data/ServerEntity'
import ServerUser from '../../../data/ServerUser'
import { MediaProviderAccount } from '../../../media/account/MediaProviderAccount'
import { CurrentMediaAccount } from '../../../media/account/CurrentMediaAccountStore'
import {
  createJellyfinProviderAccountId,
  JellyfinAccountRepository,
  JellyfinDatabaseAccountSource
} from '../../../media/jellyfin/JellyfinAccountRepository'
import { MediaProviderKind } from '../../../media/MediaProviderKind'
import { ApiClient } from '../../../network/ApiClient'
import connectionHelper from '../../../setup/ConnectionHelper'
import { CheckUrlStateType, Success } from '../../../ui/state/CheckUrlState'
import { Constants } from '../../../utils/Constants'
import { FeiniuVideoAccountStore } from '../../feiniuvideo/account/FeiniuVideoAccountStore'
import { HomeContentNavigationController } from '../../home/HomeContentNavigationController'
import {
  AccountItemGroup,
  AccountRowViewModel,
  AccountUIState
} from './AccountUIState'
import { MediaProviderComposition } from './MediaProviderComposition'

class PreparedJellyfinAccount {
  address: string
  serverName: string
  serverVersion: string
  serverSystemId: string
  userId: string
  accessToken: string
  username: string
  usesStoredPassword: boolean

  constructor(address: string, serverName: string, serverVersion: string,
    serverSystemId: string, userId: string, accessToken: string,
    username: string, usesStoredPassword: boolean) {
    this.address = address
    this.serverName = serverName
    this.serverVersion = serverVersion
    this.serverSystemId = serverSystemId
    this.userId = userId
    this.accessToken = accessToken
    this.username = username
    this.usesStoredPassword = usesStoredPassword
  }
}

class JellyfinLegacySessionSnapshot {
  hadServerId: boolean
  serverId: number
  serverSystemId: string
  userId: string
  accessToken: string
  username: string
  apiInitialized: boolean
  apiBasePath: string
  apiUserId: string
  apiAccessToken: string
  apiDeviceId: string
  apiDeviceName: string
  appStorageServer?: ServerEntity

  constructor(hadServerId: boolean, serverId: number, serverSystemId: string,
    userId: string, accessToken: string, username: string,
    apiInitialized: boolean, apiBasePath: string, apiUserId: string,
    apiAccessToken: string, apiDeviceId: string, apiDeviceName: string,
    appStorageServer?: ServerEntity) {
    this.hadServerId = hadServerId
    this.serverId = serverId
    this.serverSystemId = serverSystemId
    this.userId = userId
    this.accessToken = accessToken
    this.username = username
    this.apiInitialized = apiInitialized
    this.apiBasePath = apiBasePath
    this.apiUserId = apiUserId
    this.apiAccessToken = apiAccessToken
    this.apiDeviceId = apiDeviceId
    this.apiDeviceName = apiDeviceName
    this.appStorageServer = appStorageServer
  }
}

export class AccountViewModel extends BaseViewModel<AccountUIState> {
  private readonly apiClientController: ApiClientController = new ApiClientController()
  private readonly jellyfinRepository: JellyfinAccountRepository = new JellyfinAccountRepository()
  private readonly jellyfinSource: JellyfinDatabaseAccountSource = new JellyfinDatabaseAccountSource()
  private readonly jellyfinStoredRows: Map<string, ServerUser> = new Map()
  private feiniuAccountStore?: FeiniuVideoAccountStore
  private providerComposition?: MediaProviderComposition
  private markCurrentAccount: boolean = true

  async getAccountList(markCurrentAccount?: boolean): Promise<void> {
    if (markCurrentAccount !== undefined) {
      this.markCurrentAccount = markCurrentAccount
    }
    if (this.ui.loading) {
      return
    }
    this.ui.loading = true
    this.ui.errorText = ''
    try {
      const hostContext = this.hostContext()
      if (!hostContext) {
        throw new Error('Account page has no host context')
      }
      const composition = await MediaProviderComposition.initialize(hostContext)
      this.providerComposition = composition
      const current = this.markCurrentAccount ?
        composition.coordinator().currentSelection() : undefined
      const jellyfinAccounts = this.jellyfinRepository.listAccounts()
      let feiniuAccounts: MediaProviderAccount[] = []
      if (composition.isFeiniuAvailable()) {
        if (!this.feiniuAccountStore) {
          this.feiniuAccountStore = await FeiniuVideoAccountStore.create(hostContext)
        }
        feiniuAccounts = this.feiniuAccountStore.listAccounts()
      }
      this.indexJellyfinRows()
      this.ui.groupAccountList = this.groupAccounts(jellyfinAccounts, feiniuAccounts, current)
    } catch (_error) {
      this.ui.errorText = this.localized('account_switch_failed')
    } finally {
      this.ui.loading = false
    }
  }

  async changeAccount(row: AccountRowViewModel): Promise<boolean> {
    if (row.isCurrent || this.ui.switchingAccountKey.length > 0) {
      return row.isCurrent
    }
    this.ui.switchingAccountKey = row.accountKey
    this.ui.errorText = ''
    this.appUIState.isConnecting = true
    try {
      const switched = row.providerKey === MediaProviderKind.FeiniuVideo ?
        await this.changeFeiniuAccount(row.providerAccountId) :
        await this.changeJellyfinAccount(row.providerAccountId)
      if (!switched) {
        this.ui.errorText = this.localized('account_switch_failed')
        return false
      }
      this.appUIState.isLogin = true
      this.navigateHome()
      return true
    } catch (_error) {
      this.ui.errorText = this.localized('account_switch_failed')
      return false
    } finally {
      this.appUIState.isConnecting = false
      this.ui.switchingAccountKey = ''
    }
  }

  async deleteAccount(row: AccountRowViewModel): Promise<boolean> {
    if (row.isCurrent || this.ui.deletingAccountKey.length > 0) {
      return false
    }
    this.ui.deletingAccountKey = row.accountKey
    this.ui.errorText = ''
    try {
      if (row.providerKey === MediaProviderKind.FeiniuVideo) {
        const composition = await this.composition()
        await composition.coordinator().deleteFeiniu(row.providerAccountId)
      } else {
        const stored = this.jellyfinStoredRows.get(row.providerAccountId)
        if (!stored) {
          throw new Error('Jellyfin account row no longer exists')
        }
        DBManager.getInstance().getUserDao().deleteDataSync({
          ID: stored.user.id
        })
      }
      await this.getAccountList()
      return true
    } catch (_error) {
      this.ui.errorText = this.localized('account_delete_failed')
      return false
    } finally {
      this.ui.deletingAccountKey = ''
    }
  }

  private hostContext(): common.Context | undefined {
    return this.ui.context.getHostContext() as common.Context | undefined
  }

  private async composition(): Promise<MediaProviderComposition> {
    if (this.providerComposition) {
      return this.providerComposition
    }
    const hostContext = this.hostContext()
    if (!hostContext) {
      throw new Error('Account page has no host context')
    }
    const composition = await MediaProviderComposition.initialize(hostContext)
    this.providerComposition = composition
    return composition
  }

  private indexJellyfinRows(): void {
    this.jellyfinStoredRows.clear()
    const rows = this.jellyfinSource.listServerUsers()
    rows.forEach((stored: ServerUser): void => {
      const providerAccountId = createJellyfinProviderAccountId(
        stored.server.id,
        stored.user.userId
      )
      this.jellyfinStoredRows.set(providerAccountId, stored)
    })
  }

  private groupAccounts(jellyfinAccounts: MediaProviderAccount[],
    feiniuAccounts: MediaProviderAccount[], current?: CurrentMediaAccount): AccountItemGroup[] {
    const groups: Map<string, AccountItemGroup> = new Map()
    const accounts: MediaProviderAccount[] = jellyfinAccounts.concat(feiniuAccounts)
    accounts.forEach((account: MediaProviderAccount): void => {
      const row = this.toRow(account, current)
      const groupTitle = account.serverName.length > 0 ? account.serverName : account.originalAddress
      const groupKey = `${account.provider}\n${groupTitle}\n${account.originalAddress}`
      let group = groups.get(groupKey)
      if (!group) {
        group = new AccountItemGroup(groupKey, groupTitle, [])
        groups.set(groupKey, group)
      }
      group.items.push(row)
    })
    const result: AccountItemGroup[] = []
    groups.forEach((group: AccountItemGroup): void => {
      group.items.sort((left: AccountRowViewModel, right: AccountRowViewModel): number => {
        if (left.isCurrent !== right.isCurrent) {
          return left.isCurrent ? -1 : 1
        }
        return right.lastUsedTimestamp - left.lastUsedTimestamp
      })
      result.push(group)
    })
    result.sort((left: AccountItemGroup, right: AccountItemGroup): number => {
      const leftCurrent = left.items.some((item: AccountRowViewModel): boolean => item.isCurrent)
      const rightCurrent = right.items.some((item: AccountRowViewModel): boolean => item.isCurrent)
      if (leftCurrent !== rightCurrent) {
        return leftCurrent ? -1 : 1
      }
      const leftLastUsed = left.items.length > 0 ? left.items[0].lastUsedTimestamp : 0
      const rightLastUsed = right.items.length > 0 ? right.items[0].lastUsedTimestamp : 0
      return rightLastUsed - leftLastUsed
    })
    return result
  }

  private toRow(account: MediaProviderAccount,
    current?: CurrentMediaAccount): AccountRowViewModel {
    const isCurrent = current !== undefined && current.provider === account.provider &&
      current.providerAccountId === account.providerAccountId
    return new AccountRowViewModel(
      account.provider,
      account.providerAccountId,
      account.displayName,
      account.serverName,
      account.originalAddress,
      account.provider === MediaProviderKind.FeiniuVideo,
      account.lastUsedTimestamp,
      isCurrent
    )
  }

  private async changeFeiniuAccount(providerAccountId: string): Promise<boolean> {
    const composition = await this.composition()
    const restored = await composition.coordinator().restoreFeiniu(providerAccountId)
    return restored !== undefined
  }

  private async changeJellyfinAccount(providerAccountId: string): Promise<boolean> {
    const stored = this.jellyfinStoredRows.get(providerAccountId)
    if (!stored) {
      return false
    }
    const prepared = await this.prepareJellyfinAccount(stored)
    if (!prepared) {
      return false
    }
    const previous = this.captureLegacyJellyfinSession()
    let mutationStarted = false
    try {
      mutationStarted = true
      await this.apiClientController.logout()
      await this.apiClientController.setupServer(
        prepared.address,
        prepared.serverName,
        prepared.serverVersion,
        prepared.serverSystemId
      )
      const serverEntity = this.apiClientController.loadSavedServer()
      if (!serverEntity) {
        throw new Error('Prepared Jellyfin server was not persisted')
      }
      if (prepared.usesStoredPassword) {
        const authenticated = await this.loginByStoredPassword(prepared, serverEntity)
        if (!authenticated) {
          throw new Error('Prepared Jellyfin credentials could not be applied')
        }
      } else {
        await this.applyPreparedJellyfinUser(prepared, serverEntity)
      }
      AppStorage.setOrCreate('server', serverEntity)
      const composition = await this.composition()
      const mirrored = await composition.coordinator().mirrorLegacyJellyfinSession()
      if (!mirrored) {
        throw new Error('Prepared Jellyfin session could not be activated')
      }
      return true
    } catch (_error) {
      if (mutationStarted) {
        this.restoreLegacyJellyfinSession(previous)
      }
      return false
    }
  }

  private async prepareJellyfinAccount(
    serverUser: ServerUser): Promise<PreparedJellyfinAccount | undefined> {
    const state = await connectionHelper.checkServerUrl(serverUser.server.hostname)
    if (state.type !== CheckUrlStateType.Success) {
      return undefined
    }
    const successData = state as Success
    const storedToken = serverUser.user.accessToken?.trim() || ''
    if (storedToken.length > 0) {
      const validationApi = this.createValidationApi(
        successData.address,
        serverUser.user.userId,
        storedToken
      )
      try {
        await getSystemApi(validationApi).getSystemInfo()
        return new PreparedJellyfinAccount(
          successData.address,
          successData.name,
          successData.version,
          successData.serverId,
          serverUser.user.userId,
          storedToken,
          serverUser.user.username,
          false
        )
      } catch (_error) {
      }
    }
    return await this.prepareJellyfinPasswordLogin(serverUser, successData)
  }

  private async prepareJellyfinPasswordLogin(serverUser: ServerUser,
    successData: Success): Promise<PreparedJellyfinAccount | undefined> {
    try {
      const password = this.readStoredPassword(
        [successData.address, serverUser.server.hostname],
        serverUser.user.username
      )
      if (password.length === 0) {
        return undefined
      }
      const validationApi = this.createValidationApi(
        successData.address,
        serverUser.user.userId,
        ''
      )
      const result: AxiosResponse<AuthenticationResult> =
        await getUserApi(validationApi).authenticateUserByName({
          authenticateUserByName: {
            Username: serverUser.user.username,
            Pw: password
          }
        })
      if (!result.data.User?.Id || !result.data.AccessToken) {
        return undefined
      }
      return new PreparedJellyfinAccount(
        successData.address,
        successData.name,
        successData.version,
        result.data.ServerId || successData.serverId,
        result.data.User.Id,
        result.data.AccessToken,
        serverUser.user.username,
        true
      )
    } catch (_error) {
      return undefined
    }
  }

  private createValidationApi(address: string, userId: string,
    accessToken: string): Api {
    const api = jellyfin.createApi(address, accessToken, axios.create())
    api.userId = userId
    api.deviceInfo = {
      id: `${jellyfin.deviceInfo.id}${userId}`,
      name: jellyfin.deviceInfo.name
    }
    return api
  }

  private readStoredPassword(addresses: string[], username: string): string {
    const visited: Set<string> = new Set()
    for (const candidate of addresses) {
      const address = candidate.trim()
      if (address.length === 0 || visited.has(address)) {
        continue
      }
      visited.add(address)
      try {
        const query: asset.AssetMap = new Map()
        query.set(asset.Tag.ALIAS, stringToArray(`${address}_${username}`))
        query.set(asset.Tag.RETURN_TYPE, asset.ReturnType.ALL)
        const storedAssets: Array<asset.AssetMap> = asset.querySync(query)
        if (storedAssets.length > 0) {
          const secret = storedAssets[0].get(asset.Tag.SECRET) as Uint8Array
          return util.TextDecoder.create('utf-8').decodeToString(secret)
        }
      } catch (_error) {
      }
    }
    return ''
  }

  private async loginByStoredPassword(prepared: PreparedJellyfinAccount,
    serverEntity: ServerEntity): Promise<boolean> {
    try {
      await this.apiClientController.setupUser(
        serverEntity.id,
        prepared.userId,
        prepared.accessToken,
        prepared.username
      )
      const preference = AppPreference.getInstance()
      preference.setCurrentServerSystemId(prepared.serverSystemId)
      preference.setAccessToken(prepared.accessToken)
      return true
    } catch (_error) {
      return false
    }
  }

  private async applyPreparedJellyfinUser(prepared: PreparedJellyfinAccount,
    serverEntity: ServerEntity): Promise<void> {
    await this.apiClientController.setupUser(
      serverEntity.id,
      prepared.userId,
      prepared.accessToken,
      prepared.username
    )
    const preference = AppPreference.getInstance()
    preference.setCurrentServerSystemId(prepared.serverSystemId)
    preference.setAccessToken(prepared.accessToken)
  }

  private captureLegacyJellyfinSession(): JellyfinLegacySessionSnapshot {
    const preference = AppPreference.getInstance()
    const currentServerId = preference.getCurrentServerId()
    let apiBasePath = ''
    let apiUserId = ''
    let apiAccessToken = ''
    let apiDeviceId = jellyfin.deviceInfo.id
    let apiDeviceName = jellyfin.deviceInfo.name
    const apiInitialized = ApiClient.isInitialized()
    if (apiInitialized) {
      const api = ApiClient.Instance()
      apiBasePath = String(api.basePath || '')
      apiUserId = String(api.userId || '')
      apiAccessToken = String(api.accessToken || '')
      apiDeviceId = String(api.deviceInfo.id || '')
      apiDeviceName = String(api.deviceInfo.name || '')
    }
    return new JellyfinLegacySessionSnapshot(
      preference.hasValue(Constants.PREF_SERVER_ID),
      currentServerId === null ? 0 : currentServerId,
      preference.getCurrentServerSystemId(),
      preference.getCurrentUserId(),
      preference.getAccessToken(),
      preference.getUserName(),
      apiInitialized,
      apiBasePath,
      apiUserId,
      apiAccessToken,
      apiDeviceId,
      apiDeviceName,
      AppStorage.get<ServerEntity>('server')
    )
  }

  private restoreLegacyJellyfinSession(snapshot: JellyfinLegacySessionSnapshot): void {
    const preference = AppPreference.getInstance()
    try {
      if (snapshot.hadServerId) {
        preference.setCurrentServerId(snapshot.serverId)
      } else {
        preference.deleteValue(Constants.PREF_SERVER_ID)
      }
    } catch (_error) {
    }
    try {
      preference.setCurrentServerSystemId(snapshot.serverSystemId)
    } catch (_error) {
    }
    try {
      preference.setCurrentUserId(snapshot.userId)
    } catch (_error) {
    }
    try {
      preference.setAccessToken(snapshot.accessToken)
    } catch (_error) {
    }
    try {
      preference.setUserName(snapshot.username)
    } catch (_error) {
    }
    try {
      if (snapshot.apiInitialized) {
        const api = ApiClient.update(snapshot.apiBasePath)
        api.userId = snapshot.apiUserId
        api.accessToken = snapshot.apiAccessToken
        api.deviceInfo = {
          id: snapshot.apiDeviceId,
          name: snapshot.apiDeviceName
        }
      } else if (ApiClient.isInitialized()) {
        const api = ApiClient.Instance()
        api.basePath = ''
        api.userId = ''
        api.accessToken = ''
        api.deviceInfo = {
          id: snapshot.apiDeviceId,
          name: snapshot.apiDeviceName
        }
      }
    } catch (_error) {
    }
    try {
      if (snapshot.appStorageServer) {
        AppStorage.setOrCreate('server', snapshot.appStorageServer)
      } else {
        AppStorage.delete('server')
      }
    } catch (_error) {
    }
  }

  private navigateHome(): void {
    HomeContentNavigationController.reset(this.appUIState)
    HMRouterMgr.getPathStack(Constants.NAVIGATION_ID)?.clear()
    HMRouterMgr.to(RouterConsts.IndexPage)
      .withNavigation(Constants.NAVIGATION_ID)
      .withAnimator(false)
      .replace()
  }

  private localized(name: string): string {
    try {
      return this.ui.context.getHostContext()?.resourceManager.getStringByNameSync(name) || ''
    } catch (_error) {
      return ''
    }
  }
}
