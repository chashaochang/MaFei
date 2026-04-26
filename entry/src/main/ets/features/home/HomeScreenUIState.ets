import { BaseUIState } from "../../common/base/BaseUIState"
import { BaseDataSource } from "../../data/BaseDataSource"
import { MediaItem } from "./hometab/HomeUIState"

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
  @Trace selectedIndex: number = 0
  @Trace menuSelectedIndex: number = 0
  @Trace isLoading: boolean = true
  /** Large screen: whether left sidebar is visible (default true) */
  @Trace isLeftSidebarVisible: boolean = true
  /** Medium screen: whether menu modal is visible (default false) */
  @Trace isMenuModalVisible: boolean = false
  /** Last selected media item index, used for PadTopTab media slot when fixed tabs are selected */
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
