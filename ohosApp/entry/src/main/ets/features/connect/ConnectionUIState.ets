import { BaseUIState } from "../../common/base/BaseUIState";
import { CheckUrlState, Unchecked } from "../../ui/state/CheckUrlState";
import { ServerSelectionMode } from "../../ui/state/ServerSelectionMode";
import { ServerSuggestion } from "./ServerSuggestion";

@ObservedV2
export class ConnectionUIState extends BaseUIState {
  @Trace username: string = ''
  @Trace password: string = ''
  @Trace loading: boolean = false
  @Trace errorText: string = ''
  @Trace rememberPwd: boolean = false;
  @Trace hostname: string = ''
  @Trace serverSelectionMode: ServerSelectionMode = ServerSelectionMode.ADDRESS
  @Trace externalError: boolean = false
  @Trace checkUrlState: CheckUrlState = new Unchecked()
  @Trace serverSuggestions: ServerSuggestion[] = []
}