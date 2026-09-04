import { ArkUIViewController, Compose } from '@cpf-kmp-cmp/compose';
import { hilog } from '@kit.PerformanceAnalysisKit';
import nativeApi from 'libentry.so';

const DOMAIN = 0x4D46;

@Entry
@Component
struct Index {
  private controller: ArkUIViewController | undefined = undefined;

  @State errorMessage: string = 'Compose runtime is starting';

  aboutToAppear(): void {
    if (nativeApi === undefined) {
      this.errorMessage = 'Native Compose module is unavailable';
      hilog.error(DOMAIN, 'MaFeiCMP', 'libentry.so was not loaded');
      return;
    }

    try {
      this.controller = nativeApi.MainArkUIViewController();
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

  onBackPressed(): boolean {
    return this.controller ? this.controller.onBackPress() : false;
  }
}
