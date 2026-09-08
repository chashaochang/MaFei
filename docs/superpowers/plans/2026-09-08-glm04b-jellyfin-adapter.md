# GLM-04B Jellyfin Adapter 说明

日期：2026-09-08。基线：04A 冻结提交 `961ba13`（含主线并行提交 `b3028fe`）。实现 `data/adapter-jellyfin`，消费冻结的 `MediaProviderAdapter` 契约与 `JellyfinFacade`。字段/能力核对基准见 [2026-09-07-glm04a-adapter-mapping.md](2026-09-07-glm04a-adapter-mapping.md)，本文只记 04B 落地决策与偏差，不重复映射表。

## 源码结构

| 文件 | 内容 |
| --- | --- |
| `JellyfinMediaProviderAdapter.kt` | 契约实现：分页、查询构造、播放准备/上报、实例归属、能力门禁 |
| `JellyfinMapper.kt` | 顶级扩展：`BaseItemDto`→`MediaItem`/`MediaCollection`、`ItemKind`/`ImageType`/`WirePersonKind`/`CollectionType` 映射、`MediaFilter`/`MediaSort`→`JellyfinItemQuery` 参数、`JellyfinError`→`DomainError` 翻译 |

构建文件改动仅限本模块：新增 `commonMainImplementation(":providers:jellyfin:transport")`（`JellyfinItemQuery` 在 catalog 模块是 implementation 依赖，适配器需直接构造）、`commonTestImplementation(":providers:jellyfin:testkit")`。未动共享 Gradle/冻结 SDK/core。

## 落地决策（04B 冻结）

1. **`totalRecordCount = 0` ≠ "没有媒体"**（用户红线）：wire 类型无法区分"0 个"与"未知"。分页推进只看返回条目——短页（< pageSize）即末页；整页对齐时发下一个 offset，空页自然终止。`Page.totalEstimate` 仅在 `totalRecordCount > 0` 时填充，0 → null。测试覆盖：0+非空条目继续翻页、整页对齐追加一次空页、短页终止。
2. **无 id 条目丢弃**：`BaseItemDto.id` 空/缺的条目从列表剔除（无法缓存/重取/播放），不失败整页；详情取到无 id 时抛 `ResourceNotFoundError`。
3. **季/集编号**：Episode 的季号取 `ParentIndexNumber`、集号取 `IndexNumber`；Season 的季号取 `IndexNumber`（Jellyfin 语义）；其余类型落到通用 `indexNumber/parentIndexNumber`。
4. **图片**：`ImageTags` 按契约六类（Primary/Backdrop/Banner/Thumb/Logo/Art）映射，未知 key（如 ScreenGrab）丢弃；`BackdropImageTags` 追加为 Backdrop；URL 全部经 `JellyfinImagesApi.itemImageUrl` 构造（绝对、同源）。人员主图 P0 不取。
5. **`canPlay`**：Movie/Episode/Audio/Video 四类 true，容器/文件夹类 false（列表查询不带 MediaSources，不能以源存在性判断）。
6. **`recursive=true` 恒定**：全库浏览（parentId=null）与库内浏览都需要；`fields` 固定带 Overview/Genres/People/ProductionYear/PremiereDate/DateCreated/Runtime。
7. **筛选**：`favoritesOnly→IsFavorite`、`playedOnly→IsPlayed`、`unplayedOnly→IsUnplayed`，组合直接并列（Jellyfin filters 支持组合，能力 favoritesFilter=true）；`mediaTypes`→`includeItemTypes`（含 `Unknown` 时抛 `ClientConfigError`——wire 无法表达，拒绝优于放宽）；genres/years 直传；`items()` 内 `searchTerm` 拒绝（`search()` 专用）。
8. **排序**：Title→SortName、DateAdded→DateCreated、PremiereDate→PremiereDate、CommunityRating→CommunityRating、Runtime→Runtime、SeasonNumber→ParentIndexNumber、EpisodeNumber→IndexNumber；sort 缺省→Jellyfin 默认 SortName/Ascending。
9. **错误翻译**：`JellyfinError` 封闭树全分支映射（401 有会话→TokenExpired、无会话→InvalidCredentials；连接超时→TimeoutError、其余连接→NetworkUnreachableError；4xx 非特化（含 BusinessError）→`ServerError(statusCode)`；Decode/Redirect→ProtocolDecodingError；UnsupportedServerVersion 同名映射）。**CancellationException 先于翻译原样重抛**；非 SDK 异常原样穿透，不伪装 DomainError。
10. **播放准备**：源选择——优先支持 DirectPlay/DirectStream 的源走 `directStreamUrl`（facade 构造，同源绝对 URL），否则第一个带 `TranscodingUrl` 的源走 `transcodingUrl`（同源校验+补 api_key）；两者皆无 → `Refused(NoPlayableSource)`。`ErrorCode`（非 Unknown）→ `Refused(ServerRefused)`。跨域/不可解析链接在 URL 构造期抛 `IllegalArgumentException` → `Refused(LinkRejected)`，**绝无未验证链接进入 Ready**；wire `Path`（本地文件路径）不参与任何 URL。外挂字幕 DeliveryUrl 经 `deliveryUrl` absolutize，失败只丢字幕不丢流。`Ready.headers` 恒空（Jellyfin 凭证在 URL）；`reportingContext` 为 JSON `{itemId, mediaSourceId}` 不透明串。
11. **上报归属**：`preparedStreams` 持有本实例 Ready（引用相等 `===`），外来/手拼 Ready → `ClientConfigError`；`reportingContext` 解析失败同拒（纵深防御，公有路径先被归属校验拦截）。Start 的 position 0 不发 `PositionTicks`；Progress 带 `IsPaused`；Stop 带终值位置。durationMs 不上报（Jellyfin playstate 契约无该字段，服务器自持时长）。
12. **能力声明**：search/favoritesRead/favoritesFilter/watchedFilter/playbackReporting = true；**favoritesEdit/watchedEdit = false**——P0 SDK 无 user-data 写端点，`setFavorite/setWatched` 经 `requireCapability` 直接 `ClientConfigError`，零网络请求。

## 已知缺口 / Pending（不编造）

1. 人员（PersonRef）主图未取（需逐人 URL/缓存策略，P0 略）。
2. `liveStreamId` 源（RequiresOpening 的 RTSP/直播源）未走 autoOpenLiveStream 协商，当前按无直连源落入 NoPlayableSource/转码分支；真机直播需求出现时再扩展。
3. MusicAlbum→Track（Audio 子项）浏览未验证真实服务器返回形态，现有映射按通用路径处理，留集成窗口。
4. `DeviceProfile` 未随 PlaybackInfo 发送（服务端按客户端声明转码的场景留 04D/播放器批次决策）。
5. 转码 URL 的查询参数透传按 facade 现状（`autoOpenLiveStream=false`）；未测 transcode 容器边界。

## 验证

`:data:adapter-jellyfin:desktopTest` 与 `:data:adapter-jellyfin:testDebugUnitTest` 各 36/36（目录侧 26 + 播放侧 10），`compileKotlinIosArm64`/`compileKotlinOhosArm64` 仅编译，`verifyArchitecture`、`git diff --check` 通过。测试走 FakeJellyfinTransport + DefaultJellyfinFacade 全链路（HTTP 管道、401/404/429/5xx/解码错误、204 上报），非手写 stub 接口。
