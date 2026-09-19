import { AbilityConstant, ConfigurationConstant, UIAbility, Want } from '@kit.AbilityKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { KeyboardAvoidMode, window } from '@kit.ArkUI';
import { display } from '@kit.ArkUI';
import nativeApi from 'libentry.so';

const DOMAIN = 0x4D46;

export default class EntryAbility extends UIAbility {
  private mainWindow?: window.Window;
  private avoidAreaChangeCallback = (_options: window.AvoidAreaOptions): void => {
    if (this.mainWindow) {
      this.publishSafeArea(this.mainWindow);
    }
  };
  private windowSizeChangeCallback = (_size: window.Size): void => {
    if (this.mainWindow) {
      this.publishSafeArea(this.mainWindow);
    }
  };

  private resolveDensity(windowClass: window.Window): number {
    try {
      const info = windowClass.getWindowDensityInfo();
      if (info.customDensity > 0) return info.customDensity;
      if (info.systemDensity > 0) return info.systemDensity;
      if (info.defaultDensity > 0) return info.defaultDensity;
    } catch (_) {
      // Fall through to the display density used by the old ArkUI client.
    }
    try {
      return Math.max(display.getDefaultDisplaySync().densityPixels, 1);
    } catch (_) {
      return 1;
    }
  }

  private pxToVp(windowClass: window.Window, value: number): number {
    try {
      return windowClass.getUIContext().px2vp(value);
    } catch (_) {
      return value / this.resolveDensity(windowClass);
    }
  }

  private publishSafeArea(windowClass: window.Window): void {
    try {
      const properties = windowClass.getWindowProperties();
      const system = windowClass.getWindowAvoidArea(window.AvoidAreaType.TYPE_SYSTEM);
      const navigation = windowClass.getWindowAvoidArea(window.AvoidAreaType.TYPE_NAVIGATION_INDICATOR);
      const cutout = windowClass.getWindowAvoidArea(window.AvoidAreaType.TYPE_CUTOUT);
      const width = properties.windowRect.width;
      const leftPx = Math.max(
        0,
        system.leftRect.left + system.leftRect.width,
        cutout.leftRect.left + cutout.leftRect.width
      );
      const rightPx = Math.max(
        0,
        system.rightRect.width > 0 ? width - system.rightRect.left : 0,
        cutout.rightRect.width > 0 ? width - cutout.rightRect.left : 0
      );
      const topPx = Math.max(0, system.topRect.height, cutout.topRect.height);
      const bottomPx = Math.max(
        0,
        system.bottomRect.height,
        navigation.bottomRect.height,
        cutout.bottomRect.height
      );
      nativeApi.UpdateOhosWindowInsets(
        this.pxToVp(windowClass, leftPx),
        this.pxToVp(windowClass, topPx),
        this.pxToVp(windowClass, rightPx),
        this.pxToVp(windowClass, bottomPx)
      );
    } catch (error) {
      hilog.warn(DOMAIN, 'MaFeiCMP', 'Unable to publish safe area: %{public}s', JSON.stringify(error));
    }
  }

  onCreate(want: Want, launchParam: AbilityConstant.LaunchParam): void {
    try {
      this.context.getApplicationContext().setColorMode(
        ConfigurationConstant.ColorMode.COLOR_MODE_NOT_SET
      );
    } catch (error) {
      hilog.warn(DOMAIN, 'MaFeiCMP', 'Unable to inherit system color mode: %{public}s',
        JSON.stringify(error));
    }
  }

  private loadCmpContent(windowStage: window.WindowStage, mainWindow?: window.Window): void {
    windowStage.loadContent('pages/Index', (error) => {
      if (error.code) {
        hilog.error(DOMAIN, 'MaFeiCMP', 'Failed to load CMP host: %{public}s',
          JSON.stringify(error));
        return;
      }
      try {
        const loadedWindow = mainWindow ?? windowStage.getMainWindowSync();
        // Keep the full-screen Compose canvas stable while the IME animates.
        // The connection screen owns focused-field avoidance inside its
        // scrollable foreground; resizing the host also re-crops the full-screen
        // artwork and makes the whole page visibly jump.
        loadedWindow.getUIContext().setKeyboardAvoidMode(KeyboardAvoidMode.NONE);
      } catch (keyboardError) {
        hilog.warn(DOMAIN, 'MaFeiCMP', 'Unable to configure keyboard avoidance: %{public}s',
          JSON.stringify(keyboardError));
      }
    });
  }

  onWindowStageCreate(windowStage: window.WindowStage): void {
    let mainWindow: window.Window | undefined = undefined;
    try {
      const createdWindow = windowStage.getMainWindowSync();
      mainWindow = createdWindow;
      this.mainWindow = createdWindow;
      createdWindow.on('avoidAreaChange', this.avoidAreaChangeCallback);
      createdWindow.on('windowSizeChange', this.windowSizeChangeCallback);
      createdWindow.setWindowSystemBarProperties({
        statusBarColor: '#00000000',
        navigationBarColor: '#00000000',
        statusBarContentColor: '#E5FFFFFF',
        navigationBarContentColor: '#E5FFFFFF',
        isStatusBarLightIcon: false,
        isNavigationBarLightIcon: false
      }).catch((error: Object) => {
        hilog.warn(DOMAIN, 'MaFeiCMP', 'Unable to make system bars transparent: %{public}s',
          JSON.stringify(error));
      });
      // CPF's XComponent takes its initial bounds when the page is created.
      // Loading first and enabling full screen later leaves the Compose canvas
      // below the status bar for the lifetime of the page.
      createdWindow.setWindowLayoutFullScreen(true).then(() => {
        if (this.mainWindow === createdWindow) this.publishSafeArea(createdWindow);
        this.loadCmpContent(windowStage, createdWindow);
      }).catch((error: Object) => {
        hilog.warn(DOMAIN, 'MaFeiCMP', 'Unable to enable immersive layout: %{public}s',
          JSON.stringify(error));
        this.publishSafeArea(createdWindow);
        this.loadCmpContent(windowStage, createdWindow);
      });
      return;
    } catch (error) {
      hilog.warn(DOMAIN, 'MaFeiCMP', 'Unable to initialize the immersive window: %{public}s',
        JSON.stringify(error));
    }
    this.loadCmpContent(windowStage, mainWindow);
  }

  onWindowStageDestroy(): void {
    const mainWindow = this.mainWindow;
    this.mainWindow = undefined;
    if (!mainWindow) return;
    try {
      mainWindow.off('avoidAreaChange', this.avoidAreaChangeCallback);
      mainWindow.off('windowSizeChange', this.windowSizeChangeCallback);
    } catch (_) {
      // The window is already being destroyed; listener cleanup is best effort.
    }
  }
}
