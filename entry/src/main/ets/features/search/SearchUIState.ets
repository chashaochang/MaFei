import { BaseUIState } from "../../common/base/BaseUIState";

@ObservedV2
export class SearchUIState extends BaseUIState{
  @Trace searchText: string = '';
  @Trace histories: string[] = [];
  @Trace focus: boolean = false
  @Trace totalCount: string = ''
  @Trace errorMessage: string = ''
}
