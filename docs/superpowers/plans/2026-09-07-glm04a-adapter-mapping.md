# GLM-04A 适配器契约映射表

日期：2026-09-07。基线 `0c499b6`。本文是 `data/adapter-contract` 契约（04A）与两个 Provider SDK 真实源码的逐项核对结果，供 04B/04C 实现适配器、04D/04E 消费契约时引用。所有路径相对仓库根；所有类型/字段均核对过当前源码，无推测字段。

## 1. 契约方法 → Domain 类型 → SDK 入口

契约源码：`data/adapter-contract/src/commonMain/kotlin/cn/mafei/cmp/data/adapter_contract/MediaProviderAdapter.kt`。适配器实例在构造时绑定一个 server + account（`provider`/`serverId`/`accountId`）。

| 契约方法 | Domain 输入/输出 | Jellyfin 真实入口 | 飞牛真实入口 | 单位与空值策略 |
| --- | --- | --- | --- | --- |
| `collections(request)` | → `Page<MediaCollection>` | `JellyfinCatalogApi.views(userId): QueryResultDto<BaseItemDto>`（providers/jellyfin/catalog/.../JellyfinCatalogApi.kt:19） | `FeiniuCatalogApi.mediaLibraries(resolution): List<FeiniuMediaLibraryEntry>`（providers/feiniu/catalog/.../FeiniuCatalogApi.kt:23） | 两家均为单页调用：`nextCursor=null`；非空 cursor 拒绝（ClientConfigError），不回放首页。`totalEstimate=条目数`。Jellyfin `CollectionType`→`mediaTypes`；飞牛 `category` 同理，无法识别的类型→空集 |
| `items(collectionId, request)` | → `Page<MediaItem>`；筛选走 `PageRequest.filter` | `JellyfinCatalogApi.items(JellyfinItemQuery)`（同上:22）；query 的 `startIndex`/`limit` 为 0 基 offset（providers/jellyfin/transport/.../JellyfinItemQuery.kt:37） | `FeiniuCatalogApi.items(resolution, ancestorGuid, category, page=1基, pageSize, sortColumn, sortType, genres:Int, watchedTag, decade)`（providers/feiniu/catalog/.../FeiniuCatalogApi.kt:35）；`favorites()`（:79）是独立端点（固定 `create_time DESC`，无 tags 组合） | Jellyfin offset 游标用 `OffsetPageCursorCodec`（`v1:off:N`，0 基）；飞牛 1 基 page→offset 的换算属 04C 自有策略。总数：Jellyfin `totalRecordCount:Int`（**DTO 默认 0，"0" 与 "未知" 不可区分**，04B 须定夺何时置 `totalEstimate=null`）；飞牛 `total:Int?`（null→null）。排序：Jellyfin `sortBy/sortOrder` 与 `MediaSortField` 可直映射（SortName/DateCreated/PremiereDate/CommunityRating/Runtime/ParentIndexNumber/IndexNumber ↔ Title/DateAdded/PremiereDate/CommunityRating/Runtime/SeasonNumber/EpisodeNumber——注意 Jellyfin 用 Parent/IndexNumber 表达季/集排序）；飞牛仅 `sortColumn`（`create_time` 等）+`sortType`，`MediaSortField` 不全覆盖→无法承载的排序报 ClientConfigError 或降级并记录，不得静默 |
| `search(term, request)` | → `Page<MediaItem>` | 同 `items`，`JellyfinItemQuery.searchTerm=term`（修正勘察第 1 条：无独立 search 方法但有 searchTerm） | `FeiniuCatalogApi.search(resolution, keyword): List<FeiniuMediaItem>`（同上:87）——**不分页、无排序/筛选** | 飞牛：首次调用整表入一页（`totalEstimate=list.size`）；越界 offset 切片→终末空页或拒绝，策略 04C 定，二选一必须测试。Jellyfin：与 items 相同的 offset 分页 |
| `item(id)` | → `MediaItem`（含用户状态） | `JellyfinCatalogApi.item(userId, itemId): BaseItemDto`（同上:28，官方 GET /Items/{itemId}?userId=） | `FeiniuCatalogApi.itemDetail(resolution, guid): FeiniuItemDetail`（同上:110，P0 子集：providers/feiniu/core/.../FeiniuModels.kt:113） | 飞牛详情**无** watched/is_favorite/进度字段→用户状态以列表项为准或空 `MediaUserData()`，不得编造。详情评分：Jellyfin `CommunityRating:Double`；飞牛 `vote_average:String`→解析失败置 null。缺失 id 两家均映射 `ResourceNotFoundError` |
| `seasons(seriesId)` | → `Page<MediaItem>` | `JellyfinCatalogApi.seasons(userId, seriesId, fields)`（同上:30） | `FeiniuCatalogApi.seasons(resolution, guid)`（同上:120） | 均单页无排序。层级字段见 §3 |
| `episodes(seriesId, seasonId?, request)` | → `Page<MediaItem>` | `JellyfinCatalogApi.episodes(userId, seriesId, seasonId?, startIndex, limit, fields)`（同上:32）——**支持真实 offset 分页** | `FeiniuCatalogApi.episodes(resolution, guid)`（同上:126）——无分页 | 飞牛单页：`nextCursor=null`；`seasonId=null`（整季）飞牛无对应语义→按 04C 约定（要求 seasonId 或遍历季），需显式 |
| `setFavorite(id, favorite)` | Unit | **无 SDK 写端点**（Jellyfin facade 仅 discovery/auth/catalog/playback/playstate/images：providers/jellyfin/facade/.../JellyfinFacade.kt:22）→ `favoritesEdit=false`，调用即 `ClientConfigError` | `FeiniuCatalogApi.markFavorite/unmarkFavorite(resolution, guid)` PUT/DELETE `/v/api/v1/item/favorite`（同上:98-102） | 能力 false 时经 `requireCapability` 拒绝；不伪装成功 |
| `setWatched(id, watched)` | Unit | 同上，`watchedEdit=false` | `FeiniuCatalogApi.markWatched/unmarkWatched(resolution, guid)` POST/DELETE `/v/api/v1/item/watched`（同上:104-108） | 同上 |
| `preparePlayback(item)` | → `PlaybackPreparation`（sealed：`Ready`/`Refused`） | `JellyfinPlaybackApi.playbackInfo(...): PlaybackInfoResponseDto{mediaSources, playSessionId, ErrorCode}`（providers/jellyfin/playback/.../JellyfinPlaybackApi.kt:26；**ErrorCode 在 DTO 内不抛**：providers/jellyfin/wire/.../MediaDtos.kt:137）+ `directStreamUrl`/`transcodingUrl`/`deliveryUrl` | `FeiniuPlaybackApi.playInfo`+`play(...): FeiniuPlaybackSession`（providers/feiniu/playback/.../FeiniuPlaybackApi.kt:68,106）；`resolvePlayLink` 同源校验失败返回 null（:36）；`play_link` **服务器签发、会过期，绝不持久化** | 映射：ErrorCode≠null→`Refused(ServerRefused)`；mediaSources 空→`NoPlayableSource`；飞牛 play_link 缺失→`LinkMissing`；resolve null（跨域/不可解析）→`LinkRejected`。`Ready.streamUrl/subtitleUrl` 一律绝对、经适配器校验（契约 init 拒空白）。`Ready.headers` 仅飞牛需要（`FeiniuAssetsApi.playbackHeaders`：Authorization+Cookie[+mode=relay]，providers/feiniu/assets/.../FeiniuAssetsApi.kt:32）；`Ready.playSessionId` 仅 Jellyfin；`Ready.reportingContext` 仅飞牛（携带 guid 集/resolution/bitrate/play_link 的不透明串）。曲目协商：Jellyfin 走 `MediaSourceInfoDto.mediaStreams`；飞牛走 `streamList`（providers/feiniu/playback/.../FeiniuPlaybackApi.kt:151，字段为 JsonElement 未定形） |
| `reportPlayback(stream, event)` | `PlaybackEvent`（positionMs/durationMs，契约统一毫秒） | `JellyfinPlayStateApi.reportStartMs/ProgressMs/StoppedMs`（providers/jellyfin/playstate/.../JellyfinPlayStateApi.kt:24-80；ms→ticks ×10_000，`JellyfinTicks`） | `FeiniuPlaybackApi.record(FeiniuPlayRecordRequest)`（providers/feiniu/playback/.../FeiniuPlaybackApi.kt:133；**秒**，且必填 item/media/video/audio guid+resolution+bitrate+ts+duration，providers/feiniu/core/.../FeiniuModels.kt:227）→ 仅在 play 协商成功后可上报，上下文取自 `Ready.reportingContext` | ms→s 转换在 04C；`ts`（timestampSeconds）由适配器取当前时钟，契约不带钟 |

## 2. 能力声明矩阵

契约：`AdapterCapabilities`（七轴）+ `AdapterCapability` + `requireCapability`（AdapterCapabilities.kt）。**没有**单一 `supportsUserDataEdits`。

| 能力轴 | Jellyfin（04B 应声明） | 飞牛（04C 应声明） | 真实依据 |
| --- | --- | --- | --- |
| `search` | true | true | Jellyfin `items(searchTerm)`；飞牛专用 `/search/list` |
| `favoritesRead`（能应答 favoritesOnly 列表） | true | true | Jellyfin：`IsFavorite` filter + parentId 缺省递归；飞牛：`/favorite/list` 专用端点 |
| `favoritesFilter`（与其它筛选组合） | true | **false** | 飞牛 `/favorite/list` 请求体仅 `FeiniuItemListTags.type`（FeiniuModels.kt:143），无 genres/watched 组合 |
| `favoritesEdit` | **false** | true | Jellyfin P0 SDK 无 user-data 写端点；飞牛 PUT/DELETE `/item/favorite` |
| `watchedFilter` | true | true | Jellyfin `IsPlayed`/`IsUnplayed`；飞牛 `watchedTag`（FeiniuCatalogApi.kt:45）。两家均无独立"已看列表"端点，"读取"经筛选实现 |
| `watchedEdit` | **false** | true | 同 favoritesEdit 依据 |
| `playbackReporting` | true | true | `/Sessions/Playing*`；`/play/record` |

## 3. 字段级核对（MediaItem 映射输入）

| 域字段 | Jellyfin 来源（BaseItemDto，wire/ItemDtos.kt:42） | 飞牛来源（FeiniuMediaItem，core/FeiniuModels.kt:66；详情 FeiniuItemDetail:113） |
| --- | --- | --- |
| 媒体类型 | `Type: ItemKind`（wire/WireEnums.kt:12，含 Unknown 容错）→ MediaType 逐项映射（Movie/Series/Season/Episode/MusicArtist/MusicAlbum/Audio/PhotoAlbum/Photo/BoxSet/Folder/Video；Person/Genre/Studio/Year 等非目录项→Unknown 或过滤，04B 定） | `type:String`："Movie"→Movie，"TV"→Series，"Directory"→Folder，"Video"→Video，"LiveChannel"→**Unknown**（MediaType 无 Live），其余→Unknown；季/集来自专用端点配合 `season_number/episode_number` |
| 时长 | `RunTimeTicks:Long` → `runtimeMs = ticks / 10_000`（`JellyfinTicks.toMilliseconds`） | `durationSeconds:Long?`→×1000；`runtimeMinutes:Int?`→×60_000；两者并存时以 duration 为准（04C 定） |
| 年份 | `ProductionYear:Int` | **无独立年份字段**。仅 `release_date`/`first_air_date` 字符串（格式未在 P0 wire 定形）→解析出年份，失败置 **null**，不得造 0 |
| 首播/入库时间 | `PremiereDate`/`DateCreated`（ISO→epochMs，IsoInstantEpochMsSerializer） | `ts:Long?`（epochSeconds→×1000，入库语义）；release 日期字符串解析成功→`premiereEpochMs`，失败 null |
| 剧集层级 | `SeriesId`/`SeasonId`/`IndexNumber`/`ParentIndexNumber` | `parent_guid`→`parentId`；`ancestor_guid`→库级，可作 `collectionId`；`season_number`/`episode_number`→同名；**无 seriesId/seasonId guid**（tv_title 仅展示名，MediaItem 无 seriesName 字段→不落域字段，04C 需用 parent/ancestor guid 串联） |
| 用户状态 | `UserData: UserItemDataDto`（ItemDtos.kt:9）：IsFavorite/Played/PlayCount/PlaybackPositionTicks(ticks→ms)/PlayedPercentage/LastPlayedDate(ISO→epochMs) | `watched:Int?`/`is_favorite:Int?`（0/1→Boolean，null→false）；进度：`media_stream.progress:Double?`（百分比）与 `current_time:Double?`（秒→ms）；**无 playCount**→null；`watched_ts`（epochSeconds→`lastPlayedEpochMs`） |
| 图片 | `ImageTags`/`BackdropImageTags` tag + `JellyfinImagesApi.itemImageUrl(itemId, ImageType, maxWidth...)`（providers/jellyfin/images/.../JellyfinImagesApi.kt:15；默认匿名，可 appendApiKey） | `poster:String?`/`posters:List<String>` 经 `FeiniuAssetsApi.imageUrl(baseUrl, path, width=400)`（providers/feiniu/assets/.../FeiniuAssetsApi.kt:14）→`ImageKind.Primary`；`backdrops`→Backdrop。宽度参数属适配器策略（飞牛 w 服务端参数） |
| 人员/类型/分级 | `People:List<PersonDto>`、`Genres:List<String>`、`OfficialRating:String` | P0 wire **无** people 字段→空列表；genres 无列表字段（仅请求端 Int tag）→空列表；`content_ratings:String`→`officialRating` |

## 4. 错误映射意向（04B/04C 落实；契约不新增错误类型）

| Provider SDK 错误 | DomainError 目标 |
| --- | --- |
| `JellyfinConnectionError`/`FeiniuConnectionError` | `NetworkUnreachableError`（或按 kind=Timeout→`TimeoutError`） |
| `*AuthenticationError`（登录拒绝） | `AuthenticationError(InvalidCredentials)` |
| `*AuthenticationExpiredError` | `AuthenticationError(TokenExpired)` |
| `*AuthorizationError` | `AuthorizationError` |
| `*NotFoundError` | `ResourceNotFoundError` |
| `JellyfinRateLimitedError` | `RateLimitedError(retryAfterMs)` |
| `*ServerError`（5xx）/`FeiniuBusinessError` | `ServerError`/按语义（飞牛 code≠0 无对应 5xx 时→`ServerError` 或 `UnknownDomainError`，04C 定并测试） |
| `*DecodeError`/`*RedirectRejectedError` | `ProtocolDecodingError` |
| `*UnsupportedCapabilityError` | 保留于 provider 层；适配器在能力门禁处直接 `requireCapability`→`ClientConfigError` |
| `CancellationException` | **原样传播**，永不映射为空结果或 DomainError（两家 SDK 均已冻结此约定） |

## 5. 已知缺口 / Pending（不编造）

1. **飞牛 search 分页语义**：端点不分页；切片还是拒绝越界 offset，由 04C 定并测试。
2. **飞牛 `episodes(seasonId=null)`**：无整季遍历语义，04C 需显式拒绝或派生。
3. **Jellyfin `totalRecordCount=0` 与"未知"不可区分**：04B 决定 `totalEstimate` 何时置 null。
4. **飞牛年份/首播日期字符串格式**：P0 wire 未定形，需真实 fixture（04C）确认解析规则；在此之前解析失败一律 null。
5. **`MediaSortField` 在飞牛的覆盖面**（`create_time`↔`DateAdded` 之外）：04C 决定降级或拒绝。
6. FakeMediaProviderAdapter 目前在本模块 commonTest；04B-D 需要复用时再提升为 testkit（涉及 settings.gradle.kts 共享变更，届时先报告）。
