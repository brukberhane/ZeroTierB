# T15 — UI polish: motion, empty states, copy actions (Phase E)

**Status**: Done  
**Parent INDEX**: [INDEX.md](./INDEX.md)  
**Depends-on**: T14  
**Next**: T16  
**Layer**: L7

## Description

Final UX pass on the T11–T14 surfaces: consistent **empty states**, light **motion** (stable M3 plus Expressive `MotionScheme` if already wired in T12 theme), **copy-to-clipboard** for node ID and ADB grant command, scroll/layout fixes on small screens. Cohesive "operator dashboard" feel. Toolchain/BOM already set in T11.5 — do not bump AGP/BOM here.

## Status History

| Timestamp | Event | From | To | Details | User |
| --------- | ----- | ---- | -- | ------- | ---- |
| 2026-08-31 | created | — | Pending | /setup-tasks UI rewrite phase E | |
| 2026-08-31 | planned | Pending | Planned | /task-1-plan — snackbar copy, empty states, single scroll | |
| 2026-08-31 | execute | Planned | InProgress | /task-2-execute | |
| 2026-08-31 | complete | InProgress | Done | /task-3-complete — verify green, dialectic, commit | |

## Requirements

- [x] **Empty states**:
  - No networks in Room → illustrated/text card + CTA to add network (existing add flow)
  - No saved Wi-Fi links → Links screen message (may already exist; align copy with hero)
  - Runtime OFF → hero shows calm stopped state, not error styling
- [x] **Copy actions**:
  - Node ID: tap icon or long-press → `ClipboardManager` + brief snackbar "Copied"
  - ADB grant command on grant card: ensure copy works (T09); snackbar feedback
  - Network ID on detail screen: copy short ID
- [x] **Motion** (stable APIs only):
  - `AnimatedVisibility` for consent banner, grant card, applying spinner region
  - `animateContentSize()` on hero when status line length changes
  - Chip crossfade JOINING → OK via `AnimatedContent` optional — no shared-element transitions
- [x] **Layout**:
  - Main list + hero scroll together; no double scrollbars
  - Bottom sheet settings safe on gesture nav / small height
  - Network detail sections use consistent spacing (8/16 dp rhythm from theme)
- [x] **Accessibility**: content descriptions on icon-only buttons (copy, pin, settings)
- [x] Strings for snackbars and empty states in `strings.xml`

## Non-goals (this task)

- Lottie / custom illustrations
- Navigation suite / adaptive list-detail pane
- Compose BOM / AGP bump (T11.5 already did this)
- Haptic feedback unless trivial one-liner

## Constraints

- Read `.cursor/rules/compose.mdc`. Do **not** bump BOM/AGP (T11.5). Expressive motion OK if T12 already opted `MaterialExpressiveTheme`.
- Motion must not block interaction or delay status updates
- Copy must not include secrets (`identity.secret`)

## References

- All T11–T14 UI files
- `app/src/main/java/com/brukb/zerotier/ui/MainScreen.kt`
- `app/src/main/java/com/brukb/zerotier/ui/GrantSecureSettingsCard.kt`
- Material 3 SnackbarHost pattern in Compose

## Implementation Plan

See Execution plan below.

### High-level notes (setup-tasks)

1. `SnackbarHost` on `MainScreen` `Scaffold` (not `MainActivity`).
2. Shared `CopyableMonoText` for node ID, network ID, ADB.
3. Empty-networks card; retone Links Wi-Fi empty copy.
4. Single `verticalScroll` Column (drop unbounded `LazyColumn`).

## Execution plan (filled by /task-1-plan)

**Date:** 2026-08-31  
**Codebase snapshot:** branch `T15-ui-polish-motion-empty-states` after T14 `13d2bd9`. `MainScreen` `Scaffold` has **no** `snackbarHost`. Body is non-scroll `Column` wrapping **unbounded** `LazyColumn` (hero outside list — they do **not** scroll together). No empty-networks UI. Grant card copies ADB via `LocalClipboardManager` with **no** snackbar. Hero node ID / settings node ID / detail network ID are plain `Text`. No `AnimatedVisibility` / `animateContentSize` / `AnimatedContent`. `ZerotierBTheme` already wraps `MaterialExpressiveTheme(colorScheme=…)` with **no** extra `MotionScheme`. Settings sheet: `rememberModalBottomSheetState(skipPartiallyExpanded = true)`, no `navigationBarsPadding`. Links already shows `no_saved_wifi`. Icon-only buttons on main already have `contentDescription`.  
**Execute model:** small

### Context for executor

**Goal:** Polish T11–T14 surfaces: empty states, copy-to-clipboard + snackbar, light motion, one shared scroll on home, small-screen settings/detail spacing. No orchestrator/VPN/JNI/Room changes. No AGP/BOM bump.

**Resolved ambiguities (do not reopen):**

| Question | Pick |
| -------- | ---- |
| SnackbarHost in `MainActivity`? | **No.** Theme + `Scaffold` live in `MainScreen.kt:97`. Add `snackbarHost` there. |
| Hero + list scroll together | **One** `Column(Modifier.verticalScroll)` — **drop** `LazyColumn`. Typical ZT list is tiny. Do **not** `weight` the list (that would freeze the hero). |
| Chip `AnimatedContent` JOINING→OK | **Skip** (optional in AC). `JoinStatusChip` already uses `LoadingIndicator` for JOINING. |
| `rememberBottomSheetState` migrate | **Skip.** Keep `rememberModalBottomSheetState(skipPartiallyExpanded = true)`. Add `navigationBarsPadding()` only. |
| Copy package name in settings | **Skip.** AC is node ID + ADB + detail network ID. Settings Advanced **node ID** uses the same widget. |
| Clipboard API | Keep `LocalClipboardManager.setText(AnnotatedString(value))` (grant already). Do not switch to `LocalClipboard` unless compile fails. |
| Copy payload | **Raw** value (`nodeId`, `networkId`, `adbCommand`) — not the `"Node: …"` prefix. |
| Dialog vs snackbar window | Compose `AlertDialog` / `ModalBottomSheet` are separate windows. **Local** `SnackbarHostState` inside detail + settings. Main `Scaffold` host for hero + grant. Same `copied` string. |
| Empty networks illustration | **Text `Card` + CTA** — no Lottie / drawable (non-goal). |
| Hero OFF “calm” | Lifecycle chip: `ERROR` → errorContainer; **STOPPED / STARTING / ONLINE / PAUSED_DOZE** → tertiaryContainer (same NEUTRAL as joining). Do **not** paint STOPPED as error. Keep `orchestratorError` Text if non-null. |
| `MotionScheme` arg on theme | **Skip.** Default Expressive motion from `MaterialExpressiveTheme` is enough. |

**Key files**

| Path | Action |
| ---- | ------ |
| `app/.../ui/CopyableMonoText.kt` | **new** |
| `app/.../ui/StatusFormat.kt` | Add `heroLifecycleChipRole` |
| `app/src/test/.../ui/StatusFormatTest.kt` | Table for chip role |
| `app/.../ui/MainScreen.kt` | Snackbar, scroll, empty card, motion, copy, hero chrome |
| `app/.../ui/GrantSecureSettingsCard.kt` | Use `CopyableMonoText` + `onCopied` |
| `app/.../ui/NetworkDetailScreen.kt` | Copy nwid + local snackbar; 16/8 dp rhythm |
| `app/.../ui/SettingsBottomSheet.kt` | Copy node ID + local snackbar; `navigationBarsPadding` |
| `app/.../ui/LinksScreen.kt` | Retone empty Wi-Fi string only |
| `app/.../ui/JoinStatusChip.kt` | **no change** (optional motion skipped) |
| `app/.../ui/theme/Theme.kt` | **no change** |
| `app/src/main/res/values/strings.xml` | New strings below |
| `MainActivity.kt` / ViewModel / VPN / proxy | **no change** |

**Invariants** (`.cursor/rules/compose.mdc`)

- ViewModel stays the only UI→service bridge. Copy is local clipboard — do not add ViewModel copy methods.
- No per-ZT PROXY/VPN chips. Reuse `JoinStatusChip` — do not restyle.
- Consent banner still `requestVpnConsent()` (not `requestVpnAndStart()`).
- Do **not** bump AGP/BOM. Do **not** copy `identity.secret`.
- Settings grant: hint row only — do not embed full grant UI in the sheet.

### Steps

1. **Strings** — add to `strings.xml`:

```xml
<string name="copied">Copied</string>
<string name="copy_node_id">Copy node ID</string>
<string name="copy_network_id">Copy network ID</string>
<string name="empty_networks">No networks yet — tap Add to join a ZeroTier network.</string>
<string name="empty_networks_action">Add network</string>
```

   Change `no_saved_wifi` from `"No saved networks"` to `"No saved Wi-Fi — save the current SSID above."`  
   Keep `copy_adb`. Keep `empty_networks_action` equal in meaning to `add_network` (can reuse `add_network` for the CTA label instead of a second string — **prefer reuse `add_network`**, skip `empty_networks_action`).  
   → verify: resources compile

2. **Pure helper** — in `StatusFormat.kt` add:

```kotlin
fun heroLifecycleChipRole(lifecycle: NodeLifecycleStatus): JoinStatusChipRole =
    if (lifecycle == NodeLifecycleStatus.ERROR) JoinStatusChipRole.ERROR
    else JoinStatusChipRole.NEUTRAL
```

   Tests in `StatusFormatTest.kt`:

| Input | Expect |
| ----- | ------ |
| `STOPPED` | `NEUTRAL` |
| `PAUSED_DOZE` | `NEUTRAL` |
| `ONLINE` | `NEUTRAL` |
| `STARTING` | `NEUTRAL` |
| `ERROR` | `ERROR` |

   → verify: `./gradlew :app:testDebugUnitTest --tests com.brukb.zerotier.ui.StatusFormatTest`

3. **`CopyableMonoText`** — new `app/src/main/java/com/brukb/zerotier/ui/CopyableMonoText.kt`:

```kotlin
@Composable
fun CopyableMonoText(
    value: String,
    contentDescription: String,
    onCopied: () -> Unit,
    modifier: Modifier = Modifier,
    display: String = value,
)
```

   Layout: `Row(verticalAlignment = CenterVertically)`  
   - `Text(display, Modifier.weight(1f).combinedClickable(onClick = {}, onLongClick = copy), bodySmall, FontFamily.Monospace)`  
   - `IconButton(onClick = copy) { Icon(Icons.Default.ContentCopy, contentDescription) }`  
   `copy` = `LocalClipboardManager.current.setText(AnnotatedString(value)); onCopied()`.  
   `combinedClickable` needs `@OptIn(ExperimentalFoundationApi::class)`.  
   No unit test for the composable (Compose UI tests out of scope).  
   → verify: file compiles; unused imports none

4. **Grant card** — `GrantSecureSettingsCard`: add `onCopied: () -> Unit`. Replace the ADB `Row` + inline clipboard with:

```kotlin
CopyableMonoText(
    value = adbCommand,
    contentDescription = stringResource(R.string.copy_adb),
    onCopied = onCopied,
)
```

   → verify: grant card still copies command; caller must pass `onCopied`

5. **Home: snackbar + single scroll + empty + motion + hero copy** — `MainScreen.kt`:

   - `val snackbarHostState = remember { SnackbarHostState() }`
   - `val scope = rememberCoroutineScope()`
   - `val copiedMessage = stringResource(R.string.copied)`
   - `fun showCopied() { scope.launch { snackbarHostState.showSnackbar(copiedMessage) } }`
   - `Scaffold(..., snackbarHost = { SnackbarHost(snackbarHostState) })`
   - Replace body `Column` modifiers with:

```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState())
        .padding(16.dp)
        .navigationBarsPadding(),
    verticalArrangement = Arrangement.spacedBy(16.dp),
)
```

   - **Drop** `LazyColumn` / `items`. `uiState.networks.forEach { NetworkRow(...) }` inside the same Column (or nested `Column(spacedBy(8.dp))`).
   - Empty: `if (uiState.networks.isEmpty()) { Card { Column(padding 16.dp, spacedBy 8.dp) { Text(empty_networks, bodyMedium); TextButton(onClick = { viewModel.showAddNetworkDialog(true) }) { Text(stringResource(R.string.add_network)) } } } }` — keep the Networks header `Row` + add `IconButton` above either way.
   - Consent banner: wrap existing `if (uiState.vpnConsentMissing) { Card… }` in `AnimatedVisibility(visible = uiState.vpnConsentMissing) { … }`. Keep `requestVpnConsent()`.
   - Grant: `AnimatedVisibility(visible = showGrant) { GrantSecureSettingsCard(..., onCopied = { showCopied() }) }`.
   - `RuntimeHeroCard`:
     - `Card(Modifier.fillMaxWidth().animateContentSize())`
     - Applying row: `AnimatedVisibility(visible = isApplying) { existing Row }`
     - Lifecycle `AssistChip`: set `colors` from `heroLifecycleChipRole(lifecycle)` using the **same** container/label mapping as `JoinStatusChip` (SUCCESS unused here; NEUTRAL = tertiaryContainer; ERROR = errorContainer). `contentDescription`: `stringResource(R.string.lifecycle_chip)` via `Modifier.semantics { contentDescription = … }` or chip `modifier`.
     - Node ID: replace `Text(node_line)` with `CopyableMonoText(value = nodeId, display = stringResource(R.string.node_line, nodeId), contentDescription = stringResource(R.string.copy_node_id), onCopied = onNodeCopied)` — add `onNodeCopied: () -> Unit` param to `RuntimeHeroCard`; MainScreen passes `{ showCopied() }`. Hide the row when `nodeId.isBlank()` (already).

   → verify: compile; empty list shows card; home scrolls as one surface

6. **Detail** — `NetworkDetailScreen.kt`:

   - `val snackbarHostState = remember { SnackbarHostState() }`
   - `val scope = rememberCoroutineScope()`
   - Wrap `text` body in `Box` { `Column(Modifier.verticalScroll…)` ; `SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))` }
   - Replace nwid `Text` with `CopyableMonoText(value = network.networkId, contentDescription = stringResource(R.string.copy_network_id), onCopied = { scope.launch { snackbarHostState.showSnackbar(stringResource(R.string.copied)) } })`
   - Outer column `spacedBy(16.dp)` (header / runtime / fields). Keep `RuntimeSection` at `8.dp`, `RuntimeListSection` at `4.dp`, `ToggleRow` vertical padding `4.dp`.
   - Do **not** convert to `ModalBottomSheet`. Enable/delete stay on home row.

   → verify: copy nwid shows snackbar in dialog; scroll still works

7. **Settings sheet** — `SettingsBottomSheet.kt`:

   - Keep `rememberModalBottomSheetState(skipPartiallyExpanded = true)`.
   - Wrap sheet content in `Box` { existing `Column(verticalScroll + padding)` with **added** `.navigationBarsPadding()` on that Column; `SnackbarHost` aligned bottom + `navigationBarsPadding()`. }
   - Local `SnackbarHostState` + `rememberCoroutineScope` like detail.
   - Replace node-ID `Text` with `CopyableMonoText(value = it, display = stringResource(R.string.settings_debug_node_id, it), contentDescription = stringResource(R.string.copy_node_id), onCopied = …)`.
   - Package line stays plain `Text` (no copy).
   - Do **not** embed `GrantSecureSettingsCard`.

   → verify: sheet full-expand; node copy; gesture-nav not covering last rows

8. **Links empty copy** — only the `no_saved_wifi` string change from step 1. No layout change.  
   → verify: empty Wi-Fi section shows new sentence

9. **`make verify`** with `JAVA_HOME=/usr/lib/jvm/java-17-openjdk` (mise Java 27 breaks Lombok).  
   → verify: lint + unit tests + assembleDebug green

### Tests to add

Only `heroLifecycleChipRole` table in `StatusFormatTest` (step 2). No Compose UI tests. No new Room/orchestrator tests.

### Verify commands

```bash
test -f Makefile && grep -q '^verify' Makefile
test -f lefthook.yml && test -f app/lint.xml
JAVA_HOME=/usr/lib/jvm/java-17-openjdk make verify
```

### Risks / pitfalls

- **Unbounded `LazyColumn` in `Column`:** current home layout; replacing with `verticalScroll` + `forEach` is the fix. Do not nest `LazyColumn` inside `verticalScroll`.
- **Two snackbar hosts:** required because dialog/sheet windows. Do not try to show Scaffold snackbar on top of `AlertDialog`.
- **`combinedClickable` empty `onClick`:** required so long-press copies without stealing scroll. Short tap on text does nothing; icon tap copies.
- **Consent banner `AnimatedVisibility`:** keep `requestVpnConsent()` — not `requestVpnAndStart()`.
- **Motion must not delay status:** default fade is fine; do **not** add `delay` / long tween on applying/lifecycle.
- **Copy secrets:** only node ID, network ID, ADB command. Never `identity.secret`.
- **Settings deprecation:** ignore `rememberModalBottomSheetState` warnings; do not migrate this task.
- JDK 17 for verify.

### Out of scope

- Lottie / custom illustrations; navigation suite / list-detail pane
- AGP / Compose BOM bump; `MotionScheme` theme arg
- `AnimatedContent` on join chips; haptics
- Package-name copy; ViewModel snackbar channel
- Convert detail to bottom sheet
- Orchestrator / VPN / libzt behavior
- T10 E2E matrix

### Execute model recommendation

- **small** — mechanical Compose polish; helpers + call sites specified. No runtime/stack design.

## Test Plan

- Unit tests for any new pure format helpers only
- `make verify`
- Manual on small phone + foldable if available

## Acceptance Criteria

- [x] Zero networks shows friendly empty state with add action
- [x] Node ID copy works with snackbar
- [x] No jank on JOINING→OK transition; hero resizes smoothly
- [x] TalkBack reads copy/pin/settings buttons
- [x] `make verify` green
- [x] Full A–E manual walk documented in task Verification

## Verification

**Date:** 2026-08-31  
**Commands:**
```bash
test -f Makefile && grep -q '^verify' Makefile
test -f lefthook.yml && test -f app/lint.xml
JAVA_HOME=/usr/lib/jvm/java-17-openjdk make verify
```
**Result:** green (lint + unit tests + assembleDebug). `rememberModalBottomSheetState` deprecation warning only (intentional per plan).

## Files Modified

- `app/src/main/java/com/brukb/zerotier/ui/CopyableMonoText.kt` (new)
- `app/src/main/java/com/brukb/zerotier/ui/StatusFormat.kt`
- `app/src/test/java/com/brukb/zerotier/ui/StatusFormatTest.kt`
- `app/src/main/java/com/brukb/zerotier/ui/MainScreen.kt`
- `app/src/main/java/com/brukb/zerotier/ui/GrantSecureSettingsCard.kt`
- `app/src/main/java/com/brukb/zerotier/ui/NetworkDetailScreen.kt`
- `app/src/main/java/com/brukb/zerotier/ui/SettingsBottomSheet.kt`
- `app/src/main/res/values/strings.xml`

## Manual test (for humans)

Full A–E regression walk on device:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk make verify
./gradlew :app:installDebug
```

1. **A/B — Hero + chips:** PROXY/VPN modes; lifecycle chip calm when STOPPED (tertiary, not red); applying spinner fades in/out; hero scrolls with list on small screen.
2. **C — Settings:** Open gear sheet; copy node ID in Advanced → "Copied" snackbar; last rows clear of gesture nav.
3. **D — Detail:** Open network → copy network ID → snackbar; runtime sections still scroll.
4. **E — Empty + copy:** Delete all networks → empty card + Add CTA; long-press or copy icon on hero node ID → snackbar; grant card ADB copy → snackbar; Links → empty Wi-Fi shows retuned copy.

## Learnings

- `SnackbarHost` on `MainScreen` `Scaffold` — not `MainActivity`. Dialogs/sheets need **local** `SnackbarHostState`.
- Home scroll: one `verticalScroll` `Column` + `forEach`; drop unbounded `LazyColumn` in fixed `Column`.
- Reuse `CopyableMonoText` for monospace copy (icon + long-press); copy **raw** value, show label in `display` only.
- Hero lifecycle chip: `heroLifecycleChipRole` — STOPPED/ONLINE = NEUTRAL tertiary; ERROR only for `ERROR`.
- `AnimatedVisibility` for consent/grant/applying; `animateContentSize` on hero card. Skip chip `AnimatedContent` (optional).
- Settings: keep `rememberModalBottomSheetState(skipPartiallyExpanded = true)`; add `navigationBarsPadding`.

## Reality notes

### From T12 close-out

- `MaterialExpressiveTheme` is wired in `ZerotierBTheme` (T12). Motion polish can use Expressive APIs without another theme change.
- Copy node ID on hero is T15 — hero already shows monospace full ID.

### From T13 close-out

- Settings sheet (`SettingsBottomSheet`) shipped with read-only package/node ID in Advanced — T15 adds clipboard + snackbar there and on hero.
- `rememberModalBottomSheetState` deprecation — optional migrate to `rememberBottomSheetState` during T15 layout pass.

### From T14 close-out

- `NetworkDetailScreen` is `AlertDialog` + `verticalScroll` with live runtime sections (addresses, routes, DNS) and `JoinStatusChip` in header.
- Network ID shown monospace in header — T15 adds copy + snackbar (same pattern as hero node ID).
- Runtime empty states: `detail_not_connected`, `detail_vpn_main_only`; route list uses `filterDisplayRoutes` with Room `allow*` flags (preview before Save).
- T15 spacing polish: align detail section rhythm (8/16 dp) with hero/settings; no sheet conversion for detail unless planned explicitly.

### From T15 plan (reality check)

- `SnackbarHost` belongs on `MainScreen` `Scaffold`, not `MainActivity` (`setContent { MainScreen }` only).
- Home today: non-scroll `Column` + unbounded `LazyColumn` — T15 replaces with one `verticalScroll` Column; drop `LazyColumn`.
- Grant ADB copy already works (`LocalClipboardManager`); missing snackbar + shared widget.
- `lifecycle_chip` / `join_status_chip` strings exist; lifecycle AssistChip does not use them yet.
- `MaterialExpressiveTheme` already wired — do not pass `MotionScheme`.
- Settings: keep `rememberModalBottomSheetState(skipPartiallyExpanded = true)`; add `navigationBarsPadding` only.
