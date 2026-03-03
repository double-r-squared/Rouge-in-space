#!/usr/bin/env bash
set -e
# run-new-window.sh - Linux: Run the game in a new terminal window

REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
RUN_CMD="cd \"$REPO_DIR\" && mkdir -p out && javac -d out -cp src:src/sqlite-jdbc-3.36.0.3.jar src/*.java && java -cp out:src/sqlite-jdbc-3.36.0.3.jar Game"

# Try a bunch of terminal emulators. If none found, print the command to run manually.
if command -v x-terminal-emulator >/dev/null 2>&1; then
  x-terminal-emulator -e bash -lc "$RUN_CMD; exec bash"
elif command -v gnome-terminal >/dev/null 2>&1; then
  gnome-terminal -- bash -lc "$RUN_CMD; exec bash"
elif command -v konsole >/dev/null 2>&1; then
  konsole -e bash -lc "$RUN_CMD; exec bash"
elif command -v xterm >/dev/null 2>&1; then
  xterm -e bash -lc "$RUN_CMD; exec bash"
else
  echo "No supported terminal emulator found."
  echo "Run manually:"
  echo "$RUN_CMD"
  exit 1
fi
