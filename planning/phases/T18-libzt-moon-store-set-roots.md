# T18 — libzt moon store + set_roots + Dummy planet

**Status**: Done  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T17  
**Next**: T19  
**Layer**: L4

## Description

Restore libzt behavior the C API already documents: persist moons, allow `orbit(id, 0)` when a `.moon` file exists, and actually call `zts_init_set_roots` from Java. Generate **once** a Dummy TYPE_PLANET (no public endpoints, world id ≠ Earth `149604618`) while **no** stack is running; save under `zt-worlds/dummy.planet`. **Do not** change `Topology` ctor (no skip-Earth flag). **Do not** rename libzt cache `roots` → `planet`. Rebuild and link `libzt-release.aar`.

Ships on submodule **`brukberhane/libzt` branch `pylon`**. Other machines: `git submodule update --init --recursive` then `./scripts/build-libzt.sh` (AAR is gitignored).

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| 2026-09-05 | created | — | Pending | /setup-tasks Roots feature | |
| 2026-09-05 | in progress | Planned | InProgress | /task-2-execute | |
| 2026-09-05 | executed | InProgress | InProgress | /task-2-execute — native + Kotlin + AAR rebuild | |
| 2026-09-05 | completed | InProgress | Done | /task-3-complete — verify green, dialectic, commit | |

## Requirements

- [x] `NodeService` put/get/delete `ZT_STATE_OBJECT_MOON` → `<home>/moons.d/<16hex>.moon` (same layout as JNI / `ZeroTierOne.h`)
- [x] `NodeService::orbit`: allow seed `0` (core `Topology::addMoon` already loads from store)
- [x] Java: `ZeroTierNative.zts_init_set_roots(byte[])`; JNI must call C (today’s stub returns OK and takes `void*` — unusable). Before `zts_node_start` only.
- [x] Dummy TYPE_PLANET generated **in memory** (no CWD key files, **no** private keys returned or written). Persist `zt-worlds/dummy.planet` only. World id ≠ Earth.
- [x] **No** `Topology.cpp` skip of `ZT_DEFAULT_WORLD`. Dummy + different world id is how Earth is not used.
- [x] Keep libzt planet cache filename **`roots`**. App restages from `zt-worlds/` in T19.
- [x] Native diffs in `libzt` working tree (`pylon`). Rebuild AAR. `make verify`. Parent submodule SHA bump is **`/task-3-complete`**.
- [x] clang-format-11 **line-range only** on libzt edits. Wipe `libzt/pkg/android/app/.cxx` + `libzt/cache/android-*` only if new `.cpp` added (this task should not add `.cpp` files).

## Non-goals (this task)

- Kotlin orbit-on-start / identity staging (T19)
- Roots UI (T20)
- JNI `Node.orbit` already exists — do not add a parallel JNI C API
- Auto-scan `moons.d` like desktop OneService (Room remains source of truth)
- Calling `zts_init_set_roots` from PROXY start (T19)
- Wiring Dummy into `ConnectionOrchestrator` / `Application.onCreate` (loads libzt for every launch)

## Constraints

- `.cursor/rules/libzt.mdc` — exclusive node; NODE_UP not NODE_ONLINE; AAR rebuild required (`make verify` does **not** rebuild AAR)
- Identity files never touched (`identity.secret` / `identity.public` / `networks.d`)
- One live ZeroTier node invariant unchanged
- NDK 28.2 for libzt AAR; x86_64 host (`scripts/build-libzt.sh`)
- clang-format-11 `--lines=start:end` only

## References

- `libzt/src/NodeService.cpp` — PLANET → `roots`; `orbit` rejects `!moon_seed` (1759); MOON falls through `default` (1901, 2001)
- `libzt/ext/ZeroTierOne/service/OneService.cpp` ~3149 / ~3300 — `moons.d/%.16llx.moon` (copy this path, not OneService scan)
- `libzt/src/bindings/java/JavaSockets.cxx` ~551–555 stub `zts_1init_1set_roots` (**wrong JNI name** + no C call)
- `libzt/src/bindings/java/com/zerotier/sockets/ZeroTierNative.java` ~430 commented native
- `libzt/include/ZeroTierSockets.h` `zts_init_set_roots`, `zts_moon_orbit`
- `libzt/src/Utilities.cpp` `zts_util_sign_root_set` — reads `previous.c25519` / `current.c25519` from **CWD**; do **not** call from Android Kotlin
- `.gitmodules` → `https://github.com/brukberhane/libzt.git` `pylon`
- T17: `RootsFileStore.dummyPlanetFile()`, `WorldBlobParser`, `IdentityHomeAllowlist`

## Reality notes

- Parent HEAD `7c2a7b1` (T17). libzt `pylon` @ `2ffb16b`. AAR is gitignored; stale AAR = old `.so`.
- `zts_util_sign_root_set` is **unsafe** as a Java API here: CWD key files, returns 96-byte C25519 **private** material, needs `zts_root_set_t` pointer marshalling. Grill “use sign_root_set” = same `World::make(TYPE_PLANET, …)` path **in C**, not the stock CWD helper from Kotlin.
- Existing JNI symbol `Java_…_zts_1init_1set_roots` would bind Java `zts_init_setroots`. Real name must be `zts_1init_1set_1roots` (see `zts_1init_1set_1port`).
- `ZeroTierNative.java` is compiled **from the AAR** (`libzt/pkg/android` `srcDir '../../../src/bindings/java'`). Edit libzt Java, then rebuild AAR — do not copy `ZeroTierNative` into `:app`.
- Do not auto-scan `moons.d` on libzt start (NodeService only scans `networks.d` today — leave that).
- Dummy world id constant (C + Kotlin must match): **`0x5e2071e4b0000001ULL`** (`5e2071e4b0000001` hex). Not Earth `149604618` (`0x08eac90a`), not Mars `227883110`.

## Implementation Plan

See Execution plan.

## Execution plan (filled by /task-1-plan)

**Date:** 2026-09-05  
**Codebase snapshot:** branch `T18-libzt-moon-store-set-roots` @ T17 `7c2a7b1`. libzt `pylon` `2ffb16b`. Room v5 + `zt-worlds/` already exist. `zts_init_set_roots` Java commented; JNI stub no-ops.  
**Execute model:** medium

### Context for executor

**Goal:** libzt can persist moons, accept `orbit(id, 0)`, accept a planet blob before start, and the app can create a Dummy planet file under `zt-worlds/` with **no secrets on disk**. T19 will stage that file / `set_roots` / orbit. This task does **not** change PROXY/VPN start.

**Do not:** edit `Topology.cpp`. Rename `roots` → `planet`. Start a node. Call `zts_util_sign_root_set` from Kotlin. Touch `identity.*`. Whole-file clang-format. Commit (wait for `/task-3-complete`).

**Key files:**

| Path | Action |
| ---- | ------ |
| `libzt/src/NodeService.cpp` | MOON put/get; orbit seed 0 |
| `libzt/src/Utilities.cpp` + `Utilities.hpp` if needed | `zts_util_make_dummy_planet` |
| `libzt/include/ZeroTierSockets.h` | declare Dummy helper + world-id `#define` |
| `libzt/src/bindings/java/JavaSockets.cxx` | real `set_roots` + Dummy JNI |
| `libzt/src/bindings/java/com/zerotier/sockets/ZeroTierNative.java` | natives |
| `libzt/src/bindings/java/com/zerotier/sockets/ZeroTierNode.java` | `initSetRoots(byte[])` |
| `app/.../data/DummyPlanet.kt` | **New** world-id + `isValidDummy` |
| `app/.../data/RootsFileStore.kt` | `writeDummyPlanet` |
| `app/.../data/RootsRepository.kt` | `ensureDummyPlanet` |
| `app/src/test/.../DummyPlanetTest.kt` | **New** |
| `app/src/test/.../RootsFileStoreTest.kt` | dummy write |

**Invariants:**
- `.cursor/rules/libzt.mdc` — AAR rebuild after native edit; NODE_UP not ONLINE; 127.0.0.1 bind unchanged
- `.cursor/rules/zerotier-jni.mdc` — identity-home allowlist; Dummy lives in `zt-worlds/` not `filesDir/planet` until T19
- `.cursor/rules/kotlin.mdc` — JVM tests for Kotlin; no Robolectric
- Exclusive stacks; do not start libzt+JNI

### Steps

1. **`NodeService::orbit`** (`NodeService.cpp` ~1757):
   Change `if (! moon_roots_id || ! moon_seed)` to **`if (! moon_roots_id)`** only. Seed `0` is valid (reload store). Keep `ZTS_ERR_SERVICE` if `!_run`.  
   → verify: grep `! moon_seed` gone from `orbit`. clang-format-11 `--lines` on that function only.

2. **`ZT_STATE_OBJECT_MOON` put** in `nodeStatePutFunction` switch **before** `default` (~1901). Mirror NETWORK_CONFIG + OneService:
   ```cpp
   case ZT_STATE_OBJECT_MOON:
       if (_homePath.length() > 0) {
           OSUtils::ztsnprintf(dirname, sizeof(dirname), "%s" ZT_PATH_SEPARATOR_S "moons.d", _homePath.c_str());
           OSUtils::ztsnprintf(
               p, sizeof(p),
               "%s" ZT_PATH_SEPARATOR_S "%.16llx.moon",
               dirname, (unsigned long long)id[0]);
       } else {
           return;
       }
       break;
   ```
   Existing write path already `mkdir(dirname)` if `dirname[0]` set (~1920). `len < 0` already `OSUtils::rm(p)` (delete). **Do not** memcpy into `_rootsData`. **Do not** send a new event unless one already exists for moons (skip events).  
   → verify: `dirname` is zeroed at function start (`dirname[0] = 0` ~1834). PLANET path stays `roots`.

3. **`ZT_STATE_OBJECT_MOON` get** in `nodeStateGetFunction` **before** `default` (~2001):
   ```cpp
   case ZT_STATE_OBJECT_MOON:
       OSUtils::ztsnprintf(
           p, sizeof(p),
           "%s" ZT_PATH_SEPARATOR_S "moons.d" ZT_PATH_SEPARATOR_S "%.16llx.moon",
           _homePath.c_str(), (unsigned long long)id[0]);
       break;
   ```
   Fall through to existing `fopen`/`fread`. No `_userDefinedWorld` branch.  
   → verify: `default` still `return -1` for unknown types.

4. **Do not** add moons.d directory scan next to networks.d join (~345). Room + T19 orbit are source of truth.

5. **`zts_init_set_roots` Java + JNI**
   - `ZeroTierNative.java`: uncomment as  
     `public static native int zts_init_set_roots(byte[] rootsData);`
   - `JavaSockets.cxx`: **replace** the stub. JNI name **must** be  
     `Java_com_zerotier_sockets_ZeroTierNative_zts_1init_1set_1roots`  
     (`jclass clazz`, `jbyteArray buf`) — **not** `zts_1init_1set_roots`, **not** `void*`.
   - Template (`zts_bsd_write` ~297): `GetPrimitiveArrayCritical` → `zts_init_set_roots(data, (unsigned)GetArrayLength(buf))` → `ReleasePrimitiveArrayCritical(..., JNI_ABORT)`. Null array → `ZTS_ERR_ARG`.
   - `ZeroTierNode.java`: add `initSetRoots(byte[] rootsData)` next to `initAllowRootsCache` (~110): `return ZeroTierNative.zts_init_set_roots(rootsData);`  
   → verify: `nm` / javap after AAR: method exists. No remaining `zts_1init_1set_roots` (missing `_1`).

6. **Dummy C helper** in `Utilities.cpp` (same file as `zts_util_sign_root_set`). **Do not** call `OSUtils::readFile("previous.c25519")`.
   - Header `ZeroTierSockets.h` near sign_root_set:
     ```c
     #define ZTS_WORLD_ID_DUMMY 0x5e2071e4b0000001ULL
     ZTS_API int ZTCALL zts_util_make_dummy_planet(void* roots_out, unsigned int* roots_len);
     ```
   - Impl: `C25519::Pair kp = C25519::generate();` empty `std::vector<World::Root> roots;`  
     `World nw = World::make(World::TYPE_PLANET, ZTS_WORLD_ID_DUMMY, /*ts*/1, kp.pub, roots, kp);`  
     serialize `forSign=false` into `roots_out`; set `*roots_len`. Null checks → `ZTS_ERR_ARG`. Buffer must be at least `ZT_WORLD_MAX_SERIALIZED_LENGTH` (or document min 256). **Never** copy `kp.priv` out. **Never** write files.  
   → verify: empty-root planet first byte `0x01`; id BE `5e2071e4b0000001`.

7. **Dummy JNI** `JavaSockets.cxx`:
   `public static native byte[] zts_util_make_dummy_planet();`  
   Allocate `unsigned char buf[ZT_WORLD_MAX_SERIALIZED_LENGTH]`, call C, `NewByteArray` + `SetByteArrayRegion`. Failure → `null`.  
   JNI name: `zts_1util_1make_1dummy_1planet`.

8. **Kotlin `DummyPlanet`** `app/.../data/DummyPlanet.kt`:
   ```kotlin
   object DummyPlanet {
     const val WORLD_ID_LONG = 0x5e2071e4b0000001L
     val WORLD_ID_HEX = "5e2071e4b0000001"
     fun isValid(bytes: ByteArray): Boolean {
       val parsed = WorldBlobParser.parse(bytes)
       return parsed is WorldBlobParseResult.Planet && parsed.worldId == WORLD_ID_HEX
     }
   }
   ```
   `RootsFileStore.writeDummyPlanet(bytes)` — `dir.mkdirs(); dummyPlanetFile().writeBytes(bytes)` (same as custom planet).
   `RootsRepository.ensureDummyPlanet(generate: () -> ByteArray): ByteArray`:
   - If `dummyPlanetFile()` exists and `DummyPlanet.isValid(readBytes())` → return those bytes (no regen).
   - Else `val bytes = generate()`; if `!isValid(bytes)` throw; `writeDummyPlanet`; return bytes.  
   **Do not** invoke `generate` from `Application.onCreate`. T19 will pass `{ ZeroTierNative.zts_util_make_dummy_planet() }`.  
   → verify: unit tests with fake `generate` (no libzt load).

9. **Rebuild AAR** (mandatory; `make verify` will **not** pick up C++ otherwise):
   ```bash
   ./scripts/build-libzt.sh
   ```
   Needs `ANDROID_HOME`, NDK 28.2, x86_64. Wipe `.cxx` only if link misses new symbols (no new `.cpp` expected).  
   → verify: `unzip -l libzt/dist/android-any-android-release/libzt-release.aar | grep libzt.so` ; `nm` or `javap -classpath …/classes.jar com.zerotier.sockets.ZeroTierNative | grep set_roots`

10. **`make verify`**. Fix only failures you caused. Native compile errors → AAR log, not Gradle `:app` first.

11. **clang-format-11** `--lines=start:end` on edited C++ ranges only. Recreate venv if needed: `pip install clang-format==11.0.1`.

### Tests to add

**DummyPlanetTest** (pure JVM, fake signer):
- `isValid` false on Earth fixture from `WorldBlobParserTest` (`0x01` + `0x08eac90a`).
- `isValid` true on 17-byte blob: type `0x01` + BE `5e2071e4b0000001` + 8 zero ts.
- `ensureDummyPlanet`: missing file → calls generate once, writes `dummy.planet`.
- Second call: does **not** call generate again if file valid.
- Corrupt existing file → regenerate.

**RootsFileStoreTest**: `writeDummyPlanet` round-trip; path is under store dir, name `dummy.planet`.

**IdentityHomeAllowlistTest** (already denies `zt-worlds/planet`): add deny `dummy.planet` as an identity-home relative path.

Do **not** load `libzt.so` in JVM unit tests. Native Dummy round-trip is device / T19.

### Verify commands

- `./scripts/build-libzt.sh`
- `make verify`

### Risks / pitfalls

- **Stale AAR:** edit C++, skip `build-libzt.sh` → APK still old. `libzt patch not in APK`.
- **JNI name:** `set_roots` needs **three** `_1` segments (`init`, `set`, `roots`).
- **`zts_util_sign_root_set` from app:** CWD `*.c25519` + private keys in Java heaps. Forbidden.
- **Dummy keys:** must never land in `identity.secret` or `zt-worlds/`. Blob only.
- **mkdir moons.d:** set `dirname` like NETWORK_CONFIG or write fails silently (WARNING fprintf).
- **orbit seed 0 without file:** core no-ops; T19 copies `.moon` first.
- **Do not commit libzt** until `/task-3-complete` (parent records submodule SHA then).
- **Termux:** cannot rebuild AAR (x86_64 NDK). This task needs a desktop host.

### Out of scope

- Stage Dummy/`set_roots`/orbit on PROXY or VPN start (T19)
- Settings Roots UI (T20)
- Topology skip-Earth
- QR / clipboard
- Multiple Dummy files / regenerating on every launch

### Execute model recommendation

- medium — native + JNI + AAR rebuild; Kotlin Dummy store is small. Plan is enough for a lesser model if they follow JNI names and **do not** use stock `sign_root_set` from Java.

## Verification

- verify tooling: `Makefile` + `lefthook.yml` + `app/lint.xml` present
- `./scripts/build-libzt.sh` (with `PATH` including `/opt/android-sdk/cmake/3.22.1/bin`) — AAR rebuilt; `javap` shows `zts_init_set_roots(byte[])` + `zts_util_make_dummy_planet()`; `nm -D libzt.so` shows both C symbols
- `make verify` — exit 0 (lint + unit tests + assembleDebug)
- New JVM tests: `DummyPlanetTest`, `RootsFileStoreTest` (dummy write), `IdentityHomeAllowlistTest` (deny `dummy.planet`)

## Files Modified

- `libzt/src/NodeService.cpp` — MOON put/get; `orbit(id, 0)`
- `libzt/src/Utilities.cpp` — `zts_util_make_dummy_planet`
- `libzt/include/ZeroTierSockets.h` — `ZTS_WORLD_ID_DUMMY`, `ZTS_WORLD_MAX_SERIALIZED_LENGTH`, declare helper
- `libzt/src/bindings/java/JavaSockets.cxx` — real `set_roots` + dummy JNI
- `libzt/src/bindings/java/com/zerotier/sockets/ZeroTierNative.java` — natives
- `libzt/src/bindings/java/com/zerotier/sockets/ZeroTierNode.java` — `initSetRoots`
- `app/src/main/java/com/brukb/zerotier/data/DummyPlanet.kt` (new)
- `app/src/main/java/com/brukb/zerotier/data/RootsFileStore.kt` — `writeDummyPlanet`
- `app/src/main/java/com/brukb/zerotier/data/RootsRepository.kt` — `ensureDummyPlanet`
- `app/src/test/java/com/brukb/zerotier/data/DummyPlanetTest.kt` (new)
- `app/src/test/java/com/brukb/zerotier/data/RootsFileStoreTest.kt` — dummy round-trip
- `app/src/test/java/com/brukb/zerotier/data/IdentityHomeAllowlistTest.kt` — deny dummy paths

## Learnings

- libzt `NodeService` lacked `ZT_STATE_OBJECT_MOON` — mirror OneService `moons.d/<16hex>.moon`; `orbit(id,0)` valid when file staged first (T19).
- JNI `zts_init_set_roots`: symbol must be `zts_1init_1set_1roots` (three segments); takes `byte[]`, not `void*`.
- Dummy planet: `zts_util_make_dummy_planet()` in C only — never `zts_util_sign_root_set` from Kotlin (CWD keys + private material).
- Do not `#include <node/World.hpp>` from `JavaSockets.cxx` — lwIP/socket redefinition; use `ZTS_WORLD_MAX_SERIALIZED_LENGTH` in `ZeroTierSockets.h`.
- AAR rebuild: host needs SDK cmake on `PATH`; failed Gradle can leave stale AAR — verify with `javap`/`nm` before trusting `build-libzt.sh` output.
