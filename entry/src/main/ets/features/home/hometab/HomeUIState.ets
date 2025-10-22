import { BaseItemKind } from "sdk/src/main/ets/generated-client/models"

@ObservedV2
export class HomeUIState {
  //媒体库列表
  @Trace mediaList: MediaItem[] = []
  //继续观看列表
  @Trace resumeList: VideoItem[] = []
  //接下啦列表
  @Trace nextUpList: VideoItem[] = []
  //媒体库列表
  @Trace latestMediaList: MediaItem[] = []
  @Trace latestMap: Map<string, VideoItem[]> = new Map

  @Trace isRefreshing: boolean = false;
}

export interface MediaItem {
  name: string,
  image: string,
  id: string,
  childCount: number,
  type?: BaseItemKind,
}

export interface VideoItem {
  id: string,
  name: string,
  image: string,
  mediaName?: string,
  type?: string,
  percent?: number,
  subtitle?: string,
  score?: number,//评分
  tags?: string[],//都有什么分辨率的
  time?: string,//时长
}