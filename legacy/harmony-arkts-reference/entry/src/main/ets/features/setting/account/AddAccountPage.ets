import { AppStorageV2 } from '@kit.ArkUI'
import { HMRouter, HMRouterMgr } from '@hadss/hmrouter'
import { RouterConsts } from '../../../common/RouterConsts'
import { ActionBar } from '../../../component/ActionBar'
import { AppRouteDestination, AppRouteDestinationOptions } from '../../../component/AppRouteDestination'
import { AppUIState } from '../../../entity/AppUIState'
import { AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver'
import { AppThemeTokens } from '../../../theme/AppThemeTokens'
import { HdsNavDestination, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'
import { Constants } from '../../../utils/Constants'

@HMRouter({
  pageUrl: RouterConsts.AddAccountPage,
  useNavDst: true
})
@ComponentV2
export struct AddAccountPage {
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!

  private openJellyfin(): void {
    HMRouterMgr.to(RouterConsts.JellyfinConnectPage)
      .withNavigation(Constants.NAVIGATION_ID)
      .push()
  }

  private openFeiniuVideo(): void {
    HMRouterMgr.to(RouterConsts.FeiniuVideoConnectPage)
      .withNavigation(Constants.NAVIGATION_ID)
      .push()
  }

  private routeOptions(): AppRouteDestinationOptions {
    return {
        title: $r('app.string.account_add_title')
      }
  }

  build() {
    if (this.routeDestination.usesHdsDestination()) {
      HdsNavDestination() {
        Column() {
          Blank().height(this.routeDestination.nativeContentTopInset(this.routeOptions()))
          Stack() {
            if (this.routeDestination.usesNativeChrome()) {
              AddAccountContent({
                appUIState: this.appUIState,
                onOpenJellyfin: (): void => {
                  this.openJellyfin()
                },
                onOpenFeiniuVideo: (): void => {
                  this.openFeiniuVideo()
                }
              })
            } else {
              AddAccountContent({
                appUIState: this.appUIState,
                showLegacyActionBar: true,
                onOpenJellyfin: (): void => {
                  this.openJellyfin()
                },
                onOpenFeiniuVideo: (): void => {
                  this.openFeiniuVideo()
                }
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
        AddAccountContent({
          appUIState: this.appUIState,
          showLegacyActionBar: true,
          onOpenJellyfin: (): void => {
            this.openJellyfin()
          },
          onOpenFeiniuVideo: (): void => {
            this.openFeiniuVideo()
          }
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
struct ProviderRow {
  @Require @Param title: ResourceStr
  @Require @Param description: ResourceStr
  @Require @Param logo: Resource
  @Param nativeSurface: boolean = false
  @Event onPressed: () => void = () => {}

  build() {
    Row({ space: 14 }) {
      Image(this.logo)
        .width(48)
        .height(48)
        .objectFit(ImageFit.Contain)
        .borderRadius(12)
      Column({ space: 5 }) {
        Text(this.title)
          .fontSize(16)
          .fontWeight(FontWeight.Medium)
          .fontColor(this.nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary'))
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        Text(this.description)
          .fontSize(13)
          .fontColor(this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_2'))
          .maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
      SymbolGlyph($r('sys.symbol.chevron_right'))
        .fontSize(16)
        .fontColor([this.nativeSurface ? $r('sys.color.icon_secondary') : $r('app.color.text_2')])
    }
    .width('100%')
    .height(80)
    .padding({ left: 16, right: 16 })
    .onClick((): void => {
      this.onPressed()
    })
  }
}

@ComponentV2
struct ProviderList {
  @Require @Param appUIState: AppUIState
  @Param nativeSurface: boolean = false
  @Event onOpenJellyfin: () => void = () => {}
  @Event onOpenFeiniuVideo: () => void = () => {}

  build() {
    if (this.nativeSurface) {
      Column() {
        ProviderRow({
          title: $r('app.string.provider_jellyfin_name'),
          description: $r('app.string.provider_jellyfin_description'),
          logo: $r('app.media.jellyfin_logo'),
          nativeSurface: true,
          onPressed: this.onOpenJellyfin
        })
        Divider()
          .color($r('sys.color.comp_divider'))
          .margin({ left: 16, right: 16 })
        ProviderRow({
          title: $r('app.string.feiniu_video_service_name'),
          description: $r('app.string.provider_feiniu_video_description'),
          logo: $r('app.media.feiniu_video_logo'),
          nativeSurface: true,
          onPressed: this.onOpenFeiniuVideo
        })
      }
      .width('calc(100% - 32vp)')
      .constraintSize({ maxWidth: 560 })
      .borderRadius(AppThemeTokens.groupRadius(this.appUIState.themeStyle))
      .clip(true)
      .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
      .margin({ top: 16 })
    } else {
      Column({ space: 12 }) {
        Row() {
          ProviderRow({
            title: $r('app.string.provider_jellyfin_name'),
            description: $r('app.string.provider_jellyfin_description'),
            logo: $r('app.media.jellyfin_logo'),
            onPressed: this.onOpenJellyfin
          })
        }
        .backgroundColor($r('app.color.bg_1'))
        .borderRadius(8)
        Row() {
          ProviderRow({
            title: $r('app.string.feiniu_video_service_name'),
            description: $r('app.string.provider_feiniu_video_description'),
            logo: $r('app.media.feiniu_video_logo'),
            onPressed: this.onOpenFeiniuVideo
          })
        }
        .backgroundColor($r('app.color.bg_1'))
        .borderRadius(8)
      }
      .width('calc(100% - 32vp)')
      .padding({ top: 16 })
    }
  }
}

@ComponentV2
struct AddAccountContent {
  @Require @Param appUIState: AppUIState
  @Param showLegacyActionBar: boolean = false
  @Event onOpenJellyfin: () => void = () => {}
  @Event onOpenFeiniuVideo: () => void = () => {}

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle,
      this.appUIState.nativeThemeAvailable
    )
  }

  build() {
    Column() {
      if (this.showLegacyActionBar) {
        ActionBar({
          title: $r('app.string.account_add_title'),
          showBackButton: true
        })
      }
      Text($r('app.string.account_add_description'))
        .fontSize(14)
        .fontColor(this.useNativeSurface() ?
          $r('sys.color.font_secondary') : $r('app.color.text_2'))
        .width('100%')
        .padding({ top: 16, left: 20, right: 20, bottom: 4 })
      ProviderList({
        appUIState: this.appUIState,
        nativeSurface: !this.showLegacyActionBar,
        onOpenJellyfin: this.onOpenJellyfin,
        onOpenFeiniuVideo: this.onOpenFeiniuVideo
      })
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.showLegacyActionBar ? $r('app.color.bg_main') : Color.Transparent)
  }
}
