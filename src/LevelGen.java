// ─────────────────────────────────────────────────────────────────────────────
// LevelGen  –  procedural level generation
//
// Builds the tile map, places rooms, carves corridors, converts wall tiles
// adjacent to corridors into doors, spawns enemies and items, and places
// the player in the first room.
// ─────────────────────────────────────────────────────────────────────────────
public class LevelGen {

    static void generateLevel(PlayerClass pc, String playerName) {
        GameState.map      = new char[GameState.WORLD_H][GameState.WORLD_W];
        GameState.explored = new boolean[GameState.WORLD_H][GameState.WORLD_W];
        GameState.rooms.clear();
        GameState.enemies.clear();
        GameState.items.clear();
        GameState.combatLog.clear();

        for (int y = 0; y < GameState.WORLD_H; y++)
            for (int x = 0; x < GameState.WORLD_W; x++)
                GameState.map[y][x] = GameState.TILE_EMPTY;

        int targetRooms = 5 + GameState.rng.nextInt(6);
        int attempts    = 300;

        while (GameState.rooms.size() < targetRooms && attempts-- > 0) {
            int rw = 8  + GameState.rng.nextInt(13);
            int rh = 6  + GameState.rng.nextInt(9);
            int rx = 2  + GameState.rng.nextInt(GameState.WORLD_W - rw - 4);
            int ry = 2  + GameState.rng.nextInt(GameState.WORLD_H - rh - 4);
            GameState.Room candidate = new GameState.Room(rx, ry, rw, rh);

            boolean fits = true;
            for (GameState.Room existing : GameState.rooms)
                if (candidate.overlaps(existing, 3)) { fits = false; break; }

            if (fits) {
                paintRoom(candidate);
                GameState.rooms.add(candidate);
            }
        }

        for (int i = 0; i < GameState.rooms.size() - 1; i++)
            carveCorridor(GameState.rooms.get(i), GameState.rooms.get(i + 1));

        convertWallsToDoors();

        for (int i = 1; i < GameState.rooms.size(); i++)
            spawnEnemiesInRoom(GameState.rooms.get(i));

        spawnItems();

        GameState.Room start = GameState.rooms.get(0);
        if (GameState.player == null) {
            GameState.player = new Player(playerName, pc, start.cx(), start.cy());
        } else {
            GameState.player.setWorldX(start.cx());
            GameState.player.setWorldY(start.cy());
            GameState.player.resetSightBonus();
        }
        revealAround(GameState.player.getWorldX(), GameState.player.getWorldY());
    }

    // ── Room painting ─────────────────────────────────────────────────────────

    static void paintRoom(GameState.Room r) {
        for (int y = r.y; y < r.y + r.h; y++)
            for (int x = r.x; x < r.x + r.w; x++) {
                boolean wall = (x == r.x || x == r.x + r.w - 1 ||
                        y == r.y || y == r.y + r.h - 1);
                GameState.map[y][x] = wall ? GameState.TILE_WALL : GameState.TILE_FLOOR;
            }
    }

    // ── Corridor carving ──────────────────────────────────────────────────────

    static void carveCorridor(GameState.Room a, GameState.Room b) {
        int ax = a.cx(), ay = a.cy(), bx = b.cx(), by = b.cy();
        if (GameState.rng.nextBoolean()) { carveH(ay, ax, bx); carveV(bx, ay, by); }
        else                             { carveV(ax, ay, by); carveH(by, ax, bx); }
    }

    static void carveH(int y, int x1, int x2) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            if (GameState.map[y][x] == GameState.TILE_EMPTY)
                GameState.map[y][x] = GameState.TILE_CORRIDOR;
    }

    static void carveV(int x, int y1, int y2) {
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
            if (GameState.map[y][x] == GameState.TILE_EMPTY)
                GameState.map[y][x] = GameState.TILE_CORRIDOR;
    }

    static void convertWallsToDoors() {
        int[] ddx = {0, 0, -1, 1};
        int[] ddy = {-1, 1, 0, 0};
        for (GameState.Room r : GameState.rooms) {
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
        if (GameState.map[y][x] != GameState.TILE_WALL) return;
        for (int i = 0; i < 4; i++) {
            int nx = x + ddx[i], ny = y + ddy[i];
            if (nx >= 0 && nx < GameState.WORLD_W && ny >= 0 && ny < GameState.WORLD_H
                    && GameState.map[ny][nx] == GameState.TILE_CORRIDOR) {
                GameState.map[y][x] = GameState.TILE_DOOR;
                return;
            }
        }
    }

    // ── Enemy spawning ────────────────────────────────────────────────────────

    static void spawnEnemiesInRoom(GameState.Room r) {
        int count = 1 + GameState.rng.nextInt(3);
        for (int i = 0; i < count; i++) {
            int ex, ey, tries = 20;
            do {
                ex = r.x + 1 + GameState.rng.nextInt(r.w - 2);
                ey = r.y + 1 + GameState.rng.nextInt(r.h - 2);
                tries--;
            } while (tries > 0 && isOccupied(ex, ey));
            if (tries == 0) continue;
            GameState.enemies.add(randomEnemy(ex, ey));
        }
    }

    static boolean isOccupied(int x, int y) {
        for (Enemy e : GameState.enemies)
            if (e.isAlive() && e.getWorldX() == x && e.getWorldY() == y) return true;
        return false;
    }

    static Enemy randomEnemy(int x, int y) {
        switch (GameState.rng.nextInt(5)) {
            case 0: return new Zombie(x, y);
            case 1: return new Mutant(x, y);
            case 2: return new Snake(x, y);
            case 3: return new Titan(x, y);
            case 4: return new Eye(x, y);
            default: return new Ghost(x, y);
        }
    }

    // ── Item spawning ─────────────────────────────────────────────────────────

    static void spawnItems() {
        for (int i = 1; i < GameState.rooms.size(); i++) {
            GameState.Room r = GameState.rooms.get(i);
            int count = GameState.rng.nextInt(2);
            for (int j = 0; j < count; j++) {
                int ix = r.x + 1 + GameState.rng.nextInt(r.w - 2);
                int iy = r.y + 1 + GameState.rng.nextInt(r.h - 2);
                GameState.items.add(randomPotion(ix, iy));
            }
        }
        for (int y = 0; y < GameState.WORLD_H; y++)
            for (int x = 0; x < GameState.WORLD_W; x++)
                if (GameState.map[y][x] == GameState.TILE_CORRIDOR
                        && GameState.rng.nextInt(100) < 4)
                    GameState.items.add(randomPotion(x, y));
    }

    static Potion randomPotion(int x, int y) {
        return GameState.rng.nextInt(10) < 6
                ? new HealthPotion(x, y)
                : new VisionPotion(x, y);
    }

    // ── Fog of war ────────────────────────────────────────────────────────────

    static void revealAround(int cx, int cy) {
        int radius = GameState.SIGHT +
                (GameState.player != null ? GameState.player.getSightBonus() : 0);
        for (int dy = -radius; dy <= radius; dy++)
            for (int dx = -radius; dx <= radius; dx++) {
                int tx = cx + dx, ty = cy + dy;
                if (tx >= 0 && tx < GameState.WORLD_W &&
                        ty >= 0 && ty < GameState.WORLD_H)
                    GameState.explored[ty][tx] = true;
            }
    }
}