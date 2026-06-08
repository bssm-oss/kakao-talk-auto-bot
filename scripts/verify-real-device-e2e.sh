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
LOGCAT_FILE="${OUT_DIR}/${STAMP}-logcat.txt"
SUMMARY_FILE="${OUT_DIR}/${STAMP}-summary.txt"
ADB_DEVICES_FILE="${OUT_DIR}/${STAMP}-adb-devices.txt"

bool_env() {
  case "${1:-false}" in
    true | TRUE | 1 | yes | YES) echo "true" ;;
    *) echo "false" ;;
  esac
}

manual_kakao_complete() {
  local notification_access
  local in_log
  local out_log
  local reply_visible
  notification_access="$(bool_env "${MANUAL_KAKAO_NOTIFICATION_ACCESS_CONFIRMED:-false}")"
  in_log="$(bool_env "${MANUAL_KAKAO_IN_LOG_CONFIRMED:-false}")"
  out_log="$(bool_env "${MANUAL_KAKAO_OUT_LOG_CONFIRMED:-false}")"
  reply_visible="$(bool_env "${MANUAL_KAKAO_REPLY_VISIBLE_IN_KAKAOTALK:-false}")"

  if [[ "${notification_access}" == "true" &&
    -n "${MANUAL_KAKAO_TEST_ROOM:-}" &&
    -n "${MANUAL_KAKAO_TEST_SENDER:-}" &&
    "${in_log}" == "true" &&
    "${out_log}" == "true" &&
    "${reply_visible}" == "true" &&
    -z "${MANUAL_KAKAO_REMOTEINPUT_FAILURE_REASON:-}" ]]; then
    echo "true"
  else
    echo "false"
  fi
}

manual_kakao_pending_reason() {
  if [[ "$(manual_kakao_complete)" == "true" ]]; then
    echo "none"
  elif [[ -n "${MANUAL_KAKAO_REMOTEINPUT_FAILURE_REASON:-}" ]]; then
    echo "remoteinput_failed"
  elif [[ "$(bool_env "${MANUAL_KAKAO_NOTIFICATION_ACCESS_CONFIRMED:-false}")" != "true" ]]; then
    echo "notification_access_unconfirmed"
  elif [[ -z "${MANUAL_KAKAO_TEST_ROOM:-}" ]]; then
    echo "missing_test_room"
  elif [[ -z "${MANUAL_KAKAO_TEST_SENDER:-}" ]]; then
    echo "missing_test_sender"
  elif [[ "$(bool_env "${MANUAL_KAKAO_IN_LOG_CONFIRMED:-false}")" != "true" ]]; then
    echo "in_log_unconfirmed"
  elif [[ "$(bool_env "${MANUAL_KAKAO_OUT_LOG_CONFIRMED:-false}")" != "true" ]]; then
    echo "out_log_unconfirmed"
  elif [[ "$(bool_env "${MANUAL_KAKAO_REPLY_VISIBLE_IN_KAKAOTALK:-false}")" != "true" ]]; then
    echo "reply_not_visible"
  else
    echo "pending_evidence"
  fi
}

print_manual_kakao_template() {
  cat <<EOF
manual_kakao_e2e_required=true
manual_kakao_notification_access_confirmed=$(bool_env "${MANUAL_KAKAO_NOTIFICATION_ACCESS_CONFIRMED:-false}")
manual_kakao_test_room=${MANUAL_KAKAO_TEST_ROOM:-}
manual_kakao_test_sender=${MANUAL_KAKAO_TEST_SENDER:-}
manual_kakao_in_log_confirmed=$(bool_env "${MANUAL_KAKAO_IN_LOG_CONFIRMED:-false}")
manual_kakao_out_log_confirmed=$(bool_env "${MANUAL_KAKAO_OUT_LOG_CONFIRMED:-false}")
manual_kakao_reply_visible_in_kakaotalk=$(bool_env "${MANUAL_KAKAO_REPLY_VISIBLE_IN_KAKAOTALK:-false}")
manual_kakao_remoteinput_failure_reason=${MANUAL_KAKAO_REMOTEINPUT_FAILURE_REASON:-}
manual_kakao_evidence_note=${MANUAL_KAKAO_EVIDENCE_NOTE:-}
manual_kakao_complete=$(manual_kakao_complete)
manual_kakao_pending_reason=$(manual_kakao_pending_reason)
manual_kakao_steps=1) enable notification access, 2) send KakaoTalk message from another account, 3) confirm IN log with the expected room/sender, 4) confirm OUT log or OUT_FAIL reason, 5) confirm the reply appears in KakaoTalk
EOF
}

contains_log() {
  local pattern="$1"
  if grep -E -q "${pattern}" "${LOGCAT_FILE}" 2>/dev/null; then
    echo "true"
  else
    echo "false"
  fi
}

count_log() {
  local pattern="$1"
  grep -E -c "${pattern}" "${LOGCAT_FILE}" 2>/dev/null || true
}

write_blocker_summary() {
  local reason="$1"
  shift || true
  mkdir -p "${OUT_DIR}"
  {
    echo "status=blocked"
    echo "blocker_reason=${reason}"
    echo "started_at=${STAMP}"
    echo "adb_path=${ADB_BIN}"
    for item in "$@"; do
      echo "${item}"
    done
    print_manual_kakao_template
  } | tee "${SUMMARY_FILE}" >&2
}

if [[ "${1:-}" == "--print-manual-template" ]]; then
  print_manual_kakao_template
  exit 0
fi

if [[ ! -x "${ADB_BIN}" ]]; then
  write_blocker_summary "adb_not_found"
  echo "adb not found: ${ADB_BIN}" >&2
  echo "Set ADB=/path/to/adb or install Android platform-tools." >&2
  exit 1
fi

mkdir -p "${OUT_DIR}"
"${ADB_BIN}" devices -l > "${ADB_DEVICES_FILE}"
mapfile -t DEVICES < <("${ADB_BIN}" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
if [[ "${#DEVICES[@]}" -ne 1 ]]; then
  cat "${ADB_DEVICES_FILE}"
  write_blocker_summary \
    "expected_exactly_one_device" \
    "device_count=${#DEVICES[@]}" \
    "adb_devices_file=${ADB_DEVICES_FILE}"
  echo "Expected exactly one attached Android device." >&2
  exit 1
fi

SERIAL="${DEVICES[0]}"
ABI="$("${ADB_BIN}" -s "${SERIAL}" shell getprop ro.product.cpu.abi | tr -d '\r')"
if [[ "${ABI}" != arm64* ]]; then
  write_blocker_summary \
    "non_arm64_device" \
    "serial=${SERIAL}" \
    "abi=${ABI}" \
    "adb_devices_file=${ADB_DEVICES_FILE}"
  echo "Expected an ARM64 real device, got abi=${ABI}." >&2
  exit 1
fi

FINGERPRINT="$("${ADB_BIN}" -s "${SERIAL}" shell getprop ro.build.fingerprint | tr -d '\r')"
DEVICE_BRAND="$("${ADB_BIN}" -s "${SERIAL}" shell getprop ro.product.brand | tr -d '\r')"
DEVICE_MODEL="$("${ADB_BIN}" -s "${SERIAL}" shell getprop ro.product.model | tr -d '\r')"
ANDROID_RELEASE="$("${ADB_BIN}" -s "${SERIAL}" shell getprop ro.build.version.release | tr -d '\r')"
ANDROID_SDK="$("${ADB_BIN}" -s "${SERIAL}" shell getprop ro.build.version.sdk | tr -d '\r')"
if [[ "${FINGERPRINT}" == *generic* || "${FINGERPRINT}" == *sdk_gphone* ]]; then
  write_blocker_summary \
    "emulator_detected" \
    "serial=${SERIAL}" \
    "abi=${ABI}" \
    "fingerprint=${FINGERPRINT}" \
    "device_brand=${DEVICE_BRAND}" \
    "device_model=${DEVICE_MODEL}" \
    "android_release=${ANDROID_RELEASE}" \
    "android_sdk=${ANDROID_SDK}" \
    "adb_devices_file=${ADB_DEVICES_FILE}"
  echo "This looks like an emulator, not a real device: ${FINGERPRINT}" >&2
  exit 1
fi

cd "${ROOT_DIR}"

{
  echo "status=started"
  echo "serial=${SERIAL}"
  echo "abi=${ABI}"
  echo "fingerprint=${FINGERPRINT}"
  echo "device_brand=${DEVICE_BRAND}"
  echo "device_model=${DEVICE_MODEL}"
  echo "android_release=${ANDROID_RELEASE}"
  echo "android_sdk=${ANDROID_SDK}"
  echo "started_at=${STAMP}"
} | tee "${SUMMARY_FILE}"

"${ADB_BIN}" -s "${SERIAL}" logcat -c
ANDROID_SERIAL="${SERIAL}" ./gradlew installDebug installDebugAndroidTest

"${ADB_BIN}" -s "${SERIAL}" shell am start -n "${PACKAGE_NAME}/.MainActivity" >/dev/null

NOTIFICATION_LISTENERS="$("${ADB_BIN}" -s "${SERIAL}" shell settings get secure enabled_notification_listeners | tr -d '\r' || true)"
if [[ "${NOTIFICATION_LISTENERS}" == *"${PACKAGE_NAME}"* ]]; then
  NOTIFICATION_LISTENER_ENABLED="true"
else
  NOTIFICATION_LISTENER_ENABLED="false"
fi

{
  echo "notification_listener_expected_component=${PACKAGE_NAME}/.NotificationListener"
  echo "notification_listener_enabled=${NOTIFICATION_LISTENER_ENABLED}"
  echo "notification_listener_raw=${NOTIFICATION_LISTENERS}"
} | tee -a "${SUMMARY_FILE}"

set +e
"${ADB_BIN}" -s "${SERIAL}" shell am instrument -w -r \
  -e class "${PACKAGE_NAME}.ExampleInstrumentedTest" \
  "${TEST_PACKAGE}/${RUNNER}" | tee -a "${SUMMARY_FILE}"
TEST_STATUS="${PIPESTATUS[0]}"
set -e

"${ADB_BIN}" -s "${SERIAL}" logcat -d > "${LOGCAT_FILE}"

MODEL_LOAD_LOG_DETECTED="$(contains_log "LlmEngine.*Model loaded successfully|AiProviderClient.*LLM loaded successfully|Model loaded: n_vocab")"
MODEL_GENERATION_LOG_DETECTED="$(contains_log "ExampleInstrumentedTest.*LLM_.*REPLY=|AiProviderClient.*Selected LLM reply source=|AiProviderClient.*raw LLM response length")"
MODEL_GENERATION_REPLY_LOG_COUNT="$(count_log "ExampleInstrumentedTest.*LLM_.*REPLY=")"
MODEL_CANDIDATE_LOG_COUNT="$(count_log "AiProviderClient.*Candidate source=|AiProviderClient.*Selected LLM reply source=")"

MODEL_SIZE="$("${ADB_BIN}" -s "${SERIAL}" shell run-as "${PACKAGE_NAME}" stat -c %s "${MODEL_PATH}" 2>/dev/null | tr -d '\r' || true)"
MODEL_SHA="$("${ADB_BIN}" -s "${SERIAL}" shell run-as "${PACKAGE_NAME}" sha256sum "${MODEL_PATH}" 2>/dev/null | awk '{ print $1 }' | tr -d '\r' || true)"

{
  echo "instrumentation_status=${TEST_STATUS}"
  echo "instrumentation_class=${PACKAGE_NAME}.ExampleInstrumentedTest"
  echo "model_path=${MODEL_PATH}"
  echo "model_size=${MODEL_SIZE}"
  echo "expected_model_size=${EXPECTED_MODEL_SIZE}"
  echo "model_sha256=${MODEL_SHA}"
  echo "expected_model_sha256=${EXPECTED_MODEL_SHA}"
  echo "model_load_log_detected=${MODEL_LOAD_LOG_DETECTED}"
  echo "model_generation_log_detected=${MODEL_GENERATION_LOG_DETECTED}"
  echo "model_generation_reply_log_count=${MODEL_GENERATION_REPLY_LOG_COUNT}"
  echo "model_candidate_log_count=${MODEL_CANDIDATE_LOG_COUNT}"
  echo "logcat_file=${LOGCAT_FILE}"
  print_manual_kakao_template
} | tee -a "${SUMMARY_FILE}"

if [[ "${TEST_STATUS}" -ne 0 ]]; then
  echo "status=instrumentation_failed" | tee -a "${SUMMARY_FILE}" >&2
  echo "Instrumentation failed. See ${SUMMARY_FILE} and ${LOGCAT_FILE}." >&2
  exit "${TEST_STATUS}"
fi

if [[ "${MODEL_SIZE}" != "${EXPECTED_MODEL_SIZE}" ]]; then
  echo "status=model_size_mismatch" | tee -a "${SUMMARY_FILE}" >&2
  echo "Model size mismatch. See ${SUMMARY_FILE}." >&2
  exit 1
fi

if [[ "${MODEL_SHA}" != "${EXPECTED_MODEL_SHA}" ]]; then
  echo "status=model_sha_mismatch" | tee -a "${SUMMARY_FILE}" >&2
  echo "Model SHA-256 mismatch. See ${SUMMARY_FILE}." >&2
  exit 1
fi

if [[ "$(manual_kakao_complete)" == "true" ]]; then
  echo "status=complete" | tee -a "${SUMMARY_FILE}"
  echo "Real-device model checks and manual KakaoTalk E2E evidence are complete."
else
  echo "status=manual_kakao_pending" | tee -a "${SUMMARY_FILE}"
  echo "Device model checks passed, but manual KakaoTalk notification/reply evidence is still pending."
fi
