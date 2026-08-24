# AGENTS.md — ZerotierB (ZeroTier-Pylon)

Android ZeroTier client with two **exclusive** runtimes: **PROXY** (libzt + loopback HTTP proxy, many ZT nets) and **VPN** (JNI `VpnService` TUN, one main ZT net). Product spec: `docs/PROXY-VPN-PLAN.md` (read §3 locked decisions before changing behavior). Domain rules for agents live in `.cursor/rules/*.mdc` (also reachable via `.agents/rules`).

## Build / verify

- `make verify` = lint + unit tests + assembleDebug (pre-commit via lefthook). Requires JDK 17, `ANDROID_HOME`, NDK 25.1.8937393 (`:core`) and NDK 28.2.13676358 (`libzt/pkg/android`).
- libzt AAR: `cd libzt && ANDROID_HOME=$ANDROID_HOME ./build.sh android-aar release` → `libzt/dist/android-any-android-release/libzt-release.aar` (**gitignored local artifact** — after pulling new libzt source you must rebuild it or `:app` links a stale one).
- Toolchain is pinned: Kotlin 2.0.21, AGP 8.7.3, Compose BOM 2024.12.01, compileSdk 35, minSdk 26. Do not bump as drive-bys.

## Hard invariants (violate these and things break subtly)

- **One ZeroTier identity, never two live nodes.** libzt (UDP 9993) and the JNI VPN node (UDP 9994) share `filesDir`. Two live nodes with the same identity make roots/controller flap paths between sockets → the VPN join sits in `REQUESTING_CONFIGURATION` forever while proxy mode "works". Every swap must: stop the other stack (including in-flight starts), await full stop, wait until its UDP port is bindable again (spec §7.2), only then start the next stack. If a stop times out, **abort the swap** — never "proceed anyway".
- HTTP proxy binds **127.0.0.1 only**. PROXY is not a kill-switch.
- Don't `VpnService.prepare()` from `NetworkCallback`; don't prompt for VPN consent from background.

## Lessons learned (state/lifecycle — app)

These came from real bugs; preserve the patterns.

- **`isRunning` flags set asynchronously are invisible to fast-path checks.** Both services flip state after `start()` returns (VPN in `onStartCommand`, proxy in an IO coroutine). A `stopAndAwait` that early-returns on `!isRunning` skips sending STOP to an in-flight start → split-brain. Fix pattern: synchronous **start tokens** (`AtomicLong startCounter` + `stoppedToken`); a stop supersedes any earlier start; treat `isRunning || startRequested` as active everywhere.
- **A stop that can take minutes breaks every swap.** `startProxy()` held its mutex through joins with 120 s `waitForNetworkReady` each; STOP queued behind it, the orchestrator timed out and started the VPN anyway. Long waits need **abort predicates** checked every ~100 ms, and bounded per-item timeouts.
- **Record "applied" only when reality matches.** If `lastApplied = plan` is set when intents are fired (not when the stack is confirmed up), identical later plans no-op and failures become sticky (mode toggles "do nothing"). Skip apply only when `plan == lastApplied` **and** live stack state matches; on failure leave `lastApplied` stale so the next refresh retries (self-healing).
- **`Service.onDestroy` must not launch cleanup on a scope it then cancels.** `scope.launch { cleanup() }; job.cancel()` kills the cleanup before it dispatches → stale system HTTP proxy + zombie libzt node. Use a detached scope for final cleanup.
- **FGS 5-second rule:** call `startForeground()` synchronously in `onStartCommand`. Any early-return path (superseded start, refusal) that skips it crashes with `ForegroundServiceDidNotStartInTimeException`.
- **Never parse proxy request headers with `BufferedReader` and then relay the raw socket.** The reader's read-ahead buffer swallows body bytes → every POST/PUT through the proxy is corrupted (form logins 500, clients show the redirect/login page). Parse byte-exact to CRLF-CRLF from a `BufferedInputStream` and relay from the same stream. Tell: plain POST fails but an `Expect: 100-continue` POST works.

## Lessons learned (libzt / lwIP — native)

- **ZeroTierOne applies managed routes only at OS level** (`osdep/ManagedRoute.cpp`). libzt's lwIP has no OS — off-subnet prefixes need `LWIP_HOOK_IP4_ROUTE` (pick netif by longest-prefix) + `LWIP_HOOK_ETHARP_GET_GW` (off-link dest → resolve the route's `via` on the overlay; via-less → resolve dest directly), with IPv6 parity via `LWIP_HOOK_IP6_ROUTE` + `LWIP_HOOK_ND6_GET_GW`. Implemented in `libzt/src/Routing.{hpp,cpp}` fed by `NodeService::rebuildRouteCache()`.
- **Route policy = `OneService::checkIfManagedIsAllowed`** (whitelist → `allowDefault` for /0 → `allowGlobal` for global scope → reject NONE/MULTICAST/LOOPBACK/LINK_LOCAL). Ported verbatim into libzt; keep in sync. The app's `RouteResolver` mirrors it in Kotlin.
- **`zts_connect` retries fresh connects for the entire timeout** on any error. A full-timeout hang ending in `-1` means *no route / stack problem*, not a slow peer.
- Gateway semantics (same as `TunTapAdapter` in VPN mode): for off-subnet destinations, ARP/ND for the route's **`via`**, not the destination.
- Rebuild-before-delete: refresh the route cache **before** freeing a `VirtualTap` so the hooks never hand out a dead netif.

## Native build pitfalls

- CMake `file(GLOB …)` is evaluated at configure time: **new `.cpp` files are silently skipped** → undefined-symbol link errors. Wipe `libzt/pkg/android/app/.cxx` (and `libzt/cache/android-*`) when adding/removing sources.
- `libzt/build.sh` now respects an existing `ANDROID_HOME` (was hardcoded `/usr/lib/android-sdk`).

## Style / upstream discipline

- The libzt fork is **not** format-clean. Never run whole-file clang-format on it: use **clang-format-11** with `--lines=start:end` restricted to your ranges, and keep upstream-bound diffs 100% intentional (no drive-by reflows). A pinned clang-format 11 venv exists at `/tmp/opencode/cf-venv` (recreate with `pip install clang-format==11.0.1`).
- `.clang-format` (LLVM base, Stroustrup braces, 4-space) governs libzt; Kotlin/Android side follows existing project style.

## Debugging recipe (adb)

- Proxy port is ephemeral: `adb shell settings get global http_proxy` → `adb forward tcp:PORT tcp:PORT` → `curl --proxy http://127.0.0.1:PORT …`.
- **`curl: (52) Empty reply` in ~2 ms** = the forwarded port is stale (adbd can't connect), re-read the setting — not a proxy bug. A 10 s hang → 502 = ZT connect failing (see libzt notes).
- Useful logs: `HttpProxySession` (`route host:port -> useZeroTier=… reason=…`, `zt connect … nodeOnline=… transportReady=…`), `ProxyModeService` (`routes <netid>: assigned=[…] managed=[…]`), `ConnectionOrchestrator` (`apply <runtime>: <reason>`).
- Debug intents: `adb shell am start -n com.brukb.zerotier/.ui.MainActivity --es zerotierb_action <apply_mode|stop_all|start_proxy|stop_proxy|grant_secure_settings> [--es mode vpn|proxy|off|auto]`.

## Repo layout notes

- `app/` Android app · `core/` ZeroTier JNI (`com.zerotier.sdk`) · `libzt/` submodule (fork `brukberhane/libzt`, remotes: `twisteroid`, `upstream/zerotier`) · `docs/PROXY-VPN-PLAN.md` spec · `planning/phases/` Turboplan tasks.
- `.agents/skills` and `.agents/rules` are **symlinks** into `.cursor/` — edit the real files in `.cursor/`, never through the links.
