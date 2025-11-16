import { AppUIState } from "../../entity/AppUIState";
import { AppStorageV2 } from "@kit.ArkUI";
import lottie, { AnimationItem } from "@ohos/lottie";

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

  build() {
    Stack() {
      Column() {
        Blank().layoutWeight(1.5)
        Text('正在连接...')
          .fontColor($r('app.color.text_primary'))
          .fontSize(20)
        Blank().layoutWeight(1.5)
        Image($r('app.media.nas'))
          .height(160)
        Blank().layoutWeight(0.5)
        // 关联画布
        Canvas(this.canvasRenderingContext)
          .width(160)
          .height(90)
          .onReady(() => {
            // 加载动画
            if (this.animateItem != null) {
              // 可在此生命回调周期中加载动画，可以保证动画尺寸正确
              this.animateItem?.resize();
            } else {
              // 抗锯齿的设置
              this.canvasRenderingContext.imageSmoothingEnabled = true;
              this.canvasRenderingContext.imageSmoothingQuality = 'medium';
              this.loadAnimation();
            }
          })
        Blank().layoutWeight(1.5)
        Text('退出登录')
          .fontColor($r('app.color.text_1'))
          .fontSize(16)
          .padding(20)
          .onClick(() => {
            this.onLogoutClick()
          })
        Blank().layoutWeight(0.5)
      }
      .width('100%')
      .height('100%')
      .backgroundImage($r('app.media.bg_zhezhao'))
      .backgroundImageSize(ImageSize.Cover)
    }.width('100%')
    .height('100%')
    .backgroundColor($r('app.color.bg_main'))
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