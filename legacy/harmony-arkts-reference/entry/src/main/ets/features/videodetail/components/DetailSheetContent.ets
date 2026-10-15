import { LengthUnit } from '@ohos.arkui.node'
import { MediaInfoSectionViewData } from '../../../media/presentation/VideoDetailPresentationModels'
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver'
import { VideoInfoDetail } from './VideoInfoDetail'

@ComponentV2
export struct OverviewSheetContent {
  @Require @Param overview: string
  @Require @Param textColor: ResourceColor
  @Param blurFallback: boolean = false

  build() {
    Scroll() {
      Text(this.overview)
        .width('100%')
        .fontColor(this.textColor)
        .fontSize(14)
        .textAlign(TextAlign.Start)
        .lineSpacing({
          value: 6,
          unit: LengthUnit.VP
        })
    }
    .scrollable(ScrollDirection.Vertical)
    .padding({
      left: 16,
      right: 16,
      bottom: 10
    })
    .width('100%')
    .height('100%')
    .align(Alignment.Top)
    .nestedScroll({
      scrollForward: NestedScrollMode.PARENT_FIRST,
      scrollBackward: NestedScrollMode.SELF_FIRST
    })
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.Floating, this.blurFallback, false))
  }
}

@ComponentV2
export struct VideoInfoSheetContent {
  @Require @Param sections: MediaInfoSectionViewData[]
  @Param safeBottom: number = 0
  @Param blurFallback: boolean = false

  build() {
    Scroll() {
      VideoInfoDetail({
        sections: this.sections
      })
    }
    .scrollBar(BarState.Off)
    .scrollable(ScrollDirection.Vertical)
    .edgeEffect(EdgeEffect.None)
    .padding({
      left: 16,
      right: 16,
      bottom: 10 + this.safeBottom
    })
    .width('100%')
    .height('100%')
    .align(Alignment.Top)
    .nestedScroll({
      scrollForward: NestedScrollMode.PARENT_FIRST,
      scrollBackward: NestedScrollMode.SELF_FIRST
    })
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.Floating, this.blurFallback, false))
  }
}
