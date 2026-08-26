import { HMRouterMgr } from "@hadss/hmrouter"
import { ImageKnifeComponent, ImageKnifeOption } from "@ohos/imageknifepro"
import { RouterConsts } from "../common/RouterConsts"
import { AppStorageV2 } from "@kit.ArkUI"
import { AppUIState } from "../entity/AppUIState"
import { createMediaImageKnifeOption } from "../media/catalog/MediaImageKnifeAdapter"
import { MediaPersonCredit, MediaPersonKind } from "../media/catalog/MediaCatalogModels"
import { AppThemeSurfaceResolver } from "../theme/AppThemeSurfaceResolver"

@ComponentV2
export struct CastView {
  @Param @Require peopleList: Array<MediaPersonCredit>
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!

  private imageOption(item: MediaPersonCredit): ImageKnifeOption {
    const option = createMediaImageKnifeOption(item.primaryImage, item.primaryImage?.url || '')
    option.objectFit = ImageFit.Cover
    option.border = { radius: 33 }
    option.placeholderSrc = $r('app.media.ic_avatar')
    return option
  }

  build() {
    Column() {
      Text('演职人员')
        .fontSize(15)
        .fontWeight(FontWeight.Medium)
        .fontColor($r('app.color.text_primary'))
        .margin({ left: 16 })
      List({ space: 12 }) {
        ListItem().width(8)
        ForEach(this.peopleList, (item: MediaPersonCredit) => {
          ListItem() {
            Column({ space: 8 }) {
              if (item.primaryImage && item.primaryImage.url.length > 0) {
                ImageKnifeComponent({
                  imageKnifeOption: this.imageOption(item)
                })
                  .width(66)
                  .height(66)
                  .borderRadius(33)
                  .backgroundColor(AppThemeSurfaceResolver.mediaPlaceholderBackground(
                    this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable))
              } else {
                Stack() {
                  SymbolGlyph($r('sys.symbol.person_fill'))
                    .fontSize(30)
                    .fontColor([$r('sys.color.icon_secondary')])
                }
                .width(66)
                .height(66)
                .borderRadius(33)
                .backgroundColor(AppThemeSurfaceResolver.mediaPlaceholderBackground(
                  this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable))
              }

              Text(item.name)
                .fontColor($r('app.color.text_primary'))
                .fontSize(12)
                .fontWeight(FontWeight.Medium)
                .width(66)
                .maxLines(1)
                .ellipsisMode(EllipsisMode.END)
                .textOverflow({
                  overflow: TextOverflow.Ellipsis
                })
                .textAlign(TextAlign.Center)
              Text(item.kind === MediaPersonKind.Actor ? `饰${item.role}` :
                (item.kind === MediaPersonKind.Director ? '导演' : item.role))
                .fontColor($r('app.color.text_2'))
                .fontSize(10)
                .width(66)
                .maxLines(1)
                .ellipsisMode(EllipsisMode.END)
                .textOverflow({
                  overflow: TextOverflow.Ellipsis
                })
                .textAlign(TextAlign.Center)
            }.onClick(() => {
              HMRouterMgr.to(RouterConsts.CastDetailPage).withParam(item.personRef.id).push()
            })
          }
        })
        ListItem().width(8)
      }
      .height(130)
      .width('100%')
      .listDirection(Axis.Horizontal)
      .scrollBar(BarState.Off)
      .padding({
        top: 10, bottom: 10
      })
    }.alignItems(HorizontalAlign.Start)
  }
}
