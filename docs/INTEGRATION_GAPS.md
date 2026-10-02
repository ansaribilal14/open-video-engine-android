# INTEGRATION GAPS — engine interface limitations affecting the Android client

Per the takeover charter: when an engine API limitation blocks an Android requirement,
the client documents it, names the exact interface involved, and **does not alter the
engine**. This report is the running record.

| # | Requirement | Exact interface involved | Gap | Client treatment | Status |
|---|---|---|---|---|---|
| 1 | Full composite export reachable from a client | `ove-engine::Engine::export_reencode` — **library-only**; `ove-cli` batch grammar has no `export-reencode` verb; `ove-mcp` tool catalogue has no export tool | The engine's own certified flagship export (REALWORLD_VALIDATION §4 step 7) is unreachable from CLI/MCP | Client uses the **library** via the JNI bridge (`ove-android`), the same session `ove-cli` itself consumes | RESOLVED via bridge |
| 2 | Clip layout for timeline UI | CLI/MCP `status`/`get_status` return only hashes/ids (ove-cli main.rs:119, ove-mcp lib.rs:356) | No client surface returns clips/durations/positions | Bridge reads library `project()` accessors; UI projection verified by `state_hash` | RESOLVED via bridge |
| 3 | Typed error handling | `EngineError` taxonomy is library-only; CLI/MCP flatten to strings | String scraping is fragile | Bridge maps typed variants → structured errors | RESOLVED via bridge |
| 4 | Audio during preview playback | `assemble_timeline_audio` returns a **complete** mix (planar f32); no streaming/segment API | Cannot stream audio synced to scrubbed preview without full-mix-per-position cost | v0.1.0 preview is silent video frames (real engine output); audio audible only in WAV export | OPEN — client UX limitation, honestly surfaced in UI copy |
| 5 | Undo depth indicator | `Project::undo_depth()` is library-public but absent from CLI/MCP status output | CLI/MCP-only clients can't show depth | Bridge reads `project().undo_depth()` directly (library session) | RESOLVED via bridge |
| 6 | Export progress/cancel | `export_reencode` is synchronous, no progress callback, no cancellation | Cannot show percent or offer cancel | Honest indeterminate progress + "cancel not supported by engine" copy; no fake percent | OPEN — UX limitation surfaced truthfully |
| 7 | Clip thumbnails / waveform data | Probe sidecar has streams/index but no frame-extract verb at client surfaces | Timeline thumbnails would need per-clip `render_frame` calls (cost) or platform extraction (fake engine truth) | v0.1.0: labeled tiles (name + duration + audio badge), no thumbnails, no drawn waveforms | OPEN — documented product limit |
| 8 | First Android build of libav-linked chain | CI compiles pure-Rust core only for aarch64 (run_platform_conformance.sh [4/4]) | Engine repo itself has no Android libav leg | Client's build supplies Android FFmpeg 7.1.x (LGPL config); any Android-only breakage is fixed client-side, engine untouched | OWNED BY CLIENT |
| 9 | New-project naming/registry | Engine has no project-listing API (projects are folders) | Client maintains its own project registry under app storage | Documented client responsibility; never presented as engine feature | CLIENT-SIDE BY DESIGN |

**Rule reaffirmed:** none of these justify engine modification. Items 1–3 are the reason
the bridge consumes the library session; items 4–7 are surfaced as honest product
limitations; item 8 is this repository's build responsibility.
