import { HMRouter, HMRouterMgr } from '@hadss/hmrouter';
import { ImageKnifeComponentV2 } from '@ohos/imageknife';
import { ActionBar } from '../../component/ActionBar';
import { VideoDetailUIState } from './VideoDetailUIState';
import { CalculateHeightParam, VideoDetailViewModel } from './VideoDetailViewModel';
import { LengthUnit } from '@ohos.arkui.node';
import { BaseItemPerson, ExternalUrl, ImageType, PersonKind } from 'sdk/src/main/ets/generated-client/models';
import { ApiClient } from '../../network/ApiClient'
import { ImageUrlsApi } from 'sdk'
import { VideoItem } from '../home/hometab/HomeUIState';
import { VideoCard } from '../videolist/VideoListPage';
import { playItems } from '../../common/CommonFunc';
import { TextExpandView } from '../../component/textexpand/TextExpand';
import { LastSpanAttribute } from '../../component/textexpand/Model';
import { RouterConsts } from '../../common/RouterConsts';

@HMRouter({
  pageUrl: RouterConsts.VideoDetailPage
})
@ComponentV2
export struct VideoDetailPage {
  @Local ui: VideoDetailUIState = new VideoDetailUIState(this.getUIContext())
  @Local vm: VideoDetailViewModel = new VideoDetailViewModel(this.ui)
  @Local isShowSheet: boolean = false;

  aboutToAppear(): void {
    let param = HMRouterMgr.getCurrentParam() as object
    if (param) {
      this.ui.id = param['id'] as string
      this.ui.mediaName = param['mediaName'] as string
      this.ui.isTvShows = param['isTvShows'] ?? false
      if (this.ui.isTvShows) {
        this.vm.getTvShowDetail(this.ui.id)
        this.vm.getNextUp()
        this
      } else {
        this.vm.getVideoDetail(this.ui.id)
      }
    }
  }

  build() {
    Stack() {
      Scroll(this.ui.scroller) {
        Column() {
          Stack() {
            Column() {
              ImageKnifeComponentV2({
                imageKnifeOption: {
                  loadSrc: this.ui.image,
                  objectFit: ImageFit.Cover
                }
              }).width('100%')
                .height('100%')
                .blur(this.ui.blur)
            }.width('100%')
            .height('100%')

            Stack().width('100%')
              .height('100%')
              .linearGradient({
                angle: 0, // 0度在CSS中是从下到上，ArkTS中180度对应
                colors: [
                  [0x121618, 0.0], // 起始位置：完全不透明
                  ['rgba(26, 30, 35, 0.6)', 0.3], // 30%位置：60%不透明
                  ['rgba(26, 30, 35, 0.2)', 0.4], // 40%位置：20%不透明
                  ['rgba(26, 30, 35, 0.1)', 1]// 100%位置：10%不透明
                ]
              })

            if (this.ui.isMovie) {
              ImageKnifeComponentV2({
                imageKnifeOption: {
                  loadSrc: this.ui.logo,
                  objectFit: ImageFit.Contain,
                }
              })
                .height(80)
                .aspectRatio(80 / 31) // 刮削的logo都是800x310
                .margin({ bottom: 10 })
            } else {
              Column({ space: 12 }) {
                Text(this.ui.subtitle)
                  .fontSize(14)
                  .fontColor(Color.White)
                  .maxLines(1)
                  .ellipsisMode(EllipsisMode.END)
                  .textOverflow({
                    overflow: TextOverflow.Ellipsis
                  })
                  .textAlign(TextAlign.Center)
                Text(this.ui.title)
                  .fontSize(26)
                  .fontWeight(FontWeight.Medium)
                  .fontColor(Color.White)
                  .maxLines(1)
                  .ellipsisMode(EllipsisMode.END)
                  .textOverflow({
                    overflow: TextOverflow.Ellipsis
                  })
                  .textAlign(TextAlign.Center)
              }.padding(16).alignItems(HorizontalAlign.Start)
            }
          }.width('100%')
          .height(this.ui.imageHeight)
          .alignContent(Alignment.BottomStart)

          Column() {
            //信息
            Text() {
              if (!this.ui.isTvShows && this.ui.score > 0) {
                Span(this.ui.score + '分  ').fontColor($r('app.color.text_score'))
              }
              Span(this.ui.info)
            }
            .fontSize(12)
            .fontColor('#CBD5E0')
            .lineSpacing({ value: 6, unit: LengthUnit.VP })
            .width('100%')
            .textAlign(TextAlign.Start)

            if (!this.ui.isTvShows) {
              Blank().height(16)

              //todo 字幕音频切换
              Row({ space: 16 }) {
                ForEach(this.ui.tags, (item: string) => {
                  Image(item).height(22)
                })
              }
            }

            if (this.ui.playbackPositionTicks > 0) {
              Blank().height(16)
              Row({ space: 8 }) {
                Progress({
                  value: this.ui.percentageWatched,
                  total: 100,
                  type: ProgressType.Linear,
                })
                  .height(4)
                  .width(150)
                  .color('#3B82F6')
                Text(this.ui.remainingTime).fontColor('#CBD5E0').fontSize(12)
              }.width('100%').justifyContent(FlexAlign.Start)
            }
            Blank().height(16)
            Row({ space: 8 }) {
              Button() {
                Row() {
                  Image($r('app.media.ic_play_round')).width(20).height(20).margin({ right: 8 })
                  Text(this.ui.playButtonText)
                    .fontSize(16)
                    .fontColor(Color.White)
                    .fontWeight(FontWeight.Medium)
                }
              }
              .height(50)
              .layoutWeight(1)
              .margin({
                right: 12
              })
              .borderRadius(25)
              .onClick(() => {
                if (this.ui.isTvShows) {
                  this.vm.playFirst()
                } else {
                  playItems([this.ui.id], this.getUIContext().getHostContext()!)
                }
              })

              Button() {
                Image(this.ui.isFavorite ? $r('app.media.ic_like') : $r('app.media.ic_unlike'))
                  .fillColor('#CBD5E0')
                  .width(22)
                  .height(22)
              }
              .width(50)
              .height(50)
              .borderWidth(1)
              .borderColor($r('app.color.border'))
              .borderRadius(25)
              .backgroundColor('#0affffff')
              .onClick(()=>{
                this.vm.changeFavorite()
              })

              Button() {
                Image($r('app.media.ic_check'))
                  .fillColor(this.ui.played ? $r('app.color.color_main') : '#CBD5E0')
                  .width(22)
                  .height(22)
              }
              .width(50)
              .height(50)
              .borderWidth(1)
              .borderColor($r('app.color.border'))
              .borderRadius(25)
              .backgroundColor('#0affffff')
              .onClick(()=>{
                this.vm.changePlayedState()
              })
            }.width('100%')


            // TextExpandView({
            //   textSectionAttribute: this.textSectionAttribute,
            //   lastSpanAttribute: new LastSpanAttribute(0, 2,
            //     this.handleContent(), 16,
            //     $r('app.color.text_expand_selected_tab_name'))
            // }).id(`textClick${this.index}`)
            if (this.ui.overview.length > 0) {
              Blank().height(16)
              TextExpandView({
                text: this.ui.overview,
                onMoreClick: () => {
                  this.isShowSheet = !this.isShowSheet;
                },
                lastSpanAttribute: new LastSpanAttribute(0, 2, ['更多', '收起'], 14, $r('app.color.color_main'))
              })
                .bindSheet($$this.isShowSheet, this.SheetBuilder(), {
                  height: 420,
                  preferType: SheetType.BOTTOM,
                  title: { title: '简介' },
                })
            }

            if (this.ui.isTvShows) {
              if (this.ui.seasons.length > 1) {
                Blank().height(20)
                Text(`共${this.ui.seasons.length}季`).fontSize(15).fontWeight(FontWeight.Medium).fontColor(Color.White)
              }
              Blank().height(20)
              Grid() {
                ForEach(this.ui.seasons, (item: VideoItem) => {
                  GridItem() {
                    VideoCard({
                      item: item,
                      textAlign: TextAlign.Center
                    }).onClick(() => {
                      HMRouterMgr.to(RouterConsts.SeasonDetailPage)
                        .withParam({
                          id: item.id,
                          mediaName: this.ui.mediaName,
                          seasons: this.ui.seasons,
                          info: this.ui.info,
                          parentId: this.ui.id
                        })
                        .push()
                    })
                  }
                })
              }.columnsTemplate('1fr 1fr 1fr')
              .columnsGap(8)
              .rowsGap(20)
              .width('100%')
            }
            if(this.ui.people.length > 0){
              Blank().height(16)
              Text('演职人员').fontSize(15).fontWeight(FontWeight.Medium).fontColor(Color.White)
              List({ space: 12 }) {
                ForEach(this.ui.people, (item: BaseItemPerson) => {
                  ListItem() {
                    Column({ space: 8 }) {
                      ImageKnifeComponentV2({
                        imageKnifeOption: {
                          loadSrc: ApiClient.Instance().basePath +
                          new ImageUrlsApi().getItemImageUrlById(item.Id || '', ImageType.Primary),
                          objectFit: ImageFit.Cover,
                          border: {
                            radius: 33
                          },
                          placeholderSrc: $r('app.media.ic_avatar'),
                        }
                      })
                        .width(66)
                        .height(66)
                        .borderColor('#535b60')
                        .borderRadius(33)
                        .backgroundColor('#0affffff')

                      Text(item.Name)
                        .fontColor(Color.White)
                        .fontSize(12)
                        .fontWeight(FontWeight.Medium)
                        .width(66)
                        .maxLines(1)
                        .ellipsisMode(EllipsisMode.END)
                        .textOverflow({
                          overflow: TextOverflow.Ellipsis
                        })
                        .textAlign(TextAlign.Center)
                      Text(item.Type == PersonKind.Actor ? `饰${item.Role}` :
                        (item.Type == PersonKind.Director ? '导演' : item.Role))
                        .fontColor('#b3cbd5e0')
                        .fontSize(10)
                        .width(66)
                        .maxLines(1)
                        .ellipsisMode(EllipsisMode.END)
                        .textOverflow({
                          overflow: TextOverflow.Ellipsis
                        })
                        .textAlign(TextAlign.Center)
                    }
                  }
                })
              }
              .height(130)
              .width('100%')
              .listDirection(Axis.Horizontal)
              .scrollBar(BarState.Off)
              .padding({
                top: 10, bottom: 10
              })

            }
            if (!this.ui.isTvShows) {
              Blank().height(16)

              Text('文件信息').fontSize(15).fontWeight(FontWeight.Medium).fontColor(Color.White)

              Column() {
                Text('文件位置').fontSize(12).fontColor('#b3cbd5e0')
                Blank().height(10)
                Text(this.ui.path).fontSize(12).fontColor('#CBD5E0')
                Blank().height(16)
                Text('文件大小').fontSize(12).fontColor('#b3cbd5e0')
                Blank().height(10)
                Text(this.ui.size).fontSize(12).fontColor('#CBD5E0')
                Blank().height(16)
                Text('创建日期').fontSize(12).fontColor('#b3cbd5e0')
                Blank().height(10)
                Text(this.ui.createDate).fontSize(12).fontColor('#CBD5E0')
              }
              .width('100%')
              .backgroundColor('#0affffff')
              .borderRadius(12)
              .padding(12)
              .alignItems(HorizontalAlign.Start)
              .margin({ top: 12 })

              Blank().height(16)

              Text('视频信息').fontSize(15).fontWeight(FontWeight.Medium).fontColor(Color.White)

              Column({ space: 16 }) {
                if (this.ui.videoInfo.length > 0) {
                  Column({ space: 10 }) {
                    Text('视频').fontSize(12).fontColor('#b3cbd5e0')
                    Text(this.ui.videoInfo)
                      .fontSize(12)
                      .fontColor('#CBD5E0')
                      .lineSpacing({ value: 4, unit: LengthUnit.VP })
                  }.width('100%')
                  .alignItems(HorizontalAlign.Start)
                }
                if (this.ui.audioInfo.length > 0) {
                  Column({ space: 10 }) {
                    Text('音频').fontSize(12).fontColor('#b3cbd5e0')
                    Text(this.ui.audioInfo)
                      .fontSize(12)
                      .fontColor('#CBD5E0')
                      .lineSpacing({ value: 4, unit: LengthUnit.VP })
                  }.width('100%')
                  .alignItems(HorizontalAlign.Start)
                }
                if (this.ui.subtitleInfo.length > 0) {
                  Column({ space: 10 }) {
                    Text('字幕').fontSize(12).fontColor('#b3cbd5e0')
                    Text(this.ui.subtitleInfo)
                      .fontSize(12)
                      .fontColor('#CBD5E0')
                      .lineSpacing({ value: 4, unit: LengthUnit.VP })
                  }.width('100%')
                  .alignItems(HorizontalAlign.Start)
                }
              }
              .width('100%')
              .backgroundColor('#0affffff')
              .borderRadius(12)
              .padding(12)
              .margin({ top: 12 })
            }

            if (this.ui.externalUrls.length) {
              Blank().height(16)
              Text('链接').fontSize(15).fontWeight(FontWeight.Medium).fontColor(Color.White)
              Row({ space: 6 }) {
                ForEach(this.ui.externalUrls, (item: ExternalUrl) => {
                  Text(item.Name)
                    .fontSize(12)
                    .fontColor('#CBD5E0')
                    .backgroundColor('#0affffff')
                    .borderRadius(4)
                    .padding({
                      left: 12,
                      right: 12,
                      top: 8,
                      bottom: 8
                    })
                    .onClick(() => {
                      this.vm.openExternalUrl(item.Url || '')
                    })
                })
              }.margin({ top: 12 })
            }
            Blank().height(30 + this.vm.appUIState.safeBottom)

          }.alignItems(HorizontalAlign.Start).padding({
            left: 16, right: 16, top: 12
          })
          .align(Alignment.Top)

        }
      }
      .width('100%')
      .height('100%')
      .onScrollFrameBegin((offset: number, state: ScrollState) => {
        const param: CalculateHeightParam = { offset, state, yOffset: this.ui.scroller.currentOffset().yOffset };
        const imageHeightChange: boolean | void = this.vm.calculateImageHeight(param);
        if (imageHeightChange) {
          return { offsetRemain: 0 };
        }
        return { offsetRemain: offset };
      })
      .scrollBar(BarState.Off)
      .onDidScroll(() => {
        let y = this.ui.scroller.currentOffset().yOffset
        if (y === 0 && this.ui.currentScrollState === 1) {
          this.vm.bannerSpringBack();
        }
        this.ui.blur = y / 10
        if (y > 200) {
          this.ui.showActionBar = true
          this.ui.actionBarOpacity = Math.min((y - 200) / 72, 0.6)
        } else {
          this.ui.actionBarOpacity = 0
          this.ui.showActionBar = false
        }
      })

      ActionBar({
        title: this.ui.showActionBar ? this.ui.title : ''
      }).backgroundColor('rgba(18,22,24,' + this.ui.actionBarOpacity + ')')
        .backdropBlur((this.ui.blur - 20) * 10)
    }.backgroundColor($r('app.color.bg_main')).alignContent(Alignment.Top)
  }

  @Builder
  SheetBuilder() {
    Scroll() {
      Text(this.ui.overview)
        .width('100%')
        .fontColor('#CBD5E0')
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