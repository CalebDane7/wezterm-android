#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STATE_DIR="${MANTIS_STATE_DIR:-$HOME/.mantis}"
LOG="${WEZTERM_APK_RELEASE_LOG:-$STATE_DIR/logs/wezterm-apk-release.log}"
REPO="${WEZTERM_APK_GITHUB_REPO:-CalebDane7/wezterm-android}"
IDLE_SECONDS="${WEZTERM_APK_RELEASE_IDLE_SECONDS:-60}"
FORCE=0
mkdir -p "$(dirname "$LOG")" "$STATE_DIR"
exec >>"$LOG" 2>&1

while [ $# -gt 0 ]; do
  case "$1" in
    --force) FORCE=1; shift ;;
    *) echo "unknown arg: $1" >&2; exit 1 ;;
  esac
done

timestamp() {
  date -Iseconds
}

die() {
  echo "[$(timestamp)] ERROR: $*" >&2
  exit 1
}

have() {
  command -v "$1" >/dev/null 2>&1
}

cd "$ROOT"
have git || die "git is required"
have gh || die "gh is required"

if [ -n "$(git status --porcelain)" ]; then
  echo "[$(timestamp)] waiting for WEzTerm source autopush before APK release; repo is dirty"
  git status --short | sed 's/^/[dirty] /'
  exit 0
fi

if [ "$FORCE" = "0" ]; then
  recent="$(find "$ROOT" -type f -mmin "-$(( (IDLE_SECONDS + 59) / 60 ))" \
    -not -path "$ROOT/.git/*" \
    -not -path "$ROOT/build/*" \
    -not -path "$ROOT/backups/*" \
    -not -path "$ROOT/scripts/backups/*" \
    -not -path "$ROOT/*.backups/*" \
    -not -path "$ROOT/*/*.backups/*" \
    | head -1)"
  if [ -n "$recent" ]; then
    echo "[$(timestamp)] skipping APK release; recent edit still settling: $recent"
    exit 0
  fi
fi

git fetch --quiet origin main --tags
head_sha="$(git rev-parse HEAD)"
origin_sha="$(git rev-parse origin/main)"
if [ "$head_sha" != "$origin_sha" ]; then
  echo "[$(timestamp)] waiting for origin/main to match local HEAD before APK release local=$head_sha origin=$origin_sha"
  exit 0
fi

version_name="$(grep -o 'android:versionName="[^"]*"' app/src/main/AndroidManifest.xml | head -n 1 | cut -d'"' -f2)"
version_code="$(grep -o 'android:versionCode="[0-9]*"' app/src/main/AndroidManifest.xml | head -n 1 | cut -d'"' -f2)"
[ -n "$version_name" ] || die "manifest versionName missing"
[ -n "$version_code" ] || die "manifest versionCode missing"
tag="v$version_name"

if git ls-remote --exit-code --tags origin "refs/tags/$tag" >/dev/null 2>&1; then
  if gh release view "$tag" --repo "$REPO" >/dev/null 2>&1; then
    echo "[$(timestamp)] no APK release needed; $tag already exists"
    exit 0
  fi
fi

# WHY: APK releases happen outside the Mantis bundle repo, but the phone install
# page and Mantis Remote notification cron read GitHub Releases. Build from the
# clean, pushed source commit here so a tagged APK cannot drift from origin/main.
"$ROOT/build-apk.sh" >/tmp/wezterm-apk-release-build.log 2>&1 || {
  cat /tmp/wezterm-apk-release-build.log >&2
  die "build-apk.sh failed"
}
apk="$ROOT/build/WEzterm.apk"
[ -s "$apk" ] || die "missing built APK at $apk"
sha="$(sha256sum "$apk" | awk '{print $1}')"

notes="$(mktemp)"
trap 'rm -f "$notes"' EXIT
cat >"$notes" <<EOF
WEzTerm Android $tag

- versionCode: $version_code
- APK sha256: $sha
- Stop maps to one desktop Escape.
- Enter / IME action submits through the same pinned path as Send.
EOF

if ! git rev-parse "$tag" >/dev/null 2>&1; then
  git tag -a "$tag" -m "WEzTerm Android $tag" "$head_sha"
fi
git push --quiet origin "$tag"

if gh release view "$tag" --repo "$REPO" >/dev/null 2>&1; then
  gh release upload "$tag" "$apk#WEzterm.apk" --repo "$REPO" --clobber
else
  gh release create "$tag" "$apk#WEzterm.apk" \
    --repo "$REPO" \
    --title "WEzTerm Android $tag" \
    --notes-file "$notes" \
    --verify-tag
fi

echo "[$(timestamp)] published APK release $tag versionCode=$version_code sha256=$sha repo=$REPO"
