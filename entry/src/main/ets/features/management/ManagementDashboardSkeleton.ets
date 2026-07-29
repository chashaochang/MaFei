import { AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver';

@ComponentV2
export struct ManagementDashboardSkeleton {
  @Require @Param feiniuCardColor: ResourceColor
  @Require @Param cardRadius: number
  @Require @Param isBigScreen: boolean
  @Require @Param useNativeMaterial: boolean

  private lineColor(): ResourceColor {
    return this.useNativeMaterial ?
      $r('sys.color.comp_background_tertiary') : $r('app.color.component_bg')
  }

  @Builder
  line(width: Length, height: Length = 12) {
    Row()
      .width(width)
      .height(height)
      .borderRadius(6)
      .backgroundColor(this.lineColor())
  }

  @Builder
  sectionBody() {
    Column({ space: 12 }) {
      this.line('42%', 16)
      this.line('100%')
      this.line('76%')
      this.line('58%')
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  section(height: number) {
    Column() {
      this.sectionBody()
    }
    .width('100%')
    .height(height)
    .padding(16)
    .borderRadius(this.cardRadius)
    .clip(true)
    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  feiniuSection(height: number) {
    Column() {
      this.sectionBody()
    }
    .width('100%')
    .height(height)
    .padding(16)
    .borderRadius(this.cardRadius)
    .backgroundColor(this.feiniuCardColor)
  }

  @Builder
  sectionSurface(height: number) {
    if (this.useNativeMaterial) {
      this.section(height)
    } else {
      this.feiniuSection(height)
    }
  }

  @Builder
  content() {
    if (this.isBigScreen) {
      Row({ space: 12 }) {
        Column({ space: 12 }) {
          this.sectionSurface(196)
          this.sectionSurface(142)
          this.sectionSurface(174)
        }
        .layoutWeight(1.15)
        Column({ space: 12 }) {
          this.sectionSurface(174)
          this.sectionSurface(174)
        }
        .layoutWeight(0.85)
      }
      .width('100%')
      .constraintSize({ maxWidth: 1180 })
      .alignItems(VerticalAlign.Top)
    } else {
      Column({ space: 12 }) {
        this.sectionSurface(196)
        this.sectionSurface(142)
        this.sectionSurface(174)
        this.sectionSurface(174)
        this.sectionSurface(174)
      }
      .width('100%')
      .constraintSize({ maxWidth: 680 })
    }
  }

  build() {
    Scroll() {
      Row() {
        this.content()
      }
      .width('100%')
      .justifyContent(FlexAlign.Center)
      .padding({ left: 16, right: 16, top: 12, bottom: 32 })
    }
    .width('100%')
    .height('100%')
    .scrollBar(BarState.Off)
  }
}
