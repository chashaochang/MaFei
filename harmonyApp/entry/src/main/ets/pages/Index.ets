import { ArkUIViewController, Compose } from '@cpf-kmp-cmp/compose';
import { hilog } from '@kit.PerformanceAnalysisKit';
import nativeApi from 'libentry.so';
import { registerMaFeiPlayerInteropBuilder } from '../player/PlayerInterop';
import {
  captureLegacyAccountSnapshot,
  captureLegacyCmpSettings
} from '../migration/LegacyAccountSnapshotBridge';

const DOMAIN = 0x4D46;

@Entry
@Component
struct Index {
  @State controller: ArkUIViewController | undefined = undefined;

  @State errorMessage: string = 'Compose runtime is starting';

  async aboutToAppear(): Promise<void> {
    if (nativeApi === undefined) {
      this.errorMessage = 'Native Compose module is unavailable';
      hilog.error(DOMAIN, 'MaFeiCMP', 'libentry.so was not loaded');
      return;
    }

    try {
      const context = this.getUIContext().getHostContext();
      if (!context) {
        this.errorMessage = 'Application context is unavailable';
        return;
      }
      const legacySnapshot = await captureLegacyAccountSnapshot(
        context,
        context.applicationInfo.name
      );
      const legacyCmpSettings = captureLegacyCmpSettings(context);
      // Register the native video surface before CPF creates its ArkUIView
      // node. CMP owns the visible player UI and interaction layer.
      registerMaFeiPlayerInteropBuilder();
      this.controller = nativeApi.MainArkUIViewController(
        context.filesDir,
        context.applicationInfo.name,
        legacySnapshot,
        legacyCmpSettings
      );
      if (!this.controller) {
        this.errorMessage = 'Compose controller creation failed';
      }
    } catch (error) {
      this.errorMessage = 'Compose controller creation failed';
      hilog.error(DOMAIN, 'MaFeiCMP', 'Controller error: %{public}s', JSON.stringify(error));
    }
  }

  build() {
    Column() {
      if (this.controller) {
        Compose({
          controller: this.controller,
          libraryName: 'entry',
          // CPF invokes this callback only after Compose has declined the
          // back event. Calling controller.onBackPress() here re-enters the
          // same BackHandler and overflows the JS stack.
          onBackPressed: () => false
        })
          .width('100%')
          .height('100%')
          .expandSafeArea(
            [SafeAreaType.SYSTEM, SafeAreaType.CUTOUT],
            [SafeAreaEdge.TOP, SafeAreaEdge.BOTTOM]
          )
      } else {
        Text(this.errorMessage)
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(Color.Black)
  }

  onPageHide(): void {
    this.controller?.onPageHide();
  }

  onPageShow(): void {
    this.controller?.onPageShow();
  }

  onBackPress(): boolean {
    // Compose owns the visible player's menu/fullscreen/page back priority.
    // Keep the native callback only as a fallback for a missing dispatcher.
    if (this.controller?.onBackPress()) {
      return true;
    }
    if (nativeApi.IsOhosPlayerScreenVisible() && nativeApi.HandleOhosPlayerBack()) {
      return true;
    }
    return false;
  }
}
