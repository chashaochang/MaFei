import { HMRouter, HMRouterMgr } from "@hadss/hmrouter";
import { RouterConsts } from "../../common/RouterConsts";
import { ActionBar } from "../../component/ActionBar";
import { AppRouteDestination } from "../../component/AppRouteDestination";
import { ServerDiscoveryUIState } from "./ServerDiscoveryUIState";
import { ServerDiscoveryViewModel } from "./ServerDiscoveryViewModel";
import { ServerSuggestion } from "./ServerSuggestion";
import { AppStorageV2 } from "@kit.ArkUI";
import { AppUIState } from "../../entity/AppUIState";
import { AppThemeSurfaceResolver } from "../../theme/AppThemeSurfaceResolver";
import { AppThemeTokens } from "../../theme/AppThemeTokens";

@HMRouter({
  pageUrl: RouterConsts.ServerDiscoveryPage,
  useNavDst: true
})
@ComponentV2
export struct ServerDiscoveryPage {
  @Local ui: ServerDiscoveryUIState = new ServerDiscoveryUIState(this.getUIContext())
  @Local vm: ServerDiscoveryViewModel = new ServerDiscoveryViewModel(this.ui)
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;

  aboutToAppear(): void {
    this.vm.init()
  }

  @Builder
  private discoveryContent(nativeSurface: boolean) {
    if (this.ui.loading) {
      Column({ space: 12 }) {
        LoadingProgress()
          .width(32)
          .height(32)
        Text('正在扫描局域网设备...')
          .fontColor(nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1'))
          .fontSize(14)
      }
      .width('100%')
      .layoutWeight(1)
      .justifyContent(FlexAlign.Center)
    } else if (this.ui.errorText.length > 0) {
      Column({ space: 12 }) {
        Text('扫描失败')
          .fontColor(nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary'))
          .fontSize(18)
          .fontWeight(FontWeight.Medium)
        Text(this.ui.errorText)
          .fontColor(nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1'))
          .fontSize(14)
          .textAlign(TextAlign.Center)
      }
      .width('100%')
      .layoutWeight(1)
      .justifyContent(FlexAlign.Center)
    } else if (this.ui.serverSuggestions.length > 0) {
      Column() {
        Text('找到' + this.ui.serverSuggestions.length + '个设备')
          .fontColor(nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary'))
          .fontSize(18)
          .fontWeight(FontWeight.Medium)
        Column() {
          Blank().height(8)
          Column({ space: 8 }) {
            Repeat<ServerSuggestion>(this.ui.serverSuggestions).each((obj: RepeatItem<ServerSuggestion>) => {
              ServerDiscoveryItem({
                serverSuggestion: obj.item,
                nativeSurface: nativeSurface,
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
          .fontColor(nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary'))
          .fontSize(18)
          .fontWeight(FontWeight.Medium)
        Text('请确认设备与 Jellyfin 服务器在同一网络下，或直接手动输入服务器地址。')
          .fontColor(nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1'))
          .fontSize(14)
          .textAlign(TextAlign.Center)
      }
      .width('100%')
      .layoutWeight(1)
      .justifyContent(FlexAlign.Center)
    }
  }

  @Builder
  private nativeDiscoveryArea() {
    Column() {
      Column() {
        this.discoveryContent(true)
      }
      .width('100%')
      .layoutWeight(1)
      .borderRadius(AppThemeTokens.groupRadius(this.appUIState.themeStyle))
      .clip(true)
      .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
    }
    .width('100%')
    .layoutWeight(1)
    .padding({ left: 16, right: 16, bottom: 16 })
  }

  @Builder
  private feiniuDiscoveryArea() {
    Column() {
      this.discoveryContent(false)
    }
    .width('100%')
    .layoutWeight(1)
    .padding({ left: 16, right: 16, bottom: 16 })
    .alignItems(HorizontalAlign.Center)
  }

  @Builder
  private pageContent(showLegacyActionBar: boolean) {
    Column() {
      if (showLegacyActionBar) {
        ActionBar({
          title: '局域网内设备',
          showBackButton: true,
        })
      }
      if (showLegacyActionBar) {
        this.feiniuDiscoveryArea()
      } else {
        this.nativeDiscoveryArea()
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(showLegacyActionBar ? $r('app.color.bg_main') : Color.Transparent)
  }

  build() {
    AppRouteDestination({
      title: '局域网内设备',
      contentBuilder: () => {
        this.pageContent(false)
      },
      legacyContentBuilder: () => {
        this.pageContent(true)
      }
    })
  }
}


@ComponentV2
struct ServerDiscoveryItem {
  @Param @Require serverSuggestion: ServerSuggestion
  @Param @Require onClickServer: () => void
  @Param nativeSurface: boolean = false

  build() {
    Column({
      space: 4
    }) {
      Text(this.serverSuggestion.name)
        .fontColor(this.nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary'))
        .fontSize(16)
      Text(this.serverSuggestion.address)
        .fontColor(this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1'))
        .fontSize(12)
    }
    .width('100%')
    .padding(16)
    .alignItems(HorizontalAlign.Start)
    .onClick(() => {
      this.onClickServer()
    })
    .backgroundColor(this.nativeSurface ? Color.Transparent : $r('app.color.bg_1'))
    .borderRadius(8)
  }
}
