export interface Credentials {
  Servers: Server[]
}

export interface Server {
  UserId: string
  AccessToken: string
  DateLastAccessed: number
  ExchangeToken: null
  Id: string
  LastConnectionMode: number
  LocalAddress: string | null
  ManualAddress: string
  Name: string
  manualAddressOnly: boolean
}