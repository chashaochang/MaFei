import { ImageKnifeComponent, ImageKnifeOption } from "@ohos/imageknifepro"
import { AppStorageV2 } from "@kit.ArkUI";
import { AppUIState } from "../entity/AppUIState";
import { HORIZONTAL_CARD_HEIGHT, HORIZONTAL_CARD_WIDTH } from "../common/OneMoreConst";
import { hdsEffect } from "@kit.UIDesignKit";
import { ColorUtils } from "../utils/ColorUtils";

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
  //第几个用于展示点光源
  @Param index?: number = 0
  @Local pointLightColor: string = '#FFFFFF'
  @Local option: ImageKnifeOption = {
    loadSrc: this.image,
    objectFit: ImageFit.Cover,
    border: {
      radius: 6
    },
    placeholderSrc: $r('app.media.placeholder_video'),
    placeholderObjectFit: ImageFit.CENTER,
    onComplete: () => {
      if (this.appUIState.isLargeScreen) {
        ColorUtils.extractLeftEdgeColor(this.image).then((color) => {
          this.pointLightColor = color
        })
      }
    }
  }
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;

  build() {
    Column({ space: 8 }) {
      if (this.index === 0) {
        // 第一个图片添加光源效果 - 使用外层Stack
        Stack() {
          ImageKnifeComponent({
            imageKnifeOption: this.option
          })
            .width(HORIZONTAL_CARD_WIDTH.getValue(this.appUIState.currentBreakpoint))
            .height(HORIZONTAL_CARD_HEIGHT.getValue(this.appUIState.currentBreakpoint))
            .borderColor($r('app.color.border_1'))
            .borderWidth(0.5)
            .backgroundColor($r('app.color.bg_2'))
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
        .borderRadius(6)
        .clip(true)
        .width(HORIZONTAL_CARD_WIDTH.getValue(this.appUIState.currentBreakpoint))
        .height(HORIZONTAL_CARD_HEIGHT.getValue(this.appUIState.currentBreakpoint))
        .visualEffect(new hdsEffect.HdsEffectBuilder()
          .pointLight({
            illuminatedType: this.appUIState.illuminatedType,
            options: {
              color: this.pointLightColor,
              intensity: 2,
              height: HORIZONTAL_CARD_WIDTH.getValue(this.appUIState.currentBreakpoint),
            }
          })
          .buildEffect())

      } else {
        Stack() {
          ImageKnifeComponent({
            imageKnifeOption: this.option
          })
            .width(HORIZONTAL_CARD_WIDTH.getValue(this.appUIState.currentBreakpoint))
            .height(HORIZONTAL_CARD_HEIGHT.getValue(this.appUIState.currentBreakpoint))
            .borderColor($r('app.color.border_1'))
            .borderWidth(0.5)
            .backgroundColor($r('app.color.bg_2'))
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
        .borderRadius(6)
        .clip(true)
        .width(HORIZONTAL_CARD_WIDTH.getValue(this.appUIState.currentBreakpoint))
        .height(HORIZONTAL_CARD_HEIGHT.getValue(this.appUIState.currentBreakpoint))
      }

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