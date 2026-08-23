# T06 — System HTTP_PROXY + Shizuku grant

**Status**: Pending  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T05  
**Next**: T07  
**Layer**: L5

## Description

SystemProxyManager: save/restore Global HTTP_PROXY. Enable only after bind. Shizuku pm grant WRITE_SECURE_SETTINGS + ADB copy. Crash/start clears stale proxy if not PROXY runtime.

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| 2026-08-23 | created | — | Pending | stub seeded by bootstrap | |

## Requirements

- [ ] enable after listen; disable restores
- [ ] No APN writes
- [ ] UI honesty: system proxy inactive without permission

## Implementation Plan

*(Filled by `/task-1-plan` — do not invent during bootstrap beyond high-level notes.)*

### High-level notes (bootstrap)

- android-http-proxy.mdc
- shizuku.mdc
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

- [ ] Wi-Fi browser uses ZT HTTP via Global when granted
- [ ] Force-stop then relaunch does not leave dead proxy if mode≠PROXY
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

### From T05 close-out

- `com.brukb.zerotier.proxy.ProxyModeService` — start via `ProxyModeService.start(context)` or intent `ACTION_START`; no orchestrator/UI yet.
- HTTP proxy listens `127.0.0.1:0`; actual port in `ProxyServiceState.httpProxyPort` and `AppPreferences.lastHttpProxyPort`.
- T06 `SystemProxyManager` must bind-listen first (already done in T05 service), then write Global — read port from service state or prefs.
- No `Settings.Global` write in T05; Shizuku not added yet.
- VPN mutex: proxy refuses when VPN running unless `EXTRA_FORCE_DEBUG`.
