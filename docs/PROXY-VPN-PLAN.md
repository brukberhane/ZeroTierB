# Proxy + VPN dual-mode plan

**Status:** design spec, not implemented.  
**Current app:** JNI `VpnService` only (`com.brukb.zerotier`), multi-network TUN.  
**Proxy-era source:** branch `archive/proxy-mode`, tag `v0.1.0-proxy` (`com.zerotier.pylon`).

This document is the implementation contract. Change the spec here before changing the code.

### Current tree (names to use)

| Piece | Path / name |
|-------|-------------|
| App id | `com.brukb.zerotier` |
| Application | `ZerotierBApplication` |
| VPN FGS | `vpn/ZerotierBVpnService.kt` (`com.zerotier.sdk.Node`) |
| Identity I/O | `vpn/ZeroTierDataStore.kt` → `context.filesDir` |
| Room | `data/AppDatabase.kt` v2, entity `ZerotierBNetwork` |
| Prefs | `data/AppPreferences.kt` (`startOnBoot`, `vpnAlwaysOn`) |
| Boot | `system/BootReceiver.kt` |
| JNI module | `:core` (`externals/ZeroTierOne/java`) |
| libzt tree | `libzt/` (not wired into `:app` today) |
| Archive | `archive/proxy-mode` — `com.zerotier.pylon`, `PylonService`, `SystemProxyManager`, `ShizukuPermissionHelper` |

New Room fields on `ZerotierBNetwork`: `createdAt`, `isPinnedMain` (do not rename existing `isEnabled` / `allowManaged` / `allowDefault` / `allowGlobal` / `allowDns` / `routePriority`).

---

## 1. Goal

Two **runtime modes**, one **ZeroTier identity**, never both stacks live:

| Runtime | Stack | ZeroTier nets | Device traffic |
|---------|--------|---------------|----------------|
| **PROXY** | libzt + local HTTP proxy | All **enabled** ZT networks | HTTP/HTTPS that honor the system HTTP proxy. Bind **127.0.0.1 only**. |
| **VPN** | JNI SDK + `VpnService` TUN | **One** ZT network (main) | All IP. Leave every other ZT network. |
| **OFF** | neither | none | Restore previous system HTTP proxy. |

Which runtime is active is decided by **global mode**, and when global is `AUTO`, by the **physical link** the phone is using (WiFi SSID, mobile SIM, or Other). Physical-link chips are **not** ZeroTier network chips.

Not a security product. No `blockOutside`. Proxy is convenience for Calibre-Web / Immich / other HTTP(S). Apps that ignore the system proxy, plus SSH/SMB/UDP/QUIC, need VPN.

---

## 2. Non-goals (v1)

- Concurrent libzt + JNI (same identity = split-brain).
- Per-APN name matching (`WRITE_APN_SETTINGS` / null APN on modern Android).
- Per-ZeroTier PROXY/VPN chip.
- Link-profile `AUTO` (nested AUTO).
- SOCKS5 as the system proxy (Android system proxy is HTTP CONNECT). SOCKS listen is **phase 7**, optional.
- `ProxyRulesEngine` allow/deny lists, captive-portal lock.
- Exposing the proxy on LAN / `0.0.0.0`.
- Multi-network TUN while in VPN runtime (today’s JNI multi-net stays in the code until VPN runtime filters to one net).

---

## 3. Locked decisions

| ID | Decision |
|----|----------|
| Stacks | libzt for PROXY, JNI for VPN. Hard mutex. |
| Identity | Same ZeroTier identity files. Exclusive access. |
| Global mode | `OFF` \| `PROXY` \| `VPN` \| `AUTO` |
| Link chips | `OFF` \| `PROXY` \| `VPN` only. No link AUTO. |
| Unknown WiFi / unclassified | Default **PROXY** (localhost bind still). Do **not** auto-insert a Room row. |
| Cellular | Built-in profile **per active SIM subscription**. |
| Default-network pick | `ConnectivityManager` default / internet network. Trailing debounce. |
| Debounce | Default **5s** quiet period (settings 3–15s). Last event wins. |
| Missing VPN consent | Do **not** prompt from background. Stay PROXY or OFF. Sticky notification “Grant VPN”. |
| Other transport | Built-in **Other** profile: ethernet, USB, Bluetooth PAN/hotspot-as-client, leftover non-WiFi/non-cell. |
| New ZT network | `isEnabled = true`. Joined in PROXY runtime. VPN runtime still only main. |
| Main ZT | `isPinnedMain` else oldest `createdAt` among enabled. |
| Overlap in proxy | `routePriority` (lower wins). Equal priority = user problem; log it. |
| System proxy | `Settings.Global.HTTP_PROXY` only. Not APN DB, not per-SSID `WifiConfiguration` proxy. |
| HTTP listen | `127.0.0.1:0` (ephemeral). Show port in UI. Never `0.0.0.0`. |
| Grant | Shizuku `pm grant … WRITE_SECURE_SETTINGS` + copyable ADB one-liner. |
| Restore source | `git checkout archive/proxy-mode -- <paths>` then re-package to `com.brukb.zerotier`. |

---

## 4. Concepts

### 4.1 Global mode (`AppPreferences.globalMode`)

User-facing segmented control. Overrides link profiles unless value is `AUTO`.

```
OFF    → Runtime OFF. Ignore SSID/SIM.
PROXY  → Runtime PROXY. Ignore SSID/SIM. Join all enabled ZT nets.
VPN    → Runtime VPN. Ignore SSID/SIM. Join main ZT only.
AUTO   → Classify physical link → look up LinkProfile.mode → that runtime.
```

### 4.2 Physical link vs ZeroTier network

| Layer | Examples | Stored as |
|-------|----------|-----------|
| Physical | `HomeWifi`, SIM slot 1 “T-Mobile”, ethernet, BT tethering | `LinkProfile` |
| Overlay | ZeroTier `deadbeef…` Calibre VLAN | `ZerotierBNetwork` |

`ZerotierBNetwork` keeps `isEnabled`, `routePriority`, `allow*`, `isPinnedMain`, `createdAt`. It does **not** store PROXY/VPN.

### 4.3 Runtime plan

Orchestrator output, not user-facing:

```kotlin
data class RuntimePlan(
    val runtime: Runtime,          // OFF | PROXY | VPN
    val reason: String,            // "global PROXY" | "AUTO ssid=HomeWifi" | …
    val vpnNetworkId: String?,     // set iff runtime == VPN
    val joinNetworkIds: List<String>, // enabled nets for PROXY; singleton for VPN
    val vpnConsentMissing: Boolean, // plan wanted VPN, user never granted VpnService
)
```

---

## 5. Physical link classification

### 5.1 Which Android `Network` to read

Do **not** naively use `activeNetwork` while **our** `VpnService` is up. That `Network` is `TRANSPORT_VPN` and would classify as Other → mode flap → death loop.

Algorithm:

1. List networks with `NET_CAPABILITY_INTERNET` (and `VALIDATED` if present).
2. Drop any network that is **our** VPN (`NetworkCapabilities.TRANSPORT_VPN` **and** belongs to this app — compare with `VpnService` underlying / session, or `Builder.addDisallowedApplication` aside: use `connectivityManager.getNetworkInfo` is deprecated; prefer: skip all `TRANSPORT_VPN`, then pick default-capable underlying).
3. Prefer the network `ConnectivityManager` reports as bound default **after** stripping VPN: API 31+ `NetworkCapabilities.underlyingNetworks` on the VPN network; else first remaining WIFI, else CELLULAR, else first remaining.
4. If nothing left (airplane, no link) → treat as **no link** → Runtime OFF while global is AUTO (do not keep a stale PROXY to a dead uplink). Exception: if global is PROXY or VPN, user asked for that regardless; still run, ZT will sit unconnected.

### 5.2 Transport → profile key

| Transport | Profile |
|-----------|---------|
| `TRANSPORT_WIFI` | User `LinkProfile` where `kind == WIFI` and `ssid` equals normalized current SSID. Else **unknown WiFi** → implicit PROXY (no row). |
| `TRANSPORT_CELLULAR` | Built-in `kind == MOBILE` row for **data** `subscriptionId`. Multi-SIM: see 5.4. |
| `TRANSPORT_ETHERNET`, `TRANSPORT_USB` (API 31+), `TRANSPORT_BLUETOOTH`, Wi‑Fi Aware, other non-wifi/non-cell | Built-in `kind == OTHER` (includes phone **joined** to a BT/ethernet/USB shared net / hotspot). |
| `TRANSPORT_VPN` (not ours) | Treat as OTHER. Do not fight third-party VPNs; if establish() fails, notify. |

Phone **providing** a hotspot: default route is usually still WiFi or cell. Classify that upstream, not the hotspot AP.

### 5.3 WiFi SSID

- Read `WifiManager.connectionInfo.ssid` / `NetworkCapabilities` transport info (`WifiInfo` via `NetworkCapabilities.transportInfo` on API 29+).
- Normalize: strip surrounding `"`, treat `<unknown ssid>`, `0x…`, empty as **unknown**.
- Unknown **without** location / nearby-wifi permission is expected. Banner: “SSID hidden — unknown WiFi uses PROXY. Grant location or Nearby Wi‑Fi to match profiles.”
- Permissions:
  - API 26–32: `ACCESS_FINE_LOCATION` (SSID is location).
  - API 33+: `NEARBY_WIFI_DEVICES` (`neverForLocation` if we do not derive location; still request runtime). Keep `ACCESS_FINE_LOCATION` fallback on OEM weirdness.
- Match: exact case-sensitive SSID string after normalize (WiFi SSIDs are case-sensitive). No BSSID in v1.
- User action **Save this SSID**: insert `LinkProfile(kind=WIFI, ssid=…, mode=PROXY)` from the live classifier.
- Do **not** auto-save cafe SSIDs.

### 5.4 Multi-SIM

Built-in rows, one per `SubscriptionInfo` from `SubscriptionManager`:

| Field | Use |
|-------|-----|
| `subscriptionId` | Primary key for MOBILE profiles |
| `simSlotIndex` | UI “SIM 1 / SIM 2” |
| `displayName` / `carrierName` | Label, refresh when OS updates it |
| `iccId` | Optional sanity check if `subscriptionId` recycled |

**Which SIM is the current data path**

1. `SubscriptionManager.getActiveDataSubscriptionId()` (API 29+) / `DEFAULT_SUBSCRIPTION_ID`.
2. Else `TelephonyManager.dataSubscriptionId` if available.
3. Else `NetworkCapabilities` specifier / `getSubscriptionIds()` (API 30+ `Network.get*`) when present.

If data sub has **no** row yet (hot-insert SIM), upsert a MOBILE profile, `mode = PROXY`.

If SIM removed: keep the row (user’s mode choice) but hide or mark “SIM absent”. Do not delete.

Dual-SIM dual-data OEM quirks: if both look active, prefer the network Android bound as default cellular.

No per-APN matching in v1. “T-Mobile vs MVNO APN on same SIM” is one profile.

Permission: `READ_PHONE_STATE` for subscription list on many OEMs; request only when user opens Links UI or sets AUTO. If denied, collapse all cellular to a single fallback MOBILE row `subscriptionId = -1` (“Mobile data”).

### 5.5 Other

Singleton built-in `id = "other"`, not deletable. Default mode **PROXY**. User may set OFF/VPN. Covers ethernet dongle, USB tethering uplink, Bluetooth PAN client, mystery transports.

### 5.6 Debounce

Trailing quiet period, default **5 seconds**, DataStore `linkDebounceMs` (clamp 3_000–15_000).

```
onAvailable / onLost / onCapabilitiesChanged / subscriptions changed
  → cancel previous Job
  → start Job { delay(debounce); classify(); apply(plan) }
```

Weak WiFi: suppress apply if classify() equals last **applied** plan (`equals` on `RuntimePlan.runtime` + `vpnNetworkId` + `joinNetworkIds`).

Boot / first classify: still wait debounce so OEM WiFi stack can settle; except global OFF (apply immediately).

---

## 6. ZeroTier network rules

### 6.1 Main

```
fun mainNetwork(networks: List<ZerotierBNetwork>): ZerotierBNetwork? {
    val enabled = networks.filter { it.isEnabled }
    return enabled.firstOrNull { it.isPinnedMain }
        ?: enabled.minByOrNull { it.createdAt }
}
```

Pin chip: at most one. Setting pin on B clears pin on A (transaction).

`createdAt` = `System.currentTimeMillis()` on first insert. Room migration 2→3 adds column default `0`; treat `0` as “unknown age”, sort those **after** real timestamps then by `networkId` so order is stable.

### 6.2 What gets joined

| Runtime | Join | Leave |
|---------|------|--------|
| PROXY | all `isEnabled` | disabled |
| VPN | `mainNetwork` if enabled | **all others**, including enabled non-main |
| OFF | none | all (node stopped) |

VPN runtime **must** `leave()` non-main on the JNI node and omit them from `rebuildVpn()`. Do not leave them “joined but unrouted” — that was the packet-interference concern.

### 6.3 Proxy routing (libzt)

Port `RouteResolver` + `DnsResolver` from archive. Longest-prefix match across joined nets’ assigned addrs + managed routes (honor `allowManaged` / `allowDefault` / `allowGlobal`). Tie: `routePriority`. Still tied: pick lower `networkId` unsigned, log warning (same as current VPN overlap list).

`allowDns`: populate proxy DNS from that net’s ZT DNS when true.

No `blockOutside`: destination not in any ZT prefix → **plain `java.net.Socket`** (device uplink). Destination in ZT prefix → `ZeroTierSocket`.

HTTP server: CONNECT + origin-form HTTP from archive `HttpProxyServer`. Drop `ProxyRulesEngine` for v1 (always allow if we opened a socket).

---

## 7. Dual stack and identity

### 7.1 Why two stacks

- JNI (`:core` / `com.zerotier.sdk.Node`): Ethernet frames ↔ TUN. No BSD sockets API.
- libzt (`ZeroTierSocket`): userspace sockets. No Android TUN.

One stack cannot do both without a large JNI sockets shim. Do not build that.

### 7.2 Identity home

| Stack | Path |
|-------|------|
| JNI today | `context.filesDir` via `ZeroTierDataStore` (`identity.secret`, `networks.d/`, …) |
| libzt archive | `node.initFromStorage(filesDir.absolutePath)` |

**Keep one home:** `context.filesDir` (or a single subdir `filesDir/zerotier/` used by **both** — prefer subdir **only if** we migrate JNI DataStore prefixes in the same change). Same `identity.secret` ⇒ same node ID on the controller when switching modes.

Never start libzt while JNI `Node` is alive, and vice versa. Orchestrator:

1. Stop PROXY service (join leave, `zts` stop, unbind sockets).
2. Wait until UDP 9993/9994 closed (poll / timeout 10s).
3. Delete stale `*.lock` if present.
4. Start the other stack.
5. If PROXY: bind HTTP, **then** write `HTTP_PROXY`.

Failure: roll back to OFF, notify, leave system proxy restored.

### 7.3 libzt artifact

Archive used `libzt.aar` from `zerotier.properties`. `libzt/` still lives in-tree. Native JNI today is `:core`; `:app` does not depend on libzt.

Implementation: restore AAR **or** add `:libzt` Android library from existing tree — whichever builds on this NDK. Do not load `libzt.so` and `libZeroTierOneJNI.so` in the **same** process at the same time if they both register overlapping JNI/planet ports. Orchestrator process is one app process: **unload is not a thing**. Practical rule:

- Load both `.so`s if the loader allows (many apps do).
- Only **start** one node.
- If native init of both at class-load fights, lazy-load via split `ProxyModeService` vs `ZerotierBVpnService` classloaders is not available on Android. Then **serialize native `start`/`stop`** and verify with a device test in phase 2. If they cannot coexist in one process even when stopped, document a process split (`:proxy` isolated process) as escape hatch — last resort, two processes must **not** both open `identity.secret`.

---

## 8. System HTTP proxy

### 8.1 Write path

Port `SystemProxyManager` (`archive/proxy-mode`).

```
enable(port):
  saved = current Global.HTTP_PROXY  // if not our loopback
  persist saved in DataStore
  Settings.Global.putString(HTTP_PROXY, "127.0.0.1:$port")

disable():
  restore saved or ":0"
```

Bind server **before** `putString`. On crash: `Application` / service `onDestroy` / `START_STICKY` restart must not leave a proxy pointing at a dead port. `onTaskRemoved` + `onDestroy`: `disable()`. Boot: if last runtime was PROXY, start listen then enable.

`hasPermission()`: try `Settings.Global.getString` / `putString`; catch `SecurityException`.

### 8.2 What Global HTTP_PROXY actually does

- Honored by `HttpURLConnection`, WebView, many Chromium-based browsers, a lot of OEM HTTP stacks.
- **Not** a true per-app VPN. OkHttp honors it unless the app clears proxy. Flutter/Dart, games, custom TLS stacks often ignore it.
- HTTPS goes through **CONNECT** (tunnel). We do not MITM TLS.
- HTTP/3 / QUIC often bypasses HTTP proxy → Immich app may fail in PROXY; browser often works.
- WiFi vs LTE: Global applies to both. **Do not** write APN `proxy=` in v1. Test both transports; if an OEM ignores Global on mobile, note in Gaps — still no APN writes unless a later spec says so.

### 8.3 WRITE_SECURE_SETTINGS

Manifest:

```xml
<uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS"
    tools:ignore="ProtectedPermissions" />
```

Cannot be granted from Play-style runtime dialog. Grant:

1. **Shizuku** (preferred): port `ShizukuPermissionHelper` — `Shizuku.newProcess` reflection `pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS`. Need `dev.rikka.shizuku:api` + `provider` 13.1.5, provider in manifest as in archive.
2. **ADB:** `adb shell pm grant com.brukb.zerotier android.permission.WRITE_SECURE_SETTINGS` — copy button in UI.

Without grant: PROXY runtime can still listen on 127.0.0.1 for apps that set proxy manually; UI shows “system proxy inactive”. Do not pretend Global is set.

---

## 9. ConnectionOrchestrator

Application-scoped (`ZerotierBApplication` or singleton bound to app process). One mutex (`Mutex`) around `apply(plan)`.

```
inputs:
  globalMode (DataStore)
  linkProfiles (Room)
  ztNetworks (Room)
  linkEvents (NetworkCallback + SubscriptionManager)
  vpnConsent (VpnService.prepare == null)
  secureSettings (SystemProxyManager.hasPermission)

outputs:
  start/stop ProxyModeService
  start/stop ZerotierBVpnService
  SystemProxyManager enable/disable
  notifications
```

`NetworkCallback` registered for app lifetime when `globalMode == AUTO` **or** when we need UI “current SSID”. Unregister when OFF and Links screen not visible, if we want to save radio; simpler v1: register while app process alive and mode ≠ OFF.

### 9.1 Resolve

```
fun resolve(...): RuntimePlan {
  if (global == OFF) return OFF
  if (global == PROXY) return proxyPlan(allEnabled)
  if (global == VPN) return vpnPlan(main)  // or consent-blocked
  // AUTO
  val link = classify()
  val mode = when (link) {
    is WifiKnown -> profile.mode
    is WifiUnknown -> PROXY
    is Mobile -> mobileProfile(subId).mode
    is Other -> otherProfile.mode
    is None -> OFF
  }
  return planFor(mode)
}
```

`planFor(VPN)` when `VpnService.prepare(ctx) != null`: `runtime = PROXY` if we can still proxy (enabled nets nonempty), else OFF; `proxyBlocked` flag **misnamed** — use `vpnConsentMissing = true`. Notification: “VPN not granted — using proxy” or “VPN not granted — idle”.

### 9.2 Swap sequence (PROXY → VPN)

1. `SystemProxyManager.disable()` (restore user proxy).
2. `ProxyModeService.stop()` — libzt `node.stop()`, unbind HTTP.
3. Wait node dead / lock files gone (timeout 10s).
4. If consent missing → abort, stay OFF or PROXY per 9.1, notify.
5. `ZerotierBVpnService.start()` with extra `EXTRA_SINGLE_NETWORK_ID = main`.
6. Service joins only that id; `leave` others if JNI persisted membership in `networks.d`.

Reverse for VPN → PROXY: `ACTION_STOP` VPN, wait TUN gone, start proxy service, bind, `enable(port)`.

Same-runtime plan change (AUTO WiFi A PROXY → WiFi B PROXY): **no** stack swap; optionally no-op.

AUTO PROXY → AUTO VPN: full swap. UI may show “Switching…” for debounce + swap duration.

### 9.3 VPN service change

Today `rebuildVpn()` adds every OK net. Add filter:

```
val vpnIds = orchestrator.allowedVpnNetworkIds() // size 0 or 1
```

`joinNetwork` intents from UI while runtime is VPN: if user enables a second net, **do not** join it on TUN. Persist `isEnabled` for later PROXY. If they pin a new main, orchestrator rebuilds VPN onto that net (leave old, join new) without involving libzt.

---

## 10. Data model

### 10.1 DataStore (`AppPreferences`)

| Key | Type | Default |
|-----|------|---------|
| `global_mode` | string enum | `OFF` (or `VPN` if we want not to surprise existing installs — **existing users run VPN**; default **`VPN`** if `startOnBoot` was true else **`OFF`**. Prefer: migrate `startOnBoot==true` → `globalMode=VPN`.) |
| `saved_http_proxy` | string? | null |
| `last_http_proxy_port` | int | 0 (display) |
| `link_debounce_ms` | int | 5000 |
| `start_on_boot` | bool | existing |
| `vpn_always_on` | bool | existing (OS always-on VPN still only applies to VpnService) |

### 10.2 `ZerotierBNetwork` (Room `networks`, version 3)

Add:

- `createdAt: Long` (default 0, backfill)
- `isPinnedMain: Boolean` (default false)

Keep: `networkId`, `name`, `isEnabled`, `allowManaged`, `allowDefault`, `allowGlobal`, `allowDns`, `routePriority`.

DAO: `clearPinnedMain()` + `setPinnedMain(id)` in one transaction.

### 10.3 `LinkProfile` (new table `link_profiles`)

```kotlin
@Entity(tableName = "link_profiles")
data class LinkProfile(
    @PrimaryKey val id: String,          // "other" | "mobile-$subId" | uuid for wifi
    val kind: LinkKind,                  // WIFI | MOBILE | OTHER
    val mode: LinkMode,                  // OFF | PROXY | VPN
    val ssid: String? = null,            // WIFI only
    val subscriptionId: Int? = null,     // MOBILE only
    val simSlotIndex: Int? = null,
    val label: String = "",              // user or carrier name
    val iccId: String? = null,
)
```

Seed on first run: `other` PROXY. Upsert MOBILE rows when subscriptions change.

`LinkProfileDao.observeAll()`, `getBySsid`, `getBySubscriptionId`.

### 10.4 State flows for UI

Extend or add `OrchestratorState`:

- `globalMode`, `runtime`, `reason`
- `currentSsid`, `currentSubId`, `currentKind`
- `httpProxyPort`, `systemProxyActive`, `hasSecureSettings`
- `shizukuAvailable`
- `vpnConsentMissing`
- `nodeId`, per-ZT join status (merge VPN + proxy service states)

Keep `ZerotierBVpnService.state` for VPN internals; proxy service gets a sibling `ProxyServiceState` (port archive `PylonServiceState`, drop socks unless phase 7).

---

## 11. File map

### 11.1 New

| File | Role |
|------|------|
| `connection/ConnectionOrchestrator.kt` | Mutex, resolve, swap |
| `connection/LinkClassifier.kt` | Network → Wifi/Mobile/Other/None |
| `connection/LinkDebouncer.kt` | Trailing debounce |
| `connection/RuntimePlan.kt` | Data class + equals |
| `data/model/LinkProfile.kt` | Entity |
| `data/LinkProfileDao.kt` | DAO |
| `proxy/ProxyModeService.kt` | Archive `PylonService` (no SOCKS v1) |
| `proxy/SystemProxyManager.kt` | Archive |
| `proxy/http/HttpProxyServer.kt` | Archive, bind 127.0.0.1 |
| `proxy/RouteResolver.kt` | Archive minus blockOutside |
| `proxy/dns/*` | Archive |
| `proxy/ProxyConnection.kt` | Archive |
| `ztlib/ZeroTierNodeManager.kt` | Archive `zt/*` |
| `system/ShizukuPermissionHelper.kt` | Archive |
| `system/LinkNetworkCallback.kt` | Register/unregister |
| `ui/LinksScreen.kt` | SSID list, SIM rows, Other, Save SSID |
| `ui/GrantSecureSettingsCard.kt` | Shizuku + ADB |

### 11.2 Restore from archive (re-package `com.zerotier.pylon` → `com.brukb.zerotier`)

Bring over, do **not** restore wholesale UI/data that collides with current Room:

- `proxy/**` (skip `ProxyRulesEngine` unless needed as dead code — skip)
- `proxy/http/**`
- `proxy/dns/**`
- `zt/ZeroTierNodeManager.kt`, `ZtModels.kt`, `ZtNetworkQuery.kt`
- `system/ShizukuPermissionHelper.kt`
- `service/PylonService.kt` → rewrite as `ProxyModeService` talking to orchestrator
- Shizuku gradle + manifest provider

Do **not** restore archive `AppDatabase` / `PylonNetwork` / `MainScreen` over current VPN UI. Merge.

Skip `socks5/Socks5ProxyServer.kt` until phase 7 (~177 lines; BIND/UDP still out).

### 11.3 Edit in place

| File | Change |
|------|--------|
| `ZerotierBApplication.kt` | Construct orchestrator, seed Other, observe subscriptions |
| `data/AppDatabase.kt` | v3: columns + `link_profiles` |
| `data/model/ZerotierBNetwork.kt` + `NetworkDao` + `NetworkRepository` | `isPinnedMain` + `createdAt` |
| `data/AppPreferences.kt` | `globalMode`, saved proxy, debounce |
| `vpn/ZerotierBVpnService.kt` | Single-net filter; stop-complete callback for orchestrator |
| `system/BootReceiver.kt` | Orchestrator refresh, not blind `ZerotierBVpnService.start` |
| `AndroidManifest.xml` | Proxy FGS, Shizuku, `WRITE_SECURE_SETTINGS`, location/nearby-wifi, `READ_PHONE_STATE` |
| `app/build.gradle.kts` | Shizuku, libzt artifact |
| `ui/MainScreen.kt` / `MainViewModel.kt` | Global mode, grant, port, pin chip |
| `ui/MainActivity.kt` | VPN consent result → orchestrator `onVpnConsentGranted()` |

---

## 12. UI

### 12.1 Main

- Segmented **OFF | PROXY | VPN | AUTO**
- Status: runtime, node ID, “System proxy 127.0.0.1:PORT” or “not granted”
- Current link line: `WiFi HomeWifi` / `SIM 2 T-Mobile` / `Other (USB)` / `Unknown WiFi (PROXY)`
- ZT list: enable switch, status, **Main** chip (pin)
- Banner if `vpnConsentMissing`

### 12.2 Links screen

- Live “this link” + **Save SSID** (WiFi known/unknown)
- User WiFi rows: SSID, mode chips OFF/PROXY/VPN, delete
- SIM rows: slot + carrier, mode chips, not deletable
- Other row: mode chips, not deletable
- Debounce slider or settings field

### 12.3 Settings

- Start on boot (applies last global mode via orchestrator)
- Copy ADB grant
- Shizuku grant button
- Optional: show last proxy port

### 12.4 Network detail

Existing allow* + `routePriority`. Add pin Main. No PROXY/VPN chip.

---

## 13. Lifecycle

| Event | Action |
|-------|--------|
| Boot | If `startOnBoot`, `orchestrator.refresh()`. AUTO waits debounce + link. VPN only if consent already granted. PROXY only if we can bind; system proxy only if secure settings granted. |
| Process death | OS restarts FGS if sticky. Orchestrator re-reads DataStore. Always `disable()` proxy in `onDestroy` if we set it. |
| Airplane | AUTO → None → OFF. Global PROXY/VPN stay requested. |
| User force-stop | Cannot run `onDestroy` reliably. Accept stale `HTTP_PROXY` until next start; on next start if runtime ≠ PROXY, `disable()`. |
| Always-on VPN (system) | Only meaningful for VPN runtime. Document: AUTO flipping off VPN will fight always-on. Settings note. |

Foreground types: keep VPN as `connectedDevice` (current). Proxy FGS: `specialUse` or `connectedDevice` + type in manifest (API 34). Persistent notification: mode + SSID/SIM + port.

---

## 14. Permissions (manifest)

| Permission | Why |
|------------|-----|
| existing INTERNET, NETWORK_STATE, FGS, BOOT | unchanged |
| `WRITE_SECURE_SETTINGS` | Global HTTP_PROXY |
| `ACCESS_FINE_LOCATION` | SSID (pre-33 / OEM) |
| `NEARBY_WIFI_DEVICES` | SSID API 33+ |
| `READ_PHONE_STATE` | Subscription list / data sub |
| `CHANGE_NETWORK_STATE` | already present |
| Shizuku provider | grant helper |

Runtime: location/nearby when AUTO or Links screen; phone state when Links or AUTO.

---

## 15. Tests

### 15.1 Unit (no device)

- `RuntimePlan` resolve table: global × link × consent × enabled nets
- `RouteResolver` LPM + `routePriority` ties
- SSID normalize (`"Home"` vs `Home`, unknown sentinels)
- Main selection (pin vs createdAt)

### 15.2 Device matrix (manual)

| # | Setup | Expect |
|---|--------|--------|
| 1 | Global PROXY, WiFi, browser → ZT HTTP | Works; `HTTP_PROXY` = loopback |
| 2 | Same on LTE | Works if OEM honors Global |
| 3 | Chrome vs Firefox | Note failures |
| 4 | Immich native | May fail; browser OK |
| 5 | SSH to ZT | Fail in PROXY; work in VPN |
| 6 | Two ZT nets, different subnets, PROXY | Both reachable via LPM |
| 7 | Overlap same prefix, different priority | Lower priority wins |
| 8 | Global VPN, two enabled ZT | Only main on TUN; other left |
| 9 | AUTO WiFi A PROXY → WiFi B VPN | After 5s: stack swap, main only |
| 10 | Flap WiFi 2s | No swap until quiet 5s |
| 11 | AUTO unknown SSID | PROXY, no new row |
| 12 | Save SSID | Row appears, mode persisted |
| 13 | Dual SIM, switch data SIM | Other MOBILE profile applies |
| 14 | USB/BT uplink | Other profile |
| 15 | VPN runtime, check classifier | Does not snap to Other |
| 16 | Kill app | `HTTP_PROXY` restored or `:0` on next launch |
| 17 | Reboot, start on boot, AUTO | Debounced classify |
| 18 | No Shizuku, no ADB | Listen works; system proxy flag false |
| 19 | Shizuku grant | `hasPermission` true, Global set |
| 20 | VPN consent denied, AUTO profile VPN | Stay PROXY/OFF + notify |
| 21 | `curl --proxy 127.0.0.1:PORT` from Termux | Loopback only; LAN IP:PORT refused |

---

## 16. Implementation phases

Do not skip sequence. Each phase should leave `main` buildable.

**Phase 1 — Data + classify + orchestrator stub**  
Room v3, DataStore globalMode, `LinkClassifier` + debounce, UI global control + Links list. No libzt. `apply()` only logs plan / sets OFF/VPN using **existing** multi-net VPN if global VPN (still multi-net until phase 4).

**Phase 2 — libzt proxy service**  
Restore AAR/node manager, `HttpProxyServer` on 127.0.0.1:0, no Global write yet. Manual Termux `--proxy` test. Mutex: cannot start if VPN running.

**Phase 3 — System proxy + Shizuku**  
`SystemProxyManager`, grant UI, enable after bind. Test WiFi + LTE.

**Phase 4 — Single-net VPN + swap**  
Filter `rebuildVpn()`; leave non-main; orchestrator swap sequences; consent-missing path.

**Phase 5 — AUTO + SIM + Other**  
Wire `NetworkCallback`, subscription upsert, unknown-SSID PROXY, Save SSID, VPN-underlay classify.

**Phase 6 — Boot, crash, notifications, polish**  
BootReceiver, banners, overlap log, README.

**Phase 7 (optional)** — SOCKS5 listen on second ephemeral port, display only. No BIND/UDP. Not written to `HTTP_PROXY`.

---

## 17. Risks

| Risk | Mitigation |
|------|------------|
| libzt + JNI `.so` in one process | Phase 2 spike: start/stop both sequentially. Isolated process only if spike fails. |
| Identity file format drift | Same ZeroTierOne generation; never dual-open. |
| OEM ignores `HTTP_PROXY` on mobile | Document; still no APN writer in v1. |
| SSID permission denied | Unknown → PROXY; banner. |
| Always-on VPN vs AUTO | Settings warning. |
| `HTTP_PROXY` leak after force-stop | Clear on next process start if runtime ≠ PROXY. |
| Controller sees flapping online | Debounce 5s; avoid swap on equal plans. |

---

## 18. Out of scope follow-ups (later specs)

- BSSID / Passpoint realm matching
- True APN name matching
- Isolated `:proxy` process
- SOCKS BIND/UDP
- Per-app proxy (no API) / always-on mock VPN that only forwards HTTP (defeats PROXY goal)
- Play Store (protected permission + Shizuku)

---

## 19. Resume / implement

When implementing, follow phase order in §16. Do not re-litigate §3 without updating this file first.
