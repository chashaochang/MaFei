# 浏览数据的职责边界

`MediaBrowser` 是兼容现有调用方的薄门面，不再承载请求实现。每个账号创建一套控制器；账号替换时整套销毁。

| 修改目标 | 实现入口 | 独立拥有的运行状态 |
| --- | --- | --- |
| 首页、媒体库、首页分库最新 | `CatalogBrowser` | 初始化标记、库分页、首页与分库请求序号 |
| 搜索 | `SearchBrowser` | 请求序号、800 项结果窗口 |
| 收藏列表 | `FavoriteBrowser` | 请求序号、分页累加器 |
| 详情、季与集返回恢复 | `DetailBrowser` | 详情序号、刷新序号、季快照 |
| 播放版本、轨道与队列输入 | `PlaybackSelectionBrowser` | 账号级播放偏好的访问 |
| 人物与作品 | `PersonBrowser` | 人物请求序号 |
| 播放历史 | `HistoryBrowser` | 两路分页、重试游标、剧集补全缓存 |
| 已观看、收藏、删除、权限探测 | `MediaActionsBrowser` | 每条媒体/操作的互斥状态 |
| Tab 文档切换与恢复 | `BrowserTabContexts` | 切换代次；各领域通过能力接口接入 |

`BrowserContext` 只提供 Repository、MediaSession、能力和统一的可观察读模型，以及账号认证错误通道。
不得向它加入页面、路由、播放器实例或其他控制器。控制器不保存第二份已发布列表。
`MediaBrowserState` 暂时保持原有公开字段，以维持调用兼容；字段的请求与缓存归属以上表为准。

跨页面的收藏、已观看和删除事实统一经 `BrowserMediaFacts` 更新。详情操作只通过一个只读的
详情请求序号回调判断结果是否过期；不得为各页面建立互不相通的实体副本。

## 保活 Tab 的数据文档

`switchTabContext(key, retainedKeys)` 与导航切换在同一个无挂起回调中执行；传入导航最终保留的根，
在容量驱逐前先丢弃已退出的临时根，避免它挤掉仍然保活的常用根。
详情、季返回上下文、人物、库分页/排序、搜索属于各 Tab；首页、收藏、历史与账号事实共享。
当前文档仍使用 `MediaBrowserState` 的平面字段，最多三个后台文档放在同一模型的 `tabDocuments`，
合计最多四槽。`stateForTab(key)` 是同一个 `StateFlow` 的只读投影，没有额外 store 或订阅任务；
保活 entry 必须订阅自己根的投影，不能直接拿当前前台文档展示。

导航更新保留根后调用 `retainTabContexts(keys)`，同步释放临时根及对应分页累加器。
控制器仍拥有请求序号和分页游标历史；切换只推进请求代次，不能恢复旧代次。
旧请求无论成功、异常或取消都不能修改新 Tab。未完成请求在文档内标记，返回前台后由
`resumeTabContext()` 按各领域重试；已完成文档在 Repository revision 未变时不重新请求。
Mine 等覆盖层也可能在同一 Tab 内取消恢复任务；当前代次取消时，控制器必须保留 retry 标记并清除
对应 loading，不能只依赖切换时的快照捕获。显式 clear/back 或替代请求通过代次校验排除旧重试。
前台根宿主统一触发恢复，entry 仅在目标 id 与已有文档不匹配时首次加载，禁止每次 active 都重新 open。

Repository 的收藏、观看、删除或已确认播放 Stop 使 revision 变化；恢复时保留现有内容并刷新，
同时丢弃可能含旧播放进度的季返回快照。`BrowserMediaFacts` 的实体变更同时遍历前后台文档与
季返回文档，在途操作成功也要更新其原实体；只把 busy/error 的清理限制在操作所属代次。
分页累加器只保留游标遍历，合并后必须尊重统一模型里的用户事实和已删除条目。

调用与状态提交仍由 UI dispatcher 串行执行；网络操作可挂起，原有请求序号拒绝过时结果。
取消异常必须继续传播；认证失败只进入账号恢复通道，不能把服务器原始错误写进内容列表。

验证入口：本模块 `src/jvmTest/.../browser/` 的行为测试。新增场景优先验证请求竞争、分页、
返回恢复、跨列表同步和失败回滚，而不是断言文件长度或复刻内部函数结构。
Tab 回归入口：`BrowserTabDocumentsTest`、`BrowserTabConcurrencyTest`、`BrowserTabResumeCancellationTest`。
