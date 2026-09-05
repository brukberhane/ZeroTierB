# T21 — Roots start hardening (orbit retry, VPN off main, one worlds store)

**Status**: Pending  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T20  
**Next**: T22  
**Layer**: L6

## Description

Close three T19 follow-ups that were too big for the ABC hotfix: PROXY must not join if orbit fails; VPN must not `runBlocking` DataStore/file IO on `onStartCommand`; PROXY and VPN must share the Application `RootsFileStore`, not construct a second `zt-worlds` path.

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| 2026-09-05 | created | — | Pending | T19 ABC follow-up: D/E/F | |

## Requirements

- [ ] PROXY: `applyOrbits` failure is a start failure — `stop()`, backoff, retry ensure loop. Do **not** proceed to `joinConfiguredNetwork`.
- [ ] VPN: no `runBlocking` on `onStartCommand`. `startForeground` stays synchronous (FGS 5s). Stage / `Node.init` / orbit run on the service IO scope under the existing start-token / `node != null` guards.
- [ ] One `RootsFileStore`: `RootsRepository` exposes the store already owned by Application. PROXY and VPN `onCreate` use it. Do not `RootsFileStore(File(filesDir, "zt-worlds"))` in the services.
- [ ] `make verify`.

## Non-goals (this task)

- Roots Compose UI (T20)
- libzt Dummy C buffer / selftest (T22)
- Topology skip-Earth
- Changing NODE_UP vs NODE_ONLINE

## Constraints

- `.cursor/rules/connection-orchestrator.mdc` — exclusive swap; abort on stop timeout; `isRunning \|\| startRequested`
- `.cursor/rules/android-vpn.mdc` — `startForeground` sync in `onStartCommand`; no `prepare()` from background
- `.cursor/rules/kotlin.mdc` — do not spread `runBlocking` on main; JVM tests no Robolectric
- `.cursor/rules/libzt.mdc` — NODE_UP join gate unchanged
- One identity; never two live nodes

## References

- `app/src/main/java/com/brukb/zerotier/proxy/ProxyModeService.kt` — `runNodeEnsureLoop` `applyOrbits` `runCatching.onFailure` log-and-join
- `app/src/main/java/com/brukb/zerotier/vpn/ZerotierBVpnService.kt` — `runBlocking { stageBeforeNode }` / `applyOrbits` inside `onStartCommand`
- `app/src/main/java/com/brukb/zerotier/data/RootsRepository.kt`
- `app/src/main/java/com/brukb/zerotier/ZerotierBApplication.kt` — `RootsFileStore(File(filesDir, "zt-worlds"))`

## Reality notes (from T19 ABC)

- ABC already shipped: `copiedMoonIds` / `orbitSeedForMoon`; `RootsStatusCopy` Earth vs Dummy; `observeCustomPlanetEpoch` on Application combine.
- PROXY `initSetRoots` failure already backoffs. Orbit failure is the remaining swallow.
- VPN `startForegroundCompat` is already before the `synchronized` start body. UDP bind/protect stay on the start path (need a live `DatagramSocket` + `protect()`). Only stage/init/orbit leave main.
- Application already constructs `RootsFileStore(File(filesDir, "zt-worlds"))` for `RootsRepository`. Services must not invent a second instance with a mistyped path.

## Implementation Plan

See Execution plan.

## Execution plan (filled by /task-1-plan)

**Date:** 2026-09-05  
**Codebase snapshot:** branch `T20-roots-settings-screen` after T19 ABC hotfix.  
**Execute model:** medium

### Context for executor

**Goal:** Orbit fail does not join; VPN start does not block main with `runBlocking`; both stacks use the Application worlds store.

**Do not:** restage from ViewModel. Fake ONLINE. Bind proxy off loopback. Rebuild AAR. Touch libzt C (T22). Expand Roots UI.

### Steps

1. **D — PROXY orbit fail = start fail** in `ProxyModeService.runNodeEnsureLoop`.
   After successful `start()`, replace `runCatching { applyOrbits }.onFailure { log }` with the same pattern as `initSetRoots` failure: log, `nodeManager.stop()`, `nodeStarted = false`, backoff, `continue`. Do not enter the join loop on that iteration.
   → verify: grep `applyOrbits` has no `onFailure` that falls through to `joinConfiguredNetwork`. JVM: extract is optional; prefer a small `enum class NodeEnsureStep { Stage, SetRoots, Start, Orbit }` only if it stays tiny. No Robolectric service test.

2. **E — VPN off `runBlocking`**. Keep in `onStartCommand` (main, sync): FGS, start-token superseded check, UDP bind + `protect()`, assign `datagramSocket`. Then `scope.launch` (existing `Dispatchers.IO` scope) the rest:
   - `stageBeforeNode { ZeroTierNative.zts_util_make_dummy_planet() ?: error(...) }`
   - `Node.init` / assign `node` / `applyOrbits` / scheduler + threads / `refreshJoinedNetworks`.
   Guard with existing `synchronized` / start token: if STOP lands, abort before `init`. If `node != null` on a later START, keep today’s refresh path.
   FGS 5s: `startForeground` already ran. Early fail paths still `stopForeground` + `stopSelf`.
   Delete both `runBlocking` imports/usages in this file.
   → verify: `rg runBlocking ZerotierBVpnService.kt` empty. `startForegroundCompat` still before any `scope.launch`. Do not init `Node` on main.

3. **F — one worlds store**. `RootsRepository`: `val worlds: RootsFileStore get() = files` (rename ctor param stays `files` or rename to `worlds` — one name). `ProxyModeService.onCreate` and `ZerotierBVpnService.onCreate`: `worlds = app.rootsRepository.worlds` (or pass into `RootsApplier`). Remove `RootsFileStore(File(filesDir, "zt-worlds"))` from both services. Application remains the only constructor.
   → verify: `rg 'RootsFileStore\\(File' app/src/main` is only `ZerotierBApplication`.

4. **`make verify`**.

### Tests to add

- Keep `RootsApplierTest` orbit-seed table (ABC). No new native tests.
- Optional: `RootsRepository` worlds identity — skip if getter is trivial.
- Do **not** Robolectric `VpnService`.

### Verify commands

- `make verify`

### Risks / pitfalls

- **VPN async start race.** `onStartCommand` returning `START_STICKY` before `node != null` means a second START can enter the `node == null` branch. Set a start-in-flight flag (existing start token) and treat in-flight as “already starting”. STOP must cancel the IO job / supersede token before `init`.
- **FGS 5s.** Never move `startForeground` into the IO coroutine.
- **UDP bind on main vs IO.** Keep bind+protect sync so `protect()` has a service that finished `onCreate`/`onStartCommand` setup. Do not bind 9994 twice.
- **Two worlds dirs.** A typo `zt-world` vs `zt-worlds` would silently empty moons. Sharing the repo store is the fix.

### Out of scope

- T20 UI
- T22 Dummy C capacity
- Putting roots into `RuntimePlan`

### Execute model recommendation

- medium — VPN start threading + start-token races. Not large: no new native API.

## Test Plan

- Unit: existing Roots tests still green; `runBlocking` gone from VPN service source
- Device (manual / T10): Dummy moon join after orbit; VPN start does not ANR on first launch
- Commands: `make verify`

## Acceptance Criteria

- [ ] Orbit failure does not join
- [ ] No `runBlocking` in `ZerotierBVpnService`
- [ ] Single `RootsFileStore` constructed in Application
- [ ] Tests added/updated for new behavior (or documented why untestable on JVM)
- [ ] Full lint + test verify suite green
- [ ] Verification commands recorded and passing
- [ ] No secrets committed

## Verification

*(Filled by `/task-2-execute`)*

## Files Modified

*(Filled by `/task-2-execute`)*

## Manual test (for humans)

*(Filled by `/task-3-complete`)*

## Learnings

*(Filled by `/task-3-complete`)*

## Reality notes

- ABC hotfix already on the T20 branch before this task is planned.
