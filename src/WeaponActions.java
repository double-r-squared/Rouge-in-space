import java.util.List;

// ─────────────────────────────────────────────────────────────────────────────
// WeaponActions  –  all player-initiated weapon and item use
//
// Each method returns a boolean indicating whether a game turn was consumed,
// so the game loop knows whether to tick enemies afterward.
// ─────────────────────────────────────────────────────────────────────────────
public class WeaponActions {

    // ── Floor item pickup (E key) ─────────────────────────────────────────────

    static boolean tryUseItem() {
        Potion p = GameState.itemAt(
                GameState.player.getWorldX(), GameState.player.getWorldY());
        if (p == null) {
            GameState.log("Nothing to use here.");
            return false;
        }
        GameState.log(p.use(GameState.player));
        GameState.items.removeIf(Potion::isConsumed);
        return true;
    }

    // ── Melee attack (T key) ──────────────────────────────────────────────────

    static boolean tryWeaponAttack() {
        Weapon w = GameState.player.getInventory().getActiveWeapon();
        if (w == null) {
            GameState.log("No weapon equipped."); return false;
        }
        if (w.getType() != WeaponType.MELEE) {
            GameState.log("Use I/Q/F for non-melee weapons."); return false;
        }
        List<Enemy> adj = Combat.adjacentEnemies();
        if (adj.isEmpty()) {
            GameState.log("No enemy adjacent to attack."); return false;
        }
        Enemy target = adj.get(0);
        int dmg = w.resolveAttack(GameState.player.getPlayerClass(), target);
        if (dmg == 0) {
            GameState.log("You swing your " + w.getWeaponClass() + " and miss!");
        } else {
            GameState.log("You hit " + target.getName() + " with "
                    + w.getWeaponClass() + " for " + dmg + " dmg.");
        }
        if (!target.isAlive()) {
            GameState.player.gainExperience(Combat.xpReward(target));
            GameState.log(target.getName() + " defeated!");
            Combat.rollDrop(target);
        }
        return true;
    }

    // ── Flashbang (I key) ────────────────────────────────────────────────────

    static boolean tryUseFlashbang() {
        Inventory inv = GameState.player.getInventory();
        Weapon w = findOrEquip(inv, Flashbang.class);
        if (!(w instanceof Flashbang)) {
            GameState.log("No Flashbang in inventory."); return false;
        }
        GameState.log(((Flashbang) w).use(
                GameState.player.getPlayerClass(),
                Combat.adjacentEnemies(),
                GameState.player));
        inv.removeActiveWeapon();
        return true;
    }

    // ── Chemical (Q key) ─────────────────────────────────────────────────────

    static boolean tryUseChemical() {
        Inventory inv = GameState.player.getInventory();
        Weapon w = findOrEquip(inv, Chemical.class);
        if (!(w instanceof Chemical)) {
            GameState.log("No Chemical in inventory."); return false;
        }
        GameState.log(((Chemical) w).use(Combat.adjacentEnemies()));
        inv.removeActiveWeapon();
        return true;
    }

    // ── Laser gun (F key) ────────────────────────────────────────────────────

    static boolean tryFireLaser() {
        Inventory inv = GameState.player.getInventory();
        if (!(inv.getActiveWeapon() instanceof LaserGun))
            inv.equipFirstOfType(WeaponType.RANGED);

        Weapon w = inv.getActiveWeapon();
        if (!(w instanceof LaserGun)) {
            GameState.log("No Laser Gun in inventory."); return false;
        }
        if (!inv.getAmmo().hasAmmo()) {
            GameState.log("Out of ammo!"); return false;
        }

        int range = GameState.SIGHT + GameState.player.getSightBonus();
        Enemy target = Combat.closestEnemyInLOS(range);
        if (target == null) {
            GameState.log("No target in range (" + range + " tiles)."); return false;
        }

        inv.consumeAmmo();
        int dmg = w.resolveAttack(GameState.player.getPlayerClass(), target);
        if (dmg == 0) {
            GameState.log("Laser shot at " + target.getName() + " — missed!");
        } else {
            GameState.log("Laser hits " + target.getName() + " for " + dmg
                    + " dmg.  AMO:" + inv.getAmmo().getCount() + " left.");
        }
        if (!target.isAlive()) {
            GameState.player.gainExperience(Combat.xpReward(target));
            GameState.log(target.getName() + " destroyed!");
            Combat.rollDrop(target);
        }
        return true;
    }

    // ── Quick-equip laser (1 key) ─────────────────────────────────────────────

    static void tryQuickEquipLaser() {
        if (!GameState.player.getInventory().equipFirstOfType(WeaponType.RANGED)) {
            GameState.log("No Laser Gun in inventory."); return;
        }
        GameState.log("Laser Gun equipped.");
        PlayerClass pc = GameState.player.getPlayerClass();
        if (pc != PlayerClass.SOLDIER && pc != PlayerClass.MARINE)
            Combat.tickEnemies();   // costs a turn for non-synergy classes
    }

    // ── Private helper ────────────────────────────────────────────────────────

    /** Find the first weapon of a given type in inventory and auto-equip it. */
    @SuppressWarnings("unchecked")
    private static <T extends Weapon> Weapon findOrEquip(
            Inventory inv, Class<T> type) {
        Weapon current = inv.getActiveWeapon();
        if (type.isInstance(current)) return current;
        List<Weapon> weapons = inv.getWeapons();
        for (int i = 0; i < weapons.size(); i++) {
            if (type.isInstance(weapons.get(i))) {
                inv.equipWeapon(i);
                return inv.getActiveWeapon();
            }
        }
        return null;
    }
}