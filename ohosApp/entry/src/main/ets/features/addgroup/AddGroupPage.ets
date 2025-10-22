import { HMRouter } from '@hadss/hmrouter';
import { RouterConsts } from '../../common/RouterConsts';
import { ActionBar } from '../../component/ActionBar';

@HMRouter({
  pageUrl: RouterConsts.AddGroupPage
})
@ComponentV2
export struct AddGroupPage{
  build() {
    Column(){
      ActionBar({
        title: '加入群聊'
      })


    }.width('100%').height('100%').backgroundColor($r('app.color.bg_main'))
  }
}