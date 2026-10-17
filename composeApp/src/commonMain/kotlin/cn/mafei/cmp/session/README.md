# 进程与账号服务

`ApplicationRuntime` 保留平台入口使用的公开门面，装配既有 `ApplicationSessions`、
账号登录、连接策略与下列内部服务。页面绑定继续只接收实际需要的能力，不传整个 Runtime。

| 修改目标 | 状态所有者 |
| --- | --- |
| 登录、账号准入、切换与账号历史 | `ApplicationRuntime` / `ApplicationSessions` |
| 当前账号播放器复用、前后台事件与退役释放 | `ApplicationPlaybackRegistry` |
| 搜索历史、外观强度、首页轮播、渲染模式、媒体源选择持久化 | `RuntimePreferences` |
| 一次性旧库迁移、迁移收据、旧密码补全与退出后禁止恢复 | `ApplicationLegacyMigration` |
| 播放器用户偏好及其可观察状态 | `PlayerPreferencesStore` |
| 单个账号的播放会话 | `SessionPlaybackRuntime` |

播放器注册表是唯一的实例映射所有者。迁移仍经同一个 `ApplicationSessions.activate`
准入，不新建账号状态。偏好与迁移服务不启动独立生命周期；进程 scope 与传输关闭仍由
Runtime 管理。连接偏好在播放停止后更新，继续通过注册表查询现有实例，不为查询创建播放器。

定向回归：`ApplicationRuntimePlaybackTest`、`ApplicationRuntimeAccountHistoryTest`、
`FeiniuConnectionPreferenceRuntimeTest`、`PlayerPreferencesTest` 及迁移行为测试。
