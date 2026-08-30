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

## Prerequisites & Requirements

### Linux Host

* **JDK 17** (>= 17.0.10 required to avoid Linux cgroup v2 kernel issue)
  * Via pacman: `sudo pacman -S jdk17-openjdk`
  * Or via mise: `mise use java=path:/usr/lib/jvm/java-17-openjdk` (or `mise install java@17.0.14`)
* **Android SDK & Command-line Tools** (SDK platforms: `android-35`, `android-33`)
  * `sudo pacman -S android-tools`
  * AUR: `yay -S android-sdk-cmdline-tools-latest android-platform-35 android-platform-33`
* **Android NDK** (r28 or r25.1 side-by-side, e.g. `28.2.13676358` / `25.1.8937393`)
* **CMake 3.22.1** (inside Android SDK)
  * `sdkmanager "cmake;3.22.1"`
* **Build essentials**: `git`, `make`, `cmake`, `which`

### Termux (Android Native Build)

* **Packages**:
  ```bash
  pkg update && pkg install openjdk-17 git make cmake clang binutils
  ```
* **Android SDK & NDK** for Termux:
  * Located at `$PREFIX/opt/android-sdk` or custom `$ANDROID_HOME`
  * NDK r28 installed in `$ANDROID_HOME/ndk/`

---

## Linux Build Steps

### 1. Clone with Submodules

```bash
git clone --recurse-submodules <repo-url> ZeroTierB
cd ZeroTierB
# If cloned without recurse:
git submodule update --init --recursive
git -C libzt checkout pylon
```

### 2. Environment Configuration

Ensure environment variables point to your SDK and Java 17:

```bash
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$ANDROID_HOME/ndk/28.2.13676358}"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
```

Accept SDK licenses if needed:
```bash
sdkmanager --licenses
```

### 3. Build libzt AAR

```bash
./scripts/build-libzt.sh
```

Generates: `libzt/dist/android-any-android-release/libzt-release.aar` referenced by `zerotier.properties`.

### 4. Build APK

```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

---

## Termux Build Steps

### 1. Install Dependencies in Termux

```bash
pkg update
pkg install openjdk-17 git make cmake clang binutils
```

### 2. Setup Android SDK & NDK in Termux

Ensure SDK and NDK paths are exported:

```bash
export ANDROID_HOME="$PREFIX/opt/android-sdk"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.2.13676358"
export JAVA_HOME="$PREFIX/lib/jvm/java-17-openjdk"
```

### 3. Build Everything (AAR + APK)

Use the dedicated Termux build helper:

```bash
./scripts/build-termux.sh
```

Or step-by-step:

```bash
# Build libzt AAR
./scripts/build-libzt-termux.sh

# Build APK
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
