import { HMRouter } from "@hadss/hmrouter";
import { webview } from '@kit.ArkWeb';
import { RouterConsts } from "../../common/RouterConsts";
import { ActionBar } from "../../component/ActionBar";
import { AppRouteDestination } from "../../component/AppRouteDestination";

@HMRouter({
  pageUrl: RouterConsts.PrivacyPage,
  useNavDst: true
})
@ComponentV2
export struct PrivacyPage {
  webviewController: webview.WebviewController = new webview.WebviewController();

  @Builder
  private pageContent(showLegacyActionBar: boolean) {
    Column() {
      if (showLegacyActionBar) {
        ActionBar({
          title: '隐私协议'
        })
      }

      // Web内容区域
      Web({
        src: 'https://agreement-drcn.hispace.dbankcloud.cn/index.html?lang=zh&agreementId=1915508141466875008',
        controller: this.webviewController
      })
        .darkMode(WebDarkMode.Auto)
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
    .backgroundColor(showLegacyActionBar ? $r('app.color.start_window_background') : Color.Transparent)
    .expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.BOTTOM])
  }

  build() {
    AppRouteDestination({
      title: '隐私协议',
      contentBuilder: () => {
        this.pageContent(false)
      },
      legacyContentBuilder: () => {
        this.pageContent(true)
      }
    })
  }
}
