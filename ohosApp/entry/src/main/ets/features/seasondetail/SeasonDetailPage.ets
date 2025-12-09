import { HMRouter, HMRouterMgr } from '@hadss/hmrouter';
import { ImageKnifeComponentV2 } from '@ohos/imageknife';
import { ActionBar } from '../../component/ActionBar';
import { SeasonDetailUIState } from './SeasonDetailUIState';
import { SeasonDetailViewModel } from './SeasonDetailViewModel';
import { LengthUnit } from '@ohos.arkui.node';
import { VideoItem } from '../home/hometab/HomeUIState';
import { TextExpandView } from '../../component/textexpand/TextExpand';
import { LastSpanAttribute } from '../../component/textexpand/Model';
import { RouterConsts } from '../../common/RouterConsts';
import { CalculateHeightParam } from '../videodetail/VideoDetailViewModel';
import { CastView } from '../../component/CastView';
import { SkeletonDetailView } from '../../component/skeleton/SkeletonDetailView';

@HMRouter({
  pageUrl: RouterConsts.SeasonDetailPage
})
@ComponentV2
export struct SeasonDetailPage {
  @Local ui: SeasonDetailUIState = new SeasonDetailUIState(this.getUIContext())
  vm: SeasonDetailViewModel = new SeasonDetailViewModel(this.ui)
  @Local isShowSheet: boolean = false;

  aboutToAppear(): void {
    let param = HMRouterMgr.getCurrentParam() as object
    if (param) {
      this.ui.id = param['id'] as string
      this.ui.parentId = param['parentId'] as string
      this.ui.mediaName = param['mediaName'] as string
      this.ui.preInfo = param['info'] as string
      let seasons = param['seasons'] as VideoItem[]
      this.ui.seasons = seasons
      this.vm.getSeasonDetail()
    }
  }

  build() {
    Stack() {
      if (this.ui.firstLoading) {
        SkeletonDetailView()
      } else {
        Scroll(this.ui.scroller) {
          Column() {
            Stack() {
              //背景图
              ImageKnifeComponentV2({
                imageKnifeOption: {
                  loadSrc: this.ui.backgroundImage,
                  objectFit: ImageFit.Cover,
                }
              }).width('100%')
                .height(this.ui.imageHeight - 100)
                .blur(this.ui.blur)
              //渐变遮罩
              Stack().width('100%')
                .height(this.ui.imageHeight - 100)
                .linearGradient({
                  angle: 0, // 0度在CSS中是从下到上，ArkTS中180度对应
                  colors: [
                    [0x121618, 0.0], // 起始位置：完全不透明
                    ['rgba(26, 30, 35, 0.6)', 0.3], // 30%位置：60%不透明
                    ['rgba(26, 30, 35, 0.2)', 0.4], // 40%位置：20%不透明
                    ['rgba(26, 30, 35, 0.1)', 1]// 100%位置：10%不透明
                  ]
                })

              Column() {
                Blank()
                Row({ space: 16 }) {
                  ImageKnifeComponentV2({
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
                    Blank().height(60)
                    Text(this.ui.seriesName).fontColor(Color.White).fontWeight(FontWeight.Medium).fontSize(28)
                    Blank()
                    Text(this.ui.seasonName).fontColor($r('app.color.text_primary')).fontSize(14)
                    Blank().height(10)
                    Text() {
                      //Span(this.ui.score + '分  / ').fontColor($r('app.color.text_score'))
                      Span(this.ui.info)
                    }
                    .fontSize(12)
                    .fontColor($r('app.color.text_1'))
                    .lineSpacing({ value: 6, unit: LengthUnit.VP })
                    .width('100%')
                    .textAlign(TextAlign.Start)
                  }.alignItems(HorizontalAlign.Start)
                  .height('100%')
                  .layoutWeight(1)
                }.height(200)
                .padding({
                  left: 16,
                  right: 16
                })
              }.width('100%')
              .height('100%')

            }.width('100%')
            .height(this.ui.imageHeight)
            .alignContent(Alignment.Top)

            Column() {
              Row({ space: 8 }) {
                Button() {
                  Row() {
                    Image($r('app.media.ic_play_round')).width(20).height(20).margin({ right: 8 })
                    Text(this.ui.episodeName != '' ? this.ui.episodeName : '播放')
                      .fontSize(16)
                      .fontColor(Color.White)
                      .fontWeight(FontWeight.Medium)
                  }
                }
                .layoutWeight(1)
                .margin({
                  right: 12
                })
                .borderRadius(25)
                .height(50)
                .onClick(() => {
                  this.vm.playFirst()
                })

                Button() {
                  Image($r('app.media.ic_check'))
                    .fillColor(this.ui.played ? $r('app.color.color_main') : $r('app.color.text_1'))
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
                  this.vm.changePlayedState()
                })
              }.width('100%')
              .padding({ left: 16, right: 16 })

              if (this.ui.overview.length > 0) {
                Blank().height(20)
                TextExpandView({
                  text: this.ui.overview,
                  onMoreClick: () => {
                    this.isShowSheet = !this.isShowSheet;
                  },
                  lastSpanAttribute: new LastSpanAttribute(0, 2, ['更多', '收起'], 14, $r('app.color.color_main'))
                }).padding({ left: 16, right: 16 })
                  .bindSheet($$this.isShowSheet, this.SheetBuilder(), {
                    height: 420,
                    preferType: SheetType.BOTTOM,
                    title: { title: '简介' },
                  })
              }
              //Text(this.ui.overview).width('100%').fontSize(14).fontColor($r('app.color.text_1')).textAlign(TextAlign.Start)

              Blank().height(20)
              if (this.ui.seasons.length > 1) {
                List() {
                  ForEach(this.ui.seasons, (season: VideoItem, index) => {
                    ListItem() {
                      Row() {
                        Text(season.name)
                          .fontColor(season.id === this.ui.id ? $r('app.color.color_main') : $r('app.color.text_1'))
                          .fontSize(14)
                          .onClick(() => {
                            this.ui.videoScroller.scrollEdge(Edge.Start)
                            this.ui.id = season.id
                            this.vm.getSeasonDetail()
                            this.vm.getEpisodes(season.id)
                          })
                        if (index !== this.ui.seasons.length - 1) {
                          Text('  /  ').fontSize(12).fontColor('#7f8c94')
                        }
                      }
                    }
                  })
                }.width('100%').height(20).listDirection(Axis.Horizontal).scrollBar(BarState.Off)
                .padding({ left: 16, right: 16 })
              }

              if (this.ui.videoGroups.length > 1) {
                List() {
                  ForEach(this.ui.videoGroups, (group: string, index) => {
                    ListItem() {
                      Text(group)
                        .fontColor(index === this.ui.selectGroupIndex ? $r('app.color.color_main') :
                          $r('app.color.text_1'))
                        .fontSize(14)
                        .padding({
                          left: 8,
                          right: 8,
                          top: 6,
                          bottom: 6
                        })
                        .borderRadius(6)
                        .backgroundColor(index === this.ui.selectGroupIndex ? 'rgba(29, 155, 240, 0.1)' :
                          Color.Transparent)
                        .onClick(() => {
                          this.ui.videoScroller.scrollEdge(Edge.Start)
                          this.ui.selectGroupIndex = index
                        })
                    }
                  })
                }
                .width('100%')
                .height(30)
                .listDirection(Axis.Horizontal)
                .scrollBar(BarState.Off)
                .margin({
                  top: 12
                })
                .padding({ left: 16, right: 16 })
              }

              Blank(16)
              if (this.ui.videos.length > 0) {
                List({ space: 12, scroller: this.ui.videoScroller }) {
                  ForEach(this.ui.videos[this.ui.selectGroupIndex], (item: VideoItem) => {
                    ListItem() {
                      Column() {
                        RelativeContainer() {
                          ImageKnifeComponentV2({
                            imageKnifeOption: {
                              loadSrc: item.image,
                              objectFit: ImageFit.Cover,
                              border: {
                                radius: 6
                              }
                            }
                          })
                            .aspectRatio(16 / 9)
                            .width('100%')
                            .borderColor($r('app.color.border_1'))
                            .borderWidth(0.5)
                            .borderRadius(6)
                          if (item.tags && item.tags.length > 0) {
                            Row({ space: 4 }) {
                              ForEach(item.tags, (tag: string) => {
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
                        }.width('100%').aspectRatio(16 / 9)

                        Text(item.name)
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
                          .textAlign(TextAlign.Start)
                        Text(item.subtitle)
                          .fontColor(Color.Gray)
                          .fontSize(12)
                          .width('100%')
                          .maxLines(2)
                          .ellipsisMode(EllipsisMode.END)
                          .textOverflow({
                            overflow: TextOverflow.Ellipsis
                          })
                          .textAlign(TextAlign.Start)
                          .margin({
                            top: 4
                          })
                        Text(item.time)
                          .fontColor($r('app.color.text_1'))
                          .fontSize(12)
                          .width('100%')
                          .maxLines(1)
                          .ellipsisMode(EllipsisMode.END)
                          .textOverflow({
                            overflow: TextOverflow.Ellipsis
                          })
                          .textAlign(TextAlign.Start)
                          .margin({
                            top: 4
                          })
                      }.width(160)
                      .height(140)
                      .alignItems(HorizontalAlign.Start).margin({
                        bottom: 8
                      })
                    }.onClick(() => {
                      HMRouterMgr.to(RouterConsts.VideoDetailPage)
                        .withParam({
                          id: item.id,
                          mediaName: this.ui.mediaName
                        })
                        .push()
                    })
                  })
                }
                .width('100%')
                .height(165)
                .listDirection(Axis.Horizontal)
                .scrollBar(BarState.Off)
                .padding({ left: 16, right: 16 })
              }
              if (this.ui.people.length > 0) {
                Blank().height(20)
                CastView({
                  peopleList: this.ui.people
                })
              }

              Blank().height(30 + this.vm.appUIState.safeBottom)

            }.alignItems(HorizontalAlign.Start).padding({
              top: 20
            })
            .align(Alignment.Top)

          }.constraintSize({
            minHeight: 850
          })
        }
        .width('100%')
        .height('100%')
        .scrollBar(BarState.Off)
        .onScrollFrameBegin((offset: number, state: ScrollState) => {
          const param: CalculateHeightParam = { offset, state, yOffset: this.ui.scroller.currentOffset().yOffset };
          const imageHeightChange: boolean | void = this.vm.calculateImageHeight(param);
          if (imageHeightChange) {
            return { offsetRemain: 0 };
          }
          return { offsetRemain: offset };
        })
        .onDidScroll(() => {
          let y = this.ui.scroller.currentOffset().yOffset
          if (y === 0 && this.ui.currentScrollState === 1) {
            this.vm.bannerSpringBack();
          }
          this.ui.blur = y / 5
          if (y > 128) {
            this.ui.showActionBar = true
            this.ui.actionBarOpacity = Math.min((y - 128) / 72, 0.6)
          } else {
            this.ui.actionBarOpacity = 0
            this.ui.showActionBar = false
          }
        })
      }
      ActionBar({
        title: this.ui.showActionBar ? this.ui.title : ''
      })
        .backgroundColor('rgba(18,22,24,' + this.ui.actionBarOpacity + ')')
        .backdropBlur((this.ui.blur - 20) * 10)
    }.backgroundColor($r('app.color.bg_main')).alignContent(Alignment.Top)
  }

  @Builder
  SheetBuilder() {
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