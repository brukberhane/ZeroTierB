# T07 — VPN single-net + exclusive stack swap

**Status**: Done  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T06  
**Next**: T08  
**Layer**: L6

## Description

rebuildVpn() only main net; leave() others. Orchestrator swap PROXY↔VPN: disable Global, stop libzt, wait, start JNI (and reverse). Consent-missing path.

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| 2026-08-23 | created | — | Pending | stub seeded by bootstrap | |
| 2026-08-23 | planned | Pending | Planned | /task-1-plan: orchestrator + VPN single-net filter + swap sequences | |
| 2026-08-23 | execute | Planned | InProgress | /task-2-execute T07 | |
| 2026-08-23 | complete | InProgress | Done | /task-3-complete T07 | |

## Requirements

- [x] ZerotierBVpnService honors allowedVpnNetworkIds
- [x] Stop-complete callback for orchestrator
- [x] No background VpnService.prepare

## Implementation Plan

*(Filled by `/task-1-plan` — do not invent during bootstrap beyond high-level notes.)*

### High-level notes (bootstrap)

- android-vpn.mdc
- zerotier-jni.mdc
- connection-orchestrator.mdc
- Spec: `docs/PROXY-VPN-PLAN.md`

## Execution plan (filled by /task-1-plan)

**Date:** 2026-08-23  
**Codebase snapshot:** branch `T07-vpn-single-net-swap` @ T06 `170cc97`. `connection/` has `RuntimePlan`, `RuntimePlanResolver`, `LinkClassifier`, `LinkDebouncer`, `PhysicalLinkSelector`, `LinkModeLookup` (T03/T04). `ZerotierBVpnService` joins **all** enabled nets on start (`refreshJoinedNetworks`); `rebuildVpn()` routes every OK config; `shutdown()` is synchronous under `synchronized(this)` but there is no completion signal. `ProxyModeService` self-writes Global `HTTP_PROXY` after bind (T06). `ZerotierBApplication` clears stale proxy on start. No orchestrator exists. `MainActivity` debug intents: `start_proxy` / `stop_proxy` / `grant_secure_settings`.  
**Execute model:** medium — new orchestrator + VPN filter + awaitable stop + debug intents + pure tests; all shapes specified below.

### Context for executor

**Goal:** Introduce `ConnectionOrchestrator` (application-scoped, mutex-serialized) that resolves `RuntimePlan` and drives exclusive stack swaps: PROXY↔VPN↔OFF. VPN runtime must put **only** the main network on TUN and `leave()` all others on the JNI node. Proxy↔VPN swaps must not leak `HTTP_PROXY` (disable before stopping libzt). No background `VpnService.prepare()` — consent-missing plans fall back to PROXY/OFF per resolver (T03).

**Key files:**
- New → `connection/ConnectionOrchestrator.kt`
- Edit → `vpn/ZerotierBVpnService.kt` — `EXTRA_SINGLE_NETWORK_ID` filter + `stopAndAwait()`
- Edit → `proxy/ProxyModeService.kt` — `stopAndAwait()` + optional `joinNetworkIds` extra
- Edit → `ZerotierBApplication.kt` — construct orchestrator
- Edit → `ui/MainActivity.kt` — debug intents `apply_mode` / `stop_all`
- New tests → `app/src/test/java/com/brukb/zerotier/connection/ConnectionOrchestratorTest.kt`

**Invariants (connection-orchestrator.mdc / android-vpn.mdc / zerotier-jni.mdc / android-http-proxy.mdc):**
1. At most one ZeroTier node live. Swap = stop A, wait dead, start B.
2. VPN joins **only** main; `leave()` all others. PROXY joins every `isEnabled`.
3. Never call `VpnService.prepare()` from background/callback — Activity only.
4. PROXY→VPN: `SystemProxyManager.disable()` **before** `ProxyModeService.stop()`.
5. VPN→PROXY: stop VPN, await dead, start proxy (proxy binds then writes Global itself).
6. `vpnNetworkId` set iff `runtime == VPN`.
7. Same-runtime plan change (AUTO WiFi A PROXY → WiFi B PROXY): no stack swap; no-op.

### Steps

1. **`connection/ConnectionOrchestrator.kt`** — new file, package `com.brukb.zerotier.connection`.

   ```kotlin
   class ConnectionOrchestrator(
       private val context: Context,
       private val preferences: AppPreferences,
       private val networkRepository: NetworkRepository,
       private val linkProfileRepository: LinkProfileRepository,
       private val scope: CoroutineScope,
   ) {
       private val mutex = Mutex()
       private var lastApplied: RuntimePlan? = null

       private val _state = MutableStateFlow(OrchestratorState())
       val state: StateFlow<OrchestratorState> = _state.asStateFlow()

       suspend fun refresh() { /* resolve + apply */ }
       suspend fun applyGlobalMode(mode: GlobalMode) { preferences.setGlobalMode(mode); refresh() }
       suspend fun applyPlan(plan: RuntimePlan) = mutex.withLock { applyLocked(plan) }
       suspend fun stopAll() = mutex.withLock { applyLocked(offPlan("manual stop")) }
       private suspend fun applyLocked(plan: RuntimePlan) { /* swap sequences below */ }
       private suspend fun stopProxyLocked() { ProxyModeService.stopAndAwait(context) }
       private suspend fun stopVpnLocked() { ZerotierBVpnService.stopAndAwait(context) }
   }
   ```

   `OrchestratorState` data class (same file): `plan: RuntimePlan?`, `isApplying: Boolean`, `lastError: String?`.

   `refresh()`:
   - `globalMode = preferences.globalMode.first()`
   - `enabled = networkRepository.getAll().filter { it.isEnabled }`
   - `link = classifyLink()` — build `LinkModeLookup` over `linkProfileRepository` (`modeForSsid` → `getBySsid()?.mode`, `modeForSubscription` → `getBySubscriptionId()?.mode ?: LinkMode.PROXY`, `modeForOther` → `getById(OTHER_ID)?.mode ?: LinkMode.PROXY`); call `LinkClassifier(context, connectivityManager, lookup).classify(dataSubscriptionId = null)`
   - `vpnConsentGranted = VpnService.prepare(context) == null`
   - `plan = RuntimePlanResolver.resolve(globalMode, link, vpnConsentGranted, enabled)`
   - `applyPlan(plan)`

   `applyLocked(plan)`:
   - If `plan == lastApplied` → log no-op, return.
   - `_state.value = _state.value.copy(isApplying = true, plan = plan)`
   - `try { when (plan.runtime) { OFF → …; PROXY → …; VPN → … } ; lastApplied = plan; _state.value = …(isApplying=false, lastError=null) } catch (e) { _state.value = …(isApplying=false, lastError=e.message); Log.e }`

   **OFF:** if VPN running → `stopVpnLocked()`; if PROXY running → `stopProxyLocked()` (proxy service disables Global itself in `stopProxy`).

   **PROXY:** if VPN running → `stopVpnLocked()`; if PROXY not running → `ProxyModeService.start(context, joinNetworkIds = plan.joinNetworkIds)`.

   **VPN:** if PROXY running → `SystemProxyManager(context, preferences).disable()` then `stopProxyLocked()`; if VPN not running → `ZerotierBVpnService.start(context, singleNetworkId = plan.vpnNetworkId)`.

   → verify: `:app:compileDebugKotlin`.

2. **`ZerotierBVpnService` — single-net filter + awaitable stop.**

   - Add `const val EXTRA_SINGLE_NETWORK_ID = "single_network_id"` to companion.
   - In `onStartCommand` ACTION_START path (currently no action check — it falls through to start), read `intent?.getStringExtra(EXTRA_SINGLE_NETWORK_ID)`; store in a `@Volatile private var allowedVpnNetworkId: String?` (null = all enabled, preserves legacy behavior).
   - `refreshJoinedNetworks()`: after loading `enabled`, if `allowedVpnNetworkId != null`, keep only that network in `networkSettings` and join only it; for every other enabled network, call `node?.leave(network.networkIdLong())` before clearing (spec §6.2: leave non-main on JNI node).
   - `rebuildVpn()`: change `val enabledIds = networkSettings.keys.toSet()` to also filter by `allowedVpnNetworkId` when non-null:
     ```kotlin
     val enabledIds = networkSettings.keys.toSet()
         .filter { allowedVpnNetworkId == null || it == ZerotierBNetwork.parseNetworkIdLong(allowedVpnNetworkId!!) }
         .toSet()
     ```
     (Compute the Long once outside the loop.)
   - `joinNetwork(networkIdHex)`: if `allowedVpnNetworkId != null && networkIdHex != allowedVpnNetworkId` → log skip, return (spec §9.3).
   - Add companion `suspend fun stopAndAwait(context: Context)`:
     ```kotlin
     suspend fun stopAndAwait(context: Context, timeoutMs: Long = 10_000) {
         if (!state.value.isRunning) return
         stop(context)
         withTimeoutOrNull(timeoutMs) { state.first { !it.isRunning } }
     }
     ```
     Need imports `kotlinx.coroutines.flow.first`, `kotlinx.coroutines.withTimeoutOrNull`.
   - `start(context)` overload: `fun start(context: Context, singleNetworkId: String? = null)` — put extra when non-null.

   → verify: `:app:compileDebugKotlin`.

3. **`ProxyModeService` — awaitable stop + join filter.**

   - Add `const val EXTRA_JOIN_NETWORK_IDS = "join_network_ids"` (String array extra).
   - `start(context, forceDebug: Boolean = false, joinNetworkIds: List<String>? = null)` — put `toTypedArray()` extra.
   - In `startProxy`, after loading `enabledNetworks`, if `joinNetworkIds != null` filter to that list (normalize IDs via `ZerotierBNetwork.normalizeNetworkId`).
   - Add companion `suspend fun stopAndAwait(context: Context, timeoutMs: Long = 10_000)`:
     ```kotlin
     suspend fun stopAndAwait(context: Context, timeoutMs: Long = 10_000) {
         if (!state.value.isRunning) return
         stop(context)
         withTimeoutOrNull(timeoutMs) { state.first { !it.isRunning } }
     }
     ```
     Need imports `kotlinx.coroutines.flow.first`, `kotlinx.coroutines.withTimeoutOrNull`.

   → verify: `:app:compileDebugKotlin`.

4. **`ZerotierBApplication` — construct orchestrator.**

   - Add `lateinit var orchestrator: ConnectionOrchestrator; private set`.
   - In `onCreate`, after repositories:
     ```kotlin
     orchestrator = ConnectionOrchestrator(
         context = this,
         preferences = preferences,
         networkRepository = networkRepository,
         linkProfileRepository = linkProfileRepository,
         scope = appScope,
     )
     ```
   → verify: `:app:compileDebugKotlin`.

5. **`MainActivity` — debug intents.**

   Add branches in `handleDebugIntent`:
   ```kotlin
   ACTION_APPLY_MODE -> {
       val raw = intent.getStringExtra(EXTRA_MODE)
       val mode = GlobalMode.parse(raw)
       Log.i(TAG, "adb debug: apply mode=$mode")
       lifecycleScope.launch { (application as ZerotierBApplication).orchestrator.applyGlobalMode(mode) }
   }
   ACTION_STOP_ALL -> {
       Log.i(TAG, "adb debug: stop all")
       lifecycleScope.launch { (application as ZerotierBApplication).orchestrator.stopAll() }
   }
   ```
   Companion: `const val ACTION_APPLY_MODE = "apply_mode"`, `const val EXTRA_MODE = "mode"`, `const val ACTION_STOP_ALL = "stop_all"`. Imports: `androidx.lifecycle.lifecycleScope`, `com.brukb.zerotier.ZerotierBApplication`, `com.brukb.zerotier.data.model.GlobalMode`, `kotlinx.coroutines.launch`.

   → verify: `:app:assembleDebug`; lint.

6. **Tests** — `app/src/test/java/com/brukb/zerotier/connection/ConnectionOrchestratorTest.kt`.

   Pure logic only; no Android framework. Test `RuntimePlan` equality semantics and `MainNetworkSelector` tie-break (already covered in T03? check existing tests — if `RuntimePlanResolverTest` exists, extend it with VPN single-net cases; otherwise create `ConnectionOrchestratorTest` with pure helper tests).

   If `RuntimePlanResolverTest` already covers `vpnNetworkId` / `joinNetworkIds`, add only:
   - `RuntimePlan` data-class equality: same runtime + same vpnNetworkId + same joinNetworkIds → equal (no-op swap trigger).
   - `RuntimePlan` different vpnNetworkId → not equal (swap trigger).

   → verify: `./gradlew :app:testDebugUnitTest --console=plain`.

7. **`make verify`** → record.

### Tests to add

| Case | Expect |
|------|--------|
| `RuntimePlan(VPN, "r", "net1", ["net1"], false)` vs same | equal → no swap |
| `RuntimePlan(VPN, "r", "net1", ["net1"], false)` vs `RuntimePlan(VPN, "r", "net2", ["net2"], false)` | not equal → swap |
| `RuntimePlan(PROXY, "r", null, ["n1","n2"], false)` vs same | equal → no swap |
| `RuntimePlan(PROXY, "r", null, ["n1"], false)` vs `RuntimePlan(PROXY, "r", null, ["n1","n2"], false)` | not equal → re-apply |
| `RuntimePlan(OFF, "r", null, [], false)` vs `RuntimePlan(PROXY, "r", null, ["n1"], false)` | not equal → swap |

### Verify commands

- `./gradlew :app:testDebugUnitTest --console=plain`
- `make verify`

### Risks / pitfalls

- **Proxy self-writes Global** — orchestrator does not call `SystemProxyManager.enable()`; it only calls `disable()` on PROXY→VPN swap. `ProxyModeService.startProxy` already enables after bind (T06).
- **VPN legacy start** — `ZerotierBVpnService.start(context)` without extra must still join all enabled nets (backward compat for existing UI / BootReceiver). Only orchestrator passes `EXTRA_SINGLE_NETWORK_ID`.
- **Stop timeout** — `stopAndAwait` uses 10s timeout; if exceeded, log warning and proceed (spec §7.2 timeout 10s). Do not block forever.
- **Consent check** — `VpnService.prepare(context)` from orchestrator is safe (read-only check, no prompt). Never call `startActivityForResult` from orchestrator.
- **Same-runtime no-op** — `plan == lastApplied` check prevents AUTO WiFi A PROXY → WiFi B PROXY flap.
- **Leave before clear** — in `refreshJoinedNetworks`, `node?.leave()` non-main **before** removing from `networkSettings` / `virtualNetworkConfigs` (spec §6.2).
- **No UI wiring** — T09 owns global mode selector; T07 only adds debug intents for manual test.
- **No BootReceiver change** — boot still starts VPN directly if `startOnBoot`; orchestrator boot path is T08/T09 scope.

### Out of scope

- AUTO `NetworkCallback` registration / debounce wiring (T08)
- UI mode selector, grant card, Links screen (T09)
- Boot orchestrator refresh (T08/T09)
- `ConnectionOrchestrator` unit tests with mocked services (overkill; pure plan equality only)
- SOCKS5, per-app proxy bypass

### Execute model recommendation

- **medium** (default) — orchestrator + VPN filter + awaitable stop + debug intents; every file and shape specified above. No architecture left to discover.

## Test Plan

- Prove this layer without later layers.
- Commands: `make verify` (lint + unit tests + assembleDebug)
- New Kotlin: tests required

## Acceptance Criteria

- [ ] Two enabled ZT nets + VPN runtime → only main on TUN (manual)
- [x] Swap does not leak HTTP_PROXY (orchestrator disables Global before proxy stop)
- [x] make verify green
- [x] Tests added/updated for new behavior
- [x] Full lint + test verify suite green
- [x] Verification commands recorded and passing
- [x] No secrets committed

## Verification

- `make verify` — **PASS** (2026-08-23): lintDebug, testDebugUnitTest (app + core), assembleDebug
- `./gradlew :app:testDebugUnitTest --console=plain` — **PASS** (ConnectionOrchestratorTest 5 cases + existing)
- `ConnectionOrchestrator` mutex-serialized apply; PROXY→VPN disables Global before proxy stop
- `ZerotierBVpnService.EXTRA_SINGLE_NETWORK_ID` filters join + rebuildVpn; legacy start unchanged
- `stopAndAwait()` on both services (10s timeout)

## Files Modified

- `app/src/main/java/com/brukb/zerotier/connection/ConnectionOrchestrator.kt` — new
- `app/src/main/java/com/brukb/zerotier/vpn/ZerotierBVpnService.kt` — single-net filter, stopAndAwait
- `app/src/main/java/com/brukb/zerotier/proxy/ProxyModeService.kt` — join filter, stopAndAwait
- `app/src/main/java/com/brukb/zerotier/ZerotierBApplication.kt` — orchestrator construct
- `app/src/main/java/com/brukb/zerotier/ui/MainActivity.kt` — debug apply_mode / stop_all
- `app/src/test/java/com/brukb/zerotier/connection/ConnectionOrchestratorTest.kt` — new (5 cases)

## Manual test (for humans)

**Prereq:** Two enabled ZT networks in app DB; VPN consent granted (`VpnService.prepare` null); optional `WRITE_SECURE_SETTINGS` for PROXY Global test.

```bash
make verify
./gradlew :app:installDebug

# Grant secure settings (optional, for Global proxy check)
adb shell pm grant com.brukb.zerotier android.permission.WRITE_SECURE_SETTINGS

# Must use DEBUG action + SINGLE_TOP (0x20000000). Plain MAIN `am start`
# is dropped when MainActivity is already on top (Samsung: "intent delivered").
DEBUG='am start -a com.brukb.zerotier.DEBUG -n com.brukb.zerotier/.ui.MainActivity -f 0x20000000'

# PROXY via orchestrator (all enabled nets)
adb shell $DEBUG --es zerotierb_action apply_mode --es mode PROXY
adb logcat -d -s ConnectionOrchestrator ProxyModeService | tail -10
# Expect: proxy running; "System proxy set" if granted

adb shell settings get global http_proxy
# Expect: 127.0.0.1:<PORT> when granted

# VPN via orchestrator (main net only) — consent dialog if not yet granted
adb shell $DEBUG --es zerotierb_action apply_mode --es mode VPN
adb logcat -d -s ConnectionOrchestrator ZerotierBVpnService | tail -15
# Expect: only main net joined; rebuildVpn status "VPN active (1 networks)"

adb shell settings get global http_proxy
# Expect: :0 or prior user proxy (not our loopback)

# Stop everything
adb shell $DEBUG --es zerotierb_action stop_all
```

**Success:** PROXY→VPN swap clears Global proxy; VPN TUN shows one network; PROXY and VPN never both `isRunning`.

## Learnings

- `ConnectionOrchestrator` mutex + `lastApplied` plan equality prevents equal-runtime re-swap.
- PROXY→VPN: `SystemProxyManager.disable()` before `ProxyModeService.stopAndAwait()` — order matters for AC.
- `stopAndAwait()` polls service `StateFlow.isRunning` (10s timeout); no custom callback needed.
- `EXTRA_SINGLE_NETWORK_ID` on VPN start; legacy `start()` without extra still joins all enabled (BootReceiver compat).
- `VpnService.prepare()` in orchestrator is read-only consent check — never prompts from background.
- Debug intents: `apply_mode` + `mode=PROXY|VPN|OFF|AUTO`, `stop_all`. ADB must use action `com.brukb.zerotier.DEBUG` + `-f 0x20000000` (`singleTop`); launcher `MAIN` extras are dropped when activity already on top.

## Reality notes

*(Amended by upstream `/task-3-complete` if prior tasks changed assumptions)*

### From T06 close-out

- `com.brukb.zerotier.proxy.SystemProxyManager` — sole writer of `Settings.Global.HTTP_PROXY`; `enable(port)` / `disable()` / `hasPermission()` / `shouldClearStale(...)`.
- `ProxyModeService` calls `enable(boundPort)` after listen; `disable()` first on stop. State: `systemProxyActive`, `hasSecureSettingsPermission`.
- Shizuku 13.1.5 (`api` + `provider`); `ShizukuProvider` in manifest; grant via `ShizukuPermissionHelper.grantWriteSecureSettings` or ADB `pm grant`.
- Stale-proxy clear on `ZerotierBApplication.onCreate` when mode ≠ PROXY — T07 orchestrator must call `disable()` on PROXY→VPN swap before stopping libzt (AC: "Swap does not leak HTTP_PROXY").
- `GlobalMode.PROXY` not settable from UI yet (T09) — orchestrator in T07 should drive enable/disable from resolved runtime plan, not raw debug intents.
- VPN mutex unchanged: proxy refuses when `ZerotierBVpnService.state.isRunning` unless `EXTRA_FORCE_DEBUG`.
