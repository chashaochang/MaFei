import { HMRouter } from '@hadss/hmrouter';
import { RouterConsts } from '../../common/RouterConsts';
import { ActionBar } from '../../component/ActionBar';

@HMRouter({
  pageUrl: RouterConsts.AddGroupPage
})
@ComponentV2
export struct AddGroupPage {
  build() {
    Column() {
      ActionBar({
        title: '加入群聊'
      })
      Column() {
        Image($r('app.media.qrcode')).width(240).borderRadius(16).padding(8).backgroundColor('#fcfcfc')
        Blank().height(30)
        Text('扫描二维码加入群聊').fontSize(16).fontColor($r('app.color.text_primary'))
        Blank().height(20)
      }.width('100%')
      .padding(20)
      .backgroundColor($r('app.color.bg_1'))
      .borderRadius(20)

      Blank()
    }.width('100%').height('100%').backgroundColor($r('app.color.bg_main')).padding({
      left: 16, right: 16
    })
  }
}