# Codex 06：共享凭证编码与 HTTP 异常隐私

## 共享模块

`platform:secure-storage-core` 提供 `SecureSecretCodec`，平台实现通过 implementation 使用，不把凭证注册进普通 JSON 序列化。

- MFS1 固定版本：4-byte magic + 1-byte 类型 + 每字段 BE32 字节长度。
- AuthToken、Password、CookieText、RawBytes 全部可逆；保留空值、Unicode、NUL，不 trim/猜类型/宽容替换。
- 整体上限 1 MiB，包含头部与字段；未知版本/类型、非法 UTF-8、截断、溢出长度、尾随数据严格拒绝。
- 编解码错误仅给固定 StorageFailure 消息，不带输入或异常 cause；临时明文 ByteArray 在 finally 清零。Kotlin 不可变 String/运行时复制不能承诺全内存擦除。
- 这只是安全存储内部编码，**不是加密**；只能输入 Keystore 加密/Keychain 等安全边界，不可落普通设置、日志或备份。各平台访问策略和加密由实际实现保证。

## 验证

初始 `/private/tmp/mafei-secure-codec-red.log` 为缺实现符号的编译红灯（不是行为断言红灯）。实现后 `/private/tmp/mafei-secure-codec-green.log`：Desktop/Android 各 11 项实际通过，iOS/OHOS common 实现编译实际执行通过。

后续 `/private/tmp/mafei-security-mainline-green.log` 联合回归仍通过；`/private/tmp/mafei-06b-independent.log` 独立复验 Android 存储各 14 项和 AAR，codec 校验命中缓存。不以 Native 编译或 JVM 合成密钥代替真实安全存储运行。

## HTTP 异常日志收紧

主线新增合成签名 URL 回归，在 `/private/tmp/mafei-http-privacy-red.log` 中实际 1 项断言失败，证明内置异常文本直接带出 URL 的路径和 query。修正 `core:http-contract` 内置超时、HTTP 状态、重定向限制、不安全重定向和读体超限错误文案：仅保留分类、状态、计数，不含 URL。

此修改不改请求/错误类型、结构化 URL/response 属性、超时/重定向/读体语义。消费者仍应按类型和字段判断，不解析错误文案。显式 URL/response 属性、自定义 message/cause 仍应视为敏感；日志使用 redactor，不能宣称所有对象的 toString 都安全。

`/private/tmp/mafei-security-mainline-green.log` 记录 HTTP contract 双 JVM 全量回归和架构门禁通过。

## 平台边界

- Android 实际实现见独立 06B 说明；本模块验证不是 AndroidKeyStore 实测。
- iOS 模块已接共享编码依赖，但 Keychain 实现/运行验收另批处理。
- macOS JVM Keychain 需要原生桥接；OHOS 安全存储桥接未实现。不提供明文或内存假持久化兜底。
