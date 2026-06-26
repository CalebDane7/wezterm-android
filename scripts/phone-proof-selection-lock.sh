#!/usr/bin/env bash

phone_proof_fail_lock() {
    echo "phone proof selection lock failed: $*" >&2
    return 1
}

phone_proof_lock_value() {
    local file="$1"
    local key="$2"
    awk -F= -v wanted="$key" '$1 == wanted {print substr($0, length($1) + 2); exit}' "$file"
}

phone_proof_current_monitor() {
    tmux display-message -p '#{session_name}:#{window_id}' 2>/dev/null || true
}

phone_proof_refuse_visible_apk_draft() {
    local context="${1:-phone proof}"
    local serial="${ADB_SERIAL:-100.77.22.120:5555}"
    local timeout_s="${PHONE_PROOF_DRAFT_PREFLIGHT_TIMEOUT_SECONDS:-8}"
    local focus remote_xml local_xml

    if [ "${PHONE_PROOF_ALLOW_DRAFT_STEAL:-0}" = "1" ]; then
        echo "phone proof visible-draft preflight bypassed for $context by PHONE_PROOF_ALLOW_DRAFT_STEAL=1" >&2
        return 0
    fi
    if [ "${PHONE_PROOF_SKIP_DRAFT_PREFLIGHT:-0}" = "1" ]; then
        return 0
    fi
    command -v adb >/dev/null 2>&1 || return 0
    command -v timeout >/dev/null 2>&1 || return 0

    focus="$(timeout "${timeout_s}s" adb -s "$serial" shell dumpsys window 2>/dev/null \
        | sed -n 's/.*mCurrentFocus=//p' | head -n 1 || true)"
    case "$focus" in
        *com.kaleeb.wezterm*) ;;
        *) return 0 ;;
    esac

    remote_xml="/sdcard/wezterm-draft-preflight-$$.xml"
    local_xml="${TMPDIR:-/tmp}/wezterm-draft-preflight-$$.xml"
    if ! timeout "${timeout_s}s" adb -s "$serial" shell uiautomator dump "$remote_xml" >/dev/null 2>&1; then
        phone_proof_fail_lock "$context could not dump WEzTerm UI before moving tabs"
        return 1
    fi
    if ! timeout "${timeout_s}s" adb -s "$serial" pull "$remote_xml" "$local_xml" >/dev/null 2>&1; then
        timeout "${timeout_s}s" adb -s "$serial" shell rm -f "$remote_xml" >/dev/null 2>&1 || true
        phone_proof_fail_lock "$context could not read WEzTerm UI before moving tabs"
        return 1
    fi
    timeout "${timeout_s}s" adb -s "$serial" shell rm -f "$remote_xml" >/dev/null 2>&1 || true

    if ! python3 - "$local_xml" "$context" <<'PY'
import sys
import xml.etree.ElementTree as ET

path, context = sys.argv[1:]
root = ET.parse(path).getroot()
empty_hint_texts = {
    "Type prompt - tap Send",
}
for node in root.iter("node"):
    if node.attrib.get("package") != "com.kaleeb.wezterm":
        continue
    if node.attrib.get("class") != "android.widget.EditText":
        continue
    text = (node.attrib.get("text") or "").strip()
    if text in empty_hint_texts:
        continue
    if text:
        print(
            "phone proof selection lock failed: "
            f"{context} would move tabs while a visible nonempty WEzTerm draft is present "
            f"({len(text)} chars); set PHONE_PROOF_ALLOW_DRAFT_STEAL=1 only for a disposable proof target",
            file=sys.stderr,
        )
        sys.exit(75)
PY
    then
        rm -f "$local_xml"
        return 1
    fi
    rm -f "$local_xml"
}

phone_proof_require_selection_locks() {
    local context="${1:-phone proof}"
    local root="${PHONE_PROOF_LOCK_ROOT:-/home/cabule/.mantis/fork-lanes/20260623-1030-phone-web-matrix/locks}"
    local current_monitor="${PHONE_PROOF_LOCK_VISIBLE_MONITOR:-$(phone_proof_current_monitor)}"
    local lock owner monitor first_owner="" first_monitor=""

    if [ "${PHONE_PROOF_ALLOW_UNLOCKED_SELECTION:-0}" = "1" ]; then
        echo "phone proof selection lock bypassed for $context by PHONE_PROOF_ALLOW_UNLOCKED_SELECTION=1" >&2
        return 0
    fi
    [ -n "$current_monitor" ] || phone_proof_fail_lock "$context could not resolve the current tmux session/window"

    for lock in real-phone-ui-proof.lock tmux-mainphone-selection.lock; do
        local owner_file="$root/$lock/OWNER"
        [ -f "$owner_file" ] || phone_proof_fail_lock "$context requires $owner_file"
        owner="$(phone_proof_lock_value "$owner_file" owner)"
        monitor="$(phone_proof_lock_value "$owner_file" visible_monitor)"
        [ -n "$owner" ] || phone_proof_fail_lock "$context found $lock without an owner"
        [ -n "$monitor" ] || phone_proof_fail_lock "$context found $lock without visible_monitor"
        if [ "$monitor" != "$current_monitor" ]; then
            phone_proof_fail_lock "$context would move main_phone, but $lock is owned by $owner at $monitor; current tmux window is $current_monitor"
        fi
        if [ -n "$first_owner" ] && [ "$owner" != "$first_owner" ]; then
            phone_proof_fail_lock "$context found mismatched lock owners: $first_owner and $owner"
        fi
        if [ -n "$first_monitor" ] && [ "$monitor" != "$first_monitor" ]; then
            phone_proof_fail_lock "$context found mismatched lock monitors: $first_monitor and $monitor"
        fi
        first_owner="$owner"
        first_monitor="$monitor"
    done

    export PHONE_PROOF_SELECTION_LOCK_OWNER="$first_owner"
    export PHONE_PROOF_SELECTION_LOCK_MONITOR="$first_monitor"
    export PHONE_PROOF_SELECTION_LOCK_HEADERS_READY=1
    phone_proof_refuse_visible_apk_draft "$context"
}

phone_proof_curl() {
    local arg
    if [ "${PHONE_PROOF_SELECTION_LOCK_HEADERS_READY:-0}" = "1" ]; then
        for arg in "$@"; do
            if [ "${CONTROL_URL:-}" ] && [[ "$arg" == "$CONTROL_URL"* ]]; then
                command curl \
                    -H "X-Mantis-Automation: 1" \
                    -H "X-Mantis-Selection-Lock-Owner: ${PHONE_PROOF_SELECTION_LOCK_OWNER:-}" \
                    -H "X-Mantis-Selection-Lock-Monitor: ${PHONE_PROOF_SELECTION_LOCK_MONITOR:-}" \
                    "$@"
                return
            fi
        done
    fi
    command curl "$@"
}
