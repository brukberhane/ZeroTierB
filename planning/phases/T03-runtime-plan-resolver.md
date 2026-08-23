# T03 — RuntimePlan resolver (pure)

**Status**: Done  
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
| 2026-08-23 | planned | Pending | Planned | /task-1-plan: pure resolver, PhysicalLink sealed, 17-case matrix | |
| 2026-08-23 | execute | Planned | InProgress | /task-2-execute T03 | |
| 2026-08-23 | complete | InProgress | Done | /task-3-complete T03 (no push) | |

## Requirements

- [x] Table tests for OFF/PROXY/VPN/AUTO × WifiKnown/Unknown/Mobile/Other/None × consent
- [x] VPN plan uses main = pin else oldest createdAt
- [x] Consent missing: PROXY if possible else OFF + vpnConsentMissing

## Implementation Plan

*(Filled by `/task-1-plan` — do not invent during bootstrap beyond high-level notes.)*

### High-level notes (bootstrap)

- connection-orchestrator.mdc
- Spec: `docs/PROXY-VPN-PLAN.md`

## Execution plan (filled by /task-1-plan)

**Date:** 2026-08-23  
**Codebase snapshot:** branch `T03-runtime-plan-resolver` @ T02 `df29601`. `Modes.kt` has `GlobalMode` / `LinkKind` / `LinkMode`. `MainNetworkSelector.select(enabled)` exists (pin → oldest createdAt → zeros last → networkId). No `connection/` package yet.  
**Execute model:** small (default)

### Context for executor

**Goal:** Pure resolver: `(globalMode, physicalLink, vpnConsentGranted, enabledNets) → RuntimePlan`. No Android types (`Context`, `Network`, `ConnectivityManager`). No Room/DataStore reads — caller passes values. No stack swap, no service starts (T07). No classifier (T04) — link arrives **already classified** with its `LinkMode` resolved from profiles.

**New package:** `app/src/main/java/com/brukb/zerotier/connection/` (matches `connection-orchestrator.mdc` globs).

**Key existing files (reuse, do not modify):**
- `app/src/main/java/com/brukb/zerotier/data/model/Modes.kt` — `GlobalMode`, `LinkMode`
- `app/src/main/java/com/brukb/zerotier/data/model/MainNetworkSelector.kt` — main selection
- `app/src/main/java/com/brukb/zerotier/data/model/ZerotierBNetwork.kt`

**Spec:** `docs/PROXY-VPN-PLAN.md` §4.1 (mode semantics), §4.3 (RuntimePlan shape), §9.1 (resolve pseudocode + consent fallback).

**Invariants (connection-orchestrator.mdc):**
- PROXY joins every enabled net; VPN joins **only** main
- AUTO + no link → runtime OFF; global PROXY/VPN ignore link entirely
- Consent missing → PROXY if enabled nets nonempty else OFF, `vpnConsentMissing = true`; never background-prompt
- `vpnNetworkId` set **iff** `runtime == VPN` (null on consent fallback)

### Steps

1. **`connection/PhysicalLink.kt`** — sealed interface, already-classified link with resolved mode:
   ```kotlin
   sealed interface PhysicalLink {
       data class WifiKnown(val ssid: String, val mode: LinkMode) : PhysicalLink
       data object WifiUnknown : PhysicalLink
       data class Mobile(val subscriptionId: Int, val mode: LinkMode) : PhysicalLink
       data class Other(val mode: LinkMode) : PhysicalLink
       data object None : PhysicalLink
   }
   ```
   → verify: compiles via `:app:compileDebugKotlin`.

2. **`connection/RuntimePlan.kt`** — spec §4.3 shape + runtime enum:
   ```kotlin
   enum class Runtime { OFF, PROXY, VPN }
   data class RuntimePlan(
       val runtime: Runtime,
       val reason: String,
       val vpnNetworkId: String?,
       val joinNetworkIds: List<String>,
       val vpnConsentMissing: Boolean,
   )
   ```
   → verify: compiles.

3. **`connection/RuntimePlanResolver.kt`** — `object RuntimePlanResolver`:
   ```kotlin
   fun resolve(
       globalMode: GlobalMode,
       link: PhysicalLink,
       vpnConsentGranted: Boolean,
       enabled: List<ZerotierBNetwork>,
   ): RuntimePlan
   ```
   Logic (exactly spec §9.1):
   - `GlobalMode.OFF` → `RuntimePlan(OFF, "global OFF", null, emptyList(), false)`
   - `GlobalMode.PROXY` → proxy plan, reason `"global PROXY"` — runtime PROXY **even when `enabled` is empty** (user asked; ZT sits idle), joins = all enabled ids
   - `GlobalMode.VPN` → `vpnPlan("global VPN")`
   - `GlobalMode.AUTO` → map link to mode + reason:
     - `None` → OFF plan, reason `"AUTO no link"`
     - `WifiUnknown` → proxy plan, reason `"AUTO unknown wifi"`
     - `WifiKnown(ssid, mode)` → `planFor(mode, "AUTO ssid=$ssid")`
     - `Mobile(subId, mode)` → `planFor(mode, "AUTO mobile sub=$subId")`
     - `Other(mode)` → `planFor(mode, "AUTO other")`
   - Private `planFor(mode: LinkMode, reason: String)`:
     - `OFF` → `RuntimePlan(OFF, reason, null, emptyList(), false)`
     - `PROXY` → `RuntimePlan(PROXY, reason, null, enabled.map { it.networkId }, false)`
     - `VPN` → `vpnPlan(reason)`
   - Private `vpnPlan(reason: String)`:
     - `main = MainNetworkSelector.select(enabled)`; if `null` → `RuntimePlan(OFF, "$reason (no enabled networks)", null, emptyList(), false)` — consent flag **false** (nothing wanted VPN on a net)
     - if `!vpnConsentGranted` → `RuntimePlan(PROXY, "$reason (consent missing)", null, enabled.map { it.networkId }, vpnConsentMissing = true)` — enabled is nonempty here (main exists), so fallback is always PROXY; still write the `else OFF` branch defensively
     - else → `RuntimePlan(VPN, reason, main.networkId, listOf(main.networkId), false)`
   → verify: `RuntimePlanResolverTest` green (below).

4. **Tests** — `app/src/test/java/com/brukb/zerotier/connection/RuntimePlanResolverTest.kt`, JUnit 4, no Robolectric. Helper: `fun net(id: String, createdAt: Long = 0, pinned: Boolean = false)` (pad id to 16 like T02 test). Cover the matrix table below.  
   → verify: `./gradlew :app:testDebugUnitTest --console=plain` shows new tests pass + 32 existing still pass.

5. **`make verify`** → lint + tests + assembleDebug green. Record in Verification.

### Tests to add

| # | global | link | consent | enabled | expect |
|---|--------|------|---------|---------|--------|
| 1 | OFF | WifiKnown(VPN) | yes | 2 | OFF, joins empty, flag false |
| 2 | PROXY | None | no | 2 | PROXY, joins both, vpnNetworkId null |
| 3 | PROXY | Mobile(PROXY) | no | 0 | PROXY, joins empty (runs idle) |
| 4 | VPN | Other(OFF) | yes | 2 (one pinned) | VPN, vpnNetworkId = pinned, joins = [pinned] |
| 5 | VPN | WifiUnknown | yes | 2 (createdAt 100, 50) | VPN, main = createdAt 50 |
| 6 | VPN | None | **no** | 2 | PROXY, joins both, flag **true**, vpnNetworkId null |
| 7 | VPN | None | no | 0 | OFF, flag **true** |
| 8 | VPN | None | yes | 0 | OFF, flag **false**, reason mentions no enabled |
| 9 | AUTO | None | yes | 2 | OFF |
| 10 | AUTO | WifiUnknown | no | 1 | PROXY |
| 11 | AUTO | WifiKnown("Home", OFF) | yes | 2 | OFF |
| 12 | AUTO | WifiKnown("Home", VPN) | yes | 2 (pinned B) | VPN on B, reason contains `ssid=Home` |
| 13 | AUTO | WifiKnown("Home", VPN) | no | 1 | PROXY, flag true |
| 14 | AUTO | Mobile(2, PROXY) | no | 1 | PROXY, reason contains `sub=2` |
| 15 | AUTO | Mobile(2, VPN) | yes | 1 | VPN |
| 16 | AUTO | Other(VPN) | no | 1 | PROXY, flag true |
| 17 | AUTO | Other(OFF) | yes | 1 | OFF |

Also assert `reason` non-blank on every case (loop or per-case).

### Verify commands

- `./gradlew :app:testDebugUnitTest --console=plain`
- `make verify`

### Risks / pitfalls

- **Do not** reimplement pin/createdAt sort — call `MainNetworkSelector.select`.
- **Do not** take `Context` / `VpnService.prepare` — consent arrives as `Boolean` (kotlin.mdc: resolver testable without framework).
- Consent fallback: `vpnNetworkId` must be **null** when runtime fell back to PROXY (spec §4.3: set iff runtime == VPN).
- Global PROXY/VPN must **not** look at `link` at all (spec §4.1).
- VPN with zero enabled nets → OFF with flag **false** (consent irrelevant; nothing to protect).
- Keep `Runtime` name despite clash risk with `java.lang.Runtime` — different package; tests import `com.brukb.zerotier.connection.Runtime`. If lint/compile complains, rename to `RuntimeKind` and note it.
- No new dependencies. No Room/DataStore changes. No UI.

### Out of scope

- ConnectionOrchestrator class / mutex / service start-stop (T07)
- Link classifier, SSID normalize, NetworkCallback, debounce (T04/T08)
- libzt, HTTP proxy, Shizuku, `HTTP_PROXY` (T05/T06)
- VPN single-net TUN filter (T07)
- UI (T09)

### Execute model recommendation

- **small** — one sealed type + one pure function + table tests; plan is file-level complete.

## Test Plan

- Prove this layer without later layers.
- Commands: `make verify` (lint + unit tests + assembleDebug)
- New Kotlin: tests required

## Acceptance Criteria

- [x] Resolver has no Context/Network types
- [x] All matrix cases in PROXY-VPN-PLAN §9.1 covered by tests
- [x] Tests added/updated for new behavior
- [x] Full lint + test verify suite green
- [x] Verification commands recorded and passing
- [x] No secrets committed

## Verification

```text
2026-08-23 /task-2-execute T03
Presence: Makefile verify + lefthook.yml + app/lint.xml OK
make verify → PASS
  :app:lintDebug PASS
  :app:testDebugUnitTest PASS (+ RuntimePlanResolverTest 17 cases)
  :app:assembleDebug PASS

2026-08-23 /task-3-complete T03 (re-verify)
Presence OK; make verify → PASS
```

## Files Modified

- `app/src/main/java/com/brukb/zerotier/connection/PhysicalLink.kt` (new)
- `app/src/main/java/com/brukb/zerotier/connection/RuntimePlan.kt` (new)
- `app/src/main/java/com/brukb/zerotier/connection/RuntimePlanResolver.kt` (new)
- `app/src/test/java/com/brukb/zerotier/connection/RuntimePlanResolverTest.kt` (new)
- `.cursor/rules/connection-orchestrator.mdc` (dialectic: consent / vpnNetworkId / pure inputs)
- `planning/phases/INDEX.md` / `T03` / `T04` reality notes

## Manual test (for humans)

```text
Nothing to test — pure JVM resolver; no UI/service wiring until T07/T08.
Unit coverage: ./gradlew :app:testDebugUnitTest --tests '*RuntimePlanResolverTest'
```

## Learnings

- Mode A: no debugging triggers.
- Mode B: encoded consent fallback + `vpnNetworkId` iff VPN; resolve stays Context-free.
- Downstream: classifier (T04) must emit `PhysicalLink` for resolver.

## Reality notes

*(Amended by upstream `/task-3-complete` if prior tasks changed assumptions)*

### From T02 close-out

- `GlobalMode` / `LinkKind` / `LinkMode` in `data/model/Modes.kt`. Main selection: `MainNetworkSelector.select(enabled)`.
- DataStore: `global_mode`, `saved_http_proxy`, `last_http_proxy_port`, `link_debounce_ms` (clamp 3–15s). Absent `global_mode` migrates from `start_on_boot` → VPN.
- Room v3: `createdAt`, `isPinnedMain`, `link_profiles` + `LinkProfileRepository.upsertMobile` stub.
- Reuse `MainNetworkSelector` in RuntimePlan — do not reimplement pin/createdAt sort.
