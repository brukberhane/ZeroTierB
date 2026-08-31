# T14 — Network detail: live addresses, routes, DNS (Phase D)

**Status**: Done  
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
| 2026-08-31 | planned | Pending | Planned | /task-1-plan — live panel + VPN publish lists | |
| 2026-08-31 | execute | Planned | InProgress | /task-2-execute | |
| 2026-08-31 | complete | InProgress | Done | /task-3-complete — verify green, dialectic, commit | |

## Requirements

- [x] **Header**: network name + short ID; join status chip (reuse T12 chip component)
- [x] **Runtime section** (visible when `network.enabled` and active runtime has data for this `networkId`):
  - Assigned addresses (list, monospace)
  - Managed routes: prefix → via (if present) — format like VPN logs / `RouteResolver`
  - DNS servers list
  - Last updated: implicit via state flow (no manual refresh button unless stale detection needed)
- [x] When runtime OFF or network not joined: empty state copy ("Not connected — enable network and start PROXY or VPN")
- [x] When wrong runtime (e.g. VPN up but viewing proxy-only net not in VPN plan): honest message, not stale VPN data from another net
- [x] **Edit section** unchanged scope: enable toggle, pin Main, delete network — save still via `NetworkRepository` + `orchestrator.refresh()`
- [x] Data plumbing:
  - Extend T11 `NetworkRuntimeStatus` if missing `routes` / `dns` / `addresses` lists
  - Proxy: pull from `ZtNetworkStatus` / `nodeManager` assigned routes (mirror VPN publish path)
  - VPN: reuse existing `VpnServiceState.networkStatuses` fields
- [x] Scrollable column — detail can be long on busy controllers

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

**Date:** 2026-08-31  
**Codebase snapshot:** branch `T14-network-detail-live-runtime` after T13 `abab6d8`. `NetworkRuntimeStatus` already has `assignedAddresses` / `routes` / `dnsServers`. PROXY fills them via `ztNetworkToRuntime`. VPN `publishNetworkStatuses` (`ZerotierBVpnService.kt:729`) writes join status only. `NetworkDetailScreen` is a Room-only `AlertDialog`.  
**Execute model:** medium

### Context for executor

**Goal:** Detail screen shows live ZeroTier addresses, managed routes, and DNS for the selected network when the **active** stack has joined it. Header reuses T12 `JoinStatusChip`. Room edit (name, flags, pin Main, save) stays. VPN publish must fill the same lists PROXY already does.

**Resolved ambiguities (do not reopen):**

| Question | Pick |
| -------- | ---- |
| Rename `routes` → `managedRoutes` | **No.** Field is `NetworkRuntimeStatus.routes`. |
| New JNI for proxy route `via` | **Skip.** PROXY shows CIDR only. VPN includes via when `VirtualNetworkRoute.via` is set. |
| `dnsDomain` | **Skip.** Not in `NetworkRuntimeStatus`; AC does not require it. |
| Convert detail to `ModalBottomSheet` | **No.** Keep `AlertDialog`; make `text` Column `verticalScroll`. Sheet is T15 layout polish. |
| Enable / delete on detail | **No.** Stay on home `NetworkRow`. |
| Show DNS when `allowDns` is false | **Yes** (controller list, read-only). `allowDns` remains the edit toggle that affects TUN/proxy apply. |
| Filter displayed routes | **Yes** — `filterDisplayRoutes` mirroring `RouteResolver.shouldIncludeRoute` using Room `allowManaged` / `allowDefault` / `allowGlobal`. |
| Wrong-runtime (VPN up, not-main net) | `resolveNetworkRuntime` already returns `null`. UI copy: VPN-only-main, **not** PROXY leftover. |

**Key files**

| Path | Action |
| ---- | ------ |
| `app/src/main/java/com/brukb/zerotier/connection/RouteDisplay.kt` | **new** — `formatRouteLine`, `formatAssignedCidr`, `filterDisplayRoutes` |
| `app/src/test/java/com/brukb/zerotier/connection/RouteDisplayTest.kt` | **new** — tables below |
| `app/src/main/java/com/brukb/zerotier/vpn/ZerotierBVpnService.kt` | Enrich `publishNetworkStatuses` |
| `app/src/main/java/com/brukb/zerotier/ui/NetworkDetailScreen.kt` | Header chip + runtime sections + scroll |
| `app/src/main/java/com/brukb/zerotier/ui/MainScreen.kt` | Pass lifecycle / runtime / `networkRuntime` into detail |
| `app/src/main/res/values/strings.xml` | Runtime empty / section titles |
| `app/src/main/java/com/brukb/zerotier/connection/RuntimeStatusMapper.kt` | **no type change**; optional `vpnLists` helpers if they stay pure |
| `app/src/main/java/com/brukb/zerotier/proxy/RouteResolver.kt` | **no changes** (display copies its filter rules) |
| `app/src/main/java/com/brukb/zerotier/ztlib/ZtNetworkQuery.kt` | **no changes** (no via binding) |

**Invariants**

- `.cursor/rules/compose.mdc`: ViewModel is the only UI→service bridge. No per-ZT PROXY/VPN chips. Reuse `JoinStatusChip` — do not restyle.
- `.cursor/rules/connection-orchestrator.mdc`: VPN joins **only Main**; PROXY joins every enabled net. Detail must not invent the other stack’s map.
- `.cursor/rules/android-vpn.mdc` / `zerotier-jni.mdc`: do not change TUN rebuild / join-leave; only **publish** extra fields from `VirtualNetworkConfig` already in memory.
- Do **not** bump AGP/BOM. Do **not** `VpnService.prepare()` from detail.

**Data already shipped**

- `MainViewModel.networkRuntime(id)` → `resolveNetworkRuntime(plan.runtime, proxy, vpn, id)`
- `joinChipStatus(lifecycle, runtime, enabled, networkRuntime)`
- PROXY: `ProxyModeService.publishNetworkStatusesFromNode` + `ztNetworkToRuntime`

**VPN config shape** (`rebuildVpn` already reads this — copy formatting, do not rebuild TUN):

- Assigned: `config.assignedAddresses` is `InetSocketAddress[]`; **`address` = IP, `port` = prefix length**
- Routes: `config.routes` — `target` same InetSocketAddress trick; `via` nullable; skip if `InetAddressUtils.addressToRoute(target.address, target.port)` is null
- DNS: `config.dns?.servers` — each has `.address: InetAddress`

### Steps

1. **Pure display helpers** — create `connection/RouteDisplay.kt`:

```kotlin
fun formatAssignedCidr(host: String?, prefix: Int): String?
// null host → null; else "$host/$prefix"

fun formatRouteLine(prefixCidr: String, via: String?): String
// blank/null via, "0.0.0.0", "::", "/0" unspecified → just prefixCidr
// else "$prefixCidr → $via"

fun filterDisplayRoutes(
    routes: List<String>,
    allowManaged: Boolean,
    allowDefault: Boolean,
    allowGlobal: Boolean,
): List<String>
```

   Filter rules (copy `RouteResolver.shouldIncludeRoute` + `allowManaged` gate):
   - `!allowManaged` → empty list
   - default (`0.0.0.0/0`, `::/0`) → keep iff `allowDefault`
   - `IpClassification.isPrivateOrLocal(ip)` → keep
   - else → keep iff `allowGlobal`

   `formatRouteLine` input `prefixCidr` may already contain ` → via` from VPN publish — **filter on the CIDR before ` → `**.  
   → verify: unit tests in step 2 compile

2. **Tests** — `RouteDisplayTest.kt` (table):

| Case | Expect |
| ---- | ------ |
| `formatAssignedCidr("10.147.20.1", 24)` | `"10.147.20.1/24"` |
| `formatAssignedCidr(null, 24)` | `null` |
| `formatRouteLine("10.0.0.0/8", null)` | `"10.0.0.0/8"` |
| `formatRouteLine("10.0.0.0/8", "10.147.20.1")` | `"10.0.0.0/8 → 10.147.20.1"` |
| `formatRouteLine("0.0.0.0/0", "0.0.0.0")` | `"0.0.0.0/0"` (unspecified via dropped) |
| `filterDisplayRoutes(["10.0.0.0/8"], allowManaged=false, …)` | empty |
| `filterDisplayRoutes(["0.0.0.0/0"], allowManaged=true, allowDefault=false, …)` | empty |
| `filterDisplayRoutes(["0.0.0.0/0"], allowManaged=true, allowDefault=true, …)` | keep |
| `filterDisplayRoutes(["8.8.8.0/24"], allowManaged=true, allowDefault=false, allowGlobal=false)` | empty |
| `filterDisplayRoutes(["8.8.8.0/24"], … allowGlobal=true)` | keep |
| `filterDisplayRoutes(["10.0.0.0/8 → 10.147.20.1"], allowManaged=true, allowDefault=false, allowGlobal=false)` | keep (private prefix) |

   Also extend `RuntimeStatusMapperTest` if you add a pure `vpnNetworkToRuntime(id, join, addrs, routes, dns)` helper — otherwise skip.  
   → verify: `./gradlew :app:testDebugUnitTest --tests com.brukb.zerotier.connection.RouteDisplayTest`

3. **VPN publish** — `ZerotierBVpnService.publishNetworkStatuses` (`:729-737`):

Replace join-only constructor with lists extracted from `VirtualNetworkConfig`:

```kotlin
NetworkRuntimeStatus(
    networkId = StringUtils.networkIdToString(id),
    joinStatus = vpnVirtualStatusToJoinStatus(config.status),
    assignedAddresses = config.assignedAddresses.mapNotNull {
        formatAssignedCidr(it.address.hostAddress, it.port)
    },
    routes = config.routes.mapNotNull { rc ->
        val target = InetAddressUtils.addressToRoute(rc.target.address, rc.target.port)
            ?: return@mapNotNull null
        val cidr = "${target.hostAddress}/${rc.target.port}"
        formatRouteLine(cidr, rc.via?.address?.hostAddress)
    },
    dnsServers = config.dns?.servers?.mapNotNull { it.address.hostAddress }.orEmpty(),
)
```

   Do **not** apply TUN `allowDefault` skip here — display filter uses Room flags in the UI. Publish controller truth.  
   → verify: compile; no change to `rebuildVpn`

4. **Strings** — add to `strings.xml`:

   - `detail_title` (optional; dialog title can stay hardcoded)
   - `detail_runtime_section` — "Live runtime"
   - `detail_addresses` / `detail_routes` / `detail_dns`
   - `detail_none` — "None"
   - `detail_not_connected` — "Not connected — enable network and start PROXY or VPN"
   - `detail_vpn_main_only` — "VPN joins the Main network only. Pin this network Main, or switch to PROXY."  
   → verify: resources compile

5. **`NetworkDetailScreen`** — add params (keep `network`, `onDismiss`, `onSave`):

```kotlin
fun NetworkDetailScreen(
    network: ZerotierBNetwork,
    joinStatus: JoinStatus?,
    runtimeStatus: NetworkRuntimeStatus?,
    activeRuntime: Runtime?,
    onDismiss: () -> Unit,
    onSave: (ZerotierBNetwork) -> Unit,
)
```

   Layout inside `AlertDialog` `text`:
   - `Column(Modifier.verticalScroll(rememberScrollState()), spacedBy(12.dp))`
   - **Header:** `Text(network.networkId, FontFamily.Monospace, bodySmall)` + `joinStatus?.let { JoinStatusChip(it) }` then existing name / priority fields
   - **Runtime block** (after header, before toggles):
     - If `!network.isEnabled` **or** `activeRuntime == null || activeRuntime == Runtime.OFF`: `Text(detail_not_connected)`
     - Else if `runtimeStatus == null && activeRuntime == Runtime.VPN`: `Text(detail_vpn_main_only)`
     - Else if `runtimeStatus == null`: `Text(detail_not_connected)` (PROXY join in flight still usually has JOINING status; chip handles that)
     - Else: three subsections (title + monospace `bodySmall` lines). Routes = `filterDisplayRoutes(runtimeStatus.routes, edited.allowManaged, edited.allowDefault, edited.allowGlobal)` so toggling flags **preview** filter before Save. Empty list → `detail_none`.
   - Existing toggles + Save/Close unchanged  

   → verify: open detail from row; disabled net → empty copy; no crash

6. **Wire `MainScreen.kt`** (`selected?.let` ~213):

```kotlin
NetworkDetailScreen(
    network = network,
    joinStatus = joinChipStatus(
        viewModel.nodeLifecycle(),
        uiState.plan?.runtime,
        network.isEnabled,
        viewModel.networkRuntime(network.networkId),
    ),
    runtimeStatus = viewModel.networkRuntime(network.networkId),
    activeRuntime = uiState.plan?.runtime,
    onDismiss = viewModel::closeNetworkDetail,
    onSave = viewModel::saveNetwork,
)
```

   `saveNetwork` already `orchestrator.refresh()` — do not change.  
   → verify: PROXY joined net shows lists; pin Main + save still works

7. **`make verify`**  
   → verify: lint + unit tests + assembleDebug green

### Tests to add

See step 2 table. No Compose UI tests. No new Room/orchestrator tests.

### Verify commands

```bash
make verify
```

### Risks / pitfalls

- **`InetSocketAddress.port` is prefix**, not a TCP port — copy `rebuildVpn` (`:526-528`).
- **Do not** call `rebuildVpn` from publish. Display-only lists.
- **Do not** fall back `vpn.networkStatuses` when `plan.runtime == PROXY` (mapper already keyed off plan).
- **Scroll:** AlertDialog + `verticalScroll` only — no nested `LazyColumn`.
- **Join chip:** hide when disabled / OFF / Doze via `joinChipStatus` — do not invent a second chip.
- **`hostAddress`** may be null; `mapNotNull`.
- PROXY via-less routes are OK; do not add `zts_core_query_route` JNI this task.

### Out of scope

- Enable/delete on detail; join/leave buttons
- Editing routes/DNS; clipboard (T15)
- ModalBottomSheet for detail; `dnsDomain`; proxy via JNI
- TUN / libzt / orchestrator behavior changes
- AGP/BOM bump

### Execute model recommendation

- **medium** — VPN publish + Room-flag route filter + three empty-state copies. APIs exist; still easy to leak the wrong stack or misuse `port` as TCP.

## Test Plan

- Unit tests: route line formatting helper (prefix, via, no via)
- `make verify`
- Manual on device with managed routes + DNS from controller

## Acceptance Criteria

- [x] Joined network in PROXY shows addresses/routes/DNS on detail screen
- [x] Same network in VPN mode shows VPN-sourced data
- [x] Disabled network shows empty runtime section, not crash
- [x] Pin Main + save still works; orchestrator refresh after save
- [x] `make verify` green

## Verification

**Date:** 2026-08-31  
**Commands:**
```bash
test -f Makefile && grep -q '^verify' Makefile
test -f lefthook.yml && test -f app/lint.xml
JAVA_HOME=/usr/lib/jvm/java-17-openjdk make verify
```
**Result:** green (lint + unit tests + assembleDebug). Note: default mise Java 27 breaks Lombok javac; use JDK 17 per AGENTS.md.

## Files Modified

- `app/src/main/java/com/brukb/zerotier/connection/RouteDisplay.kt` (new)
- `app/src/test/java/com/brukb/zerotier/connection/RouteDisplayTest.kt` (new)
- `app/src/main/java/com/brukb/zerotier/vpn/ZerotierBVpnService.kt`
- `app/src/main/java/com/brukb/zerotier/ui/NetworkDetailScreen.kt`
- `app/src/main/java/com/brukb/zerotier/ui/MainScreen.kt`
- `app/src/main/res/values/strings.xml`

## Manual test (for humans)

1. PROXY, join net with routes → open detail → see routes list
2. Switch VPN (main net) → detail updates to VPN data
3. Disable network → "Not connected" empty state

## Learnings

- VPN `publishNetworkStatuses` must publish list fields from in-memory `VirtualNetworkConfig`, not join status alone.
- JNI `InetSocketAddress.port` on assigned addresses / route targets is CIDR prefix length — not TCP port.
- UI route display filters via `connection/RouteDisplay.filterDisplayRoutes` (mirrors `RouteResolver` + Room `allow*` flags); publish controller truth, filter in UI.
- PROXY routes are CIDR-only in Kotlin; VPN can show `prefix → via` when `VirtualNetworkRoute.via` is set.
- Detail stays `AlertDialog` + `verticalScroll`; copy-to-clipboard deferred to T15.

## Reality notes

### From T12 close-out

- Reuse `JoinStatusChip(status)` on detail header — do not duplicate chip styling.
- `joinChipStatus(lifecycle, plan.runtime, enabled, viewModel.networkRuntime(id))` for visibility rules.

### From T13 close-out

- Settings is `SettingsBottomSheet` (`ModalBottomSheet`), not `AlertDialog`. Unrelated to detail screen but confirms UI rewrite phase C done.
- Debug package/node ID patterns in settings Advanced — T15 adds clipboard on similar read-only lines.

### From T14 plan (reality check)

- `NetworkRuntimeStatus.routes` already exists — do not rename to `managedRoutes`.
- PROXY already fills addresses/routes/dns via `ztNetworkToRuntime`. VPN `publishNetworkStatuses` is join-only until this task.
- Proxy route via is not in Kotlin (`queryManagedRouteCidrs` is CIDR-only). VPN `VirtualNetworkRoute.via` can feed `formatRouteLine`.
- `RouteResolver` lives at `app/.../proxy/RouteResolver.kt` (not `routing/`). `shouldIncludeRoute` is private — T14 copies rules into public `filterDisplayRoutes`.
- Detail stays `AlertDialog` (scrollable); enable/delete stay on the home row.
