import { BaseUIState } from "../../common/base/BaseUIState"
import { BaseDataSource } from "../../data/BaseDataSource"
import { MediaItem } from "./hometab/HomeUIState"
import { HomeDestination } from "./HomeDestination"
import { HomeMinePane } from './HomePadNavigation'

export enum PadMenuType {
  TITLE,
  ITEM,
}

export class PadMenuItem {
  type: PadMenuType
  title: string
  selectedIcon: ResourceStr
  unselectedIcon: ResourceStr
  badge: number
  extra?: MediaItem

  constructor(type: PadMenuType, title: string, selectedIcon: ResourceStr = '', unselectedIcon: ResourceStr = '',
    badge: number = 0) {
    this.type = type
    this.title = title
    this.selectedIcon = selectedIcon
    this.unselectedIcon = unselectedIcon
    this.badge = badge
  }
}

@ObservedV2
export class HomeScreenUIState extends BaseUIState {
  @Trace favoriteCount: number = 0
  @Trace chasingCount: number = 0
  @Trace selectedDestination: HomeDestination = HomeDestination.Home
  @Trace selectedMinePane: HomeMinePane = HomeMinePane.Overview
  /** Active media child in the Pad sidebar. -1 keeps the media overview selected. */
  @Trace selectedMediaDataSourceIndex: number = -1
  @Trace isLoading: boolean = true
  /** Large screen: whether left sidebar is visible (default true) */
  @Trace isLeftSidebarVisible: boolean = true
  /** Medium screen: whether menu modal is visible (default false) */
  @Trace isMenuModalVisible: boolean = false
  /** Last valid media item index, retained when the media overview is selected. */
  @Trace lastSelectedMediaIndex: number = 0
  /** Incremented when datasource changes, triggers PadTopTab list refresh */
  @Trace padMenuDataVersion: number = 0
}

export class PadMenuDataSource extends BaseDataSource<PadMenuItem> {
  private dataArray: PadMenuItem[] = []

  public totalCount(): number {
    return this.dataArray.length;
  }

  public getData(index: number): PadMenuItem {
    return this.dataArray[index];
  }

  public pushData(data: PadMenuItem): void {
    this.dataArray.push(data);
    this.notifyDataAdd(this.dataArray.length - 1);
  }

  public pushDataArray(datas: PadMenuItem[]): void {
    datas.forEach((item)=>{
      this.pushData(item)
    })
  }

  public clearData(): void {
    this.dataArray = []
    this.notifyDataReload()
  }

  /** First real media item index, excluding fixed tabs like 首页/追剧/收藏 */
  public getFirstMediaItemIndex(): number {
    for (let i = 0; i < this.totalCount(); i++) {
      if (this.getData(i).type === PadMenuType.ITEM && this.getData(i).extra) {
        return i
      }
    }
    return 0
  }
}
