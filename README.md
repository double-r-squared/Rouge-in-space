# Rogue In Space

A terminal deep-space roguelike.

## Repo layout

- `src/` — Java source code in a flat layout (`src/*.java`)
- `assets/` — canonical game assets used at runtime
- `scripts/` — helper scripts (including optional scripts to launch in a new window)
- `db/` — canonical SQL seed source (`db/seed_monsters.sql`)
- `utils/` — developer tools (for example `utils/ascii-gen.py`)
- `out/` — local build output (please do not commit)

Notes on legacy duplicate files:
- `src/seed_monsters.sql` is a legacy copy; use `db/seed_monsters.sql` as source of truth.
- `src/assets/` is a legacy/duplicate asset copy; use root `assets/` as canonical.

## Quick start (macOS / Linux)

From the project root:

```bash
# 1) Setup from src/ (src/setup.sh expects this working directory)
cd src
chmod +x ./setup.sh
./setup.sh
cd ..

# 2) Compile to out/
mkdir -p out
javac -d out -cp src:src/sqlite-jdbc-3.36.0.3.jar src/*.java

# 3) Run
java -cp out:src/sqlite-jdbc-3.36.0.3.jar Game
```

## Quick start (Windows PowerShell)

From the project root:

```powershell
# 1) Setup from src/ (requires bash via Git Bash or WSL)
cd src
bash .\setup.sh
cd ..

# 2) Compile to out/
mkdir out -ErrorAction SilentlyContinue
javac -d out -cp src;src\sqlite-jdbc-3.36.0.3.jar src\*.java

# 3) Run
java -cp out;src\sqlite-jdbc-3.36.0.3.jar Game
```

## Optional: launch in a new terminal window

Convenient scripts:

- macOS: `scripts/run-new-window.command`
- Linux: `scripts/run-new-window.sh`
- Windows: `scripts/run-new-window.bat`

They open a new terminal window and run setup/build/run for you.

## Requirements

Required:
- Java (JDK 8+)
- A terminal

Optional:
- `sqlite3` CLI (for manual DB inspection and manual reseeding)

The game itself uses SQLite through the JDBC JAR. So the `sqlite3` command is optional.

## Manual setup (fallback)

If you skip `src/setup.sh`, do this from project root:

```bash
# 1) Download JDBC driver into src/
curl -L -o src/sqlite-jdbc-3.36.0.3.jar \
  https://github.com/xerial/sqlite-jdbc/releases/download/3.36.0.3/sqlite-jdbc-3.36.0.3.jar

# 2) (Re)seed database from canonical SQL source
sqlite3 src/monsters.db < db/seed_monsters.sql
```

## Troubleshooting

- `Could not find or load main class Game`
  - Re-run compile with `-d out`, then run with `-cp out:...` (or `out;...` on Windows).

- `sqlite-jdbc-3.36.0.3.jar not found`
  - Confirm `src/sqlite-jdbc-3.36.0.3.jar` exists.

- `sqlite3: command not found`
  - Install `sqlite3` if you need manual reseeding/inspection, or just use `src/setup.sh`.

- Terminal size warning (`border requires 190x54`)
  - Resize your terminal to at least `190x54`. The game may still run, but visuals can be clipped.

## Contributor guidelines

Do not commit generated artifacts.

Keep these ignored:
- `out/`
- `*.class`
- `.idea/`
- `.DS_Store`

`src/savegame.json` is a runtime artifact and should be treated as local state, not source.

To clean local `.class` files:

```bash
find . -name "*.class" -delete
```

## Short roadmap

The current repo intentionally stays on a flat `src/*.java` layout while active features land.
We may migrate later to package-based MVC with a standard `src/main/java` structure.
That migration is not part of this sprint.
Until the migration lands, the commands in this README are the source of truth.
