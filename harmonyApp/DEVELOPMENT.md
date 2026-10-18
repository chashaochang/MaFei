# 开发包与正式包

`AppScope/app.json5` 声明正式包名 `cn.xiaobai.mafei`。
根 `hvigorfile.ts` 按构建模式在内存中选择包名，适用于 CLI 和 IDE 构建：

| 构建模式 | 包名 |
| --- | --- |
| debug（本地开发默认） | `cn.xiaobai.mafei.dev` |
| release | `cn.xiaobai.mafei` |

不要为了开发包永久修改 AppScope 的正式包名。两者使用独立应用沙箱，
开发版首次安装需要重新登录，不自动读取正式版的账户或数据。

## 本地签名

在 DevEco 中为 `cn.xiaobai.mafei.dev` 配置匹配当前手机的调试 profile，
在本地 `build-profile.json5` 的 `app.signingConfigs` 中命名为 `development`。
Debug 自动选择它；Release 保留产品原来的 `signingConfig`。
没有本地签名材料的干净检出仍可生成未签名 HAP。

正式包和开发包的证书、profile、密钥文件应分开存放，避免 IDE 自动生成
签名时复用同一路径并覆盖另一份材料。DevEco 签名页可能同时修改 AppScope
包名，生成后需确认它仍是正式包名。密码和绝对签名路径不提交。

## 构建、检查和安装

从仓库根运行，设备 ID 以 `hdc list targets` 为准：

```bash
bash runscript/runOhosApp-Mac.sh -m debug ohosArm64 <设备ID>
```

Windows 使用对应的 `runOhosApp-Win.bat`。脚本会解析开发包名、编译共享库、
组装 HAP，核验包内实际 `bundleName` 和 `debug` 后再安装、启动。
旧 IDE 配置传入正式 `-b` 参数时，Debug 会自动归一到 `.dev`。
安装失败直接停止，不会自动卸载任何已有应用。

手动打包后，从仓库根检查：

```bash
node scripts/verify-harmony-artifacts.mjs "$PWD" \
  harmonyApp/entry/build/default/outputs/default/entry-default-signed.hap debug
```

该检查同时核验包名、构建模式、原生库/头文件配对及资源内容。
切换到 Release 打包必须显式传入 `-p buildMode=release`；本地设备验证使用 Debug。
