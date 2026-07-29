import { ImageKnifeComponent, ImageKnifeOption } from "@ohos/imageknifepro"
import { AppStorageV2 } from "@kit.ArkUI";
import { AppUIState } from "../entity/AppUIState";
import { HORIZONTAL_CARD_HEIGHT, HORIZONTAL_CARD_WIDTH } from "../common/OneMoreConst";
import { AppThemeTokens } from "../theme/AppThemeTokens";
import { ColorUtils } from "../utils/ColorUtils";
import { FeiniuPointLightModifier } from "../theme/FeiniuPointLightModifier";
import { AppThemeSurfaceResolver } from "../theme/AppThemeSurfaceResolver";

@ComponentV2
export struct HorizontalVideoCard {
  //图片
  @Require @Param image: string
  //标题
  @Param name: string = ''
  //副标题
  @Param subtitle: string = ''
  //进度
  @Param percent?: number = undefined
  //第几个卡片
  @Param index?: number = 0
  @Local pointLightColor: string = '#FFFFFF'
  private imageLoadCompleted: boolean = false
  private pointLightColorSource: string = ''
  @Local option: ImageKnifeOption = {
    loadSrc: this.image,
    objectFit: ImageFit.Cover,
    border: {
      radius: 10
    },
    placeholderSrc: $r('app.media.placeholder_video'),
    placeholderObjectFit: ImageFit.CENTER,
    onComplete: () => {
      this.imageLoadCompleted = true
      this.refreshPointLightColor()
    }
  }
  @Local appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;

  @Monitor('appUIState.feiniuPointLightEnabled')
  onPointLightEnabledChange(): void {
    if (this.appUIState.feiniuPointLightEnabled && this.imageLoadCompleted) {
      this.refreshPointLightColor()
    }
  }

  private refreshPointLightColor(): void {
    if (!this.imageLoadCompleted || !this.appUIState.feiniuPointLightEnabled ||
      this.index !== 0 || this.image.length === 0) {
      return
    }
    const image = this.image
    if (this.pointLightColorSource === image) {
      return
    }
    this.pointLightColorSource = image
    ColorUtils.extractLeftEdgeColor(image).then((color) => {
      if (this.image === image) {
        this.pointLightColor = color
      }
    })
  }

  build() {
    Column({ space: 8 }) {
      Stack() {
        ImageKnifeComponent({
          imageKnifeOption: this.option
        })
          .width(HORIZONTAL_CARD_WIDTH.getValue(this.appUIState.currentBreakpoint))
          .height(HORIZONTAL_CARD_HEIGHT.getValue(this.appUIState.currentBreakpoint))
          .borderColor(AppThemeSurfaceResolver.mediaOutline(
            this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable))
          .borderWidth(AppThemeSurfaceResolver.outlineWidth(
            this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable, 0.5))
          .backgroundColor(AppThemeSurfaceResolver.mediaPlaceholderBackground(
            this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable))
        Progress({
          value: this.percent || 0,
          total: 100,
          type: ProgressType.Linear,
        })
          .height(4)
          .layoutWeight(1)
          .color('#3B82F6')
      }
      .alignContent(Alignment.Bottom)
      .borderRadius(AppThemeTokens.mediaRadius(this.appUIState.themeStyle))
      .clip(true)
      .width(HORIZONTAL_CARD_WIDTH.getValue(this.appUIState.currentBreakpoint))
      .height(HORIZONTAL_CARD_HEIGHT.getValue(this.appUIState.currentBreakpoint))
      .visualEffect(FeiniuPointLightModifier.build({
        enabled: this.appUIState.feiniuPointLightEnabled && this.index === 0,
        color: this.pointLightColor,
        intensity: 2,
        height: HORIZONTAL_CARD_HEIGHT.getValue(this.appUIState.currentBreakpoint)
      }))

      Text(this.name)
        .fontColor($r('app.color.text_primary'))
        .fontSize(14)
        .fontWeight(FontWeight.Medium)
        .width('100%')
        .maxLines(1)
        .ellipsisMode(EllipsisMode.END)
        .textOverflow({
          overflow: TextOverflow.Ellipsis
        })
      Text(this.subtitle)
        .fontColor(Color.Gray)
        .fontSize(12)
        .width('100%')
        .maxLines(1)
        .ellipsisMode(EllipsisMode.END)
    }
    .width(HORIZONTAL_CARD_WIDTH.getValue(this.appUIState.currentBreakpoint))
    .alignItems(HorizontalAlign.Start)
  }
}
