# 主线 UI 首个切片

日期：2026-09-07

本轮交付 Stage 1 交互验证入口：深浅主题、840dp 单/双栏切换、独立滚动控制区、长列表、弹层、材质请求切换。公共容器在 `common/design-system` 中复用。

三档请求当前均显示实体背景。CMPLiquidGlass Backdrop 尚未接入，界面明确展示实际回退状态；本轮不作为 FullGlass/ReducedGlass 验收。`MaFeiSurface` 的回退已改为完全不透明，避免把透明填色当作玻璃实现。

## 并行边界

- 主线本轮只修改 `composeApp`、`common/design-system` 和本记录。
- GLM-03 继续按工作单实现飞牛 SDK；后续 GLM-04 仍等待 GLM-03 冻结。
- 页面不消费 Provider DTO，不模拟登录或播放成功。

## 验证

冻结基线 `598d1de` + 本轮三个 UI 源文件的临时快照，以下任务联合执行成功（44 秒）：

```text
:composeApp:compileDebugKotlinAndroid
:composeApp:compileKotlinDesktop
:composeApp:compileKotlinIosArm64
:composeApp:compileKotlinOhosArm64
```

日志：`/private/tmp/mafei-ui-isolated-build.log`（临时文件，不是仓库持久附件）。

共享工作区整体验证曾遇到 GLM 正在编辑的 `FeiniuDigest.kt` 类型错误，以及历史不同 Konan 路径产生的重复 KLIB；没有修改 GLM 文件或清理其构建目录。隔离快照用于验证本轮 UI，不能代表正在开发的 GLM-03 通过。

模拟器/桌面视觉、HAP、真机、旋转恢复和真实玻璃均 Pending。840dp 是本验证页的双栏阈值，尚不是产品全部窗口断点的最终定义。下一步按 bootstrap 计划固定 Glass fork 并接入真实 Backdrop，再完成各端启动与材质验收。
