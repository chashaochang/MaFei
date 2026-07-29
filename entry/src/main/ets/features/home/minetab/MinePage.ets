import { HMRouter } from "@hadss/hmrouter"
import { RouterConsts } from "../../../common/RouterConsts"
import { MineTab } from "./MineTab"
import { AppRouteDestination } from "../../../component/AppRouteDestination"

@HMRouter({
  pageUrl: RouterConsts.MinePage,
  useNavDst: true
})
@ComponentV2
export struct MinePage {
  build() {
    AppRouteDestination({
      title: '我的',
      contentBuilder: () => {
        MineTab({
          fromHome: true,
          destinationOwnsTitleBar: true
        })
      },
      legacyContentBuilder: () => {
        MineTab({
          fromHome: true,
          destinationOwnsTitleBar: false
        })
      }
    })
  }
}
