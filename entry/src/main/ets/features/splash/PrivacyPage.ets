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
        src: 'https://agreement-drcn.hispace.dbankcloud.cn/index.html?lang=zh&agreementId=1915508141466875008',
        controller: this.webviewController
      })
        .darkMode(WebDarkMode.Auto)
        .forceDarkAccess(true)
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
    .backgroundColor($r('app.color.start_window_background'))
    .expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.BOTTOM])
  }
}