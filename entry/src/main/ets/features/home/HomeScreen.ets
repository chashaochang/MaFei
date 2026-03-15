import { AppUIState } from "../../entity/AppUIState";
import { HomeTab } from "./hometab/HomeTab";
import { MediaTab } from "./mediatab/MediaTab";
import { MineTab } from "./minetab/MineTab";
import { AppStorageV2 } from "@kit.ArkUI";
import { BreakpointSystem, BreakpointTypeEnum } from "../../utils/BreakpointSystem";
import {
  hdsEffect
} from "@kit.UIDesignKit";
import { CustomTab } from "../../component/tabbar/CustomTab";
import { HomeScreenUIState } from "./HomeScreenUIState";
import { HomeScreenViewModel } from "./HomeScreenViewModel";
import { getUIAbilityContext } from "../../common/CommonFunc";
import { HomeBackPressedEvent } from "../../events/Events";
import { process } from "@kit.ArkTS";
import { FavoriteListPage } from "../favorite/FavoriteListPage";
import { VideoListPage } from "../videolist/VideoListPage";
import { LoadingView } from "../../ui/components/LoadingView";
import { MediaItem } from "./hometab/HomeUIState";
import { HORIZONTAL_CARD_SPACE } from "../../common/OneMoreConst";

@ComponentV2
export struct HomeScreen {
  private breakpointSystem: BreakpointSystem = new BreakpointSystem();
  @Local appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;
  private tabsController = new TabsController()
  private padTabsController = new TabsController()
  @Local ui: HomeScreenUIState = new HomeScreenUIState(this.getUIContext())
  @Local vm: HomeScreenViewModel = new HomeScreenViewModel(this.ui)
  private lastTime: number = 0;

  @Monitor('appUIState.currentBreakpoint')
  onBreakpointChange() {
    if (this.appUIState.currentBreakpoint.includes('l')) {
      this.appUIState.illuminatedType = hdsEffect.PointLightIlluminatedType.DEFAULT_FEATHERING_BORDER
    } else {
      this.appUIState.illuminatedType = hdsEffect.PointLightIlluminatedType.NONE
    }
    this.appUIState.isLargeScreen = this.appUIState.currentBreakpoint.includes('l')
  }

  aboutToAppear() {
    this.breakpointSystem.register(this.getUIContext());
    this.appUIState.padHomeNavStack.pushPath({
      name: "HomeTab"
    })
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
  }

  aboutToDisappear() {
    this.breakpointSystem.unregister();
    getUIAbilityContext().eventHub.off(HomeBackPressedEvent)
  }

  build() {
    Row() {
      if (this.appUIState.currentBreakpoint != BreakpointTypeEnum.XS &&
        this.appUIState.currentBreakpoint != BreakpointTypeEnum.SM &&
        this.appUIState.currentBreakpoint != BreakpointTypeEnum.MD) { // 大于等于LG屏幕尺寸时显示左侧间距栏
        Stack() {
          Column() {
            Row() {
              Image($r('app.media.app_icon')).height(48)
              Text($r('app.string.app_name'))
                .fontColor($r('app.color.text_primary'))
                .fontSize(20)
                .fontWeight(FontWeight.Medium)
                .fontStyle(FontStyle.Italic)
            }.width('100%')
            .padding({
              left: 12,
              bottom: 16
            })
            .justifyContent(FlexAlign.Start)

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
                    .backgroundColor(this.ui.menuSelectedIndex == index ? $r('app.color.component_bg') :
                      Color.Transparent)
                    .width('100%')
                    .padding(10)
                    .borderRadius(8)
                    .onClick(() => {
                      if (this.ui.menuSelectedIndex === index) {
                        return
                      }
                      this.ui.menuSelectedIndex = index
                      this.padTabsController.changeIndex(index)
                      // 点击菜单项时，如果是大屏幕则直接切换Tab，保持原有逻辑不变；如果是小屏幕则先切换到对应的Tab再更新菜单选中状态
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
          .backgroundColor($r('app.color.bg_1'))
          .backgroundBlurStyle(BlurStyle.COMPONENT_THIN)
          .borderColor($r('app.color.border'))
          .borderWidth(1)
          .borderRadius(20)
          .visualEffect(new hdsEffect.HdsEffectBuilder().pointLight({
            illuminatedType: hdsEffect.PointLightIlluminatedType.BORDER_CONTENT,
          }).buildEffect())
        }.padding({
          top: this.appUIState.safeTop + 12,
          bottom: this.appUIState.safeBottom + 12,
        })

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
                }.tabBar(this.tabBuilder('首页', index, $r('app.media.ic_home_sel'), $r('app.media.ic_home_unsel')))
              })
            }
            .width('100%')
            .layoutWeight(1)
            .barHeight(0)
            .barWidth(0)
            .vertical(true)
            .scrollable(false)
          } else {
            LoadingView()
          }
        }.layoutWeight(1)
      } else {
        Stack() {
          Tabs({
            barPosition: BarPosition.End,
            controller: this.tabsController
          }) {
            TabContent() {
              HomeTab()
            }
            .tabBar(this.tabBuilder('首页', 0, $r('app.media.ic_home_sel'), $r('app.media.ic_home_unsel')))

            TabContent() {
              FavoriteListPage()
            }
            .tabBar(this.tabBuilder('收藏', 0, $r('app.media.ic_favorite_sel'), $r('app.media.ic_favorite_unsel')))

            TabContent() {
              MediaTab()
            }.tabBar(this.tabBuilder('媒体库', 1, $r('app.media.ic_media_sel'), $r('app.media.ic_media_unsel')))
            .padding({
              left: this.appUIState.currentBreakpoint.includes('l') ? 97 : 0
            })

            TabContent() {
              MineTab()
            }
            .tabBar(this.tabBuilder('我', 2, $r('app.media.ic_mine_sel'), $r('app.media.ic_mine_unsel')))
            .padding({
              left: this.appUIState.currentBreakpoint.includes('l') ? 97 : 0
            })
          }
          .barHeight(0)
          // .barHeight(this.appUIState.currentBreakpoint.includes('l') ? '100%' : 0)
          // .barWidth(this.appUIState.currentBreakpoint.includes('l') ? 0 : '')
          .barMode(this.appUIState.currentBreakpoint.includes('l') ? BarMode.Scrollable : BarMode.Fixed,
            { nonScrollableLayoutStyle: LayoutStyle.ALWAYS_CENTER })
          .vertical(this.appUIState.currentBreakpoint.includes('l'))
          // .barBackgroundColor(Color.Transparent)
          // .barOverlap(true)
          // .onChange((index: number) => {
          //   this.selectedIndex = index
          // })
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
            onItemClick: (index: number) => {
              this.ui.selectedIndex = index
              this.tabsController.changeIndex(index)
            }
          }).margin({
            bottom: this.appUIState.safeBottom,
          })
            .visibility(this.appUIState.isLargeScreen ? Visibility.None : Visibility.Visible)
        }.alignContent(Alignment.Bottom)
      }
    }
    .padding({
      left: this.appUIState.currentBreakpoint.includes('l') ? 12 : 0
    })
  }

  @Builder
  tabBuilder(_title: string, _targetIndex: number, _selectedImg: Resource, _normalImg: Resource) {
    Stack()
  }
}

export enum PadMenuType {
  TITLE,
  ITEM,
}

export class PadMenuItem {
  type: PadMenuType
  title: string
  selectedIcon: ResourceStr
  unselectedIcon: ResourceStr
  badge: number
  extra?: MediaItem

  constructor(type: PadMenuType, title: string, selectedIcon: ResourceStr = '', unselectedIcon: ResourceStr = '',
    badge: number = 0) {
    this.type = type
    this.title = title
    this.selectedIcon = selectedIcon
    this.unselectedIcon = unselectedIcon
    this.badge = badge
  }
}