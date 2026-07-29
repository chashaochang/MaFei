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
  build() {
    AppRouteDestination({
      title: '新增账号',
      contentBuilder: () => {
        ConnectScreen({
          isFromAddAccount: true,
          showRouteActionBar: false
        })
      },
      legacyContentBuilder: () => {
        ConnectScreen({
          isFromAddAccount: true,
          showRouteActionBar: true
        })
      }
    })
  }
}
