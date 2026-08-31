# T09 — UI: global mode, Links, pin Main

**Status**: Done  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T08  
**Next**: T11  
**Layer**: L7

## Description

Segmented OFF|PROXY|VPN|AUTO. Current link line. Pin Main chip. Links screen. Grant card. Route MainViewModel through orchestrator (stop calling VpnService.start directly).

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| 2026-08-23 | created | — | Pending | stub seeded by bootstrap | |
| 2026-08-23 | plan | Pending | Planned | /task-1-plan T09 | |
| 2026-08-23 | execute | Planned | InProgress | /task-2-execute T09 | |
| 2026-08-23 | complete | InProgress | Done | /task-3-complete T09 | |

## Requirements

- [x] No per-ZT PROXY/VPN chips
- [x] Show 127.0.0.1:port or not granted
- [x] vpnConsentMissing banner

## Implementation Plan

*(Filled by `/task-1-plan` — do not invent during bootstrap beyond high-level notes.)*

### High-level notes (bootstrap)

- compose.mdc
- Spec: `docs/PROXY-VPN-PLAN.md`

## Execution plan (filled by /task-1-plan)

**Date:** 2026-08-23
**Codebase snapshot:** post-T08 (`9b62fe5` on `T09-ui-global-links-pin`). Orchestrator + LinkObserver + states live; UI is single-screen light-only M3 (BOM 2024.12.01); ViewModel still calls VPN stop/join/leave directly; no Links screen, no grant card, no pin-Main UI.
**Execute model:** medium (default)

### Context for executor

Goal: Material 3 Expressive UI for dual-mode control. Global segmented OFF|PROXY|VPN|AUTO routed through `orchestrator.applyGlobalMode()`; status card shows runtime + node + proxy port / "not granted"; current-link line from `orchestrator.state.lastLink`; ZT rows get Main pin; Links screen (SSID/SIM/Other rows + Save SSID + debounce slider); grant card (Shizuku + copyable ADB); `vpnConsentMissing` banner; dark + dynamic color theme. ViewModel stops calling VPN service directly.

Key files:
- `app/build.gradle.kts` — bump Compose BOM for M3 Expressive (see step 1).
- `app/src/main/java/com/brukb/zerotier/ui/theme/Theme.kt` (+ `Color.kt`) — light-only today; add dark + dynamic.
- `app/src/main/java/com/brukb/zerotier/ui/MainViewModel.kt` — collects only `ZerotierBVpnService.state` + networks; extend.
- `app/src/main/java/com/brukb/zerotier/ui/MainScreen.kt` — `StatusCard` (VPN switch), `NetworkRow`, dialogs; rework.
- `app/src/main/java/com/brukb/zerotier/ui/NetworkDetailScreen.kt` — add pin Main.
- New: `ui/LinksScreen.kt`, `ui/GrantSecureSettingsCard.kt`.
- `app/src/main/java/com/brukb/zerotier/ui/MainActivity.kt` — permission launcher; Links overlay wiring.
- State surfaces: `OrchestratorState(plan, lastLink, isApplying, lastError)`; `RuntimePlan(runtime, reason, vpnNetworkId, joinNetworkIds, vpnConsentMissing)`; `ProxyServiceState(isRunning, httpProxyPort, systemProxyActive, hasSecureSettingsPermission, nodeId, statusMessage, lastError)`; `VpnServiceState(isRunning, nodeId, statusMessage, networkStatuses, overlappingRoutes)`; `PhysicalLink` sealed: `WifiKnown(ssid, mode)`, `WifiUnknown`, `Mobile(subscriptionId, mode)`, `Other(mode)`, `None`.
- Repos: `NetworkRepository.observeAll()`, `setPinnedMain(networkId)`; `LinkProfileRepository.observeAll()`, `upsertWifi(ssid, mode)`, `upsertMobile(...)`; `AppPreferences.globalMode`, `setGlobalMode`, `linkDebounceMs`, `setLinkDebounceMs`, `startOnBoot`, `setStartOnBoot`.
- `ShizukuPermissionHelper.isAvailable()`, `grantWriteSecureSettings(context): Result<Unit>`; `SystemProxyManager.adbGrantCommand(packageName)`.

Invariants (rules): M3 + existing theme family, no extra design system; segmented control is the only global-mode UI; no per-ZT PROXY/VPN chips; all mode changes through orchestrator (never `ZerotierBVpnService.start/stop` from UI); Links screen owns SSID/SIM/Other; show proxy port + "system proxy inactive" honestly; `VpnService.prepare()` only from Activity.

### Steps

1. **Compose BOM bump for M3 Expressive** — `app/build.gradle.kts`: `platform("androidx.compose:compose-bom:2024.12.01")` → `platform("androidx.compose:compose-bom:2025.08.01")` (maps material3 → 1.5.0-alpha line with `ExperimentalMaterial3ExpressiveApi`; Kotlin 2.2 + AGP pinned — if lint `KaCall` crash resurfaces, fall back to newest BOM whose material3 1.5.0-alpha passes `:app:lintDebug`; record chosen BOM in Verification). No other dependency changes.
   → verify: `./gradlew :app:lintDebug :app:assembleDebug`

2. **Theme: dark + dynamic + expressive shapes** — `ui/theme/Theme.kt`:
   - Add `DarkColors = darkColorScheme(primary = …, secondary = …, …)` mirroring brand blues (lighten for dark).
   - `ZerotierBTheme(darkTheme = isSystemInDarkTheme(), dynamicColor = true)`: if `dynamicColor && Build.VERSION.SDK_INT >= 31` use `dynamicDarkColorScheme` / `dynamicLightColorScheme` (Material You), else brand schemes.
   - Keep signature `ZerotierBTheme(content: …)` with default params so existing call sites compile.
   → verify: `./gradlew :app:compileDebugKotlin`

3. **MainViewModel reroute + state merge** — `ui/MainViewModel.kt`:
   - Extend `MainUiState`: `globalMode: GlobalMode`, `plan: RuntimePlan?`, `lastLink: PhysicalLink?`, `isApplying: Boolean`, `orchestratorError: String?`, `proxy: ProxyServiceState`, `vpnConsentMissing: Boolean` (derive from `plan?.vpnConsentMissing == true`).
   - `combine(ZerotierBVpnService.state, ProxyModeService.state, orchestrator.state, networks, preferences.globalMode)` → single `MainUiState` (5-flow combine OK; use `combine(vararg)` overload or chained).
   - `fun setGlobalMode(mode: GlobalMode)` → `viewModelScope.launch { orchestrator.applyGlobalMode(mode) }`.
   - `fun toggleMainPin(network: ZerotierBNetwork)` → `networkRepository.setPinnedMain(if (network.isPinnedMain) null else network.networkId)` — check DAO signature: `setPinnedMain(networkId)` exists; if no unpin path, call `clearPinnedMain()` when already pinned.
   - Replace direct `ZerotierBVpnService.stop/joinNetwork/leaveNetwork` in `toggleRunning`/`toggleNetworkEnabled`/`deleteNetwork`/`saveNetwork` with `orchestrator.refresh()` after the Room write (orchestrator re-resolves and applies). Delete `toggleRunning` entirely — segmented control replaces the switch.
   - Links state: `val linkProfiles: StateFlow<List<LinkProfile>> = linkProfileRepository.observeAll().stateIn(...)`.
   - Actions: `fun setLinkMode(profile: LinkProfile, mode: LinkMode)` → `dao.upsert(profile.copy(mode = mode))` via repo (add `suspend fun update(profile)` passthrough to `LinkProfileRepository` if missing); `fun saveCurrentSsid()` → read `orchestrator.state.value.lastLink`, if `WifiKnown` → `upsertWifi(ssid)`; if `WifiUnknown` → no-op (UI disables button); `fun setLinkDebounce(ms: Int)` → `preferences.setLinkDebounceMs(ms)`.
   - Get orchestrator/repos via `(application as ZerotierBApplication)`.
   → verify: `./gradlew :app:compileDebugKotlin`; ViewModel unit test additions (see Tests).

4. **MainScreen rework (Expressive)** — `ui/MainScreen.kt`:
   - Replace VPN `Switch` in `StatusCard` with `SingleChoiceSegmentedButtonRow` of 4 `SegmentedButton`s (OFF|PROXY|VPN|AUTO) bound to `uiState.globalMode` → `viewModel.setGlobalMode(...)`. `@OptIn(ExperimentalMaterial3Api::class)` as needed.
   - Status card content: runtime badge (`uiState.plan?.runtime`), node ID (proxy or VPN whichever running), proxy line: `"System proxy 127.0.0.1:$port"` when `proxy.systemProxyActive`, else `"System proxy not granted"` when `proxy.isRunning && !systemProxyActive`, hidden otherwise. While `isApplying` show `LoadingIndicator` (Expressive, `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`).
   - Current-link line under status: format `lastLink`: `WifiKnown → "WiFi $ssid ($mode)"`, `WifiUnknown → "Unknown WiFi (PROXY)"`, `Mobile → "SIM data ($mode)"`, `Other → "Other ($mode)"`, `None → "No link"`.
   - `vpnConsentMissing` → `Card` banner (errorContainer) with "Grant VPN" button → `activity.requestVpnAndStart()` (existing consent path).
   - `NetworkRow`: add Main pin — `IconButton`/`FilterChip` with star icon; pinned → filled star + "Main" label; click → `toggleMainPin`. Keep enable switch; remove nothing else.
   - Top bar actions: Links icon (opens Links overlay), Settings icon (existing dialog).
   → verify: `./gradlew :app:compileDebugKotlin :app:lintDebug`

5. **LinksScreen** — new `ui/LinksScreen.kt`:
   - `fun LinksScreen(viewModel: MainViewModel, onDismiss: () -> Unit)` as full-screen overlay (match `NetworkDetailScreen` pattern — no NavHost).
   - Sections: "Current link" card (same formatting as step 4 + **Save SSID** button enabled only when `lastLink is WifiKnown` and no existing row for that SSID); "Wi-Fi" rows (ssid, OFF/PROXY/VPN `SingleChoiceSegmentedButtonRow`, delete `IconButton`); "SIM" rows (label/slot, mode chips, no delete); "Other" row (mode chips, no delete); debounce slider (`Slider`, 3–15s, value from `preferences.linkDebounceMs` collected as state, onValueChangeFinished → `setLinkDebounce`).
   - Empty Wi-Fi section → "No saved networks" text.
   → verify: `./gradlew :app:compileDebugKotlin :app:lintDebug`

6. **GrantSecureSettingsCard** — new `ui/GrantSecureSettingsCard.kt`:
   - `fun GrantSecureSettingsCard(hasPermission: Boolean, shizukuAvailable: Boolean, onShizukuGrant: () -> Unit, adbCommand: String)`.
   - If `hasPermission` → render nothing (or compact "granted" chip).
   - Else card with: "System proxy inactive" text, Shizuku grant `Button` (enabled when `shizukuAvailable`), copyable ADB line (`Text` + copy `IconButton` → `LocalClipboardManager`).
   - Show on MainScreen when `globalMode == PROXY || plan?.runtime == Runtime.PROXY` and `!proxy.hasSecureSettingsPermission`.
   → verify: `./gradlew :app:compileDebugKotlin`

7. **MainActivity wiring** — `ui/MainActivity.kt`:
   - Add `ActivityResultContracts.RequestMultiplePermissions` launcher: on AUTO select or Links open, if API ≥ 33 request `NEARBY_WIFI_DEVICES`, else `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`; on result → `orchestrator.refresh()` (SSID may resolve now).
   - Pass permission-request callback into `MainScreen`/`LinksScreen` (hoist via params, e.g. `onRequestLocationPermission: () -> Unit`).
   - Keep debug intents untouched.
   → verify: `./gradlew :app:compileDebugKotlin :app:lintDebug`

8. **NetworkDetailScreen pin** — add "Main network" `Switch`/`Checkbox` bound to `isPinnedMain` → onSave copies flag; saving pinned network also calls `networkRepository.setPinnedMain(networkId)` (single pin invariant already in DAO `@Transaction`).
   → verify: `make verify`

9. **Strings** — add user-facing strings to `res/values/strings.xml` for new UI (mode labels, links, grant, banners). Keep hardcoded text out of composables.
   → verify: `make verify`

### Tests to add

- `app/src/test/java/com/brukb/zerotier/ui/` — pure-logic only (no Compose UI test deps):
  - `LinkLineFormatTest` — extract `fun formatLinkLine(link: PhysicalLink?): String` into a non-composable helper (e.g. `ui/LinkLine.kt`) and table-test all 5 `PhysicalLink` variants + null.
  - `MainUiStateTest` — consent banner visible iff `plan.vpnConsentMissing`; proxy line text selection logic (extract `fun proxyStatusText(proxy: ProxyServiceState): String?` helper).
- Existing `LinkProfileMergeTest`, `RuntimePlanResolverTest` must stay green (no changes expected).

### Verify commands

- `make verify`
- Targeted: `./gradlew :app:testDebugUnitTest --tests "com.brukb.zerotier.ui.*"`

### Risks / pitfalls

- **BOM bump vs lint KaCall crash** (compose.mdc problem class): if `lintDebug` aborts with `KaFunctionCall`, drop to the newest BOM that lints clean; do not bump AGP/Kotlin.
- **Expressive APIs are `@ExperimentalMaterial3ExpressiveApi`** — opt in per file; do not opt in module-wide.
- **SegmentedButton stability**: stable since material3 1.2; expressive shapes land via theme, not custom drawing. Do not hand-roll a segmented control.
- **Do not** call `ZerotierBVpnService.*` or `ProxyModeService.*` from composables/ViewModel after rework — grep before claiming done.
- **Single pin invariant**: use `NetworkDao.setPinnedMain` transaction; never set `isPinnedMain=true` via plain `update` on two networks.
- **Permission rationale**: no rationale dialog v1 — request directly; unknown SSID already degrades to PROXY.
- **Save SSID on `WifiUnknown`**: button disabled, not hidden-with-row-creation (spec §5.3: no auto-save).

### Out of scope

- Navigation suite / multi-pane adaptive layout
- Animations beyond M3 defaults (no shared-element, no motion-scheme customization)
- Per-app proxy bypass UI
- Always-on VPN settings deep-link
- T10 E2E scripting

### Execute model recommendation
- medium (default) — UI volume is large but every component and state source is specified; no architecture left to discover.

## Test Plan

- Prove this layer without later layers.
- Commands: `make verify` (lint + unit tests + assembleDebug)
- New Kotlin: tests required

## Acceptance Criteria

- [x] Manual UI walk documented
- [x] BootReceiver uses orchestrator.refresh()
- [x] Tests added/updated for new behavior
- [x] Full lint + test verify suite green
- [x] Verification commands recorded and passing
- [x] No secrets committed

## Verification

**Date:** 2026-08-23

Presence:
- `Makefile` `verify` target — present
- `lefthook.yml` pre-commit → `make verify` — present
- `app/lint.xml` — present

Commands:
```
test -f Makefile && grep -q '^verify' Makefile
test -f lefthook.yml
test -f app/lint.xml
make verify
./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin --rerun-tasks
./gradlew :app:testDebugUnitTest --tests 'com.brukb.zerotier.ui.*'
```

Outcomes:
- `make verify` — lintDebug, unit tests, assembleDebug — BUILD SUCCESSFUL (re-confirmed `/task-3-complete` 2026-08-23)
- Forced Kotlin recompile — SUCCESS
- `com.brukb.zerotier.ui.*` unit tests — green (`StatusFormatTest`, `MainUiStateTest`)
- Compose BOM stayed `2024.12.01` (no M3 Expressive / no AGP bump). Applying spinner = `CircularProgressIndicator`.
- ViewModel does not call `ZerotierBVpnService.start/stop/join/leave`. Mode → `orchestrator.applyGlobalMode`. VPN chip → Activity `VpnService.prepare()`.
- BootReceiver already `orchestrator.refresh()` (T08).
- Post-review fixes (2026-08-23): `PhysicalLink.WifiUnsaved` for Save SSID; consent banner uses `requestVpnConsent()` (no global VPN flip); `applyVpn` restarts when `vpnNetworkId` changes; Shizuku grant on IO + `invalidateAppliedPlan()`; AUTO/Links `LaunchedEffect` for location.

Manual UI walk (device, for `/task-3-complete`):
1. Home: segmented OFF|PROXY|VPN|AUTO. No per-ZT PROXY/VPN chips. Pin Main star + enable switch.
2. PROXY: grant card if no `WRITE_SECURE_SETTINGS`; copy ADB; Shizuku if available. Status shows `127.0.0.1:port` or not granted.
3. VPN: system consent dialog; banner if `vpnConsentMissing`.
4. AUTO / Links: location (or NEARBY_WIFI_DEVICES) prompt. Links: Save SSID only when `WifiKnown` and not already saved; Wi-Fi/SIM/Other mode chips; debounce 3–15s.
5. Dark theme + dynamic color (API 31+).

## Files Modified

- `app/src/main/java/com/brukb/zerotier/ui/MainViewModel.kt`
- `app/src/main/java/com/brukb/zerotier/ui/MainScreen.kt`
- `app/src/main/java/com/brukb/zerotier/ui/MainActivity.kt`
- `app/src/main/java/com/brukb/zerotier/ui/NetworkDetailScreen.kt`
- `app/src/main/java/com/brukb/zerotier/ui/theme/Theme.kt`
- `app/src/main/java/com/brukb/zerotier/ui/LinksScreen.kt` (new)
- `app/src/main/java/com/brukb/zerotier/ui/GrantSecureSettingsCard.kt` (new)
- `app/src/main/java/com/brukb/zerotier/ui/StatusFormat.kt` (new)
- `app/src/test/java/com/brukb/zerotier/ui/StatusFormatTest.kt` (new)
- `app/src/test/java/com/brukb/zerotier/ui/MainUiStateTest.kt` (new)
- `app/src/main/res/values/strings.xml`
- `app/src/main/java/com/brukb/zerotier/data/LinkProfileDao.kt`
- `app/src/main/java/com/brukb/zerotier/data/LinkProfileRepository.kt`
- `app/src/main/java/com/brukb/zerotier/data/NetworkRepository.kt`
- `planning/phases/T09-ui-global-links-pin.md`
- `planning/phases/INDEX.md` (InProgress only)

## Manual test (for humans)

```bash
# install
./gradlew :app:installDebug

# debug helper (singleTop — always use -f 0x20000000)
DEBUG='am start -a com.brukb.zerotier.DEBUG -n com.brukb.zerotier/.ui.MainActivity -f 0x20000000'
adb shell $DEBUG --es zerotierb_action apply_mode --es mode PROXY
adb shell $DEBUG --es zerotierb_action apply_mode --es mode AUTO
adb shell $DEBUG --es zerotierb_action apply_mode --es mode VPN
adb shell $DEBUG --es zerotierb_action stop_all
adb shell settings get global http_proxy
adb logcat -s ConnectionOrchestrator MainActivity
```

**Look for:**
1. Home segmented OFF|PROXY|VPN|AUTO; no per-ZT mode chips; Main star + enable switch.
2. PROXY: status `127.0.0.1:port` or grant card; Shizuku/ADB grant → proxy active.
3. AUTO on Wi‑Fi: location prompt on open; Links → Save SSID when `Wifi … (unsaved, PROXY)`.
4. VPN: system consent; pin swap while VPN up restarts on new main net.
5. AUTO + consent missing: banner Grant VPN does **not** flip global mode to VPN.

## Learnings

- M3 Expressive BOM blocked on AGP 8.7 — stayed `2024.12.01`, stable segmented control.
- `WifiUnsaved` bridges classifier (readable SSID, no row) and Links Save SSID.
- Consent banner vs VPN chip: two Activity paths (`requestVpnConsent` vs `requestVpnAndStart`).
- `applyVpn` must restart TUN when `vpnNetworkId` changes while VPN already running.
- Post-grant: `invalidateAppliedPlan()` so HTTP_PROXY re-applies after Shizuku.

## Reality notes

*(Amended by upstream `/task-3-complete` if prior tasks changed assumptions)*

### From T07 close-out

- Mode chips / global selector should call `orchestrator.applyGlobalMode(...)` and `orchestrator.refresh()` — not raw `ProxyModeService` / `ZerotierBVpnService` starts.
- `ConnectionOrchestrator` owns PROXY↔VPN swap order (Global disable before proxy stop).
- Status UI can observe `ProxyModeService.state`, `ZerotierBVpnService.state`, `orchestrator.state` (`plan`, `lastLink`, `lastError`).

### From T08 close-out

- `LinkObserver` already running when mode ≠ OFF — T09 mode changes to AUTO/PROXY/VPN auto-register; no duplicate observer needed.
- Save SSID: call `linkProfileRepository.upsertWifi(ssid)` with SSID from `orchestrator.state.lastLink` when `WifiKnown` or after user confirms unknown→known; read `lastLink` from orchestrator state.
- Location runtime request on AUTO or Links screen (manifest already declares FINE/COARSE ≤32 + NEARBY_WIFI_DEVICES).
- Debug intents: use `com.brukb.zerotier.DEBUG` action + `-f 0x20000000` (`singleTop`).
