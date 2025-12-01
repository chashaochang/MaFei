import { HMLifecycleState, HMRouter, HMRouterMgr } from '@hadss/hmrouter';
import { RouterConsts } from '../../common/RouterConsts';
import { UIConstants } from '../../common/UIConstants';
import { WebITongUIState } from './WebITongUIState';
import { WebITongViewModel } from './WebITongViewModel';
import { ApiClient } from '../../network/ApiClient';
import { ActivityEventHandler } from '../../events/ActivityEventHandler';

@HMRouter({
  pageUrl: RouterConsts.WebITongPage
})
@ComponentV2
export struct WebITongPage {
  @Local ui: WebITongUIState = new WebITongUIState(this.getUIContext());
  @Local vm: WebITongViewModel = new WebITongViewModel(this.ui)
  @Consumer() activityEventHandler: ActivityEventHandler =
    new ActivityEventHandler(this.vm.appUIState.context!.eventHub)

  aboutToAppear(): void {
    HMRouterMgr.getCurrentLifecycleOwner()?.addObserver(HMLifecycleState.onBackPressed,()=>{
      if (this.vm.controller?.accessBackward()) {
        this.vm.controller?.backward()
        return true
      }
      return false
    })
  }

  build() {
    Stack() {
      Column() {
        Stack()
          .width('100%')
          .height(this.vm.appUIState.safeTop)
          .backgroundColor(this.ui.progress === 100 ? $r('app.color.start_window_background') : Color.Transparent)
        Progress({
          value: this.ui.progress
        }).visibility(this.ui.progress === 100 && !this.vm.isFirst ? Visibility.None : Visibility.Visible)
        Web({
          controller: this.vm.controller,
          src: ApiClient.Instance().basePath
        })
          .backgroundColor($r('app.color.start_window_background'))
          .darkMode(WebDarkMode.Auto)
          .domStorageAccess(true)
          .zoomAccess(true)
          .fileAccess(true)
          .mixedMode(MixedMode.All)
          .cacheMode(CacheMode.Default)
          .javaScriptAccess(true)
          .onControllerAttached(() => {
            this.vm.registerJavaScriptProxy(this.activityEventHandler)
          })
          .onPageEnd(() => {
            this.vm.injectCredentials()
          })
          .onProgressChange((event) => {
            this.ui.progress = event.newProgress
          })
          .onTitleReceive((event) => {
            this.ui.pageTitle = event.title
          })
          .onInterceptRequest((event) => {
            if (!event) {
              return;
            }
            // 此处匹配自己想要加载的本地离线资源，进行资源拦截替换，绕过跨域
            let path = event.request.getRequestUrl()
            return this.vm.interceptRequest(path);
          })
          .onHttpErrorReceive((e) => {
            if (e.request.getRequestUrl() == ApiClient.Instance().basePath) {
              //this.handleError()
            }
          })
          .onSslErrorEventReceive((event) => {
            event.handler.handleCancel()
            //this.handleError()
          })
          .onConsole((e) => {
            // if (e.message.getMessage().includes('redirecting to login page')) {
            //   HMRouterMgr.to('/LoginPage').onResult((info) => {
            //     if (info.result) {
            //       let res = info.result['loginRes'] as AuthenticationResult
            //       this.controller.runJavaScript("JSON.parse(window.localStorage.getItem('jellyfin_credentials'))")
            //         .then(result => {
            //           const credentials: Credentials | null = JSON.parse(result)
            //           if (credentials) {
            //             let server = credentials.Servers[0]
            //             server.UserId = res.User!.Id!
            //             server.AccessToken = res.AccessToken!
            //             this.controller.runJavaScript('window.localStorage.setItem("jellyfin_credentials", JSON.stringify(' +
            //             JSON.stringify(credentials) + '))')
            //               .then(() => {
            //                 this.controller.runJavaScript('window.localStorage.setItem("user-' + res.User?.Id + '-' +
            //                   res.User?.ServerId + '", JSON.stringify(' +
            //                 JSON.stringify(res.User) + '))')
            //                   .then(() => {
            //                     this.apiClientController.setupUser(this.server.id, server.UserId, server.AccessToken)
            //                     AppStorage.setOrCreate('userId', server.UserId)
            //                     this.controller.loadUrl(ApiClient.Instance().basePath)
            //                   }).catch()
            //               }).catch()
            //           }
            //         })
            //         .catch(() => {
            //           throw Error('Failed to extract credentials')
            //         })
            //     }
            //   }).push()
            // }
            return false
          })
      }
      .width('100%')
      .height('100%')

      Row({ space: 12 }) {
        Stack() {
          SymbolGlyph($r('sys.symbol.chevron_left'))
            .fontSize(20)
            .fontColor([$r('app.color.text_primary')])
            .fontWeight(FontWeight.Bold)
        }
        .align(Alignment.Center)
        .width(40)
        .height(40)
        .borderRadius(26)
        .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THIN)
        .onClick(() => {
          if (this.vm.controller?.accessBackward()) {
            this.vm.controller?.backward()
          } else {
            HMRouterMgr.pop()
          }
        })

        Text(this.ui.pageTitle)
          .fontSize(14)
          .fontColor($r('app.color.text_primary'))
          .fontWeight(FontWeight.Medium)
          .maxLines(1)
          .textOverflow({
            overflow: TextOverflow.Ellipsis
          })
          .layoutWeight(1)
          .height(40)
          .borderWidth(1)
          .borderColor($r('app.color.border'))
          .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THIN)
          .borderRadius(6)
          .padding({
            left: 12, right: 12
          })

        Stack() {
          SymbolGlyph($r('sys.symbol.arrow_clockwise'))
            .fontSize(20)
            .fontColor([$r('app.color.text_primary')])
            .fontWeight(FontWeight.Bold)
        }
        .align(Alignment.Center)
        .width(40)
        .height(40)
        .borderRadius(26)
        .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THIN)
        .onClick(() => {
          try {
            this.vm.controller?.refresh()
          } catch (error) {
            console.log("刷新失败:" + JSON.stringify(error));
          }
        })
      }
      .alignItems(VerticalAlign.Center)
      .height(UIConstants.ACTION_BAR_HEIGHT)
      .layoutWeight(1)
      .padding({
        left: 16,
        right: 16,
      })
      .margin({
        bottom: this.vm.appUIState.safeBottom
      })

      // .backgroundBlurStyle(BlurStyle.BACKGROUND_THIN)
    }.width('100%')
    .height('100%')
    .align(Alignment.Bottom)
  }
}