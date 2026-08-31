# T13 — Settings bottom sheet with sections (Phase C)

**Status**: Pending  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T12  
**Next**: T14  
**Layer**: L7

## Description

Replace the cramped `AlertDialog` settings with a **ModalBottomSheet** (or dedicated settings route if sheet insufficient) organized into clear sections. Operator can find reliability, battery, and advanced options without scrolling a single undifferentiated list.

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| 2026-08-31 | created | — | Pending | /setup-tasks UI rewrite phase C | |

## Requirements

- [ ] Settings entry: top-bar gear opens **ModalBottomSheet** (M3; Expressive APIs OK after T11.5 — `compose-bom-alpha` / material3 1.5.0-alpha). Do **not** bump AGP/BOM here.
- [ ] Sections with `ListItem` / `HorizontalDivider` headers:

  **Reliability**
  - Start on boot (`AppPreferences.startOnBoot`) — existing
  - Privileged watchdog (`serviceWanted` / Shizuku) — existing toggle + short explanation
  - Link to grant card hint if proxy permission missing (navigate/dismiss to main grant card, not duplicate full grant UI)

  **Battery**
  - Pause libzt node in Doze (`pauseNodeInDoze` or equivalent pref) — existing
  - Request battery optimization exemption — existing `BatteryOptimizationHelper` flow
  - Short copy: what each does for drain vs recovery

  **Links** (shortcut)
  - Row "Manage link profiles" → dismiss sheet, open existing `LinksScreen` overlay

  **Advanced**
  - Link debounce: show current value + "Edit in Links" (debounce slider stays on Links screen per spec — no duplicate slider here unless UX review says otherwise)
  - Debug: copyable package name / node id read-only line (optional, no log export)

- [ ] ViewModel exposes settings state: booleans from `AppPreferences` flows already collected or add `settingsState` sub-state
- [ ] All toggles call existing repository/preference methods — **no new persistence** unless a pref is missing from UI (grep before adding)
- [ ] Sheet dismiss: swipe down + back; state survives dismiss
- [ ] Strings in `strings.xml` with section titles and toggle descriptions

## Non-goals (this task)

- New preference keys without product approval
- Per-app proxy bypass UI
- Always-on VPN system settings deep link
- Network detail changes (T14)
- Motion polish (T15)

## Constraints

- Read `.cursor/rules/compose.mdc`, `.cursor/rules/shizuku.mdc` for watchdog copy accuracy
- Watchdog remains **optional** and **interactive-only** (no background su loop when screen off)
- Do not prompt `VpnService.prepare()` from sheet callbacks

## References

- `app/src/main/java/com/brukb/zerotier/ui/MainScreen.kt` — current settings `AlertDialog`
- `app/src/main/java/com/brukb/zerotier/data/AppPreferences.kt`
- `app/src/main/java/com/brukb/zerotier/ui/MainViewModel.kt` — boot, watchdog, doze prefs
- `docs/PROXY-VPN-PLAN.md` — reliability settings intent

## Implementation Plan

*(Filled by `/task-1-plan`)*

### High-level notes (setup-tasks)

1. New `SettingsBottomSheet.kt` composable; `MainScreen` holds `showSettings` → sheet not dialog.
2. Section composables: `SettingsSection(title) { ... }`.
3. Wire existing ViewModel methods (`setStartOnBoot`, watchdog enable, doze pause, battery exemption).
4. Links shortcut: callback `onOpenLinks` from MainScreen.

## Execution plan (filled by /task-1-plan)

*(empty)*

## Test Plan

- Pure helper tests if any (e.g. section visibility when Shizuku unavailable)
- `make verify`
- Manual walk all toggles persist across process kill

## Acceptance Criteria

- [ ] Settings opens as bottom sheet, not alert dialog
- [ ] All T09 settings toggles still reachable and functional
- [ ] Section headers visible; battery vs reliability visually separated
- [ ] "Manage link profiles" opens Links screen
- [ ] `make verify` green

## Verification

*(Filled by `/task-3-complete`)*

## Manual test (for humans)

1. Open settings → sheet, scroll sections
2. Toggle boot / watchdog / doze pause → kill app → values retained
3. Battery exemption button → system dialog
4. Links shortcut → Links overlay

## Learnings

*(Filled on close-out)*

## Reality notes

### From T12 close-out

- Settings still `SettingsDialog` `AlertDialog` in `MainScreen.kt` — T13 replaces with bottom sheet.
- Reuse `JoinStatusChip` + `joinChipStatus()` patterns from T12; `MaterialExpressiveTheme` already in `ZerotierBTheme`.
