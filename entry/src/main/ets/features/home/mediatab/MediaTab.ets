import { HMRouterMgr } from "@hadss/hmrouter";
import { AppStorageV2 } from "@kit.ArkUI";
import { PageState } from "../../../common/base/BaseUIState";
import { CustomRefreshComponent } from "../../../common/CustomRefreshComponent";
import { RouterConsts } from "../../../common/RouterConsts";
import { UIConstants } from "../../../common/UIConstants";
import { ActionBar } from "../../../component/ActionBar";
import { FailedView } from "../../../component/FailedView";
import { BaseGroupItem, ListGroup } from "../../../component/ListGroup";
import { AppUIState } from "../../../entity/AppUIState";
import { MediaLibraryRefreshEvent } from "../../../events/Events";
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from "../../../theme/AppThemeSurfaceResolver";
import { ManagementLibraryOperationResult } from "../../management/library/ManagementLibraryModels";
import { MediaUIState } from "./MediaUIState";
import { MediaViewModel } from "./MediaViewModel";
import { HomeInsetsPolicy } from "../HomeInsetsPolicy";
import { CollectionType } from "sdk/src/main/ets/generated-client/models";
import { MediaItem } from "../hometab/HomeUIState";

@ComponentV2
export struct MediaTab {
  @Param contentBottomInset?: number = undefined
  @Param rootTitleBarOwned: boolean = false
  @Param contentScroller: Scroller = new Scroller()
  @Param onAdministratorStateChange: (isAdministrator: boolean) => void = (_isAdministrator: boolean) => {}
  @Local ui: MediaUIState = new MediaUIState(this.getUIContext())
  @Local vm: MediaViewModel = new MediaViewModel(this.ui)
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;
  private refreshEventHandler = (): void => {
    this.vm.init()
  }

  private scrollTail(): number {
    return HomeInsetsPolicy.scrollTail(this.contentBottomInset, this.appUIState.safeBottom)
  }

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable)
  }

  private contentTopSpacer(): number {
    const safeTop = Math.max(0, this.appUIState.safeTop)
    return safeTop + UIConstants.ACTION_BAR_HEIGHT
  }

  @Monitor('ui.isAdministrator')
  onAdministratorChange(): void {
    this.onAdministratorStateChange(this.ui.isAdministrator)
  }

  aboutToAppear(): void {
    this.getUIContext().getHostContext()?.eventHub.on(MediaLibraryRefreshEvent, this.refreshEventHandler)
    this.vm.init()
  }

  aboutToDisappear(): void {
    this.getUIContext().getHostContext()?.eventHub.off(MediaLibraryRefreshEvent, this.refreshEventHandler)
    this.onAdministratorStateChange(false)
  }

  private emitLibraryRefresh(): void {
    this.getUIContext().getHostContext()?.eventHub.emit(MediaLibraryRefreshEvent)
  }

  private mediaIcon(item: MediaItem): Resource {
    if (item.collectionType === CollectionType.Photos) {
      return $r('app.media.ic_photo')
    }
    if (item.collectionType === CollectionType.Music) {
      return $r('app.media.ic_audio')
    }
    if (item.collectionType === CollectionType.Movies) {
      return $r('app.media.movie')
    }
    if (item.collectionType === CollectionType.Tvshows) {
      return $r('app.media.tv')
    }
    return $r('app.media.placeholder_folder')
  }

  private openLibrary(item: MediaItem): void {
    if (item.collectionType === CollectionType.Photos) {
      HMRouterMgr.to(RouterConsts.PhotoLibraryPage).withParam(item).push()
      return
    }
    if (item.collectionType === CollectionType.Music) {
      HMRouterMgr.to(RouterConsts.MusicLibraryPage).withParam(item).push()
      return
    }
    if (item.collectionType === CollectionType.Movies || item.collectionType === CollectionType.Tvshows ||
      item.type !== undefined && item.collectionType === undefined) {
      HMRouterMgr.to(RouterConsts.VideoListPage).withParam(item).push()
      return
    }
    this.getUIContext().getPromptAction().showToast({
      message: $r('app.string.media_library_type_unsupported')
    })
  }

  private showScanResult(result: ManagementLibraryOperationResult): void {
    this.getUIContext().getPromptAction().showToast({
      message: result.ok ? $r('app.string.management_library_scan_started') :
        $r('app.string.management_library_operation_failed')
    })
  }

  private actionTextColor(primary: boolean): ResourceColor {
    if (primary) {
      return $r('app.color.on_primary')
    }
    return this.useNativeSurface() ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  @Builder
  private administratorActionContent(title: ResourceStr, icon: Resource, primary: boolean) {
    Row({ space: 6 }) {
      SymbolGlyph(icon)
        .fontSize(17)
        .fontColor([this.actionTextColor(primary)])
      Text(title)
        .fontSize(13)
        .fontWeight(FontWeight.Medium)
        .fontColor(this.actionTextColor(primary))
        .maxLines(1)
    }
  }

  @Builder
  private nativeAdministratorAction(title: ResourceStr, icon: Resource, enabled: boolean,
    primary: boolean, action: () => void) {
    Button({ type: ButtonType.Normal }) {
      this.administratorActionContent(title, icon, primary)
    }
    .height(40)
    .padding({ left: 14, right: 14 })
    .borderRadius(20)
    .backgroundColor(Color.Transparent)
    .systemMaterial(AppThemeSurfaceResolver.material(
      primary ? AppThemeMaterialRole.PrimaryInteractive : AppThemeMaterialRole.AdaptiveInteractive))
    .enabled(enabled)
    .opacity(enabled ? 1 : 0.55)
    .onClick(action)
  }

  @Builder
  private feiniuAdministratorAction(title: ResourceStr, icon: Resource, enabled: boolean,
    primary: boolean, action: () => void) {
    Button({ type: ButtonType.Normal }) {
      this.administratorActionContent(title, icon, primary)
    }
    .height(40)
    .padding({ left: 14, right: 14 })
    .borderRadius(20)
    .backgroundColor(primary ? $r('app.color.color_main') : $r('app.color.bg_1'))
    .enabled(enabled)
    .opacity(enabled ? 1 : 0.55)
    .onClick(action)
  }

  @Builder
  private administratorAction(title: ResourceStr, icon: Resource, enabled: boolean,
    primary: boolean, action: () => void) {
    if (this.useNativeSurface()) {
      this.nativeAdministratorAction(title, icon, enabled, primary, action)
    } else {
      this.feiniuAdministratorAction(title, icon, enabled, primary, action)
    }
  }

  @Builder
  private administratorActions() {
    if (!this.rootTitleBarOwned && this.ui.isAdministrator) {
      Scroll() {
        Row({ space: 8 }) {
          this.administratorAction($r('app.string.management_library_add'), $r('sys.symbol.plus'), true,
            false, () => {
              HMRouterMgr.to(RouterConsts.ManagementLibraryEditorPage)
                .withParam({ libraryId: '' })
                .push()
            })
          this.administratorAction($r('app.string.management_library_scan_all'), $r('sys.symbol.play_fill'),
            !this.ui.isScanBusy, true, () => {
              this.vm.scanAll().then((result: ManagementLibraryOperationResult) => this.showScanResult(result))
            })
          this.administratorAction($r('app.string.management_library_title'),
            $r('sys.symbol.list_bullet_square'), true, false, () => {
              HMRouterMgr.to(RouterConsts.ManagementLibrariesPage).push()
            })
          this.administratorAction($r('app.string.management_refresh'), $r('sys.symbol.arrow_clockwise'), true,
            false, () => this.emitLibraryRefresh())
        }
      }
      .width('100%')
      .height(48)
      .scrollable(ScrollDirection.Horizontal)
      .scrollBar(BarState.Off)
    }
  }

  @Builder
  private topBar() {
    if (!this.rootTitleBarOwned) {
      if (this.useNativeSurface()) {
        ActionBar({
          title: '媒体库',
          showBackButton: false
        })
      } else {
        ActionBar({
          title: '媒体库',
          showBackButton: false
        })
          .backgroundBlurStyle(BlurStyle.COMPONENT_THICK)
      }
    }
  }

  build() {
    Stack() {
      if (this.ui.pageState === PageState.Content) {
        Refresh({ refreshing: $$this.ui.isRefreshing, builder: this.customRefreshComponent() }) {
          Scroll(this.contentScroller) {
            Column() {
              Blank().height(this.contentTopSpacer())
              this.administratorActions()
              // ListGroup({
              //   items: [new BaseGroupItem('收藏', $r('app.media.ic_unlike'), this.ui.favoriteCount + '')],
              //   onItemClick: (_index) => {
              //     HMRouterMgr.to(RouterConsts.FavoriteListPage).push()
              //   }
              // }).margin({ top: 12 })
              // Text('媒体库').fontColor($r('app.color.text_primary')).fontSize(14).fontWeight(FontWeight.Medium).margin({ top: 20 })
              ListGroup({
                items: this.ui.mediaList.map((e) => {
                  return new BaseGroupItem(e.name, this.mediaIcon(e),
                    e.childCount + '')
                }),
                onItemClick: (index) => {
                  this.openLibrary(this.ui.mediaList[index])
                }
              }).margin({ top: this.ui.isAdministrator && !this.rootTitleBarOwned ? 4 : 12 })
              Blank().height(this.scrollTail())
            }.alignItems(HorizontalAlign.Start)
            .padding({
              left: 16,
              right: 16
            })
          }
          .width('100%')
          .height('100%')
          .align(Alignment.Top)
        }
        .pullDownRatio(0.3)
        .refreshOffset(64)
        .maxPullDownDistance(64 + this.appUIState.safeTop)
        .width('100%')
        .height('100%')
        .onStateChange((status) => {
          this.ui.refreshStatus = status
        })
        .onOffsetChange((offset) => {
          this.ui.refreshOffset = offset
        })
        .onRefreshing(() => {
          this.vm.init()
        })
      }
      if (this.ui.pageState === PageState.Loading) {
        LoadingProgress()
          .width(42)
          .height(42)
          .align(Alignment.Center)
      }
      if (this.ui.pageState === PageState.Error) {
        FailedView()
          .onClick(() => {
            this.vm.init()
          })
      }

      this.topBar()
    }
    .backgroundColor(AppThemeSurfaceResolver.routeBackground(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable))
    .width('100%')
    .height('100%')
    .alignContent(Alignment.Top)
  }

  @Builder
  customRefreshComponent() {
    CustomRefreshComponent({
      refreshOffset: this.ui.refreshOffset,
      refreshStatus: this.ui.refreshStatus
    })
  }
}
