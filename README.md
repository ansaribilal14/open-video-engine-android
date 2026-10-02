# Open Video Engine — Android Client

A production Android video-editor application that consumes the existing
[Open Video Engine (OVE)](https://github.com/ansaribilal14/open-video-engine) as an
**independent, read-only dependency**.

> **Repository isolation.** This repository does not modify the engine repository, does
> not open PRs against it, and does not copy its sources into its own history. The
> engine is pinned by exact commit hash and fetched at build time. See
> [`docs/ENGINE_INTEGRATION_AUDIT.md`](docs/ENGINE_INTEGRATION_AUDIT.md).

- Engine consumed: `ansaribilal14/open-video-engine` @ pinned commit (see `bridge/README.md`)
- Client versioning: independent (`v0.1.0`…), always paired with the exact engine commit
- Status vocabulary is strict: IMPLEMENTED / INTEGRATED / TESTED / DEVICE-VERIFIED /
  BLOCKED / UNSUPPORTED BY ENGINE — no feature is displayed as supported unless the
  engine actually performs it.
