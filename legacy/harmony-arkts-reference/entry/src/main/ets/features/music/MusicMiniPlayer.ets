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
import { Constants } from '../../utils/Constants'

@ComponentV2
struct MusicMiniCover {
  @Require @Param item: MusicQueueItem
  @Require @Param nativeSurface: boolean

  private coverSize(): number {
    return this.nativeSurface ? 36 : 40
  }

  build() {
    Stack() {
      SymbolGlyph($r('sys.symbol.music'))
        .fontSize(this.nativeSurface ? 16 : 18)
        .fontColor([this.nativeSurface ? $r('sys.color.icon_secondary') : $r('app.color.text_1')])
      if (this.item.imageUrl.length > 0) {
        ImageKnifeComponent({
          imageKnifeOption: {
            loadSrc: this.item.imageUrl,
            objectFit: ImageFit.Cover,
            border: { radius: this.nativeSurface ? 18 : 9 }
          }
        })
          .width(this.coverSize())
          .height(this.coverSize())
          .borderRadius(this.nativeSurface ? 18 : 9)
      }
    }
    .width(this.coverSize())
    .height(this.coverSize())
    .borderRadius(this.nativeSurface ? 18 : 9)
    .clip(true)
    .backgroundColor(this.nativeSurface ?
      $r('sys.color.comp_background_tertiary') : $r('app.color.component_bg'))
  }
}

@ComponentV2
struct MusicMiniActionButton {
  @Require @Param symbol: Resource
  @Require @Param accessibilityLabel: Resource
  @Require @Param nativeSurface: boolean
  @Param emphasized: boolean = false
  @Param buttonSize: number = 40
  @Param iconSize: number = 20
  @Event onPressed: () => void = () => {
  }

  build() {
    Button({ type: ButtonType.Circle, stateEffect: true }) {
      SymbolGlyph(this.symbol)
        .fontSize(this.iconSize)
        .fontColor([this.nativeSurface ?
          (this.emphasized ? Color.Black : $r('sys.color.icon_primary')) : $r('app.color.text_primary')])
    }
    .width(this.buttonSize)
    .height(this.buttonSize)
    .buttonStyle(ButtonStyleMode.TEXTUAL)
    .backgroundColor(this.nativeSurface && this.emphasized ? 'rgba(255,255,255,0.90)' : Color.Transparent)
    .accessibilityText(this.accessibilityLabel)
    .onClick(() => this.onPressed())
  }
}

@ComponentV2
struct MusicMiniMenu {
  @Require @Param nativeSurface: boolean
  @Require @Param systemMaterialAvailable: boolean
  @Event onOpenPlayer: () => void = () => {
  }
  @Event onClosePlayer: () => void = () => {
  }

  build() {
    Menu() {
      MenuItem({ content: $r('app.string.music_queue') })
        .onClick(() => this.onOpenPlayer())
      MenuItem({ content: $r('app.string.music_close_player') })
        .onClick(() => this.onClosePlayer())
    }
    .backgroundBlurStyle(this.nativeSurface ? BlurStyle.NONE : BlurStyle.COMPONENT_ULTRA_THICK)
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.InteractiveFloating,
      this.nativeSurface,
      this.systemMaterialAvailable
    ))
  }
}

@ComponentV2
struct MusicMiniSurface {
  @Require @Param item: MusicQueueItem
  @Require @Param nativeSurface: boolean
  @Require @Param isPlaying: boolean
  @Require @Param systemMaterialAvailable: boolean
  @Event onTogglePlayback: () => void = () => {
  }
  @Event onNext: () => void = () => {
  }
  @Event onOpenPlayer: () => void = () => {
  }
  @Event onClosePlayer: () => void = () => {
  }

  // CustomBuilderAdapter: music-mini-player-menu
  @Builder
  private menuBuilder() {
    MusicMiniMenu({
      nativeSurface: this.nativeSurface,
      systemMaterialAvailable: this.systemMaterialAvailable,
      onOpenPlayer: this.onOpenPlayer,
      onClosePlayer: this.onClosePlayer
    })
  }

  build() {
    Row({ space: this.nativeSurface ? 8 : 9 }) {
      MusicMiniCover({ item: this.item, nativeSurface: this.nativeSurface })
      Column({ space: 2 }) {
        Text(this.item.title)
          .width('100%')
          .fontSize(14)
          .fontWeight(FontWeight.Medium)
          .fontColor(this.nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary'))
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        Text(this.item.artist || this.item.album)
          .width('100%')
          .fontSize(11)
          .fontColor(this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1'))
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
      .onClick(() => this.onOpenPlayer())
      Row({ space: this.nativeSurface ? 0 : 4 }) {
        MusicMiniActionButton({
          symbol: this.isPlaying ? $r('sys.symbol.pause_fill') : $r('sys.symbol.play_fill'),
          accessibilityLabel: this.isPlaying ? $r('app.string.music_pause') : $r('app.string.music_play'),
          nativeSurface: this.nativeSurface,
          emphasized: this.nativeSurface,
          buttonSize: this.nativeSurface ? 32 : 40,
          iconSize: this.nativeSurface ? 16 : 20,
          onPressed: this.onTogglePlayback
        })
        MusicMiniActionButton({
          symbol: $r('sys.symbol.forward_end_fill'),
          accessibilityLabel: $r('app.string.music_next'),
          nativeSurface: this.nativeSurface,
          buttonSize: this.nativeSurface ? 32 : 40,
          iconSize: this.nativeSurface ? 19 : 20,
          onPressed: this.onNext
        })
        Button({ type: ButtonType.Circle, stateEffect: true }) {
          SymbolGlyph($r('sys.symbol.music_note_list'))
            .fontSize(20)
            .fontColor([this.nativeSurface ? $r('sys.color.icon_primary') : $r('app.color.text_primary')])
        }
        .width(this.nativeSurface ? 32 : 40)
        .height(this.nativeSurface ? 32 : 40)
        .buttonStyle(ButtonStyleMode.TEXTUAL)
        .backgroundColor(Color.Transparent)
        .accessibilityText($r('app.string.music_queue'))
        .bindMenu(this.menuBuilder())
      }
      .height(this.nativeSurface ? 32 : 40)
      .alignItems(VerticalAlign.Center)
    }
    .width('100%')
    .height(this.nativeSurface ? MusicHostVisibilityPolicy.NATIVE_MINI_PLAYER_HEIGHT :
      MusicHostVisibilityPolicy.LEGACY_MINI_PLAYER_HEIGHT)
    .padding({ left: this.nativeSurface ? 8 : 10, right: this.nativeSurface ? 6 : 8 })
    .alignItems(VerticalAlign.Center)
    .borderRadius(this.nativeSurface ? MusicHostVisibilityPolicy.NATIVE_MINI_PLAYER_HEIGHT / 2 : 18)
    .clip(true)
    .backgroundColor(this.nativeSurface ? Color.Transparent : $r('app.color.bg_2'))
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.MusicMiniPlayer,
      this.nativeSurface,
      this.systemMaterialAvailable
    ))
  }
}

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

  private openPlayer(): void {
    HMRouterMgr.to(RouterConsts.MusicPlayerPage)
      .withNavigation(Constants.NAVIGATION_ID)
      .push()
  }

  build() {
    if (this.visible && this.currentItem()) {
      MusicMiniSurface({
        item: this.currentItem()!,
        nativeSurface: this.useNativeSurface(),
        isPlaying: this.isPlaying(),
        systemMaterialAvailable: this.appUIState.systemMaterialAvailable,
        onTogglePlayback: () => this.togglePlayback(),
        onNext: () => this.controller.next().catch((): void => {}),
        onOpenPlayer: () => this.openPlayer(),
        onClosePlayer: () => this.controller.stopAndClear().catch((): void => {})
      })
    } else {
      Row()
        .width(0)
        .height(0)
        .hitTestBehavior(HitTestMode.None)
    }
  }
}
