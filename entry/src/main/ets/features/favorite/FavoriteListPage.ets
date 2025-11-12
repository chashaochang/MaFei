import { HMRouter } from '@hadss/hmrouter';
import { RouterConsts } from '../../common/RouterConsts';
import { ActionBar } from '../../component/ActionBar';
import { FavoriteListUIState } from './FavoriteListUIState';
import { FavoriteListViewModel } from './FavoriteListViewModel';

@HMRouter({
  pageUrl: RouterConsts.FavoriteListPage
})
@ComponentV2
export struct FavoriteListPage {
  @Local ui: FavoriteListUIState = new FavoriteListUIState(this.getUIContext())
  vm: FavoriteListViewModel = new FavoriteListViewModel(this.ui)

  build() {
    Column() {
      ActionBar({
        title: this.ui.title
      }).backgroundBlurStyle(BlurStyle.BACKGROUND_THICK)
      // 使用RecyclerView创建视图，传入视图管理器
      Text('开发中')
        .width('100%')
        .layoutWeight(1)
        .textAlign(TextAlign.Center)
        .align(Alignment.Top)
        .padding(20)
    }.backgroundColor($r('app.color.bg_main'))
  }
}