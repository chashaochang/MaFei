# 飞牛适配器修改入口

公开契约仍是 `FeiniuMediaProviderAdapter`。它只装配下列内部实现，并保持
`MediaProviderAdapter` 的参数、返回值与默认不支持能力；Provider SDK 不向 Feature 暴露。

| 修改目标 | 实现文件 | 独立状态 |
| --- | --- | --- |
| 身份过期保护、错误翻译、图片地址与类别字典 | `FeiniuAdapterSession.kt` | 绑定的身份代际、类别缓存 |
| 库/搜索/收藏/人物作品分页、季集及下一集、媒体操作 | `FeiniuCatalog.kt` | 无独立已发布媒体状态 |
| 首页并发请求、各库最新、推荐信号 | `FeiniuHomeFeed.kt` | 账号级推荐缓存及过期时间 |
| 详情、父剧集回溯、轨道元数据、继续观看补全 | `FeiniuMediaDetails.kt` | 无独立已发布媒体状态 |
| 原画/STRM/转码协商、字幕、播放上报 | `FeiniuPlayback.kt` | 本实例准备过的流句柄 |
| 推荐排序、按账号与日期生成稳定种子 | `FeiniuRecommendationPlanner.kt` | 纯算法 |
| 列表/详情候选字段合并、隐藏直播库判断 | `FeiniuCatalogMapping.kt` | 纯映射 |

每个适配器实例只有一个 `FeiniuAdapterSession`；请求继续在前后检查身份，取消继续传播。
目录和首页共用同一个详情补全实现，推荐缓存与已准备流由各自服务持有，不复制状态。

验证：`./gradlew :data:adapter-feiniu:desktopTest verifyDependencyBoundaries`。
现有测试覆盖线协议、分页、身份替换、推荐、父子关系、转码和播放上报；编译与模拟响应
测试不等于真实服务器或设备播放验收。
