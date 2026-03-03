# Rogue In Space

A tiny terminal deep-space rogue-like.

---

## Repo layout

- `src/` — **Java source code** (flat repo: `src/*.java`)
- `assets/` — **game assets** (ASCII art, encounter frames, etc.)
- `scripts/` — setup helpers (note: a copy of `setup.sh` is under `src/`)
- `db/` — database-related files (canonical database seed script lives here)
- `utils/` — dev tools (e.g., `ascii-gen.py`)
- `out/` — build output (please do not commit)

---

## TL;DR (macOS / Linux)

From the **project root**:

```bash
# 1) Setup (downloads the JDBC driver & checks the DB)
# Our current `src/setup.sh` expects to be run from inside `src/`
cd src # 
chmod +x ./setup.sh
./setup.sh
cd ..

# 2) Compile into ./out
mkdir -p out
javac -d out -cp src:src/sqlite-jdbc-3.36.0.3.jar src/*.java

# 3) Run
java -cp out:src/sqlite-jdbc-3.36.0.3.jar Game
```

---

## TL;DR (Windows PowerShell)

```powershell
# 1) Setup (requires a bash shell like Git Bash / WSL)
cd src
bash .\setup.sh
cd ..

# 2) Compile into ./out
mkdir out -ErrorAction SilentlyContinue
javac -d out -cp src;src\sqlite-jdbc-3.36.0.3.jar src\*.java

# 3) Run
java -cp out;src\sqlite-jdbc-3.36.0.3.jar Game
```

If you don’t have `bash` on Windows, you can manually download the JDBC jar (below).

---

## What you need

### Required
- **Java (JDK 8+)**
- A terminal

### Optional
- **SQLite CLI (`sqlite3`)** — if you want to inspect the database.

> Note: Our game talks to SQLite via the JDBC driver JAR. 
> 
> The `sqlite`command is not required.

---

## Manual setup

If you skipped the script:

### 1) Download the SQLite JDBC driver

Download the driver **into `src/`**.
(currently, the build commands expect it to be there):

```bash
curl -L -o src/sqlite-jdbc-3.36.0.3.jar \
  https://github.com/xerial/sqlite-jdbc/releases/download/3.36.0.3/sqlite-jdbc-3.36.0.3.jar
```

### 2) Ensure the monsters database exists

The game currently uses:
- `src/monsters.db`

To (re)seed it from SQL:

```bash
sqlite3 src/monsters.db < db/seed_monsters.sql
```

---

## Keeping the repo clean

Please do **not** commit compiled `.class` files or local build outputs.

Please include the following into your`.gitignore`:

- `out/`
- `*.class`
- `.idea/`
- `.DS_Store`

Run to clean out `.class` files (macOS/Linux):

```bash
find . -name "*.class" -delete
```

---

## Common issues

- **“Could not find or load main class Game”**
  - Make sure you compiled with `-d out` and you are running with `-cp out:...` (see TL;DR).

- **“sqlite-jdbc-3.36.0.3.jar not found”**
  - Confirm the jar is at `src/sqlite-jdbc-3.36.0.3.jar`.

- **Assets not found**
  - Run from the **project root**. The game may expects assets live in `assets/` (root) or 
    `src/assets/` depending on the version you are running.

---

## Team notes (contributors)

Currently (03 March), the Java code is flat in `src/` (no package declarations for 
model/view/controller/etc.). That’s intentional for the current state of the repo.

Planned future improvement: move to a standard Java layout with packages for proper MVC 
& encapsulation.

Keep commits small and readable, and avoid committing generated files (e.g., `.class` files).
