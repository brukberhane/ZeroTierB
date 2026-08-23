# T05 — libzt HTTP proxy on 127.0.0.1

**Status**: Done  
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
| 2026-08-23 | planned | Pending | Planned | /task-1-plan: archive port, loopback bind, no Global write | |
| 2026-08-23 | execute | Planned | InProgress | /task-2-execute T05 | |
| 2026-08-23 | complete | InProgress | Done | /task-3-complete T05 | |

## Requirements

- [x] Port HttpProxyServer + RouteResolver from archive/proxy-mode
- [x] Loopback-only bind; show port in logs/state
- [ ] Phase-2 spike: sequential JNI stop then libzt start in one process

## Implementation Plan

*(Filled by `/task-1-plan` — do not invent during bootstrap beyond high-level notes.)*

### High-level notes (bootstrap)

- libzt.mdc
- If .so clash: document; isolated process last resort
- Spec: `docs/PROXY-VPN-PLAN.md`

## Execution plan (filled by /task-1-plan)

**Date:** 2026-08-23  
**Codebase snapshot:** branch `T05-libzt-http-proxy-loopback` @ T04 `dd7cf08`. Archive branch `archive/proxy-mode` has full proxy tree under `com.zerotier.pylon`. `libzt-release.aar` exists at `libzt/dist/android-any-android-release/libzt-release.aar` (arm64-v8a/armeabi-v7a/x86/x86_64 `libzt.so`). Current app: Room v3 `ZerotierBNetwork` (no `blockOutside`/`enabledInProxy`/rules fields), `ZerotierBVpnService.state.isRunning`, `startForegroundCompat` pattern in VPN service, `POST_NOTIFICATIONS` granted in manifest, FGS type `connectedDevice` only. No `proxy/` or `ztlib/` packages yet.  
**Execute model:** medium — archive port + re-package + service rewrite; bounded but multi-file.

### Context for executor

**Goal:** PROXY runtime stack: libzt node + HTTP CONNECT proxy on **127.0.0.1:0** (ephemeral). Port from `archive/proxy-mode`, re-package `com.zerotier.pylon` → `com.brukb.zerotier`, adapt to current `ZerotierBNetwork` (no `blockOutside`, no `enabledInProxy`, no allow/deny rules, no `customDnsServers`). **Do not** write `Settings.Global.HTTP_PROXY` (T06). **Do not** start while VPN node live. No orchestrator (T07) — service is startable via intent for manual test.

**Source files (archive):**
- `proxy/http/HttpProxyServer.kt` (219 lines: server + session)
- `proxy/ProxyConnection.kt` (30)
- `proxy/RouteResolver.kt` (200: LPM + `IpPrefix` + `IpClassification`)
- `proxy/dns/DnsResolver.kt` (63), `proxy/dns/NetworkDnsResolver.kt` (97)
- `zt/ZeroTierNodeManager.kt` (225), `zt/ZtModels.kt` (27), `zt/ZtNetworkQuery.kt` (62)
- `service/PylonService.kt` (457) → rewrite as `proxy/ProxyModeService.kt`

**Skip:** `ProxyRulesEngine.kt`, `socks5/`, `SystemProxyManager.kt` (T06), all archive UI/data/prefs.

**Invariants (libzt.mdc / android-http-proxy.mdc):**
- Bind `InetSocketAddress("127.0.0.1", 0)` — never `0.0.0.0`
- No `blockOutside` — outside ZT → plain `java.net.Socket` uplink
- Never start libzt while JNI `Node` alive — refuse start when `ZerotierBVpnService.state.value.isRunning`
- Same `filesDir` identity home as JNI (spec §7.2)
- Listen first; `Settings.Global` write is T06

### Steps

1. **Gradle** — `app/build.gradle.kts`: add `implementation(files(rootProject.file("libzt/dist/android-any-android-release/libzt-release.aar")))`. No `zerotier.properties` indirection.  
   → verify: `:app:assembleDebug` compiles; `com.zerotier.sockets.ZeroTierSocket` resolvable (write a throwaway reference or wait for step 3 compile).

2. **Port `ztlib/`** (new package `com.brukb.zerotier.ztlib`): copy `ZeroTierNodeManager.kt`, `ZtModels.kt`, `ZtNetworkQuery.kt`; re-package; replace `PylonNetwork` param in `join(networkId, config)` with `ZerotierBNetwork` (uses `allowManaged/allowGlobal/allowDefault` — same names). Keep everything else byte-close to archive.  
   → verify: `:app:compileDebugKotlin`.

3. **Port `proxy/`** (package `com.brukb.zerotier.proxy`):
   - `ProxyConnection.kt` — as-is (re-package).
   - `RouteResolver.kt` — adapt: `updateNetwork(config: ZerotierBNetwork, status: ZtNetworkStatus)`; drop `enabledInProxy` check (caller filters `isEnabled`); drop `blockOutside` branches (`resolveHost`/`resolveIpString` always fall through to `useZeroTier=false, block=false`); keep `RouteDecision` minus `block` field **or** keep field always false (prefer: keep field, always false — smaller diff); keep `IpPrefix`, `IpClassification`, LPM + `allowManaged/allowDefault/allowGlobal` route filters.
   - `dns/DnsResolver.kt` — adapt: drop `customDnsServers` branch (use `status.dnsServers` only); keep `allowDns` gate.
   - `dns/NetworkDnsResolver.kt` — as-is.
   - `http/HttpProxyServer.kt` — remove `rulesEngine` param + `isAllowed` checks (keep `decision.block` check gone with rules engine — remove both); `networkLookup: (Long?) -> ZerotierBNetwork?`; port ctor param `port: Int` stays, caller passes 0; expose `val boundPort: Int get() = serverSocket?.localPort ?: -1` (archive only had `listenPort` = ctor arg — ephemeral needs actual bound port).  
   → verify: compiles; `RouteResolverTest` below green.

4. **`proxy/ProxyModeService.kt`** — rewrite of `PylonService` (do not copy wholesale):
   - `Service` (not LifecycleService — no lifecycle-service dep in current tree; plain `Service` + own scope)
   - Companion: `ACTION_START`, `ACTION_STOP`, `EXTRA_FORCE_DEBUG` (bool, default false), `state: StateFlow<ProxyServiceState>`, `fun start(context, forceDebug: Boolean = false)`, `fun stop(context)`
   - `ProxyServiceState` data class: `isRunning`, `httpProxyPort: Int?`, `nodeId: String?`, `statusMessage: String`, `lastError: String?`
   - `onStartCommand(START)`: if `ZerotierBVpnService.state.value.isRunning` **and** not `EXTRA_FORCE_DEBUG` → set state error `"VPN active — stop VPN before proxy"`, `stopSelf()`, return. (Mutex guard.)
   - Start sequence: `startForegroundCompat` (copy pattern from `ZerotierBVpnService` lines 700–710; own `NOTIFICATION_ID` 5919814, channel `zerotierb_proxy`) → `ZeroTierNodeManager(filesDir.absolutePath)` init → start → join each `networkRepository.getAll().filter { it.isEnabled }` → wait ready → `routeResolver`/`dnsResolver.updateNetwork` → `HttpProxyServer(port = 0, …)` start → read `boundPort` → `preferences.setLastHttpProxyPort(boundPort)` → state `httpProxyPort = boundPort`.
   - Stop: http stop → leave all → node stop → `stopForeground` → `stopSelf`.
   - `onDestroy`/`onTaskRemoved`: launch stop (no Global write — T06).
   - Join/leave runtime intents: **skip** (T07 orchestrator owns membership changes).
   → verify: `:app:assembleDebug`; lint.

5. **Manifest** — add:
   ```xml
   <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
   <service android:name=".proxy.ProxyModeService" android:exported="false"
       android:foregroundServiceType="dataSync" />
   ```
   → verify: manifest merger OK in assemble.

6. **Strings** — add `notification_proxy_channel_name` ("ZerotierB Proxy"), `notification_proxy_text` ("Proxy on 127.0.0.1:%1$d"). Reuse `notification_title`.  
   → verify: lint.

7. **Tests** — `app/src/test/java/com/brukb/zerotier/proxy/`:
   - `RouteResolverTest` — port the LPM logic tests fresh: assigned addr /32 match → ZT; managed route longer prefix wins over shorter across two nets; `allowManaged=false` → routes ignored (assigned still match); default route `0.0.0.0/0` included only when `allowDefault`; global route excluded when `!allowGlobal`; private route included regardless of `allowGlobal`; no networks → `useZeroTier=false`; `routePriority` — **not in archive resolver** (spec §6.3 tie-break: same prefix → lower `routePriority` wins): add `routePriority` to `NetworkRoutes` from `ZerotierBNetwork` and tie-break in `resolveIpString` (equal prefixLength → lower priority). Test the tie.
   - `IpPrefixTest` — v4 /24 contains/bounds, v6 /64, mismatched family false, host bits set (parse `10.1.2.3/24` contains `10.1.2.9`).
   - No Robolectric. `ZeroTierSocket` must not load in tests — keep resolver free of socket imports.
   → verify: `:app:testDebugUnitTest`.

8. **`make verify`** → record.

### Tests to add

| Case | Expect |
|------|--------|
| assigned 10.1.0.5/32, dest 10.1.0.5 | useZeroTier, that net |
| two nets: 10.0.0.0/8 vs 10.1.0.0/16, dest 10.1.2.3 | /16 net (LPM) |
| same prefix both nets, priorities 5 vs 1 | priority 1 net |
| allowManaged=false, dest only in managed route | outside |
| 0.0.0.0/0 route, allowDefault=false | outside; allowDefault=true → ZT |
| public managed route, allowGlobal=false | outside |
| private managed route, allowGlobal=false | ZT |
| empty resolver | useZeroTier=false, block=false |
| IpPrefix v4/v6 parse + contains | per table in step 7 |

### Verify commands

- `./gradlew :app:testDebugUnitTest --console=plain`
- `make verify`

### Risks / pitfalls

- **Native coexistence unknown** until device: `libzt.so` + `libZeroTierOneJNI.so` both in APK. Load-time clash possible but unlikely (distinct symbols); runtime clash only if both nodes started — guarded by `isRunning` check. Document outcome in Verification after manual test (T05 AC includes Termux curl — human runs it).
- `ZeroTierNodeManager` uses `Executors.newSingleThreadExecutor` daemon thread — fine in service scope; do not move to Main.
- `filesDir` shared with JNI `networks.d` — libzt writes its own `networks.d` entries on join; spec accepts this (same identity home). Do not point libzt elsewhere.
- `HttpProxyServer` ctor takes `port` — pass **0**, then read `serverSocket.localPort` after `bind`. Archive `listenPort` returns ctor arg — useless with 0; add `boundPort`.
- `ZerotierBNetwork.networkIdLong()` exists (T02) — reuse; do not copy `sanitizeHex`.
- Keep `routePriority` tie-break **inside** resolver (spec §6.3); archive lacked it.
- No `Settings.Global` import anywhere in this task.
- No Shizuku dep yet (T06).
- `ProxyModeService` must not reference `SystemProxyManager`.

### Out of scope

- `SystemProxyManager` / `HTTP_PROXY` write / Shizuku (T06)
- ConnectionOrchestrator / swap / exclusive-start enforcement beyond the `isRunning` refuse guard (T07)
- VPN single-net filter (T07)
- AUTO observer wiring (T08)
- UI (port display, mode control) (T09)
- SOCKS5, ProxyRulesEngine, custom DNS servers field

### Execute model recommendation

- **medium** — ~1,100 lines archive port across 9 files + service rewrite + resolver tie-break addition; mechanical but wide. Not large: every file's source and target shape is specified above.

## Test Plan

- Prove this layer without later layers.
- Commands: `make verify` (lint + unit tests + assembleDebug)
- New Kotlin: tests required

## Acceptance Criteria

- [ ] Termux curl --proxy 127.0.0.1:PORT reaches a ZT HTTP service when proxy mode forced in debug
- [ ] LAN IP:PORT refused
- [x] make verify green
- [x] Tests added/updated for new behavior
- [x] Full lint + test verify suite green
- [x] Verification commands recorded and passing
- [x] No secrets committed

## Verification

- `make verify` — **PASS** (2026-08-23): lintDebug, testDebugUnitTest (app + core), assembleDebug
- `./gradlew :app:testDebugUnitTest --console=plain` — **PASS** (RouteResolverTest 9 cases, IpPrefixTest 4, LoopbackBindTest 1)
- Proxy binds `127.0.0.1:0` via `HttpProxyServer`; `boundPort` exposed; `setLastHttpProxyPort` on start
- VPN mutex: refuses start when `ZerotierBVpnService.state.isRunning` unless `EXTRA_FORCE_DEBUG`
- No `Settings.Global.HTTP_PROXY` write
- Native coexistence (libzt + JNI .so in APK): **not device-tested** — manual spike deferred to human AC

## Files Modified

- `app/build.gradle.kts` — libzt AAR dependency
- `app/src/main/AndroidManifest.xml` — `FOREGROUND_SERVICE_DATA_SYNC`, `ProxyModeService`
- `app/src/main/res/values/strings.xml` — proxy notification strings
- `app/src/main/java/com/brukb/zerotier/data/NetworkRepository.kt` — `getAll()`
- `app/src/main/java/com/brukb/zerotier/ztlib/` — `ZtModels.kt`, `ZtNetworkQuery.kt`, `ZeroTierNodeManager.kt`
- `app/src/main/java/com/brukb/zerotier/proxy/` — `ProxyConnection.kt`, `RouteResolver.kt`, `ProxyServiceState.kt`, `ProxyModeService.kt`, `dns/`, `http/HttpProxyServer.kt`
- `app/src/test/java/com/brukb/zerotier/proxy/` — `RouteResolverTest.kt`, `IpPrefixTest.kt`, `LoopbackBindTest.kt`

## Manual test (for humans)

**Prereq:** At least one enabled ZT network in app DB; VPN stopped (or use `force_debug` only for JNI→libzt spike).

```bash
make verify
./gradlew :app:installDebug

# Start proxy via exported activity (service is not exported)
adb shell am start -n com.brukb.zerotier/.ui.MainActivity \
  --es zerotierb_action start_proxy

# Broader logcat if proxy fails early
adb logcat -d -s MainActivity ProxyModeService ZeroTierNodeManager | tail -30
# Expect: "HTTP proxy on 127.0.0.1:<PORT>"

# Termux on device — ZT HTTP target (replace PORT + ZT host)
curl -v --proxy 127.0.0.1:PORT http://<zt-service-ip>/

# LAN bind refused (replace PHONE_LAN_IP + PORT from logcat)
curl -v --connect-timeout 3 --proxy PHONE_LAN_IP:PORT http://example.com/
# Expect: connection refused / timeout — not reachable off-loopback

# Stop
adb shell am start -n com.brukb.zerotier/.ui.MainActivity \
  --es zerotierb_action stop_proxy
```

**Success:** logcat shows node online + loopback port; Termux curl reaches ZT HTTP via `127.0.0.1:PORT`; LAN IP proxy fails. Notification shows proxy port.

**JNI→libzt spike (optional):** stop VPN, then start with `--ez force_debug true` — document whether both `.so` coexist without load crash.

## Learnings

- Archive port `listenPort` useless with ephemeral bind — read `serverSocket.localPort` after bind.
- Spec `routePriority` tie-break added to resolver (archive lacked it).
- `ProxyModeService` FGS `dataSync`; VPN mutex via `ZerotierBVpnService.state.isRunning`.
- libzt AAR + JNI `.so` both in APK — compiles; device coexistence needs manual spike.
- Port cached in `AppPreferences.setLastHttpProxyPort` for T06 Global write.

## Reality notes

*(Amended by upstream `/task-3-complete` if prior tasks changed assumptions)*

### From T04 close-out

- Classifier/debounce land in `connection/` (`LinkClassifier`, `LinkDebouncer`, `PhysicalLinkSelector`). Callback shell: `system/LinkNetworkCallback` — **not registered** until T08.
- Strip our VPN via scan (`PhysicalLinkSelector`); do not call missing public `getUnderlyingNetworks`.
- `ZerotierBVpnService` sets `setUnderlyingNetworks` on establish; `state.isRunning` for “ours”.
- Exclusive stack mutex still required for libzt vs JNI (T05 spike / T07).
