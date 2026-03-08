import { BaseUIState } from "../../common/base/BaseUIState"
import { BaseDataSource } from "../../data/BaseDataSource"
import { PadMenuItem } from "./HomeScreen"

@ObservedV2
export class HomeScreenUIState extends BaseUIState {
  @Trace favoriteCount: number = 0
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
}