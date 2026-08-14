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
export struct MusicTrackRow {
  @Require @Param track: MusicTrack
  @Require @Param index: number
  @Require @Param nativeSurface: boolean
  @Param showDivider: boolean = true
  @Param onTrackClick: (track: MusicTrack, index: number) => void = (_track: MusicTrack, _index: number) => {}
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

  @Builder
  private leading() {
    Stack() {
      if (this.isPreparing()) {
        LoadingProgress()
          .width(18)
          .height(18)
          .color($r('app.color.color_main'))
      } else if (this.isPlaying()) {
        SymbolGlyph($r('sys.symbol.music_badge_wave_2'))
          .fontSize(18)
          .fontColor([$r('app.color.color_main')])
      } else if (this.hasError()) {
        SymbolGlyph($r('sys.symbol.exclamationmark_circle'))
          .fontSize(18)
          .fontColor([$r('app.color.main_red_color')])
      } else {
        Text(this.trackNumber())
          .fontSize(12)
          .fontColor(this.isCurrent() ? $r('app.color.color_main') : this.secondaryTextColor())
          .textAlign(TextAlign.Center)
      }
    }
    .width(26)
    .height(26)
  }

  @Builder
  private playbackError() {
    if (this.hasError()) {
      Row({ space: 8 }) {
        Text(musicPlaybackErrorText(this.visibleErrorCode()))
          .layoutWeight(1)
          .fontSize(11)
          .fontColor(this.secondaryTextColor())
          .maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        Button($r('app.string.music_retry'), { type: ButtonType.Normal })
          .height(34)
          .borderRadius(17)
          .buttonStyle(ButtonStyleMode.TEXTUAL)
          .onClick(() => this.controller.retryCurrent().catch((): void => {}))
        if (this.controller.canSkipFailed()) {
          Button($r('app.string.music_skip'), { type: ButtonType.Normal })
            .height(34)
            .borderRadius(17)
            .buttonStyle(ButtonStyleMode.TEXTUAL)
            .onClick(() => this.controller.skipFailed().catch((): void => {}))
        }
      }
      .width('100%')
      .padding({ left: 38, right: 2, bottom: 8 })
    }
  }

  build() {
    Column() {
      Row({ space: 12 }) {
        this.leading()
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
      this.playbackError()
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
  @Param onTrackClick: (track: MusicTrack, index: number) => void = (_track: MusicTrack, _index: number) => {}
  @Param onReachEnd: () => void = () => {}
  @Param onRetryLoadMore: () => void = () => {}
  @Event onContentScrollStop: () => void = () => {}

  private secondaryTextColor(): ResourceColor {
    return this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  @Builder
  private footer() {
    if (this.loadingMore) {
      Row({ space: 8 }) {
        LoadingProgress().width(20).height(20)
        Text($r('app.string.music_loading_more'))
          .fontSize(13)
          .fontColor(this.secondaryTextColor())
      }
      .height(52)
      .justifyContent(FlexAlign.Center)
    } else if (this.loadMoreFailed) {
      Column({ space: 6 }) {
        Text(musicBrowseErrorText(this.loadMoreErrorCode))
          .fontSize(12)
          .fontColor(this.secondaryTextColor())
        Button($r('app.string.music_retry'), { type: ButtonType.Normal })
          .height(40)
          .borderRadius(20)
          .backgroundColor(Color.Transparent)
          .fontColor(this.secondaryTextColor())
          .onClick(() => this.onRetryLoadMore())
      }
      .padding({ top: 8, bottom: 8 })
    }
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
          this.footer()
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
