# Codex 04E：会话与安全凭证写序

## 后续增量：账号恢复索引（2026-09-08）

`SessionRepository(store, settings)` 现在可使用现有 `SettingsStorage` 保存非敏感账号选择与凭证别名索引；原单参数构造继续保持进程内行为。新增 `savedAccounts()`、`restoreCurrent()` 和 `restoreAccount(selection)`，恢复结果只提供 SDK 安装材料，不发布 Ready，不复用旧进程的 SDK 代次。

- 每组凭证写入前提交 Pending，全部安全存储写入成功后提交 Committed。中断的组不参与恢复，避免新 Token 与旧 Cookie 混用；不宣称跨存储事务。
- 退出先撤销恢复资格再删除凭证，标记失败必须重试，不能宣称退出成功。不会自动恢复另一个保存的账号。
- 别名跨账号冲突拒绝；缺少任一应有凭证则拒绝恢复整组；无 Cookie 的合法快照无需伪造值。索引损坏抛脱敏存储异常。
- 主线回归发现无效投影校验被移出清理分支，导致旧租约未撤销；既有测试实际失败后已修复。日志 `/private/tmp/mafei-session-recovery-review.log`。
- `/private/tmp/mafei-session-recovery-green.log`：Desktop/Android 各 **56/56** 实际通过（新增恢复测试 13 项）；iOS/OHOS commonMain 编译、架构门禁通过。Native 未运行。

仍需应用启动层提供真实 `SettingsStorage`、服务器地址/用户信息、稳定别名并安装 SDK 会话，再调用 select/observe。索引依然要求单实例独占写入，Pending 组重新登录时清理，不提供跨进程写协调或自动修复受损凭证。下面保留原批次历史，旧文所列“恢复/journal 未实现”已由此增量替代，不代表应用集成或真机验收完成。

主线终审（2026-09-08）：已逐项审读并修正排队取消与过早 Ready 发布；独立重跑 Repository Desktop/Android 各 43 项（04D 24 + 04E 19）全部通过，迁移联合回归各 30 项通过。日志 `/private/tmp/mafei-data-final-independent.log`；本轮 Native 编译门禁通过（缓存命中），不计 Native 运行。批准此批跨平台写序核心，不代表真实平台存储或应用登录接线完成。

本包只修改 `data/repositories/**/session/`；没有改共享 Gradle、Provider、core、UI、宿主或 legacy，没有自行提交。基于工作区实际代码和冻结飞牛 `FeiniuFacade.credentials: StateFlow<FeiniuSessionSnapshot>` 编写，不消费 `FeiniuCallResult.credentials`。

## 已实现边界

- `SessionSelection` 使用真实 `ProviderKind/ServerId/AccountId`，`state` 是不含秘密的选择与持久化状态流。
- `SessionHandle` 为本实例引用级写入租约；每次重新选择（包括同账号新代次）撤销旧句柄。迟到旧写/旧退出不能覆盖或删除新账号，包含复用相同别名的情况。
- `StateFlowCredentialSource<T>` 直接读取 SDK 权威 StateFlow 的 `.value`；流只唤醒写入，每次获得锁后重新读权威源。没有异步镜像反向覆盖权威值。
- `SessionCredentialSnapshot` 只是一份安全存储材料投影：identityGeneration、revision、别名到既有 SecureSecret 的映射。不是复制出来的飞牛凭证业务模型；复制输入 Map，日志字符串脱敏。
- 同一身份只提交递增 revision。SDK identityGeneration 改变需显式重新选择账号，不把新登录静默绑定旧账号；进行中的保存结束后也重查身份。
- 同一实例中的保存、选择、删除通过同一 Mutex 串行化；select/persistLatest 可取消地排队，获锁后再检查取消，仅真正准入的存储临界区在 NonCancellable 中排空。退出的删除排在旧保存之后，即使退出调用方取消，已进入的退出仍完成或暴露清理失败。
- Ready 只在保存结束且重新核对权威身份后发布；保存期间身份变化会清理，不短暂发布已失效身份的 Ready。
- 部分保存失败撤销会话并清理整个本次别名集合。删除失败保留别名，状态为 CleanupRequired，后续选择必须先清理成功；`retryCleanup()` 不恢复旧租约。
- 切换到不同账号不删除旧账号独立别名下已保存的登录；重新选择同账号替换其旧别名集。别名仍应按 provider/server/account 稳定隔离。别名复用的旧句柄不能操作新账号。

## 接线约定（本包不越界接宿主）

Repositories 当前只能看见 adapter 的 API，不能通过其 implementation 依赖直接看 SDK facade。因此提供可实际接入的泛型 StateFlow port，没有加隐藏 SDK 依赖。

在拥有 `FeiniuFacade` 的组装层构造 source：

```kotlin
val source = StateFlowCredentialSource(facade.credentials) { snapshot ->
    SessionCredentialSnapshot(
        identityGeneration = snapshot.identityGeneration,
        revision = snapshot.revision,
        secrets = buildMap {
            // tokenAlias/cookieAlias 由稳定、账号隔离的别名分配器提供。
            // scheme 使用该 provider 的真实认证约定，不从媒体 URL 推导。
            if (snapshot.credentials.token.isNotEmpty()) {
                put(tokenAlias, SecureSecret.AuthToken(providerScheme, snapshot.credentials.token))
            }
            if (snapshot.credentials.cookie.isNotEmpty()) {
                put(cookieAlias, SecureSecret.CookieText(snapshot.credentials.cookie))
            }
        },
    )
}
val handle = sessions.select(selection, setOf(tokenAlias, cookieAlias), source)
// 使用调用方拥有的 scope；取消旧账号观察 Job，禁止 fire-and-forget。
val observation = scope.launch { sessions.observe(handle) }
```

Jellyfin 可投影其组装层拥有的会话权威 StateFlow；不使用响应 DTO 作为跨操作权威源。登录流程的 SDK 安装/清除、UI scope 生命周期、MediaRepository.open/close/invalidate、恢复已保存账号、稳定别名分配均留给主线接线。`logout(handle)` 清安全存储与本仓库租约，不能伪称它已替宿主清除了 SDK 内存或播放器。

## 准确限制

1. 只有一个实例拥有这些别名的写序。迁移应在启动会话协调器前完成；多实例、多进程或其他代码绕过协调器直接写相同别名不受此 Mutex 保护。
2. 不宣称跨 Token/Cookie 的崩溃原子事务。进程终止后的清理 journal、普通账号记录/选择持久化与平台存储恢复仍需后续集成，不用 VersionedStorage 标记冒充事务。
3. 获锁并复核取消后真正准入的写入/切换不会因为调用者取消自动回滚；调用方必须依据状态流和退出完成结果决策。仍在等锁的 select/persistLatest 取消后不准入、不写存储、不切号；已进入退出则一定先排空写再清理。底层永不返回时无法安全放弃写锁，不能通过超时让后续旧写绕过删除。
4. `observe` 的 Job 由调用方管理；取消观察不是退出，退出需要显式调用并处理 CleanupRequired。SDK 身份切换必须重新选择，不跨操作缓存凭证。
5. OHOS/iOS 实际调度、真实 Keychain/HUKS 存储、HAP 和覆盖安装验证仍 Pending。

## TDD 与验证

- 初始红灯 `/private/tmp/mafei-04e-red.log`：测试先落，SessionRepository 等符号不存在导致编译失败（不是行为断言红灯）。
- 账号切换保留登录的行为红灯 `/private/tmp/mafei-04e-switch-red.log`：当前实现误删旧账号独立别名，新增断言失败后修复。
- 最终 `/private/tmp/mafei-04e-green.log`：Desktop、Android JVM 各 **17/17**（3 个 common 投影边界 + 14 个 JVM 挂起/取消/写序测试）实际执行成功，verifyArchitecture 通过。JVM 并发测试不在 Native 执行。
- iOS/OHOS 的 commonMain 编译已在实现修改后实际执行通过；最终仅增加测试后的重复检查命中缓存/UP-TO-DATE。不把 commonMain 编译当 Native 测试运行。
- 主线终审红灯 `/private/tmp/mafei-04e-review-red.log`：取消排队选 B 仍切号、保存后身份已变仍瞬间发 Ready，两个确定性用例均先失败（2/2 红灯）。修正为可取消准入及发布前复核，并增加取消排队 persist 不多写一笔的用例。终审修正绿灯 `/private/tmp/mafei-04e-review-green.log`：Desktop/Android JVM 各 **19/19** 实际通过（3 common + 16 JVM），架构门禁通过；iOS/OHOS commonMain 修正后编译通过，最终仅改测试的重跑为 UP-TO-DATE。

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew \
  :data:repositories:desktopTest --tests '*session.*' \
  :data:repositories:testDebugUnitTest --tests '*session.*' \
  :data:repositories:compileKotlinIosArm64 \
  :data:repositories:compileKotlinOhosArm64 --console=plain
```
