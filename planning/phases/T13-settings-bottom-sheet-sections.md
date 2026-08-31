# T13 — Settings bottom sheet with sections (Phase C)

**Status**: Planned  
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
| 2026-08-31 | planned | Pending | Planned | /task-1-plan — ModalBottomSheet replaces SettingsDialog | |

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

**Date:** 2026-08-31  
**Codebase snapshot:** branch `T13-settings-bottom-sheet-sections` at `3885d47` (libzt crash fix + T12). AGP 9.2.1, `compose-bom-alpha:2026.08.01`, material3 1.5.0-alpha. Settings still private `SettingsDialog` `AlertDialog` in `MainScreen.kt:422–526`. No `ModalBottomSheet` anywhere in `app/`. `MaterialExpressiveTheme` already in `ZerotierBTheme` (T12).  
**Execute model:** small (lift existing dialog rows; no new prefs / ViewModel layer)

### Context for executor

**Goal:** Replace cramped settings `AlertDialog` with a **ModalBottomSheet** organized into Reliability / Battery / Links / Advanced sections. All T09 toggles stay reachable and wired through existing `MainViewModel` + `AppPreferences` methods — **no new persistence**. Sheet dismisses on swipe/back; preference values survive dismiss. Links shortcut opens existing `LinksScreen` overlay. Grant hint dismisses sheet so user sees main `GrantSecureSettingsCard` (no duplicate grant UI).

**Resolved ambiguities (do not reopen):**

| Question | Pick |
| -------- | ---- |
| `serviceWanted` in spec | **Does not exist.** Use `privilegedWatchdogEnabled` / `setPrivilegedWatchdogEnabled`. |
| Separate `settingsState` in VM | **Skip.** Reuse `uiState` fields already collected (`startOnBoot`, `privilegedWatchdogEnabled`, `pauseNodeInDoze`, `linkDebounceMs`, `proxy.hasSecureSettingsPermission`). |
| Debounce slider in settings | **No.** Read-only value in Advanced + "Edit in Links" → dismiss sheet + open `LinksScreen`. |
| Grant UI in sheet | **Hint row only** when `showGrant` predicate true; dismiss sheet, do not embed `GrantSecureSettingsCard`. |
| Battery status refresh | Recompute `BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)` when sheet opens (`LaunchedEffect` or pass from parent each composition — same as dialog today). |
| First bottom sheet in app | `@OptIn(ExperimentalMaterial3Api::class)` on sheet composable; follow M3 `ModalBottomSheet` + `rememberModalBottomSheetState(skipPartiallyExpanded = true)`. |
| Mode-entry battery dialog | **Leave** `showBatteryOptDialog` `AlertDialog` in `MainScreen.kt:239–258` — out of scope. |

**Key files**

| Path | Action |
| ---- | ------ |
| `app/src/main/java/com/brukb/zerotier/ui/SettingsBottomSheet.kt` | **new** — sheet + `SettingsSection` helper |
| `app/src/main/java/com/brukb/zerotier/ui/MainScreen.kt` | Mount sheet; delete `SettingsDialog`; wire callbacks |
| `app/src/main/res/values/strings.xml` | Section titles, links row, grant hint, advanced copy |
| `app/src/main/java/com/brukb/zerotier/ui/MainViewModel.kt` | **no changes** unless compile forces import cleanup |
| `app/src/main/java/com/brukb/zerotier/data/AppPreferences.kt` | **no changes** |
| `app/src/main/java/com/brukb/zerotier/ui/LinksScreen.kt` | **no changes** (debounce slider stays here) |

**Invariants (`.cursor/rules/compose.mdc`, `shizuku.mdc`)**

- Do **not** call `VpnService.prepare()` from sheet callbacks.
- Watchdog remains optional; `setPrivilegedWatchdogEnabled` returns `false` when Shizuku unavailable — show inline error (same as dialog).
- Do not bump AGP / BOM / Kotlin.
- Mode changes still only via existing VM/orchestrator paths — sheet has **no** global mode control.
- `GrantSecureSettingsCard` stays on main scroll; sheet only hints toward it.

**Data already shipped — reuse**

| API | Location |
| --- | -------- |
| `setStartOnBoot(Boolean)` | `MainViewModel:178` |
| `setPrivilegedWatchdogEnabled(Boolean): Boolean` | `MainViewModel:184` |
| `setPauseNodeInDoze(Boolean)` | `MainViewModel:199` |
| `setShowLinks(Boolean)` | `MainViewModel:111` |
| `setLinkDebounceMs(Int)` | `MainViewModel:247` (Links only; settings shows read-only) |
| `nodeId(): String?` | `MainViewModel:294` — Advanced debug row |
| `uiState.linkDebounceMs` | default 5000, clamped 3000–15000 in `AppPreferences` |
| `BatteryOptimizationHelper` | `MainActivity.openBatteryOptimizationSettings()` / `openBatteryOptimizationSettingsPage()` |
| `showGrant` predicate | `MainScreen.kt:160–162` |

### Steps

1. **Strings** — add to `strings.xml`:
   - `settings_section_reliability`, `settings_section_battery`, `settings_section_links`, `settings_section_advanced`
   - `settings_manage_links` ("Manage link profiles")
   - `settings_edit_in_links` ("Edit in Links")
   - `settings_debounce_summary` — e.g. `Link debounce: %1$d s` (or reuse `debounce_label` if wording fits)
   - `settings_grant_hint` — one line: dismiss sheet to use grant card on main screen
   - `settings_debug_package` / `settings_debug_node_id` — labels for optional Advanced read-only rows  
   → verify: `./gradlew :app:assembleDebug` (resource compile)

2. **Create `SettingsBottomSheet.kt`** — public `@Composable` with `@OptIn(ExperimentalMaterial3Api::class)`:

```kotlin
@Composable
fun SettingsBottomSheet(
    // booleans from uiState
    startOnBoot: Boolean,
    watchdogEnabled: Boolean,
    pauseNodeInDoze: Boolean,
    batteryUnrestricted: Boolean,
    linkDebounceSec: Int,
    showGrantHint: Boolean,
    packageName: String,
    nodeId: String?,
    onDismiss: () -> Unit,
    onStartOnBoot: (Boolean) -> Unit,
    onWatchdogEnabled: (Boolean) -> Boolean,
    onPauseNodeInDoze: (Boolean) -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenLinks: () -> Unit,
    onGrantHint: () -> Unit,
)
```

   - `val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)`
   - `ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState)`
   - Body: `Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp))` with spaced sections
   - **`SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit)`** — `Text(title, style = titleSmall)` + `HorizontalDivider` + content
   - **Reliability:** lift three switch+hint blocks from `SettingsDialog` (`MainScreen.kt:442–498`); keep `watchdogError` local state + `watchdog_needs_shizuku` on failed enable
   - **Reliability grant hint:** if `showGrantHint`, `TextButton` → `onGrantHint()` (parent dismisses sheet)
   - **Battery:** status text + two `TextButton`s (same strings/logic as dialog `499–519`)
   - **Links:** single `ListItem` / `TextButton` row → `onOpenLinks()`
   - **Advanced:** read-only `linkDebounceSec`; `TextButton` → `onOpenLinks()`; optional debug lines for `packageName` and `nodeId` (monospace `bodySmall`, no clipboard — T15)
   - No confirm "Close" button — sheet dismisses via scrim/swipe/back  
   → verify: compile; manual open sheet from gear

3. **Wire `MainScreen.kt`**
   - Replace `if (showSettings) { SettingsDialog(...) }` (`225–238`) with `SettingsBottomSheet(...)` passing same VM callbacks + new props:
     - `linkDebounceSec = uiState.linkDebounceMs / 1000`
     - `showGrantHint` = same `showGrant` as L160–162 (recompute or hoist `val showGrant` above both usages)
     - `packageName = context.packageName`
     - `nodeId = viewModel.nodeId()`
     - `onOpenLinks = { showSettings = false; requestLinkPermissions(permissionLauncher); viewModel.setShowLinks(true) }`
     - `onGrantHint = { showSettings = false }`
   - Delete private `SettingsDialog` (`422–526`)
   - Remove unused `AlertDialog` imports if orphaned  
   → verify: settings gear opens sheet; back/swipe dismisses; toggles still work

4. **Regression pass** — confirm unchanged behavior:
   - Start on boot / watchdog / doze pause persist after kill
   - Watchdog toggle off when Shizuku missing shows error
   - Battery buttons launch `MainActivity` intents
   - Links shortcut opens `LinksScreen`; debounce editable only there
   - Grant hint only when PROXY + missing secure settings  
   → verify: manual checklist in task file

5. **Tests** — no new pure helpers required. If you extract section visibility (e.g. `showGrantHint` predicate) to a testable function, add unit test; otherwise skip.  
   → verify: `make verify`

### Tests to add

| Case | Expect |
| ---- | ------ |
| *(optional)* `settingsGrantHintVisible(mode, runtime, hasPermission)` | `true` only for PROXY runtime without secure settings |
| Default | **No new test file required** — UI wiring only |

### Verify commands

```bash
make verify
```

### Risks / pitfalls

- **Sheet height:** use `verticalScroll` inside sheet; avoid nested `LazyColumn` + sheet measurement fights.
- **Partial expand:** `skipPartiallyExpanded = true` avoids half-height sheet stuck state on small screens.
- **Battery status stale:** user returns from system settings — sheet may show old status until recomposed; acceptable (same as dialog); optional `LaunchedEffect(showSettings)` refresh if easy.
- **Links permissions:** must call `requestLinkPermissions(permissionLauncher)` before `setShowLinks(true)` — mirror Wi-Fi icon (`98–100`).
- **Do not** duplicate debounce slider — spec and Links screen own it.

### Out of scope

- New preference keys (`vpnAlwaysOn` stays dormant)
- Per-app proxy bypass, always-on VPN deep link
- Network detail (T14), motion polish (T15)
- Replacing `LinksScreen` / add-network / battery-opt-entry `AlertDialog`s
- `settingsState` ViewModel refactor
- Log export, clipboard on debug rows

### Execute model recommendation

- **small** — mechanical lift from `SettingsDialog` to sheet; all persistence and VM APIs exist. No orchestrator / libzt / Room work.

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

- Settings still `SettingsDialog` `AlertDialog` in `MainScreen.kt:422–526` — T13 replaces with bottom sheet.
- Reuse `MaterialExpressiveTheme` from T12; no theme work in T13.
- `privilegedWatchdogEnabled` is the watchdog pref (spec's `serviceWanted` name is stale).
- No `ModalBottomSheet` in repo yet — first usage; opt in `ExperimentalMaterial3Api`.
- libzt `1f134a0` on `pylon` (PROXY→VPN crash fix) — unrelated to T13 UI.
