import { HMRouter, HMRouterMgr } from '@hadss/hmrouter';
import { ImageKnife, ImageKnifeComponent } from '@ohos/imageknifepro';
import { ActionBar } from '../../component/ActionBar';
import { AppRouteDestination } from '../../component/AppRouteDestination';
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
import { image } from '@kit.ImageKit';
import {
  HomeFeedTabItem,
  HomeLibraryChipSelector
} from '../home/hometab/HomeLibraryChipSelector';

@ComponentV2
struct NativeSeasonPlayAction {
  @Require @Param label: string
  @Require @Param onAction: () => void

  build() {
    Button({ type: ButtonType.Normal, stateEffect: false }) {
      Row({ space: 8 }) {
        SymbolGlyph($r('sys.symbol.play_fill'))
          .fontSize(20)
          .fontColor([$r('sys.color.icon_primary')])
        Text(this.label)
          .fontSize(16)
          .fontColor($r('sys.color.font_primary'))
          .fontWeight(FontWeight.Medium)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
      }
      .padding({ left: 20, right: 20 })
      .width('100%')
      .justifyContent(FlexAlign.Center)
    }
    .height(50)
    .borderRadius(25)
    .buttonStyle(ButtonStyleMode.TEXTUAL)
    .backgroundColor(Color.Transparent)
    .systemMaterial(AppThemeSurfaceResolver.material(AppThemeMaterialRole.AdaptiveInteractive))
    .accessibilityText(this.label)
    .onClick(() => {
      this.onAction()
    })
  }
}

@ComponentV2
struct NativeSeasonIconAction {
  @Require @Param label: string
  @Require @Param accessibilityLabel: string
  @Require @Param selected: boolean
  @Require @Param onAction: () => void

  build() {
    Column({ space: 6 }) {
      Button({ type: ButtonType.Circle, stateEffect: false }) {
        Image($r('app.media.ic_check'))
          .width(22)
          .height(22)
          .renderMode(ImageRenderMode.Template)
          .interpolation(ImageInterpolation.High)
          .fillColor(this.selected ? $r('app.color.color_main') : $r('sys.color.icon_primary'))
      }
      .width(50)
      .height(50)
      .buttonStyle(ButtonStyleMode.TEXTUAL)
      .systemMaterial(AppThemeSurfaceResolver.material(AppThemeMaterialRole.InteractiveFloating))
      .accessibilityText(this.accessibilityLabel)
      .onClick(() => {
        this.onAction()
      })

      Text(this.label)
        .fontSize(12)
        .fontColor($r('sys.color.font_secondary'))
        .maxLines(1)
        .textAlign(TextAlign.Center)
    }
    .width(88)
    .alignItems(HorizontalAlign.Center)
  }
}

@HMRouter({
  pageUrl: RouterConsts.SeasonDetailPage,
  useNavDst: true
})
@ComponentV2
export struct SeasonDetailPage {
  @Local ui: SeasonDetailUIState = new SeasonDetailUIState(this.getUIContext())
  vm: SeasonDetailViewModel = new SeasonDetailViewModel(this.ui)
  @Local isShowSheet: boolean = false;
  @Local heroPixelMap?: image.PixelMap
  private heroPixelMapSource: string = ''
  private heroPixelMapLoadingSource: string = ''

  private async prepareHeroPixelMap(sourceUrl: string): Promise<void> {
    if (!sourceUrl || this.heroPixelMapSource === sourceUrl || this.heroPixelMapLoadingSource === sourceUrl) {
      return
    }
    this.heroPixelMapLoadingSource = sourceUrl
    try {
      const imageData = await ImageKnife.getInstance().getCacheImage(sourceUrl)
      const sourcePixelMap = imageData?.pixelmapList?.[0]
      if (!sourcePixelMap) {
        return
      }
      const imageInfo = await sourcePixelMap.getImageInfo()
      const pixelFormat = imageInfo.pixelFormat
      const sourceAlphaType = imageInfo.alphaType
      if (pixelFormat !== image.PixelMapFormat.RGBA_8888 &&
        pixelFormat !== image.PixelMapFormat.BGRA_8888 &&
        pixelFormat !== image.PixelMapFormat.ARGB_8888) {
        return
      }

      const width = imageInfo.size.width
      const height = imageInfo.size.height
      const rowBytes = sourcePixelMap.getBytesNumberPerRow()
      const sourceBuffer = new ArrayBuffer(sourcePixelMap.getPixelBytesNumber())
      await sourcePixelMap.readPixelsToBuffer(sourceBuffer)
      const sourcePixels = new Uint8Array(sourceBuffer)
      const compactBuffer = new ArrayBuffer(width * height * 4)
      const compactPixels = new Uint8Array(compactBuffer)

      for (let y = 0; y < height; y++) {
        const sourceRowStart = y * rowBytes
        const targetRowStart = y * width * 4
        compactPixels.set(sourcePixels.subarray(sourceRowStart, sourceRowStart + width * 4), targetRowStart)
      }

      const fadeStart = Math.floor(height * 0.64)
      const fadeHeight = Math.max(1, height - fadeStart)
      const alphaOffset = pixelFormat === image.PixelMapFormat.ARGB_8888 ? 0 : 3
      for (let y = fadeStart; y < height; y++) {
        const progress = (y - fadeStart) / fadeHeight
        const smoothProgress = progress * progress * (3 - 2 * progress)
        const alphaScale = 1 - smoothProgress
        for (let x = 0; x < width; x++) {
          const pixelIndex = (y * width + x) * 4
          const alphaIndex = pixelIndex + alphaOffset
          const sourceAlpha = compactPixels[alphaIndex]
          const targetAlpha = Math.round(sourceAlpha * alphaScale)
          const colorScale = sourceAlphaType === image.AlphaType.PREMUL ? alphaScale : targetAlpha / 255
          for (let channel = 0; channel < 4; channel++) {
            if (channel !== alphaOffset) {
              compactPixels[pixelIndex + channel] = Math.round(compactPixels[pixelIndex + channel] * colorScale)
            }
          }
          compactPixels[alphaIndex] = targetAlpha
        }
      }

      const nextPixelMap = await image.createPixelMap(compactBuffer, {
        size: { width: width, height: height },
        srcPixelFormat: pixelFormat,
        pixelFormat: pixelFormat,
        alphaType: image.AlphaType.PREMUL,
        editable: false
      })
      if (this.ui.backgroundImage !== sourceUrl) {
        nextPixelMap.release()
        return
      }
      const previousPixelMap = this.heroPixelMap
      this.heroPixelMapSource = sourceUrl
      this.getUIContext().animateTo({ duration: 220, curve: Curve.FastOutSlowIn }, () => {
        this.heroPixelMap = nextPixelMap
      })
      if (previousPixelMap && previousPixelMap !== nextPixelMap) {
        previousPixelMap.release()
      }
    } finally {
      if (this.heroPixelMapLoadingSource === sourceUrl) {
        this.heroPixelMapLoadingSource = ''
      }
    }
  }

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable)
  }

  private configureHeroHeight(): void {
    this.ui.imageBaseHeight = this.useNativeSurface() ?
      SeasonDetailUIState.nativeImageInitHeight : SeasonDetailUIState.imageInitHeight
    this.ui.imageHeight = this.ui.imageBaseHeight
  }

  private seasonTabs(): HomeFeedTabItem[] {
    return this.ui.seasons.map<HomeFeedTabItem>((season: VideoItem) => {
      return { id: season.id, label: season.name }
    })
  }

  private episodeGroupTabs(): HomeFeedTabItem[] {
    return this.ui.videoGroups.map<HomeFeedTabItem>((group: string, index: number) => {
      return { id: index.toString(), label: group }
    })
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
  private nativeHeroBackdrop() {
    Stack() {
      if (this.heroPixelMap && this.heroPixelMapSource === this.ui.backgroundImage) {
        Image(this.heroPixelMap)
          .width('100%')
          .height('100%')
          .objectFit(ImageFit.Cover)
          .transition(TransitionEffect.OPACITY)
      } else {
        ImageKnifeComponent({
          imageKnifeOption: {
            loadSrc: this.ui.backgroundImage,
            objectFit: ImageFit.Cover,
            onComplete: () => {
              this.prepareHeroPixelMap(this.ui.backgroundImage)
            }
          }
        })
          .width('100%')
          .height('100%')
      }

      Stack()
        .width('100%')
        .height('100%')
        .linearGradient({
          angle: 0,
          colors: [
            ['rgba(0, 0, 0, 0)', 0],
            ['rgba(0, 0, 0, 0.08)', 0.24],
            ['rgba(0, 0, 0, 0.28)', 0.66],
            ['rgba(0, 0, 0, 0)', 1]
          ]
        })
    }
    .width('100%')
    .height('100%')
  }

  @Builder
  private nativePageBackdrop() {
    Stack() {
      ImageKnifeComponent({
        imageKnifeOption: {
          loadSrc: this.ui.backgroundImage,
          objectFit: ImageFit.Cover
        }
      })
        .width('100%')
        .height('100%')
        .scale({ x: 1.18, y: 1.18 })
        .blur(128)
        .opacity(0.95)

      Stack()
        .width('100%')
        .height('100%')
        .linearGradient({
          angle: 0,
          colors: [
            [$r('app.color.native_detail_page_overlay_soft'), 0],
            [$r('app.color.native_detail_page_overlay_soft'), 0.56],
            [$r('app.color.native_detail_page_overlay_strong'), 1]
          ]
        })
    }
    .width('100%')
    .height('100%')
    .hitTestBehavior(HitTestMode.None)
  }

  @Builder
  private nativeHeroMetadata() {
    Column({ space: 10 }) {
      Text(this.ui.seasonName)
        .fontSize(14)
        .fontColor(Color.White)
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })

      Text(this.ui.seriesName)
        .fontSize(28)
        .fontWeight(FontWeight.Bold)
        .fontColor(Color.White)
        .maxLines(2)
        .textOverflow({ overflow: TextOverflow.Ellipsis })

      Text(this.ui.info)
        .fontSize(13)
        .fontColor(Color.White)
        .maxLines(2)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .lineSpacing({ value: 10, unit: LengthUnit.VP })
    }
    .width('100%')
    .padding({ left: 16, right: 16, bottom: 12 })
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  private nativeDetailActions() {
    Column({ space: 14 }) {
      NativeSeasonPlayAction({
        label: this.ui.episodeName !== '' ? this.ui.episodeName : '播放',
        onAction: () => {
          this.vm.playFirst()
        }
      })
        .width('100%')

      Row() {
        NativeSeasonIconAction({
          label: this.ui.played ? '已观看' : '标记已看',
          accessibilityLabel: this.ui.played ? '标记为未播放' : '标记为已播放',
          selected: this.ui.played,
          onAction: () => {
            this.vm.changePlayedState()
          }
        })
      }
      .width('100%')
      .justifyContent(FlexAlign.Center)
    }
    .width('100%')
    .padding({ left: 16, right: 16 })
  }

  @Builder
  private legacyDetailActions() {
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
      .margin({ right: 12 })
      .borderRadius(25)
      .height(50)
      .backgroundColor($r('app.color.color_main'))
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
    }
    .width('100%')
    .padding({ left: 16, right: 16 })
  }

  @Builder
  private feiniuBackdrop() {
    ImageKnifeComponent({
      imageKnifeOption: {
        loadSrc: this.ui.backgroundImage,
        objectFit: ImageFit.Cover,
      }
    })
      .width('100%')
      .height(this.ui.imageHeight - 100)
      .blur(this.ui.blur)
  }

  @Builder
  private feiniuActionBar() {
    ActionBar({
      title: this.ui.showActionBar ? this.ui.title : ''
    })
      .backgroundColor('rgba(18,22,24,' + this.ui.actionBarOpacity + ')')
      .backdropBlur((this.ui.blur - 20) * 10)
  }

  @Builder
  private overviewTriggerContent() {
    TextExpandView({
      text: this.ui.overview,
      onMoreClick: () => {
        this.isShowSheet = !this.isShowSheet;
      },
      lastSpanAttribute: new LastSpanAttribute(0, 2, ['更多', '收起'], 14, $r('app.color.color_main'))
    })
  }

  @Builder
  private overviewSheetTrigger() {
    if (deviceInfo.sdkApiVersion < 26) {
      Row() {
        this.overviewTriggerContent()
      }
      .width('100%')
      .padding({ left: 16, right: 16 })
      .bindSheet($$this.isShowSheet, this.SheetBuilder(), {
        height: 420,
        preferType: SheetType.BOTTOM,
        title: { title: '简介' },
      })
    } else if (this.sheetMaterialDecision() === OverlayMaterialDecision.UseFloatingMaterial) {
      Row() {
        this.overviewTriggerContent()
      }
      .width('100%')
      .padding({ left: 16, right: 16 })
      .bindSheet($$this.isShowSheet, this.SheetBuilder(), {
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
      .padding({ left: 16, right: 16 })
      .bindSheet($$this.isShowSheet, this.SheetBuilder(), {
        height: 420,
        preferType: SheetType.BOTTOM,
        title: { title: '简介' },
        systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
      })
    }
  }

  aboutToAppear(): void {
    this.configureHeroHeight()
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

  aboutToDisappear(): void {
    if (this.heroPixelMap) {
      this.heroPixelMap.release()
      this.heroPixelMap = undefined
      this.heroPixelMapSource = ''
    }
  }

  @Builder
  private pageContent(showLegacyActionBar: boolean) {
    Stack() {
      if (this.useNativeSurface() && !this.ui.firstLoading) {
        this.nativePageBackdrop()
      }

      if (this.ui.firstLoading) {
        SkeletonDetailView()
      } else {
        Scroll(this.ui.scroller) {
          Column() {
            Stack() {
              if (this.useNativeSurface()) {
                this.nativeHeroBackdrop()
                this.nativeHeroMetadata()
              } else {
                this.feiniuBackdrop()

                Stack().width('100%')
                  .height(this.ui.imageHeight - 100)
                  .linearGradient({
                    angle: 0,
                    colors: [
                      [0x121618, 0.0],
                      ['rgba(26, 30, 35, 0.6)', 0.3],
                      ['rgba(26, 30, 35, 0.2)', 0.4],
                      ['rgba(26, 30, 35, 0.1)', 1]
                    ]
                  })

                Column() {
                  Blank()
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
                      Blank().height(60)
                      Text(this.ui.seriesName).fontColor(Color.White).fontWeight(FontWeight.Medium).fontSize(28)
                      Blank()
                      Text(this.ui.seasonName).fontColor($r('app.color.text_primary')).fontSize(14)
                      Blank().height(10)
                      Text() {
                        Span(this.ui.info)
                      }
                      .fontSize(12)
                      .fontColor($r('app.color.text_1'))
                      .lineSpacing({ value: 6, unit: LengthUnit.VP })
                      .width('100%')
                      .textAlign(TextAlign.Start)
                    }
                    .alignItems(HorizontalAlign.Start)
                    .height('100%')
                    .layoutWeight(1)
                  }
                  .height(200)
                  .padding({ left: 16, right: 16 })
                }
                .width('100%')
                .height('100%')
              }

            }
            .width('100%')
            .height(this.ui.imageHeight)
            .alignContent(this.useNativeSurface() ? Alignment.BottomStart : Alignment.Top)

            Stack() {
              Column() {
                if (this.useNativeSurface()) {
                  this.nativeDetailActions()
                } else {
                  this.legacyDetailActions()
                }

              if (this.ui.overview.length > 0) {
                Blank().height(20)
                this.overviewSheetTrigger()
              }

              if (this.useNativeSurface()) {
                Blank().height(24)
                if (this.ui.seasons.length > 1) {
                  Text('选择季')
                    .fontSize(18)
                    .fontWeight(FontWeight.Medium)
                    .fontColor($r('sys.color.font_primary'))
                    .padding({ left: 16, right: 16 })

                  Blank().height(12)
                  HomeLibraryChipSelector({
                    tabs: this.seasonTabs(),
                    selectedId: this.ui.id,
                    onSelectedIdChange: (id: string) => {
                      if (id === this.ui.id) {
                        return
                      }
                      this.ui.videoScroller.scrollEdge(Edge.Start)
                      this.ui.id = id
                      this.ui.episodeName = ''
                      this.vm.getSeasonDetail()
                    }
                  })
                    .padding({ left: 16, right: 16 })
                }

                Blank().height(this.ui.seasons.length > 1 ? 24 : 0)
                Text('剧集')
                  .fontSize(18)
                  .fontWeight(FontWeight.Medium)
                  .fontColor($r('sys.color.font_primary'))
                  .padding({ left: 16, right: 16 })

                if (this.ui.videoGroups.length > 1) {
                  Blank().height(12)
                  HomeLibraryChipSelector({
                    tabs: this.episodeGroupTabs(),
                    selectedId: this.ui.selectGroupIndex.toString(),
                    onSelectedIdChange: (id: string) => {
                      this.ui.videoScroller.scrollEdge(Edge.Start)
                      this.ui.selectGroupIndex = Number(id)
                    }
                  })
                    .padding({ left: 16, right: 16 })
                }
              } else {
                Blank().height(20)
                if (this.ui.seasons.length > 1) {
                  List() {
                    ForEach(this.ui.seasons, (season: VideoItem, index) => {
                      ListItem() {
                        Row() {
                          Text(season.name)
                            .fontColor(season.id === this.ui.id ? $r('app.color.color_main') :
                              $r('app.color.text_1'))
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
                  }
                  .width('100%')
                  .height(20)
                  .listDirection(Axis.Horizontal)
                  .scrollBar(BarState.Off)
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
                          .padding({ left: 8, right: 8, top: 6, bottom: 6 })
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
                  .margin({ top: 12 })
                  .padding({ left: 16, right: 16 })
                }
              }

              Blank(16)
              if (this.ui.videos.length > this.ui.selectGroupIndex) {
                List({ space: 12, scroller: this.ui.videoScroller }) {
                  ForEach(this.ui.videos[this.ui.selectGroupIndex], (item: VideoItem) => {
                    ListItem() {
                      Column() {
                        RelativeContainer() {
                          ImageKnifeComponent({
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
                            .borderColor(AppThemeSurfaceResolver.mediaOutline(
                              this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable))
                            .borderWidth(AppThemeSurfaceResolver.outlineWidth(
                              this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable, 0.5))
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
                          .fontColor(this.useNativeSurface() ? $r('sys.color.font_primary') :
                            $r('app.color.text_primary'))
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
                          .fontColor(this.useNativeSurface() ? $r('sys.color.font_secondary') : Color.Gray)
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
                          .fontColor(this.useNativeSurface() ? $r('sys.color.font_secondary') :
                            $r('app.color.text_1'))
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
                      }
                      .width(this.useNativeSurface() ? 180 : 160)
                      .height(this.useNativeSurface() ? 158 : 140)
                      .alignItems(HorizontalAlign.Start)
                      .margin({
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
                .height(this.useNativeSurface() ? 184 : 165)
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
                top: this.useNativeSurface() ? 0 : 20
              })
              .width('100%')
              .constraintSize({ maxWidth: this.useNativeSurface() ? 720 : '100%' })
              .align(Alignment.Top)
            }
            .width('100%')
            .alignContent(Alignment.Top)

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
          const titleRevealOffset = this.useNativeSurface() ? 200 : 128
          if (y > titleRevealOffset) {
            this.ui.showActionBar = true
            this.ui.actionBarOpacity = Math.min((y - titleRevealOffset) / 72, 0.6)
          } else {
            this.ui.actionBarOpacity = 0
            this.ui.showActionBar = false
          }
        })
      }
      if (showLegacyActionBar) {
        this.feiniuActionBar()
      }
    }
    .backgroundColor(AppThemeSurfaceResolver.routeBackground(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable))
    .alignContent(Alignment.Top)
  }

  build() {
    AppRouteDestination({
      title: this.ui.showActionBar ? this.ui.title : '',
      contentExtendsUnderTitleBar: true,
      heroTitleChrome: true,
      scrollControllers: [this.ui.scroller],
      contentBuilder: () => {
        this.pageContent(false)
      },
      legacyContentBuilder: () => {
        this.pageContent(true)
      }
    })
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
