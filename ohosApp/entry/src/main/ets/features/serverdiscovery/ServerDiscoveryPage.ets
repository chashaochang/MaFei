import { HMRouter, HMRouterMgr } from "@hadss/hmrouter";
import { RouterConsts } from "../../common/RouterConsts";
import { ActionBar } from "../../component/ActionBar";
import { ServerDiscoveryUIState } from "./ServerDiscoveryUIState";
import { ServerDiscoveryViewModel } from "./ServerDiscoveryViewModel";
import { ServerSuggestion } from "./ServerSuggestion";

@HMRouter({
  pageUrl: RouterConsts.ServerDiscoveryPage
})
@ComponentV2
export struct ServerDiscoveryPage {
  @Local ui: ServerDiscoveryUIState = new ServerDiscoveryUIState(this.getUIContext())
  @Local vm: ServerDiscoveryViewModel = new ServerDiscoveryViewModel(this.ui)

  aboutToAppear(): void {
    this.vm.init()
  }

  build() {
    Column() {
      ActionBar({
        title: '局域网内设备',
        showBackButton: true,
      })
      Column() {
        if (this.ui.loading) {
          Column({ space: 12 }) {
            LoadingProgress()
              .width(32)
              .height(32)
            Text('正在扫描局域网设备...')
              .fontColor($r('app.color.text_1'))
              .fontSize(14)
          }
          .width('100%')
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
        } else if (this.ui.errorText.length > 0) {
          Column({ space: 12 }) {
            Text('扫描失败')
              .fontColor($r('app.color.text_primary'))
              .fontSize(18)
              .fontWeight(FontWeight.Medium)
            Text(this.ui.errorText)
              .fontColor($r('app.color.text_1'))
              .fontSize(14)
              .textAlign(TextAlign.Center)
          }
          .width('100%')
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
        } else if (this.ui.serverSuggestions.length > 0) {
          Column() {
            Text('找到' + this.ui.serverSuggestions.length + '个设备')
              .fontColor($r('app.color.text_primary'))
              .fontSize(18)
              .fontWeight(FontWeight.Medium)
            Column() {
              Blank().height(8)
              Column({
                space: 8
              }) {
                Repeat<ServerSuggestion>(this.ui.serverSuggestions).each((obj: RepeatItem<ServerSuggestion>) => {
                  ServerDiscoveryItem({
                    serverSuggestion: obj.item,
                    onClickServer: () => {
                      HMRouterMgr.pop({
                        param: obj.item.address
                      })
                    }
                  })
                })
              }
              .width('100%')
              .layoutWeight(1)
              .margin({ bottom: 16 })
              .borderRadius(4)
            }.height('100%')
          }
          .layoutWeight(1)
          .alignItems(HorizontalAlign.Start)
        } else {
          Column({ space: 12 }) {
            Text('未发现局域网设备')
              .fontColor($r('app.color.text_primary'))
              .fontSize(18)
              .fontWeight(FontWeight.Medium)
            Text('请确认设备与 Jellyfin 服务器在同一网络下，或直接手动输入服务器地址。')
              .fontColor($r('app.color.text_1'))
              .fontSize(14)
              .textAlign(TextAlign.Center)
          }
          .width('100%')
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
        }
      }
      .width('100%')
      .layoutWeight(1)
      .padding({ left: 16, right: 16, bottom: 16 })
      .alignItems(HorizontalAlign.Center)
    }
    .backgroundColor($r('app.color.bg_main'))
  }
}


@ComponentV2
struct ServerDiscoveryItem {
  @Param @Require serverSuggestion: ServerSuggestion
  @Param @Require onClickServer: () => void

  build() {
    Column({
      space: 4
    }) {
      Text(this.serverSuggestion.name).fontColor($r('app.color.text_primary')).fontSize(16)
      Text(this.serverSuggestion.address).fontColor($r('app.color.text_1')).fontSize(12)
    }
    .width('100%')
    .padding(16)
    .alignItems(HorizontalAlign.Start)
    .onClick(() => {
      this.onClickServer()
    })
    .backgroundColor($r('app.color.bg_1'))
    .borderRadius(8)
  }
}
