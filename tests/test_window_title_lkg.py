from dataclasses import dataclass
from pathlib import Path


TRANSACTION = Path(__file__).resolve().parents[1]
APP_ROOT = TRANSACTION / "app"
if not APP_ROOT.is_dir():
    # WHY: the original recovery transaction used candidate/app; the canonical
    # release repository uses app directly. The guard must follow identical
    # source bytes in either layout instead of requiring a transaction symlink.
    APP_ROOT = TRANSACTION / "candidate" / "app"
SOURCE = APP_ROOT / "src/main/java/com/kaleeb/wezterm/MainActivity.java"
MANIFEST = APP_ROOT / "src/main/AndroidManifest.xml"


@dataclass(frozen=True)
class Envelope:
    thread_id: str
    revision: int
    digest: str
    display_title: str


@dataclass(frozen=True)
class Binding:
    window_id: str
    session_id: str
    thread_id: str
    pane_id: str
    pane_incarnation_digest: str
    revision: int
    digest: str
    envelope: Envelope

    def references(self, envelope: Envelope) -> bool:
        return (
            self.thread_id == envelope.thread_id
            and self.revision == envelope.revision
            and self.digest == envelope.digest
        )

    def slot_key(self) -> tuple[str, str, str, str]:
        return (
            self.window_id,
            self.session_id,
            self.pane_id,
            self.pane_incarnation_digest,
        )


def v291_exact(retained: Envelope, binding: Binding):
    return retained if binding.references(retained) else None


def candidate_exact(retained: Envelope, binding: Binding):
    if binding.references(retained):
        return retained
    bound = binding.envelope
    return bound if binding.references(bound) else None


def candidate_can_replace(incoming: Binding, previous: Binding) -> bool:
    if incoming.window_id != previous.window_id:
        return False
    if incoming.thread_id != previous.thread_id:
        return True
    return incoming.envelope.revision >= previous.envelope.revision


def candidate_remember_slot(
    slot_store: dict[tuple[str, str, str, str], Binding],
    incoming: Binding,
) -> None:
    previous = slot_store.get(incoming.slot_key())
    if previous is not None:
        if incoming.thread_id == previous.thread_id:
            if incoming.revision < previous.revision:
                return
        elif incoming.slot_key() != previous.slot_key():
            return
    slot_store[incoming.slot_key()] = incoming


def candidate_restore_retained_slot(
    slot_store: dict[tuple[str, str, str, str], Binding],
    retained_binding: Binding,
) -> Envelope | None:
    current = slot_store.get(retained_binding.slot_key())
    if current is None or current.slot_key() != retained_binding.slot_key():
        return None
    return current.envelope if current.references(current.envelope) else None


def candidate_restore_retained_row_slot(
    slot_store: dict[tuple[str, str, str, str], Binding],
    window_id: str,
    session_id: str,
    pane_id: str,
    pane_incarnation_digest: str,
) -> Envelope | None:
    current = slot_store.get(
        (window_id, session_id, pane_id, pane_incarnation_digest)
    )
    return (
        current.envelope
        if current is not None and current.references(current.envelope)
        else None
    )


def binding(window_id: str, thread_id: str, revision: int, tail: str) -> Binding:
    digest = f"digest-{revision}-{tail}"
    envelope = Envelope(
        thread_id=thread_id,
        revision=revision,
        digest=digest,
        display_title=f"Mantis Control title sync: {tail}",
    )
    return Binding(
        window_id=window_id,
        session_id="main",
        thread_id=thread_id,
        pane_id=f"%{window_id.removeprefix('@')}",
        pane_incarnation_digest="a" * 64,
        revision=revision,
        digest=digest,
        envelope=envelope,
    )


def test_old_red_thread_refresh_cannot_blank_same_window_binding():
    # Literal v291 mechanism from the 22:41-22:43 APK log: @30's toolbar
    # advances the thread store to rev79 while the visible/cached row still owns
    # the fully validated rev78 projection.
    previous = binding("@30", "thread-30", 78, "checking inputs")
    refreshed = binding("@30", "thread-30", 79, "restoring inputs")

    assert v291_exact(refreshed.envelope, previous) is None
    assert candidate_exact(refreshed.envelope, previous) == previous.envelope
    assert candidate_exact(
        refreshed.envelope, previous
    ).display_title == "Mantis Control title sync: checking inputs"


def test_fresh_same_window_revision_replaces_lkg_without_cross_window_or_rollback():
    previous = binding("@30", "thread-30", 78, "checking inputs")
    refreshed = binding("@30", "thread-30", 79, "restoring inputs")
    other_window = binding("@39", "thread-39", 4115, "testing phone path")
    stale = binding("@30", "thread-30", 77, "older cache")

    assert candidate_can_replace(refreshed, previous)
    assert not candidate_can_replace(other_window, previous)
    assert not candidate_can_replace(stale, previous)


def test_send_then_active_rejoins_latest_exact_slot_without_blank_or_cross_row_bleed():
    # Installed v292 old red at 01:23:28-01:23:36: Send advanced @107's
    # canonical thread LKG, then Active painted a 33.6-second retained binding.
    # Thread-only digest equality rejected that retained row and rendered a green
    # Working card with no title. The exact slot store is updated only by a fully
    # validated producer projection for the same window/session/pane incarnation.
    previous = binding("@107", "thread-107", 79, "before send")
    refreshed = binding("@107", "thread-107", 80, "treating red")
    other_window = binding("@108", "thread-108", 400, "scroll proof")
    stale = binding("@107", "thread-107", 78, "older cache")
    slot_store: dict[tuple[str, str, str, str], Binding] = {}

    candidate_remember_slot(slot_store, previous)
    candidate_remember_slot(slot_store, refreshed)
    candidate_remember_slot(slot_store, other_window)
    candidate_remember_slot(slot_store, stale)

    restored = candidate_restore_retained_slot(slot_store, previous)
    assert restored is not None
    assert restored.display_title == "Mantis Control title sync: treating red"
    assert candidate_restore_retained_slot(slot_store, other_window) == other_window.envelope
    assert candidate_restore_retained_slot(slot_store, binding(
        "@109", "thread-107", 79, "wrong row"
    )) is None


def test_v292_missing_binding_rejoins_only_exact_validated_retained_row_slot():
    # Literal v293 first-open old red at 01:51:46: v292 had already removed the
    # selected @107 row's stale binding before persisting/reusing the retained
    # dialog. The row still carried exact non-semantic window/session/pane
    # incarnation identity, and the live toolbar had validated @107 rev92 into
    # the canonical slot store before Active opened.
    current = binding("@107", "thread-107", 92, "replaying surface")
    slot_store = {current.slot_key(): current}

    restored = candidate_restore_retained_row_slot(
        slot_store,
        current.window_id,
        current.session_id,
        current.pane_id,
        current.pane_incarnation_digest,
    )
    assert restored is not None
    assert restored.display_title == "Mantis Control title sync: replaying surface"
    assert candidate_restore_retained_row_slot(
        slot_store,
        "@108",
        current.session_id,
        current.pane_id,
        current.pane_incarnation_digest,
    ) is None


def test_source_uses_only_validated_binding_fallback_at_all_native_title_consumers():
    source = SOURCE.read_text(encoding="utf-8")
    row_start = source.index("    private void addTabRow(")
    row_title_end = source.index("        String status =", row_start)
    row_title_consumer = source[row_start:row_title_end]
    strip_start = source.index(
        "    private void updateSessionTitleStrip(JSONObject window)"
    )
    strip_end = source.index(
        "    private void updateSessionTitleStrip(CanonicalTitleBinding binding)",
        strip_start,
    )
    strip_consumer = source[strip_start:strip_end]

    assert "final CanonicalTitleEnvelope envelope;" in source
    assert "CanonicalTitleEnvelope bound = binding.envelope;" in source
    assert "binding.transport.references(bound)" in source
    assert "incomingBinding.canReplaceVisibleBinding(" in source
    assert "private final LinkedHashMap<String, CanonicalTitleBinding> lkgBySlot" in source
    assert "static String retainedRowSlotKey(JSONObject row)" in source
    assert "TitleTransportBinding.retainedRowSlotKey(object)" in source
    assert "using the strictly validated retained row slot only when" in source
    assert "retainedSlotKey.equals(\n                                    resolvedTransport.slotKey()" in source
    assert 'object.put(\n                                "titleTransportBinding",\n                                resolvedTransport.toTransportJson()' in source
    assert "WHY(Send -> Active no-blank join)" in source
    assert source.count('canonicalTitleForBinding(titleBinding);') >= 1
    assert 'canonicalTitleForBinding(binding);' in source
    assert 'window.optString("rawName"' not in row_title_consumer
    assert 'window.optString("name"' not in row_title_consumer
    assert 'window.optString("rawName"' not in strip_consumer
    assert 'window.optString("name"' not in strip_consumer
    assert 'sessionTitleStrip.setText(display);' in source


def test_candidate_version_is_install_distinct_only_by_expected_manifest_bump():
    manifest = MANIFEST.read_text(encoding="utf-8")
    assert 'android:versionCode="296"' in manifest
    assert 'android:versionName="3.83"' in manifest
