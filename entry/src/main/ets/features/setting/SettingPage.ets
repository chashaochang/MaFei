import { HMRouter, HMRouterMgr } from "@hadss/hmrouter";
import { AppStorageV2 } from '@kit.ArkUI';
import { AppUIState } from '../../entity/AppUIState';
import { AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver';
import { AppThemeTokens } from '../../theme/AppThemeTokens';
import { AppRouteDestination } from '../../component/AppRouteDestination';

@HMRouter({
  pageUrl:'/Setting',
  useNavDst: true
})
@ComponentV2
export struct SettingPage{
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;

  private useNativeMaterial(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle,
      this.appUIState.nativeThemeAvailable
    )
  }

  private primaryTextColor(): ResourceColor {
    return AppThemeTokens.isNative(this.appUIState.themeStyle) ?
      $r('sys.color.font_primary') : Color.White
  }

  private secondaryTextColor(): ResourceColor {
    return AppThemeTokens.isNative(this.appUIState.themeStyle) ?
      $r('sys.color.font_secondary') : '#999999'
  }

  private iconColor(): ResourceColor {
    return AppThemeTokens.isNative(this.appUIState.themeStyle) ?
      $r('sys.color.icon_secondary') : '#666666'
  }
  
  @Builder
  private SettingItemContent(title: string, subtitle?: string, showArrow: boolean = true, onClick?: () => void) {
    Row() {
      Column() {
        Text(title)
          .fontSize(16)
          .fontColor(this.primaryTextColor())
          .textAlign(TextAlign.Start)
        if (subtitle) {
          Text(subtitle)
            .fontSize(12)
            .fontColor(this.secondaryTextColor())
            .textAlign(TextAlign.Start)
            .margin({ top: 4 })
        }
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1)
      
      if (showArrow) {
        Image($r('app.media.ic_right'))
          .width(20)
          .height(20)
          .fillColor(this.iconColor())
      }
    }
    .width('100%')
    .height(subtitle ? 72 : 56)
    .padding({ left: 16, right: 16 })
    .justifyContent(FlexAlign.SpaceBetween)
    .onClick(() => {
      if (onClick) {
        onClick()
      }
    })
  }

  @Builder
  private SettingGroup(title: string, subtitle?: string, showArrow: boolean = true, onClick?: () => void) {
    Column() {
      this.SettingItemContent(title, subtitle, showArrow, onClick)
    }
    .width('100%')
    .borderRadius(AppThemeTokens.groupRadius(this.appUIState.themeStyle))
    .clip(true)
    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  private feiniuSettingGroup(title: string, subtitle?: string, showArrow: boolean = true, onClick?: () => void) {
    Column() {
      this.SettingItemContent(title, subtitle, showArrow, onClick)
    }
    .width('100%')
    .backgroundColor('#1a1a1a')
    .borderRadius(8)
  }

  @Builder
  private settingGroupSurface(title: string, subtitle?: string, showArrow: boolean = true, onClick?: () => void) {
    if (this.useNativeMaterial()) {
      this.SettingGroup(title, subtitle, showArrow, onClick)
    } else {
      this.feiniuSettingGroup(title, subtitle, showArrow, onClick)
    }
  }

  @Builder
  private pageContent(showLegacyActionBar: boolean) {
    Column(){
      if (showLegacyActionBar) {
        Row() {
          Image($r('app.media.ic_back'))
            .width(44)
            .height(44)
            .padding(12)
            .onClick(() => {
              HMRouterMgr.pop()
            })
          Text('设置').fontColor(this.primaryTextColor()).fontSize(16).margin({left:16})
          Blank().layoutWeight(1)
        }
        .height(56)
        .width('100%')
        .justifyContent(FlexAlign.Start)
        .backgroundColor('#202020')
      }
      
      // 设置项列表
      Scroll() {
        Column({ space: 12 }) {
          // 应用信息分组
          Column({ space: 8 }) {
            Text('应用信息')
              .fontSize(14)
              .fontColor(this.secondaryTextColor())
              .width('100%')
              .textAlign(TextAlign.Start)
              .margin({ left: 16, bottom: 4 })
            
            this.settingGroupSurface('关于应用', '客户端信息', true, () => {
              HMRouterMgr.push({
                pageUrl: '/About'
              })
            })
          }
          
          // 用户协议分组
          Column({ space: 8 }) {
            Text('隐私协议')
              .fontSize(14)
              .fontColor(this.secondaryTextColor())
              .width('100%')
              .textAlign(TextAlign.Start)
              .margin({ left: 16, bottom: 4 })
            
            this.settingGroupSurface('隐私协议', '查看隐私保护政策', true, () => {
              HMRouterMgr.push({
                pageUrl: '/Privacy'
              })
            })
          }
          // 联系我们
          Column({ space: 8 }) {
            Text('联系我们')
              .fontSize(14)
              .fontColor(this.secondaryTextColor())
              .width('100%')
              .textAlign(TextAlign.Start)
              .margin({ left: 16, bottom: 4 })
            
            this.settingGroupSurface('反馈建议', '需求/反馈QQ群: 991893385', false)
          }
        }
        .width('100%')
        .constraintSize({ maxWidth: this.useNativeMaterial() ? 560 : '100%' })
        .padding({ left: 16, right: 16, top: 16, bottom: 32 })
      }
      .align(Alignment.Top)
      .layoutWeight(1)
      .scrollBar(BarState.Off)
      
    }
    .width('100%')
    .height('100%')
    .backgroundColor(showLegacyActionBar ? '#101010' : Color.Transparent)
    .expandSafeArea([SafeAreaType.SYSTEM],[SafeAreaEdge.BOTTOM])
  }

  build() {
    AppRouteDestination({
      title: '设置',
      contentBuilder: () => {
        this.pageContent(false)
      },
      legacyContentBuilder: () => {
        this.pageContent(true)
      }
    })
  }
}
