# Stage 1 主线宿主、资源与交互回归

日期：2026-09-07。状态：本批主线实现已落地，Stage 1 整体验收未通过。

## 范围与隔离

本批完成不依赖 GLM 的宿主、Probe、安全区和验证脚本，不实现 Provider、Repository、迁移或播放器运行时，也不提前迁移完整 Home。

隔离构建目录：`/private/tmp/mafei-mainline-hosts.U3LBBd`，以 `0c499b6` 的归档加本批主线文件构建，不包含 GLM-04 半成品。工作期间根 HEAD 增加了 GLM 的 `0bf87b6`；本批不修改、不代验该提交。`gradle.properties` 与 `runscript/` 保持原状，不纳入主线提交。

## 已完成实现

- iPhone/iPad：从仅有说明文件补成真实 UIKit Scene 薄宿主、共享 Xcode scheme、ARM64 Debug/Release、手机/平板方向声明和 XCTest UI target；业务页面仍为同一套 Compose。
- 资源：显式生成 `mafei.composeapp.generated.resources.Res`，通过本地化字符串探针验证真实资源，修复 sharedUi source set 的自动生成遗漏；与 OHOS rawfile 发布路径一致。
- 安全区：共享根布局只消费一次 safeDrawing，背景延伸至窗口；Android 根据 Probe 主题更新系统栏图标。已修复 Android Manifest 入口包名错误和 iOS 缺失 `CADisableMinimumFrameDurationOnPhone` 导致的启动崩溃。
- 底栏：增加 220ms 收展动画，收展状态位于宽窄布局分支之外，列表底部留白仍按真实底栏高度计算。此项完成交互验证，不是帧率/性能验收。
- 门禁：根 `verifyArchitecture` 不再是空任务。检查 72 个模块 build 文件、CPF 版本、Skia 配置、ARM-only 宿主和入口。七个 Node 门禁测试通过，包括版本漂移和错误入口/配置的负向样本。
- HAP 校验：原始链接与暂存 libkn/header 逐字节相同；Hvigor copy-in 相同；stripped 库与原始链接 GNU Build ID 相同；HAP 内 native 库与 stripped 产物字节相同；资源路径与内容相同。去调试符号后不错误要求整个 ELF 与原始库字节一致。

## 构建与测试证据

| 检查 | 本批证据 | 边界 |
| --- | --- | --- |
| Android APK | `mafei-mainline-final-build.log`，assembleDebug 通过 | 开发包，debug 签名 |
| iOS device/simulator ARM64 编译 | 同一构建日志，两个 compileKotlin 通过 | device 只编译，不是 iPhone 真机 |
| iOS 完整宿主 | `mafei-mainline-ios-final.log`，Xcode BUILD SUCCEEDED | ARM 模拟器 App + framework + resources |
| Mac Apple Silicon 分发 | `mafei-mainline-final-build.log`，createDistributable 通过 | 最新包交互复核未通过，见下节 |
| OHOS 最终链接 | `mafei-mainline-harmony-final-link.log`，BUILD SUCCESSFUL | fusionRenderer partial-linkage 警告仍须真机判定 |
| OHOS 最终 HAP | `mafei-mainline-hap-final.log`，BUILD SUCCESSFUL | unsigned，无安装证据 |
| HAP 结构与来源 | `node scripts/verify-harmony-artifacts.mjs /private/tmp/mafei-mainline-hosts.U3LBBd` 通过 | 不等同于运行时安全/签名校验 |
| JVM 单测 | composeApp 1、glass 6、design-system 3，XML 零失败 | 最终构建 UP-TO-DATE，保留原执行时间，不算 Native 执行 |
| 静态门禁 | Node tests 7/7；verifyArchitecture、plutil、diff check 通过 | 门禁按当前声明格式检查，不是任意 Gradle 程序的架构证明 |

日志文件均在 `/private/tmp/`，不是永久 CI 归档。JVM XML 位于隔离目录对应模块 `build/test-results/desktopTest`；glass/design-system XML 原执行时间为 2026-09-07T11:51Z，composeApp 为 15:20Z。

## 运行证据（E，不是 D/R/A）

- Android：ARM64 API34 模拟器 `emulator-5554`。`mafei-android-final-smoke.log` 通过资源、收展、三档材质逐次命中 0→3、系统返回关闭弹层、模拟特效故障→Opaque、请求 Full 仍 Opaque、命中到 4、Home/前台后保持计数与熔断。每次点击重新读取 UIAutomator bounds。`mafei-android-final-frame.png` 实查深色背景白色系统栏图标与安全区。
- Android 最终扩展回归：`mafei-android-rotation-smoke.log` 全部通过，新增浅/深主题双向切换、布局树 rotation=1/0 验证、横屏 bounds 点击 3→4、竖屏保持 4，随后熔断用例命中到 5、前后台保持 5。旋转设置先保存并在 finally 中恢复。没有把窗口重建前的渲染故障状态算作跨进程恢复验证。
- iPhone 17 Pro / iPad Pro 11 M5：iOS26.5 ARM 模拟器。`mafei-ios-phone-tests-final.log` 与 `mafei-ios-pad-tests-final.log` UI 测试通过；验证资源、收展、三档材质、弹层、横屏真实 bounds 点击 3→4、返回竖屏及前后台保持 4。横屏先等待 window 宽高变化及旋转动画，再查询并点击新节点。使用可命中按钮节点而非不可命中的 Compose 文本子节点。
- 首帧：`mafei-ios-fixed-first-frame.png`（iPhone）、`mafei-ipad-first-frame.png`（iPad）实查共享页面与资源。iPad 834dp 使用单栏是 840dp 断点的预期结果，不按设备名称强行双栏。
- XCTest App 局部横屏截图出现裁剪歧义，测试附件改用 `XCUIScreen.main.screenshot()` 整屏截图。最终双设备测试 `mafei-ios-screen-tests.log` 显示两端通过；`mafei-ios-screen-tests.xcresult` 保留四张附件。实查整屏横屏图：iPhone 750dp 单栏、iPad 1210dp 双栏，均显示资源和命中 4，未沿用被裁剪的截图作视觉证据。只覆盖这两个样本尺寸，不代表所有屏幕通过。
- Mac：保留前几批已经核实的旧版本运行分项。本批新 App 分发构建通过，但存在同名旧 Probe 的 AX 定位歧义；已退出明确属于主线临时目录的旧 Probe，后续 System Events 返回辅助功能权限拒绝（-25211）。本批未把旧树当新包成功，资源/收展的最新包运行复核 Pending；未修改系统权限。
- HarmonyOS：`hdc list targets` 返回 `[Empty]`。没有安装、Ability 启动、首帧或真机交互证据。

## 可重复命令

根目录先执行轻量静态门禁；SDK 路径、JDK、DevEco Node 由本机配置，不写入凭证：

```bash
node --test scripts/verify-cmp-foundation.test.mjs
./gradlew verifyArchitecture :composeApp:assembleDebug :composeApp:createDistributable \
  :composeApp:compileKotlinIosArm64 :composeApp:compileKotlinIosSimulatorArm64 \
  :composeApp:compileKotlinOhosArm64 :composeApp:desktopTest \
  :common:glass:desktopTest :common:design-system:desktopTest
node scripts/android-probe-smoke.mjs emulator-5554
git diff --check
```

Android 脚本需要 adb 在 PATH 且最新 dev APK 已安装；显式选择测试设备，不清空 App 数据。iOS 构建/测试见 `iosApp/README.md`，OHOS 发布/打包/校验见 `harmonyApp/README.md`。本批未配置生产签名、商店发布或自动推送。

## 剩余门槛与接线次序

1. 环境而非 GLM：HarmonyOS 签名与 ARM 设备连接，随后做安装/首帧、返回、窗口和材质回退；Mac 辅助功能权限恢复后复核最新包；全尺寸、字体放大、性能和进程死亡恢复仍需专门验收。
2. GLM：Player runtime/Surface 探针、真实 Transport/安全存储，以及 GLM-04 Adapter/Repository/迁移冻结后主线接线。不得用空实现或假成功代替。
3. 阶段约束：Stage 1 未通过，不进入完整 Home/业务 Feature 迁移；也不把“无 GLM 依赖”的 UI 扩张成绕过阶段门禁的理由。

此记录说明本批完成项，不声明所有非 GLM 工作已经通过验收，更不声明项目 2.0 完成。

## 本机可运行产物

- Android dev/debug APK：`/private/tmp/mafei-mainline-hosts.U3LBBd/composeApp/build/outputs/apk/debug/composeApp-debug.apk`。
- iOS ARM 模拟器 App：`/private/tmp/mafei-mainline-ios-derived/Build/Products/Debug-iphonesimulator/MaFei.app`。
- Apple Silicon Mac App：`/private/tmp/mafei-mainline-hosts.U3LBBd/composeApp/build/compose/binaries/main/app/MaFei.app`。
- HarmonyOS unsigned HAP：`/private/tmp/mafei-mainline-hosts.U3LBBd/harmonyApp/entry/build/default/outputs/default/entry-default-unsigned.hap`。

以上均为 Probe 开发产物，不是 2.0 业务版或发布包。临时路径可能被系统清理，应按宿主 README 重建。
