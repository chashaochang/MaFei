import { ImageKnifeComponent, ImageKnifeOption } from "@ohos/imageknifepro";
import { ImageUrlsApi } from "sdk";
import { BaseItemDto, BaseItemKind } from "sdk/src/main/ets/generated-client/models";
import { resolveLatestImageUrl } from "../common/CommonFunc";
import { ApiClient } from "../network/ApiClient";
import { AppStorageV2 } from "@kit.ArkUI";
import { AppUIState } from "../entity/AppUIState";
import { AppThemeTokens } from "../theme/AppThemeTokens";

@ComponentV2
export struct BaseItemCard {
  @Param @Require item: BaseItemDto;
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;
  @Local image: string = ''
  @Local option: ImageKnifeOption = {
    loadSrc: this.image,
    objectFit: ImageFit.Cover,
    border: {
      radius: 10
    },
    placeholderSrc: this.item.Type == BaseItemKind.Season ? $r('app.media.placeholder_folder') :
      $r('app.media.placeholder_video'),
    placeholderObjectFit: ImageFit.CENTER
  }
  @Local score: number = 0
  @Local tags: string[] = []
  @Local title: string = ''
  @Local textAlign: TextAlign = TextAlign.Start
  @Local subtitle: string = ''
  imageApi: ImageUrlsApi = new ImageUrlsApi()

  aboutToAppear(): void {
    switch (this.item.Type) {
      case BaseItemKind.Movie: {
        this.title = this.item.Name || ''
        this.subtitle = this.item.ProductionYear ? this.item.ProductionYear.toString() : ''
        this.image = ApiClient.Instance().basePath + resolveLatestImageUrl(this.item, this.imageApi)
        break
      }
      case BaseItemKind.Series:{
        this.title = this.item.SeriesName || ''
        this.subtitle = this.item.ProductionYear ? this.item.ProductionYear.toString() : ''
        this.image = ApiClient.Instance().basePath + resolveLatestImageUrl(this.item, this.imageApi)
        break
      }
      case BaseItemKind.Episode:{
        this.title = this.item.Name || ''
        this.subtitle = `S${this.item.ParentIndexNumber}:E${this.item.IndexNumber} - ${this.item.Name}`
        this.image = ApiClient.Instance().basePath + resolveLatestImageUrl(this.item, this.imageApi)
        break
      }
      default:
        this.title = this.item.Name || ''
        this.subtitle = this.item.ProductionYear ? this.item.ProductionYear.toString() : ''
        this.image = ApiClient.Instance().basePath + resolveLatestImageUrl(this.item, this.imageApi)
        break
    }
    this.option.loadSrc = this.image
  }

  build() {
    Column() {
      RelativeContainer() {
        ImageKnifeComponent({
          imageKnifeOption: this.option
        })
          .aspectRatio(2 / 3)
          .width('100%')
          .borderColor($r('app.color.border_1'))
          .borderWidth(0.5)
          .borderRadius(AppThemeTokens.mediaRadius(this.appUIState.themeStyle))
          .backgroundColor('#CBD2D3')
        // .backgroundBlurStyle(BlurStyle.BACKGROUND_THICK)
        if (this.score) {
          Text(this.score.toFixed(1) + '')
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
        if (this.tags && this.tags.length > 0) {
          Row({ space: 4 }) {
            ForEach(this.tags, (tag: string) => {
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

      Text(this.title)
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
      Text(this.subtitle)
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
