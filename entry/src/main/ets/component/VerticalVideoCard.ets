import { ImageKnifeComponent, ImageKnifeOption } from "@ohos/imageknifepro"
import { AppStorageV2 } from "@kit.ArkUI";
import { AppUIState } from "../entity/AppUIState";
import {
  VERTICAL_CARD_HEIGHT, VERTICAL_CARD_WIDTH
} from "../common/OneMoreConst";
import { AppThemeTokens } from "../theme/AppThemeTokens";
import { ColorUtils } from "../utils/ColorUtils";
import { FeiniuPointLightModifier } from "../theme/FeiniuPointLightModifier";
import { AppThemeSurfaceResolver } from "../theme/AppThemeSurfaceResolver";

@ComponentV2
export struct VerticalVideoCard {
  //图片
  @Require @Param image: string
  //标题
  @Param name: string = ''
  //副标题
  @Param subtitle: string = ''
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
      ImageKnifeComponent({
        imageKnifeOption: this.option
      })
        .width(VERTICAL_CARD_WIDTH.getValue(this.appUIState.currentBreakpoint))
        .height(VERTICAL_CARD_HEIGHT.getValue(this.appUIState.currentBreakpoint))
        .borderColor(AppThemeSurfaceResolver.mediaOutline(
          this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable))
        .borderWidth(AppThemeSurfaceResolver.outlineWidth(
          this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable, 0.5))
        .borderRadius(AppThemeTokens.mediaRadius(this.appUIState.themeStyle))
        .backgroundColor(AppThemeSurfaceResolver.mediaPlaceholderBackground(
          this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable))
        .visualEffect(FeiniuPointLightModifier.build({
          enabled: this.appUIState.feiniuPointLightEnabled && this.index === 0,
          color: this.pointLightColor,
          intensity: 2,
          height: VERTICAL_CARD_HEIGHT.getValue(this.appUIState.currentBreakpoint)
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
        .textOverflow({
          overflow: TextOverflow.Ellipsis
        })
    }.width(VERTICAL_CARD_WIDTH.getValue(this.appUIState.currentBreakpoint)).alignItems(HorizontalAlign.Start)
  }
}
