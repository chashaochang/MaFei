import { HMRouter } from '@hadss/hmrouter';
import { AppStorageV2 } from '@kit.ArkUI';
import { RouterConsts } from '../../common/RouterConsts';
import { ActionBar } from '../../component/ActionBar';
import { AppRouteDestination } from '../../component/AppRouteDestination';
import { AppUIState } from '../../entity/AppUIState';
import { AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver';
import { AppThemeTokens } from '../../theme/AppThemeTokens';

@HMRouter({
  pageUrl: RouterConsts.AddGroupPage,
  useNavDst: true
})
@ComponentV2
export struct AddGroupPage {
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;

  @Builder
  private qrContent(nativeSurface: boolean) {
    Image($r('app.media.qrcode')).width(240).borderRadius(16).padding(8).backgroundColor('#fcfcfc')
    Blank().height(30)
    Text('扫描二维码加入群聊')
      .fontSize(16)
      .fontColor(nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary'))
    Blank().height(20)
  }

  @Builder
  private nativeQrGroup() {
    Column() {
      this.qrContent(true)
    }
    .width('100%')
    .padding(20)
    .borderRadius(AppThemeTokens.groupRadius(this.appUIState.themeStyle))
    .clip(true)
    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  private feiniuQrGroup() {
    Column() {
      this.qrContent(false)
    }
    .width('100%')
    .padding(20)
    .backgroundColor($r('app.color.bg_1'))
    .borderRadius(20)
  }

  @Builder
  private pageContent(showLegacyActionBar: boolean) {
    Column() {
      if (showLegacyActionBar) {
        ActionBar({
          title: '加入群聊'
        })
      }
      if (showLegacyActionBar) {
        this.feiniuQrGroup()
      } else {
        this.nativeQrGroup()
      }

      Blank()
    }
    .width('100%')
    .height('100%')
    .backgroundColor(showLegacyActionBar ? $r('app.color.bg_main') : Color.Transparent)
    .padding({ left: 16, right: 16 })
  }

  build() {
    AppRouteDestination({
      title: '加入群聊',
      contentBuilder: () => {
        this.pageContent(false)
      },
      legacyContentBuilder: () => {
        this.pageContent(true)
      }
    })
  }
}
