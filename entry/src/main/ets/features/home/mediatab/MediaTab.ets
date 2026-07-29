import { HMRouterMgr } from "@hadss/hmrouter";
import { AppStorageV2 } from "@kit.ArkUI";
import { CustomRefreshComponent } from "../../../common/CustomRefreshComponent";
import { RouterConsts } from "../../../common/RouterConsts";
import { UIConstants } from "../../../common/UIConstants";
import { ActionBar } from "../../../component/ActionBar";
import { BaseGroupItem, ListGroup } from "../../../component/ListGroup";
import { AppUIState } from "../../../entity/AppUIState";
import { AppThemeSurfaceResolver } from "../../../theme/AppThemeSurfaceResolver";
import { ToastUtil } from "../../../utils/ToastUtil";
import { MediaUIState } from "./MediaUIState";
import { MediaViewModel } from "./MediaViewModel";
import { HomeInsetsPolicy } from "../HomeInsetsPolicy";

@ComponentV2
export struct MediaTab {
  @Param contentBottomInset?: number = undefined
  @Param rootTitleBarOwned: boolean = false
  @Local ui: MediaUIState = new MediaUIState()
  @Local vm: MediaViewModel = new MediaViewModel(this.ui)
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;

  private scrollTail(): number {
    return HomeInsetsPolicy.scrollTail(this.contentBottomInset, this.appUIState.safeBottom)
  }

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable)
  }

  private contentTopSpacer(): number {
    const safeTop = Math.max(0, this.appUIState.safeTop)
    return this.rootTitleBarOwned ? 0 : safeTop + UIConstants.ACTION_BAR_HEIGHT
  }

  aboutToAppear(): void {
    this.vm.init()
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
      Refresh({ refreshing: $$this.ui.isRefreshing, builder: this.customRefreshComponent() }) {
        Scroll() {
          Column() {
            Blank().height(this.contentTopSpacer())
            // ListGroup({
            //   items: [new BaseGroupItem('收藏', $r('app.media.ic_unlike'), this.ui.favoriteCount + '')],
            //   onItemClick: (_index) => {
            //     HMRouterMgr.to(RouterConsts.FavoriteListPage).push()
            //   }
            // }).margin({ top: 12 })
            // Text('媒体库').fontColor($r('app.color.text_primary')).fontSize(14).fontWeight(FontWeight.Medium).margin({ top: 20 })
            ListGroup({
              items: this.ui.mediaList.map((e) => {
                return new BaseGroupItem(e.name, e.type == 'Movie' ? $r('app.media.movie') : $r('app.media.tv'),
                  e.childCount + '')
              }),
              onItemClick: (index) => {
                // if (this.ui.mediaList[index].type == undefined) {
                //   ToastUtil.showToast(this.getUIContext(), '暂不支持混合文件夹')
                //   return;
                // }
                HMRouterMgr.to(RouterConsts.VideoListPage)
                  .withParam(this.ui.mediaList[index])
                  .push()
              }
            }).margin({ top: 12 })
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
