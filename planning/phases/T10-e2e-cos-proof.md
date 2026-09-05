# T10 — E2E / CoS proof

**Status**: Pending  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T22  
**Next**: —  
**Layer**: L8

## Description

Holistic verification of docs/PROXY-VPN-PLAN.md test matrix. Record what OEM honors Global on LTE. Tighten README. No new features. After T22, include Roots: moons on PROXY+VPN, Earth vs Custom vs Dummy (airgap), identity files untouched, LAN/moon path without public Earth.

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| 2026-08-23 | created | — | Pending | stub seeded by bootstrap | |

## Requirements

- [ ] Walk matrix §15.2; file gaps
- [ ] make verify green
- [ ] README status matches shipped behavior

## Implementation Plan

*(Filled by `/task-1-plan` — do not invent during bootstrap beyond high-level notes.)*

### High-level notes (bootstrap)

- Do not add SOCKS unless already done; SOCKS is optional follow-up
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

- [ ] Written CoS notes in this task file
- [ ] INDEX can go ✅ for T10
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

### From T09 close-out

- UI shipped: segmented global mode, Links overlay, grant card, pin Main, dark+dynamic theme. ViewModel routes through orchestrator only.
- `PhysicalLink.WifiUnsaved` — readable SSID without profile; Save SSID in Links.
- Consent banner uses `requestVpnConsent()` (no global VPN flip). Segmented VPN chip uses `requestVpnAndStart()`.
- Compose BOM is `compose-bom-alpha:2026.08.01` (material3 1.5.0-alpha27, Expressive available). Device E2E matrix is T10. Do not bump AGP/BOM in T10.

### From T15 close-out (UI rewrite A–E complete)

- UI rewrite phases **A–E done** (T11 → T15). T10 is device E2E / CoS only — no new product UI.
- `CopyableMonoText` — shared copy widget (hero node ID, detail network ID, settings node ID, ADB grant).
- Home: single `verticalScroll` surface; empty-networks card; `SnackbarHost` on main `Scaffold`.
- Detail/settings: local snackbar hosts (overlay windows).
- Manual walk: hero/chips → settings → detail runtime → empty/copy/scroll (see T15 Manual test).
