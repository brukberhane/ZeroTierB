# T03 — RuntimePlan resolver (pure)

**Status**: Pending  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T02  
**Next**: T04  
**Layer**: L3

## Description

Pure function: globalMode × classified link × vpnConsent × enabled nets → RuntimePlan. JVM tests, no Android framework.

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| 2026-08-23 | created | — | Pending | stub seeded by bootstrap | |

## Requirements

- [ ] Table tests for OFF/PROXY/VPN/AUTO × WifiKnown/Unknown/Mobile/Other/None × consent
- [ ] VPN plan uses main = pin else oldest createdAt
- [ ] Consent missing: PROXY if possible else OFF + vpnConsentMissing

## Implementation Plan

*(Filled by `/task-1-plan` — do not invent during bootstrap beyond high-level notes.)*

### High-level notes (bootstrap)

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

- [ ] Resolver has no Context/Network types
- [ ] All matrix cases in PROXY-VPN-PLAN §9.1 covered by tests
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

### From T02 close-out

- `GlobalMode` / `LinkKind` / `LinkMode` in `data/model/Modes.kt`. Main selection: `MainNetworkSelector.select(enabled)`.
- DataStore: `global_mode`, `saved_http_proxy`, `last_http_proxy_port`, `link_debounce_ms` (clamp 3–15s). Absent `global_mode` migrates from `start_on_boot` → VPN.
- Room v3: `createdAt`, `isPinnedMain`, `link_profiles` + `LinkProfileRepository.upsertMobile` stub.
- Reuse `MainNetworkSelector` in RuntimePlan — do not reimplement pin/createdAt sort.
