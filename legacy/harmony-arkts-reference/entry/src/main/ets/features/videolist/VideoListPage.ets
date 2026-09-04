import { GridView, RefreshLayoutStatus } from "@abner/refresh_v2";
import { HMRouter, HMRouterMgr } from "@hadss/hmrouter";
import { ImageKnifeComponent, ImageKnifeOption } from "@ohos/imageknifepro";
import { PageState } from "../../common/base/BaseUIState";
import { RouterConsts } from "../../common/RouterConsts";
import { ActionBar } from "../../component/ActionBar";
import { AppRouteDestination, AppRouteDestinationOptions } from '../../component/AppRouteDestination';
import { EmptyView } from "../../component/EmptyView";
import { FailedView } from "../../component/FailedView";
import { SkeletonGridView } from "../../component/skeleton/SkeletonGridView";
import { sortOptions } from "../../entity/SortOption";
import { MediaItem, VideoItem } from "../home/hometab/HomeUIState";
import { VideoListUIState } from "./VideoListUIState";
import { VideoListViewModel } from "./VideoListViewModel";
import { BreakpointTypeEnum } from "../../utils/BreakpointSystem";
import { HORIZONTAL_CARD_SPACE } from "../../common/OneMoreConst";
import { AppThemeSurfaceResolver } from "../../theme/AppThemeSurfaceResolver";
import { AppStorageV2 } from "@kit.ArkUI";
import { AppUIState } from "../../entity/AppUIState";
import {
  ManagementLibraryOperationCode,
  ManagementLibraryOperationResult
} from "../management/library/ManagementLibraryModels";
import { ManagementMediaDeleteDialog } from "../management/library/ManagementMediaDeleteDialog";
import { createMediaImageKnifeOption } from "../../media/catalog/MediaImageKnifeAdapter";
import { MediaRefKind } from "../../media/MediaRef";
import { toMediaRouteRefData } from "../../media/navigation/MediaRouteRef";
import { HdsNavDestination, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'
import { HomeContentLayoutPolicy } from '../home/HomeContentLayoutPolicy'

@HMRouter({
  pageUrl: RouterConsts.VideoListPage,
  useNavDst: true
})
@ComponentV2
export struct VideoListPage {
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
  //首页来的要隐藏顶部栏
  @Param fromHome: boolean = false
  @Param useHomeContentWidth: boolean = false
  @Param mediaItem: MediaItem = {} as MediaItem
  @Local ui: VideoListUIState = new VideoListUIState(this.getUIContext())
  @Local vm: VideoListViewModel = new VideoListViewModel(this.ui)

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable)
  }

  private pageBackground(): ResourceColor {
    if (this.vm.appUIState.isBigScreen) {
      return Color.Transparent
    }
    return AppThemeSurfaceResolver.routeBackground(this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable)
  }

  private primaryTextColor(nativeSurface: boolean): ResourceColor {
    return nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private contentBreakpoint(): string {
    const useHomeContentWidth = this.fromHome ? this.useHomeContentWidth :
      this.vm.appUIState.homeContentNavigationMounted
    return HomeContentLayoutPolicy.breakpoint(this.vm.appUIState, useHomeContentWidth)
  }

  private getString(name: string): string {
    return this.getUIContext().getHostContext()?.resourceManager.getStringByNameSync(name) || name
  }

  private routeTitle(): ResourceStr {
    if (!this.ui.selectionMode) {
      return this.ui.title
    }
    return `${this.ui.selectedIds.length} · ${this.getString('management_media_delete')}`
  }

  private localizedDeleteMessage(result: ManagementLibraryOperationResult): string {
    if (result.message.startsWith('management_')) {
      return this.getString(result.message)
    }
    if (result.message.length > 0) {
      return result.message
    }
    if (result.code === ManagementLibraryOperationCode.AuthenticationExpired) {
      return this.getString('management_library_authentication_expired')
    }
    if (result.code === ManagementLibraryOperationCode.PermissionDenied) {
      return this.getString('management_permission_denied')
    }
    if (result.code === ManagementLibraryOperationCode.NotFound) {
      return this.getString('management_library_not_found')
    }
    if (result.code === ManagementLibraryOperationCode.Conflict) {
      return this.getString('management_media_delete_conflict')
    }
    return this.getString('management_media_delete_failed')
  }

  private async openDeleteDialog(): Promise<void> {
    if (!this.ui.selectionMode || this.ui.deletingSelection || this.ui.deleteDialogVisible) {
      return
    }
    this.ui.deleteDialogVisible = true
    const preparation = await this.vm.prepareDeleteSelection()
    if (!preparation.ok) {
      this.ui.deleteDialogVisible = false
      this.getUIContext().getPromptAction().showToast({
        message: this.localizedDeleteMessage(preparation)
      })
      return
    }
    const summary = this.vm.deleteSummary()
    if (summary.allowedIds.length === 0) {
      this.ui.deleteDialogVisible = false
      this.getUIContext().getPromptAction().showToast({
        message: $r('app.string.management_media_delete_no_eligible')
      })
      return
    }
    ManagementMediaDeleteDialog.show(this.getUIContext(), summary, () => {
      this.ui.deleteDialogVisible = false
      this.vm.deleteSelection().then((result: ManagementLibraryOperationResult) => {
        this.getUIContext().getPromptAction().showToast({
          message: result.ok ? $r('app.string.management_media_delete_success') :
            this.localizedDeleteMessage(result)
        })
      }).catch(() => {
        this.getUIContext().getPromptAction().showToast({
          message: $r('app.string.management_media_delete_failed')
        })
      })
    }, () => {
      this.ui.deleteDialogVisible = false
    })
  }

  private selectionMenus(): Array<NavigationMenuItem> {
    if (!this.ui.selectionMode) {
      return []
    }
    return [{
      value: $r('app.string.management_media_delete'),
      icon: $r('sys.symbol.trash'),
      isEnabled: !this.ui.deletingSelection && this.ui.selectedIds.length > 0,
      action: () => this.openDeleteDialog()
    }, {
      value: $r('app.string.management_cancel'),
      icon: $r('sys.symbol.xmark'),
      isEnabled: !this.ui.deletingSelection,
      action: () => this.vm.cancelSelection()
    }]
  }

  private initEmbeddedMediaItem(): void {
    if (!this.fromHome || !this.mediaItem?.id) {
      return
    }
    const currentRefIdentity = this.vm.mediaItem?.mediaRef?.identity || ''
    const nextRefIdentity = this.mediaItem.mediaRef?.identity || ''
    if (this.vm.mediaItem?.id === this.mediaItem.id && currentRefIdentity === nextRefIdentity) {
      return
    }
    this.vm.init(this.mediaItem)
  }

  @Monitor('mediaItem')
  onEmbeddedMediaItemChange(): void {
    this.initEmbeddedMediaItem()
  }

  aboutToAppear(): void {
    if (this.fromHome) {
      this.initEmbeddedMediaItem()
      return
    }
    let param = HMRouterMgr.getCurrentParam()
    if (param) {
      this.vm.init(param as MediaItem)
    }
  }

  // CustomBuilderAdapter: video-list-grid-item
  @Builder
  private gridItemBuilder(item: Object, _index: number) {
    VideoCard({
      item: item as VideoItem,
      selectable: this.vm.canSelect(item as VideoItem),
      selected: this.ui.isSelected((item as VideoItem).id),
      selectionMode: this.ui.selectionMode,
      onLongPress: () => this.vm.enterSelection(item as VideoItem),
      onSelect: () => this.vm.toggleSelection(item as VideoItem),
      onOpen: () => {
        HMRouterMgr.to(RouterConsts.VideoDetailPage)
          .withParam({
            id: (item as VideoItem).id,
            ref: (item as VideoItem).mediaRef ?
              toMediaRouteRefData((item as VideoItem).mediaRef!) : undefined,
            mediaName: (item as VideoItem).mediaName,
            isTvShows: (item as VideoItem).kind === MediaRefKind.Series ||
              (item as VideoItem).mediaRef?.kind === MediaRefKind.Series ||
              (item as VideoItem).type === 'Series'
          })
          .push()
      }
    })
  }

  // CustomBuilderAdapter: video-list-grid-empty
  @Builder
  private gridEmptyBuilder() {
    EmptyView()
  }







  private routeOptions(): AppRouteDestinationOptions {
    return {
          title: this.routeTitle(),
          menus: this.selectionMenus(),
          beforeBack: () => {
                    if (this.ui.selectionMode) {
                      this.vm.cancelSelection()
                      return true
                    }
                    return false
                  }
        }
  }

  build() {
    if (this.fromHome) {
      Column() {
        Column() {
          if (this.ui.selectionMode && (this.fromHome || !(this.useNativeSurface()))) {
            Row({ space: 8 }) {
              if ((this.fromHome)) {
                Text(this.routeTitle())
                  .fontSize(15)
                  .fontWeight(FontWeight.Medium)
                  .fontColor(this.primaryTextColor(this.useNativeSurface()))
                  .maxLines(1)
                  .textOverflow({ overflow: TextOverflow.Ellipsis })
                  .layoutWeight(1)
              } else {
                Blank().layoutWeight(1)
              }
              Button({ type: ButtonType.Circle, stateEffect: true }) {
                SymbolGlyph($r('sys.symbol.trash'))
                  .fontSize(20)
                  .fontColor([$r('app.color.main_red_color')])
              }
              .width(40)
              .height(40)
              .buttonStyle(ButtonStyleMode.TEXTUAL)
              .enabled(!this.ui.deletingSelection && this.ui.selectedIds.length > 0)
              .accessibilityText($r('app.string.management_media_delete'))
              .onClick(() => this.openDeleteDialog())
              Button({ type: ButtonType.Circle, stateEffect: true }) {
                SymbolGlyph($r('sys.symbol.xmark'))
                  .fontSize(20)
                  .fontColor([this.primaryTextColor(this.useNativeSurface())])
              }
              .width(40)
              .height(40)
              .buttonStyle(ButtonStyleMode.TEXTUAL)
              .enabled(!this.ui.deletingSelection)
              .accessibilityText($r('app.string.management_cancel'))
              .onClick(() => this.vm.cancelSelection())
            }
            .width('100%')
            .height(48)
          }
          Row() {
            Text(sortOptions[this.ui.selectedSortIndex].label)
              .fontSize(14)
              .fontColor(this.primaryTextColor((this.useNativeSurface())))
              .height(46)
              .onClick(() => {
                this.vm.sortDialog.show()
              })
            Image(this.ui.isDesc ? $r('app.media.jiangxu') : $r('app.media.shengxu'))
              .width(16)
              .height(16)
              .fillColor(this.primaryTextColor((this.useNativeSurface())))
              .margin({ left: 6 })
              .onClick(() => {
                if (this.vm.controller.getRefreshLayoutStatus() === RefreshLayoutStatus.Finish) {
                  this.ui.isDesc = !this.ui.isDesc
                  this.vm.refresh()
                }
              })
            if ((this.useNativeSurface())) {
              Text(this.ui.totalCount)
                .fontSize(12)
                .fontColor($r('sys.color.font_secondary'))
                .padding({
                  left: 6,
                  right: 6,
                  top: 4,
                  bottom: 4
                })
                .margin({
                  left: 12
                })
                .borderRadius(4)
            } else {
              Text(this.ui.totalCount)
                .fontSize(12)
                .fontColor($r('app.color.text_1'))
                .backgroundColor($r('app.color.bg_1'))
                .padding({
                  left: 6,
                  right: 6,
                  top: 4,
                  bottom: 4
                })
                .margin({
                  left: 12
                })
                .borderRadius(4)
            }
            Blank().layoutWeight(1)
          }
          .width('100%')
          .height(46)

          GridView({
            lazyDataSource: this.vm.dataSource,
            onLazyDataSource: (dataSource) => {
              this.vm.dataSource = dataSource
            },
            itemLayout: (item, index) => this.gridItemBuilder(item, index),
            emptyLayout: () => this.gridEmptyBuilder(),
            showEmptyLayout: this.ui.totalCount === '0',
            controller: this.vm.controller,
            onRefresh: () => {
              this.vm.refresh()
            },
            onLoadMore: () => {
              this.vm.loadMore()
            },
            gridAttribute: (attr) => {
              attr.scrollBar = BarState.Off
              switch (this.contentBreakpoint()) {
                case BreakpointTypeEnum.XS:
                  attr.columnsTemplate = '1fr '.repeat(2)
                  break
                case BreakpointTypeEnum.SM:
                  attr.columnsTemplate = '1fr '.repeat(3)
                  break
                case BreakpointTypeEnum.MD:
                  attr.columnsTemplate = '1fr '.repeat(4)
                  break
                case BreakpointTypeEnum.LG:
                  attr.columnsTemplate = '1fr '.repeat(6)
                  break
                case BreakpointTypeEnum.XL:
                  attr.columnsTemplate = '1fr '.repeat(8)
                  break
                case BreakpointTypeEnum.XXL:
                  attr.columnsTemplate = '1fr '.repeat(12)
                  break
                default:
                  attr.columnsTemplate = '1fr '.repeat(3)
                  break
              }
              attr.columnsGap = HORIZONTAL_CARD_SPACE.getValue(this.contentBreakpoint())
            },
            lazyCachedCount: 10
          }).width('100%')
            .layoutWeight(1)
          if (this.ui.pageState == PageState.Loading) {
            SkeletonGridView()
          }
          if (this.ui.pageState == PageState.Error) {
            FailedView()
              .onClick(() => {
                this.vm.refresh()
              })
          }
        }
        .width('100%')
        .layoutWeight(1)
        .backgroundColor((this.useNativeSurface()) ? Color.Transparent : this.pageBackground())
        .borderRadius({
          topLeft: (this.useNativeSurface()) ? 0 : 20,
          topRight: (this.useNativeSurface()) ? 0 : 20
        })
        .padding({
          left: HORIZONTAL_CARD_SPACE.getValue(this.contentBreakpoint()) * 2,
          right: HORIZONTAL_CARD_SPACE.getValue(this.contentBreakpoint()) * 2,
          bottom: this.vm.appUIState.safeBottom
        })
      }
      .width('100%')
      .height('100%')
      .backgroundColor(this.pageBackground())
    } else {
      if (this.routeDestination.usesHdsDestination()) {
        HdsNavDestination() {
          Column() {
            Blank().height(this.routeDestination.nativeContentTopInset(this.routeOptions()))
            Stack() {
              if (this.routeDestination.usesNativeChrome()) {
                Column() {
                  if ((false)) {
                    ActionBar({
                      title: this.routeTitle(),
                      onBackClick: () => {
                        if (this.ui.selectionMode) {
                          this.vm.cancelSelection()
                        } else {
                          HMRouterMgr.pop()
                        }
                      }
                    })
                  }
                  Column() {
                    if (this.ui.selectionMode && (this.fromHome || !(!(false)))) {
                      Row({ space: 8 }) {
                        if ((this.fromHome)) {
                          Text(this.routeTitle())
                            .fontSize(15)
                            .fontWeight(FontWeight.Medium)
                            .fontColor(this.primaryTextColor(this.useNativeSurface()))
                            .maxLines(1)
                            .textOverflow({ overflow: TextOverflow.Ellipsis })
                            .layoutWeight(1)
                        } else {
                          Blank().layoutWeight(1)
                        }
                        Button({ type: ButtonType.Circle, stateEffect: true }) {
                          SymbolGlyph($r('sys.symbol.trash'))
                            .fontSize(20)
                            .fontColor([$r('app.color.main_red_color')])
                        }
                        .width(40)
                        .height(40)
                        .buttonStyle(ButtonStyleMode.TEXTUAL)
                        .enabled(!this.ui.deletingSelection && this.ui.selectedIds.length > 0)
                        .accessibilityText($r('app.string.management_media_delete'))
                        .onClick(() => this.openDeleteDialog())
                        Button({ type: ButtonType.Circle, stateEffect: true }) {
                          SymbolGlyph($r('sys.symbol.xmark'))
                            .fontSize(20)
                            .fontColor([this.primaryTextColor(this.useNativeSurface())])
                        }
                        .width(40)
                        .height(40)
                        .buttonStyle(ButtonStyleMode.TEXTUAL)
                        .enabled(!this.ui.deletingSelection)
                        .accessibilityText($r('app.string.management_cancel'))
                        .onClick(() => this.vm.cancelSelection())
                      }
                      .width('100%')
                      .height(48)
                    }
                    Row() {
                      Text(sortOptions[this.ui.selectedSortIndex].label)
                        .fontSize(14)
                        .fontColor(this.primaryTextColor((!(false))))
                        .height(46)
                        .onClick(() => {
                          this.vm.sortDialog.show()
                        })
                      Image(this.ui.isDesc ? $r('app.media.jiangxu') : $r('app.media.shengxu'))
                        .width(16)
                        .height(16)
                        .fillColor(this.primaryTextColor((!(false))))
                        .margin({ left: 6 })
                        .onClick(() => {
                          if (this.vm.controller.getRefreshLayoutStatus() === RefreshLayoutStatus.Finish) {
                            this.ui.isDesc = !this.ui.isDesc
                            this.vm.refresh()
                          }
                        })
                      if ((!(false))) {
                        Text(this.ui.totalCount)
                          .fontSize(12)
                          .fontColor($r('sys.color.font_secondary'))
                          .padding({
                            left: 6,
                            right: 6,
                            top: 4,
                            bottom: 4
                          })
                          .margin({
                            left: 12
                          })
                          .borderRadius(4)
                      } else {
                        Text(this.ui.totalCount)
                          .fontSize(12)
                          .fontColor($r('app.color.text_1'))
                          .backgroundColor($r('app.color.bg_1'))
                          .padding({
                            left: 6,
                            right: 6,
                            top: 4,
                            bottom: 4
                          })
                          .margin({
                            left: 12
                          })
                          .borderRadius(4)
                      }
                      Blank().layoutWeight(1)
                    }
                    .width('100%')
                    .height(46)

                    GridView({
                      lazyDataSource: this.vm.dataSource,
                      onLazyDataSource: (dataSource) => {
                        this.vm.dataSource = dataSource
                      },
                      itemLayout: (item, index) => this.gridItemBuilder(item, index),
                      emptyLayout: () => this.gridEmptyBuilder(),
                      showEmptyLayout: this.ui.totalCount === '0',
                      controller: this.vm.controller,
                      onRefresh: () => {
                        this.vm.refresh()
                      },
                      onLoadMore: () => {
                        this.vm.loadMore()
                      },
                      gridAttribute: (attr) => {
                        attr.scrollBar = BarState.Off
                        switch (this.contentBreakpoint()) {
                          case BreakpointTypeEnum.XS:
                            attr.columnsTemplate = '1fr '.repeat(2)
                            break
                          case BreakpointTypeEnum.SM:
                            attr.columnsTemplate = '1fr '.repeat(3)
                            break
                          case BreakpointTypeEnum.MD:
                            attr.columnsTemplate = '1fr '.repeat(4)
                            break
                          case BreakpointTypeEnum.LG:
                            attr.columnsTemplate = '1fr '.repeat(6)
                            break
                          case BreakpointTypeEnum.XL:
                            attr.columnsTemplate = '1fr '.repeat(8)
                            break
                          case BreakpointTypeEnum.XXL:
                            attr.columnsTemplate = '1fr '.repeat(12)
                            break
                          default:
                            attr.columnsTemplate = '1fr '.repeat(3)
                            break
                        }
                        attr.columnsGap = HORIZONTAL_CARD_SPACE.getValue(this.contentBreakpoint())
                      },
                      lazyCachedCount: 10
                    }).width('100%')
                      .layoutWeight(1)
                    if (this.ui.pageState == PageState.Loading) {
                      SkeletonGridView()
                    }
                    if (this.ui.pageState == PageState.Error) {
                      FailedView()
                        .onClick(() => {
                          this.vm.refresh()
                        })
                    }
                  }
                  .width('100%')
                  .layoutWeight(1)
                  .backgroundColor((!(false)) ? Color.Transparent : this.pageBackground())
                  .borderRadius({
                    topLeft: (!(false)) ? 0 : 20,
                    topRight: (!(false)) ? 0 : 20
                  })
                  .padding({
                    left: HORIZONTAL_CARD_SPACE.getValue(this.contentBreakpoint()) * 2,
                    right: HORIZONTAL_CARD_SPACE.getValue(this.contentBreakpoint()) * 2,
                    bottom: this.vm.appUIState.safeBottom
                  })
                }
                .width('100%')
                .height('100%')
                .backgroundColor((false) ? $r('app.color.bg_main') : Color.Transparent)
              } else {
                Column() {
                  if ((true)) {
                    ActionBar({
                      title: this.routeTitle(),
                      onBackClick: () => {
                        if (this.ui.selectionMode) {
                          this.vm.cancelSelection()
                        } else {
                          HMRouterMgr.pop()
                        }
                      }
                    })
                  }
                  Column() {
                    if (this.ui.selectionMode && (this.fromHome || !(!(true)))) {
                      Row({ space: 8 }) {
                        if ((this.fromHome)) {
                          Text(this.routeTitle())
                            .fontSize(15)
                            .fontWeight(FontWeight.Medium)
                            .fontColor(this.primaryTextColor(this.useNativeSurface()))
                            .maxLines(1)
                            .textOverflow({ overflow: TextOverflow.Ellipsis })
                            .layoutWeight(1)
                        } else {
                          Blank().layoutWeight(1)
                        }
                        Button({ type: ButtonType.Circle, stateEffect: true }) {
                          SymbolGlyph($r('sys.symbol.trash'))
                            .fontSize(20)
                            .fontColor([$r('app.color.main_red_color')])
                        }
                        .width(40)
                        .height(40)
                        .buttonStyle(ButtonStyleMode.TEXTUAL)
                        .enabled(!this.ui.deletingSelection && this.ui.selectedIds.length > 0)
                        .accessibilityText($r('app.string.management_media_delete'))
                        .onClick(() => this.openDeleteDialog())
                        Button({ type: ButtonType.Circle, stateEffect: true }) {
                          SymbolGlyph($r('sys.symbol.xmark'))
                            .fontSize(20)
                            .fontColor([this.primaryTextColor(this.useNativeSurface())])
                        }
                        .width(40)
                        .height(40)
                        .buttonStyle(ButtonStyleMode.TEXTUAL)
                        .enabled(!this.ui.deletingSelection)
                        .accessibilityText($r('app.string.management_cancel'))
                        .onClick(() => this.vm.cancelSelection())
                      }
                      .width('100%')
                      .height(48)
                    }
                    Row() {
                      Text(sortOptions[this.ui.selectedSortIndex].label)
                        .fontSize(14)
                        .fontColor(this.primaryTextColor((!(true))))
                        .height(46)
                        .onClick(() => {
                          this.vm.sortDialog.show()
                        })
                      Image(this.ui.isDesc ? $r('app.media.jiangxu') : $r('app.media.shengxu'))
                        .width(16)
                        .height(16)
                        .fillColor(this.primaryTextColor((!(true))))
                        .margin({ left: 6 })
                        .onClick(() => {
                          if (this.vm.controller.getRefreshLayoutStatus() === RefreshLayoutStatus.Finish) {
                            this.ui.isDesc = !this.ui.isDesc
                            this.vm.refresh()
                          }
                        })
                      if ((!(true))) {
                        Text(this.ui.totalCount)
                          .fontSize(12)
                          .fontColor($r('sys.color.font_secondary'))
                          .padding({
                            left: 6,
                            right: 6,
                            top: 4,
                            bottom: 4
                          })
                          .margin({
                            left: 12
                          })
                          .borderRadius(4)
                      } else {
                        Text(this.ui.totalCount)
                          .fontSize(12)
                          .fontColor($r('app.color.text_1'))
                          .backgroundColor($r('app.color.bg_1'))
                          .padding({
                            left: 6,
                            right: 6,
                            top: 4,
                            bottom: 4
                          })
                          .margin({
                            left: 12
                          })
                          .borderRadius(4)
                      }
                      Blank().layoutWeight(1)
                    }
                    .width('100%')
                    .height(46)

                    GridView({
                      lazyDataSource: this.vm.dataSource,
                      onLazyDataSource: (dataSource) => {
                        this.vm.dataSource = dataSource
                      },
                      itemLayout: (item, index) => this.gridItemBuilder(item, index),
                      emptyLayout: () => this.gridEmptyBuilder(),
                      showEmptyLayout: this.ui.totalCount === '0',
                      controller: this.vm.controller,
                      onRefresh: () => {
                        this.vm.refresh()
                      },
                      onLoadMore: () => {
                        this.vm.loadMore()
                      },
                      gridAttribute: (attr) => {
                        attr.scrollBar = BarState.Off
                        switch (this.contentBreakpoint()) {
                          case BreakpointTypeEnum.XS:
                            attr.columnsTemplate = '1fr '.repeat(2)
                            break
                          case BreakpointTypeEnum.SM:
                            attr.columnsTemplate = '1fr '.repeat(3)
                            break
                          case BreakpointTypeEnum.MD:
                            attr.columnsTemplate = '1fr '.repeat(4)
                            break
                          case BreakpointTypeEnum.LG:
                            attr.columnsTemplate = '1fr '.repeat(6)
                            break
                          case BreakpointTypeEnum.XL:
                            attr.columnsTemplate = '1fr '.repeat(8)
                            break
                          case BreakpointTypeEnum.XXL:
                            attr.columnsTemplate = '1fr '.repeat(12)
                            break
                          default:
                            attr.columnsTemplate = '1fr '.repeat(3)
                            break
                        }
                        attr.columnsGap = HORIZONTAL_CARD_SPACE.getValue(this.contentBreakpoint())
                      },
                      lazyCachedCount: 10
                    }).width('100%')
                      .layoutWeight(1)
                    if (this.ui.pageState == PageState.Loading) {
                      SkeletonGridView()
                    }
                    if (this.ui.pageState == PageState.Error) {
                      FailedView()
                        .onClick(() => {
                          this.vm.refresh()
                        })
                    }
                  }
                  .width('100%')
                  .layoutWeight(1)
                  .backgroundColor((!(true)) ? Color.Transparent : this.pageBackground())
                  .borderRadius({
                    topLeft: (!(true)) ? 0 : 20,
                    topRight: (!(true)) ? 0 : 20
                  })
                  .padding({
                    left: HORIZONTAL_CARD_SPACE.getValue(this.contentBreakpoint()) * 2,
                    right: HORIZONTAL_CARD_SPACE.getValue(this.contentBreakpoint()) * 2,
                    bottom: this.vm.appUIState.safeBottom
                  })
                }
                .width('100%')
                .height('100%')
                .backgroundColor((true) ? $r('app.color.bg_main') : Color.Transparent)
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
          Column() {
            if ((true)) {
              ActionBar({
                title: this.routeTitle(),
                onBackClick: () => {
                  if (this.ui.selectionMode) {
                    this.vm.cancelSelection()
                  } else {
                    HMRouterMgr.pop()
                  }
                }
              })
            }
            Column() {
              if (this.ui.selectionMode && (this.fromHome || !(!(true)))) {
                Row({ space: 8 }) {
                  if ((this.fromHome)) {
                    Text(this.routeTitle())
                      .fontSize(15)
                      .fontWeight(FontWeight.Medium)
                      .fontColor(this.primaryTextColor(this.useNativeSurface()))
                      .maxLines(1)
                      .textOverflow({ overflow: TextOverflow.Ellipsis })
                      .layoutWeight(1)
                  } else {
                    Blank().layoutWeight(1)
                  }
                  Button({ type: ButtonType.Circle, stateEffect: true }) {
                    SymbolGlyph($r('sys.symbol.trash'))
                      .fontSize(20)
                      .fontColor([$r('app.color.main_red_color')])
                  }
                  .width(40)
                  .height(40)
                  .buttonStyle(ButtonStyleMode.TEXTUAL)
                  .enabled(!this.ui.deletingSelection && this.ui.selectedIds.length > 0)
                  .accessibilityText($r('app.string.management_media_delete'))
                  .onClick(() => this.openDeleteDialog())
                  Button({ type: ButtonType.Circle, stateEffect: true }) {
                    SymbolGlyph($r('sys.symbol.xmark'))
                      .fontSize(20)
                      .fontColor([this.primaryTextColor(this.useNativeSurface())])
                  }
                  .width(40)
                  .height(40)
                  .buttonStyle(ButtonStyleMode.TEXTUAL)
                  .enabled(!this.ui.deletingSelection)
                  .accessibilityText($r('app.string.management_cancel'))
                  .onClick(() => this.vm.cancelSelection())
                }
                .width('100%')
                .height(48)
              }
              Row() {
                Text(sortOptions[this.ui.selectedSortIndex].label)
                  .fontSize(14)
                  .fontColor(this.primaryTextColor((!(true))))
                  .height(46)
                  .onClick(() => {
                    this.vm.sortDialog.show()
                  })
                Image(this.ui.isDesc ? $r('app.media.jiangxu') : $r('app.media.shengxu'))
                  .width(16)
                  .height(16)
                  .fillColor(this.primaryTextColor((!(true))))
                  .margin({ left: 6 })
                  .onClick(() => {
                    if (this.vm.controller.getRefreshLayoutStatus() === RefreshLayoutStatus.Finish) {
                      this.ui.isDesc = !this.ui.isDesc
                      this.vm.refresh()
                    }
                  })
                if ((!(true))) {
                  Text(this.ui.totalCount)
                    .fontSize(12)
                    .fontColor($r('sys.color.font_secondary'))
                    .padding({
                      left: 6,
                      right: 6,
                      top: 4,
                      bottom: 4
                    })
                    .margin({
                      left: 12
                    })
                    .borderRadius(4)
                } else {
                  Text(this.ui.totalCount)
                    .fontSize(12)
                    .fontColor($r('app.color.text_1'))
                    .backgroundColor($r('app.color.bg_1'))
                    .padding({
                      left: 6,
                      right: 6,
                      top: 4,
                      bottom: 4
                    })
                    .margin({
                      left: 12
                    })
                    .borderRadius(4)
                }
                Blank().layoutWeight(1)
              }
              .width('100%')
              .height(46)

              GridView({
                lazyDataSource: this.vm.dataSource,
                onLazyDataSource: (dataSource) => {
                  this.vm.dataSource = dataSource
                },
                itemLayout: (item, index) => this.gridItemBuilder(item, index),
                emptyLayout: () => this.gridEmptyBuilder(),
                showEmptyLayout: this.ui.totalCount === '0',
                controller: this.vm.controller,
                onRefresh: () => {
                  this.vm.refresh()
                },
                onLoadMore: () => {
                  this.vm.loadMore()
                },
                gridAttribute: (attr) => {
                  attr.scrollBar = BarState.Off
                  switch (this.contentBreakpoint()) {
                    case BreakpointTypeEnum.XS:
                      attr.columnsTemplate = '1fr '.repeat(2)
                      break
                    case BreakpointTypeEnum.SM:
                      attr.columnsTemplate = '1fr '.repeat(3)
                      break
                    case BreakpointTypeEnum.MD:
                      attr.columnsTemplate = '1fr '.repeat(4)
                      break
                    case BreakpointTypeEnum.LG:
                      attr.columnsTemplate = '1fr '.repeat(6)
                      break
                    case BreakpointTypeEnum.XL:
                      attr.columnsTemplate = '1fr '.repeat(8)
                      break
                    case BreakpointTypeEnum.XXL:
                      attr.columnsTemplate = '1fr '.repeat(12)
                      break
                    default:
                      attr.columnsTemplate = '1fr '.repeat(3)
                      break
                  }
                  attr.columnsGap = HORIZONTAL_CARD_SPACE.getValue(this.contentBreakpoint())
                },
                lazyCachedCount: 10
              }).width('100%')
                .layoutWeight(1)
              if (this.ui.pageState == PageState.Loading) {
                SkeletonGridView()
              }
              if (this.ui.pageState == PageState.Error) {
                FailedView()
                  .onClick(() => {
                    this.vm.refresh()
                  })
              }
            }
            .width('100%')
            .layoutWeight(1)
            .backgroundColor((!(true)) ? Color.Transparent : this.pageBackground())
            .borderRadius({
              topLeft: (!(true)) ? 0 : 20,
              topRight: (!(true)) ? 0 : 20
            })
            .padding({
              left: HORIZONTAL_CARD_SPACE.getValue(this.contentBreakpoint()) * 2,
              right: HORIZONTAL_CARD_SPACE.getValue(this.contentBreakpoint()) * 2,
              bottom: this.vm.appUIState.safeBottom
            })
          }
          .width('100%')
          .height('100%')
          .backgroundColor((true) ? $r('app.color.bg_main') : Color.Transparent)
        }
        .attributeModifier(this.routeDestination.legacyModifier(this.routeOptions()))
        .gestureModifier(this.routeDestination.gestureModifier)
        .width('100%')
        .height('100%')
      }
    }
  }


}

@ComponentV2
export struct VideoCard {
  @Param @Require item: VideoItem
  @Param textAlign: TextAlign = TextAlign.Start
  @Param selectable: boolean = false
  @Param selected: boolean = false
  @Param selectionMode: boolean = false
  @Event onLongPress: () => void = () => {}
  @Event onSelect: () => void = () => {}
  @Event onOpen: () => void = () => {}
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!

  private posterImageOption(): ImageKnifeOption {
    const option = createMediaImageKnifeOption(this.item.imageRef, this.item.image)
    option.objectFit = ImageFit.Cover
    option.border = {
      radius: 6
    }
    option.placeholderSrc = this.item.kind === MediaRefKind.Season ||
      this.item.mediaRef?.kind === MediaRefKind.Season || this.item.type === 'Season' ?
      $r('app.media.placeholder_folder') : $r('app.media.placeholder_video')
    option.placeholderObjectFit = ImageFit.CENTER
    return option
  }

  private displayTags(): string[] {
    const visibleTags: string[] = []
    this.item.tags?.forEach((tag: string): void => {
      const normalized = tag.trim().toUpperCase()
      if (normalized.length === 0 || normalized.startsWith('OTH') || visibleTags.length >= 2) {
        return
      }
      if (!visibleTags.some((visibleTag: string): boolean => visibleTag.toUpperCase() === normalized)) {
        visibleTags.push(tag.trim())
      }
    })
    return visibleTags
  }

  build() {
    Column() {
      RelativeContainer() {
        ImageKnifeComponent({
          imageKnifeOption: this.posterImageOption()
        })
          .aspectRatio(2 / 3)
          .width('100%')
          .borderColor(AppThemeSurfaceResolver.mediaOutline(
            this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable))
          .borderWidth(AppThemeSurfaceResolver.outlineWidth(
            this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable, 0.5))
          .borderRadius(6)
          .backgroundColor(AppThemeSurfaceResolver.useNativeSurface(
            this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable) ?
            AppThemeSurfaceResolver.mediaPlaceholderBackground(
              this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable) : '#CBD2D3')
        // .backgroundBlurStyle(BlurStyle.BACKGROUND_THICK)
        if (this.item.score) {
          Text(this.item.score.toFixed(1) + '')
            .fontSize(11)
            .fontColor(Color.Yellow)
            .fontWeight(FontWeight.Medium)
            .padding({
              left: 6,
              right: 6,
              top: 4,
              bottom: 4
            })
            .alignRules({
              top: {
                anchor: '__container__',
                align: VerticalAlign.Top,
              },
              left: {
                anchor: '__container__',
                align: HorizontalAlign.Start
              }
            })
            .margin(4)
            .backgroundColor(0x60000000)
            // .backgroundBlurStyle(BlurStyle.BACKGROUND_THICK)
            .borderRadius(4)
        }
        if (this.displayTags().length > 0) {
          Row({ space: 4 }) {
            ForEach(this.displayTags(), (tag: string) => {
              if (tag === '4K') {
                Image($r('app.media.4K')).height(20).width(28).opacity(0.7).objectFit(ImageFit.Fill)
              } else {
                Text(tag)
                  .fontSize(9)
                  .fontColor(Color.White)
                  .fontWeight(FontWeight.Medium)
                  .padding({
                    top: 1,
                    bottom: 1,
                    left: 2,
                    right: 2
                  })
                  .borderWidth(1)
                  .borderColor(Color.White)
                  .borderRadius(3)
                  .margin(3)
              }
            })
          }
          .alignRules({
            bottom: {
              anchor: '__container__',
              align: VerticalAlign.Bottom,
            },
            right: {
              anchor: '__container__',
              align: HorizontalAlign.End
            }
          })
          .margin(4)
        }
        if (this.selected) {
          Column() {
          }
          .width('100%')
          .height('100%')
          .backgroundColor(0x38007DFF)
          .hitTestBehavior(HitTestMode.None)
          Stack() {
            SymbolGlyph($r('sys.symbol.checkmark'))
              .fontSize(18)
              .fontColor([Color.White])
          }
          .width(28)
          .height(28)
          .borderRadius(14)
          .backgroundColor($r('app.color.color_main'))
          .alignRules({
            top: {
              anchor: '__container__',
              align: VerticalAlign.Top
            },
            right: {
              anchor: '__container__',
              align: HorizontalAlign.End
            }
          })
          .margin(8)
          .hitTestBehavior(HitTestMode.None)
        }
      }.width('100%').aspectRatio(2 / 3)

      Text(this.item.name)
        .fontColor($r('app.color.text_primary'))
        .fontSize(14)
        .fontWeight(FontWeight.Medium)
        .width('100%')
        .maxLines(1)
        .ellipsisMode(EllipsisMode.END)
        .textOverflow({
          overflow: TextOverflow.Ellipsis
        })
        .margin({
          top: 8
        })
        .textAlign(this.textAlign)
      Text(this.item.subtitle)
        .fontColor(AppThemeSurfaceResolver.useNativeSurface(
          this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable) ?
          $r('sys.color.font_primary') : Color.Gray)
        .fontSize(12)
        .width('100%')
        .maxLines(1)
        .ellipsisMode(EllipsisMode.END)
        .textOverflow({
          overflow: TextOverflow.Ellipsis
        })
        .textAlign(this.textAlign)
        .margin({
          top: 4
        })
    }.alignItems(HorizontalAlign.Start).margin({
      bottom: 8
    })
    .onClick(() => {
      if (this.selectionMode) {
        this.onSelect()
      } else {
        this.onOpen()
      }
    })
    .priorityGesture(LongPressGesture().onAction(() => {
      if (this.selectable && !this.selectionMode) {
        this.onLongPress()
      }
    }))
  }
}
