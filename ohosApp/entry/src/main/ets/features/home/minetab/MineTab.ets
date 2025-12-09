import { HMLifecycleState, HMRouterMgr } from "@hadss/hmrouter"
import { ImageKnifeComponent } from "@ohos/imageknifepro"
import { UIConstants } from "../../../common/UIConstants"
import { MineUIState } from "./MineUIState"
import { MineViewModel } from "./MineViewModel"
import { BaseGroupItem, ListGroup } from "../../../component/ListGroup"
import { RouterConsts } from "../../../common/RouterConsts"
import { getUIAbilityContext } from "../../../common/CommonFunc"
import { wantConstant } from "@kit.AbilityKit"
import { AlertDialogV2, AdvancedDialogV2Button } from "@kit.ArkUI"

@ComponentV2
export struct MineTab {
  @Local ui: MineUIState = new MineUIState(this.getUIContext())
  @Local vm: MineViewModel = new MineViewModel(this.ui)

  aboutToAppear(): void {
    HMRouterMgr.getCurrentLifecycleOwner()?.addObserver(HMLifecycleState.onShown, () => {
      this.vm.getColorModeName()
    })
    this.vm.init()
    this.vm.getColorModeName()
  }

  build() {
    Stack() {
      Image($r('app.media.bg_mine')).width('100%').objectFit(ImageFit.Contain)
      Scroll() {
        Column({ space: 16 }) {
          Blank().height(this.vm.appUIState.safeTop + UIConstants.ACTION_BAR_HEIGHT)
          Row({ space: 12 }) {
            ImageKnifeComponent({
              imageKnifeOption: {
                loadSrc: this.ui.userAvatar,
                border: {
                  radius: 32
                },
                placeholderSrc: $r('app.media.default_avatar')
              }
            }).width(64).height(64).margin({ left: 12 })
            Column({ space: 4 }) {
              Row({ space: 12 }) {
                Text(this.ui.userName)
                  .fontColor($r('app.color.text_primary'))
                  .fontSize(24)
                  .fontWeight(FontWeight.Medium)
                Text(this.ui.userPolicy)
                  .fontColor($r('app.color.color_main'))
                  .fontSize(12)
                  .padding({
                    left: 6,
                    right: 6,
                    top: 2,
                    bottom: 2
                  })
                  .backgroundColor($r('app.color.primary_light_hover'))
                  .borderRadius(4)
              }

              Text(this.ui.serverName)
                .fontColor($r('app.color.text_1'))
                .fontSize(14)
                .fontWeight(FontWeight.Medium)
              Text(this.ui.serverVersion)
                .fontColor($r('app.color.text_2'))
                .fontSize(12)
                .fontWeight(FontWeight.Medium)
            }.alignItems(HorizontalAlign.Start)
          }.width('100%')
          .padding({
            left: 8, right: 8, bottom: 8
          })

          ListGroup({
            items: [new BaseGroupItem('切换账号',$r('app.media.ic_account_switch')),
              new BaseGroupItem('网(Web)易通',$r('app.media.web'))],
            onItemClick: (index) => {
              switch (index){
                case 0:{
                  HMRouterMgr.to(RouterConsts.AccountPage).push()
                  break
                }
                case 1:{
                  HMRouterMgr.to(RouterConsts.WebITongPage).push()
                  break
                }
              }
            }
          })
          ListGroup({
            items: [new BaseGroupItem('外观', $r('app.media.theme'), this.ui.colorModeName)],
            onItemClick: () => {
              HMRouterMgr.to(RouterConsts.ColorModePage).push()
            }
          })
          ListGroup({
            items: [
              new BaseGroupItem('隐私协议',$r('app.media.ic_yinsi')),
              new BaseGroupItem('加入群聊', $r('app.media.add_group')),
              new BaseGroupItem('关于我们', $r('app.media.about'), this.ui.versionName, false)
            ],
            onItemClick: (index) => {
              switch (index) {
                case 0:
                  HMRouterMgr.push({
                    pageUrl: RouterConsts.PrivacyPage
                  })
                  break
                case 1:
                  try {
                    getUIAbilityContext().startAbility({
                      uri: 'mqqapi://card/show_pslcard?src_type=internal&version=1&uin=991893385&card_type=group&source=qrcode',
                      flags: wantConstant.Flags.FLAG_START_WITHOUT_TIPS
                    }).catch(() => {
                      HMRouterMgr.to(RouterConsts.AddGroupPage).push()
                    })
                  } catch (e) {
                    HMRouterMgr.to(RouterConsts.AddGroupPage).push()
                  }
                  break
                case 2:
                  // HMRouterMgr.push({
                  //   pageUrl: RouterConsts.AboutPage
                  // })
                  break
              }
            }
          })

          ListGroup({
            items: [new BaseGroupItem('退出登录')],
            onItemClick: () => {
              this.vm.logout()
            }
          })
        }.padding({
          left: 16,
          right: 16
        })
      }
      .width('100%')
      .height('100%')
      .scrollable(ScrollDirection.Vertical)
      .scrollBar(BarState.Off)
      .align(Alignment.Top)

      //Header
      Stack() {
        Image($r('app.media.bg_mine')).width('100%').aspectRatio(780 / 556).backgroundColor($r('app.color.bg_main'))
      }.width('100%').align(Alignment.Top)
      .height(UIConstants.ACTION_BAR_HEIGHT + this.vm.appUIState.safeTop)
      .clip(true)

      Row() {
        Blank()
        // Image($r('app.media.ic_setting'))
        //   .width(40)
        //   .height(40)
        //   .borderRadius(20)
        //   .padding({
        //     top: 8, bottom: 8
        //   })
        //   .objectFit(ImageFit.Contain)
        //   .backgroundColor($r('app.color.component_bg'))
        //   .onClick(() => {
        //     HMRouterMgr.push({
        //       pageUrl: '/Setting'
        //     })
        //   })
      }
      .width('100%')
      .height(UIConstants.ACTION_BAR_HEIGHT + this.vm.appUIState.safeTop)
      .padding({
        left: 8, right: 16, top: this.vm.appUIState.safeTop
      })
      .justifyContent(FlexAlign.Center)
    }
    .backgroundColor($r('app.color.bg_main'))
    .width('100%')
    .height('100%')
    .alignContent(Alignment.Top)
  }

  @Builder
  dialogBuilder(): void {
    AlertDialogV2({
      primaryTitle: '提示',
      content: 'QQ群:991893385',
      primaryButton: new AdvancedDialogV2Button({
        content: '确认',
        action: () => {

        },
      })
    })
  }
}