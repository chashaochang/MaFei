import { HMRouter, HMRouterMgr } from "@hadss/hmrouter";
import { RouterConsts } from "../../common/RouterConsts";
import { ActionBar } from "../../component/ActionBar";
import { AppRouteDestination, AppRouteDestinationOptions } from "../../component/AppRouteDestination";
import { ServerDiscoveryUIState } from "./ServerDiscoveryUIState";
import { ServerDiscoveryViewModel } from "./ServerDiscoveryViewModel";
import { ServerSuggestion } from "./ServerSuggestion";
import { AppStorageV2 } from "@kit.ArkUI";
import { AppUIState } from "../../entity/AppUIState";
import { AppThemeSurfaceResolver } from "../../theme/AppThemeSurfaceResolver";
import { AppThemeTokens } from "../../theme/AppThemeTokens";
import { HdsNavDestination, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'

@HMRouter({
  pageUrl: RouterConsts.ServerDiscoveryPage,
  useNavDst: true
})
@ComponentV2
export struct ServerDiscoveryPage {
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
  @Local ui: ServerDiscoveryUIState = new ServerDiscoveryUIState(this.getUIContext())
  @Local vm: ServerDiscoveryViewModel = new ServerDiscoveryViewModel(this.ui)
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;

  aboutToAppear(): void {
    this.vm.init()
  }

  private routeOptions(): AppRouteDestinationOptions {
    return {
        title: '局域网内设备'
      }
  }

  build() {
    if (this.routeDestination.usesHdsDestination()) {
      HdsNavDestination() {
        Column() {
          Blank().height(this.routeDestination.nativeContentTopInset(this.routeOptions()))
          Stack() {
            if (this.routeDestination.usesNativeChrome()) {
              ServerDiscoveryPageContent({
                ui: this.ui,
                appUIState: this.appUIState,
                onSelectServer: (address: string): void => {
                  HMRouterMgr.pop({ param: address })
                }
              })
            } else {
              ServerDiscoveryPageContent({
                ui: this.ui,
                appUIState: this.appUIState,
                showLegacyActionBar: true,
                onSelectServer: (address: string): void => {
                  HMRouterMgr.pop({ param: address })
                }
              })
            }
          }
          .width('100%')
          .layoutWeight(1)
        }
        .width('100%')
        .height('100%')
      }
      .backgroundColor(this.routeDestination.hdsBackgroundColor())
      .titleMode(HdsNavDestinationTitleMode.MINI)
      .titleBar(this.routeDestination.nativeTitleBarOptions(this.routeOptions()))
      .hideTitleBar(this.routeDestination.hdsTitleBarHidden(this.routeOptions()))
      .hideBackButton(!this.routeDestination.backButtonVisible(this.routeOptions()))
      .hideToolBar(true)
      .bindToScrollable(this.routeDestination.hdsBoundScrollers(this.routeOptions()))
      .onWillAppear(() => this.routeDestination.helper.onWillAppear())
      .onAppear(() => this.routeDestination.helper.onAppear())
      .onWillShow(() => this.routeDestination.helper.onWillShow())
      .onShown(() => this.routeDestination.helper.onShown())
      .onWillHide(() => this.routeDestination.helper.onWillHide())
      .onHidden(() => this.routeDestination.helper.onHidden())
      .onWillDisappear(() => this.routeDestination.helper.onWillDisappear())
      .onDisAppear(() => this.routeDestination.helper.onDisAppear())
      .onReady((ctx: NavDestinationContext) => this.routeDestination.helper.onReady(ctx))
      .onBackPressed(() => this.routeDestination.handleBackPressed(this.routeOptions()))
      .onResult((result: ESObject) => this.routeDestination.helper.onResult(result))
      .onActive((reason: NavDestinationActiveReason) => this.routeDestination.helper.onActive(reason))
      .onInactive((reason: NavDestinationActiveReason) => this.routeDestination.helper.onInactive(reason))
      .ignoreLayoutSafeArea(
        this.routeDestination.nativeSafeAreaTypes(),
        this.routeDestination.nativeSafeAreaEdges()
      )
      .width('100%')
      .height('100%')
    } else {
      NavDestination() {
        ServerDiscoveryPageContent({
          ui: this.ui,
          appUIState: this.appUIState,
          showLegacyActionBar: true,
          onSelectServer: (address: string): void => {
            HMRouterMgr.pop({ param: address })
          }
        })
      }
      .attributeModifier(this.routeDestination.legacyModifier(this.routeOptions()))
      .gestureModifier(this.routeDestination.gestureModifier)
      .width('100%')
      .height('100%')
    }
  }
}

@ComponentV2
struct ServerDiscoveryContent {
  @Require @Param ui: ServerDiscoveryUIState
  @Param nativeSurface: boolean = false
  @Event onSelectServer: (address: string) => void = () => {}

  build() {
    if (this.ui.loading) {
      Column({ space: 12 }) {
        LoadingProgress()
          .width(32)
          .height(32)
        Text('正在扫描局域网设备...')
          .fontColor(this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1'))
          .fontSize(14)
      }
      .width('100%')
      .height('100%')
      .justifyContent(FlexAlign.Center)
    } else if (this.ui.errorText.length > 0) {
      Column({ space: 12 }) {
        Text('扫描失败')
          .fontColor(this.nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary'))
          .fontSize(18)
          .fontWeight(FontWeight.Medium)
        Text(this.ui.errorText)
          .fontColor(this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1'))
          .fontSize(14)
          .textAlign(TextAlign.Center)
      }
      .width('100%')
      .height('100%')
      .justifyContent(FlexAlign.Center)
    } else if (this.ui.serverSuggestions.length > 0) {
      Column() {
        Text('找到' + this.ui.serverSuggestions.length + '个设备')
          .fontColor(this.nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary'))
          .fontSize(18)
          .fontWeight(FontWeight.Medium)
        Column() {
          Blank().height(8)
          Column({ space: 8 }) {
            Repeat<ServerSuggestion>(this.ui.serverSuggestions).each((obj: RepeatItem<ServerSuggestion>) => {
              ServerDiscoveryItem({
                serverSuggestion: obj.item,
                nativeSurface: this.nativeSurface,
                onClickServer: (): void => {
                  this.onSelectServer(obj.item.address)
                }
              })
            })
          }
          .width('100%')
          .layoutWeight(1)
          .margin({ bottom: 16 })
          .borderRadius(4)
        }
        .height('100%')
      }
      .height('100%')
      .alignItems(HorizontalAlign.Start)
    } else {
      Column({ space: 12 }) {
        Text('未发现局域网设备')
          .fontColor(this.nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary'))
          .fontSize(18)
          .fontWeight(FontWeight.Medium)
        Text('请确认设备与 Jellyfin 服务器在同一网络下，或直接手动输入服务器地址。')
          .fontColor(this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1'))
          .fontSize(14)
          .textAlign(TextAlign.Center)
      }
      .width('100%')
      .height('100%')
      .justifyContent(FlexAlign.Center)
    }
  }
}

@ComponentV2
struct ServerDiscoveryArea {
  @Require @Param ui: ServerDiscoveryUIState
  @Require @Param appUIState: AppUIState
  @Param nativeSurface: boolean = false
  @Event onSelectServer: (address: string) => void = () => {}

  build() {
    if (this.nativeSurface) {
      Column() {
        ServerDiscoveryContent({
          ui: this.ui,
          nativeSurface: true,
          onSelectServer: this.onSelectServer
        })
      }
      .width('100%')
      .layoutWeight(1)
      .borderRadius(AppThemeTokens.groupRadius(this.appUIState.themeStyle))
      .clip(true)
      .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
      .margin({ left: 16, right: 16, bottom: 16 })
    } else {
      Column() {
        ServerDiscoveryContent({
          ui: this.ui,
          onSelectServer: this.onSelectServer
        })
      }
      .width('100%')
      .layoutWeight(1)
      .padding({ left: 16, right: 16, bottom: 16 })
      .alignItems(HorizontalAlign.Center)
    }
  }
}

@ComponentV2
struct ServerDiscoveryPageContent {
  @Require @Param ui: ServerDiscoveryUIState
  @Require @Param appUIState: AppUIState
  @Param showLegacyActionBar: boolean = false
  @Event onSelectServer: (address: string) => void = () => {}

  build() {
    Column() {
      if (this.showLegacyActionBar) {
        ActionBar({
          title: '局域网内设备',
          showBackButton: true,
        })
      }
      ServerDiscoveryArea({
        ui: this.ui,
        appUIState: this.appUIState,
        nativeSurface: !this.showLegacyActionBar,
        onSelectServer: this.onSelectServer
      })
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.showLegacyActionBar ? $r('app.color.bg_main') : Color.Transparent)
  }
}


@ComponentV2
struct ServerDiscoveryItem {
  @Param @Require serverSuggestion: ServerSuggestion
  @Param nativeSurface: boolean = false
  @Event onClickServer: () => void = () => {}

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
