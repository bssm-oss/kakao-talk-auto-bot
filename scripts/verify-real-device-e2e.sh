#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADB_BIN="${ADB:-/Users/heodongun/Library/Android/sdk/platform-tools/adb}"
PACKAGE_NAME="com.example.kakaotalkautobot"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
TEST_PACKAGE="${PACKAGE_NAME}.test"
MODEL_PATH="files/llm_models/model.litertlm"
EXPECTED_MODEL_SIZE="2588147712"
EXPECTED_MODEL_SHA="181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
OUT_DIR="${ROOT_DIR}/outputs/real-device-e2e"
STAMP="$(date +%Y%m%d-%H%M%S)"

if [[ ! -x "${ADB_BIN}" ]]; then
  echo "adb not found: ${ADB_BIN}" >&2
  echo "Set ADB=/path/to/adb or install Android platform-tools." >&2
  exit 1
fi

mapfile -t DEVICES < <("${ADB_BIN}" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
if [[ "${#DEVICES[@]}" -ne 1 ]]; then
  "${ADB_BIN}" devices -l
  echo "Expected exactly one attached Android device." >&2
  exit 1
fi

SERIAL="${DEVICES[0]}"
ABI="$("${ADB_BIN}" -s "${SERIAL}" shell getprop ro.product.cpu.abi | tr -d '\r')"
if [[ "${ABI}" != arm64* ]]; then
  echo "Expected an ARM64 real device, got abi=${ABI}." >&2
  exit 1
fi

FINGERPRINT="$("${ADB_BIN}" -s "${SERIAL}" shell getprop ro.build.fingerprint | tr -d '\r')"
if [[ "${FINGERPRINT}" == *generic* || "${FINGERPRINT}" == *sdk_gphone* ]]; then
  echo "This looks like an emulator, not a real device: ${FINGERPRINT}" >&2
  exit 1
fi

mkdir -p "${OUT_DIR}"
LOGCAT_FILE="${OUT_DIR}/${STAMP}-logcat.txt"
SUMMARY_FILE="${OUT_DIR}/${STAMP}-summary.txt"

cd "${ROOT_DIR}"

{
  echo "serial=${SERIAL}"
  echo "abi=${ABI}"
  echo "fingerprint=${FINGERPRINT}"
  echo "started_at=${STAMP}"
} | tee "${SUMMARY_FILE}"

"${ADB_BIN}" -s "${SERIAL}" logcat -c
ANDROID_SERIAL="${SERIAL}" ./gradlew installDebug installDebugAndroidTest

"${ADB_BIN}" -s "${SERIAL}" shell am start -n "${PACKAGE_NAME}/.MainActivity" >/dev/null

set +e
"${ADB_BIN}" -s "${SERIAL}" shell am instrument -w -r \
  -e class "${PACKAGE_NAME}.ExampleInstrumentedTest" \
  "${TEST_PACKAGE}/${RUNNER}" | tee -a "${SUMMARY_FILE}"
TEST_STATUS="${PIPESTATUS[0]}"
set -e

"${ADB_BIN}" -s "${SERIAL}" logcat -d > "${LOGCAT_FILE}"

MODEL_SIZE="$("${ADB_BIN}" -s "${SERIAL}" shell run-as "${PACKAGE_NAME}" stat -c %s "${MODEL_PATH}" 2>/dev/null | tr -d '\r' || true)"
MODEL_SHA="$("${ADB_BIN}" -s "${SERIAL}" shell run-as "${PACKAGE_NAME}" sha256sum "${MODEL_PATH}" 2>/dev/null | awk '{ print $1 }' | tr -d '\r' || true)"

{
  echo "model_path=${MODEL_PATH}"
  echo "model_size=${MODEL_SIZE}"
  echo "expected_model_size=${EXPECTED_MODEL_SIZE}"
  echo "model_sha256=${MODEL_SHA}"
  echo "expected_model_sha256=${EXPECTED_MODEL_SHA}"
  echo "logcat_file=${LOGCAT_FILE}"
  echo "manual_kakao_e2e_required=true"
  echo "manual_kakao_steps=enable notification access, send KakaoTalk message from another account, confirm IN log, confirm OUT log, confirm reply appears in KakaoTalk"
} | tee -a "${SUMMARY_FILE}"

if [[ "${TEST_STATUS}" -ne 0 ]]; then
  echo "Instrumentation failed. See ${SUMMARY_FILE} and ${LOGCAT_FILE}." >&2
  exit "${TEST_STATUS}"
fi

if [[ "${MODEL_SIZE}" != "${EXPECTED_MODEL_SIZE}" ]]; then
  echo "Model size mismatch. See ${SUMMARY_FILE}." >&2
  exit 1
fi

if [[ "${MODEL_SHA}" != "${EXPECTED_MODEL_SHA}" ]]; then
  echo "Model SHA-256 mismatch. See ${SUMMARY_FILE}." >&2
  exit 1
fi

echo "Device model checks passed. Complete the manual KakaoTalk notification/reply steps before calling E2E complete."
