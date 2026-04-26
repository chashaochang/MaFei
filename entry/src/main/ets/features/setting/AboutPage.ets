import { HMRouter } from "@hadss/hmrouter";
import bundleManager from '@ohos.bundle.bundleManager';
import { BusinessError } from '@ohos.base';
import common from '@ohos.app.ability.common';
import Want from '@ohos.app.ability.Want';
import { RouterConsts } from "../../common/RouterConsts";
import { ActionBar } from "../../component/ActionBar";

@HMRouter({
  pageUrl: RouterConsts.AboutPage
})
@Component
export struct AboutPage{
  @State versionName: string = 'v1.0.0';
  private readonly githubUrl: string = 'https://github.com/chashaochang/MaFei';
  private readonly websiteUrl: string = 'https://mafei.hmos.site/';
  private readonly appGalleryUrl: string = 'https://appgallery.huawei.com/app/detail?id=cn.xiaobai.mafei';
  private readonly mpvUrl: string = 'https://github.com/dex2oat/mpv';
  
  aboutToAppear() {
    this.getAppVersion();
  }
  
  private getAppVersion() {
    try {
      let context = getContext(this) as common.UIAbilityContext;
      
      bundleManager.getBundleInfoForSelf(bundleManager.BundleFlag.GET_BUNDLE_INFO_WITH_APPLICATION)
        .then((bundleInfo) => {
          this.versionName = `v${bundleInfo.versionName}`;
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
  
  @Builder
  InfoItem(title: string, content: string) {
    Row() {
      Text(title)
        .fontSize(15)
        .fontColor('#999999')
        .width(96)
        .textAlign(TextAlign.Start)
      
      Text(content)
        .fontSize(15)
        .fontColor(Color.White)
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
  LinkItem(title: string, content: string, url: string) {
    Column() {
      Row() {
        Text(title)
          .fontSize(15)
          .fontColor('#999999')
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
  SectionTitle(title: string) {
    Text(title)
      .fontSize(20)
      .fontColor(Color.White)
      .fontWeight(FontWeight.Medium)
      .width('100%')
      .textAlign(TextAlign.Start)
      .margin({ left: 20, bottom: 20 })
  }

  build() {
    Column(){
      ActionBar({
        title: '关于马飞'
      })
      
      // 内容区域
      Scroll() {
        Column({ space: 20 }) {
          // 应用图标和名称
          Column({ space: 16 }) {
            Image($r('app.media.app_icon'))
              .width(88)
              .height(88)
              .borderRadius(20)
            
            Text($r('app.string.app_name'))
              .fontSize(18)
              .fontColor(Color.White)
              .fontWeight(FontWeight.Medium)
            
            Text('HarmonyOS Jellyfin 第三方客户端')
              .fontSize(15)
              .fontColor('#999999')

            Text('连接你自己的 Jellyfin 服务器，在鸿蒙设备上完成媒体浏览、剧集选播和播放控制。')
              .fontSize(14)
              .fontColor('#999999')
              .textAlign(TextAlign.Center)
              .lineHeight(22)
              .padding({ left: 28, right: 28 })
          }
          .width('100%')
          .padding({ top: 40, bottom: 8 })
          
          // 应用信息
          Column({ space: 0 }) {
            this.SectionTitle('应用信息')
            
            Column() {
              this.InfoItem('版本号', this.versionName)
              Divider().color('#333333').margin({ left: 16, right: 16 })
              this.InfoItem('当前版本', 'HarmonyOS 1.x 稳定维护中')
              Divider().color('#333333').margin({ left: 16, right: 16 })
              this.InfoItem('开源协议', 'GPL-3.0-or-later')
            }
            .backgroundColor('#1a1a1a')
            .borderRadius(12)
          }

          Column({ space: 0 }) {
            this.SectionTitle('核心能力')

            Column() {
              this.InfoItem('媒体浏览', '首页、收藏、媒体库、搜索、详情页')
              Divider().color('#333333').margin({ left: 16, right: 16 })
              this.InfoItem('播放能力', 'AVPlayer / mpv 双引擎、续播、字幕音轨、清晰度切换')
              Divider().color('#333333').margin({ left: 16, right: 16 })
              this.InfoItem('设备适配', '手机、平板与 PC 形态适配中')
            }
            .backgroundColor('#1a1a1a')
            .borderRadius(12)
          }
          
          // 项目信息
          Column({ space: 0 }) {
            this.SectionTitle('项目链接')
            
            Column() {
              this.LinkItem('GitHub', this.githubUrl, this.githubUrl)
              Divider().color('#333333').margin({ left: 16, right: 16 })
              this.LinkItem('官网', this.websiteUrl, this.websiteUrl)
              Divider().color('#333333').margin({ left: 16, right: 16 })
              this.LinkItem('华为应用市场', this.appGalleryUrl, this.appGalleryUrl)
            }
            .backgroundColor('#1a1a1a')
            .borderRadius(12)
          }

          Column({ space: 0 }) {
            this.SectionTitle('2.0 方向')

            Column() {
              this.InfoItem('重构方向', 'Kotlin Multiplatform 共享逻辑层')
              Divider().color('#333333').margin({ left: 16, right: 16 })
              this.InfoItem('当前阶段', 'HarmonyOS 1.x 稳定维护，2.0 跨平台开发中')
              Divider().color('#333333').margin({ left: 16, right: 16 })
              this.InfoItem('共享边界', '业务规则下沉到 shared，端侧 UI 保持原生实现')
            }
            .backgroundColor('#1a1a1a')
            .borderRadius(12)
          }

          Column({ space: 0 }) {
            this.SectionTitle('鸣谢')

            Column() {
              this.LinkItem('mpv 源码支持', '感谢 hosplayer 作者 5en 提供 mpv 相关源码支持', this.mpvUrl)
              Divider().color('#333333').margin({ left: 16, right: 16 })
              this.InfoItem('开源生态', '感谢 Jellyfin、OpenHarmony 及相关开源项目')
            }
            .backgroundColor('#1a1a1a')
            .borderRadius(12)
          }
          
        }
        .padding({ bottom: 32 })
      }
      .layoutWeight(1)
      .scrollBar(BarState.Off)
      
    }.width('100%')
    .height('100%')
    .backgroundColor($r('app.color.bg_main'))
  }
}
