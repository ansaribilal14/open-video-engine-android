# ove-android — JNI bridge (engine client, this repository only)

A thin Rust **cdylib** that exposes the `ove-engine` headless session to the Android
application over JNI. It is a **client of the engine library** — exactly what `ove-cli`
and `ove-mcp` are — and contains **zero engine code**.

## Engine dependency pin

| Item | Value |
|---|---|
| Repository | `https://github.com/ansaribilal14/open-video-engine` (READ-ONLY for this client) |
| Pinned commit | **`06c92496f7051f15069663296ec51e88e170fe18`** |
| Relationship | code-identical to tag `v0.2.0` (`4173211`) plus the PR #25 documentation pass |
| Fetch | `sh fetch-engine.sh` → clones into `engine-checkout/` (git-ignored), verifies HEAD == pin |
| libav | FFmpeg 7.1.x, LGPL configuration; Android aarch64 build produced by this repo's build (see `docs/ENGINE_INTEGRATION_AUDIT.md` §6) |

## JNI surface (class `app.ove.studio.engine.OveJni`)

Every method returns a JSON string: `{"ok":true,...}` or
`{"ok":false,"kind":"<typed-kind>","message":"..."}` — the typed `EngineError` taxonomy
mapped at the boundary (see `docs/ENGINE_INTEGRATION_AUDIT.md` §8).

Sessions are single-writer: the bridge holds one engine session; the Kotlin side
serializes all calls through a dedicated engine dispatcher.

## Build

- Host (smoke tests): see `tests/e2e.rs`
- Android: `cargo build --release --target aarch64-linux-android` with the NDK toolchain
  and the Android FFmpeg prefix; output `liboveandroid.so` → `app/src/main/jniLibs/arm64-v8a/`
