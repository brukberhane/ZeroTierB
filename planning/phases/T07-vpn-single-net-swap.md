# T07 — VPN single-net + exclusive stack swap

**Status**: Pending  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T06  
**Next**: T08  
**Layer**: L6

## Description

rebuildVpn() only main net; leave() others. Orchestrator swap PROXY↔VPN: disable Global, stop libzt, wait, start JNI (and reverse). Consent-missing path.

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| 2026-08-23 | created | — | Pending | stub seeded by bootstrap | |

## Requirements

- [ ] ZerotierBVpnService honors allowedVpnNetworkIds
- [ ] Stop-complete callback for orchestrator
- [ ] No background VpnService.prepare

## Implementation Plan

*(Filled by `/task-1-plan` — do not invent during bootstrap beyond high-level notes.)*

### High-level notes (bootstrap)

- android-vpn.mdc
- zerotier-jni.mdc
- connection-orchestrator.mdc
- Spec: `docs/PROXY-VPN-PLAN.md`

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

### Risks / pitfalls
- …

### Out of scope
- …

### Execute model recommendation
- default (small/cheap) | large — rationale: …

## Test Plan

- Prove this layer without later layers.
- Commands: `make verify` (lint + unit tests + assembleDebug)
- New Kotlin: tests required

## Acceptance Criteria

- [ ] Two enabled ZT nets + VPN runtime → only main on TUN
- [ ] Swap does not leak HTTP_PROXY
- [ ] make verify green
- [ ] Tests added/updated for new behavior
- [ ] Full lint + test verify suite green
- [ ] Verification commands recorded and passing
- [ ] No secrets committed

## Verification

*(Filled by `/task-2-execute`; re-confirmed by `/task-3-complete`)*

## Files Modified

*(Filled by `/task-2-execute`)*

## Manual test (for humans)

*(Filled by `/task-3-complete`)*

## Learnings

*(Filled by `/task-3-complete` / dialectic)*

## Reality notes

*(Amended by upstream `/task-3-complete` if prior tasks changed assumptions)*

### From T06 close-out

- `com.brukb.zerotier.proxy.SystemProxyManager` — sole writer of `Settings.Global.HTTP_PROXY`; `enable(port)` / `disable()` / `hasPermission()` / `shouldClearStale(...)`.
- `ProxyModeService` calls `enable(boundPort)` after listen; `disable()` first on stop. State: `systemProxyActive`, `hasSecureSettingsPermission`.
- Shizuku 13.1.5 (`api` + `provider`); `ShizukuProvider` in manifest; grant via `ShizukuPermissionHelper.grantWriteSecureSettings` or ADB `pm grant`.
- Stale-proxy clear on `ZerotierBApplication.onCreate` when mode ≠ PROXY — T07 orchestrator must call `disable()` on PROXY→VPN swap before stopping libzt (AC: "Swap does not leak HTTP_PROXY").
- `GlobalMode.PROXY` not settable from UI yet (T09) — orchestrator in T07 should drive enable/disable from resolved runtime plan, not raw debug intents.
- VPN mutex unchanged: proxy refuses when `ZerotierBVpnService.state.isRunning` unless `EXTRA_FORCE_DEBUG`.
