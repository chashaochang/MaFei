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
      if (this.ui.serverSuggestions.length > 0) {
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
                    //this.onSelectServer(obj.item.address)
                    HMRouterMgr.pop({
                      param: obj.item.address
                    })
                  }
                })
              })
              LoadingProgress().margin({
                left: 12, right: 12
              }).width(24).height(24)
            }
            .width('100%')
            .layoutWeight(1)
            .margin({ bottom: 16 })
            .borderRadius(4)
          }.height('100%')
        }.layoutWeight(1)
        .padding({
          left: 16, right: 16
        })
        .alignItems(HorizontalAlign.Start)
      }
    }.backgroundColor($r('app.color.bg_main'))
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

