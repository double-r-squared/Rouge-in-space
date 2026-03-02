import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.io.File;

// ─────────────────────────────────────────────────────────────────────────────
// Renderer  –  all screen drawing
//
// Covers:
//   • Main game view (map + HUD + combat log)
//   • Sprite loading and caching from the assets/ folder
//   • Hit animation (normal → negative sprite flash)
// ─────────────────────────────────────────────────────────────────────────────
public class Renderer {

    // ── Overlay ───────────────────────────────────────────────────────────────
    // We want to load the border overlay over the rendered map so the border appears around the game view,
    // i want to make the boarder a txt file and each line should be as long as the VIEW_W



    // ── Main render ───────────────────────────────────────────────────────────

    static void render() {
        StringBuilder sb = new StringBuilder();
        sb.append("\033[H");

        int px = GameState.player.getWorldX();
        int py = GameState.player.getWorldY();

        for (int vy = 0; vy < GameState.VIEW_H; vy++) {
            for (int vx = 0; vx < GameState.VIEW_W; vx++) {
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
                        Potion item = GameState.itemAt(wx, wy);
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
            sb.append("\033[K\n");
        }

        // Status bar + controls hint
        sb.append("\033[K").append(GameState.player.getStatusBar()).append('\n');
        sb.append("\033[K [WASD] Move  [E] Pick up  [T] Melee  [I] Flashbang  " +
                "[Q] Chemical  [F] Laser  [1] Equip Laser  [V] Inventory  " +
                "[R] New Level  [Ctrl+S] Save  [Shift+Q] Quit\n");


        // Combat log
        for (int i = 0; i < GameState.LOG_SIZE; i++) {
            sb.append("\033[K");
            if (i < GameState.combatLog.size())
                sb.append(" ").append(GameState.combatLog.get(i));
            sb.append('\n');
        }

        System.out.print(sb);
        System.out.flush();
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
                    Paths.get(GameState.ASSETS_DIR + "/" + key + ".txt"));
            GameState.spriteCache.put(key, lines);
            return lines;
        } catch (IOException e) {
            GameState.spriteCache.put(key, null);
            return null;
        }
    }

    // ── Sprite rendering ──────────────────────────────────────────────────────

    /**
     * Paint a sprite into the viewport area only.
     * Pads/truncates each line to VIEW_W; centres vertically if taller than VIEW_H.
     * The status bar and log below are untouched.
     */
    static void renderSprite(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        sb.append("\033[H");

        int startLine = (lines.size() > GameState.VIEW_H)
                ? (lines.size() - GameState.VIEW_H) / 2 : 0;

        for (int row = 0; row < GameState.VIEW_H; row++) {
            int srcRow = startLine + row;
            String line = (srcRow < lines.size()) ? lines.get(srcRow) : "";

            if (line.length() >= GameState.VIEW_W) {
                sb.append(line, 0, GameState.VIEW_W);
            } else {
                sb.append(line);
                for (int i = line.length(); i < GameState.VIEW_W; i++) sb.append(' ');
            }
            sb.append("\033[K\n");
        }

        System.out.print(sb);
        System.out.flush();
    }

    // ── Animated encounter display ────────────────────────────────────────────

    /**
     * Display an animated encounter sequence from a folder.
     * Looks for files matching pattern: enemyName-000.txt, enemyName-001.txt, etc.
     * Displays each frame for 125ms (1/8 second).
     * Returns true if animation was displayed, false if folder doesn't exist.
     */
    static boolean showEncounterAnimation(String enemyName) {
        String folderPath = GameState.ASSETS_DIR + "/" + enemyName.toLowerCase() + "-encounter";
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
        java.util.Arrays.sort(files, Collections.reverseOrder());

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

    // ── Hit animation ─────────────────────────────────────────────────────────

    /**
     * Flash normal → negative sprite when an enemy hits the player.
     * First attempts to play encounter animation from a folder.
     * Falls back to normal/negative sprite flash if folder doesn't exist.
     * Falls back silently if either file is missing.
     */
    static void showHitAnimation(Enemy e) {
        // Try to show encounter animation first
        if (showEncounterAnimation(e.getName())) {
            return;
        }

        // Fall back to normal/negative sprite flash
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

    // ── Death animation ─────────────────────────────────────────────────────────

    /**
     * Display an animated death sequence from a folder.
     * Looks for files matching pattern: death-000.txt, death-001.txt, etc.
     * Displays each frame for 125ms (1/8 second).
     * if folder does not exit then display simple text.
     */
    static void deathAnimation() throws InterruptedException, IOException {
        String folderPath = GameState.ASSETS_DIR + "/" +  "death-encounter";
        File folder = new File(folderPath);

        File[] files = folder.listFiles((dir, name) ->
                name.matches("death-encounter" + "_\\d{3}\\.txt"));

        if (files == null || files.length == 0) {
            return;
        }

        // Sort files numerically
        java.util.Arrays.sort(files, Collections.reverseOrder());

        for (File file : files) {
            List<String> lines = Files.readAllLines(file.toPath());
            renderSprite(lines);
            Thread.sleep(125); // 1/8 second
        }
    }
}
