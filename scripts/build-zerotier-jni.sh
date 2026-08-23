#!/usr/bin/env bash
# ZeroTierOne JNI is built automatically by Gradle (:core module).
# This script is a convenience wrapper for a clean native rebuild.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
./gradlew :core:externalNativeBuildDebug
