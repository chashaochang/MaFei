import { HMRouter, HMRouterMgr } from "@hadss/hmrouter";
import { AppStorageV2 } from '@kit.ArkUI';
import { AppRouteDestination, AppRouteDestinationOptions } from '../../component/AppRouteDestination';
import { AppUIState } from '../../entity/AppUIState';
import { AppThemeTokens } from '../../theme/AppThemeTokens';
import { HdsNavDestination, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'

@HMRouter({
  pageUrl: '/UserAgreement',
  useNavDst: true
})
@ComponentV2
export struct UserAgreementPage{
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;

  private routeOptions(): AppRouteDestinationOptions {
    return {
        title: '用户协议'
      }
  }

  build() {
    if (this.routeDestination.usesHdsDestination()) {
      HdsNavDestination() {
        Column() {
          Blank().height(this.routeDestination.nativeContentTopInset(this.routeOptions()))
          Stack() {
            if (this.routeDestination.usesNativeChrome()) {
              UserAgreementContent({ appUIState: this.appUIState })
            } else {
              UserAgreementContent({
                appUIState: this.appUIState,
                showLegacyActionBar: true
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
        UserAgreementContent({
          appUIState: this.appUIState,
          showLegacyActionBar: true
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
struct AgreementSection {
  @Require @Param title: string
  @Require @Param content: string
  @Require @Param primaryTextColor: ResourceColor
  @Require @Param secondaryTextColor: ResourceColor

  build() {
    Column() {
      Text(this.title)
        .fontSize(18)
        .fontColor(this.primaryTextColor)
        .fontWeight(FontWeight.Medium)
        .width('100%')
        .textAlign(TextAlign.Start)
        .margin({ top: 24, bottom: 12 })
      Text(this.content)
        .fontSize(14)
        .fontColor(this.secondaryTextColor)
        .lineHeight(22)
        .width('100%')
        .textAlign(TextAlign.Start)
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }
}

@ComponentV2
struct UserAgreementContent {
  @Require @Param appUIState: AppUIState
  @Param showLegacyActionBar: boolean = false

  private primaryTextColor(): ResourceColor {
    return AppThemeTokens.isNative(this.appUIState.themeStyle) ?
      $r('sys.color.font_primary') : Color.White
  }

  private secondaryTextColor(): ResourceColor {
    return AppThemeTokens.isNative(this.appUIState.themeStyle) ?
      $r('sys.color.font_secondary') : '#CCCCCC'
  }

  private tertiaryTextColor(): ResourceColor {
    return AppThemeTokens.isNative(this.appUIState.themeStyle) ?
      $r('sys.color.font_tertiary') : '#999999'
  }

  build() {
    Column() {
      if (this.showLegacyActionBar) {
        Row() {
          Image($r('app.media.ic_back'))
            .width(44)
            .height(44)
            .padding(12)
            .onClick((): void => {
              HMRouterMgr.pop()
            })
          Text('用户协议')
            .fontColor(this.primaryTextColor())
            .fontSize(16)
            .margin({ left: 16 })
          Blank().layoutWeight(1)
        }
        .height(56)
        .width('100%')
        .justifyContent(FlexAlign.Start)
        .backgroundColor('#202020')
      }

      Scroll() {
        Column() {
          Text('用户使用协议')
            .fontSize(24)
            .fontColor(this.primaryTextColor())
            .fontWeight(FontWeight.Bold)
            .width('100%')
            .textAlign(TextAlign.Center)
            .margin({ top: 24, bottom: 8 })
          Text('最后更新时间：2024年12月1日')
            .fontSize(12)
            .fontColor(this.tertiaryTextColor())
            .width('100%')
            .textAlign(TextAlign.Center)
            .margin({ bottom: 24 })

          AgreementSection({
            title: '1. 协议接受',
            content: '欢迎使用JellyFin客户端！通过下载、安装或使用本应用，您同意遵守本用户协议的所有条款和条件。如果您不同意本协议，请不要使用本应用。',
            primaryTextColor: this.primaryTextColor(),
            secondaryTextColor: this.secondaryTextColor()
          })
          AgreementSection({
            title: '2. 应用描述',
            content: 'JellyFin客户端是一个开源的媒体服务器客户端应用，用于连接和访问JellyFin媒体服务器。本应用允许您：\n• 浏览和播放媒体内容\n• 管理媒体库\n• 同步播放进度\n• 获取弹幕等增强功能',
            primaryTextColor: this.primaryTextColor(),
            secondaryTextColor: this.secondaryTextColor()
          })
          AgreementSection({
            title: '3. 使用条件',
            content: '您同意：\n• 仅将本应用用于合法目的\n• 不会逆向工程、反编译或试图提取源代码（除非法律明确允许）\n• 不会使用本应用进行任何违法活动\n• 尊重版权和知识产权\n• 不会干扰应用的正常运行',
            primaryTextColor: this.primaryTextColor(),
            secondaryTextColor: this.secondaryTextColor()
          })
          AgreementSection({
            title: '4. 内容责任',
            content: '您理解并同意：\n• 您对通过本应用访问的所有内容负责\n• 您必须拥有或获得适当的许可来访问和播放媒体内容\n• 我们不对您服务器上的内容承担任何责任\n• 您应遵守当地法律法规关于媒体内容的规定',
            primaryTextColor: this.primaryTextColor(),
            secondaryTextColor: this.secondaryTextColor()
          })
          AgreementSection({
            title: '5. 开源许可',
            content: '本应用基于GPL-3.0-or-later许可证开源发布。您可以：\n• 自由使用、修改和分发本应用\n• 查看和研究源代码\n• 贡献代码改进\n\n但必须遵守GPL-3.0-or-later许可证的条款。',
            primaryTextColor: this.primaryTextColor(),
            secondaryTextColor: this.secondaryTextColor()
          })
          AgreementSection({
            title: '6. 免责声明',
            content: '本应用按"现状"提供，不提供任何明示或暗示的保证。我们不保证：\n• 应用的持续可用性\n• 应用完全无错误\n• 应用满足您的特定需求\n• 数据不会丢失\n\n您使用本应用的风险由您自行承担。',
            primaryTextColor: this.primaryTextColor(),
            secondaryTextColor: this.secondaryTextColor()
          })
          AgreementSection({
            title: '7. 责任限制',
            content: '在法律允许的最大范围内，我们不对以下情况承担责任：\n• 使用或无法使用本应用造成的任何损失\n• 数据丢失或损坏\n• 第三方服务的问题\n• 因网络问题导致的服务中断',
            primaryTextColor: this.primaryTextColor(),
            secondaryTextColor: this.secondaryTextColor()
          })
          AgreementSection({
            title: '8. 第三方服务',
            content: '本应用可能集成第三方服务（如弹弹play API）。这些服务有各自的使用条款，您在使用时应遵守相关条款。我们不对第三方服务的可用性或质量负责。',
            primaryTextColor: this.primaryTextColor(),
            secondaryTextColor: this.secondaryTextColor()
          })
          AgreementSection({
            title: '9. 协议修改',
            content: '我们保留随时修改本协议的权利。重大变更会在应用中通知您。继续使用应用即表示您接受修改后的协议。',
            primaryTextColor: this.primaryTextColor(),
            secondaryTextColor: this.secondaryTextColor()
          })
          AgreementSection({
            title: '10. 终止',
            content: '您可以随时停止使用本应用并删除它。如果您违反本协议，我们有权终止您的使用权限。',
            primaryTextColor: this.primaryTextColor(),
            secondaryTextColor: this.secondaryTextColor()
          })
          AgreementSection({
            title: '11. 适用法律',
            content: '本协议受中华人民共和国法律管辖。如有争议，应通过友好协商解决；协商不成的，可向有管辖权的人民法院提起诉讼。',
            primaryTextColor: this.primaryTextColor(),
            secondaryTextColor: this.secondaryTextColor()
          })
          AgreementSection({
            title: '12. 联系方式',
            content: '如果您对本协议有任何疑问，请联系我们：\n• QQ群：991893385\n• GitHub：在项目仓库中提交问题',
            primaryTextColor: this.primaryTextColor(),
            secondaryTextColor: this.secondaryTextColor()
          })
        }
        .width('100%')
        .constraintSize({ maxWidth: this.showLegacyActionBar ? '100%' : 720 })
        .padding({ left: 16, right: 16, bottom: 32 })
      }
      .layoutWeight(1)
      .scrollBar(BarState.Auto)
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.showLegacyActionBar ? '#101010' : Color.Transparent)
    .expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.BOTTOM])
  }
}
