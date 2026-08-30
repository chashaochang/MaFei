import { HomeDestination } from './HomeDestination'

export enum HomeMinePane {
  Overview = 'overview',
  History = 'history',
  Accounts = 'accounts',
  Management = 'management',
  Settings = 'settings',
  AboutPrivacy = 'about_privacy'
}

export enum HomeSidebarItemType {
  Title = 'title',
  Item = 'item'
}

export class HomeSidebarItem {
  id: string
  type: HomeSidebarItemType
  title: string
  icon: ResourceStr
  badge: number
  destination?: HomeDestination
  symbolIcon?: Resource
  mediaDataSourceIndex: number
  minePane?: HomeMinePane

  constructor(id: string, type: HomeSidebarItemType, title: string,
    icon: ResourceStr = '', badge: number = 0) {
    this.id = id
    this.type = type
    this.title = title
    this.icon = icon
    this.badge = badge
    this.mediaDataSourceIndex = -1
  }
}
