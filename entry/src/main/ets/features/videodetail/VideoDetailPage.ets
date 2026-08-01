import { HMRouter, HMRouterMgr } from '@hadss/hmrouter';
import { ImageKnife, ImageKnifeComponent } from '@ohos/imageknifepro';
import { ActionBar } from '../../component/ActionBar';
import { AppRouteDestination } from '../../component/AppRouteDestination';
import { VideoDetailUIState } from './VideoDetailUIState';
import { CalculateHeightParam, VideoDetailViewModel } from './VideoDetailViewModel';
import { LengthUnit } from '@ohos.arkui.node';
import { ExternalUrl, MediaSourceInfo, MediaStream, MediaStreamType } from 'sdk/src/main/ets/generated-client/models';
import { VideoItem } from '../home/hometab/HomeUIState';
import { VideoCard } from '../videolist/VideoListPage';
import { playItems } from '../../common/CommonFunc';
import { TextExpandView } from '../../component/textexpand/TextExpand';
import { LastSpanAttribute } from '../../component/textexpand/Model';
import { RouterConsts } from '../../common/RouterConsts';
import { ColorUtils } from '../../utils/ColorUtils';
import { ConfigurationConstant } from '@kit.AbilityKit';
import { CastView } from '../../component/CastView';
import { SkeletonDetailView } from '../../component/skeleton/SkeletonDetailView';
import { AudioSubtitleSelector } from './components/AudioSubtitleSelector';
import { VideoInfoDetail } from './components/VideoInfoDetail';
import { VideoDetailRefreshEvent } from '../../events/Events';
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
import { image } from '@kit.ImageKit';

@ComponentV2
struct NativeDetailPlayAction {
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
    .systemMaterial(AppThemeSurfaceResolver.material(AppThemeMaterialRole.InteractiveFloating))
    .accessibilityText(this.label)
    .onClick(() => {
      this.onAction()
    })
  }
}

@ComponentV2
struct NativeDetailIconAction {
  @Param icon: ResourceStr | undefined = undefined
  @Param symbol: Resource | undefined = undefined
  @Require @Param label: string
  @Require @Param accessibilityLabel: string
  @Require @Param selected: boolean
  @Require @Param onAction: () => void

  build() {
    Column({ space: 6 }) {
      Button({ type: ButtonType.Circle, stateEffect: false }) {
        if (this.symbol) {
          SymbolGlyph(this.symbol)
            .fontSize(23)
            .fontColor([
              this.selected ? $r('app.color.color_main') : $r('sys.color.icon_primary')
            ])
        } else if (this.icon) {
          Image(this.icon)
            .width(22)
            .height(22)
            .renderMode(ImageRenderMode.Template)
            .interpolation(ImageInterpolation.High)
            .fillColor(this.selected ? $r('app.color.color_main') : $r('sys.color.icon_primary'))
        }
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
    .width(72)
    .alignItems(HorizontalAlign.Center)
  }
}

@HMRouter({
  pageUrl: RouterConsts.VideoDetailPage,
  useNavDst: true
})
@ComponentV2
export struct VideoDetailPage {
  @Local ui: VideoDetailUIState = new VideoDetailUIState(this.getUIContext())
  @Local vm: VideoDetailViewModel = new VideoDetailViewModel(this.ui)
  @Local heroPixelMap?: image.PixelMap
  @Provider() selectedSubtitleIndex: number = 0
  @Provider() selectedAudioIndex: number = 0
  private heroTagColorFilter: ColorFilter = ColorUtils.hexToColorMatrix('#ffffffff')
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
      if (this.ui.image !== sourceUrl) {
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

  private secondarySurfaceBackground(): ResourceColor {
    return this.useNativeSurface() ?
      AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_2')
  }

  private detailInformationBackground(): ResourceColor {
    return this.useNativeSurface() ?
      $r('app.color.native_detail_information_background') : this.secondarySurfaceBackground()
  }

  private configureHeroHeight(): void {
    this.ui.imageBaseHeight = this.useNativeSurface() ?
      VideoDetailUIState.nativeImageInitHeight : VideoDetailUIState.imageInitHeight
    this.ui.imageHeight = this.ui.imageBaseHeight
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
      if (this.heroPixelMap && this.heroPixelMapSource === this.ui.image) {
        Image(this.heroPixelMap)
          .width('100%')
          .height('100%')
          .objectFit(ImageFit.Cover)
          .transition(TransitionEffect.OPACITY)
      } else {
        ImageKnifeComponent({
          imageKnifeOption: {
            loadSrc: this.ui.image,
            objectFit: ImageFit.Cover,
            onComplete: () => {
              this.prepareHeroPixelMap(this.ui.image)
            }
          }
        })
          .width('100%')
          .height('100%')
          .opacity(0)
          .hitTestBehavior(HitTestMode.None)
      }

      Stack()
        .width('100%')
        .height('100%')
        .linearGradient({
          angle: 0,
          colors: [
            ['rgba(0, 0, 0, 0)', 0],
            ['rgba(0, 0, 0, 0.08)', 0.24],
            ['rgba(0, 0, 0, 0.26)', 0.64],
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
          loadSrc: this.ui.image,
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
      if (this.ui.isMovie && this.ui.logo.length > 0) {
        ImageKnifeComponent({
          imageKnifeOption: {
            loadSrc: this.ui.logo,
            objectFit: ImageFit.Contain,
          }
        })
          .height(76)
          .aspectRatio(80 / 31)
      } else {
        if (this.ui.subtitle.length > 0) {
          Text(this.ui.subtitle)
            .fontSize(14)
            .fontColor(Color.White)
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
        }
        Text(this.ui.title)
          .fontSize(28)
          .fontWeight(FontWeight.Bold)
          .fontColor(Color.White)
          .maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
      }

      Text() {
        if (this.ui.score > 0) {
          Span(this.ui.score.toFixed(1) + '分  ')
            .fontColor($r('app.color.text_score'))
        }
        Span(this.ui.info)
      }
      .fontSize(13)
      .fontColor(Color.White)
      .maxLines(2)
      .textOverflow({ overflow: TextOverflow.Ellipsis })
      .lineSpacing({ value: 10, unit: LengthUnit.VP })

      if (!this.ui.isTvShows && this.ui.mediaSources.length > 0) {
        Row({ space: 14 }) {
          ForEach(this.vm.getTags(), (item: string) => {
            Image(item)
              .height(21)
              .colorFilter(this.heroTagColorFilter)
          })
        }
      }
    }
    .width('100%')
    .padding({ left: 16, right: 16, bottom: 8 })
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  private nativeDetailActions() {
    Column({ space: 14 }) {
      NativeDetailPlayAction({
        label: this.ui.playButtonText,
        onAction: () => {
          if (this.ui.isTvShows) {
            this.vm.playFirst()
          } else {
            let id = this.ui.mediaSources[this.ui.selectedMediaSourceIndex].Id || this.ui.id
            playItems([id], this.getUIContext().getHostContext()!, this.ui.selectedAudio?.Index,
              this.ui.selectedSubtitle?.Index)
          }
        }
      })
        .width('100%')

      Row({ space: 16 }) {
        NativeDetailIconAction({
          icon: this.ui.isFavorite ? $r('app.media.ic_like') : $r('app.media.ic_unlike'),
          label: this.ui.isFavorite ? '已收藏' : '收藏',
          accessibilityLabel: this.ui.isFavorite ? '取消收藏' : '收藏',
          selected: this.ui.isFavorite,
          onAction: () => {
            this.vm.changeFavorite()
          }
        })
        if (this.ui.isTvShows) {
          NativeDetailIconAction({
            symbol: this.ui.isChasing ? $r('sys.symbol.list_bullet_square_fill') :
              $r('sys.symbol.list_bullet_square'),
            label: this.ui.isChasing ? '已追剧' : '追剧',
            accessibilityLabel: this.ui.isChasing ? '取消追剧' : '追剧',
            selected: this.ui.isChasing,
            onAction: () => {
              this.vm.changeChasingState()
            }
          })
        }
        NativeDetailIconAction({
          icon: $r('app.media.ic_check'),
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
          Text(this.ui.playButtonText)
            .fontSize(16)
            .fontColor(Color.White)
            .fontWeight(FontWeight.Medium)
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
        }
        .padding({ left: 18, right: 18 })
        .width('100%')
        .justifyContent(FlexAlign.Center)
      }
      .height(50)
      .layoutWeight(1)
      .margin({ right: 12 })
      .borderRadius(25)
      .onClick(() => {
        if (this.ui.isTvShows) {
          this.vm.playFirst()
        } else {
          let id = this.ui.mediaSources[this.ui.selectedMediaSourceIndex].Id || this.ui.id
          playItems([id], this.getUIContext().getHostContext()!, this.ui.selectedAudio?.Index,
            this.ui.selectedSubtitle?.Index)
        }
      })

      Button() {
        Image(this.ui.isFavorite ? $r('app.media.ic_like') : $r('app.media.ic_unlike'))
          .fillColor($r('app.color.text_1'))
          .width(this.ui.isTvShows ? 20 : 22)
          .height(this.ui.isTvShows ? 20 : 22)
      }
      .width(this.ui.isTvShows ? 44 : 50)
      .height(this.ui.isTvShows ? 44 : 50)
      .borderWidth(AppThemeSurfaceResolver.outlineWidth(
        this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable, 1))
      .borderColor(AppThemeSurfaceResolver.contentOutline(
        this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable))
      .borderRadius(this.ui.isTvShows ? 22 : 25)
      .backgroundColor(this.secondarySurfaceBackground())
      .onClick(() => {
        this.vm.changeFavorite()
      })

      if (this.ui.isTvShows) {
        Button() {
          Image(this.ui.isChasing ? $r('app.media.history') : $r('app.media.add'))
            .fillColor(this.ui.isChasing ? $r('app.color.color_main') : $r('app.color.text_1'))
            .width(20)
            .height(20)
        }
        .width(44)
        .height(44)
        .borderWidth(AppThemeSurfaceResolver.outlineWidth(
          this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable, 1))
        .borderColor(this.ui.isChasing ? $r('app.color.color_main') :
          AppThemeSurfaceResolver.contentOutline(
            this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable))
        .borderRadius(22)
        .backgroundColor(this.secondarySurfaceBackground())
        .onClick(() => {
          this.vm.changeChasingState()
        })
      }

      Button() {
        Image($r('app.media.ic_check'))
          .fillColor(this.ui.played ? $r('app.color.color_main') : $r('app.color.text_1'))
          .width(this.ui.isTvShows ? 20 : 22)
          .height(this.ui.isTvShows ? 20 : 22)
      }
      .width(this.ui.isTvShows ? 44 : 50)
      .height(this.ui.isTvShows ? 44 : 50)
      .borderWidth(AppThemeSurfaceResolver.outlineWidth(
        this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable, 1))
      .borderColor(AppThemeSurfaceResolver.contentOutline(
        this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable))
      .borderRadius(this.ui.isTvShows ? 22 : 25)
      .backgroundColor(this.secondarySurfaceBackground())
      .onClick(() => {
        this.vm.changePlayedState()
      })
    }
    .width('100%')
    .padding({ left: 16, right: 16 })
  }

  @Builder
  private feiniuHeroBackdrop() {
    Column() {
      ImageKnifeComponent({
        imageKnifeOption: {
          loadSrc: this.ui.image,
          objectFit: ImageFit.Cover
        }
      }).width('100%')
        .height('100%')
        .blur(this.ui.blur)
    }
    .width('100%')
    .height('100%')
  }

  @Builder
  private feiniuActionBar() {
    ActionBar({
      title: this.ui.showActionBar ? this.ui.title : ''
    })
      .backgroundBlurStyle(this.ui.showActionBar ? BlurStyle.BACKGROUND_THICK : BlurStyle.NONE)
  }

  @Monitor('vm.appUIState.colorMode')
  onColorModeChange(): void {
    let color =
      this.vm.appUIState.colorMode === ConfigurationConstant.ColorMode.COLOR_MODE_DARK ? '#ffffffff' :
        '#ff000000';
    this.ui.colorFilter = ColorUtils.hexToColorMatrix(color);
  }

  private refreshDetail(): void {
    if (!this.ui.id) {
      return
    }
    if (this.ui.isTvShows) {
      this.vm.getTvShowDetail(this.ui.id)
      this.vm.getNextUp()
    } else {
      this.vm.getVideoDetail(this.ui.id)
    }
  }

  aboutToAppear(): void {
    this.configureHeroHeight()
    let param = HMRouterMgr.getCurrentParam() as object
    if (param) {
      this.ui.id = param['id'] as string
      this.ui.mediaName = param['mediaName'] as string
      this.ui.isTvShows = param['isTvShows'] ?? false
      this.refreshDetail()
    }
    this.getUIContext().getHostContext()?.eventHub.on(VideoDetailRefreshEvent, () => {
      setTimeout(() => {
        this.refreshDetail()
      }, 1000)
    })
    this.onColorModeChange()
  }

  aboutToDisappear(): void {
    this.getUIContext().getHostContext()?.eventHub.off(VideoDetailRefreshEvent)
    if (this.heroPixelMap) {
      this.heroPixelMap.release()
      this.heroPixelMap = undefined
      this.heroPixelMapSource = ''
    }
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
      .padding({ left: 16, right: 16 })
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
      .padding({ left: 16, right: 16 })
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
      .padding({ left: 16, right: 16 })
      .bindSheet($$this.ui.isShowOverviewSheet, this.overviewSheetBuilder(), {
        height: 420,
        preferType: SheetType.BOTTOM,
        title: { title: '简介' },
        systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
      })
    }
  }

  @Builder
  private videoInfoTriggerContent() {
    Text('查看全部')
      .fontColor($r('app.color.text_1'))
      .fontSize(12)
      .fontWeight(FontWeight.Medium)
      .layoutWeight(1)
    SymbolGlyph($r('sys.symbol.chevron_right'))
      .fontSize(16)
      .fontColor([$r('app.color.text_1')])
      .fontWeight(FontWeight.Bold)
  }

  @Builder
  private videoInfoSheetTrigger() {
    if (deviceInfo.sdkApiVersion < 26) {
      Row({ space: 10 }) {
        this.videoInfoTriggerContent()
      }
      .width('100%')
      .padding({ top: 4, left: 4, bottom: 8 })
      .onClick(() => {
        this.ui.isShowVideoInfoSheet = true
      })
      .bindSheet($$this.ui.isShowVideoInfoSheet, this.videoInfoSheetBuilder(), {
        height: '70%',
        preferType: SheetType.BOTTOM,
        title: { title: '文件媒体信息' },
      })
    } else if (this.sheetMaterialDecision() === OverlayMaterialDecision.UseFloatingMaterial) {
      Row({ space: 10 }) {
        this.videoInfoTriggerContent()
      }
      .width('100%')
      .padding({ top: 4, left: 4, bottom: 8 })
      .onClick(() => {
        this.ui.isShowVideoInfoSheet = true
      })
      .bindSheet($$this.ui.isShowVideoInfoSheet, this.videoInfoSheetBuilder(), {
        height: '70%',
        preferType: SheetType.BOTTOM,
        title: { title: '文件媒体信息' },
        backgroundColor: Color.Transparent,
        systemMaterial: AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating)
      })
    } else {
      Row({ space: 10 }) {
        this.videoInfoTriggerContent()
      }
      .width('100%')
      .padding({ top: 4, left: 4, bottom: 8 })
      .onClick(() => {
        this.ui.isShowVideoInfoSheet = true
      })
      .bindSheet($$this.ui.isShowVideoInfoSheet, this.videoInfoSheetBuilder(), {
        height: '70%',
        preferType: SheetType.BOTTOM,
        title: { title: '文件媒体信息' },
        systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
      })
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
              } else {
                this.feiniuHeroBackdrop()
              }

              if (!this.useNativeSurface()) {
                Stack().width('100%')
                  .height('100%')
                  .linearGradient({
                    angle: 0,
                    colors: [
                      ['rgba(26, 30, 35, 0.8)', 0],
                      ['rgba(26, 30, 35, 0.6)', 0.3],
                      ['rgba(26, 30, 35, 0.2)', 0.4],
                      ['rgba(26, 30, 35, 0.1)', 1]
                    ]
                  })
              }

              if (this.useNativeSurface()) {
                this.nativeHeroMetadata()
              } else if (this.ui.isMovie) {
                ImageKnifeComponent({
                  imageKnifeOption: {
                    loadSrc: this.ui.logo,
                    objectFit: ImageFit.Contain,
                  }
                })
                  .height(80)
                  .aspectRatio(80 / 31) // 刮削的logo都是800x310
                  .margin({ bottom: 10, left: 16 })
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
                }
                .padding(16)
                .alignItems(HorizontalAlign.Start)
              }
            }.width('100%')
            .height(this.ui.imageHeight)
            .alignContent(Alignment.BottomStart)

            Stack() {
              Column() {
              if (!this.useNativeSurface()) {
                Text() {
                  if (!this.ui.isTvShows && this.ui.score > 0) {
                    Span(this.ui.score + '分  ').fontColor($r('app.color.text_score'))
                  }
                  Span(this.ui.info)
                }
                .fontSize(12)
                .fontColor($r('app.color.text_1'))
                .lineSpacing({ value: 6, unit: LengthUnit.VP })
                .width('100%')
                .textAlign(TextAlign.Start)
                .padding({ left: 16, right: 16 })
              }

              if (!this.ui.isTvShows) {
                if (!this.useNativeSurface()) {
                  Blank().height(10)
                  Row({ space: 16 }) {
                    ForEach(this.vm.getTags(), (item: string) => {
                      Image(item)
                        .height(22)
                        .colorFilter(this.ui.colorFilter)
                    })
                  }.padding({ left: 16, right: 16 })
                }

                Blank().height(10)
                //todo 字幕音频切换
                Row({ space: 16 }) {
                  AudioSubtitleSelector({
                    source: this.ui.mediaSources[this.ui.selectedMediaSourceIndex],
                    triggerTextColor: this.useNativeSurface() ? Color.White : $r('app.color.text_primary'),
                    onSelectSubtitle: (subtitle) => {
                      this.ui.selectedSubtitle = subtitle
                    },
                    onSelectAudio: (audio) => {
                      this.ui.selectedAudio = audio
                    }
                  })
                }.padding({ left: 16, right: 16 })
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
                  Text(this.ui.remainingTime).fontColor($r('app.color.text_1')).fontSize(12)
                }.width('100%').justifyContent(FlexAlign.Start).padding({ left: 16, right: 16 })
              }
              Blank().height(16)
              if (this.useNativeSurface()) {
                this.nativeDetailActions()
              } else {
                this.legacyDetailActions()
              }


              // TextExpandView({
              //   textSectionAttribute: this.textSectionAttribute,
              //   lastSpanAttribute: new LastSpanAttribute(0, 2,
              //     this.handleContent(), 16,
              //     $r('app.color.text_expand_selected_tab_name'))
              // }).id(`textClick${this.index}`)
              if (this.ui.mediaSources.length > 1) {
                Blank().height(16)
                List({ space: 8 }) {
                  ForEach(this.ui.mediaSources, (item: MediaSourceInfo, index: number) => {
                    ListItem() {
                      Text(item.Name)
                        .fontSize(14)
                        .fontColor(index === this.ui.selectedMediaSourceIndex ? $r('app.color.color_main') :
                          $r('app.color.text_1'))
                        .padding({
                          left: 12,
                          right: 12
                        })
                        .height(32)
                        .borderWidth(AppThemeSurfaceResolver.outlineWidth(
                          this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable, 1))
                        .borderColor(index === this.ui.selectedMediaSourceIndex ? $r('app.color.color_main') :
                          AppThemeSurfaceResolver.contentOutline(
                            this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable))
                        .borderRadius(4)
                        .onClick(() => {
                          this.ui.selectedMediaSourceIndex = index
                          this.vm.getFileInfo()
                          this.vm.getVideoInfo()
                        })
                    }
                  })
                }.listDirection(Axis.Horizontal)
                .width('100%')
                .height(32)
                .padding({ left: 16, right: 16 })
              }
              if (this.ui.overview.length > 0) {
                Blank().height(16)
                this.overviewSheetTrigger()
              }

              if (this.ui.isTvShows) {
                if (this.ui.seasons.length > 1) {
                  Blank().height(20)
                  Text(`共${this.ui.seasons.length}季`)
                    .fontSize(15)
                    .fontWeight(FontWeight.Medium)
                    .fontColor($r('app.color.text_primary')).padding({ left: 16, right: 16 })
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
                }
                .columnsTemplate('1fr 1fr 1fr')
                .columnsGap(8)
                .rowsGap(20)
                .width('100%')
                .padding({ left: 16, right: 16 })
              }
              if (this.ui.people.length > 0) {
                Blank().height(16)
                CastView({
                  peopleList: this.ui.people
                })
              }
              if (!this.ui.isTvShows) {
                Column() {
                  Blank().height(16)

                  Text('文件信息').fontSize(15).fontWeight(FontWeight.Medium).fontColor($r('app.color.text_primary'))

                  Column() {
                    Text('文件位置').fontSize(12).fontColor($r('app.color.text_2'))
                    Blank().height(10)
                    Text(this.ui.path).fontSize(12).fontColor($r('app.color.text_1'))
                    Blank().height(16)
                    Text('文件大小').fontSize(12).fontColor($r('app.color.text_2'))
                    Blank().height(10)
                    Text(this.ui.size).fontSize(12).fontColor($r('app.color.text_1'))
                    //这个不是每个文件的,不准
                    // Blank().height(16)
                    // Text('创建日期').fontSize(12).fontColor($r('app.color.text_2'))
                    // Blank().height(10)
                    // Text(this.ui.createDate).fontSize(12).fontColor($r('app.color.text_1'))
                  }
                  .width('100%')
                  .backgroundColor(this.detailInformationBackground())
                  .borderRadius(12)
                  .padding(12)
                  .alignItems(HorizontalAlign.Start)
                  .margin({ top: 12 })

                  Blank().height(16)

                  Text('视频信息').fontSize(15).fontWeight(FontWeight.Medium).fontColor($r('app.color.text_primary'))

                  Column({ space: 16 }) {
                    if (this.ui.videoInfo.length > 0) {
                      Column({ space: 10 }) {
                        Text('视频').fontSize(12).fontColor($r('app.color.text_2'))
                        Text(this.ui.videoInfo)
                          .fontSize(12)
                          .fontColor($r('app.color.text_1'))
                          .lineSpacing({ value: 4, unit: LengthUnit.VP })
                      }.width('100%')
                      .alignItems(HorizontalAlign.Start)
                    }
                    if (this.ui.audioInfo.length > 0) {
                      Column({ space: 10 }) {
                        Text('音频').fontSize(12).fontColor($r('app.color.text_2'))
                        Text(this.ui.audioInfo)
                          .fontSize(12)
                          .fontColor($r('app.color.text_1'))
                          .lineSpacing({ value: 4, unit: LengthUnit.VP })
                      }.width('100%')
                      .alignItems(HorizontalAlign.Start)
                    }
                    if (this.ui.subtitleInfo.length > 0) {
                      Column({ space: 10 }) {
                        Text('字幕').fontSize(12).fontColor($r('app.color.text_2'))
                        Text(this.ui.subtitleInfo)
                          .fontSize(12)
                          .fontColor($r('app.color.text_1'))
                          .lineSpacing({ value: 4, unit: LengthUnit.VP })
                      }.width('100%')
                      .alignItems(HorizontalAlign.Start)
                    }

                    Divider().height(0.5).color(AppThemeSurfaceResolver.contentDivider(
                      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable))

                    this.videoInfoSheetTrigger()
                  }
                  .width('100%')
                  .backgroundColor(this.detailInformationBackground())
                  .borderRadius(12)
                  .padding(12)
                  .margin({ top: 12 })
                }.padding({ left: 16, right: 16 })
                .alignItems(HorizontalAlign.Start)
              }

              if (this.ui.externalUrls.length) {
                Blank().height(16)
                Text('链接')
                  .fontSize(15)
                  .fontWeight(FontWeight.Medium)
                  .fontColor($r('app.color.text_primary'))
                  .padding({ left: 16, right: 16 })
                Row({ space: 6 }) {
                  ForEach(this.ui.externalUrls, (item: ExternalUrl) => {
                    Text(item.Name)
                      .fontSize(12)
                      .fontColor($r('app.color.text_1'))
                      .backgroundColor(this.detailInformationBackground())
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
                }.margin({ top: 12 }).padding({ left: 16, right: 16 })
              }
              Blank().height(30 + this.vm.appUIState.safeBottom)

              }.alignItems(HorizontalAlign.Start).padding({
                top: this.useNativeSurface() ? 0 : 12
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
      }

      if (showLegacyActionBar) {
        this.feiniuActionBar()
      }
    }
    .backgroundColor(this.useNativeSurface() ? Color.Transparent : AppThemeSurfaceResolver.routeBackground(
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

  @Builder
  videoInfoSheetBuilder() {
    Scroll() {
      VideoInfoDetail({
        source: this.ui.mediaSources[this.ui.selectedMediaSourceIndex]
      })
    }
    .scrollBar(BarState.Off)
    .scrollable(ScrollDirection.Vertical)
    .edgeEffect(EdgeEffect.None)
    .padding({
      left: 16,
      right: 16,
      bottom: 10 + this.vm.appUIState.safeBottom
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
