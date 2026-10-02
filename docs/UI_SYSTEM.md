# UI SYSTEM — design tokens, Material 3 theme, iconography, motion

## 1. Design research summary (principles applied)

Studied for principles (not for imitation): professional desktop NLEs (dark, dense,
preview-first workspaces; single accent for selection/playhead), Android-native creative
apps (bottom-sheet tools, gesture-first timelines, system pickers), and Material 3
guidance (component semantics, state layers, motion tokens).

Rules adopted:
1. The **preview is sacred** — maximum area, zero chrome overlap.
2. **One accent** (primary) marks selection/playhead; error red is reserved for the
   playhead line only in the editor (industry convention: playhead visibility).
3. **Neutral dark surfaces** in the editor (OLED-friendly, color-accurate preview
   context); light theme supported for home/settings.
4. Density over decoration: 4dp spacing grid, compact M3 density where components allow.
5. Every state is designed (empty/loading/error/progress) — no happy-path-only screens.

## 2. Color tokens (dark = editor default)

| Token | Dark value | Light value | Use |
|---|---|---|---|
| `background` | #0F1014 | #FAFAFC | screen background |
| `surface` | #16171C | #FFFFFF | cards/sheets |
| `surfaceContainer` | #1C1E24 | #F0F0F4 | app bar, timeline well |
| `surfaceContainerHigh` | #24262E | #E6E6EC | clips, elevated |
| `surfaceVariant` | #2A2C36 | #E2E2E8 | ruler, secondary fills |
| `primary` | #7FB2FF | #3659A6 | selection, primary actions |
| `onPrimary` | #0A1E3C | #FFFFFF | on primary fills |
| `secondary` | #9BB4D8 | #4A6080 | secondary text/actions |
| `tertiary` | #8FD0C0 | #2E6B5E | audio-badge accent (only audio semantics) |
| `error` | #FFB4AB | #B3261E | destructive + playhead line |
| `onSurface` | #E4E2E6 | #1B1B1F | primary text |
| `onSurfaceVariant` | #A9AAB4 | #47464F | secondary text |
| `outline` | #3C3F4A | #C4C6CF | hairlines, clip borders |
| `clipVideo` | #2E4A73 | #B9CBEE | video clip fill |
| `clipAudioBadge` | tertiary | tertiary | audio presence badge only |

No gradients, no glassmorphism, no decorative blobs, no accent soup. Exports and
re-encodes use exactly these roles; nothing else introduces color.

## 3. Typography (M3 type scale, system Roboto — no custom fonts)

- `headlineSmall` 24/32 — screen titles only (Home).
- `titleMedium` 16/24 500 — app bar project name, dialogs.
- `bodyMedium` 14/20 — list metadata, explanations.
- `labelLarge` 14/20 500 — buttons, clip context bar.
- `labelSmall` 11/16 500 — time codes (monospaced figures via `FontFeature.tnum`).

## 4. Shape scale

- `small` 8dp — chips, badges.
- `medium` 12dp — clips, cards, text fields.
- `large` 20dp — bottom sheets, dialogs.
- `full` — selection pill, FAB (single FAB in the whole app: New project on empty home).

No radius beyond the scale; no card-inside-card.

## 5. Spacing (4dp grid)

`space.xs=4 · space.sm=8 · space.md=16 · space.lg=24 · space.xl=32`
Screen margin 16dp; app-bar↔preview 8dp; preview↔transport 0 (flush); transport↔timeline
8dp; timeline track pitch 48dp video / 40dp audio-visualized badge row; clip margin 2dp.

## 6. Elevation

Flat by default (surface tones carry hierarchy). Elevation used only: bottom sheet
(level 1), dialogs (level 3), dragged clip (level 3 shadow). No random shadows.

## 7. Iconography

**Material Symbols (Rounded)** exclusively — one family, consistent optical weight:
`add, movie, video_library, play_arrow, pause, skip_next, skip_previous, content_cut,
delete, undo, redo, export/upload, settings, info, warning, check_circle, error,
graphic_eq, timer, folder, create_new_folder, close`.

Icons ≤ 24dp in bars, 20dp inline badges; every icon has a content description; no
decorative icons in empty space.

## 8. Motion (Material standard easing only)

- Screen transition: 250ms `EmphasizedDecelerate` fade-through.
- Sheet: 300ms, slide-up, `standard`.
- Clip selection: 150ms border+ring; context bar: 200ms expand.
- Gesture rejection: 250ms spring-back.
- Export running: indeterminate `LinearProgressIndicator` — real work only.
- No looping decorative animations anywhere.

## 9. Component usage (semantic, not decorative)

- `TopAppBar` — screens; `BottomAppBar`-style context bar for clip ops.
- `AlertDialog` — destructive confirmations, new-project input.
- `ModalBottomSheet` — export only.
- `LinearProgressIndicator` (indeterminate) — import staging, export running.
- `Snackbar` — non-blocking failures (gesture rejections, copy results).
- No badges/chips without meaning; no statistic tiles; no hero sections.

## 10. Dark/light behavior

Editor surfaces always dark (preview fidelity decision, recorded). Home/Settings follow
the system theme via the token table above. The editor's forced-dark is a documented
product decision, not an oversight.
