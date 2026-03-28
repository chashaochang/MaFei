import { BaseUIState } from "../../common/base/BaseUIState";
import { ServerSuggestion } from "./ServerSuggestion";

@ObservedV2
export class ServerDiscoveryUIState extends BaseUIState{
  @Trace serverSuggestions: ServerSuggestion[] = []
}