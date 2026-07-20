import { AppUIState } from "../../entity/AppUIState";
import { HomeTab } from "./hometab/HomeTab";
import { ChasingTab } from "./chasing/ChasingTab";
import { MediaTab } from "./mediatab/MediaTab";
import { MineTab } from "./minetab/MineTab";
import { AppStorageV2 } from "@kit.ArkUI";
import { BreakpointSystem, BreakpointTypeEnum } from "../../utils/BreakpointSystem";
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
import { ThemePromptService } from "../../theme/ThemePromptService";
import { AppThemeTokens } from "../../theme/AppThemeTokens";
import { HdsUiCapability } from "../../theme/HdsUiCapability";

@ComponentV2
export struct HomeScreen {
  private breakpointSystem: BreakpointSystem = new BreakpointSystem();
  @Local appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;
  private tabsController: HdsTabsController = new HdsTabsController()
  private padTabsController = new TabsController()
  private nativeTabsModifier: HdsTabsModifier = new HdsTabsModifier()
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

  private useNativeTabs(): boolean {
    return AppThemeTokens.isNative(this.appUIState.themeStyle) && HdsUiCapability.supportsSystemMaterial()
  }

  private updateIlluminatedType(): void {
    if (!this.appUIState.currentBreakpoint.includes('s')) {
      this.appUIState.illuminatedType = hdsEffect.PointLightIlluminatedType.DEFAULT_FEATHERING_BORDER
      return
    }
    this.appUIState.illuminatedType = hdsEffect.PointLightIlluminatedType.NONE
  }

  private configureNativeTabs(): void {
    this.nativeTabsModifier = new HdsTabsModifier()
    this.nativeTabsModifier
      .vertical(false)
      .barPosition(BarPosition.End)
      .barHeight(UIConstants.BOTTOM_BAR_HEIGHT + this.appUIState.safeBottom)
      .barFloatingStyle({
        adaptToHandedness: true,
        barBottomMargin: this.appUIState.safeBottom,
        systemMaterialEffect: {
          materialType: hdsMaterial.MaterialType.IMMERSIVE,
          materialLevel: hdsMaterial.MaterialLevel.ADAPTIVE,
        },
      })
  }

  aboutToAppear() {
    this.breakpointSystem.register(this.getUIContext());
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
    this.breakpointSystem.unregister();
    getUIAbilityContext().eventHub.off(HomeBackPressedEvent)
  }

  private showThemePromptIfNeeded(): void {
    if (this.themePromptChecked || !ThemePromptService.shouldShow()) {
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
        if (!this.themePromptCompleted) {
          this.saveThemePrompt(ThemeStyle.Feiniu, false)
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

  private saveThemePrompt(style: ThemeStyle, closeDialog: boolean = true): void {
    try {
      ThemePromptService.complete(style)
      this.appUIState.themeStyle = style
      this.themePromptCompleted = true
      if (closeDialog && this.themeDialogId >= 0) {
        this.getUIContext().getPromptAction().closeCustomDialog(this.themeDialogId)
        this.themeDialogId = -1
      }
    } catch (error) {
      this.getUIContext().getPromptAction().showToast({
        message: $r('app.string.theme_save_failed')
      })
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
    if (this.ui.menuSelectedIndex === 0) {
      return 0
    }
    if (this.ui.menuSelectedIndex === 1) {
      return 1
    }
    if (this.ui.menuSelectedIndex === 2) {
      return 2
    }
    return 3
  }

  private getThirdItemDatasourceIndex(): number {
    const ds = this.vm.datasource
    if (this.ui.menuSelectedIndex === 0 || this.ui.menuSelectedIndex === 1 || this.ui.menuSelectedIndex === 2) {
      const lastMedia = this.ui.lastSelectedMediaIndex
      if (lastMedia >= 0 && lastMedia < ds.totalCount() && ds.getData(lastMedia).type === PadMenuType.ITEM &&
        ds.getData(lastMedia).extra) {
        return lastMedia
      }
      return ds.getFirstMediaItemIndex()
    }
    return this.ui.menuSelectedIndex
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
                Image(this.ui.menuSelectedIndex == index ? item.selectedIcon : item.unselectedIcon)
                  .fillColor(this.ui.menuSelectedIndex == index ? $r('app.color.color_main') :
                    $r('app.color.text_primary'))
                  .width(20)
                  .height(20)
                Text(item.title)
                  .fontColor(this.ui.menuSelectedIndex == index ? $r('app.color.color_main') :
                    $r('app.color.text_primary'))
                  .fontSize(14)
                Blank().layoutWeight(1)
                Text(item.badge > 0 ? item.badge > 99 ? '99+' : item.badge.toString() : '')
                  .fontColor(this.ui.menuSelectedIndex == index ? $r('app.color.color_main') :
                    $r('app.color.text_primary'))
                  .fontSize(14)
              }
              .backgroundBlurStyle(this.ui.menuSelectedIndex == index ? BlurStyle.Thin : BlurStyle.NONE)
              .backgroundColor((this.ui.menuSelectedIndex == index &&
              this.vm.appUIState.currentBreakpoint.includes('l')) ? $r('app.color.menu_item_bg') : Color.Transparent)
              .width('100%')
              .padding(10)
              .borderRadius(8)
              .onClick(() => {
                if (this.ui.menuSelectedIndex === index) {
                  return
                }
                this.ui.menuSelectedIndex = index
                if (item.extra) {
                  this.ui.lastSelectedMediaIndex = index
                }
                this.padTabsController.changeIndex(index)
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
        Image(this.ui.selectedIndex === targetIndex ? selectedImg : normalImg)
          .size({ width: 20, height: 20 })
          .fillColor(this.ui.selectedIndex === targetIndex ? $r('app.color.color_main') :
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
        .fontColor(this.ui.selectedIndex === targetIndex ? $r('app.color.color_main') :
          (this.useNativeTabs() ? $r('sys.color.font_primary') : $r('app.color.text_primary')))
    }
    .height('100%')
    .justifyContent(FlexAlign.Center)
    .backgroundColor(this.ui.selectedIndex === targetIndex && !this.useNativeTabs() ?
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

  build() {
    Row() {
      if (this.appUIState.currentBreakpoint != BreakpointTypeEnum.XS &&
        this.appUIState.currentBreakpoint != BreakpointTypeEnum.SM) { // MD or LG+ : pad layout
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
              bottom: this.appUIState.safeBottom + 12,
            })
            .visibility((this.appUIState.currentBreakpoint.includes('l') && this.ui.isLeftSidebarVisible) ?
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
                //大屏顶部导航栏
                Row({ space: 16 }) {
                  Text(this.vm.datasource.getData(this.ui.menuSelectedIndex).title)
                    .fontSize(24)
                    .fontWeight(FontWeight.Bold)
                    .margin({
                      left: HORIZONTAL_CARD_SPACE.getValue(this.vm.appUIState.currentBreakpoint) * 2
                    })
                  Blank().layoutWeight(1)
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

                  // Button() {
                  //   SymbolGlyph($r('sys.symbol.gearshape'))
                  //     .fontSize(24)
                  //     .fontColor([$r('app.color.text_primary')])
                  // }
                  // .width(48)
                  // .height(48)
                  // .borderRadius(22)
                  // .align(Alignment.Center)
                  // .backgroundColor($r('app.color.bg_main'))
                }
                .width('100%')
                .alignItems(VerticalAlign.Center)
                .height(88)
                .padding({
                  top: this.vm.appUIState.safeTop,
                  right: 24
                })

                if (!this.ui.isLoading) {
                  //页面容器
                  Tabs({
                    barPosition: BarPosition.End,
                    controller: this.padTabsController
                  }) {
                    LazyForEach(this.vm.datasource, (item: PadMenuItem, index: number) => {
                      TabContent() {
                        if (item.type == PadMenuType.TITLE) {
                          Stack() {
                            Text(item.title).fontColor($r('app.color.text_1')).fontSize(12)
                          }.height(0)
                        } else {
                          if (item.title === '首页') {
                            HomeTab()
                          } else if (item.title === '追剧') {
                            ChasingTab({
                              fromHome: true,
                            })
                          } else if (item.title === '收藏') {
                            FavoriteListPage({
                              fromHome: true,
                            })
                          } else if (item.extra) {
                            VideoListPage({
                              fromHome: true,
                              mediaItem: item.extra as MediaItem
                            })
                          } else {
                            Stack() {
                              Text(item.title).fontSize(20)
                            }
                          }
                        }
                      }.tabBar(new BottomTabBarStyle(item.unselectedIcon, item.title))
                    })
                  }
                  .width('100%')
                  .layoutWeight(1)
                  .barHeight(0)
                  .barWidth(0)
                  .vertical(false)
                  .scrollable(false)
                  .barPosition(BarPosition.Start)
                } else {
                  LoadingView()
                }
              }

              PadTopTab({
                list: this.getPadTopTabItems(),
                selectIndex: this.getPadTopTabSelectIndex(),
                onItemClick: (index: number) => {
                  const targetIndex = index === 0 ? 0 : index === 1 ? 1 : index === 2 ? 2 : this.getThirdItemDatasourceIndex()
                  if (targetIndex < 0 || targetIndex >= this.vm.datasource.totalCount()) {
                    return
                  }
                  this.ui.menuSelectedIndex = targetIndex
                  if (this.vm.datasource.getData(targetIndex)?.extra) {
                    this.ui.lastSelectedMediaIndex = targetIndex
                  }
                  this.padTabsController.changeIndex(targetIndex)
                },
                onMenuButtonClick: () => {
                  if (this.appUIState.currentBreakpoint.includes('l')) {
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
                .visibility((this.appUIState.currentBreakpoint.includes('l') && this.ui.isLeftSidebarVisible) ?
                  Visibility.None : Visibility.Visible)
            }
            .layoutWeight(1)
            .alignContent(Alignment.Top)
          }
          .width('100%')
          .height('100%')

          if (this.ui.isMenuModalVisible && !this.appUIState.currentBreakpoint.includes('l')) {
            this.menuModalBuilder()
          }
        }
        .width('100%')
        .height('100%')
        .alignContent(Alignment.TopStart)
      } else {
        Stack() {
          HdsTabs({
            barPosition: BarPosition.End,
            controller: this.tabsController
          }) {
            TabContent() {
              HomeTab()
            }
            .tabBar(this.tabBuilder('首页', 0, $r('app.media.ic_home_sel'), $r('app.media.ic_home_unsel')))

            TabContent() {
              ChasingTab()
            }
            .tabBar(this.tabBuilder('追剧', 1, $r('app.media.history'), $r('app.media.history'),
              this.ui.chasingCount))

            TabContent() {
              FavoriteListPage()
            }
            .tabBar(this.tabBuilder('收藏', 2, $r('app.media.ic_favorite_sel'), $r('app.media.ic_favorite_unsel')))

            TabContent() {
              MediaTab()
            }.tabBar(this.tabBuilder('媒体库', 3, $r('app.media.ic_media_sel'), $r('app.media.ic_media_unsel')))
            .padding({
              left: this.appUIState.currentBreakpoint.includes('l') ? 97 : 0
            })

            TabContent() {
              MineTab()
            }
            .tabBar(this.tabBuilder('我', 4, $r('app.media.ic_mine_sel'), $r('app.media.ic_mine_unsel')))
            .padding({
              left: this.appUIState.currentBreakpoint.includes('l') ? 97 : 0
            })
          }
          .barHeight(this.useNativeTabs() ? UIConstants.BOTTOM_BAR_HEIGHT + this.appUIState.safeBottom : 0)
          .barMode(BarMode.Fixed, { nonScrollableLayoutStyle: LayoutStyle.ALWAYS_CENTER })
          .vertical(false)
          .divider({ mode: DividerMode.NONE })
          .barOverlap(true)
          .animationDuration(0)
          .attributeModifier(this.useNativeTabs() ? this.nativeTabsModifier : undefined)
          .onAnimationStart((index: number, targetIndex: number, event: TabsAnimationEvent) => {
            if (index === targetIndex) {
              return
            }
            // selectedIndex控制自定义TabBar内Image和Text颜色切换
            this.ui.selectedIndex = targetIndex
          })
          .scrollable(false)
          .onChange((index: number) => {
            this.ui.selectedIndex = index
          })

          CustomTab({
            selectIndex: this.ui.selectedIndex,
            chasingBadgeCount: this.ui.chasingCount,
            onItemClick: (index: number) => {
              this.ui.selectedIndex = index
              this.tabsController.changeIndex(index)
            }
          }).margin({
            bottom: this.appUIState.safeBottom,
          })
            .visibility(this.appUIState.isBigScreen || this.useNativeTabs() ?
              Visibility.None : Visibility.Visible)
        }.alignContent(Alignment.Bottom)
      }
    }
    .padding({
      left: this.appUIState.currentBreakpoint.includes('l') ? 12 : 0
    })
  }
}
