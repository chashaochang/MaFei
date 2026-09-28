# 播放器进度预览设计

## 目标

恢复旧鸿蒙播放器拖动进度条时显示当前帧预览的行为。预览必须来自真实媒体帧；没有预览数据时继续显示现有的时间和进度反馈，不伪造封面或占位画面。

## 范围与边界

- Jellyfin：优先使用服务器生成的 Trickplay tile。播放器拖动时按媒体源和时间计算 tile sheet 与单帧位置。
- Feiniu/其他 Provider：本批只保留无预览降级，不把播放 URL 当作可取帧地址。
- OHOS 原生直连取帧：保留为后续平台桥接任务；本批不把 ArkTS PixelMap 强行穿过 CMP 公共契约。

## 数据流

`MediaProviderAdapter.previewFrame(item, positionMs, mediaSourceId)` 返回经过 Provider 鉴权的 JPEG tile 与裁剪元数据；`MediaRepository` 仅转发当前会话调用。Compose App 将它映射为播放器 API 的无 Provider 类型预览帧，播放器 UI 在拖动时节流、取消过期请求并绘制裁剪区域。

## 失败与性能

- Trickplay 元数据缺失、服务器 404、解码失败或 Provider 不支持均返回 `null`，不影响 seek。
- 元数据和 tile sheet 在适配器实例内按 item/source/width 缓存；同一 sheet 的连续拖动不重复下载。
- UI 仅在拖动时请求，使用短 debounce；释放进度条时清除预览状态。
- 预览卡片继续使用现有播放器黑色反馈表面，不新增 Material 卡片或玻璃容器。

## 验收

覆盖 tile 坐标计算、边界时间、未知/空 Trickplay、缓存命中和 Provider 404 降级；通过 Desktop/Android 单测与编译门禁。视觉验收关注拖动中真实帧、释放后反馈消失及无 Trickplay 时原有时间反馈不变。
