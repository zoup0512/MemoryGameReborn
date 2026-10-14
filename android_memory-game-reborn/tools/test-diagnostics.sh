#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
if [[ -n "${JAVA_HOME:-}" ]]; then
  javac="$JAVA_HOME/bin/javac"; java="$JAVA_HOME/bin/java"
else
  javac=javac; java=java
fi
src="$root/src/main/java/com/cube/memorygames/diagnostics"
"$javac" -encoding UTF-8 -source 8 -target 8 -d "$work" \
  "$src/DiagnosticSanitizer.java" "$src/DiagnosticLogStore.java" \
  "$src/DiagnosticUploadPolicy.java" "$root/tools/DiagnosticCoreTest.java"
"$java" -cp "$work" DiagnosticCoreTest
