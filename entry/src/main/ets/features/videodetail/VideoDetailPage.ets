import { HMRouter, HMRouterMgr } from '@hadss/hmrouter';
import { CacheStrategy, ImageKnife, ImageKnifeComponent, ImageKnifeOption } from '@ohos/imageknifepro';
import { ActionBar } from '../../component/ActionBar';
import { AppRouteDestination } from '../../component/AppRouteDestination';
import { VideoDetailUIState } from './VideoDetailUIState';
import { CalculateHeightParam, VideoDetailViewModel } from './VideoDetailViewModel';
import { LengthUnit } from '@ohos.arkui.node';
import { VideoItem } from '../home/hometab/HomeUIState';
import { VideoCard } from '../videolist/VideoListPage';
import { TextExpandView } from '../../component/textexpand/TextExpand';
import { LastSpanAttribute } from '../../component/textexpand/Model';
import { RouterConsts } from '../../common/RouterConsts';
import { ColorUtils } from '../../utils/ColorUtils';
import { ConfigurationConstant } from '@kit.AbilityKit';
import { CastView } from '../../component/CastView';
import { SkeletonDetailView } from '../../component/skeleton/SkeletonDetailView';
import { AudioSubtitleSelector } from './components/AudioSubtitleSelector';
import { MediaSourceSelector } from './components/MediaSourceSelector';
import { VideoInfoDetail } from './components/VideoInfoDetail';
import { VideoDetailRefreshEvent } from '../../events/Events';
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
import { hilog } from '@kit.PerformanceAnalysisKit'
import { ManagementMediaDeleteDialog } from '../management/library/ManagementMediaDeleteDialog'
import {
  ManagementLibraryOperationCode,
  ManagementLibraryOperationResult
} from '../management/library/ManagementLibraryModels'
import { currentMediaCatalogProvider } from '../../media/MediaCatalogAccess'
import {
  MediaExternalLink,
  MediaImageRef
} from '../../media/catalog/MediaCatalogModels'
import { createMediaImageKnifeOption } from '../../media/catalog/MediaImageKnifeAdapter'
import { MediaRef, MediaRefKind } from '../../media/MediaRef'
import {
  MediaRouteRefData,
  parseMediaRouteRefData,
  toMediaRouteRefData
} from '../../media/navigation/MediaRouteRef'

const VIDEO_HERO_LOG_DOMAIN: number = 0xFF00
const VIDEO_HERO_LOG_TAG: string = 'mafei-video-hero'
const HERO_TITLE_FONT_FAMILY: string = 'MaFei Hero Calligraphy'
let heroTitleFontRegistered: boolean = false

@ComponentV2
struct NativeDetailPlayAction {
  @Require @Param label: string
  @Require @Param remainingTime: string
  @Require @Param progress: number
  @Require @Param systemMaterialAvailable: boolean
  @Require @Param onAction: () => void

  private normalizedProgress(): number {
    return Math.min(100, Math.max(0, this.progress))
  }

  private hasPlaybackProgress(): boolean {
    const progress = this.normalizedProgress()
    return this.remainingTime.length > 0 && progress > 0 && progress < 100
  }

  private compactRemainingTime(): string {
    return this.remainingTime
      .replace('剩余 ', '剩余')
      .replace(' 小时 ', '小时')
      .replace(' 分钟', '分')
  }

  build() {
    Button({ type: ButtonType.Normal, stateEffect: false }) {
      Stack({ alignContent: Alignment.Start }) {
        if (this.hasPlaybackProgress()) {
          Stack() {
            Row()
              .width('100%')
              .height('100%')
              .backgroundColor('rgba(255, 255, 255, 0.26)')

            Particle({
              particles: [
                {
                  emitter: {
                    particle: {
                      type: ParticleType.POINT,
                      config: { radius: 1.1 },
                      count: -1,
                      lifetime: 1600,
                      lifetimeRange: 400
                    },
                    emitRate: 8,
                    shape: ParticleEmitterShape.RECTANGLE
                  },
                  color: {
                    range: ['rgba(255, 255, 255, 0.45)', 'rgba(255, 255, 255, 0.92)']
                  },
                  opacity: {
                    range: [0.3, 0.78]
                  },
                  scale: {
                    range: [0.65, 1.15]
                  },
                  velocity: {
                    speed: [3, 9],
                    angle: [-10, 10]
                  }
                }
              ]
            })
              .width('100%')
              .height('100%')
              .hitTestBehavior(HitTestMode.None)
          }
          .width(`${this.normalizedProgress()}%`)
          .height('100%')
          .clip(true)
          .hitTestBehavior(HitTestMode.None)
        }

        Row({ space: 8 }) {
          SymbolGlyph($r('sys.symbol.play_fill'))
            .fontSize(20)
            .fontColor([$r('sys.color.icon_primary')])
          Column({ space: 1 }) {
            Text(this.label)
              .fontSize(16)
              .fontColor($r('sys.color.font_primary'))
              .fontWeight(FontWeight.Medium)
            if (this.remainingTime.length > 0) {
              Text(this.compactRemainingTime())
                .fontSize(12)
                .fontColor($r('sys.color.font_secondary'))
                .maxLines(1)
                .textOverflow({ overflow: TextOverflow.Ellipsis })
            }
          }
          .alignItems(HorizontalAlign.Center)
        }
        .padding({ left: 12, right: 12 })
        .width('100%')
        .justifyContent(FlexAlign.Center)
      }
      .width('100%')
      .height('100%')
    }
    .height(50)
    .padding(0)
    .borderRadius(25)
    .clip(true)
    .buttonStyle(ButtonStyleMode.TEXTUAL)
    .backgroundColor(Color.Transparent)
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.InteractiveFloating, true, this.systemMaterialAvailable))
    .accessibilityText(this.remainingTime.length > 0 ? `${this.label} ${this.remainingTime}` : this.label)
    .onClick(() => {
      this.onAction()
    })
  }
}

@ComponentV2
struct NativeDetailIconAction {
  @Param icon: ResourceStr | undefined = undefined
  @Param symbol: Resource | undefined = undefined
  @Param buttonSize: number = 50
  @Param selectedColor: ResourceColor = $r('app.color.color_main')
  @Param preserveOriginalColor: boolean = false
  @Require @Param accessibilityLabel: string
  @Require @Param selected: boolean
  @Require @Param systemMaterialAvailable: boolean
  @Require @Param onAction: () => void

  build() {
    Button({ type: ButtonType.Circle, stateEffect: false }) {
      if (this.symbol) {
        SymbolGlyph(this.symbol)
          .fontSize(23)
          .fontColor([
            this.selected ? this.selectedColor : $r('sys.color.icon_primary')
          ])
      } else if (this.icon && this.preserveOriginalColor) {
        Image(this.icon)
          .width(22)
          .height(22)
          .interpolation(ImageInterpolation.High)
      } else if (this.icon) {
        Image(this.icon)
          .width(22)
          .height(22)
          .renderMode(ImageRenderMode.Template)
          .interpolation(ImageInterpolation.High)
          .fillColor(this.selected ? this.selectedColor : $r('sys.color.icon_primary'))
      }
    }
    .width(this.buttonSize)
    .height(this.buttonSize)
    .buttonStyle(ButtonStyleMode.TEXTUAL)
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.InteractiveFloating, true, this.systemMaterialAvailable))
    .accessibilityText(this.accessibilityLabel)
    .onClick(() => {
      this.onAction()
    })
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
  @Local heroBackdropPixelMap?: image.PixelMap
  @Local heroFeatheredPixelMap?: image.PixelMap
  @Local titleCollapseProgress: number = 0
  @Local collapsedTitleGlobalX: number = 0
  @Local collapsedTitleCenterX: number = 0
  private heroTagColorFilter: ColorFilter = ColorUtils.hexToColorMatrix('#ffffffff')
  private heroBackdropKey: string = ''
  private heroBackdropLoadingKey: string = ''
  private heroBackdropGeneration: number = 0
  private heroBackdropActive: boolean = false

  private heroRequestSummary(): string {
    const imageRef = this.ui.imageRef
    return `provider=${imageRef?.provider || 'none'} ref=${imageRef ? 1 : 0} ` +
      `url=${imageRef?.url.length || this.ui.image.length} headers=${imageRef?.headers.length || 0} ` +
      `cacheKey=${imageRef?.cacheKey.length || 0} active=${this.heroBackdropActive ? 1 : 0}`
  }

  private safeHeroError(error: string): string {
    return error.replace(/https?:\/\/[^\s]+/g, '<redacted-url>').substring(0, 320)
  }

  private logHeroInfo(stage: string, detail: string = ''): void {
    hilog.info(VIDEO_HERO_LOG_DOMAIN, VIDEO_HERO_LOG_TAG,
      '%{public}s', `${stage} ${this.heroRequestSummary()} ${detail}`.trim())
  }

  private logHeroWarn(stage: string, detail: string = ''): void {
    hilog.warn(VIDEO_HERO_LOG_DOMAIN, VIDEO_HERO_LOG_TAG,
      '%{public}s', `${stage} ${this.heroRequestSummary()} ${detail}`.trim())
  }

  private logHeroError(stage: string, detail: string = ''): void {
    hilog.error(VIDEO_HERO_LOG_DOMAIN, VIDEO_HERO_LOG_TAG,
      '%{public}s', `${stage} ${this.heroRequestSummary()} ${detail}`.trim())
  }

  private currentHeroBackdropKey(): string {
    return this.ui.imageRef?.cacheKey || this.ui.image
  }

  private async createFeatheredHeroPixelMap(sourcePixelMap: image.PixelMap): Promise<image.PixelMap | undefined> {
    const imageInfo = await sourcePixelMap.getImageInfo()
    const pixelFormat = imageInfo.pixelFormat
    const sourceAlphaType = imageInfo.alphaType
    if (pixelFormat !== image.PixelMapFormat.RGBA_8888 &&
      pixelFormat !== image.PixelMapFormat.BGRA_8888 &&
      pixelFormat !== image.PixelMapFormat.ARGB_8888) {
      this.logHeroWarn('backdrop.feather.unsupported', `format=${pixelFormat}`)
      return undefined
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

    this.logHeroInfo('backdrop.feather.complete', `size=${width}x${height} fadeStart=${fadeStart}`)
    return image.createPixelMap(compactBuffer, {
      size: { width: width, height: height },
      srcPixelFormat: pixelFormat,
      pixelFormat: pixelFormat,
      alphaType: image.AlphaType.PREMUL,
      editable: false
    })
  }

  private async prepareHeroBackdrop(): Promise<void> {
    const key = this.currentHeroBackdropKey()
    this.logHeroInfo('backdrop.prepare.enter',
      `key=${key.length} ready=${key === this.heroBackdropKey ? 1 : 0} ` +
      `loading=${key === this.heroBackdropLoadingKey ? 1 : 0}`)
    if (key.length === 0) {
      this.logHeroWarn('backdrop.prepare.skip', 'reason=empty-key')
      return
    }
    if (key === this.heroBackdropKey) {
      this.logHeroInfo('backdrop.prepare.skip', 'reason=already-ready')
      return
    }
    if (key === this.heroBackdropLoadingKey) {
      this.logHeroInfo('backdrop.prepare.skip', 'reason=already-loading')
      return
    }
    const generation = ++this.heroBackdropGeneration
    this.heroBackdropLoadingKey = key
    this.logHeroInfo('backdrop.cache.start', `generation=${generation}`)
    let nextPixelMap: image.PixelMap | undefined = undefined
    let nextFeatheredPixelMap: image.PixelMap | undefined = undefined
    try {
      // ImageKnife's system Image completion callback can run just before its file-cache write is visible.
      // Wait for the current load callback stack to settle before reading the same signed request from cache.
      await new Promise<void>((resolve: () => void) => {
        setTimeout(resolve, 80)
      })
      if (!this.heroBackdropActive || generation !== this.heroBackdropGeneration ||
        key !== this.currentHeroBackdropKey()) {
        this.logHeroWarn('backdrop.cache.cancel-before-read',
          `generation=${generation} currentGeneration=${this.heroBackdropGeneration} ` +
          `keyMatch=${key === this.currentHeroBackdropKey() ? 1 : 0}`)
        return
      }
      let imageData = await ImageKnife.getInstance().getCacheImage(this.heroImageOption(), CacheStrategy.DEFAULT)
      this.logHeroInfo('backdrop.cache.complete',
        `generation=${generation} data=${imageData ? 1 : 0} ` +
        `pixelmaps=${imageData?.pixelmapList?.length || 0}`)
      nextPixelMap = imageData?.pixelmapList?.[0]
      if (!nextPixelMap) {
        this.logHeroWarn('backdrop.cache.empty', `generation=${generation} fallback=direct-load`)
        imageData = await ImageKnife.getInstance().getCacheImage(this.heroImageOption(), CacheStrategy.NONE)
        this.logHeroInfo('backdrop.direct.complete',
          `generation=${generation} data=${imageData ? 1 : 0} ` +
          `pixelmaps=${imageData?.pixelmapList?.length || 0}`)
        nextPixelMap = imageData?.pixelmapList?.[0]
        if (!nextPixelMap) {
          this.logHeroError('backdrop.direct.empty', `generation=${generation}`)
          return
        }
      }
      nextFeatheredPixelMap = await this.createFeatheredHeroPixelMap(nextPixelMap)
      if (!nextFeatheredPixelMap) {
        this.logHeroError('backdrop.feather.empty', `generation=${generation}`)
        nextPixelMap.release()
        nextPixelMap = undefined
        return
      }
      if (!this.heroBackdropActive || generation !== this.heroBackdropGeneration ||
        key !== this.currentHeroBackdropKey()) {
        this.logHeroWarn('backdrop.cache.discard',
          `generation=${generation} currentGeneration=${this.heroBackdropGeneration} ` +
          `keyMatch=${key === this.currentHeroBackdropKey() ? 1 : 0}`)
        nextPixelMap.release()
        nextFeatheredPixelMap.release()
        nextPixelMap = undefined
        nextFeatheredPixelMap = undefined
        return
      }
      const previousPixelMap = this.heroBackdropPixelMap
      const previousFeatheredPixelMap = this.heroFeatheredPixelMap
      this.heroBackdropKey = key
      this.heroBackdropPixelMap = nextPixelMap
      this.getUIContext().animateTo({ duration: 180, curve: Curve.FastOutSlowIn }, () => {
        this.heroFeatheredPixelMap = nextFeatheredPixelMap
      })
      nextPixelMap = undefined
      nextFeatheredPixelMap = undefined
      if (previousPixelMap) previousPixelMap.release()
      if (previousFeatheredPixelMap) previousFeatheredPixelMap.release()
      this.logHeroInfo('backdrop.render.ready', `generation=${generation}`)
    } catch (error) {
      this.logHeroError('backdrop.cache.error',
        `generation=${generation} type=${typeof error}`)
      if (nextPixelMap) nextPixelMap.release()
      if (nextFeatheredPixelMap) nextFeatheredPixelMap.release()
    } finally {
      if (this.heroBackdropLoadingKey === key) this.heroBackdropLoadingKey = ''
      this.logHeroInfo('backdrop.cache.finish', `generation=${generation}`)
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

  private hasFileInformation(): boolean {
    return this.ui.path.trim().length > 0 || this.ui.size.trim().length > 0 ||
      this.ui.createDate.trim().length > 0 || this.ui.addedDate.trim().length > 0
  }

  private hasSelectedMediaStreams(): boolean {
    return this.ui.mediaInfoSections.length > 0
  }

  private hasVideoInformation(): boolean {
    return this.ui.videoInfo.trim().length > 0 ||
      this.ui.audioInfo.trim().length > 0 ||
      this.ui.subtitleInfo.trim().length > 0 ||
      this.hasSelectedMediaStreams()
  }

  private getString(name: string): string {
    return this.getUIContext().getHostContext()?.resourceManager.getStringByNameSync(name) || name
  }

  private localizedDeleteMessage(result: ManagementLibraryOperationResult): string {
    if (result.message.length > 0 && result.message.startsWith('management_')) {
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

  private openDeleteDialog(): void {
    if (!this.ui.isAdministrator || !this.ui.canDelete || this.ui.deleting) {
      return
    }
    ManagementMediaDeleteDialog.show(this.getUIContext(), this.vm.deleteSummary(), () => {
      this.vm.deleteCurrentMedia().then((result: ManagementLibraryOperationResult) => {
        if (result.ok) {
          this.getUIContext().getPromptAction().showToast({
            message: $r('app.string.management_media_delete_success')
          })
          HMRouterMgr.pop()
        } else {
          this.getUIContext().getPromptAction().showToast({
            message: this.localizedDeleteMessage(result)
          })
        }
      }).catch(() => {
        this.getUIContext().getPromptAction().showToast({
          message: $r('app.string.management_media_delete_failed')
        })
      })
    })
  }

  private openDeleteActionMenu(): void {
    if (!this.ui.isAdministrator || !this.ui.canDelete || this.ui.deleting) {
      return
    }
    this.getUIContext().getPromptAction().showActionMenu({
      buttons: [{
        text: $r('app.string.management_media_delete'),
        color: $r('sys.color.font_primary')
      }]
    }).then((result) => {
      if (result.index === 0) {
        this.openDeleteDialog()
      }
    }).catch(() => {
    })
  }

  private navigationMenus(): Array<NavigationMenuItem> {
    if (!this.useNativeSurface() || !this.ui.isAdministrator || !this.ui.canDelete) {
      return []
    }
    return [{
      value: $r('app.string.management_media_delete'),
      icon: $r('sys.symbol.more'),
      isEnabled: !this.ui.deleting,
      action: () => {
        this.openDeleteActionMenu()
      }
    }]
  }

  private configureHeroHeight(): void {
    this.vm.initializeHeroHeight(this.useNativeSurface() ?
      VideoDetailUIState.nativeImageInitHeight : VideoDetailUIState.imageInitHeight)
  }

  private imageOption(imageRef: MediaImageRef | undefined, fallback: string,
    objectFit: ImageFit = ImageFit.Cover): ImageKnifeOption {
    const option = createMediaImageKnifeOption(imageRef, fallback)
    option.objectFit = objectFit
    return option
  }

  private heroImageOption(onComplete?: () => void): ImageKnifeOption {
    const option = this.imageOption(this.ui.imageRef, this.ui.image)
    if (onComplete) {
      option.onLoadListener = {
        onLoadStart: () => {
          this.logHeroInfo('hero.load.start')
        },
        onLoadSuccess: (imageInfo) => {
          this.logHeroInfo('hero.load.success',
            `source=${imageInfo.imageSourceFrom} http=${imageInfo.httpCode || 0} ` +
            `size=${imageInfo.imageWidth}x${imageInfo.imageHeight}`)
        },
        onLoadFailed: (error: string, imageInfo) => {
          this.logHeroError('hero.load.failed',
            `http=${imageInfo.httpCode || 0} error=${this.safeHeroError(error)}`)
        },
        onLoadCancel: (reason: string) => {
          this.logHeroWarn('hero.load.cancel', `reason=${this.safeHeroError(reason)}`)
        },
        onMemorySuccess: () => {
          this.logHeroInfo('hero.load.memory-hit')
        },
        onDownloadSuccess: () => {
          this.logHeroInfo('hero.load.download-success')
        },
        onDecodeSuccess: () => {
          this.logHeroInfo('hero.load.decode-success')
        }
      }
      option.onComplete = (event) => {
        this.logHeroInfo('hero.image.complete', event ?
          `status=${event.loadingStatus} image=${event.width}x${event.height} ` +
          `component=${event.componentWidth}x${event.componentHeight}` : 'event=none')
        onComplete()
      }
    }
    return option
  }

  private logoImageOption(): ImageKnifeOption {
    return this.imageOption(this.ui.logoRef, this.ui.logo, ImageFit.Contain)
  }

  private updateTitleCollapseProgress(offset: number): void {
    const startOffset = 72
    const endOffset = 290
    const progress = Math.max(0, Math.min(1, (offset - startOffset) / (endOffset - startOffset)))
    if (Math.abs(progress - this.titleCollapseProgress) > 0.002) {
      this.titleCollapseProgress = progress
    }
  }

  private normalizedTitlePhase(start: number): number {
    if (this.titleCollapseProgress <= start) {
      return 0
    }
    return Math.min(1, (this.titleCollapseProgress - start) / (1 - start))
  }

  private easedTitlePhase(start: number): number {
    const progress = this.normalizedTitlePhase(start)
    return progress * progress * (3 - 2 * progress)
  }

  private heroTitleUsesCalligraphy(): boolean {
    return /[\u3400-\u9fff]/.test(this.ui.title)
  }

  private heroTitleScale(): number {
    let collapsedScale = 0.46
    if (this.ui.logo.length > 0) {
      collapsedScale = 0.28
    } else if (this.heroTitleUsesCalligraphy()) {
      collapsedScale = 0.23
    }
    return 1 - (1 - collapsedScale) * this.easedTitlePhase(0.42)
  }

  private registerHeroTitleFont(): void {
    if (heroTitleFontRegistered) {
      return
    }
    try {
      this.getUIContext().getFont().registerFont({
        familyName: HERO_TITLE_FONT_FAMILY,
        familySrc: $rawfile('fonts/MaShanZheng-Regular.ttf')
      })
      heroTitleFontRegistered = true
    } catch (_error) {
      this.logHeroWarn('title.font.register-failed')
    }
  }

  private heroTitleOpacity(): number {
    const fadeStart = 0.72
    const fadeEnd = 0.94
    if (this.titleCollapseProgress <= fadeStart) {
      return 1
    }
    return Math.max(0, 1 - (this.titleCollapseProgress - fadeStart) / (fadeEnd - fadeStart))
  }

  private collapsedTitleOpacity(): number {
    const fadeStart = 0.72
    const fadeEnd = 0.94
    if (this.titleCollapseProgress <= fadeStart) {
      return 0
    }
    return Math.min(1, (this.titleCollapseProgress - fadeStart) / (fadeEnd - fadeStart))
  }

  private collapsedTitleScale(): number {
    return 0.92 + 0.08 * this.collapsedTitleOpacity()
  }

  private heroLogoTranslateX(): number {
    const viewportWidth = this.vm.appUIState.deviceWidth > 0 ? this.vm.appUIState.deviceWidth : 360
    const logoWidth = 76 * 80 / 31
    const targetLeft = this.collapsedTitleGlobalX > 0 ? this.collapsedTitleGlobalX : 68
    const targetCenter = targetLeft + logoWidth * 0.28 / 2
    return (targetCenter - viewportWidth / 2) * this.easedTitlePhase(0.24)
  }

  private heroTextTranslateX(): number {
    const viewportWidth = this.vm.appUIState.deviceWidth > 0 ? this.vm.appUIState.deviceWidth : 360
    const targetCenter = this.collapsedTitleCenterX > 0 ? this.collapsedTitleCenterX : 112
    return (targetCenter - viewportWidth / 2) * this.easedTitlePhase(0.24)
  }

  private heroTitleTranslateY(): number {
    return -16 * this.easedTitlePhase(0.24)
  }

  private heroLogoTranslateY(): number {
    return -8 - 8 * this.easedTitlePhase(0.24)
  }

  private updateCollapsedTitleArea(area: Area): void {
    const globalX = Number(area.globalPosition.x)
    const centerX = globalX + Number(area.width) / 2
    if (globalX > 0 && Math.abs(globalX - this.collapsedTitleGlobalX) > 0.5) {
      this.collapsedTitleGlobalX = globalX
    }
    if (centerX > 0 && Math.abs(centerX - this.collapsedTitleCenterX) > 0.5) {
      this.collapsedTitleCenterX = centerX
    }
  }

  private legacyMediaRef(id: string, isTvShows: boolean): MediaRef | undefined {
    const normalizedId = id.trim()
    if (normalizedId.length === 0) return undefined
    try {
      const catalog = currentMediaCatalogProvider()
      return new MediaRef(catalog.session.provider, catalog.session.accountScope, normalizedId,
        isTvShows ? MediaRefKind.Series : MediaRefKind.Unknown)
    } catch (_error) {
      return undefined
    }
  }

  private sheetMaterialDecision(): OverlayMaterialDecision {
    return AppThemeOverlayPolicy.resolve(
      OverlaySurfaceRole.AppFloating,
      this.vm.appUIState.themeStyle,
      this.vm.appUIState.systemMaterialAvailable,
      this.vm.appUIState.nativeThemeAvailable
    )
  }

  @Builder
  private nativeHeroBackdrop() {
    Stack() {
      if (this.heroFeatheredPixelMap && this.heroBackdropKey === this.currentHeroBackdropKey()) {
        Image(this.heroFeatheredPixelMap)
          .width('100%')
          .height('100%')
          .objectFit(ImageFit.Cover)
          .transition(TransitionEffect.OPACITY)
          .hitTestBehavior(HitTestMode.None)
      } else {
        ImageKnifeComponent({
          imageKnifeOption: this.heroImageOption(() => this.prepareHeroBackdrop())
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
      if (this.heroBackdropPixelMap && this.heroBackdropKey === this.currentHeroBackdropKey()) {
        Image(this.heroBackdropPixelMap)
          .width('100%')
          .height('100%')
          .objectFit(ImageFit.Cover)
          .scale({ x: 1.18, y: 1.18 })
          .blur(128)
          .opacity(0.95)
      } else {
        ImageKnifeComponent({
          imageKnifeOption: this.heroImageOption()
        })
          .width('100%')
          .height('100%')
          .scale({ x: 1.18, y: 1.18 })
          .blur(128)
          .opacity(0.95)
      }

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
    .brightness(this.vm.appUIState.colorMode === ConfigurationConstant.ColorMode.COLOR_MODE_DARK ? 0.58 : 1)
    .hitTestBehavior(HitTestMode.None)
  }

  @Builder
  private nativeHeroTextLabel() {
    if (this.heroTitleUsesCalligraphy()) {
      Text(this.ui.title)
        .fontSize(68)
        .minFontSize(34)
        .maxFontSize(68)
        .heightAdaptivePolicy(TextHeightAdaptivePolicy.MIN_FONT_SIZE_FIRST)
        .fontFamily(HERO_TITLE_FONT_FAMILY)
        .fontWeight(FontWeight.Normal)
        .fontColor(Color.White)
        .textShadow([
          { radius: 10, color: 'rgba(0, 0, 0, 0.86)', offsetX: 0, offsetY: 3 },
          { radius: 20, color: 'rgba(0, 90, 255, 0.26)', offsetX: 0, offsetY: 0 }
        ])
        .maxLines(2)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .textAlign(TextAlign.Center)
        .width('100%')
    } else {
      Text(this.ui.title)
        .fontSize(38)
        .fontWeight(FontWeight.Bold)
        .fontColor(Color.White)
        .textShadow({ radius: 10, color: 'rgba(0, 0, 0, 0.82)', offsetX: 0, offsetY: 3 })
        .maxLines(2)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .textAlign(TextAlign.Center)
        .width('100%')
    }
  }

  @Builder
  private nativeHeroTitle() {
    if (this.ui.logo.length > 0) {
      Row() {
        ImageKnifeComponent({
          imageKnifeOption: this.logoImageOption()
        })
          .height(76)
          .aspectRatio(80 / 31)
      }
      .width('100%')
      .justifyContent(FlexAlign.Center)
      .scale({ x: this.heroTitleScale(), y: this.heroTitleScale() })
      .translate({ x: this.heroLogoTranslateX(), y: this.heroLogoTranslateY() })
      .opacity(this.heroTitleOpacity())
    } else {
      Column({ space: 6 }) {
        if (this.ui.subtitle.length > 0) {
          Text(this.ui.subtitle)
            .fontSize(14)
            .fontColor(Color.White)
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
        }
        this.nativeHeroTextLabel()
      }
      .width('100%')
      .alignItems(HorizontalAlign.Center)
      .scale({ x: this.heroTitleScale(), y: this.heroTitleScale() })
      .translate({ x: this.heroTextTranslateX(), y: this.heroTitleTranslateY() })
      .opacity(this.heroTitleOpacity())
    }
  }

  @Builder
  private nativeHeroMetadataLine() {
    Column({ space: 4 }) {
      if (this.ui.score > 0 || this.ui.primaryInfo.length > 0) {
        Scroll() {
          Text() {
            if (this.ui.score > 0) {
              Span(this.ui.score.toFixed(1) + '分')
                .fontColor($r('app.color.text_score'))
            }
            if (this.ui.score > 0 && this.ui.primaryInfo.length > 0) {
              Span(' · ')
            }
            if (this.ui.primaryInfo.length > 0) {
              Span(this.ui.primaryInfo)
            }
          }
          .fontSize(13)
          .fontColor(Color.White)
          .maxLines(1)
        }
        .width('100%')
        .scrollable(ScrollDirection.Horizontal)
        .scrollBar(BarState.Off)
        .edgeEffect(EdgeEffect.None)
      }

      if (this.ui.secondaryInfo.length > 0) {
        Scroll() {
          Text(this.ui.secondaryInfo)
            .fontSize(13)
            .fontColor(Color.White)
            .maxLines(1)
        }
        .width('100%')
        .scrollable(ScrollDirection.Horizontal)
        .scrollBar(BarState.Off)
        .edgeEffect(EdgeEffect.None)
      }
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  private nativeHeroMetadata() {
    Column({ space: 10 }) {
      this.nativeHeroTitle()

      this.nativeHeroMetadataLine()

      if (!this.ui.isTvShows && this.ui.playbackSources.length > 0) {
        Row({ space: 14 }) {
          ForEach(this.vm.getTags(), (item: string) => {
            Image(item)
              .height(21)
              .colorFilter(this.heroTagColorFilter)
          })
        }
        .width('100%')
        .justifyContent(FlexAlign.Center)
      }
    }
    .width('100%')
    .padding({ left: 16, right: 16, bottom: 8 })
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  private nativeCollapsedTitle() {
    Row() {
      Text(this.ui.title)
        .fontSize(17)
        .fontWeight(FontWeight.Medium)
        .fontColor($r('app.color.text_primary'))
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .constraintSize({ maxWidth: '100%' })
        .onAreaChange((_oldValue: Area, newValue: Area): void => {
          this.updateCollapsedTitleArea(newValue)
        })
    }
    .width('calc(100% - 136vp)')
    .height('100%')
    .justifyContent(FlexAlign.Start)
    .align(Alignment.Center)
    .opacity(this.collapsedTitleOpacity())
    .scale({ x: this.collapsedTitleScale(), y: this.collapsedTitleScale() })
    .hitTestBehavior(HitTestMode.None)
  }

  @Builder
  private nativeDetailActions() {
    Row({ space: 8 }) {
      NativeDetailPlayAction({
        label: this.ui.playButtonText,
        remainingTime: this.ui.remainingTime,
        progress: this.ui.percentageWatched,
        systemMaterialAvailable: this.vm.appUIState.systemMaterialAvailable,
        onAction: () => {
          this.vm.playCurrent()
        }
      })
        .layoutWeight(1)

      NativeDetailIconAction({
        icon: this.ui.isFavorite ? $r('app.media.ic_like') : $r('app.media.ic_unlike'),
        buttonSize: 44,
        selectedColor: $r('app.color.main_red_color'),
        preserveOriginalColor: this.ui.isFavorite,
        accessibilityLabel: this.ui.isFavorite ? '取消收藏' : '收藏',
        selected: this.ui.isFavorite,
        systemMaterialAvailable: this.vm.appUIState.systemMaterialAvailable,
        onAction: () => {
          this.vm.changeFavorite()
        }
      })
      NativeDetailIconAction({
        icon: $r('app.media.ic_check'),
        buttonSize: 44,
        accessibilityLabel: this.ui.played ? '标记为未播放' : '标记为已播放',
        selected: this.ui.played,
        systemMaterialAvailable: this.vm.appUIState.systemMaterialAvailable,
        onAction: () => {
          this.vm.changePlayedState()
        }
      })
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
        this.vm.playCurrent()
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
        imageKnifeOption: this.heroImageOption()
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
    const ref = this.ui.mediaRef
    if (!ref) return
    this.vm.getDetail(ref)
    if (this.useNativeSurface()) this.vm.loadDeleteAccess()
  }

  aboutToAppear(): void {
    this.heroBackdropActive = true
    this.registerHeroTitleFont()
    this.logHeroInfo('page.appear')
    this.configureHeroHeight()
    const raw = HMRouterMgr.getCurrentParam()
    if (raw && typeof raw !== 'string') {
      const param = raw as object
      const isTvShows = param['isTvShows'] === true
      this.ui.mediaName = typeof param['mediaName'] === 'string' ? param['mediaName'] as string : ''
      const ref = parseMediaRouteRefData(param['ref'] as MediaRouteRefData) ||
        this.legacyMediaRef(typeof param['id'] === 'string' ? param['id'] as string : '', isTvShows)
      if (ref) {
        this.ui.mediaRef = ref
        this.ui.id = ref.id
        this.ui.isTvShows = ref.kind === MediaRefKind.Series || isTvShows
        this.logHeroInfo('page.route.resolved', `kind=${ref.kind} tv=${this.ui.isTvShows ? 1 : 0}`)
        this.refreshDetail()
      }
    } else if (typeof raw === 'string') {
      const ref = this.legacyMediaRef(raw, false)
      if (ref) {
        this.ui.mediaRef = ref
        this.ui.id = ref.id
        this.logHeroInfo('page.route.legacy', `kind=${ref.kind}`)
        this.refreshDetail()
      }
    }
    this.getUIContext().getHostContext()?.eventHub.on(VideoDetailRefreshEvent, () => {
      setTimeout(() => {
        this.refreshDetail()
      }, 1000)
    })
    this.onColorModeChange()
  }

  aboutToDisappear(): void {
    this.heroBackdropActive = false
    this.heroBackdropGeneration += 1
    this.logHeroInfo('page.disappear',
      `generation=${this.heroBackdropGeneration} release=${this.heroBackdropPixelMap ? 1 : 0}`)
    this.getUIContext().getHostContext()?.eventHub.off(VideoDetailRefreshEvent)
    if (this.heroBackdropPixelMap) {
      this.heroBackdropPixelMap.release()
      this.heroBackdropPixelMap = undefined
    }
    if (this.heroFeatheredPixelMap) {
      this.heroFeatheredPixelMap.release()
      this.heroFeatheredPixelMap = undefined
    }
    this.heroBackdropKey = ''
    this.heroBackdropLoadingKey = ''
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
    if (this.sheetMaterialDecision() === OverlayMaterialDecision.UseBlurFallback) {
      Row() {
        this.overviewTriggerContent()
      }
      .width('100%')
      .padding({ left: 16, right: 16 })
      .bindSheet($$this.ui.isShowOverviewSheet, this.overviewSheetBuilder(true), {
        height: 420,
        preferType: SheetType.BOTTOM,
        title: { title: '简介' },
        backgroundColor: Color.Transparent
      })
    } else if (this.sheetMaterialDecision() === OverlayMaterialDecision.UseFloatingMaterial) {
      Row() {
        this.overviewTriggerContent()
      }
      .width('100%')
      .padding({ left: 16, right: 16 })
      .bindSheet($$this.ui.isShowOverviewSheet, this.overviewSheetBuilder(false), {
        height: 420,
        preferType: SheetType.BOTTOM,
        title: { title: '简介' },
        backgroundColor: Color.Transparent,
        systemMaterial: AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating)
      })
    } else if (this.sheetMaterialDecision() === OverlayMaterialDecision.DisableSystemMaterial) {
      Row() {
        this.overviewTriggerContent()
      }
      .width('100%')
      .padding({ left: 16, right: 16 })
      .bindSheet($$this.ui.isShowOverviewSheet, this.overviewSheetBuilder(false), {
        height: 420,
        preferType: SheetType.BOTTOM,
        title: { title: '简介' },
        systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
      })
    } else {
      Row() {
        this.overviewTriggerContent()
      }
      .width('100%')
      .padding({ left: 16, right: 16 })
      .bindSheet($$this.ui.isShowOverviewSheet, this.overviewSheetBuilder(false), {
        height: 420,
        preferType: SheetType.BOTTOM,
        title: { title: '简介' }
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
    if (this.sheetMaterialDecision() === OverlayMaterialDecision.UseBlurFallback) {
      Row({ space: 10 }) {
        this.videoInfoTriggerContent()
      }
      .width('100%')
      .padding({ top: 4, left: 4, bottom: 8 })
      .onClick(() => {
        this.ui.isShowVideoInfoSheet = true
      })
      .bindSheet($$this.ui.isShowVideoInfoSheet, this.videoInfoSheetBuilder(true), {
        height: '70%',
        preferType: SheetType.BOTTOM,
        title: { title: '文件媒体信息' },
        backgroundColor: Color.Transparent
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
      .bindSheet($$this.ui.isShowVideoInfoSheet, this.videoInfoSheetBuilder(false), {
        height: '70%',
        preferType: SheetType.BOTTOM,
        title: { title: '文件媒体信息' },
        backgroundColor: Color.Transparent,
        systemMaterial: AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating)
      })
    } else if (this.sheetMaterialDecision() === OverlayMaterialDecision.DisableSystemMaterial) {
      Row({ space: 10 }) {
        this.videoInfoTriggerContent()
      }
      .width('100%')
      .padding({ top: 4, left: 4, bottom: 8 })
      .onClick(() => {
        this.ui.isShowVideoInfoSheet = true
      })
      .bindSheet($$this.ui.isShowVideoInfoSheet, this.videoInfoSheetBuilder(false), {
        height: '70%',
        preferType: SheetType.BOTTOM,
        title: { title: '文件媒体信息' },
        systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
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
      .bindSheet($$this.ui.isShowVideoInfoSheet, this.videoInfoSheetBuilder(false), {
        height: '70%',
        preferType: SheetType.BOTTOM,
        title: { title: '文件媒体信息' }
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
              } else if (this.ui.logo.length > 0) {
                ImageKnifeComponent({
                  imageKnifeOption: this.logoImageOption()
                })
                  .height(80)
                  .aspectRatio(80 / 31) // 刮削的logo都是800x310
                  .margin({ bottom: 18, left: 16 })
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
                Column({ space: 4 }) {
                  if ((!this.ui.isTvShows && this.ui.score > 0) || this.ui.primaryInfo.length > 0) {
                    Text() {
                      if (!this.ui.isTvShows && this.ui.score > 0) {
                        Span(this.ui.score + '分').fontColor($r('app.color.text_score'))
                      }
                      if (!this.ui.isTvShows && this.ui.score > 0 && this.ui.primaryInfo.length > 0) {
                        Span(' · ')
                      }
                      if (this.ui.primaryInfo.length > 0) {
                        Span(this.ui.primaryInfo)
                      }
                    }
                    .fontSize(12)
                    .fontColor($r('app.color.text_1'))
                    .width('100%')
                    .textAlign(TextAlign.Start)
                  }

                  if (this.ui.secondaryInfo.length > 0) {
                    Text(this.ui.secondaryInfo)
                      .fontSize(12)
                      .fontColor($r('app.color.text_1'))
                      .width('100%')
                      .textAlign(TextAlign.Start)
                  }
                }
                .width('100%')
                .padding({ left: 16, right: 16 })
                .alignItems(HorizontalAlign.Start)
              }

              if (!this.useNativeSurface() && !this.ui.isTvShows &&
                this.ui.selectedMediaSourceIndex >= 0 &&
                this.ui.selectedMediaSourceIndex < this.ui.playbackSources.length) {
                Blank().height(10)
                Row({ space: 16 }) {
                  ForEach(this.vm.getTags(), (item: string) => {
                    Image(item)
                      .height(22)
                      .colorFilter(this.ui.colorFilter)
                  })
                }.padding({ left: 16, right: 16 })
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
              if (!this.ui.isTvShows && this.ui.selectedMediaSourceIndex >= 0 &&
                this.ui.selectedMediaSourceIndex < this.ui.playbackSources.length) {
                Blank().height(16)
                Row() {
                  if (this.ui.playbackSources.length > 1) {
                    MediaSourceSelector({
                      sources: this.ui.playbackSources,
                      selectedIndex: this.ui.selectedMediaSourceIndex,
                      onSelect: (index: number) => {
                        this.vm.selectMediaSource(index)
                      }
                    })
                      .layoutWeight(1)
                  }
                  AudioSubtitleSelector({
                    subtitleOptions: this.ui.playbackSources[this.ui.selectedMediaSourceIndex].subtitleOptions,
                    audioOptions: this.ui.playbackSources[this.ui.selectedMediaSourceIndex].audioOptions,
                    selectedSubtitleIndex: this.ui.selectedSubtitleOptionIndex,
                    selectedAudioIndex: this.ui.selectedAudioOptionIndex,
                    triggerTextColor: this.useNativeSurface() ? Color.White : $r('app.color.text_primary'),
                    onSelectSubtitle: (index: number) => {
                      this.vm.selectSubtitleOption(index)
                    },
                    onSelectAudio: (index: number) => {
                      this.vm.selectAudioOption(index)
                    }
                  })
                    .layoutWeight(this.ui.playbackSources.length > 1 ? 2 : 1)
                }
                .width('calc(100% - 24vp)')
                .height(32)
                .alignSelf(ItemAlign.Center)
              }
              if (this.ui.overview.length > 0) {
                Blank().height(28)
                this.overviewSheetTrigger()
                Blank().height(12)
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
                        textAlign: TextAlign.Center,
                        onOpen: () => {
                          HMRouterMgr.to(RouterConsts.SeasonDetailPage)
                            .withParam({
                              id: item.id,
                              ref: item.mediaRef ? toMediaRouteRefData(item.mediaRef) : undefined,
                              mediaName: this.ui.mediaName,
                              seasons: this.ui.seasons,
                              info: this.ui.info,
                              parentId: this.ui.id,
                              seriesRef: this.ui.mediaRef ? toMediaRouteRefData(this.ui.mediaRef) : undefined
                            })
                            .push()
                        }
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
              if (!this.ui.isTvShows && (this.hasFileInformation() || this.hasVideoInformation())) {
                Column() {
                  Blank().height(16)

                  if (this.hasFileInformation()) {
                    Text('文件信息').fontSize(15).fontWeight(FontWeight.Medium).fontColor($r('app.color.text_primary'))

                    Column({ space: 16 }) {
                      if (this.ui.path.trim().length > 0) {
                        Column({ space: 10 }) {
                          Text('文件位置').fontSize(12).fontColor($r('app.color.text_2'))
                          Text(this.ui.path).fontSize(12).fontColor($r('app.color.text_1'))
                        }.width('100%').alignItems(HorizontalAlign.Start)
                      }
                      if (this.ui.size.trim().length > 0) {
                        Column({ space: 10 }) {
                          Text('文件大小').fontSize(12).fontColor($r('app.color.text_2'))
                          Text(this.ui.size).fontSize(12).fontColor($r('app.color.text_1'))
                        }.width('100%').alignItems(HorizontalAlign.Start)
                      }
                      if (this.ui.createDate.trim().length > 0) {
                        Column({ space: 10 }) {
                          Text('文件创建日期').fontSize(12).fontColor($r('app.color.text_2'))
                          Text(this.ui.createDate).fontSize(12).fontColor($r('app.color.text_1'))
                        }.width('100%').alignItems(HorizontalAlign.Start)
                      }
                      if (this.ui.addedDate.trim().length > 0) {
                        Column({ space: 10 }) {
                          Text('添加日期').fontSize(12).fontColor($r('app.color.text_2'))
                          Text(this.ui.addedDate).fontSize(12).fontColor($r('app.color.text_1'))
                        }.width('100%').alignItems(HorizontalAlign.Start)
                      }
                    }
                    .width('100%')
                    .backgroundColor(this.detailInformationBackground())
                    .borderRadius(12)
                    .padding(12)
                    .alignItems(HorizontalAlign.Start)
                    .margin({ top: 12 })
                  }

                  if (this.hasVideoInformation()) {
                    if (this.hasFileInformation()) {
                      Blank().height(16)
                    }

                    Text('视频信息').fontSize(15).fontWeight(FontWeight.Medium).fontColor($r('app.color.text_primary'))

                    Column({ space: 16 }) {
                      if (this.ui.videoInfo.trim().length > 0) {
                        Column({ space: 10 }) {
                          Text('视频').fontSize(12).fontColor($r('app.color.text_2'))
                          Text(this.ui.videoInfo)
                            .fontSize(12)
                            .fontColor($r('app.color.text_1'))
                            .lineSpacing({ value: 4, unit: LengthUnit.VP })
                        }.width('100%')
                        .alignItems(HorizontalAlign.Start)
                      }
                      if (this.ui.audioInfo.trim().length > 0) {
                        Column({ space: 10 }) {
                          Text('音频').fontSize(12).fontColor($r('app.color.text_2'))
                          Text(this.ui.audioInfo)
                            .fontSize(12)
                            .fontColor($r('app.color.text_1'))
                            .lineSpacing({ value: 4, unit: LengthUnit.VP })
                        }.width('100%')
                        .alignItems(HorizontalAlign.Start)
                      }
                      if (this.ui.subtitleInfo.trim().length > 0) {
                        Column({ space: 10 }) {
                          Text('字幕').fontSize(12).fontColor($r('app.color.text_2'))
                          Text(this.ui.subtitleInfo)
                            .fontSize(12)
                            .fontColor($r('app.color.text_1'))
                            .lineSpacing({ value: 4, unit: LengthUnit.VP })
                        }.width('100%')
                        .alignItems(HorizontalAlign.Start)
                      }

                      if (this.hasSelectedMediaStreams()) {
                        Divider().height(0.5).color(AppThemeSurfaceResolver.contentDivider(
                          this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable))
                        this.videoInfoSheetTrigger()
                      }
                    }
                    .width('100%')
                    .backgroundColor(this.detailInformationBackground())
                    .borderRadius(12)
                    .padding(12)
                    .margin({ top: 12 })
                  }
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
                  ForEach(this.ui.externalUrls, (item: MediaExternalLink) => {
                    Text(item.title)
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
                        this.vm.openExternalUrl(item.url)
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
          this.updateTitleCollapseProgress(y)
          if (y === 0 && this.ui.currentScrollState === 1) {
            this.vm.bannerSpringBack();
          }
          this.ui.blur = y / 10
          if (y > 200) {
            this.ui.showActionBar = true
          } else {
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
      title: '',
      contentExtendsUnderTitleBar: true,
      heroTitleChrome: true,
      menus: this.navigationMenus(),
      scrollControllers: [this.ui.scroller],
      hasTitleBarStackBuilder: true,
      titleBarStackBuilder: () => {
        this.nativeCollapsedTitle()
      },
      contentBuilder: () => {
        this.pageContent(false)
      },
      legacyContentBuilder: () => {
        this.pageContent(true)
      }
    })
  }

  @Builder
  overviewSheetBuilder(blurFallback: boolean) {
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
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.Floating, blurFallback, false))
  }

  @Builder
  videoInfoSheetBuilder(blurFallback: boolean) {
    Scroll() {
      VideoInfoDetail({
        sections: this.ui.mediaInfoSections
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
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.Floating, blurFallback, false))
  }
}
