#!/usr/bin/env bash
set -e

# Compile and run JUnit 5 tests for the current flat repo layout.
REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JUNIT_JAR="$REPO_DIR/support_files/junit-platform-console-standalone-1.10.2.jar"

cd "$REPO_DIR"

./scripts/setup-tests.sh

mkdir -p out out_test

echo "[..] Compiling application classes..."
javac -d out -cp src:src/sqlite-jdbc-3.36.0.3.jar src/*.java

echo "[..] Compiling test classes..."
javac -d out_test -cp out:src/sqlite-jdbc-3.36.0.3.jar:"$JUNIT_JAR" src/test/*.java

echo "[..] Running tests..."
java -jar "$JUNIT_JAR" \
  --class-path out:out_test:src/sqlite-jdbc-3.36.0.3.jar \
  --scan-class-path
