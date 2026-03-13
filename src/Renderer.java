import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

// ─────────────────────────────────────────────────────────────────────────────
// Renderer  –  all screen drawing
//
// Covers:
//   • Main game view (map + HUD + combat log)
//   • Sprite loading and caching from the assets/ folder
//   • Hit animation (normal → negative sprite flash)
// ─────────────────────────────────────────────────────────────────────────────
public class Renderer {

    // ── Border stamp ─────────────────────────────────────────────────────────

    /**
     * Draw the border file once onto the full terminal screen.
     *
     * Called on game start and whenever a full redraw is needed (e.g. after
     * closing an overlay).  The border occupies the outer shell; the world
     * viewport in the middle is left as spaces and filled by render().
     *
     * The border is stamped from the top-left corner of the terminal.
     * Lines shorter than BORDER_FILE_W are padded with spaces.
     * Lines longer are truncated.
     */
    static void stampBorder() {
        List<String> lines = loadSprite("border");
        if (lines == null) return;   // border file missing — silent fallback

        StringBuilder sb = new StringBuilder();
        sb.append(GameState.ESC + "[H");   // cursor to top-left

        // Stop before the bottom content rows (status bar + log) so stampBorder
        // never erases HUD text that render() wrote into the bottom border wall.
        // The very last row (bottom wall art) is still drawn as part of the border.
        int stampRows = GameState.BORDER_FILE_H;   // draw all rows on full stamp
        for (int row = 0; row < stampRows; row++) {
            String line = (row < lines.size()) ? lines.get(row) : "";
            // Pad or truncate to exact border width
            if (line.length() > GameState.BORDER_FILE_W) {
                sb.append(line, 0, GameState.BORDER_FILE_W);
            } else {
                sb.append(line);
                for (int i = line.length(); i < GameState.BORDER_FILE_W; i++)
                    sb.append(' ');
            }
            sb.append(GameState.ESC + "[K\n");
        }

        System.out.print(sb);
        System.out.flush();
    }

    // ── Main render ───────────────────────────────────────────────────────────

    /**
     * Render the world view into the inner viewport only.
     *
     * Uses absolute cursor positioning (ESC[row;colH) to place each viewport
     * row at the correct terminal row and column, leaving the border cells
     * completely untouched.  This means stampBorder() only needs to be called
     * once — render() never overwrites border cells.
     *
     * Terminal rows and columns are 1-based in ANSI escape sequences, so we
     * add 1 to the 0-based VIEWPORT_ROW / VIEWPORT_COL offsets.
     */
    static void render() {
        StringBuilder sb = new StringBuilder();

        int px = GameState.player.getWorldX();
        int py = GameState.player.getWorldY();

        // ── World viewport ────────────────────────────────────────────────────
        for (int vy = 0; vy < GameState.VIEW_H + 1; vy++) {
            // Move cursor to the start of this viewport row
            int termRow = GameState.VIEWPORT_ROW + vy + 1;   // 1-based
            int termCol = GameState.VIEWPORT_COL + 1;         // 1-based
            sb.append(String.format(GameState.ESC + "[%d;%dH", termRow, termCol));

            for (int vx = 0; vx < GameState.VIEW_W + 1; vx++) {
                int wx = px + (vx - GameState.VIEW_CX);
                int wy = py + (vy - GameState.VIEW_CY);

                if (vx == GameState.VIEW_CX && vy == GameState.VIEW_CY) {
                    sb.append('@');
                } else if (wx < 0 || wx >= GameState.WORLD_W ||
                        wy < 0 || wy >= GameState.WORLD_H) {
                    sb.append(' ');
                } else if (!GameState.explored[wy][wx]) {
                    sb.append(' ');
                } else {
                    Enemy e = GameState.enemyAt(wx, wy);
                    if (e != null) {
                        sb.append(e.getGlyph());
                    } else {
                        Item item = GameState.itemAt(wx, wy);
                        if (item != null) {
                            sb.append(item.getGlyph());
                        } else {
                            switch (GameState.map[wy][wx]) {
                                case GameState.TILE_WALL:     sb.append('#'); break;
                                case GameState.TILE_DOOR:     sb.append('+'); break;
                                case GameState.TILE_FLOOR:    sb.append('*'); break;
                                case GameState.TILE_CORRIDOR: sb.append('.'); break;
                                default:                      sb.append(' '); break;
                            }
                        }
                    }
                }
            }
        }

        // ── Status bar, log, artifacts — below the border frame ─────────────
        // Rows start at BORDER_FILE_H + 1 (1-based), safely below the border.
        // centredInViewport centres within VIEW_W using VIEWPORT_COL offset.

        int statusBarAnsiRow = GameState.BORDER_FILE_H + 1;
        int logAnsiRow       = GameState.BORDER_FILE_H + 2;
        int artifactsAnsiRow = GameState.BORDER_FILE_H + 3;

        sb.append(centredInViewport(
                GameState.C_WHITE + GameState.player.getStatusBar().trim() + GameState.C_RESET,
                statusBarAnsiRow));

        if (!GameState.combatLog.isEmpty())
            sb.append(centredInViewport(
                    GameState.C_RED + buildLogLine() + GameState.C_RESET,
                    logAnsiRow));

        sb.append(centredInViewport(
                GameState.C_PURPLE + "Artifacts: " + GameState.artifactsCollected + GameState.C_RESET,
                artifactsAnsiRow));

        System.out.print(sb);
        System.out.flush();
    }

    // ── HUD helpers ──────────────────────────────────────────────────────────

    /**
     * Build an ANSI sequence that positions the cursor at the given ANSI row
     * and centres `text` within the inner viewport width.
     *
     * Centering is relative to VIEWPORT_COL and VIEW_W so the text sits
     * visually centred inside the border walls, not the full terminal width.
     *
     * If the text is wider than VIEW_W it is truncated.
     */
    static String centredInViewport(String text, int ansiRow) {
        int visibleLen = visibleLength(text);

        if (visibleLen > GameState.VIEW_W) {
            text = truncateVisible(text, GameState.VIEW_W);
            visibleLen = GameState.VIEW_W;
        }

        int leftPad  = (GameState.VIEW_W - visibleLen) / 2;
        int termCol  = GameState.VIEWPORT_COL + leftPad + 1;
        int clearCol = GameState.VIEWPORT_COL + 1;

        // Clear exactly VIEW_W characters first so stale text from any
        // previously longer string is erased, without touching border walls.
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(GameState.ESC + "[%d;%dH", ansiRow, clearCol));
        for (int i = 0; i < GameState.VIEW_W; i++) sb.append(' ');
        sb.append(String.format(GameState.ESC + "[%d;%dH", ansiRow, termCol));
        sb.append(text);
        return sb.toString();
    }

    /** Count printable characters, skipping ESC[...m sequences. */
    private static int visibleLength(String s) {
        int count = 0;
        boolean inEsc = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == (char)27) { inEsc = true; continue; }
            if (inEsc) { if (c == 'm') inEsc = false; continue; }
            count++;
        }
        return count;
    }

    /** Truncate to maxVisible printable characters, preserving escape codes. */
    private static String truncateVisible(String s, int maxVisible) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        boolean inEsc = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == (char)27) { inEsc = true; sb.append(c); continue; }
            if (inEsc) { sb.append(c); if (c == 'm') inEsc = false; continue; }
            if (count >= maxVisible) break;
            sb.append(c);
            count++;
        }
        // Always close with a reset so colours don't bleed into border
        sb.append(GameState.C_RESET);
        return sb.toString();
    }

    /**
     * Combine the last LOG_SIZE combat log entries into a single display line.
     * With LOG_SIZE=2 the format is:  entry0   |   entry1
     * If only one entry exists it is shown alone.
     * If no entries exist an empty string is returned.
     */
    private static String buildLogLine() {
        if (GameState.combatLog.isEmpty()) return "";
        return GameState.combatLog.get(0).trim();
    }

    // ── Sprite loading ────────────────────────────────────────────────────────

    /**
     * Load a sprite from assets/<key>.txt and cache it.
     * Returns null if the file is missing — callers must handle that gracefully.
     */
    static List<String> loadSprite(String key) {
        if (GameState.spriteCache.containsKey(key))
            return GameState.spriteCache.get(key);

        try {
            List<String> lines = Files.readAllLines(
                    Paths.get(GameState.ASSETS_DIR + key + ".txt"));
            GameState.spriteCache.put(key, lines);
            return lines;
        } catch (IOException e) {
            GameState.spriteCache.put(key, null);
            return null;
        }
    }

    // ── Sprite rendering ──────────────────────────────────────────────────────

    /**
     * Paint a sprite into the inner viewport only — never touches border cells.
     *
     * Uses absolute cursor positioning (same as render()) so each sprite row
     * lands at the correct terminal position inside the border frame.
     * Lines are padded/truncated to VIEW_W.  If the sprite is taller than
     * VIEW_H it is centred vertically; if shorter, remaining rows are blanked.
     */
    static void renderSprite(List<String> lines) {
        StringBuilder sb = new StringBuilder();

        int startLine = (lines.size() > GameState.VIEW_H)
                ? (lines.size() - GameState.VIEW_H) / 2 : 0;

        for (int row = 0; row < GameState.VIEW_H + 1; row++) {
            // Position cursor at the correct terminal cell for this viewport row
            int termRow = GameState.VIEWPORT_ROW + row + 1;   // 1-based
            int termCol = GameState.VIEWPORT_COL + 1;          // 1-based
            sb.append(String.format(GameState.ESC + "[%d;%dH", termRow, termCol));

            int srcRow = startLine + row;
            String line = (srcRow < lines.size()) ? lines.get(srcRow) : "";

            // Truncate to viewport width
            if (line.length() > GameState.VIEW_W)
                line = line.substring(0, GameState.VIEW_W);

            sb.append(line);
            // Pad remaining cols with spaces so stale characters are cleared
            // for (int i = line.length(); i < GameState.VIEW_W; i++) sb.append(' ');
        }

        System.out.print(sb);
        System.out.flush();
    }

    // ── Hit animation ─────────────────────────────────────────────────────────


    // ── Animated encounter display ────────────────────────────────────────────

    /**
     * Display an animated encounter sequence from a folder.
     * Looks for files matching pattern: enemyName-000.txt, enemyName-001.txt, etc.
     * Displays each frame for 125ms (1/8 second).
     * Returns true if animation was displayed, false if folder doesn't exist.
     */
    static boolean showEncounterAnimation(String enemyName) {
        String folderPath = GameState.ASSETS_DIR + enemyName.toLowerCase() + "-encounter";
        File folder = new File(folderPath);

        if (!folder.exists() || !folder.isDirectory()) {
            return false;
        }

        File[] files = folder.listFiles((dir, name) ->
                name.matches(enemyName.toLowerCase() + "_\\d{3}\\.txt"));

        if (files == null || files.length == 0) {
            return false;
        }

        // Sort files numerically
        java.util.Arrays.sort(files, (f1, f2) -> f1.getName().compareTo(f2.getName()));

        try {
            for (File file : files) {
                List<String> lines = Files.readAllLines(file.toPath());
                renderSprite(lines);
                Thread.sleep(125); // 1/8 second
            }
            return true;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /**
     * Flash normal → negative sprite inside the viewport when an enemy hits.
     * Falls back silently if either sprite file is missing.
     * Re-stamps the border after the animation so any bleed is cleaned up.
     * Total duration: ~500ms (250ms per frame).
     */
    static void showHitAnimation(Enemy e) {

        // Try to show encounter animation first
        if (showEncounterAnimation(e.getName())) { return; }

        // If false fallback
        List<String> normal   = loadSprite(e.getName().toLowerCase());
        List<String> negative = loadSprite(e.getName().toLowerCase() + "-neg");
        if (normal == null || negative == null) return;

        try {
            renderSprite(normal);
            Thread.sleep(250);
            renderSprite(negative);
            Thread.sleep(250);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}