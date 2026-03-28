import { AxiosResponse } from '@ohos/axios'
import { getUserLibraryApi, ImageUrlsApi } from 'sdk'
import {
  BaseItemDto,
  BaseItemKind,
  CollectionType,
  ImageType,
  MediaStream
} from 'sdk/src/main/ets/generated-client/models'
import { ActivityEventType } from '../events/ActivityEvent'
import { VideoTag } from '../features/videodetail/VideoDetailUIState'
import { ApiClient } from '../network/ApiClient'
import { PlayOptions } from '../player/interaction/PlayOptions'
import { UIConstants } from './UIConstants'
import { common, Want } from '@kit.AbilityKit'
import { util } from '@kit.ArkTS'
import { AppStorageV2 } from '@kit.ArkUI'
import { AppUIState } from '../entity/AppUIState'

export function parseVideoType(type?: BaseItemKind): string {
  let res = ''
  switch (type) {
    case BaseItemKind.Movie:
      res = '电影'
      break
    case BaseItemKind.Episode:
      res = '剧集'
      break
  }
  return res
}

export function getItemImageUrl(e: BaseItemDto, type?: ImageType): string {
  //电影和剧集封面不一样
  if (e.Type === BaseItemKind.Episode) {
    return ApiClient.Instance().basePath +
    new ImageUrlsApi().getItemImageUrlById(e.ParentThumbItemId, type || ImageType.Primary)
  } else if (e.Type === BaseItemKind.Season) {
    return ApiClient.Instance().basePath + new ImageUrlsApi().getItemImageUrlById(e.Id, ImageType.Primary)
  } else {
    return ApiClient.Instance().basePath + new ImageUrlsApi().getItemImageUrlById(e.Id, type || ImageType.Primary)
  }
}

export function getItemBackDropImageUrl(e: BaseItemDto): string | null {
  const imageApi = new ImageUrlsApi();
  const basePath: string = ApiClient.Instance().basePath;

  // helper
  const getUrl = (id: string | undefined, type: ImageType) =>
  id ? basePath + imageApi.getItemImageUrlById(id, type) : null;

  if (e.Type === BaseItemKind.Episode) {
    // 1. 优先父级thumb
    if (e.ParentThumbItemId) {
      return getUrl(e.ParentThumbItemId, ImageType.Thumb);
    }
    // 2. 再父级backdrop
    if (e.ParentBackdropItemId) {
      return getUrl(e.ParentBackdropItemId, ImageType.Backdrop);
    }
    // 3. SeriesId 通常有剧集主图
    if (e.SeriesId) {
      return getUrl(e.SeriesId, ImageType.Primary);
    }
  }

  if (e.ImageTags) {
    // 如果有 ImageTags，说明 Jellyfin 端确实有图，尝试用 Primary（通常是剧集/电影主图）
    if (e.ImageTags[ImageType.Primary]) {
      return getUrl(e.Id, ImageType.Primary);
    }
  }

  // 普通条目，尝试多种图片类型优先级
  const preferredTypes: ImageType[] = [
    ImageType.Backdrop,
    ImageType.Primary,
    ImageType.Thumb,
    ImageType.Banner
  ];

  for (const type of preferredTypes) {
    const url = getUrl(e.Id, type);
    if (url) {
      return url;
    }
  }

  // 全部无图，返回 null
  return null;
}

// export function getItemBackDropImageUrl(e: BaseItemDto): string {
//   //电影和剧集封面不一样
//   if (e.Type === BaseItemKind.Episode) {
//     if (e.ParentThumbItemId) {
//       return ApiClient.Instance().basePath +
//       new ImageUrlsApi().getItemImageUrlById(e.ParentThumbItemId, ImageType.Thumb)
//     } else if (e.ParentBackdropItemId) {
//       return ApiClient.Instance().basePath +
//       new ImageUrlsApi().getItemImageUrlById(e.ParentBackdropItemId, ImageType.Backdrop)
//     } else if (e.SeriesPrimaryImageTag){
//       return ApiClient.Instance().basePath +
//       new ImageUrlsApi().getItemImageUrlById(e.SeriesId, ImageType.Primary)
//     }
//   }
//   return ApiClient.Instance().basePath + new ImageUrlsApi().getItemImageUrlById(e.Id, ImageType.Primary)
// }


export function resolveLatestImageUrl(
  item: BaseItemDto,
  imageApi: ImageUrlsApi,
): string {
  const allowEpisodeSelfFallback = false,
    includeParentBackdropFallback = true

  const placeholder = '/assets/placeholder-video.png';

  if (!item?.Id) {
    return placeholder;
  }
  // 仅针对 Episode 做特殊处理
  if (item.Type === BaseItemKind.Episode) {
    // 1. Series 主图（最符合“显示整个剧”的需求）
    if (item.SeriesId) {
      const tag = item.SeriesPrimaryImageTag || item.ImageTags?.[ImageType.Primary]; // 有些版本可能缺少 SeriesPrimaryImageTag
      return imageApi.getItemImageUrlById(
        item.ParentBackdropItemId,
        ImageType.Primary,
        tag ? { tag } : {}
      );
    }

    // 2. 父级主图（可能是 Season 或 Series，具体由 Jellyfin 返回）
    if (item.ParentPrimaryImageItemId) {
      return imageApi.getItemImageUrlById(
        item.ParentPrimaryImageItemId,
        ImageType.Primary,
        item.ParentPrimaryImageTag ? { tag: item.ParentPrimaryImageTag } : {}
      );
    }

    // 3. 父级缩略图
    if (item.ParentThumbItemId) {
      return imageApi.getItemImageUrlById(
        item.ParentThumbItemId,
        ImageType.Thumb,
        item.ParentThumbImageTag ? { tag: item.ParentThumbImageTag } : {}
      );
    }

    // 4. 父级 Backdrop
    if (includeParentBackdropFallback &&
    item.ParentBackdropItemId &&
      item.ParentBackdropImageTags?.length) {
      return imageApi.getItemImageUrlById(
        item.ParentBackdropItemId,
        ImageType.Backdrop,
        { tag: item.ParentBackdropImageTags[0] }
      );
    }

    // 5. 是否允许回退到 Episode 自身
    if (allowEpisodeSelfFallback) {
      // 尝试自身 Thumb → Primary → Backdrop 单标签
      const order = [ImageType.Thumb, ImageType.Primary, ImageType.Backdrop];
      for (const t of order) {
        const tag = item.ImageTags?.[t];
        if (tag) {
          return imageApi.getItemImageUrlById(item.Id, t, { tag });
        }
      }
      // 自身 Backdrop 列表
      if (item.BackdropImageTags?.length) {
        return imageApi.getItemImageUrlById(
          item.Id,
          ImageType.Backdrop,
          { tag: item.BackdropImageTags[0] }
        );
      }
    }

    return placeholder;
  } else if (item.Type === BaseItemKind.Series || item.Type === BaseItemKind.Movie) {
    // Series 自身 Primary 的 tag 一般在 item.ImageTags?.Primary
    const seriesPrimaryTag = item.ImageTags?.[ImageType.Primary];
    return imageApi.getItemImageUrlById(
      item.Id,
      ImageType.Primary,
      seriesPrimaryTag ? { tag: seriesPrimaryTag } : {},
    );
  }

  // 非 Episode：保持原逻辑（可与你原来的合并）
  const preferOrder = [ImageType.Thumb, ImageType.Primary, ImageType.Backdrop];
  const tags = item.ImageTags || {};

  for (const t of preferOrder) {
    const tag = tags[t];
    if (tag) {
      return imageApi.getItemImageUrlById(item.Id, t, { tag });
    }
  }

  if (item.BackdropImageTags?.length) {
    return imageApi.getItemImageUrlById(
      item.Id,
      ImageType.Backdrop,
      { tag: item.BackdropImageTags[0] }
    );
  }

  if (includeParentBackdropFallback &&
  item.ParentBackdropItemId &&
    item.ParentBackdropImageTags?.length) {
    return imageApi.getItemImageUrlById(
      item.ParentBackdropItemId,
      ImageType.Backdrop,
      { tag: item.ParentBackdropImageTags[0] }
    );
  }

  return placeholder;
}

export function getTypeByCollectionType(collectionType?: CollectionType) {
  switch (collectionType) {
    case CollectionType.Movies:
      return BaseItemKind.Movie
    case CollectionType.Tvshows:
      return BaseItemKind.Series
    case CollectionType.Music:
      return BaseItemKind.Audio
    case CollectionType.Books:
      return BaseItemKind.Book
    case CollectionType.Livetv:
      return BaseItemKind.LiveTvChannel
    default:
      return undefined
  }
}

//解析分辨率
export function getResolutions(streams?: MediaStream[] | null): string[] {
  let res: string[] = []
  if (streams && streams.length > 0) {
    let resolutions = streams.map<string>((i) => {
      const width = i.Width;
      const height = i.Height;

      if (width && height) {
        if (width >= 3800 || height >= 2000) {
          return '4K';
        }
        if (width >= 2500 || height >= 1400) {
          return '1440';
        }
        if (width >= 1800 || height >= 1000) {
          return '1080';
        }
        if (width >= 1200 || height >= 700) {
          return '720';
        }
        if (width >= 700 || height >= 400) {
          return '480';
        }
      }
      return ""
    })
    res = resolutions.filter((item, index) => resolutions.indexOf(item) === index);
  }
  //只要两个,多了放不下
  if (res.length > 2) {
    res.length = 2
  }
  res = res.filter((e) => e !== '')
  return res
}

export function getVideoTagsByStreams(streams?: Array<MediaStream> | null): VideoTag[] {
  let tags: VideoTag[] = []
  if (streams !== undefined && streams !== null && streams.length > 0) {
    streams.forEach(i => {
      if (i.Type === 'Video') {
        if (i.Width && i.Height) {
          if (i.Width >= 3800 || i.Height >= 2000) {
            if (!tags.includes('4K')) {
              tags.push('4K')
            }
          } else if (i.Width >= 1900 || i.Height >= 1000) {
            if (!tags.includes('1080')) {
              tags.push('1080')
            }
          }
        }
        if (i.VideoRange && i.VideoRange.includes('HDR')) {
          if (!tags.includes('HDR')) {
            tags.push('HDR')
          }
        }

        if (i.VideoRange && i.VideoRange.includes('SDR')) {
          if (!tags.includes('SDR')) {
            tags.push('SDR')
          }
        }
        if (i.VideoDoViTitle && i.VideoDoViTitle.includes('Dolby Vision')) {
          if (!tags.includes('DOLBY_VISION')) {
            tags.push('DOLBY_VISION')
          }
        } else {

        }
      } else if (i.Type === 'Audio') {
        if (i.ChannelLayout?.toLowerCase().includes('stereo')) {
          if (!tags.includes('STEREO')) {
            tags.push('STEREO')
          }
        }
        if (i.Codec && i.Codec.toLowerCase().includes('dts')) {
          if (!tags.includes('DTS')) {
            tags.push('DTS')
          }
        } else if (i.Codec &&
          (i.Codec.toLowerCase().includes('dolby') || i.Codec.toLowerCase().includes('ac3'))) {
          if (i.ChannelLayout && i.ChannelLayout.toLowerCase().includes('7.1')) {
            if (!tags.includes('DOLBY_SURROUND')) {
              tags.push('DOLBY_SURROUND')
            }
          } else if (i.ChannelLayout && i.ChannelLayout.toLowerCase().includes('5.1')) {
            if (!tags.includes('DOLBY_ATOMS')) {
              tags.push('DOLBY_ATOMS')
            }
          }
        }
      }
    })
  }

  return tags
}

export function getTagBase64(tag: VideoTag): string {
  let res = ''
  switch (tag) {
    case '1080':
      res = UIConstants.TAG_1080
      break
    case '4K':
      res = UIConstants.TAG_4K
      break
    case 'STEREO':
      res = UIConstants.TAG_STEREO
      break
    case 'HDR':
      res = UIConstants.TAG_HDR
      break
    case 'SDR':
      res = UIConstants.TAG_SDR
      break
    case 'DTS':
      res = UIConstants.TAG_DTS
      break
    case 'DOLBY_VISION':
      res = UIConstants.TAG_DOLBY_VISION
      break
    case 'DOLBY_ATOMS':
      res = UIConstants.TAG_DOLBY_ATOMS
      break
    case 'DOLBY_SURROUND':
      res = UIConstants.TAG_DOLBY_SURROUND
      break
    default:
      ''
      break
  }
  return res
}

export function playItems(ids: string[], context: Context,
  audioStreamIndex?: number,
  subtitleStreamIndex?: number) {
  getUserLibraryApi(ApiClient.Instance()).getItem({
    userId: ApiClient.Instance().userId,
    itemId: ids[0],
  }).then((res: AxiosResponse<BaseItemDto>) => {
    if (res.data) {
      let itemData = res.data
      let options: PlayOptions = new PlayOptions()
      options.ids = ids
      options.startPositionTicks = itemData.UserData?.PlaybackPositionTicks || 0
      if (audioStreamIndex !== undefined) {
        options.audioStreamIndex = audioStreamIndex
      }
      if (subtitleStreamIndex !== undefined) {
        options.subtitleStreamIndex = subtitleStreamIndex
      }
      context.eventHub.emit(ActivityEventType.LaunchNativePlayer, options)
    }
  }).catch()
}

export function openUrlByBrowser(url: string) {
  try {
    let want: Want = {
      action: 'ohos.want.action.viewData',
      entities: ['entity.system.browsable'],
      uri: url
    };
    getUIAbilityContext().startAbility(want).then(() => {
      console.info('Open URL success');
    }).catch(() => {
      console.error('Open URL failed');
    });
  } catch (error) {
    console.error('Failed to open URL:', error);
  }
}

export function getUIAbilityContext(): common.UIAbilityContext {
  let appUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;
  return appUIState.context!
}

export function stringToArray(str: string): Uint8Array {
  let textEncoder = new util.TextEncoder();
  return textEncoder.encodeInto(str);
}

function isSubtitleStream(s: MediaStream): boolean {
  return s.Type === 'Subtitle';
}

function isLikelySelectableSubtitle(s: MediaStream): boolean {
  if (!isSubtitleStream(s)) {
    return false;
  }

  const codec = (s.Codec ?? '').toLowerCase();
  const textCodecs = new Set(['srt', 'ass', 'ssa', 'vtt', 'webvtt', 'ttml', 'subrip']);
  const imageCodecs = new Set(['pgs', 'pgssub', 'sup', 'vobsub', 'dvdsub']);

  const isText = s.IsTextSubtitleStream === true || textCodecs.has(codec);
  const isImage = imageCodecs.has(codec);

  // 外挂、文本型通常可软渲染；图像型根据设备能力，若 SupportsExternalStream 为真则倾向可软交付
  if (s.IsExternal === true) {
    return true;
  }
  if (isText) {
    return true;
  }
  if (isImage && s.SupportsExternalStream === true) {
    return true;
  }

  return false;
}

/** 详情页是否显示“关闭字幕（无）” */
export function shouldShowCloseSubtitleOptionOnDetail(streams: MediaStream[]): boolean {
  const subs = streams.filter(isSubtitleStream);
  if (subs.length === 0) {
    return false;
  }
  return subs.some(isLikelySelectableSubtitle);
}

/**
 * 生成美观的深色蓝色系随机背景色
 * 包含蓝色、青色、紫蓝色等相近色调
 * @returns 返回十六进制颜色字符串
 */
export function generateBeautifulDeepBlueColor(): string {
  // 色相范围：180-260 度
  const hue = Math.floor(Math.random() * 80) + 180;

  // 饱和度：50%-75%，保证色彩鲜艳但不刺眼
  const saturation = Math.floor(Math.random() * 25) + 50;

  // 🔧 关键：亮度提升到 35%-50%，确保图标可见
  const lightness = Math.floor(Math.random() * 15) + 35; // 改为 35-50%

  return hslToHex(hue, saturation, lightness);
}

/**
 * 将 HSL 颜色转换为十六进制格式
 */
function hslToHex(h: number, s: number, l: number): string {
  // 将百分比转换为小数
  s = s / 100;
  l = l / 100;

  const c = (1 - Math.abs(2 * l - 1)) * s;
  const x = c * (1 - Math.abs((h / 60) % 2 - 1));
  const m = l - c / 2;

  let r = 0, g = 0, b = 0;

  if (h >= 0 && h < 60) {
    r = c;
    g = x;
    b = 0;
  } else if (h >= 60 && h < 120) {
    r = x;
    g = c;
    b = 0;
  } else if (h >= 120 && h < 180) {
    r = 0;
    g = c;
    b = x;
  } else if (h >= 180 && h < 240) {
    r = 0;
    g = x;
    b = c;
  } else if (h >= 240 && h < 300) {
    r = x;
    g = 0;
    b = c;
  } else {
    r = c;
    g = 0;
    b = x;
  }

  // 转换为 0-255 范围并转为十六进制
  const red = Math.round((r + m) * 255);
  const green = Math.round((g + m) * 255);
  const blue = Math.round((b + m) * 255);

  const hexRed = red.toString(16).padStart(2, '0');
  const hexGreen = green.toString(16).padStart(2, '0');
  const hexBlue = blue.toString(16).padStart(2, '0');

  return `#${hexRed}${hexGreen}${hexBlue}`;
}