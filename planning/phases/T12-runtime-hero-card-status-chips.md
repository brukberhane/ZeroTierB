# T12 — Runtime hero card + status chips (Phase B)

**Status**: Pending  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T11.5  
**Next**: T13  
**Layer**: L7

## Description

Replace the flat `StatusCard` with a **runtime hero** that answers "what is ZeroTier doing right now?" at a glance: active runtime (OFF / PROXY / VPN / AUTO), node lifecycle (starting, online, paused for Doze, error), link line, node ID, proxy grant line. Add **status chips** on each network row using T11 unified `networkRuntime()` — JOINING, OK, DENIED, ERROR, DOWN — instead of em-dash for proxy mode.

Keep global mode segmented control (OFF|PROXY|VPN|AUTO). No per-network mode chips.

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| 2026-08-31 | created | — | Pending | /setup-tasks UI rewrite phase B | |

## Requirements

- [ ] **Hero card** (`RuntimeHeroCard` composable or refactor `StatusCard`):
  - Runtime badge: OFF / PROXY / VPN / AUTO (from `plan.runtime` + `globalMode`)
  - Node lifecycle chip: STARTING | ONLINE | PAUSED_DOZE | ERROR | STOPPED (from T11 `nodeLifecycle()`)
  - Status line: human copy from `StatusFormat` (not raw service strings)
  - Node ID: monospace, truncated middle optional (full copy in T15)
  - Proxy line when PROXY/AUTO+proxy: `127.0.0.1:port` or "System proxy not granted"
  - Current link line (existing `formatLinkLine`)
  - `isApplying` → Expressive `LoadingIndicator` (or `CircularWavyProgressIndicator`) with `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` — T11.5 proved `LoadingIndicator` compiles; do not fall back to `CircularProgressIndicator` unless the symbol is gone after a later BOM change
  - `vpnConsentMissing` banner unchanged behavior (consent-only path)
- [ ] **Network rows** (`NetworkRow`):
  - Status chip from `viewModel.networkRuntime(network.networkId)` — color by join state:
    - JOINING → tertiary/neutral + optional small progress
    - OK → primary/success tone
    - ACCESS_DENIED / NOT_FOUND / DOWN / ERROR → errorContainer
  - Chip label from `StatusFormat.joinStatusLabel()` (strings.xml)
  - When runtime OFF or network disabled → chip hidden or "Off"
  - Main pin + enable switch unchanged
- [ ] **Grant card** placement: show below hero when PROXY needs `WRITE_SECURE_SETTINGS` (existing `GrantSecureSettingsCard`)
- [ ] Strings in `res/values/strings.xml` — no hardcoded chip labels in composables
- [ ] `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` on hero/chips that use Expressive APIs; toolchain already bumped in T11.5 (`compose-bom-alpha`, material3 1.5.0-alpha, AGP 9.2 / compileSdk 37). Do **not** bump BOM/AGP here.

## Non-goals (this task)

- Settings bottom sheet restructure (T13)
- Network detail screen expansion (T14)
- Copy-to-clipboard, motion, empty-state illustrations (T15)
- Links screen layout changes beyond chip consistency if any network appears there

## Constraints

- Read `.cursor/rules/compose.mdc`
- All mode changes still via `orchestrator.applyGlobalMode()` — no service starts from composables
- Hero must not show contradictory state (e.g. VPN node ID while PROXY runtime active)
- Dark + dynamic theme from T09 must remain working. Optional T11.5 hook: wrap `ZerotierBTheme`'s `MaterialTheme` with `MaterialExpressiveTheme` (one-liner in Theme.kt) — do not restyle unrelated screens.

## References

- `app/src/main/java/com/brukb/zerotier/ui/MainScreen.kt` — `StatusCard`, `NetworkRow`
- `app/src/main/java/com/brukb/zerotier/ui/MainViewModel.kt` — T11 APIs
- `app/src/main/java/com/brukb/zerotier/ui/StatusFormat.kt`
- `app/src/main/java/com/brukb/zerotier/ui/GrantSecureSettingsCard.kt`

## Implementation Plan

*(Filled by `/task-1-plan`)*

### High-level notes (setup-tasks)

1. Extract `RuntimeHeroCard` from `StatusCard`; keep segmented control at top of card or directly above.
2. Add `JoinStatusChip` composable (AssistChip or SuggestionChip per M3).
3. Wire `NetworkRow` to `networkRuntime()`; remove dead `runtimeStatus()` VPN-only path.
4. Unit tests for chip color/label selection via pure helpers.

## Execution plan (filled by /task-1-plan)

*(empty)*

## Test Plan

- `StatusFormatTest` / `JoinStatusChipLogicTest`: label + semantic color role per status
- `make verify`
- Manual: PROXY joined network shows OK chip; VPN shows same; OFF shows no false OK

## Acceptance Criteria

- [ ] PROXY mode: enabled joined network row shows OK (or JOINING then OK), not `—`
- [ ] Hero shows PAUSED_DOZE when doze pause active; recovers on unlock
- [ ] Global segmented control + pin + enable switch behavior unchanged
- [ ] Grant card still appears when secure settings missing in PROXY
- [ ] `make verify` green; strings externalized

## Verification

*(Filled by `/task-3-complete`)*

## Manual test (for humans)

1. PROXY + one net ON → hero ONLINE, row chip JOINING→OK
2. Lock (pause in doze on) → hero PAUSED_DOZE
3. VPN + same net → hero VPN, chips from VPN state
4. Denied net (if available) → ERROR/DENIED chip

## Reality notes

### From T11 close-out

- Per-network UI status: `MainViewModel.networkRuntime()` / `resolveNetworkRuntime(plan.runtime, …)` — not VPN-only.
- `JoinStatus`, `NodeLifecycleStatus`, `joinStatusLabel`, `joinStatusChipRole` in `StatusFormat` — use for T12 chips.
- Hero lifecycle: `viewModel.nodeLifecycle()` reads typed enum from active stack's service state.

### From T11.5 (toolchain)

- Pins: AGP **9.2.1**, Gradle **9.4.1**, Kotlin **2.2.21**, KSP **2.3.11**, `compose-bom-alpha:2026.08.01` → material3 **1.5.0-alpha27**, Room **2.7.2**, `compileSdk 37` / `targetSdk 35`.
- `LoadingIndicator` and `MaterialExpressiveTheme` compile. Optional one-liner in `ZerotierBTheme`. Do **not** bump AGP/Kotlin/compileSdk/BOM in T12.
- SDK: `platforms/android-37.0` (not `platforms;android-37`). Built-in Kotlin: no `kotlinOptions`.

## Learnings

*(Filled on close-out)*
