#!/usr/bin/env bash
set -euo pipefail

# Requires an already booted iOS ARM64 simulator. The tests only create unique
# cn.mafei.keychain-tests.* services and delete those entries in finally blocks.
# Uses simulator-only embedded entitlements, never a developer signing identity.
if [[ $# -ne 1 || ! "$1" =~ ^[0-9A-Fa-f-]{36}$ ]]; then
    echo "Usage: bash scripts/test-ios-keychain.sh <booted-simulator-UDID>" >&2
    exit 2
fi

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_dir"
./gradlew :platform:secure-storage-ios:linkDebugTestIosSimulatorArm64 --console=plain
xcrun simctl spawn "$1" "$repo_dir/platform/secure-storage-ios/build/bin/iosSimulatorArm64/debugTest/test.kexe"
