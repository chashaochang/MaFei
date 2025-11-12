import { MediaItem } from "../hometab/HomeUIState";

@ObservedV2
export class MediaUIState {
  //收藏数量
  @Trace favoriteCount: number = 0;
  //媒体库列表
  @Trace mediaList: MediaItem[] = []
  @Trace isRefreshing: boolean = false;
}