import { HMRouter, HMRouterMgr } from '@hadss/hmrouter'
import { AppStorageV2 } from '@kit.ArkUI'
import { ImageKnifeComponent } from '@ohos/imageknifepro'
import { RouterConsts } from '../../common/RouterConsts'
import { UIConstants } from '../../common/UIConstants'
import { AppRouteDestination, AppRouteDestinationOptions } from '../../component/AppRouteDestination'
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
import { HdsNavDestination, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'
import { Constants } from '../../utils/Constants'

@ComponentV2
struct MusicSecondaryControl {
  @Require @Param symbol: Resource
  @Require @Param accessibilityLabel: Resource
  @Param controlEnabled: boolean = true
  @Param active: boolean = false
  @Param nativeSurface: boolean = false
  @Param systemMaterialAvailable: boolean = false
  @Require @Param primaryTextColor: ResourceColor
  @Event action: () => void = () => {}

  build() {
    Button({ type: ButtonType.Circle, stateEffect: true }) {
      SymbolGlyph(this.symbol)
        .fontSize(24)
        .fontColor([this.active ? $r('app.color.color_main') : this.primaryTextColor])
    }
    .width(52)
    .height(52)
    .buttonStyle(this.nativeSurface ? ButtonStyleMode.TEXTUAL : ButtonStyleMode.NORMAL)
    .backgroundColor(this.nativeSurface ? Color.Transparent : $r('app.color.bg_tab_item'))
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.InteractiveFloating,
      this.nativeSurface,
      this.systemMaterialAvailable
    ))
    .enabled(this.controlEnabled)
    .accessibilityText(this.accessibilityLabel)
    .onClick(this.action)
  }
}

@ComponentV2
struct MusicQueueSheetContent {
  @Param blurFallback: boolean = false
  @Event onCleared: () => void = () => {}

  build() {
    Column() {
      MusicQueueSheet({
        onCleared: this.onCleared
      })
    }
    .width('100%')
    .height('100%')
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.Floating, this.blurFallback, false))
  }
}

@HMRouter({
  pageUrl: RouterConsts.MusicPlayerPage,
  useNavDst: true,
  lifecycle: 'musicPlayerLifecycle'
})
@ComponentV2
export struct MusicPlayerPage {
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
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

  private closePlayer(): void {
    HMRouterMgr.pop({ navigationId: Constants.NAVIGATION_ID })
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


















  private routeOptions(): AppRouteDestinationOptions {
    return {
        title: '',
        contentExtendsUnderTitleBar: true,
        heroTitleChrome: this.hasArtwork(),
        titleMaterialFollowsSystem: false,
        menus: this.menus(),
        scrollControllers: [this.scroller]
      }
  }

  // CustomBuilderAdapter: music-player-queue-sheet
  @Builder
  private queueSheetBuilder(blurFallback: boolean) {
    MusicQueueSheetContent({
      blurFallback,
      onCleared: () => this.showQueue = false
    })
  }

  build() {
    if (this.routeDestination.usesHdsDestination()) {
      HdsNavDestination() {
        Column() {
          Blank().height(this.routeDestination.nativeContentTopInset(this.routeOptions()))
          Stack() {
            if (this.routeDestination.usesNativeChrome()) {
              Stack() {
                Stack() {
                  if (this.currentItem()) {
                    Stack() {
                      if ((this.currentItem()!).imageUrl.length > 0) {
                        ImageKnifeComponent({
                          imageKnifeOption: {
                            loadSrc: (this.currentItem()!).imageUrl,
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
                  Scroll(this.scroller) {
                    Column() {
                      if ((false)) {
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
                          .onClick(() => this.closePlayer())
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
                      if (this.currentItem()) {
                        if (this.isWide()) {
                          Row({ space: 56 }) {
                            if (((this.currentItem()!)).imageUrl.length > 0) {
                              ImageKnifeComponent({
                                imageKnifeOption: {
                                  loadSrc: ((this.currentItem()!)).imageUrl,
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
                            Column() {
                              Column({ space: 20 }) {
                                Column({ space: 8 }) {
                                  Text((((this.currentItem()!))).title)
                                    .width('100%')
                                    .fontSize(this.isWide() ? 28 : 24)
                                    .fontWeight(FontWeight.Bold)
                                    .fontColor(this.primaryTextColor())
                                    .textAlign(this.isWide() ? TextAlign.Start : TextAlign.Center)
                                    .maxLines(2)
                                    .textOverflow({ overflow: TextOverflow.Ellipsis })
                                  Text((((this.currentItem()!))).artist)
                                    .width('100%')
                                    .fontSize(15)
                                    .fontColor(this.secondaryTextColor())
                                    .opacity(this.hasArtwork() ? 0.86 : 1)
                                    .textAlign(this.isWide() ? TextAlign.Start : TextAlign.Center)
                                    .maxLines(1)
                                    .textOverflow({ overflow: TextOverflow.Ellipsis })
                                  if ((((this.currentItem()!))).album.length > 0) {
                                    Text((((this.currentItem()!))).album)
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
                                Row({ space: 24 }) {
                                  MusicSecondaryControl({
                                    symbol: $r('sys.symbol.backward_end_fill'),
                                    accessibilityLabel: $r('app.string.music_previous'),
                                    action: () => this.controller.previous().catch((): void => {}),
                                    controlEnabled: this.playbackState.currentIndex > 0,
                                    active: false,
                                    nativeSurface: this.useNativeSurface(),
                                    systemMaterialAvailable: this.appUIState.systemMaterialAvailable,
                                    primaryTextColor: this.primaryTextColor()
                                  })
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
                                    .attributeModifier(AppThemeSurfaceResolver.modifier(
                                      AppThemeMaterialRole.PrimaryInteractive, true,
                                      this.appUIState.systemMaterialAvailable))
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
                                  MusicSecondaryControl({
                                    symbol: $r('sys.symbol.forward_end_fill'),
                                    accessibilityLabel: $r('app.string.music_next'),
                                    action: () => this.controller.next().catch((): void => {}),
                                    controlEnabled: this.playbackState.currentIndex >= 0 &&
                                      this.playbackState.currentIndex < this.playbackState.queue.length - 1,
                                    active: false,
                                    nativeSurface: this.useNativeSurface(),
                                    systemMaterialAvailable: this.appUIState.systemMaterialAvailable,
                                    primaryTextColor: this.primaryTextColor()
                                  })
                                }
                                .width('100%')
                                .justifyContent(FlexAlign.Center)
                                .alignItems(VerticalAlign.Center)
                                Row({ space: 22 }) {
                                  MusicSecondaryControl({
                                    symbol: (((this.currentItem()!))).favorite ? $r('sys.symbol.heart_fill') : $r('sys.symbol.heart'),
                                    accessibilityLabel: (((this.currentItem()!))).favorite ? $r('app.string.music_unfavorite') : $r('app.string.music_favorite'),
                                    action: () => this.toggleFavorite(),
                                    controlEnabled: !this.viewModel.favoritePending,
                                    active: (((this.currentItem()!))).favorite,
                                    nativeSurface: this.useNativeSurface(),
                                    systemMaterialAvailable: this.appUIState.systemMaterialAvailable,
                                    primaryTextColor: this.primaryTextColor()
                                  })
                                  MusicSecondaryControl({
                                    symbol: $r('sys.symbol.music_note_list'),
                                    accessibilityLabel: $r('app.string.music_queue'),
                                    action: () => this.showQueue = true,
                                    controlEnabled: true,
                                    active: false,
                                    nativeSurface: this.useNativeSurface(),
                                    systemMaterialAvailable: this.appUIState.systemMaterialAvailable,
                                    primaryTextColor: this.primaryTextColor()
                                  })
                                  MusicSecondaryControl({
                                    symbol: this.repeatSymbol(),
                                    accessibilityLabel: this.repeatAccessibilityText(),
                                    action: () => this.controller.cycleRepeatMode(),
                                    controlEnabled: true,
                                    active: this.playbackState.repeatMode !== MusicRepeatMode.Off,
                                    nativeSurface: this.useNativeSurface(),
                                    systemMaterialAvailable: this.appUIState.systemMaterialAvailable,
                                    primaryTextColor: this.primaryTextColor()
                                  })
                                }
                                .width('100%')
                                .justifyContent(FlexAlign.Center)
                              }
                              .width('100%')
                              .alignItems(HorizontalAlign.Center)
                            }
                            .width(430)
                          }
                          .width('100%')
                          .justifyContent(FlexAlign.Center)
                          .alignItems(VerticalAlign.Center)
                        } else {
                          Column({ space: 28 }) {
                            if (((this.currentItem()!)).imageUrl.length > 0) {
                              ImageKnifeComponent({
                                imageKnifeOption: {
                                  loadSrc: ((this.currentItem()!)).imageUrl,
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
                            Column({ space: 20 }) {
                              Column({ space: 8 }) {
                                Text((((this.currentItem()!))).title)
                                  .width('100%')
                                  .fontSize(this.isWide() ? 28 : 24)
                                  .fontWeight(FontWeight.Bold)
                                  .fontColor(this.primaryTextColor())
                                  .textAlign(this.isWide() ? TextAlign.Start : TextAlign.Center)
                                  .maxLines(2)
                                  .textOverflow({ overflow: TextOverflow.Ellipsis })
                                Text((((this.currentItem()!))).artist)
                                  .width('100%')
                                  .fontSize(15)
                                  .fontColor(this.secondaryTextColor())
                                  .opacity(this.hasArtwork() ? 0.86 : 1)
                                  .textAlign(this.isWide() ? TextAlign.Start : TextAlign.Center)
                                  .maxLines(1)
                                  .textOverflow({ overflow: TextOverflow.Ellipsis })
                                if ((((this.currentItem()!))).album.length > 0) {
                                  Text((((this.currentItem()!))).album)
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
                              Row({ space: 24 }) {
                                MusicSecondaryControl({
                                  symbol: $r('sys.symbol.backward_end_fill'),
                                  accessibilityLabel: $r('app.string.music_previous'),
                                  action: () => this.controller.previous().catch((): void => {}),
                                  controlEnabled: this.playbackState.currentIndex > 0,
                                  active: false,
                                  nativeSurface: this.useNativeSurface(),
                                  systemMaterialAvailable: this.appUIState.systemMaterialAvailable,
                                  primaryTextColor: this.primaryTextColor()
                                })
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
                                  .attributeModifier(AppThemeSurfaceResolver.modifier(
                                    AppThemeMaterialRole.PrimaryInteractive, true,
                                    this.appUIState.systemMaterialAvailable))
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
                                MusicSecondaryControl({
                                  symbol: $r('sys.symbol.forward_end_fill'),
                                  accessibilityLabel: $r('app.string.music_next'),
                                  action: () => this.controller.next().catch((): void => {}),
                                  controlEnabled: this.playbackState.currentIndex >= 0 &&
                                    this.playbackState.currentIndex < this.playbackState.queue.length - 1,
                                  active: false,
                                  nativeSurface: this.useNativeSurface(),
                                  systemMaterialAvailable: this.appUIState.systemMaterialAvailable,
                                  primaryTextColor: this.primaryTextColor()
                                })
                              }
                              .width('100%')
                              .justifyContent(FlexAlign.Center)
                              .alignItems(VerticalAlign.Center)
                              Row({ space: 22 }) {
                                MusicSecondaryControl({
                                  symbol: (((this.currentItem()!))).favorite ? $r('sys.symbol.heart_fill') : $r('sys.symbol.heart'),
                                  accessibilityLabel: (((this.currentItem()!))).favorite ? $r('app.string.music_unfavorite') : $r('app.string.music_favorite'),
                                  action: () => this.toggleFavorite(),
                                  controlEnabled: !this.viewModel.favoritePending,
                                  active: (((this.currentItem()!))).favorite,
                                  nativeSurface: this.useNativeSurface(),
                                  systemMaterialAvailable: this.appUIState.systemMaterialAvailable,
                                  primaryTextColor: this.primaryTextColor()
                                })
                                MusicSecondaryControl({
                                  symbol: $r('sys.symbol.music_note_list'),
                                  accessibilityLabel: $r('app.string.music_queue'),
                                  action: () => this.showQueue = true,
                                  controlEnabled: true,
                                  active: false,
                                  nativeSurface: this.useNativeSurface(),
                                  systemMaterialAvailable: this.appUIState.systemMaterialAvailable,
                                  primaryTextColor: this.primaryTextColor()
                                })
                                MusicSecondaryControl({
                                  symbol: this.repeatSymbol(),
                                  accessibilityLabel: this.repeatAccessibilityText(),
                                  action: () => this.controller.cycleRepeatMode(),
                                  controlEnabled: true,
                                  active: this.playbackState.repeatMode !== MusicRepeatMode.Off,
                                  nativeSurface: this.useNativeSurface(),
                                  systemMaterialAvailable: this.appUIState.systemMaterialAvailable,
                                  primaryTextColor: this.primaryTextColor()
                                })
                              }
                              .width('100%')
                              .justifyContent(FlexAlign.Center)
                            }
                            .width('100%')
                            .alignItems(HorizontalAlign.Center)
                          }
                          .width('100%')
                          .alignItems(HorizontalAlign.Center)
                        }
                      } else {
                        Column() {
                          if ((false)) {
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
                              .onClick(() => this.closePlayer())
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
                          top: (false) ? Math.max(0, this.appUIState.safeTop) + 8 :
                            Math.max(0, this.appUIState.safeTop) + UIConstants.ACTION_BAR_HEIGHT,
                          left: 16,
                          right: 16,
                          bottom: Math.max(16, this.appUIState.safeBottom)
                        })
                      }
                    }
                    .width('100%')
                    .constraintSize({ maxWidth: this.contentMaxWidth() })
                    .padding({
                      top: (false) ? Math.max(0, this.appUIState.safeTop) + 8 :
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
              .width('100%')
              .height('100%')
              .bindSheet($$this.showQueue,
                this.queueSheetBuilder(!this.appUIState.systemMaterialAvailable),
                this.appUIState.systemMaterialAvailable ? {
                  height: '70%',
                  preferType: SheetType.BOTTOM,
                  showClose: false,
                  dragBar: true,
                  backgroundColor: Color.Transparent,
                  systemMaterial: AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating)
                } : {
                  height: '70%',
                  preferType: SheetType.BOTTOM,
                  showClose: false,
                  dragBar: true,
                  backgroundColor: Color.Transparent
                })
            } else {
              Stack() {
                Stack() {
                  if (this.currentItem()) {
                    Stack() {
                      if ((this.currentItem()!).imageUrl.length > 0) {
                        ImageKnifeComponent({
                          imageKnifeOption: {
                            loadSrc: (this.currentItem()!).imageUrl,
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
                  Scroll(this.scroller) {
                    Column() {
                      if ((true)) {
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
                          .onClick(() => this.closePlayer())
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
                      if (this.currentItem()) {
                        if (this.isWide()) {
                          Row({ space: 56 }) {
                            if (((this.currentItem()!)).imageUrl.length > 0) {
                              ImageKnifeComponent({
                                imageKnifeOption: {
                                  loadSrc: ((this.currentItem()!)).imageUrl,
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
                            Column() {
                              Column({ space: 20 }) {
                                Column({ space: 8 }) {
                                  Text((((this.currentItem()!))).title)
                                    .width('100%')
                                    .fontSize(this.isWide() ? 28 : 24)
                                    .fontWeight(FontWeight.Bold)
                                    .fontColor(this.primaryTextColor())
                                    .textAlign(this.isWide() ? TextAlign.Start : TextAlign.Center)
                                    .maxLines(2)
                                    .textOverflow({ overflow: TextOverflow.Ellipsis })
                                  Text((((this.currentItem()!))).artist)
                                    .width('100%')
                                    .fontSize(15)
                                    .fontColor(this.secondaryTextColor())
                                    .opacity(this.hasArtwork() ? 0.86 : 1)
                                    .textAlign(this.isWide() ? TextAlign.Start : TextAlign.Center)
                                    .maxLines(1)
                                    .textOverflow({ overflow: TextOverflow.Ellipsis })
                                  if ((((this.currentItem()!))).album.length > 0) {
                                    Text((((this.currentItem()!))).album)
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
                                Row({ space: 24 }) {
                                  MusicSecondaryControl({
                                    symbol: $r('sys.symbol.backward_end_fill'),
                                    accessibilityLabel: $r('app.string.music_previous'),
                                    action: () => this.controller.previous().catch((): void => {}),
                                    controlEnabled: this.playbackState.currentIndex > 0,
                                    active: false,
                                    nativeSurface: this.useNativeSurface(),
                                    systemMaterialAvailable: this.appUIState.systemMaterialAvailable,
                                    primaryTextColor: this.primaryTextColor()
                                  })
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
                                    .attributeModifier(AppThemeSurfaceResolver.modifier(
                                      AppThemeMaterialRole.PrimaryInteractive, true,
                                      this.appUIState.systemMaterialAvailable))
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
                                  MusicSecondaryControl({
                                    symbol: $r('sys.symbol.forward_end_fill'),
                                    accessibilityLabel: $r('app.string.music_next'),
                                    action: () => this.controller.next().catch((): void => {}),
                                    controlEnabled: this.playbackState.currentIndex >= 0 &&
                                      this.playbackState.currentIndex < this.playbackState.queue.length - 1,
                                    active: false,
                                    nativeSurface: this.useNativeSurface(),
                                    systemMaterialAvailable: this.appUIState.systemMaterialAvailable,
                                    primaryTextColor: this.primaryTextColor()
                                  })
                                }
                                .width('100%')
                                .justifyContent(FlexAlign.Center)
                                .alignItems(VerticalAlign.Center)
                                Row({ space: 22 }) {
                                  MusicSecondaryControl({
                                    symbol: (((this.currentItem()!))).favorite ? $r('sys.symbol.heart_fill') : $r('sys.symbol.heart'),
                                    accessibilityLabel: (((this.currentItem()!))).favorite ? $r('app.string.music_unfavorite') : $r('app.string.music_favorite'),
                                    action: () => this.toggleFavorite(),
                                    controlEnabled: !this.viewModel.favoritePending,
                                    active: (((this.currentItem()!))).favorite,
                                    nativeSurface: this.useNativeSurface(),
                                    systemMaterialAvailable: this.appUIState.systemMaterialAvailable,
                                    primaryTextColor: this.primaryTextColor()
                                  })
                                  MusicSecondaryControl({
                                    symbol: $r('sys.symbol.music_note_list'),
                                    accessibilityLabel: $r('app.string.music_queue'),
                                    action: () => this.showQueue = true,
                                    controlEnabled: true,
                                    active: false,
                                    nativeSurface: this.useNativeSurface(),
                                    systemMaterialAvailable: this.appUIState.systemMaterialAvailable,
                                    primaryTextColor: this.primaryTextColor()
                                  })
                                  MusicSecondaryControl({
                                    symbol: this.repeatSymbol(),
                                    accessibilityLabel: this.repeatAccessibilityText(),
                                    action: () => this.controller.cycleRepeatMode(),
                                    controlEnabled: true,
                                    active: this.playbackState.repeatMode !== MusicRepeatMode.Off,
                                    nativeSurface: this.useNativeSurface(),
                                    systemMaterialAvailable: this.appUIState.systemMaterialAvailable,
                                    primaryTextColor: this.primaryTextColor()
                                  })
                                }
                                .width('100%')
                                .justifyContent(FlexAlign.Center)
                              }
                              .width('100%')
                              .alignItems(HorizontalAlign.Center)
                            }
                            .width(430)
                          }
                          .width('100%')
                          .justifyContent(FlexAlign.Center)
                          .alignItems(VerticalAlign.Center)
                        } else {
                          Column({ space: 28 }) {
                            if (((this.currentItem()!)).imageUrl.length > 0) {
                              ImageKnifeComponent({
                                imageKnifeOption: {
                                  loadSrc: ((this.currentItem()!)).imageUrl,
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
                            Column({ space: 20 }) {
                              Column({ space: 8 }) {
                                Text((((this.currentItem()!))).title)
                                  .width('100%')
                                  .fontSize(this.isWide() ? 28 : 24)
                                  .fontWeight(FontWeight.Bold)
                                  .fontColor(this.primaryTextColor())
                                  .textAlign(this.isWide() ? TextAlign.Start : TextAlign.Center)
                                  .maxLines(2)
                                  .textOverflow({ overflow: TextOverflow.Ellipsis })
                                Text((((this.currentItem()!))).artist)
                                  .width('100%')
                                  .fontSize(15)
                                  .fontColor(this.secondaryTextColor())
                                  .opacity(this.hasArtwork() ? 0.86 : 1)
                                  .textAlign(this.isWide() ? TextAlign.Start : TextAlign.Center)
                                  .maxLines(1)
                                  .textOverflow({ overflow: TextOverflow.Ellipsis })
                                if ((((this.currentItem()!))).album.length > 0) {
                                  Text((((this.currentItem()!))).album)
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
                              Row({ space: 24 }) {
                                MusicSecondaryControl({
                                  symbol: $r('sys.symbol.backward_end_fill'),
                                  accessibilityLabel: $r('app.string.music_previous'),
                                  action: () => this.controller.previous().catch((): void => {}),
                                  controlEnabled: this.playbackState.currentIndex > 0,
                                  active: false,
                                  nativeSurface: this.useNativeSurface(),
                                  systemMaterialAvailable: this.appUIState.systemMaterialAvailable,
                                  primaryTextColor: this.primaryTextColor()
                                })
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
                                  .attributeModifier(AppThemeSurfaceResolver.modifier(
                                    AppThemeMaterialRole.PrimaryInteractive, true,
                                    this.appUIState.systemMaterialAvailable))
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
                                MusicSecondaryControl({
                                  symbol: $r('sys.symbol.forward_end_fill'),
                                  accessibilityLabel: $r('app.string.music_next'),
                                  action: () => this.controller.next().catch((): void => {}),
                                  controlEnabled: this.playbackState.currentIndex >= 0 &&
                                    this.playbackState.currentIndex < this.playbackState.queue.length - 1,
                                  active: false,
                                  nativeSurface: this.useNativeSurface(),
                                  systemMaterialAvailable: this.appUIState.systemMaterialAvailable,
                                  primaryTextColor: this.primaryTextColor()
                                })
                              }
                              .width('100%')
                              .justifyContent(FlexAlign.Center)
                              .alignItems(VerticalAlign.Center)
                              Row({ space: 22 }) {
                                MusicSecondaryControl({
                                  symbol: (((this.currentItem()!))).favorite ? $r('sys.symbol.heart_fill') : $r('sys.symbol.heart'),
                                  accessibilityLabel: (((this.currentItem()!))).favorite ? $r('app.string.music_unfavorite') : $r('app.string.music_favorite'),
                                  action: () => this.toggleFavorite(),
                                  controlEnabled: !this.viewModel.favoritePending,
                                  active: (((this.currentItem()!))).favorite,
                                  nativeSurface: this.useNativeSurface(),
                                  systemMaterialAvailable: this.appUIState.systemMaterialAvailable,
                                  primaryTextColor: this.primaryTextColor()
                                })
                                MusicSecondaryControl({
                                  symbol: $r('sys.symbol.music_note_list'),
                                  accessibilityLabel: $r('app.string.music_queue'),
                                  action: () => this.showQueue = true,
                                  controlEnabled: true,
                                  active: false,
                                  nativeSurface: this.useNativeSurface(),
                                  systemMaterialAvailable: this.appUIState.systemMaterialAvailable,
                                  primaryTextColor: this.primaryTextColor()
                                })
                                MusicSecondaryControl({
                                  symbol: this.repeatSymbol(),
                                  accessibilityLabel: this.repeatAccessibilityText(),
                                  action: () => this.controller.cycleRepeatMode(),
                                  controlEnabled: true,
                                  active: this.playbackState.repeatMode !== MusicRepeatMode.Off,
                                  nativeSurface: this.useNativeSurface(),
                                  systemMaterialAvailable: this.appUIState.systemMaterialAvailable,
                                  primaryTextColor: this.primaryTextColor()
                                })
                              }
                              .width('100%')
                              .justifyContent(FlexAlign.Center)
                            }
                            .width('100%')
                            .alignItems(HorizontalAlign.Center)
                          }
                          .width('100%')
                          .alignItems(HorizontalAlign.Center)
                        }
                      } else {
                        Column() {
                          if ((false)) {
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
                              .onClick(() => this.closePlayer())
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
                          top: (false) ? Math.max(0, this.appUIState.safeTop) + 8 :
                            Math.max(0, this.appUIState.safeTop) + UIConstants.ACTION_BAR_HEIGHT,
                          left: 16,
                          right: 16,
                          bottom: Math.max(16, this.appUIState.safeBottom)
                        })
                      }
                    }
                    .width('100%')
                    .constraintSize({ maxWidth: this.contentMaxWidth() })
                    .padding({
                      top: (true) ? Math.max(0, this.appUIState.safeTop) + 8 :
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
              .width('100%')
              .height('100%')
              .bindSheet($$this.showQueue, this.queueSheetBuilder(false), {
                height: '70%',
                preferType: SheetType.BOTTOM,
                showClose: false,
                dragBar: true,
                backgroundColor: $r('app.color.bg_1')
              })
            }
          }
          .width('100%')
          .layoutWeight(1)
        }
        .width('100%')
        .height('100%')
      }
      .backgroundColor(this.routeDestination.hdsBackgroundColor())
      .titleMode(HdsNavDestinationTitleMode.MINI)
      .titleBar(this.routeDestination.nativeTitleBarOptions(this.routeOptions()))
      .hideTitleBar(this.routeDestination.hdsTitleBarHidden(this.routeOptions()))
      .hideBackButton(!this.routeDestination.backButtonVisible(this.routeOptions()))
      .hideToolBar(true)
      .bindToScrollable(this.routeDestination.hdsBoundScrollers(this.routeOptions()))
      .onWillAppear(() => this.routeDestination.helper.onWillAppear())
      .onAppear(() => this.routeDestination.helper.onAppear())
      .onWillShow(() => this.routeDestination.helper.onWillShow())
      .onShown(() => this.routeDestination.helper.onShown())
      .onWillHide(() => this.routeDestination.helper.onWillHide())
      .onHidden(() => this.routeDestination.helper.onHidden())
      .onWillDisappear(() => this.routeDestination.helper.onWillDisappear())
      .onDisAppear(() => this.routeDestination.helper.onDisAppear())
      .onReady((ctx: NavDestinationContext) => this.routeDestination.helper.onReady(ctx))
      .onBackPressed(() => this.routeDestination.handleBackPressed(this.routeOptions()))
      .onResult((result: ESObject) => this.routeDestination.helper.onResult(result))
      .onActive((reason: NavDestinationActiveReason) => this.routeDestination.helper.onActive(reason))
      .onInactive((reason: NavDestinationActiveReason) => this.routeDestination.helper.onInactive(reason))
      .ignoreLayoutSafeArea(
        this.routeDestination.nativeSafeAreaTypes(),
        this.routeDestination.nativeSafeAreaEdges()
      )
      .width('100%')
      .height('100%')
    } else {
      NavDestination() {
        Stack() {
          Stack() {
            if (this.currentItem()) {
              Stack() {
                if ((this.currentItem()!).imageUrl.length > 0) {
                  ImageKnifeComponent({
                    imageKnifeOption: {
                      loadSrc: (this.currentItem()!).imageUrl,
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
            Scroll(this.scroller) {
              Column() {
                if ((true)) {
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
                    .onClick(() => this.closePlayer())
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
                if (this.currentItem()) {
                  if (this.isWide()) {
                    Row({ space: 56 }) {
                      if (((this.currentItem()!)).imageUrl.length > 0) {
                        ImageKnifeComponent({
                          imageKnifeOption: {
                            loadSrc: ((this.currentItem()!)).imageUrl,
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
                      Column() {
                        Column({ space: 20 }) {
                          Column({ space: 8 }) {
                            Text((((this.currentItem()!))).title)
                              .width('100%')
                              .fontSize(this.isWide() ? 28 : 24)
                              .fontWeight(FontWeight.Bold)
                              .fontColor(this.primaryTextColor())
                              .textAlign(this.isWide() ? TextAlign.Start : TextAlign.Center)
                              .maxLines(2)
                              .textOverflow({ overflow: TextOverflow.Ellipsis })
                            Text((((this.currentItem()!))).artist)
                              .width('100%')
                              .fontSize(15)
                              .fontColor(this.secondaryTextColor())
                              .opacity(this.hasArtwork() ? 0.86 : 1)
                              .textAlign(this.isWide() ? TextAlign.Start : TextAlign.Center)
                              .maxLines(1)
                              .textOverflow({ overflow: TextOverflow.Ellipsis })
                            if ((((this.currentItem()!))).album.length > 0) {
                              Text((((this.currentItem()!))).album)
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
                          Row({ space: 24 }) {
                            MusicSecondaryControl({
                              symbol: $r('sys.symbol.backward_end_fill'),
                              accessibilityLabel: $r('app.string.music_previous'),
                              action: () => this.controller.previous().catch((): void => {}),
                              controlEnabled: this.playbackState.currentIndex > 0,
                              active: false,
                              nativeSurface: this.useNativeSurface(),
                              systemMaterialAvailable: this.appUIState.systemMaterialAvailable,
                              primaryTextColor: this.primaryTextColor()
                            })
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
                              .attributeModifier(AppThemeSurfaceResolver.modifier(
                                AppThemeMaterialRole.PrimaryInteractive, true,
                                this.appUIState.systemMaterialAvailable))
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
                            MusicSecondaryControl({
                              symbol: $r('sys.symbol.forward_end_fill'),
                              accessibilityLabel: $r('app.string.music_next'),
                              action: () => this.controller.next().catch((): void => {}),
                              controlEnabled: this.playbackState.currentIndex >= 0 &&
                                this.playbackState.currentIndex < this.playbackState.queue.length - 1,
                              active: false,
                              nativeSurface: this.useNativeSurface(),
                              systemMaterialAvailable: this.appUIState.systemMaterialAvailable,
                              primaryTextColor: this.primaryTextColor()
                            })
                          }
                          .width('100%')
                          .justifyContent(FlexAlign.Center)
                          .alignItems(VerticalAlign.Center)
                          Row({ space: 22 }) {
                            MusicSecondaryControl({
                              symbol: (((this.currentItem()!))).favorite ? $r('sys.symbol.heart_fill') : $r('sys.symbol.heart'),
                              accessibilityLabel: (((this.currentItem()!))).favorite ? $r('app.string.music_unfavorite') : $r('app.string.music_favorite'),
                              action: () => this.toggleFavorite(),
                              controlEnabled: !this.viewModel.favoritePending,
                              active: (((this.currentItem()!))).favorite,
                              nativeSurface: this.useNativeSurface(),
                              systemMaterialAvailable: this.appUIState.systemMaterialAvailable,
                              primaryTextColor: this.primaryTextColor()
                            })
                            MusicSecondaryControl({
                              symbol: $r('sys.symbol.music_note_list'),
                              accessibilityLabel: $r('app.string.music_queue'),
                              action: () => this.showQueue = true,
                              controlEnabled: true,
                              active: false,
                              nativeSurface: this.useNativeSurface(),
                              systemMaterialAvailable: this.appUIState.systemMaterialAvailable,
                              primaryTextColor: this.primaryTextColor()
                            })
                            MusicSecondaryControl({
                              symbol: this.repeatSymbol(),
                              accessibilityLabel: this.repeatAccessibilityText(),
                              action: () => this.controller.cycleRepeatMode(),
                              controlEnabled: true,
                              active: this.playbackState.repeatMode !== MusicRepeatMode.Off,
                              nativeSurface: this.useNativeSurface(),
                              systemMaterialAvailable: this.appUIState.systemMaterialAvailable,
                              primaryTextColor: this.primaryTextColor()
                            })
                          }
                          .width('100%')
                          .justifyContent(FlexAlign.Center)
                        }
                        .width('100%')
                        .alignItems(HorizontalAlign.Center)
                      }
                      .width(430)
                    }
                    .width('100%')
                    .justifyContent(FlexAlign.Center)
                    .alignItems(VerticalAlign.Center)
                  } else {
                    Column({ space: 28 }) {
                      if (((this.currentItem()!)).imageUrl.length > 0) {
                        ImageKnifeComponent({
                          imageKnifeOption: {
                            loadSrc: ((this.currentItem()!)).imageUrl,
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
                      Column({ space: 20 }) {
                        Column({ space: 8 }) {
                          Text((((this.currentItem()!))).title)
                            .width('100%')
                            .fontSize(this.isWide() ? 28 : 24)
                            .fontWeight(FontWeight.Bold)
                            .fontColor(this.primaryTextColor())
                            .textAlign(this.isWide() ? TextAlign.Start : TextAlign.Center)
                            .maxLines(2)
                            .textOverflow({ overflow: TextOverflow.Ellipsis })
                          Text((((this.currentItem()!))).artist)
                            .width('100%')
                            .fontSize(15)
                            .fontColor(this.secondaryTextColor())
                            .opacity(this.hasArtwork() ? 0.86 : 1)
                            .textAlign(this.isWide() ? TextAlign.Start : TextAlign.Center)
                            .maxLines(1)
                            .textOverflow({ overflow: TextOverflow.Ellipsis })
                          if ((((this.currentItem()!))).album.length > 0) {
                            Text((((this.currentItem()!))).album)
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
                        Row({ space: 24 }) {
                          MusicSecondaryControl({
                            symbol: $r('sys.symbol.backward_end_fill'),
                            accessibilityLabel: $r('app.string.music_previous'),
                            action: () => this.controller.previous().catch((): void => {}),
                            controlEnabled: this.playbackState.currentIndex > 0,
                            active: false,
                            nativeSurface: this.useNativeSurface(),
                            systemMaterialAvailable: this.appUIState.systemMaterialAvailable,
                            primaryTextColor: this.primaryTextColor()
                          })
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
                            .attributeModifier(AppThemeSurfaceResolver.modifier(
                              AppThemeMaterialRole.PrimaryInteractive, true,
                              this.appUIState.systemMaterialAvailable))
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
                          MusicSecondaryControl({
                            symbol: $r('sys.symbol.forward_end_fill'),
                            accessibilityLabel: $r('app.string.music_next'),
                            action: () => this.controller.next().catch((): void => {}),
                            controlEnabled: this.playbackState.currentIndex >= 0 &&
                              this.playbackState.currentIndex < this.playbackState.queue.length - 1,
                            active: false,
                            nativeSurface: this.useNativeSurface(),
                            systemMaterialAvailable: this.appUIState.systemMaterialAvailable,
                            primaryTextColor: this.primaryTextColor()
                          })
                        }
                        .width('100%')
                        .justifyContent(FlexAlign.Center)
                        .alignItems(VerticalAlign.Center)
                        Row({ space: 22 }) {
                          MusicSecondaryControl({
                            symbol: (((this.currentItem()!))).favorite ? $r('sys.symbol.heart_fill') : $r('sys.symbol.heart'),
                            accessibilityLabel: (((this.currentItem()!))).favorite ? $r('app.string.music_unfavorite') : $r('app.string.music_favorite'),
                            action: () => this.toggleFavorite(),
                            controlEnabled: !this.viewModel.favoritePending,
                            active: (((this.currentItem()!))).favorite,
                            nativeSurface: this.useNativeSurface(),
                            systemMaterialAvailable: this.appUIState.systemMaterialAvailable,
                            primaryTextColor: this.primaryTextColor()
                          })
                          MusicSecondaryControl({
                            symbol: $r('sys.symbol.music_note_list'),
                            accessibilityLabel: $r('app.string.music_queue'),
                            action: () => this.showQueue = true,
                            controlEnabled: true,
                            active: false,
                            nativeSurface: this.useNativeSurface(),
                            systemMaterialAvailable: this.appUIState.systemMaterialAvailable,
                            primaryTextColor: this.primaryTextColor()
                          })
                          MusicSecondaryControl({
                            symbol: this.repeatSymbol(),
                            accessibilityLabel: this.repeatAccessibilityText(),
                            action: () => this.controller.cycleRepeatMode(),
                            controlEnabled: true,
                            active: this.playbackState.repeatMode !== MusicRepeatMode.Off,
                            nativeSurface: this.useNativeSurface(),
                            systemMaterialAvailable: this.appUIState.systemMaterialAvailable,
                            primaryTextColor: this.primaryTextColor()
                          })
                        }
                        .width('100%')
                        .justifyContent(FlexAlign.Center)
                      }
                      .width('100%')
                      .alignItems(HorizontalAlign.Center)
                    }
                    .width('100%')
                    .alignItems(HorizontalAlign.Center)
                  }
                } else {
                  Column() {
                    if ((false)) {
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
                        .onClick(() => this.closePlayer())
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
                    top: (false) ? Math.max(0, this.appUIState.safeTop) + 8 :
                      Math.max(0, this.appUIState.safeTop) + UIConstants.ACTION_BAR_HEIGHT,
                    left: 16,
                    right: 16,
                    bottom: Math.max(16, this.appUIState.safeBottom)
                  })
                }
              }
              .width('100%')
              .constraintSize({ maxWidth: this.contentMaxWidth() })
              .padding({
                top: (true) ? Math.max(0, this.appUIState.safeTop) + 8 :
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
        .width('100%')
        .height('100%')
        .bindSheet($$this.showQueue, this.queueSheetBuilder(false), {
          height: '70%',
          preferType: SheetType.BOTTOM,
          showClose: false,
          dragBar: true,
          backgroundColor: $r('app.color.bg_1')
        })
      }
      .attributeModifier(this.routeDestination.legacyModifier(this.routeOptions()))
      .gestureModifier(this.routeDestination.gestureModifier)
      .width('100%')
      .height('100%')
    }
  }
}
