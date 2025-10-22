import { HMRouter, HMRouterMgr } from "@hadss/hmrouter";
import { webview } from '@kit.ArkWeb';
import { RouterConsts } from "../../common/RouterConsts";
import { ActionBar } from "../../component/ActionBar";

@HMRouter({
  pageUrl: RouterConsts.PrivacyPage
})
@ComponentV2
export struct PrivacyPage {
  webviewController: webview.WebviewController = new webview.WebviewController();

  build() {
    Column() {
      ActionBar({
        title: '隐私协议'
      })

      // Web内容区域
      Web({
        src: 'https://jellyfin.xiaobaihome.cn/yinsi.html',
        controller: this.webviewController
      })
        .layoutWeight(1)
        .width('100%')
        .onPageBegin((event) => {
          console.info('Privacy page loading started: ' + event?.url);
        })
        .onPageEnd((event) => {
          console.info('Privacy page loading finished: ' + event?.url);
        })
        .onErrorReceive((event) => {
          console.error('Privacy page loading error: ' + event?.error.getErrorInfo());
        })

    }.width('100%')
    .height('100%')
    .backgroundColor('#101010')
    .expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.BOTTOM])
  }
}