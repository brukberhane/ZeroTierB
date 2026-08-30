# ZeroTier-Pylon

Android app that runs a ZeroTier node via libzt and exposes network access through a local HTTP proxy (system-wide) and optional gated SOCKS5 proxy.

## libzt patches (submodule)

This project uses `brukberhane/libzt` on branch **`pylon`** (not `main` / `py311`). That branch carries:

- Managed routes via `zts_core_query_route_cidr`
- Assigned addresses via `zts_core_query_addr_cidr`
- Network DNS via `zts_core_query_dns_*`
- Per-network settings via `zts_net_set_settings` (allowManaged/Default/Global)
- lwIP managed-route hooks + `zts_net_set_managed_whitelist`

`.gitmodules` sets `branch = pylon`. After clone:

```bash
git submodule update --init --recursive
git -C libzt checkout pylon
```

`./scripts/build-libzt.sh` (and the Termux variant) check out `pylon` before building.

Rebuild after pulling libzt changes:

```bash
git -C libzt pull
./scripts/build-libzt.sh
```

## Features

- Full ZeroTier node in userspace (no VPN/tun interface)
- Join multiple ZeroTier networks with runtime join/leave
- Managed routes from controller (when allowManaged enabled)
- Auto network DNS from controller (when allowDns enabled)
- Local HTTP proxy for Android system proxy setting
- Optional SOCKS5 proxy (disabled by default)
- Separate HTTP proxy toggle while node keeps running
- Per-network options: DNS, allowManaged, allowDefault, allowGlobal, blockOutside, allow/deny rules
- Shizuku one-tap grant for WRITE_SECURE_SETTINGS
- Start on boot option

## Prerequisites

- **JDK 17.0.10+** (libzt Android build is Gradle 7.5 / AGP 7.3 — not JDK 18+)
- **Android SDK** (`ANDROID_HOME`, or `sdk.dir` in `local.properties`)
- **SDK CMake 3.22.1** — required by libzt's Android build; system CMake 4.x is not used:
  ```bash
  sdkmanager "cmake;3.22.1"
  ```
- **git** (submodules)

## Build (Linux)

```bash
git clone --recurse-submodules <repo-url> ZeroTierB
cd ZeroTierB
git submodule update --init --recursive
git -C libzt checkout pylon

./scripts/build-libzt.sh
./gradlew assembleDebug
```

AAR: `libzt/dist/android-any-android-release/libzt-release.aar`  
APK: `app/build/outputs/apk/debug/app-debug.apk`

## Build (Termux)

```bash
pkg update
pkg install openjdk-17 git make cmake clang binutils
export ANDROID_HOME="${ANDROID_HOME:-$PREFIX/opt/android-sdk}"

./scripts/build-termux.sh
```

`build-termux.sh` builds the libzt AAR if missing, then the APK.

---

## System proxy permission

Grant once via ADB:

```bash
adb shell pm grant com.zerotier.pylon android.permission.WRITE_SECURE_SETTINGS
```

Or use the in-app **Grant via Shizuku** button (requires Shizuku installed and running).

## Usage

1. Add a ZeroTier network ID (16 hex chars)
2. Grant `WRITE_SECURE_SETTINGS`
3. Toggle Pylon on
4. Authorize the displayed node ID in your ZeroTier controller
5. Traffic using the system HTTP proxy routes through the local proxy into ZeroTier networks

## Architecture

- `PylonService` — foreground service orchestrating node, proxies, system proxy
- `ZeroTierNodeManager` — libzt node lifecycle
- `HttpProxyServer` / `Socks5ProxyServer` — local proxies
- `RouteResolver` — routes destinations through libzt or OS stack
- `SystemProxyManager` — manages `Settings.Global.HTTP_PROXY`
