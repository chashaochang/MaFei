# 本地播放器依赖

`MFPlayer` 是 MaFei 正式播放器使用的闭源本地 SDK，二进制不包含在公开仓库内。

拥有合法 SDK 的内部开发环境可将其放置为：

```text
ohosApp/entry/libs/mfplayer.har
```

随后重新执行 ohpm 同步。`libs/*.har` 已被 Git 忽略，禁止将 SDK、构建产物或其他私有二进制提交到公开仓库。
