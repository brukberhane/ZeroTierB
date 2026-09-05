# T19 — Apply roots on PROXY + VPN start

**Status**: Done  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T18  
**Next**: T20  
**Layer**: L6

## Description

Every PROXY and VPN start **stages** the active planet and **orbits** Room moons, then joins as today. Live identity home never keeps a custom/Dummy world when the operator wants Earth. Config edits while a stack is up restart that runtime (same exclusive swap rules). Dummy is local to this device; peers do not need it — moons + LAN still work.

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| 2026-09-05 | created | — | Pending | /setup-tasks Roots feature | |
| 2026-09-05 | planned | Pending | Planned | /task-1-plan — stage+orbit both stacks, fingerprint restart | |
| 2026-09-05 | execute | Planned | InProgress | /task-2-execute — wired both stacks + orchestrator restart | |
| 2026-09-05 | complete | InProgress | Done | /task-3-complete — verify + dialectic + INDEX ✅ | |

## Requirements

- [x] **Live planet source:** airgap on → Dummy; else `planetSource=custom` and blob present → Custom; else Earth.
- [x] **Earth:** do not `set_roots`. **Delete** live `planet` and `roots` only. Baked Earth can load. Never leave Dummy/Custom in identity dir.
- [x] **Custom / Dummy:** JNI write `filesDir/planet` **before** `Node.init`. PROXY `zts_init_set_roots` **before** `zts_node_start`. Same Dummy bytes both stacks.
- [x] After node UP / JNI init: orbit each Room moon (`orbit(id, 0)` if `hasMoonFile` after copying `.moon` into `moons.d`; else `orbit(id, seed)`). Deorbit worldIds not in Room.
- [x] **Allowlist:** writes/deletes only `planet`, `roots`, `moons.d/*`. Never `identity.*` or `networks.d`. Tests on the allowlist function.
- [x] Changing moons / airgap / planetSource while PROXY or VPN running → orchestrator restart current runtime. Stop timeout still **aborts** swap (existing invariant).
- [x] Ready gate stays **NODE_UP**. Do not wait NODE_ONLINE. Airgap + no upstream → lifecycle **Starting**; copy “waiting for roots/moons (LAN ok)”. **Do not fake Online.** Device-check: if Online never fires with working Dummy+moon, keep Starting.
- [x] Last-moon-removed + latch off + airgap on → force airgap **off** + restage Earth (snack in T20; state flip here or T20 — prefer prefs write here so apply is correct even without UI).
- [x] `make verify`.

## Non-goals (this task)

- Roots Compose screen (T20) — apply must work from prefs/Room already
- QR / clipboard
- Topology skip-Earth
- Kill-switch / `blockOutside`

## Constraints

- `.cursor/rules/connection-orchestrator.mdc` — exclusive swap; abort on stop timeout
- `.cursor/rules/libzt.mdc` — NODE_UP; 127.0.0.1 bind unchanged
- `.cursor/rules/zerotier-jni.mdc` — shared `filesDir`; `Node.orbit` / `deorbit`
- `.cursor/rules/android-vpn.mdc` — no `VpnService.prepare()` from background
- One identity; never two live nodes
- Dummy signing keys never in identity files

## References

- `app/src/main/java/com/brukb/zerotier/proxy/ProxyModeService.kt` — `ZeroTierNodeManager(filesDir)`
- `app/src/main/java/com/brukb/zerotier/ztlib/ZeroTierNodeManager.kt` — start / NODE_UP
- `app/src/main/java/com/brukb/zerotier/vpn/ZerotierBVpnService.kt` — `Node` + `ZeroTierDataStore`
- `app/src/main/java/com/brukb/zerotier/connection/ConnectionOrchestrator.kt`
- `app/src/main/java/com/brukb/zerotier/connection/RuntimeStatusMapper.kt` — Starting vs Online
- JNI `com.zerotier.sdk.Node.orbit` / `deorbit`

## Reality notes (from T18 + T19 plan)

- Dummy blob API: `RootsRepository.ensureDummyPlanet(generate: () -> ByteArray)` + `DummyPlanet.WORLD_ID_HEX`. Do **not** call stock `zts_util_sign_root_set` from Kotlin. Native `ZeroTierNative.zts_util_make_dummy_planet()` returns **blob only**. Call generate **only** from PROXY/VPN start when source is Dummy — never `Application.onCreate`.
- PROXY Custom/Dummy: `ZeroTierNode.initSetRoots(byte[])` / `ZeroTierNative.zts_init_set_roots` **before** `zts_node_start` (`ACQUIRE_SERVICE_OFFLINE`). JNI name `zts_1init_1set_1roots`.
- libzt planet cache filename is `<home>/roots`. JNI datastore name is `planet`. Earth staging deletes **both**. Dummy/Custom write identity-home `planet` (JNI) **and** PROXY `initSetRoots` (same bytes). Never write Dummy keys; never write `dummy.planet` into identity home.
- `zts_moon_orbit(id, 0)` / `Node.orbit(id, 0)` allowed when `.moon` is in `moons.d`. **No** `orbit`/`deorbit` wrappers on Java `ZeroTierNode` — PROXY calls `ZeroTierNative.zts_moon_orbit` / `zts_moon_deorbit`. VPN uses `com.zerotier.sdk.Node.orbit` / `deorbit`.
- After `zts_node_stop`, libzt **deletes** `NodeService` (`Controls.cpp` `_runNodeService` → `delete zts_service`). `_userDefinedWorld` does **not** survive a completed stop. Next start must `initFromStorage` again. Today `ZeroTierNodeManager.initialize()` is a one-shot `compareAndSet` — T19 **must** `reinitialize()` before `initSetRoots`/`start` when the node is not UP. Do **not** add a C `clear_roots` API.
- `RuntimePlan` has **no** roots fields. `applyLocked` skips when `plan == lastApplied && runtimeMatches`. `invalidateAppliedPlan()` alone is **not** enough: `applyProxy` will **poke START** if join set unchanged (node stays UP → no restage). Roots change while PROXY/VPN up must **stop then start** (copy join-set / vpnNetworkId restart). Stop timeout still **throws / abort swap**.
- `IdentityHomeAllowlist.isAllowedRelative` exists; **zero** production callers. All identity-home writes/deletes in this task go through a new `IdentityHomeStore`.
- `MoonDao.observeAll` / `RootsRepository.observeMoons` exist; nobody collects them. `setAirgap` has no callers yet.
- Ready gate already NODE_UP (`ZeroTierNodeManager.start` + `RuntimeStatusMapperTest` “UP without roots is Starting”). Do not wait NODE_ONLINE. New string `roots_waiting_lan`; chip stays `lifecycle_starting`.
- `RootsRepository` has no `getAll()` wrapper — use `MoonDao.getAll()` via a new `suspend fun getMoons()` on the repo. Custom blob: `RootsFileStore.customPlanetFile()`.
- App construct order (`ZerotierBApplication`): `rootsRepository` **before** `preferences`. Latch helper is a pure object; Application collector + `RootsApplier.stage` both call it (do not inject prefs into the repo ctor unless you reorder).

## Implementation Plan

See Execution plan.

## Execution plan (filled by /task-1-plan)

**Date:** 2026-09-05  
**Codebase snapshot:** branch `T19-roots-apply-both-stacks` @ T18 `0ea6451`. libzt `pylon` `2dad562`. Room v5 + `zt-worlds/` + DummyPlanet + `initSetRoots` exist. No orbit/set_roots callers in `app/`.  
**Execute model:** medium

### Context for executor

**Goal:** Every PROXY and VPN start stages Earth/Custom/Dummy into identity home (allowlisted paths only), then after the node is up orbits Room moons. Roots/moon/airgap edits while a stack is live **stop then start** that runtime. NODE_UP remains the ready gate; airgap without ONLINE stays Starting with LAN-ok copy. No Roots UI (T20). No Topology.cpp. No AAR rebuild unless you touch libzt (you should not).

**Do not:** edit `Topology.cpp` / libzt C++. Call `zts_util_sign_root_set`. Write `identity.*` / `networks.d`. Put Dummy generate in `Application.onCreate`. Fake `NodeLifecycleStatus.ONLINE`. Wait for `NODE_ONLINE` before join. Bind proxy off 127.0.0.1. Run libzt+JNI together. Expand Settings UI. Commit (wait `/task-3-complete`).

**Key files:**

| Path | Action |
| ---- | ------ |
| `app/.../data/LivePlanetResolver.kt` | **New** pure Earth/Custom/Dummy + latch |
| `app/.../data/IdentityHomeStore.kt` | **New** allowlisted write/delete/list under `filesDir` |
| `app/.../data/RootsApplier.kt` | **New** stage files + orbit/deorbit orchestration |
| `app/.../data/RootsFingerprint.kt` | **New** data class + restart predicate |
| `app/.../data/RootsRepository.kt` | `getMoons()`; optional no prefs |
| `app/.../ztlib/ZeroTierNodeManager.kt` | `initSetRoots` / `orbit` / `deorbit`; document reinitialize-before-start |
| `app/.../proxy/ProxyModeService.kt` | stage + reinitialize + set_roots before start; orbit after NODE_UP |
| `app/.../vpn/ZerotierBVpnService.kt` | stage before `Node.init`; orbit after init |
| `app/.../connection/ConnectionOrchestrator.kt` | fingerprint in skip + stop-then-start |
| `app/.../ZerotierBApplication.kt` | collect roots flows → `refresh()` |
| `app/src/main/res/values/strings.xml` | `roots_waiting_lan` |
| tests under `app/src/test/.../data/` and `connection/` | **New** |

**Invariants:** `.cursor/rules/libzt.mdc` NODE_UP not ONLINE; `.cursor/rules/zerotier-jni.mdc` allowlist; `.cursor/rules/connection-orchestrator.mdc` exclusive swap, abort on stop timeout; `.cursor/rules/kotlin.mdc` JVM tests no Robolectric / no `libzt.so`.

### Steps

1. **Pure resolver** `LivePlanetResolver` (`app/.../data/LivePlanetResolver.kt`):
   ```kotlin
   enum class LivePlanetSource { EARTH, CUSTOM, DUMMY }
   data class LivePlanetDecision(
     val source: LivePlanetSource,
     val airgapForcedOff: Boolean, // caller must setAirgap(false) when true
   )
   object LivePlanetResolver {
     fun resolve(
       airgap: Boolean,
       airgapWithoutMoons: Boolean,
       planetSource: PlanetSource,
       moonCount: Int,
       customPlanetPresent: Boolean,
     ): LivePlanetDecision
   }
   ```
   Rules (in this order):
   - If `airgap && moonCount == 0 && !airgapWithoutMoons` → `airgapForcedOff = true`, then treat airgap as **false** for source.
   - If airgap (after that) → `DUMMY` (moon count ≥ 1 **or** latch on). Custom planet does **not** win over Dummy.
   - Else if `planetSource == CUSTOM && customPlanetPresent` → `CUSTOM`.
   - Else → `EARTH` (`CUSTOM` with missing file falls back to Earth).
   → verify: table test every row in **Tests to add**. No Android types.

2. **`IdentityHomeStore(home: File)`** — only identity-home I/O:
   - `fun write(relative: String, bytes: ByteArray)` — `check(IdentityHomeAllowlist.isAllowedRelative(relative))`; mkdir parent; write. Throw `IllegalArgumentException` on deny.
   - `fun delete(relative: String)` — same check; no-op if missing.
   - `fun listMoonWorldIds(): Set<String>` — list `home/moons.d/*.moon` whose relative path **passes** the allowlist; return 16-hex ids (filename without `.moon`).
   Never accept `identity.secret`, `networks.d/…`, `dummy.planet`, `zt-worlds/…`.
   → verify: unit tests on a temp dir (see Tests).

3. **`RootsFingerprint`** (`app/.../data/RootsFingerprint.kt`):
   ```kotlin
   data class RootsFingerprint(
     val source: LivePlanetSource,
     val moonIds: List<String>, // sorted
     val customStamp: Long,     // customPlanetFile.lastModified() if CUSTOM else 0
   )
   object RootsRestart {
     fun requiresRestart(stackRunning: Boolean, last: RootsFingerprint?, next: RootsFingerprint): Boolean =
       stackRunning && last != next
   }
   ```
   Build from decision + `dao` moon ids + custom file mtime. Dummy stamp not required (ensure-once file).
   → verify: `requiresRestart` table like `proxyJoinSetRequiresRestart`.

4. **`RootsApplier`** (`app/.../data/RootsApplier.kt`):
   Ctor: `RootsApplier(prefs: AppPreferences, repo: RootsRepository, identity: IdentityHomeStore, worlds: RootsFileStore)`.
   ```kotlin
   data class RootsStageResult(
     val source: LivePlanetSource,
     val planetBytes: ByteArray?, // null = Earth (do not set_roots)
     val extraMoonIdsToDeorbit: Set<String>,
     val moons: List<Moon>,
     val fingerprint: RootsFingerprint,
   )
   suspend fun stageBeforeNode(generateDummy: () -> ByteArray): RootsStageResult
   suspend fun applyOrbits(
     result: RootsStageResult,
     orbit: suspend (worldId: Long, seed: Long) -> Unit,
     deorbit: suspend (worldId: Long) -> Unit,
   )
   ```
   **`stageBeforeNode`:**
   1. `moons = repo.getMoons()`; `customPresent = worlds.customPlanetFile().exists()`.
   2. `decision = LivePlanetResolver.resolve(airgap.first(), airgapWithoutMoons.first(), planetSource.first(), moons.size, customPresent)`.
   3. If `decision.airgapForcedOff` → `prefs.setAirgap(false)`.
   4. `extra = identity.listMoonWorldIds() - moons.map { it.worldId }.toSet()`.
   5. For each extra: `identity.delete("moons.d/$id.moon")`.
   6. Planet:
      - **EARTH:** `identity.delete("planet")`; `identity.delete("roots")`. `planetBytes = null`.
      - **CUSTOM:** `bytes = worlds.customPlanetFile().readBytes()`; `identity.write("planet", bytes)`; `planetBytes = bytes`.
      - **DUMMY:** `bytes = repo.ensureDummyPlanet(generateDummy)`; `check(DummyPlanet.isValid(bytes))`; `identity.write("planet", bytes)`; `planetBytes = bytes`. Do **not** write `dummy.planet` into identity home.
   7. For each Room moon with `hasMoonFile`: copy `worlds.moonFile(worldId).readBytes()` → `identity.write("moons.d/$worldId.moon", bytes)` if the zt-worlds file exists (skip + log if missing).
   8. Return result (fingerprint from source + sorted moon ids + customStamp).
   **`applyOrbits`:** `java.lang.Long.parseUnsignedLong(hex, 16)` for worldId (16 hex) and seed (10 hex). For each `extraMoonIdsToDeorbit`: `deorbit(id)`. For each Room moon: if `hasMoonFile` → `orbit(id, 0L)`; else if `seed != null` → `orbit(id, seedLong)`; else skip (log).
   Add `suspend fun getMoons() = dao.getAll()` on `RootsRepository`.
   Add `Moon` helpers if useful: `fun worldIdLong(): Long` via unsigned parse.
   → verify: `RootsApplier` tests with temp dirs + fake generate; Earth deletes leftover Dummy `planet`; extra moon file deleted; `identity.secret` write throws.

5. **`ZeroTierNodeManager`** — add (all `withNode`):
   ```kotlin
   suspend fun initSetRoots(bytes: ByteArray): Result<Unit>  // node.initSetRoots(bytes); require ZTS_ERR_OK
   suspend fun orbit(worldId: Long, seed: Long): Result<Unit>  // ZeroTierNative.zts_moon_orbit
   suspend fun deorbit(worldId: Long): Result<Unit>            // ZeroTierNative.zts_moon_deorbit
   ```
   Do **not** call `initSetRoots` from `initialize()`.
   → verify: compiles against AAR (`initSetRoots` exists on `ZeroTierNode` after T18). No `libzt.so` in unit tests.

6. **PROXY `ProxyModeService.runNodeEnsureLoop`** when `!isReadyToJoin` (today ~350–365), **replace** bare `initialize(); start()` with:
   ```
   val app = application as ZerotierBApplication
   val applier = RootsApplier(...)  // or keep one field created in onCreate from filesDir
   val staged = applier.stageBeforeNode { ZeroTierNative.zts_util_make_dummy_planet() }
   nodeManager.reinitialize()          // new NodeService after stop; sets storage path
   staged.planetBytes?.let { nodeManager.initSetRoots(it).getOrThrow() }  // skip on Earth
   nodeManager.start(shouldAbort)
   // on success, BEFORE join loop:
   applier.applyOrbits(staged, orbit = { id, seed -> nodeManager.orbit(id, seed).getOrThrow() },
                       deorbit = { id -> nodeManager.deorbit(id).getOrThrow() })
   ```
   Hold `lastStageSource` on the service. In `publishFromNodeState`, when `wentOffline` **or** (`lifecycle == STARTING && receivedNodeUp && !isOnline && lastStageSource == DUMMY`): `statusMessage = getString(R.string.roots_waiting_lan)`. Do not set lifecycle to ONLINE.
   Construct `IdentityHomeStore(filesDir)` — same path as `ZeroTierNodeManager(filesDir.absolutePath)`. Worlds store is `app.rootsRepository`’s files (`zt-worlds`).
   If `zts_util_make_dummy_planet()` returns null, fail start (log); do not proceed with invalid Dummy.
   → verify: grep `initSetRoots` appears **before** `nodeManager.start`; `applyOrbits` **after** successful start, **before** `joinConfiguredNetwork`. `reinitialize()` on the not-UP path.

7. **VPN `ZerotierBVpnService.onStartCommand`** — **before** `ztNode.init(...)` (~159):
   ```
   val staged = applier.stageBeforeNode { ZeroTierNative.zts_util_make_dummy_planet() }
   ```
   After `initResult == RESULT_OK` and `node = ztNode`, **before** `refreshJoinedNetworks()`:
   ```
   applier.applyOrbits(staged,
     orbit = { id, seed -> ztNode.orbit(id, seed) },
     deorbit = { id -> ztNode.deorbit(id) })
   ```
   If `staged.source == DUMMY`, `statusMessage = getString(R.string.roots_waiting_lan)` instead of hardcoded `"Waiting for roots"`. Keep `nodeLifecycle = STARTING`. Same for the OFFLINE branch `"Node offline — waiting for roots"` (~254) when Dummy.
   `IdentityHomeStore(filesDir)` — same home `ZeroTierDataStore` uses.
   → verify: stage is **before** `init`; orbit **after** init, **before** join. Earth path does not call `initSetRoots` (VPN has no set_roots).

8. **String:** `app/src/main/res/values/strings.xml`:
   `<string name="roots_waiting_lan">Waiting for roots/moons (LAN ok)</string>`
   Do **not** change `lifecycle_starting`. Hero chip stays “Starting”.
   → verify: resource exists; `StatusFormat.nodeLifecycleLabelRes` unchanged.

9. **Orchestrator restart** — `ConnectionOrchestrator`:
   - Add `private var lastRootsFp: RootsFingerprint? = null`.
   - Add helper `suspend fun currentRootsFingerprint(): RootsFingerprint` (read prefs + `rootsRepository.getMoons()` + custom file mtime + resolver). Pass `rootsRepository: RootsRepository` into the ctor (`ZerotierBApplication` already has it).
   - **`applyLocked` skip** becomes:
     `if (plan == lastApplied && runtimeMatches(plan) && fp == lastRootsFp) return`
     Compute `fp` **before** the skip.
   - **`applyProxy`:** if `RootsRestart.requiresRestart(ProxyModeService.state.value.isRunning || startRequested, lastRootsFp, fp)` → `stopProxyStack()` + `awaitUdpPortReleased(LIBZT_UDP_PORT)` (same as join-set change). Then start as today. Use `isRunning || startRequested` for “stack active” (split-brain rule).
   - **`applyVpn`:** same with VPN `isRunning || startRequested` → `stopVpnStack()` then start (even if `vpnNetworkId` unchanged).
   - On successful apply: `lastRootsFp = fp`. On `applyOff`: `lastRootsFp = null`.
   - Stop timeout still throws → `lastApplied` stays stale (existing catch). Do not swallow.
   → verify: `ConnectionOrchestratorTest` — `RootsRestart.requiresRestart` table; RuntimePlan equality **unchanged** (do not add roots fields to `RuntimePlan`).

10. **Collect roots (no UI yet)** — `ZerotierBApplication.onCreate` `appScope.launch`:
    ```kotlin
    combine(
      preferences.airgap,
      preferences.airgapWithoutMoons,
      preferences.planetSource,
      rootsRepository.observeMoons(),
    ) { airgap, latch, src, moons -> /* fingerprint inputs */ }
    .distinctUntilChanged()
    .collect { orchestrator.refresh() }
    ```
    Also: if `LivePlanetResolver` says `airgapForcedOff`, `setAirgap(false)` here **or** rely on `stageBeforeNode` (do both; `setAirgap` is idempotent). First collect may race boot `refresh()` — OK.
    → verify: no collect on main; uses existing `appScope`.

11. **`make verify`**. Fix only failures you caused. Do not rebuild AAR.

### Tests to add

**`LivePlanetResolverTest`** (table):
| airgap | latch | moons | planetSource | customFile | source | forcedOff |
| ------ | ----- | ----- | ------------ | ---------- | ------ | --------- |
| false | * | * | EARTH | * | EARTH | false |
| false | * | * | CUSTOM | yes | CUSTOM | false |
| false | * | * | CUSTOM | no | EARTH | false |
| true | false | 0 | * | * | EARTH | **true** |
| true | true | 0 | * | * | DUMMY | false |
| true | false | 1 | EARTH | * | DUMMY | false |
| true | true | 1 | CUSTOM | yes | DUMMY | false |

**`IdentityHomeStoreTest`:** write/read `planet`; write `moons.d/000000deadbeef00.moon`; delete `roots`; `write("identity.secret")` throws; `write("dummy.planet")` throws; `write("networks.d/x.conf")` throws.

**`RootsApplierTest`:** temp `filesDir` + temp `zt-worlds`; fake `generate`; Earth deletes leftover `planet`+`roots`; Dummy writes `planet` only (not `dummy.planet` in home); copies moon file; removes extra `moons.d` file not in Room (fake dao with 0 moons); `ensureDummyPlanet` not called on Earth.

**`RootsRestartTest` / extend `ConnectionOrchestratorTest`:** `requiresRestart(false, a, b) == false`; running + equal fp → false; running + moon list change → true; running + EARTH→DUMMY → true.

**`RuntimeStatusMapperTest`:** keep existing airgap Starting assertion; do **not** map Dummy to ONLINE.

Do **not** load `libzt.so`. Native Dummy / real orbit = device / T20.

### Verify commands

- `make verify`

### Risks / pitfalls

- **Poke START skips restage.** Join-set unchanged + `lastApplied` equal → node stays UP. Roots **must** force stop. Use `isRunning || startRequested`.
- **`initialize()` one-shot.** After stop, `NodeService` is gone. Always `reinitialize()` on the not-UP PROXY path before `initSetRoots`/`start`, or identity/home/`set_roots` hit a fresh service with empty home path.
- **`set_roots` while running → `ZTS_ERR_SERVICE`.** Never call it after `start`. Earth: omit the call (new NodeService has `_userDefinedWorld=false`).
- **Identity wipe.** Only `IdentityHomeStore` writes under `filesDir` for this task. VPN already writes `networks.d/*.local.conf` — do not route those through the allowlist.
- **Dummy native null.** `zts_util_make_dummy_planet` returns `byte[]` or null; treat null as start failure.
- **Seed vs file.** `orbit(id, 0)` only when `hasMoonFile` **and** file copied. Seed-only uses 10-hex seed as unsigned long. Missing both → skip.
- **JNI vs libzt names.** VPN file `planet`; PROXY cache `roots`. Delete both on Earth so a later PROXY start cannot reload Dummy from `roots`.
- **Do not rebuild AAR** unless compile fails on missing `initSetRoots` (stale AAR) — then `PATH=$ANDROID_HOME/cmake/3.22.1/bin:$PATH ./scripts/build-libzt.sh` and `javap` check (see `libzt.mdc` `libzt patch not in APK`).

### Out of scope

- Roots Compose screen / SAF / snacks (T20)
- QR / clipboard
- Topology skip-Earth / extra libzt C APIs
- Putting roots into `RuntimePlan`
- Auto-scan `moons.d` on libzt start (Room remains SoT; we copy then orbit)

### Execute model recommendation

- medium — two stacks + sticky skip/poke START footgun; Kotlin-only if the plan is followed. Not large: no new native design.

## Verification

- `make verify` — lint + unit tests + assembleDebug — **PASS** (2026-09-05, re-run at close-out)

## Files Modified

- `app/src/main/java/com/brukb/zerotier/data/LivePlanetResolver.kt` (new)
- `app/src/main/java/com/brukb/zerotier/data/IdentityHomeStore.kt` (new)
- `app/src/main/java/com/brukb/zerotier/data/RootsFingerprint.kt` (new)
- `app/src/main/java/com/brukb/zerotier/data/RootsApplier.kt` (new)
- `app/src/main/java/com/brukb/zerotier/data/RootsRepository.kt`
- `app/src/main/java/com/brukb/zerotier/ztlib/ZeroTierNodeManager.kt`
- `app/src/main/java/com/brukb/zerotier/proxy/ProxyModeService.kt`
- `app/src/main/java/com/brukb/zerotier/vpn/ZerotierBVpnService.kt`
- `app/src/main/java/com/brukb/zerotier/connection/ConnectionOrchestrator.kt`
- `app/src/main/java/com/brukb/zerotier/ZerotierBApplication.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/brukb/zerotier/data/LivePlanetResolverTest.kt` (new)
- `app/src/test/java/com/brukb/zerotier/data/IdentityHomeStoreTest.kt` (new)
- `app/src/test/java/com/brukb/zerotier/data/RootsApplierTest.kt` (new)
- `app/src/test/java/com/brukb/zerotier/data/RootsRestartTest.kt` (new)
- `app/src/test/java/com/brukb/zerotier/connection/ConnectionOrchestratorTest.kt`
- `.cursor/rules/connection-orchestrator.mdc` — `Poke START skips restage`
- `.cursor/rules/libzt.mdc` — `NodeService gone after stop`, `set_roots while node running`
- `.cursor/rules/zerotier-jni.mdc` — allowlist throws on exists
- `.cursor/rules/kotlin.mdc` — public ctor vs internal type
- `planning/phases/T20-roots-settings-screen.md` — Reality notes

## Manual test (for humans)

1. `./gradlew :app:installDebug` then start PROXY (Earth, no moons). Hero chip stays **Starting** until roots reachable, then **Online**. Identity home must not keep a Dummy `planet`.
2. Airgap **on** with ≥1 moon (or latch on, 0 moons): Dummy generate only on start. Status copy **Waiting for roots/moons (LAN ok)** while not Online. Chip stays Starting — never fake Online.
3. Custom planet file in `zt-worlds/` + `planetSource=custom`: PROXY `initSetRoots` before start; VPN writes `planet` before `Node.init`. Toggle Earth: both `planet` and `roots` deleted from identity home; next start uses baked Earth.
4. Change moons / airgap while PROXY or VPN is up: stack **stops then starts** (not a silent START poke). Stop timeout still aborts swap.
5. Last moon removed, latch off, airgap on → airgap forced off + Earth restage.

## Learnings

- `RuntimePlan` skip + poke START leaves the node UP — roots/moons need a fingerprint and **stop-then-start** (`connection-orchestrator.mdc` `Poke START skips restage`).
- After `zts_node_stop`, `NodeService` is gone; one-shot init skips `set_roots` (`libzt.mdc` `NodeService gone after stop`). `set_roots` is init-time only.
- Allowlist throws on denied `exists()`/`read()` — assert forbidden files via `java.io.File` (`zerotier-jni.mdc`).
- Public ctor cannot take an `internal` fake-prefs type (`kotlin.mdc`).
