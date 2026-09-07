# Stage 1 公共玻璃组件与 Mac 首帧

日期：2026-09-07。接续 `2026-09-07-glass-integration.md`。

## 本轮范围

- 按已确认 Glass 设计实现 `AppGlassCard`、`AppGlassBar`、`AppGlassDialog`。
  都位于 `common:design-system`；没有更改 GLM Provider/Data/Player 模块。
- 新增纯 Kotlin `MaFeiGlassDefaults`，统一角色的 tint、圆角和减少透明度策略。
  `GlassSurfaceSpec` 增加有默认值的 `cornerRadiusDp`，三档材质不改变几何参数。
- Card/Bar 消费 Shell 提供的背景句柄；Feature 不接触底层 Backdrop 类型。
- Dialog 使用标准 Compose 模态窗口，保留返回/焦点/关闭机制。它明确不接收父窗口
  Backdrop，当前所有档位均实体回退；跨窗口背景捕获尚未验证，不宣称玻璃弹窗完成。
- 明确提供 onSurface 内容颜色，避免脱离外层 Surface 的深色弹层沿用错误文字颜色。
- Probe 新增 Full/Reduced/Opaque 同屏卡片和减少透明度开关；共用一次独立彩条捕获。
  底部栏改用公共组件，列表底部留白跟随实测栏高；命中计数提升到页面保存状态。
- 本轮不是 Home 重设计、业务迁移或 Player probe 实现。

## 验证

隔离快照 `/private/tmp/mafei-glass-components.GZcvBQ`：基于 `1ed5146` 加本轮
UI 源码。不包含 GLM 同期对 30866e6 的未提交修复；不能用这里的结果冻结 GLM-03。

先写三项 defaults 契约测试，首次因接口尚不存在而编译失败；实现后通过：

- 每个角色/每档请求都遵循减少透明度开关。
- 切换材质不改变圆角，tint 保持有效范围。
- 未强制回退时保留调用方的角色/材质选择。

最终联合验证（23 秒）通过：

```text
:composeApp:assembleDebug
:composeApp:createDistributable
:composeApp:compileKotlinIosArm64
:composeApp:compileKotlinIosSimulatorArm64
:composeApp:compileKotlinOhosArm64
:common:design-system:desktopTest          3 项，0 失败
:common:design-system:testDebugUnitTest    3 项，0 失败
:common:glass:desktopTest                  2 项，0 失败
verifyArchitecture
```

OHOS `publishDebugBinariesToHarmonyApp` 通过（1 分 24 秒），Hvigor `assembleHap`
通过（7 秒）。HAP 解包核对只包含 `libs/arm64-v8a` 的 kn/entry/skikobridge/c++ 库。
CPF fusionRenderer partial linkage 提示仍存在，见前一轮记录；本轮没有屏蔽或
将其判为无害。签名、安装和真机运行仍 Pending。

临时日志：

```text
/private/tmp/mafei-glass-components-red.log
/private/tmp/mafei-glass-components-green.log
/private/tmp/mafei-glass-components-final.log
/private/tmp/mafei-glass-components-ohos.log
/private/tmp/mafei-glass-components-hap.log
```

## Mac 运行证据

`createDistributable` 产出独立 `MaFei.app`，可执行文件经 file 核对为 ARM64。
通过应用路径成功打开，窗口检查工具返回真实可访问性树和截图：

- 窗口标题 MaFei，800dp 单栏。
- 显示界面实验室、材质控制区、减少透明度开关和底部玻璃栏。
- 首帧文字可见；浮层显示“请求 Full → 能力档位 Full”。该标签是能力策略结果，
  不是 shader 性能或视觉质量通过的证据。
- 可访问性树包含三张对照卡和命中按钮，但首帧未完整显示同屏对照区域，
  因而没有记录三档视觉对比通过。

本轮只确认 Mac 首帧；没有完成滚动/主题/模态关闭/键盘/窗口缩放的交互验收。
没有使用估算坐标点击，也没有据此推断命中 Bug。

## 本地产物（临时目录）

- Mac：`/private/tmp/mafei-glass-components.GZcvBQ/composeApp/build/compose/binaries/main/app/MaFei.app`
- Android：`/private/tmp/mafei-glass-components.GZcvBQ/composeApp/build/outputs/apk/debug/composeApp-debug.apk`
- Harmony：`/private/tmp/mafei-glass-components.GZcvBQ/harmonyApp/entry/build/default/outputs/default/entry-default-unsigned.hap`

Android 是开发 debug 包，Mac 是本机开发 app，Harmony 未签名；均非正式发布产物。

## 剩余门槛

hdc 和 adb 均没有连接设备。Android/iOS/Harmony 首帧、Mac 完整交互、三档视觉、
大字体/高对比度、滚动性能、Shader 故障熔断、Player 测试帧仍 Pending。
减少透明度当前是 UI 开关，尚未接各平台系统偏好；不能据此宣布无障碍适配完成。
Stage 1 仍未整体通过，不能开始完整 Home 迁移。
