import { BaseUIState } from "../../../common/base/BaseUIState";
import ServerUser from "../../../data/ServerUser";

@ObservedV2
export class AccountUIState extends BaseUIState {

  @Trace groupAccountList: ItemGroup[] = []
}

export class ItemGroup {
  title: string = ''
  items: ServerUser[] = []
}