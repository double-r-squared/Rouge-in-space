#!/bin/bash
# run-new-window.command (macOS)
# Opens a new macOS Terminal window and runs the game:
#   - Runs src/setup.sh once to ensure JDBC jar and monsters.db are ready
#   - Compiles Java sources into ./out
#   - Runs Game with the correct classpath
# ─────────────────────────────────────────────────────────────────────────────

set -e # to abort the script if any command fails

# Determine  repo root directory
# This script might be run from anywhere, including Finder sessions
# "$0" and dirname helps us find the full path to the script (rather than the
# caller's location)
#  cd up one directory from scripts/ to get to the project root
REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"

# Breakdown / rationale:
#   1) `cd "$REPO_DIR"`: Ensure the whole pipeline runs from repo root
#   2) `cd src && chmod +x ./setup.sh && ./setup.sh && cd ..`:
#        - `cd src`: setup.sh lives in src/
#        - `chmod +x ./setup.sh`: Mark script as executable
#        - `./setup.sh`: Downloads the SQLite JDBC jar if needed and checks monsters.db.
#        - `cd ..`: return to repo root since subsequent steps expect to be at the top level
#   3) `mkdir -p out`: Fresh build output directory.
#   4) `javac -d out -cp src:src/sqlite-jdbc-3.36.0.3.jar src/*.java`: Compiled files go
#   into out/
#   5) `java -cp out:src/sqlite-jdbc-3.36.0.3.jar Game`: Puts the compiled classes and the
#   JDBC driver on the runtime classpath.
#
# Chained with `&&` so any failure fails fast and prevents the game from launching
#
# Written as a single string, so that AppleScript can pass it as a single command line argument
# And then Terminal an run it as a shell command
RUN_CMD="cd \"$REPO_DIR\" && \
cd src && chmod +x ./setup.sh && ./setup.sh && cd .. && \
mkdir -p out && \
javac -d out -cp src:src/sqlite-jdbc-3.36.0.3.jar src/*.java && \
java -cp out:src/sqlite-jdbc-3.36.0.3.jar Game"


# ── Open a NEW macOS Terminal window and run RUN_CMD inside it ──────────────
#   Passing RUN_CMD as an argv item lets us avoid errors with quoting
#
#   - `osascript - "$RUN_CMD"`:
#       The `-` tells osascript to read AppleScript code from stdin.
#       `$RUN_CMD` is passed as the first positional parameter (argv[1]) to
#       the AppleScript’s `run` handler. read AppleScript; pass in`$RUN_CMD` without
#       interpolating
osascript - "$RUN_CMD" <<'APPLESCRIPT'
on run argv
  -- Runs the entire shell command string from earlier as an argv.
  set runCmd to item 1 of argv
  tell application "Terminal"
    activate -- bring Terminal to the foreground
    do script runCmd -- launch the entire compiled pipeline in a new session
  end tell
end run
APPLESCRIPT