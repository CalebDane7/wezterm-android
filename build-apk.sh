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

# WHY: the phone plan has repeatedly regressed protected UX after unrelated
# fixes. Run cheap source-level regression gates before compiling so a future
# agent cannot silently ship a build that again requires pressing Enter just to
# reconnect the ttyd WebView transport.
if [ -x scripts/test-reconnect-overlay-guard.sh ]; then
    scripts/test-reconnect-overlay-guard.sh
fi
if [ -x scripts/test-phone-plan-guards.sh ]; then
    PHONE_SKIP_GENERATED_PAGE_GUARD=1 scripts/test-phone-plan-guards.sh
fi

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

# WHY: the phone opens build/install.html for no-USB installs. APK signing is
# not treated as hash-stable here, so a manually maintained SHA can become
# stale after an otherwise valid rebuild. Generate the public install page from
# the just-signed APK so the user never sees a v1.44/v1.45 handoff or a stale
# checksum for the file being served.
VERSION_CODE="$(grep -o 'android:versionCode="[0-9]*"' app/src/main/AndroidManifest.xml | head -n 1 | cut -d'"' -f2)"
VERSION_NAME="$(grep -o 'android:versionName="[^"]*"' app/src/main/AndroidManifest.xml | head -n 1 | cut -d'"' -f2)"
APK_SHA="$(sha256sum build/WEzterm.apk | awk '{print $1}')"
cat > build/install.html <<HTML
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>WEzterm v${VERSION_NAME} Install</title>
  <style>
    :root {
      color-scheme: dark;
      font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      background: #111418;
      color: #f3f5f7;
    }
    body {
      margin: 0;
      min-height: 100vh;
      display: grid;
      place-items: center;
      padding: 24px;
    }
    main {
      width: min(100%, 520px);
    }
    h1 {
      margin: 0 0 12px;
      font-size: 26px;
      line-height: 1.15;
    }
    p {
      color: #c8d0d8;
      line-height: 1.45;
    }
    a {
      display: block;
      margin: 22px 0;
      padding: 16px 18px;
      text-align: center;
      color: #071014;
      background: #7ce7c2;
      border-radius: 8px;
      font-weight: 700;
      text-decoration: none;
    }
    code {
      word-break: break-all;
      color: #d8e2ea;
    }
  </style>
</head>
<body>
  <main>
    <h1>WEzterm v${VERSION_NAME}</h1>
    <p>Install this build to keep Refresh, Active/Old session pickers, current-first Active Sessions, compact Active Sessions action row, deterministic plain toolbar taps, one-tap Active title switching, parent-only old sessions by date, Needs Attention, Copy/Paste with full multi-word clipboard paste, direct Upload toolbar media, streaming video-safe phone uploads, no-USB update flow, automatic reconnect, Android viewer zoom/pan, zoomed true-bottom viewer reach, native composer typing with voice dictation, no duplicate hidden composer action row, safe native prompt submit, separate Start/Stop controls, direct Bottom button, no-reload Active switching, scroll-only Scroll menu, long-press command palette, passive upload/page-finished focus guards, no direct WebView IME typing for terminal-body taps, no activity-start IME request, late-control IME guard, extended two-finger viewer-pan guard, fixed-height toolbar chrome, two-row toolbar tap feedback, safe control retry, /home/cabule new-session cwd, fast no-refresh live-bottom typing recovery, lightweight tmux touch-scroll gestures, quiet touch-bottom copy-mode exit, fast upward flicks, 20-line bounded downward return, 16-line near-bottom quiet restore, and no-refresh bottom-edge scroll guards.</p>
    <a href="./WEzterm.apk" download>Download WEzterm.apk</a>
    <p>Package: <code>com.kaleeb.wezterm</code><br>versionCode: <code>${VERSION_CODE}</code></p>
    <p>SHA-256: <code>${APK_SHA}</code></p>
  </main>
</body>
</html>
HTML
cat > build/index.html <<HTML
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta http-equiv="refresh" content="0; url=./install.html">
  <title>WEzterm v${VERSION_NAME} Install</title>
</head>
<body>
  <p><a href="./install.html">Open WEzterm v${VERSION_NAME} install page</a></p>
</body>
</html>
HTML

# WHY: the first phone-plan guard run happens before generated install pages
# exist for the new manifest version. Run it again here without the skip so
# stale public handoff pages cannot survive a successful APK build.
if [ -x scripts/test-phone-plan-guards.sh ]; then
    scripts/test-phone-plan-guards.sh
fi
echo "$ROOT/build/WEzterm.apk"
