#!/bin/bash
set -e

# Always operate from the repo root (parent of this scripts/ directory)
REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"

# Command we want to run in the new Terminal window
# - Runs setup from src/ (downloads JDBC jar + checks src/monsters.db)
# - Compiles into ./out
# - Runs Game with the correct classpath
RUN_CMD="cd \"$REPO_DIR\" && \
cd src && chmod +x ./setup.sh && ./setup.sh && cd .. && \
mkdir -p out && \
javac -d out -cp src:src/sqlite-jdbc-3.36.0.3.jar src/*.java && \
java -cp out:src/sqlite-jdbc-3.36.0.3.jar Game"

# Open a NEW Terminal window even if one is already open
osascript <<APPLESCRIPT
tell application "Terminal"
  activate
  set w to (do script "$RUN_CMD")
end tell
APPLESCRIPT
