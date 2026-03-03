#!/usr/bin/env bash
set -e

# Download JUnit 5 standalone console launcher for local test runs.
REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JUNIT_JAR="$REPO_DIR/support_files/junit-platform-console-standalone-1.10.2.jar"
JUNIT_URL="https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.2/junit-platform-console-standalone-1.10.2.jar"

echo "=== Rogue in Space — test setup ==="
echo ""

if [ -f "$JUNIT_JAR" ]; then
  echo "[OK] JUnit jar already present: $JUNIT_JAR"
  exit 0
fi

mkdir -p "$REPO_DIR/support_files"
echo "[..] Downloading JUnit 5 standalone jar..."

if command -v curl >/dev/null 2>&1; then
  if ! curl -L -o "$JUNIT_JAR" "$JUNIT_URL"; then
    echo "[ERR] Failed to download JUnit jar with curl."
    echo "      Download manually:"
    echo "      $JUNIT_URL"
    echo "      and save to:"
    echo "      $JUNIT_JAR"
    exit 1
  fi
elif command -v wget >/dev/null 2>&1; then
  if ! wget -O "$JUNIT_JAR" "$JUNIT_URL"; then
    echo "[ERR] Failed to download JUnit jar with wget."
    echo "      Download manually:"
    echo "      $JUNIT_URL"
    echo "      and save to:"
    echo "      $JUNIT_JAR"
    exit 1
  fi
else
  echo "[ERR] Neither curl nor wget was found."
  echo "      Download manually:"
  echo "      $JUNIT_URL"
  echo "      and save to:"
  echo "      $JUNIT_JAR"
  exit 1
fi

if [ ! -f "$JUNIT_JAR" ]; then
  echo "[ERR] Download did not produce $JUNIT_JAR"
  echo "      Download manually:"
  echo "      $JUNIT_URL"
  echo "      and save to:"
  echo "      $JUNIT_JAR"
  exit 1
fi

echo "[OK] Downloaded: $JUNIT_JAR"
