# OHOS HTTP binding

状态：**阻塞，未接入生产工厂**。本轮尝试复用 RaidMate 的 CPF Curl 引擎，
通过共享 `createKtorHttpTransport(Curl, ...)` 固定 `sslVerify = true`。
Kotlin 编译通过，但 Native 测试链接真实失败：`undefined symbol: curl_ws_send`
和 `curl_ws_meta`。CPF 自带静态 libcurl 与绑定符号不匹配。
日志 `/private/tmp/mafei-native-http-compile.log`。

主线已撤去本轮试接的工厂、测试与依赖，模块保持原占位状态，不将无法链接的实现
带入应用、不填假符号、不忽略 linker 错误，也不修改用户工具链缓存。
试接代码仅是共享 transport 加 Curl 引擎的薄绑定，问题解决后不需要重新设计请求层。

## 已核对的 CPF Curl 行为

依据本机 `ktor-client-curl-ohosarm64:3.3.3-1.0.0` sources.jar（SHA 路径 `3c21a7202fcbac997b5ce0f349b1d307f5a93027`），其源码位于 jar 内 `desktopMain/io/ktor/client/engine/curl/`，不代表使用 JVM：

- TLS：`CurlClientEngineConfig.sslVerify` 默认 true；`CurlMultiApiHandler.scheduleRequest` 只有 false 时才把 `CURLOPT_SSL_VERIFYPEER/VERIFYHOST` 清零。本工厂固定 true，不覆盖 CA 或代理。实际系统根证书可用性仍需真机 HTTPS 验收。
- Redirect/Cookie/retry：每次 `curl_easy_init()`；未设置 `CURLOPT_FOLLOWLOCATION`，未启用 Cookie engine/jar，源码没有自动请求重试循环。共享层关闭 Ktor 自动重定向/插件 Cookie/重试，重定向唯一入口保持 `HttpRedirects.nextRequest`。
- **自动解压差异**：底层固定 `CURLOPT_ACCEPT_ENCODING = ""`，Curl 自动协商/解压；`CurlClientEngine` 调用 `dropCompressionHeaders` 移除压缩和 Content-Length 元数据。共享字节上限作用于解压后数据；不能沿用 CIO 的“绝不主动解压”描述。不是新增的业务解压代码。
- Streaming：`CurlHttpResponseBody.bodyChannel` 提供响应分块，绑定 executionContext；写 callback 内部用了 `runBlocking`（上游 TODO KTOR-6030）。本模块仍只暴露共享 buffered API，未提供 WebSocket/SSE 会话。
- **取消/清理待核风险**：`CurlProcessor` 监听 request executionContext completion 并调用 `cancelRequest`；但收到响应头、completionHandler 完成后会 dispose 该监听。`CurlMultiApiHandler.cancelRequest` 当前直接 remove/reset/cleanup easy handle，原 `cancelledHandles += ...` 被注释，且未同步移除 `activeHandles`/dispose holder。`close()` 又遍历 activeHandles 清理。这是源码可见的取消遗漏/重复释放风险，不能仅凭 factory 编译宣称取消安全；请主线优先运行真机取消/超时/Discarded/连续 close 用例。
- **部分读失败与错误类别**：`collectFailedResponse` 在已有非零 HTTP status 时直接返回 null；应靠共享 Content-Length/通道错误检查兜住，但 chunked/压缩流截断需真机 fixture。TLS/DNS/连接失败多以 `IllegalStateException` 文本返回，现共享分类可能保守归 Unknown，不能把这些类型的精确 Native 映射当已验。

## 编译 / 验收

主线已实际执行以下试接验证，Kotlin 编译通过，测试 ELF 链接失败；没有 OHOS 运行通过证据。

由主线统一执行：

```bash
./gradlew :platform:http-ohos:linkDebugTestOhosArm64
```

下一步先取得与绑定匹配且修复取消清理的现成 Curl 产物，或复用已有系统网络桥接；
之后再做真实 HAP/设备 strict HTTPS、跨源重定向剥凭证、取消/超时 socket 回收验收。
本模块仍为 **未实现**，不是“工厂完成只差真机”。
