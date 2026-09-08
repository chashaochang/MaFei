# 04D 媒体 Repository（Codex 接手）

基线：04C 主线修正 `001a452`。承接已批准的 GLM-04 分批计划；只实现内存媒体查询、缓存和分页核心，不含凭证持久化、迁移或 UI 装配。

## 入口与边界

- `MediaRepository.open(adapter)` 返回 `MediaSession`；Provider/Server/Account 与每次选择的 generation 共同标识作用域。即使重复打开同一账号也产生新句柄，旧句柄取消。04E 在登录/恢复后创建账号独立的客户端和 Adapter，退出时关闭句柄，不将共享 facade 切换账号后继续复用旧 Adapter。
- `browse(session, MediaQuery, PageRequest, refresh)` 覆盖 items/search/seasons/episodes；collections/item 使用独立强类型缓存。BrowseMediaUseCase/GetMediaDetailUseCase 是 Feature 注入入口，UI 不创建 SDK。
- 缓存键包含句柄、查询类型、库/系列/季/搜索词及完整分页/筛选/排序。搜索词仅 trim，不做大小写折叠；集合按值相等，并复制调用方可变集合，游标不解析。
- `MediaPageAccumulator` 是单个查询和句柄的调用方所有对象；按返回游标顺序合并，按媒体 ID 稳定去重，重复条目更新内容不改变顺序。乱序、循环游标、跨服务器合并均拒绝且不破坏上一个结果。刷新/切换查询或账号必须 reset/重建。

## 缓存和并发

- 默认 fresh TTL 60 秒，旧缓存最长 300 秒；每种缓存最多 256 项，均可配置。墙钟回拨/差值溢出不视为新鲜缓存。
- 返回值显式携带 Network/FreshCache/StaleCache；StaleCache 保留刷新错误供 UI 展示。回退白名单仅 NetworkUnreachableError、TimeoutError、明确 HTTP 5xx 的 ServerError。认证、授权、取消、解码、配置及无 HTTP 状态的业务错误不降级。
- 认证/授权错误清除当前内存缓存并使此前在途结果失效，不会随后命中之前的新鲜缓存掩盖错误。
- Mutex 仅保护短状态转换，网络不持锁；同键每次网络请求有序号，先发后至的旧请求不能覆盖新结果。open/close/invalidate 切换代次或修订，旧成功与旧失败均不得返回/缓存/回退。
- 取消原样传播；即使 Adapter 吞掉取消并返回值，发布前也检查协程状态。finally 只在短 NonCancellable 区段移除本次请求登记，不包含网络或持久化。
- 飞牛详情缺少用户状态，合并本会话近期 items/search 结果的 userData；这是有时效的最近观察值，不冒充服务端详情字段。新列表状态使对应详情缓存失效。Jellyfin 详情保留其自身 userData。
- 后续业务写入需在适当边界调用 invalidate；此批没有自动重试、数据落盘、后台刷新任务或登录副作用。

## 构建范围

只改 `data/repositories` 模块构建：API 依赖既有 `common:core` 复用 EpochClock/协程，未新增 Maven 坐标、模块、共享 Gradle。JVM 并发测试源映射到 desktopTest/androidUnitTest。

## 本轮验证

- 测试先于实现添加；首次红灯为新 API 未实现导致编译失败，不能称为行为缺陷红灯。
- commonTest 22 项 + JVM 调度/取消 2 项，Desktop/Android JVM 各 24/24 实际执行通过。
- Repository iOS/OHOS 编译强制执行通过；2 项 JVM 调度测试不计作 Native 实际并发验证。
- 联合回归：公共 Adapter 契约各 36/36、Jellyfin Adapter 各 49/49、Feiniu Adapter 各 44/44；verifyArchitecture、git diff --check 通过。
- 日志：`/private/tmp/mafei-04d-work.IWLJ1R/final.log`。使用合成 Adapter/确定性时钟，未完成真机网络、真实服务器、进程恢复或 UI 联调。

后续：04E 会话与安全凭证、04F 迁移核心和 GLM-05 播放器由独立子 Agent 工作；主线审查后合入，不因本批通过而宣布 GLM-04 全部完成。
