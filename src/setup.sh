#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# setup.sh  –  first-time project setup
#
# Downloads the SQLite JDBC driver and verifies monsters.db is present.
# Run once from your src/ directory before compiling.
# ─────────────────────────────────────────────────────────────────────────────

set -e

JAR="sqlite-jdbc-3.45.1.0.jar"
JAR_URL="https://github.com/xerial/sqlite-jdbc/releases/download/3.45.1.0/$JAR"

echo "=== Rogue in Space — setup ==="
echo ""

# ── Download sqlite-jdbc driver if not present ────────────────────────────────
if [ -f "$JAR" ]; then
    echo "[OK] $JAR already present"
else
    echo "[..] Downloading $JAR ..."
    if command -v curl &>/dev/null; then
        curl -L -o "$JAR" "$JAR_URL"
    elif command -v wget &>/dev/null; then
        wget -O "$JAR" "$JAR_URL"
    else
        echo "[ERR] Neither curl nor wget found. Download manually:"
        echo "      $JAR_URL"
        exit 1
    fi
    echo "[OK] Downloaded $JAR"
fi

# ── Check monsters.db ─────────────────────────────────────────────────────────
if [ -f "monsters.db" ]; then
    COUNT=$(sqlite3 monsters.db "SELECT COUNT(*) FROM monsters;" 2>/dev/null || echo "0")
    echo "[OK] monsters.db present — $COUNT monsters loaded"
else
    echo "[ERR] monsters.db not found in current directory."
    echo "      Copy it from the project root or run the seed script."
    exit 1
fi

echo ""
echo "=== Compile ==="
echo "  javac -cp .:$JAR *.java"
echo ""
echo "=== Run ==="
echo "  java -cp .:$JAR Game"
echo ""
echo "Setup complete."