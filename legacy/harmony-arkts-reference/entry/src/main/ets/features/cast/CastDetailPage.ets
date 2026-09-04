import { HMRouter, HMRouterMgr } from "@hadss/hmrouter";
import { ImageKnifeComponent, ImageKnifeOption } from "@ohos/imageknifepro";
import { RouterConsts } from "../../common/RouterConsts";
import { ActionBar } from "../../component/ActionBar";
import { AppRouteDestination, AppRouteDestinationOptions } from "../../component/AppRouteDestination";
import { LengthUnit } from "@kit.ArkUI";
import { CastDetailUIState } from "./CastDetailUIState";
import { CastDetailViewModel } from "./CastDetailViewModel";
import { TextExpandView } from "../../component/textexpand/TextExpand";
import { LastSpanAttribute, TextSectionAttribute } from "../../component/textexpand/Model";
import { BaseItemCard } from "../../component/BaseItemCard";
import { currentMediaCatalogProvider } from "../../media/MediaCatalogAccess";
import { createMediaImageKnifeOption } from "../../media/catalog/MediaImageKnifeAdapter";
import { MediaSummary } from "../../media/catalog/MediaCatalogModels";
import { MediaRef, MediaRefKind } from "../../media/MediaRef";
import { MediaRouteRefData, parseMediaRouteRefData, toMediaRouteRefData } from "../../media/navigation/MediaRouteRef";
import {
  AppThemeMaterialRole,
  AppThemeSurfaceResolver
} from '../../theme/AppThemeSurfaceResolver';
import {
  AppThemeOverlayPolicy,
  OverlayMaterialDecision,
  OverlaySurfaceRole
} from '../../theme/AppThemeOverlayPolicy';
import { OverviewSheetContent } from '../videodetail/components/DetailSheetContent';
import { HdsNavDestination, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'

@HMRouter({
  pageUrl: RouterConsts.CastDetailPage,
  useNavDst: true
})
@ComponentV2
export struct CastDetailPage {
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
  @Local ui: CastDetailUIState = new CastDetailUIState(this.getUIContext())
  @Local vm: CastDetailViewModel = new CastDetailViewModel(this.ui)
  @Local showActionBar: boolean = false
  private scroller: Scroller = new Scroller()

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable)
  }

  private secondarySurfaceBackground(): ResourceColor {
    return this.useNativeSurface() ? Color.Transparent : $r('app.color.bg_2')
  }

  private sheetMaterialDecision(): OverlayMaterialDecision {
    return AppThemeOverlayPolicy.resolve(
      OverlaySurfaceRole.AppFloating,
      this.vm.appUIState.themeStyle,
      this.vm.appUIState.systemMaterialAvailable,
      this.vm.appUIState.nativeThemeAvailable
    )
  }

  private primaryTextColor(): ResourceColor {
    return this.useNativeSurface() ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private personImageOption(): ImageKnifeOption {
    const option = createMediaImageKnifeOption(this.ui.imageRef, this.ui.image)
    option.objectFit = ImageFit.Cover
    option.border = {
      width: AppThemeSurfaceResolver.outlineWidth(
        this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable, 0.5),
      radius: 12,
      color: AppThemeSurfaceResolver.mediaOutline(
        this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable)
    }
    return option
  }

  private shouldShowSortName(): boolean {
    return this.ui.sortName.length > 0 &&
      (!this.useNativeSurface() || this.ui.sortName !== this.ui.name)
  }

  private openMedia(item: MediaSummary): void {
    HMRouterMgr.to(RouterConsts.VideoDetailPage).withParam({
      id: item.ref.id,
      ref: toMediaRouteRefData(item.ref),
      mediaName: item.ref.kind === MediaRefKind.Series ? '电视节目' : '电影',
      isTvShows: item.ref.kind === MediaRefKind.Series
    }).push()
  }




  aboutToAppear(): void {
    const raw = HMRouterMgr.getCurrentParam()
    let ref: MediaRef | undefined
    if (typeof raw === 'string') {
      try {
        const catalog = currentMediaCatalogProvider()
        ref = new MediaRef(catalog.session.provider, catalog.session.accountScope, raw, MediaRefKind.Person)
      } catch (_error) {
      }
    } else if (raw) {
      const param = raw as object
      ref = parseMediaRouteRefData(param['ref'] as MediaRouteRefData)
      if (!ref && typeof param['id'] === 'string') {
        try {
          const catalog = currentMediaCatalogProvider()
          ref = new MediaRef(catalog.session.provider, catalog.session.accountScope,
            param['id'] as string, MediaRefKind.Person)
        } catch (_error) {
        }
      }
    }
    if (ref) {
      this.vm.getCastDetail(ref)
    }
  }


  private routeOptions(): AppRouteDestinationOptions {
    return {
        title: this.showActionBar ? this.ui.name : '',
        scrollControllers: [this.scroller]
      }
  }

  // CustomBuilderAdapter: cast-overview-sheet
  @Builder
  private overviewSheetBuilder(blurFallback: boolean) {
    OverviewSheetContent({
      overview: this.ui.overview,
      textColor: this.primaryTextColor(),
      blurFallback
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
                List({ scroller: this.scroller }){
                  if ((false)) {
                    ListItem(){
                      ActionBar().visibility(Visibility.Hidden)
                    }
                  }
                  ListItem(){
                    Stack() {
                      Row({ space: 16 }) {
                      ImageKnifeComponent({
                        imageKnifeOption: this.personImageOption()
                      })
                        .height('100%')
                        .aspectRatio(291 / 436)
                      Column() {
                        Blank().layoutWeight(1)
                        Text(this.ui.name).fontColor(this.primaryTextColor()).fontWeight(FontWeight.Medium).fontSize(28)
                        Blank()
                        if (this.shouldShowSortName()) {
                          Text(this.ui.sortName).fontColor(this.primaryTextColor()).fontSize(14)
                        }
                        if (this.ui.overview.length > 0) {
                          Blank().height(10)
                          if (this.sheetMaterialDecision() === OverlayMaterialDecision.UseBlurFallback) {
                            Row() {
                              TextExpandView({
                                text: this.ui.overview,
                                onMoreClick: () => {
                                  this.ui.isShowOverviewSheet = !this.ui.isShowOverviewSheet;
                                },
                                lastSpanAttribute: new LastSpanAttribute(0, 2, ['更多', '收起'], 13, $r('app.color.color_main'))
                              })
                            }
                            .width('100%')
                            .bindSheet($$this.ui.isShowOverviewSheet, this.overviewSheetBuilder(true), {
                              height: 420,
                              preferType: SheetType.BOTTOM,
                              title: { title: '简介' },
                              backgroundColor: Color.Transparent
                            })
                          } else if (this.sheetMaterialDecision() === OverlayMaterialDecision.UseFloatingMaterial) {
                            Row() {
                              TextExpandView({
                                text: this.ui.overview,
                                onMoreClick: () => {
                                  this.ui.isShowOverviewSheet = !this.ui.isShowOverviewSheet;
                                },
                                lastSpanAttribute: new LastSpanAttribute(0, 2, ['更多', '收起'], 13, $r('app.color.color_main'))
                              })
                            }
                            .width('100%')
                            .bindSheet($$this.ui.isShowOverviewSheet, this.overviewSheetBuilder(false), {
                              height: 420,
                              preferType: SheetType.BOTTOM,
                              title: { title: '简介' },
                              backgroundColor: Color.Transparent,
                              systemMaterial: AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating)
                            })
                          } else if (this.sheetMaterialDecision() === OverlayMaterialDecision.DisableSystemMaterial) {
                            Row() {
                              TextExpandView({
                                text: this.ui.overview,
                                onMoreClick: () => {
                                  this.ui.isShowOverviewSheet = !this.ui.isShowOverviewSheet;
                                },
                                lastSpanAttribute: new LastSpanAttribute(0, 2, ['更多', '收起'], 13, $r('app.color.color_main'))
                              })
                            }
                            .width('100%')
                            .bindSheet($$this.ui.isShowOverviewSheet, this.overviewSheetBuilder(false), {
                              height: 420,
                              preferType: SheetType.BOTTOM,
                              title: { title: '简介' },
                              systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
                            })
                          } else {
                            Row() {
                              TextExpandView({
                                text: this.ui.overview,
                                onMoreClick: () => {
                                  this.ui.isShowOverviewSheet = !this.ui.isShowOverviewSheet;
                                },
                                lastSpanAttribute: new LastSpanAttribute(0, 2, ['更多', '收起'], 13, $r('app.color.color_main'))
                              })
                            }
                            .width('100%')
                            .bindSheet($$this.ui.isShowOverviewSheet, this.overviewSheetBuilder(false), {
                              height: 420,
                              preferType: SheetType.BOTTOM,
                              title: { title: '简介' }
                            })
                          }
                        }
                        Blank().height(10)
                        if (this.useNativeSurface()) {
                          Button({ type: ButtonType.Circle, stateEffect: false }) {
                            Image(this.ui.isFavorite ? $r('app.media.ic_like') : $r('app.media.ic_unlike'))
                              .width(22)
                              .height(22)
                              .renderMode(ImageRenderMode.Template)
                              .interpolation(ImageInterpolation.High)
                              .fillColor(this.ui.isFavorite ? $r('app.color.color_main') : $r('sys.color.icon_primary'))
                          }
                          .width(50)
                          .height(50)
                          .buttonStyle(ButtonStyleMode.TEXTUAL)
                          .attributeModifier(AppThemeSurfaceResolver.modifier(
                            AppThemeMaterialRole.InteractiveFloating, true,
                            this.vm.appUIState.systemMaterialAvailable))
                          .accessibilityText(this.ui.isFavorite ? '取消收藏' : '收藏')
                          .onClick(() => {
                            this.vm.changeFavorite()
                          })
                        } else {
                          Button() {
                            Image(this.ui.isFavorite ? $r('app.media.ic_like') : $r('app.media.ic_unlike'))
                              .fillColor($r('app.color.text_1'))
                              .width(22)
                              .height(22)
                          }
                          .width(50)
                          .height(50)
                          .borderWidth(AppThemeSurfaceResolver.outlineWidth(
                            this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable, 1))
                          .borderColor(AppThemeSurfaceResolver.contentOutline(
                            this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable))
                          .borderRadius(25)
                          .backgroundColor(this.secondarySurfaceBackground())
                          .onClick(() => {
                            this.vm.changeFavorite()
                          })
                        }
                      }.alignItems(HorizontalAlign.Start)
                      .height('100%')
                      .layoutWeight(1)
                      }.height(200)
                      .width('100%')
                      .constraintSize({ maxWidth: this.useNativeSurface() ? 720 : '100%' })
                      .padding({
                        top: this.useNativeSurface() ? 20 : 10,
                        left: 16,
                        right: 16,
                        bottom: this.useNativeSurface() ? 16 : 0
                      })
                    }
                    .width('100%')
                    .height(200)
                    .alignContent(Alignment.Top)
                  }
                  if(this.ui.movies.length > 0){
                    ListItem(){
                      Column() {
                        Text('电影')
                          .fontSize(14)
                          .fontWeight(FontWeight.Medium)
                          .fontColor(this.primaryTextColor())
                          .margin({ left: 16 })
                        List({ space: 8 }) {
                          ListItem().width(8)
                          ForEach(this.ui.movies, (item: MediaSummary) => {
                            ListItem() {
                              BaseItemCard({
                                item: item
                              })
                            }.onClick(() => this.openMedia(item))
                          })
                          ListItem().width(8)
                        }
                        .width('100%')
                        .height(194)
                        .margin({ top: 12 })
                        .listDirection(Axis.Horizontal)
                        .scrollBar(BarState.Off)
                      }
                      .width('100%')
                      .margin({
                        top: 20
                      })
                      .alignItems(HorizontalAlign.Start)
                    }
                  }
                  if (this.ui.series.length > 0) {
                    ListItem(){
                      Column() {
                        Text('电视剧')
                          .fontSize(14)
                          .fontWeight(FontWeight.Medium)
                          .fontColor(this.primaryTextColor())
                          .margin({ left: 16 })
                        List({ space: 8 }) {
                          ListItem().width(8)
                          ForEach(this.ui.series, (item: MediaSummary) => {
                            ListItem() {
                              BaseItemCard({
                                item: item
                              })
                            }.onClick(() => this.openMedia(item))
                          })
                          ListItem().width(8)
                        }
                        .width('100%')
                        .height(194)
                        .margin({ top: 12 })
                        .listDirection(Axis.Horizontal)
                        .scrollBar(BarState.Off)
                      }
                      .width('100%')
                      .margin({
                        top: 20
                      })
                      .alignItems(HorizontalAlign.Start)
                    }
                  }
                  if(this.ui.episodes.length > 0){
                    ListItem() {
                      Column() {
                        Text('剧集')
                          .fontSize(14)
                          .fontWeight(FontWeight.Medium)
                          .fontColor(this.primaryTextColor())
                          .margin({ left: 16 })
                        List({ space: 8 }) {
                          ListItem().width(8)
                          ForEach(this.ui.episodes, (item: MediaSummary) => {
                            ListItem() {
                              BaseItemCard({
                                item: item
                              })
                            }.onClick(() => this.openMedia(item))
                          })
                          ListItem().width(8)
                        }
                        .width('100%')
                        .height(194)
                        .margin({ top: 12 })
                        .listDirection(Axis.Horizontal)
                        .scrollBar(BarState.Off)
                      }
                      .width('100%')
                      .margin({
                        top: 20
                      })
                      .alignItems(HorizontalAlign.Start)
                    }
                  }
                }.width('100%')
                .height('100%')
                .listDirection(Axis.Vertical)
                .onDidScroll(() => {
                  this.showActionBar = this.scroller.currentOffset().yOffset > 0
                })
                if ((false)) {
                  ActionBar()
                    .backgroundColor($r('app.color.component_bg'))
                }
              }
              .align(Alignment.Top)
              .backgroundColor(AppThemeSurfaceResolver.routeBackground(
                this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable))
            } else {
              Stack() {
                List({ scroller: this.scroller }){
                  if ((true)) {
                    ListItem(){
                      ActionBar().visibility(Visibility.Hidden)
                    }
                  }
                  ListItem(){
                    Stack() {
                      Row({ space: 16 }) {
                      ImageKnifeComponent({
                        imageKnifeOption: this.personImageOption()
                      })
                        .height('100%')
                        .aspectRatio(291 / 436)
                      Column() {
                        Blank().layoutWeight(1)
                        Text(this.ui.name).fontColor(this.primaryTextColor()).fontWeight(FontWeight.Medium).fontSize(28)
                        Blank()
                        if (this.shouldShowSortName()) {
                          Text(this.ui.sortName).fontColor(this.primaryTextColor()).fontSize(14)
                        }
                        if (this.ui.overview.length > 0) {
                          Blank().height(10)
                          if (this.sheetMaterialDecision() === OverlayMaterialDecision.UseBlurFallback) {
                            Row() {
                              TextExpandView({
                                text: this.ui.overview,
                                onMoreClick: () => {
                                  this.ui.isShowOverviewSheet = !this.ui.isShowOverviewSheet;
                                },
                                lastSpanAttribute: new LastSpanAttribute(0, 2, ['更多', '收起'], 13, $r('app.color.color_main'))
                              })
                            }
                            .width('100%')
                            .bindSheet($$this.ui.isShowOverviewSheet, this.overviewSheetBuilder(true), {
                              height: 420,
                              preferType: SheetType.BOTTOM,
                              title: { title: '简介' },
                              backgroundColor: Color.Transparent
                            })
                          } else if (this.sheetMaterialDecision() === OverlayMaterialDecision.UseFloatingMaterial) {
                            Row() {
                              TextExpandView({
                                text: this.ui.overview,
                                onMoreClick: () => {
                                  this.ui.isShowOverviewSheet = !this.ui.isShowOverviewSheet;
                                },
                                lastSpanAttribute: new LastSpanAttribute(0, 2, ['更多', '收起'], 13, $r('app.color.color_main'))
                              })
                            }
                            .width('100%')
                            .bindSheet($$this.ui.isShowOverviewSheet, this.overviewSheetBuilder(false), {
                              height: 420,
                              preferType: SheetType.BOTTOM,
                              title: { title: '简介' },
                              backgroundColor: Color.Transparent,
                              systemMaterial: AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating)
                            })
                          } else if (this.sheetMaterialDecision() === OverlayMaterialDecision.DisableSystemMaterial) {
                            Row() {
                              TextExpandView({
                                text: this.ui.overview,
                                onMoreClick: () => {
                                  this.ui.isShowOverviewSheet = !this.ui.isShowOverviewSheet;
                                },
                                lastSpanAttribute: new LastSpanAttribute(0, 2, ['更多', '收起'], 13, $r('app.color.color_main'))
                              })
                            }
                            .width('100%')
                            .bindSheet($$this.ui.isShowOverviewSheet, this.overviewSheetBuilder(false), {
                              height: 420,
                              preferType: SheetType.BOTTOM,
                              title: { title: '简介' },
                              systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
                            })
                          } else {
                            Row() {
                              TextExpandView({
                                text: this.ui.overview,
                                onMoreClick: () => {
                                  this.ui.isShowOverviewSheet = !this.ui.isShowOverviewSheet;
                                },
                                lastSpanAttribute: new LastSpanAttribute(0, 2, ['更多', '收起'], 13, $r('app.color.color_main'))
                              })
                            }
                            .width('100%')
                            .bindSheet($$this.ui.isShowOverviewSheet, this.overviewSheetBuilder(false), {
                              height: 420,
                              preferType: SheetType.BOTTOM,
                              title: { title: '简介' }
                            })
                          }
                        }
                        Blank().height(10)
                        if (this.useNativeSurface()) {
                          Button({ type: ButtonType.Circle, stateEffect: false }) {
                            Image(this.ui.isFavorite ? $r('app.media.ic_like') : $r('app.media.ic_unlike'))
                              .width(22)
                              .height(22)
                              .renderMode(ImageRenderMode.Template)
                              .interpolation(ImageInterpolation.High)
                              .fillColor(this.ui.isFavorite ? $r('app.color.color_main') : $r('sys.color.icon_primary'))
                          }
                          .width(50)
                          .height(50)
                          .buttonStyle(ButtonStyleMode.TEXTUAL)
                          .attributeModifier(AppThemeSurfaceResolver.modifier(
                            AppThemeMaterialRole.InteractiveFloating, true,
                            this.vm.appUIState.systemMaterialAvailable))
                          .accessibilityText(this.ui.isFavorite ? '取消收藏' : '收藏')
                          .onClick(() => {
                            this.vm.changeFavorite()
                          })
                        } else {
                          Button() {
                            Image(this.ui.isFavorite ? $r('app.media.ic_like') : $r('app.media.ic_unlike'))
                              .fillColor($r('app.color.text_1'))
                              .width(22)
                              .height(22)
                          }
                          .width(50)
                          .height(50)
                          .borderWidth(AppThemeSurfaceResolver.outlineWidth(
                            this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable, 1))
                          .borderColor(AppThemeSurfaceResolver.contentOutline(
                            this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable))
                          .borderRadius(25)
                          .backgroundColor(this.secondarySurfaceBackground())
                          .onClick(() => {
                            this.vm.changeFavorite()
                          })
                        }
                      }.alignItems(HorizontalAlign.Start)
                      .height('100%')
                      .layoutWeight(1)
                      }.height(200)
                      .width('100%')
                      .constraintSize({ maxWidth: this.useNativeSurface() ? 720 : '100%' })
                      .padding({
                        top: this.useNativeSurface() ? 20 : 10,
                        left: 16,
                        right: 16,
                        bottom: this.useNativeSurface() ? 16 : 0
                      })
                    }
                    .width('100%')
                    .height(200)
                    .alignContent(Alignment.Top)
                  }
                  if(this.ui.movies.length > 0){
                    ListItem(){
                      Column() {
                        Text('电影')
                          .fontSize(14)
                          .fontWeight(FontWeight.Medium)
                          .fontColor(this.primaryTextColor())
                          .margin({ left: 16 })
                        List({ space: 8 }) {
                          ListItem().width(8)
                          ForEach(this.ui.movies, (item: MediaSummary) => {
                            ListItem() {
                              BaseItemCard({
                                item: item
                              })
                            }.onClick(() => this.openMedia(item))
                          })
                          ListItem().width(8)
                        }
                        .width('100%')
                        .height(194)
                        .margin({ top: 12 })
                        .listDirection(Axis.Horizontal)
                        .scrollBar(BarState.Off)
                      }
                      .width('100%')
                      .margin({
                        top: 20
                      })
                      .alignItems(HorizontalAlign.Start)
                    }
                  }
                  if (this.ui.series.length > 0) {
                    ListItem(){
                      Column() {
                        Text('电视剧')
                          .fontSize(14)
                          .fontWeight(FontWeight.Medium)
                          .fontColor(this.primaryTextColor())
                          .margin({ left: 16 })
                        List({ space: 8 }) {
                          ListItem().width(8)
                          ForEach(this.ui.series, (item: MediaSummary) => {
                            ListItem() {
                              BaseItemCard({
                                item: item
                              })
                            }.onClick(() => this.openMedia(item))
                          })
                          ListItem().width(8)
                        }
                        .width('100%')
                        .height(194)
                        .margin({ top: 12 })
                        .listDirection(Axis.Horizontal)
                        .scrollBar(BarState.Off)
                      }
                      .width('100%')
                      .margin({
                        top: 20
                      })
                      .alignItems(HorizontalAlign.Start)
                    }
                  }
                  if(this.ui.episodes.length > 0){
                    ListItem() {
                      Column() {
                        Text('剧集')
                          .fontSize(14)
                          .fontWeight(FontWeight.Medium)
                          .fontColor(this.primaryTextColor())
                          .margin({ left: 16 })
                        List({ space: 8 }) {
                          ListItem().width(8)
                          ForEach(this.ui.episodes, (item: MediaSummary) => {
                            ListItem() {
                              BaseItemCard({
                                item: item
                              })
                            }.onClick(() => this.openMedia(item))
                          })
                          ListItem().width(8)
                        }
                        .width('100%')
                        .height(194)
                        .margin({ top: 12 })
                        .listDirection(Axis.Horizontal)
                        .scrollBar(BarState.Off)
                      }
                      .width('100%')
                      .margin({
                        top: 20
                      })
                      .alignItems(HorizontalAlign.Start)
                    }
                  }
                }.width('100%')
                .height('100%')
                .listDirection(Axis.Vertical)
                .onDidScroll(() => {
                  this.showActionBar = this.scroller.currentOffset().yOffset > 0
                })
                if ((true)) {
                  ActionBar()
                    .backgroundColor($r('app.color.component_bg'))
                }
              }
              .align(Alignment.Top)
              .backgroundColor(AppThemeSurfaceResolver.routeBackground(
                this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable))
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
          List({ scroller: this.scroller }){
            if ((true)) {
              ListItem(){
                ActionBar().visibility(Visibility.Hidden)
              }
            }
            ListItem(){
              Stack() {
                Row({ space: 16 }) {
                ImageKnifeComponent({
                  imageKnifeOption: this.personImageOption()
                })
                  .height('100%')
                  .aspectRatio(291 / 436)
                Column() {
                  Blank().layoutWeight(1)
                  Text(this.ui.name).fontColor(this.primaryTextColor()).fontWeight(FontWeight.Medium).fontSize(28)
                  Blank()
                  if (this.shouldShowSortName()) {
                    Text(this.ui.sortName).fontColor(this.primaryTextColor()).fontSize(14)
                  }
                  if (this.ui.overview.length > 0) {
                    Blank().height(10)
                    if (this.sheetMaterialDecision() === OverlayMaterialDecision.UseBlurFallback) {
                      Row() {
                        TextExpandView({
                          text: this.ui.overview,
                          onMoreClick: () => {
                            this.ui.isShowOverviewSheet = !this.ui.isShowOverviewSheet;
                          },
                          lastSpanAttribute: new LastSpanAttribute(0, 2, ['更多', '收起'], 13, $r('app.color.color_main'))
                        })
                      }
                      .width('100%')
                      .bindSheet($$this.ui.isShowOverviewSheet, this.overviewSheetBuilder(true), {
                        height: 420,
                        preferType: SheetType.BOTTOM,
                        title: { title: '简介' },
                        backgroundColor: Color.Transparent
                      })
                    } else if (this.sheetMaterialDecision() === OverlayMaterialDecision.UseFloatingMaterial) {
                      Row() {
                        TextExpandView({
                          text: this.ui.overview,
                          onMoreClick: () => {
                            this.ui.isShowOverviewSheet = !this.ui.isShowOverviewSheet;
                          },
                          lastSpanAttribute: new LastSpanAttribute(0, 2, ['更多', '收起'], 13, $r('app.color.color_main'))
                        })
                      }
                      .width('100%')
                      .bindSheet($$this.ui.isShowOverviewSheet, this.overviewSheetBuilder(false), {
                        height: 420,
                        preferType: SheetType.BOTTOM,
                        title: { title: '简介' },
                        backgroundColor: Color.Transparent,
                        systemMaterial: AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating)
                      })
                    } else if (this.sheetMaterialDecision() === OverlayMaterialDecision.DisableSystemMaterial) {
                      Row() {
                        TextExpandView({
                          text: this.ui.overview,
                          onMoreClick: () => {
                            this.ui.isShowOverviewSheet = !this.ui.isShowOverviewSheet;
                          },
                          lastSpanAttribute: new LastSpanAttribute(0, 2, ['更多', '收起'], 13, $r('app.color.color_main'))
                        })
                      }
                      .width('100%')
                      .bindSheet($$this.ui.isShowOverviewSheet, this.overviewSheetBuilder(false), {
                        height: 420,
                        preferType: SheetType.BOTTOM,
                        title: { title: '简介' },
                        systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
                      })
                    } else {
                      Row() {
                        TextExpandView({
                          text: this.ui.overview,
                          onMoreClick: () => {
                            this.ui.isShowOverviewSheet = !this.ui.isShowOverviewSheet;
                          },
                          lastSpanAttribute: new LastSpanAttribute(0, 2, ['更多', '收起'], 13, $r('app.color.color_main'))
                        })
                      }
                      .width('100%')
                      .bindSheet($$this.ui.isShowOverviewSheet, this.overviewSheetBuilder(false), {
                        height: 420,
                        preferType: SheetType.BOTTOM,
                        title: { title: '简介' }
                      })
                    }
                  }
                  Blank().height(10)
                  if (this.useNativeSurface()) {
                    Button({ type: ButtonType.Circle, stateEffect: false }) {
                      Image(this.ui.isFavorite ? $r('app.media.ic_like') : $r('app.media.ic_unlike'))
                        .width(22)
                        .height(22)
                        .renderMode(ImageRenderMode.Template)
                        .interpolation(ImageInterpolation.High)
                        .fillColor(this.ui.isFavorite ? $r('app.color.color_main') : $r('sys.color.icon_primary'))
                    }
                    .width(50)
                    .height(50)
                    .buttonStyle(ButtonStyleMode.TEXTUAL)
                    .attributeModifier(AppThemeSurfaceResolver.modifier(
                      AppThemeMaterialRole.InteractiveFloating, true,
                      this.vm.appUIState.systemMaterialAvailable))
                    .accessibilityText(this.ui.isFavorite ? '取消收藏' : '收藏')
                    .onClick(() => {
                      this.vm.changeFavorite()
                    })
                  } else {
                    Button() {
                      Image(this.ui.isFavorite ? $r('app.media.ic_like') : $r('app.media.ic_unlike'))
                        .fillColor($r('app.color.text_1'))
                        .width(22)
                        .height(22)
                    }
                    .width(50)
                    .height(50)
                    .borderWidth(AppThemeSurfaceResolver.outlineWidth(
                      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable, 1))
                    .borderColor(AppThemeSurfaceResolver.contentOutline(
                      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable))
                    .borderRadius(25)
                    .backgroundColor(this.secondarySurfaceBackground())
                    .onClick(() => {
                      this.vm.changeFavorite()
                    })
                  }
                }.alignItems(HorizontalAlign.Start)
                .height('100%')
                .layoutWeight(1)
                }.height(200)
                .width('100%')
                .constraintSize({ maxWidth: this.useNativeSurface() ? 720 : '100%' })
                .padding({
                  top: this.useNativeSurface() ? 20 : 10,
                  left: 16,
                  right: 16,
                  bottom: this.useNativeSurface() ? 16 : 0
                })
              }
              .width('100%')
              .height(200)
              .alignContent(Alignment.Top)
            }
            if(this.ui.movies.length > 0){
              ListItem(){
                Column() {
                  Text('电影')
                    .fontSize(14)
                    .fontWeight(FontWeight.Medium)
                    .fontColor(this.primaryTextColor())
                    .margin({ left: 16 })
                  List({ space: 8 }) {
                    ListItem().width(8)
                    ForEach(this.ui.movies, (item: MediaSummary) => {
                      ListItem() {
                        BaseItemCard({
                          item: item
                        })
                      }.onClick(() => this.openMedia(item))
                    })
                    ListItem().width(8)
                  }
                  .width('100%')
                  .height(194)
                  .margin({ top: 12 })
                  .listDirection(Axis.Horizontal)
                  .scrollBar(BarState.Off)
                }
                .width('100%')
                .margin({
                  top: 20
                })
                .alignItems(HorizontalAlign.Start)
              }
            }
            if (this.ui.series.length > 0) {
              ListItem(){
                Column() {
                  Text('电视剧')
                    .fontSize(14)
                    .fontWeight(FontWeight.Medium)
                    .fontColor(this.primaryTextColor())
                    .margin({ left: 16 })
                  List({ space: 8 }) {
                    ListItem().width(8)
                    ForEach(this.ui.series, (item: MediaSummary) => {
                      ListItem() {
                        BaseItemCard({
                          item: item
                        })
                      }.onClick(() => this.openMedia(item))
                    })
                    ListItem().width(8)
                  }
                  .width('100%')
                  .height(194)
                  .margin({ top: 12 })
                  .listDirection(Axis.Horizontal)
                  .scrollBar(BarState.Off)
                }
                .width('100%')
                .margin({
                  top: 20
                })
                .alignItems(HorizontalAlign.Start)
              }
            }
            if(this.ui.episodes.length > 0){
              ListItem() {
                Column() {
                  Text('剧集')
                    .fontSize(14)
                    .fontWeight(FontWeight.Medium)
                    .fontColor(this.primaryTextColor())
                    .margin({ left: 16 })
                  List({ space: 8 }) {
                    ListItem().width(8)
                    ForEach(this.ui.episodes, (item: MediaSummary) => {
                      ListItem() {
                        BaseItemCard({
                          item: item
                        })
                      }.onClick(() => this.openMedia(item))
                    })
                    ListItem().width(8)
                  }
                  .width('100%')
                  .height(194)
                  .margin({ top: 12 })
                  .listDirection(Axis.Horizontal)
                  .scrollBar(BarState.Off)
                }
                .width('100%')
                .margin({
                  top: 20
                })
                .alignItems(HorizontalAlign.Start)
              }
            }
          }.width('100%')
          .height('100%')
          .listDirection(Axis.Vertical)
          .onDidScroll(() => {
            this.showActionBar = this.scroller.currentOffset().yOffset > 0
          })
          if ((true)) {
            ActionBar()
              .backgroundColor($r('app.color.component_bg'))
          }
        }
        .align(Alignment.Top)
        .backgroundColor(AppThemeSurfaceResolver.routeBackground(
          this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable))
      }
      .attributeModifier(this.routeDestination.legacyModifier(this.routeOptions()))
      .gestureModifier(this.routeDestination.gestureModifier)
      .width('100%')
      .height('100%')
    }
  }

}
