import { BaseUIState } from '../../../common/base/BaseUIState'

@ObservedV2
export class AccountRowViewModel {
  readonly providerKey: string
  readonly providerAccountId: string
  readonly accountKey: string
  readonly displayName: string
  readonly serverName: string
  readonly address: string
  readonly avatarText: string
  readonly isFeiniuVideo: boolean
  readonly lastUsedTimestamp: number
  @Trace isCurrent: boolean

  constructor(providerKey: string, providerAccountId: string, displayName: string,
    serverName: string, address: string, isFeiniuVideo: boolean,
    lastUsedTimestamp: number, isCurrent: boolean) {
    this.providerKey = providerKey
    this.providerAccountId = providerAccountId
    this.accountKey = `${providerKey}\n${providerAccountId}`
    this.displayName = displayName.trim()
    this.serverName = serverName.trim()
    this.address = address.trim()
    this.avatarText = this.displayName.length > 0 ? this.displayName.substring(0, 1) : '?'
    this.isFeiniuVideo = isFeiniuVideo
    this.lastUsedTimestamp = lastUsedTimestamp
    this.isCurrent = isCurrent
  }
}

@ObservedV2
export class AccountItemGroup {
  readonly key: string
  readonly title: string
  @Trace items: AccountRowViewModel[]

  constructor(key: string, title: string, items: AccountRowViewModel[]) {
    this.key = key
    this.title = title
    this.items = items
  }
}

@ObservedV2
export class AccountUIState extends BaseUIState {
  @Trace groupAccountList: AccountItemGroup[] = []
  @Trace loading: boolean = false
  @Trace switchingAccountKey: string = ''
  @Trace deletingAccountKey: string = ''
  @Trace errorText: string = ''
}
