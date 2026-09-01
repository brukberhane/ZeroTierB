#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TERMUX_PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"

# Force Java 17 — do not inherit JAVA_HOME from the shell (e.g. java-21).
JAVA_HOME="$TERMUX_PREFIX/lib/jvm/java-17-openjdk"
export JAVA_HOME
if [ ! -d "$JAVA_HOME" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
    echo "Error: Java 17 not found at $JAVA_HOME" >&2
    echo "Install or repair: pkg install openjdk-17" >&2
    exit 1
fi
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_OPTS="-Dorg.gradle.java.home=$JAVA_HOME ${GRADLE_OPTS:-}"

# Configure Android SDK & NDK for Termux
export ANDROID_HOME="${ANDROID_HOME:-$TERMUX_PREFIX/opt/android-sdk}"

if [ -z "${ANDROID_NDK_HOME:-}" ]; then
    if [ -d "$ANDROID_HOME/ndk/28.2.13676358" ]; then
        export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.2.13676358"
    elif [ -d "$ANDROID_HOME/ndk" ]; then
        LATEST_NDK="$(find "$ANDROID_HOME/ndk" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
        if [ -n "$LATEST_NDK" ]; then
            export ANDROID_NDK_HOME="$LATEST_NDK"
        fi
    fi
fi
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$ANDROID_HOME/ndk/28.2.13676358}"

# SDK cmake;3.22.1 from sdkmanager is x86_64 only — unusable on aarch64 Termux.
# Use native ARM cmake 4.1.2 (install: sdkmanager "cmake;4.1.2").
TERMUX_CMAKE="$ANDROID_HOME/cmake/4.1.2/bin/cmake"
if [ ! -x "$TERMUX_CMAKE" ]; then
    echo "Error: ARM SDK CMake 4.1.2 not found at $TERMUX_CMAKE" >&2
    echo "Install: yes | sdkmanager \"cmake;4.1.2\"" >&2
    exit 1
fi

echo "=== Termux libzt build environment ==="
echo "JAVA_HOME:        $JAVA_HOME"
echo "Java version:     $("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1)"
echo "ANDROID_HOME:     $ANDROID_HOME"
echo "ANDROID_NDK_HOME: $ANDROID_NDK_HOME"
echo "CMake:            $("$TERMUX_CMAKE" --version 2>&1 | head -n 1)"
echo "======================================="

# Sync to commit pinned by ZeroTierB — do not `checkout pylon` (overwrites pointer,
# fails on local edits, and sdkmanager cmake;3.22.1 is x86_64-only on Termux).
git -C "$ROOT" submodule update --init --recursive -- libzt
git -C "$ROOT/libzt" submodule update --init --recursive

LIBZT_GRADLE="$ROOT/libzt/pkg/android/app/build.gradle"
if grep -q "version '3.22.1'" "$LIBZT_GRADLE"; then
    sed -i "s/version '3.22.1'/version '4.1.2'/g" "$LIBZT_GRADLE"
    echo "Patched libzt cmake 3.22.1 -> 4.1.2 (Termux aarch64)"
fi

cd "$ROOT/libzt"
./build.sh android-aar release

echo "AAR built successfully:"
echo "$ROOT/libzt/dist/android-any-android-release/libzt-release.aar"
