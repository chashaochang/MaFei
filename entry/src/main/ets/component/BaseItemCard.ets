import { ImageKnifeComponent, ImageKnifeOption } from "@ohos/imageknifepro";
import { AppStorageV2 } from "@kit.ArkUI";
import { AppUIState } from "../entity/AppUIState";
import { createMediaImageKnifeOption } from "../media/catalog/MediaImageKnifeAdapter";
import { MediaSummary } from "../media/catalog/MediaCatalogModels";
import { MediaRefKind } from "../media/MediaRef";
import { AppThemeTokens } from "../theme/AppThemeTokens";
import { AppThemeSurfaceResolver } from "../theme/AppThemeSurfaceResolver";

@ComponentV2
export struct BaseItemCard {
  @Param @Require item: MediaSummary;
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;
  @Local textAlign: TextAlign = TextAlign.Start

  private imageOption(): ImageKnifeOption {
    const option = createMediaImageKnifeOption(this.item.primaryImage, this.item.primaryImage?.url || '')
    option.objectFit = ImageFit.Cover
    option.border = { radius: 10 }
    option.placeholderSrc = this.item.ref.kind === MediaRefKind.Season ?
      $r('app.media.placeholder_folder') : $r('app.media.placeholder_video')
    option.placeholderObjectFit = ImageFit.CENTER
    return option
  }

  private subtitle(): string {
    if (this.item.ref.kind === MediaRefKind.Episode) {
      return `S${this.item.seasonNumber || 0}:E${this.item.episodeNumber || 0} - ${this.item.title}`
    }
    if (this.item.year !== undefined) {
      return this.item.year.toString()
    }
    return this.item.subtitle
  }

  build() {
    Column() {
      RelativeContainer() {
        ImageKnifeComponent({
          imageKnifeOption: this.imageOption()
        })
          .aspectRatio(2 / 3)
          .width('100%')
          .borderColor(AppThemeSurfaceResolver.mediaOutline(
            this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable))
          .borderWidth(AppThemeSurfaceResolver.outlineWidth(
            this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable, 0.5))
          .borderRadius(AppThemeTokens.mediaRadius(this.appUIState.themeStyle))
          .backgroundColor(AppThemeSurfaceResolver.useNativeSurface(
            this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable) ?
            AppThemeSurfaceResolver.mediaPlaceholderBackground(
              this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable) : '#CBD2D3')
        // .backgroundBlurStyle(BlurStyle.BACKGROUND_THICK)
        if (this.item.communityRating) {
          Text(this.item.communityRating.toFixed(1) + '')
            .fontSize(11)
            .fontColor(Color.Yellow)
            .fontWeight(FontWeight.Medium)
            .padding({
              left: 6,
              right: 6,
              top: 4,
              bottom: 4
            })
            .alignRules({
              top: {
                anchor: '__container__',
                align: VerticalAlign.Top,
              },
              left: {
                anchor: '__container__',
                align: HorizontalAlign.Start
              }
            })
            .margin(4)
            .backgroundColor(0x60000000)
            // .backgroundBlurStyle(BlurStyle.BACKGROUND_THICK)
            .borderRadius(4)
        }
        if (this.item.tags.length > 0) {
          Row({ space: 4 }) {
            ForEach(this.item.tags, (tag: string) => {
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
      }
      .width('100%')
      .aspectRatio(2 / 3)
      .borderRadius(AppThemeTokens.mediaRadius(this.appUIState.themeStyle))
      .clip(true)

      Text(this.item.title)
        .fontColor($r('app.color.text_primary'))
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
        .textAlign(this.textAlign)
      Text(this.subtitle())
        .fontColor(Color.Gray)
        .fontSize(12)
        .width('100%')
        .maxLines(1)
        .ellipsisMode(EllipsisMode.END)
        .textOverflow({
          overflow: TextOverflow.Ellipsis
        })
        .textAlign(this.textAlign)
        .margin({
          top: 4
        })
    }.alignItems(HorizontalAlign.Start).margin({
      bottom: 8
    })
    .width(100)
  }
}
