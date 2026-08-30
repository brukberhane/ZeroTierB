#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$ANDROID_HOME/ndk/28.2.13676358}"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}"

git -C "$ROOT" submodule update --init --recursive -- libzt
git -C "$ROOT/libzt" submodule update --init --recursive
cd "$ROOT/libzt"
./build.sh android-aar release

echo "AAR: $ROOT/libzt/dist/android-any-android-release/libzt-release.aar"
