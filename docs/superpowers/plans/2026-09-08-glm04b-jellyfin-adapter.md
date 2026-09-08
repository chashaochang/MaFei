# GLM-04B Jellyfin Adapter 说明

日期：2026-09-08。基线：04A 冻结提交 `961ba13`（含主线并行提交 `b3028fe`）。实现 `data/adapter-jellyfin`，消费冻结的 `MediaProviderAdapter` 契约与 `JellyfinFacade`。字段/能力核对基准见 [2026-09-07-glm04a-adapter-mapping.md](2026-09-07-glm04a-adapter-mapping.md)，本文只记 04B 落地决策与偏差，不重复映射表。

## 源码结构

| 文件 | 内容 |
| --- | --- |
| `JellyfinMediaProviderAdapter.kt` | 契约实现：分页、查询构造、播放准备/上报、实例归属、能力门禁 |
| `JellyfinMapper.kt` | 顶级扩展：`BaseItemDto`→`MediaItem`/`MediaCollection`、`ItemKind`/`ImageType`/`WirePersonKind`/`CollectionType` 映射、`MediaFilter`/`MediaSort`→`JellyfinItemQuery` 参数、`JellyfinError`→`DomainError` 翻译 |

构建文件改动仅限本模块：新增 `commonMainImplementation(":providers:jellyfin:transport")`（`JellyfinItemQuery` 在 catalog 模块是 implementation 依赖，适配器需直接构造）、`commonTestImplementation(":providers:jellyfin:testkit")`。未动共享 Gradle/冻结 SDK/core。

## 落地决策（04B 冻结）

1. **`totalRecordCount = 0` ≠ "没有媒体"**（用户红线）：wire 类型无法区分"0 个"与"未知"。`Page.totalEstimate` 仅在 `totalRecordCount > 0` 时填充，0 → null。推进规则（04B 复审修正）：空页恒终止；正数 total 下只要 `nextOffset < total` 就继续发 cursor——**服务器限页（请求 50 返 1）也走完**；`nextOffset >= total` 或 total 未知时短页即末页。测试覆盖：0+非空条目继续翻页、限页三条走满、total 已知对齐页免探测直接终止、total 未知时整页追加空页兜底。
2. **无 id 条目丢弃**：`BaseItemDto.id` 空/缺的条目从列表剔除（无法缓存/重取/播放），不失败整页；详情取到无 id 时抛 `ResourceNotFoundError`。
3. **季/集编号**：Episode 的季号取 `ParentIndexNumber`、集号取 `IndexNumber`；Season 的季号取 `IndexNumber`（Jellyfin 语义）；其余类型落到通用 `indexNumber/parentIndexNumber`。
4. **图片**：`ImageTags` 按契约六类（Primary/Backdrop/Banner/Thumb/Logo/Art）映射，未知 key（如 ScreenGrab）丢弃；`BackdropImageTags` 追加为 Backdrop；URL 全部经 `JellyfinImagesApi.itemImageUrl` 构造（绝对、同源）。人员主图 P0 不取。
5. **`canPlay`**：Movie/Episode/Audio/Video 四类 true，容器/文件夹类 false（列表查询不带 MediaSources，不能以源存在性判断）。
6. **`recursive=true` 恒定**：全库浏览（parentId=null）与库内浏览都需要；`fields` 固定带 Overview/Genres/People/ProductionYear/PremiereDate/DateCreated/Runtime。
7. **筛选（04B 复审修正）**：`favoritesOnly→IsFavorite`、`playedOnly→IsPlayed`、`unplayedOnly→IsUnplayed`，组合直接并列（能力 favoritesFilter=true）；`mediaTypes`→`includeItemTypes`（含 `Unknown` 抛 `ClientConfigError`）；genres/years 直传；`items()` 内 `searchTerm` 拒绝。**`search()` 与 items 共用 /Items 管道，全部可表达筛选与排序随行**（清空即伪造结果）。**`episodes()` 拒绝 filter/sort**——冻结 `/Shows/{id}/Episodes` 无此类参数，拒绝优于假装执行（零请求）。
8. **排序**：Title→SortName、DateAdded→DateCreated、PremiereDate→PremiereDate、CommunityRating→CommunityRating、Runtime→Runtime、SeasonNumber→ParentIndexNumber、EpisodeNumber→IndexNumber；sort 缺省→Jellyfin 默认 SortName/Ascending。
9. **错误翻译**：`JellyfinError` 封闭树全分支映射（401 有会话→TokenExpired、无会话→InvalidCredentials；连接超时→TimeoutError、其余连接→NetworkUnreachableError；4xx 非特化（含 BusinessError）→`ServerError(statusCode)`；Decode/Redirect→ProtocolDecodingError；UnsupportedServerVersion 同名映射）。**CancellationException 先于翻译原样重抛**；非 SDK 异常原样穿透。
10. **播放准备（04B 复审修正）**：`ErrorCode` **非 null 即拒绝（含 SDK 容错出的 Unknown——"不认识的错误"不得解释为成功）→ `Refused(ServerRefused)`**。源选择严格三路：DirectPlay（原文件，`static=true`）→ DirectStream（**重封装，stream 端点不带 static**，服务器按协商重封装）→ 转码 URL（同源校验+补 api_key）；三者皆无 → `Refused(NoPlayableSource)`。**音频路由**：源内无 Video 流且有 Audio 流 → `/Audio/{id}/stream...`（否则恒 `/Videos/`）。跨域/不可解析链接 → `Refused(LinkRejected)`；wire `Path`（本地文件路径）不参与任何 URL。直放/重封装 URL 由适配器基于 `config.baseUrl` 同源构造（facade 的 directStreamUrl 恒 static=true，无法表达 remux）。**字幕只取选中源的 `mediaStreams`**（其他版本的轨不串台），优先 `IsDefault` 外挂轨；失败只丢字幕不丢流。`Ready.headers` 恒空；`reportingContext` 为 JSON `{itemId, mediaSourceId}` 不透明串。
11. **上报归属**：`preparedStreams` 持有本实例 Ready（引用相等），外来/手拼 Ready → `ClientConfigError`；`reportingContext` 解析失败同拒。**位置 0 是合法值原样上报**（`PositionTicks:0`）——用户拖回开头必须覆盖服务器旧续播点，不得吞零。durationMs 不上报（Jellyfin playstate 契约无该字段）。
12. **能力声明**：search/favoritesRead/favoritesFilter/watchedFilter/playbackReporting = true；**favoritesEdit/watchedEdit = false**——P0 SDK 无 user-data 写端点，`setFavorite/setWatched` 经 `requireCapability` 直接 `ClientConfigError`，零网络请求。
13. **collectionId 盖章**：`items(collectionId,…)` 把查询上下文写入每条 `MediaItem.collectionId`（非 wire 字段）；search/seasons/episodes 无库上下文 → null。

## 已知缺口 / Pending（不编造）

1. 人员（PersonRef）主图未取（需逐人 URL/缓存策略，P0 略）。
2. `liveStreamId` 源（RequiresOpening 的 RTSP/直播源）未走 autoOpenLiveStream 协商，当前按无直连源落入 NoPlayableSource/转码分支；真机直播需求出现时再扩展。
3. MusicAlbum→Track（Audio 子项）浏览未验证真实服务器返回形态，现有映射按通用路径处理，留集成窗口。
4. `DeviceProfile` 未随 PlaybackInfo 发送（服务端按客户端声明转码的场景留 04D/播放器批次决策）。
5. 直放/重封装音频判定基于源内流构成（无 Video 流且有 Audio 流）；空 `mediaStreams` 的源按视频路由处理，真实音频库留集成窗口验证。

## 验证

`:data:adapter-jellyfin:desktopTest` 与 `:data:adapter-jellyfin:testDebugUnitTest` 各 46/46（目录侧 31 + 播放侧 15），`compileKotlinIosArm64`/`compileKotlinOhosArm64` 仅编译，`verifyArchitecture`、`git diff --check` 通过。测试走 FakeJellyfinTransport + DefaultJellyfinFacade 全链路（HTTP 管道、401/404/429/5xx/解码错误、204 上报），非手写 stub 接口。
