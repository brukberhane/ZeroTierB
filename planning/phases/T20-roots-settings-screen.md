# T20 — Roots settings screen

**Status**: Pending  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T19  
**Next**: T10  
**Layer**: L7

## Description

Settings gains a **Roots** row that opens a nested screen (not a full Settings reorg). Operator adds/removes moons (id+seed or SAF), imports a custom planet, picks Earth vs Custom, and uses the airgap latch + switch. Clipboard/QR/camera are **out**. Apply/restart is T19; this task is UI + ViewModel wiring + strings.

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| 2026-09-05 | created | — | Pending | /setup-tasks Roots feature | |
| 2026-09-05 | notes | Pending | Pending | T19 close-out: Application already collects roots → refresh | |

## Requirements

- [ ] Settings: one **Roots** row → nested screen. Reliability / battery / links / advanced DNS **unchanged**.
- [ ] Moon list (max 16): add id+seed; remove. SAF: one document picker → parser (T17) → confirm sheet “Moon `id` — orbit?” / “Planet `id` — save?” / moon.json id+seed (keys not saved) / reject unknown.
- [ ] Airgap: **no moons** → show **Enable airgap without moons**; hide **Enable airgap** until latch on. **≥1 moon** → hide latch, show **Enable airgap**. Custom planet does **not** replace the latch. Latch subtitle: LAN / moons only; no public roots.
- [ ] Last moon removed with latch off and airgap on → airgap forced off (T19) + snack: airgap ended because no moons.
- [ ] After custom planet import: **Earth vs Custom planet** control. Hidden/disabled until a custom blob exists. Import does **not** auto-select Custom. Airgap on → control disabled (Dummy wins). Delete custom while Custom selected → force Earth + restart.
- [ ] One custom planet file; new import replaces. Strings in `strings.xml`.
- [ ] Starting copy when airgap and not Online: waiting for roots/moons (LAN ok). Do not fake Online.
- [ ] `make verify`.

## Non-goals (this task)

- QR, clipboard b64, CAMERA, `joinzt.com` intent (V2)
- Generating operator `mkworld` / `initmoon` keys
- Multiple saved custom planets
- Settings information architecture rewrite

## Constraints

- `.cursor/rules/compose.mdc` — Expressive theme already; `@OptIn(ExperimentalMaterial3ExpressiveApi)` as existing screens
- ViewModel is the only UI→service bridge
- No `VpnService.prepare()` from this screen
- Do not bump AGP/BOM

## References

- `app/src/main/java/com/brukb/zerotier/ui/SettingsBottomSheet.kt`
- `app/src/main/java/com/brukb/zerotier/ui/MainScreen.kt` / `MainViewModel.kt`
- T13 nested-sheet pattern; Links overlay as a prior “navigate out of settings” example

## Reality notes (from T19 close-out)

- Apply/restart is T19: `LivePlanetResolver` + `RootsApplier` + orchestrator `RootsFingerprint`. UI only mutates Room/prefs; do **not** restage from the ViewModel. `ZerotierBApplication` already `combine`s airgap / latch / planetSource / `observeMoons()` → `refresh()`.
- Last-moon latch: T19 writes `setAirgap(false)` in both the Application collector and `stageBeforeNode`. Snack copy is this task.
- Starting subtitle string (T19): `R.string.roots_waiting_lan` = “Waiting for roots/moons (LAN ok)”. PROXY + VPN Dummy/offline already use it. Hero chip stays `lifecycle_starting`. Do not fake Online.
- Dummy generate stays in start path: `{ ZeroTierNative.zts_util_make_dummy_planet() }` — never `Application.onCreate`.
- Identity-home allowlist **throws** on denied paths including `exists()` / `read()` — do not probe `dummy.planet` through `IdentityHomeStore`.

## Implementation Plan

*(Filled by `/task-1-plan`)*

### High-level notes (setup-tasks)

1. `RootsScreen` (or sheet) from Settings row.
2. Wire repository/prefs from T17; apply/restart already T19.
3. SAF `OpenDocument`; confirm dialog; no camera.

## Execution plan (filled by /task-1-plan)

**Date:**  
**Codebase snapshot:**  
**Execute model:** small/default | large (only if justified)

### Context for executor
- …

### Steps
1. … → verify: …

### Tests to add
- …

### Verify commands
- `make verify`
