#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
REPORT_PATH="${ROOT_DIR}/app/build/reports/reply-quality/report.md"

cd "${ROOT_DIR}"
./gradlew testDebugUnitTest --tests 'com.example.kakaotalkautobot.ReplyQualityReportTest'

echo "reply_quality_report=${REPORT_PATH}"
