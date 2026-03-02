import java.io.IOException;

// ─────────────────────────────────────────────────────────────────────────────
// Game  –  entry point and main game loop
//
// All logic lives in dedicated classes.  This file is intentionally thin —
// it just wires the pieces together and drives the turn cycle.
//
// Class map:
//   GameState       – all shared static fields (map, player, enemies, items…)
//   LevelGen        – procedural level generation and fog-of-war
//   InputHandler    – terminal setup, key reading, pre-game screens
//   Renderer        – map drawing, HUD, sprite animations
//   Combat          – movement, melee, enemy turns, drop tables, LOS
//   WeaponActions   – weapon and item use triggered by key presses
//   InventoryScreen – in-game inventory overlay
// ─────────────────────────────────────────────────────────────────────────────
public class Game {

    public static void main(String[] args) throws IOException {
        InputHandler.detectTerminalSize();

        PlayerClass chosenClass = InputHandler.selectClass();
        String      chosenName  = InputHandler.enterName();

        // ── Load or new game ─────────────────────────────────────────────────
        if (SaveManager.saveExists(SaveManager.DEFAULT_SAVE)) {
            System.out.print("  Save file found. Load it? (Y/n): ");
            String answer = new java.util.Scanner(System.in).nextLine().trim();
            if (!answer.equalsIgnoreCase("n")) {
                try {
                    LevelGen.generateLevel(chosenClass, chosenName); // init map arrays
                    SaveManager.load(SaveManager.DEFAULT_SAVE);
                } catch (Exception e) {
                    System.out.println("  Failed to load save: " + e.getMessage());
                    System.out.println("  Starting a new game instead.");
                    LevelGen.generateLevel(chosenClass, chosenName);
                }
            } else {
                LevelGen.generateLevel(chosenClass, chosenName);
            }
        } else {
            LevelGen.generateLevel(chosenClass, chosenName);
        }

        InputHandler.enableRawMode();
        try {
            gameLoop();
        } finally {
            InputHandler.restoreTerminal();
            InputHandler.clearScreen();
            System.out.println("Thanks for playing!");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    static void gameLoop() throws IOException {
        while (GameState.player.isAlive()) {
            Renderer.render();
            int key = InputHandler.readKey();
            if (key == -1) continue;

            int dx = 0, dy = 0;
            switch (key) {
                // ── Movement ──────────────────────────────────────────────────
                case 1000:          dy = -1; break;
                case 1001:          dy =  1; break;
                case 1002:          dx = -1; break;
                case 1003:          dx =  1; break;

                // ── Level / meta ──────────────────────────────────────────────
                case 'r': case 'R':
                    GameState.levelsCleared++;
                    LevelGen.generateLevel(
                            GameState.player.getPlayerClass(),
                            GameState.player.getName());
                    continue;

                case 's': case 'S':   // Ctrl+S — open save screen
                    SaveScreen.show();
                    continue;

                case 'Q': return;

                // ── Item pickup ───────────────────────────────────────────────
                case 'e': case 'E':
                    WeaponActions.tryUseItem();
                    Combat.tickEnemies();
                    continue;

                    // ── Weapon actions ────────────────────────────────────────────
                case 't': case 'T':
                    if (WeaponActions.tryWeaponAttack()) Combat.tickEnemies();
                    continue;
                case 'i': case 'I':
                    if (WeaponActions.tryUseFlashbang()) Combat.tickEnemies();
                    continue;
                case 'q':
                    if (WeaponActions.tryUseChemical()) Combat.tickEnemies();
                    continue;
                case 'f': case 'F':
                    if (WeaponActions.tryFireLaser()) Combat.tickEnemies();
                    continue;
                case '1':
                    WeaponActions.tryQuickEquipLaser();
                    continue;

                    // ── Inventory ─────────────────────────────────────────────────
                case 'v': case 'V':
                    InventoryScreen.show();
                    continue;

                default: continue;
            }

            if (!Combat.tryMoveOrAttack(dx, dy)) continue;
            Combat.tickEnemies();
        }

        // Death screen
        Renderer.render();
        InputHandler.moveCursor(GameState.VIEW_H + GameState.LOG_SIZE + 5, 0);
        System.out.println("You have died. Press ENTER to exit.");
        InputHandler.readKey();
    }
}