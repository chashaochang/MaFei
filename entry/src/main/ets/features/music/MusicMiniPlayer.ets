import { HMRouterMgr } from '@hadss/hmrouter'
import { AppStorageV2 } from '@kit.ArkUI'
import { ImageKnifeComponent } from '@ohos/imageknifepro'
import { RouterConsts } from '../../common/RouterConsts'
import { AppUIState } from '../../entity/AppUIState'
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver'
import { MusicPlaybackStatus, MusicQueueItem } from './MusicModels'
import { MusicPlaybackController } from './MusicPlaybackController'
import { connectMusicPlaybackState, MusicPlaybackState } from './MusicPlaybackState'
import { MusicHostVisibilityPolicy } from './MusicHostVisibilityPolicy'

@ComponentV2
export struct MusicMiniPlayer {
  @Require @Param visible: boolean
  private appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!
  @Local playbackState: MusicPlaybackState = connectMusicPlaybackState()
  private controller: MusicPlaybackController = MusicPlaybackController.instance()

  private currentItem(): MusicQueueItem | undefined {
    return this.playbackState.currentItem()
  }

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable)
  }

  private isPlaying(): boolean {
    return this.playbackState.status === MusicPlaybackStatus.Playing ||
      this.playbackState.status === MusicPlaybackStatus.Buffering
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

  private next(): void {
    this.controller.next().catch((): void => {})
  }

  private closePlayer(): void {
    this.controller.stopAndClear().catch((): void => {})
  }

  private openPlayer(): void {
    HMRouterMgr.to(RouterConsts.MusicPlayerPage).push()
  }

  private coverSize(nativeSurface: boolean): number {
    return nativeSurface ? 36 : 40
  }

  @Builder
  private cover(item: MusicQueueItem, nativeSurface: boolean) {
    Stack() {
      SymbolGlyph($r('sys.symbol.music'))
        .fontSize(nativeSurface ? 16 : 18)
        .fontColor([nativeSurface ? $r('sys.color.icon_secondary') : $r('app.color.text_1')])
      if (item.imageUrl.length > 0) {
        ImageKnifeComponent({
          imageKnifeOption: {
            loadSrc: item.imageUrl,
            objectFit: ImageFit.Cover,
            border: { radius: nativeSurface ? 18 : 9 }
          }
        })
          .width(this.coverSize(nativeSurface))
          .height(this.coverSize(nativeSurface))
          .borderRadius(nativeSurface ? 18 : 9)
      }
    }
    .width(this.coverSize(nativeSurface))
    .height(this.coverSize(nativeSurface))
    .borderRadius(nativeSurface ? 18 : 9)
    .clip(true)
    .backgroundColor(nativeSurface ?
      $r('sys.color.comp_background_tertiary') : $r('app.color.component_bg'))
  }

  @Builder
  private nativeActionButton(symbol: Resource, accessibilityText: Resource,
    action: () => void, emphasized: boolean, buttonSize: number, iconSize: number) {
    Button({ type: ButtonType.Circle, stateEffect: true }) {
      SymbolGlyph(symbol)
        .fontSize(iconSize)
        .fontColor([emphasized ? Color.Black : $r('sys.color.icon_primary')])
    }
    .width(buttonSize)
    .height(buttonSize)
    .buttonStyle(ButtonStyleMode.TEXTUAL)
    .backgroundColor(emphasized ? 'rgba(255,255,255,0.90)' : Color.Transparent)
    .accessibilityText(accessibilityText)
    .onClick(action)
  }

  @Builder
  private legacyActionButton(symbol: Resource, accessibilityText: Resource,
    action: () => void) {
    Button({ type: ButtonType.Circle, stateEffect: true }) {
      SymbolGlyph(symbol)
        .fontSize(20)
        .fontColor([$r('app.color.text_primary')])
    }
    .width(40)
    .height(40)
    .buttonStyle(ButtonStyleMode.TEXTUAL)
    .backgroundColor(Color.Transparent)
    .accessibilityText(accessibilityText)
    .onClick(action)
  }

  @Builder
  private closeMenu(nativeSurface: boolean) {
    if (nativeSurface) {
      Menu() {
        MenuItem({
          content: $r('app.string.music_queue')
        })
          .onClick(() => this.openPlayer())
        MenuItem({
          content: $r('app.string.music_close_player')
        })
          .onClick(() => this.closePlayer())
      }
      .backgroundBlurStyle(BlurStyle.NONE)
      .attributeModifier(AppThemeSurfaceResolver.modifier(
        AppThemeMaterialRole.InteractiveFloating, nativeSurface,
        this.appUIState.systemMaterialAvailable))
    } else {
      Menu() {
        MenuItem({
          content: $r('app.string.music_queue')
        })
          .onClick(() => this.openPlayer())
        MenuItem({
          content: $r('app.string.music_close_player')
        })
          .onClick(() => this.closePlayer())
      }
      .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
    }
  }

  @Builder
  private nativeActions() {
    Row({ space: 0 }) {
      this.nativeActionButton(
        this.isPlaying() ? $r('sys.symbol.pause_fill') : $r('sys.symbol.play_fill'),
        this.isPlaying() ? $r('app.string.music_pause') : $r('app.string.music_play'),
        () => this.togglePlayback(),
        true,
        32,
        16
      )
      this.nativeActionButton(
        $r('sys.symbol.forward_end_fill'),
        $r('app.string.music_next'),
        () => this.next(),
        false,
        32,
        19
      )
      Button({ type: ButtonType.Circle, stateEffect: true }) {
        SymbolGlyph($r('sys.symbol.music_note_list'))
          .fontSize(20)
          .fontColor([$r('sys.color.icon_primary')])
      }
      .width(32)
      .height(32)
      .buttonStyle(ButtonStyleMode.TEXTUAL)
      .backgroundColor(Color.Transparent)
      .accessibilityText($r('app.string.music_queue'))
      .bindMenu(this.closeMenu(true))
    }
    .height(32)
    .alignItems(VerticalAlign.Center)
  }

  @Builder
  private legacyActions() {
    Row({ space: 4 }) {
      this.legacyActionButton(
        this.isPlaying() ? $r('sys.symbol.pause_fill') : $r('sys.symbol.play_fill'),
        this.isPlaying() ? $r('app.string.music_pause') : $r('app.string.music_play'),
        () => this.togglePlayback()
      )
      this.legacyActionButton(
        $r('sys.symbol.forward_end_fill'),
        $r('app.string.music_next'),
        () => this.next()
      )
      Button({ type: ButtonType.Circle, stateEffect: true }) {
        SymbolGlyph($r('sys.symbol.music_note_list'))
          .fontSize(20)
          .fontColor([$r('app.color.text_primary')])
      }
      .width(40)
      .height(40)
      .buttonStyle(ButtonStyleMode.TEXTUAL)
      .backgroundColor(Color.Transparent)
      .accessibilityText($r('app.string.music_queue'))
      .bindMenu(this.closeMenu(false))
    }
    .height(40)
    .alignItems(VerticalAlign.Center)
  }

  @Builder
  private playerContent(item: MusicQueueItem, nativeSurface: boolean) {
    Row({ space: nativeSurface ? 8 : 9 }) {
      this.cover(item, nativeSurface)
      Column({ space: 2 }) {
        Text(item.title)
          .width('100%')
          .fontSize(14)
          .fontWeight(FontWeight.Medium)
          .fontColor(nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary'))
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        Text(item.artist || item.album)
          .width('100%')
          .fontSize(11)
          .fontColor(nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1'))
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
      .onClick(() => this.openPlayer())
      if (nativeSurface) {
        this.nativeActions()
      } else {
        this.legacyActions()
      }
    }
    .width('100%')
    .height(nativeSurface ? MusicHostVisibilityPolicy.NATIVE_MINI_PLAYER_HEIGHT :
      MusicHostVisibilityPolicy.LEGACY_MINI_PLAYER_HEIGHT)
    .padding({
      left: nativeSurface ? 8 : 10,
      right: nativeSurface ? 6 : 8
    })
    .alignItems(VerticalAlign.Center)
  }

  @Builder
  private nativePlayer(item: MusicQueueItem) {
    Row() {
      this.playerContent(item, true)
    }
    .width('100%')
    .height(MusicHostVisibilityPolicy.NATIVE_MINI_PLAYER_HEIGHT)
    .borderRadius(MusicHostVisibilityPolicy.NATIVE_MINI_PLAYER_HEIGHT / 2)
    .clip(true)
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.MusicMiniPlayer, true,
      this.appUIState.systemMaterialAvailable))
  }

  @Builder
  private legacyPlayer(item: MusicQueueItem) {
    Row() {
      this.playerContent(item, false)
    }
    .width('100%')
    .height(MusicHostVisibilityPolicy.LEGACY_MINI_PLAYER_HEIGHT)
    .borderRadius(18)
    .clip(true)
    .backgroundColor($r('app.color.bg_2'))
  }

  build() {
    if (this.visible && this.currentItem()) {
      if (this.useNativeSurface()) {
        this.nativePlayer(this.currentItem()!)
      } else {
        this.legacyPlayer(this.currentItem()!)
      }
    } else {
      Row()
        .width(0)
        .height(0)
        .hitTestBehavior(HitTestMode.None)
    }
  }
}
