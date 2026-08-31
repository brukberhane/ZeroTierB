# T12 — Runtime hero card + status chips (Phase B)

**Status**: Done  
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
| 2026-08-31 | plan | Pending | Planned | /task-1-plan T12 | |
| 2026-08-31 | execute | Planned | InProgress | /task-2-execute T12 | |
| 2026-08-31 | complete | InProgress | Done | /task-3-complete T12 | |

## Requirements

- [x] **Hero card** (`RuntimeHeroCard` composable or refactor `StatusCard`):
  - Runtime badge: OFF / PROXY / VPN / AUTO (from `plan.runtime` + `globalMode`)
  - Node lifecycle chip: STARTING | ONLINE | PAUSED_DOZE | ERROR | STOPPED (from T11 `nodeLifecycle()`)
  - Status line: human copy from `StatusFormat` (not raw service strings)
  - Node ID: monospace, truncated middle optional (full copy in T15)
  - Proxy line when PROXY/AUTO+proxy: `127.0.0.1:port` or "System proxy not granted"
  - Current link line (existing `formatLinkLine`)
  - `isApplying` → Expressive `LoadingIndicator` (or `CircularWavyProgressIndicator`) with `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` — T11.5 proved `LoadingIndicator` compiles; do not fall back to `CircularProgressIndicator` unless the symbol is gone after a later BOM change
  - `vpnConsentMissing` banner unchanged behavior (consent-only path)
- [x] **Network rows** (`NetworkRow`):
  - Status chip from `viewModel.networkRuntime(network.networkId)` — color by join state:
    - JOINING → tertiary/neutral + optional small progress
    - OK → primary/success tone
    - ACCESS_DENIED / NOT_FOUND / DOWN / ERROR → errorContainer
  - Chip label from `StatusFormat.joinStatusLabel()` (strings.xml)
  - When runtime OFF or network disabled → chip hidden or "Off"
  - Main pin + enable switch unchanged
- [x] **Grant card** placement: show below hero when PROXY needs `WRITE_SECURE_SETTINGS` (existing `GrantSecureSettingsCard`)
- [x] Strings in `res/values/strings.xml` — no hardcoded chip labels in composables
- [x] `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` on hero/chips that use Expressive APIs; toolchain already bumped in T11.5 (`compose-bom-alpha`, material3 1.5.0-alpha, AGP 9.2 / compileSdk 37). Do **not** bump BOM/AGP here.

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

### High-level notes (setup-tasks)

1. Rename `StatusCard` → `RuntimeHeroCard` in `MainScreen.kt`; wire `viewModel.nodeId()` + `nodeLifecycle()`.
2. New `JoinStatusChip.kt` (`AssistChip` + `joinStatusChipRole` colors). Hide when OFF/disabled/PAUSED_DOZE.
3. Stop calling `runtimeStatus()` (the `"—"` path). Delete it if unused.
4. Optional-but-do: `MaterialExpressiveTheme` one-liner in `ZerotierBTheme` **with** `colorScheme`.
5. Unit tests for `joinChipStatus` + exhaustive `joinStatusChipRole`.

## Execution plan (filled by /task-1-plan)

**Date:** 2026-08-31  
**Codebase snapshot:** branch `T12-runtime-hero-card-status-chips` at `f0627e2` (T11.5). AGP 9.2.1, `compose-bom-alpha:2026.08.01`, material3 1.5.0-alpha27. Hero is still `StatusCard` (`MainScreen.kt:264`). Rows still `runtimeStatus()` → `"—"`. `nodeLifecycle()` / `nodeId()` exist on VM, **unused** by UI. `joinStatusChipRole` unused in prod. `ZerotierBTheme` wraps `MaterialTheme` only.  
**Execute model:** small (APIs exist; layout + pure helpers)

### Context for executor

**Goal:** Make the home screen answer “what is ZeroTier doing?” Hero shows resolved runtime + node lifecycle (incl. PAUSED_DOZE) without raw service strings and without leaking the other stack’s node ID. Each network row gets a join-status chip (JOINING/OK/DENIED/…) instead of `Status: —`. Segmented OFF|PROXY|VPN|AUTO, pin, enable, grant card, consent banner stay. No settings rewrite. No BOM/AGP bump.

**Resolved ambiguities (do not reopen):**

| Question | Pick |
| -------- | ---- |
| Chip hidden vs “Off” when OFF / disabled | **Hide** the chip. No `"—"` text. |
| `networkRuntime == null` while enabled + PROXY/VPN up | Treat as **JOINING** (avoid flash of empty). |
| Same null during `PAUSED_DOZE` / `STOPPED` | **Hide** chip (T11 clears maps on doze; do not fake JOINING). |
| `MaterialExpressiveTheme` | **Do** the one-liner in `ZerotierBTheme`. Pass **existing `colorScheme`** (T09 dark/dynamic). |
| Chip widget | **`AssistChip`**, not another `FilterChip` (Main pin stays `FilterChip`). |
| Copy-to-clipboard / truncate node ID | **Skip** (T15). Monospace full ID. |

**Key files**

| Path | Action |
| ---- | ------ |
| `app/src/main/java/com/brukb/zerotier/ui/MainScreen.kt` | Rename `StatusCard` → `RuntimeHeroCard`; fix call site; `NetworkRow` takes `JoinStatus?` |
| `app/src/main/java/com/brukb/zerotier/ui/JoinStatusChip.kt` | **new** — `AssistChip` |
| `app/src/main/java/com/brukb/zerotier/ui/StatusFormat.kt` | `runtimeHeadline`, `joinChipStatus`, `*LabelRes` |
| `app/src/main/java/com/brukb/zerotier/ui/theme/Theme.kt` | `MaterialExpressiveTheme` wrap |
| `app/src/main/java/com/brukb/zerotier/ui/MainViewModel.kt` | delete `runtimeStatus()` if unused |
| `app/src/main/res/values/strings.xml` | join + lifecycle + AUTO headline |
| `app/src/test/java/com/brukb/zerotier/ui/StatusFormatTest.kt` | tables below |
| `app/src/debug/.../ExpressiveApiSmoke.kt` | leave; still unused by MainScreen |

**Invariants (`.cursor/rules/compose.mdc`)**

- Mode changes only via `viewModel.setGlobalMode` / `requestVpnAndStart` — no service starts from composables.
- Consent banner stays `requestVpnConsent()` (does **not** flip global mode to VPN).
- No per-network PROXY/VPN chips.
- `compileSdk 37` / `targetSdk 35`. Expressive = `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`. **Do not bump** AGP/BOM/Kotlin.
- Hero must not show VPN node ID while `plan.runtime == PROXY` (today `MainScreen.kt:126` does `vpn.nodeId.ifBlank { proxy.nodeId }` — **bug**, fix).

**Data already shipped (T11) — do not reimplement**

- `MainViewModel.nodeLifecycle(): NodeLifecycleStatus`
- `MainViewModel.networkRuntime(id): NetworkRuntimeStatus?`
- `MainViewModel.nodeId(): String?` — keyed off `plan.runtime` (use this)
- `StatusFormat.joinStatusLabel` / `nodeLifecycleLabel` / `joinStatusChipRole`
- `JoinStatus`: JOINING, REQUESTING_CONFIG, OK, ACCESS_DENIED, NOT_FOUND, DOWN, UNKNOWN, ERROR
- `NodeLifecycleStatus`: STOPPED, STARTING, ONLINE, PAUSED_DOZE, ERROR
- `joinStatusChipRole`: OK→SUCCESS; JOINING+REQUESTING_CONFIG→NEUTRAL; rest→ERROR

**Layout stay** (`MainScreen` Column): `RuntimeHeroCard` → consent `Card` (if `vpnConsentMissing`) → `GrantSecureSettingsCard` (same `showGrant` predicate) → networks header → `LazyColumn`/`NetworkRow`. Do not move grant into the hero.

### Steps

1. **Theme wrap** — `Theme.kt` `ZerotierBTheme`: after computing `colorScheme`, replace bare `MaterialTheme(colorScheme, content)` with Expressive + same scheme:

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
MaterialExpressiveTheme(colorScheme = colorScheme, content = content)
```

If that overload does not compile, nest:

```kotlin
MaterialExpressiveTheme {
    MaterialTheme(colorScheme = colorScheme, content = content)
}
```

Do **not** call `MaterialExpressiveTheme { content() }` without `colorScheme` — that drops T09 dynamic/dark colors.  
→ verify: `./gradlew :app:compileDebugKotlin` succeeds; `MainScreen` still wrapped in `ZerotierBTheme`.

2. **Pure helpers** in `StatusFormat.kt` (JVM-testable, no Compose):

```kotlin
fun runtimeHeadline(globalMode: GlobalMode, runtime: Runtime?): String =
    if (globalMode == GlobalMode.AUTO) {
        "AUTO (${runtime?.name ?: "OFF"})"
    } else {
        runtime?.name ?: globalMode.name
    }

/** null = hide chip. */
fun joinChipStatus(
    lifecycle: NodeLifecycleStatus,
    runtime: Runtime?,
    networkEnabled: Boolean,
    networkRuntime: NetworkRuntimeStatus?,
): JoinStatus? {
    if (!networkEnabled) return null
    if (runtime == null || runtime == Runtime.OFF) return null
    if (lifecycle == NodeLifecycleStatus.PAUSED_DOZE ||
        lifecycle == NodeLifecycleStatus.STOPPED
    ) {
        return null
    }
    return networkRuntime?.joinStatus ?: JoinStatus.JOINING
}

@StringRes
fun joinStatusLabelRes(status: JoinStatus): Int = /* map to R.string.join_status_* */

@StringRes
fun nodeLifecycleLabelRes(status: NodeLifecycleStatus): Int = /* map to R.string.lifecycle_* */
```

Keep existing `joinStatusLabel` / `nodeLifecycleLabel` English strings **identical** to `strings.xml` (tests + any leftover callers). New imports on `StatusFormat.kt`: `GlobalMode`, `Runtime`, `NetworkRuntimeStatus`, `androidx.annotation.StringRes`.  
→ verify: unit tests in step 6 compile against these signatures.

3. **`strings.xml`** — add keys (English = current `StatusFormat` labels):

| name | value |
| ---- | ----- |
| `join_status_joining` | Joining |
| `join_status_requesting_config` | Requesting config |
| `join_status_ok` | Connected |
| `join_status_access_denied` | Access denied |
| `join_status_not_found` | Not found |
| `join_status_down` | Down |
| `join_status_unknown` | Unknown |
| `join_status_error` | Error |
| `lifecycle_stopped` | Stopped |
| `lifecycle_starting` | Starting |
| `lifecycle_online` | Online |
| `lifecycle_paused_doze` | Paused (Doze) |
| `lifecycle_error` | Error |
| `lifecycle_chip` | Node status |
| `join_status_chip` | Network status |

Keep `runtime_line` (`Runtime %1$s — %2$s`). `%1$s` becomes `runtimeHeadline(...)`.  
→ verify: no hardcoded `"Connected"` / `"Paused (Doze)"` inside composable **files** (`grep` those strings only in `StatusFormat.kt` + `strings.xml` + tests).

4. **`JoinStatusChip.kt`** (new, `package com.brukb.zerotier.ui`):

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun JoinStatusChip(status: JoinStatus, modifier: Modifier = Modifier) {
    val role = joinStatusChipRole(status)
    val scheme = MaterialTheme.colorScheme
    val container = when (role) {
        JoinStatusChipRole.SUCCESS -> scheme.primaryContainer
        JoinStatusChipRole.NEUTRAL -> scheme.tertiaryContainer
        JoinStatusChipRole.ERROR -> scheme.errorContainer
    }
    val labelColor = when (role) {
        JoinStatusChipRole.SUCCESS -> scheme.onPrimaryContainer
        JoinStatusChipRole.NEUTRAL -> scheme.onTertiaryContainer
        JoinStatusChipRole.ERROR -> scheme.onErrorContainer
    }
    AssistChip(
        onClick = {},
        enabled = false, // display-only; do not toggle
        modifier = modifier,
        label = { Text(stringResource(joinStatusLabelRes(status))) },
        leadingIcon = if (status == JoinStatus.JOINING ||
            status == JoinStatus.REQUESTING_CONFIG
        ) {
            { LoadingIndicator(modifier = Modifier.size(16.dp)) }
        } else null,
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = container,
            disabledLabelColor = labelColor,
            disabledLeadingIconContentColor = labelColor,
        ),
    )
}
```

If `enabled = false` greys out against your colors, leave `enabled = true` and `onClick = {}`. Prefer visible role colors over a grey disabled chip.  
`@file:OptIn` or function OptIn required.  
→ verify: `:app:compileDebugKotlin`; chip is **not** on Links screen.

5. **Hero** — in `MainScreen.kt` rename `StatusCard` → `RuntimeHeroCard`. Call site (`:112–134`):

- Keep segmented `onMode` exactly (VPN → `requestVpnAndStart()`, else `setGlobalMode`; AUTO still `requestLinkPermissions`).
- `nodeId = viewModel.nodeId().orEmpty()` — **not** `vpn.nodeId.ifBlank { proxy.nodeId }`.
- Drop `status = vpn.statusMessage ?: proxy.statusMessage`.
- Pass `lifecycle = viewModel.nodeLifecycle()`.
- `runtimeHeadline = runtimeHeadline(uiState.globalMode, uiState.plan?.runtime)`.
- `reason = uiState.plan?.reason`.
- `proxyLine = proxyStatusText(uiState.proxy)` unchanged (already “127.0.0.1:port” / “not granted” / inactive).
- `isApplying`, overlap, error unchanged.

Inside `RuntimeHeroCard`:

- Segmented row **unchanged**.
- `isApplying` → `LoadingIndicator` (Expressive) + `R.string.applying`. **Remove** `CircularProgressIndicator` from this file if unused.
- Runtime line: `stringResource(R.string.runtime_line, runtimeHeadline, reason ?: "—")`.
- Lifecycle: `AssistChip` or `SuggestionChip` display-only, label `stringResource(nodeLifecycleLabelRes(lifecycle))`, contentDescription `R.string.lifecycle_chip`. No extra color system — default chip is fine (lifecycle is not join-status).
- Link line, node line (`R.string.node_line` + `FontFamily.Monospace` if nodeId non-blank), `proxyLine`, overlap, error — same order as today.
- Do **not** put consent banner or grant card inside the hero.

→ verify: `grep -n 'vpn.nodeId.ifBlank' app/src/main` empty; `grep CircularProgressIndicator app/src/main/java/com/brukb/zerotier/ui/MainScreen.kt` empty; `grep MaterialExpressiveTheme app/src/main` is Theme.kt only.

6. **`NetworkRow`** — replace `runtimeStatus: String` with `joinStatus: JoinStatus?`:

Call site:

```kotlin
val lifecycle = viewModel.nodeLifecycle()
val runtime = uiState.plan?.runtime
// inside items:
NetworkRow(
    ...
    joinStatus = joinChipStatus(
        lifecycle,
        runtime,
        network.isEnabled,
        viewModel.networkRuntime(network.networkId),
    ),
    ...
)
```

In the row `Column`, **delete** `Text(stringResource(R.string.status_line, runtimeStatus))`. If `joinStatus != null`, show `JoinStatusChip(joinStatus)` under the hex id. Pin `FilterChip` + `Switch` + delete **unchanged**.  
If `status_line` has no remaining references, leave the string (harmless) or delete it.  
→ verify: `grep -n 'runtimeStatus(' app/src/main` empty; `grep 'Status: —' app/src` empty.

7. **Delete `MainViewModel.runtimeStatus`** if nothing calls it. Do not delete `networkRuntime` / `nodeLifecycle` / `nodeId`.  
→ verify: compile; no unused public warning required.

8. **Tests** — extend `StatusFormatTest` (same file, table-style). See **Tests to add**.  
→ verify:

```
./gradlew :app:testDebugUnitTest --tests "com.brukb.zerotier.ui.StatusFormatTest"
```

9. **Lint + full verify** (mandatory):

```
test -f Makefile && grep -q '^verify' Makefile
test -f lefthook.yml
test -f app/lint.xml
make verify
```

If lint flags `LoadingIndicator` in composition, fix the call site (size/modifier) — do **not** re-disable the five Compose detectors and do **not** bump BOM.  
→ verify: `make verify` green.

### Tests to add

In `StatusFormatTest.kt`:

1. `joinStatusChipRole_allValues` — every `JoinStatus.entries` maps as in StatusFormat (OK=SUCCESS; JOINING+REQUESTING_CONFIG=NEUTRAL; else ERROR). Not just OK≠JOINING.
2. `joinChipStatus_table` — parameterized-style list of cases:

| lifecycle | runtime | enabled | networkRuntime.joinStatus | expect |
| --------- | ------- | ------- | ------------------------- | ------ |
| ONLINE | PROXY | true | OK | OK |
| ONLINE | PROXY | true | null | JOINING |
| ONLINE | VPN | true | ACCESS_DENIED | ACCESS_DENIED |
| ONLINE | PROXY | false | OK | null (hide) |
| ONLINE | OFF | true | OK | null |
| ONLINE | null | true | OK | null |
| PAUSED_DOZE | PROXY | true | OK | null |
| PAUSED_DOZE | PROXY | true | null | null |
| STOPPED | PROXY | true | JOINING | null |
| STARTING | PROXY | true | null | JOINING |
| ERROR | VPN | true | ERROR | ERROR |

3. `runtimeHeadline_autoShowsResolved` — `AUTO` + `Runtime.PROXY` → `"AUTO (PROXY)"`; `GlobalMode.VPN` + `Runtime.VPN` → `"VPN"`; `OFF` + null runtime → `"OFF"`.
4. Keep existing `joinStatusLabel_allValuesNonEmpty` / paused≠stopped / proxyStatusText / formatLinkLine.

No Robolectric. No Compose UI tests. Do not add tests that start services.

### Verify commands

```
./gradlew :app:testDebugUnitTest --tests "com.brukb.zerotier.ui.StatusFormatTest"
make verify
```

Optional: `grep -n MaterialExpressiveTheme app/src/main` → `Theme.kt` only.

### Risks / pitfalls

- **VPN-first nodeId** (`MainScreen.kt:126`) contradicts PROXY runtime. Always `viewModel.nodeId()`.
- **Doze + null map ≠ JOINING.** `joinChipStatus` must hide on `PAUSED_DOZE` or AC “hero PAUSED_DOZE” rows will lie as Joining.
- **`MaterialExpressiveTheme` without `colorScheme`** resets T09 dynamic color. Pass the scheme.
- **`enabled = false` AssistChip** may ignore custom colors — switch to enabled no-op click if chips look grey.
- **Do not bump** AGP/Kotlin/`compileSdk`/BOM. Room stays 2.7.2.
- **Do not** parse `statusMessage` for Doze (T11: `nodeLifecycle` is the source).
- **Do not** call `ZerotierBVpnService.start` / `ProxyModeService` from composables.
- Consent banner must stay `requestVpnConsent()`, not `requestVpnAndStart()`.
- `LoadingIndicator` needs `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`.
- Small-row crowding: chip under hex id, not a third FilterChip in the pin row.

### Out of scope

- T13 settings bottom sheet
- T14 network detail routes/DNS (reuse `JoinStatusChip` later; do not edit `NetworkDetailScreen` except if it already duplicated `runtimeStatus` — it should not)
- T15 clipboard, motion, empty-state art
- Links screen layout
- libzt / JNI / orchestrator behavior
- `ExpressiveApiSmoke` wiring into MainScreen

### Execute model recommendation

- **small** — data layer and labels exist; this is wiring + one pure helper + chips. Use **medium** only if Theme/Expressive overload fights you. Not large.

## Test Plan

- `StatusFormatTest` / `JoinStatusChipLogicTest`: label + semantic color role per status
- `make verify`
- Manual: PROXY joined network shows OK chip; VPN shows same; OFF shows no false OK

## Acceptance Criteria

- [x] PROXY mode: enabled joined network row shows OK (or JOINING then OK), not `—`
- [x] Hero shows PAUSED_DOZE when doze pause active; recovers on unlock
- [x] Global segmented control + pin + enable switch behavior unchanged
- [x] Grant card still appears when secure settings missing in PROXY
- [x] `make verify` green; strings externalized

## Verification

**Date:** 2026-08-31

Presence: Makefile `verify`, lefthook pre-commit, `app/lint.xml` — present.

Commands:
```
./gradlew :app:testDebugUnitTest --tests "com.brukb.zerotier.ui.StatusFormatTest"
make verify
grep -n 'vpn.nodeId.ifBlank' app/src/main   # empty
grep -n 'runtimeStatus(' app/src/main       # empty
```

Outcomes:
- `StatusFormatTest` — 9 tests green (joinChipStatus table, joinStatusChipRole_allValues, runtimeHeadline)
- `make verify` — lintDebug + unit tests + assembleDebug BUILD SUCCESSFUL (re-confirmed `/task-3-complete` 2026-08-31)
- Hero: `RuntimeHeroCard` uses `viewModel.nodeId()`, `nodeLifecycle()`, `runtimeHeadline()`, Expressive `LoadingIndicator`
- Rows: `JoinStatusChip` via `joinChipStatus()`; no `Status: —` path
- `ZerotierBTheme` → `MaterialExpressiveTheme(colorScheme = …)`
- `MainViewModel.runtimeStatus()` removed

## Files Modified

- `app/src/main/java/com/brukb/zerotier/ui/MainScreen.kt`
- `app/src/main/java/com/brukb/zerotier/ui/MainViewModel.kt`
- `app/src/main/java/com/brukb/zerotier/ui/StatusFormat.kt`
- `app/src/main/java/com/brukb/zerotier/ui/JoinStatusChip.kt` (new)
- `app/src/main/java/com/brukb/zerotier/ui/theme/Theme.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/brukb/zerotier/ui/StatusFormatTest.kt`
- `planning/phases/T12-runtime-hero-card-status-chips.md`
- `planning/phases/INDEX.md`
- `.cursor/rules/compose.mdc`

## Manual test (for humans)

```bash
make verify
./gradlew :app:installDebug
```

On device:

1. **PROXY** — enable one network, grant secure settings if prompted. Hero: lifecycle **Online**, runtime line shows PROXY. Row chip **Joining** → **Connected** (not `Status: —`).
2. **Doze pause** — Settings → enable “Pause ZeroTier in Doze”, lock device briefly, unlock. Hero lifecycle chip **Paused (Doze)**; row chips **hidden** (not Joining).
3. **VPN** — switch mode, grant VPN consent. Hero runtime **VPN**; node ID matches VPN stack only. Row chips follow VPN join state.
4. **OFF** — mode OFF: enabled networks show **no** join chip.
5. Segmented control, Main pin, enable switch, grant card, consent banner still work as T09/T11.

## Reality notes

### From T11 close-out

- Per-network UI status: `MainViewModel.networkRuntime()` / `resolveNetworkRuntime(plan.runtime, …)` — not VPN-only.
- `JoinStatus`, `NodeLifecycleStatus`, `joinStatusLabel`, `joinStatusChipRole` in `StatusFormat` — use for T12 chips.
- Hero lifecycle: `viewModel.nodeLifecycle()` reads typed enum from active stack's service state.

### From T11.5 (toolchain)

- Pins: AGP **9.2.1**, Gradle **9.4.1**, Kotlin **2.2.21**, KSP **2.3.11**, `compose-bom-alpha:2026.08.01` → material3 **1.5.0-alpha27**, Room **2.7.2**, `compileSdk 37` / `targetSdk 35`.
- `LoadingIndicator` and `MaterialExpressiveTheme` compile. Optional one-liner in `ZerotierBTheme`. Do **not** bump AGP/Kotlin/compileSdk/BOM in T12.
- SDK: `platforms/android-37.0` (not `platforms;android-37`). Built-in Kotlin: no `kotlinOptions`.

### From T12 plan (2026-08-31)

- `StatusCard` still live (`MainScreen.kt:264`). Call site `:126` uses **VPN nodeId first** — contradicts PROXY. Executor must switch to `viewModel.nodeId()`.
- `runtimeStatus()` still returns `"—"` when `networkRuntime` is null — that is the em-dash AC. Delete after chip wiring.
- `nodeLifecycle()` / `joinStatusChipRole` have **zero** UI callers.
- `ZerotierBTheme` is `MaterialTheme` only; debug `ExpressiveApiSmoke` is unused.
- Labels in `StatusFormat` are hardcoded English; `strings.xml` has no join/lifecycle keys yet.
- `joinChipStatus`: hide on OFF/disabled/`PAUSED_DOZE`/`STOPPED`; null map while ONLINE/STARTING → JOINING (not `"—"`).

## Learnings

**Dialectic 2026-08-31** (`/task-3-complete T12`):

- `compose.mdc`: `ZerotierBTheme` → `MaterialExpressiveTheme(colorScheme=…)` invariant; refined em-dash row class to `joinChipStatus`/`JoinStatusChip`; added `Hero shows wrong stack node ID` and `Join chip flashes JOINING during Doze`.
- Reusable UI: `JoinStatusChip.kt`, `RuntimeHeroCard`, pure `joinChipStatus` / `runtimeHeadline` in `StatusFormat.kt`.
- Hero must use `viewModel.nodeId()` — never VPN-first `ifBlank` fallback.
- Display-only chips: `AssistChip(enabled=false)` + custom disabled colors works; JOINING uses Expressive `LoadingIndicator` at 16dp.
