#!/bin/sh
set -eu

STATE=${1:-idle}
TITLE=${2:-Codex}
NOW_MS=$(($(date +%s) * 1000))
STARTED_MS=${STARTED_MS:-$NOW_MS}
ACTIVE_COUNT=${ACTIVE_COUNT:-1}
QUOTA_5H=${QUOTA_5H:--1}
QUOTA_7D=${QUOTA_7D:--1}
QUOTA_7D_RESETS_AT_MS=${QUOTA_7D_RESETS_AT_MS:-0}
COMMUTE_AVAILABLE=${COMMUTE_AVAILABLE:-false}
COMMUTE_DURATION_MIN=${COMMUTE_DURATION_MIN:--1}
COMMUTE_ARRIVAL_AT_MS=${COMMUTE_ARRIVAL_AT_MS:-0}
COMMUTE_UPDATED_AT_MS=${COMMUTE_UPDATED_AT_MS:-0}
LX04_SERIAL=${LX04_SERIAL:-}

if [ -z "$LX04_SERIAL" ]; then
  echo "LX04_SERIAL is required" >&2
  exit 2
fi

case "$STATE" in
  idle|working|waiting|completed|failed) ;;
  *) echo "state must be idle, working, waiting, completed, or failed" >&2; exit 2 ;;
esac

adb -s "$LX04_SERIAL" shell am broadcast \
  -n com.codex.statusdisplay/.StatusReceiver \
  -a com.codex.status.UPDATE \
  --es state "$STATE" \
  --es title "$TITLE" \
  --es phase "手动测试" \
  --el started_at_ms "$STARTED_MS" \
  --ei active_count "$ACTIVE_COUNT" \
  --ei quota_5h_percent "$QUOTA_5H" \
  --ei quota_7d_percent "$QUOTA_7D" \
  --el quota_7d_resets_at_ms "$QUOTA_7D_RESETS_AT_MS" \
  --ez commute_available "$COMMUTE_AVAILABLE" \
  --ei commute_duration_min "$COMMUTE_DURATION_MIN" \
  --el commute_arrival_at_ms "$COMMUTE_ARRIVAL_AT_MS" \
  --el commute_updated_at_ms "$COMMUTE_UPDATED_AT_MS" \
  --el host_time_ms "$NOW_MS" \
  --ez connected true
