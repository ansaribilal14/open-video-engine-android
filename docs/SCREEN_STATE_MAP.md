# SCREEN STATE MAP — OVE Studio

Every state is designed and implemented. Status vocabulary per PRODUCT_SPEC §4.

## 1. Projects Home

| State | Trigger | Presentation |
|---|---|---|
| `firstLaunch` | no projects dir entry | Illustration-free empty state: "No projects yet", single primary button **New project** |
| `projectsAvailable` | ≥1 project | List rows: name · clip count · duration · last opened; tap → Editor |
| `loading` | scan in progress | Linear indeterminate under app bar |
| `projectError` | unreadable project folder | Row marked broken; tap → recovery dialog (open anyway / delete) |
| `deleteConfirm` | row long-press → delete | AlertDialog naming the project; confirms permanent removal |
| `newProjectDialog` | primary action | Name field + create; duplicate-name guarded; engine `create` failure surfaces typed |

## 2. Import flow (from editor)

| State | Trigger | Presentation |
|---|---|---|
| `picking` | Import button | Android `PickVisualMedia` (video+audio+image where supported by engine probe) |
| `staging` | URI selected | Copy to app cache; indeterminate progress; cancelable (client-owned copy) |
| `importing` | staged | Engine `import_media` running; modal busy with honest copy "Importing into engine…"; no cancel (engine has none) — button never shown |
| `success` | hash returned | Clip appended to selected track; metadata row (duration, WxH, codec, audio) from probe.json |
| `unsupportedMedia` | typed `ImportRejected` / `BeyondDeclaredLimits` | Error card: category + explanation ("exceeds declared safe limits" for RLW-9 rejections); Try another file |
| `storageError` | disk full / IO | Typed storage card; suggests freeing space |

## 3. Editor

| State | Trigger | Presentation |
|---|---|---|
| `editorEmpty` | project with no clips | Timeline shows track lane + centered hint "Import media to start"; Import is the primary action |
| `populated` | ≥1 clip | Timeline + preview live; preview = engine frame at playhead |
| `clipSelected` | tap clip | Selection ring + handles + context bar (Split at playhead · Delete) |
| `scrubbing` | ruler/preview drag | Playhead follows; time label updates; preview frame follows (stepped engine render) |
| `trimming` | handle drag | Clip width follows; source-bounds clamp; on release engine `resize` |
| `gestureRejected` | typed engine error on commit | Spring-back animation + Snackbar with typed reason |
| `engineBusy` | export only | Modal sheet, editor input locked (single-writer session) |
| `recovery` | reopen hash mismatch | Full-screen recovery card: expected vs actual hash, open-read-only / close; never auto-repairs |
| `undoDepthZero` / `redoDepthZero` | — | Undo/redo buttons disabled (true disabled states) |

## 4. Export sheet

| State | Trigger | Presentation |
|---|---|---|
| `options` | Export button | MP4 composite (MPEG4+AAC) primary · WAV mixdown secondary · segment copy (advanced); each with real format description |
| `running` | start | Honest indeterminate progress + elapsed time + "the engine does not support cancel" note; no cancel button |
| `success` | engine returns | Path, size, client-computed SHA-256, Open folder (SAF) |
| `failure` | typed error | Category card (`EncodeFailed`, `MuxFailed`, `RenderFailed`, `NoAudioStream`…) + retry |

## 5. Settings / Diagnostics

| State | Content |
|---|---|
| Engine info | engine repo, pinned commit, bridge version, libav build config |
| Diagnostics | last engine errors (typed), project storage usage, cache clear (P-8 disposable rule) |
| About | client version, licenses (MIT OR Apache-2.0; LGPL notice for libav) |
