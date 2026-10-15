# iOS HTTP：复用 Ktor Darwin 的薄工厂

`createIosHttpTransport(defaultTimeoutMillis = 30_000)` 只选择 Darwin 引擎并关闭
NSURLSession 的隐式 Cookie、credential storage 和缓存。超时、取消、重定向、
读体限制完全复用 `platform:http-ktor` 的 `createKtorHttpTransport`，不复制实现。
主线实际复现 NSURLSession 折叠多条 Set-Cookie，已在 iOS 薄包装恢复分界，
保留原始属性与 Expires 日期逗号，不创建 Cookie jar。

## 已核对的现成来源

- 工程使用 CPF Ktor `3.3.3-1.0.0`。
- CPF releases 的 `ktor-client-darwin` module 元数据和
  `ktor-client-darwin-iossimulatorarm64` KLIB 均实际返回 HTTP 200。
- 匹配版本源码已从同一仓库读取；`KtorNSURLSessionDelegate.URLSession(...
  willPerformHTTPRedirection...)` 明确执行 `completionHandler(null)`，禁用系统
  自动跳转，因此 302 POST 是否保留由共享重定向契约唯一决定。
- 引擎 `DarwinSession.createSession` 默认已清除 Cookie storage；本薄工厂再次显式
  禁用 session Cookie 接受、request Cookie handling、URLCredentialStorage 和 URLCache，
  不替换 Ktor delegate，不安装 Cookie/auth plugin，不改系统 TLS 信任策略。

源码证据：
`https://maven.eazytec-cloud.com/nexus/repository/maven-releases/io/ktor/ktor-client-darwin/3.3.3-1.0.0/ktor-client-darwin-3.3.3-1.0.0-sources.jar`

本机下载的同版本参考：`/private/tmp/mafei-ktor-darwin-3.3.3-1.0.0-sources.jar`。
没有拷贝第三方引擎源码，也没有新建公共 testkit。

## 局部测试

`IosHttpTransportLoopbackTest` 使用 iOS Native 的实际 POSIX loopback socket 和
NSURLSession，局部 fixture 限定 localhost、随机端口、有限读超时；不接真实服务器。
覆盖：200/401、302 POST/body 保留、DoNotFollow、拒绝超限、总超时与调用者取消区分、
响应 Cookie 不隐式发送到下一请求、多条 Set-Cookie（含 Expires 逗号）分别保留。

2026-09-08 主线实际验证命令：

```bash
./gradlew :platform:http-ios:compileKotlinIosArm64 \
  :platform:http-ios:linkDebugTestIosSimulatorArm64
xcrun simctl spawn A6D901FE-EBE0-44A0-A39D-64BCCBF85E38 \
  "$PWD/platform/http-ios/build/bin/iosSimulatorArm64/debugTest/test.kexe"
```

验收：iPhone 17 Pro / iOS 26.0 ARM 模拟器 **7/7 实际通过**；iOS 设备目标编译通过。
多 Cookie 回归先真实出现 1 个失败，修复后 7/7 通过。
共享 Android JVM / Desktop 回归各 **32/32**，verifyArchitecture、diff-check 通过。
日志：`/private/tmp/mafei-native-http-final-build.log`、`/private/tmp/mafei-ios-http-final.log`；
红灯：`/private/tmp/mafei-ios-http-cookie-red.log`。

尚未验证真实服务器、真机 HTTPS/系统代理/网络切换、生产应用账号装配；
本次是 Native loopback 通过，不是整个 iOS 业务或 Stage 1 完成。

遵守 harmony-kmp-cmp 技能的模块/薄宿主边界：本批只修改 `platform/http-ios/**`，
不改 OHOS/ArkTS/HAP、共享目录、TLS 信任或其他平台代码。
