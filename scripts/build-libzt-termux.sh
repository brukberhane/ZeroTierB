#!/usr/bin/env bash
set -euo pipefail

# libzt AAR cannot be built on aarch64 Termux: the Android NDK host toolchain
# (linux-x86_64) and sdkmanager CMake 3.22.1 are x86_64-only. Build on an x86_64
# Linux/macOS host with scripts/build-libzt.sh, then copy the AAR here or let
# scripts/build-termux.sh pick it up from libzt/dist/.

cat >&2 <<'EOF'
Error: libzt AAR build is not supported on aarch64 Termux.

The NDK host clang/llvm prebuilts are linux-x86_64; sdkmanager cmake;3.22.1 is
also x86_64-only. Cross-compiling the AAR requires an x86_64 (or macOS arm64)
build host.

On desktop:
  ./scripts/build-libzt.sh
  # → libzt/dist/android-any-android-release/libzt-release.aar

Then on Termux:
  ./scripts/build-termux.sh
EOF
exit 1
