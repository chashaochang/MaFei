import { HMRouter, HMRouterMgr } from '@hadss/hmrouter'
import { AppStorageV2 } from '@kit.ArkUI'
import { ImageKnifeComponent } from '@ohos/imageknifepro'
import { RouterConsts } from '../../common/RouterConsts'
import { UIConstants } from '../../common/UIConstants'
import { AppRouteDestination } from '../../component/AppRouteDestination'
import { AppUIState } from '../../entity/AppUIState'
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver'
import { musicPlaybackErrorText } from './MusicErrorText'
import { MusicFormatters } from './MusicFormatters'
import { MusicPlayerLifecycle } from './MusicHostLifecycle'
import {
  MusicPlaybackCandidateStatus,
  MusicPlaybackErrorCode,
  MusicPlaybackStatus,
  MusicQueueItem,
  MusicRepeatMode
} from './MusicModels'
import { MusicPlaybackController } from './MusicPlaybackController'
import { connectMusicPlaybackState, MusicPlaybackState } from './MusicPlaybackState'
import { MusicPlayerViewModel } from './MusicPlayerViewModel'
import { MusicQueueSheet } from './MusicQueueSheet'

@HMRouter({
  pageUrl: RouterConsts.MusicPlayerPage,
  useNavDst: true,
  lifecycle: 'musicPlayerLifecycle'
})
@ComponentV2
export struct MusicPlayerPage {
  private appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!
  private playbackState: MusicPlaybackState = connectMusicPlaybackState()
  private controller: MusicPlaybackController = MusicPlaybackController.instance()
  @Local viewModel: MusicPlayerViewModel = new MusicPlayerViewModel()
  @Local showQueue: boolean = false
  @Local seeking: boolean = false
  @Local seekPositionMs: number = 0
  private readonly scroller: Scroller = new Scroller()
  private musicLifecycle?: MusicPlayerLifecycle =
    HMRouterMgr.getCurrentLifecycleOwner()?.getLifecycle() as MusicPlayerLifecycle

  private currentItem(): MusicQueueItem | undefined {
    return this.playbackState.currentItem()
  }

  private hasArtwork(): boolean {
    return (this.currentItem()?.imageUrl || '').length > 0
  }

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable)
  }

  private isWide(): boolean {
    return this.appUIState.deviceWidth >= 840
  }

  private contentMaxWidth(): number {
    if (this.isWide()) {
      return 1080
    }
    return this.appUIState.deviceWidth >= 600 ? 560 : 520
  }

  private coverSize(): number {
    const width = Math.max(360, this.appUIState.deviceWidth)
    if (this.isWide()) {
      return Math.min(420, Math.max(320, width * 0.34))
    }
    if (width >= 600) {
      return 360
    }
    return Math.min(360, Math.max(228, width - 64))
  }

  private primaryTextColor(): ResourceColor {
    if (this.hasArtwork()) {
      return Color.White
    }
    return this.useNativeSurface() ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    if (this.hasArtwork()) {
      return Color.White
    }
    return this.useNativeSurface() ? $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  private visiblePositionMs(): number {
    return this.seeking ? this.seekPositionMs : this.playbackState.positionMs
  }

  private durationMs(): number {
    return Math.max(this.playbackState.durationMs, this.currentItem()?.durationMs || 0)
  }

  private isPlaying(): boolean {
    return this.playbackState.status === MusicPlaybackStatus.Playing ||
      this.playbackState.status === MusicPlaybackStatus.Buffering
  }

  private hasPlaybackError(): boolean {
    if (this.playbackState.candidateStatus === MusicPlaybackCandidateStatus.Preparing) {
      return false
    }
    return this.playbackState.status === MusicPlaybackStatus.Error ||
      this.playbackState.candidateStatus === MusicPlaybackCandidateStatus.Error
  }

  private visibleErrorCode(): MusicPlaybackErrorCode {
    if (this.playbackState.candidateStatus === MusicPlaybackCandidateStatus.Error) {
      return this.playbackState.candidateErrorCode
    }
    return this.playbackState.errorCode
  }

  private togglePlayback(): void {
    if (this.playbackState.status === MusicPlaybackStatus.Preparing) {
      return
    }
    if (this.isPlaying()) {
      this.controller.pause().catch((): void => {})
      return
    }
    if (this.playbackState.status === MusicPlaybackStatus.Error) {
      this.controller.retryCurrent().catch((): void => {})
      return
    }
    this.controller.play().catch((): void => {})
  }

  private seek(value: number, mode: SliderChangeMode): void {
    const duration = this.durationMs()
    if (duration <= 0) {
      return
    }
    this.seekPositionMs = Math.max(0, Math.min(value, duration))
    if (mode === SliderChangeMode.Begin || mode === SliderChangeMode.Moving) {
      this.seeking = true
      return
    }
    this.seeking = false
    this.controller.seek(this.seekPositionMs).catch((): void => {})
  }

  private toggleFavorite(): void {
    const item = this.currentItem()
    if (!item) {
      return
    }
    this.viewModel.setCurrentFavorite(!item.favorite).then((updated: boolean) => {
      if (!updated) {
        this.getUIContext().getPromptAction().showToast({
          message: $r('app.string.music_favorite_failed')
        })
      }
    })
  }

  private repeatSymbol(): Resource {
    return this.playbackState.repeatMode === MusicRepeatMode.One ?
      $r('sys.symbol.repeat_1') : $r('sys.symbol.repeat')
  }

  private repeatAccessibilityText(): Resource {
    if (this.playbackState.repeatMode === MusicRepeatMode.One) {
      return $r('app.string.music_repeat_one')
    }
    if (this.playbackState.repeatMode === MusicRepeatMode.All) {
      return $r('app.string.music_repeat_all')
    }
    return $r('app.string.music_repeat_off')
  }

  private menus(): Array<NavigationMenuItem> {
    if (!this.currentItem()) {
      return []
    }
    return [{
      value: $r('app.string.music_queue'),
      icon: $r('sys.symbol.music_note_list'),
      action: () => this.showQueue = true
    }]
  }

  @Builder
  private backdrop(item: MusicQueueItem) {
    Stack() {
      if (item.imageUrl.length > 0) {
        ImageKnifeComponent({
          imageKnifeOption: {
            loadSrc: item.imageUrl,
            objectFit: ImageFit.Cover
          }
        })
          .width('100%')
          .height('100%')
          .scale({ x: 1.18, y: 1.18 })
          .blur(128)
          .opacity(0.98)
        Stack()
          .width('100%')
          .height('100%')
          .backgroundColor($r('app.color.reload_title_color'))
      } else {
        Stack() {
          SymbolGlyph($r('sys.symbol.music'))
            .fontSize(180)
            .fontColor([this.secondaryTextColor()])
            .opacity(0.14)
        }
        .width('100%')
        .height('100%')
        .backgroundColor(AppThemeSurfaceResolver.destinationCanvasBackground(
          this.appUIState.themeStyle,
          this.appUIState.nativeThemeAvailable
        ))
      }
    }
    .width('100%')
    .height('100%')
    .hitTestBehavior(HitTestMode.None)
  }

  @Builder
  private cover(item: MusicQueueItem) {
    if (item.imageUrl.length > 0) {
      ImageKnifeComponent({
        imageKnifeOption: {
          loadSrc: item.imageUrl,
          objectFit: ImageFit.Cover,
          border: { radius: 16 },
          placeholderSrc: $r('app.media.ic_audio'),
          placeholderObjectFit: ImageFit.CENTER
        }
      })
        .width(this.coverSize())
        .height(this.coverSize())
        .borderRadius(16)
        .backgroundColor($r('sys.color.comp_background_tertiary'))
    } else {
      Stack() {
        SymbolGlyph($r('sys.symbol.music'))
          .fontSize(88)
          .fontColor([this.secondaryTextColor()])
      }
      .width(this.coverSize())
      .height(this.coverSize())
      .borderRadius(16)
      .backgroundColor(this.useNativeSurface() ?
        $r('sys.color.comp_background_tertiary') : $r('app.color.component_bg'))
    }
  }

  @Builder
  private progress() {
    Column({ space: 5 }) {
      Slider({
        value: this.visiblePositionMs(),
        min: 0,
        max: Math.max(1, this.durationMs()),
        step: 1000,
        style: SliderStyle.OutSet
      })
        .width('100%')
        .height(28)
        .blockSize({ width: 16, height: 16 })
        .blockColor(this.hasArtwork() ? Color.White : $r('app.color.color_main'))
        .trackColor(this.hasArtwork() ?
          $r('app.color.media_progress_track_on_image') : $r('sys.color.comp_background_tertiary'))
        .selectedColor($r('app.color.color_main'))
        .trackThickness(4)
        .trackBorderRadius(2)
        .selectedBorderRadius(2)
        .onChange((value: number, mode: SliderChangeMode) => this.seek(value, mode))
      Row() {
        Text(MusicFormatters.duration(this.visiblePositionMs()))
          .fontSize(11)
          .fontColor(this.secondaryTextColor())
          .opacity(this.hasArtwork() ? 0.82 : 1)
        Blank().layoutWeight(1)
        Text(MusicFormatters.duration(this.durationMs()))
          .fontSize(11)
          .fontColor(this.secondaryTextColor())
          .opacity(this.hasArtwork() ? 0.82 : 1)
      }
      .width('100%')
    }
    .width('100%')
  }

  @Builder
  private secondaryControl(symbol: Resource, accessibilityText: Resource,
    action: () => void, enabled: boolean = true, active: boolean = false) {
    if (this.useNativeSurface()) {
      Button({ type: ButtonType.Circle, stateEffect: true }) {
        SymbolGlyph(symbol)
          .fontSize(24)
          .fontColor([active ? $r('app.color.color_main') : this.primaryTextColor()])
      }
      .width(52)
      .height(52)
      .buttonStyle(ButtonStyleMode.TEXTUAL)
      .backgroundColor(Color.Transparent)
      .systemMaterial(AppThemeSurfaceResolver.material(AppThemeMaterialRole.InteractiveFloating))
      .enabled(enabled)
      .accessibilityText(accessibilityText)
      .onClick(action)
    } else {
      Button({ type: ButtonType.Circle, stateEffect: true }) {
        SymbolGlyph(symbol)
          .fontSize(24)
          .fontColor([active ? $r('app.color.color_main') : this.primaryTextColor()])
      }
      .width(52)
      .height(52)
      .backgroundColor($r('app.color.bg_tab_item'))
      .enabled(enabled)
      .accessibilityText(accessibilityText)
      .onClick(action)
    }
  }

  @Builder
  private playControl() {
    if (this.useNativeSurface()) {
      Button({ type: ButtonType.Circle, stateEffect: true }) {
        if (this.playbackState.status === MusicPlaybackStatus.Preparing) {
          LoadingProgress()
            .width(28)
            .height(28)
            .color(Color.White)
        } else {
          SymbolGlyph(this.isPlaying() ? $r('sys.symbol.pause_fill') : $r('sys.symbol.play_fill'))
            .fontSize(32)
            .fontColor([Color.White])
        }
      }
      .width(68)
      .height(68)
      .buttonStyle(ButtonStyleMode.TEXTUAL)
      .backgroundColor(Color.Transparent)
      .systemMaterial(AppThemeSurfaceResolver.material(AppThemeMaterialRole.PrimaryInteractive))
      .accessibilityText(this.isPlaying() ?
        $r('app.string.music_pause') : $r('app.string.music_play'))
      .onClick(() => this.togglePlayback())
    } else {
      Button({ type: ButtonType.Circle, stateEffect: true }) {
        if (this.playbackState.status === MusicPlaybackStatus.Preparing) {
          LoadingProgress().width(28).height(28).color(Color.White)
        } else {
          SymbolGlyph(this.isPlaying() ? $r('sys.symbol.pause_fill') : $r('sys.symbol.play_fill'))
            .fontSize(32)
            .fontColor([Color.White])
        }
      }
      .width(68)
      .height(68)
      .backgroundColor($r('app.color.color_main'))
      .accessibilityText(this.isPlaying() ?
        $r('app.string.music_pause') : $r('app.string.music_play'))
      .onClick(() => this.togglePlayback())
    }
  }

  @Builder
  private primaryControls() {
    Row({ space: 24 }) {
      this.secondaryControl(
        $r('sys.symbol.backward_end_fill'),
        $r('app.string.music_previous'),
        () => this.controller.previous().catch((): void => {}),
        this.playbackState.currentIndex > 0
      )
      this.playControl()
      this.secondaryControl(
        $r('sys.symbol.forward_end_fill'),
        $r('app.string.music_next'),
        () => this.controller.next().catch((): void => {}),
        this.playbackState.currentIndex >= 0 &&
          this.playbackState.currentIndex < this.playbackState.queue.length - 1
      )
    }
    .width('100%')
    .justifyContent(FlexAlign.Center)
    .alignItems(VerticalAlign.Center)
  }

  @Builder
  private secondaryControls(item: MusicQueueItem) {
    Row({ space: 22 }) {
      this.secondaryControl(
        item.favorite ? $r('sys.symbol.heart_fill') : $r('sys.symbol.heart'),
        item.favorite ? $r('app.string.music_unfavorite') : $r('app.string.music_favorite'),
        () => this.toggleFavorite(),
        !this.viewModel.favoritePending,
        item.favorite
      )
      this.secondaryControl(
        $r('sys.symbol.music_note_list'),
        $r('app.string.music_queue'),
        () => this.showQueue = true
      )
      this.secondaryControl(
        this.repeatSymbol(),
        this.repeatAccessibilityText(),
        () => this.controller.cycleRepeatMode(),
        true,
        this.playbackState.repeatMode !== MusicRepeatMode.Off
      )
    }
    .width('100%')
    .justifyContent(FlexAlign.Center)
  }

  @Builder
  private errorActions() {
    if (this.hasPlaybackError()) {
      Column({ space: 8 }) {
        Text(musicPlaybackErrorText(this.visibleErrorCode()))
          .width('100%')
          .fontSize(12)
          .fontColor(this.secondaryTextColor())
          .textAlign(TextAlign.Center)
          .maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        Row({ space: 12 }) {
          Button($r('app.string.music_retry'), { type: ButtonType.Normal })
            .height(40)
            .borderRadius(20)
            .onClick(() => this.controller.retryCurrent().catch((): void => {}))
          if (this.controller.canSkipFailed()) {
            Button($r('app.string.music_skip'), { type: ButtonType.Normal })
              .height(40)
              .borderRadius(20)
              .onClick(() => this.controller.skipFailed().catch((): void => {}))
          }
        }
      }
      .width('100%')
      .alignItems(HorizontalAlign.Center)
    }
  }

  @Builder
  private trackInformation(item: MusicQueueItem) {
    Column({ space: 8 }) {
      Text(item.title)
        .width('100%')
        .fontSize(this.isWide() ? 28 : 24)
        .fontWeight(FontWeight.Bold)
        .fontColor(this.primaryTextColor())
        .textAlign(this.isWide() ? TextAlign.Start : TextAlign.Center)
        .maxLines(2)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
      Text(item.artist)
        .width('100%')
        .fontSize(15)
        .fontColor(this.secondaryTextColor())
        .opacity(this.hasArtwork() ? 0.86 : 1)
        .textAlign(this.isWide() ? TextAlign.Start : TextAlign.Center)
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
      if (item.album.length > 0) {
        Text(item.album)
          .width('100%')
          .fontSize(12)
          .fontColor(this.secondaryTextColor())
          .opacity(this.hasArtwork() ? 0.72 : 1)
          .textAlign(this.isWide() ? TextAlign.Start : TextAlign.Center)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
      }
    }
    .width('100%')
    .alignItems(this.isWide() ? HorizontalAlign.Start : HorizontalAlign.Center)
  }

  @Builder
  private controlColumn(item: MusicQueueItem) {
    Column({ space: 20 }) {
      this.trackInformation(item)
      this.progress()
      this.errorActions()
      this.primaryControls()
      this.secondaryControls(item)
    }
    .width('100%')
    .alignItems(HorizontalAlign.Center)
  }

  @Builder
  private playerLayout(item: MusicQueueItem) {
    if (this.isWide()) {
      Row({ space: 56 }) {
        this.cover(item)
        Column() {
          this.controlColumn(item)
        }
        .width(430)
      }
      .width('100%')
      .justifyContent(FlexAlign.Center)
      .alignItems(VerticalAlign.Center)
    } else {
      Column({ space: 28 }) {
        this.cover(item)
        this.controlColumn(item)
      }
      .width('100%')
      .alignItems(HorizontalAlign.Center)
    }
  }

  @Builder
  private legacyHeader() {
    Row() {
      Button({ type: ButtonType.Circle, stateEffect: true }) {
        SymbolGlyph($r('sys.symbol.chevron_backward'))
          .fontSize(22)
          .fontColor([this.primaryTextColor()])
      }
      .width(44)
      .height(44)
      .backgroundColor($r('app.color.bg_tab_item'))
      .accessibilityText($r('app.string.music_back'))
      .onClick(() => HMRouterMgr.pop())
      Blank().layoutWeight(1)
      Button({ type: ButtonType.Circle, stateEffect: true }) {
        SymbolGlyph($r('sys.symbol.music_note_list'))
          .fontSize(22)
          .fontColor([this.primaryTextColor()])
      }
      .width(44)
      .height(44)
      .backgroundColor($r('app.color.bg_tab_item'))
      .accessibilityText($r('app.string.music_queue'))
      .onClick(() => this.showQueue = true)
    }
    .width('100%')
    .height(52)
  }

  @Builder
  private queueContent() {
    MusicQueueSheet({
      onCleared: () => this.showQueue = false
    })
  }

  @Builder
  private emptyPlayer(showLegacyHeader: boolean) {
    Column() {
      if (showLegacyHeader) {
        this.legacyHeader()
      }
      Column({ space: 12 }) {
        SymbolGlyph($r('sys.symbol.music'))
          .fontSize(72)
          .fontColor([this.secondaryTextColor()])
        Text($r('app.string.music_nothing_playing'))
          .fontSize(16)
          .fontColor(this.secondaryTextColor())
      }
      .layoutWeight(1)
      .justifyContent(FlexAlign.Center)
    }
    .width('100%')
    .height('100%')
    .padding({
      top: showLegacyHeader ? Math.max(0, this.appUIState.safeTop) + 8 :
        Math.max(0, this.appUIState.safeTop) + UIConstants.ACTION_BAR_HEIGHT,
      left: 16,
      right: 16,
      bottom: Math.max(16, this.appUIState.safeBottom)
    })
  }

  @Builder
  private playerSurface(showLegacyHeader: boolean) {
    Stack() {
      if (this.currentItem()) {
        this.backdrop(this.currentItem()!)
      }
      Scroll(this.scroller) {
        Column() {
          if (showLegacyHeader) {
            this.legacyHeader()
          }
          if (this.currentItem()) {
            this.playerLayout(this.currentItem()!)
          } else {
            this.emptyPlayer(false)
          }
        }
        .width('100%')
        .constraintSize({ maxWidth: this.contentMaxWidth() })
        .padding({
          top: showLegacyHeader ? Math.max(0, this.appUIState.safeTop) + 8 :
            Math.max(0, this.appUIState.safeTop) + UIConstants.ACTION_BAR_HEIGHT + 20,
          left: this.isWide() ? 24 : 16,
          right: this.isWide() ? 24 : 16,
          bottom: Math.max(28, this.appUIState.safeBottom + 20)
        })
        .alignItems(HorizontalAlign.Center)
      }
      .width('100%')
      .height('100%')
      .scrollBar(BarState.Off)
      .align(Alignment.Center)
    }
    .width('100%')
    .height('100%')
  }

  @Builder
  private nativePlayerContent() {
    Stack() {
      this.playerSurface(false)
    }
    .width('100%')
    .height('100%')
    .bindSheet($$this.showQueue, this.queueContent(), {
      height: '70%',
      preferType: SheetType.BOTTOM,
      showClose: false,
      dragBar: true,
      backgroundColor: Color.Transparent,
      systemMaterial: AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating)
    })
  }

  @Builder
  private legacyPlayerContent() {
    Stack() {
      this.playerSurface(true)
    }
    .width('100%')
    .height('100%')
    .bindSheet($$this.showQueue, this.queueContent(), {
      height: '70%',
      preferType: SheetType.BOTTOM,
      showClose: false,
      dragBar: true,
      backgroundColor: $r('app.color.bg_1')
    })
  }

  build() {
    AppRouteDestination({
      title: '',
      contentExtendsUnderTitleBar: true,
      heroTitleChrome: this.hasArtwork(),
      titleMaterialFollowsSystem: false,
      menus: this.menus(),
      scrollControllers: [this.scroller],
      contentBuilder: () => {
        this.nativePlayerContent()
      },
      legacyContentBuilder: () => {
        this.legacyPlayerContent()
      }
    })
  }
}
