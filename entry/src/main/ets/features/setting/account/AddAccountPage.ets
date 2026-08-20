import { AppStorageV2 } from '@kit.ArkUI'
import { HMRouter, HMRouterMgr } from '@hadss/hmrouter'
import { RouterConsts } from '../../../common/RouterConsts'
import { ActionBar } from '../../../component/ActionBar'
import { AppRouteDestination } from '../../../component/AppRouteDestination'
import { AppUIState } from '../../../entity/AppUIState'
import { AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver'
import { AppThemeTokens } from '../../../theme/AppThemeTokens'

@HMRouter({
  pageUrl: RouterConsts.AddAccountPage,
  useNavDst: true
})
@ComponentV2
export struct AddAccountPage {
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle,
      this.appUIState.nativeThemeAvailable
    )
  }

  private openJellyfin(): void {
    HMRouterMgr.to(RouterConsts.JellyfinConnectPage).push()
  }

  private openFeiniuVideo(): void {
    HMRouterMgr.to(RouterConsts.FeiniuVideoConnectPage).push()
  }

  @Builder
  private providerRow(title: ResourceStr, description: ResourceStr, logo: Resource,
    nativeSurface: boolean, onClick: () => void) {
    Row({ space: 14 }) {
      Image(logo)
        .width(48)
        .height(48)
        .objectFit(ImageFit.Contain)
        .borderRadius(12)
      Column({ space: 5 }) {
        Text(title)
          .fontSize(16)
          .fontWeight(FontWeight.Medium)
          .fontColor(nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary'))
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        Text(description)
          .fontSize(13)
          .fontColor(nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_2'))
          .maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
      SymbolGlyph($r('sys.symbol.chevron_right'))
        .fontSize(16)
        .fontColor([nativeSurface ? $r('sys.color.icon_secondary') : $r('app.color.text_2')])
    }
    .width('100%')
    .height(80)
    .padding({ left: 16, right: 16 })
    .onClick(onClick)
  }

  @Builder
  private nativeProviderList() {
    Column() {
      this.providerRow(
        $r('app.string.provider_jellyfin_name'),
        $r('app.string.provider_jellyfin_description'),
        $r('app.media.app_icon'),
        true,
        () => this.openJellyfin()
      )
      Divider()
        .color($r('sys.color.comp_divider'))
        .margin({ left: 16, right: 16 })
      this.providerRow(
        $r('app.string.feiniu_video_service_name'),
        $r('app.string.provider_feiniu_video_description'),
        $r('app.media.feiniu_video_logo'),
        true,
        () => this.openFeiniuVideo()
      )
    }
    .width('calc(100% - 32vp)')
    .constraintSize({ maxWidth: 560 })
    .borderRadius(AppThemeTokens.groupRadius(this.appUIState.themeStyle))
    .clip(true)
    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
    .margin({ top: 16 })
  }

  @Builder
  private feiniuProviderList() {
    Column({ space: 12 }) {
      Row() {
        this.providerRow(
          $r('app.string.provider_jellyfin_name'),
          $r('app.string.provider_jellyfin_description'),
          $r('app.media.app_icon'),
          false,
          () => this.openJellyfin()
        )
      }
      .backgroundColor($r('app.color.bg_1'))
      .borderRadius(8)
      Row() {
        this.providerRow(
          $r('app.string.feiniu_video_service_name'),
          $r('app.string.provider_feiniu_video_description'),
          $r('app.media.feiniu_video_logo'),
          false,
          () => this.openFeiniuVideo()
        )
      }
      .backgroundColor($r('app.color.bg_1'))
      .borderRadius(8)
    }
    .width('calc(100% - 32vp)')
    .padding({ top: 16 })
  }

  @Builder
  private pageContent(showLegacyActionBar: boolean) {
    Column() {
      if (showLegacyActionBar) {
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
      if (showLegacyActionBar) {
        this.feiniuProviderList()
      } else {
        this.nativeProviderList()
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(showLegacyActionBar ? $r('app.color.bg_main') : Color.Transparent)
  }

  build() {
    AppRouteDestination({
      title: $r('app.string.account_add_title'),
      contentBuilder: () => {
        this.pageContent(false)
      },
      legacyContentBuilder: () => {
        this.pageContent(true)
      }
    })
  }
}
