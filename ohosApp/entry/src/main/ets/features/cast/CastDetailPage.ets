import { HMRouter, HMRouterMgr } from "@hadss/hmrouter";
import { ImageKnifeComponent } from "@ohos/imageknifepro";
import { RouterConsts } from "../../common/RouterConsts";
import { ActionBar } from "../../component/ActionBar";
import { LengthUnit } from "@kit.ArkUI";
import { CastDetailUIState } from "./CastDetailUIState";
import { CastDetailViewModel } from "./CastDetailViewModel";
import { TextExpandView } from "../../component/textexpand/TextExpand";
import { LastSpanAttribute, TextSectionAttribute } from "../../component/textexpand/Model";
import { BaseItemDto } from "sdk/src/main/ets/generated-client/models";
import { BaseItemCard } from "../../component/BaseItemCard";

@HMRouter({
  pageUrl: RouterConsts.CastDetailPage
})
@ComponentV2
export struct CastDetailPage {
  @Local ui: CastDetailUIState = new CastDetailUIState(this.getUIContext())
  @Local vm: CastDetailViewModel = new CastDetailViewModel(this.ui)

  aboutToAppear(): void {
    let id = HMRouterMgr.getCurrentParam() as string
    this.ui.personId = id
    this.vm.getCastDetail()
  }

  build() {
    Stack() {
      List(){
        ListItem(){
          ActionBar().visibility(Visibility.Hidden)
        }
        ListItem(){
          Row({ space: 16 }) {
            ImageKnifeComponent({
              imageKnifeOption: {
                loadSrc: this.ui.image,
                objectFit: ImageFit.Fill,
                border: {
                  width: 0.5,
                  radius: 12,
                  color: $r('app.color.border_1')
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
                TextExpandView({
                  text: this.ui.overview,
                  onMoreClick: () => {
                    this.ui.isShowOverviewSheet = !this.ui.isShowOverviewSheet;
                  },
                  lastSpanAttribute: new LastSpanAttribute(0, 2, ['更多', '收起'], 13, $r('app.color.color_main'))
                }).bindSheet($$this.ui.isShowOverviewSheet, this.overviewSheetBuilder(), {
                  height: 420,
                  preferType: SheetType.BOTTOM,
                  title: { title: '简介' },
                }).width('100%')
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
              .borderWidth(1)
              .borderColor($r('app.color.border'))
              .borderRadius(25)
              .backgroundColor($r('app.color.bg_2'))
              .onClick(() => {
                this.vm.changeFavorite()
              })
            }.alignItems(HorizontalAlign.Start)
            .height('100%')
            .layoutWeight(1)
          }.height(200)
          .padding({
            top: 10,
            left: 16,
            right: 16
          })
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
      ActionBar().backgroundColor($r('app.color.component_bg'))
    }.align(Alignment.Top)
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