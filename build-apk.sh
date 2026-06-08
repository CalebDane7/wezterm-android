#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK="${ANDROID_HOME:-$HOME/.local/share/android-sdk}"
BUILD_TOOLS="$SDK/build-tools/35.0.0"
ANDROID_JAR="$SDK/platforms/android-35/android.jar"
KEYSTORE="$HOME/.android/wezterm-debug.keystore"

AAPT2="$BUILD_TOOLS/aapt2"
D8="$BUILD_TOOLS/d8"
ZIPALIGN="$BUILD_TOOLS/zipalign"
APKSIGNER="$BUILD_TOOLS/apksigner"

for tool in "$AAPT2" "$D8" "$ZIPALIGN" "$APKSIGNER" "$ANDROID_JAR" "$KEYSTORE"; do
    if [ ! -e "$tool" ]; then
        echo "Missing build dependency: $tool" >&2
        exit 1
    fi
done

cd "$ROOT"
rm -rf build/compiled build/gen build/classes build/dex
rm -f build/WEzterm-unsigned.apk build/WEzterm-with-dex-unsigned.apk build/WEzterm-aligned-unsigned.apk build/WEzterm.apk build/WEzterm.apk.idsig
mkdir -p build/compiled build/gen build/classes build/dex

"$AAPT2" compile --dir app/src/main/res -o build/compiled
"$AAPT2" link \
    -o build/WEzterm-unsigned.apk \
    -I "$ANDROID_JAR" \
    --manifest app/src/main/AndroidManifest.xml \
    --min-sdk-version 26 \
    --target-sdk-version 35 \
    --java build/gen \
    build/compiled/*.flat

# WHY: source-adjacent timestamped backups are required by the phone plan, but
# javac must ignore those folders or it compiles duplicate public MainActivity
# classes from preserved backup copies.
javac --release 8 \
    -classpath "$ANDROID_JAR" \
    -d build/classes \
    $(find app/src/main/java build/gen -name '*.java' -not -path '*.backups/*' | sort)

"$D8" \
    --lib "$ANDROID_JAR" \
    --min-api 26 \
    --output build/dex \
    $(find build/classes -name '*.class' | sort)

cp build/WEzterm-unsigned.apk build/WEzterm-with-dex-unsigned.apk
(cd build/dex && zip -q ../WEzterm-with-dex-unsigned.apk classes.dex)

"$ZIPALIGN" -f 4 build/WEzterm-with-dex-unsigned.apk build/WEzterm-aligned-unsigned.apk
"$APKSIGNER" sign \
    --ks "$KEYSTORE" \
    --ks-key-alias wezterm \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out build/WEzterm.apk \
    build/WEzterm-aligned-unsigned.apk

"$APKSIGNER" verify --print-certs build/WEzterm.apk >/dev/null
echo "$ROOT/build/WEzterm.apk"
