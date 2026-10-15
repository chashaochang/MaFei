import { webview } from '@kit.ArkWeb';
import { url, util } from '@kit.ArkTS';
import { registerComposeInteropBuilder } from '@cpf-kmp-cmp/compose/src/main/ets/compose/ArkUIView';
import nativeApi from 'libentry.so';

interface WebArgs {
  handle: number;
  revision: number;
  url: string;
  origin: string;
  script: string;
  deviceInformation: string;
}
interface WebEvent { handle: number; action: string; value: string; canBack: boolean; }

class NativeWebInterface {
  private controller: webview.WebviewController;
  private args: WebArgs;
  constructor(controller: webview.WebviewController, args: WebArgs) { this.controller = controller; this.args = args; }
  private valid(): boolean {
    try { return url.URL.parseURL(this.controller.getLastJavascriptProxyCallingFrameUrl()).origin === this.args.origin; }
    catch (_error) { return false; }
  }
  private send(action: string, value: string = ''): void {
    if (!this.valid()) return;
    const event: WebEvent = { handle: this.args.handle, action: action, value: value, canBack: false };
    if (value.length <= 32 * 1024) nativeApi.UpdateOhosWebState(JSON.stringify(event));
  }
  getDeviceInformation(): string { return this.valid() ? this.args.deviceInformation : '{}'; }
  openUrl(value: string): void { this.send('openUrl', value); }
  openClientSettings(): void { this.send('openClientSettings'); }
  openServerSelection(): void { this.send('openServerSelection'); }
  exitApp(): void { this.send('exitApp'); }
  enableFullscreen(): void { this.send('enableFullscreen'); }
  disableFullscreen(): void { this.send('disableFullscreen'); }
  isEnabled(): boolean { return this.valid(); }
  loadPlayer(value: string): void { this.send('loadPlayer', value); }
  pausePlayer(): void { this.send('pausePlayer'); }
  resumePlayer(): void { this.send('resumePlayer'); }
  stopPlayer(): void { this.send('stopPlayer'); }
  destroyPlayer(): void { this.send('destroyPlayer'); }
  seek(value: number): void { if (Number.isFinite(value) && value >= 0) this.send('seek', Math.round(value).toString()); }
  seekMs(value: number): void { if (Number.isFinite(value) && value >= 0) this.send('seekMs', Math.round(value).toString()); }
  setVolume(value: number): void { if (Number.isFinite(value) && value >= 0 && value <= 100) this.send('setVolume', value.toString()); }
}

// CustomBuilderAdapter: cmp-embedded-web-surface
@Builder
function embeddedWebBuilder(_args: Object): void { MaFeiEmbeddedWebSurface(); }

@Component
struct MaFeiEmbeddedWebSurface {
  @Consume('compose_args') @Watch('onCommand') args: WebArgs;
  private controller: webview.WebviewController = new webview.WebviewController();
  private interface: NativeWebInterface | undefined = undefined;
  private revision: number = 0;
  private alive: boolean = true;

  onCommand(): void {
    if (this.args.revision !== this.revision) {
      this.revision = this.args.revision;
      if (this.controller.accessBackward()) this.controller.backward();
    }
  }
  aboutToDisappear(): void {
    this.alive = false;
    this.controller.stop();
    if (this.interface) {
      this.controller.deleteJavaScriptRegister('NativeInterface');
      this.controller.deleteJavaScriptRegister('NativePlayer');
      this.interface = undefined;
    }
  }
  private publishBackState(): void {
    if (!this.alive) return;
    const event: WebEvent = { handle: this.args.handle, action: 'state', value: '', canBack: this.controller.accessBackward() };
    nativeApi.UpdateOhosWebState(JSON.stringify(event));
    const location: WebEvent = { handle: this.args.handle, action: 'location', value: this.controller.getUrl(), canBack: false };
    if (this.accepts(location.value)) nativeApi.UpdateOhosWebState(JSON.stringify(location));
  }
  private accepts(value: string): boolean {
    try { return url.URL.parseURL(value).origin === this.args.origin; } catch (_error) { return false; }
  }
  private intercept(value: string): WebResourceResponse | undefined {
    if (!this.accepts(value) || this.args.script.length === 0) return undefined;
    const parsed = url.URL.parseURL(value);
    const file = parsed.pathname.split('/').pop() || '';
    const main = /.*\/main\.[^/\s]+\.bundle\.js/.test(value) && !value.includes('deferred=true');
    if (!main && !value.includes('/native/')) return undefined;
    const resource = main ? 'injectionScript.js' : file;
    if (!['injectionScript.js', 'nativeshell.js', 'EventEmitter.js', 'ExoPlayerPlugin.js', 'NavigationPlugin.js'].includes(resource)) return undefined;
    const bytes = getContext(this).resourceManager.getRawFileContentSync(`native/${resource}`);
    let script = new util.TextDecoder('utf-8').decodeToString(bytes);
    if (resource === 'nativeshell.js') {
      // Only advertise operations actually connected to CMP. No fake download/remote-control support.
      script += '\nwindow.NativeShell.AppHost.supports = c => ["externallinks", "clientsettings", "multiserver", "exit"].includes(c.toLowerCase());';
      script += '\nwindow.NativeShell.getPlugins = () => ["NavigationPlugin", "ExoPlayerPlugin"];';
    }
    const response = new WebResourceResponse();
    response.setResponseData(script); response.setResponseMimeType('application/javascript');
    response.setResponseEncoding('utf-8'); response.setResponseCode(200); response.setReasonMessage('OK');
    return response;
  }
  build() {
    Web({ src: this.args.url, controller: this.controller })
      .width('100%').height('100%')
      .javaScriptAccess(true).domStorageAccess(true).fileAccess(false)
      .javaScriptOnDocumentStart([{ script: this.args.script, scriptRules: [this.args.origin] }])
      .onControllerAttached((): void => {
        if (this.args.script.length > 0) {
          this.interface = new NativeWebInterface(this.controller, this.args);
          this.controller.registerJavaScriptProxy(this.interface, 'NativeInterface',
            ['getDeviceInformation', 'openUrl', 'openClientSettings', 'openServerSelection', 'exitApp', 'enableFullscreen', 'disableFullscreen']);
          this.controller.registerJavaScriptProxy(this.interface, 'NativePlayer',
            ['isEnabled', 'loadPlayer', 'pausePlayer', 'resumePlayer', 'stopPlayer', 'destroyPlayer', 'seek', 'seekMs', 'setVolume']);
        }
      })
      .onPageEnd((): void => this.publishBackState())
      .onInterceptRequest((event) => {
        if (event) {
          const response = this.intercept(event.request.getRequestUrl());
          if (response) return response;
        }
        return;
      })
      .onErrorReceive((event): void => {
        if (event?.request.isMainFrame()) {
          const state: WebEvent = { handle: this.args.handle, action: 'error', value: '', canBack: false };
          nativeApi.UpdateOhosWebState(JSON.stringify(state));
        }
      })
      .onOverrideUrlLoading((request): boolean => {
        const value = request.getRequestUrl();
        if (this.accepts(value)) return false;
        const event: WebEvent = { handle: this.args.handle, action: 'openUrl', value: value, canBack: false };
        nativeApi.UpdateOhosWebState(JSON.stringify(event)); return true;
      })
  }
}

export function registerEmbeddedWebInterop(): void { registerComposeInteropBuilder('MaFeiEmbeddedWeb', embeddedWebBuilder); }
