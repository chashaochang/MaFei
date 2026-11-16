# Emby 与 Plex 接入验证（2026-11-16）

基于 `df8b0f56259570cad01198e55481f3a609532e4d` 的完整工作区。
工作区中已有的 HarmonyOS 打包配置和独立播放器窗口修改不属于本次提交；
下列测试与编译结果来自完整工作区，不等同于隔离检出的提交验收。

## 参考与实现

- [Tigerest-Theater](https://github.com/Tigerest/Tigerest-Theater)，核对版本
  `b0081a9852847d5f0d5e81075f5be921fdc707d8`，用于对照 Emby 客户端实现。
- [Plezy](https://github.com/edde746/plezy)，核对版本
  `02eeba69723b33f28e6b8ae9dab2ce1c02a1c3cd`，用于对照 Plex 授权、
  资源发现、媒体检索和继续观看协议。生产代码使用本工程的 Kotlin 传输与适配层，
  未引入 Flutter 运行时。两个服务图标的来源、转换与许可证见
  [资源说明](../../common/ui/PROVIDER_ASSETS.md)。
- Emby 在现有 Jellyfin 协议层中明确区分服务类型，适配认证头、最低版本、
  用户作用域目录路由与相似条目响应；账号保存、恢复、封面和媒体来源保留 Emby 身份。
- Plex 新增 `providers/plex/core` 与 `data/adapter-plex`，接入现有
  `MediaProviderAdapter` 和统一媒体读模型；没有在页面中增加独立请求、分页或缓存。

## 本轮支持范围

- 登录页与已保存账号支持飞牛、Jellyfin、Emby、Plex；Plex 使用浏览器 PIN 授权、
  可取消轮询、服务器选择与连接地址回退。
- Plex 在发送服务器令牌前匿名核对服务器身份；只持久化所选服务器令牌，
  已保存账号恢复不请求 Plex 云端。日志隐藏 Plex 凭据，凭据重定向保护包含
  `X-Plex-Token`，媒体与封面地址检查同源及反向代理基础路径。
- Plex 支持视频库、搜索、详情、季集、最新内容、继续观看、观看历史与已看状态更新；
  混合类型目录使用每种类型独立的原始偏移和绑定查询的游标合并分页。
- Plex 支持原文件播放、音轨与字幕选择、毫秒时间轴上报；
  当前明确拒绝需要服务端转码及多分段的视频，未声明具备转码能力。
- Emby 复用 Jellyfin 的视频浏览与播放流程。Emby 与 Plex 均未启用
  服务端管理、删除、音乐、照片或直播能力；内嵌管理网页仍仅用于 Jellyfin。

## 自动测试与编译

以下命令退出码为 0：

```sh
./gradlew :data:adapter-jellyfin:desktopTest :data:adapter-plex:desktopTest :providers:plex:core:desktopTest :providers:jellyfin:core:desktopTest :providers:jellyfin:catalog:desktopTest :providers:jellyfin:discovery:desktopTest :core:http-contract:desktopTest :feature:connect:desktopTest :composeApp:desktopTest :composeApp:testDebugUnitTest :composeApp:compileKotlinIosArm64 :composeApp:compileKotlinOhosArm64 --continue --console=plain
node --test scripts/*.test.mjs scripts/*.test.cjs
```

- Plex 协议测试 6 项、适配器测试 11 项、服务连接界面测试 3 项通过；
  其余上述模块的行为测试通过。Node 脚本测试 147 项通过。
- 覆盖授权取消与过期、连接回退与身份不匹配、账号恢复、媒体映射、
  混合类型分页、凭据隐藏、Emby 路由和响应兼容等行为。
- Desktop 与 Android JVM 测试通过；iOS Arm64、OHOS Arm64 Kotlin 编译通过。
  编译结果不代表已生成或安装对应平台应用。
- 连接界面离屏测试覆盖 390dp/900dp、深浅色、长服务器名、18 项服务器列表、
  授权阶段切换与按压时控件边界稳定。已生成并检查离屏图；
  这些证据不代替模拟器、真机或用户视觉验收。

## 完整门禁

```sh
./gradlew :check --continue --console=plain
```

退出码为 1，唯一失败任务是 `verifySourceSize`：

| 文件 | 实际行数 | 限额 | 与本次改动的关系 |
| --- | ---: | ---: | --- |
| `player/ui/src/sharedUiMain/kotlin/cn/mafei/cmp/player/ui/NowPlayingScreen.kt` | 1096 | 965 | 已有未提交修改，本次未修改 |
| `composeApp/src/ohosMain/kotlin/cn/mafei/cmp/OhosPlayerHost.kt` | 893 | 885 | 基线已超限，本次未修改 |

应用边界、模块依赖、项目依赖、架构、反馈策略、无 SVG 资源和无原生 Material
组件等其余根门禁通过。没有放宽行数限额或增加例外。

## 待实际环境验收

本轮没有登录真实 Emby/Plex 服务器，没有安装应用到模拟器或真机，
没有完成实际网络、媒体播放、长时间音画同步和进度回写验收，
也没有触发远端 CI 或发布。当前结论是接入源码、模拟协议回归、
离屏界面测试与所列平台编译已完成，不能据此声明服务器或设备验证通过。
