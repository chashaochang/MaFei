import { HMRouter, HMRouterMgr } from '@hadss/hmrouter'
import { ImageKnifeComponent } from '@ohos/imageknifepro'
import { RouterConsts } from '../../common/RouterConsts'
import { AppRouteDestination, AppRouteDestinationOptions } from '../../component/AppRouteDestination'
import {
  AppThemeMaterialRole,
  AppThemeSurfaceResolver
} from '../../theme/AppThemeSurfaceResolver'
import { BreakpointTypeEnum } from '../../utils/BreakpointSystem'
import { MusicDetailState } from './MusicAlbumUIState'
import { MusicArtistUIState } from './MusicArtistUIState'
import { MusicArtistViewModel } from './MusicArtistViewModel'
import { musicBrowseErrorText } from './MusicErrorText'
import { MusicAlbum, MusicArtist, MusicBrowseErrorCode, MusicTrack } from './MusicModels'
import { MusicPlaybackController } from './MusicPlaybackController'
import { connectMusicPlaybackState, MusicPlaybackState } from './MusicPlaybackState'
import { MusicTrackRow } from './MusicTrackList'
import { HdsNavDestination, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'

interface MusicArtistRouteParam {
  artistId?: string
  title?: string
}

@HMRouter({
  pageUrl: RouterConsts.MusicArtistPage,
  useNavDst: true
})
@ComponentV2
export struct MusicArtistPage {
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
  @Local ui: MusicArtistUIState = new MusicArtistUIState(this.getUIContext())
  @Local vm: MusicArtistViewModel = new MusicArtistViewModel(this.ui)
  private playbackState: MusicPlaybackState = connectMusicPlaybackState()
  private playbackController: MusicPlaybackController = MusicPlaybackController.instance()
  private readonly scroller: Scroller = new Scroller()
  private routeTitle: string = ''

  aboutToAppear(): void {
    const param = HMRouterMgr.getCurrentParam() as MusicArtistRouteParam | undefined
    this.routeTitle = param?.title || ''
    this.vm.load(param?.artistId || '')
  }

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable)
  }

  private isWide(): boolean {
    return this.vm.appUIState.deviceWidth >= 840
  }

  private horizontalPadding(): number {
    return this.isWide() ? 24 : 16
  }

  private title(): ResourceStr {
    return this.ui.artist?.name || this.routeTitle || $r('app.string.music_artists')
  }

  private primaryTextColor(nativeSurface: boolean): ResourceColor {
    return nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(nativeSurface: boolean): ResourceColor {
    return nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  private columnCount(): number {
    switch (this.vm.appUIState.currentBreakpoint) {
      case BreakpointTypeEnum.SM:
        return 3
      case BreakpointTypeEnum.MD:
        return 4
      case BreakpointTypeEnum.LG:
        return 5
      case BreakpointTypeEnum.XL:
        return 6
      case BreakpointTypeEnum.XXL:
        return 8
      case BreakpointTypeEnum.XS:
      default:
        return 2
    }
  }

  private albumGridHeight(): number {
    if (this.ui.albums.length === 0) {
      return 0
    }
    const columns = this.columnCount()
    const rows = Math.ceil(this.ui.albums.length / columns)
    const windowWidth = Math.max(360, this.vm.appUIState.deviceWidth)
    const availableWidth = Math.min(1200, windowWidth - this.horizontalPadding() * 2)
    const itemWidth = Math.max(120, (availableWidth - (columns - 1) * 12) / columns)
    return rows * (itemWidth + 42) + Math.max(0, rows - 1) * 18
  }

  private openAlbum(album: MusicAlbum): void {
    HMRouterMgr.to(RouterConsts.MusicAlbumPage)
      .withParam({ albumId: album.id, title: album.title })
      .push()
  }

  private playAll(): void {
    if (this.ui.popularTracks.length > 0) {
      this.playbackController.playQueue(this.ui.popularTracks, 0).catch((): void => {})
    }
  }

  private playTrack(track: MusicTrack, index: number): void {
    if (this.playbackState.currentItem()?.id === track.id) {
      HMRouterMgr.to(RouterConsts.MusicPlayerPage).push()
      return
    }
    this.playbackController.playQueue(this.ui.popularTracks, index).catch((): void => {})
  }










  private routeOptions(): AppRouteDestinationOptions {
    return {
        title: this.title(),
        scrollControllers: [this.scroller]
      }
  }

  build() {
    if (this.routeDestination.usesHdsDestination()) {
      HdsNavDestination() {
        Column() {
          Blank().height(this.routeDestination.nativeContentTopInset(this.routeOptions()))
          Stack() {
            if (this.routeDestination.usesNativeChrome()) {
              if (this.ui.state === MusicDetailState.Loading) {
                Column() {
                  LoadingProgress().width(42).height(42)
                }
                .width('100%')
                .height('100%')
                .justifyContent(FlexAlign.Center)
              } else if (this.ui.state === MusicDetailState.Error || !this.ui.artist) {
                Column({ space: 12 }) {
                  SymbolGlyph($r('sys.symbol.exclamationmark_circle'))
                    .fontSize(42)
                    .fontColor([this.secondaryTextColor(((true)))])
                  Text(musicBrowseErrorText(this.ui.errorCode))
                    .fontSize(14)
                    .fontColor(this.secondaryTextColor(((true))))
                  Button(this.ui.errorCode === MusicBrowseErrorCode.NotFound ?
                    $r('app.string.music_back') : $r('app.string.music_retry'), { type: ButtonType.Normal })
                    .height(44)
                    .borderRadius(22)
                    .onClick(() => {
                      if (this.ui.errorCode === MusicBrowseErrorCode.NotFound) {
                        HMRouterMgr.pop()
                        return
                      }
                      const param = HMRouterMgr.getCurrentParam() as MusicArtistRouteParam | undefined
                      this.vm.load(param?.artistId || '')
                    })
                }
                .width('100%')
                .height('100%')
                .justifyContent(FlexAlign.Center)
              } else {
                List({ scroller: this.scroller }) {
                  ListItem() {
                    if (this.isWide()) {
                      Row({ space: 24 }) {
                        ImageKnifeComponent({
                          imageKnifeOption: {
                            loadSrc: ((this.ui.artist)).imageUrl,
                            objectFit: ImageFit.Cover,
                            border: { radius: 12 },
                            placeholderSrc: $r('app.media.default_avatar'),
                            placeholderObjectFit: ImageFit.Cover
                          }
                        })
                          .width(220)
                          .height(220)
                          .borderRadius(12)
                          .backgroundColor((((true))) ?
                            $r('sys.color.comp_background_tertiary') : $r('app.color.bg_2'))
                        Column({ space: 14 }) {
                          Text(((this.ui.artist)).name)
                            .width('100%')
                            .fontSize(28)
                            .fontWeight(FontWeight.Bold)
                            .fontColor(this.primaryTextColor((((true)))))
                            .maxLines(2)
                            .textOverflow({ overflow: TextOverflow.Ellipsis })
                          if (((this.ui.artist)).overview.length > 0) {
                            Text(((this.ui.artist)).overview)
                              .width('100%')
                              .fontSize(14)
                              .lineHeight(21)
                              .fontColor(this.secondaryTextColor((((true)))))
                              .maxLines(5)
                              .textOverflow({ overflow: TextOverflow.Ellipsis })
                          }
                          if ((((true)))) {
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
                            .width(this.isWide() ? 220 : '100%')
                            .height(48)
                            .borderRadius(24)
                            .buttonStyle(ButtonStyleMode.TEXTUAL)
                            .backgroundColor(Color.Transparent)
                            .attributeModifier(AppThemeSurfaceResolver.modifier(
                              AppThemeMaterialRole.PrimaryInteractive, true,
                              this.vm.appUIState.systemMaterialAvailable))
                            .enabled(this.ui.popularTracks.length > 0)
                            .accessibilityText($r('app.string.music_play_all'))
                            .onClick(() => this.playAll())
                          } else {
                            Button({ type: ButtonType.Normal }) {
                              Row({ space: 8 }) {
                                Image($r('app.media.ic_play'))
                                  .width(20)
                                  .height(20)
                                Text($r('app.string.music_play_all'))
                                  .fontSize(16)
                                  .fontWeight(FontWeight.Medium)
                                  .fontColor(Color.White)
                              }
                              .justifyContent(FlexAlign.Center)
                            }
                            .width(this.isWide() ? 220 : '100%')
                            .height(48)
                            .borderRadius(24)
                            .backgroundColor($r('app.color.color_main'))
                            .enabled(this.ui.popularTracks.length > 0)
                            .onClick(() => this.playAll())
                          }
                        }
                        .layoutWeight(1)
                        .alignItems(HorizontalAlign.Start)
                      }
                      .width('100%')
                      .alignItems(VerticalAlign.Center)
                    } else {
                      Column({ space: 14 }) {
                        ImageKnifeComponent({
                          imageKnifeOption: {
                            loadSrc: ((this.ui.artist)).imageUrl,
                            objectFit: ImageFit.Cover,
                            border: { radius: 12 },
                            placeholderSrc: $r('app.media.default_avatar'),
                            placeholderObjectFit: ImageFit.Cover
                          }
                        })
                          .width(180)
                          .height(180)
                          .borderRadius(12)
                          .backgroundColor((((true))) ?
                            $r('sys.color.comp_background_tertiary') : $r('app.color.bg_2'))
                        Text(((this.ui.artist)).name)
                          .width('100%')
                          .fontSize(24)
                          .fontWeight(FontWeight.Bold)
                          .fontColor(this.primaryTextColor((((true)))))
                          .textAlign(TextAlign.Center)
                          .maxLines(2)
                          .textOverflow({ overflow: TextOverflow.Ellipsis })
                        if ((((true)))) {
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
                          .width(this.isWide() ? 220 : '100%')
                          .height(48)
                          .borderRadius(24)
                          .buttonStyle(ButtonStyleMode.TEXTUAL)
                          .backgroundColor(Color.Transparent)
                          .attributeModifier(AppThemeSurfaceResolver.modifier(
                            AppThemeMaterialRole.PrimaryInteractive, true,
                            this.vm.appUIState.systemMaterialAvailable))
                          .enabled(this.ui.popularTracks.length > 0)
                          .accessibilityText($r('app.string.music_play_all'))
                          .onClick(() => this.playAll())
                        } else {
                          Button({ type: ButtonType.Normal }) {
                            Row({ space: 8 }) {
                              Image($r('app.media.ic_play'))
                                .width(20)
                                .height(20)
                              Text($r('app.string.music_play_all'))
                                .fontSize(16)
                                .fontWeight(FontWeight.Medium)
                                .fontColor(Color.White)
                            }
                            .justifyContent(FlexAlign.Center)
                          }
                          .width(this.isWide() ? 220 : '100%')
                          .height(48)
                          .borderRadius(24)
                          .backgroundColor($r('app.color.color_main'))
                          .enabled(this.ui.popularTracks.length > 0)
                          .onClick(() => this.playAll())
                        }
                        if (((this.ui.artist)).overview.length > 0) {
                          Text(((this.ui.artist)).overview)
                            .width('100%')
                            .fontSize(14)
                            .lineHeight(21)
                            .fontColor(this.secondaryTextColor((((true)))))
                            .maxLines(5)
                            .textOverflow({ overflow: TextOverflow.Ellipsis })
                        }
                      }
                      .width('100%')
                      .alignItems(HorizontalAlign.Center)
                    }
                  }
                  .padding({ bottom: 26 })
                  if (this.ui.popularTracks.length > 0) {
                    ListItem() {
                      Text($r('app.string.music_popular_tracks'))
                        .width('100%')
                        .fontSize(20)
                        .fontWeight(FontWeight.Bold)
                        .fontColor(this.primaryTextColor(((true))))
                    }
                    .padding({ bottom: 8 })
                    ForEach(this.ui.popularTracks, (track: MusicTrack, index: number) => {
                      ListItem() {
                        MusicTrackRow({
                          track: track,
                          index: index,
                          nativeSurface: ((true)),
                          showDivider: index < this.ui.popularTracks.length - 1,
                          onTrackClick: (selectedTrack: MusicTrack, selectedIndex: number) =>
                            this.playTrack(selectedTrack, selectedIndex)
                        })
                      }
                    }, (track: MusicTrack) => track.id)
                  }
                  if (this.ui.albums.length > 0) {
                    ListItem() {
                      Text($r('app.string.music_albums'))
                        .width('100%')
                        .fontSize(20)
                        .fontWeight(FontWeight.Bold)
                        .fontColor(this.primaryTextColor(((true))))
                    }
                    .padding({ top: 24, bottom: 12 })
                    ListItem() {
                      Grid() {
                        ForEach(this.ui.albums, (album: MusicAlbum) => {
                          GridItem() {
                            Column({ space: 7 }) {
                              ImageKnifeComponent({
                                imageKnifeOption: {
                                  loadSrc: album.imageUrl,
                                  objectFit: ImageFit.Cover,
                                  border: { radius: 12 },
                                  placeholderSrc: $r('app.media.placeholder_folder'),
                                  placeholderObjectFit: ImageFit.CENTER
                                }
                              })
                                .width('100%')
                                .aspectRatio(1)
                                .borderRadius(12)
                                .backgroundColor((((true))) ?
                                  $r('sys.color.comp_background_tertiary') : $r('app.color.bg_2'))
                              Text(album.title)
                                .width('100%')
                                .fontSize(13)
                                .fontWeight(FontWeight.Medium)
                                .fontColor(this.primaryTextColor((((true)))))
                                .maxLines(1)
                                .textOverflow({ overflow: TextOverflow.Ellipsis })
                              if (album.artist.length > 0) {
                                Text(album.artist)
                                  .width('100%')
                                  .fontSize(11)
                                  .fontColor(this.secondaryTextColor((((true)))))
                                  .maxLines(1)
                                  .textOverflow({ overflow: TextOverflow.Ellipsis })
                              }
                            }
                            .width('100%')
                            .alignItems(HorizontalAlign.Start)
                          }
                          .onClick(() => this.openAlbum(album))
                        }, (album: MusicAlbum) => album.id)
                      }
                      .width('100%')
                      .height(this.albumGridHeight())
                      .columnsTemplate('1fr '.repeat(this.columnCount()))
                      .columnsGap(12)
                      .rowsGap(18)
                      .scrollBar(BarState.Off)
                      .enableScrollInteraction(false)
                    }
                  }
                  if (this.ui.albumsLoadingMore || this.ui.albumsLoadMoreFailed) {
                    ListItem() {
                      if (this.ui.albumsLoadingMore) {
                        Row({ space: 8 }) {
                          LoadingProgress().width(20).height(20)
                          Text($r('app.string.music_loading_more'))
                            .fontSize(13)
                            .fontColor(this.secondaryTextColor((((true)))))
                        }
                        .height(52)
                        .justifyContent(FlexAlign.Center)
                      } else if (this.ui.albumsLoadMoreFailed) {
                        Column({ space: 6 }) {
                          Text(musicBrowseErrorText(this.ui.albumsLoadMoreErrorCode))
                            .fontSize(12)
                            .fontColor(this.secondaryTextColor((((true)))))
                          Button($r('app.string.music_retry'), { type: ButtonType.Normal })
                            .height(40)
                            .borderRadius(20)
                            .backgroundColor(Color.Transparent)
                            .fontColor(this.secondaryTextColor((((true)))))
                            .onClick(() => this.vm.retryLoadMoreAlbums())
                        }
                        .padding({ top: 8, bottom: 8 })
                      }
                    }
                  }
                  ListItem() {
                    Row().height(Math.max(20, this.vm.appUIState.safeBottom))
                  }
                }
                .width(`calc(100% - ${this.horizontalPadding() * 2}vp)`)
                .height('100%')
                .scrollBar(BarState.Off)
                .onReachEnd(() => this.vm.loadMoreAlbums())
              }
            } else {
              if (this.ui.state === MusicDetailState.Loading) {
                Column() {
                  LoadingProgress().width(42).height(42)
                }
                .width('100%')
                .height('100%')
                .justifyContent(FlexAlign.Center)
              } else if (this.ui.state === MusicDetailState.Error || !this.ui.artist) {
                Column({ space: 12 }) {
                  SymbolGlyph($r('sys.symbol.exclamationmark_circle'))
                    .fontSize(42)
                    .fontColor([this.secondaryTextColor(((false)))])
                  Text(musicBrowseErrorText(this.ui.errorCode))
                    .fontSize(14)
                    .fontColor(this.secondaryTextColor(((false))))
                  Button(this.ui.errorCode === MusicBrowseErrorCode.NotFound ?
                    $r('app.string.music_back') : $r('app.string.music_retry'), { type: ButtonType.Normal })
                    .height(44)
                    .borderRadius(22)
                    .onClick(() => {
                      if (this.ui.errorCode === MusicBrowseErrorCode.NotFound) {
                        HMRouterMgr.pop()
                        return
                      }
                      const param = HMRouterMgr.getCurrentParam() as MusicArtistRouteParam | undefined
                      this.vm.load(param?.artistId || '')
                    })
                }
                .width('100%')
                .height('100%')
                .justifyContent(FlexAlign.Center)
              } else {
                List({ scroller: this.scroller }) {
                  ListItem() {
                    if (this.isWide()) {
                      Row({ space: 24 }) {
                        ImageKnifeComponent({
                          imageKnifeOption: {
                            loadSrc: ((this.ui.artist)).imageUrl,
                            objectFit: ImageFit.Cover,
                            border: { radius: 12 },
                            placeholderSrc: $r('app.media.default_avatar'),
                            placeholderObjectFit: ImageFit.Cover
                          }
                        })
                          .width(220)
                          .height(220)
                          .borderRadius(12)
                          .backgroundColor((((false))) ?
                            $r('sys.color.comp_background_tertiary') : $r('app.color.bg_2'))
                        Column({ space: 14 }) {
                          Text(((this.ui.artist)).name)
                            .width('100%')
                            .fontSize(28)
                            .fontWeight(FontWeight.Bold)
                            .fontColor(this.primaryTextColor((((false)))))
                            .maxLines(2)
                            .textOverflow({ overflow: TextOverflow.Ellipsis })
                          if (((this.ui.artist)).overview.length > 0) {
                            Text(((this.ui.artist)).overview)
                              .width('100%')
                              .fontSize(14)
                              .lineHeight(21)
                              .fontColor(this.secondaryTextColor((((false)))))
                              .maxLines(5)
                              .textOverflow({ overflow: TextOverflow.Ellipsis })
                          }
                          if ((((false)))) {
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
                            .width(this.isWide() ? 220 : '100%')
                            .height(48)
                            .borderRadius(24)
                            .buttonStyle(ButtonStyleMode.TEXTUAL)
                            .backgroundColor(Color.Transparent)
                            .attributeModifier(AppThemeSurfaceResolver.modifier(
                              AppThemeMaterialRole.PrimaryInteractive, true,
                              this.vm.appUIState.systemMaterialAvailable))
                            .enabled(this.ui.popularTracks.length > 0)
                            .accessibilityText($r('app.string.music_play_all'))
                            .onClick(() => this.playAll())
                          } else {
                            Button({ type: ButtonType.Normal }) {
                              Row({ space: 8 }) {
                                Image($r('app.media.ic_play'))
                                  .width(20)
                                  .height(20)
                                Text($r('app.string.music_play_all'))
                                  .fontSize(16)
                                  .fontWeight(FontWeight.Medium)
                                  .fontColor(Color.White)
                              }
                              .justifyContent(FlexAlign.Center)
                            }
                            .width(this.isWide() ? 220 : '100%')
                            .height(48)
                            .borderRadius(24)
                            .backgroundColor($r('app.color.color_main'))
                            .enabled(this.ui.popularTracks.length > 0)
                            .onClick(() => this.playAll())
                          }
                        }
                        .layoutWeight(1)
                        .alignItems(HorizontalAlign.Start)
                      }
                      .width('100%')
                      .alignItems(VerticalAlign.Center)
                    } else {
                      Column({ space: 14 }) {
                        ImageKnifeComponent({
                          imageKnifeOption: {
                            loadSrc: ((this.ui.artist)).imageUrl,
                            objectFit: ImageFit.Cover,
                            border: { radius: 12 },
                            placeholderSrc: $r('app.media.default_avatar'),
                            placeholderObjectFit: ImageFit.Cover
                          }
                        })
                          .width(180)
                          .height(180)
                          .borderRadius(12)
                          .backgroundColor((((false))) ?
                            $r('sys.color.comp_background_tertiary') : $r('app.color.bg_2'))
                        Text(((this.ui.artist)).name)
                          .width('100%')
                          .fontSize(24)
                          .fontWeight(FontWeight.Bold)
                          .fontColor(this.primaryTextColor((((false)))))
                          .textAlign(TextAlign.Center)
                          .maxLines(2)
                          .textOverflow({ overflow: TextOverflow.Ellipsis })
                        if ((((false)))) {
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
                          .width(this.isWide() ? 220 : '100%')
                          .height(48)
                          .borderRadius(24)
                          .buttonStyle(ButtonStyleMode.TEXTUAL)
                          .backgroundColor(Color.Transparent)
                          .attributeModifier(AppThemeSurfaceResolver.modifier(
                            AppThemeMaterialRole.PrimaryInteractive, true,
                            this.vm.appUIState.systemMaterialAvailable))
                          .enabled(this.ui.popularTracks.length > 0)
                          .accessibilityText($r('app.string.music_play_all'))
                          .onClick(() => this.playAll())
                        } else {
                          Button({ type: ButtonType.Normal }) {
                            Row({ space: 8 }) {
                              Image($r('app.media.ic_play'))
                                .width(20)
                                .height(20)
                              Text($r('app.string.music_play_all'))
                                .fontSize(16)
                                .fontWeight(FontWeight.Medium)
                                .fontColor(Color.White)
                            }
                            .justifyContent(FlexAlign.Center)
                          }
                          .width(this.isWide() ? 220 : '100%')
                          .height(48)
                          .borderRadius(24)
                          .backgroundColor($r('app.color.color_main'))
                          .enabled(this.ui.popularTracks.length > 0)
                          .onClick(() => this.playAll())
                        }
                        if (((this.ui.artist)).overview.length > 0) {
                          Text(((this.ui.artist)).overview)
                            .width('100%')
                            .fontSize(14)
                            .lineHeight(21)
                            .fontColor(this.secondaryTextColor((((false)))))
                            .maxLines(5)
                            .textOverflow({ overflow: TextOverflow.Ellipsis })
                        }
                      }
                      .width('100%')
                      .alignItems(HorizontalAlign.Center)
                    }
                  }
                  .padding({ bottom: 26 })
                  if (this.ui.popularTracks.length > 0) {
                    ListItem() {
                      Text($r('app.string.music_popular_tracks'))
                        .width('100%')
                        .fontSize(20)
                        .fontWeight(FontWeight.Bold)
                        .fontColor(this.primaryTextColor(((false))))
                    }
                    .padding({ bottom: 8 })
                    ForEach(this.ui.popularTracks, (track: MusicTrack, index: number) => {
                      ListItem() {
                        MusicTrackRow({
                          track: track,
                          index: index,
                          nativeSurface: ((false)),
                          showDivider: index < this.ui.popularTracks.length - 1,
                          onTrackClick: (selectedTrack: MusicTrack, selectedIndex: number) =>
                            this.playTrack(selectedTrack, selectedIndex)
                        })
                      }
                    }, (track: MusicTrack) => track.id)
                  }
                  if (this.ui.albums.length > 0) {
                    ListItem() {
                      Text($r('app.string.music_albums'))
                        .width('100%')
                        .fontSize(20)
                        .fontWeight(FontWeight.Bold)
                        .fontColor(this.primaryTextColor(((false))))
                    }
                    .padding({ top: 24, bottom: 12 })
                    ListItem() {
                      Grid() {
                        ForEach(this.ui.albums, (album: MusicAlbum) => {
                          GridItem() {
                            Column({ space: 7 }) {
                              ImageKnifeComponent({
                                imageKnifeOption: {
                                  loadSrc: album.imageUrl,
                                  objectFit: ImageFit.Cover,
                                  border: { radius: 12 },
                                  placeholderSrc: $r('app.media.placeholder_folder'),
                                  placeholderObjectFit: ImageFit.CENTER
                                }
                              })
                                .width('100%')
                                .aspectRatio(1)
                                .borderRadius(12)
                                .backgroundColor((((false))) ?
                                  $r('sys.color.comp_background_tertiary') : $r('app.color.bg_2'))
                              Text(album.title)
                                .width('100%')
                                .fontSize(13)
                                .fontWeight(FontWeight.Medium)
                                .fontColor(this.primaryTextColor((((false)))))
                                .maxLines(1)
                                .textOverflow({ overflow: TextOverflow.Ellipsis })
                              if (album.artist.length > 0) {
                                Text(album.artist)
                                  .width('100%')
                                  .fontSize(11)
                                  .fontColor(this.secondaryTextColor((((false)))))
                                  .maxLines(1)
                                  .textOverflow({ overflow: TextOverflow.Ellipsis })
                              }
                            }
                            .width('100%')
                            .alignItems(HorizontalAlign.Start)
                          }
                          .onClick(() => this.openAlbum(album))
                        }, (album: MusicAlbum) => album.id)
                      }
                      .width('100%')
                      .height(this.albumGridHeight())
                      .columnsTemplate('1fr '.repeat(this.columnCount()))
                      .columnsGap(12)
                      .rowsGap(18)
                      .scrollBar(BarState.Off)
                      .enableScrollInteraction(false)
                    }
                  }
                  if (this.ui.albumsLoadingMore || this.ui.albumsLoadMoreFailed) {
                    ListItem() {
                      if (this.ui.albumsLoadingMore) {
                        Row({ space: 8 }) {
                          LoadingProgress().width(20).height(20)
                          Text($r('app.string.music_loading_more'))
                            .fontSize(13)
                            .fontColor(this.secondaryTextColor((((false)))))
                        }
                        .height(52)
                        .justifyContent(FlexAlign.Center)
                      } else if (this.ui.albumsLoadMoreFailed) {
                        Column({ space: 6 }) {
                          Text(musicBrowseErrorText(this.ui.albumsLoadMoreErrorCode))
                            .fontSize(12)
                            .fontColor(this.secondaryTextColor((((false)))))
                          Button($r('app.string.music_retry'), { type: ButtonType.Normal })
                            .height(40)
                            .borderRadius(20)
                            .backgroundColor(Color.Transparent)
                            .fontColor(this.secondaryTextColor((((false)))))
                            .onClick(() => this.vm.retryLoadMoreAlbums())
                        }
                        .padding({ top: 8, bottom: 8 })
                      }
                    }
                  }
                  ListItem() {
                    Row().height(Math.max(20, this.vm.appUIState.safeBottom))
                  }
                }
                .width(`calc(100% - ${this.horizontalPadding() * 2}vp)`)
                .height('100%')
                .scrollBar(BarState.Off)
                .onReachEnd(() => this.vm.loadMoreAlbums())
              }
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
        if (this.ui.state === MusicDetailState.Loading) {
          Column() {
            LoadingProgress().width(42).height(42)
          }
          .width('100%')
          .height('100%')
          .justifyContent(FlexAlign.Center)
        } else if (this.ui.state === MusicDetailState.Error || !this.ui.artist) {
          Column({ space: 12 }) {
            SymbolGlyph($r('sys.symbol.exclamationmark_circle'))
              .fontSize(42)
              .fontColor([this.secondaryTextColor(((false)))])
            Text(musicBrowseErrorText(this.ui.errorCode))
              .fontSize(14)
              .fontColor(this.secondaryTextColor(((false))))
            Button(this.ui.errorCode === MusicBrowseErrorCode.NotFound ?
              $r('app.string.music_back') : $r('app.string.music_retry'), { type: ButtonType.Normal })
              .height(44)
              .borderRadius(22)
              .onClick(() => {
                if (this.ui.errorCode === MusicBrowseErrorCode.NotFound) {
                  HMRouterMgr.pop()
                  return
                }
                const param = HMRouterMgr.getCurrentParam() as MusicArtistRouteParam | undefined
                this.vm.load(param?.artistId || '')
              })
          }
          .width('100%')
          .height('100%')
          .justifyContent(FlexAlign.Center)
        } else {
          List({ scroller: this.scroller }) {
            ListItem() {
              if (this.isWide()) {
                Row({ space: 24 }) {
                  ImageKnifeComponent({
                    imageKnifeOption: {
                      loadSrc: ((this.ui.artist)).imageUrl,
                      objectFit: ImageFit.Cover,
                      border: { radius: 12 },
                      placeholderSrc: $r('app.media.default_avatar'),
                      placeholderObjectFit: ImageFit.Cover
                    }
                  })
                    .width(220)
                    .height(220)
                    .borderRadius(12)
                    .backgroundColor((((false))) ?
                      $r('sys.color.comp_background_tertiary') : $r('app.color.bg_2'))
                  Column({ space: 14 }) {
                    Text(((this.ui.artist)).name)
                      .width('100%')
                      .fontSize(28)
                      .fontWeight(FontWeight.Bold)
                      .fontColor(this.primaryTextColor((((false)))))
                      .maxLines(2)
                      .textOverflow({ overflow: TextOverflow.Ellipsis })
                    if (((this.ui.artist)).overview.length > 0) {
                      Text(((this.ui.artist)).overview)
                        .width('100%')
                        .fontSize(14)
                        .lineHeight(21)
                        .fontColor(this.secondaryTextColor((((false)))))
                        .maxLines(5)
                        .textOverflow({ overflow: TextOverflow.Ellipsis })
                    }
                    if ((((false)))) {
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
                      .width(this.isWide() ? 220 : '100%')
                      .height(48)
                      .borderRadius(24)
                      .buttonStyle(ButtonStyleMode.TEXTUAL)
                      .backgroundColor(Color.Transparent)
                      .attributeModifier(AppThemeSurfaceResolver.modifier(
                        AppThemeMaterialRole.PrimaryInteractive, true,
                        this.vm.appUIState.systemMaterialAvailable))
                      .enabled(this.ui.popularTracks.length > 0)
                      .accessibilityText($r('app.string.music_play_all'))
                      .onClick(() => this.playAll())
                    } else {
                      Button({ type: ButtonType.Normal }) {
                        Row({ space: 8 }) {
                          Image($r('app.media.ic_play'))
                            .width(20)
                            .height(20)
                          Text($r('app.string.music_play_all'))
                            .fontSize(16)
                            .fontWeight(FontWeight.Medium)
                            .fontColor(Color.White)
                        }
                        .justifyContent(FlexAlign.Center)
                      }
                      .width(this.isWide() ? 220 : '100%')
                      .height(48)
                      .borderRadius(24)
                      .backgroundColor($r('app.color.color_main'))
                      .enabled(this.ui.popularTracks.length > 0)
                      .onClick(() => this.playAll())
                    }
                  }
                  .layoutWeight(1)
                  .alignItems(HorizontalAlign.Start)
                }
                .width('100%')
                .alignItems(VerticalAlign.Center)
              } else {
                Column({ space: 14 }) {
                  ImageKnifeComponent({
                    imageKnifeOption: {
                      loadSrc: ((this.ui.artist)).imageUrl,
                      objectFit: ImageFit.Cover,
                      border: { radius: 12 },
                      placeholderSrc: $r('app.media.default_avatar'),
                      placeholderObjectFit: ImageFit.Cover
                    }
                  })
                    .width(180)
                    .height(180)
                    .borderRadius(12)
                    .backgroundColor((((false))) ?
                      $r('sys.color.comp_background_tertiary') : $r('app.color.bg_2'))
                  Text(((this.ui.artist)).name)
                    .width('100%')
                    .fontSize(24)
                    .fontWeight(FontWeight.Bold)
                    .fontColor(this.primaryTextColor((((false)))))
                    .textAlign(TextAlign.Center)
                    .maxLines(2)
                    .textOverflow({ overflow: TextOverflow.Ellipsis })
                  if ((((false)))) {
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
                    .width(this.isWide() ? 220 : '100%')
                    .height(48)
                    .borderRadius(24)
                    .buttonStyle(ButtonStyleMode.TEXTUAL)
                    .backgroundColor(Color.Transparent)
                    .attributeModifier(AppThemeSurfaceResolver.modifier(
                      AppThemeMaterialRole.PrimaryInteractive, true,
                      this.vm.appUIState.systemMaterialAvailable))
                    .enabled(this.ui.popularTracks.length > 0)
                    .accessibilityText($r('app.string.music_play_all'))
                    .onClick(() => this.playAll())
                  } else {
                    Button({ type: ButtonType.Normal }) {
                      Row({ space: 8 }) {
                        Image($r('app.media.ic_play'))
                          .width(20)
                          .height(20)
                        Text($r('app.string.music_play_all'))
                          .fontSize(16)
                          .fontWeight(FontWeight.Medium)
                          .fontColor(Color.White)
                      }
                      .justifyContent(FlexAlign.Center)
                    }
                    .width(this.isWide() ? 220 : '100%')
                    .height(48)
                    .borderRadius(24)
                    .backgroundColor($r('app.color.color_main'))
                    .enabled(this.ui.popularTracks.length > 0)
                    .onClick(() => this.playAll())
                  }
                  if (((this.ui.artist)).overview.length > 0) {
                    Text(((this.ui.artist)).overview)
                      .width('100%')
                      .fontSize(14)
                      .lineHeight(21)
                      .fontColor(this.secondaryTextColor((((false)))))
                      .maxLines(5)
                      .textOverflow({ overflow: TextOverflow.Ellipsis })
                  }
                }
                .width('100%')
                .alignItems(HorizontalAlign.Center)
              }
            }
            .padding({ bottom: 26 })
            if (this.ui.popularTracks.length > 0) {
              ListItem() {
                Text($r('app.string.music_popular_tracks'))
                  .width('100%')
                  .fontSize(20)
                  .fontWeight(FontWeight.Bold)
                  .fontColor(this.primaryTextColor(((false))))
              }
              .padding({ bottom: 8 })
              ForEach(this.ui.popularTracks, (track: MusicTrack, index: number) => {
                ListItem() {
                  MusicTrackRow({
                    track: track,
                    index: index,
                    nativeSurface: ((false)),
                    showDivider: index < this.ui.popularTracks.length - 1,
                    onTrackClick: (selectedTrack: MusicTrack, selectedIndex: number) =>
                      this.playTrack(selectedTrack, selectedIndex)
                  })
                }
              }, (track: MusicTrack) => track.id)
            }
            if (this.ui.albums.length > 0) {
              ListItem() {
                Text($r('app.string.music_albums'))
                  .width('100%')
                  .fontSize(20)
                  .fontWeight(FontWeight.Bold)
                  .fontColor(this.primaryTextColor(((false))))
              }
              .padding({ top: 24, bottom: 12 })
              ListItem() {
                Grid() {
                  ForEach(this.ui.albums, (album: MusicAlbum) => {
                    GridItem() {
                      Column({ space: 7 }) {
                        ImageKnifeComponent({
                          imageKnifeOption: {
                            loadSrc: album.imageUrl,
                            objectFit: ImageFit.Cover,
                            border: { radius: 12 },
                            placeholderSrc: $r('app.media.placeholder_folder'),
                            placeholderObjectFit: ImageFit.CENTER
                          }
                        })
                          .width('100%')
                          .aspectRatio(1)
                          .borderRadius(12)
                          .backgroundColor((((false))) ?
                            $r('sys.color.comp_background_tertiary') : $r('app.color.bg_2'))
                        Text(album.title)
                          .width('100%')
                          .fontSize(13)
                          .fontWeight(FontWeight.Medium)
                          .fontColor(this.primaryTextColor((((false)))))
                          .maxLines(1)
                          .textOverflow({ overflow: TextOverflow.Ellipsis })
                        if (album.artist.length > 0) {
                          Text(album.artist)
                            .width('100%')
                            .fontSize(11)
                            .fontColor(this.secondaryTextColor((((false)))))
                            .maxLines(1)
                            .textOverflow({ overflow: TextOverflow.Ellipsis })
                        }
                      }
                      .width('100%')
                      .alignItems(HorizontalAlign.Start)
                    }
                    .onClick(() => this.openAlbum(album))
                  }, (album: MusicAlbum) => album.id)
                }
                .width('100%')
                .height(this.albumGridHeight())
                .columnsTemplate('1fr '.repeat(this.columnCount()))
                .columnsGap(12)
                .rowsGap(18)
                .scrollBar(BarState.Off)
                .enableScrollInteraction(false)
              }
            }
            if (this.ui.albumsLoadingMore || this.ui.albumsLoadMoreFailed) {
              ListItem() {
                if (this.ui.albumsLoadingMore) {
                  Row({ space: 8 }) {
                    LoadingProgress().width(20).height(20)
                    Text($r('app.string.music_loading_more'))
                      .fontSize(13)
                      .fontColor(this.secondaryTextColor((((false)))))
                  }
                  .height(52)
                  .justifyContent(FlexAlign.Center)
                } else if (this.ui.albumsLoadMoreFailed) {
                  Column({ space: 6 }) {
                    Text(musicBrowseErrorText(this.ui.albumsLoadMoreErrorCode))
                      .fontSize(12)
                      .fontColor(this.secondaryTextColor((((false)))))
                    Button($r('app.string.music_retry'), { type: ButtonType.Normal })
                      .height(40)
                      .borderRadius(20)
                      .backgroundColor(Color.Transparent)
                      .fontColor(this.secondaryTextColor((((false)))))
                      .onClick(() => this.vm.retryLoadMoreAlbums())
                  }
                  .padding({ top: 8, bottom: 8 })
                }
              }
            }
            ListItem() {
              Row().height(Math.max(20, this.vm.appUIState.safeBottom))
            }
          }
          .width(`calc(100% - ${this.horizontalPadding() * 2}vp)`)
          .height('100%')
          .scrollBar(BarState.Off)
          .onReachEnd(() => this.vm.loadMoreAlbums())
        }
      }
      .attributeModifier(this.routeDestination.legacyModifier(this.routeOptions()))
      .gestureModifier(this.routeDestination.gestureModifier)
      .width('100%')
      .height('100%')
    }
  }
}
