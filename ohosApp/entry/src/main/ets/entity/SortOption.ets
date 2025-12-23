import { ItemSortBy } from "sdk/src/main/ets/generated-client/models";

export class SortOption {
  label: string = '按添加日期'
  value: ItemSortBy = 'DateCreated'
}

export const sortOptions: SortOption[] = [
  { label: '默认', value: 'Default' },
  { label: '按名称', value: 'Name' },
  { label: '按添加日期', value: 'DateCreated' },
  { label: '按首播日期', value: 'PremiereDate' },
  { label: '按播放日期', value: 'DatePlayed' },
  { label: '按社区评分', value: 'CommunityRating' },
  { label: '按影评人评分', value: 'CriticRating' },
  { label: '按出品年份', value: 'ProductionYear' },
  { label: '按播放次数', value: 'PlayCount' },
  { label: '按时长', value: 'Runtime' },
  { label: '按官方评级', value: 'OfficialRating' },
  { label: '随机', value: 'Random' },
  { label: '按专辑', value: 'Album' },
  { label: '按艺术家', value: 'Artist' },
  { label: '按专辑艺术家', value: 'AlbumArtist' },
  { label: '按播出集数顺序', value: 'AiredEpisodeOrder' },
  { label: '按剧集名称', value: 'SeriesSortName' },
  { label: '按制作公司', value: 'Studio' },
  { label: '按收藏/喜欢', value: 'IsFavoriteOrLiked' },
  { label: '按最后添加内容日期', value: 'DateLastContentAdded' },
  { label: '未播放优先', value: 'IsUnplayed' },
  { label: '已播放优先', value: 'IsPlayed' }
];