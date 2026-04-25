import { HMRouter, HMRouterMgr } from "@hadss/hmrouter";
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
  
  aboutToAppear() {
    this.getAppVersion();
  }
  
  private getAppVersion() {
    try {
      let context = getContext(this) as common.UIAbilityContext;
      let bundleName = context.applicationInfo.name;
      
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
        .fontSize(16)
        .fontColor('#999999')
        .width(100)
        .textAlign(TextAlign.Start)
      
      Text(content)
        .fontSize(16)
        .fontColor(Color.White)
        .layoutWeight(1)
        .textAlign(TextAlign.Start)
    }
    .width('100%')
    .height(48)
    .padding({ left: 16, right: 16 })
    .justifyContent(FlexAlign.Start)
  }

  build() {
    Column(){
      ActionBar({
        title: '关于我们'
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
              .fontSize(16)
              .fontColor(Color.White)
            
            Text('家庭媒体管理工具')
              .fontSize(16)
              .fontColor('#999999')
          }
          .width('100%')
          .padding({ top: 40, bottom: 8 })
          
          // 应用信息
          Column({ space: 0 }) {
            Text('应用信息')
              .fontSize(20)
              .fontColor(Color.White)
              .fontWeight(FontWeight.Medium)
              .width('100%')
              .textAlign(TextAlign.Start)
              .margin({ left: 20, bottom: 20 })
            
            Column() {
              this.InfoItem('版本号', this.versionName)
              // Divider().color('#333333').margin({ left: 16, right: 16 })
              //
              // this.InfoItem('开源许可证', 'GPL-3.0-or-later')
            }
            .backgroundColor('#1a1a1a')
            .borderRadius(12)
          }
          
          // 项目信息
          Column({ space: 0 }) {
            Text('项目信息')
              .fontSize(20)
              .fontColor(Color.White)
              .fontWeight(FontWeight.Medium)
              .width('100%')
              .textAlign(TextAlign.Start)
              .margin({ left: 20, bottom: 20 })
            
            Column() {
              Column() {
                Row() {
                  Text('GitHub')
                    .fontSize(16)
                    .fontColor('#999999')
                    .textAlign(TextAlign.Start)
                  Blank()
                }
                .width('100%')
                .margin({ bottom: 8 })
                
                Text('https://github.com/chashaochang/MaFei')
                  .fontSize(14)
                  .fontColor('#007AFF')
                  .width('100%')
                  .textAlign(TextAlign.Start)
                  .maxLines(2)
                  .textOverflow({ overflow: TextOverflow.Ellipsis })
                  .onClick(() => {
                    this.openUrl('https://github.com/chashaochang/MaFei');
                  })
              }
              .width('100%')
              .padding({ left: 16, right: 16, top: 12, bottom: 12 })
              .alignItems(HorizontalAlign.Start)
              
              Divider().color('#333333').margin({ left: 16, right: 16 })
              
              Column() {
                Row() {
                  Text('官网')
                    .fontSize(16)
                    .fontColor('#999999')
                    .textAlign(TextAlign.Start)
                  Blank()
                }
                .width('100%')
                .margin({ bottom: 8 })
                
                Text('https://jellyfin.xiaobaihome.cn/')
                  .fontSize(14)
                  .fontColor('#007AFF')
                  .width('100%')
                  .textAlign(TextAlign.Start)
                  .maxLines(1)
                  .textOverflow({ overflow: TextOverflow.Ellipsis })
                  .onClick(() => {
                    this.openUrl('https://jellyfin.xiaobaihome.cn/');
                  })
              }
              .width('100%')
              .padding({ left: 16, right: 16, top: 12, bottom: 12 })
              .alignItems(HorizontalAlign.Start)
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
