# VISUAL RESEARCH — principles before pixels

## Method

Studied established interfaces for **principles**, documented below with the reason each
was adopted or rejected. No product was copied; every decision maps to a need of this
specific product: a dense, touch-first, engine-backed editing workspace.

## 1. Professional desktop/mobile NLEs (e.g., timeline-driven editors)

**Observed patterns** (principles only):
- Dark, neutral workspace: the frame's colors must be judged against a neutral ground;
  chrome recedes, content advances. → **Adopted**: editor is forced-dark with neutral
  surfaces (UI_SYSTEM §10).
- Playhead is the highest-contrast element in the timeline, commonly red/accent.
  → **Adopted**: error-red playhead line, 2dp, full-height.
- Selection is a single accent — one thing wins at a time. → **Adopted**: primary-blue
  selection ring + handles; nothing else uses that accent within the editor.
- Controls are grouped by user intent (transport near preview; edit ops near timeline).
  → **Adopted**: transport directly under preview; context bar directly above timeline.

## 2. Android-native creative apps

**Observed patterns**: bottom sheets for modal tasks; system pickers instead of custom
browsers; gesture-first timelines with zoom anchoring; honest busy states during long
operations. → **Adopted**: `ModalBottomSheet` export; `PickVisualMedia`; pinch-zoom
anchored at playhead; modal engine-busy sheet with truthful copy (no cancel button —
the engine has no cancellation, faking one is forbidden).

## 3. Material 3 guidance

- Components are used by semantic role (TopAppBar, ModalBottomSheet, AlertDialog,
  LinearProgressIndicator) — not restyled arbitrarily; M3 state layers handle
  pressed/disabled.
- Motion tokens (`EmphasizedDecelerate`, `standard`) at 150–300ms.
- Shape/elevation/typography scales adopted wholesale; density tightened in the
  workspace per M3 compact-density guidance.

## 4. Explicitly rejected patterns (AI-slop ban list compliance)

| Rejected | Reason |
|---|---|
| Gradients / glass blobs | zero function in an editing workspace |
| Card-inside-card dashboards | an editor is a workspace, not a SaaS page |
| Hero text / onboarding illustrations | steals preview area; patronizing |
| Fake statistics ("10 projects created!") | violates honesty charter |
| Custom mixed icon families | incoherent optical weight |
| Pill-ification of every control | semantic components first |
| Confetti/success animations | decorative motion in a tool |

## 5. Visual hierarchy per screen (primary → destructive)

- **Home**: primary = New project; secondary = project rows; destructive = delete
  (guarded).
- **Editor**: primary = preview+timeline (content); secondary = transport, undo/redo,
  import; destructive = delete clip / delete project (guarded, named).
- **Export**: primary = start MP4 composite; secondary = WAV / segment copy;
  destructive = none.

## 6. Decision log (every major decision has a reason)

| Decision | Reason |
|---|---|
| Editor forced dark | color judgment + OLED + NLE convention |
| Roboto only, tnum for timecodes | system coherence; tabular time jitter-free |
| Red reserved: playhead + destructive | single-scan legibility of "now" |
| Blue reserved: selection | one accent wins (NLE principle) |
| Teal reserved: audio badge | audio semantics need a disambiguator, nothing else |
| No clip thumbnails v0.1.0 | engine thumbnails not exposed as a cheap query; placeholder frames would be fake previews — honest tiles instead (name+duration+audio badge) |
| No waveform rendering | engine exposes no per-clip PCM surface; a drawn waveform would be fake data |
| Flat elevation, hairline outlines | density without noise |
