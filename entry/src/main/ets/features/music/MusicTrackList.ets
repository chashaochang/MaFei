import { musicBrowseErrorText, musicPlaybackErrorText } from './MusicErrorText'
import { MusicFormatters } from './MusicFormatters'
import {
  MusicBrowseErrorCode,
  MusicPlaybackCandidateStatus,
  MusicPlaybackErrorCode,
  MusicPlaybackStatus,
  MusicTrack
} from './MusicModels'
import { MusicPlaybackController } from './MusicPlaybackController'
import { connectMusicPlaybackState, MusicPlaybackState } from './MusicPlaybackState'

@ComponentV2
struct MusicTrackLeading {
  @Require @Param preparing: boolean
  @Require @Param playing: boolean
  @Require @Param error: boolean
  @Require @Param current: boolean
  @Require @Param trackNumber: string
  @Require @Param secondaryTextColor: ResourceColor

  build() {
    Stack() {
      if (this.preparing) {
        LoadingProgress()
          .width(18)
          .height(18)
          .color($r('app.color.color_main'))
      } else if (this.playing) {
        SymbolGlyph($r('sys.symbol.music_badge_wave_2'))
          .fontSize(18)
          .fontColor([$r('app.color.color_main')])
      } else if (this.error) {
        SymbolGlyph($r('sys.symbol.exclamationmark_circle'))
          .fontSize(18)
          .fontColor([$r('app.color.main_red_color')])
      } else {
        Text(this.trackNumber)
          .fontSize(12)
          .fontColor(this.current ? $r('app.color.color_main') : this.secondaryTextColor)
          .textAlign(TextAlign.Center)
      }
    }
    .width(26)
    .height(26)
  }
}

@ComponentV2
struct MusicPlaybackErrorRow {
  @Require @Param errorCode: MusicPlaybackErrorCode
  @Require @Param secondaryTextColor: ResourceColor
  @Require @Param canSkip: boolean
  @Event onRetry: () => void = () => {}
  @Event onSkip: () => void = () => {}

  build() {
    Row({ space: 8 }) {
      Text(musicPlaybackErrorText(this.errorCode))
        .layoutWeight(1)
        .fontSize(11)
        .fontColor(this.secondaryTextColor)
        .maxLines(2)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
      Button($r('app.string.music_retry'), { type: ButtonType.Normal })
        .height(34)
        .borderRadius(17)
        .buttonStyle(ButtonStyleMode.TEXTUAL)
        .onClick(() => this.onRetry())
      if (this.canSkip) {
        Button($r('app.string.music_skip'), { type: ButtonType.Normal })
          .height(34)
          .borderRadius(17)
          .buttonStyle(ButtonStyleMode.TEXTUAL)
          .onClick(() => this.onSkip())
      }
    }
    .width('100%')
    .padding({ left: 38, right: 2, bottom: 8 })
  }
}

@ComponentV2
struct MusicTrackListFooter {
  @Require @Param loading: boolean
  @Require @Param failed: boolean
  @Require @Param errorCode: MusicBrowseErrorCode
  @Require @Param secondaryTextColor: ResourceColor
  @Event onRetry: () => void = () => {}

  build() {
    Column() {
      if (this.loading) {
        Row({ space: 8 }) {
          LoadingProgress().width(20).height(20)
          Text($r('app.string.music_loading_more'))
            .fontSize(13)
            .fontColor(this.secondaryTextColor)
        }
        .height(52)
        .justifyContent(FlexAlign.Center)
      } else if (this.failed) {
        Column({ space: 6 }) {
          Text(musicBrowseErrorText(this.errorCode))
            .fontSize(12)
            .fontColor(this.secondaryTextColor)
          Button($r('app.string.music_retry'), { type: ButtonType.Normal })
            .height(40)
            .borderRadius(20)
            .backgroundColor(Color.Transparent)
            .fontColor(this.secondaryTextColor)
            .onClick(() => this.onRetry())
        }
        .padding({ top: 8, bottom: 8 })
      }
    }
    .width('100%')
  }
}

@ComponentV2
export struct MusicTrackRow {
  @Require @Param track: MusicTrack
  @Require @Param index: number
  @Require @Param nativeSurface: boolean
  @Param showDivider: boolean = true
  @Event onTrackClick: (track: MusicTrack, index: number) => void = (_track: MusicTrack, _index: number) => {}
  private playbackState: MusicPlaybackState = connectMusicPlaybackState()
  private controller: MusicPlaybackController = MusicPlaybackController.instance()

  private primaryTextColor(): ResourceColor {
    return this.nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  private trackNumber(): string {
    return (this.track.trackNumber > 0 ? this.track.trackNumber : this.index + 1).toString()
  }

  private subtitle(): string {
    if (this.track.artist.length > 0 && this.track.album.length > 0) {
      return `${this.track.artist} · ${this.track.album}`
    }
    return this.track.artist || this.track.album
  }

  private isCurrent(): boolean {
    return this.playbackState.currentItem()?.id === this.track.id
  }

  private isPreparing(): boolean {
    return (this.playbackState.candidateItemId === this.track.id &&
      this.playbackState.candidateStatus === MusicPlaybackCandidateStatus.Preparing) ||
      (this.isCurrent() && this.playbackState.status === MusicPlaybackStatus.Buffering)
  }

  private isPlaying(): boolean {
    return this.isCurrent() && this.playbackState.status === MusicPlaybackStatus.Playing
  }

  private hasError(): boolean {
    return (this.playbackState.candidateItemId === this.track.id &&
      this.playbackState.candidateStatus === MusicPlaybackCandidateStatus.Error) ||
      (this.isCurrent() && this.playbackState.status === MusicPlaybackStatus.Error)
  }

  private visibleErrorCode(): MusicPlaybackErrorCode {
    if (this.playbackState.candidateItemId === this.track.id &&
      this.playbackState.candidateStatus === MusicPlaybackCandidateStatus.Error) {
      return this.playbackState.candidateErrorCode
    }
    return this.playbackState.errorCode
  }

  build() {
    Column() {
      Row({ space: 12 }) {
        MusicTrackLeading({
          preparing: this.isPreparing(),
          playing: this.isPlaying(),
          error: this.hasError(),
          current: this.isCurrent(),
          trackNumber: this.trackNumber(),
          secondaryTextColor: this.secondaryTextColor()
        })
        Column({ space: 3 }) {
          Text(this.track.title)
            .width('100%')
            .fontSize(14)
            .fontWeight(FontWeight.Medium)
            .fontColor(this.isCurrent() ? $r('app.color.color_main') : this.primaryTextColor())
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
          if (this.subtitle().length > 0) {
            Text(this.subtitle())
              .width('100%')
              .fontSize(11)
              .fontColor(this.secondaryTextColor())
              .maxLines(1)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
          }
        }
        .layoutWeight(1)
        .alignItems(HorizontalAlign.Start)
        Text(MusicFormatters.duration(this.track.durationMs))
          .fontSize(11)
          .fontColor(this.secondaryTextColor())
      }
      .width('100%')
      .height(64)
      .padding({ left: 2, right: 2 })
      .alignItems(VerticalAlign.Center)
      .onClick(() => this.onTrackClick(this.track, this.index))
      if (this.hasError()) {
        MusicPlaybackErrorRow({
          errorCode: this.visibleErrorCode(),
          secondaryTextColor: this.secondaryTextColor(),
          canSkip: this.controller.canSkipFailed(),
          onRetry: () => this.controller.retryCurrent().catch((): void => {}),
          onSkip: () => this.controller.skipFailed().catch((): void => {})
        })
      }
      if (this.showDivider) {
        Divider()
          .strokeWidth(0.5)
          .color(this.nativeSurface ? $r('sys.color.comp_divider') : $r('app.color.border'))
          .margin({ left: 38 })
      }
    }
    .width('100%')
  }
}

@ComponentV2
export struct MusicTrackList {
  @Require @Param tracks: MusicTrack[]
  @Require @Param nativeSurface: boolean
  @Require @Param scroller: Scroller
  @Param loadingMore: boolean = false
  @Param loadMoreFailed: boolean = false
  @Param loadMoreErrorCode: MusicBrowseErrorCode = MusicBrowseErrorCode.RequestFailed
  @Event onTrackClick: (track: MusicTrack, index: number) => void = (_track: MusicTrack, _index: number) => {}
  @Event onReachEnd: () => void = () => {}
  @Event onRetryLoadMore: () => void = () => {}
  @Event onContentScrollStop: () => void = () => {}

  private secondaryTextColor(): ResourceColor {
    return this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  build() {
    List({ scroller: this.scroller }) {
      ForEach(this.tracks, (track: MusicTrack, index: number) => {
        ListItem() {
          MusicTrackRow({
            track: track,
            index: index,
            nativeSurface: this.nativeSurface,
            showDivider: index < this.tracks.length - 1,
            onTrackClick: (selectedTrack: MusicTrack, selectedIndex: number) =>
              this.onTrackClick(selectedTrack, selectedIndex)
          })
        }
      }, (track: MusicTrack) => track.id)
      if (this.loadingMore || this.loadMoreFailed) {
        ListItem() {
          MusicTrackListFooter({
            loading: this.loadingMore,
            failed: this.loadMoreFailed,
            errorCode: this.loadMoreErrorCode,
            secondaryTextColor: this.secondaryTextColor(),
            onRetry: () => this.onRetryLoadMore()
          })
        }
      }
      ListItem() {
        Row().height(20)
      }
    }
    .width('100%')
    .height('100%')
    .scrollBar(BarState.Off)
    .onReachEnd(() => this.onReachEnd())
    .onScrollStop(() => this.onContentScrollStop())
  }
}
