import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

// ─────────────────────────────────────────────────────────────────────────────
// SaveManager  –  save and load game state to/from a JSON file
//
// FORMAT OVERVIEW
// ────────────────
// Plain UTF-8 JSON — human-readable, forward-compatible, no external libraries.
// Hand-written serialisation avoids Java object serialisation which breaks
// whenever a class changes (very common in active development).
//
// {
//   "meta":    { "version", "savedAt", "timePlayed", "timePlayedMillis" }
//   "player":  { name, class, hp, maxHp, atk, def, hit, sightBonus,
//                level, xp, xpToNext, gold, x, y,
//                "inventory": { weapons:[{class},...], potions:[{type},...],
//                               ammo, activeIndex } }
//   "map":     { "tiles": RLE string, "explored": hex string }
//   "rooms":   [ {x,y,w,h}, ... ]
//   "enemies": [ {name,x,y,hp,aggro}, ... ]   (stats re-read from DB on load)
//   "items":   [ {type,x,y,...extra}, ... ]
// }
//
// MAP ENCODING
// ─────────────
// Tiles: run-length encoded — each run is written as  <count>:<char>
//   e.g. "5: ,3:#,1:*"  means five spaces, three walls, one floor tile
//   Rows are separated by '|'.  This typically cuts the tile data by ~70%.
//
// Explored: each row of booleans packed into a hex string.
//   Each bit = one tile; bits packed MSB-first into bytes, then hex-encoded.
//   e.g. 200 tiles → 25 bytes → 50 hex chars per row.
//
// USAGE
// ──────
//   SaveManager.save("savegame.json")   — writes current GameState to file
//   SaveManager.load("savegame.json")   — restores GameState from file
//   SaveManager.saveExists("savegame.json") — true if the file is present
// ─────────────────────────────────────────────────────────────────────────────
public class SaveManager {

    private static final int    SAVE_VERSION = 1;
    static final        String  DEFAULT_SAVE = "savegame.json";

    // ── Public API ────────────────────────────────────────────────────────────

    public static boolean saveExists(String path) {
        return Files.exists(Paths.get(path));
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    public static void save(String path) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        // ── Meta ──────────────────────────────────────────────────────────────
        sb.append("  \"meta\": {\n");
        sb.append("    \"version\": ").append(SAVE_VERSION).append(",\n");
        sb.append("    \"savedAt\": \"").append(Instant.now()).append("\",\n");
        sb.append("    \"timePlayed\": \"").append(GameState.formatTimePlayed()).append("\",\n");
        sb.append("    \"timePlayedMillis\": ").append(GameState.totalPlayedMillis()).append("\n");
        sb.append("  },\n");

        // ── Player ────────────────────────────────────────────────────────────
        sb.append("  \"player\": ").append(serializePlayer()).append(",\n");

        // ── Map ───────────────────────────────────────────────────────────────
        sb.append("  \"map\": {\n");
        sb.append("    \"tiles\": \"").append(rleEncodeTiles()).append("\",\n");
        sb.append("    \"explored\": \"").append(encodeExplored()).append("\"\n");
        sb.append("  },\n");

        // ── Rooms ─────────────────────────────────────────────────────────────
        sb.append("  \"rooms\": ").append(serializeRooms()).append(",\n");

        // ── Enemies ───────────────────────────────────────────────────────────
        sb.append("  \"enemies\": ").append(serializeEnemies()).append(",\n");

        // ── Items ─────────────────────────────────────────────────────────────
        sb.append("  \"items\": ").append(serializeItems()).append("\n");

        sb.append("}\n");

        Files.writeString(Paths.get(path), sb.toString());
        GameState.log("Game saved.  Time played: " + GameState.formatTimePlayed());
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    public static void load(String path) throws IOException {
        String json = Files.readString(Paths.get(path));
        Map<String, String> root = parseObject(json);

        // ── Meta — restore accumulated play time ──────────────────────────────
        Map<String, String> meta = parseObject(root.get("meta"));
        long savedMillis = Long.parseLong(meta.get("timePlayedMillis").trim());
        GameState.previouslyPlayed = savedMillis;
        GameState.sessionStart     = System.currentTimeMillis();

        // ── Map ───────────────────────────────────────────────────────────────
        Map<String, String> mapObj = parseObject(root.get("map"));
        GameState.map      = rleDecodeTiles(mapObj.get("tiles").trim()
                .replace("\"", ""));
        GameState.explored  = decodeExplored(mapObj.get("explored").trim()
                .replace("\"", ""));

        // ── Rooms ─────────────────────────────────────────────────────────────
        GameState.rooms.clear();
        for (Map<String, String> r : parseArrayOfObjects(root.get("rooms"))) {
            GameState.rooms.add(new GameState.Room(
                    Integer.parseInt(r.get("x").trim()),
                    Integer.parseInt(r.get("y").trim()),
                    Integer.parseInt(r.get("w").trim()),
                    Integer.parseInt(r.get("h").trim())
            ));
        }

        // ── Enemies ───────────────────────────────────────────────────────────
        GameState.enemies.clear();
        for (Map<String, String> e : parseArrayOfObjects(root.get("enemies"))) {
            String  name  = e.get("name").trim().replace("\"", "");
            int     x     = Integer.parseInt(e.get("x").trim());
            int     y     = Integer.parseInt(e.get("y").trim());
            int     hp    = Integer.parseInt(e.get("hp").trim());
            boolean aggro = e.get("aggro").trim().equals("true");
            Enemy enemy = EnemyFactory.spawn(name, x, y);
            // Restore current HP (spawn sets it to max)
            int dmg = enemy.getMaxHealth() - hp;
            if (dmg > 0) enemy.takeDamage(dmg);
            if (aggro) {
                // Force aggro flag directly via a dummy updateAggro will set it
                // — we set the field via reflection-free approach: take 0 damage
                // and let the game loop re-aggro naturally, OR expose a setter.
                // For now we use the package-accessible field directly.
                enemy.aggro = true;
            }
            GameState.enemies.add(enemy);
        }

        // ── Items ─────────────────────────────────────────────────────────────
        GameState.items.clear();
        for (Map<String, String> item : parseArrayOfObjects(root.get("items"))) {
            Potion p = deserializeItem(item);
            if (p != null) GameState.items.add(p);
        }

        // ── Player ───────────────────────────────────────────────────────────
        deserializePlayer(parseObject(root.get("player")));

        GameState.combatLog.clear();
        GameState.log("Save loaded.  Time played: " + GameState.formatTimePlayed());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Serialisation helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static String serializePlayer() {
        Player      p   = GameState.player;
        Inventory   inv = p.getInventory();
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("    \"name\": \"").append(esc(p.getName())).append("\",\n");
        sb.append("    \"class\": \"").append(p.getPlayerClass().name()).append("\",\n");
        sb.append("    \"hp\": ").append(p.getCurrentHealth()).append(",\n");
        sb.append("    \"maxHp\": ").append(p.getMaxHealth()).append(",\n");
        sb.append("    \"atk\": ").append(p.getAttack()).append(",\n");
        sb.append("    \"def\": ").append(p.getDefense()).append(",\n");
        sb.append("    \"hit\": ").append(p.getHitChance()).append(",\n");
        sb.append("    \"sightBonus\": ").append(p.getSightBonus()).append(",\n");
        sb.append("    \"level\": ").append(p.getLevel()).append(",\n");
        sb.append("    \"xp\": ").append(p.getExperience()).append(",\n");
        sb.append("    \"xpToNext\": ").append(p.getExpToNext()).append(",\n");
        sb.append("    \"gold\": ").append(p.getGold()).append(",\n");
        sb.append("    \"x\": ").append(p.getWorldX()).append(",\n");
        sb.append("    \"y\": ").append(p.getWorldY()).append(",\n");
        sb.append("    \"inventory\": ").append(serializeInventory(inv)).append("\n");
        sb.append("  }");
        return sb.toString();
    }

    private static String serializeInventory(Inventory inv) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        // Weapons
        sb.append("      \"activeIndex\": ").append(inv.getActiveIndex()).append(",\n");
        sb.append("      \"ammo\": ").append(inv.getAmmo().getCount()).append(",\n");
        sb.append("      \"weapons\": [");
        List<Weapon> weapons = inv.getWeapons();
        for (int i = 0; i < weapons.size(); i++) {
            sb.append("\"").append(weapons.get(i).getClass().getSimpleName()).append("\"");
            if (i < weapons.size() - 1) sb.append(", ");
        }
        sb.append("],\n");

        // Potions (stashed in inventory, not on the floor)
        sb.append("      \"potions\": [");
        List<Potion> potions = inv.getPotions();
        for (int i = 0; i < potions.size(); i++) {
            sb.append("\"").append(potions.get(i).getClass().getSimpleName()).append("\"");
            if (i < potions.size() - 1) sb.append(", ");
        }
        sb.append("]\n");
        sb.append("    }");
        return sb.toString();
    }

    private static String serializeRooms() {
        StringBuilder sb = new StringBuilder("[\n");
        List<GameState.Room> rooms = GameState.rooms;
        for (int i = 0; i < rooms.size(); i++) {
            GameState.Room r = rooms.get(i);
            sb.append("    {\"x\":").append(r.x)
                    .append(",\"y\":").append(r.y)
                    .append(",\"w\":").append(r.w)
                    .append(",\"h\":").append(r.h).append("}");
            if (i < rooms.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]");
        return sb.toString();
    }

    private static String serializeEnemies() {
        StringBuilder sb = new StringBuilder("[\n");
        List<Enemy> enemies = GameState.enemies;
        boolean first = true;
        for (Enemy e : enemies) {
            if (!e.isAlive()) continue;
            if (!first) sb.append(",\n");
            sb.append("    {\"name\":\"").append(esc(e.getName())).append("\"")
                    .append(",\"x\":").append(e.getWorldX())
                    .append(",\"y\":").append(e.getWorldY())
                    .append(",\"hp\":").append(e.getCurrentHealth())
                    .append(",\"aggro\":").append(e.isAggro()).append("}");
            first = false;
        }
        sb.append("\n  ]");
        return sb.toString();
    }

    private static String serializeItems() {
        StringBuilder sb = new StringBuilder("[\n");
        boolean first = true;
        for (Potion p : GameState.items) {
            if (p.isConsumed()) continue;
            if (!first) sb.append(",\n");
            if (p instanceof DroppedWeapon) {
                // Need the weapon class name to reconstruct on load
                String wClass = p.getName();   // DroppedWeapon.name = weaponClass
                sb.append("    {\"type\":\"DroppedWeapon\",\"weapon\":\"")
                        .append(esc(wClass)).append("\"")
                        .append(",\"x\":").append(p.getWorldX())
                        .append(",\"y\":").append(p.getWorldY()).append("}");
            } else if (p instanceof AmmoPickup) {
                // Parse qty back from name "Ammo xN"
                String[] parts = p.getName().split("x");
                int qty = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1;
                sb.append("    {\"type\":\"AmmoPickup\",\"qty\":").append(qty)
                        .append(",\"x\":").append(p.getWorldX())
                        .append(",\"y\":").append(p.getWorldY()).append("}");
            } else {
                sb.append("    {\"type\":\"").append(p.getClass().getSimpleName()).append("\"")
                        .append(",\"x\":").append(p.getWorldX())
                        .append(",\"y\":").append(p.getWorldY()).append("}");
            }
            first = false;
        }
        sb.append("\n  ]");
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Deserialisation helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static void deserializePlayer(Map<String, String> p) {
        String      name   = p.get("name").trim().replace("\"", "");
        PlayerClass pc     = PlayerClass.valueOf(p.get("class").trim().replace("\"", ""));
        int         x      = Integer.parseInt(p.get("x").trim());
        int         y      = Integer.parseInt(p.get("y").trim());

        // Reconstruct player at saved position
        Player player = new Player(name, pc, x, y);

        // Restore mutable stats that may have changed since creation
        int    savedHp       = Integer.parseInt(p.get("hp").trim());
        int    savedMaxHp    = Integer.parseInt(p.get("maxHp").trim());
        int    savedAtk      = Integer.parseInt(p.get("atk").trim());
        int    savedDef      = Integer.parseInt(p.get("def").trim());
        double savedHit      = Double.parseDouble(p.get("hit").trim());
        int    savedSight    = Integer.parseInt(p.get("sightBonus").trim());
        int    savedLevel    = Integer.parseInt(p.get("level").trim());
        int    savedXp       = Integer.parseInt(p.get("xp").trim());
        int    savedXpToNext = Integer.parseInt(p.get("xpToNext").trim());
        int    savedGold     = Integer.parseInt(p.get("gold").trim());

        player.restoreStats(savedHp, savedMaxHp, savedAtk, savedDef,
                savedHit, savedSight, savedLevel,
                savedXp, savedXpToNext, savedGold);

        // Restore inventory
        deserializeInventory(parseObject(p.get("inventory")), player.getInventory());

        GameState.player = player;
    }

    private static void deserializeInventory(Map<String, String> inv, Inventory inventory) {
        // Ammo
        int ammo = Integer.parseInt(inv.get("ammo").trim());
        inventory.addAmmo(ammo);

        // Weapons — reconstruct by class name
        String weaponsRaw = inv.get("weapons").trim();
        // Strip [ and ] and split by comma
        weaponsRaw = weaponsRaw.replaceAll("[\\[\\]\\s]", "");
        if (!weaponsRaw.isEmpty()) {
            for (String wClass : weaponsRaw.split(",")) {
                wClass = wClass.replace("\"", "").trim();
                Weapon w = instantiateWeapon(wClass);
                if (w != null) inventory.addWeapon(w);
            }
        }

        // Active weapon index
        int activeIdx = Integer.parseInt(inv.get("activeIndex").trim());
        inventory.equipWeapon(activeIdx);

        // Stashed potions
        String potionsRaw = inv.get("potions").trim();
        potionsRaw = potionsRaw.replaceAll("[\\[\\]\\s]", "");
        if (!potionsRaw.isEmpty()) {
            for (String pClass : potionsRaw.split(",")) {
                pClass = pClass.replace("\"", "").trim();
                Potion pot = instantiatePotion(pClass, 0, 0);
                if (pot != null) inventory.stashPotion(pot);
            }
        }
    }

    private static Potion deserializeItem(Map<String, String> item) {
        String type = item.get("type").trim().replace("\"", "");
        int x = Integer.parseInt(item.get("x").trim());
        int y = Integer.parseInt(item.get("y").trim());
        switch (type) {
            case "DroppedWeapon":
                String wClass = item.get("weapon").trim().replace("\"", "");
                Weapon w = instantiateWeapon(wClass);
                return w != null ? new DroppedWeapon(x, y, w) : null;
            case "AmmoPickup":
                int qty = Integer.parseInt(item.get("qty").trim());
                return new AmmoPickup(x, y, qty);
            case "HealthPotion":  return new HealthPotion(x, y);
            case "VisionPotion":  return new VisionPotion(x, y);
            default:              return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Map encoding: RLE tiles + hex explored
    // ─────────────────────────────────────────────────────────────────────────

    private static String rleEncodeTiles() {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < GameState.WORLD_H; y++) {
            if (y > 0) sb.append('|');
            int  runLen = 1;
            char runCh  = GameState.map[y][0];
            for (int x = 1; x < GameState.WORLD_W; x++) {
                char ch = GameState.map[y][x];
                if (ch == runCh) {
                    runLen++;
                } else {
                    sb.append(runLen).append(':').append(runCh).append(',');
                    runLen = 1; runCh = ch;
                }
            }
            sb.append(runLen).append(':').append(runCh);
        }
        return sb.toString();
    }

    private static char[][] rleDecodeTiles(String encoded) {
        char[][] result = new char[GameState.WORLD_H][GameState.WORLD_W];
        String[] rows = encoded.split("\\|");
        for (int y = 0; y < rows.length && y < GameState.WORLD_H; y++) {
            int x = 0;
            for (String run : rows[y].split(",")) {
                String[] parts = run.split(":", 2);
                if (parts.length < 2) continue;
                int  count = Integer.parseInt(parts[0]);
                char ch    = parts[1].charAt(0);
                for (int i = 0; i < count && x < GameState.WORLD_W; i++)
                    result[y][x++] = ch;
            }
        }
        return result;
    }

    private static String encodeExplored() {
        // Pack each row of booleans into bits, encode as hex
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < GameState.WORLD_H; y++) {
            if (y > 0) sb.append('|');
            // WORLD_W bits → ceil(WORLD_W/8) bytes
            int byteCount = (GameState.WORLD_W + 7) / 8;
            byte[] bytes = new byte[byteCount];
            for (int x = 0; x < GameState.WORLD_W; x++) {
                if (GameState.explored[y][x])
                    bytes[x / 8] |= (byte)(1 << (7 - (x % 8)));
            }
            for (byte b : bytes)
                sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    private static boolean[][] decodeExplored(String encoded) {
        boolean[][] result = new boolean[GameState.WORLD_H][GameState.WORLD_W];
        String[] rows = encoded.split("\\|");
        for (int y = 0; y < rows.length && y < GameState.WORLD_H; y++) {
            String row = rows[y];
            int byteCount = (GameState.WORLD_W + 7) / 8;
            for (int b = 0; b < byteCount && b * 2 + 1 < row.length(); b++) {
                int val = Integer.parseInt(row.substring(b*2, b*2+2), 16);
                for (int bit = 0; bit < 8; bit++) {
                    int x = b * 8 + bit;
                    if (x < GameState.WORLD_W)
                        result[y][x] = (val & (1 << (7 - bit))) != 0;
                }
            }
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Object instantiation by class name
    // ─────────────────────────────────────────────────────────────────────────

    private static Weapon instantiateWeapon(String className) {
        switch (className) {
            case "Knife":            return new Knife();
            case "Sword":            return new Sword();
            case "Flashbang":        return new Flashbang();
            case "LaserGun":         return new LaserGun();
            case "RayGun":           return new RayGun();
//            case "Pistol":           return new Pistol();
            case "BlindingChemical": return new BlindingChemical();
            default:
                System.err.println("[SaveManager] Unknown weapon class: " + className);
                return null;
        }
    }

    private static Potion instantiatePotion(String className, int x, int y) {
        switch (className) {
            case "HealthPotion": return new HealthPotion(x, y);
            case "VisionPotion": return new VisionPotion(x, y);
            default:
                System.err.println("[SaveManager] Unknown potion class: " + className);
                return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Minimal JSON parser — handles the subset we write (no arrays of primitives
    // at root level, no escaped unicode, no nested arrays)
    // ─────────────────────────────────────────────────────────────────────────

    /** Parse a JSON object string into a flat key→raw-value map. */
    static Map<String, String> parseObject(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}"))   json = json.substring(0, json.length() - 1);

        int i = 0;
        while (i < json.length()) {
            // Skip whitespace and commas
            while (i < json.length() && (json.charAt(i) == ',' ||
                    Character.isWhitespace(json.charAt(i)))) i++;
            if (i >= json.length()) break;

            // Read key (always a quoted string)
            if (json.charAt(i) != '"') { i++; continue; }
            int keyStart = i + 1;
            i = json.indexOf('"', keyStart);
            String key = json.substring(keyStart, i);
            i++;

            // Skip colon
            while (i < json.length() && json.charAt(i) != ':') i++;
            i++;
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;

            // Read value — may be string, number, boolean, object, or array
            String value;
            char ch = json.charAt(i);
            if (ch == '"') {
                // Quoted string
                int vStart = i + 1;
                int vEnd = vStart;
                while (vEnd < json.length()) {
                    if (json.charAt(vEnd) == '\\') { vEnd += 2; continue; }
                    if (json.charAt(vEnd) == '"')  break;
                    vEnd++;
                }
                value = "\"" + json.substring(vStart, vEnd) + "\"";
                i = vEnd + 1;
            } else if (ch == '{') {
                // Nested object — find matching closing brace
                int depth = 0, vStart = i;
                while (i < json.length()) {
                    if (json.charAt(i) == '{') depth++;
                    else if (json.charAt(i) == '}') { depth--; if (depth == 0) { i++; break; } }
                    i++;
                }
                value = json.substring(vStart, i);
            } else if (ch == '[') {
                // Array — find matching closing bracket
                int depth = 0, vStart = i;
                while (i < json.length()) {
                    if (json.charAt(i) == '[') depth++;
                    else if (json.charAt(i) == ']') { depth--; if (depth == 0) { i++; break; } }
                    i++;
                }
                value = json.substring(vStart, i);
            } else {
                // Number / boolean / null
                int vStart = i;
                while (i < json.length() && json.charAt(i) != ',' &&
                        json.charAt(i) != '\n' && json.charAt(i) != '}') i++;
                value = json.substring(vStart, i).trim();
            }
            map.put(key, value);
        }
        return map;
    }

    /** Parse a JSON array of objects into a list of maps. */
    static List<Map<String, String>> parseArrayOfObjects(String json) {
        List<Map<String, String>> list = new ArrayList<>();
        json = json.trim();
        if (json.startsWith("[")) json = json.substring(1);
        if (json.endsWith("]"))   json = json.substring(0, json.length() - 1);
        json = json.trim();
        if (json.isEmpty()) return list;

        // Split on top-level commas between objects
        int depth = 0, start = 0;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    list.add(parseObject(json.substring(start, i + 1).trim()));
                    // Skip comma and whitespace
                    start = i + 1;
                    while (start < json.length() && (json.charAt(start) == ','
                            || Character.isWhitespace(json.charAt(start)))) start++;
                    i = start - 1;
                }
            }
        }
        return list;
    }

    /** Escape special characters in a JSON string value. */
    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}