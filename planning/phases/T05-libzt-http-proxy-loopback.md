# T05 — libzt HTTP proxy on 127.0.0.1

**Status**: Pending  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T04  
**Next**: T06  
**Layer**: L4

## Description

Restore archive proxy server + RouteResolver (no blockOutside). Bind 127.0.0.1:0. Do not write Settings.Global yet. Mutex: refuse start if VPN node live.

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| 2026-08-23 | created | — | Pending | stub seeded by bootstrap | |

## Requirements

- [ ] Port HttpProxyServer + RouteResolver from archive/proxy-mode
- [ ] Loopback-only bind; show port in logs/state
- [ ] Phase-2 spike: sequential JNI stop then libzt start in one process

## Implementation Plan

*(Filled by `/task-1-plan` — do not invent during bootstrap beyond high-level notes.)*

### High-level notes (bootstrap)

- libzt.mdc
- If .so clash: document; isolated process last resort
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

- [ ] Termux curl --proxy 127.0.0.1:PORT reaches a ZT HTTP service when proxy mode forced in debug
- [ ] LAN IP:PORT refused
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

### From T04 close-out

- Classifier/debounce land in `connection/` (`LinkClassifier`, `LinkDebouncer`, `PhysicalLinkSelector`). Callback shell: `system/LinkNetworkCallback` — **not registered** until T08.
- Strip our VPN via scan (`PhysicalLinkSelector`); do not call missing public `getUnderlyingNetworks`.
- `ZerotierBVpnService` sets `setUnderlyingNetworks` on establish; `state.isRunning` for “ours”.
- Exclusive stack mutex still required for libzt vs JNI (T05 spike / T07).
