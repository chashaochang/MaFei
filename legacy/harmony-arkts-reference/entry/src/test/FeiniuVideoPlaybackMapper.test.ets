import { describe, expect, it } from '@ohos/hypium'
import { MediaProviderKind } from '../main/ets/media/MediaProviderKind'
import { MediaProviderSession } from '../main/ets/media/MediaProviderSession'
import { MediaRef, MediaRefKind } from '../main/ets/media/MediaRef'
import {
  MediaPlaybackEngineHint,
  MediaPlaybackStreamType
} from '../main/ets/media/playback/MediaPlaybackModels'
import {
  FeiniuVideoPlaybackAccess,
  FeiniuVideoPlaybackMapper
} from '../main/ets/features/feiniuvideo/playback/FeiniuVideoPlaybackMapper'
import {
  FnMediaItem,
  FnMediaItemStream
} from '../main/ets/features/feiniuvideo/catalog/FeiniuVideoDtos'

function session(): MediaProviderSession {
  return new MediaProviderSession({
    provider: MediaProviderKind.FeiniuVideo,
    providerAccountId: 'feiniu-1',
    userId: 'user-1',
    originalAddress: 'FN123456',
    baseUrl: 'http://lan:5666',
    displayName: 'Feiniu User'
  })
}

export default function feiniuVideoPlaybackMapperTest() {
  describe('FeiniuVideoPlaybackMapper', () => {
    it('mapsAuthenticatedDirectSourceTracksAndResume', 0, () => {
      const owner = session()
      const mapper = new FeiniuVideoPlaybackMapper(owner, new FeiniuVideoPlaybackAccess(
        'https://relay.example/',
        'token-1',
        'session=1',
        true
      ))
      const source = mapper.source({
        ref: new MediaRef(owner.provider, owner.accountScope, 'episode-1', MediaRefKind.Episode),
        item: {
          guid: 'episode-1',
          title: 'Episode 1',
          type: 'Episode',
          ancestor_guid: 'series-1',
          ancestor_name: '电视剧库',
          tv_title: 'Series One',
          parent_guid: 'season-1',
          duration: 1800,
          watched: 1
        },
        file: { guid: 'media-1', file_name: 'episode.mkv' },
        video: {
          media_guid: 'media-1',
          guid: 'video-1',
          index: 0,
          codec_name: 'hevc',
          resolution_type: '4K',
          color_range_type: 'Dolby Vision',
          width: 3840,
          height: 2160,
          duration: 1800
        },
        audios: [{
          media_guid: 'media-1',
          guid: 'audio-1',
          index: 1,
          codec_name: 'aac',
          language: 'zh',
          channels: 2,
          is_default: 1
        }],
        subtitles: [{
          media_guid: 'media-1',
          guid: 'subtitle-1',
          index: 2,
          codec_name: 'srt',
          language: 'zh',
          is_external: 1,
          is_default: 1
        }],
        selectedAudio: {
          media_guid: 'media-1',
          guid: 'audio-1',
          index: 1,
          codec_name: 'aac',
          is_default: 1
        },
        selectedSubtitle: {
          media_guid: 'media-1',
          guid: 'subtitle-1',
          index: 2,
          codec_name: 'srt',
          is_external: 1,
          is_default: 1
        },
        playSessionId: 'play-1',
        mediaGuid: 'media-1',
        videoUrl: 'https://relay.example/v/api/v1/media/range/media-1',
        directOriginal: true,
        startTimeTicks: 1200000000,
        engineHint: MediaPlaybackEngineHint.Auto
      })

      expect(source.startTimeMs).assertEqual(120000)
      expect(source.audioStreams.length).assertEqual(1)
      expect(source.subtitleStreams.length).assertEqual(1)
      expect(source.selectedAudioStream?.Type).assertEqual(MediaPlaybackStreamType.Audio)
      expect(source.selectedSubtitleStream?.Type).assertEqual(MediaPlaybackStreamType.Subtitle)
      expect(source.item.SeriesName).assertEqual('Series One')
      expect(source.metadata.title).assertEqual('Series One')
      expect(source.externalSubtitleStreams[0].deliveryUrl).assertEqual(
        'https://relay.example/v/api/v1/subtitle/dl/subtitle-1')
      expect(source.requestHeaders[0].name).assertEqual('Authorization')
      expect(source.requestHeaders[1].value.indexOf('Trim-MC-token=token-1') >= 0).assertTrue()
      expect(source.requestHeaders[1].value.indexOf('mode=relay') >= 0).assertTrue()
    })

    it('preservesExplicitZeroAndUsesServerResumeWhenUnspecified', 0, () => {
      const owner = session()
      const mapper = new FeiniuVideoPlaybackMapper(owner)
      const playStream: FnMediaItemStream = { current_time: 80, progress: 20 }
      const item: FnMediaItem = {
        guid: 'movie-1',
        title: 'Movie',
        type: 'Movie',
        duration: 1000,
        ts: 50,
        media_stream: playStream
      }

      expect(mapper.resumeTicks(item, 60)).assertEqual(2000000000)
      expect(mapper.resumeTicks(item, 60, 0)).assertEqual(0)
    })

    it('mapsQueueItemsToAccountScopedRefs', 0, () => {
      const owner = session()
      const mapper = new FeiniuVideoPlaybackMapper(owner)
      const queueSource: FnMediaItem = {
        guid: 'episode-2',
        title: 'Episode 2',
        type: 'Episode',
        ancestor_name: '电视剧库',
        tv_title: 'Series One',
        episode_number: 2,
        duration: 2400
      }
      const item = mapper.queueItem(queueSource, 1)

      expect(item?.ref.accountScope).assertEqual(owner.accountScope)
      expect(item?.playIndex).assertEqual(2)
      expect(item?.seriesName).assertEqual('Series One')
      expect(item?.durationTicks).assertEqual(24000000000)
    })
  })
}
