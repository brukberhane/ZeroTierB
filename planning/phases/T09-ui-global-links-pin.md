# T09 — UI: global mode, Links, pin Main

**Status**: Pending  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T08  
**Next**: T10  
**Layer**: L7

## Description

Segmented OFF|PROXY|VPN|AUTO. Current link line. Pin Main chip. Links screen. Grant card. Route MainViewModel through orchestrator (stop calling VpnService.start directly).

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| 2026-08-23 | created | — | Pending | stub seeded by bootstrap | |

## Requirements

- [ ] No per-ZT PROXY/VPN chips
- [ ] Show 127.0.0.1:port or not granted
- [ ] vpnConsentMissing banner

## Implementation Plan

*(Filled by `/task-1-plan` — do not invent during bootstrap beyond high-level notes.)*

### High-level notes (bootstrap)

- compose.mdc
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

- [ ] Manual UI walk documented
- [ ] BootReceiver uses orchestrator.refresh()
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

- Grant card must call `Shizuku.requestPermission` (or equivalent) before `ShizukuPermissionHelper.grantWriteSecureSettings` — helper alone fails if app not authorized in Shizuku.
- Show `SystemProxyManager.adbGrantCommand(packageName)` as copyable fallback; display `ProxyServiceState.systemProxyActive` and `hasSecureSettingsPermission`.
