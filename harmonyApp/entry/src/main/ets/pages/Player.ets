import { ArkUIViewController, Compose, forwardHostKeyEvent } from '@cpf-kmp-cmp/compose';
import { hilog } from '@kit.PerformanceAnalysisKit';
import nativeApi from 'libentry.so';
import { registerMaFeiPlayerInteropBuilder } from '../player/PlayerInterop';

@Entry
@Component
struct Player {
  @State controller: ArkUIViewController | undefined = undefined;

  aboutToAppear(): void {
    try {
      registerMaFeiPlayerInteropBuilder();
      this.controller = nativeApi.PlayerArkUIViewController();
      if (!this.controller) {
        throw new Error('Player controller is unavailable');
      }
    } catch (error) {
      const id = nativeApi.GetOhosPlayerWindowRequestId();
      if (id > 0) nativeApi.FailOhosPlayerWindow(id);
      hilog.error(0x4D46, 'MaFeiPlayerWindow', 'Unable to create player view: %{public}s', JSON.stringify(error));
    }
  }

  build() {
    Column() {
      if (this.controller) {
        Compose({
          controller: this.controller,
          libraryName: 'entry',
          onBackPressed: () => false
        })
          .width('100%')
          .height('100%')
          .expandSafeArea([SafeAreaType.SYSTEM, SafeAreaType.CUTOUT], [SafeAreaEdge.TOP, SafeAreaEdge.BOTTOM])
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(Color.Black)
    .focusable(true)
    .onKeyEvent((event) => {
      if (this.controller && forwardHostKeyEvent(this.controller, event)) return true;
      return nativeApi.HandleOhosPlayerKey(event.keyCode, event.type);
    })
  }

  onPageHide(): void {
    this.controller?.onPageHide();
  }

  onPageShow(): void {
    this.controller?.onPageShow();
  }

  onBackPress(): boolean {
    return this.controller?.onBackPress() === true || nativeApi.HandleOhosPlayerWindowBack() ||
      nativeApi.HandleOhosPlayerBack();
  }
}
