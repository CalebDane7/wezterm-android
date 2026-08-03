#!/usr/bin/env node

import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const mainPath = path.join(root, "app/src/main/java/com/kaleeb/wezterm/MainActivity.java");
const serverPath = process.env.SCROLL_SERVER_PATH
  || "/home/cabule/.ai-controller-repo/configs/scripts/mantis-phone-control-server";
const main = fs.readFileSync(mainPath, "utf8");
const server = fs.readFileSync(serverPath, "utf8");
const normalPollBounds = server.match(
  /const normalPollMinDelayMs=(\d+), normalPollMaxDelayMs=(\d+)/,
);
if (!normalPollBounds) {
  fail("NORMAL_POLL_BOUNDS_NOT_FOUND");
}
const normalPollMinDelayMs = Number(normalPollBounds[1]);
const normalPollMaxDelayMs = Number(normalPollBounds[2]);
if (!(normalPollMinDelayMs > 0 && normalPollMaxDelayMs >= normalPollMinDelayMs)) {
  fail("NORMAL_POLL_BOUNDS_INVALID");
}
const chromePath = "/usr/bin/google-chrome";
const requestedParityCase = String(process.env.SCROLL_PARITY_CASE || "all").trim().toLowerCase();
const parityCase = requestedParityCase === "visible-commit-recovery"
  ? "visible-commit-lease-recovery"
  : requestedParityCase;

function fail(message) {
  console.error(`scroll anchor prepend contract failed: ${message}`);
  process.exit(1);
}

// WHY: a misspelled selector previously ran only common setup and exited green
// with a null named result. Named contract proofs must execute or fail closed.
const knownParityCases = new Set([
  "all", "bootstrap", "dynamic", "hidden-underrun", "narrow", "ordering",
  "out-of-order", "owner-reset-exception", "pinch", "public-measure",
  "refill-lifecycle", "refill-pending-gap-callback", "refill-renderer-lifecycle",
  "refill-single-flight",
  "stale-inflight-aba", "stale-inflight-width", "stale-inflight-width-post-commit",
  "stale-inflight-width-pre-commit", "visible-commit-lease-recovery",
]);
if (!knownParityCases.has(parityCase)) {
  fail(`UNKNOWN_SCROLL_PARITY_CASE:${requestedParityCase || "<empty>"}`);
}

function oldSignature() {
  console.error("NO_READY_PREPEND_AT_BOUNDARY");
  process.exit(1);
}

function requireText(source, value, message) {
  if (!source.includes(value)) fail(message);
}

function canonicalGeneratedFixture(renderCols, fullChain) {
  const fixtureScript = String.raw`
import json
import runpy
import sys
import threading
from urllib.parse import urlencode

source = sys.argv[1]
render_cols = int(sys.argv[2])
full_chain = sys.argv[3] == "1"
namespace = runpy.run_path(source, run_name="scroll_anchor_generated_product_guard")
renderer = namespace.get("terminal_renderer_html")
control_state = namespace.get("ControlState")
handler_type = namespace.get("Handler")
if not callable(renderer) or control_state is None or handler_type is None:
    raise SystemExit("canonical renderer, ControlState, or HTTP Handler missing")

history_size = 13000
pane_height = 40
generation_key = "@777:%777:4242:200000"

def fake_run_tmux(*args):
    if args and args[0] == "display-message":
        return "@777\t%777\t4242\t13000\t200000\t40\t132\t0\t\t0\t39\t1\n"
    if args and args[0] == "capture-pane":
        start = int(args[args.index("-S") + 1]) + history_size
        end = int(args[args.index("-E") + 1]) + history_size
        rows = []
        for absolute_row in range(start, end + 1):
            text = f"ROW-{absolute_row:05d} canonical fixture | separator | " + ("wrap-token-" * 6)
            color = 31 + (absolute_row % 6)
            rows.append(f"\x1b[{color}m{text}\x1b[0m")
        return "\n".join(rows) + "\n"
    raise RuntimeError(f"unexpected tmux call: {args!r}")

namespace["run_tmux"] = fake_run_tmux
control_state.bounded_scrollback_chunk.__globals__["run_tmux"] = fake_run_tmux
state = object.__new__(control_state)
state.target_window = lambda **kwargs: "guard:@777"
# WHY: this fixture intentionally bypasses ControlState.__init__; mirror only
# the generation state read by the frozen canonical terminal-frame producer.
state._terminal_frame_generation_lock = threading.Lock()
state._terminal_frame_generation_epoch = "scroll-anchor-fixture"
state._terminal_frame_generation_by_window = {}
state._terminal_frame_generation_ready = False
# WHY: ControlState is deliberately allocated without __init__ so this fixture
# cannot create sessions, watchers, or runtime state. The canonical frame
# producer now snapshots the initialized light-tabs selection fence before
# rendering; model its neutral "not ready" state exactly so the guard exercises
# frame behavior without bypassing that production generation boundary.
state._light_tabs_selection_lock = threading.Lock()
state._light_tabs_selection_epoch = "scroll-anchor-selection-fixture"
state._light_tabs_selection_generation = 0
state._light_tabs_selection_window_id = ""
state._light_tabs_selection_ready = False

def routed_chunk(start):
    query = {"windowId": "@777", "lines": "500", "cols": str(render_cols)}
    if start is not None:
        query["start"] = str(start)
    captured = {}
    handler = object.__new__(handler_type)
    handler.path = "/scrollback/chunk?" + urlencode(query)
    handler.state = state
    handler.headers = {}
    handler.handle_web_get = lambda parsed, parsed_query: False
    handler.require_web_authorization = lambda parsed, parsed_query, method: (True, "")
    handler.enforce_automation_selection_lock = lambda parsed, parsed_query: True
    handler.send_json = lambda status, payload: captured.update(status=int(status), payload=payload)
    handler.do_GET()
    if captured.get("status") != 200 or not captured.get("payload", {}).get("ok"):
        raise SystemExit(f"canonical routed fixture failed: {captured}")
    return captured["payload"]

def immutable_batch(payload, ordinal):
    if payload.get("generationKey") != generation_key:
        raise SystemExit(f"canonical identity drifted: {payload.get('generationKey')}")
    if int(payload.get("renderCols", -1)) != render_cols:
        raise SystemExit(f"canonical render cols drifted: {payload.get('renderCols')} != {render_cols}")
    return {
        "state": "READY",
        "windowId": payload["windowId"],
        "generationKey": payload["generationKey"],
        "start": payload["start"],
        "end": payload["end"],
        "liveRowFrontier": payload["liveRowFrontier"],
        "hasMoreBefore": payload["hasMoreBefore"],
        "prevStart": payload["prevStart"],
        "renderCols": payload["renderCols"],
        "prepareElapsedMs": 120 + (ordinal % 5) * 25,
        "renderRows": payload["renderRows"],
    }

batches = []
cursor = None
seen_starts = set()
while True:
    payload = routed_chunk(cursor)
    batches.append(immutable_batch(payload, len(batches)))
    if not full_chain or not payload.get("hasMoreBefore"):
        break
    cursor = payload.get("prevStart")
    if cursor is None or cursor in seen_starts:
        raise SystemExit(f"producer cursor did not advance: {cursor}")
    seen_starts.add(cursor)

if full_chain and (len(batches) < 26 or batches[-1]["start"] != 0):
    raise SystemExit(f"producer chain did not reach row zero: count={len(batches)} last={batches[-1]['start']}")

frame_status, frame_payload = state.terminal_render_frame(
    window_id="@777",
    rows=500,
    cols=render_cols,
)
if int(frame_status) != 200 or not isinstance(frame_payload, dict) or not frame_payload.get("ok"):
    raise SystemExit(f"canonical live frame failed: status={frame_status} payload={frame_payload}")
live_frame = {
    "rows": frame_payload.get("rows", []),
    "rowsHtml": frame_payload.get("rowsHtml", []),
    "rowKeys": frame_payload.get("rowKeys", []),
    "requestedCols": frame_payload.get("requestedCols"),
    "liveRowFrontier": frame_payload.get("liveRowFrontier"),
    "generationKey": frame_payload.get("generationKey"),
}
print(json.dumps({"html": renderer(), "batches": batches, "liveFrame": live_frame}, separators=(",", ":")))
`;
  const result = spawnSync("python3", ["-", serverPath, String(renderCols), fullChain ? "1" : "0"], {
    input: fixtureScript,
    encoding: "utf8",
    maxBuffer: 128 * 1024 * 1024,
  });
  if (result.status !== 0) {
    fail(`canonical producer fixture failed: ${(result.stderr || result.stdout).trim()}`);
  }
  try {
    return JSON.parse(result.stdout);
  } catch (error) {
    fail(`canonical producer fixture returned invalid JSON: ${error.message}`);
  }
}

function runGeneratedChrome(html, probe, label, virtualTimeBudget = 1200, options = {}) {
  if (!fs.existsSync(chromePath)) fail(`headless Chrome missing at ${chromePath}`);
  const injected = `<pre id="contractResult"></pre>${probe}`;
  const pageHtml = html.replace("</body>", `${injected}</body>`);
  const temporaryRoot = fs.mkdtempSync(path.join(os.tmpdir(), `scroll-anchor-${label}-`));
  const pagePath = path.join(temporaryRoot, "renderer.html");
  const profilePath = path.join(temporaryRoot, "chrome-profile");
  fs.writeFileSync(pagePath, pageHtml, "utf8");
  const viewportMode = options.viewportMode === "desktop" ? "desktop" : "mobile";
  const query = new URLSearchParams({ windowId: "@777", viewportMode, fontSize: "12" });
  if (Number.isFinite(options.cols) && options.cols >= 20) query.set("cols", String(options.cols));
  const pageUrl = `file://${pagePath}?${query.toString()}`;
  const chrome = spawnSync(chromePath, [
    "--headless=new",
    "--no-sandbox",
    "--disable-gpu",
    "--disable-background-networking",
    "--disable-default-apps",
    "--disable-dev-shm-usage",
    "--no-first-run",
    "--allow-file-access-from-files",
    "--run-all-compositor-stages-before-draw",
    `--virtual-time-budget=${virtualTimeBudget}`,
    `--window-size=${options.windowSize || "390,844"}`,
    `--user-data-dir=${profilePath}`,
    "--dump-dom",
    pageUrl,
  ], {
    encoding: "utf8",
    maxBuffer: 128 * 1024 * 1024,
  });
  fs.rmSync(temporaryRoot, { recursive: true, force: true });
  if (chrome.status !== 0) {
    fail(`${label} generated renderer Chrome execution failed: ${(chrome.stderr || chrome.stdout).trim()}`);
  }
  const match = chrome.stdout.match(/<pre id="contractResult">([A-Za-z0-9+/=]+)<\/pre>/);
  if (!match) fail(`${label} generated renderer probe did not return a contract result`);
  let result;
  try {
    result = JSON.parse(Buffer.from(match[1], "base64").toString("utf8"));
  } catch (error) {
    fail(`${label} generated renderer probe result was invalid: ${error.message}`);
  }
  if (result.failures.length) {
    fail(`${result.failures[0]} facts=${JSON.stringify(result.facts)}`);
  }
  return result;
}

function javaConcatenatedStringLiterals(body) {
  let combined = "";
  let lineComment = false;
  let blockComment = false;
  for (let index = 0; index < body.length; index += 1) {
    const char = body[index];
    const next = body[index + 1];
    if (lineComment) {
      if (char === "\n") lineComment = false;
      continue;
    }
    if (blockComment) {
      if (char === "*" && next === "/") { blockComment = false; index += 1; }
      continue;
    }
    if (char === "/" && next === "/") { lineComment = true; index += 1; continue; }
    if (char === "/" && next === "*") { blockComment = true; index += 1; continue; }
    if (char === "'") {
      for (index += 1; index < body.length; index += 1) {
        if (body[index] === "\\") index += 1;
        else if (body[index] === "'") break;
      }
      continue;
    }
    if (char !== '"') continue;
    let literal = '"';
    for (index += 1; index < body.length; index += 1) {
      literal += body[index];
      if (body[index] === "\\") {
        index += 1;
        if (index < body.length) literal += body[index];
      } else if (body[index] === '"') {
        break;
      }
    }
    try {
      combined += JSON.parse(literal);
    } catch (error) {
      fail(`capture telemetry Java string decode failed: ${error.message}`);
    }
  }
  return combined;
}

function generatedCaptureTelemetryHookScript() {
  const body = javaMethodBody(main, "private String captureRendererTelemetryHookScript");
  if (!body) fail("capture renderer telemetry hook method missing");
  const cacheEntries = javaNumericConstant("ACTIVE_SWITCH_SELECTED_BODY_CACHE_MAX_ENTRIES");
  const cacheAgeMs = javaNumericConstant("ACTIVE_SWITCH_SELECTED_BODY_CACHE_MAX_AGE_MS");
  // WHY: this harness reconstructs Java's concatenated string without running
  // Android. C04 added numeric expressions inside that string; substitute their
  // exact source constants so the probe evaluates the production JavaScript,
  // not the invalid `length>)`/`ageMs>)` text left by dropping expressions.
  const generated = javaConcatenatedStringLiterals(body)
    .replace("var installReason=;", "var installReason='scroll-bootstrap-guard';")
    .replace(
      "selectedBodyCacheOrder.length>){",
      `selectedBodyCacheOrder.length>${cacheEntries}){`,
    )
    .replace("ageMs>){", `ageMs>${cacheAgeMs}){`);
  if (!generated.includes("window.__weztermCaptureTelemetryInstalled")
      || !generated.includes("window.fetch=function(resource,init)")) {
    fail("capture renderer telemetry hook could not be reconstructed from Java");
  }
  return generated;
}

function javaNumericConstant(name) {
  const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const match = main.match(new RegExp(
    `\\b${escaped}\\s*=\\s*([0-9][0-9_]*)L?\\s*;`,
  ));
  if (!match) fail(`numeric Java constant missing: ${name}`);
  const value = Number(match[1].replaceAll("_", ""));
  if (!Number.isSafeInteger(value) || value < 0) {
    fail(`numeric Java constant invalid: ${name}`);
  }
  return value;
}

function acceptedFrameColumnStorageContract() {
  const body = javaMethodBody(main, "private void rememberAcceptedCaptureRendererFrame");
  const targetBody = javaMethodBody(main, "private void setCaptureRendererWindowTarget", true);
  if (!body) return { mode: "unknown", body: "" };
  const directOwnerReset = /if\s*\(!windowId\.equals\(lastAcceptedCaptureRendererFrameWindowId\)\)\s*\{[\s\S]*?lastAcceptedCaptureRendererCols\s*=\s*-1\s*;[\s\S]*?\}/.test(body);
  // WHY(v281 accepted-frame owner): production names the target-change fact once
  // and reuses it for width/row reset plus one new-target prewarm. The prior guard
  // recognized only the older repeated inline comparison, so it called the exact
  // same fail-closed semantics "unknown" and vetoed a source already installed in
  // v291. Require both the exact fact definition and its guarded width reset.
  const namedOwnerChange = /boolean\s+acceptedFrameTargetChanged\s*=\s*!windowId\.equals\(lastAcceptedCaptureRendererFrameWindowId\)\s*;/.test(body);
  const namedOwnerReset = namedOwnerChange
    && /if\s*\(acceptedFrameTargetChanged\)\s*\{[\s\S]*?lastAcceptedCaptureRendererCols\s*=\s*-1\s*;[\s\S]*?\}/.test(body);
  const ownerReset = directOwnerReset || namedOwnerReset;
  const targetReset = /if\s*\(!stableTarget\.equals\(lastAcceptedCaptureRendererFrameWindowId\)\)\s*\{[\s\S]*?lastAcceptedCaptureRendererCols\s*=\s*-1\s*;[\s\S]*?\}/.test(targetBody);
  const validCandidate = /int\s+requestedCols\s*=\s*payload\.optInt\("requestedCols",\s*-1\)\s*;/.test(body);
  const validGate = /if\s*\(requestedCols\s*>=\s*20\)\s*\{[\s\S]*?lastAcceptedCaptureRendererCols\s*=\s*requestedCols\s*;[\s\S]*?\}/.test(body);
  const unconditionalClobber = /lastAcceptedCaptureRendererCols\s*=\s*payload\.optInt\("requestedCols",\s*-1\)\s*;/.test(body);
  if (ownerReset && targetReset && validCandidate && validGate && !unconditionalClobber) return { mode: "preserve-valid", body };
  if (unconditionalClobber) return { mode: "clobber-missing", body };
  return { mode: "unknown", body };
}

function runAcceptedFrameColumnJavaContract() {
  const storage = acceptedFrameColumnStorageContract();
  const rememberBody = javaMethodBody(main, "private void rememberAcceptedCaptureRendererFrame");
  const admissionBody = javaMethodBody(main, "private int localHistoryRendererColsForRequest");
  if (!rememberBody || !admissionBody) fail("accepted-frame Java contract method missing");
  const className = "AcceptedFrameColumnContractHarness";
  const javaSource = `
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ${className} {
  private static final String VIEWPORT_MODE_MOBILE = "mobile";
  private static final int APK_CAPTURE_RENDERER_COLS = 132;

  private final List<String> lastAcceptedCaptureRendererRows = new ArrayList<>();
  private final List<LocalHistoryPaintRow> lastAcceptedCaptureRendererPaintRows = new ArrayList<>();
  private final List<Integer> prewarmCols = new ArrayList<>();
  private String lastAcceptedCaptureRendererFrameWindowId = "";
  private String lastAcceptedCaptureRendererFrameHash = "";
  private boolean lastAcceptedCaptureRendererHasMoreBefore = false;
  private boolean lastAcceptedCaptureRendererCopyModeViewport = false;
  private int lastAcceptedCaptureRendererScrollPosition = -1;
  private int lastAcceptedCaptureRendererHistorySize = -1;
  private int lastAcceptedCaptureRendererPaneInMode = -1;
  private int lastAcceptedCaptureRendererCols = -1;
  private String confirmedCaptureRendererFrameWindowTargetKey = "";
  // WHY: the extracted production method suppresses target publication while
  // an Active switch is pending; mirror that owner field so this focused width
  // harness compiles the real method body instead of bypassing the build gate.
  private String pendingActiveSwitchTitleWindowId = "";

  private void rememberAcceptedCaptureRendererFrame(JSONObject payload) {
${rememberBody}
  }

  private int localHistoryRendererColsForRequest(LocalHistoryRequest request) {
${admissionBody}
  }

  private String captureRendererFrameWindowId(JSONObject payload, String fallback) {
    String value = payload == null ? "" : payload.optString("frameWindowId", payload.optString("windowId", fallback));
    return hasStableWindowId(value) ? value.trim() : "";
  }

  private String captureRendererVisibleWindowId(JSONObject payload, String fallback) {
    String value = payload == null ? "" : payload.optString("displayWindowId", fallback);
    return hasStableWindowId(value) ? value.trim() : fallback;
  }

  private boolean hasStableWindowId(String value) {
    return value != null && value.trim().matches("@[0-9]+");
  }

  private List<String> rowsFromPayload(JSONObject payload) throws Exception {
    if (payload.optBoolean("forceRowsException", false)) throw new Exception("forced-row-parse-failure");
    return Collections.emptyList();
  }
  private List<LocalHistoryPaintRow> localHistoryPaintRowsFromPayload(JSONObject payload, boolean unused) { return Collections.emptyList(); }
  private List<LocalHistoryPaintRow> plainLocalHistoryPaintRows(List<String> rows) { return Collections.emptyList(); }
  private String currentViewportMode() { return VIEWPORT_MODE_MOBILE; }
  private void prewarmLocalHistoryForWindow(String windowId, String reason) { prewarmCols.add(lastAcceptedCaptureRendererCols); }

  private static JSONObject accepted(String windowId, Integer requestedCols) {
    JSONObject payload = new JSONObject()
        .put("acceptedFrame", true)
        .put("windowId", windowId)
        .put("frameWindowId", windowId)
        .put("displayWindowId", windowId)
        .put("frameHash", "frame-" + windowId.substring(1));
    if (requestedCols != null) payload.put("requestedCols", requestedCols);
    return payload;
  }

  private static ${className} actualAcceptedSequence() {
    ${className} value = new ${className}();
    value.rememberAcceptedCaptureRendererFrame(accepted("@777", 39));
    value.rememberAcceptedCaptureRendererFrame(accepted("@777", 39));
    value.rememberAcceptedCaptureRendererFrame(accepted("@777", null));
    value.rememberAcceptedCaptureRendererFrame(accepted("@777", null));
    return value;
  }

  public static void main(String[] args) {
    ${className} actual = actualAcceptedSequence();
    ${className} sameOwnerOmission = new ${className}();
    sameOwnerOmission.rememberAcceptedCaptureRendererFrame(accepted("@777", 39));
    sameOwnerOmission.rememberAcceptedCaptureRendererFrame(accepted("@777", null));
    ${className} noPriorWidth = new ${className}();
    noPriorWidth.rememberAcceptedCaptureRendererFrame(accepted("@777", null));
    ${className} targetReset = new ${className}();
    targetReset.rememberAcceptedCaptureRendererFrame(accepted("@777", 39));
    targetReset.rememberAcceptedCaptureRendererFrame(accepted("@778", null));
    ${className} staleOldWindow = new ${className}();
    staleOldWindow.rememberAcceptedCaptureRendererFrame(accepted("@777", 39));
    staleOldWindow.rememberAcceptedCaptureRendererFrame(accepted("@778", null));
    staleOldWindow.rememberAcceptedCaptureRendererFrame(accepted("@777", null));
    ${className} subTwenty = new ${className}();
    subTwenty.rememberAcceptedCaptureRendererFrame(accepted("@777", 19));
    ${className} invalidSameOwner = new ${className}();
    invalidSameOwner.rememberAcceptedCaptureRendererFrame(accepted("@777", 39));
    invalidSameOwner.rememberAcceptedCaptureRendererFrame(accepted("@777", 19));
    ${className} exceptionOwner = new ${className}();
    exceptionOwner.rememberAcceptedCaptureRendererFrame(accepted("@777", 39));
    exceptionOwner.rememberAcceptedCaptureRendererFrame(
        accepted("@778", null).put("forceRowsException", true));
    int prewarmMin = actual.prewarmCols.stream().mapToInt(Integer::intValue).min().orElse(-1);
    int admittedCols = actual.localHistoryRendererColsForRequest(new LocalHistoryRequest("@777"));
    int exceptionAdmittedCols = exceptionOwner.localHistoryRendererColsForRequest(new LocalHistoryRequest("@778"));
    int exceptionPrewarmLastCols = exceptionOwner.prewarmCols.isEmpty()
        ? -1
        : exceptionOwner.prewarmCols.get(exceptionOwner.prewarmCols.size() - 1);
    System.out.println(String.format(Locale.ROOT,
        "{\\\"actualSequenceCols\\\":%d,\\\"admittedCols\\\":%d,\\\"prewarmCount\\\":%d,\\\"prewarmMinCols\\\":%d,\\\"sameOwnerOmissionCols\\\":%d,\\\"noPriorWidthCols\\\":%d,\\\"targetResetCols\\\":%d,\\\"staleOldWindowCols\\\":%d,\\\"subTwentyCols\\\":%d,\\\"invalidSameOwnerCols\\\":%d,\\\"exceptionOwnerCommitted\\\":%d,\\\"exceptionOwnerCols\\\":%d,\\\"exceptionAdmittedCols\\\":%d,\\\"exceptionPrewarmLastCols\\\":%d}",
        actual.lastAcceptedCaptureRendererCols,
        admittedCols,
        actual.prewarmCols.size(),
        prewarmMin,
        sameOwnerOmission.lastAcceptedCaptureRendererCols,
        noPriorWidth.lastAcceptedCaptureRendererCols,
        targetReset.lastAcceptedCaptureRendererCols,
        staleOldWindow.lastAcceptedCaptureRendererCols,
        subTwenty.lastAcceptedCaptureRendererCols,
        invalidSameOwner.lastAcceptedCaptureRendererCols,
        "@778".equals(exceptionOwner.lastAcceptedCaptureRendererFrameWindowId) ? 1 : 0,
        exceptionOwner.lastAcceptedCaptureRendererCols,
        exceptionAdmittedCols,
        exceptionPrewarmLastCols));
  }

  private static final class LocalHistoryRequest {
    private final String windowId;
    private LocalHistoryRequest(String windowId) { this.windowId = windowId; }
  }

  private static final class LocalHistoryPaintRow {}

  private static final class JSONObject {
    private final Map<String, Object> values = new HashMap<>();
    private JSONObject put(String key, Object value) { values.put(key, value); return this; }
    private boolean optBoolean(String key, boolean fallback) {
      Object value = values.get(key);
      return value instanceof Boolean ? (Boolean) value : fallback;
    }
    private String optString(String key, String fallback) {
      Object value = values.get(key);
      return value == null ? fallback : String.valueOf(value);
    }
    private int optInt(String key, int fallback) {
      Object value = values.get(key);
      if (value instanceof Number) return ((Number) value).intValue();
      if (value instanceof String) {
        try { return Integer.parseInt((String) value); } catch (NumberFormatException ignored) {}
      }
      return fallback;
    }
  }
}
`;
  const temporaryRoot = fs.mkdtempSync(path.join(os.tmpdir(), "accepted-frame-cols-java-"));
  const sourcePath = path.join(temporaryRoot, `${className}.java`);
  fs.writeFileSync(sourcePath, javaSource, "utf8");
  const compiled = spawnSync("javac", ["-d", temporaryRoot, sourcePath], { encoding: "utf8" });
  if (compiled.status !== 0) {
    fs.rmSync(temporaryRoot, { recursive: true, force: true });
    fail(`accepted-frame Java contract compilation failed: ${(compiled.stderr || compiled.stdout).trim()}`);
  }
  const executed = spawnSync("java", ["-cp", temporaryRoot, className], { encoding: "utf8" });
  fs.rmSync(temporaryRoot, { recursive: true, force: true });
  if (executed.status !== 0) fail(`accepted-frame Java contract execution failed: ${(executed.stderr || executed.stdout).trim()}`);
  let facts;
  try {
    facts = JSON.parse(executed.stdout.trim());
  } catch (error) {
    fail(`accepted-frame Java contract returned invalid JSON: ${error.message}`);
  }
  return {
    ...facts,
    storageMode: storage.mode,
    exactMethodBodySha256: createHash("sha256").update(rememberBody).digest("hex"),
    exactAdmissionBodySha256: createHash("sha256").update(admissionBody).digest("hex"),
  };
}

function runReadyRefillJavaContract() {
  const prefetchBody = javaMethodBody(main, "private void prefetchAdjacentLocalHistoryChunkForTouch");
  const keyDefinitionCount = (main.match(/private\s+String\s+localHistoryTouchPrefetchKey\s*\(/g) || []).length;
  const twoArgumentKeyBody = javaMethodBody(main, "private String localHistoryTouchPrefetchKey");
  const threeArgumentKeyBody = keyDefinitionCount > 1
    ? javaMethodBody(main, "private String localHistoryTouchPrefetchKey", true)
    : "return localHistoryTouchPrefetchKey(request, start);";
  if (!prefetchBody || !twoArgumentKeyBody) fail("READY refill Java contract method missing");
  const className = "ReadyRefillContractHarness";
  const javaSource = `
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class ${className} {
  private static final int MODE_SUCCESS = 1;
  private static final int MODE_NULL_REMEMBER = 2;
  private static final int MODE_NON_OK = 3;
  private static final int MODE_EXCEPTION = 4;
  private static final int MODE_HOLD = 5;
  private static final int LOCAL_HISTORY_CHUNK_LINES = 500;
  private static final String CONTROL_LOG_TAG = "guard";

  private final LinkedHashMap<String, Long> localHistoryTouchPrefetchInFlight = new LinkedHashMap<>();
  private String activeTouchScrollActionId = "guard-action";
  private int mode = MODE_SUCCESS;
  private int getJsonCalls = 0;
  private int stageCalls = 0;
  private int failureSignals = 0;
  private int mergeCalls = 0;

  private void prefetchAdjacentLocalHistoryChunkForTouch(
      LocalHistoryRequest request,
      JSONObject payload,
      long requestSerial
  ) {
${prefetchBody}
  }

  private String localHistoryTouchPrefetchKey(LocalHistoryRequest request, String start) {
${twoArgumentKeyBody}
  }

  private String localHistoryTouchPrefetchKey(
      LocalHistoryRequest request,
      String generationKey,
      String start
  ) {
${threeArgumentKeyBody}
  }

  private boolean hasStableWindowId(String value) { return value != null && value.matches("@[0-9]+"); }
  private int localHistoryRendererColsForRequest(LocalHistoryRequest request) { return 39; }
  private void notifyReadyHistoryRefillFailed(String reason) { failureSignals += 1; }
  private String localHistoryPreviousStartFromPayload(JSONObject payload) { return normalizeLocalHistoryStart(payload.optString("prevStart", "")); }
  private String normalizeLocalHistoryStart(String value) { return value == null ? "" : value.trim(); }
  private String firstLocalHistoryPayloadString(JSONObject payload, String... keys) {
    if (payload == null || keys == null) return "";
    for (String key : keys) {
      String value = payload.optString(key, "").trim();
      if (!value.isEmpty()) return value;
    }
    return "";
  }
  private LocalHistoryChunk findCachedLocalHistoryChunk(LocalHistoryRequest request, String start) { return null; }
  private void stageReadyLocalHistoryBatchForRenderer(LocalHistoryRequest request, LocalHistoryChunk chunk, String source) { stageCalls += 1; }
  private void mergeLocalHistoryTouchViewportBoundary(LocalHistoryRequest request, LocalHistoryChunk chunk, boolean prepend, long serial, String source) { mergeCalls += 1; }
  private void evictLocalHistoryTouchPrefetchIfNeeded() {}
  private String urlEncode(String value) { return value; }
  private String safeLogToken(String value) { return value == null ? "" : value; }
  private String safeWindowIdForControlLog(String value) { return value == null ? "" : value; }
  private void annotateLocalHistoryPrepareElapsed(JSONObject payload, Long startedAtMs) {}
  private LocalHistoryChunk rememberLocalHistoryChunk(LocalHistoryRequest request, String start, JSONObject payload) {
    if (mode == MODE_NULL_REMEMBER) return null;
    String generationKey = payload.optString("generationKey", "generation-A");
    return new LocalHistoryChunk(new LocalHistoryChunkIdentity(generationKey, start), payload);
  }
  private void getJson(String path, JsonSuccess success, JsonFailure failure) {
    getJsonCalls += 1;
    if (mode == MODE_HOLD) return;
    if (mode == MODE_EXCEPTION) {
      failure.accept(new Exception("forced-fetch-failure"));
      return;
    }
    JSONObject payload = new JSONObject()
        .put("ok", mode != MODE_NON_OK)
        .put("generationKey", "generation-A")
        .put("hasMoreBefore", true)
        .put("prevStart", "11540");
    success.accept(payload);
  }

  private static ${className} scenario(int mode) {
    ${className} value = new ${className}();
    value.mode = mode;
    value.prefetchAdjacentLocalHistoryChunkForTouch(
        new LocalHistoryRequest("@777", "frame-A"),
        cursor("generation-A"),
        41L);
    return value;
  }

  private static JSONObject cursor(String generationKey) {
    return new JSONObject().put("prevStart", "12040").put("generationKey", generationKey);
  }

  public static void main(String[] args) {
    ${className} success = scenario(MODE_SUCCESS);
    ${className} nullRemember = scenario(MODE_NULL_REMEMBER);
    ${className} nonOk = scenario(MODE_NON_OK);
    ${className} exception = scenario(MODE_EXCEPTION);
    ${className} held = new ${className}();
    held.mode = MODE_HOLD;
    held.prefetchAdjacentLocalHistoryChunkForTouch(
        new LocalHistoryRequest("@777", "frame-A"), cursor("generation-A"), 51L);
    held.prefetchAdjacentLocalHistoryChunkForTouch(
        new LocalHistoryRequest("@777", "frame-B"), cursor("generation-A"), 52L);
    ${className} keyHarness = new ${className}();
    String stableA = keyHarness.localHistoryTouchPrefetchKey(
        new LocalHistoryRequest("@777", "frame-A"), "generation-A", "12040");
    String stableB = keyHarness.localHistoryTouchPrefetchKey(
        new LocalHistoryRequest("@777", "frame-B"), "generation-A", "12040");
    String generationB = keyHarness.localHistoryTouchPrefetchKey(
        new LocalHistoryRequest("@777", "frame-A"), "generation-B", "12040");
    System.out.println(String.format(Locale.ROOT,
        "{\\\"successStageCalls\\\":%d,\\\"successFailureSignals\\\":%d,\\\"successInFlight\\\":%d,\\\"nullStageCalls\\\":%d,\\\"nullFailureSignals\\\":%d,\\\"nullInFlight\\\":%d,\\\"nonOkFailureSignals\\\":%d,\\\"exceptionFailureSignals\\\":%d,\\\"heldGetJsonCalls\\\":%d,\\\"heldInFlight\\\":%d,\\\"sameTupleStableAcrossFrameHash\\\":%d,\\\"differentGenerationDistinct\\\":%d}",
        success.stageCalls,
        success.failureSignals,
        success.localHistoryTouchPrefetchInFlight.size(),
        nullRemember.stageCalls,
        nullRemember.failureSignals,
        nullRemember.localHistoryTouchPrefetchInFlight.size(),
        nonOk.failureSignals,
        exception.failureSignals,
        held.getJsonCalls,
        held.localHistoryTouchPrefetchInFlight.size(),
        stableA.equals(stableB) ? 1 : 0,
        stableA.equals(generationB) ? 0 : 1));
  }

  private interface JsonSuccess { void accept(JSONObject payload); }
  private interface JsonFailure { void accept(Exception error); }

  private static final class LocalHistoryRequest {
    private final String windowId;
    private final String visibleFrameHash;
    private LocalHistoryRequest(String windowId, String visibleFrameHash) {
      this.windowId = windowId;
      this.visibleFrameHash = visibleFrameHash;
    }
  }

  private static final class LocalHistoryChunkIdentity {
    private final String generationKey;
    private final String start;
    private LocalHistoryChunkIdentity(String generationKey, String start) {
      this.generationKey = generationKey;
      this.start = start;
    }
  }

  private static final class LocalHistoryChunk {
    private final LocalHistoryChunkIdentity identity;
    private final JSONObject payload;
    private LocalHistoryChunk(LocalHistoryChunkIdentity identity, JSONObject payload) {
      this.identity = identity;
      this.payload = payload;
    }
  }

  private static final class JSONObject {
    private final Map<String, Object> values = new HashMap<>();
    private JSONObject put(String key, Object value) { values.put(key, value); return this; }
    private boolean optBoolean(String key, boolean fallback) {
      Object value = values.get(key);
      return value instanceof Boolean ? (Boolean) value : fallback;
    }
    private String optString(String key, String fallback) {
      Object value = values.get(key);
      return value == null ? fallback : String.valueOf(value);
    }
  }

  private static final class SystemClock {
    private static long value = 1000L;
    private static long elapsedRealtime() { return ++value; }
  }

  private static final class Log {
    private static int i(String tag, String value) { return 0; }
  }
}
`;
  const temporaryRoot = fs.mkdtempSync(path.join(os.tmpdir(), "ready-refill-java-"));
  const sourcePath = path.join(temporaryRoot, `${className}.java`);
  fs.writeFileSync(sourcePath, javaSource, "utf8");
  const compiled = spawnSync("javac", ["-d", temporaryRoot, sourcePath], { encoding: "utf8" });
  if (compiled.status !== 0) {
    fs.rmSync(temporaryRoot, { recursive: true, force: true });
    fail(`READY refill Java contract compilation failed: ${(compiled.stderr || compiled.stdout).trim()}`);
  }
  const executed = spawnSync("java", ["-cp", temporaryRoot, className], { encoding: "utf8" });
  fs.rmSync(temporaryRoot, { recursive: true, force: true });
  if (executed.status !== 0) fail(`READY refill Java contract execution failed: ${(executed.stderr || executed.stdout).trim()}`);
  let facts;
  try {
    facts = JSON.parse(executed.stdout.trim());
  } catch (error) {
    fail(`READY refill Java contract returned invalid JSON: ${error.message}`);
  }
  return {
    ...facts,
    exactPrefetchBodySha256: createHash("sha256").update(prefetchBody).digest("hex"),
    exactTwoArgumentKeyBodySha256: createHash("sha256").update(twoArgumentKeyBody).digest("hex"),
    exactThreeArgumentKeyBodySha256: createHash("sha256").update(threeArgumentKeyBody).digest("hex"),
    keyDefinitionCount,
  };
}

function generatedPendingGapCallbackProbe(mutantMode = "") {
  const supportedMutants = new Set(["", "drop-pending-continuation"]);
  if (!supportedMutants.has(mutantMode)) {
    fail(`UNKNOWN_PENDING_GAP_CALLBACK_MUTANT:${mutantMode}`);
  }
  const measurementFixture = canonicalGeneratedFixture(132, false);
  const measurement = runGeneratedChrome(
    measurementFixture.html,
    `<script>(() => {
      const result = { failures: [], facts: {} };
      try {
        const api = window.__mantisCaptureRenderer;
        if (!api) throw new Error("generated renderer API missing");
        result.facts.measured = api.measure();
      } catch (error) {
        result.failures.push("PENDING_GAP_MEASURE_ERROR:" + String(error && error.message || error));
      }
      document.getElementById("contractResult").textContent =
        btoa(unescape(encodeURIComponent(JSON.stringify(result))));
    })();</script>`,
    "pending-gap-callback-measure",
    500,
  );
  const measuredCols = Number(measurement.facts.measured.cols);
  if (!Number.isFinite(measuredCols) || measuredCols < 20) {
    fail(`PENDING_GAP_CALLBACK_COLS_UNMEASURED:${measuredCols}`);
  }

  const fixture = canonicalGeneratedFixture(measuredCols, true);
  let rendererHtml = fixture.html;
  if (mutantMode === "drop-pending-continuation") {
    const pendingOwner = "const pending=readyHistoryPendingBatches.includes(stagedBatch);";
    const occurrences = rendererHtml.split(pendingOwner).length - 1;
    if (occurrences !== 1) {
      fail(`PENDING_GAP_PRIVATE_MUTANT_INJECTION_FAILED:occurrences=${occurrences}`);
    }
    // Private old-red only: preserve the reported PENDING_GAP result while
    // dropping its retained interval before the nearer completion arrives.
    rendererHtml = rendererHtml.replace(
      pendingOwner,
      pendingOwner
        + "if(pending){const pendingDropIndex=readyHistoryPendingBatches.indexOf(stagedBatch);"
        + "if(pendingDropIndex>=0)readyHistoryPendingBatches.splice(pendingDropIndex,1);}",
    );
  }

  const encodedFixture = Buffer.from(
    JSON.stringify({ batches: fixture.batches }),
    "utf8",
  ).toString("base64");
  const probe = `
<script id="pendingGapCallbackFixture" type="application/octet-stream">${encodedFixture}</script>
<script>
(() => {
  const result = { failures: [], facts: {} };
  const finish = () => {
    document.getElementById("contractResult").textContent =
      btoa(unescape(encodeURIComponent(JSON.stringify(result))));
  };
  try {
    const api = window.__mantisCaptureRenderer;
    const active = document.querySelector('.screenBuffer[data-active="1"]');
    const fixture = JSON.parse(atob(
      document.getElementById("pendingGapCallbackFixture").textContent.trim()
    ));
    if (!api || !active || fixture.batches.length < 4) {
      throw new Error("generated renderer API/root/full chain missing");
    }

    const live = document.createDocumentFragment();
    for (let index = 0; index < 240; index += 1) {
      const row = document.createElement("span");
      row.className = "captureRenderRow";
      row.dataset.renderRowKey =
        "@777:%777:4242:200000:" + (13000 + index) + ":0";
      row.textContent = "LIVE-" + String(index).padStart(4, "0") + " callback row";
      live.appendChild(row);
    }
    active.appendChild(live);

    const base = fixture.batches[0];
    const nearer = fixture.batches[1];
    const deeper = fixture.batches[2];
    const baseStage = api.stageReadyHistoryBatch(base);
    const baseCommit = api.prependReadyHistoryAtBoundary("up", 0, 0, true);
    if (baseStage.status !== "READY" || baseCommit.status !== "COMMITTED") {
      result.failures.push("PENDING_GAP_CALLBACK_BASE_NOT_READY");
    }

    // Prime only the renderer's predictive horizon. Negative deltas cannot
    // consume an older READY seam, and clearing the residual leaves the measured
    // velocity intact so both callback results truthfully request more data.
    api.nudgeTouchScroll(-1);
    api.nudgeTouchScroll(-8000);
    api.clearTouchScrollNudge();

    const deeperStage = api.stageReadyHistoryBatch(deeper);
    const afterDeeper = api.state();
    const nearerStage = api.stageReadyHistoryBatch(nearer);
    const afterNearer = api.state();
    if (deeperStage.status !== "PENDING_GAP"
        || nearerStage.status !== "READY"
        || afterDeeper.readyHistoryPendingBatches !== 1
        || afterNearer.readyHistoryPendingBatches !== 0
        || afterNearer.readyHistoryBatches !== 2) {
      result.failures.push("PENDING_GAP_CALLBACK_ORPHANS_REFILL");
    }

    const remainingStatuses = [];
    for (const batch of fixture.batches.slice(3)) {
      const staged = api.stageReadyHistoryBatch(batch);
      remainingStatuses.push(staged.status);
      if (staged.status !== "READY") {
        result.failures.push("PENDING_GAP_CALLBACK_ORPHANS_REFILL");
        break;
      }
    }

    let committedIntervals = baseCommit.status === "COMMITTED" ? 1 : 0;
    let lastTruth = api.state();
    for (let frame = 0; frame < 2400 && !result.failures.length; frame += 1) {
      const truth = api.nudgeTouchScroll(240);
      lastTruth = truth;
      if (truth.seamStatus === "COMMITTED") committedIntervals += 1;
      if (truth.status === "TRUE_HISTORY_TOP" && truth.trueTopReached) break;
      if (["READY_UNDERRUN_BEFORE_TRUE_TOP", "READY_GAP", "CLAMPED"].includes(truth.status)
          || ["READY_UNDERRUN_BEFORE_TRUE_TOP", "READY_GAP"].includes(truth.seamStatus)) {
        result.failures.push("PENDING_GAP_CALLBACK_ORPHANS_REFILL");
        break;
      }
    }
    const finalState = api.state();
    if (!finalState.trueTopReached
        || Number(finalState.committedHistoryFrontier) !== 0
        || committedIntervals !== fixture.batches.length) {
      result.failures.push("PENDING_GAP_CALLBACK_ORPHANS_REFILL");
    }

    result.facts = {
      measuredCols: ${measuredCols},
      producerIntervals: fixture.batches.length,
      baseStatus: baseStage.status,
      deeperStatus: deeperStage.status,
      deeperNeedsReady: deeperStage.needsReady === true,
      deeperTrueTopPrepared: deeperStage.trueTopPrepared === true,
      deeperHasMoreBefore: deeper.hasMoreBefore === true,
      deeperPrevStart: String(deeper.prevStart == null ? "" : deeper.prevStart),
      nearerStatus: nearerStage.status,
      nearerNeedsReady: nearerStage.needsReady === true,
      nearerTrueTopPrepared: nearerStage.trueTopPrepared === true,
      nearerHasMoreBefore: nearer.hasMoreBefore === true,
      nearerPrevStart: String(nearer.prevStart == null ? "" : nearer.prevStart),
      pendingAfterDeeper: afterDeeper.readyHistoryPendingBatches,
      pendingAfterNearer: afterNearer.readyHistoryPendingBatches,
      readyAfterNearer: afterNearer.readyHistoryBatches,
      remainingStatuses: Array.from(new Set(remainingStatuses)),
      committedIntervals,
      lastStatus: lastTruth.status,
      trueTopReached: finalState.trueTopReached,
      committedHistoryFrontier: finalState.committedHistoryFrontier,
    };
  } catch (error) {
    result.failures.push(
      "PENDING_GAP_CALLBACK_ERROR:" + String(error && error.message || error)
    );
  }
  finish();
})();
</script>`;
  return runGeneratedChrome(
    rendererHtml,
    probe,
    mutantMode ? `pending-gap-callback-${mutantMode}` : "pending-gap-callback",
    3000,
  );
}

function runPendingGapCallbackJavaContract(rendererFacts) {
  const continueBody = javaMethodBody(
    main,
    "private void continueReadyHistoryRefillAfterStage",
  );
  const requestsNextBody = javaMethodBody(
    main,
    "private boolean readyHistoryStageRequestsNext",
  );
  if (!continueBody || !requestsNextBody) {
    fail("PENDING_GAP_CALLBACK_JAVA_METHOD_MISSING");
  }
  const className = "PendingGapCallbackContractHarness";
  const javaString = (value) => JSON.stringify(String(value == null ? "" : value));
  const javaBoolean = (value) => value === true ? "true" : "false";
  const javaSource = `
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

public final class ${className} {
  private long activeHistoryScrollRequestSerial = 73L;
  private final List<String> nextCursors = new ArrayList<>();
  // Mirror the deep-staging state now referenced by the extracted production
  // method. This contract exercises an active gesture so the continuation path
  // remains compiled and observable instead of silently omitting new guards.
  private static final int LOCAL_HISTORY_DEEP_STAGING_LOOKAHEAD_CHUNKS = 4;
  private int deepStagingLookAheadChunkCount = 0;

  private boolean captureRendererGestureScrollActive() {
    return true;
  }

  private void continueReadyHistoryRefillAfterStage(
      LocalHistoryRequest request,
      LocalHistoryChunk chunk,
      String source,
      JSONObject result
  ) {
${continueBody}
  }

  private boolean readyHistoryStageRequestsNext(JSONObject result, LocalHistoryChunk chunk) {
${requestsNextBody}
  }

  private void prefetchAdjacentLocalHistoryChunkForTouch(
      LocalHistoryRequest request,
      JSONObject payload,
      long requestSerial
  ) {
    nextCursors.add(payload.optString("prevStart", ""));
  }

  private static JSONObject result(String status, boolean needsReady, boolean trueTopPrepared) {
    return new JSONObject()
        .put("status", status)
        .put("needsReady", needsReady)
        .put("trueTopPrepared", trueTopPrepared);
  }

  private static LocalHistoryChunk chunk(boolean hasMoreBefore, String prevStart) {
    return new LocalHistoryChunk(
        new JSONObject()
            .put("hasMoreBefore", hasMoreBefore)
            .put("prevStart", prevStart));
  }

  public static void main(String[] args) {
    ${className} harness = new ${className}();
    JSONObject pendingResult = result(
        ${javaString(rendererFacts.deeperStatus)},
        ${javaBoolean(rendererFacts.deeperNeedsReady)},
        ${javaBoolean(rendererFacts.deeperTrueTopPrepared)});
    JSONObject readyResult = result(
        ${javaString(rendererFacts.nearerStatus)},
        ${javaBoolean(rendererFacts.nearerNeedsReady)},
        ${javaBoolean(rendererFacts.nearerTrueTopPrepared)});
    LocalHistoryChunk deeper = chunk(
        ${javaBoolean(rendererFacts.deeperHasMoreBefore)},
        ${javaString(rendererFacts.deeperPrevStart)});
    LocalHistoryChunk nearer = chunk(
        ${javaBoolean(rendererFacts.nearerHasMoreBefore)},
        ${javaString(rendererFacts.nearerPrevStart)});
    boolean pendingRequestsNext = harness.readyHistoryStageRequestsNext(
        pendingResult, deeper);
    boolean readyRequestsNext = harness.readyHistoryStageRequestsNext(
        readyResult, nearer);
    harness.continueReadyHistoryRefillAfterStage(
        new LocalHistoryRequest(), deeper, "deeper-first", pendingResult);
    harness.continueReadyHistoryRefillAfterStage(
        new LocalHistoryRequest(), nearer, "nearer-second", readyResult);
    String nextCursor = harness.nextCursors.isEmpty() ? "" : harness.nextCursors.get(0);
    System.out.println(String.format(Locale.ROOT,
        "{\\\"pendingRequestsNext\\\":%s,\\\"readyRequestsNext\\\":%s,"
            + "\\\"nextCursorCalls\\\":%d,\\\"nextCursor\\\":\\\"%s\\\"}",
        pendingRequestsNext,
        readyRequestsNext,
        harness.nextCursors.size(),
        nextCursor));
  }

  private static final class LocalHistoryRequest {}

  private static final class LocalHistoryChunk {
    private final JSONObject payload;
    private LocalHistoryChunk(JSONObject payload) { this.payload = payload; }
  }

  private static final class JSONObject {
    private final Map<String, Object> values = new HashMap<>();
    private JSONObject put(String key, Object value) {
      values.put(key, value);
      return this;
    }
    private boolean optBoolean(String key, boolean fallback) {
      Object value = values.get(key);
      return value instanceof Boolean ? (Boolean) value : fallback;
    }
    private String optString(String key, String fallback) {
      Object value = values.get(key);
      return value == null ? fallback : String.valueOf(value);
    }
  }
}
`;
  const temporaryRoot = fs.mkdtempSync(
    path.join(os.tmpdir(), "pending-gap-callback-java-"),
  );
  const sourcePath = path.join(temporaryRoot, `${className}.java`);
  fs.writeFileSync(sourcePath, javaSource, "utf8");
  const compiled = spawnSync(
    "javac",
    ["-d", temporaryRoot, sourcePath],
    { encoding: "utf8" },
  );
  if (compiled.status !== 0) {
    fs.rmSync(temporaryRoot, { recursive: true, force: true });
    fail(
      "PENDING_GAP_CALLBACK_JAVA_COMPILE_FAILED:"
        + (compiled.stderr || compiled.stdout).trim(),
    );
  }
  const executed = spawnSync(
    "java",
    ["-cp", temporaryRoot, className],
    { encoding: "utf8" },
  );
  fs.rmSync(temporaryRoot, { recursive: true, force: true });
  if (executed.status !== 0) {
    fail(
      "PENDING_GAP_CALLBACK_JAVA_EXECUTION_FAILED:"
        + (executed.stderr || executed.stdout).trim(),
    );
  }
  let facts;
  try {
    facts = JSON.parse(executed.stdout.trim());
  } catch (error) {
    fail(`PENDING_GAP_CALLBACK_JAVA_RESULT_INVALID:${error.message}`);
  }
  return {
    ...facts,
    exactContinueBodySha256: createHash("sha256").update(continueBody).digest("hex"),
    exactRequestsNextBodySha256:
      createHash("sha256").update(requestsNextBody).digest("hex"),
  };
}

function pendingGapCallbackContract(mutantMode = "") {
  const renderer = generatedPendingGapCallbackProbe(mutantMode);
  const java = runPendingGapCallbackJavaContract(renderer.facts);
  const expectedCursor = String(renderer.facts.nearerPrevStart || "");
  const valid = renderer.facts.deeperStatus === "PENDING_GAP"
    && renderer.facts.nearerStatus === "READY"
    && Number(renderer.facts.pendingAfterDeeper) === 1
    && Number(renderer.facts.pendingAfterNearer) === 0
    && Number(renderer.facts.readyAfterNearer) === 2
    && renderer.facts.trueTopReached === true
    && Number(renderer.facts.committedHistoryFrontier) === 0
    && java.pendingRequestsNext === false
    && java.readyRequestsNext === true
    && Number(java.nextCursorCalls) === 1
    && String(java.nextCursor) === expectedCursor;
  if (!valid) {
    fail(`PENDING_GAP_CALLBACK_ORPHANS_REFILL facts=${JSON.stringify({
      renderer: renderer.facts,
      java,
      expectedCursor,
    })}`);
  }
  return { renderer: renderer.facts, java };
}

function generatedBootstrapProbe() {
  const fixture = canonicalGeneratedFixture(39, false);
  const hookScript = generatedCaptureTelemetryHookScript();
  const storage = acceptedFrameColumnStorageContract();
  const javaContract = runAcceptedFrameColumnJavaContract();
  const payload = {
    ok: true,
    rows: ["BOOTSTRAP-ROW-0001 accepted terminal frame"],
    windowId: "@777",
    frameWindowId: "@777",
    requestedWindowId: "@777",
    displayWindowId: "@777",
    resolvedWindowId: "@777",
    acceptedWindowIds: ["@777"],
    requestedCols: 39,
    hasMoreBefore: true,
    copyModeViewport: false,
    paneInMode: 0,
    scrollPosition: 0,
    historySize: 13000,
  };
  const encoded = Buffer.from(JSON.stringify({
    hookScript,
    storageMode: storage.mode,
    javaContract,
    payload,
    bodySha256: createHash("sha256")
      .update(payload.rows.join("\n"), "utf8")
      .digest("hex"),
    batch: fixture.batches[0],
    liveFrame: fixture.liveFrame,
  }), "utf8").toString("base64");
  const probe = `
<script id="bootstrapFixtureData" type="application/octet-stream">${encoded}</script>
<script>
(() => {
  const result = { failures: [], facts: {} };
  const finish = () => {
    document.getElementById("contractResult").textContent = btoa(unescape(encodeURIComponent(JSON.stringify(result))));
  };
  const contractFailure = (reason) => {
    if (!result.facts.contractFailures) result.facts.contractFailures = [];
    result.facts.contractFailures.push(reason);
  };
  try {
    const fixture = JSON.parse(atob(document.getElementById("bootstrapFixtureData").textContent.trim()));
    const api = window.__mantisCaptureRenderer;
    const active = document.querySelector('.screenBuffer[data-active="1"]');
    if (!api || !active || !window.visualViewport) throw new Error("bootstrap renderer fixture missing");
    Object.defineProperty(window.visualViewport, "width", { configurable: true, get: () => 300 });
    Object.defineProperty(window.visualViewport, "scale", { configurable: true, get: () => 1 });
    if (typeof api.syncViewportGeometryOnly === "function") api.syncViewportGeometryOnly();
    const measured = api.measure();
    api.refresh = () => false;
    api.refreshIfIdle = () => false;
    window.requestAnimationFrame = (callback) => setTimeout(() => callback(Date.now()), 0);

    const bridgeEvents = [];
    let sequence = 0;
    window.WeztermCaptureBridge = {
      bodySha256ForTelemetry: (body) => (
        String(body) === fixture.payload.rows.join("\\n")
          ? fixture.bodySha256
          : ""
      ),
      onCaptureRendererTelemetry: (json) => bridgeEvents.push({ sequence: ++sequence, kind: "telemetry", payload: JSON.parse(json) }),
      requestIdleVisualCommit: (json) => bridgeEvents.push({ sequence: ++sequence, kind: "commit", payload: JSON.parse(json) }),
    };
    const responseFor = () => ({
      status: 200,
      ok: true,
      clone: () => ({ json: () => Promise.resolve(fixture.payload) }),
      json: () => Promise.resolve(fixture.payload),
    });
    window.fetch = () => Promise.resolve(responseFor());
    const installed = (0, eval)(fixture.hookScript);

    Promise.resolve(window.fetch("/terminal-frame?windowId=%40777&readOnly=1"))
      .then(() => new Promise((resolve) => setTimeout(resolve, 40)))
      .then(() => {
        active.textContent = fixture.payload.rows.join("\\n");
        return new Promise((resolve) => setTimeout(resolve, 140));
      })
      .then(() => {
        const accepted = bridgeEvents
          .filter((event) => event.payload && event.payload.acceptedFrame)
          .sort((left, right) => left.sequence - right.sequence);
        const responseAccepted = accepted.some((event) => event.payload.endpoint === "/terminal-frame" && event.payload.stage === "response");
        const domAccepted = accepted.some((event) => event.payload.endpoint === "dom-apply");
        const commitAccepted = accepted.some((event) => event.kind === "commit");
        const terminalAccepted = accepted.filter((event) => event.payload.endpoint === "/terminal-frame");
        const domAcceptedEvents = accepted.filter((event) => event.payload.endpoint === "dom-apply");
        const terminalWidthsForwarded = terminalAccepted.length >= 2
          && terminalAccepted.every((event) => Number(event.payload.requestedCols) === 39);
        const domWidthsOmitted = domAcceptedEvents.length >= 2
          && domAcceptedEvents.every((event) => !Number.isFinite(Number(event.payload.requestedCols)));
        const storedCols = Number(fixture.javaContract.actualSequenceCols);
        const admittedCols = Number(fixture.javaContract.admittedCols);

        const chunkRequests = [];
        let staged = { status: "SKIPPED_NO_RENDER_COLS" };
        if (admittedCols >= 20) {
          chunkRequests.push("/scrollback/chunk?windowId=%40777&lines=500&cols=" + admittedCols);
          api.stageRenderedFrame(
            (fixture.liveFrame.rows || []).join("\\n"),
            (fixture.liveFrame.rowsHtml || []).join("\\n"),
            fixture.liveFrame.rowsHtml || [],
            fixture.liveFrame.rowKeys || []
          );
          staged = api.stageReadyHistoryBatch(fixture.batch);
        }
        const state = api.state();

        if (installed !== "capture-telemetry-installed") contractFailure("actual-hook-not-installed");
        if (Number(measured.cols) !== 39) contractFailure("requested-cols-not-live-39");
        if (!responseAccepted || !domAccepted || !commitAccepted) contractFailure("accepted-response-dom-commit-sequence-missing");
        if (!terminalWidthsForwarded) contractFailure("actual-hook-dropped-response-or-commit-cols");
        if (!domWidthsOmitted) contractFailure("dom-omission-negative-not-exercised");
        if (fixture.storageMode !== "preserve-valid") contractFailure("accepted-frame-storage-clobbers-or-unknown");
        if (storedCols !== 39 || admittedCols !== 39) contractFailure("accepted-sequence-dropped-cols");
        // WHY(v281 passive-history pressure): repeated accepted frames for one
        // owner must preserve measured cols without starting a history fetch on
        // every live-frame heartbeat. One four-frame same-owner sequence therefore
        // prewarms exactly once, on owner admission, while the checks below prove
        // omitted/invalid widths cannot erase or invent the retained value.
        if (Number(fixture.javaContract.prewarmCount) !== 1 || Number(fixture.javaContract.prewarmMinCols) !== 39) contractFailure("new-owner-prewarm-contract-broken");
        if (Number(fixture.javaContract.sameOwnerOmissionCols) !== 39) contractFailure("same-owner-omission-erased-cols");
        if (Number(fixture.javaContract.noPriorWidthCols) !== -1) contractFailure("missing-width-invented-cols");
        if (Number(fixture.javaContract.targetResetCols) !== -1) contractFailure("target-change-did-not-reset-cols");
        if (Number(fixture.javaContract.staleOldWindowCols) !== -1) contractFailure("stale-old-window-width-reused");
        if (Number(fixture.javaContract.subTwentyCols) !== -1 || Number(fixture.javaContract.invalidSameOwnerCols) !== 39) contractFailure("sub-20-width-admitted");
        if (Number(fixture.javaContract.exceptionOwnerCommitted) !== 1
            || Number(fixture.javaContract.exceptionOwnerCols) !== -1
            || Number(fixture.javaContract.exceptionAdmittedCols) !== -1
            || Number(fixture.javaContract.exceptionPrewarmLastCols) !== -1) contractFailure("owner-reset-skipped-on-parse-exception");
        if (chunkRequests.length !== 1 || chunkRequests[0].indexOf("cols=39") < 0) contractFailure("scrollback-chunk-cols-39-missing");
        if (staged.status !== "READY" || !Number.isFinite(Number(state.nextStart))) contractFailure("ready-bootstrap-not-staged");
        if (admittedCols < 20) contractFailure("renderer-cols-unavailable");

        result.facts = {
          ...result.facts,
          installed,
          measuredCols: measured.cols,
          storageMode: fixture.storageMode,
          javaContract: fixture.javaContract,
          bridgeStages: bridgeEvents.map((event) => ({ kind: event.kind, endpoint: event.payload && event.payload.endpoint, stage: event.payload && (event.payload.stage || "commit"), acceptedFrame: !!(event.payload && event.payload.acceptedFrame), frameWindowId: event.payload && event.payload.frameWindowId, windowId: event.payload && event.payload.windowId, requestedCols: event.payload && event.payload.requestedCols, hash: event.payload && event.payload.hash, frameHash: event.payload && event.payload.frameHash })),
          acceptedStages: accepted.map((event) => ({ kind: event.kind, endpoint: event.payload.endpoint, stage: event.payload.stage || "commit", requestedCols: event.payload.requestedCols })),
          rememberedCols: storedCols,
          sameOwnerOmissionCols: fixture.javaContract.sameOwnerOmissionCols,
          noPriorWidthCols: fixture.javaContract.noPriorWidthCols,
          targetResetCols: fixture.javaContract.targetResetCols,
          staleOldWindowCols: fixture.javaContract.staleOldWindowCols,
          subTwentyCols: fixture.javaContract.subTwentyCols,
          chunkRequests,
          stagedStatus: staged.status,
          nextStart: state.nextStart,
          rendererColsUnavailable: admittedCols < 20,
        };
        if (result.facts.contractFailures && result.facts.contractFailures.length) {
          result.failures.push("MOBILE_READY_BOOTSTRAP_RENDER_COLS_DROPPED");
        }
        finish();
      })
      .catch((error) => {
        result.facts.contractFailures = ["bootstrap-probe-error:" + String(error && error.message || error)];
        result.failures.push("MOBILE_READY_BOOTSTRAP_RENDER_COLS_DROPPED");
        finish();
      });
  } catch (error) {
    result.facts.contractFailures = ["bootstrap-probe-error:" + String(error && error.message || error)];
    result.failures.push("MOBILE_READY_BOOTSTRAP_RENDER_COLS_DROPPED");
    finish();
  }
})();
</script>`;
  const result = runGeneratedChrome(
    fixture.html,
    probe,
    "mobile-ready-bootstrap-render-cols",
    1200,
    { windowSize: "500,844" },
  );
  result.facts.hookSha256 = createHash("sha256").update(hookScript).digest("hex");
  return result;
}

function generatedReadyRefillLifecycleProbe() {
  const fixture = canonicalGeneratedFixture(39, false);
  const probe = `
<script>
(() => {
  const result = { failures: [], facts: {} };
  const finish = () => {
    document.getElementById("contractResult").textContent = btoa(unescape(encodeURIComponent(JSON.stringify(result))));
  };
  try {
    const api = window.__mantisCaptureRenderer;
    if (!api || !window.visualViewport) throw new Error("READY refill renderer API missing");
    Object.defineProperty(window.visualViewport, "width", { configurable: true, get: () => 300 });
    Object.defineProperty(window.visualViewport, "scale", { configurable: true, get: () => 1 });
    if (typeof api.syncViewportGeometryOnly === "function") api.syncViewportGeometryOnly();
    const measured = api.measure();
    const requests = [];
    window.WeztermCaptureBridge = {
      requestReadyHistoryRefill: (json) => requests.push(JSON.parse(json)),
    };
    const generationKey = "@777:%777:4242:200000";
    const batch = (start, end, hasMoreBefore, prevStart, generation = generationKey, liveRowFrontier = 13000) => ({
      state: "READY",
      windowId: "@777",
      generationKey: generation,
      start,
      end,
      renderCols: 39,
      hasMoreBefore,
      prevStart,
      liveRowFrontier,
      renderRows: [{
        key: generation + ":" + start + ":0",
        absoluteRow: start,
        segmentIndex: 0,
        text: "ROW-" + start,
        html: "ROW-" + start,
      }],
    });

    api.resetReadyHistoryForBottom();
    const initialStage = api.stageReadyHistoryBatch(batch(12999, 12999, true, "12998"));
    const firstRequest = api.maybeRequestReadyHistoryRefill("guard-first");
    const duplicateWhileLatched = api.maybeRequestReadyHistoryRefill("guard-busy-loop");
    const failure = api.noteReadyHistoryRefillFailure("guard-failure");
    const requestAfterFailure = api.maybeRequestReadyHistoryRefill("guard-after-failure");
    const successStage = api.stageReadyHistoryBatch(batch(12998, 12998, true, "12997"));
    const requestAfterSuccess = api.maybeRequestReadyHistoryRefill("guard-after-success");
    const duplicateStage = api.stageReadyHistoryBatch(batch(12998, 12998, true, "12997"));
    const requestAfterDuplicate = api.maybeRequestReadyHistoryRefill("guard-after-duplicate");
    const generationReject = api.stageReadyHistoryBatch(batch(12997, 12997, true, "12996", "generation-other"));
    const requestAfterGenerationReject = api.maybeRequestReadyHistoryRefill("guard-after-generation-reject");
    const targetChange = api.setWindowId("@778");
    const requestAfterTargetChange = api.maybeRequestReadyHistoryRefill("guard-after-target-change");

    api.setWindowId("@777");
    const topNearStage = api.stageReadyHistoryBatch(batch(1, 1, true, "0", "top-generation", 2));
    const topRequest = api.maybeRequestReadyHistoryRefill("guard-top-request");
    const topStage = api.stageReadyHistoryBatch(batch(0, 0, false, "", "top-generation", 2));
    const requestAfterTrueTop = api.maybeRequestReadyHistoryRefill("guard-after-true-top");
    const finalState = api.state();

    result.facts = {
      measuredCols: measured.cols,
      initialStage: initialStage.status,
      firstRequest,
      duplicateWhileLatched,
      failureStatus: failure.status,
      requestAfterFailure,
      successStage: successStage.status,
      requestAfterSuccess,
      duplicateStage: duplicateStage.status,
      requestAfterDuplicate,
      generationReject: generationReject.status,
      requestAfterGenerationReject,
      targetChange,
      requestAfterTargetChange,
      topNearStage: topNearStage.status,
      topRequest,
      topStage: topStage.status,
      requestAfterTrueTop,
      trueTopPrepared: finalState.trueTopPrepared,
      requestCount: requests.length,
      requestStatuses: requests.map((item) => item.status),
      requestStarts: requests.map((item) => item.nextStart),
      requestCols: requests.map((item) => item.measuredCols),
    };
    const acceptedDuplicate = ["QUEUED_DUPLICATE", "COMMITTED_DUPLICATE"].includes(duplicateStage.status);
    if (Number(measured.cols) !== 39
        || initialStage.status !== "READY"
        || firstRequest !== true || duplicateWhileLatched !== false
        || failure.status !== "REFILL_FAILED" || requestAfterFailure !== true
        || successStage.status !== "READY" || requestAfterSuccess !== true
        || !acceptedDuplicate || requestAfterDuplicate !== true
        || generationReject.status !== "REJECTED_GENERATION" || requestAfterGenerationReject !== true
        || targetChange !== "target-changed" || requestAfterTargetChange !== false
        || topNearStage.status !== "READY" || topRequest !== true || topStage.status !== "READY"
        || requestAfterTrueTop !== false || finalState.trueTopPrepared !== true
        || requests.length !== 6
        || requests.some((item) => item.status !== "REFILL_REQUESTED" || Number(item.measuredCols) !== 39)) {
      result.failures.push("READY_REFILL_RENDERER_LIFECYCLE_BROKEN");
    }
  } catch (error) {
    result.failures.push("READY_REFILL_RENDERER_LIFECYCLE_ERROR:" + String(error && error.message || error));
  }
  finish();
})();
</script>`;
  return runGeneratedChrome(
    fixture.html,
    probe,
    "ready-refill-renderer-lifecycle",
    1200,
    { windowSize: "500,844" },
  );
}

function generatedProductProbe() {
  const measurementFixture = canonicalGeneratedFixture(132, false);
  const measurement = runGeneratedChrome(
    measurementFixture.html,
    `<script>(() => {
      const result = { failures: [], facts: {} };
      try {
        const api = window.__mantisCaptureRenderer;
        if (!api) throw new Error("generated renderer API missing");
        const measured = api.measure();
        result.facts = { measured };
        if (!Number.isFinite(measured.cols) || measured.cols < 20) result.failures.push("MOBILE_COLS_UNMEASURED");
      } catch (error) {
        result.failures.push("GENERATED_MEASURE_ERROR:" + String(error && error.message || error));
      }
      document.getElementById("contractResult").textContent = btoa(unescape(encodeURIComponent(JSON.stringify(result))));
    })();</script>`,
    "measure",
    500,
  );
  const measuredCols = Number(measurement.facts.measured.cols);
  if (measuredCols === 132) fail("CACHE_COLS_DIVERGE_LIVE_COLS: Mobile reused Desktop's fixed columns");

  const fixture = canonicalGeneratedFixture(measuredCols, true);
  const encodedFixture = Buffer.from(JSON.stringify({ batches: fixture.batches }), "utf8").toString("base64");
  const deepProbe = `
<script id="canonicalFixtureData" type="application/octet-stream">${encodedFixture}</script>
<script>
(() => {
  const result = { failures: [], facts: {} };
  const finish = () => {
    document.getElementById("contractResult").textContent = btoa(unescape(encodeURIComponent(JSON.stringify(result))));
  };
  try {
    const api = window.__mantisCaptureRenderer;
    const active = document.querySelector('.screenBuffer[data-active="1"]');
    const fixture = JSON.parse(atob(document.getElementById("canonicalFixtureData").textContent.trim()));
    if (!api || !active || !fixture.batches.length) throw new Error("generated renderer API/root/batches missing");
    const measured = api.measure();
    if (measured.cols !== ${measuredCols}) result.failures.push("CACHE_COLS_DIVERGE_LIVE_COLS");
    window.WeztermCaptureBridge = { requestReadyHistoryRefill: () => { result.facts.backgroundRefillRequests = (result.facts.backgroundRefillRequests || 0) + 1; } };

    const live = document.createDocumentFragment();
    for (let index = 0; index < 240; index += 1) {
      const row = document.createElement("span");
      row.className = "captureRenderRow";
      row.dataset.renderRowKey = "@777:%777:4242:200000:" + (13000 + index) + ":0";
      row.textContent = "LIVE-" + String(index).padStart(4, "0") + " prepared visible row";
      live.appendChild(row);
    }
    active.appendChild(live);

    const base = fixture.batches[0];
    const stagedBase = api.stageReadyHistoryBatch(base);
    const beforeFarNudge = api.state();
    const farNudge = api.nudgeTouchScroll(1);
    const afterFarNudge = api.state();
    if (stagedBase.status !== "READY" || beforeFarNudge.readyHistoryBatches !== 1) result.failures.push("STAGING_ACCEPTANCE_UNOBSERVED");
    if (afterFarNudge.readyHistoryBatches !== 1 || farNudge.seamStatus !== "NOT_AT_SEAM") result.failures.push("READY_BATCH_CONSUMED_BEFORE_SEAM");

    const forcedBase = api.prependReadyHistoryAtBoundary("up", 0, 0, true);
    if (forcedBase.status !== "COMMITTED") result.failures.push("BASE_INTERVAL_NOT_COMMITTED");
    if (Math.abs(Number(forcedBase.correctionErrorPx || 0)) > 0.5) result.failures.push("ANCHOR_ERROR_OVER_HALF_PX");
    const restagedBase = api.stageReadyHistoryBatch(base);
    if (restagedBase.status !== "COMMITTED_DUPLICATE" || api.state().readyHistoryBatches !== 0) result.failures.push("COMMITTED_INTERVAL_REQUEUED");

    if (${parityCase === "hidden-underrun" ? "true" : "false"}) {
      let underrun = null;
      let framesToUnderrun = 0;
      for (let frame = 0; frame < 400; frame += 1) {
        const candidate = api.nudgeTouchScroll(240);
        framesToUnderrun = frame + 1;
        if (["READY_UNDERRUN_BEFORE_TRUE_TOP", "READY_GAP"].includes(String(candidate.seamStatus || ""))) {
          underrun = candidate;
          break;
        }
      }
      if (!underrun) {
        result.facts = { framesToUnderrun, finalState: api.state() };
        result.failures.push("HIDDEN_UNDERRUN_CONTRACT_NOT_OBSERVED");
        finish();
        return;
      }
      const seamStatus = String(underrun.seamStatus || "");
      const seamNeedsReady = ["READY_UNDERRUN_BEFORE_TRUE_TOP", "READY_GAP"].includes(seamStatus);
      result.facts = {
        status: underrun.status,
        seamStatus,
        appliedPx: underrun.appliedPx,
        needsReady: underrun.needsReady,
        trueTopReached: underrun.trueTopReached,
        remainingPreparedPx: underrun.remainingPreparedPx,
        nextStart: underrun.nextStart,
        framesToUnderrun,
      };
      if (underrun.status === "APPLIED" && seamNeedsReady && Number(underrun.appliedPx) > 0) {
        result.failures.push("READY_UNDERRUN_HIDDEN_BY_APPLIED");
      } else if (!seamNeedsReady || underrun.status !== seamStatus
          || !underrun.needsReady || underrun.trueTopReached || Number(underrun.appliedPx) <= 0) {
        result.failures.push("HIDDEN_UNDERRUN_CONTRACT_NOT_OBSERVED");
      }
      finish();
      return;
    }

    const stagingStatuses = [];
    const completionOrder = fixture.batches.slice(1);
    if (${parityCase === "out-of-order" ? "true" : "false"} && completionOrder.length >= 2) {
      const first = completionOrder[0];
      completionOrder[0] = completionOrder[1];
      completionOrder[1] = first;
    }
    for (const batch of completionOrder) {
      const staged = api.stageReadyHistoryBatch(batch);
      stagingStatuses.push(staged.status);
      const acceptedPendingGap = ${parityCase === "out-of-order" ? "true" : "false"}
        && staged.status === "PENDING_GAP";
      if (staged.status !== "READY" && !acceptedPendingGap) {
        result.failures.push("READY_FRONTIER_NOT_SELF_DRIVING");
        break;
      }
    }

    let committedSeams = 1;
    let blockedFramesBeforeTop = 0;
    let maxBlockedFramesBeforeTop = 0;
    let maxAnchorErrorPx = Math.abs(Number(forcedBase.correctionErrorPx || 0));
    let lastTruth = api.state();
    const tailFrames = [];
    for (let frame = 0; frame < 2400 && !result.failures.length; frame += 1) {
      const truth = api.nudgeTouchScroll(240);
      lastTruth = truth;
      tailFrames.push({
        frame,
        status: truth.status,
        seamStatus: truth.seamStatus,
        appliedPx: Number(truth.appliedPx || 0),
        trueTopReached: truth.trueTopReached === true || truth.status === "TRUE_HISTORY_TOP",
        remainingPreparedPx: Number.isFinite(Number(truth.remainingPreparedPx))
          ? Number(truth.remainingPreparedPx)
          : null,
      });
      while (tailFrames.length > 12) tailFrames.shift();
      if (truth.seamStatus === "COMMITTED") {
        committedSeams += 1;
        maxAnchorErrorPx = Math.max(maxAnchorErrorPx, Math.abs(Number(truth.anchorErrorPx || 0)));
        if (maxAnchorErrorPx > 0.5) result.failures.push("ANCHOR_ERROR_OVER_HALF_PX");
      }
      if (["READY_UNDERRUN_BEFORE_TRUE_TOP", "READY_GAP", "CLAMPED"].includes(truth.status)
          || ["READY_UNDERRUN_BEFORE_TRUE_TOP", "READY_GAP"].includes(truth.seamStatus)) {
        result.failures.push(truth.status === "CLAMPED" ? "NUDGE_CLAMPED_BEFORE_TRUE_TOP" : "READY_UNDERRUN_BEFORE_TRUE_TOP");
        break;
      }
      const trueTopReached = truth.trueTopReached === true
        || truth.status === "TRUE_HISTORY_TOP";
      if (Math.abs(Number(truth.appliedPx || 0)) < 0.5 && !trueTopReached) {
        blockedFramesBeforeTop += 1;
        maxBlockedFramesBeforeTop = Math.max(maxBlockedFramesBeforeTop, blockedFramesBeforeTop);
        if (blockedFramesBeforeTop > 2) result.failures.push("STOPPED_MORE_THAN_TWO_FRAMES_BEFORE_TRUE_TOP");
      } else {
        blockedFramesBeforeTop = 0;
      }
      if (truth.status === "TRUE_HISTORY_TOP") break;
    }

    const finalState = api.state();
    const activeNow = document.querySelector('.screenBuffer[data-active="1"]');
    const historyRows = Array.from(activeNow.querySelectorAll(".readyHistoryRow"));
    const keys = historyRows.map((row) => row.dataset.renderRowKey || "");
    const uniqueKeys = new Set(keys);
    const segmentsByRow = new Map();
    for (const row of historyRows) {
      const absoluteRow = Number(row.dataset.absoluteRow);
      const segmentIndex = Number(row.dataset.segmentIndex);
      if (!segmentsByRow.has(absoluteRow)) segmentsByRow.set(absoluteRow, []);
      segmentsByRow.get(absoluteRow).push(segmentIndex);
    }
    if (!finalState.trueTopReached || finalState.committedHistoryFrontier !== 0) result.failures.push("NUDGE_CLAMPED_BEFORE_TRUE_TOP");
    // WHY(v292 native-fling merge-forward): nudgeTouchScroll deliberately returns
    // the hot-path status/seamStatus tuple, not the older full seam object with
    // batchStart/batchEnd. Count authoritative COMMITTED seams and prove continuity
    // from the final absolute-row/segment-key DOM below; requiring removed response
    // fields misclassified TRUE_HISTORY_TOP as three pre-top blocked frames.
    if (committedSeams !== fixture.batches.length) result.failures.push("READY_FRONTIER_NOT_SELF_DRIVING");
    if (uniqueKeys.size !== keys.length) result.failures.push("DUPLICATE_CANONICAL_KEYS");
    if (segmentsByRow.size !== 13000) result.failures.push("ABSOLUTE_ROW_COVERAGE_GAP");
    for (let absoluteRow = 0; absoluteRow < 13000; absoluteRow += 1) {
      const segments = (segmentsByRow.get(absoluteRow) || []).sort((a, b) => a - b);
      if (!segments.length || segments[0] !== 0 || segments.some((value, index) => value !== index)) {
        result.failures.push("CANONICAL_SEGMENT_GAP");
        break;
      }
    }
    const viewportTop = window.visualViewport && Number.isFinite(window.visualViewport.offsetTop) ? window.visualViewport.offsetTop : 0;
    const firstRow = historyRows.find((row) => Number(row.dataset.absoluteRow) === 0 && Number(row.dataset.segmentIndex) === 0);
    const firstRect = firstRow ? firstRow.getBoundingClientRect() : null;
    const activeRect = activeNow.getBoundingClientRect();
    if (!firstRect || firstRect.top > viewportTop + 0.75 || activeRect.top > viewportTop + 0.75) result.failures.push("UPPER_BLANK_BAND_AT_TRUE_TOP");
    if (Number(finalState.rightOverflowPx || 0) > 0.5) result.failures.push("RIGHT_EDGE_OVERFLOW");

    result.facts = {
      measuredCols: measured.cols,
      producerIntervals: fixture.batches.length,
      committedSeams,
      canonicalKeys: uniqueKeys.size,
      absoluteRows: segmentsByRow.size,
      farNudgeStatus: farNudge.status,
      farNudgeSeamStatus: farNudge.seamStatus,
      restagedBaseStatus: restagedBase.status,
      lastStatus: lastTruth.status,
      trueTopReached: finalState.trueTopReached,
      remainingPreparedPx: finalState.remainingPreparedPx,
      rightOverflowPx: finalState.rightOverflowPx,
      firstRowTop: firstRect && firstRect.top,
      viewportTop,
      maxAnchorErrorPx,
      maxBlockedFramesBeforeTop,
      tailFrames,
      stagingStatuses: Array.from(new Set(stagingStatuses)),
    };
  } catch (error) {
    result.failures.push("GENERATED_PRODUCT_PROBE_ERROR:" + String(error && error.message || error));
  }
  finish();
})();
</script>`;
  const deepResult = runGeneratedChrome(
    fixture.html,
    deepProbe,
    "deep-frontier",
    3000,
  );

  const parityProbeFor = (parityFixture, visualViewportWidth = 0) => {
    const encodedParity = Buffer.from(JSON.stringify({
      batch: parityFixture.batches[0],
      liveFrame: parityFixture.liveFrame,
    }), "utf8").toString("base64");
    return `
<script id="parityFixtureData" type="application/octet-stream">${encodedParity}</script>
<script>
(() => {
  const result = { failures: [], facts: {} };
  const finish = () => {
    document.getElementById("contractResult").textContent = btoa(unescape(encodeURIComponent(JSON.stringify(result))));
  };
  function rangeForOffsets(element, startOffset, endOffset) {
    const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT);
    const range = document.createRange();
    let node;
    let offset = 0;
    let start = null;
    let end = null;
    while ((node = walker.nextNode())) {
      const next = offset + node.data.length;
      if (!start && startOffset <= next) start = [node, Math.max(0, startOffset - offset)];
      if (!end && endOffset <= next) { end = [node, Math.max(0, endOffset - offset)]; break; }
      offset = next;
    }
    if (!start || !end) { range.selectNodeContents(element); return range; }
    range.setStart(start[0], start[1]);
    range.setEnd(end[0], end[1]);
    return range;
  }
  function snapshot(element) {
    const style = getComputedStyle(element);
    const rect = element.getBoundingClientRect();
    const fullRange = rangeForOffsets(element, 0, element.textContent.length);
    const fullRect = fullRange.getBoundingClientRect();
    const separatorAt = Math.max(0, element.textContent.indexOf("|"));
    const separatorRect = rangeForOffsets(element, separatorAt, Math.min(element.textContent.length, separatorAt + 1)).getBoundingClientRect();
    return {
      innerHTML: element.innerHTML,
      textContent: element.textContent,
      fontFamily: style.fontFamily,
      fontSize: style.fontSize,
      fontStyle: style.fontStyle,
      fontWeight: style.fontWeight,
      color: style.color,
      backgroundColor: style.backgroundColor,
      lineHeight: style.lineHeight,
      whiteSpace: style.whiteSpace,
      letterSpacing: style.letterSpacing,
      height: rect.height,
      width: rect.width,
      left: rect.left,
      right: rect.right,
      selectionWidth: fullRect.width,
      selectionHeight: fullRect.height,
      selectionRight: fullRect.right,
      separatorLeft: separatorRect.left,
      separatorWidth: separatorRect.width,
      separatorHeight: separatorRect.height,
    };
  }
  function sameNumber(left, right) { return Math.abs(Number(left) - Number(right)) <= 0.1; }
  try {
    const api = window.__mantisCaptureRenderer;
    const forcedVisualWidth = ${Number(visualViewportWidth) || 0};
    if (forcedVisualWidth > 0 && window.visualViewport) {
      Object.defineProperty(window.visualViewport, "width", { configurable: true, get: () => forcedVisualWidth });
      if (api && typeof api.syncViewportGeometryOnly === "function") api.syncViewportGeometryOnly();
    }
    const fixture = JSON.parse(atob(document.getElementById("parityFixtureData").textContent.trim()));
    const liveFrame = fixture.liveFrame || {};
    const liveKeys = new Set(Array.isArray(liveFrame.rowKeys) ? liveFrame.rowKeys : []);
    const row = fixture.batch.renderRows.find((candidate) => candidate.absoluteRow < fixture.batch.liveRowFrontier && candidate.text.includes("|") && liveKeys.has(candidate.key));
    if (!api || !row || !Array.isArray(liveFrame.rowsHtml) || !Array.isArray(liveFrame.rowKeys)) throw new Error("parity renderer API/actual live row missing");
    if (Number(liveFrame.requestedCols) !== Number(fixture.batch.renderCols)) result.failures.push("LIVE_CACHE_RENDER_COLS_DIVERGENCE");
    // Headless dump-dom does not advance compositor rAF reliably. Exercise the
    // generated product's own setTimeout fallback so the same hidden-buffer swap
    // completes before comparing independently serialized live and cache paths.
    window.requestAnimationFrame = undefined;
    api.stageRenderedFrame(
      (Array.isArray(liveFrame.rows) ? liveFrame.rows : []).join("\\n"),
      (Array.isArray(liveFrame.rowsHtml) ? liveFrame.rowsHtml : []).join("\\n"),
      liveFrame.rowsHtml,
      liveFrame.rowKeys
    );
    setTimeout(() => {
      try {
        const active = document.querySelector('.screenBuffer[data-active="1"]');
        const liveRow = Array.from(active.querySelectorAll(".captureRenderRow")).find((candidate) => candidate.dataset.renderRowKey === row.key);
        if (!liveRow) throw new Error("canonical live row not rendered");
        const liveSnapshot = snapshot(liveRow);
        const measuredNow = api.measure();
        if (forcedVisualWidth > 0 && Number(measuredNow.cols) !== Number(fixture.batch.renderCols)) {
          result.failures.push("MOBILE_VISUAL_VIEWPORT_WIDTH_IGNORED");
        }
        const staged = api.stageReadyHistoryBatch(fixture.batch);
        const committed = api.prependReadyHistoryAtBoundary("up", 0, 0, true);
        const cacheRow = Array.from(active.querySelectorAll(".readyHistoryRow")).find((candidate) => candidate.dataset.renderRowKey === row.key);
        const viewportWidth = window.visualViewport && Number.isFinite(window.visualViewport.width) ? window.visualViewport.width : window.innerWidth;
        result.facts = {
          measuredCols: measuredNow.cols,
          expectedRenderCols: fixture.batch.renderCols,
          liveRequestedCols: liveFrame.requestedCols,
          forcedVisualWidth,
          viewportWidth,
          innerWidth: window.innerWidth,
          staged: staged.status,
          committed: committed.status,
          liveSelectionRight: liveSnapshot.selectionRight,
          cacheRowPresent: !!cacheRow,
          rightOverflowPx: api.state().rightOverflowPx,
        };
        if (staged.status === "CACHE_COLS_DIVERGE_LIVE_COLS" && forcedVisualWidth > 0) {
          result.failures.push("MOBILE_VISUAL_VIEWPORT_WIDTH_IGNORED");
        } else if (staged.status !== "READY" || committed.status !== "COMMITTED" || !cacheRow) {
          throw new Error("canonical cache row not committed: stage=" + String(staged.status) + " commit=" + String(committed.status));
        }
        if (!cacheRow) { finish(); return; }
        const cacheSnapshot = snapshot(cacheRow);
        for (const field of ["innerHTML", "textContent", "fontFamily", "fontSize", "fontStyle", "fontWeight", "color", "backgroundColor", "lineHeight", "whiteSpace", "letterSpacing"]) {
          if (liveSnapshot[field] !== cacheSnapshot[field]) result.failures.push("LIVE_CACHE_STYLE_DIVERGENCE:" + field);
        }
        for (const field of ["height", "width", "left", "right", "selectionWidth", "selectionHeight", "selectionRight", "separatorLeft", "separatorWidth", "separatorHeight"]) {
          if (!sameNumber(liveSnapshot[field], cacheSnapshot[field])) result.failures.push("LIVE_CACHE_GEOMETRY_DIVERGENCE:" + field);
        }
        if (liveSnapshot.selectionRight > viewportWidth + 0.5 || cacheSnapshot.selectionRight > viewportWidth + 0.5 || api.state().rightOverflowPx > 0.5) {
          result.failures.push("RIGHT_EDGE_OVERFLOW");
        }
        result.facts = { ...result.facts, live: liveSnapshot, cache: cacheSnapshot, cacheSelectionRight: cacheSnapshot.selectionRight };
      } catch (error) {
        result.failures.push("LIVE_CACHE_PARITY_ERROR:" + String(error && error.message || error));
      }
      finish();
    }, 120);
  } catch (error) {
    result.failures.push("LIVE_CACHE_PARITY_ERROR:" + String(error && error.message || error));
    finish();
  }
})();
</script>`;
  };
  const parityProbe = parityProbeFor(fixture);
  const parityResult = runGeneratedChrome(
    fixture.html,
    parityProbe,
    "live-cache-parity",
    1800,
  );

  const desktopFixture = canonicalGeneratedFixture(132, false);
  const desktopParityResult = runGeneratedChrome(
    desktopFixture.html,
    parityProbeFor(desktopFixture),
    "live-cache-parity-desktop-132",
    1800,
    { viewportMode: "desktop", cols: 132 },
  );

  const narrowVisualWidth = 300;
  const narrowMeasurement = runGeneratedChrome(
    measurementFixture.html,
    `<script>(() => {
      const result = { failures: [], facts: {} };
      try {
        const api = window.__mantisCaptureRenderer;
        if (!api || !window.visualViewport) throw new Error("renderer or visualViewport missing");
        Object.defineProperty(window.visualViewport, "width", { configurable: true, get: () => ${narrowVisualWidth} });
        if (typeof api.syncViewportGeometryOnly === "function") api.syncViewportGeometryOnly();
        const style = getComputedStyle(document.querySelector('.screenBuffer[data-active="1"]'));
        const probe = document.createElement("span");
        probe.textContent = "MMMMMMMMMM";
        probe.style.position = "absolute";
        probe.style.visibility = "hidden";
        probe.style.whiteSpace = "pre";
        probe.style.font = style.font;
        document.body.appendChild(probe);
        const charWidth = Math.max(4, probe.getBoundingClientRect().width / 10);
        probe.remove();
        result.facts = {
          measured: api.measure(),
          expectedVisibleCols: Math.max(20, Math.floor((${narrowVisualWidth} - 12) / charWidth)),
          charWidth,
          visualViewportWidth: window.visualViewport.width,
          innerWidth: window.innerWidth,
        };
      } catch (error) {
        result.failures.push("NARROW_VISUAL_VIEWPORT_MEASURE_ERROR:" + String(error && error.message || error));
      }
      document.getElementById("contractResult").textContent = btoa(unescape(encodeURIComponent(JSON.stringify(result))));
    })();</script>`,
    "measure-mobile-narrow-visual-viewport",
    700,
  );
  const narrowCols = Number(narrowMeasurement.facts.expectedVisibleCols);
  if (!Number.isFinite(narrowCols) || narrowCols < 20 || narrowCols >= measuredCols) {
    fail(`NARROW_VISUAL_VIEWPORT_EXPECTATION_INVALID facts=${JSON.stringify(narrowMeasurement.facts)}`);
  }
  // A true pinch narrows visualViewport.width as magnification, not as layout
  // reflow. Lock that protected native contract separately from the settled
  // scale=1 narrow-viewport crop case above.
  const pinchMeasurement = runGeneratedChrome(
    measurementFixture.html,
    `<script>(() => {
      const result = { failures: [], facts: {} };
      try {
        const api = window.__mantisCaptureRenderer;
        if (!api || !window.visualViewport) throw new Error("renderer or visualViewport missing");
        Object.defineProperty(window.visualViewport, "width", { configurable: true, get: () => ${narrowVisualWidth} });
        Object.defineProperty(window.visualViewport, "scale", { configurable: true, get: () => 2 });
        if (typeof api.syncViewportGeometryOnly === "function") api.syncViewportGeometryOnly();
        result.facts = {
          measured: api.measure(),
          expectedLayoutCols: ${measuredCols},
          visualViewportWidth: window.visualViewport.width,
          visualViewportScale: window.visualViewport.scale,
          innerWidth: window.innerWidth,
        };
        if (Number(result.facts.measured.cols) !== Number(result.facts.expectedLayoutCols)) {
          result.failures.push("MOBILE_PINCH_REFLOWED_RENDER_COLS");
        }
      } catch (error) {
        result.failures.push("MOBILE_PINCH_MEASURE_ERROR:" + String(error && error.message || error));
      }
      document.getElementById("contractResult").textContent = btoa(unescape(encodeURIComponent(JSON.stringify(result))));
    })();</script>`,
    "measure-mobile-pinch-preserves-layout-cols",
    700,
  );
  const narrowFixture = canonicalGeneratedFixture(narrowCols, false);

  const transitionFixtureData = Buffer.from(JSON.stringify({
    initial: {
      batches: fixture.batches.slice(0, 2),
      liveFrame: fixture.liveFrame,
    },
    narrowed: {
      batch: narrowFixture.batches[0],
      liveFrame: narrowFixture.liveFrame,
    },
  }), "utf8").toString("base64");

  let publicMeasureResult = null;
  if (parityCase === "all" || parityCase === "public-measure") {
    const publicMeasureProbe = `
<script id="publicMeasureTransitionData" type="application/octet-stream">${transitionFixtureData}</script>
<script>
(() => {
  const result = { failures: [], facts: {} };
  const finish = () => {
    document.getElementById("contractResult").textContent = btoa(unescape(encodeURIComponent(JSON.stringify(result))));
  };
  try {
    const api = window.__mantisCaptureRenderer;
    const fixture = JSON.parse(atob(document.getElementById("publicMeasureTransitionData").textContent.trim()));
    if (!api || !window.visualViewport || fixture.initial.batches.length < 2) throw new Error("public measure transition fixture missing");
    window.requestAnimationFrame = undefined;
    let visualWidth = window.innerWidth;
    let visualScale = 1;
    Object.defineProperty(window.visualViewport, "width", { configurable: true, get: () => visualWidth });
    Object.defineProperty(window.visualViewport, "scale", { configurable: true, get: () => visualScale });
    api.stageRenderedFrame(
      (fixture.initial.liveFrame.rows || []).join("\\n"),
      (fixture.initial.liveFrame.rowsHtml || []).join("\\n"),
      fixture.initial.liveFrame.rowsHtml || [],
      fixture.initial.liveFrame.rowKeys || []
    );
    setTimeout(() => {
      try {
        const committedStage = api.stageReadyHistoryBatch(fixture.initial.batches[0]);
        const committed = api.prependReadyHistoryAtBoundary("up", 0, 0, true);
        const queued = api.stageReadyHistoryBatch(fixture.initial.batches[1]);
        const activeBefore = document.querySelector('.screenBuffer[data-active="1"]');
        const before = api.state();
        const beforeReadyRows = activeBefore.querySelectorAll(".readyHistoryRow").length;
        const beforeCaptureRows = activeBefore.querySelectorAll(".captureRenderRow").length;
        const beforeCaptureText = Array.from(activeBefore.querySelectorAll(".captureRenderRow")).map((row) => row.textContent).join("\\n");
        let fetchCalls = 0;
        window.fetch = () => {
          fetchCalls += 1;
          return Promise.reject(new Error("unexpected public-measure fetch"));
        };
        visualWidth = ${narrowVisualWidth};
        const measured = api.measure();
        const activeAfter = document.querySelector('.screenBuffer[data-active="1"]');
        const after = api.state();
        const afterReadyRows = activeAfter.querySelectorAll(".readyHistoryRow").length;
        const afterCaptureRows = activeAfter.querySelectorAll(".captureRenderRow").length;
        const afterCaptureText = Array.from(activeAfter.querySelectorAll(".captureRenderRow")).map((row) => row.textContent).join("\\n");
        result.facts = {
          committedStage: committedStage.status,
          committed: committed.status,
          queued: queued.status,
          measuredCols: measured.cols,
          expectedMeasuredCols: ${narrowCols},
          fetchCalls,
          before: {
            cols: before.cols,
            cacheRenderCols: before.cacheRenderCols,
            generationKey: before.generationKey,
            committedHistoryFrontier: before.committedHistoryFrontier,
            readyHistoryBatches: before.readyHistoryBatches,
            readyRows: beforeReadyRows,
            captureRows: beforeCaptureRows,
          },
          after: {
            cols: after.cols,
            cacheRenderCols: after.cacheRenderCols,
            generationKey: after.generationKey,
            committedHistoryFrontier: after.committedHistoryFrontier,
            readyHistoryBatches: after.readyHistoryBatches,
            readyRows: afterReadyRows,
            captureRows: afterCaptureRows,
          },
        };
        if (committedStage.status !== "READY" || committed.status !== "COMMITTED" || queued.status !== "READY"
            || beforeReadyRows <= 0 || beforeCaptureRows <= 0 || Number(measured.cols) !== ${narrowCols}
            || fetchCalls !== 0 || after.cols !== before.cols || after.cacheRenderCols !== before.cacheRenderCols
            || after.generationKey !== before.generationKey
            || after.committedHistoryFrontier !== before.committedHistoryFrontier
            || after.readyHistoryBatches !== before.readyHistoryBatches
            || afterReadyRows !== beforeReadyRows || afterCaptureRows !== beforeCaptureRows
            || afterCaptureText !== beforeCaptureText) {
          result.failures.push("PUBLIC_MEASURE_MUTATES_HISTORY");
        }
      } catch (error) {
        result.failures.push("PUBLIC_MEASURE_ERROR:" + String(error && error.message || error));
      }
      finish();
    }, 140);
  } catch (error) {
    result.failures.push("PUBLIC_MEASURE_ERROR:" + String(error && error.message || error));
    finish();
  }
})();
</script>`;
    publicMeasureResult = runGeneratedChrome(
      fixture.html,
      publicMeasureProbe,
      "public-measure-pure-history",
      1800,
    );
  }

  let pinchTransitionResult = null;
  if (parityCase === "all" || parityCase === "pinch") {
    const pinchTransitionProbe = `
<script id="pinchTransitionData" type="application/octet-stream">${transitionFixtureData}</script>
<script>
(() => {
  const result = { failures: [], facts: {} };
  const finish = () => {
    document.getElementById("contractResult").textContent = btoa(unescape(encodeURIComponent(JSON.stringify(result))));
  };
  try {
    const api = window.__mantisCaptureRenderer;
    const fixture = JSON.parse(atob(document.getElementById("pinchTransitionData").textContent.trim()));
    if (!api || !window.visualViewport) throw new Error("pinch transition fixture missing");
    window.requestAnimationFrame = undefined;
    let visualWidth = ${narrowVisualWidth};
    let visualScale = 1;
    Object.defineProperty(window.visualViewport, "width", { configurable: true, get: () => visualWidth });
    Object.defineProperty(window.visualViewport, "scale", { configurable: true, get: () => visualScale });
    api.stageRenderedFrame(
      (fixture.narrowed.liveFrame.rows || []).join("\\n"),
      (fixture.narrowed.liveFrame.rowsHtml || []).join("\\n"),
      fixture.narrowed.liveFrame.rowsHtml || [],
      fixture.narrowed.liveFrame.rowKeys || []
    );
    setTimeout(() => {
      try {
        const staged = api.stageReadyHistoryBatch(fixture.narrowed.batch);
        const committed = api.prependReadyHistoryAtBoundary("up", 0, 0, true);
        const activeBefore = document.querySelector('.screenBuffer[data-active="1"]');
        const before = api.state();
        const beforeReadyRows = activeBefore.querySelectorAll(".readyHistoryRow").length;
        const beforeCaptureText = Array.from(activeBefore.querySelectorAll(".captureRenderRow")).map((row) => row.textContent).join("\\n");
        let fetchCalls = 0;
        window.fetch = () => {
          fetchCalls += 1;
          return Promise.reject(new Error("unexpected pinch fetch"));
        };
        const samples = [];
        const sample = (label) => {
          const measured = api.measure();
          const state = api.state();
          const active = document.querySelector('.screenBuffer[data-active="1"]');
          samples.push({
            label,
            measuredCols: measured.cols,
            cols: state.cols,
            cacheRenderCols: state.cacheRenderCols,
            generationKey: state.generationKey,
            committedHistoryFrontier: state.committedHistoryFrontier,
            readyHistoryBatches: state.readyHistoryBatches,
            readyRows: active.querySelectorAll(".readyHistoryRow").length,
            captureRows: active.querySelectorAll(".captureRenderRow").length,
            captureText: Array.from(active.querySelectorAll(".captureRenderRow")).map((row) => row.textContent).join("\\n"),
          });
        };
        visualScale = 2;
        window.visualViewport.dispatchEvent(new Event("resize"));
        sample("sync-resize");
        setTimeout(() => {
          window.visualViewport.dispatchEvent(new Event("scroll"));
          sample("intermediate-scroll");
        }, 45);
        setTimeout(() => {
          window.visualViewport.dispatchEvent(new Event("resize"));
          sample("late-resize");
        }, 110);
        setTimeout(() => {
          const failedSample = samples.some((entry) => Number(entry.measuredCols) !== ${narrowCols}
            || Number(entry.cols) !== Number(before.cols)
            || Number(entry.cacheRenderCols) !== Number(before.cacheRenderCols)
            || entry.generationKey !== before.generationKey
            || entry.committedHistoryFrontier !== before.committedHistoryFrontier
            || entry.readyHistoryBatches !== before.readyHistoryBatches
            || entry.readyRows !== beforeReadyRows || entry.captureRows <= 0
            || entry.captureText !== beforeCaptureText);
          result.facts = {
            staged: staged.status,
            committed: committed.status,
            initialCols: before.cols,
            initialCacheRenderCols: before.cacheRenderCols,
            visualViewportWidth: window.visualViewport.width,
            visualViewportScale: window.visualViewport.scale,
            fetchCalls,
            samples: samples.map((entry) => ({ ...entry, captureText: entry.captureText.slice(0, 120) })),
          };
          if (staged.status !== "READY" || committed.status !== "COMMITTED" || beforeReadyRows <= 0
              || Number(before.cols) !== ${narrowCols} || Number(before.cacheRenderCols) !== ${narrowCols}
              || fetchCalls !== 0 || failedSample) {
            result.failures.push("PINCH_RENDER_COLS_REFLOW");
          }
          finish();
        }, 180);
      } catch (error) {
        result.failures.push("PINCH_TRANSITION_ERROR:" + String(error && error.message || error));
        finish();
      }
    }, 140);
  } catch (error) {
    result.failures.push("PINCH_TRANSITION_ERROR:" + String(error && error.message || error));
    finish();
  }
})();
</script>`;
    pinchTransitionResult = runGeneratedChrome(
      narrowFixture.html,
      pinchTransitionProbe,
      "pinch-transition-preserves-render-cols",
      2200,
    );
  }

  let renderColsSwapOrderingResult = null;
  if (parityCase === "all" || parityCase === "ordering") {
    const orderingProbe = `
<script id="renderColsSwapOrderingData" type="application/octet-stream">${transitionFixtureData}</script>
<script>
(() => {
  const result = { failures: [], facts: {} };
  const finish = () => {
    document.getElementById("contractResult").textContent = btoa(unescape(encodeURIComponent(JSON.stringify(result))));
  };
  const snapshotBuffers = () => {
    const active = document.querySelector('.screenBuffer[data-active="1"]');
    const inactive = document.querySelector('.screenBuffer[data-active="0"]');
    const capture = (buffer) => buffer ? Array.from(buffer.querySelectorAll(".captureRenderRow")) : [];
    const ready = (buffer) => buffer ? Array.from(buffer.querySelectorAll(".readyHistoryRow")) : [];
    const activeCapture = capture(active);
    const inactiveCapture = capture(inactive);
    const activeReady = ready(active);
    const inactiveReady = ready(inactive);
    return {
      activeCaptureRows: activeCapture.length,
      activeCaptureText: activeCapture.map((row) => row.textContent).join("\\n"),
      activeReadyRows: activeReady.length,
      activeReadyKeys: activeReady.map((row) => row.dataset.renderRowKey || ""),
      inactiveCaptureRows: inactiveCapture.length,
      inactiveCaptureText: inactiveCapture.map((row) => row.textContent).join("\\n"),
      inactiveReadyRows: inactiveReady.length,
    };
  };
  try {
    const api = window.__mantisCaptureRenderer;
    const fixture = JSON.parse(atob(document.getElementById("renderColsSwapOrderingData").textContent.trim()));
    if (!api || !window.visualViewport) throw new Error("renderCols ordering fixture missing");
    window.requestAnimationFrame = undefined;
    let visualWidth = window.innerWidth;
    Object.defineProperty(window.visualViewport, "width", { configurable: true, get: () => visualWidth });
    Object.defineProperty(window.visualViewport, "scale", { configurable: true, get: () => 1 });
    api.stageRenderedFrame(
      (fixture.initial.liveFrame.rows || []).join("\\n"),
      (fixture.initial.liveFrame.rowsHtml || []).join("\\n"),
      fixture.initial.liveFrame.rowsHtml || [],
      fixture.initial.liveFrame.rowKeys || []
    );
    setTimeout(() => {
      try {
        const initialStaged = api.stageReadyHistoryBatch(fixture.initial.batches[0]);
        const initialCommitted = api.prependReadyHistoryAtBoundary("up", 0, 0, true);
        const before = snapshotBuffers();
        const beforeState = api.state();
        let releaseFetch;
        let fetchCalls = 0;
        window.fetch = () => {
          fetchCalls += 1;
          return new Promise((resolve) => { releaseFetch = resolve; });
        };
        visualWidth = ${narrowVisualWidth};
        const refreshPromise = api.refresh(false, "contract-render-cols-ordering");
        const pending = snapshotBuffers();
        const pendingState = api.state();
        if (typeof releaseFetch !== "function") throw new Error("refresh did not reach deferred frame fetch");
        releaseFetch({
          ok: true,
          json: async () => ({
            ok: true,
            windowId: "@777",
            title: "contract narrowed frame",
            requestedCols: fixture.narrowed.liveFrame.requestedCols,
            requestedRows: (fixture.narrowed.liveFrame.rows || []).length,
            rows: fixture.narrowed.liveFrame.rows || [],
            rowsHtml: fixture.narrowed.liveFrame.rowsHtml || [],
            rowKeys: fixture.narrowed.liveFrame.rowKeys || [],
          }),
        });
        setTimeout(() => {
          try {
            const prepared = snapshotBuffers();
            setTimeout(() => {
              try {
                const swapped = snapshotBuffers();
                const swappedState = api.state();
                const narrowStaged = api.stageReadyHistoryBatch(fixture.narrowed.batch);
                const narrowCommitted = api.prependReadyHistoryAtBoundary("up", 0, 0, true);
                const sameColumnBefore = snapshotBuffers();
                const sameColumnKeys = sameColumnBefore.activeReadyKeys.slice();
                api.stageRenderedFrame(
                  (fixture.narrowed.liveFrame.rows || []).join("\\n") + "\\nSAME-COLUMN-LIVE-FRAME",
                  (fixture.narrowed.liveFrame.rowsHtml || []).join("\\n") + "\\nSAME-COLUMN-LIVE-FRAME",
                  [...(fixture.narrowed.liveFrame.rowsHtml || []), "SAME-COLUMN-LIVE-FRAME"],
                  [...(fixture.narrowed.liveFrame.rowKeys || []), "@777:%777:4242:200000:same-column:0"]
                );
                const sameColumnPrepared = snapshotBuffers();
                setTimeout(() => {
                  const sameColumnSwapped = snapshotBuffers();
                  const sameColumnState = api.state();
                  const disappearedBeforePrepared = pending.activeReadyRows < before.activeReadyRows
                    && pending.inactiveCaptureRows <= 0;
                  const activeEverBlank = [before, pending, prepared, swapped, sameColumnBefore, sameColumnPrepared, sameColumnSwapped]
                    .some((entry) => entry.activeCaptureRows <= 0 || !entry.activeCaptureText.trim());
                  const sameColumnHistoryLost = sameColumnBefore.activeReadyRows <= 0
                    || sameColumnPrepared.activeReadyRows !== sameColumnBefore.activeReadyRows
                    || sameColumnSwapped.activeReadyRows !== sameColumnBefore.activeReadyRows
                    || sameColumnSwapped.activeReadyKeys.join("|") !== sameColumnKeys.join("|");
                  result.facts = {
                    initialStaged: initialStaged.status,
                    initialCommitted: initialCommitted.status,
                    fetchCalls,
                    before: { ...before, activeCaptureText: before.activeCaptureText.slice(0, 120), activeReadyKeys: before.activeReadyKeys.slice(0, 4) },
                    pending: { ...pending, activeCaptureText: pending.activeCaptureText.slice(0, 120), activeReadyKeys: pending.activeReadyKeys.slice(0, 4) },
                    pendingState: { cols: pendingState.cols, cacheRenderCols: pendingState.cacheRenderCols, committedHistoryFrontier: pendingState.committedHistoryFrontier },
                    prepared: { ...prepared, activeCaptureText: prepared.activeCaptureText.slice(0, 120), inactiveCaptureText: prepared.inactiveCaptureText.slice(0, 120), activeReadyKeys: prepared.activeReadyKeys.slice(0, 4) },
                    swapped: { ...swapped, activeCaptureText: swapped.activeCaptureText.slice(0, 120), inactiveCaptureText: swapped.inactiveCaptureText.slice(0, 120), activeReadyKeys: swapped.activeReadyKeys.slice(0, 4) },
                    swappedState: { cols: swappedState.cols, cacheRenderCols: swappedState.cacheRenderCols, committedHistoryFrontier: swappedState.committedHistoryFrontier },
                    narrowStaged: narrowStaged.status,
                    narrowCommitted: narrowCommitted.status,
                    sameColumnBefore: { activeCaptureRows: sameColumnBefore.activeCaptureRows, activeReadyRows: sameColumnBefore.activeReadyRows },
                    sameColumnPrepared: { activeCaptureRows: sameColumnPrepared.activeCaptureRows, activeReadyRows: sameColumnPrepared.activeReadyRows, inactiveCaptureRows: sameColumnPrepared.inactiveCaptureRows },
                    sameColumnSwapped: { activeCaptureRows: sameColumnSwapped.activeCaptureRows, activeReadyRows: sameColumnSwapped.activeReadyRows },
                    sameColumnState: { cols: sameColumnState.cols, cacheRenderCols: sameColumnState.cacheRenderCols, committedHistoryFrontier: sameColumnState.committedHistoryFrontier },
                    disappearedBeforePrepared,
                    activeEverBlank,
                    sameColumnHistoryLost,
                  };
                  if (initialStaged.status !== "READY" || initialCommitted.status !== "COMMITTED"
                      || before.activeCaptureRows <= 0 || before.activeReadyRows <= 0 || fetchCalls !== 1
                      || disappearedBeforePrepared || activeEverBlank
                      || prepared.inactiveCaptureRows <= 0 || swapped.activeCaptureRows <= 0
                      || Number(swappedState.cols) !== ${narrowCols} || Number(swappedState.cacheRenderCols) !== ${narrowCols}
                      || narrowStaged.status !== "READY" || narrowCommitted.status !== "COMMITTED"
                      || sameColumnHistoryLost || Number(sameColumnState.cols) !== ${narrowCols}
                      || Number(sameColumnState.cacheRenderCols) !== ${narrowCols}) {
                    result.failures.push("RENDER_COLS_SWAP_ORDER");
                  }
                  finish();
                }, 40);
              } catch (error) {
                result.failures.push("RENDER_COLS_SWAP_ORDER_ERROR:" + String(error && error.message || error));
                finish();
              }
            }, 40);
          } catch (error) {
            result.failures.push("RENDER_COLS_SWAP_ORDER_ERROR:" + String(error && error.message || error));
            finish();
          }
        }, 0);
        Promise.resolve(refreshPromise).catch(() => false);
      } catch (error) {
        result.failures.push("RENDER_COLS_SWAP_ORDER_ERROR:" + String(error && error.message || error));
        finish();
      }
    }, 180);
  } catch (error) {
    result.failures.push("RENDER_COLS_SWAP_ORDER_ERROR:" + String(error && error.message || error));
    finish();
  }
})();
</script>`;
    renderColsSwapOrderingResult = runGeneratedChrome(
      fixture.html,
      orderingProbe,
      "render-cols-swap-ordering",
      2600,
    );
  }

  let staleInflightWidthResult = null;
  let staleInflightWidthPrecommitResult = null;
  if (parityCase === "all" || parityCase === "stale-inflight-width"
      || parityCase === "stale-inflight-width-post-commit"
      || parityCase === "stale-inflight-width-pre-commit") {
    const staleInflightWidthProbeFor = (pollProbeMode) => `
<script id="staleInflightWidthData" type="application/octet-stream">${transitionFixtureData}</script>
<script>
(() => {
  const result = { failures: [], facts: {} };
  const pollProbeMode = ${JSON.stringify(pollProbeMode)};
  let finished = false;
  const finish = () => {
    if (finished) return;
    finished = true;
    document.getElementById("contractResult").textContent = btoa(unescape(encodeURIComponent(JSON.stringify(result))));
  };
  const selectionRight = (row) => {
    if (!row || !row.textContent) return 0;
    const range = document.createRange();
    range.selectNodeContents(row);
    return Number(range.getBoundingClientRect().right) || 0;
  };
  const snapshot = (api) => {
    const active = document.querySelector('.screenBuffer[data-active="1"]');
    const liveRows = active ? Array.from(active.querySelectorAll(".captureRenderRow")) : [];
    const readyRows = active ? Array.from(active.querySelectorAll(".readyHistoryRow")) : [];
    const state = api.state();
    const admission = state && state.frameAdmission && typeof state.frameAdmission === "object"
      ? state.frameAdmission
      : {};
    return {
      activeId: active ? active.id : "",
      activeText: active ? active.textContent : "",
      activeTextLength: active ? active.textContent.length : 0,
      liveRows: liveRows.length,
      readyRows: readyRows.length,
      liveSelectionRight: liveRows.reduce((right, row) => Math.max(right, selectionRight(row)), 0),
      readySelectionRight: readyRows.reduce((right, row) => Math.max(right, selectionRight(row)), 0),
      cols: Number(state && state.cols),
      cacheRenderCols: Number(state && state.cacheRenderCols),
      frameSwapGeneration: Number(state && state.frameSwapGeneration),
      rightOverflowPx: Number(state && state.rightOverflowPx),
      admission,
      admissionDisposition: String(admission.disposition || state && state.frameAdmissionDisposition || ""),
    };
  };
  const publicSnapshot = (entry) => ({
    activeId: entry.activeId,
    activeTextLength: entry.activeTextLength,
    liveRows: entry.liveRows,
    readyRows: entry.readyRows,
    liveSelectionRight: entry.liveSelectionRight,
    readySelectionRight: entry.readySelectionRight,
    cols: entry.cols,
    cacheRenderCols: entry.cacheRenderCols,
    frameSwapGeneration: entry.frameSwapGeneration,
    rightOverflowPx: entry.rightOverflowPx,
    admission: entry.admission,
    admissionDisposition: entry.admissionDisposition,
  });
  try {
    const api = window.__mantisCaptureRenderer;
    const fixture = JSON.parse(atob(document.getElementById("staleInflightWidthData").textContent.trim()));
    if (!api || !window.visualViewport || !fixture.initial || !fixture.narrowed) {
      throw new Error("stale in-flight width fixture missing");
    }
    window.requestAnimationFrame = undefined;
    let visualWidth = ${narrowVisualWidth};
    Object.defineProperty(window.visualViewport, "width", { configurable: true, get: () => visualWidth });
    Object.defineProperty(window.visualViewport, "scale", { configurable: true, get: () => 1 });
    api.stageRenderedFrame(
      (fixture.narrowed.liveFrame.rows || []).join("\\n"),
      (fixture.narrowed.liveFrame.rowsHtml || []).join("\\n"),
      fixture.narrowed.liveFrame.rowsHtml || [],
      fixture.narrowed.liveFrame.rowKeys || []
    );
    setTimeout(() => {
      try {
        const initialStaged = api.stageReadyHistoryBatch(fixture.narrowed.batch);
        const initialCommitted = api.prependReadyHistoryAtBoundary("up", 0, 0, true);
        const before = snapshot(api);
        if (initialStaged.status !== "READY" || initialCommitted.status !== "COMMITTED"
            || before.liveRows <= 0 || before.readyRows <= 0 || !before.activeText.trim()
            || Number(before.cols) !== ${narrowCols} || Number(before.cacheRenderCols) !== ${narrowCols}) {
          result.failures.push("STALE_INFLIGHT_BASELINE_NOT_READY");
          result.facts = { initialStaged: initialStaged.status, initialCommitted: initialCommitted.status, before: publicSnapshot(before) };
          finish();
          return;
        }

        let fetchCalls = 0;
        let releaseWideFetch = null;
        let releaseNarrowFetch = null;
        let armedFetchOrigin = "";
        let explicitPollProbeAttempts = 0;
        const requests = [];
        const replacementVisibleFrom = (entry) => entry.activeId !== before.activeId
          && Number(entry.cols) === ${narrowCols}
          && Number(entry.cacheRenderCols) === ${narrowCols};
        const fetchStartState = (entry) => {
          const admissionRequest = entry.admission && entry.admission.request
            && typeof entry.admission.request === "object" ? entry.admission.request : null;
          const admissionCommit = entry.admission && entry.admission.commit
            && typeof entry.admission.commit === "object" ? entry.admission.commit : null;
          return {
            activeBuffer: entry.activeId,
            frameSwapGeneration: entry.frameSwapGeneration,
            cols: entry.cols,
            cacheRenderCols: entry.cacheRenderCols,
            admissionDisposition: entry.admissionDisposition,
            admissionRequestId: Number(entry.admission && entry.admission.requestId || 0),
            admissionRequest,
            admissionCommit,
            replacementVisible: replacementVisibleFrom(entry),
            liveRows: entry.liveRows,
            readyRows: entry.readyRows,
            activeTextLength: entry.activeTextLength,
          };
        };
        const responseFor = (frame, title) => ({
          ok: true,
          statusText: "OK",
          json: async () => ({
            ok: true,
            windowId: "@777",
            title,
            requestedCols: frame.requestedCols,
            requestedRows: (frame.rows || []).length,
            rows: frame.rows || [],
            rowsHtml: frame.rowsHtml || [],
            rowKeys: frame.rowKeys || [],
          }),
        });
        window.fetch = (input) => {
          fetchCalls += 1;
          const requestUrl = new URL(String(input), location.href);
          const startSnapshot = snapshot(api);
          const startState = fetchStartState(startSnapshot);
          let origin = armedFetchOrigin;
          if (fetchCalls === 1) origin = "stale-width-primary";
          else if (fetchCalls === 2 && startState.admissionDisposition === "stale-geometry-rejected") {
            origin = "response-triggered-current-width-replacement";
          } else if (!origin) {
            origin = startState.replacementVisible
              ? "renderer-background-poll-post-commit"
              : "renderer-unclassified-fetch-pre-commit";
          }
          const request = {
            ordinal: fetchCalls,
            origin,
            cols: Number(requestUrl.searchParams.get("cols")),
            rows: Number(requestUrl.searchParams.get("rows")),
            windowId: requestUrl.searchParams.get("windowId") || "",
            startState,
          };
          requests.push(request);
          return new Promise((resolve) => {
            if (fetchCalls === 1) {
              releaseWideFetch = () => resolve(responseFor(fixture.initial.liveFrame, "contract stale wide frame"));
            } else if (fetchCalls === 2) {
              releaseNarrowFetch = () => resolve(responseFor(fixture.narrowed.liveFrame, "contract current narrow frame"));
            } else {
              // WHY: explicit poll probes classify the fetch at its start boundary.
              // Keep their responses held so the guard observes replacement commit
              // state without allowing the probe itself to advance visible generation.
            }
          });
        };

        visualWidth = window.innerWidth;
        armedFetchOrigin = "stale-width-primary";
        const refreshPromise = api.refresh(false, "contract-stale-inflight-width");
        armedFetchOrigin = "";
        if (typeof releaseWideFetch !== "function" || fetchCalls !== 1 || requests[0].cols !== ${measuredCols}) {
          result.failures.push("STALE_INFLIGHT_WIDE_REQUEST_NOT_CAPTURED");
          result.facts = { fetchCalls, requests, before: publicSnapshot(before), visualWidth, innerWidth: window.innerWidth };
          finish();
          return;
        }
        visualWidth = ${narrowVisualWidth};
        releaseWideFetch();
        Promise.resolve(refreshPromise).catch(() => false);

        const staleStartedAt = Date.now();
        const staleMonitor = setInterval(() => {
          if (finished) { clearInterval(staleMonitor); return; }
          const current = snapshot(api);
          const activeGenerationChanged = current.activeId !== before.activeId
            || (Number.isFinite(current.frameSwapGeneration) && Number.isFinite(before.frameSwapGeneration)
              && current.frameSwapGeneration !== before.frameSwapGeneration);
          if (fetchCalls >= 2 && typeof releaseNarrowFetch === "function") {
            clearInterval(staleMonitor);
            const rejected = current;
            const requestTelemetry = rejected.admission.request && typeof rejected.admission.request === "object"
              ? rejected.admission.request
              : {};
            const commitTelemetry = rejected.admission.commit && typeof rejected.admission.commit === "object"
              ? rejected.admission.commit
              : {};
            result.facts = {
              initialStaged: initialStaged.status,
              initialCommitted: initialCommitted.status,
              visualWidth,
              viewportReserveRight: visualWidth - 6,
              requests,
              before: publicSnapshot(before),
              rejected: publicSnapshot(rejected),
            };
            if (activeGenerationChanged || rejected.activeText !== before.activeText
                || rejected.liveRows <= 0 || rejected.readyRows !== before.readyRows) {
              result.failures.push("STALE_INFLIGHT_GEOMETRY_COMMITTED");
              finish();
              return;
            }
            if (rejected.admissionDisposition !== "stale-geometry-rejected"
                || Number(requestTelemetry.cols) !== ${measuredCols}
                || Number(commitTelemetry.cols) !== ${narrowCols}
                || Number(requestTelemetry.visualWidth) <= Number(commitTelemetry.visualWidth)
                || requests[1].cols !== ${narrowCols}
                || requests[1].origin !== "response-triggered-current-width-replacement"
                || requests[1].startState.activeBuffer !== before.activeId
                || requests[1].startState.frameSwapGeneration !== before.frameSwapGeneration
                || requests[1].startState.replacementVisible
                || requests[1].startState.admissionDisposition !== "stale-geometry-rejected"
                || requests[1].startState.admissionRequestId <= 0) {
              result.failures.push("STALE_INFLIGHT_REJECTION_IDENTITY_MISSING");
              finish();
              return;
            }
            releaseNarrowFetch();
            const replacementStartedAt = Date.now();
            const replacementMonitor = setInterval(() => {
              if (finished) { clearInterval(replacementMonitor); return; }
              const committed = snapshot(api);
              const replacementVisible = replacementVisibleFrom(committed);
              const preCommitFetch = requests.slice(2).find((request) => !request.startState.replacementVisible);
              if (preCommitFetch) {
                clearInterval(replacementMonitor);
                result.facts = {
                  ...result.facts,
                  pollProbeMode,
                  fetchCalls,
                  requests,
                  committed: publicSnapshot(committed),
                  preCommitFetch,
                };
                result.failures.push("STALE_INFLIGHT_POLL_STARTED_BEFORE_REPLACEMENT_VISIBLE");
                finish();
                return;
              }
              if (pollProbeMode === "pre-commit" && explicitPollProbeAttempts === 0
                  && !replacementVisible
                  && (committed.admissionDisposition === "current-geometry-admitted"
                    || committed.admissionDisposition === "current-geometry-staged")) {
                explicitPollProbeAttempts += 1;
                const beforePollFetchCalls = fetchCalls;
                armedFetchOrigin = "explicit-pre-commit-poll";
                const preCommitPollPromise = api.refresh(false, "poll");
                armedFetchOrigin = "";
                Promise.resolve(preCommitPollPromise).catch(() => false);
                if (fetchCalls > beforePollFetchCalls) {
                  const probeRequest = requests[requests.length - 1];
                  clearInterval(replacementMonitor);
                  result.facts = {
                    ...result.facts,
                    pollProbeMode,
                    fetchCalls,
                    requests,
                    committed: publicSnapshot(committed),
                    preCommitFetch: probeRequest,
                  };
                  if (!probeRequest.startState.replacementVisible) {
                    result.failures.push("STALE_INFLIGHT_POLL_STARTED_BEFORE_REPLACEMENT_VISIBLE");
                  } else {
                    result.failures.push("STALE_INFLIGHT_PRECOMMIT_PROBE_PHASE_DRIFT");
                  }
                  finish();
                  return;
                }
              }
              if (replacementVisible) {
                // DOM Range bounds inherit fractional glyph metrics and can land
                // one physical pixel inside the 6px CSS padding boundary. Keep a
                // 1.25px raster tolerance; the old stale frame exceeds this bound
                // by more than 140px, so the regression signal remains decisive.
                const reserveRight = visualWidth - 6 + 1.25;
                const generationAdvancedOnce = !Number.isFinite(before.frameSwapGeneration)
                  || !Number.isFinite(committed.frameSwapGeneration)
                  || committed.frameSwapGeneration === before.frameSwapGeneration + 1;
                const responseReplacementRequests = requests.filter(
                  (request) => request.origin === "response-triggered-current-width-replacement",
                );
                const fetchOriginsComplete = requests.every((request) => request.origin
                  && request.startState && request.startState.activeBuffer
                  && Number.isFinite(Number(request.startState.frameSwapGeneration))
                  && typeof request.startState.admissionDisposition === "string"
                  && typeof request.startState.replacementVisible === "boolean");
                result.facts = {
                  ...result.facts,
                  pollProbeMode,
                  fetchCalls,
                  requests,
                  committed: publicSnapshot(committed),
                  generationAdvancedOnce,
                  responseReplacementRequests: responseReplacementRequests.length,
                  fetchOriginsComplete,
                };
                if (responseReplacementRequests.length !== 1 || !fetchOriginsComplete
                    || !generationAdvancedOnce || !committed.activeText.trim()
                    || committed.liveRows <= 0 || committed.readyRows !== before.readyRows
                    || committed.liveSelectionRight > reserveRight
                    || committed.readySelectionRight > reserveRight
                    || committed.rightOverflowPx > 0.75) {
                  result.failures.push("STALE_INFLIGHT_CURRENT_WIDTH_REPLACEMENT_INVALID");
                  clearInterval(replacementMonitor);
                  finish();
                  return;
                }
                if (pollProbeMode === "pre-commit") {
                  clearInterval(replacementMonitor);
                  if (explicitPollProbeAttempts !== 1) {
                    result.failures.push("STALE_INFLIGHT_PRECOMMIT_PROBE_WINDOW_MISSING");
                  } else {
                    result.facts.preCommitPollCoalesced = true;
                  }
                  finish();
                  return;
                }
                if (explicitPollProbeAttempts === 0) {
                  explicitPollProbeAttempts += 1;
                  // WHY: under real scheduler contention the renderer's already
                  // armed normal-poll timer can legally win just after activation
                  // and before this synthetic probe. That actual post-commit fetch
                  // is the stronger control; accept it only with the same visible
                  // active-buffer telemetry. Otherwise force one explicit fetch.
                  let postCommitRequest = requests.slice(2).find(
                    (request) => request.origin === "renderer-background-poll-post-commit"
                      && request.startState && request.startState.replacementVisible,
                  ) || null;
                  if (!postCommitRequest) {
                    const beforePollFetchCalls = fetchCalls;
                    armedFetchOrigin = "explicit-post-commit-poll";
                    const postCommitPollPromise = api.refresh(false, "poll");
                    armedFetchOrigin = "";
                    Promise.resolve(postCommitPollPromise).catch(() => false);
                    postCommitRequest = requests[requests.length - 1];
                    if (fetchCalls !== beforePollFetchCalls + 1
                        || !postCommitRequest
                        || postCommitRequest.origin !== "explicit-post-commit-poll") {
                      result.failures.push("STALE_INFLIGHT_POSTCOMMIT_POLL_NOT_CAPTURED");
                      clearInterval(replacementMonitor);
                      finish();
                      return;
                    }
                  }
                  result.facts = {
                    ...result.facts,
                    fetchCalls,
                    requests,
                    postCommitRequest,
                  };
                  if (!postCommitRequest
                      || !["explicit-post-commit-poll", "renderer-background-poll-post-commit"].includes(postCommitRequest.origin)) {
                    result.failures.push("STALE_INFLIGHT_POSTCOMMIT_POLL_NOT_CAPTURED");
                    clearInterval(replacementMonitor);
                    finish();
                    return;
                  }
                  if (!postCommitRequest.startState.replacementVisible
                      || postCommitRequest.startState.activeBuffer !== committed.activeId
                      || postCommitRequest.startState.frameSwapGeneration !== committed.frameSwapGeneration) {
                    result.failures.push("STALE_INFLIGHT_POLL_STARTED_BEFORE_REPLACEMENT_VISIBLE");
                    clearInterval(replacementMonitor);
                    finish();
                    return;
                  }
                }
                clearInterval(replacementMonitor);
                finish();
                return;
              }
              if (Date.now() - replacementStartedAt > 360) {
                clearInterval(replacementMonitor);
                result.facts = { ...result.facts, fetchCalls, requests, committed: publicSnapshot(committed) };
                result.failures.push("STALE_INFLIGHT_CURRENT_WIDTH_REPLACEMENT_MISSING");
                finish();
              }
            }, 6);
            return;
          }
          // Wait for the prepared wide buffer to become the visible buffer. Geometry
          // and READY identity change slightly earlier inside stageRenderedFrame;
          // sampling then would measure the retained narrow DOM rather than the stale
          // wide response whose visible right-edge crop this case protects.
          if (current.activeId !== before.activeId) {
            clearInterval(staleMonitor);
            result.facts = {
              initialStaged: initialStaged.status,
              initialCommitted: initialCommitted.status,
              visualWidth,
              viewportReserveRight: visualWidth - 6,
              fetchCalls,
              requests,
              before: publicSnapshot(before),
              staleCommit: publicSnapshot(current),
            };
            if (current.liveSelectionRight > visualWidth - 6 + 1.25 || current.rightOverflowPx > 1.25) {
              result.failures.push("RIGHT_EDGE_OVERFLOW_BEFORE_REPLACEMENT");
            } else {
              result.failures.push("STALE_INFLIGHT_GEOMETRY_COMMITTED");
            }
            finish();
            return;
          }
          if (Date.now() - staleStartedAt > 360) {
            clearInterval(staleMonitor);
            result.facts = { fetchCalls, requests, before: publicSnapshot(before), current: publicSnapshot(current) };
            result.failures.push("STALE_INFLIGHT_NO_REJECTION_OR_COMMIT");
            finish();
          }
        }, 6);
      } catch (error) {
        result.failures.push("STALE_INFLIGHT_WIDTH_ERROR:" + String(error && error.message || error));
        finish();
      }
    }, 90);
  } catch (error) {
    result.failures.push("STALE_INFLIGHT_WIDTH_ERROR:" + String(error && error.message || error));
    finish();
  }
})();
</script>`;
    if (parityCase === "all" || parityCase === "stale-inflight-width"
        || parityCase === "stale-inflight-width-post-commit") {
      staleInflightWidthResult = runGeneratedChrome(
        fixture.html,
        staleInflightWidthProbeFor("post-commit"),
        "stale-inflight-width-post-commit",
        2600,
      );
    }
    if (parityCase === "all" || parityCase === "stale-inflight-width"
        || parityCase === "stale-inflight-width-pre-commit") {
      staleInflightWidthPrecommitResult = runGeneratedChrome(
        fixture.html,
        staleInflightWidthProbeFor("pre-commit"),
        "stale-inflight-width-pre-commit",
        2600,
      );
    }
  }

  let visibleCommitLeaseRecoveryResult = null;
  if (parityCase === "all" || parityCase === "visible-commit-lease-recovery") {
    const visibleCommitLeaseRecoveryProbe = `
<script id="visibleCommitLeaseRecoveryData" type="application/octet-stream">${transitionFixtureData}</script>
<script>
(() => {
  const result = { failures: [], facts: {} };
  let finished = false;
  const finish = () => {
    if (finished) return;
    finished = true;
    document.getElementById("contractResult").textContent = btoa(unescape(encodeURIComponent(JSON.stringify(result))));
  };
  const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
  const activeSnapshot = () => {
    const active = document.querySelector('.screenBuffer[data-active="1"]');
    return { id: active ? active.id : "", text: active ? active.textContent : "" };
  };
  try {
    const api = window.__mantisCaptureRenderer;
    const fixture = JSON.parse(atob(document.getElementById("visibleCommitLeaseRecoveryData").textContent.trim()));
    if (!api || !fixture.initial || !fixture.initial.liveFrame) {
      throw new Error("visible commit lease recovery fixture missing");
    }
    const rafQueue = [];
    window.requestAnimationFrame = (callback) => {
      rafQueue.push(callback);
      return rafQueue.length;
    };
    const flushRaf = () => {
      let callbacks = 0;
      while (rafQueue.length && callbacks < 16) {
        const callback = rafQueue.shift();
        callback(Date.now());
        callbacks += 1;
      }
      if (rafQueue.length) throw new Error("visible commit lease rAF queue did not settle");
      return callbacks;
    };
    const baselineRows = (fixture.initial.liveFrame.rows || []).slice(0, 12).map((row) => String(row));
    const baselineFrame = {
      ok: true,
      windowId: "@777",
      title: "visible commit lease baseline",
      requestedCols: Number(fixture.initial.liveFrame.requestedCols),
      requestedRows: baselineRows.length,
      rows: baselineRows,
      rowKeys: (fixture.initial.liveFrame.rowKeys || []).slice(0, baselineRows.length),
    };
    const responseFor = (payload) => ({
      ok: true,
      statusText: "OK",
      json: async () => payload,
    });
    const queuedResponses = [baselineFrame, baselineFrame];
    let fetchCalls = 0;
    window.fetch = () => {
      fetchCalls += 1;
      if (queuedResponses.length) return Promise.resolve(responseFor(queuedResponses.shift()));
      return new Promise(() => {});
    };

    setTimeout(async () => {
      try {
        await api.refresh(false, "contract-visible-commit-baseline");
        const baselineRafCallbacks = flushRaf();
        const baselineState = api.state();
        const baselineActive = activeSnapshot();
        if (!baselineActive.text.trim() || baselineRafCallbacks < 2
            || Number(baselineState.visibleCommitLeaseToken) !== 0) {
          result.failures.push("VISIBLE_COMMIT_LEASE_BASELINE_NOT_COMMITTED");
          result.facts = { baselineRafCallbacks, baselineState, baselineActive };
          finish();
          return;
        }

        const sequenceBeforeNoop = Number(baselineState.visibleCommitLeaseSequence);
        await api.refresh(false, "contract-visible-commit-same-text");
        const sameTextState = api.state();
        if (Number(sameTextState.visibleCommitLeaseToken) !== 0
            || Number(sameTextState.visibleCommitLeaseSequence) !== sequenceBeforeNoop
            || rafQueue.length !== 0) {
          result.failures.push("VISIBLE_COMMIT_LEASE_SAME_TEXT_STARTED_LEASE");
          result.facts = { sequenceBeforeNoop, sameTextState, queuedRaf: rafQueue.length };
          finish();
          return;
        }

        const beforeSuspend = activeSnapshot();
        const suspendedText = "VISIBLE-COMMIT-SUSPENDED-ROW";
        if (!api.stageRenderedFrame(suspendedText, suspendedText, null, [])) {
          throw new Error("direct suspended stage did not prepare");
        }
        const suspendedState = api.state();
        const suspendedToken = Number(suspendedState.visibleCommitLeaseToken);
        const fetchCallsBeforeDeferredPoll = fetchCalls;
        // WHY: this forced poll occupies a browser-legal timer task between
        // prepared and active rAF phases. It is a scheduler falsifier, not a
        // simulation of any particular poll constant or installed-device rate.
        await api.refresh(false, "poll");
        const deferredState = api.state();
        if (!(suspendedToken > 0)
            || Number(deferredState.visibleCommitLeaseToken) !== suspendedToken
            || deferredState.normalPollDeferredUntilVisibleCommit !== true
            || fetchCalls !== fetchCallsBeforeDeferredPoll) {
          result.failures.push("VISIBLE_COMMIT_LEASE_PASSIVE_POLL_NOT_DEFERRED");
          result.facts = { suspendedState, deferredState, fetchCalls, fetchCallsBeforeDeferredPoll };
          finish();
          return;
        }
        await delay(520);
        const heldState = api.state();
        const heldActive = activeSnapshot();
        if (Number(heldState.visibleCommitLeaseToken) !== suspendedToken
            || heldState.normalPollDeferredUntilVisibleCommit !== true
            || heldActive.id !== beforeSuspend.id || heldActive.text !== beforeSuspend.text
            || fetchCalls !== fetchCallsBeforeDeferredPoll) {
          result.failures.push("VISIBLE_COMMIT_LEASE_TIMEOUT_OR_HIDDEN_UNLOCK");
          result.facts = { suspendedToken, heldState, beforeSuspend, heldActive, fetchCalls, fetchCallsBeforeDeferredPoll };
          finish();
          return;
        }
        // WHY: the recovery poll is deliberately slower than the live stream.
        // Capture its one-shot timer at the visual-commit boundary instead of
        // sleeping for a stale hard-coded poll interval.
        const nativeSetTimeout = window.setTimeout.bind(window);
        const deferredPollTimers = [];
        window.setTimeout = (callback, delayMs, ...args) => {
          const delayNumber = Number(delayMs);
          if (delayNumber >= ${normalPollMinDelayMs}
              && delayNumber <= ${normalPollMaxDelayMs}) {
            deferredPollTimers.push({ callback, args, delayMs: delayNumber });
            return 2147483000 + deferredPollTimers.length;
          }
          return nativeSetTimeout(callback, delayMs, ...args);
        };
        let suspendedRafCallbacks = 0;
        try {
          suspendedRafCallbacks = flushRaf();
        } finally {
          window.setTimeout = nativeSetTimeout;
        }
        const resumedState = api.state();
        const resumedActive = activeSnapshot();
        if (Number(resumedState.visibleCommitLeaseToken) !== 0
            || resumedState.normalPollDeferredUntilVisibleCommit !== false
            || !resumedActive.text.includes(suspendedText)
            || suspendedRafCallbacks !== 1
            || deferredPollTimers.length !== 1
            || fetchCalls !== fetchCallsBeforeDeferredPoll) {
          result.failures.push("VISIBLE_COMMIT_LEASE_RESUME_DID_NOT_COMMIT_AND_RELEASE");
          result.facts = {
            suspendedRafCallbacks,
            deferredPollTimerCount: deferredPollTimers.length,
            deferredPollTimerDelays: deferredPollTimers.map((timer) => timer.delayMs),
            resumedState,
            resumedActive,
          };
          finish();
          return;
        }
        const deferredPollTimer = deferredPollTimers[0];
        deferredPollTimer.callback(...deferredPollTimer.args);
        await delay(30);
        const fetchCallsAfterRearm = fetchCalls;
        if (fetchCallsAfterRearm !== fetchCallsBeforeDeferredPoll + 1) {
          result.failures.push("VISIBLE_COMMIT_LEASE_DEFERRED_POLL_NOT_REARMED_ONCE");
          result.facts = {
            fetchCallsBeforeDeferredPoll,
            fetchCallsAfterRearm,
            deferredPollTimerDelayMs: deferredPollTimer.delayMs,
            resumedState,
          };
          finish();
          return;
        }

        const beforeReject = activeSnapshot();
        const measured = api.measure();
        const stateBeforeReject = api.state();
        const staleRequest = {
          pageInstanceId: stateBeforeReject.pageInstanceId,
          requestId: 9001,
          epoch: -1,
          cols: Number(measured.cols),
          _signature: "forced-stale-stage-identity",
        };
        const rejected = api.stageRenderedFrame(
          "VISIBLE-COMMIT-REJECTED-ROW",
          "VISIBLE-COMMIT-REJECTED-ROW",
          null,
          [],
          measured,
          { request: staleRequest, commit: staleRequest, responseWindowId: "@777", responseCols: measured.cols },
        );
        const afterReject = api.state();
        const afterRejectActive = activeSnapshot();
        if (rejected !== false || Number(afterReject.visibleCommitLeaseToken) !== 0
            || afterRejectActive.id !== beforeReject.id || afterRejectActive.text !== beforeReject.text) {
          result.failures.push("VISIBLE_COMMIT_LEASE_STAGE_REJECTION_STUCK_OR_REPAINTED");
          result.facts = { rejected, afterReject, beforeReject, afterRejectActive };
          finish();
          return;
        }

        const beforeException = activeSnapshot();
        const throwingRow = { toString: () => { throw new Error("forced-visible-commit-stage-exception"); } };
        const exceptionResult = api.stageRenderedFrame(
          "VISIBLE-COMMIT-EXCEPTION-ROW",
          "VISIBLE-COMMIT-EXCEPTION-ROW",
          [throwingRow],
          [],
        );
        const afterException = api.state();
        const afterExceptionActive = activeSnapshot();
        if (exceptionResult !== false || Number(afterException.visibleCommitLeaseToken) !== 0
            || afterExceptionActive.id !== beforeException.id || afterExceptionActive.text !== beforeException.text) {
          result.failures.push("VISIBLE_COMMIT_LEASE_EXCEPTION_STUCK_OR_REPAINTED");
          result.facts = { exceptionResult, afterException, beforeException, afterExceptionActive };
          finish();
          return;
        }

        const beforeSupersession = activeSnapshot();
        if (!api.stageRenderedFrame("VISIBLE-COMMIT-OLD-ROW", "VISIBLE-COMMIT-OLD-ROW", null, [])) {
          throw new Error("old direct stage did not prepare");
        }
        const oldToken = Number(api.state().visibleCommitLeaseToken);
        if (!api.stageRenderedFrame("VISIBLE-COMMIT-NEW-ROW", "VISIBLE-COMMIT-NEW-ROW", null, [])) {
          throw new Error("new direct stage did not prepare");
        }
        const newToken = Number(api.state().visibleCommitLeaseToken);
        const oldSwap = rafQueue.shift();
        if (typeof oldSwap !== "function") throw new Error("old superseded swap missing");
        oldSwap(Date.now());
        const afterOldSwap = api.state();
        const afterOldSwapActive = activeSnapshot();
        if (!(newToken > oldToken) || Number(afterOldSwap.visibleCommitLeaseToken) !== newToken
            || afterOldSwapActive.id !== beforeSupersession.id
            || afterOldSwapActive.text !== beforeSupersession.text) {
          result.failures.push("VISIBLE_COMMIT_LEASE_OLD_TOKEN_RELEASED_OR_COMMITTED_NEWER_WORK");
          result.facts = { oldToken, newToken, afterOldSwap, beforeSupersession, afterOldSwapActive };
          finish();
          return;
        }
        const newSwap = rafQueue.shift();
        if (typeof newSwap !== "function") throw new Error("new superseding swap missing");
        newSwap(Date.now());
        const finalState = api.state();
        const finalActive = activeSnapshot();
        if (Number(finalState.visibleCommitLeaseToken) !== 0 || rafQueue.length !== 0
            || !finalActive.text.includes("VISIBLE-COMMIT-NEW-ROW")
            || finalActive.text.includes("VISIBLE-COMMIT-OLD-ROW")) {
          result.failures.push("VISIBLE_COMMIT_LEASE_NEW_TOKEN_DID_NOT_COMMIT_AND_RELEASE");
          result.facts = { oldToken, newToken, finalState, finalActive, queuedRaf: rafQueue.length };
          finish();
          return;
        }

        result.facts = {
          baselineRafCallbacks,
          sequenceBeforeNoop,
          sameTextSequence: Number(sameTextState.visibleCommitLeaseSequence),
          suspendedToken,
          suspendedRafCallbacks,
          fetchCallsBeforeDeferredPoll,
          fetchCallsAfterRearm,
          deferredPollTimerDelayMs: deferredPollTimer.delayMs,
          rejectionReleased: Number(afterReject.visibleCommitLeaseToken) === 0,
          exceptionReleased: Number(afterException.visibleCommitLeaseToken) === 0,
          oldToken,
          newToken,
          oldCompletionPreservedNewToken: Number(afterOldSwap.visibleCommitLeaseToken) === newToken,
          finalActiveText: finalActive.text,
        };
        finish();
      } catch (error) {
        result.failures.push("VISIBLE_COMMIT_LEASE_RECOVERY_ERROR:" + String(error && error.message || error));
        finish();
      }
    }, 90);
  } catch (error) {
    result.failures.push("VISIBLE_COMMIT_LEASE_RECOVERY_ERROR:" + String(error && error.message || error));
    finish();
  }
})();
</script>`;
    visibleCommitLeaseRecoveryResult = runGeneratedChrome(
      fixture.html,
      visibleCommitLeaseRecoveryProbe,
      "visible-commit-lease-recovery",
      1800,
    );
  }

  let staleInflightAbaResult = null;
  if (parityCase === "all" || parityCase === "stale-inflight-aba") {
    const staleInflightAbaProbe = `
<script id="staleInflightAbaData" type="application/octet-stream">${transitionFixtureData}</script>
<script>
(() => {
  const result = { failures: [], facts: {} };
  let finished = false;
  const finish = () => {
    if (finished) return;
    finished = true;
    document.getElementById("contractResult").textContent = btoa(unescape(encodeURIComponent(JSON.stringify(result))));
  };
  const identityHash = (value) => {
    const text = String(value || "");
    let hash = 2166136261;
    for (let index = 0; index < text.length; index += 1) {
      hash ^= text.charCodeAt(index);
      hash = Math.imul(hash, 16777619);
    }
    return (hash >>> 0).toString(16).padStart(8, "0");
  };
  const snapshot = (api) => {
    const active = document.querySelector('.screenBuffer[data-active="1"]');
    const liveRows = active ? Array.from(active.querySelectorAll(".captureRenderRow")) : [];
    const readyRows = active ? Array.from(active.querySelectorAll(".readyHistoryRow")) : [];
    const state = api.state();
    const admission = state && state.frameAdmission && typeof state.frameAdmission === "object"
      ? state.frameAdmission
      : {};
    const activeText = active ? active.textContent : "";
    return {
      activeId: active ? active.id : "",
      activeText,
      activeTextLength: activeText.length,
      activeTextHash: identityHash(activeText),
      liveRows: liveRows.length,
      readyRows: readyRows.length,
      cols: Number(state && state.cols),
      cacheRenderCols: Number(state && state.cacheRenderCols),
      generationKey: String(state && state.generationKey || ""),
      readyHistoryBatches: Number(state && state.readyHistoryBatches),
      committedHistoryFrontier: Number(state && state.committedHistoryFrontier),
      committedHistoryCoveragePx: Number(state && state.committedHistoryCoveragePx),
      frameGeometryEpoch: Number(state && state.frameGeometryEpoch),
      frameSwapGeneration: Number(state && state.frameSwapGeneration),
      admission,
      admissionDisposition: String(admission.disposition || ""),
    };
  };
  const publicSnapshot = (entry) => ({
    activeId: entry.activeId,
    activeTextLength: entry.activeTextLength,
    activeTextHash: entry.activeTextHash,
    activeTextPrefix: entry.activeText.slice(0, 120),
    liveRows: entry.liveRows,
    readyRows: entry.readyRows,
    cols: entry.cols,
    cacheRenderCols: entry.cacheRenderCols,
    generationKey: entry.generationKey,
    readyHistoryBatches: entry.readyHistoryBatches,
    committedHistoryFrontier: entry.committedHistoryFrontier,
    committedHistoryCoveragePx: entry.committedHistoryCoveragePx,
    frameGeometryEpoch: entry.frameGeometryEpoch,
    frameSwapGeneration: entry.frameSwapGeneration,
    admission: entry.admission,
    admissionDisposition: entry.admissionDisposition,
  });
  const markedFrame = (frame, marker) => {
    const rows = Array.isArray(frame && frame.rows) ? frame.rows.slice() : [];
    if (!rows.length) rows.push(marker);
    rows[0] = marker;
    const rowsHtml = Array.isArray(frame && frame.rowsHtml) ? frame.rowsHtml.slice() : rows.slice();
    while (rowsHtml.length < rows.length) rowsHtml.push(rows[rowsHtml.length]);
    rowsHtml[0] = marker;
    const rowKeys = rows.map((unused, index) => "aba:" + marker + ":" + index);
    return Object.assign({}, frame || {}, { rows, rowsHtml, rowKeys });
  };
  const frameText = (frame) => (frame.rows || []).join("\\n");
  const frameRendered = (frame) => (frame.rowsHtml || frame.rows || []).join("\\n");
  const stageFrame = (api, frame, size, identity, target) => api.stageRenderedFrame(
    frameText(frame),
    frameRendered(frame),
    frame.rowsHtml || [],
    frame.rowKeys || [],
    size,
    { request: identity, responseWindowId: target, responseCols: size.cols }
  );
  const identityFor = (api, size, epoch, requestId) => {
    const state = api.state();
    const pageInstanceId = String(state && state.pageInstanceId || "");
    const target = String(state && state.windowId || "@777").trim();
    const targetHash = identityHash(target);
    const viewport = window.visualViewport;
    const visualWidth = Math.max(1, Math.floor(Number(viewport && viewport.width) || 1));
    const visualHeight = Math.max(1, Math.floor(Number(viewport && viewport.height) || window.innerHeight || 1));
    const visualOffsetTop = Math.max(0, Math.floor(Number(viewport && viewport.offsetTop) || 0));
    const visualBottom = Math.max(visualHeight, visualOffsetTop + visualHeight);
    const layoutWidth = Math.max(1, Math.floor(window.innerWidth || document.documentElement.clientWidth || visualWidth));
    const cols = Math.max(0, Math.floor(Number(size && size.cols) || 0));
    const rows = Math.max(0, Math.floor(Number(size && size.rows) || 0));
    const columnViewportWidth = Math.max(1, Math.floor(Number(size && size.columnViewportWidth) || visualWidth));
    const charWidthMilli = Math.max(0, Math.round((Number(size && size.charWidth) || 0) * 1000));
    const scaleMilli = Math.max(1, Math.round((Number(viewport && viewport.scale) || 1) * 1000));
    const mode = String(size && size.mode || "mobile");
    // WHY: the fixture must use the producer's complete geometry identity or
    // it manufactures a stale-frame rejection before exercising the ABA guard.
    const signature = [
      pageInstanceId, targetHash, mode, rows, cols, columnViewportWidth,
      visualWidth, visualHeight, visualOffsetTop, visualBottom, layoutWidth,
      charWidthMilli, scaleMilli,
    ].join("|");
    return {
      pageInstanceId,
      epoch,
      targetHash,
      targetPresent: !!target,
      mode,
      rows,
      cols,
      columnViewportWidth,
      visualWidth,
      visualHeight,
      visualOffsetTop,
      visualBottom,
      layoutWidth,
      charWidthMilli,
      scaleMilli,
      signatureHash: identityHash(signature),
      _signature: signature,
      requestId,
      target,
    };
  };
  const sameProtectedState = (left, right) => left.activeId === right.activeId
    && left.activeText === right.activeText
    && left.liveRows === right.liveRows
    && left.readyRows === right.readyRows
    && left.cols === right.cols
    && left.cacheRenderCols === right.cacheRenderCols
    && left.generationKey === right.generationKey
    && left.readyHistoryBatches === right.readyHistoryBatches
    && left.committedHistoryFrontier === right.committedHistoryFrontier
    && left.committedHistoryCoveragePx === right.committedHistoryCoveragePx
    && left.frameSwapGeneration === right.frameSwapGeneration;
  try {
    const api = window.__mantisCaptureRenderer;
    const fixture = JSON.parse(atob(document.getElementById("staleInflightAbaData").textContent.trim()));
    if (!api || !window.visualViewport || !fixture.narrowed) throw new Error("stale in-flight ABA fixture missing");
    window.requestAnimationFrame = undefined;
    let visualWidth = ${narrowVisualWidth};
    Object.defineProperty(window.visualViewport, "width", { configurable: true, get: () => visualWidth });
    Object.defineProperty(window.visualViewport, "scale", { configurable: true, get: () => 1 });
    api.stageRenderedFrame(
      (fixture.narrowed.liveFrame.rows || []).join("\\n"),
      (fixture.narrowed.liveFrame.rowsHtml || []).join("\\n"),
      fixture.narrowed.liveFrame.rowsHtml || [],
      fixture.narrowed.liveFrame.rowKeys || []
    );
    setTimeout(() => {
      try {
        const initialStaged = api.stageReadyHistoryBatch(fixture.narrowed.batch);
        const initialCommitted = api.prependReadyHistoryAtBoundary("up", 0, 0, true);
        const baseline = snapshot(api);
        if (initialStaged.status !== "READY" || initialCommitted.status !== "COMMITTED"
            || baseline.liveRows <= 0 || baseline.readyRows <= 0 || !baseline.activeText.trim()
            || baseline.cols !== ${narrowCols} || baseline.cacheRenderCols !== ${narrowCols}) {
          result.failures.push("STALE_INFLIGHT_ABA_BASELINE_NOT_READY");
          result.facts = { initialStaged: initialStaged.status, initialCommitted: initialCommitted.status, baseline: publicSnapshot(baseline) };
          finish();
          return;
        }

        const staleMarker = "ABA_STALE_A_EPOCH_N";
        const currentMarker = "ABA_CURRENT_A_EPOCH_N2";
        const geometryBMarker = "ABA_GEOMETRY_B_EPOCH_N1";
        const returnedAMarker = "ABA_RETURNED_A_EPOCH_N2";
        const staleFrame = markedFrame(fixture.narrowed.liveFrame, staleMarker);
        const currentFrame = markedFrame(fixture.narrowed.liveFrame, currentMarker);
        const geometryBFrame = markedFrame(fixture.narrowed.liveFrame, geometryBMarker);
        const returnedAFrame = markedFrame(fixture.narrowed.liveFrame, returnedAMarker);
        let fetchCalls = 0;
        let releaseStaleFetch = null;
        let releaseCurrentFetch = null;
        const requests = [];
        const responseFor = (frame, title) => ({
          ok: true,
          statusText: "OK",
          json: async () => ({
            ok: true,
            windowId: "@777",
            title,
            requestedCols: frame.requestedCols,
            requestedRows: (frame.rows || []).length,
            rows: frame.rows || [],
            rowsHtml: frame.rowsHtml || [],
            rowKeys: frame.rowKeys || [],
          }),
        });
        window.fetch = (input) => {
          fetchCalls += 1;
          const requestUrl = new URL(String(input), location.href);
          requests.push({
            ordinal: fetchCalls,
            cols: Number(requestUrl.searchParams.get("cols")),
            rows: Number(requestUrl.searchParams.get("rows")),
            windowId: requestUrl.searchParams.get("windowId") || "",
          });
          return new Promise((resolve) => {
            if (fetchCalls === 1) releaseStaleFetch = () => resolve(responseFor(staleFrame, "contract stale ABA epoch N"));
            else if (fetchCalls === 2) releaseCurrentFetch = () => resolve(responseFor(currentFrame, "contract current ABA epoch N+2"));
            else resolve(responseFor(currentFrame, "contract duplicate ABA epoch N+2"));
          });
        };

        visualWidth = ${narrowVisualWidth};
        const refreshPromise = api.refresh(false, "contract-stale-inflight-aba");
        const requestEpoch = Number(api.state().frameGeometryEpoch);
        const originalAIdentity = identityFor(api, api.measure(), requestEpoch, 9000);
        if (typeof releaseStaleFetch !== "function" || fetchCalls !== 1
            || requests[0].cols !== ${narrowCols} || originalAIdentity.cols !== ${narrowCols}) {
          result.failures.push("STALE_INFLIGHT_ABA_REQUEST_A_NOT_CAPTURED");
          result.facts = { fetchCalls, requests, requestEpoch, originalAIdentity, baseline: publicSnapshot(baseline) };
          finish();
          return;
        }

        visualWidth = ${narrowVisualWidth} - 1;
        const geometryBSize = api.measure();
        const geometryBIdentity = identityFor(api, geometryBSize, requestEpoch + 1, 9001);
        if (geometryBSize.cols !== ${narrowCols}
            || !stageFrame(api, geometryBFrame, geometryBSize, geometryBIdentity, geometryBIdentity.target)) {
          result.failures.push("STALE_INFLIGHT_ABA_GEOMETRY_B_NOT_STAGED");
          result.facts = { geometryBSize, geometryBIdentity, baseline: publicSnapshot(baseline) };
          finish();
          return;
        }
        const geometryBStartedAt = Date.now();
        const geometryBMonitor = setInterval(() => {
          if (finished) { clearInterval(geometryBMonitor); return; }
          const geometryBCommitted = snapshot(api);
          if (geometryBCommitted.admissionDisposition === "current-geometry-committed"
              && Number(geometryBCommitted.admission.requestId) === 9001
              && geometryBCommitted.activeText.includes(geometryBMarker)) {
            clearInterval(geometryBMonitor);
            visualWidth = ${narrowVisualWidth};
            const returnedASize = api.measure();
            const returnedAIdentity = identityFor(api, returnedASize, requestEpoch + 2, 9002);
            if (returnedASize.cols !== ${narrowCols}
                || !stageFrame(api, returnedAFrame, returnedASize, returnedAIdentity, returnedAIdentity.target)) {
              result.failures.push("STALE_INFLIGHT_ABA_GEOMETRY_A_RETURN_NOT_STAGED");
              result.facts = { geometryBCommitted: publicSnapshot(geometryBCommitted), returnedASize, returnedAIdentity };
              finish();
              return;
            }
            const returnedAStartedAt = Date.now();
            const returnedAMonitor = setInterval(() => {
              if (finished) { clearInterval(returnedAMonitor); return; }
              const returnedA = snapshot(api);
              if (returnedA.admissionDisposition === "current-geometry-committed"
                  && Number(returnedA.admission.requestId) === 9002
                  && returnedA.activeText.includes(returnedAMarker)) {
                clearInterval(returnedAMonitor);
                const signatureRoundTrip = geometryBIdentity.signatureHash !== originalAIdentity.signatureHash
                  && returnedAIdentity.signatureHash === originalAIdentity.signatureHash;
                const epochRoundTrip = geometryBIdentity.epoch === requestEpoch + 1
                  && returnedAIdentity.epoch === requestEpoch + 2
                  && returnedA.frameGeometryEpoch === requestEpoch + 2;
                const cyclePreservedHistory = returnedA.liveRows > 0
                  && returnedA.readyRows === baseline.readyRows
                  && returnedA.cols === baseline.cols
                  && returnedA.cacheRenderCols === baseline.cacheRenderCols
                  && returnedA.generationKey === baseline.generationKey
                  && returnedA.readyHistoryBatches === baseline.readyHistoryBatches
                  && returnedA.committedHistoryFrontier === baseline.committedHistoryFrontier;
                if (!signatureRoundTrip || !epochRoundTrip || !cyclePreservedHistory) {
                  result.failures.push("STALE_INFLIGHT_ABA_ROUND_TRIP_IDENTITY_INVALID");
                  result.facts = {
                    originalAIdentity,
                    geometryBIdentity,
                    returnedAIdentity,
                    baseline: publicSnapshot(baseline),
                    geometryBCommitted: publicSnapshot(geometryBCommitted),
                    returnedA: publicSnapshot(returnedA),
                  };
                  finish();
                  return;
                }
                releaseStaleFetch();
                Promise.resolve(refreshPromise).catch(() => false);

                const staleStartedAt = Date.now();
                const staleMonitor = setInterval(() => {
                  if (finished) { clearInterval(staleMonitor); return; }
                  const current = snapshot(api);
                  const requestTelemetry = current.admission.request && typeof current.admission.request === "object"
                    ? current.admission.request
                    : {};
                  const commitTelemetry = current.admission.commit && typeof current.admission.commit === "object"
                    ? current.admission.commit
                    : {};
                  const admittedMismatchedEpoch = Number(requestTelemetry.epoch) === requestEpoch
                    && Number(commitTelemetry.epoch) === requestEpoch + 2
                    && String(requestTelemetry.signatureHash || "") === String(commitTelemetry.signatureHash || "")
                    && current.admissionDisposition.startsWith("current-geometry")
                    && (current.frameSwapGeneration !== returnedA.frameSwapGeneration || current.activeText.includes(staleMarker));
                  if (admittedMismatchedEpoch) {
                    clearInterval(staleMonitor);
                    result.facts = {
                      fetchCalls,
                      requests,
                      originalAIdentity,
                      geometryBIdentity,
                      returnedAIdentity,
                      returnedA: publicSnapshot(returnedA),
                      staleCommit: publicSnapshot(current),
                    };
                    result.failures.push("STALE_INFLIGHT_ABA_EPOCH_COMMITTED");
                    finish();
                    return;
                  }
                  if (fetchCalls >= 2 && typeof releaseCurrentFetch === "function"
                      && current.admissionDisposition === "stale-geometry-rejected") {
                    clearInterval(staleMonitor);
                    const rejected = current;
                    const rejectionIdentityValid = Number(requestTelemetry.epoch) === requestEpoch
                      && Number(commitTelemetry.epoch) === requestEpoch + 2
                      && String(requestTelemetry.signatureHash || "") === originalAIdentity.signatureHash
                      && String(commitTelemetry.signatureHash || "") === originalAIdentity.signatureHash
                      && String(requestTelemetry.targetHash || "") === originalAIdentity.targetHash
                      && String(commitTelemetry.targetHash || "") === originalAIdentity.targetHash
                      && Number(requestTelemetry.cols) === ${narrowCols}
                      && Number(commitTelemetry.cols) === ${narrowCols}
                      && requests[1].cols === ${narrowCols};
                    result.facts = {
                      fetchCalls,
                      requests,
                      originalAIdentity,
                      geometryBIdentity,
                      returnedAIdentity,
                      baseline: publicSnapshot(baseline),
                      geometryBCommitted: publicSnapshot(geometryBCommitted),
                      returnedA: publicSnapshot(returnedA),
                      rejected: publicSnapshot(rejected),
                    };
                    if (!rejectionIdentityValid || !sameProtectedState(returnedA, rejected)
                        || rejected.activeText.includes(staleMarker)) {
                      result.failures.push("STALE_INFLIGHT_ABA_REJECTION_STATE_INVALID");
                      finish();
                      return;
                    }
                    const replacementStartedAt = Date.now();
                    let replacementMonitor = null;
                    let replacementObserver = null;
                    const finishCommittedReplacement = () => {
                      if (finished) return true;
                      const committed = snapshot(api);
                      if (!committed.activeText.includes(currentMarker)) return false;
                      if (replacementMonitor) clearInterval(replacementMonitor);
                      if (replacementObserver) replacementObserver.disconnect();
                      const currentRequest = committed.admission.request && typeof committed.admission.request === "object"
                        ? committed.admission.request
                        : {};
                      const currentCommit = committed.admission.commit && typeof committed.admission.commit === "object"
                        ? committed.admission.commit
                        : {};
                      const currentIdentityValid = committed.admissionDisposition === "current-geometry-committed"
                        && Number(currentRequest.epoch) === requestEpoch + 2
                        && Number(currentCommit.epoch) === requestEpoch + 2
                        && String(currentRequest.signatureHash || "") === originalAIdentity.signatureHash
                        && String(currentCommit.signatureHash || "") === originalAIdentity.signatureHash;
                      const protectedStateValid = committed.frameSwapGeneration === returnedA.frameSwapGeneration + 1
                        && committed.activeId !== returnedA.activeId
                        && !committed.activeText.includes(staleMarker)
                        && committed.liveRows > 0
                        && committed.readyRows === returnedA.readyRows
                        && committed.cols === returnedA.cols
                        && committed.cacheRenderCols === returnedA.cacheRenderCols
                        && committed.generationKey === returnedA.generationKey
                        && committed.readyHistoryBatches === returnedA.readyHistoryBatches
                        && committed.committedHistoryFrontier === returnedA.committedHistoryFrontier
                        && committed.committedHistoryCoveragePx === returnedA.committedHistoryCoveragePx;
                      result.facts = {
                        ...result.facts,
                        fetchCalls,
                        requests,
                        committed: publicSnapshot(committed),
                        currentIdentityValid,
                        protectedStateValid,
                      };
                      if (fetchCalls !== 2 || !currentIdentityValid || !protectedStateValid) {
                        result.failures.push("STALE_INFLIGHT_ABA_CURRENT_EPOCH_REPLACEMENT_INVALID");
                      }
                      finish();
                      return true;
                    };
                    replacementObserver = new MutationObserver(() => finishCommittedReplacement());
                    replacementObserver.observe(document.body, { subtree: true, childList: true, attributes: true });
                    releaseCurrentFetch();
                    replacementMonitor = setInterval(() => {
                      if (finished) { clearInterval(replacementMonitor); return; }
                      if (finishCommittedReplacement()) return;
                      const committed = snapshot(api);
                      if (fetchCalls > 2) {
                        clearInterval(replacementMonitor);
                        replacementObserver.disconnect();
                        result.facts = { ...result.facts, fetchCalls, requests, committed: publicSnapshot(committed) };
                        result.failures.push("STALE_INFLIGHT_ABA_REPLACEMENT_FETCH_STORM");
                        finish();
                        return;
                      }
                      if (Date.now() - replacementStartedAt > 420) {
                        clearInterval(replacementMonitor);
                        replacementObserver.disconnect();
                        result.facts = { ...result.facts, fetchCalls, requests, committed: publicSnapshot(committed) };
                        result.failures.push("STALE_INFLIGHT_ABA_CURRENT_EPOCH_REPLACEMENT_MISSING");
                        finish();
                      }
                    }, 6);
                    return;
                  }
                  if (Date.now() - staleStartedAt > 420) {
                    clearInterval(staleMonitor);
                    result.facts = { fetchCalls, requests, returnedA: publicSnapshot(returnedA), current: publicSnapshot(current) };
                    result.failures.push("STALE_INFLIGHT_ABA_NO_REJECTION_OR_COMMIT");
                    finish();
                  }
                }, 6);
                return;
              }
              if (Date.now() - returnedAStartedAt > 420) {
                clearInterval(returnedAMonitor);
                result.facts = { geometryBCommitted: publicSnapshot(geometryBCommitted), returnedA: publicSnapshot(returnedA) };
                result.failures.push("STALE_INFLIGHT_ABA_GEOMETRY_A_RETURN_MISSING");
                finish();
              }
            }, 6);
            return;
          }
          if (Date.now() - geometryBStartedAt > 420) {
            clearInterval(geometryBMonitor);
            result.facts = { baseline: publicSnapshot(baseline), geometryBCommitted: publicSnapshot(geometryBCommitted) };
            result.failures.push("STALE_INFLIGHT_ABA_GEOMETRY_B_COMMIT_MISSING");
            finish();
          }
        }, 6);
      } catch (error) {
        result.failures.push("STALE_INFLIGHT_ABA_ERROR:" + String(error && error.message || error));
        finish();
      }
    // Keep the held-response ABA cycle inside the renderer's 180ms background
    // poll window so the guard counts only the one epoch-replacement request.
    }, 45);
  } catch (error) {
    result.failures.push("STALE_INFLIGHT_ABA_ERROR:" + String(error && error.message || error));
    finish();
  }
})();
</script>`;
    staleInflightAbaResult = runGeneratedChrome(
      fixture.html,
      staleInflightAbaProbe,
      "stale-inflight-aba",
      3200,
    );
  }

  const narrowParityResult = (parityCase === "all" || parityCase === "narrow")
    ? runGeneratedChrome(
        narrowFixture.html,
        parityProbeFor(narrowFixture, narrowVisualWidth),
        "live-cache-parity-mobile-narrow-visual-viewport",
        1800,
      )
    : null;

  let dynamicRenderColsResult = null;
  if (parityCase === "all" || parityCase === "dynamic") {
    const encodedDynamic = Buffer.from(JSON.stringify({
      initial: { batch: fixture.batches[0], liveFrame: fixture.liveFrame },
      narrowed: { batch: narrowFixture.batches[0], liveFrame: narrowFixture.liveFrame },
    }), "utf8").toString("base64");
    const dynamicProbe = `
<script id="dynamicRenderColsData" type="application/octet-stream">${encodedDynamic}</script>
<script>
(() => {
  const result = { failures: [], facts: {} };
  const finish = () => {
    document.getElementById("contractResult").textContent = btoa(unescape(encodeURIComponent(JSON.stringify(result))));
  };
  try {
    const api = window.__mantisCaptureRenderer;
    const fixture = JSON.parse(atob(document.getElementById("dynamicRenderColsData").textContent.trim()));
    if (!api || !fixture.initial || !fixture.narrowed || !window.visualViewport) throw new Error("dynamic renderer fixtures missing");
    window.requestAnimationFrame = undefined;
    const stageLive = (frame) => api.stageRenderedFrame(
      (frame.rows || []).join("\\n"),
      (frame.rowsHtml || []).join("\\n"),
      frame.rowsHtml || [],
      frame.rowKeys || []
    );
    stageLive(fixture.initial.liveFrame);
    setTimeout(() => {
      try {
        const stagedInitial = api.stageReadyHistoryBatch(fixture.initial.batch);
        const committedInitial = api.prependReadyHistoryAtBoundary("up", 0, 0, true);
        if (stagedInitial.status !== "READY" || committedInitial.status !== "COMMITTED") {
          throw new Error("initial committed cache missing: " + stagedInitial.status + "/" + committedInitial.status);
        }
        const oldKeys = new Set(fixture.initial.batch.renderRows.map((row) => String(row.key || "")));
        const oldRowsBefore = Array.from(document.querySelectorAll(".readyHistoryRow"))
          .filter((row) => oldKeys.has(String(row.dataset.renderRowKey || ""))).length;
        Object.defineProperty(window.visualViewport, "width", { configurable: true, get: () => ${narrowVisualWidth} });
        if (typeof api.syncViewportGeometryOnly === "function") api.syncViewportGeometryOnly();
        Promise.resolve(api.refresh(false, "contract-dynamic-render-cols")).catch(() => false).then(() => {
          stageLive(fixture.narrowed.liveFrame);
          setTimeout(() => {
            try {
              const state = api.state();
              const oldRowsAfter = Array.from(document.querySelectorAll(".readyHistoryRow"))
                .filter((row) => oldKeys.has(String(row.dataset.renderRowKey || ""))).length;
              const narrowedCols = Number(fixture.narrowed.batch.renderCols);
              result.facts = {
                initialCols: fixture.initial.batch.renderCols,
                narrowedCols,
                rendererColsAfter: state.cols,
                visualViewportWidth: window.visualViewport.width,
                innerWidth: window.innerWidth,
                oldRowsBefore,
                oldRowsAfter,
                committedHistoryFrontier: state.committedHistoryFrontier,
              };
              if (Number(state.cols) !== narrowedCols || oldRowsAfter !== 0 || state.committedHistoryFrontier !== null) {
                result.failures.push("DYNAMIC_RENDER_COLS_STALE_CACHE");
              }
            } catch (error) {
              result.failures.push("DYNAMIC_RENDER_COLS_ERROR:" + String(error && error.message || error));
            }
            finish();
          }, 140);
        });
      } catch (error) {
        result.failures.push("DYNAMIC_RENDER_COLS_ERROR:" + String(error && error.message || error));
        finish();
      }
    }, 140);
  } catch (error) {
    result.failures.push("DYNAMIC_RENDER_COLS_ERROR:" + String(error && error.message || error));
    finish();
  }
})();
</script>`;
    dynamicRenderColsResult = runGeneratedChrome(
      fixture.html,
      dynamicProbe,
      "live-cache-parity-dynamic-render-cols",
      2600,
    );
  }

  return {
    measurement,
    deepResult,
    parityResult,
    desktopParityResult,
    narrowMeasurement,
    pinchMeasurement,
    publicMeasureResult,
    pinchTransitionResult,
    renderColsSwapOrderingResult,
    staleInflightWidthResult,
    staleInflightWidthPrecommitResult,
    visibleCommitLeaseRecoveryResult,
    staleInflightAbaResult,
    narrowParityResult,
    dynamicRenderColsResult,
  };
}

if (parityCase === "owner-reset-exception") {
  const exceptionFacts = runAcceptedFrameColumnJavaContract();
  const ownerCommitted = Number(exceptionFacts.exceptionOwnerCommitted) === 1;
  const staleWidthRejected = Number(exceptionFacts.exceptionOwnerCols) === -1
    && Number(exceptionFacts.exceptionAdmittedCols) === -1
    && Number(exceptionFacts.exceptionPrewarmLastCols) === -1;
  if (!ownerCommitted || !staleWidthRejected) {
    fail(`MOBILE_READY_OWNER_RESET_SKIPPED_ON_PARSE_EXCEPTION facts=${JSON.stringify(exceptionFacts)}`);
  }
  console.log("mobile READY owner-reset parse-exception guard passed", JSON.stringify(exceptionFacts));
  process.exit(0);
}

if (parityCase === "refill-lifecycle" || parityCase === "refill-single-flight") {
  const refillFacts = runReadyRefillJavaContract();
  if (parityCase === "refill-lifecycle") {
    const terminalPathsRelease = Number(refillFacts.successStageCalls) === 1
      && Number(refillFacts.successInFlight) === 0
      && Number(refillFacts.nullInFlight) === 0
      && Number(refillFacts.nullFailureSignals) === 1
      && Number(refillFacts.nonOkFailureSignals) === 1
      && Number(refillFacts.exceptionFailureSignals) === 1;
    if (!terminalPathsRelease) {
      fail(`READY_REFILL_LATCH_STUCK facts=${JSON.stringify(refillFacts)}`);
    }
    console.log("READY refill Java lifecycle guard passed", JSON.stringify(refillFacts));
    process.exit(0);
  }
  const stableSingleFlight = Number(refillFacts.sameTupleStableAcrossFrameHash) === 1
    && Number(refillFacts.differentGenerationDistinct) === 1
    && Number(refillFacts.heldGetJsonCalls) === 1
    && Number(refillFacts.heldInFlight) === 1;
  if (!stableSingleFlight) {
    fail(`READY_REFILL_SINGLE_FLIGHT_UNSTABLE facts=${JSON.stringify(refillFacts)}`);
  }
  console.log("READY refill Java single-flight guard passed", JSON.stringify(refillFacts));
  process.exit(0);
}

const pendingGapCallbackMutant = String(
  process.env.SCROLL_PENDING_GAP_CALLBACK_MUTANT || "",
).trim().toLowerCase();
const pendingGapCallbackFacts =
  (parityCase === "all" || parityCase === "refill-pending-gap-callback")
    ? pendingGapCallbackContract(pendingGapCallbackMutant)
    : null;
if (parityCase === "refill-pending-gap-callback") {
  console.log(
    "READY pending-gap cross-language callback guard passed",
    JSON.stringify(pendingGapCallbackFacts),
  );
  process.exit(0);
}

const refillRendererFacts = (parityCase === "all" || parityCase === "refill-renderer-lifecycle")
  ? generatedReadyRefillLifecycleProbe()
  : null;
if (parityCase === "refill-renderer-lifecycle") {
  console.log("READY refill renderer lifecycle guard passed", JSON.stringify(refillRendererFacts.facts));
  process.exit(0);
}

const bootstrapFacts = (parityCase === "all" || parityCase === "bootstrap")
  ? generatedBootstrapProbe()
  : null;
const generatedProductFacts = generatedProductProbe();
if (parityCase === "all" || parityCase === "visible-commit-lease-recovery") {
  const recovery = generatedProductFacts.visibleCommitLeaseRecoveryResult;
  if (!recovery || !recovery.facts) fail("VISIBLE_COMMIT_LEASE_RECOVERY_PROOF_NOT_EXECUTED");
  const facts = recovery.facts;
  if (facts.rejectionReleased !== true || facts.exceptionReleased !== true
      || facts.oldCompletionPreservedNewToken !== true
      || !(Number(facts.newToken) > Number(facts.oldToken))
      || !String(facts.finalActiveText || "").includes("VISIBLE-COMMIT-NEW-ROW")
      || String(facts.finalActiveText || "").includes("VISIBLE-COMMIT-OLD-ROW")) {
    fail(`VISIBLE_COMMIT_LEASE_RECOVERY_PROOF_INCOMPLETE facts=${JSON.stringify(facts)}`);
  }
}

function templateFunctionBody(source, name) {
  const marker = `function ${name}(`;
  const start = source.indexOf(marker);
  if (start < 0) return "";
  const open = source.indexOf("{{", start);
  if (open < 0) return "";
  let depth = 0;
  let quote = "";
  let escaped = false;
  let lineComment = false;
  let blockComment = false;
  for (let index = open; index < source.length - 1; index += 1) {
    const char = source[index];
    const next = source[index + 1];
    if (lineComment) {
      if (char === "\n") lineComment = false;
      continue;
    }
    if (blockComment) {
      if (char === "*" && next === "/") { blockComment = false; index += 1; }
      continue;
    }
    if (quote) {
      if (escaped) escaped = false;
      else if (char === "\\") escaped = true;
      else if (char === quote) quote = "";
      continue;
    }
    if (char === "/" && next === "/") { lineComment = true; index += 1; continue; }
    if (char === "/" && next === "*") { blockComment = true; index += 1; continue; }
    if (char === "\"" || char === "'" || char === "`") {
      quote = char;
      continue;
    }
    const pair = source.slice(index, index + 2);
    if (pair === "{{") {
      depth += 1;
      index += 1;
    } else if (pair === "}}") {
      depth -= 1;
      if (depth === 0) return source.slice(open + 2, index);
      index += 1;
    }
  }
  return "";
}

function pythonMethodBody(source, name) {
  const marker = `    def ${name}(`;
  const start = source.indexOf(marker);
  if (start < 0) return "";
  const next = source.indexOf("\n    def ", start + marker.length);
  return source.slice(start, next < 0 ? source.length : next);
}

function javaMethodBody(source, name, fromLast = false) {
  // Match declarations at a line boundary. lastIndexOf(name + "(") can select
  // a later call site, which made overload guards inspect an unrelated block.
  const escapedName = name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const declarations = Array.from(
    source.matchAll(new RegExp(`^\\s*${escapedName}\\s*\\(`, "gm")),
  );
  const declaration = fromLast ? declarations.at(-1) : declarations[0];
  const markerIndex = declaration ? declaration.index : -1;
  if (markerIndex < 0) return "";
  const open = source.indexOf("{", markerIndex);
  if (open < 0) return "";
  let depth = 0;
  let quote = "";
  let escaped = false;
  let lineComment = false;
  let blockComment = false;
  for (let index = open; index < source.length; index += 1) {
    const char = source[index];
    const next = source[index + 1];
    if (lineComment) {
      if (char === "\n") lineComment = false;
      continue;
    }
    if (blockComment) {
      if (char === "*" && next === "/") { blockComment = false; index += 1; }
      continue;
    }
    if (quote) {
      if (escaped) escaped = false;
      else if (char === "\\") escaped = true;
      else if (char === quote) quote = "";
      continue;
    }
    if (char === "/" && next === "/") { lineComment = true; index += 1; continue; }
    if (char === "/" && next === "*") { blockComment = true; index += 1; continue; }
    if (char === '"' || char === "'") {
      quote = char;
      continue;
    }
    if (char === "{") depth += 1;
    else if (char === "}") {
      depth -= 1;
      if (depth === 0) return source.slice(open + 1, index);
    }
  }
  return "";
}

const requiredArchitecture = [
  main.includes("stageReadyLocalHistoryBatchForRenderer("),
  main.includes("invalidateStaleLocalHistoryBaseChunk("),
  main.includes("freezeCaptureRendererReadyHistoryLiveRowFrontier("),
  server.includes("function stageReadyHistoryBatch("),
  server.includes("function prependReadyHistoryAtBoundary("),
  server.includes('"renderRows": render_rows'),
  server.includes("SCROLL_ANCHOR_PREPEND_CONTRACT"),
];
if (requiredArchitecture.some((present) => !present)) oldSignature();

// These checks intentionally fail the pre-correction source: the old renderer
// had no canonical live/cache frontier and retained stale committed coverage
// across frame swaps, allowing invisible-boundary overlap and black swaps.
if (!server.includes("liveRowFrontier")) oldSignature();
if (!server.includes("committedHistoryFrontier")) oldSignature();
if (!server.includes("reconcileReadyHistoryForFrameSwap")) oldSignature();

const moveBody = templateFunctionBody(server, "nudgeTouchScroll");
const prependBody = templateFunctionBody(server, "prependReadyHistoryAtBoundary");
const stageBody = templateFunctionBody(server, "stageReadyHistoryBatch");
const reconcileBody = templateFunctionBody(server, "reconcileReadyHistoryForFrameSwap");
const renderedFrameBody = templateFunctionBody(server, "stageRenderedFrame");
const applyRenderedFrameBody = templateFunctionBody(server, "applyRenderedFrame");
const releaseVisibleCommitLeaseBody = templateFunctionBody(server, "releaseVisibleCommitLease");
const measureBody = templateFunctionBody(server, "measure");
const resetRenderColsBody = templateFunctionBody(server, "resetReadyHistoryForRenderCols");
const refreshBody = templateFunctionBody(server, "refresh");
const frameGeometryCurrentBody = templateFunctionBody(server, "frameGeometryIsCurrent");
const producerBody = pythonMethodBody(server, "bounded_scrollback_chunk");
const androidStageBody = javaMethodBody(main, "private void stageReadyLocalHistoryBatchForRenderer", true);
const androidMoveBody = javaMethodBody(main, "private void processHistoryDragSample");
if (!moveBody || !prependBody || !stageBody || !reconcileBody || !renderedFrameBody
    || !applyRenderedFrameBody || !releaseVisibleCommitLeaseBody
    || !measureBody || !refreshBody || !frameGeometryCurrentBody
    || !producerBody || !androidStageBody || !androidMoveBody) {
  fail("cannot structurally parse actual producer/APK/renderer seam functions");
}
if (parityCase === "all" || parityCase === "visible-commit-lease-recovery"
    || parityCase === "stale-inflight-width" || parityCase === "stale-inflight-width-pre-commit"
    || parityCase === "stale-inflight-width-post-commit") {
  const noopAt = applyRenderedFrameBody.indexOf("if(rendered===lastText&&nextCols===lastCols)return");
  const leaseAt = applyRenderedFrameBody.indexOf("beginVisibleCommitLease()");
  const firstRafAt = applyRenderedFrameBody.indexOf('typeof requestAnimationFrame==="function"');
  if (noopAt < 0 || leaseAt <= noopAt || firstRafAt <= leaseAt) {
    fail("VISIBLE_COMMIT_LEASE_START_BOUNDARY_DRIFT");
  }
  requireText(
    refreshBody,
    "if(pollRefresh&&visibleCommitLeaseToken>0)",
    "passive poll admission must stop at the visible commit lease",
  );
  requireText(
    refreshBody,
    "normalPollDeferredUntilVisibleCommit=true",
    "passive poll demand must retain its reason through visible commit",
  );
  requireText(
    releaseVisibleCommitLeaseBody,
    "if(!visibleCommitLeaseIsCurrent(token))return false",
    "stale commit token may not release newer work",
  );
  requireText(
    releaseVisibleCommitLeaseBody,
    "visibleCommitLeaseToken=0",
    "matching visible commit release must clear scheduler authority",
  );
  requireText(
    releaseVisibleCommitLeaseBody,
    "if(pollWasDeferred)scheduleNormalPoll()",
    "deferred normal poll must rearm after release without immediate replay",
  );
  // WHY: an aggregate literal count lets one critical branch lose its release
  // while unrelated branches keep the total green. Lock every terminal branch
  // to its own release so deletion produces a branch-specific failure.
  const releasePaths = [
    ["STAGE_GEOMETRY_REJECTION", renderedFrameBody,
      "queueCurrentGeometryReplacement();releaseVisibleCommitLease(commitLeaseToken);return false;"],
    ["INVALID_TARGET_OR_COLUMNS", renderedFrameBody,
      "if(!target||!Number.isFinite(nextCols)||nextCols<20){{releaseVisibleCommitLease(commitLeaseToken);return false;}}"],
    ["EMPTY_PREPARED_FRAME", renderedFrameBody,
      "if(!target.querySelector(\".captureRenderRow\")||!target.textContent.trim()){{target.textContent=\"\";releaseVisibleCommitLease(commitLeaseToken);return false;}}"],
    ["RENDER_COLUMNS_RESET_FAILURE", renderedFrameBody,
      "else if(renderColsChanged&&!resetReadyHistoryForRenderCols(nextCols,target)){{target.textContent=\"\";releaseVisibleCommitLease(commitLeaseToken);return false;}}"],
    ["CURRENT_TOKEN_SUPERSESSION", renderedFrameBody,
      "if(!visibleCommitLeaseIsCurrent(commitLeaseToken)||generation!==frameSwapGeneration){{releaseVisibleCommitLease(commitLeaseToken);return;}}"],
    ["SUCCESSFUL_VISIBLE_COMMIT", renderedFrameBody,
      "releaseVisibleCommitLease(commitLeaseToken);clearInactiveBufferAfterSwap(inactiveScreen,generation);"],
    ["SWAP_EXCEPTION", renderedFrameBody,
      "if(target&&target!==screen)target.textContent=\"\";releaseVisibleCommitLease(commitLeaseToken);status.textContent=\"visible commit failed: \"+"],
    ["STAGE_EXCEPTION", renderedFrameBody,
      "if(target&&target!==screen)target.textContent=\"\";releaseVisibleCommitLease(commitLeaseToken);status.textContent=\"visible commit stage failed: \"+"],
    ["HELD_TOUCH_EXIT", applyRenderedFrameBody,
      "status.textContent=\"held touch-scroll release frame\";refreshQueued=false;releaseVisibleCommitLease(visibleCommitToken);return;"],
    ["HELD_UNPROVEN_TOUCH_EXIT", applyRenderedFrameBody,
      "status.textContent=\"held unproven touch-scroll release commit\";refreshQueued=false;releaseVisibleCommitLease(visibleCommitToken);return;"],
    ["ACTIVE_UNPROVEN_TOUCH_EXIT", applyRenderedFrameBody,
      "status.textContent=\"deferred unproven active-touch frame\";refreshQueued=true;scheduleTouchScrollNudgeRefresh();releaseVisibleCommitLease(visibleCommitToken);return;"],
    ["APPLY_EXCEPTION", applyRenderedFrameBody,
      "releaseVisibleCommitLease(visibleCommitToken);status.textContent=\"visible commit apply failed: \"+"],
    ["SCHEDULING_EXCEPTION", applyRenderedFrameBody,
      "releaseVisibleCommitLease(visibleCommitToken);status.textContent=\"visible commit scheduling failed: \"+"],
  ];
  for (const [branch, body, required] of releasePaths) {
    if (!body.replace(/\s+/g, "").includes(required.replace(/\s+/g, ""))) {
      fail(`VISIBLE_COMMIT_LEASE_RELEASE_MISSING_${branch}`);
    }
  }
  for (const required of [
    "network completion precedes the renderer's",
    "two-rAF active-buffer commit",
    "refreshQueued` loses the",
    "older staged frame must never release a newer commit lease",
    "commit exception must restore the last-good active buffer",
  ]) {
    requireText(server, required, `visible commit lease lacks local WHY: ${required}`);
  }
}
if (parityCase === "all" || parityCase === "stale-inflight-aba") {
  requireText(
    frameGeometryCurrentBody,
    "Number(requestIdentity.epoch)!==Number(commitIdentity.epoch)",
    "ABA response admission must require request/commit epoch equality",
  );
  requireText(
    frameGeometryCurrentBody,
    "geometry can change A -> B -> A",
    "ABA response admission lacks local causal-freshness WHY",
  );
}

if (parityCase === "all" || parityCase === "public-measure") {
  if (measureBody.includes("resetReadyHistoryForRenderCols")
      || /\b(?:lastRows|lastCols|readyHistoryRenderCols|committedHistoryFrontier)\s*=/.test(measureBody)
      || measureBody.includes("fetch(")) {
    fail("PUBLIC_MEASURE_MUTATES_HISTORY");
  }
  requireText(renderedFrameBody, "commitMeasuredGeometry(nextGeometry)", "prepared frame must own geometry publication");
}
if (parityCase === "all" || parityCase === "pinch") {
  for (const required of ["pinchActive", "pinchOwnsColumns", "lastCols"]) {
    requireText(measureBody, required, `pinch measurement must preserve committed columns via ${required}`);
  }
}
if (parityCase === "all" || parityCase === "ordering") {
  if (!resetRenderColsBody) fail("RENDER_COLS_SWAP_ORDER");
  const preparedAt = renderedFrameBody.indexOf('target.querySelector(".captureRenderRow")');
  const resetAt = renderedFrameBody.indexOf("resetReadyHistoryForRenderCols(nextCols,target)");
  const publishAt = renderedFrameBody.indexOf("commitMeasuredGeometry(nextGeometry)");
  if (preparedAt < 0 || resetAt <= preparedAt || publishAt <= resetAt) fail("RENDER_COLS_SWAP_ORDER");
  for (const required of ["preparedReplacement===screen", 'preparedReplacement.querySelector(".captureRenderRow")']) {
    requireText(resetRenderColsBody, required, `renderCols reset lacks prepared replacement guard ${required}`);
  }
  if (refreshBody.includes("resetReadyHistoryForRenderCols")
      || /\b(?:lastRows|lastCols)\s*=/.test(refreshBody)) {
    fail("RENDER_COLS_SWAP_ORDER");
  }
}

// Exact-product cross-surface contract. The prior guard passed a parallel
// ContractRenderer even though the real producer omitted the frontier and the
// real frame swap deleted committed history. These checks must fail that source.
requireText(producerBody, '"liveRowFrontier": history_size', "producer must emit canonical oldest-live absolute row");
requireText(
  main.replace(/\s+/g, ""),
  'batch.put("liveRowFrontier"',
  "APK must forward producer frontier",
);
requireText(server, "committedHistoryRowsByKey", "renderer must retain committed row data across frame swaps");
requireText(renderedFrameBody, "appendCommittedHistoryRowsToBuffer", "new frame buffer must carry committed history before swap");
if (reconcileBody.includes("committedHistoryKeys.clear()")
    || reconcileBody.includes("committedHistoryFrontier=null")) {
  fail("frame swap destroys committed history state");
}
requireText(main, "HISTORY_DRAG_MOVE_NETWORK_ENABLED = false", "ACTION_MOVE network owner must be disabled");
requireText(main, "flushDeferredHistoryScrollOnRelease", "ACTION_UP must reconcile deferred tmux movement");
if (androidMoveBody.includes("scrollTerminalFromTouch(")) {
  fail("ACTION_MOVE still dispatches network/tmux scroll");
}
requireText(main, "HISTORY_DRAG_RELEASE_MOMENTUM_ENABLED = true", "velocity-qualified ACTION_UP must use native local-pixel momentum");
for (const required of [
  "requestReadyHistoryRefill", "handleReadyHistoryRefillRequest",
  "continueReadyHistoryRefillAfterStage", "clientPrepareElapsedMs",
  "localHistoryRendererColsForRequest", "captureRendererHistoryMovementBlocked",
  "parseJavascriptObjectResult", "READY_UNDERRUN_BEFORE_TRUE_TOP",
  "TRUE_HISTORY_TOP", "appliedPx",
]) {
  requireText(main, required, `APK structured READY contract missing ${required}`);
}
for (const required of [
  "COMMITTED_DUPLICATE", "QUEUED_DUPLICATE", "CACHE_COLS_DIVERGE_LIVE_COLS",
  "readyHistoryPreparedStart", "readyHistoryTrueTopPrepared",
  "readyHistoryRefillRequested", "maybeRequestReadyHistoryRefill(\"normal-poll\")",
  "positiveHistoryRangePx",
]) {
  requireText(server, required, `renderer structured READY contract missing ${required}`);
}
function executableJs(body) {
  return body.replace(/\/\*[\s\S]*?\*\//g, "").replace(/\/\/.*$/gm, "");
}

for (const [name, body] of [["MOVE", executableJs(moveBody)], ["prepend", executableJs(prependBody)]]) {
  for (const forbidden of [
    "fetch(", "XMLHttpRequest", "/terminal-frame", "/scrollback/chunk",
    "/touch-scroll", "Promise", "await ", "async ", "postRefreshSoon(",
    "refreshTouchScrollCommit(",
  ]) {
    if (body.includes(forbidden)) fail(`${name} path has forbidden dependency ${forbidden}`);
  }
}
for (const required of [
  "anchorKey", "oldTopPx", "newTopPx", "deltaPx", "getBoundingClientRect()",
  "readyHistoryBatches", "overflowAnchor", "prepend(", "same renderer",
]) {
  if (!prependBody.includes(required)) fail(`prepend transaction missing ${required}`);
}
for (const forbidden of [
  "replaceChildren", "replaceWith", "outerHTML", "scrollTo(", "scrollBy(",
  "smooth", "rowHeight", "lineHeight*", "setTimeout", "requestAnimationFrame",
]) {
  if (prependBody.includes(forbidden)) fail(`prepend transaction uses forbidden ${forbidden}`);
}
for (const required of ["generationKey", "start", "end", "renderRows", "READY"]) {
  if (!stageBody.includes(required)) fail(`READY staging missing ${required}`);
}
requireText(main, "localHistoryChunkCache.remove", "stale base invalidation must remove the rejected cache entry");
requireText(main, "stageReadyLocalHistoryBatchForRenderer", "APK must stage immutable renderer batches outside seam consumption");
requireText(server, "absoluteRow", "renderer rows need stable absolute coordinates");
requireText(server, "segmentIndex", "wrapped renderer rows need stable segment identity");
requireText(server, "readyHistoryPrefetchHorizonPx", "prefetch horizon must be velocity/latency aware");
requireText(server, "resetReadyHistoryForBottom", "explicit Bottom must reset prepend anchoring");

const nativeControllerPath = path.join(root, "app/src/main/java/com/kaleeb/wezterm/NativeHistoryScrollController.java");
if (!fs.existsSync(nativeControllerPath)) fail("NATIVE_KINETIC_OWNER_MISSING");
const nativeController = fs.readFileSync(nativeControllerPath, "utf8");
const nativeControllerSha256 = createHash("sha256").update(nativeController).digest("hex");
function sourceSection(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  const end = start < 0 ? -1 : source.indexOf(endMarker, start + startMarker.length);
  return start < 0 || end < 0 ? "" : source.slice(start, end);
}
const releaseBody = sourceSection(
  main,
  "private boolean dispatchHistoryReleaseFling(MotionEvent event)",
  "private boolean historyDragReleaseMomentumEnabled()",
);
const dragBody = sourceSection(
  main,
  "private void nudgeCaptureRendererForHistorySample(float y, int lineThreshold)",
  "private int historyDragRepeats(",
);
const frameResponseBody = pythonMethodBody(server, "terminal_render_frame");

for (const required of [
  "OverScroller", "Choreographer", "ViewConfiguration",
  "configuration.getScaledMinimumFlingVelocity()",
  "configuration.getScaledMaximumFlingVelocity()",
  "isFlingVelocity(", "Math.min(maximumFlingVelocity, magnitude)",
  "scroller.fling(", "scroller.computeScrollOffset()",
  "currentY - lastY", "pixelSink.applyRelativePixels(",
]) {
  requireText(nativeController, required, `native kinetic owner missing ${required}`);
}
if (nativeController.includes("setFriction(")) {
  fail("native kinetic owner overrides platform scroll friction");
}
for (const forbidden of ["/touch-scroll", "/scrollback/chunk", "fetch(", "getJson(", "postDelayed("]) {
  if (nativeController.includes(forbidden)) fail(`native kinetic owner contains forbidden dependency ${forbidden}`);
}
requireText(dragBody, "nativeHistoryScrollController.dragBy(", "direct drag is not wired to native relative pixels");
requireText(releaseBody, "nativeHistoryScrollController.fling(", "release velocity is not wired to OverScroller");
if (releaseBody.includes("scrollTerminalFromTouch(") || releaseBody.includes("historyMomentumRepeats(")) {
  fail("ROW_BURST_MOMENTUM_CONNECTED");
}
for (const forbidden of [
  "startHistoryMomentum(", "runHistoryMomentumFrame(", "HISTORY_DRAG_MOMENTUM_FRAME_MS",
  "HISTORY_DRAG_MOMENTUM_DECAY", "HISTORY_DRAG_MOMENTUM_REPEAT_VELOCITY_DIVISOR",
]) {
  if (main.includes(forbidden)) fail(`disconnected custom momentum owner remains: ${forbidden}`);
}

requireText(stageBody, "const eligibleRows=uncommittedRows.filter", "overlap batch is not trimmed to the prepared frontier");
requireText(stageBody, "row.absoluteRow<liveRowFrontier", "live frontier does not trim overlap");
requireText(stageBody, "row.absoluteRow<preparedFrontier", "producer cursor overlap is not trimmed to contiguous prepared rows");
if (stageBody.includes("end>=liveRowFrontier)return false")) {
  fail("overlap still rejects the entire mixed history/live batch");
}
for (const required of [
  "canonicalKey", "committedHistoryKeys.has(canonicalKey)",
  "row.dataset.renderRowKey=canonicalKey", "appendCommittedHistoryRowsToBuffer",
]) {
  requireText(renderedFrameBody, required, `frame swap lacks canonical row merge: ${required}`);
}
requireText(frameResponseBody, "\"rowKeys\": visible_row_keys", "terminal frame does not publish canonical row keys");
requireText(frameResponseBody, "\"liveRowFrontier\"", "terminal frame omits live frontier");

const releaseFlushBody = sourceSection(
  main,
  "private void flushDeferredHistoryScrollOnRelease(boolean releaseFlingStarted)",
  "private boolean shouldStartPhysicalUpwardHistoryGesture(",
);
if (releaseFlushBody.includes("scrollTerminalFromTouch(") || releaseFlushBody.includes("sendHistoryScrollFromTouch(")) {
  fail("ACTION_UP replays delayed tmux rows");
}
if (fs.readFileSync(fileURLToPath(import.meta.url), "utf8").includes("class " + "ContractRenderer")) {
  fail("MODEL_ONLY_GUARD_FORBIDDEN");
}

console.log("scroll anchor prepend exact-product guard passed", JSON.stringify({
  bootstrap: bootstrapFacts && bootstrapFacts.facts,
  pendingGapCallback: pendingGapCallbackFacts,
  refillRenderer: refillRendererFacts && refillRendererFacts.facts,
  measuredCols: generatedProductFacts.measurement.facts.measured.cols,
  deep: generatedProductFacts.deepResult.facts,
  parity: {
    staged: generatedProductFacts.parityResult.facts.staged,
    committed: generatedProductFacts.parityResult.facts.committed,
    rightOverflowPx: generatedProductFacts.parityResult.facts.rightOverflowPx,
  },
  pinchCols: generatedProductFacts.pinchMeasurement.facts.measured.cols,
  publicMeasure: generatedProductFacts.publicMeasureResult && generatedProductFacts.publicMeasureResult.facts,
  pinchTransition: generatedProductFacts.pinchTransitionResult && generatedProductFacts.pinchTransitionResult.facts,
  renderColsSwapOrdering: generatedProductFacts.renderColsSwapOrderingResult && generatedProductFacts.renderColsSwapOrderingResult.facts,
  staleInflightWidth: generatedProductFacts.staleInflightWidthResult && generatedProductFacts.staleInflightWidthResult.facts,
  staleInflightWidthPrecommit: generatedProductFacts.staleInflightWidthPrecommitResult && generatedProductFacts.staleInflightWidthPrecommitResult.facts,
  visibleCommitLeaseRecovery: generatedProductFacts.visibleCommitLeaseRecoveryResult && generatedProductFacts.visibleCommitLeaseRecoveryResult.facts,
  staleInflightAba: generatedProductFacts.staleInflightAbaResult && generatedProductFacts.staleInflightAbaResult.facts,
  nativeControllerSha256,
}));
