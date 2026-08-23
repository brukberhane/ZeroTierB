# T06 — System HTTP_PROXY + Shizuku grant

**Status**: Done  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T05  
**Next**: T07  
**Layer**: L5

## Description

SystemProxyManager: save/restore Global HTTP_PROXY. Enable only after bind. Shizuku pm grant WRITE_SECURE_SETTINGS + ADB copy. Crash/start clears stale proxy if not PROXY runtime.

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| 2026-08-23 | created | — | Pending | stub seeded by bootstrap | |
| 2026-08-23 | planned | Pending | Planned | /task-1-plan: port SystemProxyManager + Shizuku helper, stale-clear on app start | |
| 2026-08-23 | execute | Planned | InProgress | /task-2-execute T06 | |
| 2026-08-23 | complete | InProgress | Done | /task-3-complete T06 | |

## Requirements

- [x] enable after listen; disable restores
- [x] No APN writes
- [x] UI honesty: system proxy inactive without permission

## Implementation Plan

*(Filled by `/task-1-plan` — do not invent during bootstrap beyond high-level notes.)*

### High-level notes (bootstrap)

- android-http-proxy.mdc
- shizuku.mdc
- Spec: `docs/PROXY-VPN-PLAN.md`

## Execution plan (filled by /task-1-plan)

**Date:** 2026-08-23  
**Codebase snapshot:** branch `T06-system-proxy-shizuku` @ T05 `8bcda56` (+ uncommitted T05 debug tweaks: `MainActivity` debug intents, `networkStateJob`, 502 handling, peer logging — assume committed or present before execute). `ProxyModeService` binds `127.0.0.1:0`, exposes `boundPort`, persists `AppPreferences.setLastHttpProxyPort`. `AppPreferences` already has `savedHttpProxy: Flow<String?>` + `setSavedHttpProxy(String?)` (T02 foresight). `ProxyServiceState` has no system-proxy fields yet. No Shizuku dep, no `WRITE_SECURE_SETTINGS` in manifest. `settings.gradle.kts` has `mavenCentral()` (Shizuku 13.1.5 resolvable). Archive sources: `proxy/SystemProxyManager.kt` (65 lines), `system/ShizukuPermissionHelper.kt` (36 lines).  
**Execute model:** medium — two archive ports + service wiring + app-start stale-clear + pure-logic tests; fully specified below.

### Context for executor

**Goal:** After the T05 HTTP proxy binds on loopback, write `Settings.Global.HTTP_PROXY = "127.0.0.1:<port>"` so Wi-Fi/LTE apps route HTTP via the proxy. Restore the user's previous proxy (or `:0`) on stop. Grant `WRITE_SECURE_SETTINGS` via Shizuku (preferred) or documented ADB one-liner. Clear stale loopback proxy on app process start when runtime ≠ PROXY. No APN writes, ever.

**Key files:**
- Port → `app/src/main/java/com/brukb/zerotier/proxy/SystemProxyManager.kt` (from `archive/proxy-mode:app/src/main/java/com/zerotier/pylon/proxy/SystemProxyManager.kt`)
- Port → `app/src/main/java/com/brukb/zerotier/system/ShizukuPermissionHelper.kt` (from `archive/proxy-mode:.../system/ShizukuPermissionHelper.kt`)
- Edit → `proxy/ProxyModeService.kt` (wire enable after bind, disable first on stop)
- Edit → `proxy/ProxyServiceState.kt` (add `systemProxyActive`, `hasSecureSettingsPermission`)
- Edit → `ZerotierBApplication.kt` (stale-proxy clear on process start)
- Edit → `ui/MainActivity.kt` (debug intent `grant_secure_settings`)
- Edit → `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- New tests → `app/src/test/java/com/brukb/zerotier/proxy/SystemProxyManagerTest.kt`

**Invariants (android-http-proxy.mdc / shizuku.mdc / spec §8):**
1. Enable order: bind 127.0.0.1 → then `putString(HTTP_PROXY, "127.0.0.1:$port")`. Never write before listen.
2. Disable: restore saved value or `":0"`.
3. Force-stop may skip `onDestroy` → next process start clears stale proxy if runtime ≠ PROXY.
4. No APN `proxy`/`port` writes.
5. Without grant: proxy still listens; state must say `systemProxyActive=false` — never pretend Global is set.
6. Shizuku grant is optional convenience; ADB path always documented. No `su` path.

### Steps

1. **Gradle** — `app/build.gradle.kts` dependencies:
   ```kotlin
   implementation("dev.rikka.shizuku:api:13.1.5")
   implementation("dev.rikka.shizuku:provider:13.1.5")
   ```
   → verify: `./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep -i shizuku` resolves 13.1.5.

2. **Manifest** — `app/src/main/AndroidManifest.xml`:
   - Add `xmlns:tools="http://schemas.android.com/tools"` to `<manifest>` root.
   - Add permission:
     ```xml
     <uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS"
         tools:ignore="ProtectedPermissions" />
     ```
   - Add inside `<application>` (byte-close to archive):
     ```xml
     <provider
         android:name="rikka.shizuku.ShizukuProvider"
         android:authorities="${applicationId}.shizuku"
         android:enabled="true"
         android:exported="true"
         android:multiprocess="false"
         android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />
     ```
   → verify: `:app:assembleDebug` manifest merger OK; `:app:lintDebug` — if `ProtectedPermissions` fires on the provider's `android:permission` attribute, add `tools:ignore="ProtectedPermissions"` on the provider element (do NOT disable the check globally in lint.xml).

3. **Port `proxy/SystemProxyManager.kt`** — package `com.brukb.zerotier.proxy`. Byte-close to archive with these adaptations:
   - Ctor: `(context: Context, preferences: AppPreferences)` — current `AppPreferences` API is `savedHttpProxy: Flow<String?>` / `setSavedHttpProxy(value: String?)` (archive called it `saveHttpProxy` — rename call sites).
   - `hasPermission()`: archive only did `getString` in try/catch, but `Settings.Global` reads don't throw — that always returns true. **Deviation (spec §8.1 "getString / putString"):** do a no-op write round-trip instead:
     ```kotlin
     fun hasPermission(): Boolean = try {
         val cr = context.contentResolver
         Settings.Global.putString(cr, Settings.Global.HTTP_PROXY,
             Settings.Global.getString(cr, Settings.Global.HTTP_PROXY) ?: "")
         true
     } catch (_: SecurityException) { false }
     ```
   - `enable(port: Int)`: keep archive flow (save current if non-blank and not our loopback → `preferences.setSavedHttpProxy(current)`; then `putString(loopbackProxy(port))`). Gate on `hasPermission()` → `error("WRITE_SECURE_SETTINGS not granted")` inside `runCatching`.
   - `disable()`: keep archive flow (restore `savedProxy ?: preferences.savedHttpProxy.first()`, blank/`":0"` → write `":0"`; then `setSavedHttpProxy(null)`).
   - Keep `currentProxy()`, `loopbackProxy(port)`, `adbGrantCommand(packageName)` companions.
   - **Extract pure decision helpers into `companion object`** (unit-testable without `Settings`):
     ```kotlin
     fun decideValueToSaveOnEnable(current: String?, port: Int): String? =
         if (!current.isNullOrBlank() && current != loopbackProxy(port)) current else null
     fun decideRestoreOnDisable(saved: String?): String =
         if (saved.isNullOrBlank() || saved == ":0") ":0" else saved
     fun isOurLoopback(current: String?, lastPort: Int): Boolean =
         lastPort > 0 && current == loopbackProxy(lastPort)
     fun shouldClearStale(current: String?, saved: String?, lastPort: Int, proxyModeActive: Boolean): Boolean =
         !proxyModeActive && (isOurLoopback(current, lastPort) || !saved.isNullOrBlank())
     ```
     `enable`/`disable`/app-start call these; bodies stay thin wrappers over `Settings.Global`.
   → verify: `:app:compileDebugKotlin`.

4. **Port `system/ShizukuPermissionHelper.kt`** — package `com.brukb.zerotier.system`, byte-close to archive (`isAvailable()` = `Shizuku.pingBinder()`; `grantWriteSecureSettings(context)` runs `pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS` via reflected `Shizuku.newProcess(cmd, null, null)`; `check(exit == 0)`; fail closed via `runCatching`). No `Shizuku.requestPermission` flow — archive didn't have one; T09 grant card owns that (note in Risks).
   → verify: `:app:compileDebugKotlin` (reflection means no compile dep on hidden API).

5. **`ProxyServiceState`** — add:
   ```kotlin
   val systemProxyActive: Boolean = false,
   val hasSecureSettingsPermission: Boolean = false,
   ```
   → verify: compiles.

6. **`ProxyModeService` wiring**:
   - Field `private lateinit var systemProxyManager: SystemProxyManager`; init in `onCreate`: `systemProxyManager = SystemProxyManager(this, (application as ZerotierBApplication).preferences)`.
   - In `onCreate`, also `scope.launch { updateState { copy(hasSecureSettingsPermission = systemProxyManager.hasPermission()) } }`.
   - In `startProxy`, **after** `setLastHttpProxyPort(boundPort)` and the port state update (i.e. listen-first invariant already satisfied):
     ```kotlin
     systemProxyManager.enable(boundPort)
         .onSuccess {
             updateState { copy(systemProxyActive = true, hasSecureSettingsPermission = true) }
             Log.i(TAG, "System proxy set to 127.0.0.1:$boundPort")
         }
         .onFailure {
             updateState { copy(systemProxyActive = false) }
             Log.w(TAG, "System proxy not set: ${it.message}")
         }
     ```
     Failure is non-fatal — proxy keeps listening (honesty invariant 5).
   - In `stopProxy`, **first statement** after the "Stopping..." state update (spec §9 stop order):
     ```kotlin
     systemProxyManager.disable().onFailure {
         Log.w(TAG, "Failed to restore system proxy: ${it.message}")
     }
     ```
     `updateState { ProxyServiceState() }` at the end already resets both new flags; change to `ProxyServiceState(hasSecureSettingsPermission = systemProxyManager.hasPermission())` to keep the permission truth across stops.
   → verify: `:app:assembleDebug`; lint.

7. **Stale-proxy clear on process start** — `ZerotierBApplication.onCreate`, inside the existing `appScope.launch` block, after migrations:
   ```kotlin
   runCatching {
       val mgr = SystemProxyManager(this@ZerotierBApplication, preferences)
       val mode = preferences.globalMode.first()
       val current = mgr.currentProxy()
       val saved = preferences.savedHttpProxy.first()
       val lastPort = preferences.lastHttpProxyPort.first()
       if (SystemProxyManager.shouldClearStale(current, saved, lastPort, mode == GlobalMode.PROXY)) {
           mgr.disable()
           Log.i("ZerotierBApplication", "Cleared stale system proxy (mode=$mode)")
       }
   }
   ```
   Needs `import com.brukb.zerotier.proxy.SystemProxyManager`, `data.model.GlobalMode`, `android.util.Log`, `kotlinx.coroutines.flow.first`. Note: nothing sets `GlobalMode.PROXY` yet (T09 UI) — clear fires whenever our loopback/saved value exists at process start; the debug-start flow is unaffected because clear runs before the service binds + re-enables. T07 orchestrator will own the mode-aware path.
   → verify: `:app:assembleDebug`; lint.

8. **Debug grant trigger** — `MainActivity.handleDebugIntent`, add branch:
   ```kotlin
   ACTION_GRANT_SECURE -> {
       Log.i(TAG, "adb debug: Shizuku grant WRITE_SECURE_SETTINGS")
       val result = ShizukuPermissionHelper.grantWriteSecureSettings(this)
       Log.i(TAG, if (result.isSuccess) "grant ok" else "grant failed: ${result.exceptionOrNull()?.message}")
   }
   ```
   Companion: `const val ACTION_GRANT_SECURE = "grant_secure_settings"`. Import `com.brukb.zerotier.system.ShizukuPermissionHelper`.
   → verify: `:app:assembleDebug`.

9. **Tests** — `app/src/test/java/com/brukb/zerotier/proxy/SystemProxyManagerTest.kt`. Pure companion functions only; never touch `Settings.Global` (no Robolectric in this tree). Cases in table below.
   → verify: `./gradlew :app:testDebugUnitTest --console=plain`.

10. **`make verify`** → record in Verification.

### Tests to add

| Case | Expect |
|------|--------|
| `decideValueToSaveOnEnable("192.168.1.1:8080", 41275)` | `"192.168.1.1:8080"` (user proxy saved) |
| `decideValueToSaveOnEnable(null, 41275)` / `("", …)` | `null` |
| `decideValueToSaveOnEnable("127.0.0.1:41275", 41275)` | `null` (never save our own loopback) |
| `decideValueToSaveOnEnable("127.0.0.1:1111", 41275)` | `"127.0.0.1:1111"` (different port = not ours this run) |
| `decideRestoreOnDisable("192.168.1.1:8080")` | `"192.168.1.1:8080"` |
| `decideRestoreOnDisable(null)` / `("")` / `(":0")` | `":0"` |
| `isOurLoopback("127.0.0.1:41275", 41275)` | true |
| `isOurLoopback("127.0.0.1:9999", 41275)` / `(null, 41275)` / `("127.0.0.1:41275", 0)` | false |
| `shouldClearStale("127.0.0.1:41275", null, 41275, proxyModeActive=false)` | true |
| `shouldClearStale(null, "192.168.1.1:8080", 0, false)` | true (saved value = evidence we wrote) |
| `shouldClearStale("10.0.0.1:3128", null, 41275, false)` | false (someone else's proxy, no evidence) |
| `shouldClearStale("127.0.0.1:41275", null, 41275, proxyModeActive=true)` | false (PROXY runtime owns it) |
| `loopbackProxy(41275)` | `"127.0.0.1:41275"` |
| `adbGrantCommand("com.brukb.zerotier")` | `"adb shell pm grant com.brukb.zerotier android.permission.WRITE_SECURE_SETTINGS"` |

### Verify commands

- `./gradlew :app:testDebugUnitTest --console=plain`
- `make verify`

### Risks / pitfalls

- **Listen-before-write**: `enable()` call site must stay after `httpProxy.start()` + `boundPort` check in `startProxy`. Do not move it earlier "for symmetry".
- **Stop order**: `disable()` is the first teardown step (spec §9) — apps must stop routing to the port before the socket dies.
- `hasPermission()` deviation from archive (write round-trip instead of read-only) is deliberate: `Settings.Global` reads don't throw `SecurityException`, so archive's check always passed and the failure surfaced late at `putString` in `enable`. Round-trip write of the current value is a no-op when granted.
- Shizuku `newProcess` is hidden API — keep the reflection helper byte-close; any failure falls out through `runCatching` → ADB one-liner path (shizuku.mdc `newProcess hidden`).
- Helper does **not** call `Shizuku.requestPermission` — if the app isn't authorized inside Shizuku, grant fails closed. T09 grant card owns the request-permission UX; note it in T09's Reality notes at close-out.
- Manifest `ProtectedPermissions` lint: use per-element `tools:ignore`, never a global lint.xml disable.
- Stale-clear guard must never clobber a proxy we didn't write: `shouldClearStale` requires our exact loopback port OR a non-blank `savedHttpProxy` (evidence we saved the user's prior value).
- `Settings.Global.HTTP_PROXY` is honored by HttpURLConnection/WebView/most browsers; OkHttp-usually, Flutter/QUIC often not (spec §8.2). Not a defect — document in manual test expectations.
- Do not touch `BootReceiver` (VPN-only; PROXY boot re-enable is T07 orchestrator scope).
- No `Settings.Global` write anywhere except `SystemProxyManager`.

### Out of scope

- Grant UI card / Shizuku `requestPermission` flow / ADB copy button (T09)
- ConnectionOrchestrator owning enable/disable + boot re-enable when mode=PROXY (T07)
- APN `proxy=`/`port=` writes (never in v1)
- SOCKS5 listen
- Per-app proxy bypass, HTTP/3/QUIC handling

### Execute model recommendation

- **medium** (default) — two small archive ports with named adaptations, one service wiring pass, one app-start hook, pure-function tests. Every file and decision is specified above; no architecture left to discover.

## Test Plan

- Prove this layer without later layers.
- Commands: `make verify` (lint + unit tests + assembleDebug)
- New Kotlin: tests required

## Acceptance Criteria

- [ ] Wi-Fi browser uses ZT HTTP via Global when granted (manual — see Manual test)
- [ ] Force-stop then relaunch does not leave dead proxy if mode≠PROXY (manual)
- [x] Tests added/updated for new behavior
- [x] Full lint + test verify suite green
- [x] Verification commands recorded and passing
- [x] No secrets committed

## Verification

- `make verify` — **PASS** (2026-08-23): lintDebug, testDebugUnitTest (app + core), assembleDebug
- `./gradlew :app:testDebugUnitTest --console=plain` — **PASS** (SystemProxyManagerTest 14 cases + existing proxy tests)
- `SystemProxyManager.enable()` called only after `HttpProxyServer` bind + `boundPort` check
- `SystemProxyManager.disable()` first step in `stopProxy()`
- Stale-proxy clear on `ZerotierBApplication.onCreate` when mode ≠ PROXY
- Shizuku 13.1.5 api + provider; `ShizukuProvider` in manifest
- No APN writes

## Files Modified

- `app/build.gradle.kts` — Shizuku api + provider 13.1.5
- `app/src/main/AndroidManifest.xml` — `WRITE_SECURE_SETTINGS`, `ShizukuProvider`
- `app/src/main/java/com/brukb/zerotier/proxy/SystemProxyManager.kt` — new
- `app/src/main/java/com/brukb/zerotier/system/ShizukuPermissionHelper.kt` — new
- `app/src/main/java/com/brukb/zerotier/proxy/ProxyServiceState.kt` — `systemProxyActive`, `hasSecureSettingsPermission`
- `app/src/main/java/com/brukb/zerotier/proxy/ProxyModeService.kt` — wire enable/disable
- `app/src/main/java/com/brukb/zerotier/ZerotierBApplication.kt` — stale-proxy clear on start
- `app/src/main/java/com/brukb/zerotier/ui/MainActivity.kt` — debug grant intent
- `app/src/test/java/com/brukb/zerotier/proxy/SystemProxyManagerTest.kt` — new (14 cases)

## Manual test (for humans)

**Prereq:** Enabled ZT network in app DB; VPN stopped; Shizuku running (for grant path) or ADB.

```bash
make verify
./gradlew :app:installDebug

# Option A — Shizuku grant (Shizuku app running on device)
adb shell am start -n com.brukb.zerotier/.ui.MainActivity \
  --es zerotierb_action grant_secure_settings
adb logcat -d -s MainActivity ShizukuPermission | tail -5
# Expect: "grant ok"

# Option B — ADB grant (no Shizuku)
adb shell pm grant com.brukb.zerotier android.permission.WRITE_SECURE_SETTINGS

# Start proxy
adb shell am start -n com.brukb.zerotier/.ui.MainActivity \
  --es zerotierb_action start_proxy
adb logcat -d -s ProxyModeService | tail -5
# Expect: "HTTP proxy on 127.0.0.1:<PORT>" AND "System proxy set to 127.0.0.1:<PORT>"

# Confirm Global HTTP_PROXY (requires grant)
adb shell settings get global http_proxy
# Expect: 127.0.0.1:<PORT>

# Wi-Fi browser — open http://<zt-service-ip>:<port>/ (ZT HTTP target)
# Expect: page loads via system proxy

# Stop proxy — Global restored
adb shell am start -n com.brukb.zerotier/.ui.MainActivity \
  --es zerotierb_action stop_proxy
adb shell settings get global http_proxy
# Expect: :0 or your prior proxy value

# Force-stop stale-proxy test
adb shell am start -n com.brukb.zerotier/.ui.MainActivity --es zerotierb_action start_proxy
# wait for "System proxy set"
adb shell am force-stop com.brukb.zerotier
adb shell settings get global http_proxy
# May still show loopback (onDestroy skipped)
adb shell am start -n com.brukb.zerotier/.ui.MainActivity
sleep 2
adb shell settings get global http_proxy
# Expect: :0 (stale clear on app process start when mode ≠ PROXY)
```

**Without grant:** proxy still listens; logcat shows `System proxy not set: WRITE_SECURE_SETTINGS not granted`; `settings get global http_proxy` unchanged.

## Learnings

- Archive `hasPermission()` read-only check always true — `Settings.Global.getString` never throws; use no-op write round-trip.
- `SystemProxyManager.enable()` only after bind; `disable()` first on stop (spec §9 order).
- Stale-proxy clear on `ZerotierBApplication.onCreate` via `shouldClearStale` — guards against force-stop skipping `onDestroy`.
- Shizuku grant via reflected `newProcess`; no `requestPermission` flow until T09 UI.
- `ProxyServiceState.systemProxyActive` / `hasSecureSettingsPermission` — honesty without grant card yet.
- Debug intents: `grant_secure_settings`, `start_proxy`, `stop_proxy` via exported `MainActivity`.

## Reality notes

*(Amended by upstream `/task-3-complete` if prior tasks changed assumptions)*

### From T05 close-out

- `com.brukb.zerotier.proxy.ProxyModeService` — start via `ProxyModeService.start(context)` or intent `ACTION_START`; no orchestrator/UI yet.
- HTTP proxy listens `127.0.0.1:0`; actual port in `ProxyServiceState.httpProxyPort` and `AppPreferences.lastHttpProxyPort`.
- T06 `SystemProxyManager` must bind-listen first (already done in T05 service), then write Global — read port from service state or prefs.
- No `Settings.Global` write in T05; Shizuku not added yet.
- VPN mutex: proxy refuses when VPN running unless `EXTRA_FORCE_DEBUG`.
