import { AbilityConstant, ConfigurationConstant, UIAbility, Want } from '@kit.AbilityKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { AppStorageV2, mediaquery, window } from '@kit.ArkUI';
import { webview } from '@kit.ArkWeb';
import { HMRouterMgr } from '@hadss/hmrouter';
import { DBManager } from '../data/DBManager';
import { AppUIState } from '../entity/AppUIState';
import { OneMoreUtil } from '../utils/OneMoreUtil';
import { PlayOptions } from '../player/interaction/PlayOptions';

export default class PlayerAbility extends UIAbility {
  listener?: mediaquery.MediaQueryListener
  windowStage?: window.WindowStage

  onCreate(want: Want, _launchParam: AbilityConstant.LaunchParam): void {
    this.context.getApplicationContext().setColorMode(ConfigurationConstant.ColorMode.COLOR_MODE_NOT_SET);
    //获取entry传来的播放参数
    let funcAbilityWant = want;
    let playerOptions: PlayOptions = funcAbilityWant?.parameters?.options as PlayOptions
    AppStorage.setOrCreate('playerOptions', playerOptions)
  }

  onNewWant(want: Want, launchParam: AbilityConstant.LaunchParam): void {
    let funcAbilityWant = want;
    let playerOptions: PlayOptions = funcAbilityWant?.parameters?.options as PlayOptions
    AppStorage.setOrCreate('playerOptions', playerOptions)
    const windowStage = this.windowStage
    if (!windowStage) {
      return
    }
    windowStage.loadContent('pages/PcPlayerPage', (err) => {
      if (err.code) {
        hilog.error(0x0000, 'Mafei', 'Failed to load the content. Cause: %{public}s', JSON.stringify(err) ?? '');
        return;
      }
    });
  }

  onDestroy(): void {
  }

  onWindowStageCreate(windowStage: window.WindowStage): void {
    this.windowStage = windowStage
    // Main window is created, set main page for this ability
    AppStorage.setOrCreate('playerWindowStage', windowStage);
    windowStage.loadContent('pages/PcPlayerPage', (err) => {
      if (err.code) {
        hilog.error(0x0000, 'Mafei', 'Failed to load the content. Cause: %{public}s', JSON.stringify(err) ?? '');
        return;
      }
    });

    windowStage.getMainWindow().then((windowClass) => {
      windowClass.setWindowBackgroundColor('#101010')
      windowClass.setWindowSystemBarProperties({
        statusBarColor: '#101010',
        navigationBarColor: '#101010',
        navigationBarContentColor: '#101010',
        isStatusBarLightIcon: true
      })
    })
  }


  onWindowStageDestroy(): void {
    // Main window is destroyed, release UI related resources
  }

  onForeground(): void {
    // Ability has brought to foreground
  }

  onBackground(): void {
    // Ability has back to background
  }
}
