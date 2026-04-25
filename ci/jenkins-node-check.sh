#!/usr/bin/env bash
set -Eeuo pipefail

# Jenkins node read-only preflight check script for Android QA pipeline.
# This script does not install or modify system dependencies.

PASS_COUNT=0
FAIL_COUNT=0
WARN_COUNT=0

ok() {
  echo "[PASS] $1"
  PASS_COUNT=$((PASS_COUNT + 1))
}

warn() {
  echo "[WARN] $1"
  WARN_COUNT=$((WARN_COUNT + 1))
}

fail() {
  echo "[FAIL] $1"
  FAIL_COUNT=$((FAIL_COUNT + 1))
}

check_cmd() {
  local cmd="$1"
  local label="$2"
  if command -v "$cmd" >/dev/null 2>&1; then
    ok "$label: $(command -v "$cmd")"
  else
    fail "$label: command not found ($cmd)"
  fi
}

echo "== Jenkins Node Preflight Check =="
echo "Workspace: ${PWD}"
echo

check_cmd bash "bash"
check_cmd git "git"
check_cmd curl "curl"
check_cmd java "java"

if [[ -x "./gradlew" ]]; then
  ok "gradlew: ./gradlew exists and executable"
elif [[ -f "./gradlew" ]]; then
  warn "gradlew: ./gradlew exists but is not executable"
else
  fail "gradlew: ./gradlew not found (run in repo root)"
fi

if command -v java >/dev/null 2>&1; then
  JAVA_VERSION="$(java -version 2>&1 | head -n 1 || true)"
  if [[ "${JAVA_VERSION}" == *"17."* ]] || [[ "${JAVA_VERSION}" == *" 17"* ]]; then
    ok "java version: ${JAVA_VERSION}"
  else
    warn "java version is not clearly 17: ${JAVA_VERSION}"
  fi
fi

ANDROID_HOME_EFFECTIVE="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -n "${ANDROID_HOME_EFFECTIVE}" ]]; then
  if [[ -d "${ANDROID_HOME_EFFECTIVE}" ]]; then
    ok "ANDROID_HOME/ANDROID_SDK_ROOT: ${ANDROID_HOME_EFFECTIVE}"
  else
    fail "ANDROID_HOME/ANDROID_SDK_ROOT points to missing dir: ${ANDROID_HOME_EFFECTIVE}"
  fi
else
  warn "ANDROID_HOME/ANDROID_SDK_ROOT not set"
fi

if command -v sdkmanager >/dev/null 2>&1; then
  ok "sdkmanager available"
else
  warn "sdkmanager not found in PATH"
fi

if [[ -n "${ANDROID_HOME_EFFECTIVE}" && -d "${ANDROID_HOME_EFFECTIVE}" ]]; then
  if [[ -d "${ANDROID_HOME_EFFECTIVE}/platform-tools" ]]; then
    ok "Android platform-tools exists"
  else
    fail "Android platform-tools missing: ${ANDROID_HOME_EFFECTIVE}/platform-tools"
  fi

  if ls "${ANDROID_HOME_EFFECTIVE}/build-tools" >/dev/null 2>&1; then
    ok "Android build-tools directory exists"
  else
    fail "Android build-tools missing: ${ANDROID_HOME_EFFECTIVE}/build-tools"
  fi

  if ls "${ANDROID_HOME_EFFECTIVE}/platforms" >/dev/null 2>&1; then
    ok "Android platforms directory exists"
  else
    fail "Android platforms missing: ${ANDROID_HOME_EFFECTIVE}/platforms"
  fi
fi

if curl -sSfI https://www.pgyer.com >/dev/null 2>&1; then
  ok "Network to pgyer.com is reachable"
else
  warn "Network to pgyer.com not reachable (may block upload stage)"
fi

if curl -sSfI https://gitcode.com >/dev/null 2>&1; then
  ok "Network to gitcode.com is reachable"
else
  warn "Network to gitcode.com not reachable (may block clone/webhook checks)"
fi

echo
echo "== Summary =="
echo "PASS: ${PASS_COUNT}"
echo "WARN: ${WARN_COUNT}"
echo "FAIL: ${FAIL_COUNT}"

if [[ "${FAIL_COUNT}" -gt 0 ]]; then
  echo "Result: NOT READY"
  exit 1
fi

echo "Result: READY (with ${WARN_COUNT} warnings)"
