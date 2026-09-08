# 06D/06E Mac 与 OHOS 安全存储集成

后续网络增量：iOS Darwin 薄绑定已在 iPhone ARM 模拟器通过 7 项实际 loopback 测试，
共享 JVM 双目标各 32 项通过，见 [iOS HTTP](../../../platform/http-ios/README.md)。
OHOS 后续已复用 CPF Curl 的 HTTP-only 源码修补版，保留原 cinterop/libcurl，
消除缺失符号并修补取消与资源清理。12 项 OHOS 测试仅编译链接，unsigned ARM64 HAP
构建及 artifact 门禁通过；无设备，运行仍 Pending，见 [OHOS HTTP](../../../platform/http-ohos/README.md)。
下文保留安全存储批次当时的历史状态。

本轮并行分工：子 Agent 实现 Mac JVM/JNI Keychain；主线实现 OHOS AssetStore、补 Mac 独立隐私回归、审查与集成。没有进入业务 UI 重写，也没有更改旧鸿蒙参考目录。

## Mac：Implemented，系统正向验收 Pending

入口 `MacOSSecureCredentialStore(namespace = "default")`，JNI → Security.framework Data Protection Keychain；本模块构建生成 ARM64 dylib 并打入 Desktop JAR。无新第三方依赖、CLI 凭证参数、明文或 legacy Keychain 回退。

- 主线新增异常隐私测试，先复现包装异常携带未脱敏 cause，子 Agent 修复为保留分类但不带原始 cause。红灯 `/private/tmp/mafei-macos-keychain-review-red.log`。
- 主线独立最终执行 **10/10**：8 项合成后端契约 + 1 项真实 JNI 所有入口的拒绝范围/返回形状检查 + 1 项隐私回归。JNI 检查在进入 SecItem 前拒绝外部 service，**不是**系统 Keychain 正向 CRUD。
- `/private/tmp/mafei-macos-keychain-independent.log`：最终 dylib 重新编译、Desktop JAR 重新打包、10 项测试实际执行。Mach-O 为 arm64，install_name 已由绝对构建路径改为 `@rpath/libmafei_keychain.dylib`；JAR 内有 `native/macos-arm64/libmafei_keychain.dylib`。
- 子 Agent 真实探针首次 Add 得到 **-34018 / MissingEntitlement → AccessDenied**，没有创建凭证。当前普通 Gradle/JVM 宿主不满足权限条件，未重签用户 JDK、修改 Keychain 设置或放宽保护方式。
- 待正确签名且有 Keychain entitlement 的 Apple Silicon 应用宿主验证成功 CRUD、锁屏/重启等；hardened runtime 下的 dylib 签名/library validation 和 Composition Root 也未完成。

细节与复跑方式见 [Mac 模块 README](../../../platform/secure-storage-macos/README.md)。

## OHOS：短凭证实现已编译/链接，运行 Pending

- `OhosAssetCredentialStore` 直接使用 CPF 内置 AssetStore C API，未新增 ArkTS 页面或手写 N-API；遵守薄宿主边界。
- **MFS1 编码后最大 1024 字节**。超出 AssetStore 容量不截断、不覆盖旧值、不明文降级。长 Cookie/JWT 后端未完成，不能冻结为全容量通用存储。
- 15 项 commonTest 和 3 项真实 API fixture **仅编译/链接通过**；18 项均未运行。当前 `hdc list targets` 为 `[Empty]`。
- 模块测试 ELF 依赖 `libasset_ndk.z.so`，包含真实 OH_Asset Add/Query/Update/Remove 绑定；不是只链接了合成后端。
- `composeApp:linkDebugSharedOhosArm64` 成功。原有 fusionRenderer partial-linkage 警告保留；没有新增 HAP/设备/首帧证据，也没有把新 so 单独发布到宿主导致 header 混配。

详细容量、并发、命名空间与设备门槛见 [06E](2026-09-08-ohos-asset-06e.md)。OHOS 短凭证小批提交 `a8b1236`。

## 总体门禁与剩余工作

`/private/tmp/mafei-06de-mainline-verification.log`：composeApp Android、Mac Desktop、iOS device/simulator ARM64、OHOS ARM64 编译门禁和 verifyArchitecture 通过。Android/iOS/OHOS 部分任务缓存命中；Mac Desktop 实际重新编译。Node 架构回归 **7/7**、diff-check 通过。临时日志不是永久 CI 证据。

仍待：Mac 已签名宿主真实存储、OHOS 长凭证与应用身份内的系统存储验证、iOS/OHOS HTTP、生产 Composition Root、播放器平台 runtime/Surface、旧库 Reader/Sink。Stage 1 和 GLM-06 **没有整体完成**。

本批均以 Codex Author/Committer 提交，不推送。原有 `gradle.properties` 和 `runscript/` 未纳入，旧数据/系统 Keychain 设置未动。
