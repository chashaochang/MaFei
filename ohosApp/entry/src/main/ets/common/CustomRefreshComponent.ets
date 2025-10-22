@ComponentV2
export struct CustomRefreshComponent {
  build() {
    Stack() {
      Column() {
        LoadingProgress().height(32).color(Color.White)
      }
    }
    .align(Alignment.Center)
    .clip(true)
    // 设置最小高度约束保证自定义组件高度随刷新区域高度变化时自定义组件高度不会低于minHeight。
    .constraintSize({ minHeight: 32 })
    .width("100%")
  }
}