#!/bin/sh
set -eu
LABEL=com.codex.lx04-status
launchctl bootout "gui/$(id -u)/$LABEL" 2>/dev/null || true
rm -f "$HOME/Library/LaunchAgents/$LABEL.plist"
echo "Stopped $LABEL"
