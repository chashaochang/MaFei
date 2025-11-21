import { ListView } from '@abner/refresh_v2';
import { HMRouter, HMRouterMgr } from '@hadss/hmrouter';
import { RouterConsts } from '../../../common/RouterConsts';
import { ActionBar } from '../../../component/ActionBar';
import { AppPreference } from '../../../data/AppPreference';
import ServerUser from '../../../data/ServerUser';
import { ApiClient } from '../../../network/ApiClient';
import { AccountUIState, ItemGroup } from './AccountUIState';
import { AccountViewModel } from './AccountViewModel';
import { AlertDialogV2, AdvancedDialogV2Button } from '@kit.ArkUI';

@HMRouter({
  pageUrl: RouterConsts.AccountPage
})
@ComponentV2
export struct AccountPage {
  @Local ui: AccountUIState = new AccountUIState(this.getUIContext())
  @Local vm: AccountViewModel = new AccountViewModel(this.ui)
  @Local fromLogin: boolean =
    HMRouterMgr.getCurrentParam() ? (HMRouterMgr.getCurrentParam() as Record<string, boolean>)['fromLogin'] : false

  aboutToAppear(): void {
    this.vm.getAccountList()
  }

  @Builder
  dialogBuilder(item: ServerUser): void {
    AlertDialogV2({
      primaryTitle: '提示',
      content: '确定要删除吗?',
      primaryButton: new AdvancedDialogV2Button({
        content: '取消',
        action: () => {

        },
      }),
      secondaryButton: new AdvancedDialogV2Button({
        content: '确认',
        role: ButtonRole.ERROR,
        action: () => {
          this.vm.deleteAccount(item)
        }
      }),
    })
  }

  @Builder
  itemLayout(item: Object, index: number) {
    Row({ space: 12 }) {
      Text((item as ServerUser).user.username.substring(0, 1))
        .fontColor(Color.White)
        .fontWeight(FontWeight.Bold)
        .fontSize(24)
        .backgroundColor('#ffb88e')
        .width(48)
        .height(48)
        .borderRadius(24)
        .align(Alignment.Center)
        .textAlign(TextAlign.Center)
      Column({ space: 6 }) {
        Row() {
          Text((item as ServerUser).user.username)
            .fontSize(16)
            .fontColor($r('app.color.text_primary'))
            .textAlign(TextAlign.Start)
            .fontWeight(FontWeight.Bold)
            .textOverflow({
              overflow: TextOverflow.Ellipsis
            })
            .ellipsisMode(EllipsisMode.END)
            .maxLines(1)
            .layoutWeight(1)
          if ((item as ServerUser).user.userId === ApiClient.Instance().userId &&
            (item as ServerUser).server.id === AppPreference.getInstance().getCurrentServerId()) {
            Text('当前用户')
              .fontColor($r('app.color.color_main'))
              .fontSize(12)
              .padding({
                left: 6,
                right: 6,
                top: 2,
                bottom: 2
              })
              .backgroundColor($r('app.color.primary_light_hover'))
              .borderRadius(4)
          }
        }

        Text((item as ServerUser).server.hostname)
          .fontSize(12)
          .fontColor($r('app.color.text_primary'))
          .textAlign(TextAlign.Start)
          .maxLines(1)
          .textOverflow({
            overflow: TextOverflow.Ellipsis
          })
          .ellipsisMode(EllipsisMode.END)
          .width('100%')
      }
      .padding({
        top: 6, bottom: 6
      })
      .height('100%')
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
      .justifyContent(FlexAlign.SpaceBetween)

      if (!((item as ServerUser).user.userId === ApiClient.Instance().userId &&
        (item as ServerUser).server.id === AppPreference.getInstance().getCurrentServerId())) {
        Image($r('app.media.icon_del'))
          .width(32)
          .height(32)
          .fillColor($r('app.color.text_primary'))
          .padding(8)
          .onClick(() => {
            let uiContext: UIContext = this.getUIContext();
            uiContext.getPromptAction().openCustomDialog({
              builder: () => {
                this.dialogBuilder((item as ServerUser));
              },
            }).catch(() => {

            });
          })
      }
    }
    .padding({
      left: 16,
      right: 16,
      top: 12,
      bottom: 12
    })
    .margin({
      bottom: 10
    })
    .width('100%')
    .height(72)
    .backgroundColor($r('app.color.bg_1'))
    .borderRadius(8)
    .onClick(() => {
      if ((item as ServerUser).user.userId === ApiClient.Instance().userId &&
        (item as ServerUser).server.id === AppPreference.getInstance().getCurrentServerId()) {
        return
      }
      this.vm.changeAccount((item as ServerUser))
    })
  }

  @Builder
  itemGroupHeader(item: Object, position: number) {
    Text((item as ItemGroup).title)
      .fontSize(14)
      .fontWeight(FontWeight.Bold)
      .fontColor($r('app.color.text_primary'))
      .width("100%")
      .height(32)
      .textAlign(TextAlign.Start)
  }

  @Builder
  emptyLayout() {
    Column({ space: 12 }) {
      Image($r('app.media.no_history'))
        .width(240)
        .height(240)
      Text('无历史登录记录')
        .fontSize(16)
        .fontColor($r('app.color.text_2'))
    }
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
    .width('100%')
    .height('100%')
  }

  build() {
    Column() {
      ActionBar({
        title: this.fromLogin ? '历史登录' : '切换账号',
        rightBtnIcon: this.fromLogin ? undefined : $r('app.media.add'),
        onRightBtnClick: () => {
          if (this.fromLogin) {
            return
          }
          HMRouterMgr.to(RouterConsts.AddAccountPage).push()
        }
      })
      ListView({
        isLazyData: false,
        itemGroupHeader: this.itemGroupHeader, //分组头
        itemGroupData: this.ui.groupAccountList,
        itemLayout: (item, index) => this.itemLayout(item, index), //条目布局
        enableRefresh: false,
        enableLoadMore: false,
        emptyLayout: () => this.emptyLayout(), //空布局
        showEmptyLayout: this.ui.groupAccountList.length === 0,
        lazyCachedCount: 10 //缓存,
      }).width('100%')
        .padding({
          top: 10,
          bottom: 10,
          left: 16,
          right: 16
        })
        .layoutWeight(1)
        .backgroundColor($r('app.color.bg_main'))
    }
  }
}