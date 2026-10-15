# GLM-04C 飞牛 Adapter 说明

日期：2026-09-08。基线：04B 冻结提交 `fb32976`。实现 `data/adapter-feiniu`，消费冻结的 `MediaProviderAdapter` 契约与 `FeiniuFacade`。字段/能力核对基准见 [2026-09-07-glm04a-adapter-mapping.md](2026-09-07-glm04a-adapter-mapping.md)（含本批勘误），本文只记 04C 落地决策，不重复映射表。

## 源码结构

| 文件 | 内容 |
| --- | --- |
| `FeiniuMediaProviderAdapter.kt` | 契约实现：分页（1 基页换算）、能力门禁、播放两段协商、上报、实例归属 |
| `FeiniuMapper.kt` | 顶级扩展：类型/类别映射、日期解析、library/item/detail 映射、`FeiniuError`→`DomainError` 翻译 |

构建文件改动仅限本模块：`commonTestImplementation(":providers:feiniu:testkit")`（facade 已 api 全部飞牛模块，commonMain 无需新增）。未动共享 Gradle/冻结 SDK/core。

## 证据修订（04A 映射表勘误）

- **`watchedFilter` 飞牛改声明 `false`**：`FeiniuItemListTags.watched` 是 String 透传，旧版 legacy 从未构造过它的字面量（全库仅 `filter.watched.trim()` 透传，无赋值点）。无证据的 "1"/"0" 属编造——本批拒绝 `playedOnly/unplayedOnly` 并降声明；真机 fixture 证实字面量后再启用。
- 同理 **genres（Int tag）与 decade（string tag）**：`items()` 携带非空 genres/years 即拒绝（`throwUnsupportedFeiniuFilter`），`search()` 携带任何 filter/sort 也拒绝。
- `episodes(seasonId=null)`、`items()` 内 `searchTerm` 等 04A Pending 项已按"显式拒绝"落地。

## 主线修订（2026-09-08，优先于下方原稿）

- GLM 原提交 `82b3a9d` 不能直接冻结：主线新增六个独立边界用例全部失败，已按原契约接手修正。
- 飞牛使用私有 `v1:fnpage:页号:请求页大小:已返回条数` 游标，不复用 offset/pageSize 换算；服务器限页时页号照常递增，已返回条数只用于 total 终止判断。变更 pageSize、错误格式及溢出均拒绝；原 offset 游标不再接受。Repository 不解析此格式。
- 收藏列表只接受缺省或显式 DateAdded/Descending 排序，其他排序零请求拒绝。
- 日期校验包含月天数和闰年，非法日期不会被归一成另一个日期。
- Adapter 在登录/恢复后创建并绑定身份代次，身份切换即取消旧操作；playInfo/play/record 通过 SDK 新增的可选 pinnedOperation 参数使用同一身份的快照。读取播放器 headers 前也验证身份，旧 Ready 不能使用新身份上报。此主线 SDK 扩展保持旧调用源码兼容，不变更 core 或共享 Gradle。
- 同身份 Cookie 更新仍消费最新权威快照；这不是跨存储事务、Native 真并发或进程级一致性的验收，后续 04E 仍必须为不同账号创建独立客户端并处理迟到结果。
- 拒绝链接的 detail 不携带原始签名 URL。

## GLM 原稿决策（以上主线修订覆盖对应条目）

1. **能力声明**：search/favoritesRead/favoritesEdit/watchedEdit/playbackReporting = true；favoritesFilter = false（`/favorite/list` 无 tags，组合即拒）；watchedFilter = false（证据修订，见上）。
2. **collections**：`mediaLibraries` 单页，非空 cursor 拒绝；category→mediaTypes（movie/tv 已知，未知→空集容忍）；posters 首个非空→Primary 图。
3. **items 分页**：`OffsetPageCursorCodec` offset → 1 基 page（`offset/pageSize+1`）；非整除 offset 拒绝（游标与页对齐不符）。推进语义与 Jellyfin 适配器同款：空页终止、`total>0` 时限页短页继续走、`nextOffset>=total` 声明终止、total 缺失/0 → `totalEstimate=null`。
4. **筛选路由**：`favoritesOnly` → `/favorite/list`（ancestorGuid 保留），组合约束拒绝；`mediaTypes` 单值→category（movie/tv/directory/video），组合与 Unknown 拒绝；排序仅 `DateAdded→create_time`（默认即 create_time DESC），其余字段拒绝。
5. **search**：`/search/list` 仅 keyword；整表单页返回（`nextCursor=null`），非空 cursor 拒绝（无状态 adapter 下 cursor 意味着重查询而非翻页，伪装成分页不诚实）。
6. **item 详情**：`itemDetail` 的 guid 与请求 id 不一致→`ResourceNotFoundError`（legacy 同款防回错条目守卫）；`posters/backdrops` 字符串**原样**作 imagePath（legacy FeiniuVideoCatalogProvider.ets:744 行为，wire 未定形不猜解析）；`vote_average` 字符串→Double 解析失败 null；`release_date`/`first_air_date` 按 `yyyy-MM-dd` 前缀解析（Hinnant 算法纯 Kotlin），失败→year/premiere 均 null；**详情无用户状态→空 `MediaUserData()`，绝不编造**（repository 之后用列表态合并）；`play_error` 非空→`canPlay=false`。
7. **季/集**：`seasons(seriesId)`→`/season/list/{seriesId}`；`episodes` **必须 seasonId**（端点取季 guid），seasonId=null、非空 cursor、非空 filter/sort 一律拒绝；均单页。
8. **播放准备**：`playInfo`→`play` 两段。`play` 请求的协商字段按 legacy 缺省路径（h264/aac/2ch/forcedSdr=0/bitrate=0/resolution=""——streamList 在 P0 wire 仍是 JsonElement 未定形，真值待定形后接入）；`startTimestamp=0`（契约尚无续播位置参数，04D/播放器批次扩展）。`play_link` 缺失→`LinkMissing`；`resolvePlayLink` 同源校验失败→`LinkRejected`；成功→绝对 URL 进 Ready。字幕 `resolveSubtitleLink` 失败只丢字幕。**headers**=`playbackHeaders(权威快照凭证, requiresRelayCookie)`——Authorization+token Cookie，relay 路由附 `mode=relay`；凭证读自 `facade.credentials.value`（权威快照，不缓存不写回）。`reportingContext` 为 JSON `{itemGuid, mediaGuid, videoGuid, audioGuid, subtitleGuid, resolution, bitrate, durationSeconds, playLink}` 不透明串。
9. **上报**：`ts = positionMs/1000`（位置秒，legacy :739）；**`ts<=0` 整体跳过不发包**（legacy :752 行为，与 Jellyfin 保留 0 的语义不同——飞牛协议自身如此）；`duration` 优先事件 durationMs，否则协商 `durationSeconds`；`play_link` 随 record 回传。归属校验同 Jellyfin（引用相等，外来 Ready 拒绝）。
10. **错误翻译**：`FeiniuAuthenticationExpiredError` 先于 open 父类 `FeiniuAuthenticationError` 分支（TokenExpired/InvalidCredentials）；连接超时→`TimeoutError`、其余→`NetworkUnreachableError`；envelope `code!=0` 与非 2xx→`ServerError`（envelope code 非 HTTP status，statusCode=null，code 入 message）；Decode/Redirect→`ProtocolDecodingError`；InvalidAddress/UnsupportedCapability→`ClientConfigError`。CancellationException 先于翻译原样重抛。

## 已知缺口 / Pending（不编造）

1. `watched`/`genres`/`decade` tag 的字面量值：待真机 fixture，证实后启用 watchedFilter 并放开对应筛选。
2. `streamList` track 类型化（codec/resolution/bps 真实值进 play 请求）：P0 wire 仍为 JsonElement，待真实设备 fixture 定形。
3. `startTimestamp` 续播：契约 `preparePlayback` 无位置参数，需要时由 04D/播放器批次提出契约扩展。
4. 详情 `posters/backdrops` 的分隔规则（可能为 JSON 数组字符串）：跟随 legacy 原样透传，真机 fixture 后再定。
5. 中继/回退路由下的图片 origin：当前用 `resolution.preferred.baseUrl` 拼接，实际应答源不同 origin 的场景留集成窗口。

## 验证

主线接手复审：Adapter Desktop/Android JVM 各 **44/44**（原 36 + 主线 8），播放 SDK 两端各 **4/4**；Adapter iOS/OHOS 编译强制执行通过，verifyArchitecture、git diff --check 通过。前六个新增用例已先验证红灯再转绿。仅编译不代表 Native 运行，真实 fnOS 服务端仍 Pending。日志目录 `/private/tmp/mafei-04c-review.ihiDVf/`。

`:data:adapter-feiniu:desktopTest` 与 `:data:adapter-feiniu:testDebugUnitTest` 各 36/36（目录侧 26 + 播放侧 10），`compileKotlinIosArm64`/`compileKotlinOhosArm64` 仅编译，`verifyArchitecture`、`git diff --check` 通过。测试走 FakeFeiniuTransport + DefaultFeiniuFacade 全链路（envelope code/HTTP 状态/解码错误/连接与超时/取消、POST body 断言、PUT/DELETE 写语义），非手写 stub 接口。
