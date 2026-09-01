# 🚀 ZerotierB

```text
 _____              _   _           ____
|__  /___ _ __ ___ | |_(_) ___ _ __| __ )
  / // _ \ '__/ _ \| __| |/ _ \ '__|  _ \
 / /|  __/ | | (_) | |_| |  __/ |  | |_) |
/____\___|_|  \___/ \__|_|\___|_|  |____/
```

## Summary

ZerotierB is an Android ZeroTier client. Today it is a JNI `VpnService` with one TUN and many joined networks. The target is two **exclusive** runtimes: a **loopback HTTP proxy** (libzt, many ZT nets, for Calibre-web / Immich-in-a-browser) and a **full VPN** (JNI, **one** main ZT net). A global **AUTO** mode picks PROXY vs VPN from the **physical** link (Wi-Fi SSID, SIM, Other)—not from a per-ZT chip.

Bootstrapped with **[Turboplan](https://github.com/commoddity/turboplan)** (agent rules + phased delivery). Product spec: [`docs/PROXY-VPN-PLAN.md`](docs/PROXY-VPN-PLAN.md).

## Table of Contents

- [Summary](#summary)
- [❗ The problem](#-the-problem)
- [🛠️ The fix (target)](#️-the-fix-target)
- [📊 Status](#-status)
- [📂 Repo layout](#-repo-layout)
- [📚 Dependencies & docs](#-dependencies--docs)
- [🔁 Building with Turboplan](#-building-with-turboplan)
- [🔨 Build / verify](#-build--verify)
- [🔒 Security / invariants](#-security--invariants)
- [📜 License / attribution](#-license--attribution)

---

## ❗ The problem

| What you try | What happens |
| ------------ | ------------ |
| Browse Calibre / Immich on several ZT nets | Full VPN is heavy; Android is one-VPN-per-device |
| Need SSH / SMB / non-HTTP on ZT | HTTP proxy cannot carry that; need TUN |
| Home Wi-Fi vs LTE vs USB | Want PROXY vs VPN by **physical** network, not by toggling each ZT id |

## 🛠️ The fix (target)

- **PROXY:** libzt + HTTP CONNECT on `127.0.0.1` + optional `Settings.Global.HTTP_PROXY`. All enabled ZT nets. Not a leak-proof tunnel.
- **VPN:** existing JNI TUN, **main** ZT net only (`isPinnedMain` or oldest `createdAt`).
- **AUTO:** debounce physical link → `LinkProfile` mode OFF/PROXY/VPN. Unknown SSID → PROXY, no auto-save.
- **Never** run both stacks at once. Same `identity.secret`.

```text
physical link → classifier → RuntimePlan
                    ├── PROXY → libzt HTTP 127.0.0.1 → maybe Global HTTP_PROXY
                    ├── VPN   → JNI TUN (main net only)
                    └── OFF   → restore proxy, stop both
```

## 📊 Status

| Area | State |
| ---- | ----- |
| 🧭 Agent rules (`.cursor/rules/`) | Bootstrapped |
| 📋 MVP plan (`planning/phases/`) | Seeded — [INDEX](planning/phases/INDEX.md) T01–T10 |
| 🛠️ Product code | Orchestrator + proxy + VPN (T05–T07); `LinkObserver` AUTO debounce (T08); UI pending (T09) |
| 🧪 Verify | `make verify` (Android lint + unit tests + assembleDebug) |

## 📂 Repo layout

| Path | For |
| ---- | --- |
| [`README.md`](README.md) | 👤 Humans (this file) |
| [`docs/PROXY-VPN-PLAN.md`](docs/PROXY-VPN-PLAN.md) | 📘 Dual-mode contract |
| [`.cursor/rules/`](.cursor/rules/) | 📜 Conventions for coding agents |
| [`.cursor/skills/`](.cursor/skills/) | 🧩 plan / execute / complete / … |
| [`planning/phases/`](planning/phases/) | 🗂️ MVP sequence of record |
| [`app/`](app/) | Android application |
| [`core/`](core/) | ZeroTier JNI (`com.zerotier.sdk`) |
| [`libzt/`](libzt/) | libzt tree; `:app` links `libzt-release.aar` from `libzt/dist/` |
| [`archive/proxy-mode` (git branch)](docs/PROXY-VPN-PLAN.md) | Old Pylon HTTP/SOCKS + libzt |

## 📚 Dependencies & docs

| Dependency | Role | Docs | Agent rules |
| ---------- | ---- | ---- | ----------- |
| Android VpnService | TUN VPN | [VpnService](https://developer.android.com/reference/android/net/VpnService) | [`android-vpn.mdc`](.cursor/rules/android-vpn.mdc) |
| ZeroTier JNI | Node + frames | [ZeroTier docs](https://docs.zerotier.com/) + `externals/ZeroTierOne/java/` | [`zerotier-jni.mdc`](.cursor/rules/zerotier-jni.mdc) |
| libzt | Userspace sockets | in-tree `libzt/README.md` | [`libzt.mdc`](.cursor/rules/libzt.mdc) |
| Settings.Global.HTTP_PROXY | System HTTP proxy | [HTTP_PROXY](https://developer.android.com/reference/android/provider/Settings.Global#HTTP_PROXY) | [`android-http-proxy.mdc`](.cursor/rules/android-http-proxy.mdc) |
| Shizuku | Grant `WRITE_SECURE_SETTINGS` | [Shizuku README](https://github.com/RikkaApps/Shizuku/blob/master/README.md) | [`shizuku.mdc`](.cursor/rules/shizuku.mdc) |
| ConnectivityManager | Physical link | [NetworkCallback](https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback) | [`android-connectivity.mdc`](.cursor/rules/android-connectivity.mdc) |
| Room 2.6 | Local DB | [Room migrations](https://developer.android.com/training/data-storage/room/migrating-db-versions) | [`room.mdc`](.cursor/rules/room.mdc) |
| Jetpack Compose | UI | [Compose](https://developer.android.com/jetpack/compose/documentation) | [`compose.mdc`](.cursor/rules/compose.mdc) |
| Kotlin 2.2 / JVM 17 | Language | [kotlinlang.org](https://kotlinlang.org/docs/home.html) | [`kotlin.mdc`](.cursor/rules/kotlin.mdc) |
| Orchestrator (ours) | Mode swap | spec §4–9 | [`connection-orchestrator.mdc`](.cursor/rules/connection-orchestrator.mdc) |

## 🔁 Building with [Turboplan](https://github.com/commoddity/turboplan)

Work proceeds one phase task at a time. Full methodology:
[github.com/commoddity/turboplan](https://github.com/commoddity/turboplan).

```
  📝 /task-1-plan TXX
        ↓
  🛠️  /task-2-execute TXX
        ↓
  ✅ /task-3-complete TXX → commit local (add `--push` to push) + Manual test → next <stub-stem> branch
```

See [`planning/phases/INDEX.md`](planning/phases/INDEX.md).

## 🔨 Build / verify

Requires JDK 17, Android SDK (`ANDROID_HOME`) with **`platforms;android-37.0`** (package id, not `android-37`) and **build-tools 36.0.0**, NDK 25.1.8937393 (JNI). SDK CMake 3.22.1 for libzt's Android build on **x86_64** hosts (`sdkmanager "cmake;3.22.1"`). The libzt AAR cannot be built on aarch64 Termux (NDK host toolchain is x86_64-only); build it on desktop and copy `libzt/dist/.../libzt-release.aar`, or use `build-termux.sh` after the AAR exists.

```bash
git submodule update --init --recursive
./scripts/build-libzt.sh   # AAR → libzt/dist/android-any-android-release/libzt-release.aar
make verify                # lintDebug + unit tests + assembleDebug
make install-hooks         # lefthook: pre-commit → make verify
./gradlew :app:installDebug
adb shell pm grant com.brukb.zerotier android.permission.WRITE_SECURE_SETTINGS
```

`libzt` is `brukberhane/libzt` branch **`pylon`**: upstream libzt plus JNI harden, lwIP managed routes, and `zts_net_set_managed_whitelist`. Not `py311`. After clone:

```bash
git submodule update --init --recursive
git -C libzt checkout pylon
```

### Termux

```bash
pkg update
pkg install openjdk-17 git make cmake clang binutils
export ANDROID_HOME="${ANDROID_HOME:-$PREFIX/opt/android-sdk}"
./scripts/build-termux.sh
```

`build-termux.sh` assembles the APK. The libzt AAR must exist first — build it on an **x86_64** desktop (`./scripts/build-libzt.sh`) and copy `libzt/dist/android-any-android-release/libzt-release.aar` to the device, or sync a tree that already contains it.

Toolchain (this clone, T11.5): **AGP 9.2.1**, **Gradle 9.4.1**, **Kotlin 2.2.21** (built-in Kotlin + Compose compiler plugin), **KSP 2.3.11**, **compose-bom-alpha:2026.08.01** (material3 **1.5.0-alpha27**), **compileSdk 37**, **targetSdk 35**, **minSdk 26**. SDK: `platforms/android-37.0`, build-tools **36.0.0**. Do not bump AGP/Kotlin/compileSdk as drive-bys. Stable `compose-bom` maps material3 1.4.0 (no Expressive) — keep the alpha BOM until material3 1.5 is stable.

## 🔒 Security / invariants

- HTTP proxy **127.0.0.1 only** — never `0.0.0.0`.
- PROXY is **not** a kill-switch. Apps may ignore `HTTP_PROXY` (HTTP/3, custom stacks).
- One ZeroTier identity; never two live nodes.
- `WRITE_SECURE_SETTINGS` via Shizuku or ADB — not Play-grantable.
- No APN `proxy=` writes in v1.
- Do not `VpnService.prepare()` from `NetworkCallback`.

## 📜 License / attribution

ZeroTier core is GPLv3. App Kotlin/Java follows the same license as upstream ZeroTierOne JNI usage. Archived proxy code: branch `archive/proxy-mode`, tag `v0.1.0-proxy`.
