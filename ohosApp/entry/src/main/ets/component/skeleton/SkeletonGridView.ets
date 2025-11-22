import { AnimateType, SkeletonTheme, SkeletonType, UISkeleton } from "@hw-agconnect/ui-skeleton";

@Preview
@ComponentV2
export struct SkeletonGridView {
  // 切换动画
  @Local animate: AnimateType = AnimateType.GRADIENT;
  list: Array<number> = Array.from({ length: 99 })

  build() {
    Column() {
      Grid() {
        ForEach(this.list, (item: number, index: number) => {
          GridItem() {
            UISkeleton({
              animate: this.animate,
              options: [{
                type: SkeletonType.RECT,
                width: '100%',
                height: '100%',
                radius: 8
              }]
            }).width('100%')
              .height(150)
          }
        })
      }
      .scrollBar(BarState.Off)
      .width('100%')
      .height('100%')
      .columnsTemplate('1fr 1fr 1fr')
      .columnsGap(10)
      .rowsGap(10)
      .padding({
        top:12
      })
    }.width('100%')
    .height('100%')
  }
}