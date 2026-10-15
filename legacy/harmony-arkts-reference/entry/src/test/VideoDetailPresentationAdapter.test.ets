import { describe, expect, it } from '@ohos/hypium'
import { FeiniuVideoDetailPresentationAdapter } from
  '../main/ets/features/feiniuvideo/presentation/FeiniuVideoDetailPresentationAdapter'
import { VideoDetailPresentationAdapterRegistry } from
  '../main/ets/features/videodetail/presentation/VideoDetailPresentationAdapterRegistry'
import { MediaFileInfo, MediaStreamInfo, MediaStreamKind } from
  '../main/ets/media/catalog/MediaCatalogModels'
import { JellyfinVideoDetailPresentationAdapter } from
  '../main/ets/media/jellyfin/JellyfinVideoDetailPresentationAdapter'
import { MediaProviderKind } from '../main/ets/media/MediaProviderKind'
import { MediaRef, MediaRefKind } from '../main/ets/media/MediaRef'
import { selectedMediaOptionLabel, selectedMediaOptionStreamIndex } from
  '../main/ets/media/presentation/VideoDetailPresentationModels'

function owner(provider: MediaProviderKind, id: string): MediaRef {
  return new MediaRef(provider, `${provider}:account`, id, MediaRefKind.Movie)
}

function video(index: number, resolutionType: string, range: string, dvProfile?: number,
  bitrate: number = 25540000, bitDepth: number = 10): MediaStreamInfo {
  return {
    index: index,
    kind: MediaStreamKind.Video,
    title: '',
    displayTitle: resolutionType,
    codec: 'hevc',
    language: '',
    width: resolutionType === '4K' ? 3840 : 1920,
    height: resolutionType === '4K' ? 2160 : 1080,
    resolutionType: resolutionType,
    videoRangeType: range,
    dvProfile: dvProfile,
    bitrate: bitrate,
    bitDepth: bitDepth,
    external: false,
    default: false,
    forced: false
  }
}

function audio(index: number, displayTitle: string = '国语 EAC3 5.1', codec: string = 'eac3',
  language: string = 'chi', isDefault: boolean = true, title: string = '国语',
  sampleRate: number = 48000): MediaStreamInfo {
  return {
    index: index,
    kind: MediaStreamKind.Audio,
    title: title,
    displayTitle: displayTitle,
    codec: codec,
    language: language,
    channels: 6,
    channelLayout: '5.1',
    audioType: 'Dolby Atmos',
    sampleRate: sampleRate,
    external: false,
    default: isDefault,
    forced: false
  }
}

function subtitle(index: number, codec: string = 'ass', language: string = 'chi', title: string = '简体中文'):
  MediaStreamInfo {
  return {
    index: index,
    kind: MediaStreamKind.Subtitle,
    title: title,
    displayTitle: `${title} ${codec.toUpperCase()}`,
    codec: codec,
    language: language,
    external: true,
    default: true,
    forced: false
  }
}

function emptyVideo(index: number): MediaStreamInfo {
  return {
    index: index,
    kind: MediaStreamKind.Video,
    title: '',
    displayTitle: '',
    codec: '',
    language: '',
    external: false,
    default: false,
    forced: false
  }
}

function source(provider: MediaProviderKind, id: string, streams: MediaStreamInfo[]): MediaFileInfo {
  return {
    ownerRef: owner(provider, id),
    id: id,
    name: `${id}.mkv`,
    path: `/media/${id}.mkv`,
    container: 'mkv',
    protocol: 'http',
    sizeBytes: 4294967296,
    durationMs: 7200000,
    fileCreatedAtMs: 1785889046949,
    addedAtMs: 1785889069766,
    streams: streams
  }
}

export default function videoDetailPresentationAdapterTest() {
  describe('VideoDetailPresentationAdapter', () => {
    it('usesFeiniuDvProfileInsteadOfMislabelingTheSourceAsSdr', 0, () => {
      const adapter = new FeiniuVideoDetailPresentationAdapter()
      const result = adapter.present([
        source(MediaProviderKind.FeiniuVideo, 'sdr', [video(0, '4K', 'SDR'), audio(1)]),
        source(MediaProviderKind.FeiniuVideo, 'dolby', [video(0, '4K', 'SDR', 8), audio(1), subtitle(2)])
      ])

      expect(result[0].compactLabel).assertEqual('4KSDR')
      expect(result[0].summary.includes('杜比')).assertFalse()
      expect(result[1].compactLabel).assertEqual('4K杜比')
      expect(result[1].summary.includes('SDR')).assertFalse()
      expect(result[1].summary.includes('杜比')).assertTrue()
      expect(result[1].audioOptions[0].streamIndex).assertEqual(1)
      expect(result[1].subtitleOptions[0].streamIndex).assertEqual(2)
      expect(result[1].audioOptions[0].compactLabel).assertEqual('中文音频')
      expect(result[1].subtitleOptions[0].compactLabel).assertEqual('中文字幕')
      expect(result[1].infoSections[0].cards[0].rows[8].value).assertEqual('Dolby Vision P8')
      expect(result[1].fileSize).assertEqual('4.00 GB')
      expect(result[1].fileCreatedDate).assertEqual('2026-08-05')
      expect(result[1].addedDate).assertEqual('2026-08-05')
      expect(adapter.episodeLabel(7, 1)).assertEqual('季 7 集 1')
    })

    it('keepsOnlyTheFirstStreamInTheOuterSummaryAndAllStreamsInDetails', 0, () => {
      const adapter = new FeiniuVideoDetailPresentationAdapter()
      const firstAudio = audio(1, 'chi NONE 6声道', 'none', 'chi', true, '')
      const secondAudio = audio(3, '国语 AAC 2.0', 'aac', 'chi', false)
      const result = adapter.present([
        source(MediaProviderKind.FeiniuVideo, 'multi-stream', [video(0, '4K', 'SDR'), firstAudio, secondAudio,
          subtitle(2), subtitle(4, 'pgs')])
      ])

      expect(result[0].videoSummary).assertEqual('4k HEVC 25.54 mbps · 10 bit')
      expect(result[0].audioSummary).assertEqual('中文 NONE · 48000 Hz')
      expect(result[0].audioSummary.includes('英语')).assertFalse()
      expect(result[0].subtitleSummary.split('\n').length).assertEqual(1)
      expect(result[0].infoSections[1].cards.length).assertEqual(2)
      expect(result[0].infoSections[2].cards.length).assertEqual(2)
      expect(result[0].audioOptions[0].summary === result[0].audioOptions[1].summary).assertFalse()
      expect(result[0].subtitleOptions[0].summary === result[0].subtitleOptions[1].summary).assertFalse()
      expect(selectedMediaOptionStreamIndex(result[0].audioOptions, 1)).assertEqual(3)
      expect(selectedMediaOptionStreamIndex(result[0].subtitleOptions, 1)).assertEqual(4)
      expect(selectedMediaOptionStreamIndex(result[0].audioOptions, 2)).assertUndefined()
      expect(selectedMediaOptionLabel(result[0].audioOptions, 1, '无音频'))
        .assertEqual(result[0].audioOptions[1].compactLabel)
      expect(selectedMediaOptionLabel(result[0].subtitleOptions, 1, '无字幕'))
        .assertEqual(result[0].subtitleOptions[1].compactLabel)
      expect(selectedMediaOptionLabel(result[0].subtitleOptions, 2, '无字幕')).assertEqual('无字幕')
    })

    it('keepsJellyfinPresentationBehindItsOwnAdapter', 0, () => {
      const adapter = new JellyfinVideoDetailPresentationAdapter()
      const result = adapter.present([
        source(MediaProviderKind.Jellyfin, 'hdr', [video(0, '1080P', 'HDR10'),
          audio(1, 'Original Mix', 'eac3', 'eng'), subtitle(2, 'pgs')])
      ])

      expect(result[0].compactLabel).assertEqual('1080PHDR')
      expect(result[0].audioOptions[0].title).assertEqual('英语音频')
      expect(result[0].audioOptions[0].compactLabel).assertEqual('英语音频 EAC3 5.1')
      expect(result[0].subtitleOptions[0].title).assertEqual('中文字幕')
      expect(adapter.episodeLabel(7, 1)).assertEqual('第 7 季  第 1 集')
      expect(VideoDetailPresentationAdapterRegistry.instance()
        .resolve(MediaProviderKind.Jellyfin).provider).assertEqual(MediaProviderKind.Jellyfin)
      expect(VideoDetailPresentationAdapterRegistry.instance()
        .resolve(MediaProviderKind.FeiniuVideo).provider).assertEqual(MediaProviderKind.FeiniuVideo)
    })

    it('usesLocalizedLanguageAsThePrimaryFeiniuTrackTitle', 0, () => {
      const adapter = new FeiniuVideoDetailPresentationAdapter()
      const result = adapter.present([
        source(MediaProviderKind.FeiniuVideo, 'tracks', [video(0, '4K', 'SDR'),
          audio(1, '国语', 'eac3', 'chi'), subtitle(2, 'pgs')])
      ])

      expect(result[0].audioOptions[0].title).assertEqual('中文音频')
      expect(result[0].audioOptions[0].compactLabel).assertEqual('中文音频')
      expect(result[0].audioOptions[0].summary.includes('国语')).assertTrue()
      expect(result[0].subtitleOptions[0].title).assertEqual('中文字幕')
      expect(result[0].subtitleOptions[0].compactLabel).assertEqual('中文字幕')
      expect(result[0].subtitleOptions[0].summary.includes('简体中文')).assertTrue()
    })

    it('localizesFeiniuChsAndChtLanguageCodesInsteadOfShowingRawTitles', 0, () => {
      const adapter = new FeiniuVideoDetailPresentationAdapter()
      const result = adapter.present([
        source(MediaProviderKind.FeiniuVideo, 'localized-subtitles', [video(0, '4K', 'SDR'),
          audio(1, 'JPN AAC 2.0', 'aac', 'jpn', true, 'JPN'),
          subtitle(2, 'ass', 'chs', 'CHS'), subtitle(3, 'ass', 'CHT', 'CHT')])
      ])

      expect(result[0].audioOptions[0].title).assertEqual('日语音频')
      expect(result[0].audioOptions[0].compactLabel).assertEqual('日语音频')
      expect(result[0].subtitleOptions[0].title).assertEqual('简体中文字幕')
      expect(result[0].subtitleOptions[0].compactLabel).assertEqual('简体中文字幕')
      expect(result[0].subtitleOptions[0].summary.includes('CHS')).assertFalse()
      expect(result[0].subtitleOptions[1].title).assertEqual('繁体中文字幕')
      expect(result[0].subtitleOptions[1].compactLabel).assertEqual('繁体中文字幕')
      expect(result[0].subtitleOptions[1].summary.includes('CHT')).assertFalse()
    })

    it('hidesMediaInfoSectionsWhenTheProviderReturnsOnlyEmptyStreamShells', 0, () => {
      const adapter = new FeiniuVideoDetailPresentationAdapter()
      const result = adapter.present([
        source(MediaProviderKind.FeiniuVideo, 'empty', [emptyVideo(0)])
      ])

      expect(result[0].infoSections.length).assertEqual(0)
      expect(result[0].videoSummary).assertEqual('')
    })
  })
}
