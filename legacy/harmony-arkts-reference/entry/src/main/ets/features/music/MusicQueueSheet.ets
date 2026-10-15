import { AppStorageV2 } from '@kit.ArkUI'
import { AppUIState } from '../../entity/AppUIState'
import { AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver'
import { MusicFormatters } from './MusicFormatters'
import { MusicPlaybackCandidateStatus, MusicQueueItem } from './MusicModels'
import { MusicPlaybackController } from './MusicPlaybackController'
import { connectMusicPlaybackState, MusicPlaybackState } from './MusicPlaybackState'

@ComponentV2
struct MusicQueueRow {
  @Require @Param item: MusicQueueItem
  @Require @Param index: number
  @Require @Param currentIndex: number
  @Require @Param candidateItemId: string
  @Require @Param candidateStatus: MusicPlaybackCandidateStatus
  @Require @Param primaryTextColor: ResourceColor
  @Require @Param secondaryTextColor: ResourceColor
  @Event onPressed: () => void = () => {}
  @Event onRemove: () => void = () => {}

  build() {
    Row({ space: 10 }) {
      Row({ space: 10 }) {
        Stack() {
          if (this.candidateItemId === this.item.id &&
            this.candidateStatus === MusicPlaybackCandidateStatus.Preparing) {
            LoadingProgress()
              .width(18)
              .height(18)
              .color($r('app.color.color_main'))
          } else if (this.candidateItemId === this.item.id &&
            this.candidateStatus === MusicPlaybackCandidateStatus.Error) {
            SymbolGlyph($r('sys.symbol.exclamationmark_circle'))
              .fontSize(18)
              .fontColor([$r('app.color.main_red_color')])
          } else if (this.index === this.currentIndex) {
            SymbolGlyph($r('sys.symbol.music_badge_wave_2'))
              .fontSize(18)
              .fontColor([$r('app.color.color_main')])
          }
        }
        .width(24)
        .height(24)
        Column({ space: 2 }) {
          Text(this.item.title)
            .width('100%')
            .fontSize(15)
            .fontWeight(this.index === this.currentIndex ? FontWeight.Medium : FontWeight.Regular)
            .fontColor(this.primaryTextColor)
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
          Text(this.item.artist || this.item.album)
            .width('100%')
            .fontSize(11)
            .fontColor(this.secondaryTextColor)
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
        }
        .layoutWeight(1)
        .alignItems(HorizontalAlign.Start)
        Text(MusicFormatters.duration(this.item.durationMs))
          .fontSize(11)
          .fontColor(this.secondaryTextColor)
      }
      .layoutWeight(1)
      .height(58)
      .onClick(() => this.onPressed())
      if (this.index !== this.currentIndex) {
        Button({ type: ButtonType.Circle, stateEffect: true }) {
          SymbolGlyph($r('sys.symbol.xmark'))
            .fontSize(16)
            .fontColor([this.secondaryTextColor])
        }
        .width(36)
        .height(36)
        .buttonStyle(ButtonStyleMode.TEXTUAL)
        .backgroundColor(Color.Transparent)
        .enabled(this.candidateStatus === MusicPlaybackCandidateStatus.None)
        .accessibilityText($r('app.string.music_remove'))
        .onClick(() => this.onRemove())
      }
    }
    .width('100%')
    .height(58)
    .padding({ left: 16, right: 12 })
  }
}

@ComponentV2
export struct MusicQueueSheet {
  @Event onCleared: () => void = () => {}
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
            MusicQueueRow({
              item,
              index,
              currentIndex: this.playbackState.currentIndex,
              candidateItemId: this.playbackState.candidateItemId,
              candidateStatus: this.playbackState.candidateStatus,
              primaryTextColor: this.primaryTextColor(),
              secondaryTextColor: this.secondaryTextColor(),
              onPressed: () => this.jumpTo(index),
              onRemove: () => this.removeAt(index)
            })
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
