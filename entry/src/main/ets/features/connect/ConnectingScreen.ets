import { AppUIState } from "../../entity/AppUIState";
import { AppStorageV2 } from "@kit.ArkUI";
import lottie, { AnimationItem } from "@ohos/lottie";
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from "../../theme/AppThemeSurfaceResolver";

@ComponentV2
export struct ConnectingScreen {
  @Require @Param onLogoutClick: () => void
  // 构建上下文
  private renderingSettings: RenderingContextSettings = new RenderingContextSettings(true);
  private canvasRenderingContext: CanvasRenderingContext2D = new CanvasRenderingContext2D(this.renderingSettings);
  private animateItem: AnimationItem | null = null;
  private animateName: string = 'animation';
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;

  // 页面销毁时释放动画资源
  aboutToDisappear(): void {
    console.info('aboutToDisappear');
    lottie.destroy();
  }

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable)
  }

  private primaryTextColor(): ResourceColor {
    return this.useNativeSurface() ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return this.useNativeSurface() ? $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  private pageBackground(): ResourceColor {
    return this.useNativeSurface() ? $r('app.color.native_canvas_background') :
      AppThemeSurfaceResolver.routeBackground(
        this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable)
  }

  @Builder
  private connectingContent() {
    Blank().layoutWeight(1.5)
    Text('正在连接...')
      .fontColor(this.primaryTextColor())
      .fontSize(20)
    Blank().layoutWeight(1.5)
    Image($r('app.media.nas'))
      .height(160)
    Blank().layoutWeight(0.5)
    Canvas(this.canvasRenderingContext)
      .width(160)
      .height(90)
      .onReady(() => {
        if (this.animateItem != null) {
          this.animateItem?.resize();
        } else {
          this.canvasRenderingContext.imageSmoothingEnabled = true;
          this.canvasRenderingContext.imageSmoothingQuality = 'medium';
          this.loadAnimation();
        }
      })
    Blank().layoutWeight(1.5)
    Text('退出登录')
      .fontColor(this.secondaryTextColor())
      .fontSize(16)
      .padding(20)
      .onClick(() => {
        this.onLogoutClick()
      })
    Blank().layoutWeight(0.5)
  }

  @Builder
  private nativeConnectingContent() {
    Column({ space: 12 }) {
      Blank().layoutWeight(1)
      Stack() {
        SymbolGlyph($r('sys.symbol.externaldrive'))
          .fontSize(42)
          .fontColor([$r('sys.color.icon_primary')])
      }
      .width(88)
      .height(88)
      .borderRadius(44)
      .attributeModifier(AppThemeSurfaceResolver.modifier(
        AppThemeMaterialRole.Floating,
        this.useNativeSurface(),
        this.appUIState.systemMaterialAvailable
      ))
      LoadingProgress()
        .width(28)
        .height(28)
        .margin({ top: 8 })
      Text('正在连接服务器')
        .fontColor(this.primaryTextColor())
        .fontSize(20)
        .fontWeight(FontWeight.Medium)
      Text('正在验证服务器状态和账号信息')
        .fontColor(this.secondaryTextColor())
        .fontSize(14)
      Blank().layoutWeight(1)
      Button({ type: ButtonType.Capsule, stateEffect: true }) {
        Text('退出登录')
          .fontColor(this.primaryTextColor())
          .fontSize(14)
      }
      .height(44)
      .padding({ left: 24, right: 24 })
      .backgroundColor(Color.Transparent)
      .attributeModifier(AppThemeSurfaceResolver.modifier(
        AppThemeMaterialRole.AdaptiveControl,
        this.useNativeSurface(),
        this.appUIState.systemMaterialAvailable
      ))
      .onClick(() => {
        this.onLogoutClick()
      })
      .margin({ bottom: 32 })
    }
    .width('100%')
    .height('100%')
  }

  @Builder
  private feiniuConnectingContent() {
    Column() {
      this.connectingContent()
    }
    .width('100%')
    .height('100%')
    .backgroundImage($r('app.media.bg_zhezhao'))
    .backgroundImageSize(ImageSize.Cover)
  }

  build() {
    Stack() {
      if (this.useNativeSurface()) {
        this.nativeConnectingContent()
      } else {
        this.feiniuConnectingContent()
      }
    }.width('100%')
    .height('100%')
    .backgroundColor(this.pageBackground())
  }

  loadAnimation() {
    this.animateItem = lottie.loadAnimation({
      container: this.canvasRenderingContext,
      renderer: 'canvas', // canvas 渲染模式
      loop: true,
      autoplay: false,
      name: this.animateName,
      contentMode: 'Contain',
      path: 'common/lottie/LoadingDotsBlue.json',
    })
    // 因为动画是异步加载，所以对animateItem的操作需要放在动画加载完成回调里操作
    this.animateItem.addEventListener('DOMLoaded', (_args: Object): void => {
      //this.animateItem?.changeColor([225, 25, 100, 1]);
      this.animateItem?.play();
    });
  }

  destroy() {
    this.animateItem?.removeEventListener('DOMLoaded');
    lottie.destroy(this.animateName);
    this.animateItem = null;
  }
}
