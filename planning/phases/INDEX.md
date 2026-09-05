# MVP Phase Index

**Product**: ZerotierB  
**Method**: `/task-1-plan` → `/task-2-execute` → `/task-3-complete`  
**Rule**: Only one task `InProgress` unless the human approves more.  
**INDEX Status**: use `✅` when complete (never the word `Done` in this column).  
**Spec**: `[docs/PROXY-VPN-PLAN.md](../../docs/PROXY-VPN-PLAN.md)`


| ID    | Title                                                                                | Status  | Depends-on | Next  | Layer | Notes                                                      |
| ----- | ------------------------------------------------------------------------------------ | ------- | ---------- | ----- | ----- | ---------------------------------------------------------- |
| T01   | [Verify gate + existing VPN baseline](./T01-verify-gate-vpn-baseline.md)             | ✅       | —          | T02   | L0    |                                                            |
| T02   | [Preferences + Room v3 (modes, pin, links table)](./T02-prefs-room-v3.md)            | ✅       | T01        | T03   | L1    |                                                            |
| T03   | [RuntimePlan resolver (pure)](./T03-runtime-plan-resolver.md)                        | ✅       | T02        | T04   | L3    |                                                            |
| T04   | [Link classifier + debounce](./T04-link-classifier-debounce.md)                      | ✅       | T03        | T05   | L3    |                                                            |
| T05   | [libzt HTTP proxy on 127.0.0.1](./T05-libzt-http-proxy-loopback.md)                  | ✅       | T04        | T06   | L4    |                                                            |
| T06   | [System HTTP_PROXY + Shizuku grant](./T06-system-proxy-shizuku.md)                   | ✅       | T05        | T07   | L5    |                                                            |
| T07   | [VPN single-net + exclusive stack swap](./T07-vpn-single-net-swap.md)                | ✅       | T06        | T08   | L6    |                                                            |
| T08   | [AUTO physical-link observer](./T08-auto-link-observer.md)                           | ✅       | T07        | T09   | L6    |                                                            |
| T09   | [UI: global mode, Links, pin Main](./T09-ui-global-links-pin.md)                     | ✅       | T08        | T11   | L7    |                                                            |
| T11   | [Proxy/VPN unified runtime state](./T11-proxy-vpn-unified-runtime-state.md)          | ✅       | T09        | T11.5 | L7    | UI rewrite phase A — data layer                            |
| T11.5 | [AGP 9 + Compose Expressive toolchain](./T11.5-agp9-compose-expressive-toolchain.md) | ✅       | T11        | T12   | L0    | Approved AGP/Kotlin/compileSdk bump; no product UI         |
| T12   | [Runtime hero card + status chips](./T12-runtime-hero-card-status-chips.md)          | ✅       | T11.5      | T13   | L7    | UI rewrite phase B — hero + chips (Expressive after T11.5) |
| T13   | [Settings bottom sheet sections](./T13-settings-bottom-sheet-sections.md)            | ✅       | T12        | T14   | L7    | UI rewrite phase C — settings UX                           |
| T14   | [Network detail live runtime](./T14-network-detail-live-runtime.md)                  | ✅       | T13        | T15   | L7    | UI rewrite phase D — routes/DNS                            |
| T15   | [UI polish: motion, empty states, copy](./T15-ui-polish-motion-empty-states.md)      | ✅       | T14        | T16   | L7    | UI polish phase E — polish                                |
| T16   | [PROXY OFF heal + abortable node retry](./T16-proxy-off-heal-node-retry.md)          | ✅       | T15        | T17   | L6    | A+C+2+4: disable HTTP_PROXY first; bind-before-NODE_UP; retry |
| T17   | [Roots persistence + world parse](./T17-roots-persistence-world-parse.md)            | ✅       | T16        | T18   | L1    | Room v5 moons; DataStore airgap/planetSource; zt-worlds files |
| T18   | [libzt moon store + set_roots + Dummy planet](./T18-libzt-moon-store-set-roots.md)    | ✅       | T17        | T19   | L4    | pylon submodule; no Topology skip; AAR rebuild               |
| T19   | [Apply roots on PROXY + VPN start](./T19-roots-apply-both-stacks.md)                  | Pending | T18        | T20   | L6    | Stage Earth/Custom/Dummy; orbit moons; identity allowlist    |
| T20   | [Roots settings screen](./T20-roots-settings-screen.md)                              | Pending | T19        | T10   | L7    | Nested Roots from Settings; SAF + id+seed; airgap latch      |
| T10   | [E2E / CoS proof](./T10-e2e-cos-proof.md)                                            | Pending | T20        | —     | L8    | Run after UI rewrite A–E + T16 + Roots T17–T20               |


## Layer legend


| Layer | Meaning                                       |
| ----- | --------------------------------------------- |
| L0    | Skeleton / verify gate (existing VPN app)     |
| L1    | Config / persistence                          |
| L3    | Pure core (plan + classify)                   |
| L4    | Proxy integration (libzt listen)              |
| L5    | System proxy / privileged grant               |
| L6    | Host integration (VPN filter, AUTO callbacks) |
| L7    | Operator UX                                   |
| L8    | E2E proof                                     |


**ID scheme:** `T11.5` is a decimal insert between T11 and T12 (dot in INDEX id and filename). Do not flatten to `T115`. Chain: T09 → T11 → T11.5 → T12 → T13 → T14 → T15 → T16 → T17 → T18 → T19 → T20 → T10.

## How to work

1. `/task-1-plan T01`
2. `/task-2-execute T01`
3. `/task-3-complete T01` → commit local (add `--push` to push) + Manual test → next stub-stem branch
4. Repeat

