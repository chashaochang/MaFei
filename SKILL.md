---
name: mafei-liquid-glass-ui
description: MaFei 液态玻璃材质、背景采样与组件接入规范。涉及玻璃组件、材质降级、采样层级或渲染性能时使用。
---

# MaFei 液态玻璃接入规范

## 适用范围

本 Skill 仅负责液态玻璃的材质、采样、组件接入、降级、性能与专项验收。全局产品风格、主动设计职责、普通卡片交互和空状态规范统一见 [`AGENTS.md`](AGENTS.md)，不在此重复定义。

液态玻璃负责承载悬浮控制与层级关系，不负责把所有内容装进透明圆角矩形。

## 液态玻璃定义

液态玻璃必须同时具备：

- 背后内容的实时采样，滚动和动画时持续更新。
- Blur、Lens、Vibrancy、Highlight、Shadow 等有节制的光学组合。
- 与组件角色一致的形状、尺寸、层级和透明度。
- 按压、拖动、选择、合并或收缩时连续的材质与几何变化。
- 在复杂封面和文字背景上仍然可读。

只有 `alpha + blur + border` 的容器叫半透明表面，不叫液态玻璃。

## 开始开发前

1. 根据已确定的页面结构，明确本轮哪些悬浮表面需要玻璃、承担什么角色。
2. 找到最接近的已验证玻璃组件，复用完整结构和动效，不能只抄背景色。
3. 明确背景采样源、记录节点和所有玻璃消费者的层级关系。
4. 核对半透明、高斯模糊和液态玻璃三种材质，以及无有效采样源时的降级路径。

参考实现：

- `/Users/machunjiang/AndroidStudioProjects/AndroidLiquidGlass/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components`
- `/Users/machunjiang/AndroidStudioProjects/AndroidLiquidGlass/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/destinations`
- MaFei vendor 底层：`common/glass/vendor/liquidglass`

## 金标准规则

第一次接入交互玻璃组件时，必须与 AndroidLiquidGlass 的参考实现行为一致，不能先做产品化调参。

首轮只允许修改：

- package 和 import。
- 将 MaFei 的 `GlassBackdrop` 解包为 vendor `Backdrop`。
- 图标、文字和点击回调等内容插槽。

首轮禁止修改：

- blur、lens、vibrancy、色散、高光、阴影和内阴影参数。
- 尺寸、padding、形状、按压缩放、spring 和可见性阈值。
- 手势、拖动、速度形变和 shader 预热逻辑。
- 把多个参考源文件合并成一个产品文件。

组件迁移完成后必须逐文件执行 `diff -u`。除上述允许项外存在差异时，不得开始页面接入。

## 正确的背景层级

背景记录节点与玻璃消费者必须是兄弟节点：

```kotlin
val backdrop = rememberGlassBackdrop()

Box(Modifier.fillMaxSize()) {
    Box(
        Modifier
            .fillMaxSize()
            .captureGlassBackdrop(backdrop),
    ) {
        PageBackgroundAndScrollableContent()
    }

    PlayerOverlay(backdrop, Modifier.align(Alignment.BottomCenter))
    LiquidNavigation(backdrop, Modifier.align(Alignment.BottomCenter))
}
```

禁止让记录节点包含消费同一 backdrop 的组件，否则可能形成自采样递归：

```kotlin
// 错误
Box(Modifier.captureGlassBackdrop(backdrop)) {
    PageContent()
    GlassSurface(backdrop, ...)
}
```

一条路由通常只需要一个页面级 backdrop。Slider、Toggle、Bottom Tabs 等需要折射自身轨道时，再建立局部 backdrop，并使用 combined backdrop。

## 材质不改变信息架构

先设计内容，再决定材质：

- Hero 直接展示真实封面或剧照，不放进装饰卡片。
- 海报列表和网格通常不需要玻璃外壳。
- 页面 section 是留白、标题和内容节奏，不是一个个浮动卡片。
- 错误、空状态、加载状态应服务于任务，不能都做成相同的圆角面板。
- 手机底部导航、宽屏侧栏、Mini Player、播放控制和模态浮层才是玻璃重点。
- 不允许卡片套卡片，不允许整页只有同一种圆角和同一种透明度。

## MaFei 现有 API

### OHOS Skia 完整效果

OHOS 保持 `rendererBackend=skia`。vendor 的 `Platform.kt` 中 RenderEffect、RuntimeShader 和 GraphicsLayerEffects 三项能力均启用；最后一项控制内阴影的 clear 遮罩、外阴影的 alpha/offset/blendMode 与高光的混合模式，不能沿用早期 Fusion Renderer 的兼容关闭状态。

这些路径已在 Glass Demo 的 Mate 80 上做过针对性交互验证，不代表 MaFei 所有页面已完成验收。接入仍须保持 sibling 采样，检查浅色/深色与按压/拖动状态；独立窗口缺少有效 backdrop 时的实体降级继续保留。更换 renderer 或 CMP/Skiko 版本后必须重新验证，不得直接将 Skia 的结论套用于 Fusion Renderer。

### `rememberGlassBackdrop` / `captureGlassBackdrop`

用于路由级实时背景采样。采样节点只包含希望被玻璃看到的背景与内容。

### `GlassSurface`

它是被动材质底座，不是万能 UI 组件。适合静态悬浮面板；不自动提供液态按压、拖动、选中水滴或组件之间的融合动画。

### `AppGlassCard`

仅用于确实悬浮且需要与背景分层的单个内容单元。它不是页面 section 的默认容器，禁止在列表、设置项和网格中批量使用。

### `AppGlassBar`

适合静态工具栏。需要 Bottom Tabs 选中水滴、拖动切换或 Mini Player 合并动画时，应使用专门交互组件，不能只放一个 `Row`。

### `AppGlassDialog`

当前普通 `Dialog` 没有父窗口 backdrop，只能可靠降级为不透明材质。不得把不透明 Dialog 宣称为完整液态玻璃。需要真玻璃时，应在同一 Compose 层级实现模态遮罩和 sibling overlay，并完成返回、焦点和可访问性验证。

## 组件用法

### Liquid Button

用于悬浮主操作、播放器控制和圆形工具按钮。必须包含：

- backdrop 折射与高光。
- 按压时连续的形变和内阴影。
- 至少 44dp 命中区（与 iOS 44pt 的触控标准一致）。可见材质可以小于命中区，但不得小于 44dp。
- 明确的图标或命令，不用玻璃包装普通正文按钮。

### Toggle

轨道记录为局部 backdrop，thumb 消费页面与轨道的 combined backdrop。状态通过 lambda 读取最新值，拖动和点击都能完成切换。

### Slider

轨道与 thumb 的视觉和手势必须是同一套进度。支持点击轨道、拖动、RTL 和合理的 `visibilityThreshold`。不要在列表每一项使用高成本色散 Slider。

### Bottom Tabs

必须复用完整结构：外层玻璃容器、隐藏的选中内容 recorder、combined backdrop 选中水滴、按压高光、阴影、内阴影和拖动动画。

禁止：

- 用静态 `MaFeiSurface + Row` 冒充 Liquid Bottom Tabs。
- 只给选中图标加半透明胶囊背景。
- 给整个容器额外 clip，裁掉按压水滴。
- 删除 `InnerShadowModifier` 的 paint blur；这会产生黑硬边。
- 只复制组件文件而漏掉 Shadow、InnerShadow、RuntimeShader 和平台实现。

### Mini Player

播放器是连续变形的同一个组件，不是两个 Composable 在阈值处切换。位置、宽度、高度、内容显隐和底栏占位由同一个稳定进度驱动。滚动进度直接来自稳定 scroll distance，避免再叠一层追赶 tween 造成抖动。

### Dialog / Sheet

先保证关闭、返回、焦点、键盘和滚动，再做材质。操作区与正文层级明确；不要把标题、正文、按钮分别套玻璃卡片。

确认/提示弹窗已有标准实现，必须直接复用，不能重新设计：

- 页面入口：`common/ui/.../MaFeiGlassConfirmDialog.kt`，只承载同层模态行为和产品文案。
- 唯一展示实现：`common/glass/.../components/LiquidAlertDialog.kt`，参考源为 AndroidLiquidGlass 的 `destinations/DialogContent.kt`。保留参考的布局、圆角、内边距、等宽胶囊操作、光学参数及按压反馈；不要另造居中标题/分隔线式 Alert 或 Material 右下角文字按钮式 Alert。
- 同层宿主必须传入真实、持续更新的页面 backdrop，不能传空采样层或无条件强制不透明。独立系统窗口不能跨窗口采样，复用同一展示实现并显式降级为实体材质，不称其为完整液态玻璃。
- 用户选择半透明或高斯模糊时，仅按风格设置降级材质；布局和事件保持一致。删除等危险操作只调整语义色和文案，不创建另一套弹窗。
- 验收除关闭/确认行为外，还须检查实时背景采样，以及手机/宽屏、深浅色、按压状态。复用了名为 `Glass` 的宿主不代表已接入标准组件。

## 材质参数原则

- Navigation：中等 blur 和 lens，低到中等 tint，背景仍然可辨认。
- Player overlay：允许更明显的 lens 与 highlight，但正文区域保持可读。
- Dialog：更强 blur、更高 tint，避免背景干扰阅读。
- Card：默认不用玻璃；确需悬浮时使用弱 lens，不能与导航同强度。
- Interactive droplet：色散只用于小面积焦点，不用于大面积面板和重复列表。
- 浅色和深色必须分别调试，不可简单反转颜色。

## 玻璃接入审查

仅检查本次变更涉及的玻璃组件与采样链路：

1. `MaFeiAdaptiveShell` 的 Bottom Navigation 当前是否只是静态 `MaFeiSurface + Row`。
2. `HomeScreen` 是否大量直接使用 Material3 `Surface`、`Card`、`FilterChip`，导致产品视觉被默认组件主导。
3. 页面是否把 `AppGlassCard` 当 section 容器批量铺设。
4. 玻璃装饰是否遮挡实际媒体内容、影响文字可读性。
5. 播放器、底栏、按钮是否拥有真实按压和拖动材质，而非透明背景。
6. 同一屏幕是否出现过多相同圆角、描边和卡片嵌套。

先验证本次涉及的代表性玻璃表面，再推广同一接入方式。专项审查不能成为重做无关页面或定义全局 UI 风格的理由。

## 性能

- 路由级 backdrop 尽量复用，不能每个卡片各录一层全屏背景。
- 大 blur、色散和 depth effect 不用于重复列表项。
- backdrop、shader 和动画控制器必须 `remember`。
- 首次按压卡顿时只预热昂贵 shader，不得改变 idle 外观。
- 滚动时观察背景是否每帧更新，不能用静态截图伪装玻璃。
- 完整阴影/高光仅在尺寸、轮廓或材质参数变化时重新录制装饰图层；该复用不适用于实时 backdrop。自定义高光 Shader 可能在绘制时读取动画状态，必须保留逐帧更新。透明度为零的 SrcOver/Plus 装饰跳过绘制，不得对 Clear/DstIn 等破坏性混合模式套用此规则。
- 玻璃效果恢复必须在业务首页做滚动回归；Demo 无崩溃与模块编译通过不代表整页性能通过。前后对比应使用一致的 Kotlin/Native 构建模式，不能用 Native Debug 与之前 Native Release 的结果直接比较。

## 验收门禁

每个代表性页面至少验证：

1. 手机竖屏与平板/PC 宽屏布局。
2. 浅色与深色。
3. 静止、按住、拖动、释放和快速重复点击。
4. 内容在玻璃后滚动时实时更新。
5. Hero、导航、Mini Player、Dialog 的层级清楚。
6. 无黑硬边、递归采样、裁切、彩边污染、首次按压卡顿或滚动抖动。
7. 截图与参考实现并排检查，不能只凭代码和编译结果判断。

只有完成代表性页面真机/模拟器视觉验收后，才能把同一模式推广到其他 Feature。
