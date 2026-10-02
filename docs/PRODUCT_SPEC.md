# PRODUCT SPEC — OVE Studio (Android client of Open Video Engine)

**Version:** 0.1.0 (client) · **Engine dependency:** `open-video-engine` @ pinned commit
(see `bridge/README.md`) · **Date:** 2026-10-02

## 1. Product statement

OVE Studio is a serious mobile video editor powered by the Open Video Engine. The phone
provides the touch-first editing workspace; **the engine performs every media operation**
(import, probing, editing state, undo/redo, rendering, exporting). The app is a client,
never a second engine. Every visible control maps to a real engine operation; nothing is
simulated, and anything the engine does not support is never shown as a control.

## 2. Non-goals (v0.1.0)

- No transitions, titles, filters, stickers, speed ramps, multi-cam — the engine does not
  have them; showing controls for them would be fake UI.
- No audio retime/speed controls — typed engine gap (`AudioRetimeUnsupported`, ADR-018).
- No keyframe editor — the engine supports opacity/x/y keyframes (W8), the v0.1.0 client
  does not ship the editor UI. Listed under "not shipped in this release", never "coming".
- No background export service, no cancel of a running export — the engine has no
  cancellation interface; showing a cancel button would be fake.
- No account/cloud/social features.

## 3. Core user journeys

### J1 — First launch → first edit
1. Projects home shows the empty state ("No projects yet") with one primary action:
   **New project**.
2. New project → name + confirmation → editor opens empty (engine project created on
   disk in the documented project format; state hash visible in diagnostics).
3. **Import video** → Android photo/video picker → staging copy → engine `import_media`
   (progress = real work; typed failures land in "unsupported media" state).
4. Clip appended to the main track at the playhead-agnostic track end (engine semantics),
   timeline shows it; probe metadata (duration, resolution, codec, audio presence) is
   real, read from the engine's `probe.json`.
5. Preview shows the engine-rendered frame at the playhead (real composite output).

### J2 — Editing
- Select a clip → context bar offers **exactly** what the engine supports:
  Split / Delete / Move (track) / Resize (trim handles on the clip).
- Trim handles drive engine `resize`; split at playhead drives engine `split`; delete
  drives engine `remove`. Undo/redo use the engine's exact inverses.
- Every mutation batch is verified by the engine's `state_hash`.

### J3 — Export
- Export sheet offers the engine's real routes only:
  **MP4 (composite re-encode: MPEG4 + AAC)** — the certified flagship path;
  **WAV (timeline audio mixdown)**.
- Progress is an honest, real-work indicator (no fake percent, no cancel button — §2).
- On success: output path, size, and a client-computed SHA-256 of the artifact; on
  failure: the typed engine error category and human explanation.

### J4 — Recovery
- Projects are engine-durable (kill-safe append-only log, P-2). After process death the
  app reopens the project, verifies the state hash, and restores the timeline; a hash
  mismatch surfaces a recovery state instead of silently "fixing" anything.

## 4. Functional inventory (truth-annotated)

| Capability | Status | Performed by |
|---|---|---|
| Project create/open/delete (delete = client dir removal with confirmation) | IMPLEMENTED | App + engine project format |
| Import video/audio media (mp4/mov/mkv/wav per Android libav build) | INTEGRATED | Engine `import_media` |
| Media metadata (duration, W×H, codec, audio presence) | INTEGRATED | Engine probe (documented `probe.json`) |
| Append clip / split / resize / move / remove | INTEGRATED | Engine commands |
| Undo / redo | INTEGRATED | Engine exact inverses |
| Timeline state, playhead, zoom, selection | IMPLEMENTED | App (UI projection; engine-verified) |
| Preview frame at playhead | INTEGRATED | Engine `render_frame` (software renderer) |
| Export composite MP4 (MPEG4+AAC) | INTEGRATED | Engine `export_reencode` (certified path) |
| Export WAV mixdown | INTEGRATED | Engine `export_wav` |
| Export source-segment stream copy | INTEGRATED (advanced sheet) | Engine `export_copy` |
| Keyframes (opacity/x/y) | UNSUPPORTED BY CLIENT UI (engine-supported) | — |
| Transitions / titles / effects / speed | UNSUPPORTED BY ENGINE | — |
| Cancel running export | UNSUPPORTED BY ENGINE | — |
| Continuous-audio playback preview | NOT SHIPPED (see INTEGRATION_GAPS #4) | — |

## 5. Quality bar

- Touch targets ≥ 48dp for all primary controls; trim handles ≥ 44dp with precision
  magnification feedback.
- No placeholder buttons, no dead controls, no fake progress, no simulated exports, no
  decorative UI that does not serve editing.
- Material 3, real components, centralized design tokens (see `UI_SYSTEM.md`).
- Accessibility: content descriptions on all icons, semantics for timeline elements,
  contrast-checked palette in both themes.

## 6. Platform envelope

- `minSdk 26`, `targetSdk 35`, ABI `arm64-v8a` (v0.1.0), portrait-first (editor locked
  portrait in v0.1.0 — documented decision), dark theme default for editing surfaces.
- Storage: app-private project folders; exports surfaced via SAF/MediaStore.
