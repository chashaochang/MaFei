import { AbilityConstant, Configuration, ConfigurationConstant, UIAbility, Want } from '@kit.AbilityKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { AppStorageV2, display, mediaquery, window } from '@kit.ArkUI';
import { webview } from '@kit.ArkWeb';
import { HMRouterMgr } from '@hadss/hmrouter';
import { DBManager } from '../data/DBManager';
import { AppUIState } from '../entity/AppUIState';
import { ThemeStyle } from '../entity/ThemeStyle';
import { nativeThemeCapability } from '../theme/NativeThemeCapability';
import { themePromptService } from '../theme/ThemePromptService';
import { OneMoreUtil } from '../utils/OneMoreUtil';
// 弹幕功能暂不上线，先注释初始化依赖，后续恢复时取消注释即可。
// import { GlobalContext } from '@ohos/danmakuflamemaster';
import { ImageKnife } from '@ohos/imageknifepro';

export default class EntryAbility extends UIAbility {
  listener?: mediaquery.MediaQueryListener
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;
  private initializationPromise?: Promise<void>

  onCreate(_want: Want, _launchParam: AbilityConstant.LaunchParam): void {
    this.appUIState.context = this.context
    this.initializeThemeState()
    if(this.context.config.colorMode !== undefined)
      this.appUIState.colorMode = this.context.config.colorMode
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
      this.appUIState.themePromptPending = launchState.shouldShowPrompt
      this.appUIState.themeStyle = launchState.effectiveTheme
    } catch (error) {
      this.appUIState.nativeThemeAvailable = false
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
    initializationPromise.then(() => {
      this.loadWindowStage(windowStage)
    }).catch((error: Error) => {
      hilog.error(0x0000, 'Mafei', 'Failed to create the window stage. Cause: %{public}s', error.message)
    })
  }

  private async initializeApplication(): Promise<void> {
    await DBManager.getInstance().init(this.context)
    // 弹幕功能暂不上线，先注释初始化代码。
    // GlobalContext.getContext().setValue("path", this.context.filesDir);
    // 如果需要用文件缓存，需要提前初始化文件缓存
    await ImageKnife.getInstance().initFileCache(this.context, 1024, 1024 * 1024 * 1024)
  }

  private loadWindowStage(windowStage: window.WindowStage): void {
    // Main window is created, set main page for this ability
    AppStorage.setOrCreate('windowStage', windowStage);
    windowStage.loadContent('pages/Index', (err) => {
      if (err.code) {
        hilog.error(0x0000, 'Mafei', 'Failed to load the content. Cause: %{public}s', JSON.stringify(err) ?? '');
        return;
      }
    });

    windowStage.getMainWindow().then((windowClass) => {
      this.appUIState.windowClass = windowClass
      try {
        windowClass.on('windowSizeChange', () => {
          const properties = windowClass.getWindowProperties();
          this.appUIState.deviceHeight = windowClass.getUIContext().px2vp(properties.windowRect.height);
          this.appUIState.deviceWidth = windowClass.getUIContext().px2vp(properties.windowRect.width);
        })
        windowClass.setWindowLayoutFullScreen(true).catch()
        const avoidAreaType = window.AvoidAreaType.TYPE_SYSTEM; // 系统默认区域，包括状态栏，导航栏
        const avoidAreaTop = windowClass.getWindowAvoidArea(avoidAreaType);
        const topHeight = avoidAreaTop.topRect.height;
        let avoidArea = windowClass.getWindowAvoidArea(window.AvoidAreaType.TYPE_NAVIGATION_INDICATOR)
        this.appUIState.safeTop = windowClass.getUIContext().px2vp(topHeight)
        this.appUIState.safeBottom = windowClass.getUIContext().px2vp(avoidArea.bottomRect.height)
        this.listener = windowClass.getUIContext().getMediaQuery().matchMediaSync('(orientation: landscape)');
        this.listener.on('change', this.onPortrait)
        //一多适配放开了旋转,这里判断手机端锁定
        if (OneMoreUtil.isNormalPhone()) {
          windowClass.setPreferredOrientation(window.Orientation.PORTRAIT).catch()
        }
      } catch (e) {

      }
    }).catch()
  }

  onPortrait = (mediaQueryResult: mediaquery.MediaQueryResult) => { // 当满足媒体查询条件时，触发回调
    if (mediaQueryResult.matches as boolean) { // 若设备为横屏状态，更改相应的页面布局
      this.appUIState.orientation = display.Orientation.LANDSCAPE
    } else {
      this.appUIState.orientation = display.Orientation.PORTRAIT;
    }
  }

  onWindowStageDestroy(): void {
    // Main window is destroyed, release UI related resources
  }

  onForeground(): void {
    // Ability has brought to foreground
    this.context.eventHub.emit('onForeground')
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
