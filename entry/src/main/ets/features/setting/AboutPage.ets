import { HMRouter } from "@hadss/hmrouter";
import bundleManager from '@ohos.bundle.bundleManager';
import { BusinessError } from '@ohos.base';
import common from '@ohos.app.ability.common';
import Want from '@ohos.app.ability.Want';
import { RouterConsts } from "../../common/RouterConsts";
import { ActionBar } from "../../component/ActionBar";
import { AppRouteDestination } from '../../component/AppRouteDestination';
import { AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver';

@HMRouter({
  pageUrl: RouterConsts.AboutPage,
  useNavDst: true
})
@Component
export struct AboutPage{
  @State versionName: string = '';
  private readonly githubUrl: string = 'https://github.com/chashaochang/MaFei';
  private readonly appGalleryUrl: string = 'https://appgallery.huawei.com/app/detail?id=cn.xiaobai.mafei';
  private readonly contentScroller: Scroller = new Scroller();
  
  aboutToAppear() {
    this.getAppVersion();
  }
  
  private getAppVersion() {
    try {
      let context = getContext(this) as common.UIAbilityContext;
      
      bundleManager.getBundleInfoForSelf(bundleManager.BundleFlag.GET_BUNDLE_INFO_WITH_APPLICATION)
        .then((bundleInfo) => {
          const resolvedVersionName = bundleInfo.versionName.trim();
          if (resolvedVersionName.length > 0) {
            this.versionName = `v${resolvedVersionName}`;
          }
        })
        .catch((error: BusinessError) => {
          console.error('Failed to get bundle info:', error);
        });
    } catch (error) {
      console.error('Failed to get context:', error);
    }
  }

  private openUrl(url: string) {
    try {
      let context = getContext(this) as common.UIAbilityContext;
      let want: Want = {
        action: 'ohos.want.action.viewData',
        entities: ['entity.system.browsable'],
        uri: url
      };
      context.startAbility(want).then(() => {
        console.info('Open URL success');
      }).catch((error: BusinessError) => {
        console.error('Open URL failed:', error);
      });
    } catch (error) {
      console.error('Failed to open URL:', error);
    }
  }

  private primaryTextColor(native: boolean): ResourceColor {
    return native ? $r('sys.color.font_primary') : Color.White
  }

  private secondaryTextColor(native: boolean): ResourceColor {
    return native ? $r('sys.color.font_secondary') : '#999999'
  }

  private dividerColor(native: boolean): ResourceColor {
    return native ? $r('sys.color.comp_divider') : '#333333'
  }
  
  @Builder
  InfoItem(title: string, content: string, native: boolean) {
    Row() {
      Text(title)
        .fontSize(15)
        .fontColor(this.secondaryTextColor(native))
        .width(96)
        .textAlign(TextAlign.Start)
      
      Text(content)
        .fontSize(15)
        .fontColor(this.primaryTextColor(native))
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

  @Builder
  LinkItem(title: string, content: string, url: string, native: boolean) {
    Column() {
      Row() {
        Text(title)
          .fontSize(15)
          .fontColor(this.secondaryTextColor(native))
          .textAlign(TextAlign.Start)
        Blank()
      }
      .width('100%')
      .margin({ bottom: 8 })

      Text(content)
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
    .onClick(() => {
      this.openUrl(url);
    })
  }

  @Builder
  SectionTitle(title: string, native: boolean) {
    Text(title)
      .fontSize(20)
      .fontColor(this.primaryTextColor(native))
      .fontWeight(FontWeight.Medium)
      .width('100%')
      .textAlign(TextAlign.Start)
      .margin({ left: 4, bottom: 12 })
  }

  @Builder
  private relatedLinksContent(native: boolean) {
    Column() {
      this.LinkItem('华为应用市场', this.appGalleryUrl, this.appGalleryUrl, native)
      Divider().color(this.dividerColor(native)).margin({ left: 16, right: 16 })
      this.LinkItem('GitHub', this.githubUrl, this.githubUrl, native)
    }
  }

  @Builder
  private nativeRelatedLinksGroup() {
    Column() {
      this.relatedLinksContent(true)
    }
    .width('100%')
    .borderRadius(16)
    .clip(true)
    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  private feiniuRelatedLinksGroup() {
    Column() {
      this.relatedLinksContent(false)
    }
    .width('100%')
    .backgroundColor('#1a1a1a')
    .borderRadius(12)
  }

  @Builder
  private relatedLinksGroup(native: boolean) {
    if (native) {
      this.nativeRelatedLinksGroup()
    } else {
      this.feiniuRelatedLinksGroup()
    }
  }

  @Builder
  private licenseContent(native: boolean) {
    Column() {
      this.InfoItem('许可证', 'GPL-3.0-or-later', native)
    }
  }

  @Builder
  private nativeLicenseGroup() {
    Column() {
      this.licenseContent(true)
    }
    .width('100%')
    .borderRadius(16)
    .clip(true)
    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  private feiniuLicenseGroup() {
    Column() {
      this.licenseContent(false)
    }
    .width('100%')
    .backgroundColor('#1a1a1a')
    .borderRadius(12)
  }

  @Builder
  private licenseGroup(native: boolean) {
    if (native) {
      this.nativeLicenseGroup()
    } else {
      this.feiniuLicenseGroup()
    }
  }

  @Builder
  private acknowledgementsContent(native: boolean) {
    Column() {
      this.InfoItem('Jellyfin', '感谢 Jellyfin 开源项目', native)
      Divider().color(this.dividerColor(native)).margin({ left: 16, right: 16 })
      this.InfoItem('mpv', '感谢 mpv 开源项目', native)
      Divider().color(this.dividerColor(native)).margin({ left: 16, right: 16 })
      this.InfoItem('OpenHarmony', '感谢 OpenHarmony 开源项目', native)
    }
  }

  @Builder
  private nativeAcknowledgementsGroup() {
    Column() {
      this.acknowledgementsContent(true)
    }
    .width('100%')
    .borderRadius(16)
    .clip(true)
    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  private feiniuAcknowledgementsGroup() {
    Column() {
      this.acknowledgementsContent(false)
    }
    .width('100%')
    .backgroundColor('#1a1a1a')
    .borderRadius(12)
  }

  @Builder
  private acknowledgementsGroup(native: boolean) {
    if (native) {
      this.nativeAcknowledgementsGroup()
    } else {
      this.feiniuAcknowledgementsGroup()
    }
  }

  @Builder
  private pageContent(showLegacyActionBar: boolean) {
    Column(){
      if (showLegacyActionBar) {
        ActionBar({
          title: '关于马飞'
        })
      }
      
      // 内容区域
      Scroll(this.contentScroller) {
        Column({ space: 20 }) {
          // 应用图标和名称
          Column({ space: 12 }) {
            Image($r('app.media.app_icon'))
              .width(88)
              .height(88)
              .borderRadius(20)
            
            Text($r('app.string.app_name'))
              .fontSize(18)
              .fontColor(this.primaryTextColor(!showLegacyActionBar))
              .fontWeight(FontWeight.Medium)

            if (this.versionName.length > 0) {
              Text(this.versionName)
                .fontSize(14)
                .fontColor(this.secondaryTextColor(!showLegacyActionBar))
            }

            Text('HarmonyOS 平台的 Jellyfin 第三方客户端')
              .fontSize(14)
              .fontColor(this.secondaryTextColor(!showLegacyActionBar))
              .textAlign(TextAlign.Center)
          }
          .width('100%')
          .padding({ top: 36, bottom: 12 })

          Column({ space: 0 }) {
            this.SectionTitle('相关链接', !showLegacyActionBar)
            this.relatedLinksGroup(!showLegacyActionBar)
          }

          Column({ space: 0 }) {
            this.SectionTitle('开源许可', !showLegacyActionBar)
            this.licenseGroup(!showLegacyActionBar)
          }

          Column({ space: 0 }) {
            this.SectionTitle('鸣谢', !showLegacyActionBar)
            this.acknowledgementsGroup(!showLegacyActionBar)
          }
        }
        .width('100%')
        .constraintSize({ maxWidth: showLegacyActionBar ? '100%' : 560 })
        .padding({ left: 16, right: 16, bottom: 32 })
      }
      .layoutWeight(1)
      .scrollBar(BarState.Off)
      
    }.width('100%')
    .height('100%')
    .backgroundColor(showLegacyActionBar ? $r('app.color.bg_main') : Color.Transparent)
  }

  build() {
    AppRouteDestination({
      title: '关于马飞',
      scrollControllers: [this.contentScroller],
      contentBuilder: () => {
        this.pageContent(false)
      },
      legacyContentBuilder: () => {
        this.pageContent(true)
      }
    })
  }
}
