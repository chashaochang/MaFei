import { HMRouter, HMRouterMgr } from "@hadss/hmrouter";
import { ImageKnifeComponent } from "@ohos/imageknifepro";
import { RouterConsts } from "../../common/RouterConsts";
import { ActionBar } from "../../component/ActionBar";
import { AppRouteDestination } from "../../component/AppRouteDestination";
import { LengthUnit } from "@kit.ArkUI";
import { CastDetailUIState } from "./CastDetailUIState";
import { CastDetailViewModel } from "./CastDetailViewModel";
import { TextExpandView } from "../../component/textexpand/TextExpand";
import { LastSpanAttribute, TextSectionAttribute } from "../../component/textexpand/Model";
import { BaseItemDto } from "sdk/src/main/ets/generated-client/models";
import { BaseItemCard } from "../../component/BaseItemCard";
import { deviceInfo } from '@kit.BasicServicesKit';
import {
  AppThemeMaterialRole,
  AppThemeSurfaceResolver
} from '../../theme/AppThemeSurfaceResolver';
import {
  AppThemeOverlayPolicy,
  OverlayMaterialDecision,
  OverlaySurfaceRole
} from '../../theme/AppThemeOverlayPolicy';
import { AppThemeTokens } from '../../theme/AppThemeTokens';

@HMRouter({
  pageUrl: RouterConsts.CastDetailPage,
  useNavDst: true
})
@ComponentV2
export struct CastDetailPage {
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
      deviceInfo.sdkApiVersion >= 26,
      this.vm.appUIState.nativeThemeAvailable
    )
  }

  @Builder
  private nativeProfileGroupSurface() {
    Column()
      .width('100%')
      .height('100%')
      .constraintSize({ maxWidth: 720 })
      .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
      .clip(true)
      .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  private feiniuActionBar() {
    ActionBar()
      .backgroundColor($r('app.color.component_bg'))
  }

  @Builder
  private overviewTriggerContent() {
    TextExpandView({
      text: this.ui.overview,
      onMoreClick: () => {
        this.ui.isShowOverviewSheet = !this.ui.isShowOverviewSheet;
      },
      lastSpanAttribute: new LastSpanAttribute(0, 2, ['更多', '收起'], 13, $r('app.color.color_main'))
    })
  }

  @Builder
  private overviewSheetTrigger() {
    if (deviceInfo.sdkApiVersion < 26) {
      Row() {
        this.overviewTriggerContent()
      }
      .width('100%')
      .bindSheet($$this.ui.isShowOverviewSheet, this.overviewSheetBuilder(), {
        height: 420,
        preferType: SheetType.BOTTOM,
        title: { title: '简介' },
      })
    } else if (this.sheetMaterialDecision() === OverlayMaterialDecision.UseFloatingMaterial) {
      Row() {
        this.overviewTriggerContent()
      }
      .width('100%')
      .bindSheet($$this.ui.isShowOverviewSheet, this.overviewSheetBuilder(), {
        height: 420,
        preferType: SheetType.BOTTOM,
        title: { title: '简介' },
        backgroundColor: Color.Transparent,
        systemMaterial: AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating)
      })
    } else {
      Row() {
        this.overviewTriggerContent()
      }
      .width('100%')
      .bindSheet($$this.ui.isShowOverviewSheet, this.overviewSheetBuilder(), {
        height: 420,
        preferType: SheetType.BOTTOM,
        title: { title: '简介' },
        systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
      })
    }
  }

  aboutToAppear(): void {
    let id = HMRouterMgr.getCurrentParam() as string
    this.ui.personId = id
    this.vm.getCastDetail()
  }

  @Builder
  private pageContent(showLegacyActionBar: boolean) {
    Stack() {
      List({ scroller: this.scroller }){
        if (showLegacyActionBar) {
          ListItem(){
            ActionBar().visibility(Visibility.Hidden)
          }
        }
        ListItem(){
          Stack() {
            if (this.useNativeSurface()) {
              this.nativeProfileGroupSurface()
            }
            Row({ space: 16 }) {
            ImageKnifeComponent({
              imageKnifeOption: {
                loadSrc: this.ui.image,
              objectFit: ImageFit.Fill,
              border: {
                width: AppThemeSurfaceResolver.outlineWidth(
                  this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable, 0.5),
                radius: 12,
                  color: AppThemeSurfaceResolver.mediaOutline(
                    this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable)
                }
              }
            })
              .height('100%')
              .aspectRatio(291 / 436)
            Column() {
              Blank().layoutWeight(1)
              Text(this.ui.name).fontColor($r('app.color.text_primary')).fontWeight(FontWeight.Medium).fontSize(28)
              Blank()
              Text(this.ui.sortName).fontColor($r('app.color.text_primary')).fontSize(14)
              if (this.ui.overview.length > 0) {
                Blank().height(10)
                this.overviewSheetTrigger()
              }
              Blank().height(10)
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
            }.alignItems(HorizontalAlign.Start)
            .height('100%')
            .layoutWeight(1)
            }.height(200)
            .width('100%')
            .constraintSize({ maxWidth: this.useNativeSurface() ? 720 : '100%' })
            .padding({
              top: 10,
              left: 16,
              right: 16
            })
          }
          .width('100%')
          .height(200)
          .alignContent(Alignment.Top)
        }
        if(this.ui.movieCount > 0){
          ListItem(){
            Column() {
              Text('电影')
                .fontSize(14)
                .fontWeight(FontWeight.Medium)
                .fontColor($r('app.color.text_primary'))
                .margin({ left: 16 })
              List({ space: 8 }) {
                ListItem().width(8)
                ForEach(this.ui.movies, (item: BaseItemDto) => {
                  ListItem() {
                    BaseItemCard({
                      item: item
                    })
                  }.onClick(() => {

                  })
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
        if (this.ui.seriesCount > 0) {
          ListItem(){
            Column() {
              Text('电视剧')
                .fontSize(14)
                .fontWeight(FontWeight.Medium)
                .fontColor($r('app.color.text_primary'))
                .margin({ left: 16 })
              List({ space: 8 }) {
                ListItem().width(8)
                ForEach(this.ui.series, (item: BaseItemDto) => {
                  ListItem() {
                    BaseItemCard({
                      item: item
                    })
                  }.onClick(() => {

                  })
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
        if(this.ui.episodeCount > 0){
          ListItem() {
            Column() {
              Text('剧集')
                .fontSize(14)
                .fontWeight(FontWeight.Medium)
                .fontColor($r('app.color.text_primary'))
                .margin({ left: 16 })
              List({ space: 8 }) {
                ListItem().width(8)
                ForEach(this.ui.episodes, (item: BaseItemDto) => {
                  ListItem() {
                    BaseItemCard({
                      item: item
                    })
                  }.onClick(() => {

                  })
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
      if (showLegacyActionBar) {
        this.feiniuActionBar()
      }
    }
    .align(Alignment.Top)
    .backgroundColor(AppThemeSurfaceResolver.routeBackground(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable))
  }

  build() {
    AppRouteDestination({
      title: this.showActionBar ? this.ui.name : '',
      contentBuilder: () => {
        this.pageContent(false)
      },
      legacyContentBuilder: () => {
        this.pageContent(true)
      }
    })
  }

  @Builder
  overviewSheetBuilder() {
    Scroll() {
      Text(this.ui.overview)
        .width('100%')
        .fontColor($r('app.color.text_1'))
        .fontSize(14)
        .textAlign(TextAlign.Start)
        .lineSpacing({
          value: 6,
          unit: LengthUnit.VP
        })
    }
    .scrollable(ScrollDirection.Vertical)
    .padding({
      left: 16,
      right: 16,
      bottom: 10
    })
    .width('100%')
    .height('100%')
    .align(Alignment.Top)
    .nestedScroll({
      scrollForward: NestedScrollMode.PARENT_FIRST,
      scrollBackward: NestedScrollMode.SELF_FIRST,
    })
  }
}
