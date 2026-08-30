#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

AAR_PATH="$ROOT/libzt/dist/android-any-android-release/libzt-release.aar"
if [ ! -f "$AAR_PATH" ]; then
    echo "libzt AAR not found. Building libzt first..."
    "$ROOT/scripts/build-libzt-termux.sh"
fi

echo "=== Building ZerotierB on Termux ==="
cd "$ROOT"
./gradlew assembleDebug

echo "Build complete. APK output:"
find "$ROOT/app/build/outputs/apk" -name "*.apk" 2>/dev/null || true
