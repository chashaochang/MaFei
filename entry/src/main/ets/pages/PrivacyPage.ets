import { webview } from '@kit.ArkWeb';
import { ActionBar } from '../component/ActionBar';
import { WebView } from '../ui/components/WebView';
import { CommonConstants } from '../utils/CommonConstants';

@Entry
@Component
struct PrivacyPage {
  @State webViewController: webview.WebviewController = new webview.WebviewController();

  build() {
    Column() {
      ActionBar({
        title: '隐私政策',
        onBackClick:()=>{
          this.getUIContext().getRouter().back();
        }
      })
      WebView({
        url: 'https://jellyfin.xiaobaihome.cn/yinsi.html',
        webViewController: $webViewController,
      })
    }
    .width(CommonConstants.FULL_PERCENT)
    .height(CommonConstants.FULL_PERCENT)
    .backgroundColor($r('sys.color.ohos_id_color_sub_background'))
    .padding({ top: AppStorage.get<number>('statusBarHeight'), bottom: $r('app.float.xxl_padding_margin') })
  }
}