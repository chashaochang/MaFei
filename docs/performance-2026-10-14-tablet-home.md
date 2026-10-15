# 平板横屏首页滚动回归（2026-10-14）

## 定位与修正

SLG-W10 横屏首页按媒体库渲染多排 LazyRow。原实现给每张挂载海报注册 sharedBounds，包含不参与当前导航的卡片；整排离开视口时会集中释放转场图层和相关状态。CPF animation 的 SharedContentNode 在 attach/reset 创建 GraphicsLayer，detach 释放；CPU profile 也包含 Lookahead/Placement 和图层绘制调用。

首页现在只为实际点击的卡片注册共享转场。来源包含所属列表，避免同一媒体同时出现在继续观看、最近添加或不同媒体库时重复注册。选中的来源用 rememberSaveable 保存，返回时仍与详情匹配。播放动作、数据、滚动位置、玻璃材质、图片缓存和布局保持不变。

## 真机同模式对比

同一设备、2800×1840 横屏、Native Debug、Skia，玻璃效果始终开启。每轮先往返预热，再进行三组各四次竖向滑动；每次动作前重新读取布局树 bounds。CPF 滚动中偶尔不输出虚拟 List 树，此时只使用同次树中的唯一原生 Compose XComponent 有效区域，不估算截图坐标。

| 指标（ms） | 修改前 | 修改后第一轮 | 修改后第二轮 |
| --- | ---: | ---: | ---: |
| onForgotten 均值 / P95 | 2.806 / 13.709 | 0.413 / 3.453 | 0.481 / 3.958 |
| deactivate 均值 / P95 | 2.024 / 2.688 | 0.526 / 0.658 | 0.599 / 0.981 |
| 绘制回调均值 / P95 | 3.735 / 6.475 | 3.735 / 5.995 | 3.964 / 6.216 |
| 绘制回调最大值 | 61.964 | 25.510 | 47.504 |
| 应用 buffer 提交间隔 P95 | 13.401 | 11.046 | 12.241 |

两次复测的列表回收尾部耗时明显下降，支持减少闲置转场图层有效；绘制均值没有稳定下降，第二轮仍有 47.5ms 峰值，不能宣称整页零卡顿。普通重组均值为 0.65–0.71ms，不能仅凭重组数量认定页面性能瓶颈。

测量的是应用回调与 buffer 提交间隔，不是显示 FPS 或 GPU 完成时间。布局树读取、停止滚动和触及边界会形成秒级提交空窗，不能将其解释为单帧卡顿。未改变 Debug/Release 模式，也不将另一次同时运行 hiperf 的样本用于严格前后对比。

原始 trace（本机诊断文件，未纳入仓库）：

- `/private/tmp/mafei-home-landscape-baseline.ftrace`，PID 6735。
- `/private/tmp/mafei-home-landscape-after.ftrace`，PID 31008。
- `/private/tmp/mafei-home-landscape-after-repeat.ftrace`，PID 31008。

分析命令：`node scripts/analyze-harmony-frame-trace.mjs <trace> <pid>`。

## 验证范围

- 新增 3 项回归：手机/宽屏闲置海报不注册、实际 SharedTransitionLayout 中点击与返回仍匹配、重复媒体只注册点击来源。两项闲置测试在修复前失败，修复后全过。
- 当前工作区首页 desktopTest 56 项与共享 UI 定向转场测试 3 项通过；Android 编译、Native Debug 发布、verifyArchitecture、HAP 构建和同次 libkn/header/资源核对通过。
- HAP 覆盖安装到平板，两轮横屏首页滚动完成，未清数据或退出账号。手机实际滚动、浅色矩阵和横向 shelf 性能未在本轮验收；不宣称 UI 全矩阵完成。
- 工作区原有的 FloatingTabs、转场展示范围及其他任务改动保持原状，本提交仅包含首页按点击来源注册的性能逻辑、回归测试和本记录。构建/设备测量基于当前工作区，不代表其他待提交改动已获审查。
