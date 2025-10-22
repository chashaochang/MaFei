import { MediaItem } from "../hometab/HomeUIState";

@ObservedV2
export class MediaUIState {
  //媒体库列表
  @Trace mediaList: MediaItem[] = []
  @Trace isRefreshing: boolean = false;
}