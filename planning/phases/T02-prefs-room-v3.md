# T02 — Preferences + Room v3 (modes, pin, links table)

**Status**: Pending  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T01  
**Next**: T03  
**Layer**: L1

## Description

Persist globalMode, debounce, last proxy string; migrate Room to v3: createdAt, isPinnedMain on ZerotierBNetwork; new link_profiles. No stack swap yet.

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| 2026-08-23 | created | — | Pending | stub seeded by bootstrap | |

## Requirements

- [ ] DataStore keys per PROXY-VPN-PLAN §10.1
- [ ] MIGRATION_2_3, stop relying on destructive fallback for this bump
- [ ] Seed Other + upsert mobile rows when subscriptions observed (can be stub observer)

## Implementation Plan

*(Filled by `/task-1-plan` — do not invent during bootstrap beyond high-level notes.)*

### High-level notes (bootstrap)

- Spokes: room.mdc, kotlin.mdc
- Migrate startOnBoot==true → globalMode=VPN
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

- [ ] App upgrades from v2 DB without wiping networks
- [ ] Unit tests for pin-main transaction and createdAt sort
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

- **Upstream T01:** Compose BOM is pinned to `2024.12.01` (was `2026.06.00`) so Android Lint works with AGP 8.7.3 / Kotlin 2.0.21. Do not bump Compose BOM casually in T02.
- Unit tests need `android.testOptions.unitTests.isReturnDefaultValues = true` (already set).
- `make verify` is the gate; `ANDROID_HOME` defaults to `/opt/android-sdk` in Makefile.
- `/task-3-complete` defaults to **no push** in this repo (`--push` to opt in).
