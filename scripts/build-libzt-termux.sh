#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TERMUX_PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"

# Configure Java 17 for Termux (required by Gradle 7.5.1 in libzt)
export JAVA_HOME="${JAVA_HOME:-$TERMUX_PREFIX/lib/jvm/java-17-openjdk}"
if [ ! -d "$JAVA_HOME" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
    echo "Error: Java 17 not found at $JAVA_HOME" >&2
    echo "Install it via: pkg install openjdk-17" >&2
    exit 1
fi
export PATH="$JAVA_HOME/bin:$PATH"

# Configure Android SDK & NDK for Termux
export ANDROID_HOME="${ANDROID_HOME:-$TERMUX_PREFIX/opt/android-sdk}"

if [ -z "${ANDROID_NDK_HOME:-}" ]; then
    if [ -d "$ANDROID_HOME/ndk" ]; then
        # Pick latest installed NDK version
        LATEST_NDK="$(find "$ANDROID_HOME/ndk" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
        if [ -n "$LATEST_NDK" ]; then
            export ANDROID_NDK_HOME="$LATEST_NDK"
        fi
    fi
fi
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$ANDROID_HOME/ndk/28.2.13676358}"

echo "=== Termux libzt build environment ==="
echo "JAVA_HOME:        $JAVA_HOME"
echo "Java version:     $("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1)"
echo "ANDROID_HOME:     $ANDROID_HOME"
echo "ANDROID_NDK_HOME: $ANDROID_NDK_HOME"
echo "======================================="

cd "$ROOT/libzt"
git submodule update --init --recursive
./build.sh android-aar release

echo "AAR built successfully:"
echo "$ROOT/libzt/dist/android-any-android-release/libzt-release.aar"
