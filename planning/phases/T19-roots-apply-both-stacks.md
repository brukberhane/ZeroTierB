# T19 — Apply roots on PROXY + VPN start

**Status**: Pending  
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

## Requirements

- [ ] **Live planet source:** airgap on → Dummy; else `planetSource=custom` and blob present → Custom; else Earth.
- [ ] **Earth:** do not `set_roots`. **Delete** live `planet` and `roots` only. Baked Earth can load. Never leave Dummy/Custom in identity dir.
- [ ] **Custom / Dummy:** JNI write `filesDir/planet` **before** `Node.init`. PROXY `zts_init_set_roots` **before** `zts_node_start`. Same Dummy bytes both stacks.
- [ ] After node UP / JNI init: orbit each Room moon (`orbit(id, 0)` if `hasMoonFile` after copying `.moon` into `moons.d`; else `orbit(id, seed)`). Deorbit worldIds not in Room.
- [ ] **Allowlist:** writes/deletes only `planet`, `roots`, `moons.d/*`. Never `identity.*` or `networks.d`. Tests on the allowlist function.
- [ ] Changing moons / airgap / planetSource while PROXY or VPN running → orchestrator restart current runtime. Stop timeout still **aborts** swap (existing invariant).
- [ ] Ready gate stays **NODE_UP**. Do not wait NODE_ONLINE. Airgap + no upstream → lifecycle **Starting**; copy “waiting for roots/moons (LAN ok)”. **Do not fake Online.** Device-check: if Online never fires with working Dummy+moon, keep Starting.
- [ ] Last-moon-removed + latch off + airgap on → force airgap **off** + restage Earth (snack in T20; state flip here or T20 — prefer prefs write here so apply is correct even without UI).
- [ ] `make verify`.

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

## Implementation Plan

*(Filled by `/task-1-plan`)*

### High-level notes (setup-tasks)

1. `RootsApplier` (or similar) computes Earth/Custom/Dummy + moon list; stages files; called from proxy start and VPN start **before** node init when planet blob required.
2. PROXY: `set_roots` then start; then orbit. VPN: write `planet` then `Node.init`; then orbit.
3. Earth: delete `planet`+`roots` before init.
4. Orchestrator: roots config change → re-apply current plan (restart stack).

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
- `make verify`
