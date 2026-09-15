#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
APP="$ROOT/build/Xiaomi桥接器.app"
CONTENTS="$APP/Contents"
rm -rf "$APP"
mkdir -p "$CONTENTS/MacOS" "$CONTENTS/Resources"
xcrun clang -fobjc-arc -framework AppKit -framework WebKit "$ROOT/mac-app/main.m" -o "$CONTENTS/MacOS/XiaomiBridge"
cp "$ROOT/mac-app/app.py" "$CONTENTS/Resources/app.py"
cp "$ROOT/mac/codex_lx04_bridge.py" "$CONTENTS/Resources/codex_lx04_bridge.py"
cp "$ROOT/build/codex-status.apk" "$CONTENTS/Resources/codex-status.apk"
cp "$ROOT/mac-app/Info.plist" "$CONTENTS/Info.plist"
ICONSET="$ROOT/build/AppIcon.iconset"
rm -rf "$ICONSET";mkdir -p "$ICONSET"
for SIZE in 16 32 128 256 512; do
  sips -z "$SIZE" "$SIZE" "$ROOT/assets/logo/codex-particle-app-icon.png" --out "$ICONSET/icon_${SIZE}x${SIZE}.png" >/dev/null
  DOUBLE=$((SIZE * 2));sips -z "$DOUBLE" "$DOUBLE" "$ROOT/assets/logo/codex-particle-app-icon.png" --out "$ICONSET/icon_${SIZE}x${SIZE}@2x.png" >/dev/null
done
/usr/bin/python3 "$ROOT/mac-app/make_icns.py" "$ICONSET" "$CONTENTS/Resources/AppIcon.icns"
chmod +x "$CONTENTS/MacOS/XiaomiBridge" "$CONTENTS/Resources/app.py" "$CONTENTS/Resources/codex_lx04_bridge.py"
codesign --force --deep --sign - "$APP"
ditto -c -k --keepParent "$APP" "$ROOT/build/Xiaomi桥接器.zip"
echo "$APP"
