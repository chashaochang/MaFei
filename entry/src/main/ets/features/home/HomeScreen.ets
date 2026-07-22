import { AppUIState } from "../../entity/AppUIState";
import { HomeTab } from "./hometab/HomeTab";
import { ChasingTab } from "./chasing/ChasingTab";
import { MediaTab } from "./mediatab/MediaTab";
import { MineTab } from "./minetab/MineTab";
import { AppStorageV2 } from "@kit.ArkUI";
import { BreakpointTypeEnum } from "../../utils/BreakpointPolicy";
import {
  DividerMode,
  hdsEffect,
  hdsMaterial,
  HdsTabs,
  HdsTabsController,
  HdsTabsModifier
} from "@kit.UIDesignKit";
import { CustomTab } from "../../component/tabbar/CustomTab";
import { HomeScreenUIState, PadMenuItem, PadMenuType } from "./HomeScreenUIState";
import { HomeScreenViewModel } from "./HomeScreenViewModel";
import { getUIAbilityContext } from "../../common/CommonFunc";
import { RouterConsts } from "../../common/RouterConsts";
import { HomeBackPressedEvent } from "../../events/Events";
import { HMRouterMgr } from "@hadss/hmrouter";
import { process } from "@kit.ArkTS";
import { FavoriteListPage } from "../favorite/FavoriteListPage";
import { VideoListPage } from "../videolist/VideoListPage";
import { LoadingView } from "../../ui/components/LoadingView";
import { MediaItem } from "./hometab/HomeUIState";
import { HORIZONTAL_CARD_SPACE } from "../../common/OneMoreConst";
import { UIConstants } from "../../common/UIConstants";
import { OneMoreUtil } from "../../utils/OneMoreUtil";
import { PadTopTab, TabItem } from "../../component/tabbar/PadTopTab";
import { ThemeUpgradeDialog } from "../../component/ThemeUpgradeDialog";
import { ThemeStyle } from "../../entity/ThemeStyle";
import { themePromptService, ThemeWriteStatus } from "../../theme/ThemePromptService";
import { AppThemeTokens } from "../../theme/AppThemeTokens";
import { nativeThemeCapability } from "../../theme/NativeThemeCapability";
import { HomeShellKind, HomeShellPolicy } from "./HomeShellPolicy";
import { HomeInsetsPolicy } from "./HomeInsetsPolicy";
import { HomeDestination, HomeDestinationMapper } from "./HomeDestination";

@ComponentV2
export struct HomeScreen {
  @Local appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;
  private tabsController: HdsTabsController = new HdsTabsController()
  private contentTabsController: TabsController = new TabsController()
  private nativeTabsModifier?: HdsTabsModifier
  @Local ui: HomeScreenUIState = new HomeScreenUIState(this.getUIContext())
  @Local vm: HomeScreenViewModel = new HomeScreenViewModel(this.ui)
  private lastTime: number = 0;
  private themePromptChecked: boolean = false
  private themePromptCompleted: boolean = false
  private themePromptTimer: number = -1
  private themeDialogId: number = -1

  @Monitor('appUIState.currentBreakpoint')
  onBreakpointChange() {
    this.updateIlluminatedType()
    this.appUIState.isBigScreen = !this.appUIState.currentBreakpoint.includes('s')
    if (this.appUIState.currentBreakpoint.includes('l')) {
      this.ui.isMenuModalVisible = false
      this.ui.isLeftSidebarVisible = true
    }
    this.configureNativeTabs()
  }

  @Monitor('appUIState.themeStyle')
  onThemeStyleChange() {
    this.updateIlluminatedType()
    this.configureNativeTabs()
  }

  @Monitor('appUIState.safeBottom')
  onSafeBottomChange() {
    this.configureNativeTabs()
  }

  private resolveShell(): HomeShellKind {
    return HomeShellPolicy.resolve(
      this.appUIState.currentBreakpoint as BreakpointTypeEnum,
      this.appUIState.themeStyle,
      this.appUIState.nativeThemeAvailable
    )
  }

  private usesPadMediaContent(): boolean {
    const shell = this.resolveShell()
    return shell === HomeShellKind.MediumDrawer || shell === HomeShellKind.LargeSidebar
  }

  private phoneSelectedIndex(): number {
    return HomeDestinationMapper.phoneIndex(this.ui.selectedDestination)
  }

  private padSelectedIndex(): number {
    return HomeDestinationMapper.padMenuIndex(
      this.ui.selectedDestination,
      this.ui.lastSelectedMediaIndex
    )
  }

  private contentSelectedIndex(): number {
    return HomeDestinationMapper.contentIndex(
      this.ui.selectedDestination,
      this.usesPadMediaContent(),
      this.ui.lastSelectedMediaIndex
    )
  }

  private homeContentKey(item: PadMenuItem, index: number): string {
    const mediaId = item.extra?.id
    if (mediaId !== undefined && mediaId.length > 0) {
      return `media:${item.type}:${mediaId}`
    }
    return `menu:${item.type}:${index}`
  }

  private selectPhoneIndex(index: number): void {
    this.ui.selectedDestination = HomeDestinationMapper.fromPhoneIndex(index)
  }

  private selectPadMenu(index: number, item: PadMenuItem): void {
    if (item.extra) {
      this.ui.lastSelectedMediaIndex = index
    }
    this.ui.selectedDestination = HomeDestinationMapper.fromPadMenuIndex(
      index,
      item.extra !== undefined
    )
  }

  @Monitor('ui.selectedDestination')
  onDestinationChange(): void {
    this.contentTabsController.changeIndex(this.contentSelectedIndex())
  }

  @Monitor('ui.lastSelectedMediaIndex')
  onSelectedMediaChange(): void {
    this.contentTabsController.changeIndex(this.contentSelectedIndex())
  }

  @Monitor('appUIState.currentBreakpoint')
  onContentBreakpointChange(): void {
    this.contentTabsController.changeIndex(this.contentSelectedIndex())
  }

  private useNativeTabs(): boolean {
    return this.resolveShell() === HomeShellKind.PhoneNativeHds
  }

  private updateIlluminatedType(): void {
    if (!this.appUIState.currentBreakpoint.includes('s')) {
      this.appUIState.illuminatedType = hdsEffect.PointLightIlluminatedType.DEFAULT_FEATHERING_BORDER
      return
    }
    this.appUIState.illuminatedType = hdsEffect.PointLightIlluminatedType.NONE
  }

  private configureNativeTabs(): void {
    this.nativeTabsModifier = undefined
    if (!this.useNativeTabs()) {
      return
    }
    const navigationChromeInset = HomeInsetsPolicy.navigationChromeInset(
      HomeShellKind.PhoneNativeHds,
      this.appUIState.safeBottom
    )
    const modifier: HdsTabsModifier = new HdsTabsModifier()
    modifier
      .vertical(false)
      .barPosition(BarPosition.End)
      .barHeight(navigationChromeInset)
      .barFloatingStyle({
        adaptToHandedness: true,
        barBottomMargin: this.appUIState.safeBottom,
        systemMaterialEffect: {
          materialType: hdsMaterial.MaterialType.IMMERSIVE,
          materialLevel: hdsMaterial.MaterialLevel.ADAPTIVE,
        },
      })
    this.nativeTabsModifier = modifier
  }

  aboutToAppear() {
    this.appUIState.isBigScreen = !this.appUIState.currentBreakpoint.includes('s')
    this.updateIlluminatedType()
    this.configureNativeTabs()
    this.vm.init()
    getUIAbilityContext().eventHub.on(HomeBackPressedEvent, () => {
      //点两次退出
      let time = new Date().getTime();
      if (time - this.lastTime > 1000) {
        this.lastTime = time;
        this.getUIContext().getPromptAction().showToast({
          message: '再次返回退出应用',
          duration: 1000
        });
      } else {
        new process.ProcessManager().exit(0)
      }
    })
    this.themePromptTimer = setTimeout(() => {
      this.showThemePromptIfNeeded()
    }, 350)
  }

  aboutToDisappear() {
    if (this.themePromptTimer >= 0) {
      clearTimeout(this.themePromptTimer)
      this.themePromptTimer = -1
    }
    getUIAbilityContext().eventHub.off(HomeBackPressedEvent)
  }

  private showThemePromptIfNeeded(): void {
    if (this.themePromptChecked || !themePromptService.shouldShow()) {
      return
    }
    this.themePromptChecked = true
    this.themePromptCompleted = false
    this.getUIContext().getPromptAction().openCustomDialog({
      builder: () => {
        this.themePromptDialogBuilder()
      },
      alignment: DialogAlignment.Center,
      autoCancel: true,
      backgroundColor: Color.Transparent,
      onWillDismiss: (action: DismissDialogAction) => {
        if (!this.themePromptCompleted && !this.saveThemePrompt(ThemeStyle.Feiniu, false)) {
          return
        }
        this.themeDialogId = -1
        action.dismiss()
      }
    }).then((dialogId: number) => {
      this.themeDialogId = dialogId
      if (this.themePromptCompleted) {
        try {
          this.getUIContext().getPromptAction().closeCustomDialog(dialogId)
          this.themeDialogId = -1
        } catch (error) {
        }
      }
    }).catch(() => {
      this.themePromptChecked = false
      this.themePromptCompleted = false
    })
  }

  private saveThemePrompt(style: ThemeStyle, closeDialog: boolean = true): boolean {
    try {
      const result = themePromptService.completePrompt(style, nativeThemeCapability.current())
      if (result.status !== ThemeWriteStatus.Success) {
        throw new Error(result.status)
      }
      this.appUIState.themeStyle = result.effectiveTheme
      this.appUIState.themePromptPending = false
      this.themePromptCompleted = true
      if (closeDialog && this.themeDialogId >= 0) {
        this.getUIContext().getPromptAction().closeCustomDialog(this.themeDialogId)
        this.themeDialogId = -1
      }
      return true
    } catch (error) {
      this.getUIContext().getPromptAction().showToast({
        message: $r('app.string.theme_save_failed')
      })
      return false
    }
  }

  @Builder
  themePromptDialogBuilder() {
    ThemeUpgradeDialog({
      onSelectFeiniu: () => {
        this.saveThemePrompt(ThemeStyle.Feiniu)
      },
      onSelectNative: () => {
        this.saveThemePrompt(ThemeStyle.Native)
      },
      onDismiss: () => {
        this.saveThemePrompt(ThemeStyle.Feiniu)
      }
    })
  }

  private getPadTopTabItems(): TabItem[] {
    void this.ui.padMenuDataVersion
    const ds = this.vm.datasource
    if (ds.totalCount() < 3) {
      return [
        new TabItem('首页', $r('app.media.ic_home_sel'), $r('app.media.ic_home_unsel')),
        new TabItem('追剧', $r('app.media.history'), $r('app.media.history')),
        new TabItem('收藏', $r('app.media.ic_favorite_sel'), $r('app.media.ic_favorite_unsel')),
        new TabItem('全部', $r('app.media.all'), $r('app.media.all'))
      ]
    }
    const thirdIndex = this.getThirdItemDatasourceIndex()
    const thirdItem = ds.getData(thirdIndex)
    return [
      new TabItem('首页', $r('app.media.ic_home_sel'), $r('app.media.ic_home_unsel')),
      new TabItem('追剧', $r('app.media.history'), $r('app.media.history')),
      new TabItem('收藏', $r('app.media.ic_favorite_sel'), $r('app.media.ic_favorite_unsel')),
      new TabItem(thirdItem.title, thirdItem.selectedIcon as Resource, thirdItem.unselectedIcon as Resource)
    ]
  }

  private getPadTopTabSelectIndex(): number {
    const selectedIndex = this.padSelectedIndex()
    if (selectedIndex >= 0 && selectedIndex <= 2) {
      return selectedIndex
    }
    return this.ui.selectedDestination === HomeDestination.Media ? 3 : -1
  }

  private getThirdItemDatasourceIndex(): number {
    const ds = this.vm.datasource
    if (this.ui.selectedDestination !== HomeDestination.Media) {
      const lastMedia = this.ui.lastSelectedMediaIndex
      if (lastMedia >= 0 && lastMedia < ds.totalCount() && ds.getData(lastMedia).type === PadMenuType.ITEM &&
        ds.getData(lastMedia).extra) {
        return lastMedia
      }
      return ds.getFirstMediaItemIndex()
    }
    return this.padSelectedIndex()
  }

  private currentPadTitle(): string {
    const selectedIndex = this.padSelectedIndex()
    if (selectedIndex >= 0 && selectedIndex < this.vm.datasource.totalCount()) {
      return this.vm.datasource.getData(selectedIndex).title
    }
    if (this.ui.selectedDestination === HomeDestination.Mine) {
      return '我的'
    }
    return '首页'
  }

  @Builder
  leftMenuBuilder(onItemSelect: (index: number) => void) {
    Column() {
      Row() {
        Row() {
          Image($r('app.media.app_icon')).height(48)
          Text($r('app.string.app_name'))
            .fontColor($r('app.color.text_primary'))
            .fontSize(20)
            .fontWeight(FontWeight.Medium)
            .fontStyle(FontStyle.Italic)
        }
        .layoutWeight(1)
        .justifyContent(FlexAlign.Start)

        if (this.appUIState.currentBreakpoint.includes('l') && this.ui.isLeftSidebarVisible && !OneMoreUtil.isPc()) {
          Button() {
            SymbolGlyph($r('sys.symbol.close_sidebar'))
              .fontSize(20)
              .fontColor([$r('app.color.text_primary')])
          }
          .width(40)
          .height(40)
          .borderRadius(20)
          .align(Alignment.Center)
          .backgroundColor($r('app.color.bg_main'))
          .onClick(() => {
            this.ui.context.animateTo({ duration: 300, curve: Curve.FastOutSlowIn }, () => {
              this.ui.isLeftSidebarVisible = false
            })
          })
        } else {
          Blank().width(40).height(40)
        }
      }
      .width('100%')
      .padding({
        left: 12,
        right: 12,
        bottom: 18
      })

      List() {
        LazyForEach(this.vm.datasource, (item: PadMenuItem, index: number) => {
          if (item.type == PadMenuType.TITLE) {
            ListItem() {
              Text(item.title).fontColor($r('app.color.text_1')).fontSize(12)
                .height(20)
                .margin({
                  top: 20,
                  left: 10
                })
            }
          } else {
            ListItem() {
              Row({ space: 8 }) {
                Image(this.padSelectedIndex() == index ? item.selectedIcon : item.unselectedIcon)
                  .fillColor(this.padSelectedIndex() == index ? $r('app.color.color_main') :
                    $r('app.color.text_primary'))
                  .width(20)
                  .height(20)
                Text(item.title)
                  .fontColor(this.padSelectedIndex() == index ? $r('app.color.color_main') :
                    $r('app.color.text_primary'))
                  .fontSize(14)
                Blank().layoutWeight(1)
                Text(item.badge > 0 ? item.badge > 99 ? '99+' : item.badge.toString() : '')
                  .fontColor(this.padSelectedIndex() == index ? $r('app.color.color_main') :
                    $r('app.color.text_primary'))
                  .fontSize(14)
              }
              .backgroundBlurStyle(this.padSelectedIndex() == index ? BlurStyle.Thin : BlurStyle.NONE)
              .backgroundColor((this.padSelectedIndex() == index &&
              this.vm.appUIState.currentBreakpoint.includes('l')) ? $r('app.color.menu_item_bg') : Color.Transparent)
              .width('100%')
              .padding(10)
              .borderRadius(8)
              .onClick(() => {
                if (this.padSelectedIndex() === index) {
                  return
                }
                this.selectPadMenu(index, item)
                onItemSelect(index)
              })
            }
          }
        })
      }.padding({
        left: 20,
        right: 20,
      })
      .width('100%')
      .listDirection(Axis.Vertical)
      .layoutWeight(1)
    }
    .width(240)
    .height('100%')
    .padding({
      top: 20,
      bottom: 32
    })
    .backgroundBlurStyle(BlurStyle.Thin)
    .borderColor($r('app.color.border'))
    .borderWidth(1)
    .borderRadius(20)
    .visualEffect(new hdsEffect.HdsEffectBuilder().pointLight({
      illuminatedType: this.appUIState.illuminatedType,
    }).buildEffect())
  }

  @Builder
  tabBuilder(title: string, targetIndex: number, selectedImg: Resource, normalImg: Resource,
    badgeCount: number = 0) {
    Column({ space: 4 }) {
      Stack({ alignContent: Alignment.TopEnd }) {
        Image(this.phoneSelectedIndex() === targetIndex ? selectedImg : normalImg)
          .size({ width: 20, height: 20 })
          .fillColor(this.phoneSelectedIndex() === targetIndex ? $r('app.color.color_main') :
            (this.useNativeTabs() ? $r('sys.color.icon_primary') : $r('app.color.text_primary')))
        if (badgeCount > 0) {
          Text(badgeCount > 99 ? '99+' : badgeCount.toString())
            .fontSize(9)
            .fontWeight(FontWeight.Bold)
            .fontColor(Color.White)
            .height(14)
            .width(badgeCount > 9 ? 22 : 14)
            .padding({ left: 4, right: 4 })
            .backgroundColor('#ff4d4f')
            .borderRadius(7)
            .translate({ x: 8, y: -5 })
        }
      }
      .width(32)
      .height(22)
      .alignContent(Alignment.Center)
      Text(title)
        .fontSize(14)
        .fontColor(this.phoneSelectedIndex() === targetIndex ? $r('app.color.color_main') :
          (this.useNativeTabs() ? $r('sys.color.font_primary') : $r('app.color.text_primary')))
    }
    .height('100%')
    .justifyContent(FlexAlign.Center)
    .backgroundColor(this.phoneSelectedIndex() === targetIndex && !this.useNativeTabs() ?
      $r('app.color.bg_tab') : Color.Transparent)
    .borderRadius(this.useNativeTabs() ? 0 : 99)
    .width(70)
  }

  @Builder
  menuModalBuilder() {
    Stack() {
      Column()
        .width('100%')
        .height('100%')
        .backgroundColor('rgba(0,0,0,0.5)')
        .onClick(() => {
          this.ui.context.animateTo({ duration: 300, curve: Curve.FastOutSlowIn }, () => {
            this.ui.isMenuModalVisible = false
          })
        })
      Row() {
        Column() {
          this.leftMenuBuilder(() => {
            this.ui.context.animateTo({ duration: 300, curve: Curve.FastOutSlowIn }, () => {
              this.ui.isMenuModalVisible = false
            })
          })
        }
        .width(240)
        .height('100%')
        .margin({ left: 12 })
        .padding({
          top: this.appUIState.safeTop + 12,
          bottom: this.appUIState.safeBottom + 12,
        })
        .transition(TransitionEffect.translate({ x: -240 }).animation({ duration: 300, curve: Curve.FastOutSlowIn }))

        Blank()
          .layoutWeight(1)
          .onClick(() => {
            this.ui.context.animateTo({ duration: 300, curve: Curve.FastOutSlowIn }, () => {
              this.ui.isMenuModalVisible = false
            })
          })
      }
      .width('100%')
      .height('100%')
      .alignItems(VerticalAlign.Top)
      .justifyContent(FlexAlign.Start)
    }
    .width('100%')
    .height('100%')
    .alignContent(Alignment.Start)
  }

  @Builder
  private homeContentOwner(contentBottomInset: number) {
    Tabs({
      index: this.contentSelectedIndex(),
      controller: this.contentTabsController
    }) {
      TabContent() {
        HomeTab({ contentBottomInset })
      }
      TabContent() {
        ChasingTab({ fromHome: true, contentBottomInset })
      }
      TabContent() {
        FavoriteListPage({ fromHome: true, contentBottomInset })
      }
      TabContent() {
        MediaTab({ contentBottomInset })
      }
      TabContent() {
        MineTab({ contentBottomInset })
      }

      LazyForEach(this.vm.datasource, (item: PadMenuItem) => {
        TabContent() {
          if (item.extra) {
            VideoListPage({
              fromHome: true,
              mediaItem: item.extra as MediaItem
            })
          } else {
            Stack()
          }
        }
      }, (item: PadMenuItem, index: number) => this.homeContentKey(item, index))
    }
    .barHeight(0)
    .barWidth(0)
    .vertical(false)
    .scrollable(false)
    .animationDuration(0)
    .width('100%')
    .height('100%')
  }

  @Builder
  private phoneChromeBuilder(shell: HomeShellKind, navigationChromeInset: number) {
    Stack({ alignContent: Alignment.Bottom }) {
      HdsTabs({
        index: this.phoneSelectedIndex(),
        barPosition: BarPosition.End,
        controller: this.tabsController
      }) {
        TabContent() {
          Stack()
        }
        .tabBar(this.tabBuilder('首页', 0, $r('app.media.ic_home_sel'), $r('app.media.ic_home_unsel')))

        TabContent() {
          Stack()
        }
        .tabBar(this.tabBuilder('追剧', 1, $r('app.media.history'), $r('app.media.history'),
          this.ui.chasingCount))

        TabContent() {
          Stack()
        }
        .tabBar(this.tabBuilder('收藏', 2, $r('app.media.ic_favorite_sel'), $r('app.media.ic_favorite_unsel')))

        TabContent() {
          Stack()
        }
        .tabBar(this.tabBuilder('媒体库', 3, $r('app.media.ic_media_sel'), $r('app.media.ic_media_unsel')))

        TabContent() {
          Stack()
        }
        .tabBar(this.tabBuilder('我', 4, $r('app.media.ic_mine_sel'), $r('app.media.ic_mine_unsel')))
      }
      .width('100%')
      .height(shell === HomeShellKind.PhoneNativeHds ? navigationChromeInset : 0)
      .barHeight(shell === HomeShellKind.PhoneNativeHds ? navigationChromeInset : 0)
      .barMode(BarMode.Fixed, { nonScrollableLayoutStyle: LayoutStyle.ALWAYS_CENTER })
      .vertical(false)
      .divider({ mode: DividerMode.NONE })
      .barOverlap(true)
      .animationDuration(0)
      .attributeModifier(shell === HomeShellKind.PhoneNativeHds ? this.nativeTabsModifier : undefined)
      .onAnimationStart((index: number, targetIndex: number, _event: TabsAnimationEvent) => {
        if (index !== targetIndex) {
          this.selectPhoneIndex(targetIndex)
        }
      })
      .scrollable(false)
      .onChange((index: number) => {
        this.selectPhoneIndex(index)
      })

      CustomTab({
        selectIndex: this.phoneSelectedIndex(),
        chasingBadgeCount: this.ui.chasingCount,
        onItemClick: (index: number) => {
          this.selectPhoneIndex(index)
          this.tabsController.changeIndex(index)
        }
      })
        .margin({
          bottom: Math.max(0, navigationChromeInset - UIConstants.BOTTOM_BAR_HEIGHT)
        })
        .visibility(shell === HomeShellKind.PhoneStandard ? Visibility.Visible : Visibility.None)
    }
    .width('100%')
    .height('100%')
    .hitTestBehavior(HitTestMode.Transparent)
  }

  @Builder
  private padChromeBuilder(shell: HomeShellKind) {
    Stack() {
      Row() {
        Column() {
          this.leftMenuBuilder(() => {
          })
        }
        .width(240)
        .height('100%')
        .padding({
          top: this.appUIState.safeTop + 12,
          bottom: this.appUIState.safeBottom + 12
        })
        .visibility((shell === HomeShellKind.LargeSidebar && this.ui.isLeftSidebarVisible) ?
          Visibility.Visible : Visibility.None)
        .transition(TransitionEffect.asymmetric(
          TransitionEffect.scale({ x: 0, y: 0 })
            .combine(TransitionEffect.translate({ x: 120, y: -300 }))
            .animation({ duration: 300, curve: Curve.FastOutSlowIn }),
          TransitionEffect.scale({ x: 0, y: 0 })
            .combine(TransitionEffect.translate({ x: 120, y: -300 }))
            .animation({ duration: 300, curve: Curve.FastOutSlowIn })
        ))

        Stack() {
          Column() {
            Row({ space: 16 }) {
              Text(this.currentPadTitle())
                .fontSize(24)
                .fontWeight(FontWeight.Bold)
                .margin({
                  left: HORIZONTAL_CARD_SPACE.getValue(this.vm.appUIState.currentBreakpoint) * 2
                })
                .hitTestBehavior(HitTestMode.None)
              Blank()
                .layoutWeight(1)
                .hitTestBehavior(HitTestMode.None)
              Button() {
                SymbolGlyph($r('sys.symbol.magnifyingglass'))
                  .fontSize(20)
                  .fontColor([$r('app.color.text_primary')])
              }
              .width(40)
              .height(40)
              .borderRadius(20)
              .align(Alignment.Center)
              .backgroundColor($r('app.color.bg_main'))
              .onClick(() => {
                HMRouterMgr.to(RouterConsts.SearchPage).push()
              })

              Button() {
                SymbolGlyph($r('sys.symbol.person'))
                  .fontSize(20)
                  .fontColor([$r('app.color.text_primary')])
              }
              .width(40)
              .height(40)
              .borderRadius(20)
              .align(Alignment.Center)
              .backgroundColor($r('app.color.bg_main'))
              .onClick(() => {
                HMRouterMgr.to(RouterConsts.MinePage).push()
              })
            }
            .width('100%')
            .alignItems(VerticalAlign.Center)
            .height(88)
            .padding({
              top: this.vm.appUIState.safeTop,
              right: 24
            })
            .hitTestBehavior(HitTestMode.Transparent)

            Blank()
              .layoutWeight(1)
              .hitTestBehavior(HitTestMode.None)
          }
          .hitTestBehavior(HitTestMode.Transparent)

          PadTopTab({
            list: this.getPadTopTabItems(),
            selectIndex: this.getPadTopTabSelectIndex(),
            onItemClick: (index: number) => {
              const targetIndex = index === 0 ? 0 : index === 1 ? 1 : index === 2 ? 2 :
                this.getThirdItemDatasourceIndex()
              if (targetIndex < 0 || targetIndex >= this.vm.datasource.totalCount()) {
                return
              }
              this.selectPadMenu(targetIndex, this.vm.datasource.getData(targetIndex))
            },
            onMenuButtonClick: () => {
              if (shell === HomeShellKind.LargeSidebar) {
                this.ui.context.animateTo({ duration: 300, curve: Curve.FastOutSlowIn }, () => {
                  this.ui.isLeftSidebarVisible = true
                })
              } else {
                this.ui.context.animateTo({ duration: 300, curve: Curve.FastOutSlowIn }, () => {
                  this.ui.isMenuModalVisible = true
                })
              }
            }
          })
            .margin({
              top: this.appUIState.safeTop
            })
            .visibility((shell === HomeShellKind.LargeSidebar && this.ui.isLeftSidebarVisible) ?
              Visibility.None : Visibility.Visible)
        }
        .layoutWeight(1)
        .height('100%')
        .alignContent(Alignment.Top)
        .hitTestBehavior(HitTestMode.Transparent)
      }
      .width('100%')
      .height('100%')
      .hitTestBehavior(HitTestMode.Transparent)

      if (this.ui.isLoading) {
        LoadingView()
      }

      if (this.ui.isMenuModalVisible && shell === HomeShellKind.MediumDrawer) {
        this.menuModalBuilder()
      }
    }
    .width('100%')
    .height('100%')
    .alignContent(Alignment.TopStart)
    .padding({
      left: shell === HomeShellKind.LargeSidebar ? 12 : 0
    })
    .hitTestBehavior(HitTestMode.Transparent)
  }

  @Builder
  private shellChromeBuilder(shell: HomeShellKind, navigationChromeInset: number) {
    Stack() {
      if (shell === HomeShellKind.MediumDrawer || shell === HomeShellKind.LargeSidebar) {
        this.padChromeBuilder(shell)
      } else {
        this.phoneChromeBuilder(shell, navigationChromeInset)
      }
    }
    .width('100%')
    .height('100%')
    .hitTestBehavior(HitTestMode.Transparent)
  }

  build() {
    Stack() {
      this.homeContentOwner(HomeInsetsPolicy.contentBottomInset(
        this.resolveShell(),
        this.appUIState.safeBottom
      ))
      this.shellChromeBuilder(
        this.resolveShell(),
        HomeInsetsPolicy.navigationChromeInset(
          this.resolveShell(),
          this.appUIState.safeBottom
        )
      )
    }
    .width('100%')
    .height('100%')
  }
}
