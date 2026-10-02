# UX ARCHITECTURE — OVE Studio

## 1. Information architecture

```
Projects Home
├── Project list (recent-first)
├── New project (dialog: name)
├── Overflow → Settings / Diagnostics
└── → EDITOR (full-screen workspace)
      ├── Preview pane (engine-rendered frame)
      ├── Transport bar (frame-step, play, time display)
      ├── Timeline (tracks / clips / playhead / zoom)
      ├── Clip context bar (selection-scoped engine actions)
      ├── App bar: Undo · Redo · Import · Export
      └── Export sheet (bottom sheet)
```

Two screens. One modal sheet. No deep hierarchies: an editor's value is density and
predictability, not navigation depth.

## 2. Navigation rules

- Home ⇄ Editor: forward/back with state preservation; editor state survives process
  death via engine durability (PROJECT_FORMAT_SPEC P-2).
- Export sheet blocks the editor while the engine works (single-writer session).
- Back from editor never loses a mutation: every mutation is already durable in the
  engine's append-only log the moment its call returns.
- Destructive actions (delete project, delete clip) require confirmation; clip delete
  also previews its undo-ability.

## 3. Editor layout (portrait phone, 360×800dp reference)

| Zone | Height | Content |
|---|---|---|
| App bar | 56dp | project name · undo · redo · import · export |
| Preview | ~42% | engine-rendered frame, letterboxed, safe-area aware |
| Transport | 48dp | ⏮ step −1 frame · play/pause · step +1 frame · exact time `mm:ss.mmm` |
| Timeline | ~34% | ruler · video track(s) · clip ops affordances · playhead |
| Clip context bar | 56dp, conditional | appears only when a clip is selected: Split at playhead · Delete · (move via drag) |

The preview is the largest surface because it is the ground truth of the edit (real
engine composite). No decorative elements occupy editing space.

## 4. Timeline interaction model

- **Zoom**: pinch, 0.02–2.0 s-per-100dp continuous; zoom anchors at playhead.
- **Scroll**: horizontal drag on empty space; playhead may be pinned-center off.
- **Scrub**: drag on ruler or preview; time snaps to the project tick axis (exact
  rational, never float).
- **Select**: tap clip → selection ring + context bar; tap empty → deselect.
- **Trim**: drag clip edge handles → live width feedback → on release, engine `resize`
  with exact rational duration; handles enforce source bounds (probe duration).
- **Move**: long-press + drag vertical → track change (engine `move_clip` post-state
  index); horizontal drag reorders via engine semantics.
- All gesture results are engine-verified (state hash) before the UI commits them;
  rejected gestures (typed engine errors) animate back and explain themselves.

## 5. State design (every state is designed; see SCREEN_STATE_MAP.md)

Home: empty · loading · populated · error.
Import: picking · staging · importing (real work) · success · unsupported-media ·
storage-error.
Editor: empty (no clips) · populated · clip-selected · gesture-rejected (inline
explanation) · engine-busy (modal, export only) · recovery (hash mismatch).
Export: sheet · running (honest indeterminate) · success (path+size+sha256) · failure
(typed category).

## 6. Touch & accessibility

- Minimum target 48dp; timeline handles 44dp + magnified drag feedback.
- All icons carry `contentDescription`; timeline clips expose semantics
  ("clip 2, 3.5 seconds, video+audio").
- Destructive actions: confirm dialog with the project/clip name spelled out.
- Motion: ≤ 250ms, Material standard easing; no gratuitous animation in the workspace.

## 7. Orientation & window sizes

- v0.1.0 locks the editor to portrait (decision recorded in PRODUCT_SPEC §6): the
  vertical stack preview→transport→timeline is the phone-first editing layout.
- Small phones (≤ 360dp): timeline tracks compress to 40dp, context bar overlays.
- Large phones/tables of space go to the preview, never to padding.
