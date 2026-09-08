# Codex 06B：Android 安全凭证存储

主线终审（2026-09-08）：已审读生产密钥来源、AAD/路径边界、跨实例锁、原子替换、取消及缺密钥行为；独立运行 `/private/tmp/mafei-06b-independent.log`，Android JVM 14 项实际通过，生产源码/AAR 构建门禁通过（构建部分缓存命中）。接受源码/加密持久层小批，仍不把合成 JCE 密钥当 AndroidKeyStore 设备验收。

## 本包范围

- `platform/secure-storage-android/src/androidMain/.../AndroidSecureCredentialStore.kt`
- `platform/secure-storage-android/src/androidMain/.../EncryptedCredentialFiles.kt`
- `platform/secure-storage-android/src/androidUnitTest/.../EncryptedCredentialFilesTest.kt`

只修改这些源码、测试和本说明。共享依赖由主线增加；没有改 Gradle、宿主、core、其他平台或用户/IDE 改动，没有自行提交。

## 生产接口及安全边界

```kotlin
val credentials: SecureCredentialStore = AndroidSecureCredentialStore(
    context = applicationContext,
    namespace = "cn.mafei.credentials.v1", // 默认值；升级时保持稳定
)
```

生产构造器只有 Context 与稳定 namespace，不能注入原始密钥、外部目录或测试后端。

- AndroidKeyStore 非导出 AES-256 密钥，限 ENCRYPT/DECRYPT、GCM、NoPadding；`randomizedEncryptionRequired=true`，每次加密的 96-bit nonce 由 Cipher/provider 生成，不自选固定 IV。
- 不承诺所有设备均硬件/StrongBox 支持。没有软件密钥/明文 fallback，也没有默认合成密钥。普通后台会话存储不附加每操作生物识别提示。
- 文件只放 `Context.applicationContext.noBackupFilesDir/mafei-secure/<SHA256(namespace)>/`。凭证文件名为 SHA256(alias) + `.mfc`，不受 alias 中 `/`、Unicode 等路径字符影响。
- MFE1 记录：magic、长度前缀 UTF-8 alias（非秘密）、nonce、密文 + 128-bit GCM tag。AAD 严格绑定长度前缀 namespace 和完整记录头（包含真实 alias），抵御 alias/namespace 替换。
- `SecureSecretCodec` 是加密前的有界版本化编码，不是加密本身。编码后的明文临时 ByteArray 在 finally 中清零；磁盘临时文件只写密文。不记录秘密日志。
- 正文上限来自 codec 的 1 MiB；alias 严格 UTF-8、上限 16 KiB。读取先在已打开文件描述符上验证长度再分配；截断、增长、版本、非法 UTF-8、tag/codec 错误均不是“缺失”。
- 发现存量凭证文件时密钥缺失会 `BackendUnavailable`，不会重建新密钥覆盖仍可恢复的密文；需要用户重新认证/显式清理。

## 写序、取消、崩溃语义

- canonical-directory 全局 ConcurrentHashMap Mutex 为同进程多实例提供同一把锁，不在空闲时删除锁条目以免 waiter 竞态；每 namespace 通常只有一个目录。
- `.lock` 文件锁进一步串行化遵守协议的同应用多进程，使用 tryLock + 可取消 delay，不忙等。
- `overwrite=false` 的存在检查与保存提交处于同一目录 Mutex 和 OS 文件锁内；并发多实例只允许一个成功。
- 先完整写密文临时文件并 `fd.sync()`，再同目录 `Os.rename` 原子替换，最后打开目录并 `Os.fsync()`；失败不退回非原子的 delete-then-write。
- 取消等锁/加密后提交前会原样传播 CancellationException，并清理本次临时文件。协程栈恢复可能复制 CancellationException，本实现不将其包装成 CredentialAccessException。
- 原子 rename 是最后取消检查后的同步提交点；与 OS 提交同时发生的取消不能被当作已回滚。rename 后目录 fsync 失败可意味着文件已替换但持久性未知，调用方按可恢复重试处理。
- 单个 alias 的替换原子，不承诺 deleteAll/多 alias 的崩溃事务。deleteAll 先验证记录，再逐项删除并同步目录；中途取消/失败允许已完成的前缀删除，重试幂等。
- 进程被杀可留下密文 `.tmp` 孤儿，恢复/列表不将其当凭证，也不做无关文件的广泛清理。Known-alias delete 可以删除损坏记录；list/deleteAll 遇损坏明确失败，不假装空库成功。

## 错误分类

| 类别 | 映射 |
| --- | --- |
| overwrite=false 目标已存在 | AlreadyExists |
| 用户未授权/认证要求、SecurityException、Android errno EACCES/EPERM | AccessDenied |
| 缺失/失效 Keystore 密钥、Keystore/provider 不可用 | BackendUnavailable |
| 缺算法/provider/padding、不可支持的算法参数 | Unsupported |
| tag、损坏/超限/非法编码、文件 I/O 和原子替换失败 | StorageFailure |
| 未分类 Exception | Unknown |

CancellationException 不转换；错误顶层消息仅包含泛化操作/分类，不含凭证内容。

## 验证与证据

按 android-cli 技能读取了当前 Android 官方 Keystore 指南（`android docs search/fetch`，资料地址 [Android Keystore system](https://developer.android.com/privacy-and-security/keystore)），落实非导出/provider 限制与避开主线程密码运算。CLI 首次文档索引初始化需本机缓存权限；没有因此修改项目配置。

- `/private/tmp/mafei-06b-android-red.log`：先落测试，未实现符号导致编译红灯；不冒充行为断言红灯。
- `/private/tmp/mafei-06b-android-green.log`：Android 生产源码编译、Debug AAR 打包、Android JVM 单测 **14/14 实际执行通过**、verifyArchitecture 通过。
- `git diff --check` 通过。

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew \
  :platform:secure-storage-android:testDebugUnitTest \
  :platform:secure-storage-android:assembleDebug verifyArchitecture --console=plain
```

测试覆盖：四种 SecureSecret、无明文、nonce 变化/重开读取、12 并发实例 overwrite=false 单赢家、tag/截断/错密钥、alias + namespace 替换（含改头后仍被 AAD 拒绝）、前缀稳定列举和删除、替换失败保持旧文件、提交前/排队取消、缺密钥不重建、精准错误及取消、损坏列举、符号链接拒绝、超限输入与删除后保留密钥保护。

## 仍为 Pending

JVM 测试仅经 internal seam 注入合成 JCE 测试密钥，以及 JVM 文件原子 rename；**没有运行 AndroidKeyStore、Android Os.rename/fsync、真实 noBackupFilesDir 或真实设备恢复测试**。生产代码已编译，不能替代真机验收。

主线集成需要：Android application 组装注入、在真机上执行锁屏/重启/卸载重装/密钥缺失与存储失败验收、核验设备 Keystore 安全级别（若产品要求）、异常后的重认证 UX。Android runtime 不参与 iOS/OHOS/macOS 构建，也没有写那些平台的实现。
