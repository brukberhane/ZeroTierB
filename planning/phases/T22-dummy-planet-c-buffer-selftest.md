# T22 — Dummy planet C buffer + native selftest

**Status**: Pending  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T21  
**Next**: T10  
**Layer**: L4

## Description

T18 shipped moon store, `set_roots`, and Dummy generate. Two leftovers: C `zts_util_make_dummy_planet` ignores input `*roots_len` (memcpy overflow if the caller buffer is smaller than the serialized world), and there is no native selftest — JVM Dummy tests use a 17-byte unsigned-id fixture, not a real signed World. Fix the C API and add a host selftest. Rebuild AAR. No Topology skip-Earth.

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| 2026-09-05 | created | — | Pending | T18 execution leftover | |

## Requirements

- [ ] `zts_util_make_dummy_planet`: treat `*roots_len` as **capacity in**, length **out**. If `*roots_len < serialized size` → `ZTS_ERR_ARG`, no write. Null `roots_out` / `roots_len` still `ZTS_ERR_ARG`.
- [ ] JNI path unchanged in behavior: buffer is `ZTS_WORLD_MAX_SERIALIZED_LENGTH`, `len = sizeof(buf)` before the C call.
- [ ] `libzt/test/selftest.c` (or existing host test target): Dummy blob type byte `0x01`, world id `ZTS_WORLD_ID_DUMMY`, not Earth `0x08eac90a`; tiny-buffer call returns `ZTS_ERR_ARG`. Do **not** load `libzt.so` in Android JVM unit tests.
- [ ] Rebuild `libzt-release.aar` (`./scripts/build-libzt.sh` with SDK cmake on `PATH`). `javap` / `nm` still show Dummy + `set_roots`.
- [ ] clang-format-11 `--lines` only on edited C ranges. No `Topology.cpp`. No `zts_util_sign_root_set` from Kotlin.
- [ ] `make verify`.

## Non-goals (this task)

- Kotlin orbit/stage (T19 done)
- Roots UI (T20)
- T21 VPN/PROXY threading
- Auto-scan `moons.d`
- Skip-Earth Topology flag

## Constraints

- `.cursor/rules/libzt.mdc` — AAR rebuild after native edit; `World.hpp` not from `JavaSockets.cxx`
- Identity files never touched
- NDK 28.2 for AAR; x86_64 host for `build-libzt.sh`

## References

- `libzt/src/Utilities.cpp` `zts_util_make_dummy_planet` — `memcpy` without capacity check
- `libzt/src/bindings/java/JavaSockets.cxx` Dummy JNI
- `libzt/include/ZeroTierSockets.h` `ZTS_WORLD_ID_DUMMY`, `ZTS_WORLD_MAX_SERIALIZED_LENGTH`
- `libzt/test/selftest.c`
- T18 Learnings: JNI name `zts_1init_1set_1roots`; Dummy keys never on disk

## Reality notes (from T18 close-out)

- T18 AC for moon put/get, `orbit(id,0)`, Java `set_roots`, Dummy Kotlin store **did land**. This task is leftover safety + proof, not a redo.
- JNI Dummy allocates `unsigned char buf[ZTS_WORLD_MAX_SERIALIZED_LENGTH]` and passes `len = sizeof(buf)` — safe today. Bare C callers are not.
- JVM `DummyPlanet.isValid` only checks parser world id; it accepts a 17-byte truncated header. Native selftest must use the real serializer.

## Implementation Plan

See Execution plan.

## Execution plan (filled by /task-1-plan)

**Date:** 2026-09-05  
**Codebase snapshot:** after T21. libzt `pylon` as recorded by T18 (`2dad562` or later).  
**Execute model:** medium (AAR rebuild)

### Context for executor

**Goal:** Dummy C helper cannot overflow; host selftest proves type+id. App Kotlin behavior unchanged.

**Do not:** edit Topology. Rename `roots` → `planet`. Call `sign_root_set` from Kotlin. Whole-file clang-format. Skip AAR rebuild.

### Steps

1. **Capacity check** in `libzt/src/Utilities.cpp` `zts_util_make_dummy_planet`:
   After serialize, `if (*roots_len < outtmp.size()) return ZTS_ERR_ARG;` then memcpy and set `*roots_len = outtmp.size()`.
   Document in `ZeroTierSockets.h` that `*roots_len` is in/out.
   → verify: tiny buffer (e.g. 8) returns `ZTS_ERR_ARG`; JNI still works (`len = sizeof(buf)`).

2. **selftest.c**: one function — allocate `ZTS_WORLD_MAX_SERIALIZED_LENGTH`, call helper, assert `rc == ZTS_ERR_OK`, first byte `0x01`, 8-byte BE id equals `ZTS_WORLD_ID_DUMMY`, not Earth. Second call with `len = 8` → `ZTS_ERR_ARG`.
   Wire into whatever target already runs selftest (do not invent a new CI job if none exists — then document `cc`/`cmake` command in Verification). Prefer existing `libzt/test` make/cmake if present.
   → verify: selftest runs on host **or** recorded skip with reason if the tree has no host runner. Android `make verify` still green.

3. **AAR rebuild** `PATH=$ANDROID_HOME/cmake/3.22.1/bin:$PATH ./scripts/build-libzt.sh`. `nm -D` Dummy symbol; `javap` Dummy + `set_roots`.
   → verify: `libzt patch not in APK` checklist.

4. **clang-format-11 `--lines`** on the C function only.

5. **`make verify`**.

### Tests to add

- Native selftest as above.
- No new JVM Dummy fixture tests unless the C error mapping is exposed to Kotlin (it is not).

### Verify commands

- `./scripts/build-libzt.sh`
- host selftest command (discover in-tree)
- `make verify`

### Risks / pitfalls

- **Stale AAR** if rebuild skipped.
- **JNI `len`**: must remain `sizeof(buf)` **before** C call; after success C sets actual size; `NewByteArray` uses that.
- **Do not include World.hpp in JavaSockets.cxx**.

### Out of scope

- T20 / T21
- Regenerating Dummy every launch

### Execute model recommendation

- medium — native + AAR. Small Kotlin.

## Test Plan

- Host: Dummy generate + tiny-buffer ARG
- `make verify` for the Android app (AAR consumed)

## Acceptance Criteria

- [ ] C Dummy honors capacity
- [ ] Native proof of Dummy world id
- [ ] AAR rebuilt and symbols present
- [ ] Tests added/updated for new behavior
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

- T18 product path (MOON store, `orbit(id,0)`, Java `set_roots`, Dummy Kotlin) already ✅.
