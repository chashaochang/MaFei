import { NavDestinationHelper } from '@hadss/hmrouter'
import { AppStorageV2, LengthMetrics } from '@kit.ArkUI'
import {
  HdsNavDestination,
  HdsNavDestinationTitleMode,
  HdsNavigationMenuItemOptions,
  HdsNavigationTitleBarOptions,
  ScrollEffectType as HdsScrollEffectType,
  TitleSize,
  hdsMaterial
} from '@kit.UIDesignKit'
import { AppUIState } from '../entity/AppUIState'
import { ThemeStyle } from '../entity/ThemeStyle'
import { AppThemeSurfaceResolver } from '../theme/AppThemeSurfaceResolver'
import { HdsUiCapability } from '../theme/HdsUiCapability'
import { UIConstants } from '../common/UIConstants'

@Builder
function EmptyRouteContent() {
}

@Component
export struct AppRouteDestination {
  @Prop title: ResourceStr = ''
  @Prop titleBarVisible: boolean = true
  @Prop backButtonVisible: boolean = true
  @Prop contentExtendsUnderTitleBar: boolean = false
  @Prop heroTitleChrome: boolean = false
  @Prop titleMaterialFollowsSystem: boolean = true
  @Prop ownsRootNavigationChrome: boolean = false
  @Prop menus: Array<NavigationMenuItem> = []
  @Prop scrollControllers: Array<Scroller> = []
  @BuilderParam contentBuilder: () => void = EmptyRouteContent
  @BuilderParam legacyContentBuilder: () => void = EmptyRouteContent
  beforeBack: () => boolean = () => false
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!
  private helper: NavDestinationHelper = new NavDestinationHelper(this)

  private usesNativeChrome(): boolean {
    return this.appUIState.themeStyle === ThemeStyle.Native && HdsUiCapability.supportsNativeTheme()
  }

  private usesHdsDestination(): boolean {
    return HdsUiCapability.supportsNativeTheme()
  }

  private nativeSafeAreaTypes(): Array<LayoutSafeAreaType> {
    return this.usesNativeChrome() ? [LayoutSafeAreaType.SYSTEM] : []
  }

  private nativeSafeAreaEdges(): Array<LayoutSafeAreaEdge> {
    return this.usesNativeChrome() ? [LayoutSafeAreaEdge.TOP, LayoutSafeAreaEdge.BOTTOM] : []
  }

  private nativeContentTopInset(): number {
    if (this.contentExtendsUnderTitleBar || !this.titleBarVisible || !this.usesNativeChrome()) {
      return 0
    }
    return Math.max(0, this.appUIState.safeTop) + UIConstants.ACTION_BAR_HEIGHT
  }

  private nativeMenus(): Array<HdsNavigationMenuItemOptions> {
    return this.menus.map((item: NavigationMenuItem): HdsNavigationMenuItemOptions => {
      return {
        content: {
          icon: item.icon,
          label: item.value,
          isEnabled: item.isEnabled ?? true,
          action: item.action
        }
      }
    })
  }

  private rootHomeLibraryPinned(): boolean {
    return this.ownsRootNavigationChrome && this.appUIState.rootNavigationHomeLibraryPinned
  }

  private nativeMainTitle(): ResourceStr {
    return this.rootHomeLibraryPinned() ? '' : this.title
  }

  private nativeTitleBarOptions(): HdsNavigationTitleBarOptions {
    return {
      avoidLayoutSafeArea: true,
      enableComponentSafeArea: false,
      content: {
        title: {
          mainTitle: this.nativeMainTitle(),
          mainTitleSize: TitleSize.TITLE_S
        },
        menu: {
          value: this.nativeMenus(),
          maxCount: 3
        }
      },
      style: {
        scrollEffectOpts: {
          enableScrollEffect: !this.rootHomeLibraryPinned(),
          scrollEffectType: HdsScrollEffectType.IMMERSIVE_GRADIENT_BLUR,
          blurEffectiveStartOffset: LengthMetrics.vp(1),
          blurEffectiveEndOffset: LengthMetrics.vp(24)
        },
        systemMaterialEffect: {
          materialType: this.titleMaterialFollowsSystem ?
            hdsMaterial.MaterialType.ADAPTIVE : hdsMaterial.MaterialType.IMMERSIVE,
          materialLevel: this.titleMaterialFollowsSystem ?
            hdsMaterial.MaterialLevel.ADAPTIVE : hdsMaterial.MaterialLevel.GENTLE
        },
        originalStyle: {
          backgroundStyle: {
            backgroundColor: Color.Transparent,
            maskExtraHeight: 0,
            blurRadius: 0
          },
          contentStyle: {
            titleStyle: {
              mainTitleColor: this.heroTitleChrome ? Color.White : $r('app.color.text_primary'),
              subTitleColor: this.heroTitleChrome ? Color.White : $r('app.color.text_2')
            },
            menuStyle: {
              iconColor: this.heroTitleChrome ? Color.White : $r('app.color.text_primary'),
              textColor: this.heroTitleChrome ? Color.White : $r('app.color.color_main')
            },
            backIconStyle: {
              iconColor: this.heroTitleChrome ? Color.White : $r('app.color.text_primary')
            }
          }
        },
        scrollEffectStyle: {
          backgroundStyle: {
            backgroundColor: Color.Transparent,
            maskExtraHeight: 32,
            blurRadius: 24
          },
          contentStyle: {
            titleStyle: {
              mainTitleColor: $r('app.color.text_primary'),
              subTitleColor: $r('app.color.text_2')
            },
            menuStyle: {
              iconColor: $r('app.color.text_primary'),
              textColor: $r('app.color.color_main')
            },
            backIconStyle: {
              iconColor: $r('app.color.text_primary')
            }
          }
        }
      }
    }
  }

  private handleBackPressed(): boolean {
    return this.beforeBack() || this.helper.onBackPressed()
  }

  @Builder
  private hdsContent() {
    Column() {
      Blank().height(this.nativeContentTopInset())
      Stack() {
        if (this.usesNativeChrome()) {
          this.contentBuilder()
        } else {
          this.legacyContentBuilder()
        }
      }
      .width('100%')
      .layoutWeight(1)
    }
    .width('100%')
    .height('100%')
  }

  @Builder
  private hdsDestination() {
    HdsNavDestination() {
      this.hdsContent()
    }
    .backgroundColor(AppThemeSurfaceResolver.destinationCanvasBackground(
      this.appUIState.themeStyle,
      HdsUiCapability.supportsNativeTheme()
    ))
    .titleMode(HdsNavDestinationTitleMode.MINI)
    .titleBar(this.nativeTitleBarOptions())
    .hideTitleBar(!this.titleBarVisible || !this.usesNativeChrome() ||
      this.rootHomeLibraryPinned())
    .hideBackButton(!this.backButtonVisible)
    .hideToolBar(true)
    .bindToScrollable(this.titleBarVisible && this.usesNativeChrome() ? this.scrollControllers : [])
    .onWillAppear(() => {
      this.helper.onWillAppear()
    })
    .onAppear(() => {
      this.helper.onAppear()
    })
    .onWillShow(() => {
      this.helper.onWillShow()
    })
    .onShown(() => {
      this.helper.onShown()
    })
    .onWillHide(() => {
      this.helper.onWillHide()
    })
    .onHidden(() => {
      this.helper.onHidden()
    })
    .onWillDisappear(() => {
      this.helper.onWillDisappear()
    })
    .onDisAppear(() => {
      this.helper.onDisAppear()
    })
    .onReady((ctx: NavDestinationContext) => {
      this.helper.onReady(ctx)
    })
    .onBackPressed(() => this.handleBackPressed())
    .onResult((result: ESObject) => {
      this.helper.onResult(result)
    })
    .onActive((activeReason: NavDestinationActiveReason) => {
      this.helper.onActive(activeReason)
    })
    .onInactive((inactiveReason: NavDestinationActiveReason) => {
      this.helper.onInactive(inactiveReason)
    })
    .ignoreLayoutSafeArea(
      this.nativeSafeAreaTypes(),
      this.nativeSafeAreaEdges()
    )
    .width('100%')
    .height('100%')
  }

  @Builder
  private legacyDestination() {
    NavDestination() {
      this.legacyContentBuilder()
    }
    .backgroundColor(AppThemeSurfaceResolver.routeBackground(
      this.appUIState.themeStyle,
      HdsUiCapability.supportsNativeTheme()
    ))
    .title(this.title, { barStyle: BarStyle.STANDARD })
    .menus([])
    .hideTitleBar(true)
    .hideBackButton(!this.backButtonVisible)
    .attributeModifier(this.helper.modifier)
    .gestureModifier(this.helper.gestureModifier)
    .onWillAppear(() => {
      this.helper.onWillAppear()
    })
    .onAppear(() => {
      this.helper.onAppear()
    })
    .onWillShow(() => {
      this.helper.onWillShow()
    })
    .onShown(() => {
      this.helper.onShown()
    })
    .onWillHide(() => {
      this.helper.onWillHide()
    })
    .onHidden(() => {
      this.helper.onHidden()
    })
    .onWillDisappear(() => {
      this.helper.onWillDisappear()
    })
    .onDisAppear(() => {
      this.helper.onDisAppear()
    })
    .onReady((ctx: NavDestinationContext) => {
      this.helper.onReady(ctx)
    })
    .onBackPressed(() => this.handleBackPressed())
    .onResult((result: ESObject) => {
      this.helper.onResult(result)
    })
    .onActive((activeReason: NavDestinationActiveReason) => {
      this.helper.onActive(activeReason)
    })
    .onInactive((inactiveReason: NavDestinationActiveReason) => {
      this.helper.onInactive(inactiveReason)
    })
    .onNewParam((newParam: ESObject) => {
      this.helper.onNewParam(newParam)
    })
    .expandSafeArea(
      [SafeAreaType.SYSTEM],
      [SafeAreaEdge.BOTTOM]
    )
    .width('100%')
    .height('100%')
  }

  build() {
    if (this.usesHdsDestination()) {
      this.hdsDestination()
    } else {
      this.legacyDestination()
    }
  }
}
