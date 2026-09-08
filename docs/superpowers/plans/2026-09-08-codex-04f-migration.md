# Codex 04F：旧数据迁移核心

主线终审（2026-09-08）：已审读 Planner/Engine/Sink 边界及故障夹具，独立重跑 Desktop/Android 各 30 项全部通过，并联合验证 Repository 各 43 项。日志 `/private/tmp/mafei-data-final-independent.log`；Native 编译门禁通过（缓存命中），不计 Native 运行。批准迁移核心小批；真实 Reader、业务事务 Sink、覆盖安装仍须按本文要求集成，不得宣布旧用户迁移已验收。

日期：2026-09-08。范围仅 `data/migration/src/**`；未修改共享 Gradle、SDK、UI、宿主、legacy。未提交，由主线终审并统一提交。

## 状态和接线入口

跨平台迁移核心与合成脱敏 fixture 已实现。`LegacyMigrationPlanner` 负责纯校验/映射；`LegacyMigrationEngine` 负责可恢复提交。核心不读取磁盘、不依赖 Provider SDK。`LegacySnapshotReader` 实际 OHOS RDB/Preferences/N-API 实现、`MigrationSink` 实际业务事务/启动锁实现、真机覆盖安装均为 **Pending integration**，没有将这部分作为空库成功。

注：`LegacySnapshot.version=1` 是本次规范化交接格式版本，不冒充旧 RDB/Preferences 全部使用相同版本。Reader 必须分别检查原始版本，未知版本返回 `Unavailable(UnsupportedVersion)`。核心目前只接受快照 v1、其中首页旧格式 v2；不存在未知字段静默放宽。

## 真实来源清单

以下路径都相对 `legacy/harmony-arkts-reference/entry/src/main/ets/`，本轮只读核对。不得再沿用“只有 Server/User 两表”的旧结论。

| 来源 | 已核对文件 / 持久化位置 | 规范化映射与要求 |
| --- | --- | --- |
| Jellyfin 服务器 | `data/RdbConstants.ets`、`data/ServerDao.ets`；`jellyFin.db` / `Server` | `id`→稳定 source key；`hostname`→baseUrl；`server_name`→name；`version`→version。不得把 `last_used_timestamp` 填入“创建时间”。原始行 ID 及时间读取精度由 Reader 负责。 |
| Jellyfin 账号 | `data/UserDao.ets`、`media/jellyfin/JellyfinAccountRepository.ets`；`User` 表 | `server_id` 关联服务器；`user_id` 是字符串的远端 ID，不能使用 `User.id` 代替；`user_name`→userName；`last_login_timestamp`→lastActiveEpochMs；`access_token`→`SecureSecret.AuthToken`。原列虽声明 INTEGER，实际 API 使用字符串 ID，不能擅转 Long。 |
| Jellyfin 当前选择/旧 Token | `data/AppPreference.ets`、`utils/Constants.ets`；`JellyFin_Preference` | PREF_SERVER_ID / PREF_USER_ID / PREF_SERVER_SYSTEM_ID / PREF_ACCESS_TOKEN 等是 JSON 字符串封装。和 RDB 账号身份核对后才选中；不能把当前偏好 Token 批量给所有账号。 |
| 飞牛多账号（此前遗漏） | `features/feiniuvideo/account/FeiniuVideoAccountStore.ets`、`FeiniuVideoAccountModels.ets`；`mafei_feiniu_accounts.db` / `FeiniuVideoAccount`，v1 | provider_account_id、user_id、display_name、server_name、original_address、base_url、connection_route、fallback_base_urls、last_used_timestamp 均有真实来源。连接原地址/路线/有序回退列表保留在 `MigratedServerConnection`。Reader 可按 provider_account_id 建独立服务器记录，避免把两账号不同的动态路由覆盖。 |
| 飞牛多账号凭证 | `features/feiniuvideo/account/FeiniuVideoCredentialStore.ets`；`SP_HARMONY_UTILS_PREFERENCES` 中 `mafei.feiniu.video.credentials.v1:<providerAccountId>` | 旧 JSON v1 含 token/cookie。必须读入安全内存类型后写安全存储；不得复制原 JSON 到 SettingsStorage。源行为是已有 Token 优先、缺失时提取 `Trim-MC-token`，不能跨账号兜底。 |
| 飞牛更早单会话 | `features/feiniuvideo/upstream/common/FnMediaAuthStore.ets`、`SessionStore.ets`、`PreferencesUtil.ets` | fnmedia-base-url/token/cookie/auth-host 及 `::` scoped keys；读取必须核对源作用域和身份。只有 Token/baseUrl 而没有可证明的 user/account 关联时返回 InconsistentCapture，不虚构“默认账号”。历史类中有 OAuth 遗留命名不构成新增 OAuth 功能授权。 |
| 当前 Provider 账号 | `media/account/CurrentMediaAccountStore.ets`；`MediaProviderAccountPreference` / `current_media_provider_account` | `{provider, providerAccountId}`→selectedAccountKey，必须存在对应导入账号。`MediaProviderSelectionHistoryStore.ets` 的 explicit selection 需 Reader 用于避免推断已明确退出的选择。 |
| 全局播放偏好 | `data/AppPreference.ets`、`utils/Constants.ets`、`entity/PlayerEngineType.ets` | pref_player_engine=`auto/av/mpv`，pref_mpv_hardware_decode，pref_mpv_cache_size_mb；缺失保留 null，不在迁移时伪造旧偏好。跨平台是否支持旧 `av` 引擎由播放器应用偏好时解释。 |
| 单媒体片源偏好 | `AppPreference.ets` 的 preferredMediaSourceEntries、`media/MediaRef.ets` | pref_media_source_selections 保存 mediaRefIdentity/mediaSourceId；Reader 按旧 length-prefixed identity 精确解析并绑定账号/媒体，不能用 split('|') 猜 ID。无法证明 scope 归属返回 InconsistentCapture。 |
| 追剧 | `AppPreference.ets` 的 getChasingSeriesIdsKey | `pref_chasing_series_ids_<serverSystemId 或本地 serverId>_<userId>`；trim/去空/去重语义保留，结果稳定排序。Reader 不能按下划线简单拆 key（远端 ID 可能含分隔符），必须用已知账号和已证实的 serverSystemId 反向匹配。查不到作用域映射必须 Pending/InconsistentCapture，不能算空列表。 |
| 首页快照 | `features/home/hometab/HomeSnapshotStore.ets`；`HomeContentSnapshot` / `home_content_snapshot` v2 | accountScope/savedAt、media/resume/recommendation/nextUp/latestMedia/latestEntries/selectedLatestMediaId/hasLiveTv。scope 必须和旧 `MediaProviderSession.createMediaAccountScope` 精确匹配；跨账号不导入。保留安全显示字段，旧 image/imageRef URL/requestPath、播放 URL、路径不导入（可能带 Token），返回 HomeImagesRequireRefresh。旧有效期 7 天；过期或未来时间返回 HomeExpired，明确失效缓存而不是当当前首页。 |

空白 display_name/server_name 是飞牛原 schema 允许值，核心不凭空拒绝或改写；缺失远端 user ID、关联断裂、重复 source key、非法 URL/版本一律拒绝整批。Reader 不能调用旧代码“吞异常返回 []”的接口后声称读完整，应直接读取原始 RDB/Preferences 并保留错误类别。

## 公共契约

- 每个 `LegacySource` 必须显式 `Read` 或 `ConfirmedAbsent`；默认缺项/`Pending` 不可迁移。存在数据却声称对应来源 absent 也会拒绝。
- source `installationId` 必须是平台 Reader 持久化的稳定、非秘密安装标识；跨重试保持不变。快照、账号携带 `SecureSecret`，故没有序列化注解且 toString 脱敏。普通业务 payload 只有 `tokenAlias/cookieAlias`。
- Source keys 是 Reader 分配的稳定 **非秘密**标识，不能使用 Token、Cookie、密码。Jellyfin 可用 provider + Server.id 和旧 createJellyfinProviderAccountId；飞牛用 provider_account_id。核心以固定宽度 UTF-16 hex 做无碰撞编码，并加安装/服务器/账号命名空间。不是随机 UUID 或依赖列表位置的 ID。
- `MigratedAccount.remoteUserId` 独立保存实际远端身份；`Account.id` 是新本地 ID，不允许适配层把它直接当请求 userId。
- `MigrationRecords` 为 provider-neutral 写入边界；公开数据不带 wire DTO/平台类型。`home` 为启动时离线展示的安全快照，不是新 Repository TTL 缓存；图片重新请求。
- 凭证不存在可迁移为无 Token 的账号，之后走显式登录；不能从另一账号复制凭证。已有别名内容不匹配时 `CredentialConflict`，绝不覆盖。

## 可恢复提交，不是假装跨存储事务

1. Sink 获取排他启动迁移 lease，必须同时排除 login/logout/普通业务写、其他 Engine 实例及其他进程。真实 Sink 无法保证则拒绝；本模块没有伪造全局锁。
2. Reader 一致捕获原数据；Planner 全量验证并映射。失败前没有任何写入。
3. 普通 VersionedStorage 用一次 `edit` 原子保存非敏感 manifest 和首次捕获时钟。时钟固定，避免跨七天边界重试改变首页去留。manifest 是完整非秘密业务 JSON，不用不可靠 hash。
4. 安全存储逐别名 create-if-absent；已存在时只比较不覆盖。中途停止会留下迁移专用别名，重试复用；不删除旧库，也不因回滚误删别人已更新的凭证。
5. Sink 在同一业务数据库事务里写记录 **和 durable receipt**，事务必须冲突拒绝/幂等。不是先写业务再单独写 receipt。数据集合已和 Reader 可变集合断开引用。
6. 最后写 VersionedStorage 完成标记。业务成功但回包/标记失败时，下轮由 receipt 跳过所有凭证和业务写，故不会把业务提交后已经清除的凭证重新复活。

凭证存储不是普通事务的一部分；上面的顺序不能保证“要么所有存储全写、要么全不写”，保证的是有明确恢复点且重试无重放破坏。没有 receipt 的半成品业务不得可见给应用；版本化标记不能替代这个条件。普通日志缺损、receipt/标记不一致或同一安装的源业务变更会拒绝并等待恢复策略，不暗中覆盖。完成后源库不得继续产生写入；若旧导入源变更，需另行批准新的 migration version。

## 验证证据

TDD：`/private/tmp/mafei-04f-red.log`，18 项实际执行、17 项预期失败（引擎红阶段，已有脱敏测试通过）；实现后 Desktop/Android 各 18/18。补充故障边界后最终 **Desktop 30/30、Android 30/30**，无 skipped。

最终命令与日志：

```bash
./gradlew :data:migration:desktopTest :data:migration:testDebugUnitTest :data:migration:compileKotlinIosArm64 :data:migration:compileKotlinOhosArm64 verifyArchitecture --console=plain
# /private/tmp/mafei-04f-final.log
```

2026-09-08 18:11 CST，两个测试目标实际执行，两个本模块 Native compile 任务实际执行（非 UP-TO-DATE）；上游依赖存在缓存/UP-TO-DATE。架构门禁通过。Native 单测/真实并发/设备/覆盖安装未运行。

故障覆盖：安全写前失败和持久化后丢失 ACK、业务提交前失败和已提交丢失 ACK、完成标记失败、journal 写失败、取消原样传播、损坏 journal、标记没有 receipt、源改变、凭证冲突、未知版本、空库/未接 Reader、重复导入、乱序稳定 ID、缺字段/关联断裂、同源 key 重复、非法含密 query 的 URL、过期首页、跨过期边界重试、选择账号缺失、偏好重复、合法空白显示名。

## 主线集成需求 / 风险

1. `MigrationSink` 没有实现，因为真实持久库不在本工作包；需业务存储/04E 集成时实现租约和事务 receipt，启动完成前禁止激活新会话。不能直接用现有会话 `save` 拼装为事务。
2. OHOS Reader 需读取上表全部来源、稳定安装 ID、旧 scope 解析、未知/损坏隔离。不能用所有来源 ConfirmedAbsent 的临时 Reader 上线。
3. 追剧旧 serverSystemId 到历史账号的映射不总能从当前选择偏好恢复；需真机 fixture 确认，无法确定必须报告，不丢数据、不猜绑定。仅有旧 fnmedia Token 而没有身份的情况同理。
4. 首页图片和旧路径刻意不迁移，交给主线首轮刷新；这是安全的缓存重建，不影响服务器/账号/Token/追剧/播放偏好的持久迁移。
5. 未新加依赖坐标；全部类型在 migration 模块；shared Gradle 零改动。CPF/KMP 技能约束保持宿主只做桥接，不能以 ArkUI 业务代码绕开本核心。
