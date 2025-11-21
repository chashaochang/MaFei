import { HMRouter } from '@hadss/hmrouter';
import { RouterConsts } from '../../../common/RouterConsts';
import { ConnectScreen } from '../../connect/ConnectScreen';

@HMRouter({
  pageUrl: RouterConsts.AddAccountPage
})
@ComponentV2
export struct AddAccountPage {
  build() {
    ConnectScreen({
      isFromAddAccount: true
    })
  }
}