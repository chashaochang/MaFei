import { HMLifecycleState, HMRouterMgr } from "@hadss/hmrouter"
import { ImageKnifeComponent } from "@ohos/imageknifepro"
import { UIConstants } from "../../../common/UIConstants"
import { MineUIState } from "./MineUIState"
import { MineViewModel } from "./MineViewModel"
import { BaseGroupItem, ListGroup } from "../../../component/ListGroup"
import { RouterConsts } from "../../../common/RouterConsts"
import { getUIAbilityContext, playMediaRefs } from "../../../common/CommonFunc"
import { wantConstant } from "@kit.AbilityKit"
import { ActionBar } from "../../../component/ActionBar"
import { AppThemeSurfaceResolver } from "../../../theme/AppThemeSurfaceResolver"
import { AppThemeTokens } from "../../../theme/AppThemeTokens"
import { HomeInsetsPolicy } from "../HomeInsetsPolicy"
import { MineHistorySection } from "./MineHistorySection"
import { MineHistoryItem } from "./MineHistoryPlanner"
import { ThemeStyle } from "../../../entity/ThemeStyle"

@ComponentV2
struct MineProfileHeader {
  @Require @Param userAvatar: string
  @Require @Param userName: string
  @Require @Param userPolicy: string
  @Require @Param serverName: string
  @Require @Param serverVersion: string
  @Require @Param connectionDescription: string
  @Require @Param nativeSurface: boolean
  @Require @Param themeStyle: ThemeStyle

  build() {
    Row({ space: 12 }) {
      ImageKnifeComponent({
        imageKnifeOption: {
          loadSrc: this.userAvatar,
          border: { radius: this.nativeSurface ? 24 : 32 },
          placeholderSrc: $r('app.media.default_avatar')
        }
      })
        .width(this.nativeSurface ? 48 : 64)
        .height(this.nativeSurface ? 48 : 64)
      Column({ space: 4 }) {
        Row({ space: 12 }) {
          Text(this.userName)
            .fontColor(AppThemeTokens.isNative(this.themeStyle) ?
              $r('sys.color.font_primary') : $r('app.color.text_primary'))
            .fontSize(AppThemeTokens.isNative(this.themeStyle) ? 20 : 24)
            .fontWeight(FontWeight.Medium)
          Text(this.userPolicy)
            .fontColor($r('app.color.color_main'))
            .fontSize(12)
            .padding({ left: 6, right: 6, top: 2, bottom: 2 })
            .backgroundColor(this.nativeSurface ?
              $r('sys.color.comp_background_tertiary') : $r('app.color.primary_light_hover'))
            .borderRadius(4)
        }
        Text(this.serverName)
          .fontColor(AppThemeTokens.isNative(this.themeStyle) ?
            $r('sys.color.font_secondary') : $r('app.color.text_1'))
          .fontSize(14)
          .fontWeight(FontWeight.Medium)
        if (this.serverVersion.length > 0) {
          Text(this.serverVersion)
            .fontColor(this.nativeSurface ? $r('sys.color.font_tertiary') : $r('app.color.text_2'))
            .fontSize(12)
            .fontWeight(FontWeight.Medium)
        }
        if (this.connectionDescription.length > 0) {
          Text(this.connectionDescription)
            .fontColor(this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1'))
            .fontSize(12)
            .fontWeight(FontWeight.Medium)
            .padding({ left: 8, right: 8, top: 3, bottom: 3 })
            .backgroundColor(this.nativeSurface ?
              $r('sys.color.comp_background_tertiary') : $r('app.color.bg_hover'))
            .borderRadius(8)
        }
      }
      .alignItems(HorizontalAlign.Start)
    }
    .width('100%')
    .padding({ left: 16, right: 16, top: 14, bottom: 14 })
    .borderRadius(AppThemeTokens.profileRadius(this.themeStyle))
    .backgroundColor(this.nativeSurface ?
      AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
  }
}

@ComponentV2
export struct MineTab {
  @Param fromHome: boolean = false
  @Param contentBottomInset?: number = undefined
  @Param compactTopInset: boolean = false
  @Param destinationOwnsTitleBar: boolean = false
  @Param contentScroller: Scroller = new Scroller()
  @Event onAdministratorStateChange: (available: boolean) => void = (_available: boolean) => {}
  @Local ui: MineUIState = new MineUIState(this.getUIContext())
  @Local vm: MineViewModel = new MineViewModel(this.ui)

  private topSpacerHeight(): number {
    if (this.destinationOwnsTitleBar) {
      return 0
    }
    if (this.compactTopInset) {
      return Math.max(0, this.vm.appUIState.safeTop) + UIConstants.ACTION_BAR_HEIGHT
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
      this.vm.refreshHistory()
    })
    this.vm.init()
    this.vm.refreshHistory()
  }

  @Monitor('ui.isAdministrator', 'ui.managementAvailable')
  onManagementAvailabilityChange(): void {
    this.onAdministratorStateChange(this.ui.isAdministrator && this.ui.managementAvailable)
  }

  build() {
    Stack() {
      Image($r('app.media.bg_mine'))
        .width('100%')
        .objectFit(ImageFit.Contain)
        .visibility(this.vm.appUIState.currentBreakpoint.includes('sm') &&
          AppThemeTokens.showMineBackground(this.vm.appUIState.themeStyle) ?
          Visibility.Visible : Visibility.None)
      Scroll(this.contentScroller) {
        Column({ space: AppThemeTokens.groupSpacing(this.vm.appUIState.themeStyle) }) {
          Blank().height(this.topSpacerHeight())
          MineProfileHeader({
            userAvatar: this.ui.userAvatar,
            userName: this.ui.userName,
            userPolicy: this.ui.userPolicy,
            serverName: this.ui.serverName,
            serverVersion: this.ui.serverVersion,
            connectionDescription: this.ui.connectionDescription,
            nativeSurface: this.useNativeSurface(),
            themeStyle: this.vm.appUIState.themeStyle
          })

          if (this.ui.managementAvailable && this.ui.isAdministrator) {
            ListGroup({
              items: [new BaseGroupItem($r('app.string.management_title'), $r('app.media.ic_setting'))],
              onItemClick: () => {
                HMRouterMgr.to(RouterConsts.ManagementDashboardPage).push()
              }
            })
          }

          MineHistorySection({
            state: this.ui.historyState,
            items: this.ui.historyItems,
            currentBreakpoint: this.vm.appUIState.currentBreakpoint,
            nativeSurface: this.useNativeSurface(),
            themeStyle: this.vm.appUIState.themeStyle,
            onItemClick: (item: MineHistoryItem) => {
              playMediaRefs([item.mediaRef], this.getUIContext().getHostContext()!)
            },
            onRetry: () => {
              this.vm.refreshHistory()
            },
            onMore: () => {
              HMRouterMgr.to(RouterConsts.MineHistoryPage).push()
            }
          })

          if (this.ui.webITongAvailable) {
            ListGroup({
              items: [new BaseGroupItem('切换账号', $r('app.media.ic_account_switch')),
                new BaseGroupItem('网(Web)易通', $r('app.media.web'))],
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
          } else {
            ListGroup({
              items: [new BaseGroupItem('切换账号', $r('app.media.ic_account_switch'))],
              onItemClick: () => {
                HMRouterMgr.to(RouterConsts.AccountPage).push()
              }
            })
          }
          ListGroup({
            items: [new BaseGroupItem(
              $r('app.string.settings_title'), $r('app.media.ic_setting'), '', true, '',
              $r('sys.symbol.gearshape'))],
            onItemClick: () => {
              HMRouterMgr.to(RouterConsts.SettingPage).push()
            }
          })
          Column({ space: 8 }) {
            ListGroup({
              items: [
                new BaseGroupItem('隐私协议', $r('app.media.ic_yinsi')),
                new BaseGroupItem('加入群聊', $r('app.media.add_group'))
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
                }
              }
            })

            if (this.ui.versionName.length > 0) {
              Text(this.ui.versionName)
                .width('100%')
                .fontSize(12)
                .fontColor(AppThemeTokens.isNative(this.vm.appUIState.themeStyle) ?
                  $r('sys.color.font_tertiary') : $r('app.color.text_2'))
                .textAlign(TextAlign.Center)
            }
          }
          .width('100%')

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
            Image($r('app.media.bg_mine'))
              .width('100%')
              .aspectRatio(780 / 556)
              .backgroundColor($r('app.color.bg_main'))
              .visibility(this.vm.appUIState.currentBreakpoint.includes('sm') &&
                AppThemeTokens.showMineBackground(this.vm.appUIState.themeStyle) ?
                Visibility.Visible : Visibility.None)
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

}
