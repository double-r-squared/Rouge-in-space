import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// ─────────────────────────────────────────────────────────────────────────────
// EnemyFactory  –  builds Enemy instances from the monsters.db SQLite database
//
// HOW IT WORKS
// ─────────────
// On first use, EnemyFactory opens a JDBC connection to monsters.db and reads
// every row from the `monsters` table into a list of MonsterDef records.  All
// subsequent calls use that in-memory list — the DB is only read once per run.
//
// To spawn an enemy, call:
//   EnemyFactory.random(x, y)        — picks a random monster from the pool
//   EnemyFactory.spawn("Zombie", x, y) — spawns a specific monster by name
//
// ADDING A NEW MONSTER
// ─────────────────────
// Just INSERT a row into the monsters table.  No Java changes needed.
//
//   INSERT INTO monsters
//     (name, glyph, max_health, attack_power, hit_chance,
//      sight_range, sees_through_doors, xp_value, drop_table, description)
//   VALUES ('Wraith', 'W', 35, 16, 0.65, 11, 1, 20, 'ghost', 'A spectral hunter.');
//
// DB SCHEMA (monsters table)
// ───────────────────────────
//   id                INTEGER  PRIMARY KEY
//   name              TEXT     display name, also used as drop table key
//   glyph             TEXT     single character rendered on the map
//   max_health        INTEGER
//   attack_power      INTEGER
//   hit_chance        REAL     0.0 – 1.0
//   sight_range       INTEGER  tiles
//   sees_through_doors INTEGER 0 = false, 1 = true
//   xp_value          INTEGER  XP awarded on kill
//   drop_table        TEXT     key used by Combat.rollDrop() for loot
//   description       TEXT     flavour text (unused in gameplay, for tooling)
// ─────────────────────────────────────────────────────────────────────────────
public class EnemyFactory {

    // Path to the database file, relative to the working directory
    private static final String DB_PATH = "monsters.db";

    // In-memory cache — loaded once on first call to ensureLoaded()
    private static final List<MonsterDef> pool = new ArrayList<>();
    private static final Random           rng  = new Random();

    // ── Public API ────────────────────────────────────────────────────────────

    /** Spawn a random monster from the full pool at world position (x, y). */
    public static Enemy random(int x, int y) {
        ensureLoaded();
        if (pool.isEmpty()) throw new IllegalStateException("Monster pool is empty.");
        return pool.get(rng.nextInt(pool.size())).build(x, y);
    }

    /**
     * Spawn a specific monster by name at world position (x, y).
     * Name matching is case-insensitive.
     * Throws IllegalArgumentException if no monster with that name exists.
     */
    public static Enemy spawn(String name, int x, int y) {
        ensureLoaded();
        for (MonsterDef def : pool)
            if (def.name.equalsIgnoreCase(name))
                return def.build(x, y);
        throw new IllegalArgumentException("No monster named '" + name + "' in database.");
    }

    /** Return the full list of loaded definitions (read-only view). */
    public static List<MonsterDef> getPool() {
        ensureLoaded();
        return java.util.Collections.unmodifiableList(pool);
    }

    // ── Loading ───────────────────────────────────────────────────────────────

    private static void ensureLoaded() {
        if (!pool.isEmpty()) return;   // already loaded
        try {
            load();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load monsters from " + DB_PATH, e);
        }
    }

    private static void load() throws SQLException {
        // JDBC URL for SQLite — the driver is on the classpath via sqlite-jdbc
        String url = "jdbc:sqlite:" + DB_PATH;

        try (Connection conn = DriverManager.getConnection(url);
             Statement  stmt = conn.createStatement();
             ResultSet  rs   = stmt.executeQuery(
                     "SELECT name, glyph, max_health, attack_power, hit_chance, " +
                             "       sight_range, sees_through_doors, xp_value, drop_table " +
                             "FROM   monsters " +
                             "ORDER  BY id")) {

            while (rs.next()) {
                pool.add(new MonsterDef(
                        rs.getString("name"),
                        rs.getString("glyph").charAt(0),
                        rs.getInt   ("max_health"),
                        rs.getInt   ("attack_power"),
                        rs.getDouble("hit_chance"),
                        rs.getInt   ("sight_range"),
                        rs.getInt   ("sees_through_doors") == 1,
                        rs.getInt   ("xp_value"),
                        rs.getString("drop_table")
                ));
            }
        }

        if (pool.isEmpty())
            throw new RuntimeException("monsters table is empty — run the seed script.");

        System.err.println("[EnemyFactory] Loaded " + pool.size() + " monster types.");
    }

    // ── Querying a specific monster by name (used by LevelGen / Combat) ───────

    /**
     * Look up the xp_value for an enemy by name.
     * Used by Combat.xpReward() so XP is driven by the DB, not hardcoded.
     */
    public static int xpValueFor(String name) {
        ensureLoaded();
        for (MonsterDef def : pool)
            if (def.name.equalsIgnoreCase(name))
                return def.xpValue;
        return 5;   // fallback if name not found
    }

    /**
     * Look up the drop_table key for an enemy by name.
     * Used by Combat.rollDrop() to select the right loot table.
     */
    public static String dropTableFor(String name) {
        ensureLoaded();
        for (MonsterDef def : pool)
            if (def.name.equalsIgnoreCase(name))
                return def.dropTable;
        return "default";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MonsterDef  –  an immutable snapshot of one row from the monsters table
    // ─────────────────────────────────────────────────────────────────────────
    public static class MonsterDef {
        public final String  name;
        public final char    glyph;
        public final int     maxHealth;
        public final int     attackPower;
        public final double  hitChance;
        public final int     sightRange;
        public final boolean seesThroughDoors;
        public final int     xpValue;
        public final String  dropTable;

        MonsterDef(String name, char glyph, int maxHealth, int attackPower,
                   double hitChance, int sightRange, boolean seesThroughDoors,
                   int xpValue, String dropTable) {
            this.name             = name;
            this.glyph            = glyph;
            this.maxHealth        = maxHealth;
            this.attackPower      = attackPower;
            this.hitChance        = hitChance;
            this.sightRange       = sightRange;
            this.seesThroughDoors = seesThroughDoors;
            this.xpValue          = xpValue;
            this.dropTable        = dropTable;
        }

        /** Instantiate a live Enemy from this definition at world position (x, y). */
        public Enemy build(int x, int y) {
            return new Enemy(name, glyph, maxHealth, attackPower,
                    hitChance, sightRange, seesThroughDoors, x, y);
        }
    }
}