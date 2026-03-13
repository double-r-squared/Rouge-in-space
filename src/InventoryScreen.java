import java.io.IOException;
import java.util.List;

// ─────────────────────────────────────────────────────────────────────────────
// InventoryScreen  –  the in-game inventory overlay
//
// Controls:
//   Normal mode:
//     1-9     equip weapon at that slot
//     U       use first potion
//     P+1-9   use potion by slot number
//     D       enter drop mode
//     V/ESC   close
//
//   Drop mode (press D to toggle):
//     1-9     drop weapon at that slot onto the player's tile
//     P+1-9   drop potion at that slot onto the player's tile
//     D       exit drop mode
// ─────────────────────────────────────────────────────────────────────────────
public class InventoryScreen {

    static void show() throws IOException {
        boolean dropMode    = false;
        boolean awaitPotion = false;

        while (true) {
            renderOverlay(dropMode);

            int key = InputHandler.readKey();

            // Close
            if (key == 27 || key == 'v' || key == 'V') break;

            // Toggle drop mode
            if (key == 'd' || key == 'D') {
                dropMode    = !dropMode;
                awaitPotion = false;
                GameState.log(dropMode ? "Drop mode ON." : "Drop mode OFF.");
                continue;
            }

            // P — prime potion slot input
            if (key == 'p' || key == 'P') {
                awaitPotion = true;
                GameState.log(dropMode ? "Drop potion: press slot number."
                                       : "Use potion:  press slot number.");
                continue;
            }

            // Number keys
            if (key >= '1' && key <= '9') {
                int idx = key - '1';
                handleNumberKey(idx, dropMode, awaitPotion);
                awaitPotion = false;
                continue;
            }

            // U — quick-use first potion (normal mode only)
            if ((key == 'u' || key == 'U') && !dropMode)
                GameState.log(GameState.player.getInventory().useItem(GameState.player));
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    /**
     * Render the inventory as a centered box within the terminal viewport.
     *
     * The box width is the longest content line + 4 (for left/right padding),
     * capped at VIEW_W.  The box height is the number of content lines + 2
     * (top/bottom border), capped at VIEW_H.
     *
     * Everything outside the box — blank rows above/below and spaces to the
     * left/right — is filled so the rest of the map is obscured cleanly.
     *
     * Layout uses VIEW_CX and VIEW_CY (the screen centre constants from
     * GameState) to calculate the top-left corner of the box.
     */
    private static void renderOverlay(boolean dropMode) {
        List<String> content = GameState.player.getInventory().buildDisplayLines(dropMode);

        // ── Box sizing ────────────────────────────────────────────────────────
        // Find the longest raw content line to set box width
        int maxContentW = 0;
        for (String line : content)
            maxContentW = Math.max(maxContentW, line.length());

        // Box inner width = max content width; outer = +4 for "| " and " |"
        int innerW = Math.min(maxContentW, GameState.VIEW_W - 4);
        int outerW = innerW + 4;

        // Box height: content lines + top border + bottom border
        int innerH = Math.min(content.size(), GameState.VIEW_H - 2);
        int outerH = innerH + 2;

        // ── Top-left corner ───────────────────────────────────────────────────
        // VIEW_CX / VIEW_CY are the centre of the viewport
        int boxLeft = GameState.VIEW_CX - outerW / 2;
        int boxTop  = GameState.VIEW_CY - outerH / 2;

        // Clamp so the box never goes off screen
        boxLeft = Math.max(0, boxLeft);
        boxTop  = Math.max(0, boxTop);

        // ── Mode label for bottom border ──────────────────────────────────────
        String modeLabel = dropMode ? " DROP MODE " : " INVENTORY ";

        // ── Build output ──────────────────────────────────────────────────────
        // Use absolute cursor positioning for every row so we only write box
        // characters — the border and world behind the overlay are untouched.
        StringBuilder sb = new StringBuilder();

        for (int relRow = 0; relRow < outerH; relRow++) {
            // Terminal row for this box row (1-based ANSI)
            int termRow = GameState.VIEWPORT_ROW + boxTop + relRow + 1;
            int termCol = GameState.VIEWPORT_COL + boxLeft + 1;
            sb.append(String.format("\033[%d;%dH", termRow, termCol));

            if (relRow == 0) {
                // ── Top border ────────────────────────────────────────────────
                sb.append(GameState.BUFFER + '┌');
                String title = " ROGUE IN SPACE ";
                int dashTotal = innerW + 2 - title.length();
                int dashL = dashTotal / 2, dashR = dashTotal - dashL;
                repeat(sb, '─', dashL);
                sb.append(title);
                repeat(sb, '─', dashR);
                sb.append('┐'+ GameState.BUFFER);

            } else if (relRow == outerH - 1) {
                // ── Bottom border ─────────────────────────────────────────────
                sb.append(GameState.BUFFER + '└');
                int dashTotal = innerW + 2 - modeLabel.length();
                int dashL = dashTotal / 2, dashR = dashTotal - dashL;
                repeat(sb, '─', dashL);
                sb.append(modeLabel);
                repeat(sb, '─', dashR);
                sb.append('┘' + GameState.BUFFER);

            } else {
                // ── Content row ───────────────────────────────────────────────
                int contentIdx = relRow - 1;
                String line = (contentIdx < content.size()) ? content.get(contentIdx) : "";
                if (line.length() > innerW)
                    line = line.substring(0, innerW);
                int rightPad = innerW - line.length();
                sb.append(GameState.BUFFER + "│ ");
                sb.append(line);
                repeat(sb, ' ', rightPad);
                sb.append(" │" + GameState.BUFFER);
            }
        }

        System.out.print(sb);
        System.out.flush();
    }

    // ── Render helpers ────────────────────────────────────────────────────────

    /** Append `count` spaces — used to push content to the right. */
    private static void pad(StringBuilder sb, int count) {
        for (int i = 0; i < count; i++) sb.append(' ');
    }

    /** Append `count` copies of character `c`. */
    private static void repeat(StringBuilder sb, char c, int count) {
        for (int i = 0; i < count; i++) sb.append(c);
    }

    // ── Input handling ────────────────────────────────────────────────────────

    private static void handleNumberKey(int idx, boolean dropMode, boolean awaitPotion) {
        Inventory inv = GameState.player.getInventory();

        if (awaitPotion) {
            if (dropMode) {
                Item dropped = inv.dropItem(idx);
                if (dropped == null) {
                    GameState.log("No items in slot " + (idx + 1) + ".");
                } else {
                    dropped.worldX   = GameState.player.getWorldX();
                    dropped.worldY   = GameState.player.getWorldY();
                    dropped.consumed = false;
                    GameState.items.add(dropped);
                    GameState.log("Dropped " + dropped.getName() + " at your feet.");
                }
            } else {
                List<Item> items = inv.getItems();
                if (idx >= items.size()) {
                    GameState.log("No items in slot " + (idx + 1) + ".");
                } else {
                    GameState.log(items.remove(idx).use(GameState.player));
                }
            }
        } else {
            if (dropMode) {
                Weapon dropped = inv.dropWeapon(idx);
                if (dropped == null) {
                    GameState.log("No weapon in slot " + (idx + 1) + ".");
                } else {
                    GameState.items.add(new DroppedWeapon(
                            GameState.player.getWorldX(),
                            GameState.player.getWorldY(), dropped));
                    GameState.log("Dropped " + dropped.getWeaponClass() + " at your feet.");
                }
            } else {
                if (inv.equipWeapon(idx)) {
                    Weapon w = inv.getActiveWeapon();
                    GameState.log("Equipped: " + (w != null ? w.getWeaponClass() : "nothing"));
                } else {
                    GameState.log("No weapon in slot " + (idx + 1) + ".");
                }
            }
        }
    }
}