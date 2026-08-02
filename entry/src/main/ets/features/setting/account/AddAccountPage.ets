import { HMRouter } from '@hadss/hmrouter';
import { RouterConsts } from '../../../common/RouterConsts';
import { ConnectScreen } from '../../connect/ConnectScreen';
import { AppRouteDestination } from '../../../component/AppRouteDestination';

@HMRouter({
  pageUrl: RouterConsts.AddAccountPage,
  useNavDst: true
})
@ComponentV2
export struct AddAccountPage {
  @Builder
  private pageContent(showRouteActionBar: boolean) {
    ConnectScreen({
      isFromAddAccount: true,
      showRouteActionBar
    })
  }

  build() {
    AppRouteDestination({
      title: '新增账号',
      contentBuilder: () => {
        this.pageContent(false)
      },
      legacyContentBuilder: () => {
        this.pageContent(true)
      }
    })
  }
}
