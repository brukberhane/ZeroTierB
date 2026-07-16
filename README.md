# ZeroTier-Pylon

Android app that runs a ZeroTier node via libzt and exposes network access through a local HTTP proxy (system-wide) and optional gated SOCKS5 proxy.

## libzt patches (submodule)

This project patches `twisteroidambassador/libzt` to expose:

- Managed routes via `zts_core_query_route_cidr`
- Assigned addresses via `zts_core_query_addr_cidr`
- Network DNS via `zts_core_query_dns_*`
- Per-network settings via `zts_net_set_settings` (allowManaged/Default/Global)

Rebuild after pulling libzt changes:

```bash
./scripts/build-libzt.sh
```

Requires Java 17 (`JAVA_HOME=/usr/lib/jvm/java-17-openjdk`).

Uses NDK r28 with 16 KB page-size ELF alignment for `libzt.so` (`ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON`).

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

- Android SDK at `/opt/android-sdk` (or update `local.properties`)
- JDK 17+
- CMake, NDK (for building libzt)

## Build libzt AAR

```bash
cd libzt
git submodule update --init --recursive
export ANDROID_HOME=/opt/android-sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/28.2.13676358
./build.sh android-aar release
```

Output: `libzt/dist/android-any-android-release/libzt-release.aar`

`zerotier.properties` points to this AAR.

## Build app

```bash
./gradlew assembleDebug
```

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
