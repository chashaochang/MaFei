/**
 * 播放器专用时间轴。
 *
 * 使用较小的视觉轨道和独立的 32vp 手势热区，避免系统 Slider 的大拇指、默认轨道和
 * 播放器控制层视觉不一致；拖动时通过回调将预览位置交给上层统一处理。
 */
@ComponentV2
export struct PlayerSeekBar {
  @Param value: number = 0
  @Param bufferedValue: number = 0
  @Param total: number = 1
  @Param isEnabled: boolean = true
  @Param trackHeight: number = 4
  @Param thumbDiameter: number = 14
  @Param onSeekStart: (value: number) => void = (_value: number): void => {
  }
  @Param onSeekChanging: (value: number) => void = (_value: number): void => {
  }
  @Param onSeekEnd: (value: number) => void = (_value: number): void => {
  }
  @Local dragging: boolean = false
  @Local draggingValue: number = 0
  // 轨道宽度由首次布局异步回填；保留为本地状态，避免滑块先按 0vp 渲染在左侧再跳位。
  @Local trackWidthVp: number = 0
  private readonly horizontalGesture: PanGestureOptions = new PanGestureOptions({ direction: PanDirection.Horizontal })

  private safeTotal(): number {
    return Math.max(1, this.total)
  }

  private clamp(value: number): number {
    return Math.max(0, Math.min(this.safeTotal(), value))
  }

  private displayedValue(): number {
    return this.dragging ? this.draggingValue : this.clamp(this.value)
  }

  private fraction(value: number): number {
    return this.clamp(value) / this.safeTotal()
  }

  private progressWidth(value: number): string {
    return `${this.fraction(value) * 100}%`
  }

  private thumbOffset(): number {
    return this.fraction(this.displayedValue()) * Math.max(0, this.trackWidthVp - this.thumbDiameter)
  }

  private valueFromEvent(event: GestureEvent | undefined): number {
    if (!event?.fingerList?.[0] || this.trackWidthVp <= 0) {
      return this.displayedValue()
    }
    return this.clamp(event.fingerList[0].localX / this.trackWidthVp * this.safeTotal())
  }

  private beginSeek(value: number): void {
    if (!this.isEnabled) {
      return
    }
    this.dragging = true
    this.draggingValue = value
    this.onSeekStart(value)
  }

  private changeSeek(value: number): void {
    if (!this.dragging || !this.isEnabled) {
      return
    }
    this.draggingValue = value
    this.onSeekChanging(value)
  }

  private endSeek(): void {
    if (!this.dragging || !this.isEnabled) {
      return
    }
    const value: number = this.draggingValue
    this.dragging = false
    this.onSeekEnd(value)
  }

  private seekByTap(event: GestureEvent | undefined): void {
    if (!this.isEnabled) {
      return
    }
    const value: number = this.valueFromEvent(event)
    this.beginSeek(value)
    this.endSeek()
  }

  build() {
    Stack({ alignContent: Alignment.Center }) {
      Stack({ alignContent: Alignment.Start }) {
        Row()
          .width('100%')
          .height(this.trackHeight)
          .backgroundColor('#66FFFFFF')
          .borderRadius(this.trackHeight / 2)
        Row()
          .width(this.progressWidth(this.bufferedValue))
          .height(this.trackHeight)
          .backgroundColor('#A6FFFFFF')
          .borderRadius(this.trackHeight / 2)
        Row()
          .width(this.progressWidth(this.displayedValue()))
          .height(this.trackHeight)
          .backgroundColor(Color.White)
          .borderRadius(this.trackHeight / 2)
      }
      .width('100%')
      .height(this.trackHeight)

      if (this.trackWidthVp > 0) {
        Circle()
          .width(this.thumbDiameter)
          .height(this.thumbDiameter)
          .fill(Color.White)
          .position({
            x: this.thumbOffset(),
            y: (32 - this.thumbDiameter) / 2
          })
      }
    }
    .width('100%')
    .height(32)
    .enabled(this.isEnabled)
    .opacity(this.isEnabled ? 1 : 0.45)
    .hitTestBehavior(HitTestMode.BLOCK_HIERARCHY)
    .accessibilityText('播放进度')
    .onAreaChange((_oldValue: Area, newValue: Area): void => {
      this.trackWidthVp = Number(newValue.width)
    })
    .gesture(GestureGroup(GestureMode.Exclusive,
      TapGesture({ count: 1 }).onAction((event: GestureEvent | undefined): void => this.seekByTap(event)),
      PanGesture(this.horizontalGesture)
        .onActionStart((event: GestureEvent | undefined): void => this.beginSeek(this.valueFromEvent(event)))
        .onActionUpdate((event: GestureEvent | undefined): void => this.changeSeek(this.valueFromEvent(event)))
        .onActionEnd((): void => this.endSeek())
    ))
  }
}
