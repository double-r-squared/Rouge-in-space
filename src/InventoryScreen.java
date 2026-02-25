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
                GameState.log(GameState.player.getInventory().usePotion(GameState.player));
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private static void renderOverlay(boolean dropMode) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("\033[H");

        List<String> lines = GameState.player.getInventory().buildDisplayLines(dropMode);
        for (int row = 0; row < GameState.VIEW_H; row++) {
            sb.append("\033[K");
            if (row < lines.size()) sb.append(lines.get(row));
            sb.append('\n');
        }

        sb.append("\033[K").append(GameState.player.getStatusBar()).append('\n');
        sb.append("\033[K  ").append(dropMode
                ? "** DROP MODE — press a number to drop, D to cancel **"
                : "INVENTORY — V/ESC to close").append('\n');

        for (int i = 0; i < GameState.LOG_SIZE; i++) {
            sb.append("\033[K");
            if (i < GameState.combatLog.size())
                sb.append(" ").append(GameState.combatLog.get(i));
            sb.append('\n');
        }

        System.out.print(sb);
        System.out.flush();
    }

    // ── Input handling ────────────────────────────────────────────────────────

    private static void handleNumberKey(int idx, boolean dropMode, boolean awaitPotion) {
        Inventory inv = GameState.player.getInventory();

        if (awaitPotion) {
            if (dropMode) {
                Potion dropped = inv.dropPotion(idx);
                if (dropped == null) {
                    GameState.log("No potion in slot " + (idx + 1) + ".");
                } else {
                    dropped.worldX   = GameState.player.getWorldX();
                    dropped.worldY   = GameState.player.getWorldY();
                    dropped.consumed = false;
                    GameState.items.add(dropped);
                    GameState.log("Dropped " + dropped.getName() + " at your feet.");
                }
            } else {
                List<Potion> potions = inv.getPotions();
                if (idx >= potions.size()) {
                    GameState.log("No potion in slot " + (idx + 1) + ".");
                } else {
                    GameState.log(potions.remove(idx).use(GameState.player));
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