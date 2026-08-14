import { HMRouter, HMRouterMgr } from '@hadss/hmrouter'
import { ImageKnifeComponent } from '@ohos/imageknifepro'
import { RouterConsts } from '../../common/RouterConsts'
import { AppRouteDestination } from '../../component/AppRouteDestination'
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver'
import { MusicAlbumUIState, MusicDetailState } from './MusicAlbumUIState'
import { MusicAlbumViewModel } from './MusicAlbumViewModel'
import { musicBrowseErrorText } from './MusicErrorText'
import { MusicFormatters } from './MusicFormatters'
import { MusicBrowseErrorCode, MusicTrack } from './MusicModels'
import { MusicPlaybackController } from './MusicPlaybackController'
import { connectMusicPlaybackState, MusicPlaybackState } from './MusicPlaybackState'
import { MusicTrackList } from './MusicTrackList'

interface MusicAlbumRouteParam {
  albumId?: string
  title?: string
}

@HMRouter({
  pageUrl: RouterConsts.MusicAlbumPage,
  useNavDst: true
})
@ComponentV2
export struct MusicAlbumPage {
  @Local ui: MusicAlbumUIState = new MusicAlbumUIState(this.getUIContext())
  @Local vm: MusicAlbumViewModel = new MusicAlbumViewModel(this.ui)
  @Local fallbackTitle: string = ''
  @Local contentWidth: number = 0
  @Local contentHeight: number = 0
  private playbackState: MusicPlaybackState = connectMusicPlaybackState()
  private playbackController: MusicPlaybackController = MusicPlaybackController.instance()
  private readonly tracksScroller: Scroller = new Scroller()
  private readonly summaryScroller: Scroller = new Scroller()

  aboutToAppear(): void {
    const param = HMRouterMgr.getCurrentParam() as MusicAlbumRouteParam | undefined
    this.fallbackTitle = param?.title || ''
    this.vm.load(param?.albumId || '')
  }

  private title(): ResourceStr {
    return this.ui.album?.title || this.fallbackTitle || $r('app.string.music_albums')
  }

  private layoutWidth(): number {
    return this.contentWidth > 0 ? this.contentWidth : this.vm.appUIState.deviceWidth
  }

  private isWide(): boolean {
    return this.contentWidth > 0 && this.contentWidth >= 840
  }

  private horizontalPadding(): number {
    return this.layoutWidth() >= 600 ? 24 : 16
  }

  private wideSummaryWidth(): number {
    return this.layoutWidth() >= 1100 ? 320 : 280
  }

  private wideCoverSize(): number {
    const availableHeight = this.contentHeight > 0 ? this.contentHeight : this.vm.appUIState.deviceHeight
    const reservedHeight = availableHeight < 620 ? 300 : 350
    return Math.max(176, Math.min(this.wideSummaryWidth(), availableHeight - reservedHeight))
  }

  private wideOverviewLines(): number {
    if (this.contentHeight > 0 && this.contentHeight < 560) {
      return 2
    }
    if (this.contentHeight > 0 && this.contentHeight < 680) {
      return 3
    }
    return 5
  }

  private updateContentArea(area: Area): void {
    const width = Number(area.width)
    const height = Number(area.height)
    this.contentWidth = Number.isFinite(width) ? Math.max(0, width) : 0
    this.contentHeight = Number.isFinite(height) ? Math.max(0, height) : 0
  }

  private primaryTextColor(nativeSurface: boolean): ResourceColor {
    return nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(nativeSurface: boolean): ResourceColor {
    return nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  private metadata(): string {
    const album = this.ui.album
    if (!album) {
      return ''
    }
    const values: string[] = []
    if (album.year > 0) {
      values.push(album.year.toString())
    }
    if (album.trackCount > 0) {
      const unit = this.getUIContext().getHostContext()?.resourceManager
        .getStringByNameSync('music_track_unit') || ''
      values.push(`${album.trackCount} ${unit}`)
    }
    if (album.durationMs > 0) {
      values.push(MusicFormatters.duration(album.durationMs))
    }
    return values.join(' · ')
  }

  @Builder
  private favoriteButton(nativeSurface: boolean) {
    if (nativeSurface) {
      Button({ type: ButtonType.Circle, stateEffect: true }) {
        Image(this.ui.album?.favorite ? $r('app.media.ic_like') : $r('app.media.ic_unlike'))
          .width(21)
          .height(21)
          .renderMode(ImageRenderMode.Template)
          .fillColor(this.ui.album?.favorite ? $r('app.color.color_main') : $r('sys.color.icon_primary'))
      }
      .width(44)
      .height(44)
      .buttonStyle(ButtonStyleMode.TEXTUAL)
      .systemMaterial(AppThemeSurfaceResolver.material(AppThemeMaterialRole.AdaptiveInteractive))
      .enabled(!this.ui.favoritePending)
      .accessibilityText(this.ui.album?.favorite ?
        $r('app.string.music_unfavorite') : $r('app.string.music_favorite'))
      .onClick(() => this.toggleFavorite())
    } else {
      Button({ type: ButtonType.Circle, stateEffect: true }) {
        Image(this.ui.album?.favorite ? $r('app.media.ic_like') : $r('app.media.ic_unlike'))
          .width(21)
          .height(21)
          .renderMode(ImageRenderMode.Template)
          .fillColor($r('app.color.text_primary'))
      }
      .width(44)
      .height(44)
      .backgroundColor($r('app.color.bg_2'))
      .enabled(!this.ui.favoritePending)
      .accessibilityText(this.ui.album?.favorite ?
        $r('app.string.music_unfavorite') : $r('app.string.music_favorite'))
      .onClick(() => this.toggleFavorite())
    }
  }

  private toggleFavorite(): void {
    this.vm.toggleFavorite().then((updated: boolean) => {
      if (!updated) {
        this.getUIContext().getPromptAction().showToast({
          message: $r('app.string.music_favorite_failed')
        })
      }
    })
  }

  private playAll(): void {
    if (this.ui.tracks.length > 0) {
      this.playbackController.playQueue(this.ui.tracks, 0).catch((): void => {})
    }
  }

  @Builder
  private playAllButton(nativeSurface: boolean) {
    if (nativeSurface) {
      Button({ type: ButtonType.Normal, stateEffect: false }) {
        Row({ space: 8 }) {
          SymbolGlyph($r('sys.symbol.play_fill'))
            .fontSize(20)
            .fontColor([Color.White])
          Text($r('app.string.music_play_all'))
            .fontSize(16)
            .fontWeight(FontWeight.Medium)
            .fontColor(Color.White)
        }
        .justifyContent(FlexAlign.Center)
      }
      .width('100%')
      .height(48)
      .borderRadius(24)
      .buttonStyle(ButtonStyleMode.TEXTUAL)
      .backgroundColor(Color.Transparent)
      .systemMaterial(AppThemeSurfaceResolver.material(AppThemeMaterialRole.PrimaryInteractive))
      .enabled(this.ui.tracks.length > 0)
      .accessibilityText($r('app.string.music_play_all'))
      .onClick(() => this.playAll())
    } else {
      Button({ type: ButtonType.Normal }) {
        Row({ space: 8 }) {
          Image($r('app.media.ic_play')).width(20).height(20)
          Text($r('app.string.music_play_all'))
            .fontSize(16)
            .fontWeight(FontWeight.Medium)
            .fontColor(Color.White)
        }
        .justifyContent(FlexAlign.Center)
      }
      .width('100%')
      .height(48)
      .borderRadius(24)
      .backgroundColor($r('app.color.color_main'))
      .enabled(this.ui.tracks.length > 0)
      .onClick(() => this.playAll())
    }
  }

  @Builder
  private compactHeader(nativeSurface: boolean) {
    if (this.ui.album) {
      Row({ space: 16 }) {
        ImageKnifeComponent({
          imageKnifeOption: {
            loadSrc: this.ui.album.imageUrl,
            objectFit: ImageFit.Cover,
            border: { radius: 12 },
            placeholderSrc: $r('app.media.placeholder_folder'),
            placeholderObjectFit: ImageFit.CENTER
          }
        })
          .width(136)
          .height(136)
          .borderRadius(12)
          .backgroundColor(nativeSurface ?
            $r('sys.color.comp_background_tertiary') : $r('app.color.bg_2'))
        Column({ space: 8 }) {
          Text(this.ui.album.title)
            .width('100%')
            .fontSize(22)
            .fontWeight(FontWeight.Bold)
            .fontColor(this.primaryTextColor(nativeSurface))
            .maxLines(2)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
          if (this.ui.album.artist.length > 0) {
            Text(this.ui.album.artist)
              .width('100%')
              .fontSize(14)
              .fontColor(this.secondaryTextColor(nativeSurface))
              .maxLines(1)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
          }
          if (this.metadata().length > 0) {
            Text(this.metadata())
              .fontSize(12)
              .fontColor(this.secondaryTextColor(nativeSurface))
          }
          if (this.ui.album.overview.length > 0) {
            Text(this.ui.album.overview)
              .width('100%')
              .fontSize(12)
              .lineHeight(17)
              .fontColor(this.secondaryTextColor(nativeSurface))
              .maxLines(2)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
          }
          Blank().layoutWeight(1)
          this.favoriteButton(nativeSurface)
        }
        .height(136)
        .layoutWeight(1)
        .alignItems(HorizontalAlign.Start)
      }
      .width('100%')
      .height(168)
      .padding({ top: 16, bottom: 16 })
      .alignItems(VerticalAlign.Center)
    }
  }

  @Builder
  private wideSummary(nativeSurface: boolean) {
    if (this.ui.album) {
      Column({ space: 16 }) {
        ImageKnifeComponent({
          imageKnifeOption: {
            loadSrc: this.ui.album.imageUrl,
            objectFit: ImageFit.Cover,
            border: { radius: 12 },
            placeholderSrc: $r('app.media.placeholder_folder'),
            placeholderObjectFit: ImageFit.CENTER
          }
        })
          .width(this.wideCoverSize())
          .height(this.wideCoverSize())
          .borderRadius(12)
          .backgroundColor(nativeSurface ?
            $r('sys.color.comp_background_tertiary') : $r('app.color.bg_2'))
        Column({ space: 8 }) {
          Text(this.ui.album.title)
            .width('100%')
            .fontSize(26)
            .fontWeight(FontWeight.Bold)
            .fontColor(this.primaryTextColor(nativeSurface))
            .maxLines(3)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
          if (this.ui.album.artist.length > 0) {
            Text(this.ui.album.artist)
              .width('100%')
              .fontSize(15)
              .fontColor(this.secondaryTextColor(nativeSurface))
              .maxLines(2)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
          }
          if (this.metadata().length > 0) {
            Text(this.metadata())
              .width('100%')
              .fontSize(12)
              .fontColor(this.secondaryTextColor(nativeSurface))
          }
          if (this.ui.album.overview.length > 0) {
            Text(this.ui.album.overview)
              .width('100%')
              .fontSize(13)
              .lineHeight(19)
              .fontColor(this.secondaryTextColor(nativeSurface))
              .maxLines(this.wideOverviewLines())
              .textOverflow({ overflow: TextOverflow.Ellipsis })
          }
        }
        .width('100%')
        .alignItems(HorizontalAlign.Start)
        Row({ space: 12 }) {
          Stack() {
            this.playAllButton(nativeSurface)
          }
          .layoutWeight(1)
          this.favoriteButton(nativeSurface)
        }
        .width('100%')
        .alignItems(VerticalAlign.Center)
      }
      .width(this.wideSummaryWidth())
      .alignItems(HorizontalAlign.Start)
    }
  }

  @Builder
  private loadingState() {
    Column() {
      LoadingProgress().width(42).height(42)
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
  }

  @Builder
  private errorState(nativeSurface: boolean) {
    Column({ space: 12 }) {
      SymbolGlyph($r('sys.symbol.exclamationmark_circle'))
        .fontSize(42)
        .fontColor([this.secondaryTextColor(nativeSurface)])
      Text(musicBrowseErrorText(this.ui.errorCode))
        .fontSize(14)
        .fontColor(this.secondaryTextColor(nativeSurface))
      Button(this.ui.errorCode === MusicBrowseErrorCode.NotFound ?
        $r('app.string.music_back') : $r('app.string.music_retry'), { type: ButtonType.Normal })
        .height(44)
        .borderRadius(22)
        .onClick(() => {
          if (this.ui.errorCode === MusicBrowseErrorCode.NotFound) {
            HMRouterMgr.pop()
            return
          }
          const param = HMRouterMgr.getCurrentParam() as MusicAlbumRouteParam | undefined
          this.vm.load(param?.albumId || '')
        })
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
  }

  private onTrackClick(track: MusicTrack, index: number): void {
    if (this.playbackState.currentItem()?.id === track.id) {
      HMRouterMgr.to(RouterConsts.MusicPlayerPage).push()
      return
    }
    this.playbackController.playQueue(this.ui.tracks, index).catch((): void => {})
  }

  @Builder
  private pageContent(nativeSurface: boolean) {
    if (this.ui.state === MusicDetailState.Loading) {
      this.loadingState()
    } else if (this.ui.state === MusicDetailState.Error || !this.ui.album) {
      this.errorState(nativeSurface)
    } else if (this.isWide()) {
      Row({ space: 32 }) {
        Scroll(this.summaryScroller) {
          Column() {
            Blank().height(20)
            this.wideSummary(nativeSurface)
            Blank().height(Math.max(20, this.vm.appUIState.safeBottom))
          }
          .width('100%')
          .alignItems(HorizontalAlign.Start)
        }
        .width(this.wideSummaryWidth())
        .height('100%')
        .scrollBar(BarState.Off)
        Column() {
          Blank().height(20)
          Stack() {
            MusicTrackList({
              tracks: this.ui.tracks,
              nativeSurface: nativeSurface,
              scroller: this.tracksScroller,
              loadingMore: this.ui.tracksLoadingMore,
              loadMoreFailed: this.ui.tracksLoadMoreFailed,
              loadMoreErrorCode: this.ui.tracksLoadMoreErrorCode,
              onTrackClick: (track: MusicTrack, index: number) => this.onTrackClick(track, index),
              onReachEnd: () => this.vm.loadMoreTracks(),
              onRetryLoadMore: () => this.vm.retryLoadMoreTracks()
            })
          }
          .width('100%')
          .layoutWeight(1)
          Blank().height(Math.max(20, this.vm.appUIState.safeBottom))
        }
        .layoutWeight(1)
        .height('100%')
      }
      .width('calc(100% - 48vp)')
      .height('100%')
      .constraintSize({ maxWidth: 1132 })
      .alignItems(VerticalAlign.Top)
      .backgroundColor(nativeSurface ? Color.Transparent : $r('app.color.bg_main'))
    } else {
      Column() {
        this.compactHeader(nativeSurface)
        this.playAllButton(nativeSurface)
        Stack() {
          MusicTrackList({
            tracks: this.ui.tracks,
            nativeSurface: nativeSurface,
            scroller: this.tracksScroller,
            loadingMore: this.ui.tracksLoadingMore,
            loadMoreFailed: this.ui.tracksLoadMoreFailed,
            loadMoreErrorCode: this.ui.tracksLoadMoreErrorCode,
            onTrackClick: (track: MusicTrack, index: number) => this.onTrackClick(track, index),
            onReachEnd: () => this.vm.loadMoreTracks(),
            onRetryLoadMore: () => this.vm.retryLoadMoreTracks()
          })
        }
        .width('100%')
        .layoutWeight(1)
        .margin({ top: 12 })
      }
      .width(`calc(100% - ${this.horizontalPadding() * 2}vp)`)
      .height('100%')
      .backgroundColor(nativeSurface ? Color.Transparent : $r('app.color.bg_main'))
    }
  }

  build() {
    Stack({ alignContent: Alignment.Top }) {
      AppRouteDestination({
        title: this.title(),
        scrollControllers: [this.tracksScroller, this.summaryScroller],
        contentBuilder: () => {
          this.pageContent(true)
        },
        legacyContentBuilder: () => {
          this.pageContent(false)
        }
      })
    }
    .width('100%')
    .height('100%')
    .onAreaChange((_oldValue: Area, newValue: Area) => {
      this.updateContentArea(newValue)
    })
  }
}
