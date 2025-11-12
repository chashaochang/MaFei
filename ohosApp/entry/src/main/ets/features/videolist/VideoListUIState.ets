import { BaseUIState } from "../../common/base/BaseUIState"
import { ListDataSource } from "../../data/ListDataSource";
import { VideoItem } from "../home/hometab/HomeUIState";

@ObservedV2
export class VideoListUIState extends BaseUIState {
  @Trace title: string = '视频列表'
  dataSource: ListDataSource<VideoItem> = new ListDataSource();
}