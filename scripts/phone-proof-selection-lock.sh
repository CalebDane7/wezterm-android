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
