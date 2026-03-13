import java.util.ArrayList;
import java.util.List;

// ─────────────────────────────────────────────────────────────────────────────
// Combat  –  all combat resolution, enemy AI ticking, and item drop tables
//
// Covers:
//   • Player move-or-attack
//   • Melee exchange (player → enemy counter)
//   • Enemy turn (aggro update, move, attack)
//   • XP rewards
//   • Enemy item drop tables
//   • Adjacency + LOS helpers used by both combat and weapons
// ─────────────────────────────────────────────────────────────────────────────
public class Combat {

    // ── Player movement / bump attack ─────────────────────────────────────────

    static boolean tryMoveOrAttack(int dx, int dy) {
        int nx = GameState.player.getWorldX() + dx;
        int ny = GameState.player.getWorldY() + dy;
        if (nx < 0 || nx >= GameState.WORLD_W ||
                ny < 0 || ny >= GameState.WORLD_H) return false;

        Enemy target = GameState.enemyAt(nx, ny);
        if (target != null && target.isAlive()) {
            resolveMelee(GameState.player, target);
            return true;
        }

        char tile = GameState.map[ny][nx];
        if (tile == GameState.TILE_WALL || tile == GameState.TILE_EMPTY) return false;
        GameState.player.move(dx, dy);
        LevelGen.revealAround(GameState.player.getWorldX(), GameState.player.getWorldY());
        LevelGen.forgetRandom(GameState.player.getWorldX(), GameState.player.getWorldY(), GameState.player.getDecayFogBonus());
        return true;
    }

    // ── Melee exchange ────────────────────────────────────────────────────────

    static void resolveMelee(Player p, Enemy e) {
        int playerDmg = p.attackEnemy(e);
        if (playerDmg == 0) {
            GameState.log("You swing at " + e.getName() + " and miss!");
        } else {
            GameState.log("You hit " + e.getName() + " for " + playerDmg + " dmg. ("
                    + e.getCurrentHealth() + "/" + e.getMaxHealth() + " HP left)");
        }

        if (!e.isAlive()) {
            int xp = xpReward(e);
            p.gainExperience(xp);
            GameState.enemiesKilled++;
            GameState.log(e.getName() + " is defeated! +" + xp + " XP");
            rollDrop(e);
        }
        // Enemy counter-attack handled exclusively by tickEnemies() so the
        // enemy never gets two attacks on a player-initiated melee turn.
    }

    /** XP reward is defined per-monster in the DB, not derived from stats. */
    static int xpReward(Enemy e) {
        return EnemyFactory.xpValueFor(e.getName());
    }

    // ── Enemy turn ────────────────────────────────────────────────────────────

    static void tickEnemies() {
        for (Enemy e : GameState.enemies) {
            if (!e.isAlive()) continue;
            e.updateAggro(GameState.player, GameState.map);
            if (!e.isAggro()) continue;

            int px = GameState.player.getWorldX(), py = GameState.player.getWorldY();
            int ex = e.getWorldX(),                ey = e.getWorldY();

            if (Math.abs(ex - px) <= 1 && Math.abs(ey - py) <= 1
                    && !(ex == px && ey == py)) {
                int dmg = e.attack(GameState.player);
                if (dmg == 0) {
                    GameState.log(e.getName() + " lunges but misses you.");
                } else {
                    GameState.log( e.getName() + " hits you for "
                            + dmg + " dmg. ("
                            + GameState.player.getCurrentHealth() + "/"
                            + GameState.player.getMaxHealth() + " HP left)"
                            );
                    Renderer.showHitAnimation(e);
                    // Taking damage spikes decay — immediately forget extra tiles
                    LevelGen.forgetRandom(GameState.player.getWorldX(),
                            GameState.player.getWorldY(), 10);
                }
            } else {
                e.stepToward(GameState.player, GameState.map, GameState.enemies);
            }
        }
    }

    // ── Drop tables ───────────────────────────────────────────────────────────

    /**
     * Roll for item drop on enemy death.
     * The drop_table column in monsters.db maps each monster to a loot
     * behaviour key.  Adding a new monster only requires a DB row — no
     * Java changes needed here.
     * Drop table keys:
     *   zombie   – mostly potions, some melee, some ammo
     *   mutant   – mostly melee, some potions, some throwables
     *   ghost    – chemicals only
     *   skeleton – mostly ammo, some ranged, some potions
     *   titan    – mostly ranged, some melee, some potions
     *   eye      – mostly chemicals, some potions
     *   snake    – mostly ammo, some potions
     *   default  – equal split potion / ammo
     */
    static void rollDrop(Enemy e) {
        int threshold = (GameState.player.getPlayerClass() == PlayerClass.PILOT) ? 50 : 35;
        if (GameState.rng.nextInt(100) >= threshold) return;

        int    ex        = e.getWorldX(), ey = e.getWorldY();
        int    roll      = GameState.rng.nextInt(100);
        String dropTable = EnemyFactory.dropTableFor(e.getName());

        switch (dropTable) {
            case "zombie":
                if (roll < 70)      dropPotion(ex, ey);
                else if (roll < 85) dropWeapon(ex, ey, "melee");
                else                dropAmmo(ex, ey);
                break;
            case "mutant":
                if (roll < 65)      dropWeapon(ex, ey, "melee");
                else if (roll < 85) dropPotion(ex, ey);
                else                dropWeapon(ex, ey, "throwable");
                break;
            case "ghost":
                dropWeapon(ex, ey, "chemical");
                break;
            case "skeleton":
                if (roll < 50)      dropAmmo(ex, ey);
                else if (roll < 80) dropWeapon(ex, ey, "ranged");
                else                dropPotion(ex, ey);
                break;
            case "titan":
                if (roll < 60)      dropWeapon(ex, ey, "ranged");
                else if (roll < 85) dropWeapon(ex, ey, "melee");
                else                dropPotion(ex, ey);
                break;
            case "eye":
                if (roll < 70)      dropWeapon(ex, ey, "chemical");
                else                dropPotion(ex, ey);
                break;
            case "snake":
                if (roll < 60)      dropAmmo(ex, ey);
                else                dropPotion(ex, ey);
                break;
            default:
                if (roll < 50) dropPotion(ex, ey);
                else           dropAmmo(ex, ey);
        }
    }

    static void dropPotion(int x, int y) {
        Item i = LevelGen.randomPotion(x, y);
        GameState.items.add(i);
        GameState.log("Dropped: " + i.getName() + " at your feet. (E to pick up)");
    }

    static void dropAmmo(int x, int y) {
        int qty = 3 + GameState.rng.nextInt(5);
        GameState.items.add(new AmmoPickup(x, y, qty));
        GameState.log("Dropped: Ammo x" + qty + " at your feet. (E to pick up)");
    }

    static void dropWeapon(int x, int y, String category) {
        Weapon w;
        switch (category) {
            case "melee":     w = GameState.rng.nextBoolean() ? new Knife() : new Sword(); break;
            case "throwable": w = new Flashbang(); break;
            case "chemical":  w = new BlindingChemical(); break;
            case "ranged":    w = new LaserGun(); break;
            default:          dropPotion(x, y); return;
        }
        GameState.items.add(new DroppedWeapon(x, y, w));
        GameState.log("Dropped: " + w.getWeaponClass() + " at your feet. (E to pick up)");
    }

    // ── Adjacency + LOS helpers ───────────────────────────────────────────────

    static List<Enemy> adjacentEnemies() {
        List<Enemy> result = new ArrayList<>();
        int px = GameState.player.getWorldX(), py = GameState.player.getWorldY();
        for (Enemy e : GameState.enemies) {
            if (!e.isAlive()) continue;
            if (Math.abs(e.getWorldX() - px) <= 1 &&
                    Math.abs(e.getWorldY() - py) <= 1 &&
                    !(e.getWorldX() == px && e.getWorldY() == py))
                result.add(e);
        }
        return result;
    }

    static Enemy closestEnemyInLOS(int range) {
        int px = GameState.player.getWorldX(), py = GameState.player.getWorldY();
        Enemy closest = null;
        int   closestDist = Integer.MAX_VALUE;
        for (Enemy e : GameState.enemies) {
            if (!e.isAlive()) continue;
            int dist = Math.max(Math.abs(e.getWorldX() - px),
                    Math.abs(e.getWorldY() - py));
            if (dist > range) continue;
            if (!playerHasLOSto(e)) continue;
            if (dist < closestDist) { closest = e; closestDist = dist; }
        }
        return closest;
    }

    static boolean playerHasLOSto(Enemy e) {
        int x0 = GameState.player.getWorldX(), y0 = GameState.player.getWorldY();
        int x1 = e.getWorldX(), y1 = e.getWorldY();
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx + dy, cx = x0, cy = y0;
        while (true) {
            if (cx == x1 && cy == y1) return true;
            if (!(cx == x0 && cy == y0)) {
                char tile = GameState.map[cy][cx];
                if (tile == '#' || tile == '+') return false;
            }
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; cx += sx; }
            if (e2 <= dx) { err += dx; cy += sy; }
        }
    }
}