# T14 — Network detail: live addresses, routes, DNS (Phase D)

**Status**: Pending  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T13  
**Next**: T15  
**Layer**: L7

## Description

Upgrade `NetworkDetailScreen` from static Room fields to a **live runtime panel** for the selected network: join status chip (T12), assigned ZT addresses, managed routes, DNS servers — sourced from T11 unified runtime when the active stack has joined that network. Room-backed fields (name, enable, pin Main, network ID) remain editable.

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| 2026-08-31 | created | — | Pending | /setup-tasks UI rewrite phase D | |

## Requirements

- [ ] **Header**: network name + short ID; join status chip (reuse T12 chip component)
- [ ] **Runtime section** (visible when `network.enabled` and active runtime has data for this `networkId`):
  - Assigned addresses (list, monospace)
  - Managed routes: prefix → via (if present) — format like VPN logs / `RouteResolver`
  - DNS servers list
  - Last updated: implicit via state flow (no manual refresh button unless stale detection needed)
- [ ] When runtime OFF or network not joined: empty state copy ("Not connected — enable network and start PROXY or VPN")
- [ ] When wrong runtime (e.g. VPN up but viewing proxy-only net not in VPN plan): honest message, not stale VPN data from another net
- [ ] **Edit section** unchanged scope: enable toggle, pin Main, delete network — save still via `NetworkRepository` + `orchestrator.refresh()`
- [ ] Data plumbing:
  - Extend T11 `NetworkRuntimeStatus` if missing `routes` / `dns` / `addresses` lists
  - Proxy: pull from `ZtNetworkStatus` / `nodeManager` assigned routes (mirror VPN publish path)
  - VPN: reuse existing `VpnServiceState.networkStatuses` fields
- [ ] Scrollable column — detail can be long on busy controllers

## Non-goals (this task)

- Edit routes or DNS (read-only display)
- Join/leave buttons duplicating home row (enable switch enough)
- Controller API / web UI
- Traffic stats or peer list

## Constraints

- Read `.cursor/rules/zerotier-jni.mdc`, `.cursor/rules/libzt.mdc`, `.cursor/rules/connection-orchestrator.mdc`
- Route display policy should match `RouteResolver` / managed-route allow rules (don't show rejected routes as active)
- No new Gradle deps

## References

- `app/src/main/java/com/brukb/zerotier/ui/NetworkDetailScreen.kt`
- `app/src/main/java/com/brukb/zerotier/vpn/ZerotierBVpnService.kt` — `NetworkRuntimeStatus` shape
- `app/src/main/java/com/brukb/zerotier/proxy/ZeroTierNodeManager.kt` / `ZtModels.kt`
- `app/src/main/java/com/brukb/zerotier/routing/RouteResolver.kt`

## Implementation Plan

*(Filled by `/task-1-plan`)*

### High-level notes (setup-tasks)

1. Ensure `NetworkRuntimeStatus` includes `assignedAddresses`, `managedRoutes`, `dnsServers`.
2. ProxyModeService: populate on network config callback / status poll.
3. NetworkDetailScreen: collect `viewModel.networkRuntime(networkId)` + Room entity.
4. Subcomposables: `RuntimeAddressesSection`, `RuntimeRoutesSection`, `RuntimeDnsSection`.

## Execution plan (filled by /task-1-plan)

*(empty)*

## Test Plan

- Unit tests: route line formatting helper (prefix, via, no via)
- `make verify`
- Manual on device with managed routes + DNS from controller

## Acceptance Criteria

- [ ] Joined network in PROXY shows addresses/routes/DNS on detail screen
- [ ] Same network in VPN mode shows VPN-sourced data
- [ ] Disabled network shows empty runtime section, not crash
- [ ] Pin Main + save still works; orchestrator refresh after save
- [ ] `make verify` green

## Verification

*(Filled by `/task-3-complete`)*

## Manual test (for humans)

1. PROXY, join net with routes → open detail → see routes list
2. Switch VPN (main net) → detail updates to VPN data
3. Disable network → "Not connected" empty state

## Learnings

*(Filled on close-out)*
