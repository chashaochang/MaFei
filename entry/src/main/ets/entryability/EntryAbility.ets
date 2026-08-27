import { AbilityConstant, Configuration, ConfigurationConstant, UIAbility, Want } from '@kit.AbilityKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { AppStorageV2, display, KeyboardAvoidMode, mediaquery, window } from '@kit.ArkUI';
import { webview } from '@kit.ArkWeb';
import { HMRouterMgr } from '@hadss/hmrouter';
import { AppPreference } from '../data/AppPreference';
import { DBManager } from '../data/DBManager';
import { AppUIState } from '../entity/AppUIState';
import { MediaProviderComposition } from '../features/setting/account/MediaProviderComposition';
import { ThemeStyle } from '../entity/ThemeStyle';
import { nativeThemeCapability } from '../theme/NativeThemeCapability';
import { themePromptService } from '../theme/ThemePromptService';
import { BreakpointPolicy, BreakpointTypeEnum } from '../utils/BreakpointPolicy';
import { OneMoreUtil } from '../utils/OneMoreUtil';
// 弹幕功能暂不上线，先注释初始化依赖，后续恢复时取消注释即可。
// import { GlobalContext } from '@ohos/danmakuflamemaster';
import { ImageKnife } from '@ohos/imageknifepro';

const IMAGE_LOAD_MAX_CONCURRENCY: number = 9

export default class EntryAbility extends UIAbility {
  listener?: mediaquery.MediaQueryListener
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;
  private initializationPromise?: Promise<void>
  private mainWindow?: window.Window
  private contentLoadStarted: boolean = false
  private systemDensityChangeListenerRegistered: boolean = false
  private windowSizeChangeCallback = (_size: window.Size): void => {
    if (this.mainWindow) {
      this.refreshWindowMetrics(this.mainWindow)
    }
  }
  private avoidAreaChangeCallback = (options: window.AvoidAreaOptions): void => {
    if (!this.mainWindow) {
      return
    }
    if (options.type === window.AvoidAreaType.TYPE_SYSTEM ||
      options.type === window.AvoidAreaType.TYPE_NAVIGATION_INDICATOR) {
      this.refreshWindowMetrics(this.mainWindow)
    } else if (options.type === window.AvoidAreaType.TYPE_CUTOUT) {
      this.refreshCutoutMetrics(this.mainWindow, options.area)
    }
  }
  private systemDensityChangeCallback = (_density: number): void => {
    if (this.mainWindow) {
      this.refreshWindowMetrics(this.mainWindow)
    }
  }

  onCreate(_want: Want, _launchParam: AbilityConstant.LaunchParam): void {
    AppPreference.initialize(this.context.getApplicationContext())
    this.appUIState.context = this.context
    this.initializeThemeState()
    if (this.context.config.colorMode !== undefined) {
      this.appUIState.colorMode = this.context.config.colorMode
    }
    //预初始化
    webview.WebviewController.initializeWebEngine()
    HMRouterMgr.init({
      context: this.context
    })
    const initializationPromise = this.initializeApplication().catch((error: Error) => {
      hilog.error(0x0000, 'Mafei',
        'Application initialization failed; loading content with degraded services. Cause: %{public}s', error.message)
    })
    this.initializationPromise = initializationPromise
  }

  onDestroy(): void {
  }

  private initializeThemeState(): void {
    try {
      const capability = nativeThemeCapability.initialize()
      const launchState = themePromptService.resolveLaunch(capability)
      this.appUIState.nativeThemeAvailable = launchState.nativeThemeAvailable
      this.appUIState.systemMaterialAvailable = launchState.systemMaterialAvailable
      this.appUIState.themePromptPending = launchState.shouldShowPrompt
      this.appUIState.themeStyle = launchState.effectiveTheme
    } catch (error) {
      this.appUIState.nativeThemeAvailable = false
      this.appUIState.systemMaterialAvailable = false
      this.appUIState.themePromptPending = false
      this.appUIState.themeStyle = ThemeStyle.Feiniu
      hilog.error(0x0000, 'Mafei',
        'Failed to initialize theme state: %{public}s', String(error))
    } finally {
      this.appUIState.themeStateReady = true
    }
  }

  onWindowStageCreate(windowStage: window.WindowStage): void {
    const initializationPromise = this.initializationPromise
    if (!initializationPromise) {
      hilog.error(0x0000, 'Mafei', 'Application initialization was not started.')
      return
    }
    initializationPromise.then(() => this.loadWindowStage(windowStage)).catch((error: Error) => {
      hilog.error(0x0000, 'Mafei', 'Failed to create the window stage. Cause: %{public}s', error.message)
    })
  }

  private async initializeApplication(): Promise<void> {
    await DBManager.getInstance().init(this.context)
    const providerComposition = await MediaProviderComposition.initialize(this.context)
    if (!providerComposition.isFeiniuAvailable()) {
      hilog.error(0x0000, 'Mafei',
        'Feiniu Video provider initialization failed; continuing with Jellyfin. Type: %{public}s',
        providerComposition.feiniuInitializationFailureType())
    }
    // 弹幕功能暂不上线，先注释初始化代码。
    // GlobalContext.getContext().setValue("path", this.context.filesDir);
    // 如果需要用文件缓存，需要提前初始化文件缓存
    const imageKnife = ImageKnife.getInstance()
    await imageKnife.initFileCache(this.context, 1024, 1024 * 1024 * 1024)
    imageKnife.setMaxRequests(IMAGE_LOAD_MAX_CONCURRENCY)
  }

  private resolveWindowDensity(windowClass: window.Window): number {
    try {
      const densityInfo = windowClass.getWindowDensityInfo()
      if (densityInfo.customDensity > 0) {
        return densityInfo.customDensity
      }
      if (densityInfo.systemDensity > 0) {
        return densityInfo.systemDensity
      }
      if (densityInfo.defaultDensity > 0) {
        return densityInfo.defaultDensity
      }
    } catch (error) {
      hilog.warn(0x0000, 'Mafei',
        'Failed to resolve window density; falling back to display density. Cause: %{public}s', String(error))
    }
    try {
      return Math.max(display.getDefaultDisplaySync().densityPixels, 1)
    } catch (error) {
      hilog.warn(0x0000, 'Mafei',
        'Failed to resolve display density; falling back to raw pixels. Cause: %{public}s', String(error))
      return 1
    }
  }

  private refreshWindowMetrics(windowClass: window.Window): void {
    const properties = windowClass.getWindowProperties()
    const systemArea = windowClass.getWindowAvoidArea(window.AvoidAreaType.TYPE_SYSTEM)
    const navigationArea = windowClass.getWindowAvoidArea(
      window.AvoidAreaType.TYPE_NAVIGATION_INDICATOR
    )
    const cutoutArea = windowClass.getWindowAvoidArea(window.AvoidAreaType.TYPE_CUTOUT)
    const density = this.resolveWindowDensity(windowClass)
    const deviceWidth = properties.windowRect.width / density

    this.appUIState.deviceHeight = properties.windowRect.height / density
    this.appUIState.deviceWidth = deviceWidth
    this.appUIState.currentBreakpoint = BreakpointPolicy.fromWidth(deviceWidth)
    this.appUIState.safeTop = Math.max(0, systemArea.topRect.height) / density
    this.appUIState.safeBottom = Math.max(
      0,
      systemArea.bottomRect.height,
      navigationArea.bottomRect.height
    ) / density
    this.refreshCutoutMetrics(windowClass, cutoutArea)
    this.appUIState.orientation = properties.windowRect.width > properties.windowRect.height ?
      display.Orientation.LANDSCAPE : display.Orientation.PORTRAIT
  }

  private refreshCutoutMetrics(windowClass: window.Window, cutoutArea: window.AvoidArea): void {
    const windowRect = windowClass.getWindowProperties().windowRect
    const density = this.resolveWindowDensity(windowClass)
    this.appUIState.safeLeft = cutoutArea.leftRect.width > 0 ?
      Math.max(0, cutoutArea.leftRect.left + cutoutArea.leftRect.width) / density : 0
    this.appUIState.safeRight = cutoutArea.rightRect.width > 0 ?
      Math.max(0, windowRect.width - cutoutArea.rightRect.left) / density : 0
  }

  private resetWindowState(): void {
    this.appUIState.windowClass = undefined
    this.appUIState.deviceWidth = 0
    this.appUIState.deviceHeight = 0
    this.appUIState.safeTop = 0
    this.appUIState.safeBottom = 0
    this.appUIState.safeLeft = 0
    this.appUIState.safeRight = 0
    this.appUIState.currentBreakpoint = BreakpointTypeEnum.MD
    this.appUIState.orientation = display.Orientation.PORTRAIT
  }

  private clearMainWindowBindings(): void {
    const listener = this.listener
    const mainWindow = this.mainWindow
    const systemDensityChangeListenerRegistered = this.systemDensityChangeListenerRegistered
    this.listener = undefined
    this.mainWindow = undefined
    this.systemDensityChangeListenerRegistered = false

    if (listener) {
      try {
        listener.off('change', this.onPortrait)
      } catch (error) {
        hilog.warn(0x0000, 'Mafei',
          'Failed to remove orientation listener: %{public}s', String(error))
      }
    }
    if (mainWindow) {
      try {
        mainWindow.off('windowSizeChange', this.windowSizeChangeCallback)
      } catch (error) {
        hilog.warn(0x0000, 'Mafei',
          'Failed to remove window-size listener: %{public}s', String(error))
      }
      try {
        mainWindow.off('avoidAreaChange', this.avoidAreaChangeCallback)
      } catch (error) {
        hilog.warn(0x0000, 'Mafei',
          'Failed to remove avoid-area listener: %{public}s', String(error))
      }
      if (systemDensityChangeListenerRegistered) {
        try {
          mainWindow.off('systemDensityChange', this.systemDensityChangeCallback)
        } catch (error) {
          hilog.warn(0x0000, 'Mafei',
            'Failed to remove system-density listener: %{public}s', String(error))
        }
      }
    }
    this.resetWindowState()
  }

  private async initializeMainWindow(windowClass: window.Window): Promise<boolean> {
    this.clearMainWindowBindings()
    try {
      this.mainWindow = windowClass
      try {
        await windowClass.setWindowLayoutFullScreen(true)
      } catch (error) {
        hilog.warn(0x0000, 'Mafei',
          'Failed to enable full-screen layout before content load; using default safe-area layout. Cause: %{public}s',
          String(error))
      }
      if (this.mainWindow !== windowClass) {
        return false
      }
      this.refreshWindowMetrics(windowClass)
      windowClass.on('windowSizeChange', this.windowSizeChangeCallback)
      windowClass.on('avoidAreaChange', this.avoidAreaChangeCallback)
      try {
        windowClass.on('systemDensityChange', this.systemDensityChangeCallback)
        this.systemDensityChangeListenerRegistered = true
      } catch (error) {
        hilog.warn(0x0000, 'Mafei',
          'System-density listener is unavailable; continuing without it. Cause: %{public}s', String(error))
      }

      if (OneMoreUtil.isNormalPhone()) {
        windowClass.setPreferredOrientation(window.Orientation.PORTRAIT)
          .catch(() => undefined)
      }
      this.appUIState.windowClass = windowClass
      return true
    } catch (error) {
      this.clearMainWindowBindings()
      hilog.error(0x0000, 'Mafei',
        'Failed to initialize the main window: %{public}s', String(error))
      return false
    }
  }

  private initializeContentBindings(windowClass: window.Window): void {
    if (this.mainWindow !== windowClass) {
      return
    }
    let listener: mediaquery.MediaQueryListener | undefined
    try {
      listener = windowClass.getUIContext()
        .getMediaQuery()
        .matchMediaSync('(orientation: landscape)')
      listener.on('change', this.onPortrait)
      this.listener = listener
      this.appUIState.orientation = listener.matches ?
        display.Orientation.LANDSCAPE : display.Orientation.PORTRAIT
    } catch (error) {
      if (listener) {
        try {
          listener.off('change', this.onPortrait)
        } catch (cleanupError) {
          hilog.warn(0x0000, 'Mafei',
            'Failed to remove partial orientation listener: %{public}s', String(cleanupError))
        }
      }
      hilog.error(0x0000, 'Mafei',
        'Failed to initialize window content bindings: %{public}s', String(error))
    }
  }

  private loadContent(windowStage: window.WindowStage, windowClass: window.Window): void {
    if (this.contentLoadStarted) {
      hilog.warn(0x0000, 'Mafei', 'Ignoring duplicate content load request.')
      return
    }
    this.contentLoadStarted = true
    try {
      AppStorage.setOrCreate('windowStage', windowStage)
      windowStage.loadContent('pages/Index', (error) => {
        if (error.code) {
          hilog.error(0x0000, 'Mafei',
            'Failed to load the content. Cause: %{public}s',
            JSON.stringify(error) ?? '')
          return
        }
        this.refreshWindowMetrics(windowClass)
        windowClass.getUIContext().setKeyboardAvoidMode(KeyboardAvoidMode.RESIZE)
        this.initializeContentBindings(windowClass)
      })
    } catch (error) {
      this.contentLoadStarted = false
      throw new Error(String(error))
    }
  }

  private loadMainWindowAsync(windowStage: window.WindowStage): void {
    try {
      windowStage.getMainWindow().then(async (windowClass: window.Window) => {
        if (!await this.initializeMainWindow(windowClass)) {
          return
        }
        this.loadContent(windowStage, windowClass)
      }).catch((error: Error) => {
        hilog.error(0x0000, 'Mafei',
          'Failed to load the asynchronous main window: %{public}s', error.message)
      })
    } catch (error) {
      hilog.error(0x0000, 'Mafei',
        'Failed to request the main window: %{public}s', String(error))
    }
  }

  private async loadWindowStage(windowStage: window.WindowStage): Promise<void> {
    let windowClass: window.Window
    try {
      windowClass = windowStage.getMainWindowSync()
    } catch (error) {
      hilog.warn(0x0000, 'Mafei',
        'Synchronous main-window lookup failed: %{public}s', String(error))
      this.loadMainWindowAsync(windowStage)
      return
    }

    if (!await this.initializeMainWindow(windowClass)) {
      return
    }
    try {
      this.loadContent(windowStage, windowClass)
    } catch (error) {
      hilog.error(0x0000, 'Mafei',
        'Failed to load content on the synchronous main window: %{public}s', String(error))
    }
  }

  onPortrait = (mediaQueryResult: mediaquery.MediaQueryResult) => { // 当满足媒体查询条件时，触发回调
    if (mediaQueryResult.matches as boolean) { // 若设备为横屏状态，更改相应的页面布局
      this.appUIState.orientation = display.Orientation.LANDSCAPE
    } else {
      this.appUIState.orientation = display.Orientation.PORTRAIT;
    }
  }

  onWindowStageDestroy(): void {
    this.contentLoadStarted = false
    this.clearMainWindowBindings()
  }

  onForeground(): void {
    // Ability has brought to foreground
    this.context.eventHub.emit('onForeground')
    this.refreshFeiniuRouteAfterForeground()
  }

  private async refreshFeiniuRouteAfterForeground(): Promise<void> {
    try {
      const composition = await MediaProviderComposition.initialize(this.context)
      await composition.refreshSelectedFeiniuConnection()
    } catch (error) {
      const normalizedError = error as Error
      hilog.warn(0x0000, 'Mafei',
        'Failed to refresh Feiniu route after foregrounding. Cause: %{public}s',
        normalizedError.name || 'Error')
    }
  }

  onBackground(): void {
    // Ability has back to background
    this.context.eventHub.emit('onBackground')
  }

  onConfigurationUpdate(newConfig: Configuration): void {
    if (newConfig.colorMode !== undefined && this.appUIState.colorMode !== newConfig.colorMode) {
      this.appUIState.colorMode = newConfig.colorMode
    }
  }
}
