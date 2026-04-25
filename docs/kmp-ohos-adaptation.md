# MaFei KMP 适配 OHOS 方案调研

## 1. 目标

项目准备定位为跨平台 Jellyfin 第三方客户端。

当前状态：

- `ohosApp` 已经完成了一部分功能并已上架。
- 后续目标不是共享 UI，而是尽可能共享逻辑层。
- 重点关注：
  - Jellyfin 协议与数据模型
  - 播放源决策
  - 续播、进度上报、历史记录
  - 字幕/音轨默认选择
  - 错误映射、筛选排序、分页、状态机

本调研的核心问题：

1. 是否能把 Kotlin 逻辑层编译为 OHOS 可调用的产物。
2. 是否能自动导出声明文件。
3. 是否处理了线程问题和跨语言数据转换问题。
4. `Kotlin/JS` 和 `Kuikly Kotlin/Native` 哪条路线更适合作为 MaFei 的 OHOS 逻辑层方案。

## 2. 结论摘要

结论先行：

1. 可以参考 Kuikly 开源方案，把 Kotlin 逻辑层编译成 OHOS 可调用的 `.so`。
2. Kuikly 公开方案已经打通了：
   - `ohosArm64` target
   - `.so` 产物
   - C/C++ 头文件导出
   - ArkTS 通过 `N-API` 调用 Kotlin Native 产物
3. 公开资料里可以确认自动导出的主要是 C 头文件，不是 ArkTS 的 `.d.ts`。ArkTS 声明层仍然需要宿主自己维护。
4. 线程问题不是“框架全自动处理”，而是“框架给出线程模型、切换手段和校验机制，业务仍需自己守规矩”。
5. 数据桥接不是任意对象直通。复杂对象、集合、JSON 和二进制的传输规则是分层处理的。
6. `Kotlin/JS` 在 OHOS 上更适合作为历史兼容或 Web/JS 运行时方案参考，不适合作为 MaFei 的主逻辑层底座。
7. 对 MaFei 来说，推荐路线是：
   - Android / iOS / Desktop / Web：继续走标准 KMP
   - OHOS：保持 ArkTS UI + 原生播放器
   - 逻辑层：引入单独的 KMP `shared-core`，通过 Kuikly 风格的 OHOS Kotlin/Native 方案产出 `.so`

## 3. 关键边界

### 3.1 官方 KMP / Kotlin Native 当前没有 OHOS target

JetBrains 官方 Kotlin/Native 支持目标列表中没有 OHOS target。官方文档列出的 target 包含 `iosArm64`、`linuxArm64`、`androidNativeArm64` 等，但没有 `ohosArm64`。

这意味着：

- 不能直接用官方 KMP 配置出标准 OHOS target。
- 如果要让 Kotlin 逻辑层落到 OHOS，本质上必须依赖第三方适配工具链。

来源：

- Kotlin/Native supported targets and hosts  
  https://kotlinlang.org/docs/native-target-support.html

### 3.2 Kuikly 走的是“定制 Kotlin/Native for OHOS”路线

Kuikly 文档明确说明：

- 官方 KMP 模块当前“不支持编译到鸿蒙平台”。
- 为了让 KMP 模块能编译到鸿蒙，需要进行 “鸿蒙 Kotlin/Native 适配”。
- 需要使用 Kuikly 定制的 Kotlin 版本，例如 `2.0.21-KBA-010`。

这说明 Kuikly 不是简单写了一层 Gradle DSL，而是维护了一套非官方 Kotlin 发行链路。

来源：

- Kuikly: KMP模块鸿蒙Kotlin/Native适配  
  https://kuikly.tds.qq.com/DevGuide/kuiklybase-ohos-kn.html

## 4. Kotlin/JS 与 Kuikly Kotlin/Native 对比

### 4.1 Kotlin/JS

JetBrains 官方对 Kotlin/JS 的定义是：

- Kotlin 代码会被转译成 JavaScript
- 运行在“支持 JavaScript 的环境”中

来源：

- Kotlin/JavaScript  
  https://kotlinlang.org/docs/js-overview.html

对 OHOS 来说，这条路线的问题不是“能不能生成 JS”，而是“生成的 JS 落到当前 OHOS 宿主后是否是长期可维护的主路径”。

目前 HarmonyOS NEXT 当前主开发模型是 Stage + ArkTS / ArkUI / ArkCompiler，而不是把 JS 当作新的主工程底座。

来源：

- Stage模型开发概述  
  https://developer.huawei.com/consumer/cn/arkui/arkui-stage/

补充说明：

- 华为开发者站点里可以搜到 HarmonyOS JavaScript codelab，但这些资料主要对应早期 JS/FA 工程体系或特定 SDK 能力集成案例。
- 对当前要做长期主产品的 OHOS 客户端，不应把这些历史 JS 能力视为等价于当前主开发模式。

### 4.2 Kuikly Kotlin/Native

Kuikly 的公开信息可以确认：

- 支持 HarmonyOS
- `core` 模块存在 `ohosArm64Main`
- 该平台输出 `.so`
- Web/H5 小程序则走 `jsMain` 输出 `js`

来源：

- KuiklyUI README  
  https://github.com/Tencent-TDS/KuiklyUI

这说明 Kuikly 已经把两条路线都落地过：

- OHOS：Native binary
- Web/H5：JS binary

对 MaFei 这种播放器重、系统能力重的客户端，OHOS 更接近前者，而不是后者。

### 4.3 适用性对比

| 维度 | Kotlin/JS | Kuikly Kotlin/Native |
| --- | --- | --- |
| OHOS 集成形态 | 依赖 JS 运行时/历史 JS 工程体系/额外封装 | 直接生成 `.so`，ArkTS 通过 `N-API` 调用 |
| 工具链成熟度 | 对 OHOS 缺少明确主路径 | Kuikly 已公开完整接入文档 |
| 与当前 OHOS 应用结构匹配度 | 低 | 高 |
| 计算密集逻辑性能 | 一般 | 更好 |
| 桥接维护成本 | 中 | 中到高 |
| 长期可维护性 | 风险高 | 风险也高，但路径清晰 |
| 适合 MaFei 逻辑层 | 不推荐 | 推荐 |

## 5. 性能判断

### 5.1 先给结论

如果只比较逻辑层执行性能，`Kuikly Kotlin/Native` 大概率优于 `Kotlin/JS`。

但需要补一句：

- 没有找到腾讯、华为或 JetBrains 官方公开的、针对同一 OHOS 工作负载的 `Kotlin/JS vs Kotlin/Native` apples-to-apples 基准测试。
- 所以下面的性能结论是“基于公开架构和运行方式的工程判断”，不是官方基准分数。

### 5.2 为什么 Native 更占优

`Kotlin/JS` 的运行方式是：

- Kotlin 先转成 JavaScript
- 再交给 JS 运行时执行

`Kuikly Kotlin/Native` 的运行方式是：

- Kotlin 编译成 OHOS 可链接的 `.so`
- 由宿主通过 `N-API` / C++ 桥接加载
- 逻辑在 native binary 中执行

对于 MaFei 这类业务，以下场景更偏向 Native：

1. 播放源决策
2. 大量媒体流信息清洗
3. 字幕/音轨筛选
4. 复杂状态机
5. 规则判断和列表处理
6. 后续可能扩展的解析、缓存、索引类逻辑

### 5.3 什么时候 Native 优势会被吃掉

Native 不是无脑更快。

如果接口设计很差，优势会被桥接开销抵消。最典型的反模式是：

1. ArkTS 每个小字段都跨一次桥
2. UI 列表渲染过程中高频同步调用 Native
3. 把 Native 当“属性 getter 集合”

这样一来，性能瓶颈不在逻辑执行，而在频繁的 `ArkTS <-> N-API <-> Native` 边界穿越。

所以如果走 Native 方案，接口必须粗粒度：

- 一次输入 JSON
- 一次返回 JSON
- 或者一次传简单数组/二进制

不要做细粒度对象桥接。

### 5.4 对 MaFei 的实际性能判断

对 MaFei 来说，更重要的不是“单纯算力快多少”，而是：

1. 能否在 OHOS 里稳定复用逻辑层
2. 能否把复杂业务规则从 ArkTS 中抽走
3. 能否把桥接层保持很薄

在这个前提下：

- `Kotlin/Native` 的综合收益明显高于 `Kotlin/JS`
- 性能只是顺带收益，不是唯一理由

## 6. Kuikly 公开方案验证点

### 6.1 是否能编译成 OHOS 可调用的 `.so`

可以。

Kuikly 文档明确要求：

- 在 KMP 模块中添加 `ohosArm64()` target
- 如果需要单独产出供鸿蒙工程接入，则在 `ohosArm64` 下配置 `binaries.sharedLib()`
- 之后即可单独打包 so 产物和头文件

来源：

- Kuikly: KMP模块鸿蒙Kotlin/Native适配  
  https://kuikly.tds.qq.com/DevGuide/kuiklybase-ohos-kn.html

Harmony 接入文档还给出了实际构建命令：

```bash
./gradlew -c settings.ohos.gradle.kts :shared:linkOhosArm64
```

并说明会生成：

- `libshared.so`
- `libshared_api.h`

来源：

- Kuikly: 鸿蒙工程接入  
  https://kuikly.tds.qq.com/QuickStart/harmony.html

### 6.2 是否会自动导出声明文件

分两层看。

#### 6.2.1 C/C++ 头文件

会。

公开文档明确把 `libshared_api.h` 当作构建产物接入宿主 C++ 层使用。

来源：

- Kuikly: 鸿蒙工程接入  
  https://kuikly.tds.qq.com/QuickStart/harmony.html

#### 6.2.2 ArkTS / TypeScript `.d.ts`

当前公开资料看，**不会自动生成完整 ArkTS 声明**。

Harmony 接入文档里，`index.d.ts` 是宿主自己写的，例如：

```ts
export const initKuikly: () => number;
```

也就是说：

- 自动导出的是 native ABI 头文件
- ArkTS 暴露层仍由宿主 `N-API` 包装后自行维护 `.d.ts`

这点对 MaFei 很关键，因为这意味着：

- 不能指望“编译完 Kotlin 模块自动生成 ArkTS SDK”
- 需要自己维护稳定且尽量少的 ArkTS bridge API

## 7. 线程问题

### 7.1 Kuikly 是否“处理了线程问题”

处理了一部分，但不是完全托管。

Kuikly 的线程模型公开写得很明确：

1. 所有 Kuikly UI 操作只能在 `Kuikly线程` 执行。
2. `Module` 机制可以调用 Native，并利用平台多线程能力。
3. 如果使用 KMP 多线程，回到 Kuikly/UI 线程需要主动切换。

来源：

- Kuikly: 协程和多线程编程指引  
  https://kuikly.tds.qq.com/DevGuide/thread-and-coroutines.html

### 7.2 官方文档给出的线程结论

Kuikly 文档中有几个很关键的信息：

1. `Module机制` 与 `KMP多线程` 的对比里写明：
   - Module 方式切回 Kuikly 线程由框架自动完成
   - KMP 多线程方式需要主动切换
2. `kotlinx` 协程方式需要开发者自己考虑线程安全
3. `KMP多线程不支持动态化`
4. 开发者可以启用 `Pager.VERIFY_THREAD` 和 `Pager.VERIFY_REACTIVE_OBSERVER` 做线程安全校验

来源：

- Kuikly: 协程和多线程编程指引  
  https://kuikly.tds.qq.com/DevGuide/thread-and-coroutines.html

### 7.3 对 MaFei 的落地含义

如果 MaFei 只复用逻辑层，不复用 Kuikly UI，那么线程问题会简单很多，但仍然不能忽略：

1. KMP core 内部的并发安全要自己保证
2. ArkTS 调 Native 的边界要尽量使用异步粗粒度接口
3. 不要把 UI 线程相关对象泄漏到 KMP core
4. 所有返回 ArkTS 的回调都要明确切换回宿主合适线程

对 MaFei 来说，最稳的方式是：

- `shared-core` 作为纯逻辑层，不持有 UI 对象
- 通过单次请求/响应式接口进行桥接
- 把并发集中在 Kotlin core 内部处理

## 8. 跨语言数据类型转换

### 8.1 Kuikly 是否有现成规则

有，而且规则相对明确。

Kuikly 文档列出的 Harmony Native 侧支持类型包括：

- `String`
- `Int`
- `Long`
- `Float`
- `Double`
- `Bool`
- `ByteArray`
- `Array`
- `Map/Record`

来源：

- Kuikly: 扩展原生API  
  https://kuikly.tds.qq.com/DevGuide/expand-native-api.html

### 8.2 序列化规则

文档给出的规则要点：

1. 基础类型：直接透传
2. 二进制：直接透传
3. `JSONObject` / `JSONArray`：按 JSON 字符串传输
4. `Map/Record` / `List` / `NSArray` / `Array`：按 JSON 字符串传输
5. 如果走 `JSONObject` 传参路径，序列化过程不支持 `ByteArray`

来源：

- Kuikly: 扩展原生API  
  https://kuikly.tds.qq.com/DevGuide/expand-native-api.html

### 8.3 这意味着什么

这意味着它不是“任意对象图自动双向绑定”，而是：

- 标量和二进制支持比较直接
- 集合和 JSON 结构大多会退化为字符串边界

对 MaFei 这是好事，因为这反而能逼着接口设计变得稳定。

推荐的边界规范：

1. `String / Int / Long / Boolean / Double`
2. `ByteArray`
3. `JSON String`

不推荐：

1. 高频小对象调用
2. 深层嵌套复杂对象直接桥接
3. 细粒度 getter / setter 风格的桥接 API

## 9. 主要坑

### 9.1 非官方工具链

这是最大风险。

风险点：

1. Kotlin 升级要跟腾讯分支兼容
2. Harmony SDK 升级要回归验证
3. 构建环境和 CI 更复杂
4. 新人理解门槛更高

### 9.2 ArkTS 声明层不是自动生成

需要自己维护：

- `N-API` 注册代码
- `.d.ts`
- ArkTS 包装层

### 9.3 跨语言边界很容易设计坏

如果一开始接口过细，后面会出现：

1. 性能差
2. 改动大
3. 难调试
4. 宿主和 Kotlin core 强耦合

### 9.4 线程与动态化限制

Kuikly 文档明确说：

- `KMP多线程` 不支持动态化
- 如果使用 `knoi` 插件做鸿蒙代码互调，也会影响动态化能力

对 MaFei 来说，动态化不是刚需，这一点可以接受，但要提前知晓。

### 9.5 调试链路更长

问题定位会跨 4 层：

1. ArkTS
2. N-API / C++
3. Kotlin Native ABI
4. shared-core 业务逻辑

所以第一阶段必须把 bridge 接口压缩到最少。

## 10. 对 MaFei 的推荐方案

### 10.1 总体架构

推荐采用“三层拆分”：

1. `shared-core`
   - 纯 Kotlin KMP 逻辑层
   - Android / iOS / Desktop / Web 可直接复用
   - OHOS 通过 Kuikly 风格的 Native 产物接入

2. `ohos-bridge`
   - Native C/C++ 层
   - 负责 `N-API` 暴露
   - 负责 ArkTS 与 Kotlin Native 间的边界转换

3. `ohosApp`
   - ArkTS UI
   - AVPlayer / mpv / 系统能力
   - 只调用桥接后的高层接口

### 10.2 共享层第一批适合迁移的能力

优先迁移：

1. Jellyfin DTO -> 领域模型映射
2. 播放器引擎决策
3. 播放源选择与回退策略
4. 直连 / 转码 / ISO / 字幕烧录判定
5. 字幕与音轨默认选择
6. 进度恢复与上报请求组装
7. 错误分类与用户可读提示生成

不建议第一批迁移：

1. UI 状态管理
2. ArkTS 页面路由
3. 播放器控制器本身
4. 直接网络请求适配层

网络层是否共享，要放到第二阶段再决定。第一阶段先把“算法型、规则型、映射型”逻辑迁走。

### 10.3 bridge 接口设计原则

推荐用粗粒度 API：

```text
resolvePlaybackPlan(inputJson: String): String
selectTracks(inputJson: String): String
buildPlayStatePayload(inputJson: String): String
mapItemDetail(inputJson: String): String
```

不要做：

```text
getSubtitleCount()
getSubtitleAt(index)
getAudioTrackName(index)
isDirectPlaySupported()
...
```

原因：

1. 边界清晰
2. 好调试
3. 好做版本兼容
4. 桥接次数少
5. 更容易写单元测试

## 11. PoC 建议

### 11.1 第一阶段目标

先验证技术路线，不要一上来大迁移。

PoC 目标：

1. 新建一个最小 `shared-core`
2. 配出 `ohosArm64 + binaries.sharedLib()`
3. 编出：
   - `libshared_core.so`
   - `libshared_core_api.h`
4. 在 `ohosApp` 中通过 `N-API` 暴露一个接口
5. ArkTS 成功调用 Kotlin 逻辑函数

### 11.2 第一批 PoC 函数建议

建议只做一个：

```text
resolvePlaybackPlan(json: String): String
```

输入：

- Jellyfin item / mediaSource / device capability / user preference

输出：

- engine
- playMethod
- finalUrlStrategy
- subtitleStrategy
- audioStrategy
- reasons

这个函数正好符合 MaFei 现状：

1. 规则复杂
2. 可测试性强
3. 与 UI 解耦
4. 对 OHOS / Android / iOS 都有价值

## 12. 对 Kotlin/JS 的最终结论

如果只是做一个实验性原型，`Kotlin/JS` 不是完全不能碰。

但如果目标是：

- 长期维护
- 真正承载 MaFei 的 OHOS 逻辑层
- 后续扩展到 Jellyfin 全功能

那么不建议把 `Kotlin/JS` 作为 OHOS 主路线。

原因不是它绝对不能跑，而是：

1. 当前 OHOS 主路径不是围绕 Kotlin/JS 建立的
2. 对系统播放器、Native bridge、性能和长期维护都不占优
3. 公开且完整的工程化参考，明显是 Kuikly 的 Native 路线更成熟

## 13. 最终建议

建议采用以下策略：

1. 不共享 UI
2. 只共享逻辑层
3. 使用标准 KMP 编写共享逻辑
4. 对 OHOS 使用 Kuikly 风格的 Kotlin/Native 适配链路输出 `.so`
5. ArkTS 继续负责：
   - UI
   - 路由
   - AVPlayer / mpv
   - 系统能力
6. ArkTS 与 Kotlin core 之间仅保留少量粗粒度 JSON/标量 API

这是当前可行性、性能、维护成本三者之间最平衡的方案。

## 14. 参考资料

### Kotlin / JetBrains

- Kotlin/Native supported targets and hosts  
  https://kotlinlang.org/docs/native-target-support.html
- Kotlin/JavaScript  
  https://kotlinlang.org/docs/js-overview.html

### Huawei / HarmonyOS

- Stage模型开发概述  
  https://developer.huawei.com/consumer/cn/arkui/arkui-stage/
- HarmonyOS 3.1/4.0 及以下归档  
  https://developer.huawei.com/consumer/cn/archive/

### Tencent / Kuikly

- KuiklyUI GitHub  
  https://github.com/Tencent-TDS/KuiklyUI
- KuiklyBase-kotlin GitHub  
  https://github.com/Tencent-TDS/KuiklyBase-kotlin
- ovCompose sample  
  https://github.com/Tencent-TDS/ovCompose-sample
- KMP模块鸿蒙Kotlin/Native适配  
  https://kuikly.tds.qq.com/DevGuide/kuiklybase-ohos-kn.html
- 鸿蒙工程接入  
  https://kuikly.tds.qq.com/QuickStart/harmony.html
- 协程和多线程编程指引  
  https://kuikly.tds.qq.com/DevGuide/thread-and-coroutines.html
- 扩展原生API  
  https://kuikly.tds.qq.com/DevGuide/expand-native-api.html
