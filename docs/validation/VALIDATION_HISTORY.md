# 验证历史

历史快照不代表当前分支已重新验收。新结果按日期追加，分别写清提交源码、完整工作区、平台与设备证据。

## 应用入口重构（2026-10-15）

- 重构及验收修复已形成独立提交。`App.kt` 为 130 行，`MediaBrowser.kt` 为 102 行。
- `currentGlassStyle()` 与已有播放器测试使用的 `GlassStyleScope` 已分别通过 `a762a5c`、`9faeca3` 纳入提交，独立检出不再依赖临时补丁。
- 纯提交源码通过应用 Desktop 211 项、Android JVM 192 项测试、iOS/OHOS Kotlin 编译与架构门禁；播放器触摸回归 16 项、首页反馈回归 5 项通过。
- 设备回归发现并修复播放器返回按钮被顶部悬停层遮挡的问题；原有追剧关闭开关已纳入提交，首页普通连接错误改为标准提示框。
- 最终安装包来自包含原有未提交改动的完整工作区。纯提交验证与完整工作区安装验证是两种证据，不能互相替代。不得用隔离基线包覆盖用户工作版本后，遗漏恢复完整工作区包。
- 构建、设备检查、片源限制和安装包指纹见 [重构验收记录](../../composeApp/REFACTOR_ACCEPTANCE.md)。本轮未发布或推送远端，不声明全平台视觉验收完成。

## AI 可维护性整改（2026-10-18）

代码基点 `db59e3e9`，整改合回 `dev`，原有未提交改动单独保留。

- 纯提交：976 次行为测试执行 + 4 项 TestKit + 104 项 Node 测试通过；iOS/OHOS Kotlin 编译通过。
- 完整工作区：1030 次行为测试执行 + 4 项 TestKit + 110 项 Node 测试通过；根 check 与 iOS/OHOS Kotlin 编译通过。
- Desktop/Android JVM 共享测试分别计数，不当作独立场景；测试范围、声明等价和未提交修改保留证据见 [完整核实记录](../architecture/2026-10-18-ai-maintainability-review.md)。
- CI 命令在本机禁用 mavenLocal 时通过；未触发远端 CI，未链接/打包 Native 产物，未做设备、服务器或视觉验收，未推送或发布。

## 鸿蒙 IPv6-only Jellyfin 登录与标准反馈（2026-10-18）

基于保留原有未提交改动的完整工作区；临时诊断入口已恢复，不属于交付代码。

- 已签 `.dev` HAP 的真实应用进程使用与应用相同的 Curl 7.68.0 / OpenSSL，
  无凭据访问公共服务器信息。IPv6 和 AsyncDNS 均开启，IPv6 TCP/UDP socket 可创建。
- 某 AAAA-only 域名：libc `getaddrinfo(AF_UNSPEC)`（含 `AI_ADDRCONFIG`）返回
  `EAI_NONAME (-2)`；libc `AF_INET6` 与 NetworkKit 显式 IPv6 返回相同地址。
- Curl 默认请求返回主机 DNS 错误 6、HTTP 状态 0；显式 IPv6 或同地址
  `CURLOPT_RESOLVE` 均返回 HTTP 200，公共产品为 Jellyfin Server 12.2.0。
- 因此修复限定为尚未发送请求时的一次 IPv6 解析回退，保留域名及 TLS/SNI。
  原生生命周期新增 3 项测试，编译和实际执行证据分开记录。
- 32dp 共享弹窗：10 项桌面反馈测试和根 check 通过；生成手机/宽屏、深浅色、
  静止/按压离屏图并检查代表性结果。
- Jellyfin 自动端口：23 项登录测试和 11 项发现测试通过，覆盖显式无端口回退、
  显式端口精确、HTTPS 不降级、路径保留、认证失败不探测及取消。
- 修复后根 check、OHOS Debug 共享库与 15 项原生测试的 ELF 编译链接通过。
  原生测试使用 `-Pkotlin.native.cacheKind.ohosArm64=none` 和
  `-Pkotlin.native.cacheKind.ohos_arm64=none` 避开 CPF 预编译缓存的不匹配符号；
  测试 ELF 未设备执行（shell 执行受限），不计为测试通过。
- 最终签名 HAP 构建与产物一致性校验通过；包内不存在临时诊断库。已更新并启动
  `cn.xiaobai.mafei.dev`，安装前后正式版的安装信息完全相同。
- 用户在更新后的真实手机上输入不带协议和端口的域名，明确反馈“已成功登录”。
  这是实际账号登录证据，与前述无凭据 C 探针及仅编译的原生测试分别记录。
- 安装包来自完整工作区；本次提交仅包含网络回退、相关测试及验证文档，保留原有
  未提交修改。未发布 Release、未推送远端，也未宣称其余网络场景或全平台完成验收。
