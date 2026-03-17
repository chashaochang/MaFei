import { HMRouter } from "@hadss/hmrouter"
import { RouterConsts } from "../../../common/RouterConsts"
import { MineTab } from "./MineTab"

@HMRouter({
  pageUrl: RouterConsts.MinePage
})
@ComponentV2
export struct MinePage {
  build() {
    MineTab({
      fromHome: true
    })
  }
}