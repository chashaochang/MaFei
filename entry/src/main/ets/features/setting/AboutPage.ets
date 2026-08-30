import { HMRouter } from '@hadss/hmrouter';
import bundleManager from '@ohos.bundle.bundleManager';
import { BusinessError } from '@ohos.base';
import common from '@ohos.app.ability.common';
import Want from '@ohos.app.ability.Want';
import { RouterConsts } from '../../common/RouterConsts';
import { ActionBar } from '../../component/ActionBar';
import { AppRouteDestination, AppRouteDestinationOptions } from '../../component/AppRouteDestination';
import { AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver';
import { HdsNavDestination, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'

enum AboutGroupKind {
  Links,
  License,
  Acknowledgements
}

@ComponentV2
struct AboutInfoRow {
  @Require @Param title: string
  @Require @Param content: string
  @Require @Param nativeSurface: boolean

  build() {
    Row() {
      Text(this.title)
        .fontSize(15)
        .fontColor(this.nativeSurface ? $r('sys.color.font_secondary') : '#999999')
        .width(96)
        .textAlign(TextAlign.Start)
      Text(this.content)
        .fontSize(15)
        .fontColor(this.nativeSurface ? $r('sys.color.font_primary') : Color.White)
        .layoutWeight(1)
        .textAlign(TextAlign.Start)
        .maxLines(2)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
    }
    .width('100%')
    .height(56)
    .padding({ left: 16, right: 16 })
    .justifyContent(FlexAlign.Start)
  }
}

@ComponentV2
struct AboutLinkRow {
  @Require @Param title: string
  @Require @Param content: string
  @Require @Param nativeSurface: boolean
  @Event onPressed: () => void = () => {
  }

  build() {
    Column() {
      Row() {
        Text(this.title)
          .fontSize(15)
          .fontColor(this.nativeSurface ? $r('sys.color.font_secondary') : '#999999')
          .textAlign(TextAlign.Start)
        Blank()
      }
      .width('100%')
      .margin({ bottom: 8 })
      Text(this.content)
        .fontSize(14)
        .fontColor('#4DA3FF')
        .width('100%')
        .textAlign(TextAlign.Start)
        .maxLines(2)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
    }
    .width('100%')
    .padding({ left: 16, right: 16, top: 12, bottom: 12 })
    .alignItems(HorizontalAlign.Start)
    .onClick(() => this.onPressed())
  }
}

@ComponentV2
struct AboutGroup {
  @Require @Param kind: AboutGroupKind
  @Require @Param nativeSurface: boolean
  @Require @Param githubUrl: string
  @Require @Param appGalleryUrl: string
  @Event onOpenUrl: (url: string) => void = () => {
  }

  private dividerColor(): ResourceColor {
    return this.nativeSurface ? $r('sys.color.comp_divider') : '#333333'
  }

  build() {
    Column() {
      if (this.kind === AboutGroupKind.Links) {
        AboutLinkRow({
          title: '华为应用市场',
          content: this.appGalleryUrl,
          nativeSurface: this.nativeSurface,
          onPressed: () => this.onOpenUrl(this.appGalleryUrl)
        })
        Divider().color(this.dividerColor()).margin({ left: 16, right: 16 })
        AboutLinkRow({
          title: 'GitHub',
          content: this.githubUrl,
          nativeSurface: this.nativeSurface,
          onPressed: () => this.onOpenUrl(this.githubUrl)
        })
      } else if (this.kind === AboutGroupKind.License) {
        AboutInfoRow({ title: '许可证', content: 'GPL-3.0-or-later', nativeSurface: this.nativeSurface })
      } else {
        AboutInfoRow({ title: 'Jellyfin', content: '感谢 Jellyfin 开源项目', nativeSurface: this.nativeSurface })
        Divider().color(this.dividerColor()).margin({ left: 16, right: 16 })
        AboutInfoRow({ title: 'mpv', content: '感谢 mpv 开源项目', nativeSurface: this.nativeSurface })
        Divider().color(this.dividerColor()).margin({ left: 16, right: 16 })
        AboutInfoRow({ title: 'OpenHarmony', content: '感谢 OpenHarmony 开源项目', nativeSurface: this.nativeSurface })
      }
    }
    .width('100%')
    .borderRadius(this.nativeSurface ? 16 : 12)
    .clip(this.nativeSurface)
    .backgroundColor(this.nativeSurface ?
      AppThemeSurfaceResolver.contentGroupBackground() : '#1a1a1a')
  }
}

@ComponentV2
export struct AboutPageSurface {
  @Require @Param showLegacyActionBar: boolean
  @Require @Param contentScroller: Scroller
  @Local versionName: string = ''
  private readonly githubUrl: string = 'https://github.com/chashaochang/MaFei'
  private readonly appGalleryUrl: string = 'https://appgallery.huawei.com/app/detail?id=cn.xiaobai.mafei'

  aboutToAppear(): void {
    try {
      bundleManager.getBundleInfoForSelf(bundleManager.BundleFlag.GET_BUNDLE_INFO_WITH_APPLICATION)
        .then((bundleInfo) => {
          const resolvedVersionName = bundleInfo.versionName.trim()
          if (resolvedVersionName.length > 0) {
            this.versionName = `v${resolvedVersionName}`
          }
        })
        .catch((error: BusinessError) => {
          console.error('Failed to get bundle info:', error)
        })
    } catch (error) {
      console.error('Failed to get bundle info:', error)
    }
  }

  private openUrl(url: string): void {
    try {
      const context = this.getUIContext().getHostContext() as common.UIAbilityContext
      const want: Want = {
        action: 'ohos.want.action.viewData',
        entities: ['entity.system.browsable'],
        uri: url
      }
      context.startAbility(want).catch((error: BusinessError) => {
        console.error('Open URL failed:', error)
      })
    } catch (error) {
      console.error('Failed to open URL:', error)
    }
  }

  private nativeSurface(): boolean {
    return !this.showLegacyActionBar
  }

  private primaryTextColor(): ResourceColor {
    return this.nativeSurface() ? $r('sys.color.font_primary') : Color.White
  }

  private secondaryTextColor(): ResourceColor {
    return this.nativeSurface() ? $r('sys.color.font_secondary') : '#999999'
  }

  build() {
    Column() {
      if (this.showLegacyActionBar) {
        ActionBar({ title: '关于马飞' })
      }
      Scroll(this.contentScroller) {
        Column({ space: 20 }) {
          Column({ space: 12 }) {
            Image($r('app.media.app_icon'))
              .width(88)
              .height(88)
              .borderRadius(20)
            Text($r('app.string.app_name'))
              .fontSize(18)
              .fontColor(this.primaryTextColor())
              .fontWeight(FontWeight.Medium)
            if (this.versionName.length > 0) {
              Text(this.versionName)
                .fontSize(14)
                .fontColor(this.secondaryTextColor())
            }
            Text('HarmonyOS 平台的 Jellyfin 第三方客户端')
              .fontSize(14)
              .fontColor(this.secondaryTextColor())
              .textAlign(TextAlign.Center)
          }
          .width('100%')
          .padding({ top: 36, bottom: 12 })

          Column() {
            Text('相关链接')
              .fontSize(20)
              .fontColor(this.primaryTextColor())
              .fontWeight(FontWeight.Medium)
              .width('100%')
              .textAlign(TextAlign.Start)
              .margin({ left: 4, bottom: 12 })
            AboutGroup({
              kind: AboutGroupKind.Links,
              nativeSurface: this.nativeSurface(),
              githubUrl: this.githubUrl,
              appGalleryUrl: this.appGalleryUrl,
              onOpenUrl: (url: string) => this.openUrl(url)
            })
          }
          Column() {
            Text('开源许可')
              .fontSize(20)
              .fontColor(this.primaryTextColor())
              .fontWeight(FontWeight.Medium)
              .width('100%')
              .textAlign(TextAlign.Start)
              .margin({ left: 4, bottom: 12 })
            AboutGroup({
              kind: AboutGroupKind.License,
              nativeSurface: this.nativeSurface(),
              githubUrl: this.githubUrl,
              appGalleryUrl: this.appGalleryUrl
            })
          }
          Column() {
            Text('鸣谢')
              .fontSize(20)
              .fontColor(this.primaryTextColor())
              .fontWeight(FontWeight.Medium)
              .width('100%')
              .textAlign(TextAlign.Start)
              .margin({ left: 4, bottom: 12 })
            AboutGroup({
              kind: AboutGroupKind.Acknowledgements,
              nativeSurface: this.nativeSurface(),
              githubUrl: this.githubUrl,
              appGalleryUrl: this.appGalleryUrl
            })
          }
        }
        .width('100%')
        .constraintSize({ maxWidth: this.showLegacyActionBar ? '100%' : 560 })
        .padding({ left: 16, right: 16, bottom: 32 })
      }
      .layoutWeight(1)
      .scrollBar(BarState.Off)
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.showLegacyActionBar ? $r('app.color.bg_main') : Color.Transparent)
  }
}

@HMRouter({
  pageUrl: RouterConsts.AboutPage,
  useNavDst: true
})
@ComponentV2
export struct AboutPage {
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
  private readonly contentScroller: Scroller = new Scroller()

  private routeOptions(): AppRouteDestinationOptions {
    return {
        title: '关于马飞',
        scrollControllers: [this.contentScroller]
      }
  }

  build() {
    if (this.routeDestination.usesHdsDestination()) {
      HdsNavDestination() {
        Column() {
          Blank().height(this.routeDestination.nativeContentTopInset(this.routeOptions()))
          Stack() {
            if (this.routeDestination.usesNativeChrome()) {
              AboutPageSurface({
                showLegacyActionBar: false,
                contentScroller: this.contentScroller
              })
            } else {
              AboutPageSurface({
                showLegacyActionBar: true,
                contentScroller: this.contentScroller
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
        AboutPageSurface({
          showLegacyActionBar: true,
          contentScroller: this.contentScroller
        })
      }
      .attributeModifier(this.routeDestination.legacyModifier(this.routeOptions()))
      .gestureModifier(this.routeDestination.gestureModifier)
      .width('100%')
      .height('100%')
    }
  }
}
