# Stage 1：CMPLiquidGlass 接入

日期：2026-09-07。状态：源码与跨端构建通过，运行/视觉验收未完成。

本记录接续 `2026-09-07-mainline-ui-probe.md`；此前“三档均为实体”的描述
只适用于旧探针版本。本轮仍是开发探针，不是最终 Home 设计。

## 交付与边界

- 固定用户 CMPLiquidGlass fork 的 `8c328fdd5032b05f0d3d8065e5cae3e2e95bd7e9`。
  `backdrop`、`shapes` 源码及 Apache-2.0 LICENSE 收在 `common/glass/vendor`，
  本地 Skiko API 兼容补丁记录在 vendor README；未导入 Demo、构建脚本或二进制。
- `common:glass` 封装背景捕获、GlassSurface 和能力降级策略；
  `common:design-system` 的 MaFeiSurface 调用该适配，不向 Feature 暴露 vendor 类型。
- 探针用滚动彩条/卡片作为捕获源，固定底部浮层作为消费者，避免自捕获。
  Full 使用模糊与折射，Reduced 关闭折射，Opaque 完全不透明。
  缺捕获句柄/模糊能力时回退 Opaque；缺 shader/lens 能力时回退 Reduced。
- 保留主题、单/双栏、对话框和命中计数检查。对话框目前仍是 Material3
  实体弹层，尚不是玻璃弹窗；未扩大到播放器、媒体卡片或完整业务 UI。
- Harmony 薄宿主补齐必填图标字段，复用旧工程图标，不修改旧参考目录。
  原 CMake abiFilters 只限制源码编译，新增 nativeLib.filter 排除依赖携带的非 ARM64 库。
- 没有修改 GLM-03 的 providers/feiniu、数据层或共享根 Gradle 配置。

## 构建证据

隔离快照：`/private/tmp/mafei-glass-validation.clmgha`。
来源为 `f33cb4f` + 本轮主线变更；GLM-03 在验证期间提交的 `30866e6`
**不在该快照中**，以下不能作为 GLM-03 集成验收。

| 验证项 | 结果 |
| --- | --- |
| common:glass Android 编译与 testDebugUnitTest | 通过，2 项测试，0 失败 |
| common:glass Desktop 编译与 desktopTest | 通过，2 项测试，0 失败 |
| composeApp assembleDebug | 通过，生成开发 debug APK |
| composeApp compileKotlinDesktop | 通过 |
| composeApp compileKotlinIosArm64 | 通过 |
| composeApp compileKotlinIosSimulatorArm64 | 通过 |
| composeApp compileKotlinOhosArm64 | 通过 |
| publishDebugBinariesToHarmonyApp | 通过，同次链接的 so/header 已发布 |
| Hvigor assembleHap | 通过，未签名开发 HAP；不具备安装验收证据 |

联合 APK/五目标编译用时 1 分 15 秒；OHOS 链接发布用时 1 分 41 秒。
快照的源产物与宿主 staging 逐文件 SHA-256 相同：

```text
libkn.so     e7afe303f5cc3dd704befebeec492c507b99f3fbb561b959bd6fc38fb4fd9253
libkn_api.h  f0aa9909c864d4b65deb968296436bbd53218222966fbb9c61ad172a8c2f9530
```

临时日志：`/private/tmp/mafei-glass-build.log`、
`/private/tmp/mafei-glass-platform-build.log`、`/private/tmp/mafei-glass-ohos-link.log`、
`/private/tmp/mafei-glass-hap.log`、`/private/tmp/mafei-glass-desktop-run.log`。
这些是本地构建证据，不是永久 CI 附件。

HAP 构建使用 DevEco 自带 Node 24.14.1 和显式
`DEVECO_SDK_HOME=/Applications/DevEco-Studio.app/Contents/sdk`。
默认 Node 20 无法加载 ohpm 的 uuid ESM 依赖；无需修改系统 Node 或 ohpm 安装。

## 未完成与风险

1. `hdc list targets` 返回 Empty；宿主没有 signingConfig。
   鸿蒙安装、首帧、返回/前后台、窗口变化、三档效果和性能全部 Pending。
2. Desktop run 已启动，日志无运行异常，但自动化工具两次无法解析 Java
   窗口（Invalid app），没有截图/点击验收，不记录为玻璃渲染通过。
3. OHOS 链接成功，但 CPF Compose 的 fusionRenderer 源码产生
   ExternalRenderNode、RenderNode、Typeface.makeFromData 等 partial linkage
   提示。当前选择 Skia，不能仅凭分支路径判断提示无害，需真机首帧及相关
   路径验证后关闭。未修改/屏蔽这些上游诊断。
4. 降级目前依据平台能力与捕获句柄；shader 编译失败、失效图层等运行时故障
   的自动熔断尚未验收。不得宣称任何运行时故障都已自动回退。
5. Android 真机、iOS 真机/模拟器 UI、Mac 视觉和跨窗口状态保留仍 Pending。
   默认 Material3 弹层与玻璃角色参数的完整产品映射也未完成。
6. 导入库使用 ContextParameters 编译实验开关，CPF 工具链有相应警告；
   开关限定在 glass 模块，后续工具链升级需重新编译验证。

下一步：完成已签名 Harmony HAP 首帧和三档切换，再推进可复用产品玻璃组件。
这份记录不宣布 Stage 1 整体通过，也不解除 GLM 工作包冻结条件。
