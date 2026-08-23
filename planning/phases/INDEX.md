# MVP Phase Index

**Product**: ZerotierB  
**Method**: `/task-1-plan` → `/task-2-execute` → `/task-3-complete`  
**Rule**: Only one task `InProgress` unless the human approves more.  
**INDEX Status**: use `✅` when complete (never the word `Done` in this column).  
**Spec**: [`docs/PROXY-VPN-PLAN.md`](../../docs/PROXY-VPN-PLAN.md)

| ID | Title | Status | Depends-on | Next | Layer | Notes |
| -- | ----- | ------ | ---------- | ---- | ----- | ----- |
| T01 | [Verify gate + existing VPN baseline](./T01-verify-gate-vpn-baseline.md) | ✅ | — | T02 | L0 | |
| T02 | [Preferences + Room v3 (modes, pin, links table)](./T02-prefs-room-v3.md) | ✅ | T01 | T03 | L1 | |
| T03 | [RuntimePlan resolver (pure)](./T03-runtime-plan-resolver.md) | ✅ | T02 | T04 | L3 | |
| T04 | [Link classifier + debounce](./T04-link-classifier-debounce.md) | ✅ | T03 | T05 | L3 | |
| T05 | [libzt HTTP proxy on 127.0.0.1](./T05-libzt-http-proxy-loopback.md) | Pending | T04 | T06 | L4 | |
| T06 | [System HTTP_PROXY + Shizuku grant](./T06-system-proxy-shizuku.md) | Pending | T05 | T07 | L5 | |
| T07 | [VPN single-net + exclusive stack swap](./T07-vpn-single-net-swap.md) | Pending | T06 | T08 | L6 | |
| T08 | [AUTO physical-link observer](./T08-auto-link-observer.md) | Pending | T07 | T09 | L6 | |
| T09 | [UI: global mode, Links, pin Main](./T09-ui-global-links-pin.md) | Pending | T08 | T10 | L7 | |
| T10 | [E2E / CoS proof](./T10-e2e-cos-proof.md) | Pending | T09 | — | L8 | |

## Layer legend

| Layer | Meaning |
| ----- | ------- |
| L0 | Skeleton / verify gate (existing VPN app) |
| L1 | Config / persistence |
| L3 | Pure core (plan + classify) |
| L4 | Proxy integration (libzt listen) |
| L5 | System proxy / privileged grant |
| L6 | Host integration (VPN filter, AUTO callbacks) |
| L7 | Operator UX |
| L8 | E2E proof |

## How to work

1. `/task-1-plan T01`
2. `/task-2-execute T01`
3. `/task-3-complete T01` → commit local (add `--push` to push) + Manual test → next stub-stem branch
4. Repeat
