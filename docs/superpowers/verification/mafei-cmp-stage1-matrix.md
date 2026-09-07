# MaFei 2.0 CMP Stage 1 验收矩阵

状态：S/B 分项已开始，运行与产品验收尚未通过  
关联计划：`docs/superpowers/plans/2026-09-04-mafei-cmp-2-bootstrap.md`

2026-09-07 构建实测见 [玻璃接入记录](2026-09-07-glass-integration.md)。
下表只标记该记录中已验证的分项，不代表 Stage 1 整体通过。

同日后续：[公共组件与 Mac 首帧](2026-09-07-glass-components.md)。

同日后续：[特效熔断与 Mac 分项交互](2026-09-07-glass-fallback.md)。

同日后续：[窗口滚动锚点与原生鼠标](2026-09-07-window-anchor.md)。

## 1. 证据等级

| 等级 | 含义 |
| --- | --- |
| S | 源码/静态检查 |
| B | Gradle/KLIB/CMake/Hvigor 构建 |
| E | 模拟器或桌面运行 |
| D | ARM 真机运行 |
| R | 真实 Jellyfin/飞牛服务 |
| A | 用户/产品验收 |

较低等级不能替代较高等级；例如 KLIB 编译通过不能记为 HAP 首帧通过。

## 2. 工程与依赖

| 检查项 | 目标 | 结果 | 证据 |
| --- | --- | --- | --- |
| CPF/CMP/Kotlin/Skiko 版本锁定 | 统一版本矩阵 | Pending | |
| CMPLiquidGlass fork commit/tag | 可复现来源 | S 通过 | 8c328fd，vendor README 记录本地补丁 |
| `common:glass` 只暴露产品契约 | 无 Feature 直连 Backdrop/RuntimeShader | S 通过 | GlassBackdrop/GlassSurface 包装 |
| `backdrop`/`shapes` source set | common/Android/iOS/Desktop/OHOS | B 通过 | 五目标编译 |
| x64 排除 | 无 OHOS x64、Android x86、Intel Mac | Pending | |

## 3. 构建矩阵

| 平台/目标 | 最小任务 | 结果 | 证据路径 |
| --- | --- | --- | --- |
| Android ARM64 | `:composeApp:assembleDebug` | B 通过 | 玻璃接入记录，debug APK |
| iOS ARM64 | `:composeApp:compileKotlinIosArm64` | B 通过 | 玻璃接入记录 |
| iOS Simulator ARM64 | `:composeApp:compileKotlinIosSimulatorArm64` | B 通过 | 玻璃接入记录 |
| Mac Desktop/JVM ARM64 | `:composeApp:compileKotlinDesktop` | B 通过 | 玻璃接入记录 |
| OHOS ARM64 链接 | `linkDebugSharedOhosArm64` | B 通过，警告待运行验证 | 玻璃接入记录 |
| OHOS HAP | `assembleHap`，仅 `arm64-v8a` | B 通过，未签名 | 解包核对只有 ARM64 native 库 |
| OHOS resources | rawfile Compose resources present | Pending | |

## 4. 运行矩阵

| 场景 | Android | iOS | Mac ARM | HarmonyOS ARM |
| --- | --- | --- | --- | --- |
| Compose 首帧 | Pending | Pending | E：新包 800dp 首帧已观察 | Pending |
| FullGlass Probe | Pending | Pending | E：1100dp 同屏三档视觉已观察，性能待验 | Pending |
| Reduced/Opaque 回退 | Pending | Pending | E：模拟故障实体回退、主题切换保留；真实 GPU 故障待验 | Pending |
| 返回和生命周期 | Pending | Pending | E：弹层 Escape 关闭；完整生命周期待验 | Pending |
| 窗口/方向变化 | Pending | Pending | E：430dp ↔ 1100dp 修复并验证滚动锚点、材质和计数保持；全范围待验 | Pending |
| 长列表滚动 | Pending | Pending | E：原生滚轮可达末尾，调整窗口保留样本位置；性能待验 | Pending |
| 底栏真实鼠标命中 | Pending | Pending | E：Full/Reduced 窄屏、Opaque 宽屏原生鼠标计数通过；旧熔断包宽窄屏补验通过 | Pending |
| 底部栏动画 | Pending | Pending | Pending | Pending |
| 最小 Player Surface probe | Pending | Pending | Pending | Pending |

## 5. 通过门槛

- 所有 S/B 项通过后，才允许记录 E；
- HarmonyOS 必须完成 HAP 安装、Ability 启动和 Compose 首帧，不能只记录 `libkn.so` 产出；
- Full/Reduced/Opaque 切换不能改变布局、点击区域或状态语义；
- 任一平台玻璃效果失败时必须落到实体背景，不得出现透明按钮、空白页面或崩溃；
- Stage 1 通过不代表播放器真实媒体、旧数据库迁移或 Provider SDK 已完成。
