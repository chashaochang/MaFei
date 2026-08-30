import { AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver'

@ComponentV2
struct ManagementSkeletonLine {
  @Require @Param lineWidth: Length
  @Param lineHeight: Length = 12
  @Require @Param lineColor: ResourceColor

  build() {
    Row()
      .width(this.lineWidth)
      .height(this.lineHeight)
      .borderRadius(6)
      .backgroundColor(this.lineColor)
  }
}

@ComponentV2
struct ManagementSkeletonSection {
  @Require @Param sectionHeight: number
  @Require @Param cardRadius: number
  @Require @Param lineColor: ResourceColor
  @Require @Param sectionColor: ResourceColor
  @Require @Param shouldClip: boolean

  build() {
    Column({ space: 12 }) {
      ManagementSkeletonLine({ lineWidth: '42%', lineHeight: 16, lineColor: this.lineColor })
      ManagementSkeletonLine({ lineWidth: '100%', lineColor: this.lineColor })
      ManagementSkeletonLine({ lineWidth: '76%', lineColor: this.lineColor })
      ManagementSkeletonLine({ lineWidth: '58%', lineColor: this.lineColor })
    }
    .width('100%')
    .height(this.sectionHeight)
    .padding(16)
    .alignItems(HorizontalAlign.Start)
    .borderRadius(this.cardRadius)
    .clip(this.shouldClip)
    .backgroundColor(this.sectionColor)
  }
}

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

  private sectionBackground(): ResourceColor {
    return this.useNativeMaterial ?
      AppThemeSurfaceResolver.contentGroupBackground() : this.feiniuCardColor
  }

  build() {
    Scroll() {
      Row() {
        if (this.isBigScreen) {
          Row({ space: 12 }) {
            Column({ space: 12 }) {
              ManagementSkeletonSection({
                sectionHeight: 196,
                cardRadius: this.cardRadius,
                lineColor: this.lineColor(),
                sectionColor: this.sectionBackground(),
                shouldClip: this.useNativeMaterial
              })
              ManagementSkeletonSection({
                sectionHeight: 142,
                cardRadius: this.cardRadius,
                lineColor: this.lineColor(),
                sectionColor: this.sectionBackground(),
                shouldClip: this.useNativeMaterial
              })
              ManagementSkeletonSection({
                sectionHeight: 174,
                cardRadius: this.cardRadius,
                lineColor: this.lineColor(),
                sectionColor: this.sectionBackground(),
                shouldClip: this.useNativeMaterial
              })
            }
            .layoutWeight(1.15)
            Column({ space: 12 }) {
              ManagementSkeletonSection({
                sectionHeight: 174,
                cardRadius: this.cardRadius,
                lineColor: this.lineColor(),
                sectionColor: this.sectionBackground(),
                shouldClip: this.useNativeMaterial
              })
              ManagementSkeletonSection({
                sectionHeight: 174,
                cardRadius: this.cardRadius,
                lineColor: this.lineColor(),
                sectionColor: this.sectionBackground(),
                shouldClip: this.useNativeMaterial
              })
            }
            .layoutWeight(0.85)
          }
          .width('100%')
          .constraintSize({ maxWidth: 1180 })
          .alignItems(VerticalAlign.Top)
        } else {
          Column({ space: 12 }) {
            ManagementSkeletonSection({
              sectionHeight: 196,
              cardRadius: this.cardRadius,
              lineColor: this.lineColor(),
              sectionColor: this.sectionBackground(),
              shouldClip: this.useNativeMaterial
            })
            ManagementSkeletonSection({
              sectionHeight: 142,
              cardRadius: this.cardRadius,
              lineColor: this.lineColor(),
              sectionColor: this.sectionBackground(),
              shouldClip: this.useNativeMaterial
            })
            ManagementSkeletonSection({
              sectionHeight: 174,
              cardRadius: this.cardRadius,
              lineColor: this.lineColor(),
              sectionColor: this.sectionBackground(),
              shouldClip: this.useNativeMaterial
            })
            ManagementSkeletonSection({
              sectionHeight: 174,
              cardRadius: this.cardRadius,
              lineColor: this.lineColor(),
              sectionColor: this.sectionBackground(),
              shouldClip: this.useNativeMaterial
            })
            ManagementSkeletonSection({
              sectionHeight: 174,
              cardRadius: this.cardRadius,
              lineColor: this.lineColor(),
              sectionColor: this.sectionBackground(),
              shouldClip: this.useNativeMaterial
            })
          }
          .width('100%')
          .constraintSize({ maxWidth: 680 })
        }
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
