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
import { ActionBar } from "../../../component/ActionBar"
import { AppThemeSurfaceResolver } from "../../../theme/AppThemeSurfaceResolver"
import { AppThemeTokens } from "../../../theme/AppThemeTokens"
import { HomeInsetsPolicy } from "../HomeInsetsPolicy"

@ComponentV2
export struct MineTab {
  @Param fromHome: boolean = false
  @Param contentBottomInset?: number = undefined
  @Param compactTopInset: boolean = false
  @Param destinationOwnsTitleBar: boolean = false
  @Local ui: MineUIState = new MineUIState(this.getUIContext())
  @Local vm: MineViewModel = new MineViewModel(this.ui)

  private topSpacerHeight(): number {
    if (this.destinationOwnsTitleBar || this.compactTopInset) {
      return 0
    }
    return this.vm.appUIState.safeTop + UIConstants.ACTION_BAR_HEIGHT
  }

  private scrollTail(): number {
    return HomeInsetsPolicy.scrollTail(this.contentBottomInset, this.vm.appUIState.safeBottom)
  }

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable)
  }

  aboutToAppear(): void {
    HMRouterMgr.getCurrentLifecycleOwner()?.addObserver(HMLifecycleState.onShown, () => {
      this.vm.getAppearanceName()
      this.vm.getPlayerEngineName()
    })
    this.vm.init()
    this.vm.getAppearanceName()
    this.vm.getPlayerEngineName()
  }

  @Builder
  private profileHeaderContent() {
    Row({ space: 12 }) {
      ImageKnifeComponent({
        imageKnifeOption: {
          loadSrc: this.ui.userAvatar,
          border: {
            radius: 32
          },
          placeholderSrc: $r('app.media.default_avatar')
        }
      }).width(64).height(64)
      Column({ space: 4 }) {
        Row({ space: 12 }) {
          Text(this.ui.userName)
            .fontColor(AppThemeTokens.isNative(this.vm.appUIState.themeStyle) ?
              $r('sys.color.font_primary') : $r('app.color.text_primary'))
            .fontSize(AppThemeTokens.isNative(this.vm.appUIState.themeStyle) ? 20 : 24)
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
          .fontColor(AppThemeTokens.isNative(this.vm.appUIState.themeStyle) ?
            $r('sys.color.font_secondary') : $r('app.color.text_1'))
          .fontSize(14)
          .fontWeight(FontWeight.Medium)
        Text(this.ui.serverVersion)
          .fontColor(AppThemeTokens.isNative(this.vm.appUIState.themeStyle) ?
            $r('sys.color.font_tertiary') : $r('app.color.text_2'))
          .fontSize(12)
          .fontWeight(FontWeight.Medium)
      }
      .alignItems(HorizontalAlign.Start)
    }
    .width('100%')
    .padding({
      left: AppThemeTokens.isNative(this.vm.appUIState.themeStyle) ? 14 : 8,
      right: AppThemeTokens.isNative(this.vm.appUIState.themeStyle) ? 14 : 8,
      top: AppThemeTokens.isNative(this.vm.appUIState.themeStyle) ? 14 : 0,
      bottom: AppThemeTokens.isNative(this.vm.appUIState.themeStyle) ? 14 : 8
    })
  }

  @Builder
  private nativeProfileHeader() {
    Column() {
      this.profileHeaderContent()
    }
    .width('100%')
    .borderRadius(AppThemeTokens.profileRadius(this.vm.appUIState.themeStyle))
    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  private feiniuProfileHeader() {
    Column() {
      this.profileHeaderContent()
    }
    .width('100%')
    .borderRadius(AppThemeTokens.profileRadius(this.vm.appUIState.themeStyle))
  }

  @Builder
  private profileHeader() {
    if (this.useNativeSurface()) {
      this.nativeProfileHeader()
    } else {
      this.feiniuProfileHeader()
    }
  }

  @Builder
  private feiniuTopHeaderBackground() {
    Image($r('app.media.bg_mine'))
      .width('100%')
      .aspectRatio(780 / 556)
      .backgroundColor($r('app.color.bg_main'))
      .visibility(this.vm.appUIState.currentBreakpoint.includes('sm') &&
        AppThemeTokens.showMineBackground(this.vm.appUIState.themeStyle) ?
        Visibility.Visible : Visibility.None)
  }

  build() {
    Stack() {
      Image($r('app.media.bg_mine'))
        .width('100%')
        .objectFit(ImageFit.Contain)
        .visibility(this.vm.appUIState.currentBreakpoint.includes('sm') &&
          AppThemeTokens.showMineBackground(this.vm.appUIState.themeStyle) ?
          Visibility.Visible : Visibility.None)
      Scroll() {
        Column({ space: AppThemeTokens.groupSpacing(this.vm.appUIState.themeStyle) }) {
          Blank().height(this.topSpacerHeight())
          this.profileHeader()

          ListGroup({
            items: [new BaseGroupItem('切换账号', $r('app.media.ic_account_switch'), '', true,
              $r('app.string.account_switch_desc')),
              new BaseGroupItem('网(Web)易通', $r('app.media.web'), '', true,
                $r('app.string.web_itong_desc'))],
            onItemClick: (index) => {
              switch (index) {
                case 0: {
                  HMRouterMgr.to(RouterConsts.AccountPage).push()
                  break
                }
                case 1: {
                  HMRouterMgr.to(RouterConsts.WebITongPage).push()
                  break
                }
              }
            }
          })
          if (this.ui.isAdministrator) {
            ListGroup({
              items: [new BaseGroupItem($r('app.string.management_title'), $r('app.media.ic_setting'), '', true,
                $r('app.string.management_entry_desc'))],
              onItemClick: () => {
                HMRouterMgr.to(RouterConsts.ManagementDashboardPage).push()
              }
            })
          }
          ListGroup({
            items: [new BaseGroupItem($r('app.string.appearance_title'), $r('app.media.theme'),
              this.ui.appearanceName, true, $r('app.string.theme_native_desc'))],
            onItemClick: () => {
              HMRouterMgr.to(RouterConsts.ColorModePage).push()
            }
          })
          ListGroup({
            items: [new BaseGroupItem('播放器引擎', $r('app.media.movie'), this.ui.playerEngineName, true,
              $r('app.string.player_engine_desc'))],
            onItemClick: () => {
              HMRouterMgr.to(RouterConsts.PlayerEnginePage).push()
            }
          })
          ListGroup({
            items: [
              new BaseGroupItem('隐私协议', $r('app.media.ic_yinsi')),
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
                  HMRouterMgr.push({
                    pageUrl: RouterConsts.AboutPage
                  })
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
        }
        .width('100%')
        .constraintSize({ maxWidth: 680 })
        .padding({
          left: 16,
          right: 16,
          bottom: this.scrollTail()
        })
      }
      .width('100%')
      .height('100%')
      .scrollable(ScrollDirection.Vertical)
      .scrollBar(BarState.Off)
      .align(Alignment.Top)

      if (!this.compactTopInset && !this.destinationOwnsTitleBar) {
        if (!this.useNativeSurface()) {
          Stack() {
            this.feiniuTopHeaderBackground()
          }
          .width('100%')
          .align(Alignment.Top)
          .height(UIConstants.ACTION_BAR_HEIGHT + this.vm.appUIState.safeTop)
          .clip(true)
        }

        Row() {
          ActionBar({
            title: '我的',
            showBackButton: true
          }).visibility(this.fromHome ? Visibility.Visible : Visibility.None)
          //Blank()
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
        .justifyContent(FlexAlign.Center)
      }
    }
    .backgroundColor(this.destinationOwnsTitleBar ? Color.Transparent :
      AppThemeSurfaceResolver.routeBackground(
        this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable))
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
