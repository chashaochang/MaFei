import { AbilityConstant, ConfigurationConstant, UIAbility, Want } from '@kit.AbilityKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { KeyboardAvoidMode, window } from '@kit.ArkUI';
import { display } from '@kit.ArkUI';
import { deviceInfo } from '@kit.BasicServicesKit';
import nativeApi from 'libentry.so';

const DOMAIN = 0x4D46;

export default class EntryAbility extends UIAbility {
  private mainWindow?: window.Window;
  private orientationPoller?: number;
  private requestedOrientation?: window.Orientation;
  private lastPlayerOrientationRevision: number = -1;
  private lastPlayerScreenVisible: boolean = false;
  private systemBarsHidden: boolean = false;
  private playerDecorHidden: boolean = false;
  private playerWindowMaximizedByUs: boolean = false;
  private playerWindowRecoveryInFlight: boolean = false;
  private lastPlayerFullscreenRequest: boolean = false;
  private avoidAreaChangeCallback = (_options: window.AvoidAreaOptions): void => {
    if (this.mainWindow) {
      this.publishSafeArea(this.mainWindow);
    }
  };
  private windowSizeChangeCallback = (_size: window.Size): void => {
    if (this.mainWindow) {
      this.publishSafeArea(this.mainWindow);
      this.applyOrientationPolicy(this.mainWindow);
    }
  };
  private windowStatusDidChangeCallback = (_status: window.WindowStatusType): void => {
    if (this.mainWindow) {
      this.publishSafeArea(this.mainWindow);
    }
  };
  private windowTitleButtonRectChangeCallback = (_rect: window.TitleButtonRect): void => {
    if (this.mainWindow) {
      this.publishSafeArea(this.mainWindow);
    }
  };

  private isCompactDisplay(windowClass: window.Window): boolean {
    const currentDisplay = display.getDefaultDisplaySync();
    const widthVp = this.pxToVp(windowClass, currentDisplay.width);
    const heightVp = this.pxToVp(windowClass, currentDisplay.height);
    return Math.min(widthVp, heightVp) < 600;
  }

  /**
   * A freeform tablet window has desktop-like window chrome, but it must not
   * be classified as the 2-in-1 desktop shell. It does, however, use the
   * same player fullscreen behavior as a real HarmonyOS PC: the fullscreen
   * action hides the system bars instead of trying to rotate the host window.
   */
  private isFreeformWindow(windowClass: window.Window): boolean {
    try {
      return windowClass.isInFreeWindowMode();
    } catch (_) {
      // Older window managers do not expose freeform mode.
      return false;
    }
  }

  private isWindowedPlayerHost(windowClass: window.Window): boolean {
    if (deviceInfo.deviceType === '2in1') return true;
    return this.isFreeformWindow(windowClass);
  }

  private applyOrientationPolicy(windowClass: window.Window): void {
    const playerVisible = nativeApi.IsOhosPlayerScreenVisible();
    const playerFullscreen = playerVisible && nativeApi.IsOhosPlayerFullscreenRequested();
    const orientationRevision = nativeApi.GetOhosPlayerOrientationRevision();
    const compactDisplay = this.isCompactDisplay(windowClass);
    // Freeform tablet/2-in-1 windows keep their window orientation and use
    // window-level fullscreen. Phones and ordinary tablets use the player
    // button as an orientation toggle; the rest of the app keeps its natural
    // orientation on large screens.
    if (!playerVisible) {
      this.lastPlayerScreenVisible = false;
      this.lastPlayerOrientationRevision = orientationRevision;
    } else if (!this.isWindowedPlayerHost(windowClass) && !compactDisplay) {
      if (!this.lastPlayerScreenVisible) {
        // Preserve the orientation that was already active when playback
        // opened. The first explicit button request is handled below.
        this.lastPlayerScreenVisible = true;
        this.lastPlayerOrientationRevision = orientationRevision;
        return;
      }
      if (this.lastPlayerOrientationRevision === orientationRevision) return;
      this.lastPlayerOrientationRevision = orientationRevision;
    }
    const orientation = this.isWindowedPlayerHost(windowClass) ?
      window.Orientation.UNSPECIFIED : playerVisible ?
      (playerFullscreen ? window.Orientation.AUTO_ROTATION_LANDSCAPE : window.Orientation.PORTRAIT) :
      window.Orientation.UNSPECIFIED;
    if (orientation === this.requestedOrientation) return;
    this.requestedOrientation = orientation;
    windowClass.setPreferredOrientation(orientation).catch((error: Object) => {
      if (this.requestedOrientation === orientation) this.requestedOrientation = undefined;
      hilog.warn(DOMAIN, 'MaFeiCMP', 'Unable to apply orientation policy: %{public}s', JSON.stringify(error));
    });
  }

  /**
   * Only freeform/2-in-1 hosts use window-level fullscreen. Phones and normal
   * tablets keep their bars and let the player button change orientation.
   */
  private applySystemBarPolicy(windowClass: window.Window): void {
    const freeform = this.isFreeformWindow(windowClass);
    const playerVisible = nativeApi.IsOhosPlayerScreenVisible();
    const playerFullscreenRequested = nativeApi.IsOhosPlayerFullscreenRequested();
    const playerExitedFullscreen = this.lastPlayerFullscreenRequest && !playerFullscreenRequested;
    this.lastPlayerFullscreenRequest = playerVisible ? playerFullscreenRequested : false;
    const fullscreen = this.isWindowedPlayerHost(windowClass) &&
      playerVisible && playerFullscreenRequested;
    // A freeform tablet keeps its native titlebar in normal playback, but a
    // player fullscreen request should consume that chrome as well as the
    // status/navigation bars. The 2-in-1 host already hides its decor at
    // startup because its controls live in the shared desktop shell.
    const hideFreeformDecor = freeform && deviceInfo.deviceType !== '2in1' && fullscreen;
    if (hideFreeformDecor !== this.playerDecorHidden) {
      try {
        windowClass.setWindowDecorVisible(!hideFreeformDecor);
        this.playerDecorHidden = hideFreeformDecor;
      } catch (error) {
        hilog.warn(DOMAIN, 'MaFeiCMP', 'Unable to configure player window decor: %{public}s',
          JSON.stringify(error));
      }
    }

    // A real HarmonyOS PC/2-in-1 player fullscreen must expand the host
    // window, not only hide system bars. Remember whether this transition was
    // initiated by the player so leaving fullscreen restores the user's
    // previous floating window instead of unexpectedly unmaximizing a window
    // they had already maximized themselves.
    if (deviceInfo.deviceType === '2in1') {
      try {
        const status = windowClass.getWindowStatus();
        const nativeFullscreen = status === window.WindowStatusType.FULL_SCREEN ||
          status === window.WindowStatusType.MAXIMIZE;
        if (fullscreen && !this.playerWindowMaximizedByUs &&
            status === window.WindowStatusType.FLOATING) {
          this.playerWindowMaximizedByUs = true;
          windowClass.maximize(window.MaximizePresentation.ENTER_IMMERSIVE_DISABLE_TITLE_AND_DOCK_HOVER)
            .catch((error: Object) => {
              this.playerWindowMaximizedByUs = false;
              hilog.warn(DOMAIN, 'MaFeiCMP', 'Unable to maximize player window: %{public}s',
                JSON.stringify(error));
            });
        } else if (!fullscreen && this.playerWindowMaximizedByUs) {
          this.playerWindowMaximizedByUs = false;
          windowClass.recover().catch((error: Object) => {
            hilog.warn(DOMAIN, 'MaFeiCMP', 'Unable to recover player window: %{public}s',
              JSON.stringify(error));
          });
        } else if (!fullscreen && playerVisible && playerExitedFullscreen && nativeFullscreen &&
            !this.playerWindowRecoveryInFlight) {
          // The user may have entered maximize/fullscreen with Harmony's
          // native title button. The player button still means "exit" in that
          // state, so restore the previous floating window instead of merely
          // changing Compose's local state.
          this.playerWindowRecoveryInFlight = true;
          windowClass.recover().then(() => {
            this.playerWindowRecoveryInFlight = false;
          }).catch((error: Object) => {
            this.playerWindowRecoveryInFlight = false;
            hilog.warn(DOMAIN, 'MaFeiCMP', 'Unable to recover native fullscreen player window: %{public}s',
              JSON.stringify(error));
          });
        } else if (!nativeFullscreen) {
          this.playerWindowRecoveryInFlight = false;
        }
      } catch (error) {
        hilog.warn(DOMAIN, 'MaFeiCMP', 'Unable to query player window status: %{public}s',
          JSON.stringify(error));
      }
    }
    if (fullscreen === this.systemBarsHidden) {
      return;
    }
    this.systemBarsHidden = fullscreen;
    windowClass.setWindowSystemBarEnable(fullscreen ? [] : ['status', 'navigation']).catch((error: Object) => {
      if (this.systemBarsHidden === fullscreen) this.systemBarsHidden = !fullscreen;
      hilog.warn(DOMAIN, 'MaFeiCMP', 'Unable to apply system bar policy: %{public}s', JSON.stringify(error));
    });
  }

  private startOrientationPolicy(windowClass: window.Window): void {
    this.applyOrientationPolicy(windowClass);
    this.applySystemBarPolicy(windowClass);
    if (this.orientationPoller !== undefined) clearInterval(this.orientationPoller);
    this.orientationPoller = setInterval(() => {
      if (this.mainWindow === windowClass) {
        this.applyOrientationPolicy(windowClass);
        this.applySystemBarPolicy(windowClass);
      }
    }, 200);
  }

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
      const widthVp = this.pxToVp(windowClass, width);
      // Freeform tablet windows also have a native title bar. Only the
      // HarmonyOS PC/2-in-1 device class uses the custom desktop shell.
      const desktopHost = deviceInfo.deviceType === '2in1';
      const windowedHost = this.isWindowedPlayerHost(windowClass);
      nativeApi.UpdateOhosWindowMetrics(
        widthVp,
        this.pxToVp(windowClass, properties.windowRect.height),
        desktopHost,
        windowedHost
      );

      // The title buttons are native window chrome, not a physical safe area.
      // Publish their actual horizontal and vertical footprint so the player
      // top row can avoid the native title buttons without shifting the rest
      // of the page.
      let windowControlEndInsetVp = 0.0;
      let windowControlTopInsetVp = 0.0;
      let windowFullscreen = false;
      if (windowedHost) {
        try {
          const titleButtons = windowClass.getTitleButtonRect();
          const titleButtonsLeftVp = Math.max(0, titleButtons.right - titleButtons.width);
          windowControlEndInsetVp = Math.max(0, widthVp - titleButtonsLeftVp);
          windowControlTopInsetVp = Math.max(0, titleButtons.top + titleButtons.height);
        } catch (_) {
          // The button rectangle is unavailable while the window is changing
          // state; the next size/status callback will publish it again.
        }
        try {
          const status = windowClass.getWindowStatus();
          windowFullscreen = status === window.WindowStatusType.FULL_SCREEN ||
            status === window.WindowStatusType.MAXIMIZE;
        } catch (_) {
          // Older window managers may not expose status while starting.
        }
      }
      nativeApi.UpdateOhosWindowChrome(
        windowControlEndInsetVp,
        windowControlTopInsetVp,
        windowFullscreen
      );
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
        try {
          // Hide the native titlebar strip but keep the HarmonyOS-owned
          // close/minimize/maximize area. The buttons remain system-owned;
          // Compose must not paint a competing window-control surface.
          if (deviceInfo.deviceType === '2in1') {
            loadedWindow.setWindowDecorHeight(52);
            loadedWindow.setWindowDecorVisible(false);
            loadedWindow.setWindowTitleButtonVisible(true, true, true);
            loadedWindow.setWindowTitleMoveEnabled(true);
            const decorButtonStyle: window.DecorButtonStyle = {
              // MaFei uses a dark visual system. Keep the native controls
              // light instead of letting the system theme turn them black
              // over a dark hero image.
              colorMode: ConfigurationConstant.ColorMode.COLOR_MODE_DARK,
              buttonBackgroundSize: 28,
              spacingBetweenButtons: 12,
              closeButtonRightMargin: 20,
              buttonIconSize: 20,
              buttonBackgroundCornerRadius: 6
            };
            loadedWindow.setDecorButtonStyle(decorButtonStyle);
          }
        } catch (chromeError) {
          // Phones and older tablet window managers do not expose freeform
          // titlebar controls. Their normal edge-to-edge path is unchanged.
          hilog.warn(DOMAIN, 'MaFeiCMP', 'Unable to configure window chrome: %{public}s',
            JSON.stringify(chromeError));
        }
        try {
          // The title-button rectangle/status APIs are valid after content has
          // been loaded. Keep the bridge current while a freeform window is
          // resized, maximized, restored, or its native buttons are laid out.
          loadedWindow.on('windowStatusDidChange', this.windowStatusDidChangeCallback);
          loadedWindow.on('windowTitleButtonRectChange', this.windowTitleButtonRectChangeCallback);
        } catch (chromeEventError) {
          hilog.warn(DOMAIN, 'MaFeiCMP', 'Unable to observe window chrome: %{public}s',
            JSON.stringify(chromeEventError));
        }
        this.publishSafeArea(loadedWindow);
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
      this.startOrientationPolicy(createdWindow);
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
    if (this.orientationPoller !== undefined) {
      clearInterval(this.orientationPoller);
      this.orientationPoller = undefined;
    }
    this.requestedOrientation = undefined;
    this.playerDecorHidden = false;
    this.playerWindowRecoveryInFlight = false;
    this.lastPlayerFullscreenRequest = false;
    const mainWindow = this.mainWindow;
    this.mainWindow = undefined;
    if (!mainWindow) return;
    try {
      mainWindow.off('avoidAreaChange', this.avoidAreaChangeCallback);
      mainWindow.off('windowSizeChange', this.windowSizeChangeCallback);
      mainWindow.off('windowStatusDidChange', this.windowStatusDidChangeCallback);
      mainWindow.off('windowTitleButtonRectChange', this.windowTitleButtonRectChangeCallback);
    } catch (_) {
      // The window is already being destroyed; listener cleanup is best effort.
    }
  }
}
