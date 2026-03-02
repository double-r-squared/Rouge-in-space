import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// ─────────────────────────────────────────────────────────────────────────────
// SaveScreen  –  centered overlay showing run stats with an option to save
//
// Opened by pressing Ctrl+S during gameplay.
//
// Controls:
//   S         save the game (writes savegame.json)
//   ESC / Q   close without saving
// ─────────────────────────────────────────────────────────────────────────────
public class SaveScreen {

    // ── Entry point ───────────────────────────────────────────────────────────

    static void show() throws IOException {
        String status = "";   // feedback line after an action

        while (true) {
            render(status);
            int key = InputHandler.readKey();

            if (key == 27 || key == 'q' || key == 'Q') break;

            if (key == 's' || key == 'S') {
                try {
                    SaveManager.save(SaveManager.DEFAULT_SAVE);
                    status = "  Game saved to " + SaveManager.DEFAULT_SAVE + ".  " +
                             "Time played: " + GameState.formatTimePlayed();
                } catch (IOException e) {
                    status = "  SAVE FAILED: " + e.getMessage();
                }
            }
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private static void render(String status) {
        List<String> content = buildContent(status);

        // ── Box sizing ────────────────────────────────────────────────────────
        int maxW = 0;
        for (String line : content)
            maxW = Math.max(maxW, line.length());

        int innerW = Math.min(maxW + 2, GameState.VIEW_W - 4);
        int outerW = innerW + 4;  // "│ " + content + " │"

        int innerH = Math.min(content.size(), GameState.VIEW_H - 2);
        int outerH = innerH + 2;

        // ── Centre position ───────────────────────────────────────────────────
        int boxLeft = Math.max(0, GameState.VIEW_CX - outerW / 2);
        int boxTop  = Math.max(0, GameState.VIEW_CY - outerH / 2);

        // ── Draw ──────────────────────────────────────────────────────────────
        // Absolute cursor positioning only — never clears outside the box.
        // The border art and world behind the overlay are completely untouched.
        StringBuilder sb = new StringBuilder();

        for (int rel = 0; rel < outerH; rel++) {
            int termRow = GameState.VIEWPORT_ROW + boxTop + rel + 1;   // 1-based
            int termCol = GameState.VIEWPORT_COL + boxLeft + 1;        // 1-based
            sb.append(String.format("\033[%d;%dH", termRow, termCol));

            if (rel == 0) {
                // Top border
                String title = " SAVE GAME ";
                sb.append('┌');
                int dashes = innerW + 2 - title.length();
                repeat(sb, '─', dashes / 2);
                sb.append(title);
                repeat(sb, '─', dashes - dashes / 2);
                sb.append('┐');

            } else if (rel == outerH - 1) {
                // Bottom border
                String closehint = " ESC/Q: close ";
                sb.append('└');
                int dashes = innerW + 2 - closehint.length();
                repeat(sb, '─', dashes / 2);
                sb.append(closehint);
                repeat(sb, '─', dashes - dashes / 2);
                sb.append('┘');

            } else {
                // Content row
                String line = (rel - 1 < content.size()) ? content.get(rel - 1) : "";
                if (line.length() > innerW) line = line.substring(0, innerW);
                sb.append("│ ");
                sb.append(line);
                repeat(sb, ' ', innerW - line.length());
                sb.append(" │");
            }
        }

        System.out.print(sb);
        System.out.flush();
    }

    // ── Content lines ─────────────────────────────────────────────────────────

    private static List<String> buildContent(String status) {
        Player p = GameState.player;
        List<String> lines = new ArrayList<>();

        lines.add("");

        // ── Character ─────────────────────────────────────────────────────────
        lines.add("  CHARACTER");
        lines.add("  ─────────────────────────────────────────────");
        lines.add(String.format("  Name          %-20s", p.getName()));
        lines.add(String.format("  Class         %-20s", p.getPlayerClass().label));
        lines.add(String.format("  Level         %-5d  XP: %d / %d",
                p.getLevel(), p.getExperience(), p.getExpToNext()));
        lines.add(String.format("  Health        %d / %d",
                p.getCurrentHealth(), p.getMaxHealth()));
        lines.add(String.format("  Gold          %d",         p.getGold()));

        lines.add("");

        // ── Run stats ─────────────────────────────────────────────────────────
        lines.add("  RUN STATS");
        lines.add("  ─────────────────────────────────────────────");
        lines.add(String.format("  Enemies killed   %d",   GameState.enemiesKilled));
        lines.add(String.format("  Levels cleared   %d",   GameState.levelsCleared));
        lines.add(String.format("  Time played      %s",   GameState.formatTimePlayed()));

        lines.add("");

        // ── Inventory summary ─────────────────────────────────────────────────
        Inventory inv = p.getInventory();
        Weapon    w   = inv.getActiveWeapon();
        lines.add("  INVENTORY");
        lines.add("  ─────────────────────────────────────────────");
        lines.add(String.format("  Active weapon    %-20s",
                w != null ? w.getWeaponClass() : "Bare Hands"));
        lines.add(String.format("  Weapons          %d carried",   inv.getWeapons().size()));
        lines.add(String.format("  Potions          %d stashed",   inv.getPotions().size()));
        lines.add(String.format("  Ammo             %d rounds",    inv.getAmmo().getCount()));
        lines.add(String.format("  Weight           %.1f / %.0f kg",
                inv.currentWeight(), inv.getMaxWeight()));

        lines.add("");

        // ── Save file status ──────────────────────────────────────────────────
        lines.add("  SAVE FILE");
        lines.add("  ─────────────────────────────────────────────");
        if (SaveManager.saveExists(SaveManager.DEFAULT_SAVE)) {
            lines.add("  " + SaveManager.DEFAULT_SAVE + " exists");
        } else {
            lines.add("  No save file yet");
        }

        lines.add("");
        lines.add("  Press S to save");
        lines.add("");

        // ── Status / feedback line ────────────────────────────────────────────
        if (!status.isEmpty()) {
            lines.add("  ─────────────────────────────────────────────");
            lines.add(status);
        }

        return lines;
    }

    // ── Draw helpers ──────────────────────────────────────────────────────────

    private static void pad(StringBuilder sb, int count) {
        for (int i = 0; i < count; i++) sb.append(' ');
    }

    private static void repeat(StringBuilder sb, char c, int count) {
        for (int i = 0; i < count; i++) sb.append(c);
    }
}