#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SOURCE_SO="${ROOT_DIR}/shared/build/bin/ohosArm64/releaseShared/libmafei_shared.so"
TARGET_DIR="${ROOT_DIR}/ohosApp/sharedbridge/src/main/libs/arm64-v8a"

if [[ ! -f "${SOURCE_SO}" ]]; then
  echo "Missing ${SOURCE_SO}"
  echo "Run: ./gradlew :shared:linkOhosArm64"
  exit 1
fi

mkdir -p "${TARGET_DIR}"
cp "${SOURCE_SO}" "${TARGET_DIR}/libmafei_shared.so"
echo "Synced ${TARGET_DIR}/libmafei_shared.so"
