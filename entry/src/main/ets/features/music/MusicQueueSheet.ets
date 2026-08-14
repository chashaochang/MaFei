import { AppStorageV2 } from '@kit.ArkUI'
import { AppUIState } from '../../entity/AppUIState'
import { AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver'
import { MusicFormatters } from './MusicFormatters'
import { MusicPlaybackCandidateStatus, MusicQueueItem } from './MusicModels'
import { MusicPlaybackController } from './MusicPlaybackController'
import { connectMusicPlaybackState, MusicPlaybackState } from './MusicPlaybackState'

@ComponentV2
export struct MusicQueueSheet {
  @Require @Param onCleared: () => void
  private appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!
  private playbackState: MusicPlaybackState = connectMusicPlaybackState()
  private controller: MusicPlaybackController = MusicPlaybackController.instance()

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable)
  }

  private primaryTextColor(): ResourceColor {
    return this.useNativeSurface() ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return this.useNativeSurface() ? $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  private jumpTo(index: number): void {
    this.controller.jumpTo(index).catch((): void => {})
  }

  private removeAt(index: number): void {
    this.controller.removeAt(index)
  }

  private confirmClear(): void {
    this.getUIContext().getPromptAction().showDialog({
      title: $r('app.string.music_clear_queue'),
      message: $r('app.string.music_clear_queue_message'),
      buttons: [
        { text: $r('app.string.music_cancel'), color: $r('app.color.text_1') },
        { text: $r('app.string.music_clear'), color: $r('app.color.main_red_color') }
      ]
    }).then((response) => {
      if (response.index !== 1) {
        return
      }
      this.controller.clear().then(() => this.onCleared()).catch((): void => {})
    }).catch((): void => {})
  }

  @Builder
  private currentIndicator(item: MusicQueueItem, current: boolean) {
    Stack() {
      if (this.playbackState.candidateItemId === item.id &&
        this.playbackState.candidateStatus === MusicPlaybackCandidateStatus.Preparing) {
        LoadingProgress()
          .width(18)
          .height(18)
          .color($r('app.color.color_main'))
      } else if (this.playbackState.candidateItemId === item.id &&
        this.playbackState.candidateStatus === MusicPlaybackCandidateStatus.Error) {
        SymbolGlyph($r('sys.symbol.exclamationmark_circle'))
          .fontSize(18)
          .fontColor([$r('app.color.main_red_color')])
      } else if (current) {
        SymbolGlyph($r('sys.symbol.music_badge_wave_2'))
          .fontSize(18)
          .fontColor([$r('app.color.color_main')])
      }
    }
    .width(24)
    .height(24)
  }

  @Builder
  private queueRow(item: MusicQueueItem, index: number) {
    Row({ space: 10 }) {
      Row({ space: 10 }) {
        this.currentIndicator(item, index === this.playbackState.currentIndex)
        Column({ space: 2 }) {
          Text(item.title)
            .width('100%')
            .fontSize(15)
            .fontWeight(index === this.playbackState.currentIndex ? FontWeight.Medium : FontWeight.Regular)
            .fontColor(this.primaryTextColor())
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
          Text(item.artist || item.album)
            .width('100%')
            .fontSize(11)
            .fontColor(this.secondaryTextColor())
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
        }
        .layoutWeight(1)
        .alignItems(HorizontalAlign.Start)
        Text(MusicFormatters.duration(item.durationMs))
          .fontSize(11)
          .fontColor(this.secondaryTextColor())
      }
      .layoutWeight(1)
      .height(58)
      .onClick(() => this.jumpTo(index))
      if (index !== this.playbackState.currentIndex) {
        Button({ type: ButtonType.Circle, stateEffect: true }) {
          SymbolGlyph($r('sys.symbol.xmark'))
            .fontSize(16)
            .fontColor([this.secondaryTextColor()])
        }
        .width(36)
        .height(36)
        .buttonStyle(ButtonStyleMode.TEXTUAL)
        .backgroundColor(Color.Transparent)
        .enabled(this.playbackState.candidateStatus === MusicPlaybackCandidateStatus.None)
        .accessibilityText($r('app.string.music_remove'))
        .onClick(() => this.removeAt(index))
      }
    }
    .width('100%')
    .height(58)
    .padding({ left: 16, right: 12 })
  }

  build() {
    Column() {
      Row() {
        Text($r('app.string.music_queue'))
          .fontSize(20)
          .fontWeight(FontWeight.Bold)
          .fontColor(this.primaryTextColor())
          .layoutWeight(1)
        Button($r('app.string.music_clear'), { type: ButtonType.Normal })
          .height(40)
          .borderRadius(20)
          .buttonStyle(ButtonStyleMode.TEXTUAL)
          .backgroundColor(Color.Transparent)
          .fontColor($r('app.color.main_red_color'))
          .onClick(() => this.confirmClear())
      }
      .width('100%')
      .height(56)
      .padding({ left: 20, right: 20 })

      List() {
        ForEach(this.playbackState.queue, (item: MusicQueueItem, index: number) => {
          ListItem() {
            this.queueRow(item, index)
          }
        }, (item: MusicQueueItem, index: number) => `${item.id}-${index}`)
      }
      .width('100%')
      .layoutWeight(1)
      .scrollBar(BarState.Off)
      .nestedScroll({
        scrollForward: NestedScrollMode.SELF_FIRST,
        scrollBackward: NestedScrollMode.PARENT_FIRST
      })
    }
    .width('100%')
    .height('100%')
    .backgroundColor(Color.Transparent)
  }
}
