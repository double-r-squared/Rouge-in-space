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

    // ── Border overlay ───────────────────────────────────────────────────────
    // Measured from assets/border.txt  (190 wide × 55 tall, 3-row top/bottom
    // walls, 6-col left/right walls).  All other rendering is relative to the
    // inner viewport these constants define.
    static final int BORDER_FILE_W   = 190;  // total width of border.txt
    static final int BORDER_FILE_H   = 54;   // total height of border.txt
    static final int BORDER_TOP      = 3;    // solid rows at top
    static final int BORDER_BOTTOM   = 4;    // solid rows at bottom
    static final int BORDER_LEFT     = 6;    // solid cols on left
    static final int BORDER_RIGHT    = 6;    // solid cols on right

    // World viewport inside the border — set by InputHandler.detectTerminalSize()
    static int VIEWPORT_ROW;   // terminal row where world starts (0-based)
    static int VIEWPORT_COL;   // terminal col where world starts (0-based)

    // ── Viewport ──────────────────────────────────────────────────────────────
    // VIEW_W / VIEW_H are the *inner* dimensions (inside the border).
    // All gameplay, centering, and overlay code works in these coordinates.
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
    static final int    LOG_SIZE  = 2;   // fits in 2 rows of the bottom border wall
    static List<String> combatLog = new ArrayList<>();

    // ── Session stats ─────────────────────────────────────────────────────────
    static int  enemiesKilled = 0;
    static int  levelsCleared = 0;

    // ── Session timer ─────────────────────────────────────────────────────────
    // sessionStart is set when the game begins or a save is loaded.
    // previouslyPlayed accumulates time from all prior sessions.
    static long sessionStart     = System.currentTimeMillis();
    static long previouslyPlayed = 0L;   // milliseconds from saved sessions

    /** Total milliseconds played across all sessions including the current one. */
    static long totalPlayedMillis() {
        return previouslyPlayed + (System.currentTimeMillis() - sessionStart);
    }

    /** Format total played time as "HHh MMm SSs". */
    static String formatTimePlayed() {
        long total = totalPlayedMillis() / 1000;
        long h = total / 3600, m = (total % 3600) / 60, s = total % 60;
        return String.format("%02dh %02dm %02ds", h, m, s);
    }

    // ── Sprite cache ──────────────────────────────────────────────────────────
    static final String                    ASSETS_DIR   = "assets";
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