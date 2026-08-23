# T02 — Preferences + Room v3 (modes, pin, links table)

**Status**: Done  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T01  
**Next**: T03  
**Layer**: L1

## Description

Persist globalMode, debounce, last proxy string; migrate Room to v3: createdAt, isPinnedMain on ZerotierBNetwork; new link_profiles. No stack swap yet.

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| 2026-08-23 | created | — | Pending | stub seeded by bootstrap | |
| 2026-08-23 | planned | Pending | Planned | /task-1-plan: persist + Room v3; no UI/orchestrator | |
| 2026-08-23 | execute | Planned | InProgress | /task-2-execute T02 | |
| 2026-08-23 | complete | InProgress | Done | /task-3-complete T02 --no-push | |

## Requirements

- [ ] DataStore keys per PROXY-VPN-PLAN §10.1
- [ ] MIGRATION_2_3, stop relying on destructive fallback for this bump
- [ ] Seed Other + upsert mobile rows when subscriptions observed (can be stub observer)

## Implementation Plan

*(Filled by `/task-1-plan` — do not invent during bootstrap beyond high-level notes.)*

### High-level notes (bootstrap)

- Spokes: room.mdc, kotlin.mdc
- Migrate startOnBoot==true → globalMode=VPN
- Spec: `docs/PROXY-VPN-PLAN.md`

## Execution plan (filled by /task-1-plan)

**Date:** 2026-08-23  
**Codebase snapshot:** branch `T02-prefs-room-v3` @ T01 `15a61cf`. Room **v2** (`zerotierb.db`), entity `ZerotierBNetwork` **without** pin/createdAt. DataStore keys: `start_on_boot`, `vpn_always_on` only. No `link_profiles`. `fallbackToDestructiveMigration()` still on. Package `com.brukb.zerotier`. Compose BOM stay `2024.12.01`.  
**Execute model:** small (default)

### Context for executor

**Goal:** Persist dual-mode *settings* and *schema* only. VPN still starts the same way. No ConnectionOrchestrator, no Links UI, no libzt.

**Do not rename** existing Room columns. Live names (keep): `isEnabled`, `allowManaged`, `allowDefault`, `allowGlobal`, `allowDns`, `routePriority`. Spec §10.2 “isEnabled / allowManaged / …” is stale vs code — **code wins**. **Add** `createdAt: Long = 0` and `isPinnedMain: Boolean = false`.

**Key files:**
- `docs/PROXY-VPN-PLAN.md` §4.1, §6.1, §10.1–10.3 (enums + keys + LinkProfile)
- `app/src/main/java/com/brukb/zerotier/data/AppPreferences.kt`
- `app/src/main/java/com/brukb/zerotier/data/AppDatabase.kt` (v2, `MIGRATION_1_2`, destructive fallback)
- `app/src/main/java/com/brukb/zerotier/data/model/ZerotierBNetwork.kt`
- `app/src/main/java/com/brukb/zerotier/data/NetworkDao.kt`
- `app/src/main/java/com/brukb/zerotier/data/NetworkRepository.kt`
- `app/src/main/java/com/brukb/zerotier/ZerotierBApplication.kt`
- `app/src/main/java/com/brukb/zerotier/ui/MainViewModel.kt` — `ZerotierBNetwork(...)` default ctor must still compile (new fields have defaults)
- Tests: `app/src/test/java/com/brukb/zerotier/data/` (new)

**Invariants:**
- `.cursor/rules/room.mdc` — real `MIGRATION_2_3`; `link_profiles` = physical links; one `isPinnedMain=1`; `createdAt=0` sorts last then `networkId`
- `.cursor/rules/kotlin.mdc` — JVM unit tests, no Robolectric unless forced; no Log-only test hacks
- No AGP/Kotlin/BOM bump. No `0.0.0.0` proxy. No libzt+JNI. No APN writes.
- Do not call `SubscriptionManager` from Application without permission — stub = **repository method**, not a live listener.

### Steps

1. **Enums (pure)** — add `app/src/main/java/com/brukb/zerotier/data/model/Modes.kt`:
   - `enum class GlobalMode { OFF, PROXY, VPN, AUTO }` with `fun parse(raw: String?): GlobalMode` (unknown → `OFF`)
   - `enum class LinkKind { WIFI, MOBILE, OTHER }`
   - `enum class LinkMode { OFF, PROXY, VPN }` (no AUTO on links)
   - `object GlobalModeMigrate { fun initial(startOnBoot: Boolean, stored: String?): GlobalMode }`  
     Rules: if `stored` non-null/blank → `parse(stored)`; else if `startOnBoot` → `VPN`; else `OFF`.  
     → verify: `GlobalModeMigrateTest` (table: stored/boot combinations) compiles in isolation.

2. **AppPreferences** — keep `start_on_boot` / `vpn_always_on`. Add keys exactly:
   - `global_mode` string
   - `saved_http_proxy` string (nullable: missing key = null)
   - `last_http_proxy_port` int default `0`
   - `link_debounce_ms` int default `5000`; clamp on **write** to `3_000..15_000`
   - Flows + setters. One-shot: `suspend fun migrateGlobalModeIfNeeded()` reads startOnBoot + stored global_mode, writes `global_mode` if absent using `GlobalModeMigrate.initial`.  
     Call from `ZerotierBApplication.onCreate` on `appScope` (IO).  
     → verify: existing `BootReceiver` still reads `startOnBoot` unchanged.

3. **ZerotierBNetwork** — add `createdAt: Long = 0L`, `isPinnedMain: Boolean = false` at end of data class so existing `ZerotierBNetwork(networkId, name)` call sites keep working.  
     Add `object MainNetworkSelector { fun select(enabled: List<ZerotierBNetwork>): ZerotierBNetwork? }`  
     Algorithm: filter not needed (caller passes enabled); `firstOrNull { isPinnedMain }` else min by `(createdAt == 0L, createdAt, networkId)` i.e. zeros last, then oldest real timestamp, then id.  
     → verify: `MainNetworkSelectorTest` table (pin wins; older createdAt; zeros after real; empty list → null).

4. **LinkProfile entity** — `app/src/main/java/com/brukb/zerotier/data/model/LinkProfile.kt`, table `link_profiles`:
   - `id: String` PK (`"other"` | `"mobile-$subId"` | wifi uuid later)
   - `kind: LinkKind`, `mode: LinkMode`
   - `ssid: String?`, `subscriptionId: Int?`, `simSlotIndex: Int?`, `label: String = ""`, `iccId: String?`
   - Store enums as **TEXT** via `@TypeConverters(LinkConverters::class)` on DB or entity (`enum.name`).  
     `fun seedOther(): LinkProfile` = id `other`, kind OTHER, mode PROXY, label `"Other"`.

5. **LinkProfileDao** — `observeAll()`, `getById`, `getBySsid(ssid)`, `getBySubscriptionId(subId)`, `upsert`, `ensureOther()` (`INSERT OR IGNORE` for id `other` with PROXY).  
     **LinkProfileRepository**: `seedOther()`, `upsertMobile(subscriptionId, simSlotIndex, label, iccId)` — if row missing, insert `id="mobile-$subscriptionId"`, kind MOBILE, mode PROXY, keep existing mode on upsert of metadata only.  
     → verify: unit-test `upsertMobile` merge rules with an in-memory fake list (no Room). Extract `fun mergeMobile(existing: LinkProfile?, subId, slot, label, iccId): LinkProfile` if that keeps tests JVM-pure.

6. **NetworkDao pin** — `@Query("UPDATE networks SET isPinnedMain = 0")` + `@Query("UPDATE networks SET isPinnedMain = 1 WHERE networkId = :id")` inside `@Transaction suspend fun setPinnedMain(networkId: String)`.  
     `NetworkRepository.setPinnedMain(id)` calls that.  
     → verify: `MainNetworkSelector` already covers pin semantics; optional fake-dao test for “only one pin” on a mutable list helper `fun applyPin(rows, id)`.

7. **AppDatabase v3**
   - `version = 3`, entities `[ZerotierBNetwork, LinkProfile]`
   - `abstract fun linkProfileDao(): LinkProfileDao`
   - `MIGRATION_2_3`:
     ```
     ALTER TABLE networks ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0;
     ALTER TABLE networks ADD COLUMN isPinnedMain INTEGER NOT NULL DEFAULT 0;
     CREATE TABLE IF NOT EXISTS link_profiles (
       id TEXT NOT NULL PRIMARY KEY,
       kind TEXT NOT NULL,
       mode TEXT NOT NULL,
       ssid TEXT,
       subscriptionId INTEGER,
       simSlotIndex INTEGER,
       label TEXT NOT NULL,
       iccId TEXT
     );
     INSERT OR IGNORE INTO link_profiles (id, kind, mode, ssid, subscriptionId, simSlotIndex, label, iccId)
     VALUES ('other', 'OTHER', 'PROXY', NULL, NULL, NULL, 'Other', NULL);
     ```
   - `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)`
   - **Remove** `.fallbackToDestructiveMigration()`  
     → verify: read `AppDatabase.kt` — no destructive fallback; version 3.

8. **Application wiring** — `ZerotierBApplication`: expose `linkProfileRepository`. On IO: `migrateStoredNetworkIds()`, `preferences.migrateGlobalModeIfNeeded()`, `linkProfileRepository.seedOther()`.  
     Do **not** register `SubscriptionManager` (needs `READ_PHONE_STATE`; T08). Stub = `upsertMobile` exists for later.  
     → verify: app still builds; boot path unchanged.

9. **Tests (JVM, JUnit 4, no Robolectric)** under `app/src/test/java/com/brukb/zerotier/data/`:
   - `GlobalModeMigrateTest`
   - `MainNetworkSelectorTest` (pin + createdAt + zeros-last)
   - `LinkProfileMergeTest` (new mobile vs keep mode on upsert)
   - `applyPin` if extracted  
     → verify: `./gradlew :app:testDebugUnitTest` includes these; 15 existing tests still pass.

10. **`make verify`** → lint + unit tests + assembleDebug green. Record in Verification.

### Tests to add

| Case | Expect |
| ---- | ------ |
| stored `AUTO`, boot false | `GlobalMode.AUTO` |
| stored null, boot true | `VPN` |
| stored null, boot false | `OFF` |
| stored garbage | `OFF` |
| one pinned among three | selector returns pinned |
| no pin, createdAt 100 and 50 | network with 50 |
| no pin, createdAt 0 and 50 | network with 50 (zero last) |
| all createdAt 0 | lower `networkId` |
| empty enabled | null |
| upsertMobile new | id `mobile-2`, mode PROXY |
| upsertMobile existing VPN + new label | mode stays VPN, label updates |

### Verify commands

- `./gradlew :app:testDebugUnitTest --console=plain`
- `make verify`

### Risks / pitfalls

- **SQLite ALTER + Room**: column names must match entity fields exactly (`createdAt`, `isPinnedMain`).
- **TypeConverters**: forgetting them → Room compile error on enum fields.
- **Destructive fallback removal**: v1 DBs still need `MIGRATION_1_2` registered (already exists).
- **Do not** add Compose Links UI or global segmented control (T09).
- **Do not** bump Compose BOM / AGP / Kotlin (T01 pin).
- `ZerotierBNetwork(...)` positional args in ViewModel — new fields **must** have defaults.
- Pin transaction: `REPLACE` upsert must not wipe pin accidentally; `addNetwork` should set `createdAt = System.currentTimeMillis()` on insert. In `NetworkRepository.upsert`, if existing row, keep `createdAt` unless already set; if new, set now. Pin unchanged unless `setPinnedMain`.

### Out of scope

- ConnectionOrchestrator / RuntimePlan (T03)
- Link classifier, NetworkCallback, SubscriptionManager listener (T04/T08)
- libzt, HTTP proxy, Shizuku, `HTTP_PROXY` writes
- VPN single-net filter
- UI for mode / Links / pin chip
- SOCKS, APN

### Execute model recommendation

- **small** — schema + DataStore + pure selectors; no runtime swap. Handoff is file-level.

## Test Plan

- Prove this layer without later layers.
- Commands: `make verify` (lint + unit tests + assembleDebug)
- New Kotlin: tests required (tables above)

## Acceptance Criteria

- [x] App upgrades from v2 DB without wiping networks (`MIGRATION_2_3`, no destructive fallback)
- [x] Unit tests for pin-main selection and createdAt sort
- [x] DataStore keys §10.1 present; `startOnBoot==true` migrates to `globalMode=VPN` when `global_mode` unset
- [x] `link_profiles` seeded with `other` / PROXY; `upsertMobile` stub exists
- [x] Tests added/updated for new behavior
- [x] Full lint + test verify suite green
- [x] Verification commands recorded and passing
- [x] No secrets committed

## Verification

```text
2026-08-23 /task-2-execute T02
Presence: Makefile verify + lefthook.yml + app/lint.xml OK
make verify → PASS
  :app:lintDebug PASS
  :app:testDebugUnitTest PASS (32 tests; + GlobalModeMigrate / MainNetworkSelector / LinkProfileMerge)
  :app:assembleDebug PASS
No fallbackToDestructiveMigration in AppDatabase

2026-08-23 /task-3-complete T02 (re-verify)
Presence OK; make verify → PASS (lint + unit tests + assembleDebug)
```

## Files Modified

- `app/src/main/java/com/brukb/zerotier/data/model/Modes.kt` (new)
- `app/src/main/java/com/brukb/zerotier/data/model/MainNetworkSelector.kt` (new)
- `app/src/main/java/com/brukb/zerotier/data/model/LinkProfile.kt` (new)
- `app/src/main/java/com/brukb/zerotier/data/model/ZerotierBNetwork.kt` (+createdAt, isPinnedMain)
- `app/src/main/java/com/brukb/zerotier/data/LinkConverters.kt` (new)
- `app/src/main/java/com/brukb/zerotier/data/LinkProfileDao.kt` (new)
- `app/src/main/java/com/brukb/zerotier/data/LinkProfileRepository.kt` (new)
- `app/src/main/java/com/brukb/zerotier/data/AppPreferences.kt` (global_mode + proxy + debounce)
- `app/src/main/java/com/brukb/zerotier/data/NetworkDao.kt` (setPinnedMain)
- `app/src/main/java/com/brukb/zerotier/data/NetworkRepository.kt` (createdAt on insert, setPinnedMain)
- `app/src/main/java/com/brukb/zerotier/data/AppDatabase.kt` (v3, MIGRATION_2_3, drop destructive)
- `app/src/main/java/com/brukb/zerotier/ZerotierBApplication.kt` (linkProfileRepository + migrate)
- `app/src/test/java/com/brukb/zerotier/data/GlobalModeMigrateTest.kt` (new)
- `app/src/test/java/com/brukb/zerotier/data/MainNetworkSelectorTest.kt` (new)
- `app/src/test/java/com/brukb/zerotier/data/LinkProfileMergeTest.kt` (new)
- `.cursor/rules/room.mdc` (dialectic: v3 invariants, pin/upsert/mode merge)
- `.cursor/skills/task-2-execute/SKILL.md` + `audit-rules` (no-push default wording)
- `planning/phases/INDEX.md` / `T02` / `T03` reality notes

## Manual test (for humans)

```bash
export ANDROID_HOME=/opt/android-sdk
./gradlew :app:installDebug
# Open ZerotierB — existing VPN UI still works (add/enable network, connect).
# Clear app data optional; with prior install: upgrade should keep networks (Room v2→v3).
# adb shell run-as com.brukb.zerotier ls databases/   # expect zerotierb.db
# Settings/boot toggles unchanged; global mode / Links UI not in this task (T09).
```

Success: app launches; prior networks still listed after upgrade; no wipe on schema bump.

## Learnings

- Mode A: none (test id pad mismatch only — test bug).
- Mode B: Room spoke updated — no destructive fallback; pin/createdAt upsert merge; link metadata keeps mode.
- Project default: `/task-3-complete` **no push** unless `--push` (skills wording aligned).

## Reality notes

- **Upstream T01:** Compose BOM is pinned to `2024.12.01` (was `2026.06.00`) so Android Lint works with AGP 8.7.3 / Kotlin 2.0.21. Do not bump Compose BOM casually in T02.
- Unit tests need `android.testOptions.unitTests.isReturnDefaultValues = true` (already set).
- `make verify` is the gate; `ANDROID_HOME` defaults to `/opt/android-sdk` in Makefile.
- `/task-3-complete` defaults to **no push** in this repo (`--push` to opt in).
- Live Room field names are `isEnabled` / `allowManaged` / `routePriority`, not the spec’s `isEnabled` / `allowManaged` / `routePriority`. Do not rename.
- No Robolectric in T01 tests — keep T02 selectors/migrations-logic JVM-pure; do not add instrumented MigrationTestHelper in this task.
