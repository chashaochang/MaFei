import { HMRouterMgr } from "@hadss/hmrouter"
import { ImageKnifeComponent } from "@ohos/imageknifepro"
import { ImageUrlsApi } from "sdk"
import { BaseItemPerson, ImageType, PersonKind } from "sdk/src/main/ets/generated-client/models"
import { RouterConsts } from "../common/RouterConsts"
import { ApiClient } from "../network/ApiClient"
import { AppStorageV2 } from "@kit.ArkUI"
import { AppUIState } from "../entity/AppUIState"
import { AppThemeSurfaceResolver } from "../theme/AppThemeSurfaceResolver"

@ComponentV2
export struct CastView {
  @Param @Require peopleList: Array<BaseItemPerson>
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!
  imageUrlsApi: ImageUrlsApi = new ImageUrlsApi()

  build() {
    Column() {
      Text('演职人员')
        .fontSize(15)
        .fontWeight(FontWeight.Medium)
        .fontColor($r('app.color.text_primary'))
        .margin({ left: 16 })
      List({ space: 12 }) {
        ListItem().width(8)
        ForEach(this.peopleList, (item: BaseItemPerson) => {
          ListItem() {
            Column({ space: 8 }) {
              ImageKnifeComponent({
                imageKnifeOption: {
                  loadSrc: ApiClient.Instance().basePath +
                  this.imageUrlsApi.getItemImageUrlById(item.Id || '', ImageType.Primary),
                  objectFit: ImageFit.Cover,
                  border: {
                    radius: 33
                  },
                  placeholderSrc: $r('app.media.ic_avatar'),
                }
              })
                .width(66)
                .height(66)
                .borderRadius(33)
                .backgroundColor(AppThemeSurfaceResolver.mediaPlaceholderBackground(
                  this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable))

              Text(item.Name)
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
              Text(item.Type == PersonKind.Actor ? `饰${item.Role}` :
                (item.Type == PersonKind.Director ? '导演' : item.Role))
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
              HMRouterMgr.to(RouterConsts.CastDetailPage).withParam(item.Id).push()
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
