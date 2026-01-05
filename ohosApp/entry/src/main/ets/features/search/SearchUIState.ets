import { GridAttr } from "@abner/refresh_v2";
import { BaseItemDto } from "sdk/src/main/ets/generated-client/models";
import { BaseUIState } from "../../common/base/BaseUIState";

@ObservedV2
export class SearchUIState extends BaseUIState{
  @Trace searchText: string = '';
  @Trace searchResults: BaseItemDto[] = [];
  @Trace histories: string[] = [];
  @Trace focus: boolean = false
  @Trace totalCount: string = ''
  gridAttr: GridAttr = new GridAttr();
}