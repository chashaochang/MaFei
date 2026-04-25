# CI 目录说明（第一阶段）

本目录用于 Android QA 流水线，不包含业务代码。

## 文件说明

- `Jenkinsfile.android.qa`
  - Jenkins 首跑主流水线。
  - 覆盖 `feature/develop` 编译校验、`release/bugfix` 构建与蒲公英上传。

- `bkci-android-qa.yml`
  - 蓝盾 YAML 模板（需按你们蓝盾插件与执行机标签适配）。
  - 与 Jenkins 复用同一套 `ci/scripts`。

- `android-qa.env.example`
  - 流水线变量示例（仅变量名，不含真实密钥）。
  - 可作为 Jenkins 参数或节点环境变量配置参考。

- `jenkins-node-check.sh`
  - Jenkins 节点环境自检脚本（只读，不改系统）。
  - 用于上线前快速检查 `java/git/bash/curl/gradlew/android sdk/network`。

- `scripts/android_qa_build.sh`
  - Android 构建脚本。
  - 支持 `--compile-only`（用于 feature/develop）和完整 assemble（用于 release/bugfix）。
  - 产物输出到 `ci/out/`，并生成 `artifact.env`。

- `scripts/pgyer_upload.sh`
  - 蒲公英上传脚本。
  - 读取 `PGY_API_KEY` 与 APK 路径，上传后输出 `pgyer_upload_result.json` 与 `pgyer.env`。

## 产物目录

- `ci/out/`
  - `*.apk`
  - `artifact.env`
  - `pgyer_upload_result.json`
  - `pgyer.env`

## 快速入口文档

- `docs/jenkins-rollout-checklist.md`：首次落地执行清单（0-9 步）
- `docs/jenkins-first-run.md`：首次上线操作清单
- `docs/jenkins-no-pitfalls-checklist.md`：首跑不踩坑勾选清单（推荐先过一遍）
- `docs/jenkins-deployment-input-runbook.md`：部署信息与凭据收集（先填再实施）
- `docs/tencent-ubuntu-server-bootstrap.md`：腾讯云 Ubuntu 服务器初始化（Docker/Compose）
- `docs/tencent-jenkins-setup.md`：腾讯云轻量服务器 Jenkins 部署指南
- `docs/android-qa-pipeline-quickstart.md`：最短执行手册（从机器到首个 QA 包）
- `docs/gitcode-jenkins-setup.md`：GitCode -> Jenkins WebHook 接入
- `docs/ci-prerequisites.md`：前置环境与插件清单
- `ops/jenkins/`：Jenkins Docker Compose 与 agent 模板
