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
import { MediaImageRef } from "../media/catalog/MediaCatalogModels";
import { createMediaImageKnifeOption } from "../media/catalog/MediaImageKnifeAdapter";

@ComponentV2
export struct VerticalVideoCard {
  //图片
  @Require @Param image: string
  @Param imageRef?: MediaImageRef = undefined
  //标题
  @Param name: string = ''
  //副标题
  @Param subtitle: string = ''
  //第几个卡片
  @Param index?: number = 0
  @Local pointLightColor: string = '#FFFFFF'
  private imageLoadCompleted: boolean = false
  private pointLightColorSource: string = ''
  @Local appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;

  private imageOption(): ImageKnifeOption {
    const option = createMediaImageKnifeOption(this.imageRef, this.image)
    option.objectFit = ImageFit.Cover
    option.border = {
      radius: 10
    }
    option.placeholderSrc = $r('app.media.placeholder_video')
    option.placeholderObjectFit = ImageFit.CENTER
    option.onComplete = () => {
      this.imageLoadCompleted = true
      this.refreshPointLightColor()
    }
    return option
  }

  @Monitor('appUIState.feiniuPointLightEnabled')
  onPointLightEnabledChange(): void {
    if (this.appUIState.feiniuPointLightEnabled && this.imageLoadCompleted) {
      this.refreshPointLightColor()
    }
  }

  private refreshPointLightColor(): void {
    if (!this.imageLoadCompleted || !this.appUIState.feiniuPointLightEnabled ||
      this.index !== 0 || this.imageSource().length === 0) {
      return
    }
    const image = this.imageSource()
    if (this.pointLightColorSource === image) {
      return
    }
    this.pointLightColorSource = image
    ColorUtils.extractLeftEdgeColor(image).then((color) => {
      if (this.imageSource() === image) {
        this.pointLightColor = color
      }
    })
  }

  private imageSource(): string {
    return this.imageRef?.url || this.image
  }

  build() {
    Column({ space: 8 }) {
      ImageKnifeComponent({
          imageKnifeOption: this.imageOption()
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
