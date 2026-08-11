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
  @Builder
  private nativePageContent() {
    MineTab({
      fromHome: true,
      destinationOwnsTitleBar: true
    })
  }

  @Builder
  private legacyPageContent() {
    MineTab({
      fromHome: true,
      destinationOwnsTitleBar: false
    })
  }

  build() {
    AppRouteDestination({
      title: '我的',
      contentBuilder: () => {
        this.nativePageContent()
      },
      legacyContentBuilder: () => {
        this.legacyPageContent()
      }
    })
  }
}
