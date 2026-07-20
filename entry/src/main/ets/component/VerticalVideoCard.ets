import { ImageKnifeComponent, ImageKnifeOption } from "@ohos/imageknifepro"
import { AppStorageV2 } from "@kit.ArkUI";
import { AppUIState } from "../entity/AppUIState";
import {
  VERTICAL_CARD_HEIGHT, VERTICAL_CARD_WIDTH
} from "../common/OneMoreConst";
import { AppThemeTokens } from "../theme/AppThemeTokens";
import { hdsEffect } from "@kit.UIDesignKit";
import { ColorUtils } from "../utils/ColorUtils";

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
  @Local option: ImageKnifeOption = {
    loadSrc: this.image,
    objectFit: ImageFit.Cover,
    border: {
      radius: 10
    },
    placeholderSrc: $r('app.media.placeholder_video'),
    placeholderObjectFit: ImageFit.CENTER,
    onComplete: () => {
      if (!AppThemeTokens.isNative(this.appUIState.themeStyle) && this.appUIState.isBigScreen &&
        this.index === 0 && this.image.length > 0) {
        ColorUtils.extractLeftEdgeColor(this.image).then((color) => {
          this.pointLightColor = color
        })
      }
    }
  }
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;

  build() {
    Column({ space: 8 }) {
      ImageKnifeComponent({
        imageKnifeOption: this.option
      })
        .width(VERTICAL_CARD_WIDTH.getValue(this.appUIState.currentBreakpoint))
        .height(VERTICAL_CARD_HEIGHT.getValue(this.appUIState.currentBreakpoint))
        .borderColor($r('app.color.border_1'))
        .borderWidth(0.5)
        .borderRadius(AppThemeTokens.mediaRadius(this.appUIState.themeStyle))
        .backgroundColor($r('app.color.bg_2'))
        .visualEffect(new hdsEffect.HdsEffectBuilder()
          .pointLight({
            illuminatedType: !AppThemeTokens.isNative(this.appUIState.themeStyle) && this.index === 0 ?
              this.appUIState.illuminatedType : hdsEffect.PointLightIlluminatedType.NONE,
            options: {
              color: this.pointLightColor,
              intensity: 2,
              height: VERTICAL_CARD_HEIGHT.getValue(this.appUIState.currentBreakpoint),
            }
          })
          .buildEffect())
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
