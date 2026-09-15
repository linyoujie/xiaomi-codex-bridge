#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
mkdir -p "$ROOT/build/mac"
cp "$ROOT/mac/codex_lx04_bridge.py" "$ROOT/build/mac/codex-lx04-bridge"
chmod +x "$ROOT/build/mac/codex-lx04-bridge"
PYTHONPYCACHEPREFIX="$ROOT/build/pycache" python3 -m py_compile "$ROOT/mac/codex_lx04_bridge.py"
echo "$ROOT/build/mac/codex-lx04-bridge"
