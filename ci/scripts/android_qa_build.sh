#!/usr/bin/env bash
set -Eeuo pipefail

# Usage:
#   bash ci/scripts/android_qa_build.sh --compile-only
#   bash ci/scripts/android_qa_build.sh

MODE="${1:-}"
MODULE="${ANDROID_MODULE:-composeApp}"
VARIANT_RAW="${ANDROID_VARIANT:-Debug}"
APP_NAME="${APP_NAME:-MaFei}"
APP_VERSION_NAME="${APP_VERSION_NAME:-0.0.0}"
OUTPUT_DIR="ci/out"

case "${VARIANT_RAW}" in
  debug|Debug)
    VARIANT="Debug"
    ;;
  release|Release)
    VARIANT="Release"
    ;;
  *)
    echo "[ci] ERROR: unsupported ANDROID_VARIANT=${VARIANT_RAW} (use Debug/Release)"
    exit 1
    ;;
esac
VARIANT_LOWER="$(echo "${VARIANT}" | tr '[:upper:]' '[:lower:]')"
GRADLEW="${GRADLEW_PATH:-./gradlew}"

if [[ ! -f "${GRADLEW}" ]]; then
  echo "[ci] ERROR: gradlew not found at ${GRADLEW}"
  exit 1
fi
if [[ ! -x "${GRADLEW}" ]]; then
  chmod +x "${GRADLEW}" || true
fi

COMPILE_TASK=":${MODULE}:compile${VARIANT}KotlinAndroid"
ASSEMBLE_TASK=":${MODULE}:assemble${VARIANT}"

if [[ "$MODE" == "--compile-only" ]]; then
  echo "[ci] compile only mode: $COMPILE_TASK"
  "${GRADLEW}" --no-daemon --stacktrace "$COMPILE_TASK"
  exit 0
fi

echo "[ci] assemble mode: $ASSEMBLE_TASK"
"${GRADLEW}" --no-daemon --stacktrace "$ASSEMBLE_TASK"

APK_CANDIDATES="$(find "${MODULE}/build/outputs/apk" -type f -path "*/${VARIANT_LOWER}/*.apk" -name "*.apk" | sort || true)"
if [[ -z "${APK_CANDIDATES}" ]]; then
  echo "[ci] ERROR: apk not found under ${MODULE}/build/outputs/apk (variant=${VARIANT_LOWER})"
  exit 1
fi

APK_PATH="$(echo "${APK_CANDIDATES}" | grep -Ev '(-unsigned|-unaligned)\.apk$' | tail -n 1 || true)"
if [[ -z "${APK_PATH}" ]]; then
  APK_PATH="$(echo "${APK_CANDIDATES}" | tail -n 1)"
fi
if [[ ! -f "${APK_PATH}" ]]; then
  echo "[ci] ERROR: selected apk path is invalid: ${APK_PATH}"
  exit 1
fi

BRANCH_RAW="${CHANGE_BRANCH:-${BRANCH_NAME:-${GIT_BRANCH:-local}}}"
BRANCH_SLUG="$(echo "${BRANCH_RAW}" | sed -E 's#^origin/##; s#^refs/heads/##; s#[^A-Za-z0-9._-]+#-#g')"
SHORT_SHA="$(git rev-parse --short HEAD)"
BUILD_NO="${BUILD_NUMBER:-${BK_BUILD_ID:-local}}"

mkdir -p "${OUTPUT_DIR}"
rm -f "${OUTPUT_DIR}/artifact.env"

ARTIFACT_NAME="${APP_NAME}-${APP_VERSION_NAME}-${BUILD_NO}-${BRANCH_SLUG}-${SHORT_SHA}.apk"
ARTIFACT_PATH="${OUTPUT_DIR}/${ARTIFACT_NAME}"
cp "${APK_PATH}" "${ARTIFACT_PATH}"

cat > "${OUTPUT_DIR}/artifact.env" <<EOF
APK_PATH=${ARTIFACT_PATH}
APK_NAME=${ARTIFACT_NAME}
BRANCH_NAME=${BRANCH_RAW}
BRANCH_SLUG=${BRANCH_SLUG}
BUILD_NO=${BUILD_NO}
SHORT_SHA=${SHORT_SHA}
APP_VERSION_NAME=${APP_VERSION_NAME}
EOF

echo "[ci] apk source : ${APK_PATH}"
echo "[ci] apk output : ${ARTIFACT_PATH}"
echo "[ci] metadata   : ${OUTPUT_DIR}/artifact.env"
