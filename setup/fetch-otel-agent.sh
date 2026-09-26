#!/usr/bin/env bash
# Download the OpenTelemetry Java agent 2.31.1. Not a Maven dependency.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/tools/opentelemetry-javaagent.jar"
URL="https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.31.1/opentelemetry-javaagent.jar"
SUM="bbf83c151b6400709e2f225bdd07a04f839d9d13b8b93464241333fd25d3e3ba"

mkdir -p "$ROOT/tools"
if [[ -f "$DEST" ]]; then
  actual="$(shasum -a 256 "$DEST" | awk '{print $1}')"
  if [[ "$actual" == "$SUM" ]]; then
    echo "$DEST"
    exit 0
  fi
fi

curl -fsSL -L -o "$DEST" "$URL"
actual="$(shasum -a 256 "$DEST" | awk '{print $1}')"
if [[ "$actual" != "$SUM" ]]; then
  echo "checksum mismatch: $actual" >&2
  rm -f "$DEST"
  exit 1
fi
echo "$DEST"
