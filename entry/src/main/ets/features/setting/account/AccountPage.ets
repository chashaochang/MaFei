import { AlertDialogV2, AdvancedDialogV2Button, AppStorageV2 } from '@kit.ArkUI'
import { ListView } from '@abner/refresh_v2'
import { HMRouter, HMRouterMgr } from '@hadss/hmrouter'
import { RouterConsts } from '../../../common/RouterConsts'
import { ActionBar } from '../../../component/ActionBar'
import { AppRouteDestination } from '../../../component/AppRouteDestination'
import { AppUIState } from '../../../entity/AppUIState'
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver'
import { AppThemeTokens } from '../../../theme/AppThemeTokens'
import {
  AccountItemGroup,
  AccountRowViewModel,
  AccountUIState
} from './AccountUIState'
import { AccountViewModel } from './AccountViewModel'

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
    HMRouterMgr.getCurrentParam() ?
      (HMRouterMgr.getCurrentParam() as Record<string, boolean>)['fromLogin'] : false

  aboutToAppear(): void {
    this.vm.getAccountList(!this.fromLogin).then((): void => {
      this.showErrorIfNeeded()
    }).catch((): void => {
      this.showErrorIfNeeded()
    })
  }

  private onAccountClick(item: AccountRowViewModel): void {
    if (item.isCurrent) {
      return
    }
    this.vm.changeAccount(item).then((switched: boolean): void => {
      if (!switched) {
        this.showErrorIfNeeded()
      }
    }).catch((): void => {
      this.showErrorIfNeeded()
    })
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
      value: $r('app.string.account_add_title'),
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
      content: $r('app.string.account_delete_cancel'),
      action: () => {
      }
    })
  }

  private deleteDialogButton(item: AccountRowViewModel): AdvancedDialogV2Button {
    return new AdvancedDialogV2Button({
      content: $r('app.string.account_delete_confirm'),
      role: ButtonRole.ERROR,
      action: () => {
        this.vm.deleteAccount(item).then((deleted: boolean): void => {
          if (!deleted) {
            this.showErrorIfNeeded()
          }
        }).catch((): void => {
          this.showErrorIfNeeded()
        })
      }
    })
  }

  @Builder
  dialogBuilder(item: AccountRowViewModel): void {
    AlertDialogV2({
      primaryTitle: $r('app.string.account_delete_title'),
      content: $r('app.string.account_delete_message'),
      primaryButton: this.cancelDialogButton(),
      secondaryButton: this.deleteDialogButton(item)
    })
  }

  private openDeleteDialog(item: AccountRowViewModel): void {
    if (this.appUIState.systemMaterialAvailable) {
      this.getUIContext().getPromptAction().openCustomDialog({
        builder: () => {
          this.dialogBuilder(item)
        },
        systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
      }).catch((): void => {
      })
    } else {
      this.getUIContext().getPromptAction().openCustomDialog({
        builder: () => {
          this.dialogBuilder(item)
        }
      }).catch((): void => {
      })
    }
  }

  private showErrorIfNeeded(): void {
    if (this.ui.errorText.length === 0) {
      return
    }
    this.getUIContext().getPromptAction().showToast({
      message: this.ui.errorText
    })
    this.ui.errorText = ''
  }

  @Builder
  private providerLogo(item: AccountRowViewModel) {
    Image(item.isFeiniuVideo ?
      $r('app.media.feiniu_video_logo') : $r('app.media.app_icon'))
      .width(48)
      .height(48)
      .objectFit(ImageFit.Contain)
      .borderRadius(12)
  }

  @Builder
  private accountRowContent(item: AccountRowViewModel) {
    this.providerLogo(item)
    Column({ space: 5 }) {
      Row({ space: 6 }) {
        Text(item.displayName.length > 0 ? item.displayName : item.avatarText)
          .fontSize(16)
          .fontColor(this.primaryTextColor())
          .textAlign(TextAlign.Start)
          .fontWeight(FontWeight.Medium)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .ellipsisMode(EllipsisMode.END)
          .maxLines(1)
          .layoutWeight(1)
        if (item.isCurrent) {
          Text($r('app.string.account_current'))
            .fontColor($r('app.color.color_main'))
            .fontSize(12)
            .padding({ left: 6, right: 6, top: 2, bottom: 2 })
            .backgroundColor($r('app.color.primary_light_hover'))
            .borderRadius(4)
        }
      }
      Row({ space: 6 }) {
        Text(item.isFeiniuVideo ?
          $r('app.string.feiniu_video_service_name') : $r('app.string.provider_jellyfin_name'))
          .fontSize(11)
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.color_main'))
          .maxLines(1)
        Text(item.address)
          .fontSize(11)
          .fontColor(this.secondaryTextColor())
          .textAlign(TextAlign.Start)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .ellipsisMode(EllipsisMode.END)
          .layoutWeight(1)
      }
    }
    .height('100%')
    .layoutWeight(1)
    .alignItems(HorizontalAlign.Start)
    .justifyContent(FlexAlign.Center)

    if (this.ui.switchingAccountKey === item.accountKey ||
      this.ui.deletingAccountKey === item.accountKey) {
      LoadingProgress()
        .width(24)
        .height(24)
    } else if (!item.isCurrent) {
      Image($r('app.media.icon_del'))
        .width(32)
        .height(32)
        .fillColor(this.iconColor())
        .padding(8)
        .onClick((): void => {
          this.openDeleteDialog(item)
        })
    }
  }

  @Builder
  private feiniuItemLayout(item: Object, _index: number) {
    Row({ space: 12 }) {
      this.accountRowContent(item as AccountRowViewModel)
    }
    .padding({ left: 16, right: 16, top: 12, bottom: 12 })
    .margin({ bottom: 10 })
    .width('100%')
    .height(80)
    .backgroundColor($r('app.color.bg_1'))
    .borderRadius(8)
    .onClick((): void => {
      this.onAccountClick(item as AccountRowViewModel)
    })
  }

  @Builder
  private nativeItemLayout(item: AccountRowViewModel, index: number, itemCount: number) {
    Column() {
      Row({ space: 12 }) {
        this.accountRowContent(item)
      }
      .padding({ left: 16, right: 16, top: 12, bottom: 12 })
      .width('100%')
      .height(80)
      .onClick((): void => {
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
  private nativeAccountGroup(itemGroup: AccountItemGroup) {
    Column() {
      Text(itemGroup.title)
        .fontSize(14)
        .fontWeight(FontWeight.Bold)
        .fontColor($r('sys.color.font_primary'))
        .width('100%')
        .height(40)
        .padding({ left: 16, right: 16 })
        .textAlign(TextAlign.Start)

      ForEach(itemGroup.items, (item: AccountRowViewModel, index: number) => {
        this.nativeItemLayout(item, index, itemGroup.items.length)
      }, (item: AccountRowViewModel) => item.accountKey)
    }
    .width('100%')
    .borderRadius(AppThemeTokens.groupRadius(this.appUIState.themeStyle))
    .clip(true)
    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  itemGroupHeader(item: Object, _position: number) {
    Text((item as AccountItemGroup).title)
      .fontSize(14)
      .fontWeight(FontWeight.Bold)
      .fontColor(this.primaryTextColor())
      .width('100%')
      .height(32)
      .textAlign(TextAlign.Start)
  }

  @Builder
  emptyLayout() {
    Column({ space: 16 }) {
      Image($r('app.media.no_history'))
        .width(200)
        .height(200)
      Text($r('app.string.account_empty'))
        .fontSize(14)
        .fontColor(this.secondaryTextColor())
      Button($r('app.string.account_add_title'))
        .height(44)
        .padding({ left: 24, right: 24 })
        .fontColor(Color.White)
        .backgroundColor($r('app.color.color_main'))
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.PrimaryInteractive,
          AppThemeTokens.isNative(this.appUIState.themeStyle)
        ))
        .onClick((): void => {
          this.openAddAccount()
        })
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
  }

  @Builder
  private nativeAccountList() {
    if (this.ui.loading && this.ui.groupAccountList.length === 0) {
      LoadingProgress()
        .width(36)
        .height(36)
        .layoutWeight(1)
    } else if (this.ui.groupAccountList.length === 0) {
      this.emptyLayout()
    } else {
      List({ space: 12 }) {
        ForEach(this.ui.groupAccountList, (itemGroup: AccountItemGroup) => {
          ListItem() {
            this.nativeAccountGroup(itemGroup)
          }
        }, (itemGroup: AccountItemGroup) => itemGroup.key)
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
          title: this.fromLogin ?
            $r('app.string.account_history_title') : $r('app.string.account_switch_title'),
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
      title: this.fromLogin ?
        $r('app.string.account_history_title') : $r('app.string.account_switch_title'),
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
