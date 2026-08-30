#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$ANDROID_HOME/ndk/28.2.13676358}"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}"

git -C "$ROOT" submodule update --init --recursive -- libzt
if git -C "$ROOT/libzt" show-ref --verify --quiet refs/heads/pylon; then
    git -C "$ROOT/libzt" checkout pylon
elif git -C "$ROOT/libzt" show-ref --verify --quiet refs/remotes/origin/pylon; then
    git -C "$ROOT/libzt" checkout -B pylon origin/pylon
else
    echo "libzt branch 'pylon' not found (local or origin)." >&2
    echo "Push it: git -C libzt push -u origin pylon" >&2
    exit 1
fi
git -C "$ROOT/libzt" submodule update --init --recursive
cd "$ROOT/libzt"
./build.sh android-aar release

echo "AAR: $ROOT/libzt/dist/android-any-android-release/libzt-release.aar"
