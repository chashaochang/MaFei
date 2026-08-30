import { AlertDialogV2, AdvancedDialogV2Button, AppStorageV2 } from '@kit.ArkUI'
import { ListView } from '@abner/refresh_v2'
import { HMRouter, HMRouterMgr } from '@hadss/hmrouter'
import { RouterConsts } from '../../../common/RouterConsts'
import { UIConstants } from '../../../common/UIConstants'
import { ActionBar } from '../../../component/ActionBar'
import { AppRouteDestination, AppRouteDestinationOptions } from '../../../component/AppRouteDestination'
import { AppUIState } from '../../../entity/AppUIState'
import {
  AppThemeOverlayPolicy,
  OverlayMaterialDecision,
  OverlaySurfaceRole
} from '../../../theme/AppThemeOverlayPolicy'
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver'
import { AppThemeTokens } from '../../../theme/AppThemeTokens'
import { HdsUiCapability } from '../../../theme/HdsUiCapability'
import {
  AccountItemGroup,
  AccountRowViewModel,
  AccountUIState
} from './AccountUIState'
import { AccountViewModel } from './AccountViewModel'
import { HdsNavDestination, HdsNavDestinationAttribute, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'

@ComponentV2
struct AccountRowContent {
  @Require @Param item: AccountRowViewModel
  @Require @Param switchingAccountKey: string
  @Require @Param deletingAccountKey: string
  @Require @Param primaryTextColor: ResourceColor
  @Require @Param secondaryTextColor: ResourceColor
  @Require @Param iconColor: ResourceColor
  @Event onDelete: () => void = () => {}

  build() {
    Row({ space: 12 }) {
      Image(this.item.isFeiniuVideo ?
        $r('app.media.feiniu_video_logo') : $r('app.media.jellyfin_logo'))
        .width(40)
        .height(40)
        .objectFit(ImageFit.Contain)
        .borderRadius(10)
      Column({ space: 5 }) {
        Row({ space: 6 }) {
          Text(this.item.displayName.length > 0 ? this.item.displayName : this.item.avatarText)
            .fontSize(16)
            .fontColor(this.primaryTextColor)
            .textAlign(TextAlign.Start)
            .fontWeight(FontWeight.Medium)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
            .ellipsisMode(EllipsisMode.END)
            .maxLines(1)
            .layoutWeight(1)
          if (this.item.isCurrent) {
            Text($r('app.string.account_current'))
              .fontColor($r('app.color.color_main'))
              .fontSize(12)
              .padding({ left: 6, right: 6, top: 2, bottom: 2 })
              .backgroundColor($r('app.color.primary_light_hover'))
              .borderRadius(4)
          }
        }
        Text(this.item.address)
          .fontSize(11)
          .fontColor(this.secondaryTextColor)
          .textAlign(TextAlign.Start)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .ellipsisMode(EllipsisMode.END)
          .width('100%')
      }
      .height('100%')
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
      .justifyContent(FlexAlign.Center)
      if (this.switchingAccountKey === this.item.accountKey ||
        this.deletingAccountKey === this.item.accountKey) {
        LoadingProgress().width(24).height(24)
      } else if (!this.item.isCurrent) {
        Image($r('app.media.icon_del'))
          .width(32)
          .height(32)
          .fillColor(this.iconColor)
          .padding(8)
          .onClick(this.onDelete)
      }
    }
    .width('100%')
    .height('100%')
  }
}

@ComponentV2
struct AccountEmptyState {
  @Require @Param secondaryTextColor: ResourceColor
  @Param nativeMaterial: boolean = false
  @Event onAdd: () => void = () => {}

  build() {
    Column({ space: 16 }) {
      Image($r('app.media.no_history')).width(200).height(200)
      Text($r('app.string.account_empty'))
        .fontSize(14)
        .fontColor(this.secondaryTextColor)
      Button($r('app.string.account_add_title'))
        .height(44)
        .padding({ left: 24, right: 24 })
        .fontColor(Color.White)
        .backgroundColor($r('app.color.color_main'))
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.PrimaryInteractive, this.nativeMaterial))
        .onClick(this.onAdd)
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
  }
}

@ComponentV2
struct AccountGroupHeader {
  @Require @Param title: string
  @Require @Param primaryTextColor: ResourceColor

  build() {
    Row() {
      Text(this.title)
        .fontSize(14)
        .fontWeight(FontWeight.Bold)
        .fontColor(this.primaryTextColor)
        .width('100%')
        .height(32)
        .textAlign(TextAlign.Start)
    }
    .width('100%')
  }
}

@ComponentV2
struct AccountLegacyRow {
  @Require @Param item: AccountRowViewModel
  @Require @Param switchingAccountKey: string
  @Require @Param deletingAccountKey: string
  @Require @Param primaryTextColor: ResourceColor
  @Require @Param secondaryTextColor: ResourceColor
  @Require @Param iconColor: ResourceColor
  @Event onDelete: () => void = () => {}
  @Event onOpen: () => void = () => {}

  build() {
    Row() {
      AccountRowContent({
        item: this.item,
        switchingAccountKey: this.switchingAccountKey,
        deletingAccountKey: this.deletingAccountKey,
        primaryTextColor: this.primaryTextColor,
        secondaryTextColor: this.secondaryTextColor,
        iconColor: this.iconColor,
        onDelete: this.onDelete
      })
    }
    .padding({ left: 16, right: 16, top: 12, bottom: 12 })
    .margin({ bottom: 10 })
    .width('100%')
    .height(80)
    .backgroundColor($r('app.color.bg_1'))
    .borderRadius(8)
    .onClick(this.onOpen)
  }
}

@ComponentV2
export struct AccountContent {
  @Param showLegacyActionBar: boolean = false
  @Param fromLogin: boolean = false
  @Local ui: AccountUIState = new AccountUIState(this.getUIContext())
  @Local vm: AccountViewModel = new AccountViewModel(this.ui)
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!

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

  private openJellyfin(): void {
    HMRouterMgr.to(RouterConsts.JellyfinConnectPage).push()
  }

  private openFeiniuVideo(): void {
    HMRouterMgr.to(RouterConsts.FeiniuVideoConnectPage).push()
  }

  private openProviderActionMenu(): void {
    this.getUIContext().getPromptAction().showActionMenu({
      title: $r('app.string.account_add_title'),
      buttons: [{ text: $r('app.string.provider_jellyfin_name'), color: this.primaryTextColor() },
        { text: $r('app.string.feiniu_video_service_name'), color: this.primaryTextColor() }]
    }).then((result) => {
      if (result.index === 0) {
        this.openJellyfin()
      } else if (result.index === 1) {
        this.openFeiniuVideo()
      }
    }).catch(() => {
    })
  }

  private providerMenuOptions(): MenuOptions {
    const decision = AppThemeOverlayPolicy.resolve(
      OverlaySurfaceRole.AppFloating,
      this.appUIState.themeStyle,
      HdsUiCapability.supportsSystemMaterial(),
      this.appUIState.nativeThemeAvailable
    )
    if (decision === OverlayMaterialDecision.UseFloatingMaterial) {
      return {
        placement: Placement.BottomRight,
        backgroundColor: Color.Transparent,
        backgroundBlurStyle: BlurStyle.NONE,
        systemMaterial: AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating)
      }
    }
    if (decision === OverlayMaterialDecision.UseBlurFallback) {
      return {
        placement: Placement.BottomRight,
        backgroundColor: Color.Transparent,
        backgroundBlurStyle: BlurStyle.Thin
      }
    }
    if (decision === OverlayMaterialDecision.DisableSystemMaterial) {
      return {
        placement: Placement.BottomRight,
        systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
      }
    }
    return { placement: Placement.BottomRight }
  }




  private primaryTextColor(): Resource {
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

  // CustomBuilderAdapter: account-delete-dialog
  @Builder
  private deleteDialogBuilder(item: AccountRowViewModel) {
    AlertDialogV2({
      primaryTitle: $r('app.string.account_delete_title'),
      content: $r('app.string.account_delete_message'),
      primaryButton: this.cancelDialogButton(),
      secondaryButton: this.deleteDialogButton(item)
    })
  }

  // CustomBuilderAdapter: account-list-group-header
  @Builder
  private legacyGroupHeaderBuilder(item: Object, _position: number) {
    AccountGroupHeader({
      title: (item as AccountItemGroup).title,
      primaryTextColor: this.primaryTextColor()
    })
  }

  // CustomBuilderAdapter: account-list-item
  @Builder
  private legacyItemBuilder(item: Object, _index: number) {
    AccountLegacyRow({
      item: item as AccountRowViewModel,
      switchingAccountKey: this.ui.switchingAccountKey,
      deletingAccountKey: this.ui.deletingAccountKey,
      primaryTextColor: this.primaryTextColor(),
      secondaryTextColor: this.secondaryTextColor(),
      iconColor: this.iconColor(),
      onDelete: () => this.openDeleteDialog(item as AccountRowViewModel),
      onOpen: () => this.onAccountClick(item as AccountRowViewModel)
    })
  }

  // CustomBuilderAdapter: account-list-empty
  @Builder
  private legacyEmptyBuilder() {
    AccountEmptyState({
      secondaryTextColor: this.secondaryTextColor(),
      nativeMaterial: AppThemeTokens.isNative(this.appUIState.themeStyle),
      onAdd: () => this.openProviderActionMenu()
    })
  }


  private openDeleteDialog(item: AccountRowViewModel): void {
    if (this.appUIState.systemMaterialAvailable) {
      this.getUIContext().getPromptAction().openCustomDialog({
        builder: () => {
          this.deleteDialogBuilder(item)
        },
        systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
      }).catch((): void => {
      })
    } else {
      this.getUIContext().getPromptAction().openCustomDialog({
        builder: () => {
          this.deleteDialogBuilder(item)
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











  build() {
    Column() {
      if ((this.showLegacyActionBar)) {
        Stack() {
          ActionBar({
            title: this.fromLogin ?
              $r('app.string.account_history_title') : $r('app.string.account_switch_title')
          })
          if (!this.fromLogin) {
            Row() {
              Button() {
                SymbolGlyph($r('sys.symbol.plus'))
                  .fontSize(22)
                  .fontColor([this.iconColor()])
              }
              .width(AppThemeTokens.actionSize(this.appUIState.themeStyle))
              .height(AppThemeTokens.actionSize(this.appUIState.themeStyle))
              .borderRadius(AppThemeTokens.actionSize(this.appUIState.themeStyle) / 2)
              .backgroundColor(Color.Transparent)
              .accessibilityText($r('app.string.account_add_title'))
              .onClick(() => this.openProviderActionMenu())
            }
              .align(Alignment.TopEnd)
              .margin({
                top: this.appUIState.safeTop + 4,
                right: AppThemeTokens.pageHorizontalPadding(
                  this.appUIState.themeStyle, this.appUIState.isBigScreen)
              })
          }
        }
        .width('100%')
        .height(UIConstants.ACTION_BAR_HEIGHT + this.appUIState.safeTop)
      }
      if ((this.showLegacyActionBar)) {
        ListView({
          isLazyData: false,
          itemGroupHeader: (item: Object, position: number): void =>
            this.legacyGroupHeaderBuilder(item, position),
          itemGroupData: this.ui.groupAccountList,
          itemLayout: (item: Object, index: number): void => this.legacyItemBuilder(item, index),
          enableRefresh: false,
          enableLoadMore: false,
          emptyLayout: (): void => this.legacyEmptyBuilder(),
          showEmptyLayout: this.ui.groupAccountList.length === 0,
          lazyCachedCount: 10
        })
          .width('100%')
          .padding({ top: 10, bottom: 10, left: 16, right: 16 })
          .layoutWeight(1)
          .backgroundColor($r('app.color.bg_main'))
      } else {
        if (this.ui.loading && this.ui.groupAccountList.length === 0) {
          LoadingProgress()
            .width(36)
            .height(36)
            .layoutWeight(1)
        } else if (this.ui.groupAccountList.length === 0) {
          AccountEmptyState({
            secondaryTextColor: this.secondaryTextColor(),
            nativeMaterial: AppThemeTokens.isNative(this.appUIState.themeStyle),
            onAdd: () => this.openProviderActionMenu()
          })
        } else {
          List({ space: 12 }) {
            ForEach(this.ui.groupAccountList, (itemGroup: AccountItemGroup) => {
              ListItem() {
                Column() {
                  Text((itemGroup).title)
                    .fontSize(14)
                    .fontWeight(FontWeight.Bold)
                    .fontColor($r('sys.color.font_primary'))
                    .width('100%')
                    .height(40)
                    .padding({ left: 16, right: 16 })
                    .textAlign(TextAlign.Start)

                  ForEach((itemGroup).items, (item: AccountRowViewModel, index: number) => {
                    Column() {
                      Row({ space: 12 }) {
                        AccountRowContent({
                          item: item,
                          switchingAccountKey: this.ui.switchingAccountKey,
                          deletingAccountKey: this.ui.deletingAccountKey,
                          primaryTextColor: this.primaryTextColor(),
                          secondaryTextColor: this.secondaryTextColor(),
                          iconColor: this.iconColor(),
                          onDelete: () => this.openDeleteDialog(item)
                        })
                      }
                      .padding({ left: 16, right: 16, top: 12, bottom: 12 })
                      .width('100%')
                      .height(80)
                      .onClick((): void => {
                        this.onAccountClick((item))
                      })

                      if ((index) < ((itemGroup).items.length) - 1) {
                        Divider()
                          .color($r('sys.color.comp_divider'))
                          .margin({ left: 16, right: 16 })
                      }
                    }
                    .width('100%')
                  }, (item: AccountRowViewModel) => item.accountKey)
                }
                .width('100%')
                .borderRadius(AppThemeTokens.groupRadius(this.appUIState.themeStyle))
                .clip(true)
                .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
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
    }
    .width('100%')
    .height('100%')
    .backgroundColor((this.showLegacyActionBar) ? $r('app.color.bg_main') : Color.Transparent)
  }
}

@HMRouter({
  pageUrl: RouterConsts.AccountPage,
  useNavDst: true
})
@ComponentV2
export struct AccountPage {
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
  @Local fromLogin: boolean = HMRouterMgr.getCurrentParam() ?
    (HMRouterMgr.getCurrentParam() as Record<string, boolean>)['fromLogin'] : false

  private openProviderActionMenu(): void {
    this.getUIContext().getPromptAction().showActionMenu({
      title: $r('app.string.account_add_title'),
      buttons: [{ text: $r('app.string.provider_jellyfin_name'), color: $r('sys.color.font_primary') },
        { text: $r('app.string.feiniu_video_service_name'), color: $r('sys.color.font_primary') }]
    }).then((result) => {
      if (result.index === 0) {
        HMRouterMgr.to(RouterConsts.JellyfinConnectPage).push()
      } else if (result.index === 1) {
        HMRouterMgr.to(RouterConsts.FeiniuVideoConnectPage).push()
      }
    }).catch(() => {
    })
  }

  private routeOptions(): AppRouteDestinationOptions {
    return {
        title: this.fromLogin ?
                $r('app.string.account_history_title') : $r('app.string.account_switch_title'),
        menus: this.fromLogin ? [] : [{
                value: $r('app.string.account_add_title'),
                icon: $r('sys.symbol.plus'),
                action: () => this.openProviderActionMenu()
              }]
      }
  }

  build() {
    if (this.routeDestination.usesHdsDestination()) {
      HdsNavDestination() {
        Column() {
          Blank().height(this.routeDestination.nativeContentTopInset(this.routeOptions()))
          Stack() {
            if (this.routeDestination.usesNativeChrome()) {
              AccountContent({ showLegacyActionBar: false, fromLogin: this.fromLogin })
            } else {
              AccountContent({ showLegacyActionBar: true, fromLogin: this.fromLogin })
            }
          }
          .width('100%')
          .layoutWeight(1)
        }
        .width('100%')
        .height('100%')
      }
      .backgroundColor(this.routeDestination.hdsBackgroundColor())
      .titleMode(HdsNavDestinationTitleMode.MINI)
      .titleBar(this.routeDestination.nativeTitleBarOptions(this.routeOptions()))
      .hideTitleBar(this.routeDestination.hdsTitleBarHidden(this.routeOptions()))
      .hideBackButton(!this.routeDestination.backButtonVisible(this.routeOptions()))
      .hideToolBar(true)
      .bindToScrollable(this.routeDestination.hdsBoundScrollers(this.routeOptions()))
      .onWillAppear(() => this.routeDestination.helper.onWillAppear())
      .onAppear(() => this.routeDestination.helper.onAppear())
      .onWillShow(() => this.routeDestination.helper.onWillShow())
      .onShown(() => this.routeDestination.helper.onShown())
      .onWillHide(() => this.routeDestination.helper.onWillHide())
      .onHidden(() => this.routeDestination.helper.onHidden())
      .onWillDisappear(() => this.routeDestination.helper.onWillDisappear())
      .onDisAppear(() => this.routeDestination.helper.onDisAppear())
      .onReady((ctx: NavDestinationContext) => this.routeDestination.helper.onReady(ctx))
      .onBackPressed(() => this.routeDestination.handleBackPressed(this.routeOptions()))
      .onResult((result: ESObject) => this.routeDestination.helper.onResult(result))
      .onActive((reason: NavDestinationActiveReason) => this.routeDestination.helper.onActive(reason))
      .onInactive((reason: NavDestinationActiveReason) => this.routeDestination.helper.onInactive(reason))
      .ignoreLayoutSafeArea(
        this.routeDestination.nativeSafeAreaTypes(),
        this.routeDestination.nativeSafeAreaEdges()
      )
      .width('100%')
      .height('100%')
    } else {
      NavDestination() {
        AccountContent({ showLegacyActionBar: true, fromLogin: this.fromLogin })
      }
      .attributeModifier(this.routeDestination.legacyModifier(this.routeOptions()))
      .gestureModifier(this.routeDestination.gestureModifier)
      .width('100%')
      .height('100%')
    }
  }
}
