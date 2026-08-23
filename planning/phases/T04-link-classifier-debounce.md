# T04 — Link classifier + debounce

**Status**: Done  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T03  
**Next**: T05  
**Layer**: L3

## Description

Classify physical Network (strip our VPN). Wi-Fi SSID normalize; unknown → PROXY no row. Per-data-SIM. Other singleton. Trailing debounce 5s (3–15s).

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| 2026-08-23 | created | — | Pending | stub seeded by bootstrap | |
| 2026-08-23 | planned | Pending | Planned | /task-1-plan: classifier + selector + debouncer; no registration | |
| 2026-08-23 | execute | Planned | InProgress | /task-2-execute T04 | |
| 2026-08-23 | complete | InProgress | Done | /task-3-complete T04 (no push) | |

## Requirements

- [x] Never classify our VpnService network as Other
- [x] Unknown SSID does not insert LinkProfile
- [x] Debounce last-event-wins (plan-equals skip deferred to orchestrator apply — T07/T08)

## Implementation Plan

*(Filled by `/task-1-plan` — do not invent during bootstrap beyond high-level notes.)*

### High-level notes (bootstrap)

- android-connectivity.mdc
- Location/NEARBY_WIFI only when AUTO or Links
- Spec: `docs/PROXY-VPN-PLAN.md`

## Execution plan (filled by /task-1-plan)

**Date:** 2026-08-23  
**Codebase snapshot:** branch `T04-link-classifier-debounce` @ T03 `b30605d`. `connection/` has `PhysicalLink` / `RuntimePlan` / `RuntimePlanResolver` (pure). Room v3 + `LinkProfileRepository` (T02). Manifest has `ACCESS_NETWORK_STATE`; **no** `ACCESS_WIFI_STATE`, no location/nearby-wifi, no `READ_PHONE_STATE`. `ZerotierBVpnService.state.isRunning` exists; no `setUnderlyingNetworks` call. No `system/` package beyond `BootReceiver`. coroutines-android 1.9.0 present; **no** `kotlinx-coroutines-test` dep.  
**Execute model:** small (default)

### Context for executor

**Goal:** Android-facing **classifier** that turns live `ConnectivityManager` state into `PhysicalLink` (mode resolved from `LinkProfile` rows), plus a **trailing debouncer**. No orchestrator, no `RuntimePlan` apply, no service starts (T07/T08). No permission prompts (T08/T09). No `SubscriptionManager` listener (T08) — data sub id arrives as a parameter.

**New files:**
- `app/src/main/java/com/brukb/zerotier/connection/LinkClassifier.kt`
- `app/src/main/java/com/brukb/zerotier/connection/LinkDebouncer.kt`
- `app/src/main/java/com/brukb/zerotier/connection/LinkModeLookup.kt` (SAM interface so tests avoid Room)
- `app/src/main/java/com/brukb/zerotier/connection/SsidNormalizer.kt` (pure)
- `app/src/main/java/com/brukb/zerotier/connection/PhysicalLinkSelector.kt` (pure — picks underlying network from candidate list)
- `app/src/main/java/com/brukb/zerotier/system/LinkNetworkCallback.kt` (thin callback → debouncer)

**Existing (reuse / read-only):**
- `connection/PhysicalLink.kt`, `data/model/LinkProfile.kt`, `data/LinkProfileRepository.kt`
- `vpn/ZerotierBVpnService.kt` — companion `state.isRunning` (add one companion field, below)

**Invariants (android-connectivity.mdc):**
- Never treat our `VpnService` network as physical link — strip `TRANSPORT_VPN`; use `underlyingNetworks` (API 31+) when our VPN is running
- Unknown SSID → `WifiUnknown` → implicit PROXY; **no** `LinkProfile` insert
- Data SIM via parameter `dataSubscriptionId` (API 29+ `getActiveDataSubscriptionId` is T08)
- Other = singleton built-in row `other` (seeded in T02); third-party VPN transport → Other
- Debounce: trailing, last-event-wins, default 5s clamp 3–15s (clamp lives in `AppPreferences` write — T02)

### Steps

1. **`SsidNormalizer.kt`** (pure):
   ```kotlin
   object SsidNormalizer {
       fun normalize(raw: String?): String?  // null/blank → null
   }
   ```
   Rules: `trim()`; if starts+ends with `"` strip one pair then trim again; result blank → null; equals `<unknown ssid>` (case-insensitive) → null; starts with `0x` (case-insensitive) → null; else return.  
   → verify: `SsidNormalizerTest` (table below).

2. **`PhysicalLinkSelector.kt`** (pure — JVM-testable core of “which network”):
   ```kotlin
   data class LinkCandidate(
       val isVpn: Boolean,
       val isWifi: Boolean,
       val isCellular: Boolean,
       val underlyingWifi: Boolean = false,   // from VPN underlyingNetworks (API 31+)
       val underlyingCellular: Boolean = false,
   )

   object PhysicalLinkSelector {
       fun pick(candidates: List<LinkCandidate>): LinkCandidate?
   }
   ```
   Algorithm (spec §5.1): drop `isVpn` entries; if any dropped VPN had `underlyingWifi`/`underlyingCellular`, prefer a remaining candidate matching that transport; else first WIFI, else first CELLULAR, else first remaining; empty → null.  
   → verify: `PhysicalLinkSelectorTest` (table below).

3. **`LinkModeLookup.kt`**:
   ```kotlin
   fun interface LinkModeLookup {
       suspend fun modeForSsid(ssid: String): LinkMode?      // null → no row
       suspend fun modeForSubscription(subscriptionId: Int): LinkMode  // default PROXY
       suspend fun modeForOther(): LinkMode                  // default PROXY
   }
   ```
   (Three suspend funcs — use `interface` with default impls instead if SAM invalid; simplest: `interface LinkModeLookup` with three methods.)  
   → verify: compiles.

4. **`LinkClassifier.kt`** — constructor: `(connectivityManager: ConnectivityManager, modeLookup: LinkModeLookup)`. Methods:
   ```kotlin
   fun isOurVpnRunning(): Boolean = ZerotierBVpnService.state.value.isRunning

   @RequiresPermission(...)  // annotate; callers ensure grants
   suspend fun classify(dataSubscriptionId: Int?): PhysicalLink
   ```
   - `val active = connectivityManager.activeNetwork`; `val caps = active?.let { connectivityManager.getNetworkCapabilities(it) }`
   - If `caps == null` → `PhysicalLink.None`
   - If `caps.hasTransport(TRANSPORT_VPN)`:
     - if `!isOurVpnRunning()` → Other path (step 6) with `modeForOther()`
     - if ours: API 31+ → `caps.underlyingNetworks`; find first underlying with WIFI → Wi-Fi path; else CELLULAR → mobile path; else → Other path. API <31 or empty underlying → fall through to step 5 scan.
   - If WIFI → Wi-Fi path; CELLULAR → mobile path; else → Other path.
   - **Wi-Fi path:** read SSID via `caps.wifiInfo?.ssid` (API 29+ `transportInfo` cast to `WifiInfo`); if null and API < 29, fallback `(context.getSystemService(WifiManager::class.java))?.connectionInfo?.ssid` — pass `Context` into constructor for this fallback only. `SsidNormalizer.normalize` → null → `PhysicalLink.WifiUnknown`; else `modeLookup.modeForSsid(ssid)` → null → `WifiUnknown`; else `WifiKnown(ssid, mode)`.
   - **Mobile path:** `subId = dataSubscriptionId ?: SubscriptionManager.DEFAULT_SUBSCRIPTION_ID`; `PhysicalLink.Mobile(subId, modeLookup.modeForSubscription(subId))`.
   - **Other path:** `PhysicalLink.Other(modeLookup.modeForOther())`.
   - **Step 5 scan (fallback when active is our VPN and no underlying info):** iterate `connectivityManager.allNetworks`, `getNetworkCapabilities(n)`, build `LinkCandidate`s, `PhysicalLinkSelector.pick`, then dispatch WIFI/CELLULAR/Other as above (null → `None`).
   → verify: compiles; `:app:assembleDebug`.

5. **`LinkDebouncer.kt`**:
   ```kotlin
   class LinkDebouncer(
       private val scope: CoroutineScope,
       private val delayMs: () -> Long,   // read current pref each event
       private val action: suspend () -> Unit,
   ) {
       private val mutex = Mutex()
       private var job: Job? = null

       fun trigger() { /* cancel previous; launch { delay(delayMs()); action() } */ }
       suspend fun cancel() { /* cancel + join under mutex */ }
   }
   ```
   `trigger()` is **not** suspend (callback-safe); internal `scope.launch { mutex.withLock { job?.cancel(); job = launch { delay(delayMs()); action() } } }`.  
   → verify: `LinkDebouncerTest` with `kotlinx-coroutines-test` (`runTest`, `StandardTestDispatcher` or `advanceTimeBy`).

6. **`system/LinkNetworkCallback.kt`**:
   ```kotlin
   class LinkNetworkCallback(private val onEvent: () -> Unit) : ConnectivityManager.NetworkCallback() {
       override fun onAvailable(network: Network) = onEvent()
       override fun onLost(network: Network) = onEvent()
       override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = onEvent()
   }
   ```
   No registration helper yet (T08 owns lifecycle).  
   → verify: compiles.

7. **`ZerotierBVpnService` edit (minimal):** in `rebuildVpn()` after successful `builder.establish()` (around line 545), on API 29+ call `setUnderlyingNetworks(...)` with the current non-VPN default network if obtainable via `getSystemService(ConnectivityManager::class.java)` — wrap in try/catch, log on failure, never crash. Add companion `@Volatile var lastUnderlyingNetworkHandle: Long? = null` set from that network’s `networkHandle` (for future tests/T08). If this proves fragile, **skip the establish-time call** and rely on classifier step-5 scan — note choice in task Verification.  
   → verify: `:app:assembleDebug` + lint.

8. **`app/build.gradle.kts`:** add `testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")` (match existing coroutines version).  
   → verify: `./gradlew :app:testDebugUnitTest`.

9. **Tests** under `app/src/test/java/com/brukb/zerotier/connection/`:
   - `SsidNormalizerTest`
   - `PhysicalLinkSelectorTest`
   - `LinkDebouncerTest`
   → verify: full suite green.

10. **`make verify`** → record in Verification.

### Tests to add

**SsidNormalizerTest** (raw → expected):

| raw | expected |
|-----|----------|
| `"\"HomeWifi\""` | `HomeWifi` |
| `"HomeWifi"` | `HomeWifi` |
| `"<unknown ssid>"` | null |
| `"\"<unknown ssid>\""` | null |
| `"0x"` / `"0xdeadbeef"` | null |
| `""` / `"   "` / null | null |
| `"\"  Cafe Wi-Fi  \""` | `Cafe Wi-Fi` |
| `"home"` vs `"Home"` distinct (no case fold) | as-is |

**PhysicalLinkSelectorTest:**

| candidates | expect |
|-----------|--------|
| [] | null |
| [wifi] | wifi |
| [vpn] | null |
| [vpn(underlyingWifi), wifi, cell] | wifi |
| [vpn(underlyingCell), wifi, cell] | cell |
| [vpn(no underlying), wifi, cell] | wifi |
| [cell, other] | cell |
| [other] | other |

**LinkDebouncerTest** (runTest):
- two triggers 100ms apart, debounce 500ms → action runs **once** (last wins)
- trigger, advance past debounce, trigger again → action twice
- `cancel()` before delay → zero runs
- `delayMs` read at trigger time (change supplier between triggers → second uses new value)

### Verify commands

- `./gradlew :app:testDebugUnitTest --console=plain`
- `make verify`

### Risks / pitfalls

- **Do not** register a live `NetworkCallback` in Application — T08 owns lifecycle/permissions.
- **Do not** call `SubscriptionManager` / `getActiveDataSubscriptionId` — needs `READ_PHONE_STATE`; T08. Parameter only.
- **Do not** insert `LinkProfile` on unknown SSID.
- `caps.wifiInfo` needs `ACCESS_WIFI_STATE` for some fields; SSID via `WifiInfo` from `NetworkCapabilities` on API 29+ works with location/nearby perms — without perms returns `<unknown ssid>` → `WifiUnknown` (correct).
- Add `ACCESS_WIFI_STATE` to manifest (normal perm, safe). **Do not** add location/nearby-wifi/phone-state yet (T08/T09 request UX).
- `setUnderlyingNetworks` is `protected` on `VpnService` — call from inside `ZerotierBVpnService` only.
- Debouncer: never `runBlocking`; callback path uses `scope.launch`.
- Keep classifier `suspend` only where `modeLookup` is called; selector/normalizer stay pure.
- If `LinkModeLookup` as `fun interface` with 3 methods is invalid Kotlin, use plain `interface` (it is invalid — fun interface needs exactly one abstract method; use `interface`).

### Out of scope

- ConnectionOrchestrator / apply plan / service swap (T07)
- `NetworkCallback` registration lifecycle, AUTO observer, permission requests, `READ_PHONE_STATE` (T08)
- libzt / proxy / Shizuku (T05/T06)
- UI Links screen / Save SSID action (T09)
- Boot receiver changes (T08)

### Execute model recommendation

- **small** — pure selector/normalizer + thin Android glue + coroutines test; plan is file-level complete.

## Test Plan

- Prove this layer without later layers.
- Commands: `make verify` (lint + unit tests + assembleDebug)
- New Kotlin: tests required

## Acceptance Criteria

- [x] Unit tests with fake NetworkCapabilities
- [x] SSID quote-strip and unknown sentinels
- [x] Tests added/updated for new behavior
- [x] Full lint + test verify suite green
- [x] Verification commands recorded and passing
- [x] No secrets committed

## Verification

```text
2026-08-23 /task-2-execute T04
Presence: Makefile verify + lefthook.yml + app/lint.xml OK
make verify → PASS
  :app:lintDebug PASS
  :app:testDebugUnitTest PASS (SsidNormalizer / PhysicalLinkSelector / LinkDebouncer)
  :app:assembleDebug PASS
Note: NetworkCapabilities.getUnderlyingNetworks absent from public SDK jars —
classifier uses PhysicalLinkSelector scan when our VPN is activeNetwork.
setUnderlyingNetworks still set on establish (VpnService API exists).
Plan-equals skip on apply deferred to T07/T08 (no orchestrator yet).

2026-08-23 /task-3-complete T04 (re-verify)
Presence OK; make verify → PASS
```

## Files Modified

- `app/src/main/java/com/brukb/zerotier/connection/SsidNormalizer.kt` (new)
- `app/src/main/java/com/brukb/zerotier/connection/PhysicalLinkSelector.kt` (new)
- `app/src/main/java/com/brukb/zerotier/connection/LinkModeLookup.kt` (new)
- `app/src/main/java/com/brukb/zerotier/connection/LinkClassifier.kt` (new)
- `app/src/main/java/com/brukb/zerotier/connection/LinkDebouncer.kt` (new)
- `app/src/main/java/com/brukb/zerotier/system/LinkNetworkCallback.kt` (new)
- `app/src/main/java/com/brukb/zerotier/vpn/ZerotierBVpnService.kt` (setUnderlyingNetworks)
- `app/src/main/AndroidManifest.xml` (`ACCESS_WIFI_STATE`)
- `app/build.gradle.kts` (`kotlinx-coroutines-test`)
- `app/src/test/.../SsidNormalizerTest.kt`, `PhysicalLinkSelectorTest.kt`, `LinkDebouncerTest.kt`
- `.cursor/rules/android-connectivity.mdc`, `kotlin.mdc` (dialectic)
- `planning/phases/INDEX.md` / `T04` / `T05` reality notes

## Manual test (for humans)

```text
Nothing to test — classifier/debouncer not registered yet (T08 wires NetworkCallback).
Unit coverage: ./gradlew :app:testDebugUnitTest --tests '*SsidNormalizer*' --tests '*PhysicalLinkSelector*' --tests '*LinkDebouncer*'
```

## Learnings

- Mode A: public SDK missing `getUnderlyingNetworks` → scan path; `advanceUntilIdle` drains debounce delays in tests.
- Mode B: encoded in `android-connectivity.mdc` + `kotlin.mdc`.
- Plan-equals skip still deferred to orchestrator apply.

## Reality notes

*(Amended by upstream `/task-3-complete` if prior tasks changed assumptions)*

### From T03 close-out

- Emit `com.brukb.zerotier.connection.PhysicalLink` (WifiKnown/WifiUnknown/Mobile/Other/None) with `LinkMode` already resolved from profiles.
- Unknown Wi‑Fi → `PhysicalLink.WifiUnknown` (implicit PROXY in resolver) — **do not** insert `LinkProfile`.
- No uplink → `PhysicalLink.None` → AUTO resolves OFF.
- Classifier may stay Android-facing; feed already-built `PhysicalLink` into `RuntimePlanResolver` later (orchestrator). Do not put `Context` into resolve.
