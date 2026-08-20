import { MediaImageRef } from '../media/catalog/MediaCatalogModels'

//队列中
export interface QueueItem {
  //播放id
  id: string,

  //实际集数
  playIndex: number,

  //集名称
  name: string

  //封面
  image: string

  //带鉴权和稳定缓存键的封面引用
  imageRef?: MediaImageRef

  //时长
  durationText: string

  //是否已观看
  played: boolean
}
