# 06C iOS Keychain：实现与验收

## 交付范围

- `platform:secure-storage-ios` 新增 `IosKeychainCredentialStore(service)`，对接真实 Security.framework；消费既有 `SecureCredentialStore` 与 `SecureSecretCodec`，无新第三方依赖。
- generic-password + 精确 service 命名空间；AfterFirstUnlockThisDeviceOnly；不启用 iCloud 同步、不配置共享 access group、不弹出隐式认证 UI、不降级到文件明文。
- 后台 dispatcher 执行 SecItem；`overwrite=false` 只走原子 Add；覆盖遇到 Add/Update/并发删除竞争最多重试 16 次后显式 StorageFailure，不静默丢失。
- 缺失、锁定、拒绝、损坏分别处理；返回错误不带凭证、别名或原始 native cause；Kotlin 侧编码与读取暂存字节在 finally 清零。CF Create/Copy 对象显式释放，不宣称系统内部内存能被主动擦除。
- 稳定排序、字面 prefix 筛选和逐项删除。批量删除基于别名快照，不宣称跨条目事务；同步 SecItem 已开始后取消可能观察到已提交结果，不承诺回滚。
- service 限 1–255 个 ASCII 字母/数字/点/横线/下划线；alias/prefix 严格 UTF-8、最大 1024 字节，拒绝 NUL；单次枚举上限 10000 项。这些平台限制需要装配时保持一致命名，不能把任意长 URL 作为 alias。

子 Agent 提供初版，主线合并和审查。期间主线重复新增的实现造成重名，已删除主线重复文件，仅保留一套实现。不是 CPF 编译器缺陷。

## 主线修复与 TDD 证据

发现真实 CFString 含 NUL 时，C-string 长度会截短别名，后续 delete 可能误指向同 service 下另一项。补 Native 转换回归先得到 **9 项中 1 项失败**，再加入原始 UTF-16 长度校验，损坏别名显式拒绝。

另外补充持续竞争、写入/读取临时字节擦除、RawBytes 独立副本、畸形别名拒绝等测试。没有把最初重名导致的编译失败当成行为红灯。

## 本轮实际运行

**iOS 26.5 ARM64 模拟器：**

- iPhone 17 Pro：**19/19**。
- iPad Pro 11-inch (M5)：**19/19**。
- 其中 13 项合成后端逻辑测试、2 项真实 CoreFoundation 转换、4 项真实 Security.framework/模拟器 Keychain 集成测试。
- 真实集成覆盖四类凭证跨实例读取、命名空间和 prefix 隔离、8 个并发 `overwrite=false` 仅一个成功、损坏记录不能当缺失。
- 只创建随机 `cn.mafei.keychain-tests.<UUID>` service，finally 清理自身条目；不读取、枚举或删除其他 service 的凭证。

最初裸测试程序因缺 entitlement 返回 `-34018`。单独 ad-hoc 重签与临时 App 方式又遇到模拟器安全策略拒绝。最终在**仅模拟器 TestExecutable** 中链接 `__TEXT,__entitlements`，不修改生产签名或 framework。entitlement 文件已声明为链接任务输入，变更会触发重链接。尝试期间创建的专用测试 App 已卸载，可从构建产物重建，正式应用未动。

复跑（先启动目标 ARM64 iOS 模拟器）：

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  bash scripts/test-ios-keychain.sh <simulator-UDID>
```

本轮日志：

- `/private/tmp/mafei-ios-keychain-native-red.log`：NUL 回归真实红灯。
- `/private/tmp/mafei-ios-keychain-final-runtime.log`、`/private/tmp/mafei-ios-keychain-final-iphone.log`：iPhone 19 项通过；后者使用最终复跑脚本。
- `/private/tmp/mafei-ios-keychain-ipad.log`：iPad 19 项通过，含脚本构建。
- `/private/tmp/mafei-ios-keychain-integration-build.log`：iOS device 源码与测试源码编译，以及 composeApp Android/Desktop/iOS/OHOS 编译和 verifyArchitecture。应用相关任务可能缓存命中；不等于本模块已注入生产 Composition Root。

Node 架构门禁回归 **7/7**、shell 语法与 diff-check 通过。临时日志可能被系统清理，不是永久 CI 证据。

## 未完成

1. 真机 Keychain、真实签名/entitlement、锁屏/首次解锁、重启、重装/升级和备份恢复未验收。模拟器通过不证明硬件保护行为。
2. 应用 Composition Root、SessionRepository 的真实凭证链路尚未注入本实现。
3. macOS 是 JVM Desktop，不能直接使用此 Kotlin/Native iOS 实现；macOS Keychain 与 OHOS 安全存储仍待实现。本轮未交付它们，亦未完成 GLM-06 全平台。
4. iOS/OHOS HTTP、播放器平台 runtime/Surface、旧鸿蒙 Reader/Sink 等缺口沿用平台 06 状态，Stage 1 不因此视为整体完成。

保留用户/IDE 的 `gradle.properties`、`runscript/`。仅以 Codex 身份提交本小批，未推送。
