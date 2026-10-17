# Jellyfin 音乐预留模块

此模块目前没有源码，也未被 facade 接入。现有音乐浏览复用 `providers/jellyfin/catalog`、`data/adapter-jellyfin` 与 `feature/music`，模块存在不表示独立音乐 SDK 已完成。只有形成独立 Provider API 职责后才在此实现并接入 facade，不复制已有目录查询。

这是显式预留位，不是待自动填充的 TODO。暂不删除，以免把构建结构整理扩大为功能或依赖语义调整。
