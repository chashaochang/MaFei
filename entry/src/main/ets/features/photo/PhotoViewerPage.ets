import { HMRouter, HMRouterMgr } from '@hadss/hmrouter'
import { common } from '@kit.AbilityKit'
import { PageState } from '../../common/base/BaseUIState'
import { RouterConsts } from '../../common/RouterConsts'
import { AppRouteDestination } from '../../component/AppRouteDestination'
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

const PHOTO_VIEWER_SHEET_HEIGHT: string = '70%'

@HMRouter({
  pageUrl: RouterConsts.PhotoViewerPage,
  useNavDst: true,
  lifecycle: 'photoViewerLifecycle'
})
@ComponentV2
export struct PhotoViewerPage {
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

  @Builder
  private floatingButton(symbol: Resource, accessibilityText: Resource,
    action: () => void, active: boolean = false, pending: boolean = false) {
    if (this.useNativeSurface()) {
      Button({ type: ButtonType.Circle, stateEffect: true }) {
        if (pending) {
          LoadingProgress()
            .width(23)
            .height(23)
            .color(Color.White)
        } else {
          SymbolGlyph(symbol)
            .fontSize(24)
            .fontColor([active ? $r('app.color.color_main') : Color.White])
        }
      }
      .width(50)
      .height(50)
      .buttonStyle(ButtonStyleMode.TEXTUAL)
      .backgroundColor(Color.Transparent)
      .attributeModifier(AppThemeSurfaceResolver.modifier(
        AppThemeMaterialRole.InteractiveFloating, true,
        this.vm.appUIState.systemMaterialAvailable))
      .accessibilityText(accessibilityText)
      .enabled(!pending)
      .hitTestBehavior(HitTestMode.BLOCK_HIERARCHY)
      .onClick(action)
    } else {
      Button({ type: ButtonType.Circle, stateEffect: true }) {
        if (pending) {
          LoadingProgress()
            .width(23)
            .height(23)
            .color(Color.White)
        } else {
          SymbolGlyph(symbol)
            .fontSize(24)
            .fontColor([active ? $r('app.color.color_main') : Color.White])
        }
      }
      .width(50)
      .height(50)
      .backgroundColor($r('app.color.reload_title_color'))
      .accessibilityText(accessibilityText)
      .enabled(!pending)
      .hitTestBehavior(HitTestMode.BLOCK_HIERARCHY)
      .onClick(action)
    }
  }

  @Builder
  private indexBadge() {
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

  @Builder
  private topControls() {
    Row({ space: 12 }) {
      this.floatingButton(
        $r('sys.symbol.chevron_backward'),
        $r('app.string.photo_back'),
        () => HMRouterMgr.pop()
      )

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

      this.indexBadge()
    }
    .width('100%')
    .padding({
      top: Math.max(0, this.vm.appUIState.safeTop) + 8,
      left: 14,
      right: 14
    })
    .alignItems(VerticalAlign.Center)
  }

  @Builder
  private saveButton(photo: PhotoItem) {
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
        .enabled(photo.canDownload && this.saveState !== PhotoSaveState.Downloading)
        .onClick((_event: ClickEvent, result: SaveButtonOnClickResult) => {
          this.onSaveAuthorization(result)
        })
    }
    .height(48)
    .hitTestBehavior(HitTestMode.BLOCK_HIERARCHY)
  }

  @Builder
  private bottomControls(photo: PhotoItem) {
    Row({ space: 12 }) {
      this.floatingButton(
        photo.favorite ? $r('sys.symbol.heart_fill') : $r('sys.symbol.heart'),
        photo.favorite ? $r('app.string.photo_unfavorite') : $r('app.string.photo_favorite'),
        () => this.toggleFavorite(),
        photo.favorite,
        this.ui.favoritePendingPhotoId === photo.id
      )
      this.floatingButton(
        this.ui.slideshowActive ? $r('sys.symbol.pause_circle') : $r('sys.symbol.play_circle'),
        this.ui.slideshowActive ?
          $r('app.string.photo_slideshow_stop') : $r('app.string.photo_slideshow_start'),
        () => this.vm.toggleSlideshow(),
        this.ui.slideshowActive
      )
      this.floatingButton(
        $r('sys.symbol.info_circle'),
        $r('app.string.photo_info'),
        () => this.showInfo()
      )
      this.saveButton(photo)
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

  @Builder
  private controls(photo: PhotoItem) {
    if (this.ui.controlsVisible) {
      Column() {
        this.topControls()
        Blank().layoutWeight(1)
        this.bottomControls(photo)
      }
      .width('100%')
      .height('100%')
      .hitTestBehavior(HitTestMode.None)
      .transition(TransitionEffect.OPACITY.animation({ duration: 160, curve: Curve.EaseOut }))
    }
  }

  @Builder
  private photoSwiper() {
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
  }

  @Builder
  private infoContent(nativeSurface: boolean, blurFallback: boolean) {
    Column() {
      PhotoInfoSheet({
        photo: this.ui.infoPhoto,
        loading: this.ui.infoLoading,
        loadFailed: this.ui.infoErrorCode !== PhotoBrowseErrorCode.None,
        nativeSurface: nativeSurface,
        onRetry: () => {
          this.vm.loadCurrentInfo(true).catch((): void => {})
        }
      })
    }
    .width('100%')
    .height('100%')
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.Floating, blurFallback, false))
  }

  @Builder
  private viewerSurface(nativeSurface: boolean) {
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
            .onClick(() => HMRouterMgr.pop())
        }
      } else {
        this.photoSwiper()
        this.controls(this.currentPhoto()!)
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(Color.Black)
    .expandSafeArea(
      [SafeAreaType.SYSTEM],
      [SafeAreaEdge.TOP, SafeAreaEdge.BOTTOM]
    )
    .bindSheet($$this.ui.infoVisible, this.infoContent(
      nativeSurface,
      nativeSurface && !this.vm.appUIState.systemMaterialAvailable
    ), nativeSurface ?
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

  build() {
    AppRouteDestination({
      title: '',
      titleBarVisible: false,
      backButtonVisible: false,
      contentExtendsUnderTitleBar: true,
      beforeBack: () => this.beforeBack(),
      contentBuilder: () => {
        this.viewerSurface(true)
      },
      legacyContentBuilder: () => {
        this.viewerSurface(false)
      }
    })
  }
}
