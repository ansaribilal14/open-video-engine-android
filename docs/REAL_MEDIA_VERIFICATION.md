# REAL MEDIA VERIFICATION — OVE Studio Android client

**Date:** 2026-10-02 · **Engine:** `open-video-engine` @ `06c92496f7051f15069663296ec51e88e170fe18`
**Bridge:** ove-android 0.1.0 (the library session the APK loads, driven through the
exact `ops::*` call sequence the JNI shim exposes to Kotlin)

## 1. Certification-grade gate — NASA public-domain source (this build environment)

- **Source identity gate:** `sha256 2d315daf6130366d9a98c9f49716aa263036ba5fef8f241cefff727bc3b4705f`
  — verified byte-exact against the engine's certified §2 reference identity
  (docs/REALWORLD_VALIDATION.md) before the run. 122.88 s, H.264 + AAC, 44100 Hz.
- **Command sequence:** the full client journey through `bridge/tests/e2e.rs`
  (`full_journey_real_media`):
  1. create project (tick axis 48000/1) → `state_hash` returned
  2. add track 1 → `import_media(source.mp4)` → BLAKE3 content hash (64 hex) +
     probe (exact rational duration, streams, WxH, audio presence)
  3. append clips (engine-allocated ids, end-of-track placement)
  4. split / resize / remove — typed engine commands
  5. undo/redo — exact inverses, logged markers
  6. `render_frame` at t=1 s — REAL engine-composited RGBA preview frame
  7. `export_reencode` (the certified composite route) ×2 — **byte-identical
     SHA-256 on the repeat** (client-path determinism evidence)
  8. `export_wav` — real timeline audio assembly
  9. `export_copy` (stream copy) — **typed capability limit surfaced**: H.264
     stream-copy type-rejected ("stream copy v1 supports mpeg4/aac only") — the
     recorded F4/F5 limit arrives through the client as a typed error, as designed
  10. close → reopen → `state_hash` equality (P-1 discipline through the client)
- **Result: PASS (10/10 steps)** — wall time ≈ 26.6 s (two composite exports dominate;
  machine-relative, 2-core sandbox).

## 2. What this proves

- The Android client's ENTIRE call path (create → import → edit → render → export →
  reopen) works against the real engine with real media, with typed errors at the
  boundary and hash-verified authority after every mutation.
- Export artifacts carry the engine's SHA-256; the app independently recomputes and
  compares before claiming success (defense-in-depth at the UI layer).

## 3. What is NOT yet verified (honest boundaries)

| Item | Status | Why |
|---|---|---|
| On-device (arm64) execution of the same journey | BLOCKED — no Android hardware/emulator in the build environment (no `/dev/kvm`) | The aarch64 native build is compile/link-verified; runtime execution requires a device. Robolectric/rendering runs on JVM, not the native engine. |
| Preview rendering performance on a real phone | BLOCKED — same | Host e2e render cost is recorded machine-relative; phone numbers need hardware. |
| AAB generation | VERIFIED via build config only until the first tagged CI run executes | `bundleRelease` is wired; the first CI run produces the artifact. |

## 4. CI media strategy (ci.yml)

- Deterministic **mpeg4+aac fixture** journey (generated locally with ffmpeg, exported
  through the engine) — runs on every push: full pipeline INCLUDING mpeg4 segment
  stream-copy success.
- The **NASA certified-source journey** is env-gated (`OVE_E2E_MEDIA` + identity gate);
  when the media cache is attached to a CI run it runs, otherwise it is recorded as
  skipped — never hidden, never faked.
