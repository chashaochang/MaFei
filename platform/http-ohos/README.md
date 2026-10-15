# OHOS HTTP：复用 CPF Curl 的 HTTP-only 修补版

状态：**实现与 ARM64 Native/HAP 构建通过，设备运行 Pending**。入口
`createOhosHttpTransport()` 返回冻结的 `HttpTransport`，重定向、总超时、
读体上限继续复用 `platform:http-ktor`，没有新增模块、ArkTS 网络业务或更改最低 API 17。

## 为什么是局部源码补丁

- 原 CPF Curl Kotlin 引擎链接报 `curl_ws_send` / `curl_ws_meta` 缺失，而当前 API
  本来就只提供 buffered HTTP。只消费同版本 `cinterop-libcurl` KLIB，沿用其中
  libcurl/OpenSSL；不引用原 Kotlin 引擎，不添加假符号，不重编工具链或覆盖缓存。
- 复用其 HTTP 引擎源码，移除 WebSocket/SSE 代码和自动全局引擎注册。源码、许可和
  修改说明见 [third-party/NOTICE.md](third-party/NOTICE.md)。
- 已检查备选 CIO：匹配版本 Native `openTLSSession` 明确抛出
  `TLS sessions are not supported on Native platform.`，不能作为 HTTPS 替代方案。
  系统 RCP 未在本批另起实现。

## 修补范围

- native easy handle 单一清理归属，取消监听一直保留至 body 完成；延迟取消和 unpause
  核对请求实例，防止地址重用误伤新请求。
- 原生轮询显式 yield 让取消任务有机会执行；关闭先清理活动/排队请求，再退出线程。
- 原生配置失败释放已分配的 handle、StableRef、请求头链表和上传通道。
- 绑定实际 response call job：Discarded、重定向和提前终止读体能触发 native 取消，
  不依赖仍在运行的父 request job。
- 已收到 HTTP status 的截断/传输错误也传给读体 channel，不能当作完整成功响应。
  失败保留 DNS/连接/TLS 等类型，不导出原始 URL/错误链。
- 跳过 100 等临时响应，保留最终响应头；不提供媒体流/WebSocket/SSE。
- TLS 证书及主机名验证固定开启；无自签名绕过、Cookie jar、自动重定向或业务重试。
  原 libcurl 的内置 CA 路径为 `/etc/ssl/certs/cacert.pem`，与本地 SDK
  `@ohos.net.http.d.ts` 的预置 CA 文档一致，但尚未在设备内实际验证。
- Curl 自动解压且 Ktor 移除压缩/长度头；读体上限约束解压后内容。

## 本轮实际证据

- Android 宿主编译通过；共享 Desktop / Android JVM HTTP 各 **32/32 实际通过**。
- **12 项 OHOS 测试编译、ELF 链接通过，0 项设备执行**：
  7 项真实 loopback 夹具、3 项原生 handle 所有权、2 项工厂/关闭。
- 应用 `libkn.so` 链接通过；与 `libkn_api.h` 同轮发布到 harmonyApp。
- Hvigor unsigned ARM64 HAP 构建通过；`verify-harmony-artifacts.mjs` 核对
  库/头文件、strip 前后 build ID、打包字节和资源通过。
- 架构门禁、Node 架构回归 **7/7**、diff-check 通过。
- 原有 fusionRenderer 的 ExternalRenderNode/Typeface partial-linkage 警告仍保留；
  没有通过网络模块的构建宣称 UI runtime 安全。

构建命令：

```bash
./gradlew :platform:http-ohos:linkDebugTestOhosArm64 :composeApp:publishDebugBinariesToHarmonyApp
# harmonyApp 中使用已有 DevEco Hvigor assembleHap
node scripts/verify-harmony-artifacts.mjs
```

日志：`/private/tmp/mafei-ohos-http-mainline-regression.log`、
`/private/tmp/mafei-ohos-http-jvm-rerun.log`（64 项强制实际重跑）、
`/private/tmp/mafei-ohos-http-publish.log`、`/private/tmp/mafei-ohos-http-hap.log`。

## 仍需设备确认

当前 `hdc list targets` 为 Empty，HAP 未签名、未安装。待真实 OHOS 执行上述测试，
再验证受信 HTTPS、自签名/主机名不匹配拒绝、代理 CONNECT、取消/超时/关闭和截断读体；
之后接生产账号/SDK。这里的构建成功不代表真实服务器登录或 Stage 1 整体完成。
