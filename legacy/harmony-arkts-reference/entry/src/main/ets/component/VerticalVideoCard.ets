import { AppStorageV2 } from "@kit.ArkUI";
import { AppUIState } from "../entity/AppUIState";
import { VERTICAL_CARD_HEIGHT } from "../common/OneMoreConst";
import { AppThemeTokens } from "../theme/AppThemeTokens";
import { ColorUtils } from "../utils/ColorUtils";
import { FeiniuPointLightModifier } from "../theme/FeiniuPointLightModifier";
import { AppThemeSurfaceResolver } from "../theme/AppThemeSurfaceResolver";
import { MediaImageRef } from "../media/catalog/MediaCatalogModels";
import { MediaNetworkImage } from "./MediaNetworkImage";

@ComponentV2
export struct VerticalVideoCard {
  //图片
  @Require @Param image: string
  @Param imageRef?: MediaImageRef = undefined
  //标题
  @Param name: string = ''
  //副标题
  @Param subtitle: string = ''
  //评分与清晰度标签
  @Param score?: number = undefined
  @Param tags: string[] = []
  //第几个卡片
  @Param index?: number = 0
  @Local pointLightColor: string = '#FFFFFF'
  private imageLoadCompleted: boolean = false
  private pointLightColorSource: string = ''
  @Local appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;

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

  private displayTags(): string[] {
    const visibleTags: string[] = []
    this.tags.forEach((tag: string): void => {
      const normalized = tag.trim().toUpperCase()
      if (normalized.length === 0 || normalized.startsWith('OTH') || visibleTags.length >= 2) {
        return
      }
      const duplicated = visibleTags.some((visibleTag: string): boolean =>
        visibleTag.toUpperCase() === normalized)
      if (!duplicated) {
        visibleTags.push(tag.trim())
      }
    })
    return visibleTags
  }

  build() {
    Column({ space: 8 }) {
      RelativeContainer() {
        MediaNetworkImage({
          imageRef: this.imageRef,
          fallbackSource: this.image,
          radius: 10,
          onLoadSuccess: (): void => {
            this.imageLoadCompleted = true
            this.refreshPointLightColor()
          }
        })
          .width('100%')
          .height('100%')
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
        if (this.score) {
          Text(this.score.toFixed(1))
            .fontSize(11)
            .fontColor(Color.Yellow)
            .fontWeight(FontWeight.Medium)
            .padding({ left: 6, right: 6, top: 4, bottom: 4 })
            .alignRules({
              top: { anchor: '__container__', align: VerticalAlign.Top },
              left: { anchor: '__container__', align: HorizontalAlign.Start }
            })
            .margin(4)
            .backgroundColor(0x60000000)
            .borderRadius(4)
        }
        if (this.displayTags().length > 0) {
          Row({ space: 4 }) {
            ForEach(this.displayTags(), (tag: string) => {
              if (tag === '4K') {
                Image($r('app.media.4K'))
                  .height(20)
                  .width(28)
                  .opacity(0.7)
                  .objectFit(ImageFit.Fill)
              } else {
                Text(tag)
                  .fontSize(9)
                  .fontColor(Color.White)
                  .fontWeight(FontWeight.Medium)
                  .padding({ top: 1, bottom: 1, left: 2, right: 2 })
                  .borderWidth(1)
                  .borderColor(Color.White)
                  .borderRadius(3)
                  .margin(3)
              }
            }, (tag: string) => tag.toUpperCase())
          }
          .alignRules({
            bottom: { anchor: '__container__', align: VerticalAlign.Bottom },
            right: { anchor: '__container__', align: HorizontalAlign.End }
          })
          .margin(4)
        }
      }
        .width('100%')
        .aspectRatio(2 / 3)
        .borderRadius(AppThemeTokens.mediaRadius(this.appUIState.themeStyle))
        .clip(true)
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
    }.width('100%').alignItems(HorizontalAlign.Start)
  }
}
