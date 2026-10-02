# OVE Studio — Android client of Open Video Engine

A production Android video-editor application that consumes the existing
[Open Video Engine (OVE)](https://github.com/ansaribilal14/open-video-engine) as an
**independent, read-only dependency**.

> **Repository isolation.** This repository does not modify the engine repository,
> does not open PRs against it, and does not copy its sources into its own history.
> The engine is pinned by exact commit hash and fetched at build time. See
> [`docs/ENGINE_INTEGRATION_AUDIT.md`](docs/ENGINE_INTEGRATION_AUDIT.md).

## What it is

OVE Studio is a real mobile video editor: project home, media import (system picker),
a touch timeline with trim handles and pinch zoom, exact-rational time display,
undo/redo, and REAL exports through the engine's certified composite re-encode route.
Every displayed capability is performed by the engine; nothing is simulated.
The strict status vocabulary (IMPLEMENTED / INTEGRATED / TESTED / DEVICE-VERIFIED /
BLOCKED / UNSUPPORTED BY ENGINE) is enforced across docs and UI — see
[`docs/PRODUCT_SPEC.md`](docs/PRODUCT_SPEC.md) §4 for the full truth-annotated inventory.

## Architecture (engine-first)

```
Compose UI (Material 3, design tokens in ui/theme)
  → ViewModels (editor / home / export state machines)
    → OveClient (single-writer engine dispatcher)
      → ove-android cdylib  (JNI bridge — THIS REPO; a client, zero engine code)
        → ove-engine session (pinned engine commit)
          → ove-decode / ove-encode (FFmpeg 7.1.1, LGPL config, arm64)
```

- Engine pin: `06c92496f7051f15069663296ec51e88e170fe18` (code-identical to `v0.2.0`
  plus the documentation pass), enforced by `bridge/fetch-engine.sh`.
- Versioning is independent: this client is `v0.1.0` and always records the exact
  engine commit it consumes (Settings screen + `build-metadata.yml` in releases).

## Build

Prerequisites: JDK 17, Android SDK (API 35) + NDK r27c, Rust with
`aarch64-linux-android` target, `libclang`.

```sh
# 1. Android libav (LGPL config, aarch64) — scripts/ffmpeg-android.sh
NDK=$ANDROID_NDK_HOME SRC=/tmp/ffsrc PREFIX=/tmp/ffmpega64 sh scripts/ffmpeg-android.sh

# 2. JNI bridge (fetches the pinned engine, builds cargo, packages jniLibs)
NDK=$ANDROID_NDK_HOME FFPREFIX=/tmp/ffmpega64 sh scripts/build-bridge-android.sh

# 3. App
gradle assembleDebug          # debug APK
gradle assembleRelease bundleRelease
```

CI does all of this automatically: `.github/workflows/ci.yml` (tests + fixture e2e),
`android-build.yml` (native build + APK/AAB + SHA256SUMS), `release.yml` (tag →
GitHub Release). **No signing secrets live in this repository** — release signing
wires through GitHub Actions secrets only.

## Tests

- `bridge/tests/e2e.rs` — the client's full journey against the engine library
  (create → import → edits → undo/redo → render → export determinism → reopen hash
  equality). Runs with a deterministic mpeg4 fixture in CI; the certification-grade
  NASA source journey is env-gated and recorded in
  [`docs/REAL_MEDIA_VERIFICATION.md`](docs/REAL_MEDIA_VERIFICATION.md).
- Kotlin unit tests — exact rational arithmetic and timecode behavior
  (`app/src/test/…`).
- Integration-gap inventory: [`docs/INTEGRATION_GAPS.md`](docs/INTEGRATION_GAPS.md)
  — what the engine does not yet expose to clients, each item named at the exact
  interface, with the client's honest treatment.

## Licensing

- This client: MIT OR Apache-2.0.
- Engine: MIT OR Apache-2.0 (own code); libav linked as FFmpeg 7.1.1 under the
  LGPL v2.1+ configuration, confined by the engine's CI to its adapter crates
  (ADR-015). No GPL components are compiled in.
