# T08 — AUTO physical-link observer

**Status**: Done  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T07  
**Next**: T09  
**Layer**: L6

## Description

NetworkCallback + SubscriptionManager upsert. AUTO applies classifier + debounce + resolver. Save SSID action. Airplane → None → OFF.

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| 2026-08-23 | created | — | Pending | stub seeded by bootstrap | |
| 2026-08-23 | plan | Pending | Planned | /task-1-plan T08 | |
| 2026-08-23 | execute | Planned | InProgress | /task-2-execute T08 | |
| 2026-08-23 | complete | InProgress | Done | /task-3-complete T08 | |

## Requirements

- [x] Register callback when AUTO or Links visible (v1: process + mode≠OFF ok)
- [x] Subscription upsert; hidden SIM rows kept
- [x] Equal plan skip (orchestrator `lastApplied`; debouncer calls `refresh()` only)

## Implementation Plan

*(Filled by `/task-1-plan` — do not invent during bootstrap beyond high-level notes.)*

### High-level notes (bootstrap)

- android-connectivity.mdc
- Spec: `docs/PROXY-VPN-PLAN.md`

## Execution plan (filled by /task-1-plan)

**Date:** 2026-08-23
**Codebase snapshot:** post-T07 (`01d86b7` + uncommitted T07 fix-ups on `T08-auto-link-observer`). Orchestrator + resolver + classifier + debouncer exist; nothing registers a `NetworkCallback`; BootReceiver blind-starts VPN.
**Execute model:** medium (default)

### Context for executor

Goal: when `globalMode != OFF`, observe physical-link changes (default-network `NetworkCallback`), trailing-debounce them (prefs `linkDebounceMs`, 5s default), upsert a MOBILE `LinkProfile` for a never-seen data SIM, then call `orchestrator.refresh()`. Airplane/offline → classifier yields `PhysicalLink.None` → plan OFF (already resolver behavior). Also: BootReceiver routes through orchestrator instead of blind VPN start; repository gains `upsertWifi` (Save-SSID backend; button is T09).

Key files:
- `app/src/main/java/com/brukb/zerotier/system/LinkNetworkCallback.kt` — stub exists (`onAvailable/onLost/onCapabilitiesChanged → onEvent()`); reuse as-is.
- `app/src/main/java/com/brukb/zerotier/connection/LinkDebouncer.kt` — trailing debounce, `trigger()` / `cancel()`, caller-supplied `delayMs`. Unwired today.
- `app/src/main/java/com/brukb/zerotier/connection/ConnectionOrchestrator.kt` — `refresh()` currently passes `dataSubscriptionId = null` to `LinkClassifier.classify`.
- `app/src/main/java/com/brukb/zerotier/data/LinkProfileRepository.kt` + `data/model/LinkProfile.kt` — `upsertMobile(...)` + `LinkProfile.mergeMobile(...)` exist; no WIFI upsert.
- `app/src/main/java/com/brukb/zerotier/system/BootReceiver.kt` — currently `ZerotierBVpnService.start(context)` when `startOnBoot`.
- `app/src/main/java/com/brukb/zerotier/ZerotierBApplication.kt` — construct + start the observer here.
- `app/src/main/AndroidManifest.xml` — add SSID permissions.

Invariants (rules): never classify our VPN as the link (classifier already strips); unknown SSID → PROXY, **no** Room row; dual-SIM match **data** sub; last event wins after quiet period; orchestrator `lastApplied` skip must not be bypassed; never prompt `VpnService.prepare()` from callback (orchestrator only reads it).

### Steps

1. **Manifest permissions** — add to `AndroidManifest.xml`:
   ```xml
   <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="32" />
   <uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES"
       android:usesPermissionFlags="neverForLocation" />
   ```
   Runtime request is T09 UI; declaration only. Without grant, SSID reads return `<unknown ssid>` → `WifiUnknown` → PROXY (already handled).
   → verify: `./gradlew :app:assembleDebug`

2. **WIFI upsert backend** — mirror the mobile pattern:
   - `LinkProfile.kt`: add `fun wifiId(ssid: String) = "wifi-$ssid"` and
     `fun mergeWifi(existing: LinkProfile?, ssid: String, mode: LinkMode): LinkProfile` — new → `LinkProfile(id=wifiId(ssid), kind=WIFI, mode=mode, ssid=ssid)`; existing → keep existing `mode`, return unchanged meta (idempotent).
   - `LinkProfileRepository.kt`: `suspend fun upsertWifi(ssid: String, mode: LinkMode = LinkMode.PROXY)` → `dao.upsert(LinkProfile.mergeWifi(dao.getBySsid(ssid), ssid, mode))`.
   → verify: new `LinkProfileMergeTest` cases pass (see Tests).

3. **Orchestrator: live data sub + link exposure** — in `ConnectionOrchestrator`:
   - `classifyLink()`: replace `dataSubscriptionId = null` with `activeDataSubscriptionId()`:
     ```kotlin
     private fun activeDataSubscriptionId(): Int? {
         val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
         val id = SubscriptionManager.getActiveDataSubscriptionId()
         return if (id == SubscriptionManager.INVALID_SUBSCRIPTION_ID) null else id
     }
     ```
     (`getActiveDataSubscriptionId()` needs no permission.)
   - `OrchestratorState`: add `val lastLink: PhysicalLink? = null`; set it in `refresh()` after classify (before `applyPlan`). T09 UI + Save-SSID need it.
   → verify: `./gradlew :app:compileDebugKotlin`; existing `ConnectionOrchestratorTest` still green.

4. **`LinkObserver`** — new file `app/src/main/java/com/brukb/zerotier/system/LinkObserver.kt`:
   ```kotlin
   class LinkObserver(
       private val context: Context,
       private val preferences: AppPreferences,
       private val linkProfileRepository: LinkProfileRepository,
       private val orchestrator: ConnectionOrchestrator,
       private val scope: CoroutineScope,
   ) {
       private val connectivityManager =
           context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
       private val debouncer = LinkDebouncer(scope, { preferences.linkDebounceMs.first().toLong() }) { onQuietPeriod() }
       private val callback = LinkNetworkCallback { debouncer.trigger() }
       private var registered = false
       private var observeJob: Job? = null

       fun start() {
           observeJob = scope.launch {
               preferences.globalMode.collect { mode ->
                   if (mode != GlobalMode.OFF) ensureRegistered() else ensureUnregistered()
               }
           }
       }

       private fun ensureRegistered() {
           if (registered) return
           connectivityManager.registerDefaultNetworkCallback(callback)
           registered = true
           debouncer.trigger() // initial classify; onAvailable also fires
       }

       private fun ensureUnregistered() {
           if (!registered) return
           connectivityManager.unregisterNetworkCallback(callback)
           registered = false
           scope.launch { debouncer.cancel() }
       }

       private suspend fun onQuietPeriod() {
           upsertDataSimIfNew()
           orchestrator.refresh()
       }

       private suspend fun upsertDataSimIfNew() {
           val id = SubscriptionManager.getActiveDataSubscriptionId()
           if (id == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return
           if (linkProfileRepository.getBySubscriptionId(id) == null) {
               linkProfileRepository.upsertMobile(id, null, "SIM $id", null)
           }
       }
   }
   ```
   Notes: no `OnSubscriptionsChangedListener` (needs `READ_PHONE_STATE`; callback events are frequent enough — debouncer absorbs churn). SIM removal: rows are never deleted (merge-only) → "hidden SIM rows kept" satisfied. Wrap `registerDefaultNetworkCallback` in `runCatching` + `Log.w` (OEM quirks).
   → verify: `./gradlew :app:compileDebugKotlin :app:lintDebug`

5. **Application wiring** — `ZerotierBApplication`: `lateinit var linkObserver: LinkObserver`; construct after `orchestrator`; call `linkObserver.start()` at end of the existing `appScope.launch { … }` block (after `seedOther()`).
   → verify: `make verify`

6. **BootReceiver → orchestrator** — replace blind VPN start:
   ```kotlin
   val pending = goAsync()
   (context.applicationContext as ZerotierBApplication).let { app ->
       app.appScope.launch {
           try {
               if (app.preferences.startOnBoot.first()) app.orchestrator.refresh()
           } finally {
               pending.finish()
           }
       }
   }
   ```
   `appScope` is currently `private` — make it internal-visible (e.g. `val appScope` non-private or a `fun refreshFromBoot()` on the Application; prefer the latter, keeps scope private). Remove now-unused `ZerotierBVpnService` import if unused. Orchestrator refresh resolves OFF/PROXY/VPN from stored mode — no consent prompt (read-only `prepare`).
   → verify: `make verify`

7. **Device-test checklist** — add §15.2 items 9–15 as a checklist under `## Verification` (or Manual test draft) in this file: (9) WiFi A PROXY → WiFi B VPN swaps after 5s, main-only TUN; (10) 2s flap → no swap until 5s quiet; (11) unknown SSID → PROXY, no new row; (12) Save SSID → row appears (via `upsertWifi` — UI in T09; verify via `adb shell dumpsys` or Room inspection); (13) data-SIM switch → other MOBILE profile applies; (14) USB/BT uplink → Other profile; (15) VPN runtime up → classifier does not snap to Other.
   → verify: doc present in task file.

### Tests to add

- `app/src/test/java/com/brukb/zerotier/data/LinkProfileMergeTest.kt` (extend):
  - `mergeWifi` new → kind WIFI, mode PROXY default, id `wifi-<ssid>`, ssid set
  - `mergeWifi` existing with mode VPN → mode kept (idempotent re-save)
- No observer/classifier unit tests — they need `ConnectivityManager` (no Robolectric/mockk in deps; `isReturnDefaultValues` only). Debounce already covered by `LinkDebouncerTest`; equal-plan skip by `ConnectionOrchestratorTest`.

### Verify commands

- `make verify` (lint + unit tests + assembleDebug)
- Targeted: `./gradlew :app:testDebugUnitTest --tests com.brukb.zerotier.data.LinkProfileMergeTest`

### Risks / pitfalls

- **Callback storm**: `onCapabilitiesChanged` fires often (signal changes). Debouncer is the only guard — do not call `refresh()` directly from the callback.
- **Boot + debounce vs broadcast window**: `goAsync` allows ~10s; default debounce 5s fits. Max clamp 15s can exceed it — acceptable: FGS start keeps process alive; do not shorten the debounce for boot.
- **FGS-from-boot restrictions** (API 34/35): pre-existing behavior (BootReceiver already started VPN FGS); refresh() may start PROXY/VPN FGS the same way. No regression expected; note if device test fails.
- **Do not** register when mode == OFF (battery); do not unregister-and-leak (guard with `registered` flag).
- **Do not** add `READ_PHONE_STATE` / `OnSubscriptionsChangedListener` — v1 stays permission-free for SIM.
- `SubscriptionManager.getActiveDataSubscriptionId()` is static (API 24+) — no instance needed; ignore the unused `sm` if lint complains (drop the local).

### Out of scope

- Links screen / SSID banner / runtime location-permission request (T09)
- Save-SSID button (T09; backend `upsertWifi` lands here)
- "SIM absent" UI marking (rows are kept; display is T09)
- `OnSubscriptionsChangedListener`, `READ_PHONE_STATE`, iccId/slot metadata
- Always-on VPN interplay

### Execute model recommendation
- medium (default) — all shapes specified; no architecture left to discover.

## Test Plan

- Prove this layer without later layers.
- Commands: `make verify` (lint + unit tests + assembleDebug)
- New Kotlin: tests required

## Acceptance Criteria

- [x] Device test list PROXY-VPN-PLAN §15.2 items 9–15 at least documented
- [x] Unknown SSID PROXY no new row (classifier: `WifiUnknown`, no auto-insert)
- [x] Tests added/updated for new behavior
- [x] Full lint + test verify suite green
- [x] Verification commands recorded and passing
- [x] No secrets committed

## Verification

- `make verify` — **PASS** (2026-08-23, close-out re-verify): lintDebug, testDebugUnitTest, assembleDebug
- `./gradlew :app:testDebugUnitTest --tests com.brukb.zerotier.data.LinkProfileMergeTest` — **PASS** (mergeWifi cases)
- `LinkObserver` registers default-network callback when `globalMode != OFF`; unregisters on OFF
- Debounce → `orchestrator.refresh()`; SIM upsert on first sight of data sub
- `BootReceiver` → `orchestrator.refresh()` (no blind VPN start)
- `DataSubscriptionIds`: API 30+ `getActiveDataSubscriptionId`, else `getDefaultDataSubscriptionId`

### Device test checklist (PROXY-VPN-PLAN §15.2 items 9–15)

| # | Scenario | Expected |
| --- | -------- | -------- |
| 9 | AUTO WiFi A PROXY → WiFi B VPN | After 5s debounce: stack swap, main-only TUN |
| 10 | Flap WiFi 2s | No swap until 5s quiet |
| 11 | AUTO unknown SSID | PROXY runtime, no new LinkProfile row |
| 12 | Save SSID | Row appears (`upsertWifi`; UI button T09) |
| 13 | Dual SIM, switch data SIM | Other MOBILE profile applies |
| 14 | USB/BT uplink | Other profile |
| 15 | VPN runtime up | Classifier does not snap to Other |

## Files Modified

- `app/src/main/AndroidManifest.xml` — location + nearby WiFi permissions
- `app/src/main/java/com/brukb/zerotier/data/model/LinkProfile.kt` — `wifiId`, `mergeWifi`
- `app/src/main/java/com/brukb/zerotier/data/LinkProfileRepository.kt` — `upsertWifi`
- `app/src/main/java/com/brukb/zerotier/connection/ConnectionOrchestrator.kt` — `lastLink`, data sub id
- `app/src/main/java/com/brukb/zerotier/connection/DataSubscriptionIds.kt` — new
- `app/src/main/java/com/brukb/zerotier/connection/LinkDebouncer.kt` — suspend `delayMs`
- `app/src/main/java/com/brukb/zerotier/system/LinkObserver.kt` — new
- `app/src/main/java/com/brukb/zerotier/system/BootReceiver.kt` — orchestrator refresh
- `app/src/main/java/com/brukb/zerotier/ZerotierBApplication.kt` — wire LinkObserver
- `app/src/test/java/com/brukb/zerotier/data/LinkProfileMergeTest.kt` — mergeWifi tests

## Manual test (for humans)

**Prereq:** Two Wi-Fi networks with different `LinkProfile` modes (set via T09 UI later; for now use Room or pre-seed SSIDs). At least one enabled ZT network.

```bash
./gradlew :app:installDebug
DEBUG='am start -a com.brukb.zerotier.DEBUG -n com.brukb.zerotier/.ui.MainActivity -f 0x20000000'

# Enable AUTO observer path (mode != OFF registers LinkObserver callback)
adb shell $DEBUG --es zerotierb_action apply_mode --es mode AUTO

# Watch debounced refresh (default 5s after link change)
adb logcat -c
# Switch Wi-Fi A → Wi-Fi B on device, wait 6s
adb logcat -d -s LinkObserver ConnectionOrchestrator | tail -20
# Expect: apply PROXY/VPN/OFF per link profile after quiet period

# Airplane mode → OFF runtime after debounce
adb shell cmd connectivity airplane-mode enable
sleep 6
adb logcat -d -s ConnectionOrchestrator | tail -5
# Expect: apply OFF: AUTO no link (or similar)

adb shell cmd connectivity airplane-mode disable

# Unknown SSID: no auto row (classifier only)
# Cafe Wi-Fi without location grant → log shows AUTO unknown wifi / WifiUnknown

# Stop observer registration
adb shell $DEBUG --es zerotierb_action apply_mode --es mode OFF
```

**Success:** link change → 5s quiet → `ConnectionOrchestrator` apply log; mode OFF unregisters callback; unknown SSID stays PROXY without new Room row.

## Learnings

- `LinkObserver` registers `registerDefaultNetworkCallback` when `globalMode != OFF`; unregisters on OFF.
- Debounce reads `linkDebounceMs` at each trigger via suspend `delayMs` lambda.
- `DataSubscriptionIds`: API 30+ active data sub, else `getDefaultDataSubscriptionId` (lint-safe on minSdk 26).
- First sight of data SIM → `upsertMobile` PROXY default; rows never deleted on SIM removal.
- `BootReceiver` uses `goAsync` + `orchestrator.refresh()` — no blind VPN start.
- `orchestrator.state.lastLink` exposed for T09 Save-SSID / Links UI.
- `LinkProfileRepository.upsertWifi` + `mergeWifi` ready for T09 Save-SSID button.

## Reality notes

*(Amended by upstream `/task-3-complete` if prior tasks changed assumptions)*

### From T07 close-out

- `ZerotierBApplication.orchestrator` exists — T08 debounce callback should call `orchestrator.refresh()` (not start services directly).
- `ConnectionOrchestrator.applyGlobalMode(GlobalMode)` sets prefs then `refresh()`; AUTO observer only needs link changes → `refresh()`.
- `lastApplied == plan` skip already in orchestrator — T08 debounce should not bypass this.
- VPN consent: orchestrator read-only `VpnService.prepare()`; missing consent → PROXY fallback + `vpnConsentMissing` in plan (T09 UI).
- Debug `apply_mode` intents on `MainActivity` remain for manual testing until T09.
