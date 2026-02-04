import {
  IjkMediaPlayer,
  InterruptEvent,
  InterruptHintType,
  LogUtils,
  OnBufferingUpdateListener,
  OnCompletionListener,
  OnErrorListener,
  OnInfoListener,
  OnPreparedListener,
  OnSeekCompleteListener,
  OnTimedTextListener,
  OnVideoSizeChangedListener
} from "@ohos/ijkplayer";
import { PlayerStatus, PlayStatus } from "./PlayStatus";
import prompt from "@ohos.promptAction";
import { BusinessError, emitter } from "@kit.BasicServicesKit";
import { Constants } from "../../utils/Constants";
import { SmartDialog } from "ohos_smart_dialog";
import { audio } from "@kit.AudioKit";
import { window, PiPWindow } from "@kit.ArkUI";
import avSessionManager from "../../utils/AVSessionManager";
import { avSession } from "@kit.AVSessionKit";
import { common } from "@kit.AbilityKit";
import { OneMoreUtil } from "../../utils/OneMoreUtil";
import { LiveTvUIState } from "../livetv/LiveTvUIState";
import { LiveTvViewModel } from "../livetv/LiveTvViewModel";
import { BaseItemDto } from "sdk/src/main/ets/generated-client/models";
import { ImageKnifeComponent } from "@ohos/imageknifepro";

let uiTime: number = 0;

@ComponentV2
export struct LivePlayerView {
  @Param @Require videoUrl: string

  @Monitor('videoUrl')
  onUrlChange() {
    this.stop()
    this.startPlayOrResumePlay()
  }
  
  @Param @Require onNext: () => void
  @Param @Require onEpisodeChange: (index: number) => void
  @Local loadingVisible: Visibility = Visibility.None;
  @Local replayVisible: Visibility = Visibility.None;
  @Local slideEnable: boolean = false;
  @Local aspRatio: number = 0.5;
  @Local mContext: object | undefined = undefined;
  @Local mFirst: boolean = true;

  @Local mDestroyPage: boolean = false;
  @Local playSpeed: string = '1f';
  @Local oldSeconds: number = 0;
  @Local isCurrentTime: boolean = false;
  @Local videoWidth: string = '100%';
  @Local initAspectRatio: number = 1920 / 1080;
  @Local videoAspectRatio: number = this.initAspectRatio;
  private last: number = 0;
  @Local videoParentAspectRatio: number = this.initAspectRatio;
  private mIjkMediaPlayer = IjkMediaPlayer.getInstance();
  @Local PROGRESS_MAX_VALUE: number = 100;
  @Local curIndex: number = 0;
  @Local recordProgressVisible: Visibility = Visibility.None;
  @Local screenshotProgressVisible: Visibility = Visibility.None;
  @Local recordSaveFilePath: string = "";

  @Local showControls: boolean = false;
  @Local showChannelMenu: boolean = false;
  @Local isPanning: boolean = false; // 标记是否正在滑动
  private panOptionChangeChannel: PanGestureOptions = new PanGestureOptions({ direction: PanDirection.Vertical });
  private mAudioManager: audio.AudioManager | null = null;
  private channelListScroller: Scroller = new Scroller();
  sec: number = 2;
  pipController?: PiPWindow.PiPController;
  xComponentController: XComponentController = new XComponentController()
  @Local hintMsgVisibility: boolean = false;
  @Local offsetY: number = 0;

  changeSystemBar() {
    if (!this.vm.appUIState.isFullScreen && this.showControls) {
      this.vm.appUIState.windowClass?.setWindowSystemBarEnable(['status', 'navigation'])
    } else {
      this.vm.appUIState.windowClass?.setWindowSystemBarEnable([])
    }
  }

  @Monitor('showControls')
  onControlVisibilityChanged() {
    this.changeSystemBar()
  }

  @Monitor('showChannelMenu')
  onChannelMenuVisibilityChanged() {
    if (this.showChannelMenu && this.vm.channelList.length > 0) {
      // 延迟滚动，确保列表已渲染
      setTimeout(() => {
        this.scrollToSelectedChannel()
      }, 50)
    }
  }

  private scrollToSelectedChannel() {
    if (this.vm.playIndex >= 0 && this.vm.playIndex < this.vm.channelList.length) {
      // 滚动到选中项，并使其居中显示
      this.channelListScroller.scrollToIndex(this.vm.playIndex, false, ScrollAlign.CENTER)
    }
  }

  @Monitor('vm.appUIState.isFullScreen')
  onScreenChanged() {
    console.log('this.vm.appUIState.isFullScreen:'+this.vm.appUIState.isFullScreen);
    this.changeSystemBar()
    if (this.vm.appUIState.isFullScreen) {
      if (OneMoreUtil.isNormalPhone()) {
        if (canIUse('SystemCapability.Window.SessionManager')) {
          this.vm.appUIState.windowClass?.setPreferredOrientation(window.Orientation.USER_ROTATION_LANDSCAPE).catch()
        }
      } else if (OneMoreUtil.isBigScreen()) {

      }
    } else {
      if (OneMoreUtil.isNormalPhone()) {
        SmartDialog.dismiss()
        if (canIUse('SystemCapability.Window.SessionManager')) {
          this.vm.appUIState.windowClass?.setPreferredOrientation(window.Orientation.USER_ROTATION_PORTRAIT);
        }
      } else if (OneMoreUtil.isBigScreen()) {

      }
    }
  }

  @Consumer() ui: LiveTvUIState = new LiveTvUIState(this.getUIContext())
  @Consumer() vm: LiveTvViewModel = new LiveTvViewModel(this.ui)

  @Monitor('ui')
  onNewInstance() {
    this.stop()
  }

  @Monitor('ui.playerStatus')
  onPlayerStateChanged() {
    this.sendAVSessionState(this.ui.playerStatus)
  }

  @Monitor('ui.isCastPlaying')
  onCastChanged() {
    if (this.ui.isCastPlaying) {
      this.vm.appUIState.isFullScreen = false
    }
  }

  aboutToAppear() {
    this.mAudioManager = audio.getAudioManager();
    emitter.on(Constants.PLAYER_EVENT, async (data) => {
      if (data.data && data.data['eventName']) {
        let name = data.data['eventName'] as string
        switch (name) {
          case 'onPageShow':
            if (this.ui.isCastPlaying) {
              return
            }
            if (this.mContext && !this.mFirst) {
              this.startPlayOrResumePlay();
            }
            break
          case 'onPageHide':
            this.pause()
            break
          case 'Pause':
            this.pause()
            break
          case 'Play':
          case 'Resume':
            this.startPlayOrResumePlay()
            break
          case 'Stop':
            this.stop()
            break
          case 'Destroy':
            this.stop()
            break
          case 'Seek':

            break
          case 'setVolume':
            let volume = data.data['args'] as number | undefined
            if (volume) {
              if (this.mAudioManager) {
                this.mAudioManager.setVolume(audio.AudioVolumeType.MEDIA, volume, (err: BusinessError) => {
                  if (err) {
                    LogUtils.getInstance().LOGI(`Failed to set the volume. ${err}`);
                    return;
                  }
                })
              }
            }
            break
          case 'Speed':
            let speed = data.data['args'] as string | undefined
            if (speed) {
              this.playSpeed = speed
            }
            break
        }
      }
    })

    LogUtils.getInstance().LOGI("aboutToAppear");
    let event: Callback<InterruptEvent> = (event) => {
      LogUtils.getInstance().LOGI(`event: ${JSON.stringify(event)}`);
      if (event.hintType === InterruptHintType.INTERRUPT_HINT_PAUSE) {
        this.pause();
      } else if (event.hintType === InterruptHintType.INTERRUPT_HINT_RESUME) {
        this.startPlayOrResumePlay();
      } else if (event.hintType === InterruptHintType.INTERRUPT_HINT_STOP) {
        this.stop();
      }
    }
    this.mIjkMediaPlayer.on('audioInterrupt', event);

    //监听切换到桌面/恢复状态开启关闭画中画
    let context = getContext(this) as common.UIAbilityContext;
    context.eventHub.on('onForeground', () => {
      if (canIUse('SystemCapability.Window.SessionManager')) {
        this.pipController?.stopPiP()
      }
    })
    context.eventHub.on('onBackground', async () => {

    })
  }

  aboutToDisappear() {
    emitter.off(Constants.PLAYER_EVENT.eventId)
    LogUtils.getInstance().LOGI("aboutToDisappear");
    this.mDestroyPage = true;
    this.mIjkMediaPlayer.setScreenOnWhilePlaying(false);
    // this.stopRecord(false);
    if (this.ui.CONTROL_PlayStatus != PlayStatus.INIT) {
      this.stop();
    }
    this.mIjkMediaPlayer.off('audioInterrupt');
    this.vm.appUIState.windowClass?.setWindowSystemBarEnable(['status', 'navigation'])
  }

  @Builder
  leftControlView() {
    Column() {
      // 菜单标题
      Text('频道列表')
        .fontSize(16)
        .fontWeight(FontWeight.Bold)
        .fontColor(Color.White)
        .width('100%')
        .padding({ top: 15, bottom: 10, left: 12, right: 12 })
        .textAlign(TextAlign.Start)

      // 分割线
      Divider()
        .color('rgba(255, 255, 255, 0.2)')
        .strokeWidth(1)
        .margin({ bottom: 8 })

      // 频道列表
      List({ space: 6, scroller: this.channelListScroller }) {
        ForEach(this.vm.channelList, (channel: BaseItemDto, index: number) => {
          ListItem() {
            Row() {
              // 频道序号
              Text(`${index + 1}`)
                .fontSize(12)
                .fontColor(this.vm.playIndex === index ? '#00D4FF' : 'rgba(255, 255, 255, 0.6)')
                .width(30)
                .textAlign(TextAlign.Center)
                .fontWeight(this.vm.playIndex === index ? FontWeight.Bold : FontWeight.Normal)

              // 频道名称
              Text(channel.Name || '未知频道')
                .fontSize(14)
                .fontColor(this.vm.playIndex === index ? Color.White : 'rgba(255, 255, 255, 0.85)')
                .maxLines(2)
                .textOverflow({ overflow: TextOverflow.Ellipsis })
                .layoutWeight(1)
                .fontWeight(this.vm.playIndex === index ? FontWeight.Medium : FontWeight.Normal)

              // 当前播放指示器
              if (this.vm.playIndex === index) {
                Image($r('sys.media.ohos_ic_public_play'))
                  .width(16)
                  .height(16)
                  .fillColor('#00D4FF')
                  .margin({ left: 8 })
              }
            }
            .width('100%')
            .padding({ left: 12, right: 12, top: 10, bottom: 10 })
            .backgroundColor(this.vm.playIndex === index ?
              'rgba(0, 212, 255, 0.15)' :
              'rgba(255, 255, 255, 0.05)')
            .borderRadius(8)
            .border({
              width: this.vm.playIndex === index ? 1 : 0,
              color: 'rgba(0, 212, 255, 0.3)'
            })
            .onClick(() => {
              this.vm.playIndex = index
              this.vm.play()
              // 点击频道后隐藏菜单并显示频道信息
              this.showChannelMenu = false
              this.vm.showChannelInfoWithDelay()
            })
            .animation({
              duration: 200,
              curve: Curve.EaseInOut
            })
          }
          .margin({ left: 8, right: 8 })
        }, (channel: BaseItemDto) => channel.Id)
      }
      .scrollBar(BarState.Off)
      .scrollBarColor('rgba(255, 255, 255, 0.3)')
      .scrollBarWidth(4)
      .width('100%')
      .layoutWeight(1)
      .padding({ bottom: 8 })
      .edgeEffect(EdgeEffect.Spring)
    }
    .padding({
      bottom: this.showControls && !this.vm.appUIState.isFullScreen ? this.vm.appUIState.safeBottom : 10,
      left: this.vm.appUIState.isFullScreen ? this.vm.appUIState.safeTop : 0,
      right: this.vm.appUIState.isFullScreen ? this.vm.appUIState.safeTop : 0,
      top: 10
    })
    .width(240)
    .height('100%')
    .backgroundColor('rgba(0, 0, 0, 0.75)')
    .backdropBlur(30)
    .shadow({
      radius: 20,
      color: 'rgba(0, 0, 0, 0.5)',
      offsetX: 5,
      offsetY: 0
    })
    .borderRadius({ topRight: 12, bottomRight: 12 })
    .transition(TransitionEffect.OPACITY.animation({ duration: 200 })
      .combine(TransitionEffect.translate({ x: -240 }).animation({ duration: 300, curve: Curve.FastOutSlowIn })))
  }

  @Builder
  channelInfoView() {
    if (this.vm.channelList.length > 0 && this.vm.playIndex < this.vm.channelList.length) {
      Column() {
        // 右上角频道编号
        Row() {
          Text(`${this.vm.playIndex + 1}`)
            .fontSize(48)
            .fontWeight(FontWeight.Bold)
            .fontColor($r('app.color.color_main'))
        }
        .width('100%')
        .justifyContent(FlexAlign.End)
        .padding({
          top: this.vm.appUIState.safeTop,
        })

        Blank().layoutWeight(1)

        // 底部频道信息
        Row({ space: 12 }) {

          // 台标
          if (this.vm.getCurrentChannelImageUrl()) {
            ImageKnifeComponent({
              imageKnifeOption: {
                loadSrc: this.vm.getCurrentChannelImageUrl(),
                objectFit: ImageFit.Contain,
                border: {
                  radius: 8
                }
              }
            })
              .width(120)
              .height(60)
          }
          // 频道名称和台标
          Column({ space: 12 }) {
            // 频道名称
            Text(this.vm.channelList[this.vm.playIndex].Name || '未知频道')
              .fontSize(24)
              .fontWeight(FontWeight.Bold)
              .fontColor(Color.White)
              .maxLines(2)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
              .layoutWeight(1)
              .textAlign(TextAlign.Start)

            if (this.getCurrentProgramDisplayText()) {
              Text(this.getCurrentProgramDisplayText())
                .fontSize(14)
                .fontColor('rgba(255, 255, 255, 0.8)')
                .maxLines(1)
                .textOverflow({ overflow: TextOverflow.Ellipsis })
                .textAlign(TextAlign.Start)
                .layoutWeight(1)
            }
          }
          .layoutWeight(1)
          .alignItems(HorizontalAlign.Start)
        }
        .width('100%')
        .height(90)
        .padding({ left: 30, right: 30, top: 15, bottom: 15 })
        .backgroundColor('rgba(0, 0, 0, 0.3)')
        .backgroundBlurStyle(BlurStyle.BACKGROUND_ULTRA_THICK)
        .borderRadius(20)
        .margin({
          left: 20,
          right: 20,
          bottom: this.vm.appUIState.isFullScreen ? this.vm.appUIState.safeBottom : 0
        })
      }
      .padding({
        left: 100,
        right: 100
      })
      .width('100%')
      .height('100%')
    }
  }

  build() {
    Stack({ alignContent: Alignment.Center }) {
      Text('当前视频正在画中画播放')
        .fontColor(Color.Gray)
        .margin({ bottom: 30 })
        .visibility(this.hintMsgVisibility ? Visibility.Visible : Visibility.Hidden)
      Column() {
        XComponent({
          id: 'xcomponentId',
          type: XComponentType.SURFACE,
          libraryname: 'ijkplayer_napi',
          controller: this.xComponentController
        })
          .onLoad(async (event?: object) => {
            if (!!event) {
              this.initDelayPlay(event);
              //开启自动画中画
              if (canIUse('SystemCapability.Window.SessionManager')) {
                if (!this.pipController) {
                  await this.createPipController();
                  // this.pipController!.setAutoStartEnabled(true)
                }
              }
            }
          })
          .onDestroy(() => {
          })
          .width('100%')
          .width(this.videoWidth)
          .aspectRatio(this.videoAspectRatio)
          .id('xcomponentId')
      }.aspectRatio(this.videoAspectRatio)

      // 频道信息展示层
      if (this.ui.showChannelInfo) {
        this.channelInfoView()
      }

      Row() {
        //左侧菜单
        if (this.showChannelMenu) {
          this.leftControlView()
        }
        Blank()
          .layoutWeight(1)
      }
      .height('100%')
      .gesture(GestureGroup(GestureMode.Parallel,
        // 点击事件
        TapGesture({ count: 1 })
          .onAction(() => {
            // 如果正在滑动，不触发点击
            if (this.isPanning) {
              console.log('正在滑动，忽略点击事件');
              return;
            }
            // 切换菜单显示状态
            this.showChannelMenu = !this.showChannelMenu
            console.log('点击Row，showChannelMenu:', this.showChannelMenu)
          }),
        // 上下滑动换台
        PanGesture(this.panOptionChangeChannel)
          .onActionStart(() => {
            this.offsetY = 0; // 重置偏移量
            this.isPanning = false; // 重置滑动标志
          })
          .onActionUpdate((event: GestureEvent) => {
            this.offsetY = event.offsetY;
            // 如果滑动距离超过阈值，标记为正在滑动
            if (Math.abs(this.offsetY) > 10) {
              this.isPanning = true;
            }
            console.log(`当前滑动距离: ${this.offsetY.toFixed(1)}, isPanning: ${this.isPanning}`);
          })
          .onActionEnd((_event: GestureEvent | undefined) => {
            if (Math.abs(this.offsetY) > 30) { // 滑动超过30vp触发
              if (this.offsetY > 0) {
                console.log('触发下滑操作');
                this.vm.playPrevious()
              } else {
                console.log('触发上滑操作');
                this.onNext()
              }
            }
            // 延迟重置标志，确保点击事件能检查到
            setTimeout(() => {
              this.isPanning = false;
            }, 100);
          })
      ))

      Image($r('app.media.icon_load'))
        .objectFit(ImageFit.Auto)
        .width('120px')
        .height('120px')
        .visibility(this.loadingVisible)
        .border({ width: 0 })
        .borderStyle(BorderStyle.Dashed)

    }
    .width('100%')
    .backgroundColor($r('sys.color.black'))
    .clip(true)
    .onHover((isHover: boolean) => {
      if (OneMoreUtil.isPc()) {
        if (isHover) {
          if (this.loadingVisible == Visibility.Visible) {
            return;
          }
          if (!this.showControls) {
            this.timeCountdown();
          }
        } else {
          clearTimeout(uiTime);
          this.showControls = false;
        }
      }
    })
    .onMouse((event) => {
      if (this.loadingVisible == Visibility.Visible) {
        return;
      }
      if (event.action == MouseAction.Move) {
        if (!this.showControls) {
          this.timeCountdown();
        }
      }
    })
    //不知道为啥按键监听没好使,有空再说吧
    // .defaultFocus(true)
    // .onKeyPreIme((e) => {
    //   if (e.type == KeyType.Down) {
    //     switch (e.keyCode) {
    //       case KeyCode.KEYCODE_ESCAPE: {
    //         if(this.vm.appUIState.isFullScreen){
    //           OneMoreUtil.recover()
    //           this.vm.appUIState.isFullScreen = false
    //           return true
    //         }
    //         break
    //       }
    //       case KeyCode.KEYCODE_SPACE: {
    //         this.startOrStop()
    //         return true
    //       }
    //       default :{
    //
    //       }
    //     }
    //   }
    //   return false
    // })
  }

  private startOrStop() {
    if (!this.mIjkMediaPlayer) {
      return;
    }
    if (this.mIjkMediaPlayer.isPlaying()) {
      this.pause();
    } else {
      this.startPlayOrResumePlay();
    }
  }

  private initDelayPlay(context: object) {
    this.mContext = context;
    let that = this;
    setTimeout(() => {
      that.startPlayOrResumePlay();
      that.mFirst = false;
    }, 300)
  }

  private startPlayOrResumePlay() {
    this.mDestroyPage = false;
    LogUtils.getInstance()
      .LOGI("startPlayOrResumePlay start this.uiState.CONTROL_PlayStatus:" + this.ui.CONTROL_PlayStatus)
    if (this.ui.CONTROL_PlayStatus == PlayStatus.INIT) {
      this.play(this.videoUrl.toString());
      this.timeCountdown()
    }
    if (this.ui.CONTROL_PlayStatus == PlayStatus.PAUSE) {
      this.ui.CONTROL_PlayStatus = PlayStatus.PLAY
      this.mIjkMediaPlayer.start();
      this.timeCountdown()
    }
    this.ui.playerStatus = PlayerStatus.PLAYING
  }

  private showLoadIng() {
    this.loadingVisible = Visibility.Visible;
    this.replayVisible = Visibility.None;
  }

  private hideLoadIng() {
    this.loadingVisible = Visibility.None;
    this.replayVisible = Visibility.None;
  }

  private showRePlay() {
    this.loadingVisible = Visibility.None;
    this.replayVisible = Visibility.Visible;
  }

  private play(url: string) {
    let that = this;
    that.showLoadIng();
    //设置XComponent回调的context
    if (!!this.mContext) {
      this.mIjkMediaPlayer.setContext(this.mContext, 'xcomponentId');
    }
    if (this.ui.CONTROL_PlayStatus == PlayStatus.INIT) {
      this.mIjkMediaPlayer.reset();
    }
    this.ui.CONTROL_PlayStatus = PlayStatus.PLAY;
    //设置debug模式
    this.mIjkMediaPlayer.setDebug(false);
    //初始化配置
    this.mIjkMediaPlayer.native_setup();
    // 初始化配置后需要重新设置音频流音量，否则音量为默认值1.0
    // this.mIjkMediaPlayer.setVolume(this.volume.toString(), this.volume.toString());
    //设置视频源
    this.mIjkMediaPlayer.setDataSource(url);
    //使用精确寻帧 例如，拖动播放后，会寻找最近的关键帧进行播放，很有可能关键帧的位置不是拖动后的位置，而是较前的位置.可以设置这个参数来解决问题
    this.mIjkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", "1");
    //预读数据的缓冲区大小
    this.mIjkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", "102400");
    //停止预读的最小帧数
    this.mIjkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "min-frames", "100");
    //启动预加载
    this.mIjkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", "1");
    // 设置无缓冲，这是播放器的缓冲区，有数据就播放
    this.mIjkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", "0");
    //跳帧处理,放CPU处理较慢时，进行跳帧处理，保证播放流程，画面和声音同步
    this.mIjkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", "5");
    // 最大缓冲cache是3s， 有时候网络波动，会突然在短时间内收到好几秒的数据
    // 因此需要播放器丢包，才不会累积延时
    // 这个和第三个参数packet-buffering无关。
    this.mIjkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max_cached_duration", "3000");
    // 无限制收流
    // this.mIjkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", "1");
    // this.mIjkMediaPlayer.setOptionLong(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", "1")
    // 屏幕常亮
    this.mIjkMediaPlayer.setScreenOnWhilePlaying(true);
    // 设置超时
    this.mIjkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "timeout", "30000000");
    this.mIjkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "connect_timeout", "10000000");
    this.mIjkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "addrinfo_timeout", "10000000");
    this.mIjkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_timeout", "10000000");
    // 变速播放
    this.mIjkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "soundtouch", "1");
    // 开启h265硬解码 开了4K HEVC SDR的个别视频会花屏
    // this.mIjkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-hevc", "1");
    this.mIjkMediaPlayer.setSpeed(this.playSpeed);
    let Speed = this.mIjkMediaPlayer.getSpeed()
    LogUtils.getInstance().LOGI('getSpeed--' + Speed)
    //是否开启循环播放
    this.mIjkMediaPlayer.setLoopCount(false);
    let mOnVideoSizeChangedListener: OnVideoSizeChangedListener = {
      onVideoSizeChanged: (width: number, height: number, sar_num: number, sar_den: number) => {
        if (height === 0) {
          return;
        }
        const va = width / height;
        const vpa = that.videoParentAspectRatio;
        if (vpa > va) {
          that.videoWidth = (width / (height * vpa)) * 100 + '%';
        } else {
          that.videoWidth = '100%';
        }
        if (width && height) {
          that.videoAspectRatio = width / height;
        }
        LogUtils.getInstance()
          .LOGI("setOnVideoSizeChangedListener-->go:" + width + "," + height + "," + sar_num + "," + sar_den);
        that.getVideoSize();
        that.hideLoadIng();
      }
    }
    this.mIjkMediaPlayer.setOnVideoSizeChangedListener(mOnVideoSizeChangedListener);
    let mOnPreparedListener: OnPreparedListener = {
      onPrepared: () => {
        this.ui.playerStatus = PlayerStatus.PREPARED
        LogUtils.getInstance().LOGI("setOnPreparedListener-->go");
      }
    }
    this.mIjkMediaPlayer.setOnPreparedListener(mOnPreparedListener);

    let mOnTimedTextListener: OnTimedTextListener = {
      onTimedText: () => {
      }
    }
    this.mIjkMediaPlayer.setOnTimedTextListener(mOnTimedTextListener)

    let mOnCompletionListener: OnCompletionListener = {
      onCompletion: () => {
        LogUtils.getInstance().LOGI("OnCompletionListener-->go")
        that.showRePlay();
        that.slideEnable = false;
        that.stop();
      }
    }
    this.mIjkMediaPlayer.setOnCompletionListener(mOnCompletionListener);

    let mOnBufferingUpdateListener: OnBufferingUpdateListener = {
      onBufferingUpdate: (_percent: number) => {
        // LogUtils.getInstance().LOGI("OnBufferingUpdateListener-->go:" + percent);
        // let MediaInfo = this.mIjkMediaPlayer.getMediaInfo()
        // LogUtils.getInstance().LOGI('getMediaInfo---' + MediaInfo);
        // let VideoWidth = this.mIjkMediaPlayer.getVideoWidth()
        // LogUtils.getInstance().LOGI('getVideoWidth---' + VideoWidth);
        //
        // let VideoHeight = this.mIjkMediaPlayer.getVideoHeight()
        // LogUtils.getInstance().LOGI('getVideoHeight---' + VideoHeight);
        //
        // let VideoSarNum = this.mIjkMediaPlayer.getVideoSarNum()
        // LogUtils.getInstance().LOGI('getVideoSarNum---' + VideoSarNum);
        //
        // let VideoSarDen = this.mIjkMediaPlayer.getVideoSarDen()
        // LogUtils.getInstance().LOGI('getVideoSarDen---' + VideoSarDen);
        //
        // let AudioSessionId = this.mIjkMediaPlayer.getAudioSessionId()
        // LogUtils.getInstance().LOGI('getAudioSessionId---' + AudioSessionId);
        //
        // let Looping = this.mIjkMediaPlayer.isLooping()
        // LogUtils.getInstance().LOGI('isLooping---' + Looping);
      }
    }
    this.mIjkMediaPlayer.setOnBufferingUpdateListener(mOnBufferingUpdateListener);

    let mOnSeekCompleteListener: OnSeekCompleteListener = {
      onSeekComplete: () => {
        if (this.ui.isCastPlaying) {
          return
        }
        LogUtils.getInstance().LOGI("OnSeekCompleteListener-->go");
        that.startPlayOrResumePlay();
      }
    }
    this.mIjkMediaPlayer.setOnSeekCompleteListener(mOnSeekCompleteListener);

    let mOnInfoListener: OnInfoListener = {
      onInfo: (_what: number, _extra: number) => {
        //LogUtils.getInstance().LOGI("OnInfoListener-->go:" + what + "===" + extra);
        that.hideLoadIng();
      }
    }
    this.mIjkMediaPlayer.setOnInfoListener(mOnInfoListener);


    let mOnErrorListener: OnErrorListener = {
      onError: (what: number, extra: number) => {
        this.ui.playerStatus = PlayerStatus.ERROR
        LogUtils.getInstance().LOGI("OnErrorListener-->go:" + what + "===" + extra)
        that.hideLoadIng();
        prompt.showToast({
          message: getContext()
            .resourceManager
            .getStringByNameSync("Honey_the_video_is_playing_errant_The_system_is_wandering")
        });
      }
    }


    this.mIjkMediaPlayer.setOnErrorListener(mOnErrorListener);

    this.mIjkMediaPlayer.setMessageListener();

    this.mIjkMediaPlayer.prepareAsync();

    this.mIjkMediaPlayer.start();
  }

  private getVideoSize() {
    let VideoWidth = this.mIjkMediaPlayer.getVideoWidth();
    LogUtils.getInstance().LOGI("getVideoWidth---" + VideoWidth);

    let VideoHeight = this.mIjkMediaPlayer.getVideoHeight();
    LogUtils.getInstance().LOGI("getVideoHeight---" + VideoHeight);
  }

  private pause() {
    if (this.mIjkMediaPlayer.isPlaying()) {
      this.mIjkMediaPlayer.pause();
      this.mDestroyPage = true;
      this.ui.CONTROL_PlayStatus = PlayStatus.PAUSE;
      this.ui.playerStatus = PlayerStatus.PAUSED
    }
  }

  private stop() {
    this.ui.CONTROL_PlayStatus = PlayStatus.INIT;
    this.ui.playerStatus = PlayerStatus.STOPPED
    this.mIjkMediaPlayer.stop();
    this.mIjkMediaPlayer.release();
    this.ui.playerStatus = PlayerStatus.RELEASED
  }

  debounce() {
    const delay = 600;
    let cur = new Date().getTime();
    if (cur - this.last > delay) {
      this.last = cur;
      return true;
    }
    return false;
  }

  private timeCountdown() {
    clearTimeout(uiTime);
    this.sec = 2;
    uiTime = setInterval(() => {
      if (this.sec <= 0) {
        clearTimeout(uiTime);
        this.showControls = false;
      } else {
        this.sec--;
      }
    }, 1000)
  }

  sendAVSessionState(status: number) {
    let playbackState: avSession.AVPlaybackState = {
      speed: Number(this.playSpeed.replace('f', '')),
      position: {
        elapsedTime: this.mIjkMediaPlayer.getCurrentPosition(),
        updateTime: Date.now()
      },
    }
    switch (status) {
      case PlayerStatus.IDLE:
        playbackState.state = avSession.PlaybackState.PLAYBACK_STATE_IDLE
        break
      case PlayerStatus.INITIALIZED:
        playbackState.state = avSession.PlaybackState.PLAYBACK_STATE_INITIAL
        break
      case PlayerStatus.PREPARED:
        playbackState.state = avSession.PlaybackState.PLAYBACK_STATE_PREPARE
        break
      case PlayerStatus.PLAYING:
        playbackState.state = avSession.PlaybackState.PLAYBACK_STATE_PLAY
        break
      case PlayerStatus.PAUSED:
        playbackState.state = avSession.PlaybackState.PLAYBACK_STATE_PAUSE
        break
      case PlayerStatus.COMPLETED:
        playbackState.state = avSession.PlaybackState.PLAYBACK_STATE_COMPLETED
        break
      case PlayerStatus.STOPPED:
        playbackState.state = avSession.PlaybackState.PLAYBACK_STATE_STOP
        break
      case PlayerStatus.RELEASED:
        playbackState.state = avSession.PlaybackState.PLAYBACK_STATE_RELEASED
        break
      case PlayerStatus.ERROR:
        playbackState.state = avSession.PlaybackState.PLAYBACK_STATE_ERROR
        break
    }
    avSessionManager.getSession()?.setAVPlaybackState(playbackState).then(() => {
      console.info('SetAVPlaybackState successfully');
    }).catch((err: BusinessError) => {
      console.error(`SetAVPlaybackState BusinessError: code: ${err.code}, message: ${err.message}`);
    });
  }

  formatBitrate(bitrate: number): string {
    const BITRATE_MEGA_BIT: number = 1_000_000; // 1M
    const BITRATE_KILO_BIT: number = 1_000; // 1K
    let value: number;
    let unit: string;

    if (bitrate > BITRATE_MEGA_BIT) {
      value = bitrate / BITRATE_MEGA_BIT;
      unit = " Mbps";
    } else if (bitrate > BITRATE_KILO_BIT) {
      value = bitrate / BITRATE_KILO_BIT;
      unit = " kbps";
    } else {
      value = bitrate;
      unit = " bps";
    }
    // Format to 2 decimal places and remove trailing zeros
    const formatted = value.toFixed(2).replace(/\.?0+$/, '');
    return formatted + unit;
  }

  async createPipController() {
    if (canIUse('SystemCapability.Window.SessionManager')) {
      this.pipController = await PiPWindow.create({
        context: getContext(this),
        componentController: this.xComponentController,
        navigationId: Constants.NAVIGATION_ID,
        templateType: PiPWindow.PiPTemplateType.VIDEO_PLAY
      });
      this.pipController.on('stateChange', (state: PiPWindow.PiPState, reason: string) => {
        this.onStateChange(state, reason);
      });
      this.pipController.on('controlPanelActionEvent', (event: PiPWindow.PiPActionEventType, status?: number) => {
        this.onActionEvent(event, status);
      });
    }
  }

  onStateChange(state: PiPWindow.PiPState, reason: string) {
    if (canIUse('SystemCapability.Window.SessionManager')) {
      switch (state) {
        case PiPWindow.PiPState.ABOUT_TO_START:
          // this.curState = 'ABOUT_TO_START';
          // this.curError = $r('app.string.current_error_hint');
          break;
        case PiPWindow.PiPState.STARTED:
          // this.curState = 'STARTED';
          // this.curError = $r('app.string.current_error_hint');
          this.startPlayOrResumePlay()
          break;
        case PiPWindow.PiPState.ABOUT_TO_STOP:
          // this.curState = 'ABOUT_TO_STOP';
          // this.curError = $r('app.string.current_error_hint');
          //点了画中画的关闭按钮
          this.startPlayOrResumePlay()
          break;
        case PiPWindow.PiPState.STOPPED:

          break;
        case PiPWindow.PiPState.ABOUT_TO_RESTORE:
          // this.curState = 'ABOUT_TO_RESTORE';
          // this.curError = $r('app.string.current_error_hint');
          break;
        case PiPWindow.PiPState.ERROR:
          // this.curState = 'ERROR';
          // this.curError = reason;
          break;
        default:
          break;
      }
      // Logger.info(`[${TAG}] onStateChange: ${this.curState}, reason: ${reason}`);
    }
  }

  onActionEvent(event: PiPWindow.PiPActionEventType, status: number | undefined) {
    switch (event) {
      case 'playbackStateChanged':
        if (status === 0) {
          this.pause()
        } else {
          this.startPlayOrResumePlay()
        }
        break;
      default:
        break;
    }
    // this.buttonAction = event + `-status:${status}`;
    // Logger.info(`[${TAG}] onActionEvent: ${this.buttonAction} status:${status}}`);
  }

  async startPip() {
    if (!this.pipController) {
      await this.createPipController();
    }
    if (!this.pipController) {
      console.info(`pipController create error`);
      return;
    }
    await this.pipController.startPiP();
  }

  async stopPip() {
    if (!this.pipController) {
      console.info(`pipController is not exist`);
      return;
    }
    await this.pipController.stopPiP();
  }

  private getCurrentProgramDisplayText(): string | undefined {
    if (this.vm.playIndex < 0 || this.vm.playIndex >= this.vm.channelList.length) {
      return undefined;
    }
    const currentProgram = this.vm.channelList[this.vm.playIndex]?.CurrentProgram;
    const name = currentProgram?.Name;
    if (!name) {
      return undefined;
    }
    const timeText = this.formatProgramTime(currentProgram?.StartDate, currentProgram?.EndDate);
    return timeText ? `${timeText} ${name}` : name;
  }

  private formatProgramTime(startDate?: string| null, endDate?: string|null): string | undefined {
    if (!startDate || !endDate) {
      return undefined;
    }
    const start = new Date(startDate);
    const end = new Date(endDate);
    if (isNaN(start.getTime()) || isNaN(end.getTime())) {
      return undefined;
    }
    const pad2 = (value: number): string => value.toString().padStart(2, '0');
    const startText = `${pad2(start.getHours())}:${pad2(start.getMinutes())}`;
    const endText = `${pad2(end.getHours())}:${pad2(end.getMinutes())}`;
    return `${startText} - ${endText}`;
  }
}
