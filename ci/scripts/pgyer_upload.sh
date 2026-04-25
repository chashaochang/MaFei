#!/usr/bin/env bash
set -Eeuo pipefail

# Usage:
#   bash ci/scripts/pgyer_upload.sh <apk_path>
# or:
#   APK_PATH=... bash ci/scripts/pgyer_upload.sh

APK_PATH="${1:-${APK_PATH:-}}"
if [[ -z "${APK_PATH}" || ! -f "${APK_PATH}" ]]; then
  echo "[ci] ERROR: apk path is invalid: ${APK_PATH}"
  exit 1
fi

: "${PGY_API_KEY:?PGY_API_KEY is required}"
if ! command -v curl >/dev/null 2>&1; then
  echo "[ci] ERROR: curl is required but not found"
  exit 1
fi

OUTPUT_DIR="ci/out"
mkdir -p "${OUTPUT_DIR}"

RELEASE_NOTES="${RELEASE_NOTES:-$(git log -1 --pretty=format:'%h %s')}"
PGY_BUILD_INSTALL_TYPE="${PGY_BUILD_INSTALL_TYPE:-2}"
PGY_INSTALL_PASSWORD="${PGY_INSTALL_PASSWORD:-}"
UPLOAD_URL="${PGY_UPLOAD_ENDPOINT:-https://www.pgyer.com/apiv2/app/upload}"
PGY_UPLOAD_RETRY="${PGY_UPLOAD_RETRY:-2}"

CURL_ARGS=(
  -sS -X POST "${UPLOAD_URL}"
  -F "_api_key=${PGY_API_KEY}"
  -F "file=@${APK_PATH}"
  -F "buildUpdateDescription=${RELEASE_NOTES}"
  -F "buildInstallType=${PGY_BUILD_INSTALL_TYPE}"
)

if [[ -n "${PGY_INSTALL_PASSWORD}" ]]; then
  CURL_ARGS+=(-F "buildPassword=${PGY_INSTALL_PASSWORD}")
fi

if ! [[ "${PGY_UPLOAD_RETRY}" =~ ^[0-9]+$ ]] || [[ "${PGY_UPLOAD_RETRY}" -lt 1 ]]; then
  echo "[ci] WARN: invalid PGY_UPLOAD_RETRY=${PGY_UPLOAD_RETRY}, fallback to 2"
  PGY_UPLOAD_RETRY=2
fi

echo "[ci] uploading apk to pgyer: ${APK_PATH}"
UPLOAD_OK="false"

for attempt in $(seq 1 "${PGY_UPLOAD_RETRY}"); do
  echo "[ci] pgyer upload attempt ${attempt}/${PGY_UPLOAD_RETRY}"
  if curl "${CURL_ARGS[@]}" > "${OUTPUT_DIR}/pgyer_upload_result.json"; then
    RESPONSE_COMPACT="$(tr -d '\n' < "${OUTPUT_DIR}/pgyer_upload_result.json")"
    PGY_CODE="$(echo "${RESPONSE_COMPACT}" | sed -n 's/.*"code"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' | head -n 1)"
    if [[ "${PGY_CODE}" == "0" ]]; then
      UPLOAD_OK="true"
      break
    fi
    echo "[ci] WARN: pgyer response code=${PGY_CODE}, will retry if attempts remain"
  else
    echo "[ci] WARN: curl upload failed, will retry if attempts remain"
  fi

  if [[ "${attempt}" -lt "${PGY_UPLOAD_RETRY}" ]]; then
    sleep 2
  fi
done

if [[ "${UPLOAD_OK}" != "true" ]]; then
  echo "[ci] ERROR: pgyer upload failed, response:"
  cat "${OUTPUT_DIR}/pgyer_upload_result.json" || true
  exit 1
fi

RESPONSE_COMPACT="$(tr -d '\n' < "${OUTPUT_DIR}/pgyer_upload_result.json")"
PGY_SHORTCUT_URL="$(echo "${RESPONSE_COMPACT}" | sed -n 's/.*"buildShortcutUrl"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n 1)"

if [[ -n "${PGY_SHORTCUT_URL}" ]]; then
  PGYER_INSTALL_URL="https://www.pgyer.com/${PGY_SHORTCUT_URL}"
else
  PGYER_INSTALL_URL=""
fi

PGY_MESSAGE="$(echo "${RESPONSE_COMPACT}" | sed -n 's/.*"message"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n 1)"

cat > "${OUTPUT_DIR}/pgyer.env" <<EOF
PGYER_CODE=${PGY_CODE}
PGYER_MESSAGE=${PGY_MESSAGE}
PGYER_SHORTCUT_URL=${PGY_SHORTCUT_URL}
PGYER_INSTALL_URL=${PGYER_INSTALL_URL}
EOF

echo "[ci] pgyer upload success"
echo "[ci] install url: ${PGYER_INSTALL_URL}"
echo "[ci] metadata: ${OUTPUT_DIR}/pgyer.env"
