import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// ─────────────────────────────────────────────────────────────────────────────
// GameState  –  single source of truth for all shared mutable state
//
// Every other class reads and writes these fields directly.
// Nothing is instantiated — all fields are static.
// ─────────────────────────────────────────────────────────────────────────────
public class GameState {

    // ── Viewport ──────────────────────────────────────────────────────────────
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

    // ── Entities ──────────────────────────────────────────────────────────────
    static Player       player;
    static List<Enemy>  enemies  = new ArrayList<>();
    static List<Potion> items    = new ArrayList<>();
    static List<Room>   rooms    = new ArrayList<>();
    static Random       rng      = new Random();

    // ── Combat log ────────────────────────────────────────────────────────────
    static final int    LOG_SIZE  = 4;
    static List<String> combatLog = new ArrayList<>();

    // ── Sprite cache ──────────────────────────────────────────────────────────
    static final String                    ASSETS_DIR   = "Assets";
    static final Map<String, List<String>> spriteCache  = new HashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    // Room  –  axis-aligned rectangle used by level generation
    // ─────────────────────────────────────────────────────────────────────────
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

    // ── Shared helpers ────────────────────────────────────────────────────────

    static void log(String msg) {
        combatLog.add(msg);
        if (combatLog.size() > LOG_SIZE) combatLog.remove(0);
    }

    static Enemy enemyAt(int x, int y) {
        for (Enemy e : enemies)
            if (e.isAlive() && e.getWorldX() == x && e.getWorldY() == y)
                return e;
        return null;
    }

    static Potion itemAt(int x, int y) {
        for (Potion p : items)
            if (!p.isConsumed() && p.getWorldX() == x && p.getWorldY() == y)
                return p;
        return null;
    }
}