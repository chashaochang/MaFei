# Agent 提交身份与历史 SHA 对照（2026-09-08）

## 约定与改写范围

- Codex：`Codex <codex@mafei.invalid>`；GLM：`GLM <glm@mafei.invalid>`。
- Author 与 Committer 均按实际执行本次提交的 Agent 设置；不是只添加 Co-authored-by。
- 这些是项目内身份，不是官方 GitHub 机器人账号，不保证官方头像、账号关联或 Verified 徽章。
- 只改写 `4ec7113` 之后的 28 个已核对 CMP 提交：Codex 10 个，GLM 18 个。未改 master/dev、远端分支、标签及更早的旧版历史。
- 改写保留每个提交的 tree、消息、作者/提交时间；父提交 SHA 随链条替换。所有 tree 已逐项核对相等，改写前后工作区/暂存区及未跟踪文件指纹一致。
- 没有执行 push 或 force-push。原历史备份：`refs/heads/backup/agent-identities-20260908`；bootstrap 备份：`refs/heads/backup/agent-identities-bootstrap-20260908`。
- `598d1de` 是 Codex 执行的 Jellyfin 主线修正，已核对本任务提交命令与输出，不因其属于 SDK 目录而归给 GLM。
- 旧验收文档保留原 SHA 作为历史证据，阅读时使用下表映射；不要把旧链 cherry-pick/reset 回活动分支。
- 历史署名调整不代表重新验收。04B 修补 `02221da → 46beeb4` 仍待主线复审，不因此冻结。

## 冻结基线别名

| 工作包 | 旧 SHA | 新 SHA |
| --- | --- | --- |
| GLM-01 | ec531f4 | 67a812f |
| GLM-02（含主线修正） | 598d1de | 819b377 |
| GLM-03 | 0c499b6 | d2ccf0b |
| GLM-04A | 961ba13 | 6734003 |

## 完整映射

| 原 SHA | 新 SHA | Agent | 提交 |
| --- | --- | --- | --- |
| 9463de7768fbcfed3d634420a1bc71a84cf71845 | c021fafc585e4de0d793a90b40c6679579f59a86 | Codex | feat: bootstrap MaFei 2.0 CMP architecture |
| f021fb6b9c9f469eaca1e93d13d9c3e5d2953d44 | ee57654cc89f19fead1435f0bd8899fbd7ef9069 | GLM | feat(core): implement GLM-01 shared contracts with TDD coverage |
| 533098e530be6919862d511664a9b7822fe34ed5 | 300ed99cfcb387ca80ae23e3509bbe086f05d1a9 | GLM | fix(core): harden GLM-01 contracts after mainline review |
| d6ced37dc03e6029f6a581c8a9293b6dbbe86d6f | e61284aac8b90dddd62514ad979d154dd39063f8 | Codex | fix(desktop): use official Compose artifact on ARM Mac |
| bc6201e3ccccdf2c4dafe0cd7cff0f6494e62066 | 81df7e87dded73b12cd0ed34a408afe7edcd405c | GLM | fix(core): harden redirect credentials, URL validation and lenient numbers |
| ec531f427122270530e7dc42c7177b9669ea9485 | 67a812fea1dd7865d9ad0ebc4ff697067ef8e670 | GLM | fix(core): final GLM-01 follow-up before contract freeze |
| ab5e0099bf5fd06cfcd9f5cfcb4c85d0a7e3e139 | b4ccece2517a2006b22865991504313f44fc308f | GLM | feat(providers/jellyfin): implement GLM-02 first-party KMP SDK (P0) |
| ae330096cb8bf8e293972d9ca9cfd18fbb53efe2 | d4694f9aa66e5c76758ffb5ebe229016bdb65c29 | GLM | fix(providers/jellyfin): align GLM-02 with official upstream contract |
| 598d1decf13f370dbb85e1488e8d4de33e9a8157 | 819b37775181cd25528137cf8d3f9bc2d2e0c721 | Codex | fix(providers/jellyfin): close remaining OpenAPI contract gaps |
| f33cb4f678f20259aa66c600b136856d089d2bdc | 6361a58af70cb357c73a72a2825c80d77a09bb2a | Codex | feat(ui): add adaptive Stage 1 foundation probe |
| 30866e6fd29270204f4b267b7d0664b514359a73 | 9cb03929e93fe1a5bfe93c1d59d71caa8d471e42 | GLM | feat(providers/feiniu): implement GLM-03 Feiniu SDK (P0, no OAuth) |
| 1ed51468737156eac16d5bf2a4c8a0e5c9455571 | d5df96fd55e34d8ef8d58e749ecb0dcac960d254 | Codex | feat(ui): integrate pinned CMP liquid glass probe across platforms |
| 040d170918d64af3e0aad6e9602fb3e87861bba1 | eac3a06c3cda2b92edd2ac293d7c8f0c4d23c2eb | GLM | fix(providers/feiniu): resolve R1-R11 from the GLM-03 mainline review |
| ea22a877ce905802c5bec90c703134ceaee34ecf | 70497da5797d6dd591976f6ad98a317fb700f22c | Codex | feat(ui): add reusable glass components and material comparison probe |
| e203f5e0946fb1fda9028f9f4c9ae53597d59737 | 673516b6b429b7e0f2eb9aa4aabf0840c4f60452 | GLM | fix(providers/feiniu): close F1-F4 session atomicity and playback contract gaps |
| de01b6274c05938c5dabfda772e4baa71693c703 | 87b190bea62a9a03df3c97d5b4c3741ad24cb146 | GLM | fix(providers/feiniu): make session state atomically consistent (G1-G3) |
| 9360e6e6116ed6db4f3c3e828a88dab9d631dc24 | 0bbf1239414f400e934295dbac291e89a4900d9e | Codex | feat(ui): fail closed on recoverable glass effect errors |
| 82e87003c7facc98f9df54705646a78f95b39f5f | 935bc97b57e0b2aa2e2c619e5eb9cef1d647481d | Codex | docs(ui): record glass fallback and Mac probe verification |
| 71955b2da8d3d7d1b5e811f9ff87248695a05a1c | 0dc0f2a8fabf9cf188bf7e09e0f7db3b33e87c3c | GLM | fix(providers/feiniu): bind every operation to one identity context (H1-H3) |
| 365721dcace0454b3354ed4ea0539d4509153f2c | 0f01e4bfde88c878a70df9aa1b0a1489677bf94f | Codex | fix(ui): preserve probe scroll anchor across window layouts |
| 1d366be7109b475d4d86bff6ffafba6fbcabbd12 | 4b5ba82180696491c7b80fd2f59fff703c21e541 | GLM | fix(providers/feiniu): pin full operation context in auth calls (J1-J3) |
| 0c499b6cd477ee9fe34571ee7203844b842ca9b4 | d2ccf0b0d03b95562f170e35de32a7452ef01ad2 | GLM | fix(providers/feiniu): yield dispatcher on CAS loss so cancellation can run (K1) |
| 0bf87b607071b3964640b3ecb12eab53124315d6 | d22e92f6a6be8ea2b737441d62ee106cfd91f495 | GLM | feat(data): add provider-neutral adapter contract (04A) |
| b3028fea602fb4829b9aa7cb6c0c552d57e635df | d656049023184e375642300cf542855e86f04811 | Codex | feat(cmp): complete mobile probe hosts and foundation verification |
| f444bd3071829d1cfe75e3513695bf3c0f4945f8 | 3712e431d7514f76cf2428e61fdf45455ad99689 | GLM | fix(data): honor declared contract in fake adapter and correct mapping (04A review) |
| 961ba134c0e730cd6b093aa47ddbf887ba2a1d3a | 673400372410b30910c737ed55f7ed761296968f | GLM | fix(data): close favorites+watched combo gate and null ordering in fake (04A review 2) |
| 0899e1549128e11666dcb2c1a905be8251305209 | 1b08b001d9b9e8ee38dbd8a6ac793c26d883405a | GLM | feat(data): implement Jellyfin provider adapter (04B) |
| 02221dac9a8ebf445e313d9abbc72e7a019e32fe | 46beeb47ac9dcacf26891781d4fbbaf8c63f9138 | GLM | fix(data): honor negotiation, audio routing and query fidelity in Jellyfin adapter (04B review) |

## 发给 GLM 的提示词

请先阅读根目录 AGENTS.md 和本文。用户已要求 Codex/GLM 分开署名，主线已完成本地 CMP 历史改写，代码内容未改。你只需要遵守新身份，不要再次改写历史或推送备份分支。

1. 继续前重新读取当前分支、HEAD、git status；不要使用会话中缓存的旧 HEAD。若你仍持有旧链上的未提交工作/独立分支，先报告差异，不要 reset、rebase 或 cherry-pick 恢复旧链。
2. 今后每次提交都使用 GLM 作为 Author 和 Committer。不要修改全局或仓库共享 user.name/user.email，也不要仅添加 Co-authored-by。提交示例：

```bash
GIT_AUTHOR_NAME='GLM' GIT_AUTHOR_EMAIL='glm@mafei.invalid' \
GIT_COMMITTER_NAME='GLM' GIT_COMMITTER_EMAIL='glm@mafei.invalid' \
git commit --author='GLM <glm@mafei.invalid>' -m '提交说明'
```

3. 只暂存自己负责的明确路径，不用 git add . / -A；保留 gradle.properties、runscript/ 及主线改动。每次提交后用以下命令检查双方身份并汇报新 SHA：

```bash
git log -1 --format='%h%nAuthor: %an <%ae>%nCommitter: %cn <%ce>%n%s'
```

4. 旧冻结 SHA 按本文映射读取；04A 冻结基线现为 6734003。你的 04B 修补 02221da 现为 46beeb4，仍等待主线复审，不代表已经冻结，不自行进入后续批次。
5. 不要修改/删除备份分支，不执行历史重写或 force-push。发现他人新提交，重新核对 HEAD 与范围后继续。
