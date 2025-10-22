import { HMRouter, HMRouterMgr } from "@hadss/hmrouter";

@HMRouter({
  pageUrl:'/Setting'
})
@ComponentV2
export struct SettingPage{
  
  @Builder
  SettingItem(title: string, subtitle?: string, showArrow: boolean = true, onClick?: () => void) {
    Row() {
      Column() {
        Text(title)
          .fontSize(16)
          .fontColor(Color.White)
          .textAlign(TextAlign.Start)
        if (subtitle) {
          Text(subtitle)
            .fontSize(12)
            .fontColor('#999999')
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
          .fillColor('#666666')
      }
    }
    .width('100%')
    .height(subtitle ? 72 : 56)
    .padding({ left: 16, right: 16 })
    .justifyContent(FlexAlign.SpaceBetween)
    .backgroundColor('#1a1a1a')
    .borderRadius(8)
    .onClick(() => {
      if (onClick) {
        onClick()
      }
    })
  }

  build() {
    Column(){
      // 顶部导航栏
      Row() {
        Image($r('app.media.ic_back'))
          .width(44)
          .height(44)
          .padding(12)
          .onClick(() => {
            HMRouterMgr.pop()
          })
        Text('设置').fontColor(Color.White).fontSize(16).margin({left:16})
        Blank().layoutWeight(1)
      }
      .height(56)
      .width('100%')
      .justifyContent(FlexAlign.Start)
      .backgroundColor('#202020')
      
      // 设置项列表
      Scroll() {
        Column({ space: 12 }) {
          // 应用信息分组
          Column({ space: 8 }) {
            Text('应用信息')
              .fontSize(14)
              .fontColor('#999999')
              .width('100%')
              .textAlign(TextAlign.Start)
              .margin({ left: 16, bottom: 4 })
            
            this.SettingItem('关于应用', '客户端信息', true, () => {
              HMRouterMgr.push({
                pageUrl: '/About'
              })
            })
          }
          
          // 用户协议分组
          Column({ space: 8 }) {
            Text('隐私协议')
              .fontSize(14)
              .fontColor('#999999')
              .width('100%')
              .textAlign(TextAlign.Start)
              .margin({ left: 16, bottom: 4 })
            
            this.SettingItem('隐私协议', '查看隐私保护政策', true, () => {
              HMRouterMgr.push({
                pageUrl: '/Privacy'
              })
            })
          }
          // 联系我们
          Column({ space: 8 }) {
            Text('联系我们')
              .fontSize(14)
              .fontColor('#999999')
              .width('100%')
              .textAlign(TextAlign.Start)
              .margin({ left: 16, bottom: 4 })
            
            this.SettingItem('反馈建议', '需求/反馈QQ群: 991893385', false)
          }
        }
        .padding({ left: 16, right: 16, top: 16, bottom: 32 })
      }
      .align(Alignment.Top)
      .layoutWeight(1)
      .scrollBar(BarState.Off)
      
    }.width('100%')
    .height('100%')
    .backgroundColor('#101010')
    .expandSafeArea([SafeAreaType.SYSTEM],[SafeAreaEdge.BOTTOM])
  }
}