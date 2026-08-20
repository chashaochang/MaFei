import { HMRouter } from '@hadss/hmrouter'
import { RouterConsts } from '../../../common/RouterConsts'
import { AppRouteDestination } from '../../../component/AppRouteDestination'
import { ConnectScreen } from '../../connect/ConnectScreen'

@HMRouter({
  pageUrl: RouterConsts.JellyfinConnectPage,
  useNavDst: true
})
@ComponentV2
export struct JellyfinConnectPage {
  @Builder
  private pageContent(showRouteActionBar: boolean): void {
    ConnectScreen({
      isFromAddAccount: true,
      showRouteActionBar: showRouteActionBar
    })
  }

  build() {
    AppRouteDestination({
      title: $r('app.string.jellyfin_connect_title'),
      contentBuilder: () => {
        this.pageContent(false)
      },
      legacyContentBuilder: () => {
        this.pageContent(true)
      }
    })
  }
}
