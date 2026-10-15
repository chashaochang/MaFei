import { HMInterceptor, HMInterceptorAction, HMInterceptorInfo, HMRouterMgr, IHMInterceptor } from "@hadss/hmrouter";
import { OneMoreUtil } from "../../utils/OneMoreUtil";
import { WindowUtil } from "../../utils/WindowUtil";
import { HomeRefreshEvent, VideoDetailRefreshEvent } from "../../events/Events";
import { RouterConsts } from "../../common/RouterConsts";
import { Constants } from "../../utils/Constants";

@HMInterceptor({ interceptorName: 'jumpInfoInterceptor', global: true })
export class JumpInfoInterceptor implements IHMInterceptor {
  private isVideoDetailActiveInHomeContent(): boolean {
    const names = HMRouterMgr.getPathStack(Constants.HOME_CONTENT_NAVIGATION_ID)?.getAllPathName() ?? []
    return names.length > 0 && names[names.length - 1] === RouterConsts.VideoDetailPage
  }

  handle(info: HMInterceptorInfo): HMInterceptorAction {
    let connectionInfo: string = info.type === 'push' ? 'jump to' : 'back to';
    console.log(`${info.srcName} ${connectionInfo} ${info.targetName}`)
    if (info.type === 'push' && info.targetName === RouterConsts.PlayerPage) {
      if (OneMoreUtil.isNormalPhone()) {//普通手机
        //跳转到PlayerView支持旋转
        WindowUtil.setMainWindowRestricted(getContext(this))
      }
    } else if (info.type === 'pop' && info.srcName === RouterConsts.PlayerPage) {
      info.context.getHostContext()?.eventHub.emit(HomeRefreshEvent)
      if (info.targetName === RouterConsts.VideoDetailPage || this.isVideoDetailActiveInHomeContent()) {
        info.context.getHostContext()?.eventHub.emit(VideoDetailRefreshEvent)
      }
      if (OneMoreUtil.isNormalPhone()) {//普通手机
        //跳出PlayerView锁定竖屏
        WindowUtil.setMainWindowPortrait(getContext(this))
      }

    }
    return HMInterceptorAction.DO_NEXT;
  }
}
