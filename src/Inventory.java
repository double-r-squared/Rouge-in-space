import java.util.ArrayList;
import java.util.List;

// ─────────────────────────────────────────────────────────────────────────────
// Inventory
//
// Each player has one Inventory instance.  It holds:
//   • A list of Weapon objects (including consumable weapons like Flashbang)
//   • A list of Potion objects stashed for later use
//   • A single Ammo stack (stackable, weight 0)
//   • An active weapon slot (index into weapons list, or -1 = unarmed)
//
// Weight capacity is set per player class.  The total weight of all weapons
// and potions must stay at or below maxWeight to add a new item.
// Ammo has zero weight and never counts against the cap.
//
// The class intentionally has no dependency on Game or rendering — it is
// pure data + logic so it can be unit-tested or swapped out independently.
// ─────────────────────────────────────────────────────────────────────────────
public class Inventory {

    // ── Weight capacity by class ──────────────────────────────────────────────
    public static int capacityFor(PlayerClass pc) {
        return capacityFor_static(pc.label);
    }

    /** Called from PlayerClass enum constructor — takes label string to avoid circular dependency. */
    public static int capacityFor_static(String label) {
        switch (label) {
            case "Soldier":   return 20;
            case "Marine":    return 15;
            case "Engineer":  return 25;
            case "Scientist": return 12;
            case "Pilot":     return 18;
            case "Medic":     return 14;
            default:          return 15;
        }
    }

    // ── State ─────────────────────────────────────────────────────────────────
    private final double     maxWeight;
    private final List<Weapon> weapons  = new ArrayList<>();
    private final List<Potion> potions  = new ArrayList<>();
    private Ammo               ammo     = new Ammo(0);
    private int                activeWeaponIndex = -1;   // -1 = unarmed (bare hands)

    public Inventory(PlayerClass pc) {
        this.maxWeight = capacityFor(pc);
    }

    // ── Weight tracking ───────────────────────────────────────────────────────

    public double currentWeight() {
        double w = 0;
        for (Weapon wp : weapons) w += wp.getWeight();
        for (Potion p  : potions) w += 0.5;   // potions are light, fixed 0.5 each
        return w;
    }

    public double getMaxWeight() { return maxWeight; }

    public boolean canFit(double itemWeight) {
        return currentWeight() + itemWeight <= maxWeight;
    }

    // ── Weapons ───────────────────────────────────────────────────────────────

    /**
     * Add a weapon to inventory if weight allows.
     * @return true on success, false if over capacity.
     */
    public boolean addWeapon(Weapon w) {
        if (!canFit(w.getWeight())) return false;
        weapons.add(w);
        // Auto-equip if this is the first weapon
        if (activeWeaponIndex == -1) activeWeaponIndex = 0;
        return true;
    }

    /** Equip the weapon at position index (0-based). */
    public boolean equipWeapon(int index) {
        if (index < 0 || index >= weapons.size()) return false;
        activeWeaponIndex = index;
        return true;
    }

    /** Quick-equip: find and equip the first weapon of the given type. */
    public boolean equipFirstOfType(WeaponType type) {
        for (int i = 0; i < weapons.size(); i++) {
            if (weapons.get(i).getType() == type) {
                activeWeaponIndex = i;
                return true;
            }
        }
        return false;
    }

    /** Remove and return the active weapon (e.g. after a consumable is used). */
    public Weapon removeActiveWeapon() {
        if (activeWeaponIndex < 0 || activeWeaponIndex >= weapons.size()) return null;
        Weapon removed = weapons.remove(activeWeaponIndex);
        activeWeaponIndex = weapons.isEmpty() ? -1 : Math.min(activeWeaponIndex, weapons.size() - 1);
        return removed;
    }

    /** Drop a weapon by index — returns the weapon so Game can place it on the map. */
    public Weapon dropWeapon(int index) {
        if (index < 0 || index >= weapons.size()) return null;
        Weapon dropped = weapons.remove(index);
        // Keep activeWeaponIndex valid after removal
        if (weapons.isEmpty()) {
            activeWeaponIndex = -1;
        } else if (activeWeaponIndex >= weapons.size()) {
            activeWeaponIndex = weapons.size() - 1;
        }
        return dropped;
    }

    /** Drop a stashed potion by index — returns it so Game can place it on the map. */
    public Potion dropPotion(int index) {
        if (index < 0 || index >= potions.size()) return null;
        return potions.remove(index);
    }

    public Weapon       getActiveWeapon()  { return (activeWeaponIndex >= 0 && activeWeaponIndex < weapons.size()) ? weapons.get(activeWeaponIndex) : null; }
    public int          getActiveIndex()   { return activeWeaponIndex; }
    public List<Weapon> getWeapons()       { return weapons; }

    // ── Potions ───────────────────────────────────────────────────────────────

    public boolean stashPotion(Potion p) {
        if (!canFit(0.5)) return false;
        potions.add(p);
        return true;
    }

    /** Use the first potion of the given type (or just the first in the list). */
    public String usePotion(Player player) {
        if (potions.isEmpty()) return "No potions in inventory.";
        Potion p = potions.remove(0);
        return p.use(player);
    }

    public List<Potion> getPotions() { return potions; }

    // ── Ammo ─────────────────────────────────────────────────────────────────

    public void  addAmmo(int count)   { ammo.add(count); }
    public Ammo  getAmmo()            { return ammo; }
    public boolean consumeAmmo()      { return ammo.consume(); }

    // ── Display ───────────────────────────────────────────────────────────────

    /**
     * Build the inventory display lines.
     *
     * @param dropMode  when true, labels change to show drop instructions
     *                  instead of equip instructions so the player knows
     *                  the next number key will drop rather than equip.
     */
    public List<String> buildDisplayLines(boolean dropMode) {
        List<String> lines = new ArrayList<>();

        lines.add(String.format("  INVENTORY  [%.1f / %.1f kg]", currentWeight(), maxWeight));
        lines.add("  ──────────────────────────────────────────────────────────");

        // ── Weapons ──────────────────────────────────────────────────────────
        lines.add("  WEAPONS:");
        if (weapons.isEmpty()) {
            lines.add("    (none)");
        } else {
            for (int i = 0; i < weapons.size(); i++) {
                boolean active = (i == activeWeaponIndex);
                String marker = active ? "  [E] " : "      ";
                String action = dropMode
                        ? String.format("  [D+%d] drop", i + 1)
                        : String.format("  [%d] equip", i + 1);
                lines.add(String.format("%s%d. %-36s%s%s",
                        marker, i + 1,
                        weapons.get(i).getDisplayName(),
                        action,
                        active ? "  <-- equipped" : ""));
            }
        }

        lines.add("");

        // ── Potions ──────────────────────────────────────────────────────────
        lines.add("  POTIONS:");
        if (potions.isEmpty()) {
            lines.add("    (none)");
        } else {
            for (int i = 0; i < potions.size(); i++) {
                String action = dropMode
                        ? String.format("  [D+%d] drop", i + 1)
                        : String.format("  [P+%d] use ", i + 1);
                lines.add(String.format("      %d. %-36s%s", i + 1, potions.get(i).getName(), action));
            }
        }

        lines.add("");

        // ── Ammo ─────────────────────────────────────────────────────────────
        lines.add("  AMMO:  " + ammo.getDisplayName());
        lines.add("");
        lines.add("  ──────────────────────────────────────────────────────────");

        // ── Controls hint — changes based on mode ─────────────────────────────
        if (dropMode) {
            lines.add("  DROP MODE  Press D + a number to drop that item.");
            lines.add("  [D+1..9] Drop weapon   [D+P1..9] Drop potion   [D] Exit drop mode");
        } else {
            lines.add("  [1-9] Equip weapon   [U] Use first potion   [P1-9] Use potion by slot");
            lines.add("  [D] Enter drop mode  [V / ESC] Close inventory");
        }

        return lines;
    }
}