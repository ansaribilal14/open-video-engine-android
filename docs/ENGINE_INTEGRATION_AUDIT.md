# ENGINE INTEGRATION AUDIT — Android client of Open Video Engine

**Date:** 2026-10-02 · **Auditor:** Android client takeover (Phase 0)
**Method:** read-only inspection of the live engine repository, its normative specs, and its
crate sources. **The engine repository was NOT modified and will not be modified.** No
feature below is inferred from a name: every claim cites the exact interface inspected.

---

## 1. Engine identity inspected

| Item | Value |
|---|---|
| Repository | `https://github.com/ansaribilal14/open-video-engine` (public) |
| Commit inspected | **`06c9249`** (branch `main`, worktree clean) |
| Release pointers | tag `v0.1.0` → `bee72c5` (audited-state), tag `v0.2.0` → `4173211` (completion pointer) |
| Delta `4173211..06c9249` | PR #25 + record commit — **documentation-only** (README truth pass, `CHANGELOG.md`, ADR-024 collision note, state record). Engine code identical to `v0.2.0`. |
| CI at HEAD | 5/5 jobs green (verified via GitHub API in the takeover round: rust, audit, gpu-conformance, platform-conformance, libav-confinement) |
| Engine dependency pin for this client | **exact commit `06c9249`**, recorded in `bridge/README.md` and enforced by `bridge/fetch-engine.sh` (sha-checked checkout) |
| License | engine own code `MIT OR Apache-2.0`; libav linked under the LGPL-compatible configuration, confined by CI to `{ove-decode, ove-encode}` (ADR-015) |

## 2. Client surfaces that exist (verified)

The engine ships four *client shells* plus the *library session* they all sit on:

1. **`ove-cli`** (binary) — per-command subcommands + a W14 **headless batch grammar**
   (`new / add-track / import / add-clip / split / resize / move / remove / undo / redo /
   status / export-copy / export-wav`), one engine session per batch, fail-fast, deterministic
   `OK <lineno> <verb> <detail>` output lines (engine/ove-cli/src/main.rs).
2. **`ove-mcp`** (binary) — MCP stdio JSON-RPC 2.0, stateless tools mirroring the same
   grammar (`create_project … get_status`, engine/ove-mcp/src/lib.rs §tool_catalogue).
3. **`ove-script`** — Rhai (no_float) scripting client, same command semantics.
4. **`ove-plugin`** — process-tier plugin client, capability default-DENY (ADR-021).
5. **`ove-engine`** (library) — the **headless engine session** every client above is built
   on: `create / open / import_media / add_track / add_clip / split / resize / move_clip /
   remove / execute / undo / redo / state_hash / project / build_render_input /
   render_frame / export_reencode / export_copy / export_wav / assemble_timeline_audio`
   (engine/ove-engine/src/lib.rs).

The engine's own mission directive names **mobile editors** as intended clients of this
session ("editors (mobile / browser / desktop) … are all clients of the same engine",
README §Mission). This audit therefore treats the library session as a *supported public
interface* for the Android client — consumed exactly the way `ove-cli` consumes it, never
by reimplementing engine behavior in Kotlin.

### Decisive findings that shape the integration

| # | Finding | Evidence | Consequence for Android |
|---|---|---|---|
| F-1 | The **composite timeline export** (`export_reencode`: render every output frame → MPEG4 CRF6 bitexact + AAC audio → MP4) — the exact path certified on real media (REALWORLD_VALIDATION §4 step 7) — exists **only in the library**. CLI/MCP expose only `export-copy` (source-segment stream copy) and `export-wav` (timeline audio mixdown). | engine/ove-engine/src/lib.rs:484; ove-cli batch verb list; ove-mcp tool catalogue (no export tool) | The Android client **must** use a library-level bridge, or it could not perform the engine's own flagship certified export. |
| F-2 | **Typed errors exist only in the library** (`EngineError` with 16 variants incl. `NoAudioStream`, `NonExactSampleCut`, `AudioRetimeUnsupported`, `BeyondDeclaredLimits` via decode/probe errors). CLI/MCP flatten everything to Display strings + exit codes. | engine/ove-engine/src/lib.rs:49-121; ove-cli main.rs `Err` path | A library bridge gives the UI a real, typed error model instead of string scraping. |
| F-3 | **No client surface returns the clip layout.** CLI/MCP `status`/`get_status` return only `state_hash`, track ids, asset ids — not clips/durations/positions. | ove-cli main.rs:119-141, 346-353; ove-mcp get_status | UI state projection must come from the **documented project format** (below) or a bridge over the library `project()` accessors. |
| F-4 | The **project format is a normative, documented on-disk contract**: `manifest.json` (schema, tick_axis, state_hash, asset registry), append-only `commands.jsonl`, `snapshot/`, `assets/<blake3>/…probe.json` (streams, codecs, durations, width/height, color tags, keyframe index, VFR), disposable `renders/` + `cache/`. | docs/specs/PROJECT_FORMAT_SPEC.md §1-§7 (Implemented 2026-09-29, ADR-016) | The client reads `manifest.json` + `probe.json` for import metadata and verifies engine authority via `state_hash`. |
| F-5 | CI compiles **only the pure-Rust core** for `aarch64-linux-android` (`ove-time, ove-timeline, ove-media, ove-render, ove-project, ove-conformance`). The libav-linked crates (`ove-decode`, `ove-encode`) and everything above them are **not yet compiled for Android anywhere**. | scripts/ci/run_platform_conformance.sh [4/4]; .github/workflows/ci.yml job `platform-conformance` | Producing the first real Android build of the full decode→render→encode chain is **this client's job**, with an Android libav build — an integration deliverable, not an engine change. |
| F-6 | `add_clip(track, asset, duration, source_in)` **appends at the track end** (position = `track_len`); the engine allocates clip ids; `move_clip` uses post-state index semantics; undo/redo return `bool` and are logged as markers (crash-safe append-only log, kill-9 drill P-2). | engine/ove-engine/src/lib.rs:242-335; PROJECT_FORMAT_SPEC §3, §7 | The UI's placement model is sequential-with-gaps per track; ids come back from the bridge; undo/redo depth is tracked client-side against `state_hash`. |
| F-7 | Durability is engine-native: every command is appended durably; **kill -9 at any point → reopen → replay → hash-equal** (P-2). Snapshots are compaction, not a save action. | PROJECT_FORMAT_SPEC §4, §7 P-2 | No client-side save code; Android process death = reopen + `state_hash` verification. |

## 3. Supported Android integration path (chosen)

**A JNI bridge (`ove-android`, Rust cdylib in this repository under `bridge/`) over the
`ove-engine` library session, plus documented-format reads.**

```
Android UI (Compose, Kotlin)
  → ViewModels / use-cases (Kotlin)
    → OveClient (Kotlin, JNI boundary, single-threaded engine dispatcher)
      → ove-android cdylib (Rust JNI bridge — THIS REPO, a client, engine code untouched)
        → ove-engine session (engine repo, pinned commit 06c9249)
          → ove-decode / ove-encode (libav, LGPL config, Android build supplied by this repo)
```

Why this shape and not the alternatives:

- **vs. bundling `ove-cli` and scraping output**: loses typed errors (F-2), clip layout (F-3),
  and the certified composite export (F-1). Rejected as the primary path.
- **vs. speaking MCP to `ove-mcp`**: same three losses (tools return text, no export tool);
  MCP remains an excellent *agent* surface but is not the richest editor surface.
- **vs. reimplementing engine behavior in Kotlin**: forbidden by this project's charter
  ("the client must not create a second video engine") and by the engine's own rules.
- The bridge crate is a **client of the library** exactly like `ove-cli` is; it lives in this
  repository, pins the engine by commit hash, and ships zero engine code modifications.

Engine sources are fetched at build time by `bridge/fetch-engine.sh` (shallow clone at the
pinned rev, hash-verified, git-ignored) — the engine repo is never copied into this
repository's history and never modified.

## 4. Supported operations (via the bridge, engine-authoritative)

| Operation | Engine interface | Semantics (verified) |
|---|---|---|
| Create project | `Engine::create(dir, tick_axis)` | folder = project; tick axis exact rational |
| Open project | `Engine::open(dir)` | replay + snapshot fold; hash verified on load |
| Project status | `state_hash()`, `uuid()`, `project()` | authority check after every mutation batch |
| Import media | `import_media(path)` | copy into `assets/<blake3>/`, probe → `probe.json`; returns content hash |
| Media metadata | `assets/<hash>/probe.json` (documented format) | container, streams, codec, duration, width/height, color tags, keyframe index, VFR report |
| Add track | `add_track(id, Gap)` | gap-track container (sequential-with-gaps placement) |
| Append clip | `add_clip(track, hash, duration, source_in)` | appends at track end; engine allocates clip id |
| Split | `split(track, clip, at)` | exact rational cut, returns new clip id |
| Resize | `resize(track, clip, duration)` | exact rational duration |
| Move | `move_clip(id, from, to, to_index)` | post-state index semantics |
| Remove | `remove(track, clip)` | exact inverse on undo |
| Undo / Redo | `undo() / redo()` → bool | exact inverses, logged markers, crash-safe |
| State identity | `state_hash()` | BLAKE3-256 canonical state hash, verified after each batch |
| **Export (composite)** | `export_reencode(out, OutputSpec, n_frames)` | render every output frame (deterministic software renderer) → MPEG4 CRF6 bitexact + AAC → MP4. **The certified flagship export path.** |
| Export (segment) | `export_copy(out, hash, start, end)` | keyframe-aligned stream-copy of a source range; snap records reported |
| Export (audio) | `export_wav(out)` | assembled timeline audio → WAV (planar f32 → samples) |
| Per-frame render | `build_render_input(t)` + `render_frame(...)` | deterministic software-composited RGBA frame at timeline time `t` — used for the editor preview |

## 5. Unsupported operations (engine reality — never faked in UI)

**Engine-unsupported (named by the engine itself):**

- Audio retiming / speed changes — typed `AudioRetimeUnsupported` (named gap, ADR-018).
- H.264 **stream-copy** export — typed rejection with recorded capability limits F4/F5
  (codec capability matrix; re-encode routes are the supported path).
- Transitions, text/titles, filters, color grading beyond per-clip opacity/x/y keyframes
  (W8 properties: `opacity`, `x`, `y`, linear/hold) — **no engine feature exists**.
- Hardware/accelerated codecs — engine is a software renderer by design (GPU is an
  EXPERIMENTAL backend, ADR-020, not a client feature).
- Multi-writer / concurrent editing of one project — single-writer v1 (PROJECT_FORMAT_SPEC).
- GPL codec packs — out-of-tree by design (ADR-024 codec-artifact-distribution).

**Engine-supported but not shipped as client UI in v0.1.0 (honest status, not hidden):**

- Keyframe editing UI (opacity/x/y) — engine supports via `Command::SetKeyframes`; the
  v0.1.0 client ships no keyframe editor. No control for it will be shown.
- `export_copy` snap-record surfacing beyond success/failure (internal detail).

## 6. Required adapters / bridges (this repository's build deliverables)

1. **`bridge/` — `ove-android` cdylib**: JNI boundary over `ove-engine`; typed error
   mapping (§8); JSON serialization of project shape for the UI; engine fetched at pinned
   commit by `fetch-engine.sh`.
2. **Android libav**: FFmpeg 7.1.x (same major.minor as the engine's `ffmpeg-sys-next = "7.1"`
   pin) cross-compiled for `aarch64-linux-android` under the **LGPL-compatible
   configuration** (no GPL components), linked into the client build. The engine's
   confinement rule (libav linked only by ove-decode/ove-encode, ADR-015, CI-enforced)
   is preserved: the bridge links only `ove-engine`, which links the adapters.
3. **jniLibs packaging**: `liboveandroid.so` (+ static libav linked in) per ABI;
   the app loads it at startup and surfaces real engine version/commit in Settings.

## 7. Serialization / protocol requirements

- **All times are exact rationals**: `num/den` i64 pairs end-to-end. Kotlin receives
  `(num, den)` longs; **floats are forbidden** (the engine rejects them at load — P-5).
- **Hashes**: asset identity and state hashing are **BLAKE3-256, 64 lowercase hex**
  (normative, PROJECT_FORMAT_SPEC §2). No other hash participates in identity.
- **Project shape JSON** (bridge → UI): tracks `{id, clips:[{id, asset_hash, duration,
  source_in}]}` — a UI projection; the engine remains authoritative (state_hash verified).
- **Errors**: bridge returns structured errors `{kind, message}` from the typed
  `EngineError` taxonomy (§8) — never a bare string parse in the UI.
- **Frames**: RGBA frames cross JNI as direct ByteBuffers (never JSON — the engine's
  own evidence E-006a rules out frame-as-JSON).

## 8. Error model (typed mapping)

| Engine variant (verified) | Client kind | UI treatment |
|---|---|---|
| `Import(_)` | `ImportRejected` | "Unsupported or unreadable media" state with detail |
| `UnknownAsset(_)` | `UnknownAsset` | defensive; project integrity banner |
| `Timeline(_)` | `TimelineError` | inline, e.g. split outside clip bounds |
| `NoPlacement { at }` | `NothingAtTime` | preview/scrub out of coverage |
| `NoAudioStream` | `NoAudioStream` | export audio disabled with explanation |
| `NonExactSampleCut {..}` | `NonSampleExactCut` | snap-to-sample feedback |
| `AudioRetimeUnsupported {..}` | `RetimeUnsupported` | speed controls never shown (§5) |
| `KeyframeValueOutOfRange {..}` | `KeyframeRange` | (keyframe UI deferred, §5) |
| `Encode(_) / Mux(_) / Render(_) / Compile(_)` | `ExportFailed / RenderFailed` | export failure state with typed detail |
| `Project(_)` | `ProjectError` | open/recovery states (P-4 quarantine surfaced) |
| `Seam(_) / Internal(_)` | `EngineInternal` | diagnostics screen, engine info + logs |

Decode/probe input-budget failures (`BeyondDeclaredLimits`) arrive typed from the
adapter layer and are surfaced as "media exceeds declared safe limits" — the RLW-9
security boundary is preserved, never bypassed or weakened by the client.

## 9. Lifecycle requirements

- One engine session per open project, owned by a Kotlin single-threaded dispatcher
  (engine is single-writer v1). No engine calls from arbitrary threads.
- Every mutation is durable the moment the engine call returns (P-2); the app adds no
  "save" concept and no write-back cache of authoritative state.
- Android process death / interruption: reopen from disk, verify `state_hash`, restore UI
  from the project shape + command-log-derived undo depth. Recovery states are first-class.
- Projects are portable folders (hash-addressed assets, R-13: no path relinking).

## 10. Performance considerations (machine-relative, honestly scoped)

- The renderer is the deterministic **software** reference renderer. Per-frame render cost
  on mobile is expected to dominate; preview is engineered as stepped engine rendering at
  an achievable, truthfully displayed rate — never a simulated progress or fake preview.
- Export holds the ADR-023 decoder-session budget (one open per source per export,
  pinned by conformance test); the 5.8× host-side speedup (129.90 s → 22.26 s,
  byte-identical output) is machine-relative and does not transfer numerically to phones.
- Import = full copy into content-addressed assets (dedupe by hash): first import of a
  source doubles its storage inside the project; the SAF staging copy is deleted after
  successful import.

## 11. Storage requirements

- Projects live under the app's private files dir (`…/files/projects/<name>.ove/`) —
  layout exactly PROJECT_FORMAT_SPEC §1; `cache/` + `renders/` are disposable (P-8) and
  are the client's storage-reclaim surface.
- Source media arrives via the Android photo/video picker (SAF); the client stages a
  copy for the engine (engine consumes real paths), then deletes the staging copy after
  the engine's own content-addressed copy exists.
- Exports are written to the project's `renders/` and then surfaced to the user via
  SAF/MediaStore save.

## 12. Security posture (client must not weaken, and does not)

- All engine-side untrusted-input budgets (ADR-022) and decoder input budgets
  (`DECODE_MAX_DIM=16384`, `DECODE_MAX_PIXELS=2^25`, typed `BeyondDeclaredLimits`,
  RLW-9) operate **inside** the engine session the client calls. The client adds no
  bypass, no "just this once" path, no pre-validation that could substitute for the
  engine's boundaries.
- The client's own surfaces (SAF input, export sharing) use platform-scoped storage;
  no world-readable files; no signing secrets or tokens in the repository (CI uses
  GitHub Actions secrets only).

## 13. Known integration limitations (explicit)

1. First Android build of the full libav-linked chain is realized here (F-5) — the engine
   repo itself still compiles only the pure-Rust core for Android in CI. Any Android-only
   build breakage is fixed **in this repository** (bridge/build config), never by editing
   the engine.
2. Preview is stepped engine-rendered frames; it is real engine output but not a
   video-playback-_decoder preview. Continuous full-rate playback is future work.
3. Single ABI (arm64-v8a) is the v0.1.0 build target; x86_64 emulator packaging is a
   build-config follow-up.
4. Undo depth is derived from the client's command accounting against `state_hash`, not
   read from engine internals (no accessor exists on the client surfaces — F-3/F-6).
5. Real-device verification is bounded by hardware availability in the build
   environment; results are recorded truthfully per the client's status vocabulary
   (IMPLEMENTED / INTEGRATED / TESTED / DEVICE-VERIFIED / BLOCKED / UNSUPPORTED BY ENGINE).

## 14. Evidence index (inspected this session)

- `README.md` (repo layout, mission, current phase) · `CHANGELOG.md` · `STATUS.md`
- `docs/specs/PROJECT_FORMAT_SPEC.md` (§1-§7) · `docs/specs/DECODER_SPEC.md`,
  `ENCODER_SPEC.md`, `FRAME_CONTRACT.md` (boundaries referenced)
- `docs/REALWORLD_VALIDATION.md` (§4 certification steps incl. `export_reencode` route)
- `engine/Cargo.toml` (13-crate workspace) · `engine/ove-engine/src/lib.rs`
  (API surface, `EngineError`, export paths) · `engine/ove-cli/src/main.rs` (390 lines,
  full grammar) · `engine/ove-mcp/src/lib.rs` (tool catalogue + get_status)
- `engine/ove-media/src/probe.rs` (`ProbeInfo`/`ProbeStream`/`VideoDetails`/`AudioDetails`)
- `engine/ove-decode/Cargo.toml` + build comments (ffmpeg-sys-next 7.1, bundled vs
  pkg-config, distro 7.1.5 note)
- `scripts/ci/run_platform_conformance.sh` (Android check scope = pure-Rust core)
- `.github/workflows/ci.yml` (5 jobs incl. platform-conformance, libav-confinement)
