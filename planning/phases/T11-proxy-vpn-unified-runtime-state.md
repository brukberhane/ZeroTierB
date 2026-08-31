# T11 — Proxy/VPN unified runtime state (UI data layer)

**Status**: Done  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T09  
**Next**: T11.5  
**Layer**: L7

## Description

Close the **proxy-mode visibility gap**: today `MainViewModel.runtimeStatus()` reads
only `VpnServiceState.networkStatuses`, so network rows show `—` while PROXY is
active. Extend `ProxyModeService` / `ProxyServiceState` to publish the same class
of runtime facts VPN already exposes (per-network join status, assigned addresses,
node lifecycle), and add a **single ViewModel API** that picks the correct source
based on `RuntimePlan.runtime` (PROXY vs VPN vs OFF).

This task is **data + presentation helpers only** — no major layout rewrite (T12).

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| 2026-08-31 | created | — | Pending | /setup-tasks UI rewrite phase A | |
| 2026-08-31 | plan | Pending | Planned | /task-1-plan T11 | |
| 2026-08-31 | execute | Planned | InProgress | /task-2-execute T11 | |
| 2026-08-31 | complete | InProgress | Done | /task-3-complete T11 | |

## Requirements

- [ ] Shared `NetworkRuntimeStatus` (or equivalent) used by **both** stacks — same
      fields VPN UI already shows (`networkId`, human-readable `status`, optional
      `assignedAddresses`, optional `routes`/`dns` for T14).
- [ ] `NodeLifecycleStatus` enum on both `ProxyServiceState` and `VpnServiceState`
      (or one shared type): at minimum `STOPPED`, `STARTING`, `ONLINE`,
      `PAUSED_DOZE`, `ERROR`. Map proxy `statusMessage` strings ("ZeroTier paused
      (Doze)", "Node online: …") into the enum — UI must not parse English prose.
- [ ] `ProxyModeService` publishes network map updates when:
      - join completes / fails (`waitForNetworkReady`)
      - `nodeManager.state` emits (`ZtNetworkStatus.Status` changes)
      - doze pause / resume flips `nodePausedForDoze`
- [ ] Status values align with libzt `ZtNetworkStatus.Status`:
      `JOINING`, `OK`, `ACCESS_DENIED`, `NOT_FOUND`, `DOWN`, `UNKNOWN`.
      Display labels are a UI concern (T12); wire raw enum through state.
- [ ] `MainViewModel`:
      - `fun activeRuntime(): Runtime?` from `orchestrator.state.plan?.runtime`
      - `fun nodeLifecycle(): NodeLifecycleStatus` — proxy or VPN by active runtime
      - `fun networkRuntime(networkId: String): NetworkRuntimeStatus?` — unified
      - `fun nodeId(): String?` — proxy or VPN, normalized hex
- [ ] Pure helpers in `ui/StatusFormat.kt` (unit-testable): map enums → display
      strings / chip kinds (no `@Composable` in helpers).
- [ ] **No** new Gradle deps. **No** BOM / AGP bump.

## Non-goals (this task)

- Hero card layout, chips, or settings sheet (T12–T13)
- Full-screen network detail (T14)
- Motion / clipboard polish (T15)
- Changing orchestrator mutex or stack swap semantics
- Per-network PROXY/VPN mode chips (spec forbidden)

## Constraints

- Read `.cursor/rules/compose.mdc`, `.cursor/rules/libzt.mdc`, `.cursor/rules/android-vpn.mdc`.
- ViewModel remains the **only** UI→service bridge; composables do not read
  `ProxyModeService.state` / `ZerotierBVpnService.state` directly after T12
  (T11 may keep existing reads in ViewModel combine).
- Do not start both stacks to populate UI — read from the **active** runtime only;
  when OFF, show last-known or empty, never merge two nodes' maps.
- Preserve exclusive-stack invariant: if VPN `isRunning`, proxy network map must
  not be shown as authoritative (proxy should be stopped).

## References

- `app/src/main/java/com/brukb/zerotier/proxy/ProxyServiceState.kt` — sparse today
- `app/src/main/java/com/brukb/zerotier/vpn/VpnServiceState.kt` — has `networkStatuses`
- `app/src/main/java/com/brukb/zerotier/vpn/ZerotierBVpnService.kt` — `NetworkRuntimeStatus` publish pattern (~line 711)
- `app/src/main/java/com/brukb/zerotier/proxy/ProxyModeService.kt` — `applyNetworkRuntime`, doze pause/resume
- `app/src/main/java/com/brukb/zerotier/ztlib/ZtModels.kt` — `ZtNetworkStatus`
- `app/src/main/java/com/brukb/zerotier/ui/MainViewModel.kt` — `runtimeStatus()` VPN-only bug

## Implementation Plan

*(Filled by `/task-1-plan` — do not invent during bootstrap beyond high-level notes.)*

### High-level notes (setup-tasks)

1. Introduce `app/.../ui/model/RuntimeUiModels.kt` (or `connection/`) with shared
   `NetworkRuntimeStatus`, `NodeLifecycleStatus`, `JoinStatus` enums.
2. Extend `ProxyServiceState` with `nodeLifecycle` + `networkStatuses`.
3. In `ProxyModeService`, mirror VPN's status publish loop from `nodeManager.state`.
4. Refactor VPN to use shared `NetworkRuntimeStatus` if trivial; else map at VM boundary.
5. Replace `runtimeStatus()` with `networkRuntime()`; add unit tests.

## Execution plan (filled by /task-1-plan)

**Date:** 2026-08-31  
**Codebase snapshot:** post-T09 (`main`). `NetworkRuntimeStatus` lives in `vpn/VpnServiceState.kt` (2 fields: `networkId`, `status: String`). `ProxyServiceState` has no per-network map. `ProxyModeService.networkStateJob` only calls `applyNetworkRuntime` on `Status.OK` — never writes UI state. `MainViewModel.runtimeStatus()` reads `VpnServiceState` only → proxy rows show `—`. `ZeroTierNodeManager` already publishes `JOINING` on join and full `ZtNetworkStatus` (addresses/routes/DNS) on ready.  
**Execute model:** medium (default)

### Context for executor

**Goal:** Publish per-network join/runtime facts from the proxy stack into `ProxyServiceState`, share typed models with VPN, and expose unified `MainViewModel` selectors so T12 can render chips without re-reading services.

**Key files:**

| Path | Role |
| ---- | ---- |
| `app/.../connection/RuntimeStatusModels.kt` | **new** — `JoinStatus`, `NodeLifecycleStatus`, `NetworkRuntimeStatus` |
| `app/.../connection/RuntimeStatusMapper.kt` | **new** — pure ZT/VPN → model mappers + resolver |
| `app/.../vpn/VpnServiceState.kt` | import shared `NetworkRuntimeStatus`; add `nodeLifecycle` |
| `app/.../proxy/ProxyServiceState.kt` | add `networkStatuses`, `nodeLifecycle` |
| `app/.../proxy/ProxyModeService.kt` | publish network map + lifecycle on every relevant transition |
| `app/.../vpn/ZerotierBVpnService.kt` | publish typed `NetworkRuntimeStatus` + `nodeLifecycle` |
| `app/.../ui/MainViewModel.kt` | unified APIs; fix `runtimeStatus()` |
| `app/.../ui/StatusFormat.kt` | `joinStatusLabel`, `nodeLifecycleLabel`, `joinStatusChipRole` |
| `app/.../ui/MainScreen.kt` | one-line call-site fix (`runtimeStatus(networkId)` only) |
| `app/src/test/.../connection/RuntimeStatusMapperTest.kt` | **new** |
| `app/src/test/.../ui/StatusFormatTest.kt` | extend |

**Invariants:**

- Exclusive stack: when `plan.runtime != PROXY`, do **not** treat `proxy.networkStatuses` as authoritative (proxy should be stopped; empty list OK).
- When `plan.runtime == PROXY`, ignore `vpn.networkStatuses`.
- When `plan.runtime == OFF`, resolver returns `null` for per-network runtime (display `—` via `runtimeStatus()`).
- ViewModel stays the bridge — no composable reads `ProxyModeService.state` / `ZerotierBVpnService.state` directly in this task (MainScreen already uses ViewModel).
- No string parsing of `statusMessage` in UI — lifecycle is an enum on service state.

### Steps

1. **Shared runtime models** — create `connection/RuntimeStatusModels.kt`:

   ```kotlin
   enum class JoinStatus {
       JOINING,
       REQUESTING_CONFIG,  // VPN: REQUESTING_CONFIGURATION
       OK,
       ACCESS_DENIED,
       NOT_FOUND,
       DOWN,
       UNKNOWN,
       ERROR,              // VPN: PORT_ERROR, CLIENT_TOO_OLD, AUTH_REQUIRED, etc.
   }

   enum class NodeLifecycleStatus {
       STOPPED,
       STARTING,
       ONLINE,
       PAUSED_DOZE,
       ERROR,
   }

   data class NetworkRuntimeStatus(
       val networkId: String,           // normalized hex via ZerotierBNetwork.normalizeNetworkId
       val joinStatus: JoinStatus,
       val assignedAddresses: List<String> = emptyList(),
       val routes: List<String> = emptyList(),
       val dnsServers: List<String> = emptyList(),
   )
   ```

   → verify: `./gradlew :app:compileDebugKotlin`

2. **Pure mappers + resolver** — create `connection/RuntimeStatusMapper.kt`:

   - `fun ztStatusToJoinStatus(status: ZtNetworkStatus.Status): JoinStatus` — 1:1 for OK/ACCESS_DENIED/NOT_FOUND/DOWN/JOINING/UNKNOWN.
   - `fun ztNetworkToRuntime(hexId: String, zt: ZtNetworkStatus): NetworkRuntimeStatus` — map status + copy addresses/routes/dnsServers.
   - `fun vpnVirtualStatusToJoinStatus(status: VirtualNetworkStatus): JoinStatus` — port existing `formatNetworkStatus` cases from `ZerotierBVpnService` (REQUESTING_CONFIG, OK, ACCESS_DENIED, NOT_FOUND, else ERROR).
   - `fun resolveNetworkRuntime(runtime: Runtime?, proxy: ProxyServiceState, vpn: VpnServiceState, networkId: String): NetworkRuntimeStatus?` — pick list by `runtime`; normalize id; return first match or null.
   - `fun resolveNodeLifecycle(runtime: Runtime?, proxy: ProxyServiceState, vpn: VpnServiceState): NodeLifecycleStatus` — delegate to `proxy.nodeLifecycle` or `vpn.nodeLifecycle` by runtime; OFF → STOPPED.

   → verify: `./gradlew :app:compileDebugKotlin`

3. **Move VPN off string-only status** — edit `vpn/VpnServiceState.kt`:
   - Remove local `NetworkRuntimeStatus` data class (import from `connection`).
   - Add `val nodeLifecycle: NodeLifecycleStatus = NodeLifecycleStatus.STOPPED`.
   - Keep `networkStatuses: List<NetworkRuntimeStatus>`.

   Edit `ZerotierBVpnService.kt`:
   - In `publishNetworkStatuses()`, build `NetworkRuntimeStatus` with `joinStatus = vpnVirtualStatusToJoinStatus(config.status)` (addresses/routes/dns empty for now — VPN JNI config may not expose routes in this path; OK for T11, T14 can enrich).
   - Set `nodeLifecycle` on state updates:
     - Start path → `STARTING` then `ONLINE` when node up
     - `fail()` / shutdown → `STOPPED` or `ERROR` if `lastError` set
   - Delete or inline `formatNetworkStatus` — display strings move to `StatusFormat.joinStatusLabel`.

   → verify: `./gradlew :app:compileDebugKotlin`

4. **Extend ProxyServiceState** — edit `proxy/ProxyServiceState.kt`:

   ```kotlin
   val networkStatuses: List<NetworkRuntimeStatus> = emptyList(),
   val nodeLifecycle: NodeLifecycleStatus = NodeLifecycleStatus.STOPPED,
   ```

   → verify: compile

5. **ProxyModeService publish loop** — edit `proxy/ProxyModeService.kt`:

   Add private helpers:

   ```kotlin
   private fun publishNetworkStatusesFromNode(nodeState: ZtNodeState) {
       val statuses = nodeState.networks.map { (id, zt) ->
           ztNetworkToRuntime(ZerotierBNetwork.normalizeNetworkId(id.toString(16)), zt)
           // use existing hex formatter: ZeroTierNodeManager or StringUtils — match VPN's networkIdToString
       }
       updateState { copy(networkStatuses = statuses) }
   }

   private fun setNodeLifecycle(lifecycle: NodeLifecycleStatus, block: (ProxyServiceState) -> ProxyServiceState = { it.copy(nodeLifecycle = lifecycle) }) {
       updateState { block(this).copy(nodeLifecycle = lifecycle) }
   }
   ```

   **Wire triggers** (grep `updateState` after each):

   | Event | `nodeLifecycle` | `networkStatuses` |
   | ----- | --------------- | ----------------- |
   | `ACTION_START` / start coroutine begins | `STARTING` | `[]` |
   | Node online (after `nodeManager.start`) | `ONLINE` | publish from `nodeManager.state.value` |
   | Each `nodeManager.state` emit in `networkStateJob` | keep | `publishNetworkStatusesFromNode(nodeState)` **before** OK-only `applyNetworkRuntime` |
   | `joinConfiguredNetwork` after join (before ready) | — | implicit via manager JOINING emit |
   | `pauseNodeForDoze()` | `PAUSED_DOZE` | `[]` (cleared — node left) |
   | `resumeNodeFromDoze()` success | `ONLINE` | republish as joins complete |
   | `resumeNodeFromDoze()` start fail | `PAUSED_DOZE` | keep `[]` |
   | `fail()` / stop reset | `ERROR` or `STOPPED` | `[]` |

   **Important:** widen `networkStateJob` collector — today it skips non-OK for UI; still only call `applyNetworkRuntime` on OK, but always publish statuses.

   Use `StringUtils.networkIdToString(id)` or same helper VPN uses — **must match** `ZerotierBNetwork.normalizeNetworkId` lookups in ViewModel.

   → verify: `./gradlew :app:compileDebugKotlin`

6. **StatusFormat display helpers** — edit `ui/StatusFormat.kt`:

   ```kotlin
   fun joinStatusLabel(status: JoinStatus): String  // table → strings.xml keys optional; hardcode OK for unit tests if strings not added yet
   fun nodeLifecycleLabel(status: NodeLifecycleStatus): String
   enum class JoinStatusChipRole { NEUTRAL, SUCCESS, ERROR }
   fun joinStatusChipRole(status: JoinStatus): JoinStatusChipRole
   ```

   `PAUSED_DOZE` label must differ from `STOPPED` (AC). Prefer `strings.xml` entries: `join_status_joining`, `join_status_ok`, …, `node_lifecycle_paused_doze`.

   → verify: compile

7. **MainViewModel unified API** — edit `ui/MainViewModel.kt`:

   ```kotlin
   fun activeRuntime(): Runtime? = uiState.value.plan?.runtime

   fun nodeLifecycle(): NodeLifecycleStatus =
       resolveNodeLifecycle(uiState.value.plan?.runtime, uiState.value.proxy, uiState.value.vpn)

   fun networkRuntime(networkId: String): NetworkRuntimeStatus? =
       resolveNetworkRuntime(uiState.value.plan?.runtime, uiState.value.proxy, uiState.value.vpn, networkId)

   fun nodeId(): String? = when (uiState.value.plan?.runtime) {
       Runtime.PROXY -> uiState.value.proxy.nodeId
       Runtime.VPN -> uiState.value.vpn.nodeId.takeIf { it.isNotBlank() }
       else -> null
   }

   fun runtimeStatus(networkId: String): String {
       val rt = networkRuntime(networkId) ?: return "—"
       return joinStatusLabel(rt.joinStatus)
   }
   ```

   Remove old `runtimeStatus(networkId, state: VpnServiceState)` overload.

   → verify: `./gradlew :app:compileDebugKotlin`

8. **MainScreen call-site** — edit `ui/MainScreen.kt` line ~180:

   ```kotlin
   runtimeStatus = viewModel.runtimeStatus(network.networkId),
   ```

   No other layout changes.

   → verify: `./gradlew :app:lintDebug :app:assembleDebug`

9. **Unit tests** — add `app/src/test/java/com/brukb/zerotier/connection/RuntimeStatusMapperTest.kt`:

   - `ztStatusToJoinStatus_allValues`
   - `vpnVirtualStatusToJoinStatus_requestingAndOk`
   - `resolveNetworkRuntime_proxyWinsOverVpnWhenRuntimeProxy` — proxy list has OK, VPN list empty, runtime PROXY → OK
   - `resolveNetworkRuntime_returnsNullWhenOff`
   - `resolveNetworkRuntime_normalizesNetworkId` — mixed case hex
   - `resolveNodeLifecycle_pausedDozeFromProxy`

   Extend `StatusFormatTest`:
   - every `JoinStatus` → non-empty `joinStatusLabel`
   - every `NodeLifecycleStatus` → non-empty `nodeLifecycleLabel`
   - `PAUSED_DOZE` != `STOPPED` labels
   - `joinStatusChipRole(OK)` != `joinStatusChipRole(JOINING)`

   → verify: `./gradlew :app:testDebugUnitTest --tests "com.brukb.zerotier.connection.RuntimeStatusMapperTest" --tests "com.brukb.zerotier.ui.StatusFormatTest"`

10. **Full gate** — `make verify`

### Tests to add

| Test class | Cases |
| ---------- | ----- |
| `RuntimeStatusMapperTest` | ZT map, VPN map, resolver runtime pick, id normalize, lifecycle |
| `StatusFormatTest` | labels exhaustive, chip roles, doze ≠ stopped |

No Robolectric / no `MainViewModel` instrumented tests — resolver is pure.

### Verify commands

```bash
make verify
./gradlew :app:testDebugUnitTest --tests "com.brukb.zerotier.connection.RuntimeStatusMapperTest" --tests "com.brukb.zerotier.ui.StatusFormatTest"
```

### Risks / pitfalls

- **Network ID format mismatch** — proxy uses `Long` keys, VPN uses `StringUtils.networkIdToString`. Resolver must normalize both sides with `ZerotierBNetwork.normalizeNetworkId` or rows stay `—`. Grep VPN publish path and copy the same formatter in `publishNetworkStatusesFromNode`.
- **`networkStateJob` only on OK today** — easy to miss JOINING; fix is publish on every `nodeState` emit.
- **Doze pause clears networks** — per-network list empty while `nodeLifecycle == PAUSED_DOZE` is correct; don't show stale OK from pre-pause map (clear on `pauseNodeForDoze`).
- **Exclusive stack** — resolver must check `plan.runtime`, not `proxy.isRunning && vpn.isRunning` (during swap both may flicker).
- **Do not** parse `"ZeroTier paused (Doze)"` in ViewModel — set `nodeLifecycle = PAUSED_DOZE` in `pauseNodeForDoze()`.
- **VPN regression** — `publishNetworkStatuses` must still fire on config callbacks; chip text changes from "Connected" to label from `joinStatusLabel(OK)` — acceptable; T12 owns chip UI.

### Out of scope

- Hero card, chips, colors in composables (T12)
- Settings sheet (T13)
- Network detail routes panel (T14 — data fields populated in T11 for proxy, VPN addresses optional empty)
- `NodeLifecycleStatus` on orchestrator / ConnectionOrchestrator
- New Gradle deps, BOM bump

### Execute model recommendation

- **medium** — several files but mechanical mapping; no architecture discovery.

## Reality notes

### From T09 close-out

- UI observes service state via ViewModel combine (`vpn` + `proxy` + `orchestrator.state`).
- `runtimeStatus` currently VPN-only — confirmed bug to fix.
- Compose BOM stays `2024.12.01`; no layout work in T11.

### Codebase delta from stub assumptions

- `NetworkRuntimeStatus` already exists but in `vpn` package with `status: String` only — **move/extend**, don't duplicate.
- `ZtNetworkStatus` already carries addresses/routes/DNS — proxy publish can copy them now (feeds T14).
- `nodePausedForDoze` is private to `ProxyModeService` — expose lifecycle via `ProxyServiceState.nodeLifecycle`, not VM reading service internals.


## Test Plan

- Unit tests (no Robolectric):
  - `StatusFormatTest` / new `RuntimeStatusMappingTest`: every `JoinStatus` and
    `NodeLifecycleStatus` maps to non-empty label; `PAUSED_DOZE` distinct from `STOPPED`.
  - `MainViewModel` or pure helper: when `plan.runtime == PROXY`, network status
    comes from `proxy.networkStatuses`, not VPN list.
  - Table test: `ZtNetworkStatus.Status` → `JoinStatus` mapping.
- Commands: `./gradlew :app:testDebugUnitTest --tests "com.brukb.zerotier.ui.*"`
- `make verify`

## Acceptance Criteria

- [x] With PROXY active and one enabled network joined, `MainViewModel.networkRuntime(id)`
      returns `OK` (not `—`) without opening VPN.
- [x] While network is joining, status is `JOINING` / equivalent before `OK`.
- [x] After doze pause (`pauseNodeInDoze` on), `nodeLifecycle()` is `PAUSED_DOZE` and
      recovers to `ONLINE` after unlock (regression for recent fix).
- [x] VPN mode unchanged: per-network statuses still correct.
- [x] No composable layout changes required to validate (debug log or unit test OK).
- [x] `make verify` green; new tests added.

## Verification

**Date:** 2026-08-31

Presence:
- `Makefile` `verify` target — present
- `lefthook.yml` pre-commit → `make verify` — present
- `app/lint.xml` — present

Commands:
```
test -f Makefile && grep -q '^verify' Makefile
test -f lefthook.yml
test -f app/lint.xml
make verify
./gradlew :app:testDebugUnitTest --tests "com.brukb.zerotier.connection.RuntimeStatusMapperTest" --tests "com.brukb.zerotier.ui.StatusFormatTest"
```

Outcomes:
- `make verify` — lintDebug, unit tests, assembleDebug — BUILD SUCCESSFUL
- `RuntimeStatusMapperTest` + `StatusFormatTest` — green

## Files Modified

- `app/src/main/java/com/brukb/zerotier/connection/RuntimeStatusModels.kt` (new)
- `app/src/main/java/com/brukb/zerotier/connection/RuntimeStatusMapper.kt` (new)
- `app/src/main/java/com/brukb/zerotier/vpn/VpnServiceState.kt`
- `app/src/main/java/com/brukb/zerotier/vpn/ZerotierBVpnService.kt`
- `app/src/main/java/com/brukb/zerotier/proxy/ProxyServiceState.kt`
- `app/src/main/java/com/brukb/zerotier/proxy/ProxyModeService.kt`
- `app/src/main/java/com/brukb/zerotier/ui/StatusFormat.kt`
- `app/src/main/java/com/brukb/zerotier/ui/MainViewModel.kt`
- `app/src/main/java/com/brukb/zerotier/ui/MainScreen.kt`
- `app/src/test/java/com/brukb/zerotier/connection/RuntimeStatusMapperTest.kt` (new)
- `app/src/test/java/com/brukb/zerotier/ui/StatusFormatTest.kt`
- `planning/phases/T11-proxy-vpn-unified-runtime-state.md`
- `planning/phases/INDEX.md` (InProgress)

## Manual test (for humans)

```bash
./gradlew :app:installDebug
# PROXY mode, one enabled network — watch log or temporary debug Text in MainScreen:
adb logcat -s ProxyModeService MainViewModel
```

1. Enable PROXY, one network ON → join → status should transition JOINING → OK in VM state.
2. Lock device (doze pause on) → `PAUSED_DOZE`; unlock → `ONLINE`.
3. Switch to VPN → same network shows VPN-side status; proxy map not shown.

## Learnings

- Shared runtime models live in `connection/` (`JoinStatus`, `NodeLifecycleStatus`, `NetworkRuntimeStatus`) — not in `vpn/` only.
- Proxy `networkStateJob` must publish **all** join states to UI, not only call `applyNetworkRuntime` on OK.
- `NodeLifecycleStatus.PAUSED_DOZE` set explicitly in `pauseNodeForDoze()` — never parse `statusMessage` in UI.
- Network ID keys: always `StringUtils.networkIdToString` + `ZerotierBNetwork.normalizeNetworkId` for resolver lookups.
- Display strings for join/lifecycle → `StatusFormat` pure helpers (T12 chips consume `joinStatusChipRole`).
