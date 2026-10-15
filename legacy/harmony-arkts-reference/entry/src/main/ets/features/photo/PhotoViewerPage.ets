import { HMRouter, HMRouterMgr } from '@hadss/hmrouter'
import { common } from '@kit.AbilityKit'
import { PageState } from '../../common/base/BaseUIState'
import { RouterConsts } from '../../common/RouterConsts'
import { AppRouteDestination, AppRouteDestinationOptions } from '../../component/AppRouteDestination'
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver'
import { PhotoInfoSheet } from './PhotoInfoSheet'
import { photoBrowseErrorText } from './PhotoErrorText'
import {
  PhotoBrowseErrorCode,
  PhotoItem,
  PhotoSaveState,
  PhotoViewerRouteParam
} from './PhotoModels'
import {
  PhotoSaveErrorCode,
  PhotoSaveResult,
  PhotoSaveService
} from './PhotoSaveService'
import { PhotoViewerUIState } from './PhotoViewerUIState'
import { PhotoViewerViewModel } from './PhotoViewerViewModel'
import { PhotoZoomableImage } from './PhotoZoomableImage'
import { HdsNavDestination, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'
import { Constants } from '../../utils/Constants'

@ComponentV2
struct PhotoViewerInfoContent {
  @Require @Param photo: PhotoItem | null
  @Require @Param loading: boolean
  @Require @Param loadFailed: boolean
  @Require @Param nativeSurface: boolean
  @Param blurFallback: boolean = false
  @Event onRetry: () => void = () => {}

  build() {
    Column() {
      PhotoInfoSheet({
        photo: this.photo,
        loading: this.loading,
        loadFailed: this.loadFailed,
        nativeSurface: this.nativeSurface,
        onRetry: this.onRetry
      })
    }
    .width('100%')
    .height('100%')
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.Floating, this.blurFallback, false))
  }
}

const PHOTO_VIEWER_SHEET_HEIGHT: string = '70%'

@ComponentV2
struct PhotoViewerFloatingButton {
  @Require @Param symbol: Resource
  @Require @Param accessibilityLabel: Resource
  @Param active: boolean = false
  @Param pending: boolean = false
  @Param nativeSurface: boolean = false
  @Param systemMaterialAvailable: boolean = false
  @Event action: () => void = () => {}

  build() {
    Button({ type: ButtonType.Circle, stateEffect: true }) {
      if (this.pending) {
        LoadingProgress()
          .width(23)
          .height(23)
          .color(Color.White)
      } else {
        SymbolGlyph(this.symbol)
          .fontSize(24)
          .fontColor([this.active ? $r('app.color.color_main') : Color.White])
      }
    }
    .width(50)
    .height(50)
    .buttonStyle(this.nativeSurface ? ButtonStyleMode.TEXTUAL : ButtonStyleMode.NORMAL)
    .backgroundColor(this.nativeSurface ? Color.Transparent : $r('app.color.reload_title_color'))
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.InteractiveFloating,
      this.nativeSurface,
      this.systemMaterialAvailable
    ))
    .accessibilityText(this.accessibilityLabel)
    .enabled(!this.pending)
    .hitTestBehavior(HitTestMode.BLOCK_HIERARCHY)
    .onClick(this.action)
  }
}

@HMRouter({
  pageUrl: RouterConsts.PhotoViewerPage,
  useNavDst: true,
  lifecycle: 'photoViewerLifecycle'
})
@ComponentV2
export struct PhotoViewerPage {
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
  @Local ui: PhotoViewerUIState = new PhotoViewerUIState(this.getUIContext())
  @Local vm: PhotoViewerViewModel = new PhotoViewerViewModel(this.ui)
  @Local saveState: PhotoSaveState = PhotoSaveState.Idle
  private readonly swiperController: SwiperController = new SwiperController()
  private saveService?: PhotoSaveService
  private initialized: boolean = false
  private active: boolean = false
  private saveEpoch: number = 0
  private favoriteEpoch: number = 0

  aboutToAppear(): void {
    this.active = true
    if (this.initialized) {
      return
    }
    this.initialized = true
    const param = HMRouterMgr.getCurrentParam() as PhotoViewerRouteParam | undefined
    this.vm.init({
      sourceKey: param?.sourceKey || '',
      initialPhotoId: param?.initialPhotoId || ''
    })
  }

  aboutToDisappear(): void {
    this.active = false
    this.saveEpoch += 1
    this.favoriteEpoch += 1
    this.vm.dispose()
  }

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.vm.appUIState.themeStyle,
      this.vm.appUIState.nativeThemeAvailable
    )
  }

  private currentPhoto(): PhotoItem | undefined {
    return this.vm.currentPhoto()
  }

  private closeViewer(): void {
    HMRouterMgr.pop({ navigationId: Constants.NAVIGATION_ID })
  }

  private indexText(): string {
    if (this.ui.currentIndex < 0 || this.ui.photos.length === 0) {
      return ''
    }
    return `${this.ui.currentIndex + 1} / ${this.ui.photos.length}`
  }

  private beforeBack(): boolean {
    if (this.ui.infoVisible) {
      this.vm.hideInfo()
      return true
    }
    return false
  }

  private showToast(message: ResourceStr): void {
    this.getUIContext().getPromptAction().showToast({ message: message })
  }

  private toggleFavorite(): void {
    if (this.ui.favoritePendingPhotoId.length > 0) {
      return
    }
    const requestEpoch = ++this.favoriteEpoch
    this.vm.toggleCurrentFavorite().then((updated: boolean) => {
      if (!this.active || requestEpoch !== this.favoriteEpoch) {
        return
      }
      if (!updated) {
        this.showToast($r('app.string.photo_favorite_failed'))
      }
    }).catch((): void => {
      if (!this.active || requestEpoch !== this.favoriteEpoch) {
        return
      }
      this.showToast($r('app.string.photo_favorite_failed'))
    })
  }

  private showInfo(): void {
    this.vm.showInfo().catch((): void => {})
  }

  private onSaveAuthorization(result: SaveButtonOnClickResult): void {
    this.vm.notifyManualInteraction()
    if (result !== SaveButtonOnClickResult.SUCCESS) {
      this.saveState = PhotoSaveState.AuthorizationCancelled
      this.showToast($r('app.string.photo_save_authorization_failed'))
      return
    }
    this.saveCurrentPhoto()
  }

  private saveCurrentPhoto(): void {
    const photo = this.currentPhoto()
    if (!photo || this.saveState === PhotoSaveState.Downloading) {
      return
    }
    if (!photo.canDownload) {
      this.saveState = PhotoSaveState.Failed
      this.showToast($r('app.string.photo_save_unavailable'))
      return
    }
    if (!this.saveService) {
      const context = this.getUIContext().getHostContext() as common.UIAbilityContext
      this.saveService = PhotoSaveService.create(context)
    }
    this.saveState = PhotoSaveState.Downloading
    const requestEpoch = ++this.saveEpoch
    this.saveService.save(photo).then((result: PhotoSaveResult) => {
      if (!this.active || requestEpoch !== this.saveEpoch) {
        return
      }
      this.handleSaveResult(result)
    }).catch((): void => {
      if (!this.active || requestEpoch !== this.saveEpoch) {
        return
      }
      this.saveState = PhotoSaveState.Failed
      this.showToast($r('app.string.photo_save_failed'))
    })
  }

  private handleSaveResult(result: PhotoSaveResult): void {
    this.saveState = result.state
    if (result.state === PhotoSaveState.Saved) {
      this.showToast($r('app.string.photo_save_success'))
      return
    }
    if (result.errorCode === PhotoSaveErrorCode.DownloadNotAllowed) {
      this.showToast($r('app.string.photo_save_unavailable'))
      return
    }
    if (result.errorCode === PhotoSaveErrorCode.AuthorizationUnavailable) {
      this.showToast($r('app.string.photo_save_authorization_unavailable'))
      return
    }
    this.showToast($r('app.string.photo_save_failed'))
  }










  private routeOptions(): AppRouteDestinationOptions {
    return {
        title: '',
        titleBarVisible: false,
        backButtonVisible: false,
        contentExtendsUnderTitleBar: true,
        beforeBack: () => this.beforeBack()
      }
  }

  // CustomBuilderAdapter: photo-viewer-info-sheet
  @Builder
  private infoSheetBuilder(nativeSurface: boolean, blurFallback: boolean) {
    PhotoViewerInfoContent({
      photo: this.ui.infoPhoto,
      loading: this.ui.infoLoading,
      loadFailed: this.ui.infoErrorCode !== PhotoBrowseErrorCode.None,
      nativeSurface,
      blurFallback,
      onRetry: () => this.vm.loadCurrentInfo(true).catch((): void => {})
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
                if (this.ui.pageState === PageState.Loading) {
                  LoadingProgress()
                    .width(34)
                    .height(34)
                    .color(Color.White)
                } else if (this.ui.pageState === PageState.Error || !this.currentPhoto()) {
                  Column({ space: 14 }) {
                    SymbolGlyph($r('sys.symbol.exclamationmark_circle'))
                      .fontSize(44)
                      .fontColor([Color.White])
                    Text(photoBrowseErrorText(this.ui.loadErrorCode))
                      .fontSize(14)
                      .fontColor(Color.White)
                    Button($r('app.string.photo_back'), { type: ButtonType.Normal })
                      .height(42)
                      .borderRadius(21)
                      .onClick(() => this.closeViewer())
                  }
                } else {
                  Swiper(this.swiperController) {
                    ForEach(this.ui.photos, (photo: PhotoItem, index: number) => {
                      PhotoZoomableImage({
                        photo: photo,
                        active: index === this.ui.currentIndex,
                        resetRevision: this.ui.zoomRevision,
                        onSingleTap: () => this.vm.toggleControls(),
                        onInteraction: () => this.vm.notifyManualInteraction(),
                        onZoomChanged: (scale: number, offsetX: number, offsetY: number) => {
                          if (index === this.ui.currentIndex) {
                            this.vm.updateZoom(scale, offsetX, offsetY, false)
                          }
                        }
                      })
                    }, (photo: PhotoItem) => photo.id)
                  }
                  .width('100%')
                  .height('100%')
                  .index(Math.max(0, this.ui.currentIndex))
                  .loop(false)
                  .indicator(false)
                  .cachedCount(1)
                  .disableSwipe(this.ui.zoomScale > 1)
                  .onChange((index: number) => {
                    if (index !== this.ui.currentIndex) {
                      this.vm.goToIndex(index)
                    }
                  })
                  if (this.ui.controlsVisible) {
                    Column() {
                      Row({ space: 12 }) {
                        PhotoViewerFloatingButton({
                          symbol: $r('sys.symbol.chevron_backward'),
                          accessibilityLabel: $r('app.string.photo_back'),
                          action: () => this.closeViewer(),
                          active: false,
                          pending: false,
                          nativeSurface: this.useNativeSurface(),
                          systemMaterialAvailable: this.vm.appUIState.systemMaterialAvailable
                        })

                        Column({ space: 2 }) {
                          Text(this.currentPhoto()?.name || this.ui.sourceTitle)
                            .width('100%')
                            .fontSize(15)
                            .fontWeight(FontWeight.Medium)
                            .fontColor(Color.White)
                            .maxLines(1)
                            .textOverflow({ overflow: TextOverflow.Ellipsis })
                            .textAlign(TextAlign.Center)
                          Text(this.indexText())
                            .fontSize(12)
                            .fontColor(Color.White)
                            .opacity(0.74)
                        }
                        .layoutWeight(1)

                        if (this.useNativeSurface()) {
                          Text(this.indexText())
                            .height(34)
                            .padding({ left: 14, right: 14 })
                            .borderRadius(17)
                            .fontSize(13)
                            .fontColor(Color.White)
                            .textAlign(TextAlign.Center)
                            .attributeModifier(AppThemeSurfaceResolver.modifier(
                              AppThemeMaterialRole.Floating, true,
                              this.vm.appUIState.systemMaterialAvailable))
                            .hitTestBehavior(HitTestMode.None)
                        } else {
                          Text(this.indexText())
                            .height(34)
                            .padding({ left: 14, right: 14 })
                            .borderRadius(17)
                            .fontSize(13)
                            .fontColor(Color.White)
                            .textAlign(TextAlign.Center)
                            .backgroundColor($r('app.color.reload_title_color'))
                            .hitTestBehavior(HitTestMode.None)
                        }
                      }
                      .width('100%')
                      .padding({
                        top: Math.max(0, this.vm.appUIState.safeTop) + 8,
                        left: 14,
                        right: 14
                      })
                      .alignItems(VerticalAlign.Center)
                      Blank().layoutWeight(1)
                      Row({ space: 12 }) {
                        PhotoViewerFloatingButton({
                          symbol: ((this.currentPhoto()!)).favorite ? $r('sys.symbol.heart_fill') : $r('sys.symbol.heart'),
                          accessibilityLabel: ((this.currentPhoto()!)).favorite ? $r('app.string.photo_unfavorite') : $r('app.string.photo_favorite'),
                          action: () => this.toggleFavorite(),
                          active: ((this.currentPhoto()!)).favorite,
                          pending: this.ui.favoritePendingPhotoId === ((this.currentPhoto()!)).id,
                          nativeSurface: this.useNativeSurface(),
                          systemMaterialAvailable: this.vm.appUIState.systemMaterialAvailable
                        })
                        PhotoViewerFloatingButton({
                          symbol: this.ui.slideshowActive ? $r('sys.symbol.pause_circle') : $r('sys.symbol.play_circle'),
                          accessibilityLabel: this.ui.slideshowActive ?
                            $r('app.string.photo_slideshow_stop') : $r('app.string.photo_slideshow_start'),
                          action: () => this.vm.toggleSlideshow(),
                          active: this.ui.slideshowActive,
                          pending: false,
                          nativeSurface: this.useNativeSurface(),
                          systemMaterialAvailable: this.vm.appUIState.systemMaterialAvailable
                        })
                        PhotoViewerFloatingButton({
                          symbol: $r('sys.symbol.info_circle'),
                          accessibilityLabel: $r('app.string.photo_info'),
                          action: () => this.showInfo(),
                          active: false,
                          pending: false,
                          nativeSurface: this.useNativeSurface(),
                          systemMaterialAvailable: this.vm.appUIState.systemMaterialAvailable
                        })
                        Row() {
                          SaveButton({
                            icon: SaveIconStyle.FULL_FILLED,
                            text: SaveDescription.SAVE_TO_GALLERY,
                            buttonType: ButtonType.Capsule
                          })
                            .height(48)
                            .borderRadius(24)
                            .iconSize(20)
                            .fontSize(13)
                            .enabled((((this.currentPhoto()!))).canDownload && this.saveState !== PhotoSaveState.Downloading)
                            .onClick((_event: ClickEvent, result: SaveButtonOnClickResult) => {
                              this.onSaveAuthorization(result)
                            })
                        }
                        .height(48)
                        .hitTestBehavior(HitTestMode.BLOCK_HIERARCHY)
                        if (this.saveState === PhotoSaveState.Downloading) {
                          LoadingProgress()
                            .width(22)
                            .height(22)
                            .color(Color.White)
                        }
                      }
                      .constraintSize({ maxWidth: 680 })
                      .padding({
                        left: 14,
                        right: 14,
                        bottom: Math.max(0, this.vm.appUIState.safeBottom) + 14
                      })
                      .justifyContent(FlexAlign.Center)
                      .alignItems(VerticalAlign.Center)
                    }
                    .width('100%')
                    .height('100%')
                    .hitTestBehavior(HitTestMode.None)
                    .transition(TransitionEffect.OPACITY.animation({ duration: 160, curve: Curve.EaseOut }))
                  }
                }
              }
              .width('100%')
              .height('100%')
              .backgroundColor(Color.Black)
              .expandSafeArea(
                [SafeAreaType.SYSTEM],
                [SafeAreaEdge.TOP, SafeAreaEdge.BOTTOM]
              )
              .bindSheet($$this.ui.infoVisible,
                this.infoSheetBuilder(true, !this.vm.appUIState.systemMaterialAvailable), (true) ?
                (this.vm.appUIState.systemMaterialAvailable ? {
                  height: PHOTO_VIEWER_SHEET_HEIGHT,
                  preferType: SheetType.BOTTOM,
                  showClose: false,
                  dragBar: true,
                  scrollSizeMode: ScrollSizeMode.CONTINUOUS,
                  backgroundColor: Color.Transparent,
                  systemMaterial: AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating),
                  onDisappear: () => this.vm.hideInfo()
                } : {
                  height: PHOTO_VIEWER_SHEET_HEIGHT,
                  preferType: SheetType.BOTTOM,
                  showClose: false,
                  dragBar: true,
                  scrollSizeMode: ScrollSizeMode.CONTINUOUS,
                  backgroundColor: Color.Transparent,
                  onDisappear: () => this.vm.hideInfo()
                }) : {
                height: PHOTO_VIEWER_SHEET_HEIGHT,
                preferType: SheetType.BOTTOM,
                showClose: false,
                dragBar: true,
                scrollSizeMode: ScrollSizeMode.CONTINUOUS,
                backgroundColor: $r('app.color.bg_1'),
                onDisappear: () => this.vm.hideInfo()
              })
            } else {
              Stack() {
                if (this.ui.pageState === PageState.Loading) {
                  LoadingProgress()
                    .width(34)
                    .height(34)
                    .color(Color.White)
                } else if (this.ui.pageState === PageState.Error || !this.currentPhoto()) {
                  Column({ space: 14 }) {
                    SymbolGlyph($r('sys.symbol.exclamationmark_circle'))
                      .fontSize(44)
                      .fontColor([Color.White])
                    Text(photoBrowseErrorText(this.ui.loadErrorCode))
                      .fontSize(14)
                      .fontColor(Color.White)
                    Button($r('app.string.photo_back'), { type: ButtonType.Normal })
                      .height(42)
                      .borderRadius(21)
                      .onClick(() => this.closeViewer())
                  }
                } else {
                  Swiper(this.swiperController) {
                    ForEach(this.ui.photos, (photo: PhotoItem, index: number) => {
                      PhotoZoomableImage({
                        photo: photo,
                        active: index === this.ui.currentIndex,
                        resetRevision: this.ui.zoomRevision,
                        onSingleTap: () => this.vm.toggleControls(),
                        onInteraction: () => this.vm.notifyManualInteraction(),
                        onZoomChanged: (scale: number, offsetX: number, offsetY: number) => {
                          if (index === this.ui.currentIndex) {
                            this.vm.updateZoom(scale, offsetX, offsetY, false)
                          }
                        }
                      })
                    }, (photo: PhotoItem) => photo.id)
                  }
                  .width('100%')
                  .height('100%')
                  .index(Math.max(0, this.ui.currentIndex))
                  .loop(false)
                  .indicator(false)
                  .cachedCount(1)
                  .disableSwipe(this.ui.zoomScale > 1)
                  .onChange((index: number) => {
                    if (index !== this.ui.currentIndex) {
                      this.vm.goToIndex(index)
                    }
                  })
                  if (this.ui.controlsVisible) {
                    Column() {
                      Row({ space: 12 }) {
                        PhotoViewerFloatingButton({
                          symbol: $r('sys.symbol.chevron_backward'),
                          accessibilityLabel: $r('app.string.photo_back'),
                          action: () => this.closeViewer(),
                          active: false,
                          pending: false,
                          nativeSurface: this.useNativeSurface(),
                          systemMaterialAvailable: this.vm.appUIState.systemMaterialAvailable
                        })

                        Column({ space: 2 }) {
                          Text(this.currentPhoto()?.name || this.ui.sourceTitle)
                            .width('100%')
                            .fontSize(15)
                            .fontWeight(FontWeight.Medium)
                            .fontColor(Color.White)
                            .maxLines(1)
                            .textOverflow({ overflow: TextOverflow.Ellipsis })
                            .textAlign(TextAlign.Center)
                          Text(this.indexText())
                            .fontSize(12)
                            .fontColor(Color.White)
                            .opacity(0.74)
                        }
                        .layoutWeight(1)

                        if (this.useNativeSurface()) {
                          Text(this.indexText())
                            .height(34)
                            .padding({ left: 14, right: 14 })
                            .borderRadius(17)
                            .fontSize(13)
                            .fontColor(Color.White)
                            .textAlign(TextAlign.Center)
                            .attributeModifier(AppThemeSurfaceResolver.modifier(
                              AppThemeMaterialRole.Floating, true,
                              this.vm.appUIState.systemMaterialAvailable))
                            .hitTestBehavior(HitTestMode.None)
                        } else {
                          Text(this.indexText())
                            .height(34)
                            .padding({ left: 14, right: 14 })
                            .borderRadius(17)
                            .fontSize(13)
                            .fontColor(Color.White)
                            .textAlign(TextAlign.Center)
                            .backgroundColor($r('app.color.reload_title_color'))
                            .hitTestBehavior(HitTestMode.None)
                        }
                      }
                      .width('100%')
                      .padding({
                        top: Math.max(0, this.vm.appUIState.safeTop) + 8,
                        left: 14,
                        right: 14
                      })
                      .alignItems(VerticalAlign.Center)
                      Blank().layoutWeight(1)
                      Row({ space: 12 }) {
                        PhotoViewerFloatingButton({
                          symbol: ((this.currentPhoto()!)).favorite ? $r('sys.symbol.heart_fill') : $r('sys.symbol.heart'),
                          accessibilityLabel: ((this.currentPhoto()!)).favorite ? $r('app.string.photo_unfavorite') : $r('app.string.photo_favorite'),
                          action: () => this.toggleFavorite(),
                          active: ((this.currentPhoto()!)).favorite,
                          pending: this.ui.favoritePendingPhotoId === ((this.currentPhoto()!)).id,
                          nativeSurface: this.useNativeSurface(),
                          systemMaterialAvailable: this.vm.appUIState.systemMaterialAvailable
                        })
                        PhotoViewerFloatingButton({
                          symbol: this.ui.slideshowActive ? $r('sys.symbol.pause_circle') : $r('sys.symbol.play_circle'),
                          accessibilityLabel: this.ui.slideshowActive ?
                            $r('app.string.photo_slideshow_stop') : $r('app.string.photo_slideshow_start'),
                          action: () => this.vm.toggleSlideshow(),
                          active: this.ui.slideshowActive,
                          pending: false,
                          nativeSurface: this.useNativeSurface(),
                          systemMaterialAvailable: this.vm.appUIState.systemMaterialAvailable
                        })
                        PhotoViewerFloatingButton({
                          symbol: $r('sys.symbol.info_circle'),
                          accessibilityLabel: $r('app.string.photo_info'),
                          action: () => this.showInfo(),
                          active: false,
                          pending: false,
                          nativeSurface: this.useNativeSurface(),
                          systemMaterialAvailable: this.vm.appUIState.systemMaterialAvailable
                        })
                        Row() {
                          SaveButton({
                            icon: SaveIconStyle.FULL_FILLED,
                            text: SaveDescription.SAVE_TO_GALLERY,
                            buttonType: ButtonType.Capsule
                          })
                            .height(48)
                            .borderRadius(24)
                            .iconSize(20)
                            .fontSize(13)
                            .enabled((((this.currentPhoto()!))).canDownload && this.saveState !== PhotoSaveState.Downloading)
                            .onClick((_event: ClickEvent, result: SaveButtonOnClickResult) => {
                              this.onSaveAuthorization(result)
                            })
                        }
                        .height(48)
                        .hitTestBehavior(HitTestMode.BLOCK_HIERARCHY)
                        if (this.saveState === PhotoSaveState.Downloading) {
                          LoadingProgress()
                            .width(22)
                            .height(22)
                            .color(Color.White)
                        }
                      }
                      .constraintSize({ maxWidth: 680 })
                      .padding({
                        left: 14,
                        right: 14,
                        bottom: Math.max(0, this.vm.appUIState.safeBottom) + 14
                      })
                      .justifyContent(FlexAlign.Center)
                      .alignItems(VerticalAlign.Center)
                    }
                    .width('100%')
                    .height('100%')
                    .hitTestBehavior(HitTestMode.None)
                    .transition(TransitionEffect.OPACITY.animation({ duration: 160, curve: Curve.EaseOut }))
                  }
                }
              }
              .width('100%')
              .height('100%')
              .backgroundColor(Color.Black)
              .expandSafeArea(
                [SafeAreaType.SYSTEM],
                [SafeAreaEdge.TOP, SafeAreaEdge.BOTTOM]
              )
              .bindSheet($$this.ui.infoVisible, this.infoSheetBuilder(false, false), (false) ?
                (this.vm.appUIState.systemMaterialAvailable ? {
                  height: PHOTO_VIEWER_SHEET_HEIGHT,
                  preferType: SheetType.BOTTOM,
                  showClose: false,
                  dragBar: true,
                  scrollSizeMode: ScrollSizeMode.CONTINUOUS,
                  backgroundColor: Color.Transparent,
                  systemMaterial: AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating),
                  onDisappear: () => this.vm.hideInfo()
                } : {
                  height: PHOTO_VIEWER_SHEET_HEIGHT,
                  preferType: SheetType.BOTTOM,
                  showClose: false,
                  dragBar: true,
                  scrollSizeMode: ScrollSizeMode.CONTINUOUS,
                  backgroundColor: Color.Transparent,
                  onDisappear: () => this.vm.hideInfo()
                }) : {
                height: PHOTO_VIEWER_SHEET_HEIGHT,
                preferType: SheetType.BOTTOM,
                showClose: false,
                dragBar: true,
                scrollSizeMode: ScrollSizeMode.CONTINUOUS,
                backgroundColor: $r('app.color.bg_1'),
                onDisappear: () => this.vm.hideInfo()
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
          if (this.ui.pageState === PageState.Loading) {
            LoadingProgress()
              .width(34)
              .height(34)
              .color(Color.White)
          } else if (this.ui.pageState === PageState.Error || !this.currentPhoto()) {
            Column({ space: 14 }) {
              SymbolGlyph($r('sys.symbol.exclamationmark_circle'))
                .fontSize(44)
                .fontColor([Color.White])
              Text(photoBrowseErrorText(this.ui.loadErrorCode))
                .fontSize(14)
                .fontColor(Color.White)
              Button($r('app.string.photo_back'), { type: ButtonType.Normal })
                .height(42)
                .borderRadius(21)
                .onClick(() => this.closeViewer())
            }
          } else {
            Swiper(this.swiperController) {
              ForEach(this.ui.photos, (photo: PhotoItem, index: number) => {
                PhotoZoomableImage({
                  photo: photo,
                  active: index === this.ui.currentIndex,
                  resetRevision: this.ui.zoomRevision,
                  onSingleTap: () => this.vm.toggleControls(),
                  onInteraction: () => this.vm.notifyManualInteraction(),
                  onZoomChanged: (scale: number, offsetX: number, offsetY: number) => {
                    if (index === this.ui.currentIndex) {
                      this.vm.updateZoom(scale, offsetX, offsetY, false)
                    }
                  }
                })
              }, (photo: PhotoItem) => photo.id)
            }
            .width('100%')
            .height('100%')
            .index(Math.max(0, this.ui.currentIndex))
            .loop(false)
            .indicator(false)
            .cachedCount(1)
            .disableSwipe(this.ui.zoomScale > 1)
            .onChange((index: number) => {
              if (index !== this.ui.currentIndex) {
                this.vm.goToIndex(index)
              }
            })
            if (this.ui.controlsVisible) {
              Column() {
                Row({ space: 12 }) {
                  PhotoViewerFloatingButton({
                    symbol: $r('sys.symbol.chevron_backward'),
                    accessibilityLabel: $r('app.string.photo_back'),
                    action: () => this.closeViewer(),
                    active: false,
                    pending: false,
                    nativeSurface: this.useNativeSurface(),
                    systemMaterialAvailable: this.vm.appUIState.systemMaterialAvailable
                  })

                  Column({ space: 2 }) {
                    Text(this.currentPhoto()?.name || this.ui.sourceTitle)
                      .width('100%')
                      .fontSize(15)
                      .fontWeight(FontWeight.Medium)
                      .fontColor(Color.White)
                      .maxLines(1)
                      .textOverflow({ overflow: TextOverflow.Ellipsis })
                      .textAlign(TextAlign.Center)
                    Text(this.indexText())
                      .fontSize(12)
                      .fontColor(Color.White)
                      .opacity(0.74)
                  }
                  .layoutWeight(1)

                  if (this.useNativeSurface()) {
                    Text(this.indexText())
                      .height(34)
                      .padding({ left: 14, right: 14 })
                      .borderRadius(17)
                      .fontSize(13)
                      .fontColor(Color.White)
                      .textAlign(TextAlign.Center)
                      .attributeModifier(AppThemeSurfaceResolver.modifier(
                        AppThemeMaterialRole.Floating, true,
                        this.vm.appUIState.systemMaterialAvailable))
                      .hitTestBehavior(HitTestMode.None)
                  } else {
                    Text(this.indexText())
                      .height(34)
                      .padding({ left: 14, right: 14 })
                      .borderRadius(17)
                      .fontSize(13)
                      .fontColor(Color.White)
                      .textAlign(TextAlign.Center)
                      .backgroundColor($r('app.color.reload_title_color'))
                      .hitTestBehavior(HitTestMode.None)
                  }
                }
                .width('100%')
                .padding({
                  top: Math.max(0, this.vm.appUIState.safeTop) + 8,
                  left: 14,
                  right: 14
                })
                .alignItems(VerticalAlign.Center)
                Blank().layoutWeight(1)
                Row({ space: 12 }) {
                  PhotoViewerFloatingButton({
                    symbol: ((this.currentPhoto()!)).favorite ? $r('sys.symbol.heart_fill') : $r('sys.symbol.heart'),
                    accessibilityLabel: ((this.currentPhoto()!)).favorite ? $r('app.string.photo_unfavorite') : $r('app.string.photo_favorite'),
                    action: () => this.toggleFavorite(),
                    active: ((this.currentPhoto()!)).favorite,
                    pending: this.ui.favoritePendingPhotoId === ((this.currentPhoto()!)).id,
                    nativeSurface: this.useNativeSurface(),
                    systemMaterialAvailable: this.vm.appUIState.systemMaterialAvailable
                  })
                  PhotoViewerFloatingButton({
                    symbol: this.ui.slideshowActive ? $r('sys.symbol.pause_circle') : $r('sys.symbol.play_circle'),
                    accessibilityLabel: this.ui.slideshowActive ?
                      $r('app.string.photo_slideshow_stop') : $r('app.string.photo_slideshow_start'),
                    action: () => this.vm.toggleSlideshow(),
                    active: this.ui.slideshowActive,
                    pending: false,
                    nativeSurface: this.useNativeSurface(),
                    systemMaterialAvailable: this.vm.appUIState.systemMaterialAvailable
                  })
                  PhotoViewerFloatingButton({
                    symbol: $r('sys.symbol.info_circle'),
                    accessibilityLabel: $r('app.string.photo_info'),
                    action: () => this.showInfo(),
                    active: false,
                    pending: false,
                    nativeSurface: this.useNativeSurface(),
                    systemMaterialAvailable: this.vm.appUIState.systemMaterialAvailable
                  })
                  Row() {
                    SaveButton({
                      icon: SaveIconStyle.FULL_FILLED,
                      text: SaveDescription.SAVE_TO_GALLERY,
                      buttonType: ButtonType.Capsule
                    })
                      .height(48)
                      .borderRadius(24)
                      .iconSize(20)
                      .fontSize(13)
                      .enabled((((this.currentPhoto()!))).canDownload && this.saveState !== PhotoSaveState.Downloading)
                      .onClick((_event: ClickEvent, result: SaveButtonOnClickResult) => {
                        this.onSaveAuthorization(result)
                      })
                  }
                  .height(48)
                  .hitTestBehavior(HitTestMode.BLOCK_HIERARCHY)
                  if (this.saveState === PhotoSaveState.Downloading) {
                    LoadingProgress()
                      .width(22)
                      .height(22)
                      .color(Color.White)
                  }
                }
                .constraintSize({ maxWidth: 680 })
                .padding({
                  left: 14,
                  right: 14,
                  bottom: Math.max(0, this.vm.appUIState.safeBottom) + 14
                })
                .justifyContent(FlexAlign.Center)
                .alignItems(VerticalAlign.Center)
              }
              .width('100%')
              .height('100%')
              .hitTestBehavior(HitTestMode.None)
              .transition(TransitionEffect.OPACITY.animation({ duration: 160, curve: Curve.EaseOut }))
            }
          }
        }
        .width('100%')
        .height('100%')
        .backgroundColor(Color.Black)
        .expandSafeArea(
          [SafeAreaType.SYSTEM],
          [SafeAreaEdge.TOP, SafeAreaEdge.BOTTOM]
        )
        .bindSheet($$this.ui.infoVisible, this.infoSheetBuilder(false, false), (false) ?
          (this.vm.appUIState.systemMaterialAvailable ? {
            height: PHOTO_VIEWER_SHEET_HEIGHT,
            preferType: SheetType.BOTTOM,
            showClose: false,
            dragBar: true,
            scrollSizeMode: ScrollSizeMode.CONTINUOUS,
            backgroundColor: Color.Transparent,
            systemMaterial: AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating),
            onDisappear: () => this.vm.hideInfo()
          } : {
            height: PHOTO_VIEWER_SHEET_HEIGHT,
            preferType: SheetType.BOTTOM,
            showClose: false,
            dragBar: true,
            scrollSizeMode: ScrollSizeMode.CONTINUOUS,
            backgroundColor: Color.Transparent,
            onDisappear: () => this.vm.hideInfo()
          }) : {
          height: PHOTO_VIEWER_SHEET_HEIGHT,
          preferType: SheetType.BOTTOM,
          showClose: false,
          dragBar: true,
          scrollSizeMode: ScrollSizeMode.CONTINUOUS,
          backgroundColor: $r('app.color.bg_1'),
          onDisappear: () => this.vm.hideInfo()
        })
      }
      .attributeModifier(this.routeDestination.legacyModifier(this.routeOptions()))
      .gestureModifier(this.routeDestination.gestureModifier)
      .width('100%')
      .height('100%')
    }
  }
}
