# T17 — Roots persistence + world parse

**Status**: Done  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T16  
**Next**: T18  
**Layer**: L1

## Description

Operator moons and planet blobs persist across process death **without** talking to a live ZeroTier node. Room holds moon metadata; DataStore holds airgap / latch / Earth-vs-Custom; app-owned files under `files/zt-worlds/` hold copied binaries and the Dummy planet blob path. Parsers classify SAF bytes as TYPE_MOON, TYPE_PLANET, or `moon.json` (id+seed only). **No** stack start, **no** `planet`/`roots` writes in identity home, **no** UI.

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| 2026-09-05 | created | — | Pending | /setup-tasks Roots feature | |
| 2026-09-05 | planned | Pending | Planned | /task-1-plan — persist + parse only; no apply/UI/native | |
| 2026-09-05 | execute | Planned | InProgress | /task-2-execute T17 | |
| 2026-09-05 | complete | InProgress | Done | /task-3-complete T17 — verify green; Room v5 moons + parser + zt-worlds | |

## Requirements

- [x] Room **v5** + real `MIGRATION_4_5` (no destructive fallback). Entity moons: `worldId` TEXT PK (16-hex), optional `seed` (ZT address hex), `label`, `createdAt`, `hasMoonFile`. Max 16; insert rejects duplicate `worldId`.
- [x] DataStore: `airgap` bool default false; `airgapWithoutMoons` latch default false; `planetSource` `earth` \| `custom` default `earth`.
- [x] `files/zt-worlds/<worldId>.moon`, one custom `planet`, Dummy path `dummy.planet` (file may be created in T18). Copy bytes in; never persist SAF URIs.
- [x] Pure parser: binary `World` first byte `TYPE_PLANET=1` / `TYPE_MOON=127` + big-endian world id; JSON `objtype=world` / `worldType=moon` → id + first root identity address as seed. **Drop `signingKey` / `signingKey_SECRET` — do not persist.** Unknown/corrupt → error type, no coerce.
- [x] Identity allowlist helper used later by T19: paths we may touch = `planet`, `roots`, `moons.d/*` only. Unit-test that identity.* and `networks.d` are **not** in the allowlist.
- [x] JVM unit tests for parser + migration + moon cap/dedupe. `make verify`.

## Non-goals (this task)

- libzt / JNI patches, AAR rebuild (T18)
- Staging into identity home, orbit/deorbit, Dummy generation (T18/T19)
- Compose Roots screen (T20)
- QR, clipboard, camera, `joinzt.com`

## Constraints

- `.cursor/rules/room.mdc` — real migration; do not bump without `MIGRATION_4_5`
- `.cursor/rules/kotlin.mdc` — extract parse/allowlist; table-test
- Never write `identity.secret` / `identity.public`
- No AGP/Kotlin/BOM bump

## References

- `app/src/main/java/com/brukb/zerotier/data/AppDatabase.kt` (v4)
- `app/src/main/java/com/brukb/zerotier/data/AppPreferences.kt`
- `libzt/ext/ZeroTierOne/node/World.hpp` serialize layout (type byte, uint64 id)
- Grill settled: Room + app files + DataStore; `moon.json` = id+seed only

## Reality notes

- Room **v4**, entities `ZerotierBNetwork` + `LinkProfile`. Migrations 1→2→3→4 via `.addMigrations(...)`. **No** `fallbackToDestructiveMigration`. `exportSchema = false`.
- `app/src/test/.../data/` is **pure JVM** (no Robolectric, no in-memory Room, no `MigrationTestHelper`). Do **not** add instrumented migration tests. Prove cap/dedupe/parse/allowlist/file-store in pure functions + `java.io.File` temp dirs. KSP/Room compile is `assembleDebug`.
- No moon/planet/roots types under `app/.../data/`.
- No `org.json` usage in app. **Do not** use `JSONObject` (android.jar stubs throw in unit tests). Hand-parse moon JSON.
- `ZerotierBNetwork.normalizeNetworkId` is 16-hex pad for **network** IDs. World IDs are also uint64/16-hex — put `Moon.normalizeWorldId` on the moon type (same rules, own name). Seed is **10-hex** address (`Moon.normalizeSeed`), not 16-hex.
- `initmoon` JSON `"id"` is the 10-hex seed **address**; genmoon filename / binary `_id` is 16-hex `%.16llx`. Parser: pad JSON id to 16-hex for `worldId`; first `roots[].identity` field before `:` → 10-hex `seed`.
- T16 learnings (proxy stop/Doze) do not apply. Do not edit proxy/VPN/orchestrator.
- `AppPreferences.kt` / other files may already be dirty on disk from unrelated WIP. **Only add** T17 keys; do not revert or restyle unrelated prefs.

## Implementation Plan

See Execution plan. High-level: Room v5 `moons` + DataStore keys + `zt-worlds/` file store + pure `WorldBlobParser` + `IdentityHomeAllowlist`. Wire `RootsRepository` on `ZerotierBApplication` only. No UI, no identity-home writes.

## Execution plan (filled by /task-1-plan)

**Date:** 2026-09-05  
**Codebase snapshot:** branch `main` @ `a0b4d74` (`fix(proxy): join only after NODE_UP event`). Room v4 in `AppDatabase.kt`. Prefs have no airgap/planetSource. No `moons` table.  
**Execute model:** small

### Context for executor

**Goal:** Persist Roots **config** only so T18/T19/T20 have a store and a parser. After this task, process death keeps moons + flags + copied blobs. ZeroTier node is never started. Identity home (`filesDir/planet`, `identity.*`, `networks.d`) is never written.

**Do not:** edit `libzt/`, `ProxyModeService`, `ZerotierBVpnService`, `ConnectionOrchestrator`, Compose screens, or generate Dummy planet bytes.

**Key files (create/edit only these):**

| Path | Action |
| ---- | ------ |
| `app/src/main/java/com/brukb/zerotier/data/model/Moon.kt` | **New** entity |
| `app/src/main/java/com/brukb/zerotier/data/model/Modes.kt` | Add `PlanetSource` |
| `app/src/main/java/com/brukb/zerotier/data/MoonDao.kt` | **New** |
| `app/src/main/java/com/brukb/zerotier/data/AppDatabase.kt` | v5 + `MIGRATION_4_5` + `moonDao()` |
| `app/src/main/java/com/brukb/zerotier/data/AppPreferences.kt` | three keys |
| `app/src/main/java/com/brukb/zerotier/data/WorldBlobParser.kt` | **New** pure parse |
| `app/src/main/java/com/brukb/zerotier/data/IdentityHomeAllowlist.kt` | **New** pure |
| `app/src/main/java/com/brukb/zerotier/data/MoonInsertPolicy.kt` | **New** pure cap/dup |
| `app/src/main/java/com/brukb/zerotier/data/RootsFileStore.kt` | **New** `File`-based |
| `app/src/main/java/com/brukb/zerotier/data/RootsRepository.kt` | **New** |
| `app/src/main/java/com/brukb/zerotier/ZerotierBApplication.kt` | construct `rootsRepository` |
| `app/src/test/java/com/brukb/zerotier/data/WorldBlobParserTest.kt` | **New** |
| `app/src/test/java/com/brukb/zerotier/data/IdentityHomeAllowlistTest.kt` | **New** |
| `app/src/test/java/com/brukb/zerotier/data/MoonInsertPolicyTest.kt` | **New** |
| `app/src/test/java/com/brukb/zerotier/data/RootsFileStoreTest.kt` | **New** |
| `app/src/test/java/com/brukb/zerotier/data/model/PlanetSourceTest.kt` | **New** |

**Invariants:**
- `.cursor/rules/room.mdc` — real `MIGRATION_4_5`; register **all** prior migrations still; no destructive fallback; Room 2.7.2 stays (KSP2).
- `.cursor/rules/kotlin.mdc` — JVM unit tests; no `runBlocking` on main; package `com.brukb.zerotier`.
- Allowlist is **identity home relative paths** for T19. `zt-worlds/` is **not** identity home (separate store).
- HTTP proxy stays 127.0.0.1. Do not start libzt and JNI together (you will not start either).

### Steps

1. **`PlanetSource`** in `Modes.kt` (same file as `GlobalMode`):
   ```kotlin
   enum class PlanetSource { EARTH, CUSTOM;
     companion object {
       fun parse(raw: String?): PlanetSource {
         if (raw.isNullOrBlank()) return EARTH
         return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: EARTH
       }
     }
   }
   ```
   Unknown / blank → `EARTH`. No `DUMMY` enum (airgap is a separate bool; T19 maps airgap→Dummy at apply time).  
   → verify: `PlanetSourceTest` (null, `""`, `earth`, `CUSTOM`, `bogus` → EARTH/CUSTOM as specified).

2. **`Moon` entity** `data/model/Moon.kt`:
   ```kotlin
   @Entity(tableName = "moons")
   data class Moon(
     @PrimaryKey val worldId: String,
     val seed: String? = null,
     val label: String = "",
     val createdAt: Long = 0L,
     val hasMoonFile: Boolean = false,
   )
   ```
   Companion:
   - `MAX_MOONS = 16`
   - `normalizeWorldId(raw: String): String` — trim, lower, strip `0x`, 1..16 hex chars, `padStart(16, '0')`. Throw or return null; **policy** uses nullable `String?` (`isValidWorldId` / `normalizeWorldIdOrNull`).
   - `normalizeSeed(raw: String?): String?` — null/blank → null. Else 1..10 hex, `padStart(10, '0')`. Invalid → null (caller treats as invalid seed if user typed garbage).
   Hex alphabet `[0-9a-f]` only.  
   → verify: table tests in `MoonInsertPolicyTest` (or small `MoonNormalizeTest` if you split): `deadbeef00` world → `000000deadbeef00`; seed `7d115xxxxx` 10-hex; reject `gg`, length 17.

3. **`MoonInsertPolicy`** (pure, no Room):
   ```kotlin
   object MoonInsertPolicy {
     fun rejectReason(existingWorldIds: Set<String>, worldId: String): String?
     // null = ok
     // "invalid" | "duplicate" | "cap"  (use a sealed class if you prefer; keep it testable)
   }
   ```
   Rules: invalid worldId → reject; `worldId in existing` → duplicate; `existing.size >= 16` → cap. Duplicate check is **normalized** ids.  
   → verify: `MoonInsertPolicyTest` — 0 rows insert ok; 16th ok, 17th cap; same id duplicate; unnormalized vs padded treated as duplicate (`deadbeef00` vs `000000deadbeef00`).

4. **`MoonDao`** — match `NetworkDao` style:
   - `observeAll(): Flow<List<Moon>>` order `createdAt ASC, worldId ASC`
   - `suspend fun getAll(): List<Moon>`
   - `suspend fun getById(worldId: String): Moon?`
   - `@Insert(onConflict = OnConflictStrategy.ABORT)` `insert` — **not** REPLACE (requirement: reject duplicate)
   - `suspend fun delete(worldId: String)`
   - `suspend fun count(): Int` (`SELECT COUNT(*) FROM moons`)
   Do not add upsert/REPLACE.

5. **`AppDatabase`**
   - `entities = [ZerotierBNetwork::class, LinkProfile::class, Moon::class]`
   - `version = 5`
   - `abstract fun moonDao(): MoonDao`
   - `MIGRATION_4_5`:
     ```sql
     CREATE TABLE IF NOT EXISTS moons (
       worldId TEXT NOT NULL PRIMARY KEY,
       seed TEXT,
       label TEXT NOT NULL,
       createdAt INTEGER NOT NULL,
       hasMoonFile INTEGER NOT NULL
     )
     ```
   - `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)`
   - Still **no** `fallbackToDestructiveMigration`  
   → verify: `assembleDebug` / `make verify` compiles KSP. Grep `fallbackToDestructiveMigration` still absent.

6. **`AppPreferences`** — add only:
   - `booleanPreferencesKey("airgap")` → `airgap: Flow<Boolean>` default **false**; `setAirgap`
   - `booleanPreferencesKey("airgap_without_moons")` → `airgapWithoutMoons: Flow<Boolean>` default **false**; `setAirgapWithoutMoons`
   - `stringPreferencesKey("planet_source")` → `planetSource: Flow<PlanetSource>` via `PlanetSource.parse`; setter stores `value.name`
   Mirror `startOnBoot` / `globalMode` style. Do not add Dummy to DataStore.  
   → verify: existing `DnsFallbackServersTest` still passes (you did not break companion). Optional: `PlanetSource.parse` already tested.

7. **`WorldBlobParser`** — `sealed class WorldBlobParseResult`:
   - `data class Moon(val worldId: String, val seed: String?)`  // 16-hex id, 10-hex seed or null
   - `data class Planet(val worldId: String)`
   - `data class Error(val reason: String)`
   - `fun parse(bytes: ByteArray): WorldBlobParseResult`

   **Dispatch:** trim UTF-8 BOM. If first non-whitespace is `{` → JSON path. Else binary.

   **Binary** (`World.hpp` serialize `forSign=false`, BE):
   - need `size >= 17`
   - `type = bytes[0].toInt() and 0xff`
   - `id = readBeU64(bytes, 1)` then `worldId = java.lang.Long.toUnsignedString(id, 16).padStart(16, '0')`
   - `type == 1` → `Planet`
   - `type == 127` → `Moon(worldId, seed = null)` (seed comes from file presence + orbit later; do not parse roots)
   - else → `Error`
   - `readBeU64`: eight bytes MSB first, `shl 8` / `toLong() and 0xff`. Do **not** use `ByteBuffer` with native endian.

   **JSON** (UTF-8 string). Require `worldType` of `moon` (case-insensitive). `objtype` of `world` if present must be `world`; if `worldType` missing → Error. `worldType` `planet` → Error (custom planet is **binary only**).
   - Extract `"id"` string; `Moon.normalizeWorldId` (10-hex pads to 16).
   - First `"identity"` string (initmoon `roots[].identity` form `addr:0:pub…`): take substring before first `:`, `Moon.normalizeSeed`. No identity → `seed = null`.
   - **Never** copy `signingKey`, `signingKey_SECRET`, `updatesMustBeSignedBy` into the result type.
   - Hand-roll field extract (regex or small scanner). No `org.json.JSONObject`.

   Corrupt / too short / not hex → `Error`. No silent coerce of planet JSON to moon.  
   → verify: `WorldBlobParserTest` cases in **Tests to add**.

8. **`IdentityHomeAllowlist`**
   ```kotlin
   object IdentityHomeAllowlist {
     fun isAllowedRelative(path: String): Boolean
   }
   ```
   Normalize: `\` → `/`, strip leading `./`, reject if empty, if starts with `/`, if any `..` segment.
   **Allow only:**
   - `planet`
   - `roots`
   - `moons.d/<16 lowercase hex>.moon` (exactly one directory segment `moons.d`, filename `^[0-9a-f]{16}\.moon$`)
   **Deny** (tests must include): `identity.public`, `identity.secret`, `networks.d/foo.conf`, `peers.d/x`, `moons.d/../identity.secret`, `zt-worlds/planet`, `planet.bak`.  
   → verify: `IdentityHomeAllowlistTest` parameterized/table.

9. **`RootsFileStore(private val dir: File)`** — `dir` is `File(filesDir, "zt-worlds")` in production. **Never** takes identity `filesDir` as the store root.
   Constants: `CUSTOM_PLANET_NAME = "planet"`, `DUMMY_PLANET_NAME = "dummy.planet"`.
   - `fun moonFile(worldId: String): File` = `dir / "$normalized.moon"`
   - `fun writeMoon(worldId: String, bytes: ByteArray)` — `dir.mkdirs()`, write **only** under `dir` (reject if normalized id has `/`)
   - `fun deleteMoon(worldId: String)` — delete if exists
   - `fun writeCustomPlanet(bytes: ByteArray)` — overwrite `dir/planet`
   - `fun deleteCustomPlanet()`
   - `fun customPlanetFile(): File`
   - `fun dummyPlanetFile(): File` — path only; **do not** create Dummy bytes (T18)
   Do not write `../planet` or identity home.  
   → verify: `RootsFileStoreTest` with `Files.createTempDirectory`. Write moon, read bytes back, delete; custom planet replace; moonFile cannot escape `dir`.

10. **`RootsRepository(dao: MoonDao, files: RootsFileStore)`**
    - `fun observeMoons(): Flow<List<Moon>> = dao.observeAll()`
    - `suspend fun addMoon(worldId: String, seed: String?, label: String = "", moonBytes: ByteArray? = null): AddMoonResult`
      sealed `Ok` / `Invalid` / `Duplicate` / `AtCap`.
      Normalize ids via `Moon.*`. `MoonInsertPolicy.rejectReason(dao.getAll().map { it.worldId }.toSet(), normalized)`. If bytes != null, `files.writeMoon` **then** `dao.insert(Moon(..., hasMoonFile = true, createdAt = now if 0))`. If bytes == null, insert `hasMoonFile = false`. Do not REPLACE.
    - `suspend fun removeMoon(worldId: String)` — `dao.delete` + `files.deleteMoon` (both, even if file missing)
    - `suspend fun saveCustomPlanet(bytes: ByteArray)` / `deleteCustomPlanet()`
    Keep Room and files here so T20 does not forget to delete the blob.  
    → verify: policy tests cover cap/dup; file store tests cover bytes. Optional: skip a Robolectric repo test.

11. **`ZerotierBApplication`**
    ```kotlin
    lateinit var rootsRepository: RootsRepository
    // after database =
    rootsRepository = RootsRepository(
      database.moonDao(),
      RootsFileStore(File(filesDir, "zt-worlds")),
    )
    ```
    Do **not** pass into `ConnectionOrchestrator` yet (T19).  
    → verify: `:app:assembleDebug` compiles.

12. **`make verify`** — lint + unit tests + assembleDebug. Fix only failures you caused.

### Tests to add

**WorldBlobParserTest**
- Binary planet: `[0x01]` + 8 BE bytes id `0x08eac90a` (Earth) + 8 zero ts → `Planet("0000000008eac90a")`. (Earth id `149604618` = `0x08eac90a`; pad 16.)
- Binary moon: `[0x7f]` + id `0xdeadbeef00` as u64 + 8 zero ts → `Moon("000000deadbeef00", seed=null)`
- Binary type `0` / length 16 → `Error`
- JSON initmoon-shaped (include `signingKey_SECRET: "ffc5…"` and `signingKey`) → `Moon`, seed = first identity address, result `toString()` / fields contain **neither** `signingKey` nor the secret hex. Use a compact fixture:
  ```json
  {"objtype":"world","worldType":"moon","id":"deadbeef00","signingKey":"aa","signingKey_SECRET":"bb","roots":[{"identity":"deadbeef00:0:abcd","stableEndpoints":[]}]}
  ```
- JSON `worldType":"planet"` → `Error`
- JSON missing id → `Error`
- `{` garbage → `Error`
- Empty array → `Error`

**MoonInsertPolicyTest** — invalid / duplicate padded / 16 vs 17.

**IdentityHomeAllowlistTest** — allow `planet`, `roots`, `moons.d/000000deadbeef00.moon`; deny identity, networks.d, `..`, `moons.d/nothex.moon`.

**RootsFileStoreTest** — temp dir write/read/delete moon + custom planet.

**PlanetSourceTest** — parse defaults.

Do not add Robolectric. Do not add `MigrationTestHelper`.

### Verify commands

- `make verify`

### Risks / pitfalls

- **REPLACE vs ABORT:** `OnConflictStrategy.REPLACE` would “accept” duplicates by overwrite. Use ABORT + policy check first.
- **JSONObject in unit tests:** will crash. Hand-parse.
- **JSON `id` vs binary id:** 10-hex vs 16-hex. Always store 16-hex `worldId` in Room.
- **Seed vs worldId:** seed is 10-hex **address**. Do not 16-pad seeds.
- **Writing identity home by mistake:** `RootsFileStore` root is `filesDir/zt-worlds`, **not** `filesDir`. Allowlist is a separate predicate for T19.
- **Dirty `AppPreferences`:** surgical add only.
- **KSP:** new entity must be in `@Database(entities=…)`. Forget this → compile fail.
- **Boolean in SQL:** `hasMoonFile INTEGER NOT NULL` (same as other tables).

### Out of scope

- `zts_init_set_roots`, libzt `moons.d` persist, Dummy generation, AAR (T18)
- Orbit/deorbit, delete live `planet`/`roots` (T19)
- Settings Roots UI, SAF picker (T20)
- QR / clipboard / camera
- Changing `RuntimeStatusMapper` copy (T19/T20)

### Execute model recommendation

- small — data layer + pure parsers; no native, no orchestrator.

## Verification

- verify tooling: `Makefile` + `lefthook.yml` + `app/lint.xml` present
- `make verify` — exit 0 (lint + unit tests + assembleDebug)
- grep `fallbackToDestructiveMigration` in `app/` — no matches
- Room v5 + `MIGRATION_4_5` registered; `Moon` entity + `MoonDao` with `OnConflictStrategy.ABORT`
- New JVM tests: `WorldBlobParserTest`, `MoonInsertPolicyTest`, `IdentityHomeAllowlistTest`, `RootsFileStoreTest`, `PlanetSourceTest`

## Files Modified

- `app/src/main/java/com/brukb/zerotier/data/model/Moon.kt` (new)
- `app/src/main/java/com/brukb/zerotier/data/model/Modes.kt` — `PlanetSource`
- `app/src/main/java/com/brukb/zerotier/data/MoonDao.kt` (new)
- `app/src/main/java/com/brukb/zerotier/data/MoonInsertPolicy.kt` (new)
- `app/src/main/java/com/brukb/zerotier/data/WorldBlobParser.kt` (new)
- `app/src/main/java/com/brukb/zerotier/data/IdentityHomeAllowlist.kt` (new)
- `app/src/main/java/com/brukb/zerotier/data/RootsFileStore.kt` (new)
- `app/src/main/java/com/brukb/zerotier/data/RootsRepository.kt` (new)
- `app/src/main/java/com/brukb/zerotier/data/AppDatabase.kt` — v5 + migration
- `app/src/main/java/com/brukb/zerotier/data/AppPreferences.kt` — airgap prefs
- `app/src/main/java/com/brukb/zerotier/ZerotierBApplication.kt` — `rootsRepository`
- `app/src/test/java/com/brukb/zerotier/data/WorldBlobParserTest.kt` (new)
- `app/src/test/java/com/brukb/zerotier/data/MoonInsertPolicyTest.kt` (new)
- `app/src/test/java/com/brukb/zerotier/data/IdentityHomeAllowlistTest.kt` (new)
- `app/src/test/java/com/brukb/zerotier/data/RootsFileStoreTest.kt` (new)
- `app/src/test/java/com/brukb/zerotier/data/model/PlanetSourceTest.kt` (new)

## Learnings

- Roots config splits three stores: Room `moons`, DataStore airgap/planetSource, app files `zt-worlds/` (not identity home).
- World binary: type byte + BE uint64 id at offset 1; moon JSON `id` may be 10-hex (pad to 16 for Room); seed is 10-hex address from `identity` before first `:`.
- `MoonDao` uses `OnConflictStrategy.ABORT` + `MoonInsertPolicy` cap (16) — not REPLACE.
- JVM tests: no `JSONObject` (android stubs); byte literals >127 need `.toByte()` in `byteArrayOf`.
