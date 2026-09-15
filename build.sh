#!/bin/sh
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
SDK_ROOT=${ANDROID_SDK_ROOT:-/opt/homebrew/share/android-commandlinetools}
BUILD_TOOLS="$SDK_ROOT/build-tools/35.0.0"
ANDROID_JAR="$SDK_ROOT/platforms/android-35/android.jar"
JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home}
export JAVA_HOME

BUILD_DIR="$PROJECT_DIR/build"
GEN_DIR="$BUILD_DIR/generated"
CLASS_DIR="$BUILD_DIR/classes"
DEX_DIR="$BUILD_DIR/dex"
CLASS_JAR="$BUILD_DIR/classes.jar"
COMPILED_RES="$BUILD_DIR/resources.zip"
UNSIGNED_APK="$BUILD_DIR/codex-status-unsigned.apk"
ALIGNED_APK="$BUILD_DIR/codex-status-aligned.apk"
FINAL_APK="$BUILD_DIR/codex-status.apk"
KEYSTORE="$BUILD_DIR/debug.keystore"

mkdir -p "$GEN_DIR" "$CLASS_DIR" "$DEX_DIR"
rm -f "$COMPILED_RES" "$CLASS_JAR" "$UNSIGNED_APK" "$ALIGNED_APK" "$FINAL_APK"

"$BUILD_TOOLS/aapt2" compile --dir "$PROJECT_DIR/res" -o "$COMPILED_RES"
"$BUILD_TOOLS/aapt2" link \
  -I "$ANDROID_JAR" \
  --manifest "$PROJECT_DIR/AndroidManifest.xml" \
  --java "$GEN_DIR" \
  --min-sdk-version 26 \
  --target-sdk-version 27 \
  -o "$UNSIGNED_APK" \
  "$COMPILED_RES"

"$JAVA_HOME/bin/javac" -source 8 -target 8 \
  -classpath "$ANDROID_JAR" \
  -d "$CLASS_DIR" \
  $(find "$PROJECT_DIR/src" "$GEN_DIR" -name '*.java' -type f)

"$JAVA_HOME/bin/jar" cf "$CLASS_JAR" -C "$CLASS_DIR" .
"$BUILD_TOOLS/d8" --lib "$ANDROID_JAR" --min-api 26 --output "$DEX_DIR" "$CLASS_JAR"
(cd "$DEX_DIR" && "$BUILD_TOOLS/aapt" add "$UNSIGNED_APK" classes.dex)
"$BUILD_TOOLS/zipalign" -f 4 "$UNSIGNED_APK" "$ALIGNED_APK"

if [ ! -f "$KEYSTORE" ]; then
  "$JAVA_HOME/bin/keytool" -genkeypair -noprompt \
    -keystore "$KEYSTORE" -storepass android -keypass android \
    -alias codexdebug -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Codex Status, O=Local, C=US"
fi

"$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --out "$FINAL_APK" "$ALIGNED_APK"
"$BUILD_TOOLS/apksigner" verify --verbose "$FINAL_APK"
echo "$FINAL_APK"
