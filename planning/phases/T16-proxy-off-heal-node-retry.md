# T16 — PROXY OFF heal + abortable node retry (A+C+2+4)

**Status**: Done  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T15  
**Next**: T17  
**Layer**: L6

## Description

Kindle log `zerotierb-logs (2).txt` (2026-09-04): Doze resume called `node.start()`, logged `zts_node_start result=0`, **never** logged `node UP`. `startStopMutex` stayed held. User tapped global OFF: `STOP received` many times, **zero** `stopProxy begin`. After 15s: `Proxy did not stop in time — aborting swap`. `SystemProxyManager.disable()` never ran (it lives inside `stopProxy()` behind that mutex). `HTTP_PROXY` stayed loopback. Captive-portal hosts (`tabletcaptiveportal.com`) 502'd through the zombie proxy.

This task implements the locked design **A+C+2+4**:

- **A** — Clear `Settings.Global.HTTP_PROXY` as soon as OFF/swap-away is requested, **before** waiting for libzt to die.
- **C** — Node-up / join waits must be **real** timeouts (suspend `delay`, not blocking `zts_util_delay` on the single-thread `libzt-node` dispatcher). STOP must abort those waits.
- **2** — While PROXY is wanted (`isRunning` and not Doze-paused), keep retrying: NODE_UP miss → `node.stop()` + backoff + `start()`; join miss → re-`join()` without tearing HTTP. Backoff 1s → 5s → 15s → cap 30s.
- **4** — `ConnectivityManager.NetworkCallback` `onAvailable` / INTERNET capability change **kicks** the retry immediately (cancels the current backoff sleep). Do **not** wait for `NET_CAPABILITY_VALIDATED`.

**Captive portal (must not regress):** Android/Kindle captive detection is HTTP to hosts like `tabletcaptiveportal.com` / `connectivitycheck.gstatic.com`. With Global `HTTP_PROXY=127.0.0.1:port`, those requests only succeed if **our listen is up**. If listen is down (or never bound) while Global still points at us, portal **and** all HTTP fail → Wi-Fi never validates → option 4 never fires. Therefore: bind HTTP + `enable()` **before** waiting for NODE_UP; node retry must **not** call `httpProxy.stop()` or `disable()`. VALIDATED-based kicks are opportunistic; backoff (2) is the guarantee because portal/VALIDATED often never happens while we are the system proxy and uplink DNS is still dead.

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| 2026-09-04 | planned | — | Planned | A+C+2+4 cold-execute plan from Kindle stop-timeout | |
| 2026-09-04 | complete | InProgress | Done | /task-3-complete; verify green; dialectic encoded | agent |

## Requirements

- [x] OFF/VPN swap clears Global HTTP_PROXY even if `stopAndAwait` times out
- [x] `zts_util_delay` loops in `ZeroTierNodeManager.start` / `waitForNetworkReady` gone; waits abortable
- [x] `startStopMutex` not held across NODE_UP / join waits
- [x] HTTP listen + Global proxy up before NODE_UP wait; stay up across node stop/start retries
- [x] Background retry while PROXY wanted; ConnectivityManager kick; Doze pause still stops the node only
- [x] Tests for backoff + abortable wait helper; `make verify`

## Implementation Plan

See Execution plan below. Do not invent extra features.

## Execution plan (filled by /task-1-plan)

**Date:** 2026-09-04  
**Codebase snapshot:** `ProxyModeService.startProxy` holds `startStopMutex` through `nodeManager.start()` (NODE_UP wait) then HTTP bind then joins (`JOIN_READY_TIMEOUT_MS = 30_000`). `ZeroTierNodeManager` uses `Executors.newSingleThreadExecutor` named `libzt-node` via `withNode {}`. Waits call `ZeroTierNative.zts_util_delay` **inside** `withNode` + `withTimeoutOrNull` — timeout cannot run while that thread is blocked, and `NODE_UP` JNI callbacks that need the same dispatcher starve. `resumeNodeFromDoze` uses `startToken = 0L`; `isStartSuperseded(0)` is **always false** (`token != 0L && …`). `applyOff()` does **not** call `SystemProxyManager.disable()`; `applyVpn()` does. `stopProxy()` logs `stopProxy begin` as first line — Kindle dump has none, so STOP never entered `stopProxy()`.  
**Execute model:** medium

### Context for executor

**Goal:** User can turn PROXY off (or swap to VPN) and get the internet back even when libzt is wedged after Doze. While they **want** PROXY, the HTTP proxy stays listening so captive portal and app HTTP can use uplink (`RouteResolver` → `useZeroTier=false` for public hosts); libzt is started/joined in a retry loop until NODE_UP + transport-ready or STOP/Doze.

**Key files (edit these; do not drive-by others):**

| Path | Why |
| ---- | --- |
| `app/src/main/java/com/brukb/zerotier/connection/ConnectionOrchestrator.kt` | A: `applyOff` + `stopProxyStack` disable Global first |
| `app/src/main/java/com/brukb/zerotier/ztlib/ZeroTierNodeManager.kt` | C: abortable waits **off** the `libzt-node` thread |
| `app/src/main/java/com/brukb/zerotier/proxy/ProxyModeService.kt` | Bind HTTP first; mutex scope; retry job; CM callback; STOP abort |
| `app/src/main/java/com/brukb/zerotier/proxy/NodeRetryPolicy.kt` | **New.** Pure backoff. Unit-test without Robolectric |
| `app/src/test/java/com/brukb/zerotier/proxy/NodeRetryPolicyTest.kt` | **New.** |
| `app/src/test/java/com/brukb/zerotier/ztlib/AbortablePollTest.kt` | **New.** Fake clock/poll for C (no native) |
| `.cursor/rules/libzt.mdc` | **Do not edit in execute.** Dialectic in `/task-3-complete` only. The OPEN row `stopProxy stalls after link loss` is this bug. |
| `docs/GAPS.md` / `AGENTS.md` | One-line heal note optional; not required for AC |

**Invariants (rules — do not violate):**

- HTTP bind **127.0.0.1 only**; enable order: listen → `putString(HTTP_PROXY, 127.0.0.1:port)` (`.cursor/rules/android-http-proxy.mdc`).
- Stop timeout still **aborts starting the other ZeroTier stack** (do not start JNI while libzt UDP 9993 may be live) — `.cursor/rules/connection-orchestrator.mdc` `Stack swap races`. A only guarantees **Global proxy cleared**, not “proceed to VPN anyway.”
- Never libzt + JNI together. Never `InetAddress.getAllByName` on CONNECT DNS. No APN writes. No `ProxyRelay` pool change. No AGP bump.
- `resolve()` DNS stays sync. No `runBlocking` on main.
- Doze: `pauseNodeInDoze` still **stops the native node** and keeps HTTP listen (existing). Retry loop must **not** run while `nodePausedForDoze`.

**Bug facts (do not rediscover):**

1. `withTimeoutOrNull { while (…) { zts_util_delay(50) } }` on a single-thread dispatcher **never times out**. NODE_UP wait is specified as 10s (`NODE_UP_TIMEOUT_MS`) but hung **hours**.
2. Event log `node UP` is emitted from `ZeroTierEventListener` on JNI callback. Kindle: start returned 0, **no** `node UP` → either events starved (dispatcher busy in delay) or native never came up. Retry **stop+start** (2), do not infinite-wait (that was option 1, rejected).
3. `isStartSuperseded(0L)` never true → Doze resume / poke-START joins cannot abort on STOP. Abort must key off `!_state.value.isRunning` after STOP sets `isRunning=false` **without waiting for mutex**.
4. Captive portal uses the **system HTTP proxy**. Listen down + Global set = portal dead = no VALIDATED. Kick on `onAvailable`, not only VALIDATED.

---

### Locked behavior

#### A — Disable Global HTTP_PROXY first

In `ConnectionOrchestrator.kt`:

1. `applyOff()` becomes:

```kotlin
private suspend fun applyOff() {
    SystemProxyManager(context, preferences).disable()
    stopProxyStack()
    stopVpnStack()
}
```

Same `SystemProxyManager(context, preferences).disable()` already used in `applyVpn` (line 180). `disable()` is suspend and calls `disableBlocking()`.

2. `stopProxyStack()` — **still** throw on `stopAndAwait` false (do not start VPN). **Additionally** call `disable()` **before** `stopAndAwait`, so even the wait window has Global cleared:

```kotlin
private suspend fun stopProxyStack() {
    SystemProxyManager(context, preferences).disable()
    if (!ProxyModeService.stopAndAwait(context)) {
        // existing log + throw
        throw IllegalStateException("Proxy did not stop in time — aborting swap")
    }
}
```

Yes, `applyOff` then double-disables. Harmless (`disableBlocking` restores `:0` / saved). Prefer duplicate disable over a path that skips it.

3. `applyVpn` already disables then `stopProxyStack()` — after (2) that is disable ×2. Leave `applyVpn` as-is (do not “clean up” the extra call).

4. `ProxyModeService` ACTION_STOP / `stopProxy`: Global disable must happen **even if `startStopMutex` is held by resume**. Concrete:

In `onStartCommand` when `ACTION_STOP`:

```kotlin
if (intent?.action == ACTION_STOP) {
    AppLog.i(TAG, "STOP received")
    markStopped()
    systemProxyManager.disableBlocking()
    updateState {
        copy(
            isRunning = false,
            statusMessage = "Stopping...",
            systemProxyActive = false,
        )
    }
    scope.launch {
        startStopMutex.withLock { stopProxy() }
    }
    return START_NOT_STICKY
}
```

`stopProxy()` still calls `disable()` again (idempotent) then tears HTTP + node. First lines of `stopProxy` stay `AppLog.i(..., "stopProxy begin ...")`.

`isRunning = false` **before** mutex is what makes wait loops abort (C+2) while resume still holds the mutex for a native call.

`stopAndAwait` currently waits `isRunning || inFlightStarts > 0`. If we set `isRunning=false` immediately, `stopAndAwait` may return **true before `stopProxy()` finishes** → orchestrator thinks stack is dead while libzt still running → **VPN start split-brain**. Prevent that:

**Change `stopAndAwait` success condition** to wait until the service has finished teardown, not merely `!isRunning`.

Add companion:

```kotlin
private val stopGate = AtomicInteger(0) // or AtomicBoolean stopping + isFullyStopped
```

Simplest pattern matching existing tokens:

```kotlin
@Volatile private var stopEpoch = 0L
private val fullyStopped = AtomicBoolean(true)

fun stop(...) {
    markStopped()
    fullyStopped.set(false)
    // startService ACTION_STOP
}

suspend fun stopAndAwait(...): Boolean {
    if (fullyStopped.get() && !state.value.isRunning && !startRequested) return true
    stop(context)
    val stopped = withTimeoutOrNull(timeoutMs) {
        while (!fullyStopped.get()) delay(50)
        true
    }
    ...
}
```

Set `fullyStopped = true` at the **end** of `stopProxy()` (after `nodeManager.stop()`, HTTP stop, `stopSelf`), and at the early-return paths of `finishSupersededStart` / refused start. Set `fullyStopped = false` when a start actually begins binding (when `isRunning` becomes true).

Initial: `fullyStopped=true`. After successful HTTP bind: `fullyStopped=false` until next completed `stopProxy`.

Do **not** treat `!isRunning` as “UDP 9993 free.” Orchestrator still `awaitUdpPortReleased(9993)` after a **successful** `stopAndAwait`. If stop times out, throw (no VPN start). Global is already `:0` (A).

#### C — Abortable waits, not on `libzt-node`

**Do not** call `kotlinx.coroutines.delay` or `withTimeoutOrNull` **inside** `withNode { }` if the body also does blocking native work. `withNode` = `withContext(libzt-node dispatcher)`.

Refactor `ZeroTierNodeManager.start`:

1. Keep native `node.start()` **inside** `withNode` (short).
2. Move the NODE_UP poll **outside** `withNode`.

Add a private helper in the same file (or a small internal function in a new `AbortablePoll.kt` under `ztlib/` if that makes testing easier — prefer **one new testable file** `app/src/main/java/com/brukb/zerotier/ztlib/AbortablePoll.kt`):

```kotlin
suspend fun pollUntil(
    timeoutMs: Long,
    periodMs: Long = 50L,
    shouldAbort: () -> Boolean,
    elapsed: () -> Long = { android.os.SystemClock.elapsedRealtime() }, // too Android-y for unit tests
    predicate: suspend () -> Boolean,
): Boolean
```

For unit tests, inject `elapsed: () -> Long` and use `delay` from coroutines (test `StandardTestDispatcher` + `advanceTimeBy`). **Do not** pass `SystemClock` into the test module’s production helper if you can pass `elapsed` defaulting to `SystemClock.elapsedRealtime()` from the Android caller.

Production helper signature (lock this):

```kotlin
// app/src/main/java/com/brukb/zerotier/ztlib/AbortablePoll.kt
package com.brukb.zerotier.ztlib

import kotlinx.coroutines.delay

suspend fun pollUntil(
    timeoutMs: Long,
    periodMs: Long,
    shouldAbort: () -> Boolean,
    nowMs: () -> Long,
    predicate: suspend () -> Boolean,
): PollUntilResult {
    val deadline = nowMs() + timeoutMs
    while (true) {
        if (shouldAbort()) return PollUntilResult.Aborted
        if (predicate()) return PollUntilResult.Yes
        if (nowMs() >= deadline) return PollUntilResult.Timeout
        delay(periodMs)
    }
}

enum class PollUntilResult { Yes, Timeout, Aborted }
```

`start()` after native start:

```kotlin
val up = pollUntil(
    timeoutMs = NODE_UP_TIMEOUT_MS, // keep 10_000L
    periodMs = 50L,
    shouldAbort = shouldAbort,
    nowMs = { android.os.SystemClock.elapsedRealtime() },
    predicate = { withNode { node.id != 0L } },
)
```

Map: `Aborted` → failure `"Node start aborted"`; `Timeout` → `"Node did not come up within ${NODE_UP_TIMEOUT_MS}ms"`; `Yes` → copy state nodeId as today.

`waitForNetworkReady`: native checks **inside** predicate via `withNode { node.isNetworkTransportReady(networkId) }`; loop **outside**. Keep `timeoutMs` parameter. `periodMs = 100L` (was 100ms delay). After `Yes`, `withNode { refreshNetworkInfo(networkId) }` as today.

**Delete** every `ZeroTierNative.zts_util_delay` from `ZeroTierNodeManager.kt`. Grep must be empty in `app/`.

`shouldAbort` for `start()` from `startProxy`: `{ isStartSuperseded(startToken) || !_state.value.isRunning }`.  
For Doze resume token 0: `!_state.value.isRunning` is the abort (STOP sets it in ACTION_STOP before mutex).

#### HTTP bind **before** NODE_UP (captive portal)

Reorder `startProxy` (currently: node start → bind HTTP → enable Global → joins).

**New order inside `startProxy` after the empty-networks / VPN-refuse / init checks:**

1. `nodeManager.initialize()` (keep; cheap; needed before start).
2. `startNetworkStatusPump()` (keep; pump already skips when `!nodeStarted`).
3. **Bind HTTP + enable Global + `isRunning=true` + notification** — copy the existing block at today’s lines 287–316. If bind fails, `fail(...)` as today (that still `stopProxy()`).
4. `fullyStopped = false` here.
5. **Release `startStopMutex` before the retry loop.** Structure:

```kotlin
startStopMutex.withLock {
    // refuse / superseded / init / bind HTTP / enable / isRunning=true
    // do NOT call nodeManager.start() here
}
ensureNodeJob = scope.launch {
    runNodeEnsureLoop(startToken, enabledNetworks)
}
```

The `onStartCommand` `ACTION_START` coroutine today is:

```kotlin
startStopMutex.withLock { startProxy(...) }
```

Change to: `startProxy` itself takes the mutex only around the critical section, **or** split `startProxy` into `startProxyLocked()` (bind) + `runNodeEnsureLoop()` launched without holding mutex.

`finally { markStartFinished() }` must run after **bind** succeeds (so `awaitProxyStarted` unblocks when HTTP is up, not when ZT is online). If bind fails, still `markStartFinished()` in `finally`.

**Captive portal lock:** `runNodeEnsureLoop` may call `nodeManager.stop()` / `start()` / `join()`. It must **never** call `httpProxy?.stop()`, `systemProxyManager.disable()`, or set `isRunning=false`. Public CONNECT/plain HTTP keeps working via uplink + netd while ZT is down.

If leftover Global `HTTP_PROXY` pointed at a **dead** port from a previous crash, binding a **new** ephemeral port then `enable(newPort)` overwrites Global (existing `enable`). Do that before node wait so portal packets hit a live listen.

#### 2 — Retry loop

New `NodeRetryPolicy` (pure):

```kotlin
object NodeRetryPolicy {
    const val NODE_UP_TIMEOUT_MS = 10_000L      // match NodeManager
    const val JOIN_READY_TIMEOUT_MS = 30_000L    // match ProxyModeService companion
    const val BACKOFF_INITIAL_MS = 1_000L
    const val BACKOFF_CAP_MS = 30_000L

    /** previous=0 → 1000; then 5000, 15000, 30000, 30000… */
    fun nextBackoffMs(previousMs: Long): Long = when {
        previousMs <= 0L -> BACKOFF_INITIAL_MS
        previousMs < 5_000L -> 5_000L
        previousMs < 15_000L -> 15_000L
        else -> BACKOFF_CAP_MS
    }
}
```

Put `NODE_UP_TIMEOUT_MS` in **one** place: `NodeRetryPolicy` and have `ZeroTierNodeManager` use `NodeRetryPolicy.NODE_UP_TIMEOUT_MS` **or** keep 10s in NodeManager and duplicate the constant in the policy comment. Prefer **NodeManager keeps 10_000L**; policy duplicates JOIN/backoff only. Do not create a cyclic import (`proxy` → `ztlib` is OK; `ztlib` must **not** import `proxy`). So backoff lives in `proxy/NodeRetryPolicy.kt`; NodeManager stays independent.

`runNodeEnsureLoop(startToken, networks: List<ZerotierBNetwork>)`:

```
backoff = 0
while (coroutine isActive && _state.value.isRunning && !nodePausedForDoze) {
    if (isStartSuperseded(startToken)) break

    // Ensure native node UP
    val id = nodeManager.state.value.nodeId
    val up = id != null && id != 0L
    if (!up) {
        nodeManager.initialize()
        val startResult = nodeManager.start(shouldAbort = { !_state.value.isRunning || isStartSuperseded(startToken) || nodePausedForDoze })
        if (startResult.isFailure) {
            AppLog.w(TAG, "node ensure start failed: ${startResult.exceptionOrNull()?.message}")
            runCatching { nodeManager.stop() }
            nodeStarted = false
            backoff = NodeRetryPolicy.nextBackoffMs(backoff)
            sleepAbortable(backoff, kick)
            continue
        }
        nodeStarted = true
        backoff = 0
    }

    // Join each configured net; skip ones already transport-ready if cheap
    var anyJoinFailed = false
    for (network in networks.toList()) {
        if (!_state.value.isRunning || nodePausedForDoze || isStartSuperseded(startToken)) break
        val jr = nodeManager.join(network.networkIdLong(), network)
        if (jr.isFailure) { anyJoinFailed = true; continue }
        val ready = nodeManager.waitForNetworkReady(
            network.networkIdLong(),
            timeoutMs = NodeRetryPolicy.JOIN_READY_TIMEOUT_MS, // 30_000
            shouldAbort = { !_state.value.isRunning || isStartSuperseded(startToken) || nodePausedForDoze },
        )
        if (ready.isSuccess) {
            applyNetworkRuntime(network, ready.getOrThrow())
            AppLog.i(TAG, "Joined ${network.networkId}")
        } else {
            anyJoinFailed = true
            AppLog.w(TAG, "Network not ready ${network.networkId}: ${ready.exceptionOrNull()?.message}")
        }
    }

    val allOk = networks.isNotEmpty() && !anyJoinFailed
        && networks.all { /* optional: status OK from nodeManager.getNetworkStatus */ true }

    if (allOk) {
        backoff = 0
        updateState { copy(statusMessage = "Proxy on 127.0.0.1:$port") } // keep existing port message
        sleepAbortable(NodeRetryPolicy.BACKOFF_CAP_MS, kick) // idle poll; kick wakes on connectivity
        // After wake: if still running, loop (re-check node.id / joins). Cheap no-op if still UP+OK.
        continue
    }

    // Joins failed but node may be UP — do NOT node.stop() (2: restart node only on NODE_UP miss)
    backoff = NodeRetryPolicy.nextBackoffMs(backoff)
    sleepAbortable(backoff, kick)
}
```

`sleepAbortable(ms, kick: MutableSharedFlow<Unit>)`:

```kotlin
suspend fun sleepAbortable(ms: Long, kick: MutableSharedFlow<Unit>) {
    withTimeoutOrNull(ms) {
        kick.first() // Unit from extraBuffer=1, DROP_OLDEST
    }
    // returns on kick OR timeout — both OK
}
```

Need `import kotlinx.coroutines.flow.first` and `MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST)`.

While sleeping, also abort if `!isRunning`: use `while` 50ms delays checking `isRunning` **or** `select` with `stop` — simplest:

```kotlin
suspend fun sleepAbortable(ms: Long, kick: MutableSharedFlow<Unit>, shouldAbort: () -> Boolean) {
    val deadline = SystemClock.elapsedRealtime() + ms
    while (SystemClock.elapsedRealtime() < deadline) {
        if (shouldAbort()) return
        val remaining = deadline - SystemClock.elapsedRealtime()
        val slice = remaining.coerceAtMost(50L).coerceAtLeast(0L)
        val kicked = withTimeoutOrNull(slice) { kick.first() }
        if (kicked != null) return
    }
}
```

`ensureNodeJob?.cancel()` at the start of `stopProxy` (after begin log) and in `pauseNodeForDoze` (before `nodeManager.stop()`). `join()` the job with `withTimeoutOrNull(2_000)` optional; cancellation + `shouldAbort` is enough.

**Doze:**

- `pauseNodeForDoze`: cancel `ensureNodeJob`; leave HTTP; `node.stop()` as today; `nodePausedForDoze=true`. **Do not hold mutex during any new wait** (today’s pause already does leave+stop only — OK if stop is bounded; if `node.stop()` can hang, that is pre-existing; do not expand scope unless you see it in logs).
- `resumeNodeFromDoze`: do **not** call `nodeManager.start()` inline holding mutex. Set `nodePausedForDoze=false`, then `ensureNodeJob = scope.launch { runNodeEnsureLoop(0L, pausedNetworks.toList()) }`. Mutex only around flag + job launch.
- `resumeFromIdleIfNeeded` when already running: `kick.tryEmit(Unit)` in addition to existing health/watchdog. If `nodePausedForDoze`, call `resumeNodeFromDoze` as above.

Replace the current `joinConfiguredNetwork` wait inside `startProxy`’s for-loop with the ensure loop. Keep `joinConfiguredNetwork` as a helper used by the loop **or** inline; do not leave a second join path.

`startToken=0` on resume: abort only via `!isRunning` / `nodePausedForDoze`.

#### 4 — ConnectivityManager kick

In `ProxyModeService`, **do not** reuse `LinkObserver` (that drives AUTO orchestrator debounce). Register a **separate** callback when HTTP is up; unregister in `stopProxy` / `onDestroy`.

```kotlin
private val nodeKick = MutableSharedFlow<Unit>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
)

private val nodeKickCallback = object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) {
        AppLog.i(TAG, "node-kick onAvailable")
        nodeKick.tryEmit(Unit)
    }
    override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            nodeKick.tryEmit(Unit)
        }
        // Do NOT require NET_CAPABILITY_VALIDATED — captive portal often never validates
        // while Global HTTP_PROXY points at us (portal HTTP goes through our proxy).
    }
}
```

Register: `connectivity.registerDefaultNetworkCallback(nodeKickCallback)` after HTTP bind (API 26 OK). Catch exceptions like `LinkObserver`. Unregister in `stopProxy`.

Also `nodeKick.tryEmit(Unit)` from `onBecameInteractive` / `resumeFromIdleIfNeeded` (screen on).

**Captive portal vs 4 (write this in a code comment at the callback):**

- Kindle captive check = HTTP via **our** proxy (`tabletcaptiveportal.com`). Needs **listen up**, netd DNS, uplink socket. Does **not** need libzt NODE_UP.
- If we delay HTTP bind until NODE_UP, leftover Global proxy + dead port → portal fails → `VALIDATED` never comes → kick never comes → retry only via backoff. That is why bind-first exists.
- If listen is up but Wi-Fi has no DNS yet, portal 502s (Sep 2/Kindle). Backoff (2) still retries ZT; kick fires on `onAvailable` when the iface appears even before portal success.
- **Do not** special-case captive-portal hostnames in `RouteResolver` / DNS in this task.
- **Do not** clear Global HTTP_PROXY to “help” the portal while `plan.runtime==PROXY`. That would leak apps off-proxy. A is OFF/swap only.

#### Mutex / jobs checklist

| Work | Holds `startStopMutex`? |
| ---- | --- |
| HTTP bind, enable Global, `isRunning=true` | Yes, short |
| `runNodeEnsureLoop` waits / `pollUntil` / backoff | **No** |
| `pauseNodeForDoze` leave+stop node | Yes, no long poll |
| `stopProxy` teardown | Yes, after STOP already cleared Global + `isRunning=false` |
| `recoverProxy` (listen died) | No mutex today — keep; must not start a second ensure loop |

Cancel `ensureNodeJob` before `nodeManager.stop()` in `stopProxy` so the loop does not `start()` racing stop. Native calls stay serialized on `libzt-node`.

`onDestroy` already `disableBlocking()` then `startStopMutex.withLock { stopProxy() }` on a detached scope. Keep that. STOP path now also disables earlier.

#### Status message

While ensuring: keep HTTP port message if already bound. Optional: `statusMessage = "Proxy on 127.0.0.1:$port (ZeroTier connecting…)"` — **only if** it is a one-line change. Do not add new UI strings unless you use `strings.xml`. Prefer no UI copy change (Simplicity). `nodeLifecycle` already maps offline/starting via `publishFromNodeState`.

---

### Steps

1. Add `AbortablePoll.kt` + `AbortablePollTest.kt` (Yes / Timeout / Aborted; abort checked before predicate; `delay` advanced with `StandardTestDispatcher` + `runTest`). → verify: tests fail until helper exists, then pass.  
2. Refactor `ZeroTierNodeManager.start` / `waitForNetworkReady` to use `pollUntil`; grep `zts_util_delay` in `app/` empty. → verify: compile.  
3. Add `NodeRetryPolicy.kt` + `NodeRetryPolicyTest.kt` (0→1000→5000→15000→30000→30000). → verify: unit tests.  
4. Orchestrator A: `applyOff` + `stopProxyStack` disable first. → verify: read diff; no unit test if still uninjected (do not invent DI).  
5. `ProxyModeService`: `fullyStopped` gate; ACTION_STOP disable + `isRunning=false` before mutex; reorder bind-before-node; `runNodeEnsureLoop`; CM callback; Doze resume launches loop not inline `start()`. → verify: `make verify`.  
6. Confirm `ProxyRelay` / DNS / `RouteResolver` untouched. → verify: `git diff --stat`.

### Tests to add

**`AbortablePollTest`:**

- `predicate true immediately` → `Yes`, no abort.
- `predicate false until time passes` with `nowMs` fake + `runTest { advanceTimeBy(timeout) }` → `Timeout`.
- `shouldAbort true` → `Aborted` without needing predicate true.
- `predicate` suspends via `withContext` not required; a `var` flag flipped after `advanceTimeBy(50)` → `Yes`.

Use `kotlinx-coroutines-test` (`runTest`, `StandardTestDispatcher`). If the module already uses `runTest` elsewhere, copy that style. `app/src/test` already has JUnit 4 — keep `@Test` JUnit 4. `runTest` is OK inside `@Test`.

**`NodeRetryPolicyTest`:** table as above.

**Do not** Robolectric `AndroidUplinkDnsClient` / `ProxyModeService`. Do not add emulator tests.

Optional cheap test: `ConnectionOrchestratorTest` stays plan-equality only. Do not force orchestrator Android tests.

### Verify commands

```bash
make verify
```

(`:app:lintDebug :app:testDebugUnitTest :core:testDebugUnitTest :app:assembleDebug`)

Grep gates (executor must run):

```bash
rg -n "zts_util_delay" app/src/main
rg -n "stopProxy begin" app/src/main/java/com/brukb/zerotier/proxy/ProxyModeService.kt
```

First grep: no matches. Second: still present.

### Risks / pitfalls

- **`stopAndAwait` vs `isRunning=false`:** If you set `isRunning=false` on STOP without `fullyStopped`, orchestrator starts VPN while libzt alive. Must implement the gate.
- **`withTimeoutOrNull` inside `withNode`:** recreates the Kindle hang. Poll **outside** `withNode`.
- **Calling `node.id` off-thread:** always read it via `withNode { }` in the predicate.
- **Ensure loop vs `start()` reuse `-2`:** existing `ZTS_ERR_SERVICE` reuse stays. If id stays 0 after reuse, treat as NODE_UP timeout → `stop()` then backoff → `start()`.
- **Join timeout must not `node.stop()`** — only NODE_UP failure stops native node.
- **Double `HttpProxyServer`:** `start()` already stops `activeServer`. Ensure loop must not construct a new server.
- **Kindle API 28:** `registerDefaultNetworkCallback` exists. No `registerDefaultNetworkCallback(executor, cb)` API 31-only overload — use the 1-arg version (callback on app thread; only `tryEmit`, no mutex).
- **Captive portal:** do not disable Global while PROXY wanted; do not wait for VALIDATED.
- **FGS 5s:** keep `startForeground()` in `onStartCommand` as today. Bind HTTP still on IO coroutine; that is existing.

### Out of scope

- DIY DHCP DNS; DNS taxonomy; `RouteResolver` LAN overlap (item 21)
- Bypass list for captive-portal hostnames
- Changing `pickUplink` / VPN TUN DNS
- Making `node.stop()` timeout (unless you hit it; do not preempt)
- Editing `.cursor/rules/` (task-3 dialectic)
- Commit / push
- `ProxyRelay` thread pool
- Bound HTTP off loopback

### Execute model recommendation

- **medium** — several lifecycle order constraints (fullyStopped vs isRunning, mutex, bind-first) but fully specified. Not JNI/native.

## Test Plan

- Unit: `AbortablePoll` + `NodeRetryPolicy` as above.
- `make verify`.
- Manual (human / Kindle): PROXY on, wait until `ZeroTier paused (Doze)` or airplane then Wi-Fi captive. Toggle global OFF. Expect: `STOP received`, `stopProxy begin` within 15s **or** Global `http_proxy` `:0` immediately even if stop times out. `adb shell settings get global http_proxy` not `127.0.0.1:*`. Captive portal: with PROXY **on**, listen must be up **before** ZT online (`HTTP proxy on 127.0.0.1:` log before `node UP`).

## Acceptance Criteria

- [x] `applyOff` / `stopProxyStack` call `SystemProxyManager.disable()` before `stopAndAwait`
- [x] ACTION_STOP clears Global and sets `isRunning=false` before taking `startStopMutex`
- [x] `stopAndAwait` waits on full teardown (`fullyStopped`), not merely `!isRunning`
- [x] No `zts_util_delay` in `app/`
- [x] HTTP bind + `enable()` happen before NODE_UP wait; retry does not stop listen
- [x] NODE_UP failure → stop + backoff + start; join failure → re-join without node stop
- [x] Backoff 1s/5s/15s/30s; CM `onAvailable` kicks sleep
- [x] Kick does not require `VALIDATED`
- [x] Tests added; `make verify` green
- [x] No secrets committed

## Verification

- verify tooling: `Makefile` + `lefthook.yml` + `app/lint.xml` present
- `make verify` — exit 0 (lint + unit tests + assembleDebug)
- grep `zts_util_delay` in `app/src/main` — no matches
- grep `stopProxy begin` in `ProxyModeService.kt` — present (line 539)
- compile fix: `BufferOverflow` → `kotlinx.coroutines.channels.BufferOverflow`; `runNodeEnsureLoop` uses `currentCoroutineContext().isActive`

## Files Modified

- `app/src/main/java/com/brukb/zerotier/ztlib/AbortablePoll.kt` (new)
- `app/src/test/java/com/brukb/zerotier/ztlib/AbortablePollTest.kt` (new)
- `app/src/main/java/com/brukb/zerotier/proxy/NodeRetryPolicy.kt` (new)
- `app/src/test/java/com/brukb/zerotier/proxy/NodeRetryPolicyTest.kt` (new)
- `app/src/main/java/com/brukb/zerotier/ztlib/ZeroTierNodeManager.kt`
- `app/src/main/java/com/brukb/zerotier/connection/ConnectionOrchestrator.kt`
- `app/src/main/java/com/brukb/zerotier/proxy/ProxyModeService.kt`

## Learnings

- Kindle 2026-09-04: `zts_util_delay` inside `withNode` + `withTimeoutOrNull` = timeout never fires; mutex blocks STOP; Global stuck.
- Fixed: A disable-first, C `pollUntil` off dispatcher, 2 ensure loop, 4 CM kick, `fullyStopped` gate, bind-before-NODE_UP.
- Encoded: `libzt.mdc` (stopProxy stall + dispatcher timeout), `connection-orchestrator.mdc` (disable-first + fullyStopped), `android-http-proxy.mdc` (OFF stuck + captive portal bind order).

## Manual test (for humans)

1. Install: `./gradlew :app:installDebug`
2. PROXY on, wait for `HTTP proxy on 127.0.0.1:` log **before** `node UP` (captive portal path).
3. Trigger Doze (screen off 30+ min) or airplane → Wi-Fi captive portal.
4. Toggle global **OFF** (or swap VPN).
5. **Expect immediately:** `adb shell settings get global http_proxy` → `:0` (not `127.0.0.1:*`).
6. **Expect within 15s:** logcat `stopProxy begin` (may still timeout teardown; internet must work).
7. Re-enable PROXY: node retry logs; `node UP` within backoff cycles; CM kick on Wi-Fi reconnect (no VALIDATED required).

Emergency unblock if old build: `adb shell settings put global http_proxy :0`

## Reality notes

Kindle 2026-09-04: status string `ZeroTier paused (Doze)` stayed because `resumeNodeFromDoze` never reached the statusMessage update after joins (stuck in `start()` NODE_UP poll). `nodePausedForDoze` may already have been false if hang was after that flag flip — do not use statusMessage as proof of the flag. Abort using `isRunning` after STOP.
