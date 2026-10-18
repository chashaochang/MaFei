# OHOS HTTP：复用 CPF Curl 的 HTTP-only 修补版

状态：**实现与 ARM64 Native/HAP 构建通过，AAAA-only Jellyfin 登录真机通过**。入口
`createOhosHttpTransport()` 返回冻结的 `HttpTransport`，重定向、总超时、
读体上限继续复用 `platform:http-ktor`，没有新增模块、ArkTS 网络业务或更改最低 API 17。

## AAAA-only 域名解析回退

部分 OHOS libc 默认 `AF_UNSPEC` 查询会拒绝只有 AAAA 记录的域名，显式
`AF_INET6` 查询可成功。Curl 仅在主机 DNS 失败、HTTP 状态和已发送请求字节均
为 0 时，对原 easy handle 尝试 IPv6 一次。保留原 URL、端口、Host、TLS/SNI、
未消费请求体、取消监听及外层总超时；不回退代理 DNS、连接、TLS 或发送错误，
不重发已发送的 POST，不将服务器地址持久化为 IP。再次失败沿用原清理路径。

原生 handle 测试覆盖一次回退、请求体和取消监听归属及拒绝回退条件。
真机解析差异与验收范围见 [验证历史](../../docs/validation/VALIDATION_HISTORY.md)。

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

## 首次接入的历史构建证据

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

## 其余验证边界

首次接入时设备未连接，以上 12 项测试只有编译证据。IPv6 回退加入后共 15 项
原生测试已重新编译链接；设备 shell 执行受限，尚未实际运行这套测试。
更新后的签名开发包已由用户确认真实 Jellyfin 登录成功；受信 HTTPS、自签名/
主机名不匹配拒绝、代理 CONNECT、取消/超时/关闭和截断读体仍须分别设备验证。
单个服务器登录通过不代表全部网络场景或 Stage 1 整体完成。
