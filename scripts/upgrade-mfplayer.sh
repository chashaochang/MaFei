#!/usr/bin/env bash
#
# Upgrade MaFei's local MFPlayer HAR and verify the exact native binaries
# propagated from the input SDK archive to OHPM and the signed default HAP.

set -eo pipefail

fail() {
  printf '\n[失败] %s\n' "$*" >&2
  exit 1
}

info() {
  printf '[MFPlayer 升级] %s\n' "$*"
}

sha256() {
  shasum -a 256 "$1" | awk '{print $1}'
}

if [[ "$#" -lt 1 || "$#" -gt 2 ]]; then
  cat >&2 <<'USAGE'
用法:
  upgrade-mfplayer.sh <mfplayer-sdk-x.y.z.zip> [MaFei 项目根目录]

默认项目根目录: /Users/machunjiang/MaFei
可选环境变量: DEVECO_STUDIO_HOME
USAGE
  exit 64
fi

SDK_ZIP="$1"
PROJECT_ROOT="/Users/machunjiang/MaFei"
if [[ "$#" -eq 2 ]]; then
  PROJECT_ROOT="$2"
fi

[[ -f "$SDK_ZIP" ]] || fail "找不到 SDK ZIP: $SDK_ZIP"
SDK_ZIP="$(cd "$(dirname "$SDK_ZIP")" && pwd -P)/$(basename "$SDK_ZIP")"
[[ -d "$PROJECT_ROOT" ]] || fail "找不到项目根目录: $PROJECT_ROOT"
PROJECT_ROOT="$(cd "$PROJECT_ROOT" && pwd -P)"

OHOS_APP="$PROJECT_ROOT/harmonyApp"
ENTRY_DIR="$OHOS_APP/entry"
HAR_DEST="$ENTRY_DIR/libs/mfplayer.har"
ENTRY_PACKAGE="$ENTRY_DIR/oh-package.json5"
HAP_PATH="$ENTRY_DIR/build/default/outputs/default/entry-default-signed.hap"
HAR_REL="harmonyApp/entry/libs/mfplayer.har"
ROOT_LOCK_REL="harmonyApp/oh-package-lock.json5"
ENTRY_LOCK_REL="harmonyApp/entry/oh-package-lock.json5"

[[ -d "$OHOS_APP" && -d "$ENTRY_DIR" && -f "$ENTRY_PACKAGE" ]] || \
  fail "目录不是受支持的 MaFei 项目: $PROJECT_ROOT"
grep -Eq '"mfplayer"[[:space:]]*:[[:space:]]*"file:\./libs/mfplayer\.har"' "$ENTRY_PACKAGE" || \
  fail "entry 依赖不再是 file:./libs/mfplayer.har，停止以避免错误升级"

TMP_DIR="$(mktemp -d /tmp/mafei-mfplayer-upgrade.XXXXXX)"
trap 'rm -rf "$TMP_DIR"' EXIT
STAGED_HAR="$TMP_DIR/mfplayer.har"

info "校验 SDK ZIP"
unzip -tqq "$SDK_ZIP" || fail "SDK ZIP 损坏"
HAR_ENTRIES="$(unzip -Z1 "$SDK_ZIP" | awk '/(^|\/)mfplayer-[^\/]+\.har$/ { print }')"
HAR_ENTRY_COUNT="$(printf '%s\n' "$HAR_ENTRIES" | awk 'NF { count += 1 } END { print count + 0 }')"
[[ "$HAR_ENTRY_COUNT" -eq 1 ]] || \
  fail "SDK ZIP 内应恰有一个 mfplayer-*.har，实际为 $HAR_ENTRY_COUNT 个"
HAR_ENTRY="$HAR_ENTRIES"
unzip -p "$SDK_ZIP" "$HAR_ENTRY" > "$STAGED_HAR"
tar -tzf "$STAGED_HAR" >/dev/null || fail "SDK 中的 HAR 不是有效 gzip HAR"

for REQUIRED_MEMBER in \
  "package/libs/arm64-v8a/libmpv.so.2" \
  "package/libs/arm64-v8a/libmpvnative.so" \
  "package/oh-package.json5"; do
  tar -tzf "$STAGED_HAR" | grep -Fxq "$REQUIRED_MEMBER" || \
    fail "HAR 缺少必需文件: $REQUIRED_MEMBER"
done

SDK_METADATA="$(tar -xOf "$STAGED_HAR" package/oh-package.json5)"
SDK_VERSION="$(printf '%s\n' "$SDK_METADATA" | sed -n 's/.*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n 1)"
[[ -n "$SDK_VERSION" ]] || SDK_VERSION="未声明"
HAR_FILE_SHA="$(sha256 "$STAGED_HAR")"

mkdir -p "$(dirname "$HAR_DEST")"
cp "$STAGED_HAR" "$HAR_DEST"
info "已替换 entry/libs/mfplayer.har（SDK $SDK_VERSION）"

OHPM_BIN="$(command -v ohpm || true)"
if [[ -z "$OHPM_BIN" ]]; then
  for CANDIDATE in \
    "/Applications/DevEco-Studio 3.app/Contents/tools/ohpm/bin/ohpm" \
    "/Applications/DevEco-Studio.app/Contents/tools/ohpm/bin/ohpm"; do
    if [[ -x "$CANDIDATE" ]]; then
      OHPM_BIN="$CANDIDATE"
      break
    fi
  done
fi
[[ -n "$OHPM_BIN" ]] || fail "找不到 ohpm，请先安装或设置 PATH"

info "刷新 OHPM 依赖"
(
  cd "$OHOS_APP"
  "$OHPM_BIN" install
)

HAR_MPV="$TMP_DIR/har-libmpv.so.2"
HAR_NATIVE="$TMP_DIR/har-libmpvnative.so"
tar -xOf "$HAR_DEST" package/libs/arm64-v8a/libmpv.so.2 > "$HAR_MPV"
tar -xOf "$HAR_DEST" package/libs/arm64-v8a/libmpvnative.so > "$HAR_NATIVE"
HAR_MPV_SHA="$(sha256 "$HAR_MPV")"
HAR_NATIVE_SHA="$(sha256 "$HAR_NATIVE")"

INSTALLED_MPV_PATHS="$(find "$OHOS_APP/oh_modules/.ohpm" -type f \
  -path '*/oh_modules/mfplayer/libs/arm64-v8a/libmpv.so.2' -print 2>/dev/null || true)"
INSTALLED_NATIVE_PATHS="$(find "$OHOS_APP/oh_modules/.ohpm" -type f \
  -path '*/oh_modules/mfplayer/libs/arm64-v8a/libmpvnative.so' -print 2>/dev/null || true)"
[[ -n "$INSTALLED_MPV_PATHS" ]] || fail "OHPM 安装后未找到 MFPlayer libmpv.so.2"
[[ -n "$INSTALLED_NATIVE_PATHS" ]] || fail "OHPM 安装后未找到 MFPlayer libmpvnative.so"

INSTALLED_MPV=""
while IFS= read -r CANDIDATE; do
  [[ -z "$CANDIDATE" ]] && continue
  if [[ "$(sha256 "$CANDIDATE")" == "$HAR_MPV_SHA" ]]; then
    INSTALLED_MPV="$CANDIDATE"
    break
  fi
done <<< "$INSTALLED_MPV_PATHS"
[[ -n "$INSTALLED_MPV" ]] || fail "OHPM 中的 libmpv.so.2 与输入 HAR 不一致，可能仍命中缓存"

INSTALLED_NATIVE=""
while IFS= read -r CANDIDATE; do
  [[ -z "$CANDIDATE" ]] && continue
  if [[ "$(sha256 "$CANDIDATE")" == "$HAR_NATIVE_SHA" ]]; then
    INSTALLED_NATIVE="$CANDIDATE"
    break
  fi
done <<< "$INSTALLED_NATIVE_PATHS"
[[ -n "$INSTALLED_NATIVE" ]] || fail "OHPM 中的 libmpvnative.so 与输入 HAR 不一致，可能仍命中缓存"

STUDIO_HOME="$DEVECO_STUDIO_HOME"
if [[ -z "$STUDIO_HOME" ]]; then
  for CANDIDATE in "/Applications/DevEco-Studio 3.app" "/Applications/DevEco-Studio.app"; do
    if [[ -d "$CANDIDATE" ]]; then
      STUDIO_HOME="$CANDIDATE"
      break
    fi
  done
fi
[[ -n "$STUDIO_HOME" && -d "$STUDIO_HOME" ]] || \
  fail "找不到 DevEco Studio；可设置 DEVECO_STUDIO_HOME"

NODE_BIN="$STUDIO_HOME/Contents/tools/node/bin/node"
HVIGOR_BIN="$STUDIO_HOME/Contents/tools/hvigor/bin/hvigorw.js"
DEVECO_SDK="$STUDIO_HOME/Contents/sdk"
DEVECO_JAVA="$STUDIO_HOME/Contents/jbr/Contents/Home"
[[ -x "$NODE_BIN" && -f "$HVIGOR_BIN" && -d "$DEVECO_SDK" && -d "$DEVECO_JAVA" ]] || \
  fail "DevEco Studio 工具链不完整: $STUDIO_HOME"

BUILD_STAMP="$TMP_DIR/build-start"
touch "$BUILD_STAMP"
info "构建 default 签名 HAP"
(
  cd "$OHOS_APP"
  DEVECO_SDK_HOME="$DEVECO_SDK" JAVA_HOME="$DEVECO_JAVA" \
    "$NODE_BIN" "$HVIGOR_BIN" assembleHap \
      --mode module -p product=default -p module=entry@default \
      -p buildMode=debug --no-daemon
)

[[ -f "$HAP_PATH" ]] || fail "未生成预期 HAP: $HAP_PATH"
[[ "$HAP_PATH" -nt "$BUILD_STAMP" ]] || \
  fail "预期 HAP 未在本次构建后更新，停止以避免误用旧产物"

HAP_MPV="$TMP_DIR/hap-libmpv.so.2"
HAP_NATIVE="$TMP_DIR/hap-libmpvnative.so"
unzip -p "$HAP_PATH" libs/arm64-v8a/libmpv.so.2 > "$HAP_MPV"
unzip -p "$HAP_PATH" libs/arm64-v8a/libmpvnative.so > "$HAP_NATIVE"
[[ -s "$HAP_MPV" ]] || fail "HAP 未包含 libmpv.so.2"
[[ -s "$HAP_NATIVE" ]] || fail "HAP 未包含 libmpvnative.so"
HAP_MPV_SHA="$(sha256 "$HAP_MPV")"
HAP_NATIVE_SHA="$(sha256 "$HAP_NATIVE")"
[[ "$HAP_MPV_SHA" == "$HAR_MPV_SHA" ]] || \
  fail "最终 HAP 的 libmpv.so.2 与输入 HAR 不一致"
[[ "$HAP_NATIVE_SHA" == "$HAR_NATIVE_SHA" ]] || \
  fail "最终 HAP 的 libmpvnative.so 与输入 HAR 不一致"

git -C "$PROJECT_ROOT" diff --check -- \
  "$HAR_REL" "$ROOT_LOCK_REL" "$ENTRY_LOCK_REL"

printf '\n[完成] MFPlayer SDK 已接入并完成二进制校验\n'
printf 'SDK 版本: %s\n' "$SDK_VERSION"
printf 'SDK HAR SHA-256: %s\n' "$HAR_FILE_SHA"
printf 'libmpv.so.2 SHA-256: %s\n' "$HAR_MPV_SHA"
printf 'libmpvnative.so SHA-256: %s\n' "$HAR_NATIVE_SHA"
printf 'OHPM libmpv: %s\n' "$INSTALLED_MPV"
printf 'OHPM libmpvnative: %s\n' "$INSTALLED_NATIVE"
printf 'HAP: %s\n' "$HAP_PATH"
printf '\n相关工作区状态:\n'
git -C "$PROJECT_ROOT" status --short -- \
  "$HAR_REL" "$ROOT_LOCK_REL" "$ENTRY_LOCK_REL"
printf '\n验证边界: 已验证依赖刷新、默认签名 HAP 与 native 二进制一致；未执行真机播放、字幕、音轨或投屏验收。\n'
