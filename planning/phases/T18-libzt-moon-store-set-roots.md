# T18 — libzt moon store + set_roots + Dummy planet

**Status**: Pending  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T17  
**Next**: T19  
**Layer**: L4

## Description

Restore libzt behavior the C API already documents: persist moons, allow `orbit(id, 0)` when a `.moon` file exists, and actually call `zts_init_set_roots` from Java. Generate **once** a Dummy TYPE_PLANET (no public endpoints, world id ≠ Earth `149604618`) via existing `zts_util_sign_root_set` while **no** stack is running; save under `zt-worlds/dummy.planet`. **Do not** change `Topology` ctor (no skip-Earth flag). **Do not** rename libzt cache `roots` → `planet`. Rebuild and link `libzt-release.aar`.

Ships on submodule **`brukberhane/libzt` branch `pylon`**. Other machines: `git submodule update --init --recursive` then `./scripts/build-libzt.sh` (AAR is gitignored).

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| 2026-09-05 | created | — | Pending | /setup-tasks Roots feature | |

## Requirements

- [ ] `NodeService` put/get/delete `ZT_STATE_OBJECT_MOON` → `<home>/moons.d/<16hex>.moon` (same layout as JNI / `ZeroTierOne.h`)
- [ ] `NodeService::orbit`: allow seed `0` (core `Topology::addMoon` already loads from store)
- [ ] Java: `ZeroTierNative.zts_init_set_roots(byte[])`; JNI must call C (today’s stub returns OK and takes `void*` — unusable). Before `zts_node_start` only.
- [ ] Wire `zts_util_sign_root_set` (or equivalent) from Java if Dummy generation needs it. Generate Dummy **once**, persist `zt-worlds/dummy.planet` only — **not** identity files. No Dummy signing keys in `identity.secret`.
- [ ] **No** `Topology.cpp` skip of `ZT_DEFAULT_WORLD`. Dummy + different world id is how Earth is not used.
- [ ] Keep libzt planet cache filename **`roots`**. App restages from `zt-worlds/` in T19.
- [ ] Commit native diffs on `libzt` `pylon`; bump parent submodule SHA. Rebuild AAR. `make verify` (assembleDebug links new `.so`).
- [ ] clang-format-11 **line-range only** on libzt edits. Wipe `libzt/pkg/android/app/.cxx` + `libzt/cache/android-*` if new `.cpp` added.

## Non-goals (this task)

- Kotlin orbit-on-start / identity staging (T19)
- Roots UI (T20)
- JNI `Node.orbit` already exists — do not add a parallel JNI C API
- Auto-scan `moons.d` like desktop OneService (Room remains source of truth)

## Constraints

- `.cursor/rules/libzt.mdc` — exclusive node; NODE_UP not NODE_ONLINE; AAR rebuild required
- Identity files never touched
- One live ZeroTier node invariant unchanged
- NDK 28.2 for libzt AAR; x86_64 host (`scripts/build-libzt.sh`)

## References

- `libzt/src/NodeService.cpp` — PLANET → `roots`; `orbit` rejects `!moon_seed`; MOON falls through `default`
- `libzt/src/bindings/java/JavaSockets.cxx` ~551–555 stub `zts_1init_1set_roots`
- `libzt/src/bindings/java/com/zerotier/sockets/ZeroTierNative.java` ~430 commented native
- `libzt/include/ZeroTierSockets.h` `zts_init_set_roots`, `zts_moon_orbit`, `zts_util_sign_root_set`
- `libzt/ext/ZeroTierOne/java/jni/com_zerotierone_sdk_Node.cpp` JNI moon path (do not copy-paste drive-by)
- `.gitmodules` → `https://github.com/brukberhane/libzt.git` `pylon`

## Implementation Plan

*(Filled by `/task-1-plan`)*

### High-level notes (setup-tasks)

1. Moon store cases next to PLANET in NodeService put/get/delete.
2. Drop seed==0 reject in `orbit`.
3. Fix Java+JNI `set_roots`; optionally sign util for Dummy.
4. Kotlin helper to generate Dummy when both stacks stopped (or first airgap enable in T19 if cleaner — prefer generate API here, call from T19).
5. `./scripts/build-libzt.sh`; parent submodule pointer.

## Execution plan (filled by /task-1-plan)

**Date:**  
**Codebase snapshot:**  
**Execute model:** small/default | large (only if justified)

### Context for executor
- …

### Steps
1. … → verify: …

### Tests to add
- …

### Verify commands
- `make verify` after AAR rebuild
