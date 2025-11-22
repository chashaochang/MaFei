import { AnimateType, SkeletonTheme, SkeletonType, UISkeleton } from "@hw-agconnect/ui-skeleton";

@Preview
@ComponentV2
export struct SkeletonDetailView {
  // 切换动画
  @Local animate: AnimateType = AnimateType.GRADIENT;

  build() {
    Column() {
      UISkeleton({
        animate: this.animate,
        options: [{
          type: SkeletonType.RECT,
          width: '100%',
          height: '100%'
        }]
      }).width('100%')
        .height(350)

      Column({ space: 20 }) {
        UISkeleton({
          theme: SkeletonTheme.TEXT, animate: this.animate
        }).width('100%')
          .height(40)

        UISkeleton({
          theme: SkeletonTheme.NONE, animate: this.animate
        }).width('100%')
          .height(20)

        UISkeleton({
          animate: this.animate,
          options: [{
            type: SkeletonType.RECT,
            width: '100%',
            height: 60,
            radius: 30
          }]
        }).width('100%')
          .height(60)

        UISkeleton({
          theme: SkeletonTheme.TEXT, animate: this.animate
        }).width('100%')
          .height(40)

        UISkeleton({
          theme: SkeletonTheme.TEXT, animate: this.animate
        }).width(80)
          .height(40)

        Row({ space: 20 }) {
          UISkeleton({ theme: SkeletonTheme.AVATAR, animate: this.animate }).width(60).height(60)
          UISkeleton({ theme: SkeletonTheme.AVATAR, animate: this.animate }).width(60).height(60)
          UISkeleton({ theme: SkeletonTheme.AVATAR, animate: this.animate }).width(60).height(60)
          UISkeleton({ theme: SkeletonTheme.AVATAR, animate: this.animate }).width(60).height(60)
        }.height(60)

        UISkeleton({
          theme: SkeletonTheme.PARAGRAPH, animate: this.animate
        }).width('100%')
          .layoutWeight(1)
      }.padding(16)
      .alignItems(HorizontalAlign.Start)
    }.width('100%')
    .height('100%')
  }
}