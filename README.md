![CODEX particle bridge](assets/logo/codex-particle-github-header.png)

# Xiaomi LX04 Codex Particle Bridge

Silent, full-screen Codex task status display for the Xiaomi LX04 (Android 8.1).
Version 2 adds an office-safe GPU particle flow, host-synchronized clock/date,
multi-task status, quota rails, auto-start, and a local Codex bridge.

The home page shows an approximate public-transit duration using nearby street
intersections. Exact building numbers are not sent to the speaker or Google Maps.

## Build

```sh
./build.sh
```

The APK is written to `build/codex-status.apk`.

Build the zero-dependency Python standard-library macOS bridge with:

```sh
./mac/build-bridge.sh
```

Install the login service with:

```sh
./mac/install-launch-agent.sh
```

Remove the login service with `./mac/uninstall-launch-agent.sh`.

## Xiaomi桥接器 App

Build the shareable macOS settings app with `./mac-app/build-app.sh`. The output
is `build/Xiaomi桥接器.app`, with a zipped copy at `build/Xiaomi桥接器.zip`.
The app bundles the display APK and bridge, and exposes device serial, commute
intersections, refresh interval, ADB path, Chrome path, login startup, connection
test, and APK installation in one local settings page. Chrome headless rendering
is launched and managed inside the app; no visible Maps window is opened.

Closing the settings window keeps the bridge running in the background. Click
the Dock icon to reopen it; use **Xiaomi桥接器 → 退出 Xiaomi桥接器** to stop it.
The commute mode is selectable between public transit, driving, and walking;
only the selected mode is shown on the speaker.

## Control over ADB

```sh
./status.sh working "Implementing feature"
./status.sh waiting "Needs approval"
./status.sh completed "Task complete"
./status.sh failed "Build failed"
./status.sh idle "Ready"
```

The app is deliberately silent and requests no audio permissions.

The bridge prefers Codex Desktop's local IPC socket and falls back to recent
session JSONL. Only a sanitized task title and aggregate status are sent over
USB ADB. Override the target with `LX04_SERIAL` when needed.

For an idle-screen ADB preview with a 61-minute trip:

```sh
NOW_MS=$(($(date +%s) * 1000))
COMMUTE_AVAILABLE=true COMMUTE_DURATION_MIN=61 \
COMMUTE_UPDATED_AT_MS=$NOW_MS COMMUTE_ARRIVAL_AT_MS=$((NOW_MS + 3660000)) \
./status.sh idle "Codex"
```
