import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;


public class Game {

    // ── Viewport (dynamically sized at startup) ───────────────────────────────
    static int VIEW_W;
    static int VIEW_H;
    static int VIEW_CX;
    static int VIEW_CY;

    // ── Vision radius ─────────────────────────────────────────────────────────
    static final int SIGHT = 4;

    // ── Tile types ────────────────────────────────────────────────────────────
    static final char TILE_EMPTY    = ' ';
    static final char TILE_WALL     = '#';
    static final char TILE_FLOOR    = '*';
    static final char TILE_DOOR     = '+';
    static final char TILE_CORRIDOR = ',';

    // ── World canvas ──────────────────────────────────────────────────────────
    static final int WORLD_W = 200;
    static final int WORLD_H = 100;
    static char[][]    map;
    static boolean[][] explored;

    static Player      player;
    static List<Enemy>  enemies = new ArrayList<>();
    static List<Potion> items   = new ArrayList<>();
    static Random       rng     = new Random();

    // ── Combat log ────────────────────────────────────────────────────────────
    // Stores the last few lines of combat messages shown under the map.
    static final int         LOG_SIZE = 4;
    static List<String>      combatLog = new ArrayList<>();

    // ── Sprite cache ──────────────────────────────────────────────────────────
    // Loaded on first use, keyed by filename (e.g. "zombie", "zombie-neg").
    // A null value means the file was not found — skip the animation.
    static final String              ASSETS_DIR = "assets";
    static final Map<String, List<String>> spriteCache = new HashMap<>();

    // ── Room record ───────────────────────────────────────────────────────────
    static class Room {
        int x, y, w, h;

        Room(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
        }

        int cx() { return x + w / 2; }
        int cy() { return y + h / 2; }

        boolean contains(int px, int py) {
            return px > x && px < x + w - 1 && py > y && py < y + h - 1;
        }

        boolean overlaps(Room o, int padding) {
            return !(x + w + padding <= o.x ||
                    o.x + o.w + padding <= x ||
                    y + h + padding <= o.y ||
                    o.y + o.h + padding <= y);
        }
    }

    static List<Room> rooms = new ArrayList<>();

    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws IOException {
        detectTerminalSize();

        // Class + name selection runs in normal terminal mode (before raw mode)
        PlayerClass chosenClass = selectClass();
        String      chosenName  = enterName();

        generateLevel(chosenClass, chosenName);
        enableRawMode();
        try {
            gameLoop();
        } finally {
            restoreTerminal();
            clearScreen();
            System.out.println("Thanks for playing!");
        }
    }

    // ── Class selection screen ────────────────────────────────────────────────

    static PlayerClass selectClass() throws IOException {
        Scanner sc = new Scanner(System.in);
        PlayerClass[] classes = PlayerClass.values();

        while (true) {
            clearScreen();
            System.out.println("  ROGUE IN SPACE  —  SELECT YOUR CLASS");
            System.out.println();
            System.out.println("  You are adrift on a derelict station.  Something");
            System.out.println("  has gone very wrong.  Choose who you are.");
            System.out.println();
            System.out.println("  #   Class          Stats");
            System.out.println("  -   -----          -----");

            for (int i = 0; i < classes.length; i++) {
                PlayerClass pc = classes[i];
                System.out.printf("  %d   %-12s   %s%n", i + 1, pc.label, pc.description);
            }

            System.out.println();
            System.out.println("  SOLDIER   – Heavy armour, high HP, charges in head-first.");
            System.out.println("  MARINE    – Precision fighter, rarely misses, light on HP.");
            System.out.println("  SCIENTIST – Poor fighter but sees further than anyone else.");
            System.out.println("  ENGINEER  – Built-in shielding, highest defense, reliable.");
            System.out.println("  PILOT     – Fast reflexes, balanced stats, good hit chance.");
            System.out.println("  MEDIC     – Most HP of all, sustains long fights, weak hits.");
            System.out.println();
            System.out.print("  Enter a number (1-6): ");

            String input = sc.nextLine().trim();
            try {
                int choice = Integer.parseInt(input);
                if (choice >= 1 && choice <= classes.length) {
                    return classes[choice - 1];
                }
            } catch (NumberFormatException ignored) {}

            System.out.println("  Invalid choice. Press ENTER to try again.");
            sc.nextLine();
        }
    }

    static String enterName() {
        Scanner sc = new Scanner(System.in);
        clearScreen();
        System.out.println("  ROGUE IN SPACE  —  YOUR NAME");
        System.out.println();
        System.out.print("  Enter your name (or press ENTER for 'Unknown'): ");
        String name = sc.nextLine().trim();
        return name.isEmpty() ? "Unknown" : name;
    }

    // ── Terminal size detection ───────────────────────────────────────────────

    static void detectTerminalSize() {
        int w = 80, h = 24;
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", "stty size </dev/tty"});
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            String[] parts = out.split(" ");
            if (parts.length == 2) {
                h = Integer.parseInt(parts[0]);
                w = Integer.parseInt(parts[1]);
            }
        } catch (Exception ignored) {}

        // Reserve rows for status bar + combat log; ensure odd dimensions
        VIEW_H = h - 3 - LOG_SIZE;
        VIEW_W = w;
        if (VIEW_H % 2 == 0) VIEW_H--;
        if (VIEW_W % 2 == 0) VIEW_W--;
        VIEW_CX = VIEW_W / 2;
        VIEW_CY = VIEW_H / 2;
    }

    // ── Level generation ──────────────────────────────────────────────────────

    static void generateLevel(PlayerClass pc, String playerName) {
        map      = new char[WORLD_H][WORLD_W];
        explored = new boolean[WORLD_H][WORLD_W];
        rooms.clear();
        enemies.clear();
        items.clear();
        combatLog.clear();

        for (int y = 0; y < WORLD_H; y++)
            for (int x = 0; x < WORLD_W; x++)
                map[y][x] = TILE_EMPTY;

        int targetRooms = 5 + rng.nextInt(6);
        int attempts    = 300;

        while (rooms.size() < targetRooms && attempts-- > 0) {
            int rw = 8  + rng.nextInt(13);
            int rh = 6  + rng.nextInt(9);
            int rx = 2  + rng.nextInt(WORLD_W - rw - 4);
            int ry = 2  + rng.nextInt(WORLD_H - rh - 4);
            Room candidate = new Room(rx, ry, rw, rh);

            boolean fits = true;
            for (Room existing : rooms)
                if (candidate.overlaps(existing, 3)) { fits = false; break; }

            if (fits) {
                paintRoom(candidate);
                rooms.add(candidate);
            }
        }

        for (int i = 0; i < rooms.size() - 1; i++)
            carveCorridor(rooms.get(i), rooms.get(i + 1));

        convertWallsToDoors();

        for (int i = 1; i < rooms.size(); i++)
            spawnEnemiesInRoom(rooms.get(i));

        // Scatter potions in rooms and corridors
        spawnItems();

        Room start = rooms.get(0);
        if (player == null) {
            player = new Player(playerName, pc, start.cx(), start.cy());
        } else {
            player.setWorldX(start.cx());
            player.setWorldY(start.cy());
            player.resetSightBonus();   // vision potions expire between levels
        }
        revealAround(player.getWorldX(), player.getWorldY());
    }

    // ── Enemy spawning ────────────────────────────────────────────────────────

    // Spawn 1-3 enemies per room, chosen randomly from the available types.
    // Each enemy is placed on a random floor tile inside the room.
    static void spawnEnemiesInRoom(Room r) {
        int count = 1 + rng.nextInt(3); // 1–3 enemies
        for (int i = 0; i < count; i++) {
            // Find a random floor tile inside the room (not on walls)
            int ex, ey;
            int tries = 20;
            do {
                ex = r.x + 1 + rng.nextInt(r.w - 2);
                ey = r.y + 1 + rng.nextInt(r.h - 2);
                tries--;
            } while (tries > 0 && isOccupied(ex, ey));

            if (tries == 0) continue; // couldn't find a free tile, skip

            enemies.add(randomEnemy(ex, ey));
        }
    }

    // Returns true if an enemy is already standing on (x, y)
    static boolean isOccupied(int x, int y) {
        for (Enemy e : enemies)
            if (e.isAlive() && e.getWorldX() == x && e.getWorldY() == y) return true;
        return false;
    }

    // ── Item spawning ─────────────────────────────────────────────────────────

    /**
     * Scatter potions across the level.
     *
     * Room pass: place 0–2 potions per room (skipping the start room).
     * Corridor pass: walk every corridor tile and place a potion with a
     * low probability (8% chance per tile) so corridors occasionally have
     * items but aren't flooded with them.
     *
     * Items have no collision so we don't need to check isOccupied —
     * multiple items can theoretically share a tile but in practice the
     * spawn rates keep this rare.
     */
    static void spawnItems() {
        // Rooms — skip index 0 (start room, give the player a safe landing)
        for (int i = 1; i < rooms.size(); i++) {
            Room r = rooms.get(i);
            int count = rng.nextInt(2); // 0, 1 or 2 per room
            for (int j = 0; j < count; j++) {
                int ix = r.x + 1 + rng.nextInt(r.w - 2);
                int iy = r.y + 1 + rng.nextInt(r.h - 2);
                items.add(randomPotion(ix, iy));
            }
        }

        // Corridors — low probability per tile
        for (int y = 0; y < WORLD_H; y++) {
            for (int x = 0; x < WORLD_W; x++) {
                if (map[y][x] == TILE_CORRIDOR && rng.nextInt(100) < 4) {
                    items.add(randomPotion(x, y));
                }
            }
        }
    }

    // 60% health potion, 40% vision potion
    static Potion randomPotion(int x, int y) {
        return rng.nextInt(10) < 6 ? new HealthPotion(x, y) : new VisionPotion(x, y);
    }

    // Return the first unconsumed item at (x, y), or null
    static Potion itemAt(int x, int y) {
        for (Potion p : items)
            if (!p.isConsumed() && p.getWorldX() == x && p.getWorldY() == y)
                return p;
        return null;
    }

    /**
     * Called when the player presses E.
     * Checks if there is an item at the player's current position.
     * If so, uses it and logs the result.  Returns true if a turn was consumed.
     */
    static boolean tryUseItem() {
        Potion p = itemAt(player.getWorldX(), player.getWorldY());
        if (p == null) {
            log("Nothing to use here.");
            return false;   // no turn consumed — pressing E on empty tile is free
        }
        String msg = p.use(player);
        log(msg);
        items.removeIf(Potion::isConsumed);
        return true;
    }

    // Pick a random enemy type
    static Enemy randomEnemy(int x, int y) {
        switch (rng.nextInt(5)) {
            case 0: return new Zombie(x, y);
            case 1: return new Mutant(x, y);
            case 2: return new Snake(x, y);
            case 3: return new Titan(x, y);
            case 4: return new Eye(x, y);
            default: return new Ghost(x, y);
        }
    }

    // ── Room painting ─────────────────────────────────────────────────────────

    static void paintRoom(Room r) {
        for (int y = r.y; y < r.y + r.h; y++) {
            for (int x = r.x; x < r.x + r.w; x++) {
                boolean isWall = (x == r.x || x == r.x + r.w - 1 ||
                        y == r.y || y == r.y + r.h - 1);
                map[y][x] = isWall ? TILE_WALL : TILE_FLOOR;
            }
        }
    }

    // ── Corridor carving ──────────────────────────────────────────────────────

    static void carveCorridor(Room a, Room b) {
        int ax = a.cx(), ay = a.cy();
        int bx = b.cx(), by = b.cy();
        if (rng.nextBoolean()) { carveH(ay, ax, bx); carveV(bx, ay, by); }
        else                   { carveV(ax, ay, by); carveH(by, ax, bx); }
    }

    static void carveH(int y, int x1, int x2) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            if (map[y][x] == TILE_EMPTY) map[y][x] = TILE_CORRIDOR;
    }

    static void carveV(int x, int y1, int y2) {
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
            if (map[y][x] == TILE_EMPTY) map[y][x] = TILE_CORRIDOR;
    }

    static void convertWallsToDoors() {
        int[] ddx = {0, 0, -1, 1};
        int[] ddy = {-1, 1, 0, 0};
        for (Room r : rooms) {
            for (int x = r.x; x < r.x + r.w; x++) {
                checkAndConvert(x, r.y,           ddx, ddy);
                checkAndConvert(x, r.y + r.h - 1, ddx, ddy);
            }
            for (int y = r.y + 1; y < r.y + r.h - 1; y++) {
                checkAndConvert(r.x,           y, ddx, ddy);
                checkAndConvert(r.x + r.w - 1, y, ddx, ddy);
            }
        }
    }

    static void checkAndConvert(int x, int y, int[] ddx, int[] ddy) {
        if (map[y][x] != TILE_WALL) return;
        for (int i = 0; i < 4; i++) {
            int nx = x + ddx[i], ny = y + ddy[i];
            if (nx >= 0 && nx < WORLD_W && ny >= 0 && ny < WORLD_H
                    && map[ny][nx] == TILE_CORRIDOR) {
                map[y][x] = TILE_DOOR;
                return;
            }
        }
    }

    // ── Visibility / fog of war ───────────────────────────────────────────────

    static void revealAround(int cx, int cy) {
        int radius = SIGHT + (player != null ? player.getSightBonus() : 0);
        for (int dy = -radius; dy <= radius; dy++)
            for (int dx = -radius; dx <= radius; dx++) {
                int tx = cx + dx, ty = cy + dy;
                if (tx >= 0 && tx < WORLD_W && ty >= 0 && ty < WORLD_H)
                    explored[ty][tx] = true;
            }
    }

    // ── Game loop ─────────────────────────────────────────────────────────────

    static void gameLoop() throws IOException {
        while (player.isAlive()) {
            render();
            int key = readKey();
            if (key == -1) continue;

            int dx = 0, dy = 0;
            switch (key) {
                case 'w': case 'W': dy = -1; break;
                case 's': case 'S': dy =  1; break;
                case 'a': case 'A': dx = -1; break;
                case 'd': case 'D': dx =  1; break;
                case 1000: dy = -1; break;
                case 1001: dy =  1; break;
                case 1002: dx = -1; break;
                case 1003: dx =  1; break;
                case 'r': case 'R':
                    generateLevel(player.getPlayerClass(), player.getName());
                    continue;
                case 'e': case 'E':
                    tryUseItem();
                    tickEnemies();
                    continue;
                    // ── Weapon actions ─────────────────────────────────────────
                case 't': case 'T':
                    // Use active melee weapon (bare-hands attack is handled by
                    // walking into an enemy; T explicitly fires the active weapon
                    // at an adjacent enemy when you don't want to move)
                    if (tryWeaponAttack()) tickEnemies();
                    continue;
                case 'i': case 'I':
                    // Use Flashbang (throwable)
                    if (tryUseFlashbang()) tickEnemies();
                    continue;
                case 'q':
                    // Use Chemical (science weapon)
                    if (tryUseChemical()) tickEnemies();
                    continue;
                case 'f': case 'F':
                    // Fire laser gun (ranged)
                    if (tryFireLaser()) tickEnemies();
                    continue;
                case '1':
                    // Quick-equip laser gun (free for Soldier/Marine, costs turn otherwise)
                    tryQuickEquipLaser();
                    continue;
                case 'v': case 'V':
                    // Open inventory screen
                    showInventoryScreen();
                    continue;
                case 'Q': return;
                default: continue;
            }

            // Attempt to move — if an enemy is on the target tile, attack it
            // instead of moving.  After the player acts, all aggro'd enemies
            // take their turn.
            if (!tryMoveOrAttack(dx, dy)) continue;
            tickEnemies();
        }

        render();
        moveCursor(VIEW_H + LOG_SIZE + 5, 0);
        System.out.println("You have died. Press ENTER to exit.");
        readKey();
    }

    // ── Player move / melee attack ────────────────────────────────────────────

    /**
     * Returns true if the turn was consumed (move or attack), false if blocked.
     *
     * If the destination tile has a living enemy on it, the player attacks
     * that enemy instead of moving.  After the player's attack, if the enemy
     * survived it gets an immediate counter-attack back.  This produces the
     * alternating player→enemy→player rhythm.
     */
    static boolean tryMoveOrAttack(int dx, int dy) {
        int nx = player.getWorldX() + dx;
        int ny = player.getWorldY() + dy;
        if (nx < 0 || nx >= WORLD_W || ny < 0 || ny >= WORLD_H) return false;

        // Check for an enemy on the target tile
        Enemy target = enemyAt(nx, ny);
        if (target != null && target.isAlive()) {
            resolveMelee(player, target);
            return true;
        }

        // Otherwise try to move
        char tile = map[ny][nx];
        if (tile == TILE_WALL || tile == TILE_EMPTY) return false;
        player.move(dx, dy);
        revealAround(player.getWorldX(), player.getWorldY());
        return true;
    }

    /**
     * One full exchange: player swings first, then the enemy swings back
     * (if it survived).  Results are appended to the combat log.
     */
    static void resolveMelee(Player p, Enemy e) {
        // ── Player attacks ──
        int playerDmg = p.attackEnemy(e);
        if (playerDmg == 0) {
            log("You swing at " + e.getName() + " and miss!");
        } else {
            log("You hit " + e.getName() + " for " + playerDmg + " dmg. "
                    + "(" + e.getCurrentHealth() + "/" + e.getMaxHealth() + " HP left)");
        }

        // ── Enemy dies? ──
        if (!e.isAlive()) {
            int xp = xpReward(e);
            p.gainExperience(xp);
            log(e.getName() + " is defeated! +" + xp + " XP");
            rollDrop(e);
            return;
        }

        // ── Enemy counter-attacks ──
        int enemyDmg = e.attack(p);
        if (enemyDmg == 0) {
            log(e.getName() + " attacks but misses you.");
        } else {
            log(e.getName() + " hits you for " + enemyDmg + " dmg. "
                    + "(" + p.getCurrentHealth() + "/" + p.getMaxHealth() + " HP left)");
            // Show hit animation — render() is called by the game loop after
            // this returns, which restores the normal view automatically.
            showHitAnimation(e);
        }
    }

    // XP reward scales with enemy max HP
    static int xpReward(Enemy e) {
        return e.getMaxHealth() / 4 + 5;
    }

    // ── Item / weapon drop tables ─────────────────────────────────────────────

    /**
     * Roll for item drop on enemy death.
     * Drop chance is 50% for Pilot player class, 35% for all others.
     * Drop type is weighted per enemy type.
     */
    static void rollDrop(Enemy e) {
        int threshold = (player.getPlayerClass() == PlayerClass.PILOT) ? 50 : 35;
        if (rng.nextInt(100) >= threshold) return;

        int ex = e.getWorldX(), ey = e.getWorldY();
        String eName = e.getName();

        // Per-enemy weighted drop categories
        int roll = rng.nextInt(100);
        if (eName.equals("Zombie")) {
            // Zombie: 70% potion, 15% melee, 15% ammo
            if (roll < 70)       dropPotion(ex, ey);
            else if (roll < 85)  dropWeapon(ex, ey, "melee");
            else                 dropAmmo(ex, ey);
        } else if (eName.equals("Mutant")) {
            // Mutant: 65% melee, 20% potion, 15% throwable
            if (roll < 65)       dropWeapon(ex, ey, "melee");
            else if (roll < 85)  dropPotion(ex, ey);
            else                 dropWeapon(ex, ey, "throwable");
        } else if (eName.equals("Ghost")) {
            // Ghost: ONLY drops chemicals
            dropWeapon(ex, ey, "chemical");
        } else if (eName.equals("Skeleton")) {
            // Skeleton: 50% ammo, 30% ranged, 20% potion
            if (roll < 50)       dropAmmo(ex, ey);
            else if (roll < 80)  dropWeapon(ex, ey, "ranged");
            else                 dropPotion(ex, ey);
        } else if (eName.equals("Titan")) {
            // Troll: 60% ranged, 25% melee, 15% potion
            if (roll < 60)       dropWeapon(ex, ey, "ranged");
            else if (roll < 85)  dropWeapon(ex, ey, "melee");
            else                 dropPotion(ex, ey);

        } else if (eName.equals("Eye")) {
            // Troll: 60% ranged, 25% melee, 15% potion
            if (roll < 70)       dropWeapon(ex, ey, "chemical");
            else                 dropPotion(ex, ey);
        } else {
            // Default: equal chance potion or ammo
            if (roll < 50) dropPotion(ex, ey);
            else           dropAmmo(ex, ey);
        }
    }

    static void dropPotion(int x, int y) {
        Potion p = randomPotion(x, y);
        items.add(p);
        log("Dropped: " + p.getName() + " at your feet. (E to pick up)");
    }

    static void dropAmmo(int x, int y) {
        int qty = 3 + rng.nextInt(5);
        // Store ammo drops as a special marker — player walks over and presses E
        // We reuse the Potion slot system with an AmmoPickup wrapper
        items.add(new AmmoPickup(x, y, qty));
        log("Dropped: Ammo x" + qty + " at your feet. (E to pick up)");
    }

    static void dropWeapon(int x, int y, String category) {
        Weapon w;
        switch (category) {
            case "melee":     w = rng.nextBoolean() ? new Knife() : new Sword(); break;
            case "throwable": w = new Flashbang(); break;
            case "chemical":  w = new BlindingChemical(); break;
            case "ranged":    w = new LaserGun(); break;
            default:          dropPotion(x, y); return;
        }
        // Weapon drops go into a DroppedWeapon wrapper so they render on the map
        items.add(new DroppedWeapon(x, y, w));
        log("Dropped: " + w.getWeaponClass() + " at your feet. (E to pick up)");
    }

    // ── Weapon action methods ─────────────────────────────────────────────────

    /** Use active weapon on an adjacent enemy (T key — explicit melee/use). */
    static boolean tryWeaponAttack() {
        Weapon w = player.getInventory().getActiveWeapon();
        if (w == null) { log("No weapon equipped."); return false; }
        if (w.getType() != WeaponType.MELEE) { log("Use I/Q/F for non-melee weapons."); return false; }

        List<Enemy> adj = adjacentEnemies();
        if (adj.isEmpty()) { log("No enemy adjacent to attack."); return false; }

        Enemy target = adj.get(0);
        int dmg = w.resolveAttack(player.getPlayerClass(), target);
        if (dmg == 0) {
            log("You swing your " + w.getWeaponClass() + " and miss!");
        } else {
            log("You hit " + target.getName() + " with " + w.getWeaponClass()
                    + " for " + dmg + " dmg.");
        }
        if (!target.isAlive()) {
            player.gainExperience(xpReward(target));
            log(target.getName() + " defeated!");
            rollDrop(target);
        }
        return true;
    }

    /** Throw flashbang (I key). */
    static boolean tryUseFlashbang() {
        Inventory inv = player.getInventory();
        Weapon w = inv.getActiveWeapon();
        if (!(w instanceof Flashbang)) {
            // Look for one anywhere in the inventory
            for (int i = 0; i < inv.getWeapons().size(); i++) {
                if (inv.getWeapons().get(i) instanceof Flashbang) {
                    inv.equipWeapon(i);
                    w = inv.getActiveWeapon();
                    break;
                }
            }
        }
        if (!(w instanceof Flashbang)) { log("No Flashbang in inventory."); return false; }

        String msg = ((Flashbang)w).use(player.getPlayerClass(), adjacentEnemies(), player);
        log(msg);
        inv.removeActiveWeapon();   // consumable — remove after use
        return true;
    }

    /** Use chemical (Q key). */
    static boolean tryUseChemical() {
        Inventory inv = player.getInventory();
        Weapon w = inv.getActiveWeapon();
        if (!(w instanceof Chemical)) {
            for (int i = 0; i < inv.getWeapons().size(); i++) {
                if (inv.getWeapons().get(i) instanceof Chemical) {
                    inv.equipWeapon(i);
                    w = inv.getActiveWeapon();
                    break;
                }
            }
        }
        if (!(w instanceof Chemical)) { log("No Chemical in inventory."); return false; }

        String msg = ((Chemical)w).use(adjacentEnemies());
        log(msg);
        inv.removeActiveWeapon();
        return true;
    }

    /**
     * Fire laser gun (F key).
     *
     * Range = player's total sight (SIGHT + sightBonus).
     * Finds the closest living enemy in LOS within range.
     * If multiple are equidistant, picks the first found (future: prompt user).
     * Requires ammo.
     */
    static boolean tryFireLaser() {
        Inventory inv = player.getInventory();
        Weapon w = inv.getActiveWeapon();
        if (!(w instanceof LaserGun)) {
            if (!inv.equipFirstOfType(WeaponType.RANGED)) {
                log("No Laser Gun in inventory.");
                return false;
            }
            w = inv.getActiveWeapon();
        }
        if (!inv.getAmmo().hasAmmo()) { log("Out of ammo!"); return false; }

        int range = SIGHT + player.getSightBonus();
        Enemy target = closestEnemyInLOS(range);
        if (target == null) { log("No target in range (" + range + " tiles)."); return false; }

        inv.consumeAmmo();
        int dmg = w.resolveAttack(player.getPlayerClass(), target);
        if (dmg == 0) {
            log("Laser shot at " + target.getName() + " — missed!");
        } else {
            log("Laser hits " + target.getName() + " for " + dmg + " dmg.  AMO:"
                    + inv.getAmmo().getCount() + " left.");
        }
        if (!target.isAlive()) {
            player.gainExperience(xpReward(target));
            log(target.getName() + " destroyed!");
            rollDrop(target);
        }
        return true;
    }

    /** Quick-equip laser gun (1 key). Free turn for Soldier/Marine. */
    static void tryQuickEquipLaser() {
        boolean equipped = player.getInventory().equipFirstOfType(WeaponType.RANGED);
        if (!equipped) { log("No Laser Gun in inventory."); return; }
        log("Laser Gun equipped.");
        // Free turn for Soldier/Marine — enemies don't tick
        PlayerClass pc = player.getPlayerClass();
        boolean free = (pc == PlayerClass.SOLDIER || pc == PlayerClass.MARINE);
        if (!free) tickEnemies();
    }

    // ── Adjacency + LOS helpers ───────────────────────────────────────────────

    /** All living enemies within 1 tile of the player (8-directional). */
    static List<Enemy> adjacentEnemies() {
        List<Enemy> result = new ArrayList<>();
        int px = player.getWorldX(), py = player.getWorldY();
        for (Enemy e : enemies) {
            if (!e.isAlive()) continue;
            if (Math.abs(e.getWorldX() - px) <= 1 && Math.abs(e.getWorldY() - py) <= 1
                    && !(e.getWorldX() == px && e.getWorldY() == py)) {
                result.add(e);
            }
        }
        return result;
    }

    /**
     * Find the closest living enemy within `range` tiles that has clear LOS
     * to the player (uses Bresenham — same as enemy sight checks).
     */
    static Enemy closestEnemyInLOS(int range) {
        int px = player.getWorldX(), py = player.getWorldY();
        Enemy closest   = null;
        int   closestDist = Integer.MAX_VALUE;

        for (Enemy e : enemies) {
            if (!e.isAlive()) continue;
            int dist = Math.max(Math.abs(e.getWorldX() - px), Math.abs(e.getWorldY() - py));
            if (dist > range) continue;
            if (!playerHasLOSto(e)) continue;
            if (dist < closestDist) { closest = e; closestDist = dist; }
        }
        return closest;
    }

    /**
     * Bresenham LOS from the player's position to the enemy.
     * Walls block; doors block; corridors and floors are transparent.
     */
    static boolean playerHasLOSto(Enemy e) {
        int x0 = player.getWorldX(), y0 = player.getWorldY();
        int x1 = e.getWorldX(),      y1 = e.getWorldY();
        int dx =  Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1,   sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        int cx = x0, cy = y0;
        while (true) {
            if (cx == x1 && cy == y1) return true;
            if (!(cx == x0 && cy == y0)) {
                char tile = map[cy][cx];
                if (tile == '#' || tile == 'D') return false;
            }
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; cx += sx; }
            if (e2 <= dx) { err += dx; cy += sy; }
        }
    }

    // ── Inventory screen ──────────────────────────────────────────────────────

    /**
     * Full inventory overlay.
     *
     * Normal mode controls:
     *   1-9       equip weapon at that slot
     *   U         use the first stashed potion
     *   P then 1-9  use a specific potion by slot number
     *   D         enter drop mode
     *   V / ESC   close
     *
     * Drop mode controls (toggled by pressing D):
     *   D then 1-9    drop weapon at that slot onto the player's tile
     *   D then P 1-9  drop a specific potion onto the player's tile
     *   D             exit drop mode back to normal
     *
     * Dropped items land at the player's current world position as a
     * DroppedWeapon or a plain Potion, both picked up later with E.
     */
    static void showInventoryScreen() throws IOException {
        boolean dropMode    = false;
        boolean awaitPotion = false;  // true after P is pressed — next number = potion slot

        while (true) {
            // ── Render ────────────────────────────────────────────────────────
            StringBuilder sb = new StringBuilder();
            sb.append("\033[H");

            List<String> lines = player.getInventory().buildDisplayLines(dropMode);
            for (int row = 0; row < VIEW_H; row++) {
                sb.append("\033[K");
                if (row < lines.size()) sb.append(lines.get(row));
                sb.append('\n');
            }

            // Status bar and mode indicator
            sb.append("\033[K").append(player.getStatusBar()).append('\n');
            if (dropMode) {
                sb.append("\033[K  ** DROP MODE — press a number to drop, D to cancel **\n");
            } else {
                sb.append("\033[K  INVENTORY — V/ESC to close\n");
            }

            // Combat log
            for (int i = 0; i < LOG_SIZE; i++) {
                sb.append("\033[K");
                if (i < combatLog.size()) sb.append(" ").append(combatLog.get(i));
                sb.append('\n');
            }

            System.out.print(sb);
            System.out.flush();

            // ── Input ─────────────────────────────────────────────────────────
            int key = readKey();

            // Close
            if (key == 27 || key == 'v' || key == 'V') break;

            // Toggle drop mode
            if (key == 'd' || key == 'D') {
                dropMode    = !dropMode;
                awaitPotion = false;
                log(dropMode ? "Drop mode ON — press a number to drop an item."
                        : "Drop mode OFF.");
                continue;
            }

            // P — next number key will target a potion slot
            if (key == 'p' || key == 'P') {
                awaitPotion = true;
                log(dropMode ? "Drop potion: press slot number."
                        : "Use potion: press slot number.");
                continue;
            }

            // Number keys 1-9
            if (key >= '1' && key <= '9') {
                int idx = key - '1';   // 0-based
                Inventory inv = player.getInventory();

                if (awaitPotion) {
                    awaitPotion = false;
                    if (dropMode) {
                        // Drop potion by index
                        Potion dropped = inv.dropPotion(idx);
                        if (dropped == null) {
                            log("No potion in slot " + (idx + 1) + ".");
                        } else {
                            // Place it on the map at player's feet
                            dropped.worldX = player.getWorldX();
                            dropped.worldY = player.getWorldY();
                            dropped.consumed = false;   // make sure it's usable
                            items.add(dropped);
                            log("Dropped " + dropped.getName() + " at your feet.");
                        }
                    } else {
                        // Use potion by index
                        List<Potion> potions = inv.getPotions();
                        if (idx >= potions.size()) {
                            log("No potion in slot " + (idx + 1) + ".");
                        } else {
                            Potion p = potions.remove(idx);
                            log(p.use(player));
                        }
                    }
                } else {
                    if (dropMode) {
                        // Drop weapon by index
                        Weapon dropped = inv.dropWeapon(idx);
                        if (dropped == null) {
                            log("No weapon in slot " + (idx + 1) + ".");
                        } else {
                            items.add(new DroppedWeapon(
                                    player.getWorldX(), player.getWorldY(), dropped));
                            log("Dropped " + dropped.getWeaponClass() + " at your feet.");
                        }
                    } else {
                        // Equip weapon by index
                        if (inv.equipWeapon(idx)) {
                            Weapon w = inv.getActiveWeapon();
                            log("Equipped: " + (w != null ? w.getWeaponClass() : "nothing"));
                        } else {
                            log("No weapon in slot " + (idx + 1) + ".");
                        }
                    }
                }
                continue;
            }

            // U — use first potion (quick shortcut, works in normal mode only)
            if ((key == 'u' || key == 'U') && !dropMode) {
                log(player.getInventory().usePotion(player));
            }
        }
    }

    // ── Enemy turn ────────────────────────────────────────────────────────────

    /**
     * Called once per player turn after the player has acted.
     *
     * Each enemy first updates its aggro state via LOS check, then either
     * attacks (if adjacent) or steps toward the player (if aggro'd).
     */
    static void tickEnemies() {
        for (Enemy e : enemies) {
            if (!e.isAlive()) continue;

            // Update LOS-based aggro every tick
            e.updateAggro(player, map);

            if (!e.isAggro()) continue;

            int px = player.getWorldX(), py = player.getWorldY();
            int ex = e.getWorldX(),      ey = e.getWorldY();

            // Adjacent — attack instead of moving
            if (Math.abs(ex - px) <= 1 && Math.abs(ey - py) <= 1
                    && !(ex == px && ey == py)) {
                int dmg = e.attack(player);
                if (dmg == 0) {
                    log(e.getName() + " lunges but misses you.");
                } else {
                    log(e.getName() + " hits you for " + dmg + " dmg. "
                            + "(" + player.getCurrentHealth() + "/"
                            + player.getMaxHealth() + " HP left)");
                    showHitAnimation(e);
                }
            } else {
                e.stepToward(player, map, enemies);
            }
        }
    }

    // ── Combat log helpers ────────────────────────────────────────────────────

    static void log(String msg) {
        combatLog.add(msg);
        if (combatLog.size() > LOG_SIZE) combatLog.remove(0);
    }

    // ── Enemy lookup ──────────────────────────────────────────────────────────

    static Enemy enemyAt(int x, int y) {
        for (Enemy e : enemies)
            if (e.isAlive() && e.getWorldX() == x && e.getWorldY() == y)
                return e;
        return null;
    }

    // ── Sprite loading ────────────────────────────────────────────────────────

    /**
     * Load a sprite file from the assets/ directory.
     * Returns the lines of the file, or null if not found.
     * Results are cached so each file is only read from disk once.
     *
     * The key is the bare name without extension, e.g. "zombie" or "zombie-neg".
     * The actual path will be:  assets/zombie.txt
     */
    static List<String> loadSprite(String key) {
        // Return cached result (including null if previously not found)
        if (spriteCache.containsKey(key)) return spriteCache.get(key);

        String path = ASSETS_DIR + "/" + key + ".txt";
        try {
            List<String> lines = Files.readAllLines(Paths.get(path));
            spriteCache.put(key, lines);
            return lines;
        } catch (IOException e) {
            // File not found or unreadable — cache null so we don't retry
            spriteCache.put(key, null);
            return null;
        }
    }

    /**
     * Render a sprite into the viewport area (VIEW_W × VIEW_H rows).
     *
     * Each line of the sprite is padded or truncated to exactly VIEW_W chars
     * so it fills the full width.  If the sprite has fewer lines than VIEW_H
     * the remaining rows are filled with spaces.  If it has more, we centre
     * it vertically by taking the middle VIEW_H lines.
     *
     * The status bar and combat log below the viewport are NOT touched —
     * they are redrawn by the normal render() call that follows.
     */
    static void renderSprite(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        sb.append("\033[H"); // move cursor to top-left

        // Vertical centering: if sprite is taller than VIEW_H, take the middle
        int startLine = 0;
        if (lines.size() > VIEW_H) {
            startLine = (lines.size() - VIEW_H) / 2;
        }

        for (int row = 0; row < VIEW_H; row++) {
            int srcRow = startLine + row;
            String line = (srcRow < lines.size()) ? lines.get(srcRow) : "";

            // Pad or truncate to exactly VIEW_W characters
            if (line.length() >= VIEW_W) {
                sb.append(line, 0, VIEW_W);
            } else {
                sb.append(line);
                // Pad with spaces to fill the rest of the row
                for (int i = line.length(); i < VIEW_W; i++) sb.append(' ');
            }
            sb.append("\033[K\n");
        }

        System.out.print(sb);
        System.out.flush();
    }

    /**
     * Play the hit animation for an enemy that just dealt damage.
     *
     * 1. Look up <name>.txt  (normal sprite)
     * 2. Look up <name>-neg.txt  (inverted / negative sprite)
     * 3. If either file is missing, skip the whole animation (fallback).
     * 4. Show normal → wait 250ms → show negative → wait 250ms → return.
     *    Total animation time: ~500ms, matching the requested half-second.
     *
     * After this method returns, the caller must call render() to restore
     * the normal game view.
     */
    static void showHitAnimation(Enemy e) {
        String key    = e.getName().toLowerCase();
        String keyNeg = key + "-neg";

        List<String> normal   = loadSprite(key);
        List<String> negative = loadSprite(keyNeg);

        // If either file is missing, fall back — no animation
        if (normal == null || negative == null) return;

        //
        try { // TODO: add logic for the monster fade in with lots of frames
            // Frame 1 — normal sprite
            renderSprite(normal);
            Thread.sleep(250);

            // Frame 2 — negative sprite
            renderSprite(negative);
            Thread.sleep(250);

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    static void render() {
        StringBuilder sb = new StringBuilder();
        sb.append("\033[H");

        int px = player.getWorldX();
        int py = player.getWorldY();

        for (int vy = 0; vy < VIEW_H; vy++) {
            for (int vx = 0; vx < VIEW_W; vx++) {
                int wx = px + (vx - VIEW_CX);
                int wy = py + (vy - VIEW_CY);

                if (vx == VIEW_CX && vy == VIEW_CY) {
                    sb.append('@');
                } else if (wx < 0 || wx >= WORLD_W || wy < 0 || wy >= WORLD_H) {
                    sb.append(' ');
                } else if (!explored[wy][wx]) {
                    sb.append(' ');
                } else {
                    // Check if a living enemy is on this explored tile
                    Enemy e = enemyAt(wx, wy);
                    if (e != null) {
                        sb.append(e.getGlyph());
                    } else {
                        // Check for an item on this tile (items render above floor)
                        Potion item = itemAt(wx, wy);
                        if (item != null) {
                            sb.append(item.getGlyph());
                        } else {
                            switch (map[wy][wx]) {
                                case TILE_WALL:     sb.append('#'); break;
                                case TILE_DOOR:     sb.append('+'); break;
                                case TILE_FLOOR:    sb.append('*'); break;
                                case TILE_CORRIDOR: sb.append('.'); break;
                                default:            sb.append(' '); break;
                            }
                        }
                    }
                }
            }
            sb.append("\033[K\n");
        }

        // Status bar
        sb.append("\033[K").append(player.getStatusBar()).append('\n');
        sb.append("\033[K [WASD] Move  [E] Pick up  [T] Melee  [I] Flashbang  [Q] Chemical  [F] Laser  [1] Equip Laser  [V] Inventory  [R] New Level  [Shift+Q] Quit\n");

        // Combat log — always show LOG_SIZE lines (pad with blank lines if short)
        for (int i = 0; i < LOG_SIZE; i++) {
            sb.append("\033[K");
            if (i < combatLog.size()) sb.append(" ").append(combatLog.get(i));
            sb.append('\n');
        }

        System.out.print(sb);
        System.out.flush();
    }

    // ── Terminal helpers ──────────────────────────────────────────────────────

    static void clearScreen() {
        System.out.print("\033[2J\033[H");
        System.out.flush();
    }

    static void moveCursor(int row, int col) {
        System.out.printf("\033[%d;%dH", row + 1, col + 1);
        System.out.flush();
    }

    static void enableRawMode() throws IOException {
        System.out.print("\033[?25l\033[2J\033[H");
        System.out.flush();
        try {
            Runtime.getRuntime().exec(new String[]{"sh", "-c", "stty -echo -icanon min 1 </dev/tty"})
                    .waitFor(1, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    static void restoreTerminal() throws IOException {
        System.out.print("\033[?25h");
        System.out.flush();
        try {
            Runtime.getRuntime().exec(new String[]{"sh", "-c", "stty echo icanon </dev/tty"})
                    .waitFor(1, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    static int readKey() throws IOException {
        int c = System.in.read();
        if (c == 27) {
            if (System.in.available() > 0) {
                int c2 = System.in.read();
                if (c2 == '[' && System.in.available() > 0) {
                    int c3 = System.in.read();
                    switch (c3) {
                        case 'A': return 1000;
                        case 'B': return 1001;
                        case 'D': return 1002;
                        case 'C': return 1003;
                    }
                }
            }
            return 27;
        }
        return c;
    }
}
