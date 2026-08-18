import { ListView } from '@abner/refresh_v2';
import { HMRouter, HMRouterMgr } from '@hadss/hmrouter';
import { RouterConsts } from '../../../common/RouterConsts';
import { ActionBar } from '../../../component/ActionBar';
import { AppRouteDestination } from '../../../component/AppRouteDestination';
import { AppPreference } from '../../../data/AppPreference';
import ServerUser from '../../../data/ServerUser';
import { AppUIState } from '../../../entity/AppUIState';
import { AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver';
import { AppThemeTokens } from '../../../theme/AppThemeTokens';
import { AccountUIState, ItemGroup } from './AccountUIState';
import { AccountViewModel } from './AccountViewModel';
import { AlertDialogV2, AdvancedDialogV2Button, AppStorageV2 } from '@kit.ArkUI';
import { deviceInfo } from '@kit.BasicServicesKit';

@HMRouter({
  pageUrl: RouterConsts.AccountPage,
  useNavDst: true
})
@ComponentV2
export struct AccountPage {
  @Local ui: AccountUIState = new AccountUIState(this.getUIContext())
  @Local vm: AccountViewModel = new AccountViewModel(this.ui)
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!
  @Local fromLogin: boolean =
    HMRouterMgr.getCurrentParam() ? (HMRouterMgr.getCurrentParam() as Record<string, boolean>)['fromLogin'] : false

  aboutToAppear(): void {
    this.vm.getAccountList()
  }

  private isCurrentAccount(item: ServerUser): boolean {
    if (this.fromLogin) {
      return false
    }
    const preference = AppPreference.getInstance()
    const currentUserId = preference.getCurrentUserId()
    const currentServerId = preference.getCurrentServerId()
    return currentUserId.length > 0 && currentServerId !== null &&
      item.user.userId === currentUserId && item.server.id === currentServerId
  }

  private onAccountClick(item: ServerUser): void {
    if (this.isCurrentAccount(item)) {
      return
    }
    this.vm.changeAccount(item)
  }

  private openAddAccount(): void {
    if (this.fromLogin) {
      return
    }
    HMRouterMgr.to(RouterConsts.AddAccountPage).push()
  }

  private navigationMenus(): Array<NavigationMenuItem> {
    if (this.fromLogin) {
      return []
    }
    return [{
      value: '新增账号',
      icon: $r('sys.symbol.plus'),
      action: () => {
        this.openAddAccount()
      }
    }]
  }

  private primaryTextColor(): ResourceColor {
    return AppThemeTokens.isNative(this.appUIState.themeStyle) ?
      $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return AppThemeTokens.isNative(this.appUIState.themeStyle) ?
      $r('sys.color.font_secondary') : $r('app.color.text_2')
  }

  private iconColor(): ResourceColor {
    return AppThemeTokens.isNative(this.appUIState.themeStyle) ?
      $r('sys.color.icon_primary') : $r('app.color.text_primary')
  }

  private cancelDialogButton(): AdvancedDialogV2Button {
    return new AdvancedDialogV2Button({
      content: '取消',
      action: () => {
      }
    })
  }

  private deleteDialogButton(item: ServerUser): AdvancedDialogV2Button {
    return new AdvancedDialogV2Button({
      content: '确认',
      role: ButtonRole.ERROR,
      action: () => {
        this.vm.deleteAccount(item)
      }
    })
  }

  @Builder
  dialogBuilder(item: ServerUser): void {
    AlertDialogV2({
      primaryTitle: '提示',
      content: '确定要删除吗?',
      primaryButton: this.cancelDialogButton(),
      secondaryButton: this.deleteDialogButton(item)
    })
  }

  private openDeleteDialog(item: ServerUser): void {
    if (deviceInfo.sdkApiVersion >= 26) {
      this.getUIContext().getPromptAction().openCustomDialog({
        builder: () => {
          this.dialogBuilder(item)
        },
        systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
      }).catch(() => {
      })
    } else {
      this.getUIContext().getPromptAction().openCustomDialog({
        builder: () => {
          this.dialogBuilder(item)
        }
      }).catch(() => {
      })
    }
  }

  @Builder
  private accountRowContent(item: ServerUser) {
    Text(item.user.username.substring(0, 1))
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
        Text(item.user.username)
          .fontSize(16)
          .fontColor(this.primaryTextColor())
          .textAlign(TextAlign.Start)
          .fontWeight(FontWeight.Bold)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .ellipsisMode(EllipsisMode.END)
          .maxLines(1)
          .layoutWeight(1)
        if (this.isCurrentAccount(item)) {
          Text('当前用户')
            .fontColor($r('app.color.color_main'))
            .fontSize(12)
            .padding({ left: 6, right: 6, top: 2, bottom: 2 })
            .backgroundColor($r('app.color.primary_light_hover'))
            .borderRadius(4)
        }
      }

      Text(item.server.hostname)
        .fontSize(10)
        .fontColor(this.primaryTextColor())
        .textAlign(TextAlign.Start)
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .ellipsisMode(EllipsisMode.END)
        .width('100%')
    }
    .padding({ top: 6, bottom: 6 })
    .height('100%')
    .layoutWeight(1)
    .alignItems(HorizontalAlign.Start)
    .justifyContent(FlexAlign.SpaceBetween)

    if (!this.isCurrentAccount(item)) {
      Image($r('app.media.icon_del'))
        .width(32)
        .height(32)
        .fillColor(this.iconColor())
        .padding(8)
        .onClick(() => {
          this.openDeleteDialog(item)
        })
    }
  }

  @Builder
  private feiniuItemLayout(item: Object, _index: number) {
    Row({ space: 12 }) {
      this.accountRowContent(item as ServerUser)
    }
    .padding({ left: 16, right: 16, top: 12, bottom: 12 })
    .margin({ bottom: 10 })
    .width('100%')
    .height(72)
    .backgroundColor($r('app.color.bg_1'))
    .borderRadius(8)
    .onClick(() => {
      this.onAccountClick(item as ServerUser)
    })
  }

  @Builder
  private nativeItemLayout(item: ServerUser, index: number, itemCount: number) {
    Column() {
      Row({ space: 12 }) {
        this.accountRowContent(item)
      }
      .padding({ left: 16, right: 16, top: 12, bottom: 12 })
      .width('100%')
      .height(72)
      .onClick(() => {
        this.onAccountClick(item)
      })

      if (index < itemCount - 1) {
        Divider()
          .color($r('sys.color.comp_divider'))
          .margin({ left: 16, right: 16 })
      }
    }
    .width('100%')
  }

  @Builder
  private nativeAccountGroup(itemGroup: ItemGroup) {
    Column() {
      Text(itemGroup.title)
        .fontSize(14)
        .fontWeight(FontWeight.Bold)
        .fontColor($r('sys.color.font_primary'))
        .width('100%')
        .height(40)
        .padding({ left: 16, right: 16 })
        .textAlign(TextAlign.Start)

      ForEach(itemGroup.items, (item: ServerUser, index: number) => {
        this.nativeItemLayout(item, index, itemGroup.items.length)
      }, (item: ServerUser) => `${item.server.id}_${item.user.userId}`)
    }
    .width('100%')
    .borderRadius(AppThemeTokens.groupRadius(this.appUIState.themeStyle))
    .clip(true)
    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  itemGroupHeader(item: Object, _position: number) {
    Text((item as ItemGroup).title)
      .fontSize(14)
      .fontWeight(FontWeight.Bold)
      .fontColor(this.primaryTextColor())
      .width("100%")
      .height(32)
      .textAlign(TextAlign.Start)
  }

  @Builder
  emptyLayout() {
    Stack() {
      Image($r('app.media.no_history'))
        .width(240)
        .height(240)
      Text('无历史登录记录')
        .fontSize(14)
        .fontColor(this.secondaryTextColor())
        .margin({
          top: 180
        })
    }
    .width('100%')
    .height('100%')
    .padding({
      bottom: 100
    })
  }

  @Builder
  private nativeAccountList() {
    if (this.ui.groupAccountList.length === 0) {
      this.emptyLayout()
    } else {
      List({ space: 12 }) {
        ForEach(this.ui.groupAccountList, (itemGroup: ItemGroup) => {
          ListItem() {
            this.nativeAccountGroup(itemGroup)
          }
        }, (itemGroup: ItemGroup, index: number) => `${itemGroup.title}_${index}`)
      }
      .width('100%')
      .constraintSize({ maxWidth: 560 })
      .padding({ top: 10, bottom: 10, left: 16, right: 16 })
      .layoutWeight(1)
      .cachedCount(10)
      .scrollBar(BarState.Auto)
      .backgroundColor(Color.Transparent)
    }
  }

  @Builder
  private feiniuAccountList() {
    ListView({
      isLazyData: false,
      itemGroupHeader: (item, position) => this.itemGroupHeader(item, position),
      itemGroupData: this.ui.groupAccountList,
      itemLayout: (item, index) => this.feiniuItemLayout(item, index),
      enableRefresh: false,
      enableLoadMore: false,
      emptyLayout: () => this.emptyLayout(),
      showEmptyLayout: this.ui.groupAccountList.length === 0,
      lazyCachedCount: 10
    })
    .width('100%')
    .padding({ top: 10, bottom: 10, left: 16, right: 16 })
    .layoutWeight(1)
    .backgroundColor($r('app.color.bg_main'))
  }

  @Builder
  private pageContent(showLegacyActionBar: boolean) {
    Column() {
      if (showLegacyActionBar) {
        ActionBar({
          title: this.fromLogin ? '历史登录' : '切换账号',
          rightBtnIcon: this.fromLogin ? undefined : $r('app.media.add'),
          onRightBtnClick: () => {
            this.openAddAccount()
          }
        })
      }
      if (showLegacyActionBar) {
        this.feiniuAccountList()
      } else {
        this.nativeAccountList()
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(showLegacyActionBar ? $r('app.color.bg_main') : Color.Transparent)
  }

  build() {
    AppRouteDestination({
      title: this.fromLogin ? '历史登录' : '切换账号',
      menus: this.navigationMenus(),
      contentBuilder: () => {
        this.pageContent(false)
      },
      legacyContentBuilder: () => {
        this.pageContent(true)
      }
    })
  }
}
