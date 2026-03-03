# Rogue In Space

A tiny terminal roguelike with space vibes.
---

## Repo layout


- `src/main/` — Source code
- `src/test/` — Tests
- `scripts/` — scripts for setting up the project
- `assets/` — game assets (ASCII art, encounter frames, etc.)
- `utils/` — python scripts for processing images into ASCII art
- `db/` — database files
- `db/seed_monsters.sql` — seed monsters into the database
- `sqlite-jdbc-3.36.0.3.jar` — SQLite JDBC driver
---

**TL;DR:** 
- Run `./setup.sh`, 
- download `sqlite-jdbc-3.36.0.3.jar` into the project root, 
- compile with `javac -cp .:sqlite-jdbc-3.36.0.3.jar $(find src/main/java -name "*.java")`, 
- then run the game using `java -cp .:sqlite-jdbc-3.36.0.3.jar rogueinspace.Game`.

## Quick start (macOS / Linux)

### Required
- **Java (JDK 8+)**
- **A terminal** (macOS Terminal, Linux shell)

### Suggested
- **SQLite CLI (`sqlite3`)** — if you want to inspect the database manually.

1) From the project root, run the setup script:

```bash
./setup.sh
# if that doesn't work, try: chmod +x ./setup.sh
```

2) Download the SQLite JDBC driver into the project root:

```bash
curl -L -o sqlite-jdbc-3.36.0.3.jar \
  https://github.com/xerial/sqlite-jdbc/releases/download/3.36.0.3/sqlite-jdbc-3.36.0.3.jar
```

We use the JDBC API to let our Java program talk to our SQLite database.

3) Compile the project:

```bash
# From the project root
javac -cp .:sqlite-jdbc-3.36.0.3.jar $(find src/main/java -name "*.java")
```
Compiles every `.java` file under `src/main/java`.

4) Run the game:
Depending on your package structure, you may need to run the game in one of two ways:
```bash
# If your entrypoint class is in the default package
java -cp .:sqlite-jdbc-3.36.0.3.jar Game
```

If that fails with “Could not find or load main class”, your entrypoint class may be in a 
package.
Try the packaged main instead:

```bash
# If your main class is in a package
java -cp .:sqlite-jdbc-3.36.0.3.jar rogueinspace.Game
```

---

## Verifying your setup

### Java

```bash
java -version
javac -version
```

### SQLite (optional)

```bash
# Notice: sqlite3 vs sqlite
sqlite3 --version
```

---

## Keeping the repo clean

Please do **not** commit compiled `.class` files.

You may delete them safely at any time:

### macOS / Linux

```bash
find . -name "*.class" -delete
```

---

## Troubleshooting

- **“Could not find or load main class …”**
  - Try running `Game` vs `rogueinspace.Game` (both commands are shown above).

- **“sqlite-jdbc-3.36.0.3.jar not found”**
  - Make sure the JAR is in the project root and your command matches the filename.

---

## Team notes (contributors)

- Keep code organized by **MVC** packages (model/view/controller) under `src/main/java/`.
- Prefer **private fields** and methods that express intent (encapsulation) rather than direct field access.