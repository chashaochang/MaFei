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
      // Register the native mfplayer surface before CPF creates any ArkUIView
      // interop nodes. Compose owns the player chrome; this builder owns only
      // the old HarmonyOS video surface and decoder.
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
          onBackPressed: () => this.controller!.onBackPress()
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
  }

  onPageHide(): void {
    this.controller?.onPageHide();
  }

  onPageShow(): void {
    this.controller?.onPageShow();
  }

  onBackPress(): boolean {
    return this.controller ? this.controller.onBackPress() : false;
  }
}
