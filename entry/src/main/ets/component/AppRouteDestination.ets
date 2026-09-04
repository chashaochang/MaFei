import { HMRouterMgr, NavDestinationHelper } from '@hadss/hmrouter'
import { AppStorageV2, AttributeUpdater, LengthMetrics } from '@kit.ArkUI'
import {
  HdsNavigationMenuItemOptions,
  HdsNavigationTitleBarOptions,
  TitleBarContentOptions,
  ScrollEffectType as HdsScrollEffectType,
  TitleSize,
  hdsMaterial
} from '@kit.UIDesignKit'
import { UIConstants } from '../common/UIConstants'
import { AppUIState } from '../entity/AppUIState'
import { ThemeStyle } from '../entity/ThemeStyle'
import { AppThemeSurfaceResolver } from '../theme/AppThemeSurfaceResolver'
import { HdsUiCapability } from '../theme/HdsUiCapability'
import { HomeContentNavigationController } from '../features/home/HomeContentNavigationController'

export interface AppRouteDestinationOptions {
  title?: ResourceStr
  titleBarVisible?: boolean
  backButtonVisible?: boolean
  contentExtendsUnderTitleBar?: boolean
  heroTitleChrome?: boolean
  titleMaterialFollowsSystem?: boolean
  ownsRootNavigationChrome?: boolean
  menus?: Array<NavigationMenuItem>
  titleBarStackBuilder?: () => void
  scrollControllers?: Array<Scroller>
  beforeBack?: () => boolean
}

class AppLegacyRouteModifier extends AttributeUpdater<NavDestinationAttribute> {
  private readonly controller: AppRouteDestination
  private readonly options: AppRouteDestinationOptions

  constructor(controller: AppRouteDestination, options: AppRouteDestinationOptions) {
    super()
    this.controller = controller
    this.options = options
  }

  initializeModifier(instance: NavDestinationAttribute): void {
    this.controller.helper.modifier.initializeModifier(instance)
    instance.backgroundColor(AppThemeSurfaceResolver.routeBackground(
      this.controller.appUIState.themeStyle,
      HdsUiCapability.supportsNativeTheme()
    ))
    instance.title(this.options.title ?? '', { barStyle: BarStyle.STANDARD })
    instance.menus([])
    instance.hideTitleBar(true)
    instance.hideBackButton(!this.controller.backButtonVisible(this.options))
    instance.onWillAppear(() => this.controller.helper.onWillAppear())
    instance.onAppear(() => this.controller.helper.onAppear())
    instance.onWillShow(() => this.controller.helper.onWillShow())
    instance.onShown(() => this.controller.helper.onShown())
    instance.onWillHide(() => this.controller.helper.onWillHide())
    instance.onHidden(() => this.controller.helper.onHidden())
    instance.onWillDisappear(() => this.controller.helper.onWillDisappear())
    instance.onDisAppear(() => this.controller.helper.onDisAppear())
    instance.onReady((ctx: NavDestinationContext) => this.controller.helper.onReady(ctx))
    instance.onBackPressed(() => this.controller.handleBackPressed(this.options))
    instance.onResult((result: ESObject) => this.controller.helper.onResult(result))
    instance.onActive((reason: NavDestinationActiveReason) => this.controller.helper.onActive(reason))
    instance.onInactive((reason: NavDestinationActiveReason) => this.controller.helper.onInactive(reason))
    instance.onNewParam((param: ESObject) => this.controller.helper.onNewParam(param))
    instance.expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.BOTTOM])
    instance.width('100%')
    instance.height('100%')
  }
}

export class AppRouteDestination {
  readonly appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!
  readonly helper: NavDestinationHelper

  constructor(component: CustomComponent) {
    this.helper = new NavDestinationHelper(component)
  }

  get gestureModifier(): GestureModifier {
    return this.helper.gestureModifier
  }

  usesNativeChrome(): boolean {
    return HdsUiCapability.supportsHdsComponents() &&
      this.appUIState.themeStyle === ThemeStyle.Native && HdsUiCapability.supportsNativeTheme()
  }

  usesHdsDestination(): boolean {
    return HdsUiCapability.supportsHdsComponents()
  }

  titleBarVisible(options: AppRouteDestinationOptions): boolean {
    return options.titleBarVisible ?? true
  }

  backButtonVisible(options: AppRouteDestinationOptions): boolean {
    return options.backButtonVisible ?? true
  }

  scrollControllers(options: AppRouteDestinationOptions): Array<Scroller> {
    return options.scrollControllers ?? []
  }

  rootHomeLibraryPinned(options: AppRouteDestinationOptions): boolean {
    return (options.ownsRootNavigationChrome ?? false) && this.appUIState.rootNavigationHomeLibraryPinned
  }

  nativeContentTopInset(options: AppRouteDestinationOptions): number {
    if ((options.contentExtendsUnderTitleBar ?? false) || !this.titleBarVisible(options) || !this.usesNativeChrome()) {
      return 0
    }
    const safeTop = this.appUIState.homeContentNavigationVisible ? 0 : Math.max(0, this.appUIState.safeTop)
    return safeTop + UIConstants.ACTION_BAR_HEIGHT
  }

  nativeSafeAreaTypes(): Array<LayoutSafeAreaType> {
    return this.usesNativeChrome() ? [LayoutSafeAreaType.SYSTEM] : []
  }

  nativeSafeAreaEdges(): Array<LayoutSafeAreaEdge> {
    return this.usesNativeChrome() ? [LayoutSafeAreaEdge.TOP, LayoutSafeAreaEdge.BOTTOM] : []
  }

  hdsBackgroundColor(): ResourceColor {
    return AppThemeSurfaceResolver.destinationCanvasBackground(
      this.appUIState.themeStyle,
      HdsUiCapability.supportsNativeTheme()
    )
  }

  hdsTitleBarHidden(options: AppRouteDestinationOptions): boolean {
    return !this.titleBarVisible(options) || !this.usesNativeChrome() || this.rootHomeLibraryPinned(options)
  }

  hdsBoundScrollers(options: AppRouteDestinationOptions): Array<Scroller> {
    return this.titleBarVisible(options) && this.usesNativeChrome() ? this.scrollControllers(options) : []
  }

  nativeTitleBarOptions(options: AppRouteDestinationOptions): HdsNavigationTitleBarOptions {
    const content: TitleBarContentOptions = {
      title: {
        mainTitle: this.rootHomeLibraryPinned(options) ? '' : (options.title ?? ''),
        mainTitleSize: TitleSize.TITLE_S
      },
      backIcon: {
        action: (): void => this.handleTitleBarBack(options)
      },
      menu: {
        value: this.nativeMenus(options),
        maxCount: 3
      }
    }
    if (options.titleBarStackBuilder) {
      content.stackBuilder = options.titleBarStackBuilder
    }
    const heroTitleChrome = options.heroTitleChrome ?? false
    const followsSystem = options.titleMaterialFollowsSystem ?? true
    return {
      avoidLayoutSafeArea: true,
      enableComponentSafeArea: false,
      content: content,
      style: {
        scrollEffectOpts: {
          enableScrollEffect: !this.rootHomeLibraryPinned(options),
          scrollEffectType: HdsScrollEffectType.IMMERSIVE_GRADIENT_BLUR,
          blurEffectiveStartOffset: LengthMetrics.vp(1),
          blurEffectiveEndOffset: LengthMetrics.vp(24)
        },
        systemMaterialEffect: {
          materialType: followsSystem ? hdsMaterial.MaterialType.ADAPTIVE : hdsMaterial.MaterialType.IMMERSIVE,
          materialLevel: followsSystem ? hdsMaterial.MaterialLevel.ADAPTIVE : hdsMaterial.MaterialLevel.GENTLE
        },
        originalStyle: {
          backgroundStyle: {
            backgroundColor: Color.Transparent,
            maskExtraHeight: 0,
            blurRadius: 0
          },
          contentStyle: {
            titleStyle: {
              mainTitleColor: heroTitleChrome ? Color.White : $r('app.color.text_primary'),
              subTitleColor: heroTitleChrome ? Color.White : $r('app.color.text_2')
            },
            menuStyle: {
              iconColor: heroTitleChrome ? Color.White : $r('app.color.text_primary'),
              textColor: heroTitleChrome ? Color.White : $r('app.color.color_main')
            },
            backIconStyle: {
              iconColor: heroTitleChrome ? Color.White : $r('app.color.text_primary')
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

  legacyModifier(options: AppRouteDestinationOptions): AttributeModifier<NavDestinationAttribute> {
    return new AppLegacyRouteModifier(this, options)
  }

  handleBackPressed(options: AppRouteDestinationOptions): boolean {
    return (options.beforeBack?.() ?? false) || this.helper.onBackPressed()
  }

  private handleTitleBarBack(options: AppRouteDestinationOptions): void {
    if (options.beforeBack?.()) {
      return
    }
    if (HomeContentNavigationController.popHomeContentIfNeeded()) {
      return
    }
    HMRouterMgr.pop()
  }

  private nativeMenus(options: AppRouteDestinationOptions): Array<HdsNavigationMenuItemOptions> {
    return (options.menus ?? []).map((item: NavigationMenuItem): HdsNavigationMenuItemOptions => {
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
}
