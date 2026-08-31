# T15 — UI polish: motion, empty states, copy actions (Phase E)

**Status**: Pending  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T14  
**Next**: T10  
**Layer**: L7

## Description

Final UX pass on the T11–T14 surfaces: consistent **empty states**, light **motion** (stable M3 plus Expressive `MotionScheme` if already wired in T12 theme), **copy-to-clipboard** for node ID and ADB grant command, scroll/layout fixes on small screens. Cohesive "operator dashboard" feel. Toolchain/BOM already set in T11.5 — do not bump AGP/BOM here.

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| 2026-08-31 | created | — | Pending | /setup-tasks UI rewrite phase E | |

## Requirements

- [ ] **Empty states**:
  - No networks in Room → illustrated/text card + CTA to add network (existing add flow)
  - No saved Wi-Fi links → Links screen message (may already exist; align copy with hero)
  - Runtime OFF → hero shows calm stopped state, not error styling
- [ ] **Copy actions**:
  - Node ID: tap icon or long-press → `ClipboardManager` + brief snackbar "Copied"
  - ADB grant command on grant card: ensure copy works (T09); snackbar feedback
  - Network ID on detail screen: copy short ID
- [ ] **Motion** (stable APIs only):
  - `AnimatedVisibility` for consent banner, grant card, applying spinner region
  - `animateContentSize()` on hero when status line length changes
  - Chip crossfade JOINING → OK via `AnimatedContent` optional — no shared-element transitions
- [ ] **Layout**:
  - Main list + hero scroll together; no double scrollbars
  - Bottom sheet settings safe on gesture nav / small height
  - Network detail sections use consistent spacing (8/16 dp rhythm from theme)
- [ ] **Accessibility**: content descriptions on icon-only buttons (copy, pin, settings)
- [ ] Strings for snackbars and empty states in `strings.xml`

## Non-goals (this task)

- Lottie / custom illustrations
- Navigation suite / adaptive list-detail pane
- Compose BOM / AGP bump (T11.5 already did this)
- Haptic feedback unless trivial one-liner

## Constraints

- Read `.cursor/rules/compose.mdc`. Do **not** bump BOM/AGP (T11.5). Expressive motion OK if T12 already opted `MaterialExpressiveTheme`.
- Motion must not block interaction or delay status updates
- Copy must not include secrets (`identity.secret`)

## References

- All T11–T14 UI files
- `app/src/main/java/com/brukb/zerotier/ui/MainScreen.kt`
- `app/src/main/java/com/brukb/zerotier/ui/GrantSecureSettingsCard.kt`
- Material 3 SnackbarHost pattern in Compose

## Implementation Plan

*(Filled by `/task-1-plan`)*

### High-level notes (setup-tasks)

1. Add `SnackbarHost` to `MainActivity` scaffold if missing.
2. `CopyableMonoText` small reusable composable.
3. Pass through empty-state checks in `MainScreen` networks list.
4. Audit a11y on new icon buttons from T12–T14.

## Execution plan (filled by /task-1-plan)

*(empty)*

## Test Plan

- Unit tests for any new pure format helpers only
- `make verify`
- Manual on small phone + foldable if available

## Acceptance Criteria

- [ ] Zero networks shows friendly empty state with add action
- [ ] Node ID copy works with snackbar
- [ ] No jank on JOINING→OK transition; hero resizes smoothly
- [ ] TalkBack reads copy/pin/settings buttons
- [ ] `make verify` green
- [ ] Full A–E manual walk documented in task Verification

## Verification

*(Filled by `/task-3-complete`)*

## Manual test (for humans)

Full A–E regression walk:

1. **A/B**: PROXY + VPN status chips and hero lifecycle
2. **C**: Settings sheet sections and toggles
3. **D**: Network detail live routes/DNS
4. **E**: Empty state, copy node ID, scroll on small display

```bash
make verify
./gradlew :app:installDebug
```

## Learnings

*(Filled on close-out)*

## Reality notes

### From T12 close-out

- `MaterialExpressiveTheme` is wired in `ZerotierBTheme` (T12). Motion polish can use Expressive APIs without another theme change.
- Copy node ID on hero is T15 — hero already shows monospace full ID.

### From T13 close-out

- Settings sheet (`SettingsBottomSheet`) shipped with read-only package/node ID in Advanced — T15 adds clipboard + snackbar there and on hero.
- `rememberModalBottomSheetState` deprecation — optional migrate to `rememberBottomSheetState` during T15 layout pass.
