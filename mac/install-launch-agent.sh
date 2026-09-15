#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
DEST="$HOME/Library/Application Support/CodexLX04"
PLIST="$HOME/Library/LaunchAgents/com.codex.lx04-status.plist"
mkdir -p "$DEST" "$HOME/Library/LaunchAgents"
cp "$ROOT/build/mac/codex-lx04-bridge" "$DEST/codex-lx04-bridge"
sed "s|__PROGRAM__|$DEST/codex-lx04-bridge|g" "$ROOT/mac/com.codex.lx04-status.plist.template" > "$PLIST"
launchctl bootout "gui/$(id -u)/com.codex.lx04-status" 2>/dev/null || true
launchctl bootstrap "gui/$(id -u)" "$PLIST"
launchctl kickstart -k "gui/$(id -u)/com.codex.lx04-status"
echo "$PLIST"
